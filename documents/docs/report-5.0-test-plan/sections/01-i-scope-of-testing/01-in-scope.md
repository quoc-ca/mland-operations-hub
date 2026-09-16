## **In Scope**

The following are baseline acceptance scenarios to be converted into detailed test cases after the provider, data model and screen/API design are approved:

- Guest and Member can create a workshop booking/invoice and reach confirmation only after a verified 50% deposit.
- Guest is denied Member-only retail checkout and Member history; Member can access those approved journeys.
- Duplicate, delayed or invalid payment events do not create duplicate payment records or duplicate booking confirmation.
- A staff invoice adjustment records reason, actor, time and customer consent; the final balance remains unpaid until cash/bank settlement is recorded.
- Two Members cannot reserve the same ready ring after verified full payment.
- Retail delivery ends at the staff-recorded third-party handoff; no carrier API, carrier token, live tracking promise, return or shipping-refund workflow is exercised.
- Admin Technical is denied mutation of invoices, payments, orders and other business records.
