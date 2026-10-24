# Feature Specification: AI Chatbox Analysis and Suggestion

**Feature**: `004-ai-chatbox-analysis-and-suggestion`
**Status**: Draft implementation specification
**Source alignment**: Report 1 FE-04/FE-05, Report 3 AI Chatbox Analysis and Suggestion, `ppswbs-spec/spec-general.md`

## Scope and source alignment

This feature owns three separate capabilities:

1. Basic AI support for Guest and Member questions about approved public information.
2. Member-only workshop booking-design image analysis followed by Staff or Manager human review.
3. Manager-only advisory price suggestions for new workshop packages or retail products.

The feature does not own workshop booking creation, payment confirmation, custom-manufacturing acceptance, custom-manufacturing pricing, final catalogue publication, or delivery fulfilment. Those workflows consume only the recorded result or suggestion through their owning facades.

The Report 3 display roles are authoritative for this feature: `Guest`, `Member`, `Staff`, `Manager`, and `Admin`. Mapping these labels to physical role codes remains a separate approved identity decision; implementation must not silently map `Manager` to `OWNER` or `Admin` to `ADMIN_TECHNICAL`.

## Actors and permissions

| Actor | Permission |
| --- | --- |
| Guest | Use basic AI support with public catalogue, workshop, store-information and FAQ context. Cannot upload or submit a booking-design image. |
| Member | Use basic AI support; submit an image only inside an eligible workshop booking after affirmative consent; view only own analysis/review result. |
| Staff | Review booking-design requests within the delegated branch and value threshold, record Accepted, Rejected or Clarification Required with a reason. |
| Manager | Review higher-value or Manager-authority booking-design requests; request package/product price suggestions; accept, adjust or reject final catalogue prices. |
| Admin | Manage approved technical parameters, integration credentials and support configuration; cannot create business review decisions or publish prices through this feature. |
| AI API | Return text support, candidate image features/image quality, or advisory package/product price suggestions. It cannot mutate Mland business state. |
| Cloud Storage | Temporarily store booking-design image input and retain only according to the feature retention rule. |

## Permission Matrix

The following matrix is authoritative for Feature-04. `Allow` always requires the
actor to satisfy Firebase identity, Mland account status, role, resource ownership
and scope checks. A client-supplied role, reviewer, price, estimate or decision is
never trusted. Physical role-code mapping remains a separate approved decision.

| Action | Guest | Member | Staff | Manager | Admin |
| --- | --- | --- | --- | --- | --- |
| Basic AI support | Allow, public context only | Allow, public context only | Allow, public context only | Allow, public context only | Allow, public context only |
| Upload booking-design image | Deny | Allow, own eligible booking only | Deny | Deny | Deny |
| View own design result | Deny | Own data only | Deny | Deny | Deny |
| View scoped design request | Deny | Own data only | Branch/value scope | Branch/authority scope | Technical/audit evidence only if authorized |
| Review booking-design request | Deny | Deny | Up to 3,000,000 VND within scope | Above 3,000,000 VND or Manager-authority request | Deny |
| Record review decision | Deny | Deny | Allow within scope and separation-of-duties rules | Allow within scope and separation-of-duties rules | Deny |
| Request price suggestion | Deny | Deny | Deny | Allow for authorized new package/product proposal | Deny |
| View internal price suggestion | Deny | Deny | Deny by default | Authorized proposal scope | Technical/audit evidence only if authorized |
| Confirm final catalogue price | Deny | Deny | Deny | Allow through catalogue workflow | Deny |
| Publish catalogue price | Deny | Deny | Deny | Through Catalogue facade only | Deny through Feature-04 |
| Configure provider credentials | Deny | Deny | Deny | Deny | Allow technical configuration |
| Mutate booking/payment/custom order | Deny through Feature-04 | Deny through Feature-04 | Deny through Feature-04 | Deny through Feature-04 | Deny through Feature-04 |

### Permission conditions

- Staff may review only requests in the delegated branch/scope whose estimated value
  is at or below 3,000,000 VND.
- Manager may review requests above 3,000,000 VND or requests explicitly requiring
  Manager authority.
- A reviewer must not review a request created by the reviewer or a request where
  the reviewer is the protected beneficiary.
- Admin may inspect technical/audit evidence only when the configured permission
  allows it. Admin cannot replace Manager business approval.
