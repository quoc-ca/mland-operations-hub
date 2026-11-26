#### 3.2.1 Booking Lookup

{{r3-booking-lookup-guest width=100% align=center}}

{{r3-booking-lookup-member width=100% align=center}}

{{r3-booking-lookup-member-detail width=100% align=center}}

This screen allows Members and Guests to:

- As a Member, see the list of their own booking groups and open one.
- As a Guest, find a booking group by its booking code and the email or phone number used for the booking.
- View the group's status, tickets, sessions and payment information, and each confirmed ticket's check-in QR code.
- Continue the payment or cancel the hold of a group that is still waiting for payment.
- Resend the confirmation email, send a ticket to a companion, cancel a ticket or change a ticket's session; a Guest first verifies an email code.
- Open a ticket's invoice and, as a Member, write a review after attending.

| Field Name | Description |
| --- | --- |
| Lookup identity | States whether the lookup uses the signed-in Member account or Guest details; a signed-in account without the Member role cannot look up bookings. |
| Đặt chỗ của tôi | Member only. List of booking groups with code, package, status, number of tickets, sessions and amount paid at booking; **Xem vé và chi tiết** (confirmed) or **Xem chi tiết**, and **Xem đánh giá của gói** once a session has started. |
| Mã đặt chỗ | Guest only. Required booking group code. |
| Email khi đặt / Số điện thoại khi đặt | Guest only. Only one of the two is needed to verify the lookup. |
| Tra cứu nhóm đặt chỗ | Runs the Guest lookup; shows "Không tìm thấy nhóm đặt chỗ phù hợp..." when nothing matches. |
| Group status | "Chờ thanh toán", "Đã xác nhận", "Đã hủy" or "Hết hạn giữ chỗ", with the payment deadline for a pending group. |
| Ticket list | Per ticket: ticket code, person, price, amount still due, status and "Mã QR check-in" when the ticket has a QR code. |
| Ticket actions | On a confirmed ticket without an attendance record: **Gửi vé cho người đi cùng**, **Hủy vé** and **Đổi lịch** (advance change only, with no type to choose; some choices, such as fewer than 24 hours before the session, need Staff or Manager approval and a price increase needs consent). A session that has already started cannot be changed here: the card asks the customer to contact the shop and names the branch. A Guest first verifies a 6-digit code sent to the booking email. Cancelling 24 hours or more before the first session returns the points used on the ticket; a later cancellation does not. The booking-time payment is not refunded, and the group voucher is restored only when every ticket ended validly and the voucher has not expired. The result states what was returned. |
| Xem hóa đơn thanh toán của vé / Chọn buổi thay thế hoặc hoàn tiền | Opens the ticket folio (confirmed or completed ticket), or the remedy page when Mland cancelled or moved a session. |
| Viết đánh giá | Member only; "Đánh giá sau khi tham dự" is disabled before attendance. |
| Gửi lại email xác nhận | Resends the confirmation to the booking email, within a resend limit. This action is available from Booking Lookup; the read-only Magic Link view does not offer it. |
| Hủy giữ chỗ | Releases the whole pending group after a reason; not available while a payment is in progress. |
