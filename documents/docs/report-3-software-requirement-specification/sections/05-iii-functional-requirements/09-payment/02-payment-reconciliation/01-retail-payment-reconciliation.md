#### 9.2.1 Retail Payment Reconciliation

![](assets/screens/live/74-retail-payment-reconciliation.png)

This screen allows Managers to:

- View, read-only, the retail VNPay payments that need manual handling: results that could not be determined, and late payments or payments for orders no longer valid.
- Search the current page by checkout, payment reference or VNPay transaction code, and filter it by reason.
- Switch to **Daily cash** to see the retail cash Staff confirmed at the counter for one branch and one day.
- Refresh either list; the page settles, refunds or releases nothing.

Only a Manager can open this page. The VNPay list covers all branches. The daily cash view lists every selling branch, but the server answers only for the Manager's assigned branches.

**Field Description**

| Field Name | Description |
| --- | --- |
| Page header | **← Home**, **MLAND / RETAIL RECONCILIATION** and **Check the purchase transactions that need manual handling.** |
| Tabs | **VNPay reconciliation** and **Daily cash**. Default: VNPay reconciliation. |
| Transactions to check | Title of the VNPay tab, with **Recorded by the system. Filters apply to the page shown.** and **Refresh**, which reloads the list. |
| Search order or transaction code | Text search (placeholder **Enter a code**) on checkout, payment reference or VNPay transaction; applies only to the page shown. |
| Reason | Options: **All reasons**, **Payment result unknown**, **Late payment or invalid order**. Default: All reasons. Applies only to the page shown. |
| VNPay table | **Checkout**, **Payment reference**, **VNPay transaction** (**None** when empty), **Amount**, **Reason** (**Payment result not determined** or **Late payment or the order is no longer valid**) and **Recorded at**. |
| Empty messages | **No transaction to reconcile on this page.** or **No transaction matches the filters on this page.** |
| Previous page / Next page | Move between pages of 50 records, with **Page {n}** between them. |
| Retail cash collected | Title of the Daily cash tab, with the note that workshop cash and the till count are not included, and **Refresh**. |
| Branch / Date | Branch picker (default: the first branch) and a business date in Vietnam time, no later than today (default: today). |
| Daily cash total | **{count} collections · Total** with the total applied amount for the branch and day. |
| Cash table | **Time**, **Order**, **Amount**, **Received**, **Change** and **Staff** for each collection. **No cash collected on this day.** when empty. |
