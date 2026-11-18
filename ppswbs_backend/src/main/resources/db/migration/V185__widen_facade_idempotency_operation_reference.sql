-- Group ticket cancellation builds operation references such as
-- CANCEL-<ticketCode>-<Idempotency-Key UUID> (74 chars), which exceeded VARCHAR(64) and made
-- every cancel fail. 128 matches the settlement-side operation reference columns.
-- uq_fci_action_op_ref (facade_action 255 + operation_reference 128 = 383 chars * 4 bytes = 1532 B)
-- and uq_fci_operation_reference (512 B) stay within the InnoDB 3072-byte index key limit.
ALTER TABLE facade_command_idempotency_records
    MODIFY COLUMN operation_reference VARCHAR(128) NOT NULL;
