#### 12.1.3 Notification Requests

This screen allows Admins and Managers to:

- Browse the delivery log of transactional email requests with their sending state and attempt count.
- Filter the log by delivery state, business event reference and template code.
- Open a request to see its details, delivery attempts and audit trail.
- Resend a sent or failed request, with a reason.
- Cancel a request that is still waiting (PENDING or RETRY_SCHEDULED), with a reason.
- Request a redelivery with a new ticket or access link when the original link is no longer valid.

Recipient addresses are always shown masked. A request in PROCESSING cannot be resent or cancelled. Only an Admin may resend a request in UNKNOWN_OUTCOME, after explicitly accepting the risk of a duplicate email; a Manager is refused. Every action requires a reason and is recorded in the audit trail.

Internal account invitations have a separate recovery path in User Account Details. Their delivery log may be viewed here, but the generic resend, cancel and new-link actions reject this message type. An invitation makes at most two definite SMTP attempts. UNKNOWN_OUTCOME requires provider/operator reconciliation; it is not treated as a definite failure. Only Admin may retrieve the current unexpired message after two definite failures, without changing FAILED to SENT.

| Field Name | Description |
| --- | --- |
| Trạng thái gửi | Filter: PENDING, PROCESSING, RETRY_SCHEDULED, SENT, FAILED, UNKNOWN_OUTCOME or CANCELLED, or all states. |
| Mã sự kiện / đơn / booking and Mã mẫu thư | Text filters on the business event reference and the template code; **Xóa bộ lọc** clears all filters. |
| Tự động làm mới (30s) | When checked, reloads the log every 30 seconds; **Làm mới** reloads it at once. |
| Request table | ID, Mã sự kiện, Mẫu thư, Người nhận (đã che), Trạng thái, Lần thử and Thời gian tạo; clicking a row or **Xem chi tiết** opens the detail drawer. |
| Detail drawer | Event reference, state, template code and version, masked recipient, attempt count, created, next-attempt and terminal times, and the original request for a resend. |
| Attempts and audit trail | "Lịch sử các lần gửi thư (Attempts)" with start, result and message, and "Lịch sử thao tác & kiểm toán (Audit Trail)" with the time and reason of each operator action. |
| Gửi lại thư | For eligible non-invitation requests: enabled for SENT or FAILED, and for UNKNOWN_OUTCOME to an Admin after evidence review. Requires a reason and, for UNKNOWN_OUTCOME, duplicate-risk confirmation. |
| Hủy gửi | For non-invitation requests only: enabled for PENDING or RETRY_SCHEDULED and requires a reason. |
| Gửi lại với link/vé mới | For eligible booking or retail requests only: the owning feature supplies a new credential and the operator supplies a reason. |
| Pager | Shown only when there is more than one page: "Tổng cộng {n} yêu cầu • Trang x / y" with **Trang trước** and **Trang sau**. |
