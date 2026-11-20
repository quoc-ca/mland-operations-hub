#### 7.3.1 Retail Order List

![](assets/screens/56-retail-order-list.png)

This screen allows authorized Staff to:

- Browse retail orders within Staff's assigned branches in the tabs Chờ thu tiền mặt, Đang chuẩn bị hàng, Chờ khách nhận, Chờ ĐVVC lấy hàng, Đã bàn giao ĐVVC and Hoàn thành.
- See the number of open tasks, Tiền mặt đã thu hôm nay and cash orders whose hold ends in under 24 hours.
- Search by order code, tracking number or recipient name.
- Open an order's details and Retail Order Operations.

| Field Name | Description |
| --- | --- |
| Mã đơn / Thời gian đặt / Chi nhánh | Read-only processing-context columns; assigned-branch and action restrictions apply. |
| Status | Current Staff step of the order. |
| Thanh toán | Đã thanh toán VNPay, Tiền mặt tại quầy or Đã thu tiền mặt. |
| Cần thu / Thành tiền | Amount to collect for an unpaid cash order, otherwise the frozen order total. |
| Hạn giữ hàng | For a cash order, the hold deadline and the time left. |
| Mã vận đơn | Platform-generated GHTK tracking number; no GHTK provider tracking. |
| Chi tiết | Opens an authorized order; it does not grant cross-branch access. |
