package com.nazjara.brewery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the brewery modular monolith.
 * <p>
 * Spring Modulith treats every direct sub-package of this class's package ({@code com.nazjara.brewery}) as an
 * application module. Each module's top-level package is its public API; its sub-packages are internal. In the
 * microservices version every module was a separate Spring Boot process behind a gateway, discovery and config
 * server. Here they share one process, one {@code ApplicationContext} and one database, and the boundaries between
 * them are checked by {@code ApplicationModules.verify()} instead of by the network.
 */
@SpringBootApplication
public class BreweryApplication {

    /**
     * Starts the application. With {@code spring-boot-docker-compose} on the classpath, Boot starts the services
     * in {@code compose.yaml} and connects to them before the context refreshes.
     *
     * @param args command-line arguments
     */
    static void main(String[] args) {
        SpringApplication.run(BreweryApplication.class, args);
    }
}