- AI Provider and Cloud Storage are infrastructure actors, not Mland business
  actors. Neither receives permission to mutate Mland business state.
- Price suggestions are internal evidence. Guest, Member and Staff do not receive
  the suggestion projection; a customer sees a price only after Manager confirmation
  and successful catalogue publication.

### Endpoint permission mapping

| Endpoint | Allowed actor | Denied or restricted actor | Scope/business condition | Denied side effect |
| --- | --- | --- | --- | --- |
| `POST /api/v1/ai-support/messages` | Guest, Member, authorized internal actors | None for public-support scope; unsupported business requests are rejected | Public catalogue, workshop, store-information or approved FAQ context | No booking, payment, review, order or price mutation |
| `POST /api/v1/design-requests` | Authenticated eligible Member | Guest, Staff, Manager, Admin | Own package/location/session/booking context, current consent and valid design version | No storage/provider call when authorization or validation fails |
| `GET /api/v1/design-requests/{id}` | Owning Member, scoped Staff/Manager, authorized Admin audit view | Other Members and out-of-scope staff | Ownership, branch/delegation and authority scope | No raw image, raw provider payload or another Member's data |
| `GET /api/v1/design-review-requests` | Staff and Manager | Guest, Member, Admin unless approved audit view | Reviewer branch/delegation/value/authority scope | No mutation |
| `POST /api/v1/design-review-requests/{id}/decisions` | Scoped Staff or Manager | Guest, Member, Admin and out-of-scope reviewer | Current version, non-terminal state, separation of duties | No automatic booking/payment/custom-order mutation |
| `POST /api/v1/price-proposals/{id}/ai-suggestions` | Authorized Manager | Guest, Member, Staff, Admin | New workshop package or retail product proposal | No price application or publication from AI response alone |

## Validation Matrix

All protected operations MUST validate in this order:

```text
Transport
→ Firebase identity
→ Mland role/status
→ Resource ownership/scope
→ Rate limit/idempotency
→ Input schema
→ Business context
→ Security screening
→ Provider call
→ Provider output validation
→ Persistence/state transition
```

A failed validation stops the pipeline immediately. A provider call MUST NOT occur
when a preceding validation fails.

### Basic AI support validation

| Validation | Error | Provider call | Persistence | Response |
| --- | --- | --- | --- | --- |
| Empty prompt | `AI_INPUT_INVALID` | No | None | 400/422 |
| Prompt exceeds configured maximum length | `AI_INPUT_INVALID` | No | None | 400/422 |
| Unsupported topic | `AI_SCOPE_UNSUPPORTED` | No | Safe interaction evidence only | 422 |
| Private data or business-decision request | `AI_CONTEXT_NOT_ALLOWED` | No | Minimal safe audit evidence | 422 |
| Prompt injection | `AI_PROMPT_INJECTION_DETECTED` | No | Minimal safe audit evidence | 422 |
| Basic AI rate limit | `AI_RATE_LIMITED` | No | Safe rate-limit evidence | 429 |
| Provider timeout/unavailable | `AI_UNAVAILABLE` | Attempted | Failure evidence only | 503 |
| Empty, unsafe or policy-violating output | `AI_OUTPUT_POLICY_VIOLATION` | Yes | No raw output | 422 |
| Valid public question and validated response | None | Yes | Minimized interaction with retention deadline | 200 |

### Booking-design image validation

