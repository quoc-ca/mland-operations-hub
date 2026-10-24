### 5.3 Background Job Inventory

|#|Job Name|Group|Description|
|---|---|---|---|
|1|Payment Hold Expiry and Release|Payment|Runs on a schedule to find expired payment links and workshop, retail-cart, or custom-order holds. It verifies that no valid signed VNPay confirmation was applied, expires the hold, releases the related seat, quantity, or queue slot, and records an idempotent audit result.|
|2|Workshop Integration Synchronization|Workshop Integration|Runs after an enabled schedule/capacity change, a confirmed booking, or a retry time. It sends approved availability or booking data to Klook, validates the response, records the synchronization result, and never changes Mland capacity when Klook fails.|
|3|Notification Dispatch|Notification|Runs when a committed workflow creates a pending email task or a retry becomes due. It renders the approved template, sends the message through Mland SMTP, records the provider result, and retries temporary delivery failures without changing the business result.|
|4|AI and Reference Media Retention Cleanup|Data Retention|Runs daily or after an immediate cleanup request. It removes Gemini text data older than 30 days, deletes AI image input after processing, and deletes rejected, withdrawn, or independently classified reference images when permitted by the retention rules.|
|5|Delivery Data Anonymization|Fulfillment and Data Retention|Runs daily for retail or custom orders whose GHTK handoff was completed at least 30 days earlier. It irreversibly masks recipient and delivery-address data while preserving the order, handoff, payment, and required audit evidence.|
|6|Klook Booking Reconciliation|Workshop Integration|Runs when a Klook callback or synchronization result is ambiguous, duplicated, or due for retry. It verifies the required Mland availability/hold flow, applies an accepted result exactly once, and prevents duplicate bookings or capacity deductions. Klook cancellation events are not used to release Mland capacity.|
