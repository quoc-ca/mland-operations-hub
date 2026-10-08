### 2.2 Performance

The Reports 1–3 define business timing controls, but they do not approve a hosting size, concurrency target, throughput target, or page-latency SLO. The following requirements are therefore split between mandatory workflow timing and values to baseline before UAT.

| Requirement | Target | Condition / verification |
| --- | --- | --- |
| Payment-link expiry | 10 minutes after payment initiation. | Verify persisted expiry and rejection of a late payment-link use. |
| Resource-hold expiry | At most 15 minutes for a workshop slot, retail cart, or custom-order queue slot. | Verify that an unconfirmed hold is released at minute 15 and cannot be fulfilled by late evidence automatically. |
| Hold cleanup | Expiry processing must be idempotent and must not release a resource after a valid signed confirmation has been applied. | Repeated-job and race-condition test. |
| Duplicate provider events | Duplicate VNPay, Klook, or synchronization results must not duplicate payment, booking, capacity deduction, or fulfilment state. | Replay the same event and compare the resulting record and audit evidence. |
| Synchronous user operations | Normal reads and writes must return a success or actionable failure without blocking on long-running AI, email, media-cleanup, or provider-reconciliation work. Long-running work must expose a pending state where applicable. | Integration and failure-path test. Exact latency SLO is a pre-UAT baseline. |
| AI and external-provider calls | Timeouts, retry limits, and concurrency limits must be configured per provider and recorded as deployment configuration. A timeout must not imply success. | Configuration review and fault-injection test. Numeric values are a pre-UAT baseline. |
| Capacity and load | The application must support the approved two-branch, three-daily-slot workshop model and the V1 ring/catalogue workflows. Peak concurrent users, request rate, data volume, and recovery objectives remain to be baselined from operational data. | Load test against the approved baseline before production release. |
| Background jobs | Payment-hold cleanup, Klook synchronization/reconciliation, AI/reference-media cleanup, and delivery-data anonymization must be safely repeatable; a failed run must be observable and retryable without duplicating business effects. | Job replay, failure, and monitoring tests. |
