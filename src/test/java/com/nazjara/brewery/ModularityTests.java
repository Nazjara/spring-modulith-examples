package com.nazjara.brewery;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.VerificationOptions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks the module structure of the brewery from its bytecode, without starting Spring.
 */
@Slf4j
class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(BreweryApplication.class);

    private static final DescribedPredicate<JavaMethod> PUBLISH_EVENTS = DescribedPredicate.describe(
            "publish application events",
            method -> method.getMethodCallsFromSelf().stream().anyMatch(call ->
                    call.getName().equals("publishEvent")
                            && call.getTargetOwner().isAssignableTo(ApplicationEventPublisher.class)));

    private static final ArchCondition<JavaMethod> BE_TRANSACTIONAL =
            new ArchCondition<>("be @Transactional (on the method or its class)") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    var transactional = method.isMetaAnnotatedWith(Transactional.class)
                            || method.getOwner().isMetaAnnotatedWith(Transactional.class);
                    events.add(new SimpleConditionEvent(method, transactional, ("%s publishes events but is not "
                            + "@Transactional: @ApplicationModuleListener skips events published without an active "
                            + "transaction, logging only at debug level %s")
                            .formatted(method.getFullName(), method.getSourceCodeLocation())));
                }
            };

    private static final ArchRule EVENTS_ARE_PUBLISHED_IN_TRANSACTIONS = methods()
            .that(PUBLISH_EVENTS)
            .should(BE_TRANSACTIONAL);

    @Test
    void modulesRespectBoundariesAndAllowedDependencies() {
        log.info("Detected modules:\n{}", MODULES);

        MODULES.verify(VerificationOptions.defaults().andAdditionalVerifications(EVENTS_ARE_PUBLISHED_IN_TRANSACTIONS));
    }

    @Test
    void dependencyGraphMatchesTheDeclaredDesign() {
        var catalog = directDependenciesOf("catalog");
        var inventory = directDependenciesOf("inventory");
        var order = directDependenciesOf("order");

        assertThat(catalog).isEmpty();
        assertThat(inventory).containsExactly("order");
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
