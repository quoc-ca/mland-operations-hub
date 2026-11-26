#### 12.1.3 Notification Requests

{{r3-12-notification-requests width=100% align=center}}

**Description:** Displays the transactional email delivery log and allows Admins and Managers to filter requests, inspect attempt/audit history, and resend, cancel, or trigger redelivery with a reason.

::: {.docx-table width="100%" columns="50%,50%"}

| Field Name | Description |
| --- | --- |
| **Tự động làm mới (30s)** / **Làm mới** | Enables 30-second auto-refresh or reloads the log immediately. |
| Trạng thái gửi | Filters by `PENDING`, `PROCESSING`, `RETRY_SCHEDULED`, `SENT`, `FAILED`, `UNKNOWN_OUTCOME`, or `CANCELLED`. |
| Mã sự kiện / đơn / booking & Mã mẫu thư | Text filters by business event reference and template code; **Xóa bộ lọc** resets all filters. |
| Request table | Shows ID, event code, template, masked recipient, status, attempt count, created time, and **Xem chi tiết**. |
| Detail drawer | Displays request metadata, **Attempts** history, and **Audit Trail**. |
| **Gửi lại thư** / **Hủy gửi** / **Gửi lại với link/vé mới** | Operator actions requiring a reason; `UNKNOWN_OUTCOME` resend is restricted to Admin. |
| Pager | Navigates pages when results span multiple pages. |

:::
