-- ==============================================================================
-- V112__notification_original_request_idempotency.sql
-- Feature FE-007: Notification Management — Corrective Migration for
-- Original-Only Request Idempotency & Derived Lineage Normalization (T033)
-- ==============================================================================

-- 1. Normalize existing derived-request chains so every derived request references its canonical root directly
UPDATE notification_request child
JOIN notification_request parent ON child.original_request_id = parent.id
SET child.original_request_id = parent.original_request_id
WHERE parent.original_request_id IS NOT NULL;

-- 2. Normalize historical duplicate originals for the same business identity to the earliest canonical root
-- Preserves historical audit and delivery records without deleting any row.
UPDATE notification_request nr
JOIN (
    SELECT business_event_reference, template_code, recipient_role, recipient_address_fingerprint, MIN(id) AS canonical_root_id
    FROM notification_request
    WHERE original_request_id IS NULL
    GROUP BY business_event_reference, template_code, recipient_role, recipient_address_fingerprint
    HAVING COUNT(*) > 1
) dup ON nr.business_event_reference = dup.business_event_reference
     AND nr.template_code = dup.template_code
     AND nr.recipient_role = dup.recipient_role
     AND nr.recipient_address_fingerprint = dup.recipient_address_fingerprint
     AND nr.original_request_id IS NULL
     AND nr.id <> dup.canonical_root_id
SET nr.original_request_id = dup.canonical_root_id;

-- 3. Drop legacy unique key that improperly constrained derived requests
ALTER TABLE notification_request DROP INDEX uq_nr_idempotency;

-- 4. Add generated stored column 'original_idempotency_scope'
-- Evaluates to 1 when original_request_id IS NULL (original request); NULL for all derived requests.
ALTER TABLE notification_request
    ADD COLUMN original_idempotency_scope TINYINT
    GENERATED ALWAYS AS (CASE WHEN original_request_id IS NULL THEN 1 ELSE NULL END) STORED;

-- 5. Add new original-only unique constraint
-- Permits only one original request per business identity (scope=1).
-- Permits unlimited derived requests (scope=NULL) since SQL unique constraints ignore duplicate NULLs.
ALTER TABLE notification_request
    ADD UNIQUE KEY uq_nr_original_idempotency (business_event_reference, template_code, recipient_role, recipient_address_fingerprint, original_idempotency_scope);
