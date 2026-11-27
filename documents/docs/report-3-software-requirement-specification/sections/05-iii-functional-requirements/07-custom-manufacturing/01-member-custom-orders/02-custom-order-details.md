#### 7.1.2 Custom Order Details

![](assets/screens/live/42-custom-order-details.png)

This screen allows Members to:

- View one of their own custom orders with its status and four progress steps: quote approval, full payment, production and handover.
- Follow the GHTK shipment of the order once it has a tracking number.
- Review the Manager-approved quote: full amount, quote reference, quote version, payment deadline and the Manager's note.
- Pay the exact quoted amount in one VNPay payment while the quote is ready and unexpired, or request a new quote once it has expired.
- View the original request, the reference images and the ring specification snapshot.

Only the MEMBER role can open the page, and it opens only the Member's own order. It shows no deposit, remaining balance or Add to Cart control.

**Field Description**

| Field Name | Description |
| --- | --- |
| **← Back to custom orders** | Returns to My Custom Orders. |
| Payment notice | Shown after returning from VNPay: verifying, successful, received or not completed (no English label in the app). |
| Order header | "Custom order details", order code, "Created: {date}" and status: "Awaiting quote approval", "Awaiting 100% payment", "Quote expired", "Fully paid", "In production", "Ready for fulfilment" or "Completed". |
| Order progress | "1. Quote & approval", "2. 100% payment", "3. Crafting & production" and "4. Product handover", each with its current state. |
| GHTK shipping | Shown when the order has a tracking number: shipment state, GHTK tracking number, carrier name and delivery address (no English label in the app). |
| "One-time total payment (100%):" | Full quoted amount, or "Calculating" before a quote exists, with "(Single one-time payment)". |
| "Quote reference:" | Quote reference with "Quote version: #{n}". |
| "Payment deadline:" | Quote deadline, or "Not set"; "The deadline has passed. This quote is no longer valid." once expired. |
| "Note from the Manager:" | Manager's pricing note, when present. |
| **Pay the full {amount} via VNPay** | Shown while the quote is ready, unpaid and unexpired; redirects to VNPay. |
| **Request a new quote** | Shown when the quote has expired. |
| "Design Evidence & Original Request" | **View original request →**, "Customer request description:", "Staff review & crafting notes:" and "Uploaded design photos:", which can be enlarged; each shown when present. |
| Ring specification snapshot | "Crafted ring specifications (Feature-012 snapshot)": ring model, material, ring size, number of gemstones and laser engraving text, when present. |
