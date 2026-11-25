#### 3.1.3 Session and Participant Booking

This screen allows Guests and Members to:

- Review the sessions chosen on the package page and swap a session for another one with enough seats.
- As a Member, choose a voucher and use loyalty points; a Guest books without these benefits.
- Request a quote from the server that shows each ticket's price, the amount paid at booking and the rest paid at the counter.
- Enter the contact email and phone, then confirm the quote to hold seats for the whole booking group.
- Pay the amount due at booking through VNPay before the 10-minute payment deadline ends, or cancel the hold.

Each participant is a separate ticket with its own QR code, check-in and invoice. Seats are held only when the quote is confirmed, and the group is confirmed only after the server verifies the VNPay payment.

| Field Name | Description |
| --- | --- |
| Booking identity | "Đang đặt với tư cách Hội viên MLAND (Member)" or "Khách vãng lai (Guest)"; booking is locked when the account role cannot be verified. |
| 1. Ca workshop đã chọn | The chosen sessions with date and time. **Đổi ca** shows other sessions with "còn {left} chỗ", marking those without enough seats. |
| 2. Ưu đãi & Điểm thưởng | Member only. Voucher choice or code entry and the points switch; one voucher applies to the whole group. |
| Báo giá lại | Shown only when a quote fails; requests it again. The first quote and the quote after a session, voucher or points change are automatic; for a Member the best voucher is preselected. |
| Quote | Per ticket: price, amount paid at booking ("Trả khi đặt") and rest paid at the counter; totals with automatic discount, voucher and points. |
| Email nhận vé & xác nhận / Số điện thoại liên hệ | Required contact details of the buyer; companions' details are not needed at this step. |
| Xác nhận và giữ chỗ | Creates the booking group and holds all seats at once; on refusal, for example no capacity or package not sold at the branch, nothing is held. |
| Payment hold panel | Countdown "Thời hạn thanh toán còn", **Thanh toán {amount} qua VNPay** and the note that seats are released when time runs out. |
| Hủy giữ chỗ | Releases all held seats of the group after a reason is entered; refused while a payment attempt is in progress. |
| Messages | Quote expired, sales paused, package not published, or the system temporarily unavailable; entered details are kept. |
