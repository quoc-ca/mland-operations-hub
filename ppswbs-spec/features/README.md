# Feature map

`research.md` is the umbrella business map for FE-03. The feature folders below
are buildable slices; they do not imply separate applications, services, or
databases.

| Feature | Ownership | Status |
| --- | --- | --- |
| `002-workshop-booking` | Direct booking core: order, workshop item, deposit, capacity hold, attendance ticket and confirmation. | Existing draft; must be rebased to the workshop-item model before further implementation. |
| `003-workshop-operations` | Session configuration, capacity, Staff operations, ticket check-in, attendance finalization and no-show. | Spec draft; not implemented. |
| `004-workshop-settlement-and-changes` | Post-session bills/add-ons, in-store final payment, exceptional payment links, manual reschedule, cancellation and refund. | Spec draft; not implemented. |
| `005-workshop-ota-connectivity` | OTA adapters, availability, tickets, channel pricing, OTA financial reconciliation and OTA add-ons. | Future spec; blocked from Klook go-live until commercial onboarding is confirmed. |
| `006-member-profile` | Member profile and authentication journey. | Existing approved scope/design. |
| `007-ai-chatbox-analysis-and-suggestion` | AI Chatbox analysis and suggestion. | Draft implementation specification. |

The later `EACH_PARTICIPANT_RECEIVES_TICKET` delivery/transfer capability is not
created as a feature yet. V1 continues to deliver all attendance QR tickets to
the booking contact.
