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

## Microservices → Modulith

| Microservices | Modulith | Phase |
|---|---|---|
| Separate Spring Boot processes per service | Packages (application modules) inside one app | 0 |
| Gateway, discovery and config servers | Not needed: one process, one configuration | 0 |
| Service boundary = network + separate codebase | Module boundary = package, checked by `ApplicationModules.verify()` (test and startup) and `allowedDependencies` | 1 |
| Order validation over JMS (`ValidateOrderRequest` → `ValidateOrderResult`, `VALIDATION_PENDING`) | Direct call `OrderService` → `CatalogService` before the order is stored | 1 |
| `DataLoader` / `BeerInventoryBootstrap` `CommandLineRunner`s inserting data on every start | Flyway migrations per module, applied once | 1 |
