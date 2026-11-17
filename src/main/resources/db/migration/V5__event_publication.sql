-- Event Publication Registry (Spring Modulith JDBC schema v2, PostgreSQL). Shared by all modules: one row per
-- event and listener, written in the publishing transaction.
-- Copy of org/springframework/modulith/events/jdbc/schemas/v2/schema-postgresql.sql from spring-modulith-events-jdbc
-- 2.1.2. On every Spring Modulith upgrade, diff that file in the new version against this table and add a migration
-- for any change.
CREATE TABLE event_publication
(
    id                     UUID                     NOT NULL PRIMARY KEY,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE
);

CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
