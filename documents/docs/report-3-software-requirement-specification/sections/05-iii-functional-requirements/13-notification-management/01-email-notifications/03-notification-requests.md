#### 13.1.3 Notification Requests

![](assets/screens/live/95-notification-requests.png)

This screen allows Admins and Managers to:

- Browse the delivery log of transactional email requests with their delivery status and attempt count.
- Filter the log by delivery status, business event reference and template code.
- Open a request to see its details, delivery attempts and audit trail.
- Resend a sent or failed request, with a reason.
- Cancel a request that is still waiting (PENDING or RETRY_SCHEDULED), with a reason.
- Request a redelivery with a new ticket or access link when the original link is no longer valid.

Recipient addresses are always shown masked. A request in PROCESSING cannot be resent or cancelled. Only an Admin may resend a request in UNKNOWN_OUTCOME, after accepting the risk of a duplicate email; a Manager is refused. Every action requires a reason and is recorded in the audit trail.

Internal account invitations have a separate recovery path in User Account Details. Their log can be viewed here, but resend, cancel and new-link actions are disabled for them. An invitation makes at most two definite SMTP attempts. UNKNOWN_OUTCOME needs provider or operator reconciliation and is not treated as a definite failure.

**Field Description**

| Field Name | Description |
| --- | --- |
| Subtitle | "Diagnose the transactional email queue, attempt counts and the audit trail". |
| Auto refresh (30s) | Checkbox, unticked by default; when ticked, reloads the log every 30 seconds. |
| Refresh | Reloads the log at once. |
| Delivery status | Options: All statuses, Pending (PENDING), Sending (PROCESSING), Waiting to retry (RETRY_SCHEDULED), Sent successfully (SENT), Failed (FAILED), Outcome unknown (UNKNOWN_OUTCOME), Cancelled (CANCELLED). Default: All statuses. |
| Event / order / booking code and Template code | Text filters on the business event reference and the template code, empty by default; **Clear filters** clears all filters. |
| Request table | ID, Event code, Template, Recipient (masked), Status, Attempts, Created at and **View details**; clicking a row also opens the detail drawer. Empty: "No email requests match the filters." |
| Pager | 20 requests per page, shown only when there is more than one page: "{total} requests in total • Page {page} / {pages}" with **Previous page** and **Next page**. |
| Detail drawer | "Email request details #{id}": Event code, Delivery status, Template, Recipient (masked), Attempts, Created at, Next attempt, Finished at and, for a resend, "Resent from request". |
| Attempts and audit trail | "Delivery attempt history (Attempts)" with Started, Outcome and Message / Error; "Operations & audit history (Audit Trail)" with Action, Performed by, Time and Reason. |
| Resend email | Enabled for SENT, FAILED or UNKNOWN_OUTCOME requests that are not invitations. The dialog requires a reason of at least 10 characters; for UNKNOWN_OUTCOME an Admin must tick the duplicate-risk box and a Manager sees "Permission denied". |
| Cancel sending | Enabled for PENDING or RETRY_SCHEDULED requests that are not invitations; the dialog warns that it cannot be undone and requires a reason. |
| Resend with a new link/ticket | Enabled for every request that is not an invitation; the server asks the owning feature for a new credential and may refuse. Requires a reason. |
| Copy manual invitation | "Copy manual invitation" (no English label in the app): Admin only, for a FAILED invitation. Shows the subject, expiry and plain text of the current invitation with a copy button. |
| Close | Closes the drawer. |
