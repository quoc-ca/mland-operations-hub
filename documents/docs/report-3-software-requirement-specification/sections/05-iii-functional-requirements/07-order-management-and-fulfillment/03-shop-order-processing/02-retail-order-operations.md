#### 7.3.2 Retail Order Operations

![](assets/screens/57-retail-order-operations.png)

This screen allows authorized Staff to:

- Collect cash for a cash-at-the-counter order: press Xác nhận đã thu for the amount due; payment and handover are recorded together and the order is completed.
- Record preparation, then Đã chuẩn bị xong: a pickup order moves to Chờ khách nhận and a GHTK order to Chờ ĐVVC lấy hàng.
- Record Giao cho khách after confirming the signed-in owning Member and a ready order; the received time is recorded.
- Follow a GHTK order after packing: Chờ GHTK đến lấy hàng, then GHTK đang giao hàng, both recorded by the GHTK partner.
- Use valid forward transitions without a manual paid override or automatic cancellation.
- Read every label in Vietnamese or English with the VI/EN switch.

| Field Name | Description |
| --- | --- |
| Order / type / branch / state | Read-only processing context; assigned-branch and action restrictions apply. |
| Xác nhận đã thu | Shows the amount due; records payment of exactly that amount and the counter handover once, moving the order to Hoàn thành. |
| Đã chuẩn bị xong | Marks a prepared order ready for pickup or for the GHTK courier after state checks. |
| Giao cho khách | Records the counter handover once; Giao cho khách lúc is then shown. |
| Điện thoại / Địa chỉ | GHTK recipient data needed for packing; retired 30 days after the original handoff. |
| Courier notice | Read-only: the GHTK partner records Đã lấy hàng and Giao thành công on the partner portal; Staff cannot, and there is no GHTK shipment API. |
