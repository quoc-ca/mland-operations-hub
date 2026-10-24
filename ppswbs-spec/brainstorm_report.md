# Database review — original table set

## Current objective

The user explicitly requested retaining only the original tables shown in the main diagram of [ERD.md](../documents/ERD.md), supplementing fields, PK/FK and relationships in [DB.md](DB.md), without new entities.

The referenced main ERD contains 23 tables, despite being described by the user as 24. DB.md now retains exactly those 23 names. The supplementary junction diagram is outside that retained list.

## Approaches and decision

| Approach | Benefit | Cost / decision |
| --- | --- | --- |
| Add normalized workflow/junction tables | Strong relational integrity and independent histories | Rejected by the user's fixed-table instruction. |
| Keep the original tables and add typed fields or embedded snapshots | Meets the requested form and scope | Selected; JSON references lack SQL FK/unique enforcement per element, and some workflows remain TBD. |
| Keep every original field unchanged and only list gaps | Smallest edit | Does not provide the requested new relationships and consolidated workflow fields. |

## Result

- DB.md defines 23 original entities and 48 SQL FK relationships, preserving its Column / Type / Constraints, Indexes and Rules form.
- Retail and custom manufacturing share orders through order_type; payments and delivery_infors use order_id for both, with distinct deposit/balance and fulfilment rules.
- Added current-role FK, primary gemstone/attachment FKs, primary package material, primary Staff facilitator, order promotion/design/package references and booking-image linkage.
- Voucher usage remains on orders and its authoritative code remains on promotions; the existing promotion_locations junction and order_items are retained.
- Policy/cart, compatibility lists, adjustment/consent/custody, latest AI results and record-owned audit evidence use explicitly documented embedded fields where appropriate.
- Kept the immutable loyalty ledger, payment target XOR and Guest linkage optionality; did not pretend JSON IDs have SQL FK enforcement.

## Limits and risks

A single current role differs from the multi-role authentication feature. Multiple component types, branch/package compatibility and histories stored in JSON need application validation, locking, schema/version and retention decisions. Billing-on-booking and shared retail/custom orders require domain ownership review before implementation.

The restricted ERD does not fully specify persisted chat, reliable notification/integration queues, global parameter storage or unknown provider-event histories. These are explicit gaps, not claims of full Report 2/3 coverage.

Existing applied authentication/policy/token/audit tables are not deleted or migrated. The main ERD supplied the table inventory; documents/ERD.md itself was not changed and its old edges must be refreshed separately if a new drawing is needed.

## Validation and handoff

Checks cover the exact original entity set, duplicate columns, PK/FK targets, relation coverage, nullable/unique cardinality, Markdown table shape, relative links and UTF-8 content. No application code or migration is changed.

Implementation follows a feature specification after the outstanding role, owner, JSON-integrity and workflow decisions are resolved.
