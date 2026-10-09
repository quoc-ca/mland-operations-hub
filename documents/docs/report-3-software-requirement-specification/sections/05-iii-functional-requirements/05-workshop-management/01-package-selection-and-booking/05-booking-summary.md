#### 5.1.5 Booking Summary

![Booking Summary mockup](assets/screens/23-booking-summary.png)

This screen allows Guests and Members to:

- Review the package, branch, attendance session, participants and selected design.
- Review the workshop deposit equal to 50% of the selected package price.
- Confirm an eligible booking and proceed to deposit payment with a temporary capacity hold of at most 15 minutes.
- Receive booking confirmation only after verified payment, not from the browser return.

| Field Name | Description |
| --- | --- |
| Progress indicator | Shows package, location, design and confirmation stages; the selected package must precede session/design finalization. |
| Package / availability | Displays the selected active published package and current session capacity, not guaranteed capacity from sample text. |
| Branch / session | Selects an eligible branch, attendance date and available session; configured capacity must remain sufficient. |
| Participant information | Collects booking contact and participant details. |
| Next | Validates the current booking step; merely browsing or editing an earlier step creates no seat hold. |
| Deposit / hold information | Workshop deposit is 50% of package price; link validity is 10 minutes and the pending capacity hold is at most 15 minutes. |
| Confirmation / QR notice | QR is available only after verified deposit confirmation; creating a booking or returning from VNPay is insufficient. |
