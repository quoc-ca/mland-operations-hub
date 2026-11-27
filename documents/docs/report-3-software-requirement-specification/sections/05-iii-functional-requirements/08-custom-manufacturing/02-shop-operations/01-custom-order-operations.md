#### 8.2.1 Custom Order Operations

This screen allows Staff and Managers to:

- See the custom orders of all Members and search them by order code, request code, customer name, email or request description.
- Follow the orders by stage and see counts for each stage.
- Review the customer, design snapshot, reference images, quote, GHTK tracking number, production progress, and handover status of an order, open its design request, and open a read-only visual design/specification preview when a snapshot is available.
- Approve a quoted order as a Manager directly from the `QUOTE_PENDING` list stage, then move a paid order forward after a confirmation: start production, mark it ready for fulfillment (generating GHTK tracking `S...`), and pack the item to advance shipment for courier collection (`READY_FOR_CARRIER`).

The screen is at `/staff/custom-orders` (also `/manager/custom-orders`) and the server limits its data and status changes to Staff and Managers. Staff prepares the quote in Design Review Details; a Manager may approve a `QUOTE_PENDING` quote either in Design Review Details or directly from this list.

| Field Name | Description |
| --- | --- |
| Title | "Điều phối & Chế tác Đơn hàng Custom". |
| **Làm mới** | Reloads the order list. |
| Summary cards | Counts of orders awaiting Manager quote approval, waiting for production, in production, ready for handover and completed. |
| Search | Matches order code, request code, customer name, email or request description. |
| Stage tabs | "Chờ Quản lý duyệt", "Chờ chế tác" (default), "Đang gia công", "Sẵn sàng giao hàng", "Đã hoàn tất" and "Tất cả đơn". |
| Order card | Order code, status badge, linked design request, creation date, quoted amount, customer and delivery information, design details, GHTK tracking number and reference images. |
| **Xem chi tiết thiết kế 3D & Thông số** | Opens the saved design snapshot in a read-only visual preview with technical specifications; it does not mutate the order or design. |
| Stage button | **Bắt đầu chế tác**, **Hoàn thành chế tác & Sẵn sàng giao**, or **📦 Đóng gói xong & Bàn giao bưu tá GHTK**, according to the current stage; only the next stage can be chosen. Staff cannot directly close the order; final delivery is performed by the GHTK courier account (`CARRIER`) via the carrier portal. |
| Confirmation dialog | Shows the current and new stage before the change is saved. |
| **Xem thêm** | Quote reference and deadline, production status, courier tracking, handover status and the reviewer's note. |
| Empty message | "Không tìm thấy đơn gia công nào trong danh mục này." |
