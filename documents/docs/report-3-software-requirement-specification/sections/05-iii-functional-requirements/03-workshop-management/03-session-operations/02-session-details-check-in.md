#### 3.3.2 Session Details / Check-in

{{r3-session-details-check-in width=100% align=center}}

This screen allows Staff and Managers to:

- Check in a participant by ticket code or check-in QR code, or find the tickets of a booking code in this session.
- Follow the attendance list and its check-in progress.
- Change the session state and finalize attendance while the session is in progress.
- Override a check-in with a standard reason and resend a booking's tickets; as a Manager, undo a check-in or correct finalized attendance.
- As a Manager, assign or remove session Staff, set the reschedule quota, revoke or replace a booking's magic link, decide late payments and request session cancellation.

Staff can only start a session at their branch that they are assigned to; only a Manager can put a session back on sale or pause its sales. A session in progress cannot return to a sales state.

| Field Name | Description |
| --- | --- |
| Quầy check-in | "Mã vé" or "hoặc Mã QR check-in" input with **Check-in**; an attempt before the opening time shows "Check-in mở lúc {when}." |
| Tra cứu vé theo Mã đặt chỗ | Lists the tickets of a booking code in this session with **Chọn vé này**. |
| Thông tin ca | Sức chứa, Mở check-in and Staff phụ trách; a Manager is warned when check-in is open but no Staff is assigned. |
| State buttons | "Đang mở bán", "Tạm dừng bán" and "Đang diễn ra", disabled with the reason when the change is not allowed. |
| Chốt điểm danh | Finalizes attendance; enabled only while the session is "Đang diễn ra". |
| Danh sách điểm danh | Columns Mã vé, Mã đặt chỗ, Trạng thái, Giờ check-in and Thao tác, with "{checked}/{total} đã check-in" and a status filter. Rows also show "Đã thanh toán" or "Chưa thanh toán"; Thao tác opens **Hóa đơn / Add-on**. |
| Phân công Staff | Manager only. Assigns a Staff member of the session's branch or removes one; removing the last Staff asks for confirmation. |
| Hạn mức đổi lịch | Manager only. Effective quota, its source and usage; set a new quota with a reason, or restore the default of 25% of capacity (minimum 1). |
| Điều chỉnh check-in | Ticket code, reason code, optional evidence reference and note. **Ghi đè check-in** (assigned Staff or Manager, before finalization); **Hoàn tác check-in** (Manager, before finalization); **Đánh dấu đã check-in** (Manager, after finalization). |
| Gửi vé | Resends all tickets of a booking code, limited to 3 times in 15 minutes per booking. |
| Khôi phục đặt chỗ / Thanh toán về trễ | Manager only. **Thu hồi magic link** or **Đổi magic link** with a reason; **Tải hàng đợi**, then **Xác nhận thanh toán về trễ** or **Mở hoàn tiền 100%**. These and the check-in correction, ticket resend and cancellation panels sit under the collapsed "Xử lý ngoại lệ" section. |
| Manager điều chỉnh bill | Manager only. Ticket code, overcharged amount and reason; **Gửi điều chỉnh hóa đơn** opens a bank refund request without changing the finalized bill. |
| Chuyển giao hủy ca | Manager only. Standard cancellation reason and a required note; **Yêu cầu hủy ca**. |
