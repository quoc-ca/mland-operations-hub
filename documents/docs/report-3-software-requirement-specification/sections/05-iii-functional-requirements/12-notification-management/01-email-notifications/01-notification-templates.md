#### 12.1.1 Notification Templates

This screen allows Admins and Managers to:

- Browse the catalogue of transactional email templates used by the system.
- See each template's owning module, lifecycle status, active version and latest draft version.
- Open a template's details to manage its content versions.
- Reload the list.

Only Admins and Managers can open the `/admin/notifications/*` pages; other signed-in roles see an access-denied message (Feature-007 asks for the not-found page). Templates are registered by the system; this screen does not create or delete them.

| Field Name | Description |
| --- | --- |
| Mã mẫu | Stable template code, for example BOOKING_CONFIRMED. |
| Tên mẫu thư | Template name with its short business description. |
| Phân hệ | Module that owns the email event. |
| Trạng thái | "Đang kích hoạt" (active), "Bản nháp" (draft) or "Bên ngoài (Firebase)" for emails sent by Firebase rather than this system. |
| Phiên bản hiện tại | Number of the active version, or "—" when none is active. |
| Bản nháp mới nhất | Number of the latest draft version, or "Không có". |
| Xem chi tiết | Opens Template Details. |
| Làm mới | Reloads the list; a load error shows the message with **Thử lại**. |
| Empty state | "Chưa có mẫu thông báo nào được đăng ký." |
