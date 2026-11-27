#### 10.3.1 Promotion List

![](assets/screens/live/67-promotion-list.png)

This screen allows Managers to:

- Browse vouchers and automatic discounts in one list, filtered by scope and status.
- See each programme's kind, scope, discount, validity window and status.
- Open a programme's details or start a new programme.

Promotion Management pages are shown only to the Manager role; other signed-in roles are routed to the not-found page.

**Field Description**

| Field Name | Description |
| --- | --- |
| New program | Header button that opens Promotion Form. |
| Tabs | Promotion programs and Point policy. Default: Promotion programs. |
| Scope | Options: All, Products, Workshops. Default: All. Kept in the page address. |
| Status | Options: All, Enabled, Paused. Default: All. Kept in the page address. |
| Programme name | Link to Promotion Details, with the voucher code for a voucher. |
| Kind | Voucher or Automatic discount, with a Products / Workshops scope badge. |
| Discount | "{value} off" for a fixed amount, or "{value} off (up to {max})" for a percentage. |
| Valid period | Start and end date-time. |
| Status badge | Enabled or Paused. |
| Pages / Load more | The list shows 10 programmes per page with page navigation; **Load more** loads further programmes from the server. |
| Empty state | "No programme matches the filters." |
