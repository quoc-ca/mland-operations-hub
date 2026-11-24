#### 2.3.1 User Account List

![](assets/screens/72-user-account-list.png)

This screen allows Admins to:

- Search user accounts by email or user name.
- Filter accounts by role and by status.
- See each account's ID, sign-in email, role, status and last update time.
- Open an account's details, open New Internal Account, or switch to Role Catalogue.

This is the Admin's home after sign-in. Only the ADMIN role can open it; other roles get 404, and a 403 from the server shows an access-denied message.

| Field Name | Description |
| --- | --- |
| Accounts / Roles tabs | Admin navigation between this list and Role Catalogue. |
| + Create internal account | Opens New Internal Account. |
| Search | Text matched against email or user name ("Email or user name…"). |
| Role | All roles, MEMBER, STAFF, MANAGER, CARRIER or ADMIN. |
| Status | All statuses, ACTIVE, PENDING_SETUP or SUSPENDED. |
| Search button | Applies the search and filters; shows "Filtering…" while loading. |
| Account table | ID, Sign-in email, Role, Status (coloured badge), Last updated and **Details**, which opens User Account Details. |
| Empty state | "No accounts match the current filters." |
| Pager | 20 accounts per page with **Previous**, **Next**, the page number and the total number of accounts; shown only when there is more than one page. |
