### x.3 Notification Dispatch Job (`NOTIFICATION_DISPATCH`)

*Trigger*: A committed workflow creates a pending email task, or a previous mail-delivery attempt is due for retry.

*Purpose*: Deliver account-security and workflow messages without changing the business decision that created the message.

*Processing steps*:

    1. Select a pending notification with its recipient, template, subject, and minimum required data.
    2. Render the approved HTML/text template, including OTP, verification token, payment details, or QR ticket when applicable.
    3. Send the message through the configured Mland-domain SMTP service.
    4. Record the delivery result, provider reference, attempt count, and next retry time.

*Failure behavior*:

|Scenario|System behavior|
|---|---|
|SMTP timeout or temporary failure|Keep the business workflow state unchanged, retry with bounded backoff, and record the delivery failure.|
|Invalid recipient or permanent rejection|Mark the notification as failed, keep the already-committed business result, and expose the failure for operational follow-up.|
|Duplicate delivery attempt|Use the notification idempotency key and do not create a second task for the same event.|
