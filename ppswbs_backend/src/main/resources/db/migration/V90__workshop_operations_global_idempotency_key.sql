-- REST Idempotency-Key is a client-visible operation reference and must be
-- unique across every Operations command, not only within one facade action.
ALTER TABLE facade_command_idempotency_records
    ADD UNIQUE KEY uq_fci_operation_reference (operation_reference);
