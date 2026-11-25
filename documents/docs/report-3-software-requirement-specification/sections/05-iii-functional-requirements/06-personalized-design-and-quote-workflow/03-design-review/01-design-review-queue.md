#### 6.3.1 Design Review Queue

This screen allows Staff and Managers to:

- Browse all submitted design requests, newest first, filter them by review status and reload the list.
- See when each request was sent, who sent it, its idea description and its number of images.
- Open a request in Design Review Details to review it.

The route requires only sign-in; the server refuses other roles and the page shows the error. Staff and Managers have the same review authority, and the queue content is decided by the server.

| Field Name | Description |
| --- | --- |
| Subtitle | "Danh sách các yêu cầu thiết kế đang chờ nhân sự thẩm duyệt (Staff / Manager)." |
| Lọc trạng thái | "Tất cả trạng thái" (default), "Chờ thẩm duyệt", "Đã chấp nhận thiết kế", "Từ chối thiết kế" or "Cần làm rõ thêm". |
| **Làm mới** | Reloads the list. |
| Mã tham chiếu | Request reference, with the internal request ID below it when they differ. |
| Thời gian gửi | Date and time the request was submitted. |
| Người gửi | The sender's email; "Khách" when there is none. |
| Mô tả ý tưởng | First line of the description, with the full text on hover; "—" when empty. |
| Số ảnh | Number of reference images, or "—". |
| Trạng thái | "Đang chờ nhân viên thẩm duyệt", "Đã chấp nhận thiết kế", "Từ chối thiết kế" or "Cần làm rõ thêm". |
| **Thẩm duyệt** | Opens Design Review Details for the request. |
| Empty state | "Hiện không có yêu cầu nào đang chờ thẩm duyệt." |
