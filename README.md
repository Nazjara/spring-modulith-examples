# Spring Modulith Examples — Brewery

A brewery (beer catalog, inventory, orders, brewing) built as a **modular monolith** with [Spring Modulith](https://spring.io/projects/spring-modulith). The domain comes from a microservices version of the same app (Spring Boot 2.3, JMS, gateway, discovery, config server); this project shows what each of those mechanisms becomes inside one process.

## Requirements

- JDK 25
- Docker (for `compose.yaml` at runtime and Testcontainers in tests)

## Build and run

```bash
./mvnw clean verify
./mvnw spring-boot:run
```

`spring-boot:run` starts PostgreSQL from `compose.yaml` and stops it on shutdown.

## Stack

Java 25 · Spring Boot 4.1.2 · Spring Modulith 2.1.2 · Spring Data JDBC · Flyway · PostgreSQL 18 · Testcontainers

## Phases

### Phase 0 — Scaffolding

Spring Boot app with the Modulith BOM, Spring Data JDBC, Flyway and PostgreSQL via Docker Compose; tests run against Testcontainers. No modules yet.

## Microservices → Modulith

| Microservices | Modulith | Phase |
|---|---|---|
| Separate Spring Boot processes per service | Packages (application modules) inside one app | 0 |
| Gateway, discovery and config servers | Not needed: one process, one configuration | 0 |
