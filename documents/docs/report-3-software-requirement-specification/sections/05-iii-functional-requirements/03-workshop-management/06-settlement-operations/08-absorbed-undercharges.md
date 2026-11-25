#### 3.6.8 Absorbed Undercharges

This screen allows Managers to:

- List the undercharges Mland absorbed on finalized ticket folios for one branch and period.
- See the server's total of active records.
- Void an active record once, with a reason, when it was recorded by mistake.

Each record is documentation only: nothing is collected and no folio changes. New records are made on the ticket folio in Settlement Workspace, not here. A voided record stays in the list, is marked and no longer counts in the total. A non-Manager sees "Chỉ Manager mới mở được danh sách khoản thiếu Mland chịu."

| Field Name | Description |
| --- | --- |
| "Mã chi nhánh" | Required numeric branch identifier. |
| "Từ ngày" / "Đến ngày" | Required period; filters are kept in the page URL. |
| "Xem bản ghi" | Loads the records; enabled only when branch and both dates are set. |
| "Tổng các bản ghi ĐANG HIỆU LỰC" | Server total of active records. |
| "Mã tham chiếu" / "Vé" | Undercharge reference and ticket code. |
| "Số tiền" / "Lý do" | Absorbed amount and the reason given when it was recorded. |
| "Trạng thái" | "Đang hiệu lực" or "Đã hủy bỏ"; a voided record shows "không tính vào tổng" and its void reason. |
| "Ghi nhận" | Who recorded it and when. |
| "Hủy bỏ bản ghi" | Active records only. Opens a dialog that requires a reason; the record keeps its content and can be voided only once. |
| Empty state | "Không có bản ghi khoản thiếu Mland chịu nào cho chi nhánh và kỳ này." |
