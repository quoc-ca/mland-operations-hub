-- OTA dedupe/fingerprint values are bounded String hashes in the JPA model.
ALTER TABLE ota_inbound_events MODIFY COLUMN dedupe_key VARCHAR(64) NOT NULL;
ALTER TABLE ota_inbound_events MODIFY COLUMN payload_fingerprint VARCHAR(64) NOT NULL;
ALTER TABLE ota_readiness_assessments MODIFY COLUMN mapping_fingerprint VARCHAR(64) NOT NULL;
ALTER TABLE ota_reconciliation_decisions MODIFY COLUMN idempotency_key_hash VARCHAR(64) NOT NULL;
ALTER TABLE ota_reconciliation_decisions MODIFY COLUMN request_fingerprint VARCHAR(64) NOT NULL;
