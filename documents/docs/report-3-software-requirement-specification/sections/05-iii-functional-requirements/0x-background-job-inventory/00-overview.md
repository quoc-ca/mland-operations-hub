## x. Background Job Inventory

Background jobs run inside the V1 Spring Boot modular monolith. Jobs must be idempotent, preserve the owning module's business audit evidence, and retry only operations that are safe to repeat. Internal events or pending work records may trigger a job; Kafka and microservices are not implied.

|#|Job Name|Trigger|Purpose|
|---|---|---|---|
|1|Payment Hold Expiry and Release Job (`PAYMENT_HOLD_EXPIRY`)|Scheduled check for expired payment links and business holds|Releases workshop capacity, retail quantity, or custom-order queue slots when valid payment confirmation is not received in time.|
|2|Workshop Integration Synchronization Job (`WORKSHOP_INTEGRATION_SYNC`)|Schedule/capacity change, confirmed booking, or retry time|Synchronizes approved workshop availability and confirmed-booking updates with Klook.|
|3|Notification Dispatch Job (`NOTIFICATION_DISPATCH`)|Committed workflow creates a pending email task or retry is due|Sends account-security and workflow emails through the Mland SMTP service.|
|4|AI and Reference Media Retention Cleanup Job (`AI_MEDIA_RETENTION_CLEANUP`)|Daily schedule or immediate cleanup request|Deletes AI data and reference images when the approved retention rules allow deletion.|
|5|Delivery Data Anonymization Job (`DELIVERY_DATA_ANONYMIZATION`)|Daily check for GHTK handoffs older than 30 days|Removes or irreversibly masks recipient and delivery-address data while preserving audit evidence.|
|6|Klook Booking Reconciliation Job (`KLOOK_BOOKING_RECONCILIATION`)|Klook callback, ambiguous synchronization result, or retry time|Resolves approved Klook confirmations exactly once without duplicate bookings or capacity deductions.|
