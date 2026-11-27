#### 9.1.1 Workshop Payment Result

![](assets/screens/live/22-workshop-payment-result.png)

This screen allows Members and Guests to:

- Land here after VNPay and see the gateway response code and what it means.
- As a signed-in Member, see their own booking group straight away; as a Guest, enter the email or phone used for the booking.
- While the group is still held, see the time left to pay, pay again or release the hold.
- After a successful payment, see **Payment successful** and the group's tickets, which are also sent by email.
- Read an expired, cancelled or not-completed outcome, and book the same workshop again after an expiry.
- Resend the confirmation email and manage each confirmed ticket, as on Booking Lookup.

The booking group is confirmed only after the system receives and verifies the signed VNPay result (IPN), never from the browser return. While VNPay reports success but the group is not yet confirmed, the page re-reads the group a few times. It also re-reads it every 20 seconds while the tab is visible. A signed-in Staff member or Manager sees a note to return to the settlement invoice instead of the lookup form.

**Field Description**

| Field Name | Description |
| --- | --- |
| Page header | Eyebrow **WORKSHOP BOOKING GROUP PAYMENT** and title **Payment redirect result**. Opened from the header notice to resume a payment, the title is **Continue payment** and no outcome box is shown. |
| Gateway response code: | VNPay response code from the return, or N/A; hidden once the group is confirmed. |
| Outcome | **Payment successful**, **The payment gateway reports success** (waiting for the verified IPN), **Payment not completed**, **The payment gateway returned code {code}**, **The payment deadline has ended**, **No response received from the gateway**, or **Status could not be checked yet**, which asks not to create another payment. |
| Booking code: | Booking group code from the return. |
| Guest lookup | Under **Check the booking group status**: radio **Phone number** or **Email** (default: Phone number), the value field and **View group status**. An empty or invalid value shows a validation message. |
| Member check | A Member sees that the signed-in account is used and **View group status**; the group loads without a click. |
| Staff note | **You are signed in with a staff account ({role}).** with steps to load the bill on the Settlement invoice and **Back to the Settlement invoice** (not shown for an Admin). |
| Time left to pay: | Countdown while the group is pending payment; after it ends, a message that the system is waiting for the gateway result. |
| Group details | Booking code, status, ticket count and amount paid at booking, package lines with ticket count and price per ticket, and **Tickets in this group**. |
| Hold actions | **Pay {amount} via VNPay** and **Release hold** (with a reason) while the group is held; **Release hold** is replaced by a note while a payment attempt is live. |
| Confirmed actions | **Resend confirmation email**, and per ticket **Change schedule**, **Cancel ticket** and **Send the ticket to a companion**. |
| Expired / cancelled notice | States that the group's held seats were released; an expired group offers **Book the workshop again**. |
