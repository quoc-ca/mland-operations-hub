-- Flyway migration V192: Add SAVED status to ring_designs enum and modernize lifecycle
ALTER TABLE ring_designs
    MODIFY COLUMN status ENUM('DRAFT','SAVED','VALIDATED','ATTACHED_TO_BOOKING','LOCKED','ARCHIVED') NOT NULL DEFAULT 'DRAFT';

UPDATE ring_designs
SET status = 'SAVED'
WHERE status = 'VALIDATED';
