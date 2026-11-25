#### 3.2.2 Booking Access (Magic Link)

This screen allows Guests and Members to:

- Open one booking group from the secure link in the booking email, without signing in.
- View the group's status, tickets, sessions and payment information.
- Go to Booking Lookup to manage the group.

The token is read from the link and removed from the address bar at once. The view does not offer cancelling the hold or sending a ticket to another person. It also shows "Gửi lại email xác nhận" (confirmed group) and the VNPay payment button (pending group), but the server refuses these actions here; they are done from Booking Lookup.

| Field Name | Description |
| --- | --- |
| Verifying | "Đang xác thực liên kết truy cập an toàn..." while the link is checked. |
| Booking group details | Group code, status, tickets, sessions and payment information of the linked group. |
| Quản lý tại trang Tra cứu | Opens Booking Lookup. |
| Về trang chủ | Opens the Home Page. |
| Missing token | "Không tìm thấy token bảo mật trong liên kết Magic Link..." |
| Expired, revoked or unknown link | "Liên kết truy cập Magic Link không tồn tại hoặc đã hết hạn." A Manager can revoke or replace the link from Session Details / Check-in. |
| Chuyển tới trang Tra cứu | Shown with any error; explains that the group can be found by booking code and the booking email or phone. |
