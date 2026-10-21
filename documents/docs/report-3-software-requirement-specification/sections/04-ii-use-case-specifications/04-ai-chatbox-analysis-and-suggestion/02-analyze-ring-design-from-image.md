### 4.2 Analyze Ring Design from Image

#### Primary Actors

Member

#### Secondary Actors

AI API, Staff, Manager

#### Description

As a Member, I want to submit a consented ring-design image within a package, location, and session-selected workshop booking so that the system can return candidate features and route the request to Staff or Manager human review. The AI output is advisory and cannot accept, reject, price, or determine manufacturing feasibility.

#### Preconditions

1. The platform is available.
2. The Member is authenticated and has an eligible workshop-booking journey.
3. The Member has not previously submitted a material design change for the same review version.
4. The approved AI API is configured and enabled.
5. The Member has not exceeded the configured image-analysis rate limit.

#### Normal Flow

**Analyze Ring Design from Image**

1. The Member selects the reference-image design path after selecting a package, location, session, and booking information.
2. The system displays the purpose-specific image-processing consent and supported image requirements.
3. The Member gives affirmative consent and submits a reference image.
4. The system validates Member authorization, booking ownership, branch/session capability, consent, file type, file size, image quality, design version, and rate limit.
5. The system stores the image temporarily, creates one analysis request, and sends the permitted image request to the AI API.
6. The AI API returns image quality information and candidate design features or materials only.
7. The system validates the provider response, stores the analysis result version, deletes the AI image input, and calculates an estimate context from configured business data.
8. The system routes every valid request to human review: Staff for an estimated value at or below 3,000,000 VND, or Manager for a value above that threshold or a Manager-authority rule.
9. The Member sees the analysis result and a pending-review status; no design is accepted or rejected by AI.
10. The authorized Staff or Manager reviews the current design version and records Accepted, Rejected, or Clarification Required with reason and decision context.
11. The system displays the recorded decision to the Member and allows the permitted next action.

#### Alternative Flows

**Step 2 — Member does not provide consent**

The system does not store or send the image to the AI API and asks the Member to choose an approved non-image design path or provide consent before retrying.

**Step 3 — Image is missing, unsupported, or too large**

The system rejects the submission, displays the permitted image requirements, and allows the Member to submit another image.

**Step 4 — Image quality is too low for analysis**

The system does not send the image to the AI API, displays the quality issue, and asks the Member to choose another image or design path.

**Step 4 — Image-analysis rate limit has been exceeded**

The system rejects the request temporarily and displays the configured retry guidance.

**Step 5 — AI API is unavailable or times out**

The system records the failed analysis attempt, deletes the temporary image, does not create a review decision, and allows the Member to retry or choose another design path.

**Step 7 — Candidate features do not satisfy the approved hard constraints**

The system records the constraint result as analysis context, does not reject the request automatically, and routes the request to Manager or Clarification Required according to the configured rule.

**Step 10 — Reviewer requests clarification**

The system records the clarification reason, keeps the prior decision unavailable for booking continuation, and returns the Member to the current design version. A material change creates a new analysis/review version.

**Step 10 — Staff review rejects the design**

The system records the Staff decision and asks the Member to revise the request or choose another supported design path. The rejected version cannot continue to booking confirmation.

**Step 10 — Manager review rejects the design**

The system records the Manager decision and asks the Member to revise the request or choose another supported design path. The rejected version cannot continue to booking confirmation.

**Step 10 — Design changes after review**

The system invalidates the previous decision for the changed configuration, creates a new review version, and requires analysis/routing again.

#### Postconditions

- The image request is analyzed and routed to Staff or Manager human review, or remains pending clarification/failed analysis with an auditable reason.
- No valid design continues as accepted without a recorded human review decision.
- Staff reviews estimated values at or below 3,000,000 VND; Manager reviews higher-value or Manager-authority requests.
- The AI API provides candidate features and image-quality information only; it does not make the final feasibility, price, or custom-order decision.
- AI image input is not retained after processing.

#### Business Rules

GBR-05, GBR-13, BR-30-01, BR-30-02, BR-30-03, BR-30-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-30-01 | Booking-design image analysis is permitted only within the workshop-booking journey and after the Member gives the required consent. |
| BR-30-02 | The AI API may return image quality and candidate features or materials only; it cannot make the final feasibility, price, acceptance, or rejection decision. |
| BR-30-03 | Every valid booking-design request requires a human decision. Staff reviews requests at or below 3,000,000 VND; Manager reviews higher-value or Manager-authority requests. |
| BR-30-04 | A booking-design request with an estimated value above 3,000,000 VND must be reviewed by a Manager. |
