## **In Scope**

The following are baseline acceptance scenarios to be converted into detailed test cases after the provider, data model and screen/API design are approved:

- Guest and Member can create a workshop booking/invoice and reach confirmation only after a verified 50% deposit.
- Guest is denied Member-only retail checkout and Member history; Member can access those approved journeys.
- Duplicate, delayed or invalid payment events do not create duplicate payment records or duplicate booking confirmation.
- A staff invoice adjustment records reason, actor, time and customer consent; the final balance remains unpaid until cash/bank settlement is recorded.
- Two Members cannot reserve the same ready ring after verified full payment.
- Retail delivery ends at the staff-recorded third-party handoff; no carrier API, carrier token, live tracking promise, return or shipping-refund workflow is exercised.
- Admin Technical is denied mutation of invoices, payments, orders and other business records.
- A location/session cannot accept a booking until capacity is configured; a reschedule less than 24 hours before its session or to a full session is rejected, while an audited Staff/Owner no-show exception is retained.
- At check-in, only an unbooked additional person actually making the product creates the 100,000 VND fee; Staff attestation, customer consent, and Owner-approved waiver/change are auditable.
- A continuation booking is Staff-created only on customer request and available capacity; custody intake and release evidence, including code/QR release check, are retained.
- A configurator accepts only Owner-confirmed components. AI image intake requires consent; request lifecycle and simple/medium/advanced/impossible routing follow the approved boundary without AI price/feasibility decisions.
- A customer is sent to Google through the review link/QR. If governance prerequisites are met, Owner-triggered import is one-way, imported review visibility follows the approved rule, and payroll/bonus automation is absent.
- A ready-ring reservation does not expire automatically. Carrier handoff retains carrier, handoff reference, Staff actor, and timestamp; no carrier tracking, return, or shipping-refund workflow is exercised.
