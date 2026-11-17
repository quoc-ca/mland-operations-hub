### 5.4 Submit Booking Design Image

#### Primary Actors

- Member

#### Secondary Actors

- AI API
- Cloud Storage Service

#### Description

This use case allows a member to submit a reference design image as part of a workshop booking. The system validates the image and the booking context, stores the file securely, and creates a design-analysis request for the AI image analysis workflow.

#### Preconditions

1. The member is signed in.
2. The member has an active or draft workshop booking.
3. The member has provided the required consent for image processing.
4. The uploaded file complies with the supported file type and size limits.

#### Normal Flow

**Submit Booking Design Image**

1. The member opens the image submission function from the workshop booking.
2. The system displays the image-processing consent and upload requirements.
3. The member grants consent and selects a reference image.
4. The system validates the booking ownership, consent, file type, file size, and upload limit.
5. The system uploads the image to the Cloud Storage Service.
6. The system creates a design-analysis request linked to the workshop booking.
7. The system sends the image reference and request context to the AI API for analysis.
8. The system displays the submission status to the member.

#### Alternative Flows

**Step 3 — Consent is not granted**

1. The member does not grant the required consent.
2. The system cancels the submission and does not store or analyze the image.

**Step 4 — Invalid file**

1. The file type, size, or content does not meet the upload requirements.
2. The system rejects the file and asks the member to select another image.

**Step 4 — Invalid booking or ownership**

1. The booking is not owned by the member or is no longer eligible for image submission.
2. The system rejects the request and does not create an analysis request.

**Step 4 — Upload limit reached**

1. The member has reached the permitted number of image submissions.
2. The system informs the member and asks them to continue with the existing request or contact staff.

**Step 7 — AI analysis is unavailable**

1. The image is stored successfully, but the AI API is unavailable.
2. The system marks the analysis request as pending and allows a retry or staff follow-up.

#### Postconditions

- A valid reference image is stored securely and linked to the member's workshop booking.
- A design-analysis request is created with a pending, completed, or failed status.
- The image is not treated as an automatic manufacturing order or final quotation.

#### Business Rules

- BR-35-01
- BR-35-02
- BR-35-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-35-01 | Image processing requires the member's consent before storage or AI analysis. |
| BR-35-02 | The submitted image must be linked to an eligible workshop booking owned by the member. |
| BR-35-03 | AI analysis provides reference information only and does not determine final manufacturing feasibility or price. |

