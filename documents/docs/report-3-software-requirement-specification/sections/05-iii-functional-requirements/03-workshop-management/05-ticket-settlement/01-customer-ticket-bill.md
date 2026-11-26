#### 3.5.1 Customer Ticket Bill

{{r3-customer-folio width=100% align=center}}

This screen allows the buyer of a workshop ticket (Member or Guest) to:

- Open the bill of one ticket from its ticket code, without signing in.
- As a signed-in Member, see the bill of a ticket that belongs to their account directly.
- As a Guest, prove ownership by entering exactly one contact of the booking (email or phone) before the bill is shown.
- See the ticket total, the amount paid, the amount awaiting verification and the balance due, with each collection's method, amount and status.
- As a Member, look up a posted bill correction of the ticket by its correction reference.

The screen is read-only. A wrong ticket code and a wrong contact give the same "not found" answer, and the contact is cleared from the form as soon as the answer arrives; it is sent in the request body, never in the URL.

| Field Name | Description |
| --- | --- |
| Email / "Số điện thoại" | Guest only. One of the two must be filled in a valid format ("Dùng một trong hai, đúng định dạng."); the phone has a country selector. |
| "Xem vé của tôi" | Guest only. Enabled once one valid contact is entered; sends the lookup. |
| Ticket code and status | Header of the bill with the bill status: "Đang mở", "Đã chốt" or "Đã đóng". |
| "Tổng" / "Đã thanh toán" / "Chờ xác minh" / "Số còn nợ" | Total amount, confirmed paid amount, amount still awaiting verification (shown only when the server returns it) and balance due. |
| Collections table | One row per collection with "Phương thức", "Số tiền" and "Trạng thái" (for example "Đã xác nhận", "Chờ xác minh", "Đã hủy bỏ", "Thất bại", "Thu thừa"). Hidden when there is no collection. |
| Voucher notice | Shown only when a voucher used on the ticket expired and was not restored; states that the refund is not affected. |
| "Điều chỉnh hóa đơn" lookup | Member only. "Mã điều chỉnh" input and "Xem điều chỉnh" show the correction reference, ticket, overcharge refunded, correction status and, when present, the bank refund and its status. An unknown reference shows "Không có điều chỉnh nào với mã này hiển thị cho vé này." |
| Not found messages | Member: "Chúng tôi không tìm thấy vé này trong tài khoản của bạn." Guest: "Chúng tôi không tìm thấy vé nào với mã và thông tin liên hệ này." |
