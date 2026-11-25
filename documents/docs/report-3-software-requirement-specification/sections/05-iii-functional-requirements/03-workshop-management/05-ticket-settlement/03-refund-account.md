#### 3.5.3 Refund Account

This screen allows a refund recipient to:

- Open the page from the single-use link in their refund email; no sign-in is required.
- Enter their own bank, account number and account holder name.
- Review the entered details and confirm they are correct before sending.
- Go back and edit the details from the review step.

The link token stays in the URL fragment and is not stored on the page. The link works once and expires after 7 days. The holder name is not compared with the payer. After sending, the form is cleared and the account number is never shown again.

| Field Name | Description |
| --- | --- |
| "Ngân hàng" | Required bank code, up to 32 characters. |
| "Số tài khoản" | Required account number, numeric keypad, up to 32 characters. |
| "Tên chủ tài khoản" | Required, up to 128 characters, with the hint "Đúng như ngân hàng của bạn hiển thị." |
| "Kiểm tra thông tin" | Enabled when all three fields are filled; opens the review step. |
| Review step | Shows bank, account number and holder name back from the entered values; the "Các thông tin này chính xác" checkbox must be ticked. |
| "Sửa thông tin" / "Gửi tài khoản" | Return to the form, or send the account (enabled only after the confirmation is ticked). A retry reuses the same request key so the account is not written twice. |
| Success message | "Cảm ơn bạn. Chúng tôi đã nhận được tài khoản ngân hàng của bạn…"; the refund is transferred after approval and the recipient is emailed when it is done. |
| Link messages | Missing token: the link is incomplete. Unknown token: the link is not valid. Used, replaced or expired token: ask the shop to send a new link. |
