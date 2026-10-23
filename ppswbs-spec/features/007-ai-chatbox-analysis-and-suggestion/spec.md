# Feature Specification: AI Chatbox Analysis and Suggestion

**Branch**: `007-ai-chatbox-analysis-and-suggestion`  
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

### Idempotency and retry

- Image submission MUST accept a client command/idempotency key scoped to the authenticated Member and booking/design version.
- Repeating the same accepted command returns the existing request projection and MUST NOT create a second analysis/review request.
- A changed image or material design option requires a new version/key.
- Provider timeout may be retried only for the same request version; provider calls must carry a correlation ID and the application must prevent duplicate business decisions.
- Review decision submission MUST use the current version and a guarded state transition; a competing terminal decision receives a conflict response.
- Price suggestion retries create supporting suggestion evidence but never publish a price or overwrite a Manager decision.

### Error codes

The contract MUST expose stable, safe codes including:

`AUTHENTICATION_REQUIRED`, `FORBIDDEN_ROLE`, `BOOKING_CONTEXT_INVALID`, `DESIGN_VERSION_STALE`, `CONSENT_REQUIRED`, `IMAGE_INVALID`, `IMAGE_TOO_LARGE`, `IMAGE_QUALITY_INSUFFICIENT`, `AI_RATE_LIMITED`, `AI_UNAVAILABLE`, `AI_RESPONSE_INVALID`, `AI_SCOPE_UNSUPPORTED`, `REVIEW_NOT_AUTHORIZED`, `REVIEW_STATE_CONFLICT`, `PRICE_INPUT_INCOMPLETE`, `PRICE_SUGGESTION_INVALID`, and `AI_FEATURE_NOT_APPLICABLE`.

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
