# Data Model: AI Chatbox Analysis and Suggestion

This document defines logical ownership for Feature-04. It does not authorize a migration or claim that the physical schema already exists. A later feature plan must map these records to the approved MySQL/JPA model and preserve existing `custom_design_requests`/`custom_design_reviews` semantics.

## Status separation

Technical provider processing and business review are separate fields:

```text
analysis_status: PENDING → PROCESSING → SUCCEEDED / FAILED
review_status:   NEED_REVIEW → ACCEPTED / REJECTED / CLARIFICATION_REQUIRED
```

`CLARIFICATION_REQUIRED` requires a new design version before another review. A provider failure does not create a rejection. A review decision does not confirm booking payment.

## Logical records

### AiTextInteraction

- `interaction_id`
- actor scope (`GUEST` or `MEMBER`), nullable Member reference where applicable
- sanitized prompt projection and validated response projection
- supported topic/scope result
- provider correlation ID
- created/retention timestamps
- safe failure code when no response is displayed

Raw tokens, credentials, private booking/order data and unnecessary PII are excluded. Retention is no more than 30 days.

### BookingDesignRequest

- `design_request_id`
- owning Member
- workshop booking/context reference
- package, location and session snapshot/reference
- immutable design version and idempotency key
- `analysis_status`
- `review_status`
- estimated-value snapshot and source/version
- candidate-feature result schema version
- current next action
- created/updated/processed timestamps

The record is requester-scoped for Member reads and reviewer-scoped for Staff/Manager reads.

### ImageConsent

- Member identity
- purpose (`WORKSHOP_BOOKING_DESIGN_ANALYSIS`)
- consent document/version
- affirmative action
- timestamp
- design-request/version reference

Consent must exist before temporary storage or provider processing. A label rendered on screen is not sufficient evidence.

### TemporaryImageReference

- storage object key/reference
- request/version reference
- upload validation state
- processing state
- created/deleted timestamps
- safe deletion outcome

Booking-design image content is deleted after processing, including failure cleanup paths where the storage provider permits it. This record must not become a permanent image archive.

### AnalysisResult

- result schema version
- image-quality classification
- candidate feature/material/component projection
- safe confidence/uncertainty metadata where provider supplies it
- provider correlation ID
- received/validated timestamps
- safe failure code/reason

The raw provider response and raw image are not retained as normal application data.

### HumanReviewDecision

- request/version reference
- reviewer identity and Report 3 role
- review scope/branch evidence
- decision: `ACCEPTED`, `REJECTED`, `CLARIFICATION_REQUIRED`
- reason
- estimate snapshot used for routing
- decision timestamp
- audit correlation ID

The creator/member/beneficiary separation rule must prevent a reviewer from reviewing their own request or a request where they are the protected beneficiary.

### PriceSuggestion

- package/product proposal reference
- input snapshot/version
- provider correlation ID
- advisory suggested value and explanation projection
- validation state
- Manager decision: accepted/adjusted/rejected/manual fallback
- final price and catalogue application/publication reference
- decision/audit timestamps

The record is not a custom-manufacturing quote and cannot directly update the applied catalogue price.

## Ownership and relationships

- Feature-04 owns provider interaction evidence, consent, analysis projection and suggestion evidence.
- Workshop Booking owns booking, package/session, capacity, deposit and payment state.
- Design Review owns the human review decision if the approved modular boundary assigns it there; Feature-04 calls its facade and never writes its repository directly.
- Catalogue owns package/product price application and publication.
- Custom Manufacturing owns custom-order pricing and acceptance; Feature-04 has no write path to it.
- Audit evidence is written by the domain that performs the business action; Feature-04 records provider/analysis evidence and review linkage required by its contract.

## Integrity rules

1. One idempotency key may create at most one request version for one authenticated Member and booking context.
2. A design version cannot be reviewed after a newer material version is created.
3. A terminal review decision cannot be silently overwritten.
4. An analysis result cannot change booking, payment, price or custom-order status.
5. AI price suggestion cannot publish or apply a price without Manager confirmation and the owning catalogue workflow.
6. Temporary image deletion must be retry-safe and observable through safe status/evidence.
