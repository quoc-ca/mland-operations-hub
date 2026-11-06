-- OperationsAuditEvent.outcome is a free-form bounded audit value in the entity,
-- not a MySQL enum. Preserve existing SUCCESS/FAILURE values while removing the
-- schema type mismatch that prevents Hibernate validation.
ALTER TABLE operations_audit_events
    MODIFY COLUMN outcome VARCHAR(32) NOT NULL;
