#### 4.2.2 Booking Access (Magic Link)

This screen allows Guests and Members to:

- Open one booking group from the secure link in the booking email, without signing in.
- View the group's status, tickets, sessions and payment information.
- Go to Booking Lookup to manage the group.

The token is read from the link and removed from the address bar at once. The view offers no hold release, confirmation resend, ticket sending, cancelling or schedule change. For a pending group it still shows the VNPay payment button. This view sends no sign-in or contact proof, so the payment is made from Booking Lookup.

**Field Description**

| Field Name | Description |
| --- | --- |
| Page header | Eyebrow **MAGIC LINK VERIFICATION** and title **Secure booking group access**. |
| Verifying | **Verifying the secure access link...** while the link is checked. |
| Booking group details | Booking code, status, ticket count, amount paid at booking, package lines and **Tickets in this group** with each ticket's details and check-in QR code. A pending group also shows the payment countdown. |
| Manage on the Lookup page | Opens Booking Lookup. |
| Back to home | Opens the Home Page. |
| Missing token | **No security token was found in the Magic Link. Please check the link in your email.** |
| Expired, revoked or unknown link | **The Magic Link does not exist or has expired.** or **The Magic Link has been revoked or changed.** A Manager can revoke or replace the link from Session Details / Check-in. |
| Go to the Lookup page | Shown with any error, with a hint that the group can be found by booking code and the booking email or phone. |
