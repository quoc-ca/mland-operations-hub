#### 3.7.3 OTA Reconciliation

{{r3-ota-reconciliation width=100% align=center}}

This screen allows Managers to:

- Review OTA reconciliation work items: synchronization gaps, mapping suspensions and discrepancies.
- Filter the items by status, with items that need attention available as one filter.
- Filter the items by category, with a shortcut for inbound provider events.
- Resolve an unresolved item with a resolution action and a reason that is recorded for audit.

Only a Manager can open the screen; signed-out users go to sign-in and other roles to the not-found page. Status and category filters are kept in the page URL. A resolved item no longer offers the resolve action.

| Field Name | Description |
| --- | --- |
| Lọc theo trạng thái: | ALL (default by direct address; the navigation tab opens on NEEDS_ATTENTION), NEEDS_ATTENTION, OPEN, IN_PROGRESS, RESOLVED, ESCALATED, shown for example as "Cần xử lý (NEEDS_ATTENTION)". |
| Loại: | Free-text category (up to 32 characters, upper-cased) with **Áp dụng**; "Sự kiện từ kênh (INBOUND_EVENT)" applies that category directly and "Mọi loại" clears it. |
| Item header | Work item ID with its status badge, plus "MAPPING SUSPENDED" or "SALES CLOSED" when the summary reports it. |
| Loại / Mã ánh xạ liên kết | Item category, and the related mapping ID or "None (Global)". |
| Mã đối chiếu | Correlation reference of the item for tracing. |
| Tóm tắt / Lý do | Safe summary of the problem, or "No summary recorded". |
| Xử lý mục | Opens the resolve dialog for an item that is not resolved. |
| Hành động xử lý | RESYNC_REQUESTED (queue a resync), ACCEPTED (accept the outcome), REJECTED (reject or close the item) or ESCALATED (escalate for merchant investigation). |
| Lý do quyết định | Required reason for the audit record. |
| Empty state | States that no work item matches the chosen status and category. |