| Order | Validation | Error on failure | Provider call | Side effect on failure |
| ---: | --- | --- | --- | --- |
| 1 | Firebase identity | `AUTHENTICATION_REQUIRED` | No | No storage/request creation |
| 2 | Member role and active status | `FORBIDDEN_ROLE` | No | No storage/request creation |
| 3 | Idempotency key format and duplicate lookup | `AI_INPUT_INVALID` or existing result | No for duplicate | Duplicate returns existing result |
| 4 | Member image rate limit | `IMAGE_RATE_LIMITED` | No | No storage/request creation; return `Retry-After` |
| 5 | `bookingCode` format and existence | `BOOKING_CONTEXT_INVALID` | No | No storage/request creation |
| 6 | Package exists and is eligible | `BOOKING_CONTEXT_INVALID` | No | No storage/request creation |
| 7 | Location/session exists and belongs to selected package | `BOOKING_CONTEXT_INVALID` | No | No storage/request creation |
| 8 | Booking ownership and active context | `BOOKING_CONTEXT_INVALID` | No | No storage/request creation |
| 9 | Design path supports booking image | `AI_FEATURE_NOT_APPLICABLE` | No | No storage/request creation |
| 10 | Consent version, purpose, actor and time | `CONSENT_REQUIRED` | No | No storage/request creation |
| 11 | `designVersion` is current and positive | `DESIGN_VERSION_STALE` | No | No storage/request creation |
| 12 | Image is present and has valid MIME/file signature | `IMAGE_INVALID` | No | No storage/request creation |
| 13 | File size and dimensions are within configuration | `IMAGE_TOO_LARGE` or `IMAGE_INVALID` | No | No storage/request creation |
| 14 | Malware/security scan and ownership check | `IMAGE_INVALID` | No | No storage/request creation |
| 15 | Image metadata is minimized and provider context is allowlisted | `AI_INPUT_INVALID` or `AI_CONTEXT_NOT_ALLOWED` | No | No storage/request creation |
| 16 | Prompt-injection/security screening | `AI_PROMPT_INJECTION_DETECTED` | No | No storage/request creation |
| 17 | Temporary storage succeeds | `AI_UNAVAILABLE` | No | No provider request |
| 18 | Gemini response matches result schema and policy | `AI_RESPONSE_INVALID` or `AI_OUTPUT_POLICY_VIOLATION` | Yes | Delete image; record safe failure |
| 19 | Estimate snapshot and review routing are valid | `AI_INPUT_INVALID` or routing error | Yes | No review decision |
| 20 | Persistence and state transition succeed | `AI_UNAVAILABLE` or transaction error | Yes | Retry-safe cleanup and no duplicate decision |

The image rate-limit rule is fixed: scope is authenticated Member ID, the window is
rolling five minutes, and the limit is three image-submission attempts. The fourth
attempt returns HTTP 429 with `IMAGE_RATE_LIMITED` and `Retry-After`; it performs no
storage call, Gemini call or request creation. Missing consent, invalid context and
invalid file requests after identity verification still consume an attempt.
Duplicate idempotent retries return the existing command result and do not consume a
new quota slot. The quota check/reservation is atomic.

### Human review validation

| Validation | Error | Required behavior |
| --- | --- | --- |
| Review request exists and is visible | `REVIEW_NOT_AUTHORIZED` or `NOT_FOUND` | Do not reveal another request's data |
| Reviewer has Staff/Manager role and active status | `REVIEW_NOT_AUTHORIZED` | No decision mutation |
| Request is in reviewer branch/delegation/value scope | `REVIEW_NOT_AUTHORIZED` | No decision mutation |
| Staff threshold is at or below 3,000,000 VND | `REVIEW_NOT_AUTHORIZED` | Route higher value to Manager |
| Manager authority applies when required | `REVIEW_NOT_AUTHORIZED` | Staff cannot decide Manager-authority request |
| Reviewer is not creator or protected beneficiary | `REVIEW_NOT_AUTHORIZED` | No self-review or conflict review |
| Request has no terminal decision | `REVIEW_STATE_CONFLICT` | Reject competing decision |
| `designVersion` is the current version | `DESIGN_VERSION_STALE` | Require current version |
| Decision is `ACCEPTED`, `REJECTED` or `CLARIFICATION_REQUIRED` | `AI_INPUT_INVALID` | Reject unknown enum |
| Reason is present and within configured length | `AI_INPUT_INVALID` | Do not persist incomplete decision |
| Decision and audit evidence commit atomically | `REVIEW_STATE_CONFLICT` on race | At most one terminal decision |

### AI price suggestion validation

| Validation | Error | Provider call | Side effect on failure |
| --- | --- | --- | --- |
| Actor is authenticated Manager | `FORBIDDEN_ROLE` | No | No suggestion |
| Manager owns or is authorized for proposal | `REVIEW_NOT_AUTHORIZED` | No | No suggestion |
| Proposal is a new workshop package or retail product | `AI_FEATURE_NOT_APPLICABLE` | No | No suggestion |
| Target is not custom-manufacturing, Member quotation, invoice or payment | `AI_FEATURE_NOT_APPLICABLE` | No | No suggestion |
| Materials/components/pricing context are complete | `PRICE_INPUT_INCOMPLETE` | No | Manager can complete inputs |
| Input excludes private data and uses allowlisted context | `AI_CONTEXT_NOT_ALLOWED` | No | No provider call |
| Provider output is numeric, non-negative and within configured range | `PRICE_SUGGESTION_INVALID` | Yes | Store failure evidence only |
| Response has advisory flag and valid schema | `AI_RESPONSE_INVALID` | Yes | Do not expose invalid response |
| Manager decision is recorded | `REVIEW_STATE_CONFLICT` on race | No additional call | Do not apply/publish price |
| Catalogue facade confirms application/publication | Owning catalogue error | No | Feature-04 cannot publish directly |

