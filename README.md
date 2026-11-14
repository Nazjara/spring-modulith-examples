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
- **Async needs `@EnableAsync`** (`AsyncConfiguration`). The project uses only `spring-modulith-events-api`, which holds the annotation; `spring-modulith-events-core` would enable async on its own but fails to start without an Event Publication Registry (Phase 4).
- **Failure behaviour (no registry yet):** an exception in a listener rolls back only that listener's transaction and is logged; nothing retries it. If the app stops between the order commit and the listener, the event is lost. The order stays `NEW`.
- **Statuses:** `NEW → ALLOCATED | ALLOCATION_FAILED → CANCELLED`. Cancelling a `NEW` order is a 409: the async allocation could otherwise run after the cancellation and reserve stock for a cancelled order.
- **Inventory side:** `Allocation` aggregate (`V4__inventory_allocation.sql`) records what was reserved per order, referencing the order by id with no foreign key. Stock rows are read with `SELECT … FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`) in UPC order, so concurrent allocations of the same beer can't oversell. A repeated `OrderPlaced` for an already allocated order is ignored.
- **Tests:** `BreweryIntegrationTests` waits for the asynchronous outcome with Awaitility.

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
