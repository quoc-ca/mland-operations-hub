#### 5.7.4 OTA Finance

This screen allows Managers to:

- Load the financial evidence of one OTA booking by its channel booking ID.
- See the customer payment state and the payout state of the booking.
- Read the evidence ledger of recorded entries.
- Record a reconciliation stage (payout, variance, dispute, adjustment or reversal) with its evidence.

The screen requires sign-in and is marked "MANAGER ONLY"; the backend decides permissions. Provider credentials are never shown. Amounts are recorded in VND. The labels on this screen are in English.

| Field Name | Description |
| --- | --- |
| Channel booking ID / Load evidence | Identifier of the OTA booking; loads its evidence. Changing the ID clears the evidence shown. |
| Customer payment | Customer payment state of the booking. |
| Payout state | Channel payout state of the booking. |
| Evidence ledger | Number of entries and, per entry, evidence type or kind, amount and currency, reference and recorded time. |
| Kind | PAYOUT, VARIANCE, DISPUTE, ADJUSTMENT or REVERSAL. |
| Evidence type | ELIGIBILITY, INVOICE, REMITTANCE, BANK_RECEIPT, ADJUSTMENT, REVERSAL, VARIANCE or DISPUTE. |
| Amount (VND) / Reference | Required amount and evidence reference. |
| Reason / Occurred at | Required reason and the date and time the stage occurred. |
| Record evidence | Records the stage; shows "Financial evidence recorded." and the updated evidence, or "Booking ID, amount, reference, reason, and occurred time are required." when a field is missing. |
