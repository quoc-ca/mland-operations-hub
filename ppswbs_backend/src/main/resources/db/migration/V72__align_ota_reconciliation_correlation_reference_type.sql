ALTER TABLE ota_reconciliation_decisions
    MODIFY COLUMN correlation_reference VARCHAR(36) NOT NULL;