## Journey rules

The workshop booking order is:

```text
Workshop package
→ Location and session
→ Booking information
→ Design path
→ Booking summary
→ Payment
```

The image branch is available only to a Member with an eligible booking context. Consent is recorded before temporary storage or provider processing. A material design change creates a new design/review version and invalidates the previous decision for continuation.

## Invariant Rules and Security Policies

The following rules are mandatory implementation invariants. They apply to browser
requests, fallback forms, API clients, retries and internal adapters. Client-provided
role, price, status, reviewer, estimate and decision values are never trusted.

### Authorization and business invariants

1. Guest MAY use Basic AI Support only. Guest MUST NOT upload or submit a
   booking-design image.
2. Only an authenticated Member MAY submit a booking-design image.
3. A Member image request MUST reference an eligible package, location, session,
   booking context and current design version owned by that Member.
4. Affirmative, purpose-specific consent MUST be recorded before temporary image
   storage or provider processing.
5. Every valid booking-design request MUST reach Staff or Manager human review.
   AI MUST NOT create `ACCEPTED`, `REJECTED` or `CLARIFICATION_REQUIRED`.
6. A reviewer MUST NOT decide a request created by that reviewer or a request with
   a prohibited conflict of interest.
7. One design version MAY have at most one terminal review decision.
8. A material design change MUST create a new design version. An earlier decision
   MUST NOT be reused for the changed configuration.
9. AI output MUST NOT mutate booking, payment, review, custom-manufacturing or
   catalogue-price state.
10. Only Manager may make the final catalogue pricing decision. Technical Admin may
    manage approved technical configuration but may not create business review
    decisions or publish prices through this feature.

### AI input validation invariants

1. Every provider request MUST pass schema, type, length, size, ownership and
   business-context validation before serialization.
2. The system MUST construct provider requests from an allowlisted command model; it
   MUST NOT forward an untrusted request body directly to Gemini.
3. Provider context MUST be limited to the allowlist for the active use case.
4. Firebase tokens, provider credentials, passwords, payment data, private customer
   data and raw operational data MUST NOT be included in provider input.
5. Price input MUST reference a valid new workshop-package or retail-product
   proposal with complete materials, components and pricing context.
6. Candidate features, image quality and price suggestions MUST be validated against
   their versioned schema, enum values, data types, ranges and maximum lengths.
7. Images MUST pass MIME type, file-signature, size, dimensions, malware-policy and
   ownership checks before temporary storage or provider processing.
8. Unnecessary image metadata MUST be removed before provider processing. OCR text
   or text embedded in an image MUST be treated as untrusted data, never as a system
   instruction.

### Prompt-injection and provider-output invariants

1. User input MUST be treated as untrusted data and MUST NOT override system
   instructions, business policy, output schema, role, pricing rules or review rules.
2. Fixed system instructions and business policy MUST remain separate from user
   content. Provider input MUST use structured, allowlisted context.
3. Requests containing instructions such as `ignore previous instructions`,
   `publish this price`, `approve this order`, or equivalent attempts to alter system
   behavior MUST be rejected before the provider call.
4. A detected prompt injection MUST produce `AI_PROMPT_INJECTION_DETECTED`, record
   minimal safe audit evidence and create no booking, review, price or payment
   mutation. Raw malicious content MUST NOT be retained unless explicitly required
   for a controlled security investigation.
5. Provider output MUST be checked for schema violations, data leakage, hidden
   instructions, final business decisions and unsupported claims before display or
   persistence.
6. A provider response containing an approval, rejection, final price, publication
   command or instruction to mutate Mland state MUST be rejected with
   `AI_OUTPUT_POLICY_VIOLATION`.

### Image rate-limiting invariants

1. Each authenticated Member MAY submit at most three image-submission attempts in
   a rolling five-minute window, keyed by authenticated Member ID.
