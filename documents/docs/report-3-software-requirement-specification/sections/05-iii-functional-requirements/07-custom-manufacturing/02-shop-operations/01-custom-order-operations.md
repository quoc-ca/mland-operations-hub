#### 7.2.1 Custom Order Operations

![](assets/screens/live/99-custom-order-operations.png)

This screen allows Staff and Managers to:

- See the custom orders of all Members and search them by order code, request code, customer name, email or request description.
- Follow the orders by stage with a count for each stage.
- Review the customer, delivery, design details, reference images and quote of an order, and open its design request.
- As a Manager, approve a quote that is waiting for approval.
- Move a paid order forward after a confirmation: start production, then mark it ready for delivery.
- Hand a finished order to GHTK; the order closes automatically when the courier reports delivery.

Only the STAFF and MANAGER roles can open the screen, and the server limits status changes to them. Quotes are prepared in Design Review Details.

On the screen, they can also:

- Open the design drawing and technical specifications in a dialog.
- Enlarge a reference image.

**Field Description**

| Field Name | Description |
| --- | --- |
| Title | "Custom order coordination & crafting" with a short subtitle (no English label in the app). |
| Refresh | Reloads the order list (no English label in the app). |
| Summary cards | Counts of orders awaiting manager approval, awaiting production, in production, ready for delivery and completed (no English label in the app). |
| Search | Matches order code (CO-…), request code (DR-…), customer name, email or request description (no English label in the app). |
| Stage tabs | Options: Awaiting manager approval, Awaiting production, In production, Ready for fulfilment, Completed, All orders; each with its count. Default: Awaiting production. Orders awaiting customer payment or in delivery appear only under All orders. |
| Order card | Order code, stage badge, linked design request, creation date, customer and delivery address, GHTK tracking and shipment state, original description, design details and reference images (no English label in the app). |
| Quote approval banner | On an order awaiting approval: quote amount and code; a Manager also sees an approve button (no English label in the app). |
| Stage switcher | All stages shown as options with the current one marked; only the next stage can be chosen. The server rejects unpaid orders and skipped or backward steps (no English label in the app). |
| Quick action | "Start production" on a paid order, "Finish production & ready to deliver" on an order in production, and "Packed & handed to GHTK" on a ready order. A handed-over order shows the GHTK pickup or delivery state instead (no English label in the app). |
| Confirmation dialog | Shows the current and new stage with a description, and the cancel and confirm buttons, before the change is saved (no English label in the app). |
| Show more | Quote code and deadline, production status, delivery status and the staff note (no English label in the app). |
| Empty message | "No custom orders found in this category." (no English label in the app). |
