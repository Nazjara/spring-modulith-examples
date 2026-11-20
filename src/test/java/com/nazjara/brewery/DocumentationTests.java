package com.nazjara.brewery;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Generates the module documentation from the same bytecode model that {@code ModularityTests} verifies, so it is
 * rebuilt on every test run and cannot drift from the code.
 * <p>
 * {@link Documenter} writes into {@code target/spring-modulith-docs}: {@code components.puml} (all modules and their
 * dependencies), {@code module-<name>.puml} (one module and its neighbours), {@code module-<name>.adoc} (the module
 * canvas: components, events published and listened to, aggregates, properties) and {@code all-docs.adoc} (one page
 * including all of them). Module and type descriptions come from the Javadoc that the {@code spring-modulith-apt}
 * annotation processor extracts at compile time. It also writes {@code META-INF/spring-modulith/application-modules.json}
 * into {@code target/classes}, which the runtime reads instead of scanning the modules at startup.
 * <p>
 * The microservices version had no generated architecture documentation: the service graph existed only in diagrams
 * drawn by hand or in Zipkin's dependency view, built from traffic at runtime.
 */
class DocumentationTests {

    @Test
    void writeDocumentation() {
        new Documenter(ApplicationModules.of(BreweryApplication.class)).writeDocumentation();
    }
}
