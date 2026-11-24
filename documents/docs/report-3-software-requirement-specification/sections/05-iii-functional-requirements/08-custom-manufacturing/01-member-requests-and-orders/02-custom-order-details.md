#### 8.1.2 Custom Order Details

![](assets/screens/29-custom-order-details.png)

This screen allows Members to:

- View one of their own custom orders with its status and four progress steps: quote approval, full payment, production and handover.
- Review the Manager-approved quote: full amount, quote reference, quote version, payment deadline and the Manager's note.
- Pay the exact quoted amount in one VNPay payment while the quote is ready and unexpired.
- See that the deadline has passed and request a new quote when the quote has expired.
- View the ring specification snapshot taken from the attached workshop ring design.

The page requires sign-in and opens only the Member's own order. It shows no deposit, remaining balance or Add to Cart control, and a custom order is not treated as a retail order.

| Field Name | Description |
| --- | --- |
| **Quay lại danh sách đơn hàng tùy chỉnh** | Returns to My Custom Orders. |
| Order header | "Chi tiết đơn hàng thiết kế cá nhân", order code and status: "Chờ duyệt báo giá", "Chờ thanh toán 100%", "Báo giá đã hết hạn" or "Đã thanh toán toàn bộ". |
| Progress steps | "1. Báo giá & Duyệt", "2. Thanh toán 100%", "3. Chế tác sản xuất" and "4. Bàn giao sản phẩm", each with its current state. |
| "Tổng tiền thanh toán 1 lần (100%)" | Full quoted amount, or "Đang tính toán" before a quote exists. |
| "Mã tham chiếu báo giá" | Quote reference with "Phiên bản báo giá: Lần #{n}". |
| "Thời hạn thanh toán" | Quote deadline, or "Chưa thiết lập"; "Hạn chót đã qua. Báo giá đã hết hiệu lực." once expired. |
| "Ghi chú từ Quản lý (Manager)" | Manager's pricing note, when present. |
| **Thanh toán toàn bộ {amount} qua VNPay** | Shown while the quote is ready, unpaid and unexpired; redirects to VNPay. |
| **Yêu cầu báo giá mới** | Shown when the quote has expired. |
| Ring specification snapshot | "Thông số kỹ thuật mẫu nhẫn chế tác (Feature-012 Snapshot)": model, material, ring size, stone count and laser engraving text, when present. |
