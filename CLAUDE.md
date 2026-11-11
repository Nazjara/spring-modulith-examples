# Spring Modulith Examples (brewery)

Learning project for Spring Modulith: one modular-monolith app that grows phase by phase. The plan and current status live in `PLAN.md`.

## Build / run

- JDK 25 is required; the shell default may be older: prefix commands with `JAVA_HOME=/usr/lib/jvm/openjdk-25`.
- Build + tests: `JAVA_HOME=/usr/lib/jvm/openjdk-25 ./mvnw clean verify` (Testcontainers needs Docker).
- Run: `JAVA_HOME=/usr/lib/jvm/openjdk-25 ./mvnw spring-boot:run` (Boot starts and stops `compose.yaml` itself).
- Requests: `brewery.http` (IntelliJ HTTP Client, from Phase 1).

## Stack

Java 25, Spring Boot 4.1.2, Spring Modulith 2.1.2 (BOM), Spring Data JDBC, Flyway, PostgreSQL 18, Testcontainers 2.x, Maven Wrapper.

## Conventions

- One app, one Maven project. Root package `com.nazjara.brewery`; every direct sub-package is an application module.
- Module root package = its API (service facade, event and DTO records). Entities, repositories and controllers go in sub-packages (`internal`, `web`), which other modules must not use.
- Cross-module references go through the API or events, never internal types. `ModularityTests` (`ApplicationModules.verify()`, from Phase 1) must stay green.
- Aggregates reference other modules' aggregates by id only.
- Schema changes go through Flyway migrations in `src/main/resources/db/migration`; no schema generation.
- Records for DTOs and events. Lombok only for `@Slf4j` / `@RequiredArgsConstructor`.
- Javadoc on every class and public method in API, service, config and bootstrap code: explain what Modulith does under the hood and name the microservices mechanism it replaces.
- Keep the domain thin; each phase shows a Modulith feature, not business logic. Design the idiomatic Modulith way; do not mirror the old microservices' shape.
- No deprecated APIs: the compiler is configured with `showDeprecation`/`showWarnings`; a build must produce no deprecation warnings.
- Test containers use the same image as `compose.yaml` (`postgres:18`).
