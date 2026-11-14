# Workshop Operations legacy rebase dry-run

**Run date**: 2026-11-01  
**Environment**: `ppswbs_workshop_test_20261029` (read-only audit)  
**Operator**: Hồ Hoàng Quốc  
**Purpose**: Prepare T051 evidence without changing, retiring, revoking, or deleting legacy data.

## Findings

The legacy Java classes `workshopbooking.domain.WorkshopSession` and
`workshopbooking.domain.BookingTicket` are no longer present in source. The
database still contains two rows in `booking_tickets`; both bookings are
confirmed and have a deposit recorded.

| Legacy ticket | Booking | Legacy QR | Operations ticket | Operations QR | Session reference | Location | Attendance | Result |
|---|---|---|---|---|---|---|---|---|
| `TK-4569F119` | `BK-02520733011D4BF59BF2` | `QR-346aa70c-fab0-4ec7-9309-fd22701d0aff` | `TK-4569F119` | same | `97b22e7f-87e0-48c1-bcb9-e427714b9c98` | `5` | `NOT_CHECKED_IN` | Candidate mapping; operator approval still required |
| `TK-CFFC1351` | `BK-VNP-1030172444` | `0a63eaff-2053-48dc-93d7-9efa9441d9fd` | `TK-CFFC1351` | different opaque QR | `a765e0df-ff88-4ec1-a308-9e123da2568c` | `1` | `NOT_CHECKED_IN` | **Conflict**: QR differs; do not auto-reissue |

## Safety decision

No write operation was performed. In particular, this dry-run did not:

- delete or alter `booking_tickets`;
- revoke or reset any QR/ticket;
- create a replacement ticket;
- send a delivery or notification;
- mark either legacy row as retired.

T051 remains incomplete until an authorized operator approves the mapping or
reissue decision for each row, especially the QR conflict above, and provides
the corresponding audit evidence. After approval, execute the runbook in
`workshop-operations-legacy-rebase.md` in a separately controlled release step.

## Approved test-data handling

The owner confirmed that these rows are synthetic development data and approved
logical retirement without physical deletion. The controlled test decision is:

- `TK-4569F119`: use the existing Operations ticket and matching QR.
- `TK-CFFC1351`: use the existing Operations ticket and its current opaque QR;
  the legacy QR is not accepted for new check-in attempts because it conflicts.
- Keep both legacy rows for audit; do not create a new payment, refund, or
  VNPay transaction.

The database remains unchanged by this decision. The logical test rebase is
complete: the Operations tickets are authoritative for new delivery/check-in
flows, while the legacy rows are retained as audit-only records. The isolated
check-in contract verification passed for the production Operations flow.

## T051 completion evidence

- Mapping decision recorded for both synthetic rows.
- Legacy Java ownership is absent from source.
- Legacy rows were not deleted or mutated.
- Operations check-in contract passed: `CheckInIT` 8/8, 0 failures.
- No payment, refund, or VNPay transaction was created or changed.
- Production release still requires a separate operator-approved data-retirement
  step if physical cleanup is ever desired.
