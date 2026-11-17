package com.nazjara.brewery;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Keeps the Event Publication Registry moving: resubmits failed publications and purges completed ones.
 * <p>
 * The registry stores one {@code event_publication} row per event and listener in the publishing transaction and
 * marks it {@code COMPLETED} or {@code FAILED} after the listener ran; the staleness monitor
 * ({@code spring.modulith.events.staleness.*}) marks rows stuck in {@code PUBLISHED}, {@code PROCESSING} or
 * {@code RESUBMITTED} as {@code FAILED}. Nothing in Modulith retries a failed row on its own, so this class does:
 * every run hands the oldest {@code FAILED} rows to {@link FailedEventPublications#resubmit}, which claims each row
 * with a conditional update ({@code RESUBMITTED}) before invoking the listener again. Every instance runs the same
 * schedule; the claim makes sure only one of them re-runs a given row. The runs are deliberately not transactional:
 * an enclosing transaction would keep the claimed rows locked while the asynchronously invoked listener tries to mark
 * them {@code PROCESSING}.
 * <p>
 * There is no retry limit: an event whose listener can never succeed is resubmitted on every run, and enough of them
 * use up {@code maxInFlight} so newer failures wait behind them (see README, Phase 4). In the microservices version
 * the JMS broker redelivered failed messages and moved them to a dead-letter queue after a maximum number of attempts.
 * <p>
 * {@code @EnableScheduling} is declared here although {@code spring-modulith-moments} already enables it, so these
 * tasks and the staleness monitor do not depend on Moments staying enabled.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(EventPublicationMaintenanceProperties.class)
@RequiredArgsConstructor
class EventPublicationMaintenance implements SchedulingConfigurer {

    private final FailedEventPublications failedPublications;
    private final CompletedEventPublications completedPublications;
    private final EventPublicationMaintenanceProperties properties;

    /**
     * Registers the resubmission and purge tasks as fixed-delay tasks, so a slow run never overlaps the next one.
     *
     * @param registrar the scheduler's task registrar
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        var resubmission = properties.resubmission();
        var options = ResubmissionOptions.defaults()
                .withMinAge(resubmission.minAge())
                .withMaxInFlight(resubmission.maxInFlight());
        registrar.addFixedDelayTask(() -> failedPublications.resubmit(options), resubmission.interval());

        var purge = properties.purge();
        registrar.addFixedDelayTask(
                () -> completedPublications.deletePublicationsOlderThan(purge.retention()), purge.interval());
    }
}
