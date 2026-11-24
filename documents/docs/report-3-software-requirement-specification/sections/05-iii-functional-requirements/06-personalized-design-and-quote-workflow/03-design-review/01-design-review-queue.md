#### 6.3.1 Design Review Queue

This screen allows Staff and Managers to:

- Browse the submitted design requests that are waiting for human review.
- See when each request was sent, whether it came from a Guest or a Member, its idea description and its number of images.
- Open a request in Design Review Details to review it.

Only Staff and Managers can open this route; other signed-in roles are masked. Staff and Managers have the same review authority, and the queue content is decided by the server.

| Field Name | Description |
| --- | --- |
| Subtitle | "Danh sách các yêu cầu thiết kế đang chờ nhân sự thẩm duyệt (Staff / Manager)." |
| Mã tham chiếu | Request reference, with the internal request ID below it when they differ. |
| Thời gian gửi | Date and time the request was submitted. |
| Người gửi | "Khách" or "Hội viên". |
| Mô tả ý tưởng | First line of the description, with the full text on hover; "—" when empty. |
| Số ảnh | Number of reference images, or "—". |
| Trạng thái | "Đang chờ nhân viên thẩm duyệt". |
| **Thẩm duyệt** | Opens Design Review Details for the request. |
| Empty state | "Hiện không có yêu cầu nào đang chờ thẩm duyệt." |
