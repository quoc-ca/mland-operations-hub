#### 7.3.3 GHTK Partner Orders

This screen allows the GHTK partner to:

- Browse the GHTK shipments of every Mland branch, newest first, by status Đã tiếp nhận, Đang lấy hàng, Đang giao hàng and Đã giao hàng, opening at the first tab, left to right, that has shipments, and search by tracking number, order code, recipient name or phone.
- See where to collect, whom to deliver to and the item count; beside Giao thành công, Thu hộ (COD): 0 ₫ because the customer prepaid.
- Press Đã lấy hàng after collecting a packed parcel and Giao thành công after delivering it.
- Access this portal (/carrier/orders, reached from the partner home /carrier) only with an active GHTK partner account (role CARRIER).
- Work inside the operations shell with the G logo and green theme: the rail shows only Trang chủ, Đơn hàng and Thống kê, never the Mland operations groups; language and Đăng xuất sit at the top right, with no profile link.
- On the partner home, read today's notice in Vietnam time: Hôm nay có {n} vận đơn mới, with the shipments waiting for pickup and on the way.
- Read every label in Vietnamese or English with the VI/EN switch.

| Field Name | Description |
| --- | --- |
| Mã vận đơn / Mã đơn | Platform tracking number and the Mland order code. |
| Trạng thái vận đơn | Đã tiếp nhận (shop still preparing, no need to come yet), Đang lấy hàng (shop finished packing), Đang giao hàng, Đã giao hàng. |
| Lấy hàng tại | Pickup branch. |
| Giao đến | Recipient name, phone and address. |
| Thu hộ (COD) | Shown beside Giao thành công: always 0 ₫ because the customer prepaid goods and shipping. No product prices, vouchers, points or Member account data are shown. |
| Times | Shop chuẩn bị xong, Đã lấy hàng and Giao thành công times. |
| Đã lấy hàng / Giao thành công | Records the courier step once after state checks, with no confirmation dialog; the shipment moves to its next tab without a success message. Only an error is shown, e.g. a stale list that must be reloaded. |
| Thông báo hôm nay | On the partner home /carrier: Hôm nay có {n} vận đơn mới and {m} đơn chờ lấy hàng · {k} đơn đang giao; nothing is shown when the shipments cannot be read. |
| Simulation notice | Mô phỏng · không phải hệ thống GHTK thật. |
