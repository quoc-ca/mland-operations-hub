#### 7.2.1 My Retail Orders

![](assets/screens/24-my-retail-orders.png)

This screen allows Members to:

- Browse the Member's own retail orders newest first in the tabs Tất cả, Chờ thanh toán, Chờ lấy hàng, Chờ giao hàng, Chờ nhận tại cửa hàng, Đã giao and Đã hủy, and search by order code or product name.
- Land on the tab of the order after paying or placing it.
- For an unpaid VNPay order, see the time left and press Thanh toán to reopen the same VNPay link, or Hủy đơn.
- Press Mua lại to put the rings of a past order back in the cart.
- Open an order's details.
- Read every label in Vietnamese or English with the VI/EN switch.

| Field Name | Description |
| --- | --- |
| Mã đơn / Thời gian đặt | Order code (ML + date + reference) and order time. |
| Product lines | Image, name, variant, quantity and amount; accepted-order identity and options remain frozen. |
| Status | Chờ thanh toán, Đang chuẩn bị hàng, Chờ GHTK lấy hàng, Đang giao hàng, Sẵn sàng nhận hàng, Đã giao, Đã hủy or Đã hủy · quá hạn. |
| Thành tiền | Frozen payable amount of the branch order in whole VND. |
| Thanh toán trong | Countdown to the 10-minute hold deadline; afterwards the order is cancelled and the voucher and points are returned. |
| Thanh toán / Hủy đơn / Mua lại | Shown only when eligible for the current order/payment state. |
| Payment notice | Shown at the top for about 5 seconds, then hidden; errors stay on the page. Payment confirmation comes from verified VNPay evidence, not the browser redirect; a cash order shows where and until when to pay. |
