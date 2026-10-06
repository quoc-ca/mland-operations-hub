### x.1 Payment Hold Expiry and Release Job (`PAYMENT_HOLD_EXPIRY`)

*Trigger*: Every minute, or at the configured scheduler interval, for payment links, workshop holds, retail-cart holds, and custom-order queue holds whose expiry time has been reached.

*Purpose*: Release capacity and queue slots that are still pending payment so expired holds cannot block later customers or be confirmed by a late payment event.

*Processing steps*:

    1. Find pending payment attempts and related holds whose `expiresAt` is in the past.
    2. Re-check that no valid signed VNPay confirmation has already been applied.
    3. Mark the payment attempt and business hold as expired and release the workshop capacity, retail quantity, or custom-order queue slot.
    4. Record an idempotent expiry/audit result and make the affected workflow available for a new attempt.

*Failure behavior*:

|Scenario|System behavior|
|---|---|
|Database or lock failure|Leave the hold unchanged and retry on the next scheduler run; do not release capacity based on a partial update.|
|The hold is already confirmed or released|Treat the operation as an idempotent no-op and keep the existing business state.|
|A late payment confirmation arrives after expiry|Reject it for fulfilment, record the reconciliation/audit result, and do not recreate the expired booking, order, or queue hold automatically.|
