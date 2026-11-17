-- OtaControlOutboxCommand stores bounded String references, not fixed-width CHAR values.
ALTER TABLE ota_control_outbox MODIFY COLUMN operation_reference VARCHAR(36) NOT NULL;
ALTER TABLE ota_control_outbox MODIFY COLUMN idempotency_key_hash VARCHAR(64) NOT NULL;
ALTER TABLE ota_control_outbox MODIFY COLUMN request_fingerprint VARCHAR(64) NOT NULL;
ALTER TABLE ota_control_outbox MODIFY COLUMN correlation_reference VARCHAR(36) NOT NULL;
