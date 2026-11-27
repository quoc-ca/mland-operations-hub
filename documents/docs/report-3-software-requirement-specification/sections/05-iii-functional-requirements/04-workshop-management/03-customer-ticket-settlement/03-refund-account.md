#### 4.3.3 Refund Account

This screen allows a refund recipient to:

- Open the page from the single-use link in their refund email; no sign-in is required.
- Enter their own bank, account number and account holder name.
- Review the entered details and confirm they are correct before sending.
- Go back and edit the details from the review step.

The link token stays in the link and is not stored on the page. The link works once and expires after 7 days. The holder name is not compared with the payer. After sending, the form is cleared and the account number is never shown again.

**Field Description**

| Field Name | Description |
| --- | --- |
| Title and intro | **Where should we send your refund?** with **Enter your own bank account. This link works once and expires after 7 days.** |
| Bank | Required bank code, free text up to 32 characters. |
| Account number | Required account number, numeric keypad, up to 32 characters. |
| Account holder name | Required, up to 128 characters, with the hint **Exactly as your bank shows it.** |
| Review details | Enabled when all three fields are filled; opens the review step. |
| Review step | **Check the details below with the account owner before sending.**, then **Bank**, **Account number** and **Account holder name** as entered, and the **These details are correct** checkbox, unticked by default. |
| Edit details / Send account | Return to the form, or send the account (enabled only after the checkbox is ticked). A retry reuses the same request key so the account is not written twice. |
| Success message | **Thank you. Your bank account was received. We will transfer the refund after it is approved and email you when it is done.** |
| Link messages | Missing token: **This link is incomplete. Open the link from your refund email again.** Unknown token: **This link is not valid. Open the link from your refund email again.** Used, replaced or expired token: **This link was already used, replaced or has expired. Ask the shop to send you a new link.** |
