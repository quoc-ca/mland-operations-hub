#### 5.3.2 Session Details / Check-in

![](assets/screens/52-workshop-session-details.png)

This screen allows Staff and Managers to:

- Check in a participant by ticket code or check-in QR code, or find the tickets of a booking code in this session.
- Follow the attendance list and its check-in progress.
- Change the session state and finalize attendance while the session is in progress.
- Correct a check-in with a standard reason and resend a booking's tickets.
- As a Manager, assign or remove session Staff, set the reschedule quota, revoke or replace a booking's magic link, decide late payments and request session cancellation.

Staff can only start a session at their branch that they are assigned to; only a Manager can put a session back on sale or pause its sales. A session in progress cannot return to a sales state.

| Field Name | Description |
| --- | --- |
| Quầy check-in | Mã vé or Mã QR check-in input with **Check-in**; before opening time it shows "Check-in mở lúc {when}." |
| Tra cứu vé theo Mã đặt chỗ | Lists the valid tickets of a booking code in this session, marking OTA and add-on tickets, with **Chọn vé này**. |
| Thông tin ca | Sức chứa, Mở check-in and Staff phụ trách; a Manager is warned when check-in is open but no Staff is assigned. |
| State buttons | "Đang mở bán", "Tạm dừng bán" and "Đang diễn ra", disabled with the reason when the change is not allowed. |
| Chốt điểm danh | Finalizes attendance; enabled only while the session is "Đang diễn ra". |
| Danh sách điểm danh | Columns Mã vé, Mã đặt chỗ, Trạng thái, Giờ check-in and Thao tác, with "{checked}/{total} đã check-in" and a status filter. |
| Phân công Staff | Manager only. Assigns a Staff member of the session's branch or removes one; removing the last Staff asks for confirmation. |
| Hạn mức đổi lịch | Manager only. Effective quota, its source and usage; set a new quota with a reason, or restore the default of 25% of capacity (minimum 1). |
| Điều chỉnh check-in | Ticket code, standard reason code, optional evidence reference and note; **Ghi đè check-in**, **Đánh dấu đã check-in** or **Hoàn tác check-in**. |
| Gửi vé | Resends all tickets of a booking code, limited to 3 times in 15 minutes per booking. |
| Khôi phục đặt chỗ / Thanh toán về trễ | Manager only. Revokes or replaces a booking's magic link with a standard reason, and loads the late payment queue. |
| Chuyển giao hủy ca | Manager only. Standard cancellation reason and a required note; **Yêu cầu hủy ca**. |
