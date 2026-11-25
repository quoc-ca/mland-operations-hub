#### 3.4.5 Late Payment Reviews

This screen allows Managers to:

- See the late payments that were not confirmed automatically and wait for a Manager decision.
- See each payment's booking code, amount, failed conditions and answer deadline.
- Choose a standard decision reason and add a note or transaction reference.
- Confirm the late payment when its conditions allow it, or open a 100% refund.

The queue is ordered by the earliest session start. A note is required when the reason is "OTHER_REQUIRES_NOTE".

| Field Name | Description |
| --- | --- |
| Lý do quyết định (chuẩn hóa) | Required reason: provider confirmed the late deposit, customer presented a valid receipt, payment dispute sent to finance, or other reason. |
| Ghi chú chi tiết / mã tham chiếu giao dịch | Note or bank transaction reference; optional except for the other reason. |
| Tải lại hàng đợi | Reloads the queue of reviews waiting for a Manager. |
| Review item | Booking code, "Số tiền", "Điều kiện không đạt" and "Phải trả lời trước". |
| Xác nhận thanh toán về trễ | Confirms the payment; enabled only when the server allows confirmation for this review. |
| Mở hoàn tiền 100% | Opens a 100% refund of the payment through workshop settlement. |
| Messages | "Đã xác nhận thanh toán về trễ.", "Đã mở yêu cầu hoàn 100%.", or "Không có thanh toán về trễ nào đang chờ Manager." |
