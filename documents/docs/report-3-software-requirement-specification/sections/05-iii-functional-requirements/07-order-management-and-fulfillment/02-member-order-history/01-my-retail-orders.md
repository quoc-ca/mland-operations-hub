#### 7.2.1 My Retail Orders

![](assets/screens/24-my-retail-orders.png)

This screen allows Members to:

- Browse their own branch orders, newest first, in the tabs Tất cả, Chờ thanh toán, Chờ lấy hàng, Chờ giao hàng, Chờ nhận tại cửa hàng, Đã giao and Đã hủy, each except Tất cả with its count when it has orders, and search by order code or product name.
- Press Bấm để xem chi tiết to show an order's details inline and Thu gọn to collapse them (there is no separate order-details page); click a product to open its product page.
- Return here from VNPay or from placing an order and land on that order's tab; the page shows the payment result only after the server has verified it, rechecking a few times while VNPay confirms.
- For an unpaid VNPay order, see the time left and press Thanh toán to reopen VNPay, or Hủy đơn to cancel every order of the same checkout, return the items to the cart and give back the voucher and points.
- For an unpaid cash-at-the-counter order, see where and until when to pay, and press Hủy đơn to cancel only that branch order.
- Press Mua lại on a completed or cancelled order to put its rings back in the cart.
- For a delivered or picked-up order (tab Đã giao), press Đánh giá to review every product of the order on one screen, or Xem đánh giá once all of them are reviewed.
- Read every label in Vietnamese or English with the VI/EN switch.

Only a signed-in Member can open this page.

| Field Name | Description |
| --- | --- |
| Status tabs / Search | Tab per order stage with its count (none on Tất cả); the search matches the order code (ML…) or a product name. An empty tab shows Chưa có đơn hàng nào ở mục này; with no orders at all, Bạn chưa có đơn mua nào and Mua sắm ngay. |
| Order card header | Order code, branch with Giao qua GHTK or Nhận tại cửa hàng, and a status pill: Chờ thanh toán, Đang chuẩn bị hàng, Chờ GHTK lấy hàng, Đang giao hàng, Trả tiền khi nhận, Sẵn sàng · trả tiền khi nhận, Sẵn sàng nhận hàng, Giao thành công, Đã nhận tại cửa hàng, Đã hủy or Đã hủy · quá hạn. |
| Product lines | Image, name, size, quantity and line amount, frozen from the accepted order. |
| Stage hint | Short text for the current stage, e.g. Thanh toán trong mm:ss for a held VNPay order, the branch and deadline to pay cash, or the code to give at the counter when the order is ready. |
| Order details | Shown inline after Bấm để xem chi tiết: Mã đơn, Thời gian đặt, Chi nhánh, Nhận hàng, Thanh toán, payment and completion times, Hạn giữ hàng or Hạn thanh toán when relevant, and the amount breakdown. |
| Thành tiền | Payable amount of the branch order in whole VND. |
| Thanh toán / Hủy đơn / Mua lại | Shown only when valid for the order's state; Hủy đơn asks for confirmation first. |
| Đánh giá / Xem đánh giá | Shown only on a delivered or picked-up order. Đánh giá opens one review screen listing each product not yet reviewed (photo, name, size, then stars, text and photos) and creates one review per order line on the product; when every line is reviewed the button becomes Xem đánh giá and opens the Member's Đã đánh giá tab. |
| Payment notice | Toast after returning from VNPay or placing an order: Đang xác nhận thanh toán với VNPay…, Thanh toán thành công, a not-completed warning with the hold deadline, or the cash pickup instructions. It shows at the top for about 5 seconds, while errors stay on the page. A browser return alone never marks an order paid. |