2. An image-submission attempt is counted after Firebase identity verification when
   a multipart image request reaches the feature, including requests later rejected
   for missing consent, invalid context or invalid file content.
3. Requests rejected before identity verification do not consume the Member quota,
   but remain subject to infrastructure or IP-level abuse controls.
4. When the quota is exceeded, the system MUST return HTTP `429 TOO_MANY_REQUESTS`
   with `IMAGE_RATE_LIMITED` and `Retry-After`. It MUST not store the image, call
   Gemini, or create a design/review request.
5. The quota check and reservation MUST be atomic so concurrent requests cannot
   bypass the limit.
6. A duplicate retry identified by the same idempotency key MUST return the existing
   command result and MUST NOT consume another quota slot.

### Data retention and visibility invariants

1. Booking-design images MUST be temporary and MUST be deleted after provider
   processing on both success and failure paths.
2. Raw provider payloads MUST NOT be persisted unless a separately approved audit
   requirement requires a minimized safe projection.
3. The retained analysis projection is limited to candidate features, image quality,
   schema version, provider correlation ID, processed time and safe failure evidence.
4. Text prompts and responses MUST expire within 30 days.
5. Internal price suggestions MUST never be visible to Guest or Member.
6. A price suggestion MAY be viewed by the authorized Manager responsible for the
   proposal. Admin may view only approved technical or audit evidence. Staff has no
   default access to the internal suggestion.
7. A customer MAY see a price only after Manager confirmation and successful
   catalogue publication through the owning catalogue workflow.

### Required security error codes

```text
AI_INPUT_INVALID
AI_PROMPT_INJECTION_DETECTED
AI_CONTEXT_NOT_ALLOWED
AI_OUTPUT_POLICY_VIOLATION
IMAGE_RATE_LIMITED
```

## User scenarios and testing

### User Story 1 — Ask basic AI support (P1)

As a Guest or Member, I want to ask a supported natural-language question so that I can receive basic guidance before continuing with a platform workflow.

#### Acceptance scenarios

1. Given a supported product, workshop, store-information or FAQ question, when the actor submits it, then the system sends only permitted public context to the AI API and displays a validated response.
2. Given an empty, overlong, unsupported or rate-limited request, when the actor submits it, then the system does not call the AI API and displays safe retry guidance.
3. Given the AI API is unavailable, times out, or returns an empty/unsafe response, when the request is processed, then the system shows a temporary or fallback message and creates no booking, payment, price, order or review decision.
4. Given the actor asks AI to confirm a transaction, price, feasibility or custom order, when the request is processed, then the system refuses the business decision and directs the actor to the relevant workflow or Staff.

### User Story 2 — Submit and review a booking-design image (P1)

As a Member, I want to submit a consented reference image after selecting my package, location, session and booking information so that the system can extract candidate features and send the request to the authorised human reviewer.

#### Acceptance scenarios

1. Given a Guest selects an image-based design path, when the Guest attempts to continue, then the server denies the operation and does not store or send an image.
2. Given a Member has no eligible booking context, when the Member attempts to submit an image, then the server denies the operation and returns a typed context error.
3. Given a Member has not affirmatively accepted the current consent version, when the Member submits an image, then the server rejects the request before storage or provider processing.
4. Given a Member submits a valid image with consent, when the request is created, then the system stores the image temporarily, records one analysis request and sends only the permitted image/context to the AI API.
5. Given Gemini returns valid candidate features and image quality, when processing completes, then the system validates and stores the result, deletes the image input, calculates estimate context from Mland data, and creates a human review request.
6. Given the estimated value is at or below 3,000,000 VND, when routing completes, then the request is available to authorized Staff within scope.
7. Given the estimated value is above 3,000,000 VND or a Manager rule applies, when routing completes, then the request is available to an authorized Manager.
8. Given a reviewer records Accepted, Rejected or Clarification Required, when the decision commits, then the system records actor, current design version, reason, timestamp and audit evidence and exposes the permitted result to the owning Member.
9. Given the Member changes a material option after a decision, when the new design is submitted, then the system creates a new version and requires analysis/routing/review again.
10. Given the provider fails or returns an invalid result, when processing stops, then the system records failure, deletes temporary image input, creates no approval and permits a safe retry.
11. Given a Member has submitted three image attempts within the rolling five-minute window, when the Member submits a fourth image, then the system returns `429 IMAGE_RATE_LIMITED`, does not store the image, and does not call Gemini.
12. Given an image request is rejected for missing consent, invalid context or invalid file content after identity verification, when the request is processed, then the attempt still consumes the Member rate-limit slot.
13. Given concurrent image requests would exceed the Member quota, when they are processed, then the atomic quota check allows no more than three attempts in the window.
14. Given a Member retries the same accepted idempotency key, when the request is processed, then the system returns the existing result and does not consume another image quota slot.
15. Given a prompt contains an instruction to ignore system policy or change a business decision, when it is submitted, then the system returns `AI_PROMPT_INJECTION_DETECTED`, does not call Gemini, and does not mutate business state.
16. Given an AI response contains an approval, final price or publication command, when the response is validated, then the system rejects it with `AI_OUTPUT_POLICY_VIOLATION` and keeps business state unchanged.

