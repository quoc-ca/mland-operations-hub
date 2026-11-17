# Feature-04 Entity Specification: Independent Design Analysis

**Date created**: 2026-10-24  
**Date updated**: 2026-11-05
**Author**: Ngô Tiến Đạt  
**Last updated by**: Ngô Tiến Đạt  

## Boundary

These entities represent an independent Design Request, not a workshop booking-design
request. No entity may reference booking, session, location, capacity, ticket,
payment, deposit or check-in data. Cross-feature context is represented only by an
opaque catalogue reference when approved.

## Entity ownership

| Entity | Owner | Purpose |
| --- | --- | --- |
| `DesignRequest` | Feature-04 | Independent request, owner, description, optional catalogue reference, version and lifecycle. |
| `ImageConsent` | Feature-04 | Purpose-specific affirmative image-processing consent. |
| `TemporaryImageReference` | Feature-04 | Temporary storage lifecycle and deletion evidence. |
| `DesignAnalysisResult` | Feature-04 | Safe structured AI evidence and provider correlation. |
| `HumanReviewDecision` | Feature-04 | Staff/Manager decision timeline for a request version. |
| `PriceSuggestion` | Feature-04 | Advisory Manager price evidence for a catalogue proposal. |
| `AiTextInteraction` | Feature-04 | Basic AI support interaction with 30-day expiry. |

## `DesignRequest`

- `id`: UUID primary key.
- `requestReference`: public-safe unique identifier.
- `ownerAccountId`: Required Member account identity.
- `ownershipType`: `MEMBER`.
- `description`: optional normalized text.
- `catalogueReference`: optional opaque reference, never a booking reference.
- `requestVersion`: positive version number.
- `requestStatus`: `ACTIVE`, `WITHDRAWN`, `COMPLETED`; current frontend does not expose withdrawal or completion commands.
- `analysisStatus`: `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`.
- `reviewStatus`: `NEED_REVIEW`, `ACCEPTED`, `REJECTED`, `CLARIFICATION_REQUIRED` when returned by the backend review projection.
- `nextAction`, `createdAt`, `updatedAt`: server-derived projection and audit timestamps.

Forbidden fields include `bookingId`, `bookingCode`, `bookingStatus`, `packageId`
as booking context, `locationId`, `sessionId`, `holdId`, `ticketId`, `paymentId`,
`depositId` and `designApprovalReference`.

## `ImageConsent`

Stores `designRequestId`, `consentVersion`, fixed purpose
`INDEPENDENT_DESIGN_IMAGE_ANALYSIS`, actor identity, affirmative action and server
timestamp. It must exist before image storage or provider processing.

## `TemporaryImageReference`

Stores request version, sanitized storage key, validated MIME/size metadata,
processing status, deletion timestamp and deletion outcome. It never stores a
permanent image archive. Images are temporary processing data and must be deleted
after processing on success and failure.

## `DesignAnalysisResult`

Stores schema version, optional image quality, candidate features, confidence/
uncertainty, safe explanation, provider correlation ID and processing timestamp.
It never stores raw provider payload or a final business decision.

## `HumanReviewDecision`

Stores request/version, reviewer account and role, one of `ACCEPTED`, `REJECTED` or
`CLARIFICATION_REQUIRED`, bounded reason and decision timestamp. One request version
has at most one terminal decision. A material revision creates a new version.

## `PriceSuggestion`

Stores opaque proposal ID, `WORKSHOP_PACKAGE` or `RETAIL_PRODUCT` target, advisory
flag, suggestion status, suggested price/explanation, Manager decision/final price,
requesting Manager identity, provider correlation and timestamps. AI cannot apply or
publish the price.

## Cross-feature references

The only allowed application-level dependencies are:

```text
designreview → accountmanagement::facade
designreview → catalogue::facade (only for approved opaque catalogue context)
```

No booking or workshop-operations facade is allowed.
