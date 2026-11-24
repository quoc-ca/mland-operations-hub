#### 5.6.3 Change Requests

This screen allows Staff and Managers to:

- Review the queue of ticket change requests that are blocked only by a rule that Staff or a Manager may relax.
- Filter the queue by status; pending requests are shown by default.
- Open the full details of a request, including current and requested sessions and who asked.
- Approve a pending request, which applies one counted change immediately, or reject it.

An approve or reject decision requires a reason that is written to the audit log; the server still checks capacity and quota on approval. An Admin account does not take part in ticket changes and only sees a note that Staff and Managers handle this queue.

| Field Name | Description |
| --- | --- |
| "Trạng thái" filter | "Đang chờ" (default), "Đã duyệt", "Đã từ chối", "Đã hết hạn"; kept in the page URL. |
| "Làm mới" | Reloads the queue. |
| "Vé / yêu cầu" | Ticket code with a shortened request reference. |
| "Loại" | "Đổi trước" or "Đến muộn". |
| "Ca hiện tại" / "Ca yêu cầu" | Date, start–end time and branch of each current and requested session. |
| "Bị chặn bởi" / "Tăng giá" | The rule codes that blocked the change, and the price increase with "(người mua đã xác nhận)" when the buyer accepted it. |
| "Hết hạn" / "Trạng thái" | Expiry time of the request and its status. |
| "Chi tiết" | Shows request, ticket, group, requester role and channel, reason, created, expiry and decision times, decision reason and applied change reference. |
| "Duyệt" / "Từ chối" | Pending requests only; open a dialog that requires a reason. A decided request shows its decision reason. |
| Empty state | "Không tìm thấy yêu cầu đổi vé nào." |
