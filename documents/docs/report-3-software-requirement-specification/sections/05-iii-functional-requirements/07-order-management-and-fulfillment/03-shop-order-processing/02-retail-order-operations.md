#### 7.3.2 Retail Order Operations

![](assets/screens/57-retail-order-operations.png)

This screen allows authorized Staff to:

- Collect cash for a cash-at-the-counter order: enter the cash received, see the change and confirm, optionally handing the order over in the same step.
- Record preparation, then Đã chuẩn bị xong: a pickup order moves to Chờ khách nhận and a GHTK order to Chờ ĐVVC lấy hàng.
- Record Giao cho khách after confirming the signed-in owning Member and a ready order; the received time is recorded.
- Until the GHTK partner account exists, record the courier steps GHTK đã lấy hàng (mô phỏng) and Giao thành công (mô phỏng).
- Use valid forward transitions without a manual paid override or automatic cancellation.

| Field Name | Description |
| --- | --- |
| Order / type / branch / state | Read-only processing context; assigned-branch and action restrictions apply. |
| Khách đưa | Cash received for a cash order; the change is computed and Xác nhận đã thu records payment once. |
| Giao hàng cho khách ngay sau khi thu tiền | When ticked, the counter handover is recorded with the cash collection. |
| Đã chuẩn bị xong | Marks a prepared order ready for pickup or for the GHTK courier after state checks. |
| Giao cho khách | Records the counter handover once; Giao cho khách lúc is then shown. |
| Điện thoại / Địa chỉ | GHTK recipient data needed for packing; retired 30 days after the original handoff. |
| Thao tác của GHTK, tạm thời Staff bấm thay | Simulated courier steps, removed once the GHTK partner account exists; there is no GHTK shipment API. |
