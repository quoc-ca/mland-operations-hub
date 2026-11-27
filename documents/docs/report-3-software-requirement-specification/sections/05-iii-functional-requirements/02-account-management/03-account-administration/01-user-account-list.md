#### 2.3.1 User Account List

![](assets/screens/live/89-user-account-list.png)

This screen allows Admins to:

- Search user accounts by email or user name.
- Filter accounts by role and by status.
- See each account's ID, sign-in email, role, status and last update time.
- Open an account's details or open New Internal Account.

This is the Admin's home after sign-in; the page title is **System account management**. Only the ADMIN role can open it; other roles get 404, and a 403 from the server shows an access-denied message.

**Field Description**

| Field Name | Description |
| --- | --- |
| ← Back / Accounts tab | **← Back** returns to Home Page; the **Accounts** tab marks the current admin area. |
| + Create internal account | Opens New Internal Account. |
| Search | Free text, empty by default, matched against email or user name ("Email or user name…"). |
| Role | Options: All roles, MEMBER, STAFF, MANAGER, CARRIER, ADMIN. Default: All roles. |
| Status | Options: All statuses, ACTIVE, PENDING_SETUP, SUSPENDED. Default: All statuses. |
| Search button | Applies the search and filters; shows "Filtering…" while loading. |
| Account table | ID, Sign-in email, Role, Status (coloured badge), Last updated and **Details**, which opens User Account Details. |
| Empty state | "No accounts match the current filters." |
| Access denied | "Access denied (403 Forbidden)" with **Back to home**, shown when the server refuses the list. |
| Pager | 20 accounts per page with **Previous**, **Next**, the page number and the total number of accounts; shown only when there is more than one page. |
