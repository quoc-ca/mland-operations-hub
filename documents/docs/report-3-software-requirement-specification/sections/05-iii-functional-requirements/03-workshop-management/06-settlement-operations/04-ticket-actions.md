#### 3.6.4 Ticket Actions

This screen allows Staff and Managers to:

- Cancel one ticket, or several tickets of the same booking group all or none, on behalf of the buyer at the counter.
- Change one ticket, or several tickets of the same group all or none, to new sessions as an advance change or a late arrival.
- Verify the buyer first: a 6-digit code is sent to the booking's stored email and the buyer reads it out; Staff do not need to know the email.
- See a price increase before it is applied and apply it only after the buyer confirms it.
- See when a change needs a Manager: the request is queued and the ticket only changes after approval in Change Requests.
- As a Manager, grant a ticket one extra counted change allowance.

A reason is required for every action and is written to the audit log. Prices, outcomes and queue status come from the server. A cancellation cannot be undone; any refund goes to the bank account the payer provides. A paid-up ticket whose folio is finalized can still be cancelled before its first session; the folio stays finalized and nothing is refunded.

| Field Name | Description |
| --- | --- |
| "Hủy" / "Đổi" tabs | Switch between cancelling and changing tickets; the page title follows the tab ("Hủy vé" / "Đổi vé"). |
| "Mã đặt chỗ" | Booking group code; needed to send the buyer code and required when several tickets are handled. |
| "Mã vé" (cancel) | One ticket code, or several of one group separated by commas or spaces. |
| "Loại thay đổi" (change) | "Đổi trước (trước ca)" or "Đến muộn"; changing it clears the tickets already added. |
| "Mã vé cần đổi" / "Hiện các ca" | Loads the session picker of a ticket: session to change (or both sessions), replacement options with seats left, change quota, "Hết chỗ", "Cần Manager duyệt" and price increase, or the rule that blocks the change. |
| "Thêm vé vào lượt đổi" / "Các vé cần đổi" | Adds the chosen move to the list, marked "(cần Manager)" when needed; "Bỏ" removes it. |
| "Lý do (bắt buộc)" | Required reason, up to 1,000 characters. |
| "Xác minh người mua" | "Gửi mã tới email của người mua", then "Mã (6 chữ số)" and "Xác minh"; the proof is used once. |
| "Hủy các vé đã chọn" / "Đổi vé" | Enabled after the buyer is verified and a reason is given. Cancel asks for confirmation first; the result shows per ticket whether the cancellation was valid (24 hours or more before the first session) or late, points returned and voucher restoration. |
| "Cần xác nhận tăng giá" | Shown when the change raises the price; nothing changes until "Người mua xác nhận tăng giá". The applied result shows each ticket's new balance due. |
| "Cấp thêm một lượt đổi" | Manager only, on the change tab. Ticket code and "Cấp thêm lượt đổi" with a required reason; it does not move the ticket by itself. |
