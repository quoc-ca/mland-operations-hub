#### 4.5.4 Ticket Actions

![](assets/screens/live/65-ticket-actions.png)

This screen allows Staff and Managers to:

- Cancel one ticket, or several tickets of the same booking all or none, on behalf of the buyer at the counter.
- Change one ticket, or several tickets of the same booking all or none, to new sessions as an advance change or a late arrival.
- Verify the buyer first: a 6-digit code is sent to the booking's stored email and the buyer reads it out.
- See a price increase before it is applied, and apply it only after the buyer confirms it.
- See when a change needs a Manager: the request is queued and the ticket changes only after approval in Change Requests.
- As a Manager, grant a ticket one extra counted change.

On the screen, they can also:

- Switch between the **Cancel** and **Change** tabs.
- Follow the link to the change request queue after a request is queued.

A reason is required for every action and is written to the audit log. Prices, outcomes and queue status come from the server. A cancellation cannot be undone; any refund goes to the bank account the payer provides. A paid-up ticket whose folio is finalized can still be cancelled before its session; the folio stays finalized.

**Field Description**

| Field Name | Description |
| --- | --- |
| **Cancel** / **Change** tabs | Default: **Cancel**. The page title follows the tab (**Cancel tickets** / **Change tickets**). |
| **Booking code** | Booking code; needed to send the buyer code, and required when several tickets are handled. |
| **Ticket code** (Cancel tab) | One ticket code, or several of the same booking separated by commas or spaces. |
| **Change type** (Change tab) | Options: **Advance (before the session)**, **Late arrival**. Default: **Advance (before the session)**. Changing it clears the tickets already added. |
| **Ticket code to change** / **Show sessions** | Loads the session picker of one ticket: **Session to change** (one session or **Both sessions together**), then **Choose the new session**. Each option shows seats left, changes left, **Full**, **Needs a Manager to approve** or the price increase. A blocked ticket shows the rule, such as "less than 24 hours before the session". |
| **Add ticket to the change** / **Tickets to change** | Adds the chosen move to the list, marked "(needs a Manager)" when needed; **Remove** takes it off the list. |
| **Reason (required)** | Required reason, up to 1,000 characters. |
| **Buyer verification** | **Send code to the buyer's email** (enabled once a booking code is entered), then **Code (6 digits)**, **Verify** and **Send again**. The masked email is shown after sending; the proof is used once. |
| **Cancel selected tickets** | Cancel tab. Enabled after the buyer is verified and a reason is given. Asks for confirmation first, then shows per ticket whether the cancellation was valid (24 hours or more before the first session) or late, points returned and voucher restoration. |
| **Change tickets** | Change tab. Enabled after the buyer is verified, a reason is given and at least one ticket is added. Shows the applied change with each ticket's new balance due, or the queued request reference. |
| **Grant one extra change** | Manager only, on the Change tab. **Ticket code** and **Grant extra change** open a dialog that requires a reason; it does not move the ticket by itself. |
| **Price increase to confirm** | Shown when the change raises the price; nothing changes until **Buyer confirms the increase**. |
