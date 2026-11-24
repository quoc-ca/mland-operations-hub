#### 8.1.1 My Custom Orders

![](assets/screens/28-my-custom-orders.png)

This screen allows Members to:

- Browse their own custom orders and their own design requests in two tabs.
- See each order's quote status, quote version, full amount, payment deadline, production status and delivery status.
- Pay a ready, unexpired quote in full through VNPay, or request a new quote when the quote has expired.
- See each request's review status and the reviewer's feedback, and send a clarification reply when the reviewer asked for one.
- Open an order's details or a request's details, or start a new design request.

The page requires sign-in and shows only the Member's own data. There is no Add to Cart action, no deposit and no remaining balance.

| Field Name | Description |
| --- | --- |
| Title | "Đơn hàng cá nhân" with "Quản lý yêu cầu thiết kế nhẫn và đơn hàng cá nhân". |
| Tabs | "Đơn hàng cá nhân (Orders)" and "Yêu cầu thiết kế (Requests)". |
| Order card | "Mã đơn gia công", creation date, quote version and status: "Chờ duyệt báo giá", "Chờ thanh toán", "Báo giá đã hết hạn" or "Đã thanh toán toàn bộ". |
| Order figures | "Tổng tiền thanh toán 100%", "Hạn chót thanh toán", "Trạng thái sản xuất" and "Trạng thái giao hàng". |
| **Thanh toán toàn bộ ({amount})** | Shown while a ready quote is unpaid and unexpired; redirects to VNPay for the exact quoted amount. |
| **Yêu cầu báo giá mới** | Shown when the quote has expired; asks for a new quote on the same order. |
| Request card | "Mã yêu cầu", status ("Đang chờ thẩm duyệt", "Đã chấp nhận", "Đã từ chối" or "Cần làm rõ thông tin"), description and reviewer feedback, with **Xem chi tiết**. |
| **Phản hồi làm rõ** | Shown when clarification is required; opens a dialog to send the reply, which is required. |
| Empty states | "Bạn chưa có đơn hàng thiết kế cá nhân nào." with **Tạo yêu cầu thiết kế mới**, or "Bạn chưa gửi yêu cầu thiết kế tùy chỉnh nào." |
