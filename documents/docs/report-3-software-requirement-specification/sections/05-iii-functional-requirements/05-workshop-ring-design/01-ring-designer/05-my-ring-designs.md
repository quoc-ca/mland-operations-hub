#### 5.1.5 My Ring Designs

![](assets/screens/live/38-my-ring-designs.png)

This screen allows signed-in Members to:

- Review their own workshop ring designs, five per page.
- See each design's ID, status, version, package, material, shape, size, stone count and estimated price.
- Open a design's evaluation, edit it, or start a custom design request that references it.
- Remove a design from the list after confirmation, or show archived designs and restore one.
- Switch to the craft requests tab to see their own design requests with their review status.

Only the MEMBER role can open the page; a Guest sees a sign-in prompt. The design list refreshes when the page becomes visible again.

On the screen, they can also:

- Look up a custom design request by its code and open its details.

**Field Description**

| Field Name | Description |
| --- | --- |
| Title | "Your Ring Design History" with "Review your personalized ring designs, version history and booking associations." |
| Quick lookup | "Quick lookup of a custom request by code:" with a code field ("REQ-…" or "DR-…") and a lookup button; opens Design Request Details, or asks for a code when empty (no English label in the app). |
| Tabs | Options: "Workshop ring designs", "Craft requests". Default: "Workshop ring designs". The selected tab is kept in the page address; "Craft requests" has no English label in the app. |
| "Show deleted/archived designs" | Check box on the design tab that also lists archived designs. Default: unticked. |
| Design card | Design ID, status badge, version, package, material, shape, size, stone count and "Advisory Estimated Cost". |
| Design actions | **Details →** opens Design Evaluation, **Edit** opens Ring Configuration, **Craft** opens Design Request Workspace with the design attached, **Delete** opens the deletion dialog. An archived design shows **Restore Design** instead of **Delete**. |
| Request card | Request code, review status ("Awaiting staff response", "Accepted", "Rejected" or "Clarification needed"), "(Response received)" when an explanation exists, the description, and **Details →** to open Design Request Details. These texts have no English label in the app. |
| Empty states | "You have not configured any ring designs yet." with **Create Your First Design**; on the request tab, "You have not submitted any custom design requests yet." with **Create a new design request** (no English label in the app). |
| Pager | "Showing {from}–{to} of {total} items" with **Previous** and **Next**, shown whenever the tab has items. |
| Deletion dialog | "Confirm Design Deletion" with **Cancel** and **Delete Permanently**; confirming archives the design and removes it from the list. An error shows if archiving fails. |
