package com.nazjara.brewery;

import java.util.List;

import lombok.experimental.UtilityClass;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Reads the Event Publication Registry's table for assertions. The registry's API lists completed publications only,
 * so tests that wait for a failed or retried publication query {@code event_publication} directly.
 */
@UtilityClass
public class EventPublicationRows {

    /**
     * Status and attempt count of one {@code event_publication} row.
     *
     * @param status             registry status, e.g. {@code COMPLETED}
     * @param completionAttempts 1 for the first delivery, plus one per resubmission
     */
    public record Row(String status, int completionAttempts) {

        public static final Row COMPLETED_FIRST_TIME = new Row("COMPLETED", 1);
    }

    /**
     * Returns the rows of all publications of the given event type for one order, oldest first.
     *
     * @param jdbc      template on the application's database
     * @param eventType event class; its events must have an {@code orderId} property
     * @param orderId   order the events refer to
     * @return one row per publication and listener
     */
    public List<Row> of(JdbcTemplate jdbc, Class<?> eventType, long orderId) {
        return jdbc.query("""
                        SELECT status, completion_attempts
                        FROM event_publication
                        WHERE event_type = ? AND (serialized_event::jsonb ->> 'orderId')::bigint = ?
                        ORDER BY publication_date""",
                (resultSet, rowNum) -> new Row(resultSet.getString("status"), resultSet.getInt("completion_attempts")),
                eventType.getName(), orderId);
    }
}
