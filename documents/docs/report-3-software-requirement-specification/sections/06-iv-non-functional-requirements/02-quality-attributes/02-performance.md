### 2.2 Performance

The Reports 1–3 define business timing controls, but they do not approve a hosting size, concurrency target, throughput target, or page-latency SLO. The following requirements are therefore split between mandatory workflow timing and values to baseline before UAT.

| ID | Requirement                    | Target                                                                                                                                                                                                                                             | Condition / verification                                                                                         |
| --- | --- | --- | --- |
| NFR-PERF-01 | Payment-link expiry | 10 minutes after payment initiation.                                                                                                                                                                                                               | Verify persisted expiry and rejection of a late payment-link use.                                                |
| NFR-PERF-02 | Resource-hold expiry | At most 15 minutes for a workshop slot or retail cart. Custom-order queue-slot holds are outside the V1 scope.                                                                                                                                     | Verify that an unconfirmed hold is released at minute 15 and cannot be fulfilled by late evidence automatically. |
| NFR-PERF-03 | Hold cleanup | Expiry processing must be idempotent and must not release a resource after a valid signed confirmation has been applied.                                                                                                                           | Repeated-job and race-condition test.                                                                            |
| NFR-PERF-04 | Duplicate provider events | Duplicate VNPay, Klook, or synchronization results must not duplicate payment, booking, capacity deduction, or fulfilment state.                                                                                                                   | Replay the same event and compare the resulting record and audit evidence.                                       |
| NFR-PERF-05 | Synchronous user operations | Normal reads and writes must return a success or actionable failure without blocking on long-running AI, email, media-cleanup, or provider-reconciliation work. Long-running work must expose a pending state where applicable. | Integration and failure-path test; latency target is NFR-OPEN-04. |
| NFR-PERF-06 | AI and external-provider calls | Timeouts, retry limits, and concurrency limits must be configured per provider and recorded as deployment configuration. A timeout must not imply success. | Configuration review and fault-injection test; numeric values are NFR-OPEN-04. |
| NFR-PERF-07 | Capacity and load | The application must support the approved two-branch, three-daily-slot workshop model and the V1 ring/catalogue workflows. Peak concurrent users, request rate, data volume, and recovery objectives remain to be baselined from operational data. | Load test against NFR-OPEN-02 before production release. |
| NFR-PERF-08 | Background jobs | Payment-hold cleanup, Klook synchronization/reconciliation, AI/reference-media cleanup, and delivery-data anonymization must be safely repeatable; a failed run must be observable and retryable without duplicating business effects.             | Job replay, failure, and monitoring tests.                                                                       |

#### Baselines Requiring Approval

These are tracked open decisions, not approved service-level promises. They must be baselined and evidenced before the corresponding UAT or production gate.

| ID | Open baseline | Required evidence |
| --- | --- | --- |
| NFR-OPEN-01 | Supported browser/device matrix, QR scanner coverage, accessibility conformance level, and approved language glossary. | Product/UAT decision and a tested browser/device checklist. |
| NFR-OPEN-02 | Production topology, availability target, peak concurrency/request rate/data volume, backup scope, and recovery objectives. | Operations-approved deployment and capacity/recovery baseline. |
| NFR-OPEN-03 | Provider production/sandbox access, callback URLs, contracts, billing, quotas, and provider go-live evidence. | Approved provider access and go-live checklist; no live capability is implied before approval. |
| NFR-OPEN-04 | Exact interface schemas/endpoints, identity/SSO configuration, provider timeouts/retry/concurrency limits, and synchronous latency SLO. | Approved integration/deployment configuration and test evidence for each provider. |
| NFR-OPEN-05 | Security release baseline, including TLS configuration, security headers, dependency patching, penetration-test scope, and incident-response timings. | Security-approved release-readiness criteria and verification evidence. |
