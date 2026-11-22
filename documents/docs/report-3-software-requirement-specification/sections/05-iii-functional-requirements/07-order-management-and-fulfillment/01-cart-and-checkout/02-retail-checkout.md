#### 7.1.2 Retail Checkout

![](assets/screens/18-retail-checkout.png)

This screen allows Members to:

- Review the ticked lines grouped by branch (Sản phẩm theo chi nhánh).
- For each branch choose Nhận tại cửa hàng or Giao tận nơi qua GHTK with a saved or new address; the GHTK fee is quoted by GHTK.
- Apply at most one approved eligible voucher, then approved points, or continue without benefits.
- Choose Thanh toán qua VNPay or Tiền mặt tại quầy (only when every branch is store pickup; goods are held up to 3 days).
- Press Đặt hàng to accept one order per branch with exact-variant/benefit holds together or none: VNPay opens straight away (15-minute hold); cash opens Đơn mua at Chờ nhận tại cửa hàng.
- Read the reason above Đặt hàng when the voucher, points or quote changed, and press Cập nhật lại to review the new total before ordering.

| Field Name | Description |
| --- | --- |
| Sản phẩm theo chi nhánh | Read-only lines with image, variant, quantity and amount; frozen once the order is accepted. |
| Nhận hàng | Nhận tại cửa hàng (free) or Giao tận nơi qua GHTK; GHTK is refused for an address GHTK does not serve. |
| Địa chỉ nhận hàng | Saved-address picker (Địa chỉ của tôi) with + Thêm địa chỉ mới; required for GHTK. |
| Voucher giảm giá / Giảm bằng điểm | At most one voucher, then points on the remainder; on Mua Ngay the best eligible voucher is preselected. |
| Phương thức thanh toán | Thanh toán qua VNPay (at least 5.000 đ) or Tiền mặt tại quầy. |
| Chi tiết thanh toán | Tiền hàng, Phí vận chuyển, discounts and Tổng thanh toán in whole VND, calculated from applicable terms. |
| Conflict notice / Cập nhật lại | Specific reason when the voucher can no longer be used, the points changed or the quote expired; Cập nhật lại drops the refused benefit and recalculates. |
| Đặt hàng | Accepts the checkout and opens VNPay or Đơn mua; a browser return from VNPay never marks the order paid. |
