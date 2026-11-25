#### 3.6.1 Settlement Workspace

{{r3-settlement-workspace width=100% align=center}}

This screen allows Staff and Managers to:

- Open the folio of one ticket by its ticket code, or directly from a link such as the attendance roster.
- See the ticket's total, paid, pending verification and balance due amounts, with its collections and folio lines.
- Record a counter collection by VNPay (shown as a QR code the customer scans), cash or bank transfer.
- Add catalogue add-ons or a one-off custom charge that the customer agreed to, while the folio is open.
- Finalize the folio once the balance due is zero and no collection is still pending verification.
- As a Manager, confirm or fail pending collections, void lines and collections, reverse cash of a closed cash day, record or close a debt, allow handover before payment, and record an absorbed undercharge on a finalized folio.

The backend decides access: Staff act only on tickets of their own branch and sessions they are assigned to. All amounts and statuses come from the server. Every void, fail, reversal, debt, handover and undercharge action asks for a reason that is written to the audit log.

| Field Name | Description |
| --- | --- |
| "Mã vé" / "Mở folio" | Ticket code input; opens the folio and keeps the ticket code in the page URL. "Không tìm thấy folio cho vé này." when there is none. |
| Folio summary | Ticket code, folio status ("Đang mở", "Đã chốt", "Đã đóng"), "Số còn nợ", "Tổng", "Đã thanh toán" and "Chờ xác minh". |
| Folio actions | "Chốt folio" for all; Manager only: "Ghi nhận công nợ", "Đóng công nợ", "Cho phép giao hàng" (while a balance is due) and "Ghi nhận khoản thiếu Mland chịu" (on a finalized folio). |
| "Các khoản thu" table | Collections ordered booking-time payment, VNPay, cash, bank transfer, with method, amount (and change returned), status. A booking-time allocation cannot be changed (the screen still shows 'Hủy bỏ' on it to a Manager). Staff see "Manager xác minh" on a pending row. |
| Collection row actions | Manager only: "Xác nhận chuyển khoản" with the bank transaction reference, "Đánh dấu thất bại" for a pending part, "Hủy bỏ" for a confirmed collection while the folio is open and its cash day is not closed, "Đảo bút toán tiền mặt" for cash of a closed day (posts a negative cash entry on the current working day). |
| Folio lines | Line reference, description, amount and status; Manager can "Hủy bỏ dòng" while the folio is open. |
| "Ghi nhận khoản thu" | Method (VNPay, "Tiền mặt", "Chuyển khoản"), "Số tiền (VND)" prefilled with the unassigned balance, "Tiền mặt nhận (VND)" for cash, "Mã giao dịch của khách" for transfer. A warning appears when a VNPay part, or the remainder left for VNPay, would be under 5,000 VND. |
| VNPay checkout | After a VNPay collection: "Đã tạo giao dịch VNPay" with expiry time, a QR code for the customer's phone and "Mở trang thanh toán VNPay". The part counts only after VNPay's signed confirmation. |
| "Add-on và phụ phí" | Open folio only. Tick catalogue add-ons and "Khách đã đồng ý" then "Thêm add-on đã chọn"; or a custom charge with "Mô tả", amount and "Lý do", then "Thêm phụ phí". Refused while the session has not started. |
| Manager extras | "Add-on đã bán ở địa điểm trước" (read-only, when the ticket moved branch) and the absorbed undercharges recorded on this screen with "Hủy bỏ bản ghi". |
| "Điều chỉnh folio" lookup | Reads a posted folio correction of the ticket by its reference. |
