#### 3.7.4 OTA Finance

This screen allows Managers to:

- Load the financial evidence of one OTA booking by its channel booking ID.
- See the customer payment state and the payout state of the booking.
- Read the evidence ledger of recorded entries.
- After evidence is loaded, record a reconciliation stage (payout, variance, dispute, adjustment or reversal) with its evidence.

Only a Manager can open the screen; signed-out users go to sign-in and other roles to the not-found page. It is marked "CHỈ QUẢN LÝ". Provider credentials are never shown. Amounts are recorded in VND.

| Field Name | Description |
| --- | --- |
| Mã đặt chỗ trên kênh / Tải chứng từ | Identifier of the OTA booking; loads its evidence. Changing the ID clears the evidence shown. |
| Thanh toán của khách | Customer payment state of the booking. |
| Trạng thái thanh toán cho đối tác | Channel payout state of the booking. |
| Sổ chứng từ | Number of entries and, per entry, evidence type or kind, amount and currency, reference and recorded time. |
| Loại | PAYOUT, VARIANCE, DISPUTE, ADJUSTMENT or REVERSAL. |
| Loại chứng từ | ELIGIBILITY, INVOICE, REMITTANCE, BANK_RECEIPT, ADJUSTMENT, REVERSAL, VARIANCE or DISPUTE. |
| Số tiền (VND) / Mã tham chiếu | Required amount and evidence reference. |
| Lý do / Thời điểm phát sinh | Required reason and the date and time the stage occurred. |
| Ghi nhận chứng từ | Records the stage; shows "Financial evidence recorded." and the updated evidence, or "Booking ID, amount, reference, reason, and occurred time are required." when a field is missing. |