### User Story 3 — Request an advisory catalogue price suggestion (P1)

As a Manager, I want an AI price suggestion for a new workshop package or retail product so that I can make a faster informed pricing decision.

#### Acceptance scenarios

1. Given the Manager has complete configured material, component and pricing inputs, when a suggestion is requested, then the system sends the approved pricing context to the AI API.
2. Given the AI API returns a valid suggestion, when the result is displayed, then it is clearly marked advisory and does not change or publish the catalogue price.
3. Given the Manager accepts, adjusts or rejects the suggestion, when the final price is confirmed, then the system validates the final price, records the Manager decision and applies/publishes it only through the separate catalogue workflow.
4. Given inputs are incomplete or inconsistent, when a suggestion is requested, then the system does not call the AI API and identifies the missing inputs.
5. Given the AI API is unavailable or returns an invalid/out-of-range suggestion, when processing fails, then the Manager may retry or enter a manual price; no invalid or AI-only price is saved.
6. Given the target is a custom-manufacturing order, Member quotation, final workshop invoice or payment amount, when a suggestion is requested, then the operation is denied.

## Functional requirements

### Basic AI support

- **FR-04-001**: The system MUST allow Guest and Member basic AI support only for approved public product, workshop, store-information and FAQ topics.
- **FR-04-002**: The system MUST validate non-empty input, configured maximum length, scope and rate limit before calling the AI API.
- **FR-04-003**: The system MUST provide the AI API only with permitted public context and MUST exclude private customer, payment, booking, authentication and operational data.
- **FR-04-004**: The system MUST validate the response before display and MUST not present AI output as a confirmed business answer.
- **FR-04-005**: Text prompts and responses MUST be retained for no more than 30 days and MUST not contain raw credentials, tokens or unnecessary PII.

### Booking-design image analysis

- **FR-04-006**: Only an authorized Member with an eligible package, location, session and booking context MAY submit a booking-design image.
- **FR-04-007**: The system MUST record affirmative consent version, purpose, Member identity, timestamp and design-request version before storage or AI processing.
- **FR-04-008**: The system MUST validate booking ownership, branch/session capability, file type, size, image quality, design version and rate limit before provider processing.
- **FR-04-009**: The system MUST use temporary storage for booking-design image input and MUST delete the image after processing, including provider failure paths where deletion is permitted.
- **FR-04-010**: The AI API MAY return image quality and candidate ring features/materials only. It MUST NOT return a final business decision.
- **FR-04-011**: The system MUST validate the provider response against a versioned result schema and record provider correlation and safe failure evidence without retaining raw provider payloads.
- **FR-04-012**: The system MUST keep technical analysis status separate from business review status.
- **FR-04-013**: Every valid request MUST route to human review. AI MUST NOT auto-accept or auto-reject a booking-design request.
- **FR-04-014**: Staff MAY review requests at or below 3,000,000 VND within scope. Manager MUST review requests above that threshold or requests requiring Manager authority.
- **FR-04-015**: A reviewer MUST record Accepted, Rejected or Clarification Required with reason, actor, current design version and timestamp.
- **FR-04-016**: A material design change MUST create a new analysis/review version and MUST prevent reuse of an earlier decision for the changed configuration.
- **FR-04-017**: A Member MAY read only the Member's own permitted analysis/review projection. Staff and Manager access MUST be scope- and authority-limited.

### Advisory price suggestion

