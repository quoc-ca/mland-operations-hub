#### 5.2.1 Booking Lookup

This screen allows Members and Guests to:

- As a Member, see the list of their own booking groups and open one.
- As a Guest, find a booking group by its booking code and the email or phone number used for the booking.
- View the group's status, tickets, sessions and payment information, and each confirmed ticket's check-in QR code.
- Continue the payment or cancel the hold of a group that is still waiting for payment.
- Resend the confirmation email, send a ticket to a companion, cancel a ticket or change a ticket's session after an email code check.
- Open a ticket's invoice and, as a Member, write a review after attending.

| Field Name | Description |
| --- | --- |
| Lookup identity | States whether the lookup uses the signed-in Member account or Guest details; a signed-in account without the Member role cannot look up bookings. |
| Đặt chỗ của tôi | Member only. List of booking groups with code, number of tickets, sessions, amount paid at booking and **Xem chi tiết**. |
| Mã đặt chỗ | Guest only. Required booking group code. |
| Email khi đặt / Số điện thoại khi đặt | Guest only. Only one of the two is needed to verify the lookup. |
| Tra cứu nhóm đặt chỗ | Runs the Guest lookup; shows "Không tìm thấy nhóm đặt chỗ phù hợp..." when nothing matches. |
| Group status | "Chờ thanh toán", "Đã xác nhận", "Đã hủy" or "Hết hạn giữ chỗ", with the payment deadline for a pending group. |
| Ticket list | Per ticket: ticket code, person, price, amount still due, status and "Mã QR check-in" when the ticket has a QR code. |
| Ticket actions | On a confirmed ticket not yet checked in: **Gửi vé cho người đi cùng**, **Hủy vé** and **Đổi lịch**, each after the buyer verifies a 6-digit code sent to the booking email. |
| Gửi lại email xác nhận | Resends the confirmation to the booking email, within a resend limit. |
| Hủy giữ chỗ | Releases the whole pending group after a reason; not available while a payment is in progress. |
