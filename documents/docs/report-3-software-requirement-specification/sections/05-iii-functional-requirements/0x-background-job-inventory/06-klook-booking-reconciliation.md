### x.6 Klook Booking Reconciliation Job (`KLOOK_BOOKING_RECONCILIATION`)

*Trigger*: A Klook booking callback is received, a booking synchronization result is ambiguous, or a reconciliation retry is due.

*Purpose*: Resolve approved Klook booking confirmations without creating duplicate Mland bookings or consuming capacity outside the required Mland availability/hold flow.

*Processing steps*:

    1. Load the callback or synchronization record using its provider reference and idempotency key.
    2. Verify that the booking passed the Mland availability/hold confirmation flow and contains the required branch, session, participant, and reference data.
    3. Apply or return the previously recorded synchronization result exactly once.
    4. Make the accepted booking available to authorized workshop operations and record the correlation outcome.

*Failure behavior*:

|Scenario|System behavior|
|---|---|
|No valid Mland hold exists|Reject the confirmation path and do not create a booking or consume capacity.|
|Required booking data is missing|Record a validation failure and queue the item for operational correction.|
|Duplicate callback|Return the previously recorded result without creating another booking, transaction, or capacity deduction.|
|Klook cancellation event|Do not consume the cancellation or release Mland capacity automatically in V1.|
