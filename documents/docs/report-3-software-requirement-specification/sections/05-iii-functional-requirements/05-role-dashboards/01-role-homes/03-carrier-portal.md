#### 5.1.3 Carrier Portal

![](assets/screens/96-carrier-portal.png)

This screen allows the Carrier (GHTK) to:

- See the partner identity: display name, sign-in email, role and nationwide branch scope.
- See today's GHTK work: new shipments today, shipments waiting for pickup and shipments on the way.
- Open Carrier Orders to record the courier's status steps.
- Open the Carrier's Change Email / Password page to change the password.

This is the Carrier's home: the Carrier is always sent here after sign-in, and only the CARRIER role can open it. A suspended account is sent to the not-found page.

| Field Name | Description |
| --- | --- |
| Title and badge | "Carrier partner portal (GHTK)" with the badge "CARRIER · {status}". |
| Partner identity | Partner / representative, Sign-in email, System role and Branch scope "Nationwide (not limited to one branch)". |
| Shipments to deliver | "{n} new shipments today" (created today, Vietnam time) and "{waiting} waiting for pickup · {delivering} on the way", counted from the Carrier shipment list; nothing is shown when the shipments cannot be read. |
| View shipments → | Opens Carrier Orders. |
| Security & password | **Change password →** opens the Carrier password page. |
| Load error | "Could not load the partner account." |
