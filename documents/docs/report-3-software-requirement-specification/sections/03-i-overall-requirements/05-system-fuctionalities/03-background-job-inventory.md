### 5.3 Background Job Inventory

|#|Job Name|Group|Description|
|---|---|---|---|
|1|Payment Hold Expiry and Release|Payment|Runs on a schedule to release expired workshop and retail holds. It verifies that no valid signed VNPay confirmation was applied, releases the related seat, quantity and benefits, and records an idempotent audit result.|
|2|Workshop Integration Synchronization (Retired)|Workshop Integration|Retired: there is no partner channel to synchronize (Stakeholder statement, Feature-003 owner, 2026-11-25).|
|3|Notification Dispatch|Notification|Runs when a committed workflow creates a pending email task or a retry becomes due. It renders the approved template, sends the message through Mland SMTP, records the provider result, and retries temporary delivery failures without changing the business result.|
|4|AI and Reference Media Retention Cleanup|Data Retention|Runs daily. It removes Basic AI (Gemini) text data older than 30 days and deletes remaining temporary reference images; catalogue media is never removed.|
|5|Delivery Data Anonymization|Fulfillment and Data Retention|Runs daily for retail or custom orders whose GHTK handoff was completed at least 30 days earlier. It irreversibly masks recipient and delivery-address data while preserving the order, handoff, payment, and required audit evidence.|
|6|Partner Booking Reconciliation (Retired)|Workshop Integration|Retired: there are no partner bookings to reconcile (Stakeholder statement, Feature-003 owner, 2026-11-25).|
|7|Retail Payment Reconciliation and Expiry|Payment|Runs every 30 seconds on a deployed server. A retail checkout with no payment started expires at its hold deadline (10 minutes for VNPay, 72 hours for cash at the counter). A checkout whose VNPay payment was opened but not confirmed is checked with VNPay QueryDR while VNPay is enabled; only a verified failure or expiry releases the reserved quantity, voucher, and points and returns the items to the cart. A payment that arrives after cancellation or expiry goes to the Manager reconciliation queue and never revives the order.|
