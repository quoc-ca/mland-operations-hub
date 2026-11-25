#### 7.1.1 Shopping Cart

![](assets/screens/17-shopping-cart.png)

This screen allows Members to:

- Review the Member's own cart grouped by branch, with product images and current indicative prices; the cart opens with no line ticked.
- Update positive integer quantities, totaling at most 99 per variant across at most 50 lines, subject to availability.
- Remove cart items and receive validation or stale-edit feedback.
- Tick the lines to buy (or Chọn tất cả), preview the eligible voucher and member points, and proceed to checkout with only those lines.
- After Mua lại from an order, find only the re-added rings ticked and a notice listing the rings that could not be added.
- Read every label in Vietnamese or English with the VI/EN switch.

| Field Name | Description |
| --- | --- |
| Product / variant | Product image, name, SKU and size of the exact configured variant; a line no longer for sale shows Tạm thời không còn bán. |
| Đơn giá / Số lượng / Số tiền | Indicative unit price, integer quantity with − and + controls, and whole-VND line amount; prices are frozen only at checkout. For a ticked line with an automatic discount or voucher, the old unit price is struck through next to the discounted one with the tag Đã áp dụng mã giảm giá when a voucher applies, otherwise Đang giảm giá, and the line amount is net of that line's own share (automatic discount plus voucher) from the promotion preview; only lines the promotion covers are lowered, and points are not spread over lines. |
| Select / Chọn tất cả | Ticks the lines to buy; nothing is ticked when the cart opens except the rings re-added by Mua lại. |
| Voucher / Điểm thành viên | Shows the best eligible voucher or a typed code and the Member's points; the discount is recalculated at checkout. |
| Xóa | On a line, removes that line; at the foot of the cart, removes all ticked lines (disabled when nothing is ticked). It creates no hold or order. |
| Mua hàng | Opens checkout with the ticked lines; disabled when nothing is ticked. |
| Empty-cart notice | Giỏ hàng chưa có sản phẩm, with a link to continue shopping. |
