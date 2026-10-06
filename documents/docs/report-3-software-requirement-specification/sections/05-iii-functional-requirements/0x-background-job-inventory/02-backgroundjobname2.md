### x.2 Workshop Integration Synchronization Job (`WORKSHOP_INTEGRATION_SYNC`)

*Trigger*: A Manager saves an enabled schedule/capacity change, a confirmed booking is ready for synchronization, or a previous Klook synchronization attempt is due for retry.

*Purpose*: Deliver approved workshop availability and confirmed-booking updates to Klook while keeping Mland as the source of truth for capacity and holds.

*Processing steps*:

    1. Select the affected branch, date, session, capacity, availability, or confirmed-booking record.
    2. Validate that the Klook integration is enabled and that the record has the required correlation/idempotency reference.
    3. Build and send the approved Klook payload.
    4. Validate the response, record the synchronization status, and mark the work complete only once.
    5. Keep Mland schedule, capacity, hold, and booking state unchanged when Klook rejects or cannot receive the update.

*Failure behavior*:

|Scenario|System behavior|
|---|---|
|Klook credentials or endpoint are not enabled|Record the synchronization as skipped/configuration-blocked and keep Mland operations available.|
|Timeout, temporary provider error, or rate limit|Keep the work pending and retry with bounded backoff; do not create a duplicate booking or capacity deduction.|
|Invalid or duplicate response|Do not apply the ambiguous response; record the correlation details for reconciliation and return the previously recorded result for duplicates.|
|Klook cancellation event|Do not consume the event or automatically release Mland capacity in V1.|
