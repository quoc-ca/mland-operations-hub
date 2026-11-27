#### 4.5.9 Refund Account Key

![](assets/screens/live/85-refund-account-key.png)

This screen allows Managers to:

- Run the refund-account key replacement after the encryption key setting has been changed and the service restarted.
- Confirm the run with a reason that is written to the audit log.
- See how many refund requests were reset, how many new account links were sent and how many failed.
- Run it again when some requests failed; only requests that still hold an encrypted account number are processed.

The screen title is **Replace refund-account key**. The run deletes the stored encrypted account numbers and returns every affected refund request to pending account. It cancels the earlier Manager approval and emails each payer a new account-entry link. No account number or key is shown. Any other role sees "Only a Manager can open this screen."; the server also refuses other roles.

**Field Description**

| Field Name | Description |
| --- | --- |
| Intro | Explains when to run the replacement and what it does to refund requests. |
| Warning note | Every refund request still holding an encrypted account number goes back to pending account, and the earlier Manager approval no longer applies. |
| **Replace key** | Opens the confirmation dialog "Confirm refund-account key replacement". |
| Reason | Required in the dialog; recorded in the audit log. |
| **Result of the last run** | Shown after a run. |
| **Requests returned to pending account** | Number of refund requests reset by the last run. |
| **New links sent** | Number of new account links sent. |
| **Failed requests** | Number of requests that failed, with a hint that a rerun is safe. |
| Success message | "Key replaced. Each request has an audit record." |
