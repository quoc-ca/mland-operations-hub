# 003 — Workshop Management

This parent feature group contains the end-to-end workshop lifecycle. Its
subfeatures are deliberately separated so that booking, onsite operations,
settlement/change policy can be specified and implemented
independently while sharing the same workshop-item and attendance-ticket model.

| Subfeature | Responsibility |
| --- | --- |
| [`003.1-workshop-booking`](003.1-workshop-booking/spec.md) | Direct booking, deposit, capacity hold, ticket creation and confirmation. |
| [`003.2-workshop-operations`](003.2-workshop-operations/spec.md) | Session configuration, onsite operations, check-in, attendance finalization and no-show. |
| [`003.3-workshop-settlement-and-changes`](003.3-workshop-settlement-and-changes/spec.md) | Direct-booking open folio (deposit to checkout/closure), on-site add-ons, final payment, cancellation, refund and reschedule policy. |
| [Direct booking open-folio policy](direct-booking-open-folio-policy.md) | Cross-feature policy for direct booking payments, on-site add-ons and closure. |

OTA connectivity (former 003.4) was removed on 2026-11-25: see the
[decision record](ota-removal-2026-11-25.md).
