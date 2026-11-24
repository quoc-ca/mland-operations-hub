#### 5.6.6 Refund Queue

This screen allows Managers to:

- List bank refund requests and filter them by status, including all waiting statuses together.
- Select a refund and move it through its steps: approve, record the bank transfer, and confirm it as refunded.
- Reveal the full recipient account number on request, and hide it again.
- Return an approved or in-progress refund to the account-entry step, close a refund as unreachable after 30 days without an account, or reopen a closed one.
- Attach an optional receipt image to a transfer and view it later.

Every Mland refund is a bank transfer and only counts as done when a Manager confirms "Đã hoàn tiền". Staff may only enter the payer's account at the counter and resend the account link; all other steps are Manager-only and the server refuses anything else. The list shows only a masked account number; the full number is kept in screen memory only while revealed and is never stored or logged. After the refund is completed only the masked number remains.

| Field Name | Description |
| --- | --- |
| "Trạng thái" filter | "Tất cả", "Đang chờ (mọi trạng thái chờ)", "Chờ tài khoản nhận", "Chờ duyệt", "Chờ chuyển khoản", "Chờ đối soát", "Đã hoàn tiền", "Đóng do không liên lạc được"; with "Làm mới". |
| Refund list | "Hoàn tiền" (refund ID), "Vé / nhóm", "Lý do" (for example "Mland hủy ca", "Thu thừa sau khi chốt folio", "Thu thừa add-on OTA"), "Số tiền", "Trạng thái" and "Tài khoản" (masked number and holder name). Clicking a row selects it. |
| Counter account entry | While "Chờ tài khoản nhận": "Mã ngân hàng", "Số tài khoản", "Tên chủ tài khoản (như ngân hàng hiển thị)", then "Kiểm tra thông tin", a review step with the "Các thông tin này chính xác" confirmation, and "Lưu tài khoản". |
| "Gửi lại liên kết tài khoản" | Sends a new single-use account link (valid 7 days); the previous link stops working. |
| "Đóng vì không liên lạc được" / "Mở lại" | Manager only. Close requires a reason and is allowed only after 30 days without an account; a closed refund can be reopened with a new account link. |
| "Hiện số tài khoản đầy đủ" / "Ẩn" | Manager only, from "Chờ duyệt" onward until completion. Shows bank, full account number and holder until hidden or the next action. |
| "Duyệt" | Manager only, while "Chờ duyệt". |
| "Ghi nhận chuyển khoản" | Manager only, while "Chờ chuyển khoản". Requires "Mã giao dịch ngân hàng"; optional note and receipt image (JPEG, PNG or WebP, at most 5,000,000 bytes). The transfer can be recorded without the image. |
| "Xác nhận đã hoàn tiền" | Manager only, while "Chờ đối soát"; marks the refund "Đã hoàn tiền". |
| "Quay lại bước nhập tài khoản" | Manager only, with a required reason. Clears the account (keeps only the masked number), cancels the earlier approval and sends a new account link. |
| "Xem ảnh biên lai" / "Ẩn ảnh biên lai" | Manager only, when a receipt image is attached. |
