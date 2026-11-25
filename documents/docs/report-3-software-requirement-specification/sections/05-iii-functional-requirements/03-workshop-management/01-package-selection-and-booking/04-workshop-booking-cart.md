#### 3.1.4 Workshop Booking Cart

This screen allows Guests and Members to:

- Review the workshop cart lines added from package pages, each with its branch, sessions and number of tickets.
- Select the lines to book, change the number of tickets of a line, or remove a line.
- As a Member, choose a voucher and use loyalty points for the selected lines.
- Request one quote for the selected lines, enter the contact email and phone, and hold seats for all of them at once.
- Pay the whole group in one VNPay payment before the payment deadline ends.

The cart is kept in this browser, separately for each signed-in account and for a guest, and booking from the cart removes only the booked lines; confirming a "Đặt ngay" booking empties the cart. Prices on the cart are estimates; the amounts are decided by the server quote.

| Field Name | Description |
| --- | --- |
| Booking identity | Shows whether the customer books as a Member or a Guest. |
| Line selection | Checkbox per line and "Chọn tất cả ({count})". |
| Gói workshop | Package name, line number, branch name and the list "Buổi {n}: {date and time}". |
| Đơn giá / Số vé | Price per ticket and a ticket stepper within the package and seat limits. |
| Thành tiền | Line total; after a quote, the automatic discount, voucher and points saved on the line. |
| Xóa dòng | Removes the line from the cart. |
| Line warnings | "Gói này không còn bán tại chi nhánh đã chọn." with a link to choose another branch, or "Dòng này không còn đủ chỗ." |
| Tạm tính / Tổng sau ưu đãi | Estimated total of the selected tickets, then the total after discounts once quoted. |
| Ưu đãi & Điểm thưởng | Member only. Voucher and points picker for the selected lines; the best voucher and the maximum usable points are preselected. No line is selected when the cart opens. |
| Xem báo giá và đặt chỗ | Opens the cart quote with the contact fields and **Xác nhận và giữ chỗ**; if any line cannot be held, nothing is held. |
| Empty state | "Giỏ workshop của bạn đang trống." with **Xem các gói workshop**. |
