package com.nazjara.brewery;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of {@link EventPublicationMaintenance}, bound from {@code brewery.events.*}.
 *
 * @param resubmission when and how many failed publications are resubmitted
 * @param purge        when and which completed publications are deleted
 */
@ConfigurationProperties("brewery.events")
record EventPublicationMaintenanceProperties(Resubmission resubmission, Purge purge) {

    /**
     * Resubmission of {@code FAILED} publications.
     *
     * @param interval    delay between the end of one run and the start of the next
     * @param minAge      how long ago a publication must have been first published to be resubmitted; measured from
     *                    the original publication date, not from the last failure
     * @param maxInFlight how many resubmitted publications may wait to start at once, per instance; a run claims only
     *                    the remaining headroom
     */
    record Resubmission(Duration interval, Duration minAge, int maxInFlight) {
    }

    /**
     * Deletion of {@code COMPLETED} publications.
     *
     * @param interval  delay between the end of one run and the start of the next
     * @param retention how long a completed publication is kept after completion
     */
    record Purge(Duration interval, Duration retention) {
    }
}
