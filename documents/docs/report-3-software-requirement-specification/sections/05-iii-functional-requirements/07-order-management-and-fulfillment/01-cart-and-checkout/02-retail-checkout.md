#### 7.1.2 Retail Checkout

![](assets/screens/18-retail-checkout.png)

This screen allows Members to:

- Review the ticked lines grouped by branch (Sản phẩm theo chi nhánh).
- For each branch choose Nhận tại cửa hàng or Giao tận nơi qua GHTK with a saved or new address; the GHTK fee is quoted by GHTK.
- Get the best automatic product discount (always applied), add at most one eligible voucher on the price after it, then approved points, or continue without a voucher.
- Choose Thanh toán qua VNPay or Tiền mặt tại quầy (only when every branch is store pickup; goods are held up to 3 days).
- Press Đặt hàng to accept one order per branch with exact-variant/benefit holds together or none: VNPay opens straight away (10-minute hold); cash opens Đơn mua at Chờ nhận tại cửa hàng.
- Read the reason above Đặt hàng when the voucher, points or quote changed, and press Cập nhật lại to review the new total before ordering.
- See in Chọn voucher MLAND why each voucher cannot be used for this order, and on the points line when the chosen voucher blocks points.
- Read every label in Vietnamese or English with the VI/EN switch.

| Field Name | Description |
| --- | --- |
| Sản phẩm theo chi nhánh | Read-only lines with image, variant, quantity and amount; frozen once the order is accepted. |
| Nhận hàng | Nhận tại cửa hàng (free) or Giao tận nơi qua GHTK; GHTK is refused for an address GHTK does not serve. |
| Địa chỉ nhận hàng | Saved-address picker (Địa chỉ của tôi) with + Thêm địa chỉ mới; required for GHTK. |
| Voucher giảm giá / Giảm bằng điểm | At most one voucher on the price after the automatic discount, then points on the remainder; on Mua Ngay the best eligible voucher is preselected. A "Phải lưu" voucher works only after the Member saved it in Kho Voucher. A voucher that does not combine with points sets the points line to 0 with Voucher {code} không dùng chung với điểm. Bỏ voucher để dùng điểm. |
| Chọn voucher MLAND | Usable vouchers with Lựa chọn tốt nhất on the largest saving, a code field (at most 128 characters), then Chưa đủ điều kiện cho đơn này with each voucher's reason: Kho Voucher state (Chưa đến thời gian sử dụng, Voucher đang tạm dừng, Voucher đã hết hạn, Voucher không còn sản phẩm nào được áp dụng, Voucher đã hết lượt sử dụng, Bạn đã dùng hết lượt của voucher này), Mua thêm {amount} để sử dụng, or the reason from Loyalty and Promotion Management. Đồng ý applies the choice. |
| Phương thức thanh toán | Thanh toán qua VNPay (at least 5.000 đ) or Tiền mặt tại quầy. |
| Chi tiết thanh toán | Tổng tiền hàng, Tổng tiền phí vận chuyển, Ưu đãi sản phẩm (automatic discount), Voucher giảm giá (with its code), Giảm bằng điểm, Tổng thanh toán and Tiết kiệm (sum of the discounts), in whole VND; discounts in red. |
| Conflict notice / Cập nhật lại | Specific reason when the voucher can no longer be used, the points changed or the quote expired; Cập nhật lại drops the refused benefit and recalculates. |
| Đặt hàng | Accepts the checkout and opens VNPay or Đơn mua; a browser return from VNPay never marks the order paid. |

**Chọn voucher MLAND (voucher selection dialog)**

![](assets/screens/18a-voucher-selection-dialog.png)

The Member opens this dialog from the Voucher MLAND line. At the top is a code field with Áp dụng, to type a voucher code. Below, Mã giảm giá sản phẩm lists the vouchers usable for this order; the one with the largest saving carries Lựa chọn tốt nhất and is preselected, and each card shows the saving, the minimum order and the expiry date. Under Chưa đủ điều kiện cho đơn này the vouchers that cannot be used are greyed out with their reason, such as the Member having used up the voucher or the voucher not applying to a product or size in the order. The footer states how many vouchers are chosen and the discount, and Đồng ý closes the dialog and recalculates the price.

**Địa chỉ của tôi (saved-address dialog)**

![](assets/screens/18b-address-list-dialog.png)

The Member opens this dialog from the address block at the top of the checkout; an address is required only for GHTK delivery. It lists the Member's saved addresses with the recipient name, phone number and full address; the default address carries the tag Mặc định. The Member selects one address, presses Cập nhật to edit an address, or + Thêm địa chỉ mới to add one. Xác nhận applies the selected address to the checkout, and Hủy closes the dialog without changing it.

**Thêm địa chỉ nhận hàng mới (new-address dialog)**

![](assets/screens/18c-new-address-dialog.png)

The form collects Họ và tên người nhận, Số điện thoại, Tỉnh / Thành phố, Phường / Xã / Khu vực đặc thù and Địa chỉ cụ thể (house number, building, street), all required. The fields unlock one after another: the phone number after the name, the province after a valid phone number, the ward after the province and the street address after the ward, and a hint in each locked field names what to fill in first. Loại địa chỉ is Nhà riêng or Văn phòng, and the box Đặt làm địa chỉ giao hàng mặc định makes it the default. Hoàn thành stays disabled until every required field is valid; Hủy or the close icon leaves without saving. The names, phone numbers and addresses in the screenshots are test data.
