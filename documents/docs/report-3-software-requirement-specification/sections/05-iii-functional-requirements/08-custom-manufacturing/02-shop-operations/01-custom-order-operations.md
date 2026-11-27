#### 8.2.1 Custom Order Operations

{{r3-08-custom-order-operations width=100% align=center}}

**Description:** Displays all Member custom orders grouped by production stage and allows Staff and Managers to search orders, inspect design snapshots, approve quotes, and advance paid orders through production and GHTK handover.

::: {.docx-table width="100%" columns="50%,50%"}

| Field Name | Description |
| --- | --- |
| Title | Displays "Điều phối & Chế tác Đơn hàng Custom". |
| **Làm mới** | Reloads the custom order list. |
| Summary cards | Displays counts for orders awaiting Manager quote approval (`QUOTE_PENDING`), waiting production, in production, ready for handover, and completed orders. |
| Search | Searches by order code, request code, customer name, email, or description. |
| Stage tabs | Filters by **Chờ Quản lý duyệt** (`QUOTE_PENDING`), **Chờ chế tác** (`PAID`), **Đang gia công** (`IN_PRODUCTION`), **Sẵn sàng giao hàng** (`READY_FOR_FULFILMENT`), **Đã hoàn tất** (`CLOSED`), or **Tất cả đơn**. |
| Order card | Shows order code, status badge, linked design request, quoted amount, customer/delivery info, design snapshot, GHTK tracking code, and reference images. |
| **Xem chi tiết thiết kế & Thông số** | Opens the saved design snapshot in a read-only visual preview with technical specifications. |
| Stage action button | Advances the order to the next allowed stage (**Bắt đầu chế tác**, **Hoàn thành chế tác & Sẵn sàng giao**, or **📦 Đóng gói xong & Bàn giao bưu tá GHTK**) with confirmation. Final delivery is confirmed by the courier (`CARRIER`). |
| Confirmation dialog | Displays the current and target stage before updating order status. |
| **Xem thêm** | Expands quote reference, payment deadline, production status, courier tracking, handover status, and reviewer note. |

:::
