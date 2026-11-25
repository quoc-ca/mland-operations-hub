### 5.4 Submit Independent Design Request Images

#### Primary Actors

- Member

#### Secondary Actors

- Cloud Storage Service

#### Description

This use case allows a Member to submit up to five reference images as part of an independent custom design request. The system validates image quality and consent, stores the images securely for the approved human-review workflow, and does not send them to an AI provider.

#### Preconditions

1. The member is signed in.
2. The Member is creating or revising an independent design request.
3. The member has provided the required consent for image processing.
4. The uploaded file complies with the supported file type and size limits.

#### Normal Flow

**Submit Booking Design Image**

1. The Member opens the image submission function from the independent design-request workspace.
2. The system displays the image-processing consent and upload requirements.
3. The member grants consent and selects a reference image.
4. The system validates Member ownership, consent, file type, file size, image quality, and the five-image limit.
5. The system uploads the image to the Cloud Storage Service.
6. The system creates or updates the independent design request.
7. The system routes the request to Staff or Manager human review.
8. The system displays the submission status to the Member.

#### Alternative Flows

**Step 3 — Consent is not granted**

1. The member does not grant the required consent.
2. The system cancels the submission and does not store or analyze the image.

**Step 4 — Invalid file**

1. The file type, size, or content does not meet the upload requirements.
2. The system rejects the file and asks the member to select another image.

**Step 4 — Invalid ownership or request state**

1. The request is not owned by the Member or is no longer editable.
2. The system rejects the request and does not create or update the design request.

**Step 4 — Image limit reached**

1. The Member has attached five images.
2. The system informs the Member and prevents another image from being added.

#### Postconditions

- A valid reference image is stored securely and linked to the Member's independent design request.
- The request enters human review and can be accepted, rejected, or returned for clarification.
- The image is not treated as an automatic manufacturing order or final quotation.

#### Business Rules

- BR-35-01
- BR-35-02
- BR-35-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-35-01 | Image processing requires the Member's purpose-specific consent before storage or human review. |
| BR-35-02 | The submitted image must be linked to an editable independent design request owned by the Member. |
| BR-35-03 | Image quality validation is a submission gate; Staff or Manager, not AI, decides feasibility and review outcome. |

