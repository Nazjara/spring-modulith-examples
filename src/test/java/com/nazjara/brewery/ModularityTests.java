package com.nazjara.brewery;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Checks the module structure of the brewery from its bytecode, without starting Spring.
 */
@Slf4j
class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(BreweryApplication.class);

    @Test
    void modulesRespectBoundariesAndAllowedDependencies() {
        log.info("Detected modules:\n{}", MODULES);

        MODULES.verify();
    }

    @Test
    void dependencyGraphMatchesTheDeclaredDesign() {
        var catalog = directDependenciesOf("catalog");
        var inventory = directDependenciesOf("inventory");
        var order = directDependenciesOf("order");

        assertThat(catalog).isEmpty();
        assertThat(inventory).isEmpty();
        assertThat(order).containsExactly("catalog");
    }

    private static String[] directDependenciesOf(String moduleName) {
        return MODULES.getModuleByName(moduleName).orElseThrow()
                .getDirectDependencies(MODULES)
                .uniqueModules()
                .map(module -> module.getIdentifier().toString())
                .toArray(String[]::new);
    }
}
