#### 4.6.6 Audit Review

![](assets/screens/live/80-audit-review.png)

This screen allows Managers to:

- Read the audit log of workshop operations and of workshop settlement in two tabs.
- Filter each tab, with the tab and filters kept in the page URL.
- Expand an operations row to see its permitted details, and read the full reason of a settlement row.
- Load older rows page by page.

The screen is read-only, newest rows first, and viewing the operations log is not itself recorded. Without a **From** date it shows the last 7 days.

**Field Description**

| Field Name | Description |
| --- | --- |
| **Operations** / **Settlement** | Tabs for operations events and settlement records; each tab has its own filters. Default: **Operations**. |
| **Filters ({count})** / **Filter** | Shows or hides the filter fields with the active filter count, and applies them. |
| Operations filters | **Action**, **Outcome** (Options: **All**, **Success**, **Failure**, **Denied**, **Sent**. Default: **All**), **Actor (ID)**, **Session (ID)**, **From** and **To**. **From** must not be after **To**. |
| Settlement filters | **Action code**, **Actor (ID)**, **Object type**, **Object reference**, **From** and **To**. |
| Operations columns | **Time**, **Actor** ("System" for system actions), **Action**, **Outcome**, **Session**, **Correlation ID** and **Details**, which expands the row. |
| Settlement columns | **Time**, **Actor**, **Action code**, **Object**, **Evidence reference**, **Reason** and **Correlation ID**. |
| Details | Only permitted keys, with no customer email, phone number or name. |
| Reason | Shown in full (up to 500 characters); it may contain personal data and is visible to Managers only. |
| **Load more** | Loads the next page of older rows. |
| Empty state | "No audit rows match the filters" with **Clear filters** when a filter is set. |
| Error state | "The log could not be loaded." with its reference and **Try again**. |