- **FR-04-018**: Only a Manager MAY request an AI price suggestion for a new workshop package or retail product.
- **FR-04-019**: The system MUST validate completeness and consistency of configured inputs before calling the AI API.
- **FR-04-020**: The system MUST label the AI result advisory and MUST not modify or publish a price from the AI response alone.
- **FR-04-021**: The final Manager price MUST be validated, recorded with decision context, and applied/published through the owning catalogue workflow.
- **FR-04-022**: The feature MUST reject AI price suggestions for custom-manufacturing orders, individual Member quotations, final invoices and payment amounts.
- **FR-04-023**: Every provider request MUST be built from a validated, normalized and allowlisted command model; raw untrusted request bodies MUST NOT be forwarded.
- **FR-04-024**: The system MUST reject prompt-injection attempts before provider invocation and MUST return `AI_PROMPT_INJECTION_DETECTED` without business mutation.
- **FR-04-025**: The system MUST validate provider output for schema, policy, data leakage and unsupported business-decision content before display or persistence.
- **FR-04-026**: An authenticated Member MUST be limited to three image-submission attempts in a rolling five-minute window. The limit MUST be keyed by Member ID and enforced atomically.
- **FR-04-027**: A rate-limited image request MUST return HTTP 429 with `IMAGE_RATE_LIMITED` and `Retry-After`, without storage, provider invocation or request creation.
- **FR-04-028**: A duplicate idempotent image command MUST return the existing result and MUST NOT consume another image rate-limit slot.
- **FR-04-029**: Price suggestion visibility MUST be restricted to the authorized Manager, with Admin limited to approved technical/audit evidence and no default Staff access.

## State model

Technical analysis state:

```text
PENDING → PROCESSING → SUCCEEDED
                    └→ FAILED
```

Business review state:

```text
NEED_REVIEW → ACCEPTED
            ├→ REJECTED
            └→ CLARIFICATION_REQUIRED → new design version → NEED_REVIEW
```

An analysis failure has no business decision. A rejected version cannot continue to booking confirmation. A successful human decision does not itself confirm payment or create a custom-manufacturing order.

## API behavior

| Method | Route | Access | Result |
| --- | --- | --- | --- |
| `POST` | `/api/v1/ai-support/messages` | Guest/Member | Validated basic-support response or typed scope/rate/provider error. |
| `POST` | `/api/v1/design-requests` | Eligible Member | Creates one versioned booking-design request after consent/context validation. |
| `GET` | `/api/v1/design-requests/{id}` | Owning Member or authorized Staff/Manager | Returns permitted analysis/review projection; never raw provider payload or another Member's data. |
| `GET` | `/api/v1/design-review-requests` | Staff/Manager | Returns only requests in the reviewer's branch/delegation/value scope. |
| `POST` | `/api/v1/design-review-requests/{id}/decisions` | Authorized Staff/Manager | Records one allowed human decision for the current version. |
| `POST` | `/api/v1/price-proposals/{id}/ai-suggestions` | Manager | Returns advisory suggestion for a package/product proposal only. |

Protected requests require a verified Firebase ID token and Mland role/status authorization. Client-supplied role, price, status, reviewer, estimate or decision is never trusted.

Validation and security screening occur before any provider call. A rejected input,
prompt-injection detection or disallowed context returns a stable error code and does
not invoke Gemini. The image endpoint enforces the Member rolling rate limit and
returns `Retry-After` when the limit is exceeded.

### Idempotency and retry

- Image submission MUST accept a client command/idempotency key scoped to the authenticated Member and booking/design version.
- Repeating the same accepted command returns the existing request projection and MUST NOT create a second analysis/review request.
- A changed image or material design option requires a new version/key.
- Provider timeout may be retried only for the same request version; provider calls must carry a correlation ID and the application must prevent duplicate business decisions.
- Review decision submission MUST use the current version and a guarded state transition; a competing terminal decision receives a conflict response.
- Price suggestion retries create supporting suggestion evidence but never publish a price or overwrite a Manager decision.

### Error codes

The contract MUST expose stable, safe codes including:

