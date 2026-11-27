#### 7.1.1 My Custom Orders

![](assets/screens/live/41-my-custom-orders.png)

This screen allows Members to:

- Browse their own custom orders and their own design requests in two tabs.
- See each order's status, quote version, full amount, payment deadline, production status and delivery status.
- Pay a ready, unexpired quote in full through VNPay, or request a new quote when the quote has expired.
- See each request's review status and the reviewer's feedback, and send a clarification reply when the reviewer asked for one.
- Open an order's details or a request's details, or start a new design request from an empty tab.

Only the MEMBER role can open the page, and it shows only the Member's own data. There is no Add to Cart action, no deposit and no remaining balance. After returning from VNPay, the page shows the payment result only once the server confirms it.

**Field Description**

| Field Name | Description |
| --- | --- |
| Title | "My custom orders" with "Manage your ring design requests and custom orders". |
| Payment notice | Shown after returning from VNPay: verifying, payment successful, payment received and updating, or payment not completed. These texts have no English label in the app. |
| Tabs | Options: "Custom orders", "Design requests". Default: "Custom orders". |
| Order card | "Crafting order code", "Created: {date}", "Quote version: #{n}" and status: "Awaiting quote approval", "Awaiting payment", "Quote expired", "Fully paid", "In production", "Ready for fulfilment" or "Completed". |
| Linked design | Design image or placeholder, "Original design request:" with its code (which opens the request tab), the design material and the customer's description. |
| Order figures | "Total payment (100%):", "Payment deadline:", "Production status:" and "Delivery status:" with the GHTK tracking number when shipped. Production and delivery values have no English label in the app. |
| **Order details** | Opens Custom Order Details. |
| **Pay in full ({amount})** | Shown while a ready quote is unpaid and unexpired; redirects to VNPay for the exact quoted amount. |
| **Request a new quote** | Shown when the quote has expired; asks for a new quote on the same order. |
| Request card | "Request code", "Version: #{n} • Updated: {date}", status ("Awaiting review", "Accepted", "Rejected" or "Clarification needed"), the created order with **Order details →**, the description and "Review feedback:" or "Clarification requested by the reviewer:". |
| **Respond to clarification** | Shown when clarification is required; opens a dialog with a required "Response / clarification:" field, **Cancel** and **Send response**. |
| **View details** | Opens Design Request Details. |
| Empty states | "You have no custom design orders yet." with **Create a new design request**, or "You have not submitted any custom design requests yet." with **Create a request now**. |
