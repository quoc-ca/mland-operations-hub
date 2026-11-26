# Feature map

Each feature or subfeature keeps its own specification and may keep a local
`research.md` when research evidence is useful. The feature folders below are
buildable slices; they do not imply separate applications, services, or
databases.

| Feature | Ownership | Status |
| --- | --- | --- |
| [`003-workshop-management`](003-workshop-management/README.md) | Parent feature group for the workshop lifecycle. | Contains four independently planned workshop subfeatures. |
| [`001-account-management`](001-account-management/spec.md) | FE-01 account-management group; current spec/plan/tasks cover only the existing Member Profile and authentication journey. | Existing approved profile scope/design; broader FE-01 scope is not implied. |
| [`004-ai-chatbox-analysis-and-suggestion`](004-ai-chatbox-analysis-and-suggestion/spec.md) | FE-04 logical name `004-custom-design-request-and-order-intake`: Member custom-design intake, clarification, human review and quote handoff. | Contract/Figma approval and implementation pending. Legacy directory retained temporarily. |
| [`002-product-management`](002-product-management/spec.md) | FE-02 Product Management (backend module `productmanagement`): ready-ring products, fixed sizes/SKU, review and price approval, publication, categories and public catalogue search. | Draft spec/plan/tasks after speckit-analyze; not implemented. Waiting for 001's updated authentication contract. |
| [`006-loyalty-promotion`](006-loyalty-promotion/spec.md) | FE-06 Loyalty & Promotion (backend module `loyaltypromotion`): vouchers, automatic discounts, points wallet/ledger and loyalty policy. | Draft spec/plan/tasks after speckit-analyze; not implemented. Same 001 authentication dependency; depends on FE-02 T091 for the PRODUCT target provider. |
| [`007-notification-management`](007-notification-management/research.md) | FE-07 Notification Management: business email templates (SMTP); Firebase Auth keeps its own verification/reset emails. | Research only; no spec/plan/tasks yet. |
| [`009-administrative-parameter-configuration`](009-administrative-parameter-configuration/spec.md) | FE-09 Admin journey for Feature-012 ring-difficulty policy; Feature-012 retains policy/API ownership. | Proposed spec/design/tasks; Admin visual and Tech Lead approval pending. |
| [`012-workshop-ring-design-configuration`](012-workshop-ring-design-configuration/spec.md) | Member-owned saved workshop-ring design, Guest local preview, and proposed Admin-only difficulty policy; current flow has no booking attachment. | Draft — Member-only and Admin-policy design/contract gates remain; not implementation-ready. |
| [`013-staff-consultation-chat`](013-staff-consultation-chat/spec.md) | Human text consultation between Member customers and Staff/Manager, linked to approved design contexts. | Draft — SPEC/DESIGN ready for review; facade and migration approval pending. |
| [`014-navigation-shell`](014-navigation-shell/spec.md) | Rail, tabs, page header and count badges shared by Manager and Staff (split from the former `013-manager-home`; its dashboard blocks moved into 010). | Draft — wave 1 and the 2026-11-22 shell implemented; see `approval.md`. |

## 003 — Workshop Management

| Subfeature | Ownership | Status |
| --- | --- | --- |
| [`003.1-workshop-booking`](003-workshop-management/003.1-workshop-booking/spec.md) | Direct booking core: order, workshop item, deposit, capacity hold, attendance ticket and confirmation. | Existing draft; must be rebased to the workshop-item model before further implementation. |
| [`003.2-workshop-operations`](003-workshop-management/003.2-workshop-operations/spec.md) | Session configuration, capacity, Staff operations, ticket check-in, attendance finalization and no-show. | Spec draft; not implemented. |
| [`003.3-workshop-settlement-and-changes`](003-workshop-management/003.3-workshop-settlement-and-changes/spec.md) | Post-session bills/add-ons, in-store final payment, exceptional payment links, manual reschedule, cancellation and refund. | Spec draft; not implemented. |

The later `EACH_PARTICIPANT_RECEIVES_TICKET` delivery/transfer capability is not
created as a feature yet. V1 continues to deliver all attendance QR tickets to
the booking contact.
