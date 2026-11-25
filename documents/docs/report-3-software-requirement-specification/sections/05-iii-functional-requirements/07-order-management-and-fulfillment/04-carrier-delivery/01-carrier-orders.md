#### 7.4.1 Carrier Orders

![](assets/screens/97-carrier-orders.png)

This screen allows the Carrier to:

- Browse the GHTK shipments of every Mland branch, newest first, in the tabs Đã tiếp nhận, Đang lấy hàng, Đang giao hàng and Đã giao hàng, each with its count; the page opens at the first unfinished tab, left to right (never Đã giao hàng), that has shipments, otherwise at Đang lấy hàng.
- Search by tracking number, order code, recipient name or phone.
- See where to collect, whom to deliver to and the item count of each shipment.
- Press Đã lấy hàng after collecting a packed parcel and Giao thành công after delivering it.
- Access this portal (/carrier/orders, reached from the Carrier home /carrier) only with an active GHTK partner account (role CARRIER); any other account sees an access message.

| Field Name | Description |
| --- | --- |
| Mã vận đơn / Đơn | Platform tracking number, the Mland order code and the item count. |
| Trạng thái vận đơn | Đã tiếp nhận (shop still preparing; the tab shows Shop đang chuẩn bị hàng. Chưa cần đến lấy.), Đang lấy hàng (shop finished packing), Đang giao hàng, Đã giao hàng. |
| Lấy hàng tại | Pickup branch name and address. |
| Giao đến | Recipient name, phone and address. No product prices, vouchers, points or Member account data are shown. |
| Times | Tiếp nhận, Shop chuẩn bị xong, Đã lấy hàng and Giao thành công times. |
| Đã lấy hàng | Shown on an Đang lấy hàng shipment; records the courier pickup once. |
| Giao thành công / Thu hộ (COD) | Shown on an Đang giao hàng shipment beside Thu hộ (COD): 0 ₫, because the customer prepaid goods and shipping; records the delivery once. |
| Action feedback | No confirmation dialog and no success message: the shipment moves to its next tab. Only an error is shown, e.g. a stale list that must be reloaded. |
