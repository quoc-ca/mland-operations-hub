-- Preserve Booking commitments while moving the module boundary to opaque session UUIDs.
-- Preflight runs before any table alteration: data without an Operations reference must be
-- resolved by an explicitly approved migration plan, never guessed from a numeric ID.
DELIMITER //
CREATE PROCEDURE assert_booking_sessions_have_uuid_mapping()
BEGIN
    IF EXISTS (
        SELECT 1
          FROM booking_session_commitments bsc
          LEFT JOIN workshop_sessions ws ON ws.id = bsc.session_id
         WHERE ws.reference IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Booking session migration stopped: one or more numeric session IDs have no Operations UUID reference';
    END IF;
END//
DELIMITER ;

CALL assert_booking_sessions_have_uuid_mapping();
DROP PROCEDURE assert_booking_sessions_have_uuid_mapping;

ALTER TABLE booking_session_commitments
    ADD COLUMN session_reference VARCHAR(36) NULL AFTER session_id;

UPDATE booking_session_commitments bsc
JOIN workshop_sessions ws ON ws.id = bsc.session_id
   SET bsc.session_reference = ws.reference;

ALTER TABLE booking_session_commitments
    MODIFY COLUMN session_reference VARCHAR(36) NOT NULL,
    DROP FOREIGN KEY fk_bsc_location,
    DROP INDEX uq_bsc_booking_session,
    DROP INDEX idx_bsc_session,
    DROP COLUMN session_id,
    ADD UNIQUE KEY uq_bsc_booking_session (booking_id, session_reference),
    ADD KEY idx_bsc_session_reference (session_reference);

ALTER TABLE booking_ticket_session_entitlements
    ADD COLUMN session_reference VARCHAR(36) NULL AFTER session_id;

UPDATE booking_ticket_session_entitlements btse
JOIN booking_session_commitments bsc ON bsc.id = btse.session_commitment_id
   SET btse.session_reference = bsc.session_reference;

ALTER TABLE booking_ticket_session_entitlements
    MODIFY COLUMN session_reference VARCHAR(36) NOT NULL,
    DROP INDEX idx_btse_session,
    DROP COLUMN session_id,
    ADD KEY idx_btse_session_reference (session_reference);

-- location_id on Booking commitments remains a scalar canonical location snapshot.
-- The old FK crossed the module boundary into Operations-owned persistence.
