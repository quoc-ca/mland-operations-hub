#### 3.4.3 Locations and Slots

This screen allows Managers to:

- Create workshop branches and edit their name, address and active status.
- Create reusable, non-overlapping time slots for an active branch, edit them, and activate or stop them.
- Create a session for a branch, slot and date; new sessions start as "Bản nháp".
- Generate future sessions in bulk for several slots and weekdays over a date range, skipping sessions that already exist.
- Review sessions by date range, state and branch, edit a session's capacity and check-in opening time, and move it to another state.

The page has three tabs: Chi nhánh, Khung giờ and Ca. Slots and sessions are planned only at active branches.

| Field Name | Description |
| --- | --- |
| Chi nhánh tab | Mã, Tên and Địa chỉ with **Tạo chi nhánh**; code and name are required. The branch list shows active and inactive branches, each editable. |
| Khung giờ tab | Branch selector, then Nhãn, Bắt đầu lúc and Kết thúc lúc with **Tạo khung giờ**; the start must be before the end on the same day. |
| Khung giờ dùng lại | Slot list with Nhãn, Giờ, Trạng thái, **Sửa** and **Kích hoạt** or **Ngừng**. |
| Ca tab: create | Chi nhánh, Khung giờ, Ngày workshop and Sức chứa (above 0), with **Tạo ca mới**. |
| Tạo hàng loạt ca | Branch, Từ / Đến dates, Sức chứa mỗi ca, Trạng thái ban đầu, slots and weekdays; **Tạo ca** reports "Đã tạo {created} ca; bỏ qua {skipped} ca trùng." |
| Session filters | Từ, Đến, Trạng thái and Chi nhánh, with **Xóa lọc**. |
| Session list | Ngày, Giờ, Ca, Chi nhánh, Sức chứa, Trạng thái, "Chuyển trạng thái" ("Đang mở bán", "Tạm dừng bán" or "Đã hủy") with **Áp dụng**, and **Sửa**. The server refuses "Đã hủy" here; a session is cancelled from Session Details / Check-in. |
| Sửa ca | Sức chứa and Mở check-in lúc; the default opening is 60 minutes before the start. |
