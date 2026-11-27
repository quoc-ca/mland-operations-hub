#### 4.6.5 Late Payment Reviews

![](assets/screens/live/79-late-payment-reviews.png)

This screen allows Managers to:

- See the late payments that were not confirmed automatically and wait for a Manager decision.
- See each payment's booking code, amount, failed conditions and answer deadline.
- Choose a standard decision reason and add a note or transaction reference.
- Confirm the late payment when its conditions allow it, or open a 100% refund.

The queue is ordered by the earliest session start. One reason and note apply to the decision made next. With the other reason, a note is required before either button is enabled.

**Field Description**

| Field Name | Description |
| --- | --- |
| **Decision reason *** | Each option shows its code and text. Options: bank or VNPay has credited the money, customer presented a valid receipt, payment dispute handed to accounting, other reason. Default: bank or VNPay has credited the money. |
| **Detailed note / transaction reference** | Note or bank transaction reference; marked "(optional)" except for the other reason. |
| **Reload queue** | Reloads the queue of reviews waiting for a Manager. |
| Review item | Booking code, "Amount: {amount}", "Failed conditions: {list}" and "Must be answered by: {time}". |
| **Confirm late payment** | Confirms the payment; enabled only when the server allows confirmation for this review. |
| **Open 100% refund** | Opens a 100% refund of the payment through workshop settlement. |
| Messages | "Late payment confirmed.", "100% refund request opened.", or "No late payment is waiting for a Manager." |
