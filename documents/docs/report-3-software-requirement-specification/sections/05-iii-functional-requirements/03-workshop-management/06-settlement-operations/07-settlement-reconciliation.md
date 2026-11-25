#### 3.6.7 Settlement Reconciliation

This screen allows Managers to:

- List the bank transfer, VNPay and booking-time payment collections of one branch and working day.
- Narrow the list by payment method and by collection state.
- Compare the rows with the bank statement and VNPay Merchant Admin outside the system.
- See the total of confirmed collections for the chosen filter.

The list is read-only: it records no "reconciled" mark and changes nothing. Only confirmed rows count in the total; unverified, late, voided and excess rows are listed but not counted. A non-Manager sees "Chỉ Manager mới mở được danh sách đối soát." The filters are kept in the page URL.

| Field Name | Description |
| --- | --- |
| "Mã chi nhánh" / "Ngày làm việc" | Required numeric branch identifier and working day; "Xem các khoản thu" is enabled only when both are set. |
| "Phương thức" | "Tất cả", "Chuyển khoản", "VNPay" or "Thanh toán lúc đặt chỗ". |
| "Trạng thái" | "Tất cả", "Đã xác nhận", "Chờ xác minh", "Đã hủy bỏ" or "Thu thừa". |
| "Tổng đã xác nhận" | Sum of the confirmed rows shown, with a note on which rows are excluded. |
| "Vé" / "Nhóm" | Ticket code or codes and booking group code of the row. |
| "Phương thức" column | Method; a booking-time payment is marked "thanh toán cả nhóm" and lists its branches when it covers sessions at several branches. |
| "Số tiền" | Amount, with "thu thừa {amount}" and the related refund reference when there is an excess. |
| "Trạng thái" column | Collection state; an unverified booking-time payment is marked "chưa xác minh", a late payment "thanh toán muộn đang được xem xét". |
| "Mã tham chiếu" / "Người xác nhận" / "Thời gian" / "Ghi chú" | Transaction reference, the confirming user ("Hệ thống (IPN)" for a booking-time payment), confirmation or creation time and note. |
| Multi-branch warning | A booking-time payment with sessions at several branches appears once under each branch; branch totals must not be added together. |
| Unavailable message | When booking-time payments cannot be read, no partial list is shown; the Manager can retry or filter out booking-time payments. |
