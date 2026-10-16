### 4.2 Analyze Ring Design from Image

#### Primary Actors

Member

#### Secondary Actors

AI API, Staff / Consultant, Manager

#### Description

As a Member, I want to submit a consented ring-design image within the workshop-booking journey so that the system can analyze candidate features and route the request to automatic acceptance, Staff consultation, or Manager review according to the configured complexity and estimated-value rules.

#### Preconditions

1. The platform is available.
2. The Member is authenticated and has an eligible workshop-booking journey.
3. The Member has given the required image-processing consent.
4. The approved AI API is configured and enabled.
5. The Member has not exceeded the configured image-analysis rate limit.

#### Normal Flow

**Analyze Ring Design from Image**

1. The Member selects the reference-image design path within a workshop booking.
2. The system displays the image-processing consent and the supported image requirements.
3. The Member gives consent and submits a reference image.
4. The system validates the booking context, consent, file type, file size, image quality, and rate limit.
5. The system sends the permitted image request to the AI API.
6. The AI API returns image quality information and candidate design features or materials only.
7. The system evaluates the candidate features against the approved hard constraints, complexity thresholds, and estimated-value rules.
8. If the design is Simple and valid, the system records an auditable auto-accepted design path and displays the result to the Member.
9. If the design is Medium or requires consultation, the system creates a Staff / Consultant review request and provides the path for the Member to continue the discussion.
10. If the design is Advanced or its estimated value exceeds 3,000,000 VND, the system creates a Manager review request and displays the pending-review status to the Member.
11. The system removes AI image input after processing and records the analysis result and routing context without retaining the AI image.

#### Alternative Flows

**Step 2 — Member does not provide consent**

The system does not send the image to the AI API and asks the Member to choose an approved non-image design path or provide consent before retrying.

**Step 3 — Image is missing, unsupported, or too large**

The system rejects the submission, displays the permitted image requirements, and allows the Member to submit another image.

**Step 4 — Image quality is too low for analysis**

The system does not send the image to the AI API, displays the quality issue, and asks the Member to choose another image or design path.

**Step 4 — Image-analysis rate limit has been exceeded**

The system rejects the request temporarily and displays the configured retry guidance.

**Step 5 — AI API is unavailable or times out**

The system records the failed analysis attempt, does not create an automatic acceptance, and allows the Member to retry or choose another design path.

**Step 7 — Candidate features do not satisfy the approved hard constraints**

The system records the approved rejection context, does not send the request for automatic acceptance, and asks the Member to choose another supported design.

**Step 9 — Staff / Consultant review does not approve the design**

The system records the Staff decision and asks the Member to revise the request or choose another supported design path.

**Step 10 — Manager review does not approve the design**

The system records the Manager decision and asks the Member to revise the request or choose another supported design path.

#### Postconditions

- The image request is either auto-accepted, routed to Staff / Consultant review, routed to Manager review, or rejected with an auditable reason.
- Simple valid designs may continue without human review.
- Medium requests are available for Staff / Consultant discussion.
- Advanced or estimated-value requests above 3,000,000 VND are available for Manager review.
- The AI API provides candidate features and image-quality information only; it does not make the final feasibility, price, or custom-order decision.
- AI image input is not retained after processing.

#### Business Rules

GBR-05, GBR-13, BR-30-01, BR-30-02, BR-30-03, BR-30-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-30-01 | Booking-design image analysis is permitted only within the workshop-booking journey and after the Member gives the required consent. |
| BR-30-02 | The AI API may return image quality and candidate features or materials only; it cannot make the final feasibility, price, acceptance, or rejection decision. |
| BR-30-03 | A Simple valid design may be auto-accepted; a Medium design is routed to Staff / Consultant review; an Advanced design is routed to Manager review. |
| BR-30-04 | A booking-design request with an estimated value above 3,000,000 VND must be reviewed by a Manager. |
