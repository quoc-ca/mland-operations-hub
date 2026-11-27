#### 4.5.6 Refund Queue

![](assets/screens/live/82-refund-queue.png)

This screen allows Managers to:

- List bank refund requests and select one to work on.
- Move a refund through its steps: approve, record the bank transfer, and confirm it as refunded.
- Reveal the full recipient account number on request, and hide it again.
- Return a refund awaiting approval, transfer or reconciliation to the account-entry step.
- Close a refund as unreachable after 30 days without an account, or reopen a closed one.
- Attach an optional receipt image to a transfer and view it later.
- Enter the recipient account handed over at the counter, or resend the account link.

On the screen, they can also:

- Filter the list by status, including all waiting statuses together, and reload it.

Every Mland refund is a bank transfer and counts as done only when a Manager confirms it. Staff see only refunds for tickets on sessions assigned to them; they can enter the account or resend the account link, but nothing else. The list shows only a masked account number. The full number stays in screen memory only while revealed.

**Field Description**

| Field Name | Description |
| --- | --- |
| **Status** filter | Options: **All**, **Waiting (all pending)** (Managers only), **Pending account**, **Pending approval**, **Pending transfer**, **Pending reconciliation**, **Refunded**, **Closed (unreachable)**. Default: **All**; the Manager's navigation menu opens the screen on **Waiting (all pending)**. Kept in the page URL; **Refresh** reloads. |
| Refund list | **Refund** (refund ID), **Ticket / group**, **Reason** (**Mland cancellation**, **Late payment not reconfirmed**, **Overcharge after finalization** or **Excess or duplicate money**), **Amount**, **Status** and **Account** (masked number and holder name). Clicking a row selects it. Empty state: "No refund requests found." |
| Selected refund header | Refund ID and status. |
| Counter account entry | While **Pending account**: **Bank code**, **Account number**, **Holder name (as the bank shows it)** and **Review details** (all three required). The review step repeats the details and needs **These details are correct** before **Save account**; **Edit details** goes back. |
| **Resend account link** | While **Pending account**. Sends a new single-use account link (valid 7 days); the previous link stops working. |
| **Close as unreachable** | Manager only, while **Pending account**. Opens a dialog that requires a reason; the server allows it only after 30 days without an account. |
| **Show full account number** / **Hide** | Manager only, while **Pending approval**, **Pending transfer** or **Pending reconciliation**. Shows **Bank**, **Account number** and **Holder** until hidden or the next action. |
| **Return to account entry** | Manager only, in the same three statuses. Opens a dialog that requires a reason. Clears the account (keeps only the masked number), cancels any approval and sends a new account link. |
| **Approve** | Manager only, while **Pending approval**. |
| **Record transfer** | Manager only, while **Pending transfer**. **Bank transaction reference** is required (up to 128 characters); **Note (optional)** (up to 256 characters) and **Receipt image** (JPEG, PNG or WebP, at most 5,000,000 bytes) are optional. **Remove image** drops a chosen file. |
| **Confirm refunded** | Manager only, while **Pending reconciliation**; marks the refund **Refunded**. |
| **Reopen** | Manager only, while **Closed (unreachable)**; reopens the refund with a fresh account link. |
| **View receipt image** / **Hide receipt image** | Manager only, when a receipt image is attached. |
| Refunded note | "Refunded. Only the masked account remains on file." |
