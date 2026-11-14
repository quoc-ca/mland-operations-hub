-- 003.3 T107 (FR-034, FR-035, FR-049): signed VNPay evidence that settled a ticket-folio VNPAY collection was stored
-- as FINAL_PAYMENT_VERIFIED, so FolioBalanceService and the refund caps counted that money twice: once through the
-- CONFIRMED collection and once through the evidence row. From now on such evidence is FOLIO_VNPAY_VERIFIED and is
-- counted through its collection only. This moves the rows already written; legacy booking-wide evidence (no VNPAY
-- folio collection with the same merchant reference) keeps FINAL_PAYMENT_VERIFIED.
UPDATE settlement_evidence_conclusions e
    JOIN settlement_folio_collections c
      ON c.method = 'VNPAY'
     AND c.external_reference = e.provider_transaction_reference
     AND c.booking_reference = e.booking_reference
   SET e.conclusion = 'FOLIO_VNPAY_VERIFIED'
 WHERE e.conclusion = 'FINAL_PAYMENT_VERIFIED';
