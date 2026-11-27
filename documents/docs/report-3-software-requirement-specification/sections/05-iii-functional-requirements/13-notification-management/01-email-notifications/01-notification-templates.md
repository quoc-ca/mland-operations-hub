#### 13.1.1 Notification Templates

![](assets/screens/live/93-notification-templates.png)

This screen allows Admins and Managers to:

- Browse the catalogue of transactional email templates used by the system.
- See each template's owning module, status, active version and latest draft version.
- Open a template's details to manage its content versions.
- Reload the list.

Only Admins and Managers can use the email notification pages. Another signed-in role sees "Access denied" with "Administrator (Admin) or Manager permission is required…"; a signed-out visitor is sent to sign-in. Templates are registered by the system; this screen does not create or delete them.

The internal-account invitation is an Admin-initiated transactional template, separate from booking and retail confirmations. Member verification and password recovery are sent by Firebase.

**Field Description**

| Field Name | Description |
| --- | --- |
| Subtitle | "Catalogue of the system transactional email templates and their active version status". |
| Refresh | Reloads the list; a load error shows the message with a retry action. |
| Template code | Stable template code, for example BOOKING_CONFIRMED. |
| Template name | Template name with its short business description. |
| Module | Module that owns the email event. |
| Status | "Active", "Draft" or "External (Firebase)" for emails sent by Firebase rather than this system. |
| Current version | Number of the active version, or "—" when none is active. |
| Latest draft | Number of the latest draft version, or "None". |
| View details → | Opens Template Details. |
| Empty state | "No notification templates have been registered yet." |
