# Feature map

Each feature or subfeature keeps its own specification and may keep a local
`research.md` when research evidence is useful. The feature folders below are
buildable slices; they do not imply separate applications, services, or
databases.

| Feature | Ownership | Status |
| --- | --- | --- |
| [`003-workshop-management`](003-workshop-management/README.md) | Parent feature group for the workshop lifecycle. | Contains four independently planned workshop subfeatures. |
| [`001-account-management`](001-account-management/spec.md) | FE-01 account-management group; current spec/plan/tasks cover only the existing Member Profile and authentication journey. | Existing approved profile scope/design; broader FE-01 scope is not implied. |
| [`004-ai-chatbox-analysis-and-suggestion`](004-ai-chatbox-analysis-and-suggestion/spec.md) | FE-04 AI Chatbox analysis and suggestion. | Draft implementation specification. |

## 003 — Workshop Management

| Subfeature | Ownership | Status |
| --- | --- | --- |
| [`003.1-workshop-booking`](003-workshop-management/003.1-workshop-booking/spec.md) | Direct booking core: order, workshop item, deposit, capacity hold, attendance ticket and confirmation. | Existing draft; must be rebased to the workshop-item model before further implementation. |
| [`003.2-workshop-operations`](003-workshop-management/003.2-workshop-operations/spec.md) | Session configuration, capacity, Staff operations, ticket check-in, attendance finalization and no-show. | Spec draft; not implemented. |
| [`003.3-workshop-settlement-and-changes`](003-workshop-management/003.3-workshop-settlement-and-changes/spec.md) | Post-session bills/add-ons, in-store final payment, exceptional payment links, manual reschedule, cancellation and refund. | Spec draft; not implemented. |
| [`003.4-workshop-ota-connectivity`](003-workshop-management/003.4-workshop-ota-connectivity/spec.md) | OTA adapters, availability, tickets, channel pricing, OTA financial reconciliation and OTA add-ons. | Future spec; blocked from Klook go-live until commercial onboarding is confirmed. |

The later `EACH_PARTICIPANT_RECEIVES_TICKET` delivery/transfer capability is not
created as a feature yet. V1 continues to deliver all attendance QR tickets to
the booking contact.
