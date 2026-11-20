# Spring Modulith Examples — Brewery

A brewery (beer catalog, inventory, orders, brewing) built as a **modular monolith** with [Spring Modulith](https://spring.io/projects/spring-modulith). The domain comes from a microservices version of the same app (Spring Boot 2.3, JMS, gateway, discovery, config server); this project shows what each of those mechanisms becomes inside one process.

## Requirements

- JDK 25
- PostgreSQL 18 (see [Configuration](#configuration))
- Docker for tests (Testcontainers)

## Build and run

```bash
./mvnw clean verify
./mvnw spring-boot:run
```

## Configuration

The database connection is set the same way in every environment: `spring.datasource.*` in `application.properties`, filled from environment variables. Only the values differ.

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/brewery` |
| `DB_USERNAME` | `brewery` |
| `DB_PASSWORD` | `brewery` |

Tests set the same three properties from the Testcontainers database (`TestcontainersConfiguration`).

## Stack

Java 25 · Spring Boot 4.1.2 · Spring Modulith 2.1.2 · Spring Data JDBC · Flyway · PostgreSQL 18 · Testcontainers

## Phases

### Phase 0 — Scaffolding

Spring Boot app with the Modulith BOM, Spring Data JDBC, Flyway and PostgreSQL; tests run against Testcontainers. No modules yet.

### Phase 1 — Modules and their boundaries

Three modules, each a direct sub-package of `com.nazjara.brewery`:

```
catalog    ← no dependencies           allowedDependencies = {}
inventory  ← no dependencies           allowedDependencies = {}
order      → catalog                   allowedDependencies = "catalog"
```

- **Module = package.** The module's root package is its API (`CatalogService`, `BeerInfo`, …); `internal` (aggregates, repositories) and `web` (controllers) are private to the module, although the Java classes are `public`. Other modules get records, never aggregates.
- **`ApplicationModules.verify()`** (`ModularityTests`) reads the bytecode and fails on use of another module's internal types, on cycles, and on dependencies not listed in `allowedDependencies`. It runs as a plain unit test with no Spring context. `spring.modulith.runtime.verification-enabled=true` runs the same check at startup, so a violating build does not start (`APPLICATION FAILED TO START … Spring Modulith application module verification was enabled and failed!`).
- **`allowedDependencies` only restricts.** Without it, a module may use the API of any module. With it, only the listed targets. A bare name (`"catalog"`) covers the module's root package only; a named interface needs `"catalog::events"`; `"catalog::*"` covers all named interfaces but **not** the root package. Listing a module never opens its internals. `ModularityTests` enforces the declarations on every build and pins the resulting graph.
- **`order → catalog` is a method call.** `OrderService.place` validates UPCs through `CatalogService` in the same thread and transaction before storing anything; an unknown UPC is a 400 and no order exists. This removes the old `VALIDATE_ORDER_QUEUE` / `VALIDATE_ORDER_RESPONSE_QUEUE` round trip and the `VALIDATION_PENDING` state.
- **Data:** one Flyway migration per module (`V1__catalog.sql`, `V2__inventory.sql`, `V3__order.sql`), schema and initial rows together. Modules reference each other's rows by UPC.
- **`spring-modulith-runtime`** provides the startup verification.

Requests: `brewery.http`.

### Phase 2 — Events between modules

Order allocation is an asynchronous conversation between `order` and `inventory`:

```
order ──OrderPlaced────────────────────▶ inventory   reserve stock (all or nothing)
order ◀──OrderAllocated / OrderAllocationFailed──── inventory
order ──OrderCancelled─────────────────▶ inventory   release stock
```

```
catalog    ← no dependencies           allowedDependencies = {}
inventory  → order                     allowedDependencies = "order"
order      → catalog                   allowedDependencies = "catalog"
```

- **Events can go both ways; type dependencies can't.** `verify()` finds cycles from type references (a listener's parameter, a call, a field), not from the direction events travel. `order` declares all four event types, including the replies. `inventory` listens to `order`'s events and publishes `order`'s reply types, so it depends on `order`; `order` listens only to its own types and does not depend on `inventory`. If `inventory` owned the reply type instead (say `StockAllocated`), `order`'s listener would reference it and `verify()` would report the cycle `order → inventory → order`. The microservices never showed this coupling because each service kept its own copy of `AllocateOrderRequest` / `AllocateOrderResult` and only JSON crossed the wire.
- **`@ApplicationModuleListener`** = `@Async` + `@Transactional(REQUIRES_NEW)` + `@TransactionalEventListener` (after commit). The listener sees an event only if the publishing transaction commits, runs on a task-executor thread (`task-N` in the logs), and has its own transaction. `POST /orders` therefore returns `NEW`; the order becomes `ALLOCATED` or `ALLOCATION_FAILED` a moment later.
- **Publish inside a transaction.** If no transaction is active when an event is published, Spring skips every `@ApplicationModuleListener` for it and logs that only at debug level. `ModularityTests` passes an extra ArchUnit rule to `verify()` (`VerificationOptions.andAdditionalVerifications`): every method that calls `ApplicationEventPublisher.publishEvent` must be `@Transactional`, on the method or its class, so a forgotten annotation fails the build. It cannot see self-invocation, where the annotation is present but bypassed.
- **Async needs `@EnableAsync`.** Since Phase 4, `spring-modulith-events-core` (part of the registry starter) enables it unless the app declares its own; without a registry it can't be used, so Phase 2 declared `@EnableAsync` itself.
- **Failure behaviour (no registry until Phase 4):** an exception in a listener rolls back only that listener's transaction and is logged; nothing retries it. If the app stops between the order commit and the listener, the event is lost. The order stays `NEW`.
- **Statuses:** `NEW → ALLOCATED | ALLOCATION_FAILED → CANCELLED`. Cancelling a `NEW` order is a 409: the async allocation could otherwise run after the cancellation and reserve stock for a cancelled order.
- **Inventory side:** `Allocation` aggregate (`V4__inventory_allocation.sql`) records what was reserved per order, referencing the order by id with no foreign key. Stock rows are read with `SELECT … FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`) in UPC order, so concurrent allocations of the same beer can't oversell. A repeated `OrderPlaced` for an already allocated order is ignored.
- **Tests:** `BreweryIntegrationTests` waits for the asynchronous outcome with Awaitility.

### Phase 3 — Module integration tests

`@ApplicationModuleTest` is a `@SpringBootTest` cut down to one module: component scanning, auto-configuration packages and Spring Data repositories are limited to the included modules (plus the root package), beans of other modules are removed from the context, and `ApplicationModules.verify()` runs first. Everything inside the module stays real: database, Flyway schema, transactions, after-commit async listener dispatch. A module test checks the module against its infrastructure; `BreweryIntegrationTests` (full app) checks the modules against each other.

| Bootstrap mode | Loads |
|---|---|
| `STANDALONE` (default) | the module only |
| `DIRECT_DEPENDENCIES` | plus the modules it depends on |
| `ALL_DEPENDENCIES` | plus their dependencies, transitively |

- **Only injected beans count as dependencies, event types don't.** `order` injects `CatalogService`, so `DIRECT_DEPENDENCIES` brings `catalog` in. `inventory` uses only `order`'s event types, so `order` is absent from `inventory`'s test in every mode. Modules connected by events can be tested completely alone; a synchronous call pulls the other module in or forces a mock.
- **Mock another module only for a reason:** it is expensive or non-deterministic, its failures are hard to trigger, or another team owns it. `catalog` is none of these, so `OrderModuleTests` uses `DIRECT_DEPENDENCIES` and the real `catalog`. Removed beans are not replaced automatically: `order` in `STANDALONE` would not start without a `@MockitoBean CatalogService`.
- **The test plays the absent module.** `InventoryModuleTests` publishes `OrderPlaced` and expects `OrderAllocated` / `OrderAllocationFailed`; `OrderModuleTests` checks that placing an order publishes `OrderPlaced`, then publishes the replies itself and waits for the status to change.
- **`Scenario`** (test method parameter): `publish(event)` or `stimulate(...)`, then `andWaitForEventOfType(T.class).matching…(…).toArrive()` or `andWaitForStateChange(supplier, condition)`. `publish` runs in its own transaction, which `@ApplicationModuleListener`s need. Waiting is Awaitility on the app's task executor. An event expectation does nothing until `toArrive…` is called: without it the stimulus never runs and the test passes having tested nothing.
- **`AssertablePublishedEvents`** records every event published in the context during a test method, at `publishEvent` time, before the publisher's transaction commits. A recorded event is therefore not proof of delivery, and a reply may be visible before the listener's changes are committed: `InventoryModuleTests` waits for the stock change first, then expects the event. Leftover async work from an earlier test can publish into the current test's recording, so tests match on the order id.
- **Cost:** each module/mode combination is its own cached Spring context, and with `TestcontainersConfiguration` its own PostgreSQL container. Flyway runs all migrations in each.

### Phase 4 — Event Publication Registry

`spring-modulith-starter-jdbc` makes events durable. Every `publishEvent` writes one row per after-commit listener into `event_publication`, **in the publishing transaction**: the order and the record that `inventory` still has to handle it commit or roll back together (a transactional outbox). After commit, the listener runs as before, and the registry records the outcome:

```
place() tx:   INSERT beer_order + INSERT event_publication (PUBLISHED)      ── commit
task-N:       mark PROCESSING (own tx) → listener (own tx) → mark COMPLETED (own tx)
                                                         └ exception → mark FAILED (own tx)
```

| Status | Meaning |
|---|---|
| `PUBLISHED` | Stored with the business data, listener not started |
| `PROCESSING` | Listener running |
| `COMPLETED` | Listener's transaction committed |
| `FAILED` | Listener threw, or the row was stuck too long (staleness) |
| `RESUBMITTED` | Claimed for another attempt (`completion_attempts` + 1) |

- **Schema through Flyway.** `V5__event_publication.sql` holds Modulith's v2 PostgreSQL schema; `spring.modulith.events.jdbc.schema-initialization.enabled=false`. Without that property the starter creates the table itself at startup, although its metadata documents `false` as the default. The table belongs to no module. **Upgrade obligation:** the table is now ours to evolve. On every Spring Modulith upgrade, diff `schemas/v2/schema-postgresql.sql` in the new `spring-modulith-events-jdbc` jar against V5 and add a migration for any change. The starter's own initializer would not do it either: it only runs `CREATE TABLE IF NOT EXISTS` and never alters an existing table (Modulith's v1 → v2 change needed a manual migration). `EventPublicationRows` queries the same columns.
- **At-least-once delivery.** `COMPLETED` is written in its own transaction after the listener's commits, so a crash in between re-runs the listener later. Every listener is idempotent: `inventory` ignores `OrderPlaced` for an order that already has an allocation, and `order` ignores a reply for an order that is no longer `NEW`. The check alone only covers duplicates that arrive one after another. Concurrent ones are possible too (a slow listener outliving its staleness threshold is resubmitted while still running): `inventory` is protected by the `UNIQUE` `allocation.order_id`, so the second insert fails and its stock change rolls back; `order` reads the order with `SELECT … FOR UPDATE` (`BeerOrderRepository.findLockedById`), so the duplicate waits, then sees the new status. Without the lock both would read `NEW`, and the later save could overwrite a cancellation committed in between.
- **Nothing retries on its own.** The registry stores and tracks; retrying is the app's job. `EventPublicationMaintenance` (root package) schedules `FailedEventPublications.resubmit(ResubmissionOptions)` every 30 s for `FAILED` rows at least 30 s old, at most 20 waiting to start per instance (`brewery.events.resubmission.*`). Each row is claimed with a conditional update before the listener runs again, so with several instances exactly one re-runs it. The job is not transactional: an enclosing transaction would hold the claimed rows locked while the listener tries to mark them `PROCESSING`.
- **Staleness monitor** (`spring.modulith.events.staleness.*`, 1 min): a row stuck in `PUBLISHED`, `PROCESSING` or `RESUBMITTED` (the instance died mid-listener) is marked `FAILED`, so the resubmission picks it up. Thresholds must exceed the slowest listener plus executor queueing, otherwise a slow but alive listener is re-run in parallel. It needs `@EnableScheduling`; `spring-modulith-moments` (in `starter-core`, active by default) already enables it, `EventPublicationMaintenance` declares it again so nothing depends on Moments.
- **No republish on restart.** `spring.modulith.events.republish-outstanding-events-on-restart` resubmits every incomplete row at startup, including rows other instances are processing right now, so each restart or rolling deploy duplicates in-flight work. Staleness plus scheduled resubmission recovers the same events by age instead.
- **Completion mode `update`:** completed rows stay for inspection; `EventPublicationMaintenance` deletes those completed more than 7 days ago, hourly (`brewery.events.purge.*`). `delete` would keep no history, `archive` moves rows to `event_publication_archive`.
- **Events are a persisted contract.** Rows hold the event as JSON plus its class name. Renaming or moving an event record, or changing its fields, breaks the retry of rows written before the change.
- **Known gap: poison events.** Modulith 2.1.2 has no retry limit, no "given up" status and no dead-letter queue. An event whose listener can never succeed stays `FAILED` and is resubmitted on every run, forever. Resubmission reads the oldest `FAILED` rows first, limited by `max-in-flight`, so once there are that many poison events, every run is spent on them and newer failures are never retried. `ResubmissionOptions.withFilter` can't exclude them: it is applied in memory after that limited query. Until custom handling exists (e.g. moving rows past an attempt cap to a dead-letter table), watch the number of `FAILED` rows and their `completion_attempts`.
- **Tests:** `OrderModuleTests` runs the real schedule shortened to 0.2 s: a repository spy throws once, the reply's row goes `FAILED`, is resubmitted and ends `COMPLETED` with 2 attempts; a repeated reply completes without changing the order. `InventoryModuleTests` publishes `OrderPlaced` twice and, once both rows are `COMPLETED`, checks that stock was taken once. `EventPublicationRows` reads the table, because the registry API lists completed publications only.

See the lifecycle while the app runs:

```sql
SELECT event_type, listener_id, status, completion_attempts, publication_date, completion_date
FROM event_publication
ORDER BY publication_date DESC;
```

### Phase 7 — Documentation

`DocumentationTests` draws the module model that `ModularityTests` verifies, so the documentation is regenerated on every build and always matches the code. It is a plain unit test with no Spring context. `Documenter` comes with `spring-modulith-starter-test`. Output goes to `target/spring-modulith-docs` and is not committed:

| File | Content |
|---|---|
| `components.puml` | All modules and their dependencies (C4 component diagram): `Order` uses `Catalog`, `Inventory` listens to `Order` |
| `module-<name>.puml` | One module and its direct neighbours |
| `module-<name>.adoc` | Module canvas: description, base package, API services, bean references to other modules, events listened to |
| `all-docs.adoc` | One page including all diagrams and canvases |

- **Javadoc in the canvases.** The `spring-modulith-apt` annotation processor (next to Lombok in `maven-compiler-plugin`) extracts the Javadoc of main classes and `package-info.java` into `target/generated-spring-modulith/javadoc.json` at compile time. `Documenter` reads it for the module descriptions and the component and bean-reference entries.
- **Not Modulith-specific.** `ApplicationModules.of(...)` accepts any `@SpringBootApplication` class and treats its direct sub-packages as modules. An app without `package-info`/`@ApplicationModule` can still be documented, and the diagrams then show the dependencies it actually has.
- **Viewing.** The diagrams are PlantUML (GitHub renders only Mermaid, so nothing is committed): open the `.puml` files with IntelliJ's PlantUML Integration plugin, or `all-docs.adoc` with the AsciiDoc plugin.
- **Side effect.** `Documenter` also writes `target/classes/META-INF/spring-modulith/application-modules.json`, so a jar packaged after the tests contains the module model and the runtime reads it instead of scanning the packages at startup.
- **Only C4 level 3.** Internally `Documenter` builds a C4 model with one system, one container and one component per module, all without descriptions. It knows nothing about users or the database, and its model can't be extended. The full C4 description is hand-written (Phase 7b).

### Phase 7b — C4 model

`docs/architecture/workspace.dsl` describes the brewery with the [C4 model](https://c4model.com) in Structurizr DSL: the model (people, the system, its containers and components) is written once, and each diagram is a view of it.

| View | C4 level | Shows |
|---|---|---|
| `Context` | 1 | The API client and the Brewery system; no other systems |
| `Containers` | 2 | The application and PostgreSQL: a modular monolith is one container plus its database |
| `Components` | 3 | `catalog`, `inventory`, `order`, the Event Publication Registry and `EventPublicationMaintenance` inside the application |
| `PlaceOrder` | dynamic | Placing an order step by step: the call to `catalog`, then events through the registry |

- **Level 4 (code) is left out**, as C4 recommends: it changes with every commit, and the IDE draws class diagrams on demand.
- **Hand-written, so it can drift.** Levels 1–2 change rarely. Level 3 duplicates what `ModularityTests` checks and `Documenter` generates; when modules change, update `workspace.dsl` too.
- **View locally** (Docker; Structurizr Lite and the hosted service are retired, `local` replaces Lite):

  ```bash
  docker run -it --rm -p 8090:8080 -u "$(id -u):$(id -g)" -v "$PWD/docs/architecture:/usr/local/structurizr" structurizr/structurizr local
  ```

  Then open http://localhost:8090. Edits to `workspace.dsl` show after a page reload. The generated `workspace.json` and `.structurizr/` are ignored (all views use automatic layout).
- **Validate:** replace `local` with `validate -w /usr/local/structurizr/workspace.dsl`.

## Microservices → Modulith

| Microservices | Modulith | Phase |
|---|---|---|
| Separate Spring Boot processes per service | Packages (application modules) inside one app | 0 |
| Gateway, discovery and config servers | Not needed: one process, one configuration | 0 |
| Service boundary = network + separate codebase | Module boundary = package, checked by `ApplicationModules.verify()` (test and startup) and `allowedDependencies` | 1 |
| Order validation over JMS (`ValidateOrderRequest` → `ValidateOrderResult`, `VALIDATION_PENDING`) | Direct call `OrderService` → `CatalogService` before the order is stored | 1 |
| `DataLoader` / `BeerInventoryBootstrap` `CommandLineRunner`s inserting data on every start | Flyway migrations per module, applied once | 1 |
| Allocation over JMS (`ALLOCATE_ORDER_QUEUE` → `AllocateOrderListener`, reply on `ALLOCATE_ORDER_RESPONSE_QUEUE` → `AllocationResultListener`) | `OrderPlaced` → `@ApplicationModuleListener` in `inventory` → `OrderAllocated` / `OrderAllocationFailed` → listener in `order`; after commit, on executor threads | 2 |
| Deallocation over JMS (`DEALLOCATE_ORDER_QUEUE` → `DeallocateOrderListener`) | `OrderCancelled` → `@ApplicationModuleListener` in `inventory` | 2 |
| Event classes copied into each service, JSON on the wire | One set of event records owned by `order`; the owner decides the dependency direction | 2 |
| Spring State Machine driving order status | Status transitions on the `BeerOrder` aggregate | 2 |
| Per-service test suite with stubbed neighbours (mocked Feign client, JMS test harness on the queues) | `@ApplicationModuleTest` per module (bootstrap mode decides which modules load) + `Scenario` / `AssertablePublishedEvents` | 3 |
| Message held by the JMS broker between send and receive; DB commit and send are two separate writes | Event Publication Registry: one `event_publication` row per event and listener, written in the publishing transaction | 4 |
| Broker redelivery of unacknowledged messages | Staleness monitor + scheduled `FailedEventPublications.resubmit` (`EventPublicationMaintenance`) | 4 |
| Dead-letter queue after a maximum number of delivery attempts | None in Modulith 2.1.2 (known gap, see Phase 4) | 4 |
| Architecture diagrams drawn by hand, or Zipkin's dependency view built from runtime traffic | `Documenter`: diagrams and module canvases generated from the bytecode on every build | 7 |
