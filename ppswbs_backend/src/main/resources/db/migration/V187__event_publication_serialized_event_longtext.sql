-- V20 declares event_publication.serialized_event as LONGTEXT, but the shared dev schema ended up with
-- VARCHAR(255) (table created before V20 / outside Flyway). Group ticket cancel publishes an event whose
-- serialized form is longer than 255 characters and failed with "Data too long".
-- Re-asserting LONGTEXT is a no-op on schemas that already match V20.
ALTER TABLE event_publication
    MODIFY COLUMN serialized_event LONGTEXT NOT NULL;
