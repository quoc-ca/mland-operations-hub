#### 3.6.2 Cash Close

This screen allows Staff and Managers to:

- Choose a branch and working day and read the expected cash computed by the server.
- Enter the counted cash and, when it differs from the expected amount, a difference reason.
- Close the cash book and see the server's expected, counted and difference figures.
- As a Manager, confirm a difference that is waiting for confirmation, from the closed result or from the confirmation list.

The screen never computes a difference itself. A close with a difference stays "Chờ Manager xác nhận" until a Manager confirms it; Staff see that a Manager must confirm. Cash recorded after the close belongs to the next working day, even while the difference is still waiting. The Manager home opens this screen on the list of differences awaiting confirmation.

| Field Name | Description |
| --- | --- |
| "Mã chi nhánh" | Numeric branch identifier; required to load the expected cash. |
| "Ngày làm việc" | Working day date; optional, the server uses the current working day when it is empty. |
| "Xem tiền mặt kỳ vọng" | Loads the working day and "Tiền mặt kỳ vọng" from the server. |
| "Tiền mặt kiểm đếm (VND)" | Counted cash as a whole VND amount (0 allowed). |
| "Lý do chênh lệch (nếu kiểm đếm khác kỳ vọng)" | Free text up to 1,000 characters explaining a difference. |
| "Chốt sổ tiền mặt" | Closes the cash book; enabled only for a valid counted amount. Shows "Đã chốt sổ tiền mặt." on success. |
| Close result | Close reference and status ("Chờ Manager xác nhận", "Đã chốt", "Đã xác nhận") with "Kỳ vọng", "Kiểm đếm", signed "Chênh lệch" and "Lý do", plus the cut-off note. |
| "Chênh lệch chờ xác nhận" | Manager only, shown when the screen is opened from the Manager home or the "Chốt sổ tiền mặt" navigation tab: close reference, branch, day, expected, counted, difference and reason, or "Không có chênh lệch chốt sổ tiền mặt nào đang chờ xác nhận." |
| "Xác nhận chênh lệch" | Manager only. Opens a dialog that requires a reason; the day is closed with the difference and the action is written to the audit log. |
