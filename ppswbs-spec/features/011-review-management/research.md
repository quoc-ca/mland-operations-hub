# Feature-011 Research — Review Management

Date created: 2026-11-03  
Date updated: 2026-11-03  
Author: Ngô Tiến Đạt  
Last updated by: Ngô Tiến Đạt

## Status

`SPEC/DESIGN READY FOR REVIEW`. This document records the decisions supplied for Feature-011 and the remaining implementation dependencies. It is not evidence that the feature is approved for production code.

## Context and evidence

| Topic | Evidence or decision | Consequence |
|---|---|---|
| Review target | User decision: only workshop sessions and retail products | No branch review and no Google Places review persistence. |
| Workshop eligibility | Attendance ticket is `CHECKED_IN` | `NO_SHOW`, cancelled and not-checked-in sources are rejected. |
| Retail eligibility | Order line belongs to an order in `PICKED_UP` or `DELIVERED` fulfilment state | Unpaid, cancelled and unfulfilled lines are rejected. |
| Identity | Member uses authenticated ownership; Guest supplies email or phone | Guest lookup is intentionally not OTP-protected and has no V1 rate limit. This is a known abuse risk, not an omitted requirement. |
| Publication | Valid technical input becomes `PUBLIC` immediately | No pending queue and no AI moderation. Staff/Manager perform post-publication moderation. |
| Moderation | Staff may hide any review; Manager may hide, remove and reply | This is a deliberate Feature-011 exception to ordinary branch-scoped staff operations. |
| Editing | Review owner may edit while retaining `PUBLIC` | Each edit creates revision/audit evidence; old content is not silently overwritten. |
| Public identity | Partially masked display name only | Email, phone and source transaction data never appear in public responses. |
| External reviews | Google Places is read-only and separate | Feature-011 never imports or writes back Google review content. |

## Why the previous internal-review rule changes

The former general wording that prohibited Mland from collecting an internal review is superseded only for the two approved targets: a workshop session and a retail product. The purpose is to close the post-attendance and post-purchase feedback gap inside Mland. This does not authorize reviews of a branch/location, nor does it create a Google Places review integration.

## Abuse and integrity trade-offs

Guest email/phone lookup without OTP or rate limiting reduces friction but increases the risk of impersonation, repeated submissions and source enumeration. Mitigations selected for V1 are exact source matching, generic no-eligible-source responses, source ownership checks, one-review-per-source uniqueness, idempotency, audit history and public masking. Adding OTP, rate limiting or reputation scoring remains a future decision and must not be silently added by implementation.

## Dependencies and unknowns

1. A source facade must verify workshop attendance and retail fulfilment without direct repository coupling.
2. Physical mapping for attendance tickets and retail order lines is not yet approved.
3. Aggregate strategy is now fixed for V1 as a realtime query over `PUBLIC` rows; projection is deferred.
4. Migration version must be selected after checking every Flyway version in the repository and the target database history.
5. The API names in the contract are proposed until `ppswbs-spec/APIs.md` is reviewed and synchronized.
6. The current Report 3 review-list screen is not proof that Feature-011 is approved; it is a related baseline to update.
7. Guest lookup-to-create binding is not yet specified. A server-issued opaque eligibility proof is the recommended direction, but its field, lifetime, replay policy and storage are still TBD; direct client-submitted source IDs are not sufficient ownership proof.

## Research conclusion

The feature is ready for cross-artifact review, not code generation. No unresolved item above changes the already supplied business behavior, but source-facade DTOs, Guest handle security details, migration selection and final API ratification still block a production-ready implementation contract.
