#### 5.6.9 Refund Account Key

This screen allows Managers to:

- Run the refund-account key replacement after the encryption key setting has been changed and the service restarted.
- Confirm the run with a reason that is written to the audit log.
- See how many refund requests were reset, how many new account links were sent and how many failed.
- Run it again when some requests failed; only requests that still hold an encrypted account number are processed.

The run deletes the stored encrypted account numbers, returns every affected refund request to "Chờ tài khoản nhận", cancels the earlier Manager approval and emails each recipient a new account-entry link. The Manager approves each refund again after the recipient enters a new account. No account number or key is shown on this screen. Any role other than Manager sees "Chỉ Manager mới mở được màn này."; the backend also refuses other roles.

| Field Name | Description |
| --- | --- |
| Intro | Explains when to use the screen and what the run does to refund requests. |
| Warning note | Every refund request still holding an encrypted account number returns to waiting for an account and the previous approval no longer applies. |
| "Thay khóa" | Opens the confirmation dialog "Xác nhận thay khóa tài khoản hoàn tiền". |
| Reason | Required in the dialog; recorded in the audit log. |
| "Yêu cầu đã đưa về chờ tài khoản" | Number of refund requests reset by the last run. |
| "Link mới đã gửi" | Number of new account links sent. |
| "Yêu cầu lỗi" | Number of requests that failed, with a hint that the run can be repeated safely. |
| Success message | "Đã thay khóa. Mỗi yêu cầu có một bản ghi audit." |