`AUTHENTICATION_REQUIRED`, `FORBIDDEN_ROLE`, `BOOKING_CONTEXT_INVALID`, `DESIGN_VERSION_STALE`, `CONSENT_REQUIRED`, `IMAGE_INVALID`, `IMAGE_TOO_LARGE`, `IMAGE_QUALITY_INSUFFICIENT`, `IMAGE_RATE_LIMITED`, `AI_RATE_LIMITED`, `AI_INPUT_INVALID`, `AI_PROMPT_INJECTION_DETECTED`, `AI_CONTEXT_NOT_ALLOWED`, `AI_UNAVAILABLE`, `AI_RESPONSE_INVALID`, `AI_OUTPUT_POLICY_VIOLATION`, `AI_SCOPE_UNSUPPORTED`, `REVIEW_NOT_AUTHORIZED`, `REVIEW_STATE_CONFLICT`, `PRICE_INPUT_INCOMPLETE`, `PRICE_SUGGESTION_INVALID`, and `AI_FEATURE_NOT_APPLICABLE`.

## Logical data ownership

The feature plan must map these logical records to approved physical tables/migrations without introducing raw SQL:

| Logical record | Required evidence |
| --- | --- |
| AI text interaction | Actor scope, safe prompt/response projection, provider correlation, timestamps, retention deadline. |
| Booking-design request | Member, booking context, design version, analysis/review states, estimate snapshot and next action. |
| Image consent | Member, consent version, purpose, timestamp, request/design version and affirmative action. |
| Temporary image reference | Storage object reference, processing state, deletion outcome; no permanent booking-design image retention. |
| Analysis result | Versioned candidate feature/image-quality schema, provider correlation, safe failure reason and processed time. |
| Human review | Reviewer, role/scope, decision, reason, design version, timestamp and audit evidence. |
| Price suggestion | Target package/product, input snapshot/version, advisory output, provider correlation, Manager decision and publication/application linkage. |

The feature MUST not link booking-design review to custom-manufacturing acceptance or create payment records. The owning booking, catalogue, payment and custom-manufacturing modules remain responsible for their records.

## External provider boundary

- `AiTextSupportPort` sends approved public context and returns a text response.
- `ImageFeatureAnalysisPort` sends a temporarily stored, consented image and returns candidate features/image quality.
- `AiPriceSuggestionPort` sends complete package/product pricing inputs and returns an advisory suggestion.
- `ReferenceImageStoragePort` stores and deletes temporary image input.
- Provider adapters own credentials, request serialization, response validation, timeout and safe error mapping.
- No provider response directly mutates a business entity or publishes a price.

## Non-functional and security requirements

- Never log raw Firebase tokens, provider credentials, raw image content, raw signed provider payloads or unnecessary PII.
- Use server-side authorization for every protected operation.
- Store only minimized audit evidence and safe provider correlation data.
- Core forms/links must remain usable without htmx or JavaScript where the platform constitution requires a fallback.
- Cross-module access uses named facades; no controller, entity or repository is imported across modules.
- AI/image/provider calls must execute outside business database locks and must use retry-safe application boundaries.

## Non-goals

- Autonomous ring design generation.
- Exact image reconstruction or CAD/3D editing.
- AI final feasibility, price, acceptance or rejection decisions.
- Guest booking-design image upload.
- Custom-manufacturing quotation or order acceptance by AI.
- Payment creation or confirmation.
- Catalogue price publication without Manager confirmation.
- Delivery tracking, accounting or notification-centre implementation.

## Success criteria

- **SC-04-001**: All Guest image-upload attempts are denied without storage/provider calls.
- **SC-04-002**: 100% of valid booking-design requests have recorded consent, analysis result/failure, human reviewer outcome or explicit pending/failure state, and design version evidence.
- **SC-04-003**: No AI response alone changes booking status, review status, custom-order state, catalogue price or payment state.
- **SC-04-004**: Duplicate image commands and provider retries do not create duplicate requests or decisions.
- **SC-04-005**: Booking-design image input is deleted after processing and text interactions expire within 30 days.
- **SC-04-006**: Staff/Manager routing and authorization tests cover threshold, branch/delegation and Manager-authority cases.
- **SC-04-007**: Price suggestions remain advisory and manual Manager fallback works when Gemini is unavailable.

## Assumptions and unresolved implementation gates

- The exact physical role-code mapping remains a separate approved gate.
- File size, image dimensions, prompt length, rate-limit values and provider timeout values must be configured in the feature plan; no numeric values are invented here.
- The exact physical schema/migration mapping for analysis result JSON, consent and suggestion evidence must be reviewed against `DB.md` before implementation.
- Gemini production credentials, billing, model version and provider contract remain integration configuration gates.
