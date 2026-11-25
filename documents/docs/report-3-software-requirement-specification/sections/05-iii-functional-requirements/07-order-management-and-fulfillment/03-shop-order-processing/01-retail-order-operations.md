#### 7.3.1 Retail Order Operations

![](assets/screens/57-retail-order-operations.png)

This screen allows Staff (Managers only as noted below) to:

- Work through the branch's retail orders in tabs: Đơn hàng cần chuẩn bị, Chờ ĐVVC lấy hàng, Đã bàn giao ĐVVC, Chờ thu tiền mặt, Chờ khách nhận and Hoàn thành.
- Press Đã chuẩn bị xong on a paid order: a pickup order moves to Chờ khách nhận and a GHTK order to Chờ ĐVVC lấy hàng.
- Collect cash at the counter with Xác nhận đã thu for the exact amount; payment and handover are recorded together and the order moves to Hoàn thành.
- Press Giao cho khách to record the counter handover of a ready pickup order.
- Follow a packed GHTK order without acting on it: the GHTK partner records the pickup and the delivery on the carrier portal.
- Open a card to see its details inline, including the recipient data and status history of a GHTK shipment.

The page is reached at /staff/retail-orders; the former addresses /staff/retail-shipments and /staff/retail-pickups open the same page on the Đơn hàng cần chuẩn bị and Chờ thu tiền mặt tabs. Without a chosen tab it opens the first tab, left to right, that has orders. The server returns its data only to Staff and Managers, limited to their assigned branches. A Manager can view the orders and mark a GHTK order as prepared; preparing and handing over a pickup order and collecting cash are accepted from Staff only.

| Field Name | Description |
| --- | --- |
| Summary | Tiền mặt đã thu hôm nay, Sắp quá hạn giữ hàng (dưới 24 giờ) and Việc đang chờ (orders to prepare, collect cash for or hand over). |
| Search / Làm mới | Matches order code (ML…), tracking number or recipient name; Làm mới reloads every tab. |
| Tabs | Each tab except Hoàn thành with its count of orders; Chờ thu tiền mặt is sorted by hold deadline, the others newest first. At most 50 GHTK shipments per shipment status are loaded. |
| Order card | Order code, branch with Nhận tại cửa hàng or the GHTK tracking number, a stage pill, product lines or recipient name and area, and Thành tiền or Cần thu. |
| Giữ hàng đến | On Chờ thu tiền mặt: the cash hold deadline and time left, highlighted when under 24 hours. |
| Xác nhận đã thu | Shows the amount due; records payment of exactly that amount and the handover once. If it fails, nothing is recorded and Staff presses again. |
| Đã chuẩn bị xong | Marks a prepared order ready; a GHTK shipment asks for confirmation first. |
| Giao cho khách | Records the handover of a pickup order once; Giao cho khách lúc is then shown in its details. |
| Card details | Mã đơn, Thời gian đặt, Thanh toán, amounts and cash received and change for a pickup order; Mã vận đơn, Điện thoại, Địa chỉ and Lịch sử for a GHTK shipment. |
| Courier hint | Read-only Chờ GHTK đến lấy hàng or GHTK đang giao hàng on a packed GHTK order. |
