#### 5.4.4 Package Availability

This screen allows Managers to:

- See a matrix of workshop packages against branches, showing where each package is sold.
- Turn selling of a package at a branch on or off with one cell.
- See the result of each change, or a conflict when the cell was changed elsewhere.

Turning a cell off only stops new bookings at that branch; existing bookings stay unchanged. Only an active Manager can view and change the matrix; other users see an access-denied panel. The screen sets availability per branch only, not per date.

| Field Name | Description |
| --- | --- |
| Gói đang bán theo chi nhánh | Matrix with branches as rows and packages as columns ("Chi nhánh \\ Gói"). |
| Cell | Ticked when the package is sold at the branch ("Đang hoạt động"); unticked otherwise ("Chưa hoạt động"). |
| Saving | The cell being saved is disabled while other cells stay usable. |
| Success banner | "Đã bật bán" or "Đã tắt bán" for the package and branch; hides after a few seconds. |
| Conflict banner | "Ô vừa được thay đổi ở nơi khác": the latest value is reloaded, with **Tải lại ma trận**. |
| Error banner | "Không lưu được thay đổi": the cell keeps its old value, with a reference code. |
| Empty state | No package or branch yet, with **Đến Gói workshop**. |
| Access denied | "Bạn không có quyền xem trang này" with **Về trang chính**. |
