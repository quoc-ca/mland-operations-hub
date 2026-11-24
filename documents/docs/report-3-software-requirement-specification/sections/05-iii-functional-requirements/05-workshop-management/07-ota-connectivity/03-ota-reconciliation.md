#### 5.7.3 OTA Reconciliation

This screen allows Managers to:

- Review OTA reconciliation work items: synchronization gaps, mapping suspensions and discrepancies.
- Filter the items by status, with items that need attention available as one filter.
- Filter the items by category, with a shortcut for inbound provider events.
- Resolve an unresolved item with a resolution action and a reason that is recorded for audit.

The screen requires sign-in; the backend decides permissions. Status and category filters are kept in the page URL. A resolved item no longer offers the resolve action. The labels on this screen are in English.

| Field Name | Description |
| --- | --- |
| Filter by Status | ALL (default), NEEDS_ATTENTION, OPEN, IN_PROGRESS, RESOLVED, ESCALATED. |
| Category | Free-text category (up to 32 characters, upper-cased) with **Apply**; **INBOUND_EVENT** applies that category directly and **All categories** clears it. |
| Item header | Work item ID with its status badge, plus "MAPPING SUSPENDED" or "SALES CLOSED" when the summary reports it. |
| Type / Linked Mapping ID | Item category, and the related mapping ID or "None (Global)". |
| Correlation ID | Correlation reference of the item for tracing. |
| Summary / Reason | Safe summary of the problem, or "No summary recorded". |
| Resolve Item | Opens "Resolve Reconciliation Item" for an item that is not resolved. |
| Resolution Action | RESYNC_REQUESTED (queue a resync), ACCEPTED (accept the outcome), REJECTED (reject or close the item) or ESCALATED (escalate for merchant investigation). |
| Decision Reason | Required reason for the audit record. |
| Empty state | States that no work item matches the chosen status and category. |
