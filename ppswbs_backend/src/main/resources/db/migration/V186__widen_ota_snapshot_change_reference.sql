-- workshop_session_ota_snapshots.change_reference stores the Operations command operation reference
-- (TicketCancellationService, OtaTicketRevocationService, TicketSessionReplacementService, facade).
-- Group ticket cancel sends CANCEL-<ticketCode>-<Idempotency-Key UUID> (74 chars) which overflowed
-- VARCHAR(64) (V19) and failed with "Data too long". 128 matches facade_command_idempotency_records (V185).
-- The column is not part of any index (uq_ota_snapshot_session_version uses session_reference, source_version).
ALTER TABLE workshop_session_ota_snapshots
    MODIFY COLUMN change_reference VARCHAR(128) NOT NULL;
