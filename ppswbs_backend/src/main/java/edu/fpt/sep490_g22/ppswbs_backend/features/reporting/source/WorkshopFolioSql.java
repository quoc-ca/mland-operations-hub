package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source;

/**
 * The FE-03 folios FE-10 counts as workshop revenue, shared by the KPI and the trend queries so both read the same
 * facts. A folio is fully paid when 003.3 finalizes it (balance zero, nothing pending). Its revenue is the frozen
 * post-promotion amount (the deposit is inside it) plus confirmed charges. The fully-paid time is the FOLIO_FINALIZED
 * audit, else the latest confirmed collection or verified final payment, else the freeze time.
 *
 * <p>The branch of a folio, in order: a ticket folio of a booking group (003.3 T093: the folio reference is the
 * 003.1 ticket code) takes the session of its Operations ticket; an older direct-booking folio takes the first
 * committed session of the booking, else the session of one of its tickets.
 */
final class WorkshopFolioSql {
    private WorkshopFolioSql() { }

    static final String FINALIZED_FOLIOS = """
        SELECT b.currency_code AS currency_code,
               b.post_promotion_amount_vnd + COALESCE((SELECT SUM(ch.amount_vnd) FROM settlement_charges ch
                    WHERE ch.bill_reference = b.bill_reference AND ch.lifecycle_status = 'CONFIRMED'), 0) AS total_vnd,
               COALESCE(
                    (SELECT MIN(ws.location_id) FROM ticket_bookings tb
                     JOIN attendance_tickets t ON t.reference = tb.operations_ticket_reference
                     JOIN ticket_session_entitlements e ON e.ticket_id = t.id
                     JOIN workshop_sessions ws ON ws.id = e.session_id
                     WHERE tb.ticket_code = b.booking_reference),
                    (SELECT bsc.location_id FROM booking_session_commitments bsc
                     JOIN workshop_bookings wb ON wb.id = bsc.booking_id
                     WHERE wb.booking_code = b.booking_reference ORDER BY bsc.ordinal LIMIT 1),
                    (SELECT MIN(ws.location_id) FROM attendance_tickets t
                     JOIN ticket_session_entitlements e ON e.ticket_id = t.id
                     JOIN workshop_sessions ws ON ws.id = e.session_id
                     WHERE t.booking_reference = b.booking_reference)) AS location_id,
               COALESCE(
                    (SELECT MAX(a.created_at) FROM settlement_business_audits a
                     WHERE a.aggregate_type = 'SETTLEMENT_FINAL_BILL' AND a.action_code = 'FOLIO_FINALIZED'
                       AND a.aggregate_reference IN (b.booking_reference, b.bill_reference)),
                    GREATEST(
                        COALESCE((SELECT MAX(col.confirmed_at) FROM settlement_folio_collections col
                                  WHERE col.bill_reference = b.bill_reference AND col.status = 'CONFIRMED'), b.frozen_at),
                        COALESCE((SELECT MAX(ev.created_at) FROM settlement_evidence_conclusions ev
                                  WHERE ev.booking_reference = b.booking_reference
                                    AND ev.conclusion = 'FINAL_PAYMENT_VERIFIED'), b.frozen_at))) AS fully_paid_at
        FROM settlement_final_bills b
        WHERE b.lifecycle_status = 'FINALIZED'
        """;
}
