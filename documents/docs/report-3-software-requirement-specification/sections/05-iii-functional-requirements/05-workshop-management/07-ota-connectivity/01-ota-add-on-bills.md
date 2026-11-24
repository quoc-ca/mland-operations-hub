#### 5.7.1 OTA Add-on Bills

This screen allows Staff and Managers to:

- Create a shop add-on quote for an OTA booking at the workshop: free laser engraving or a paid catalogue option.
- Record the customer's acceptance of a quote with an acceptance reference.
- Create a direct Mland bill for an accepted paid option, separate from the OTA checkout and channel settlement.
- Collect the bill by VNPay and follow its status until it is paid.
- As a Manager, request a refund of a paid add-on bill.

The screen requires sign-in. It can be opened with the channel booking ID prefilled from a link. Laser engraving is free for OTA customers and never creates a bill. The bill status is refreshed automatically every few seconds until it is paid. The refund form is shown under a paid bill, but only a Manager's request is accepted by the server, which also decides the refundable amount. Only one refund can be open at a time.

| Field Name | Description |
| --- | --- |
| Channel booking ID | Required identifier of the OTA booking. |
| Add-on type | "Laser engraving (free)" or "Paid option". |
| Catalogue add-on reference / Description | Paid option: required catalogue add-on reference. Laser engraving: required description of the engraving. |
| Create quote | Records the quote at the Mland shop; the quote shows type, status, amount and quote ID. |
| Acceptance reference / Record acceptance | While the quote is "QUOTED": a required reference (for example signed at the counter) records the customer's acceptance. |
| Create direct Mland bill | Shown for an accepted paid option; links a settlement bill to the quote. |
| Bill status | Bill reference and "Trạng thái": the lifecycle status, or "Đã thanh toán (PAID)" with a completion message. |
| "Thu tiền (VNPay)" | Creates a VNPay payment link while the bill is unpaid; "Mở cổng thanh toán VNPay" opens it until "Hết hạn lúc" passes. |
| "Hoàn tiền add-on" | Paid bills only. Lists earlier refunds (amount, status, reason); "Số tiền hoàn (VND)" as a whole amount and a required "Lý do", then "Tạo yêu cầu hoàn tiền". While a refund is pending, a note says to wait before creating another. |
