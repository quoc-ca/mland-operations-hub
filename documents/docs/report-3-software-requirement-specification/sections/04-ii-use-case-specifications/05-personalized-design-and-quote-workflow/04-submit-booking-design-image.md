### 5.4 Submit Custom Design Request

| Use Case ID | UC-39 | Use Case Name | Submit Custom Design Request |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member | Secondary Actors | Cloud Storage Service |

#### Description

As a Member, I want to submit a custom design request with text, reference images, or a saved design so that Staff can review it.

#### Trigger

A Member opens the custom design request form in the design request workspace.

#### Preconditions

1. The Member is signed in and the account is active.
2. The request includes a description, reference image, or owned saved ring design.
3. The Member consents to image processing whenever images are attached.
4. Each attached image meets supported file type and size limits.

#### Postconditions

1. A request with status Need Review exists, owned by the Member, awaiting human review.
2. Any reference image is stored securely and linked to the request.
3. Later edits to the source ring design do not change the stored snapshot.
4. No booking, payment, quotation, or manufacturing order is created.

#### Normal Flow

1. Member opens the custom design request form in the design request workspace.
2. System displays the description field, saved-design choice, image requirements, and image-processing consent.
3. Member enters a description, attaches a saved design and reference images, and grants consent.
4. System validates each image's type, size, and readable quality and displays the result.
5. Member submits the request.
6. System revalidates account, ownership, consent, content, image count, and submission rate limit.
7. System stores images in Cloud Storage Service and saves an immutable design snapshot.
8. System creates the request with status Need Review and routes it for human review.

#### Alternative Flows

6a. Member submits a duplicate request → System returns the original request and creates no second one.

#### Exception Flows

1a. Actor is a Guest, Staff, or Manager → System prompts Guest sign-in and blocks Staff or Manager submission.

3a. Image-processing consent is not granted → System does not accept or store the images.

3b. Five images are already attached → System informs the Member and prevents adding another image.

4a. Image is unsupported, too large, or too blurry → System rejects it and asks the Member to replace it.

6a. Request is empty, exceeds five images, or description is too long → System rejects the request with the reason.

6b. Image submission rate limit is reached → System temporarily rejects the request and shows when to retry.

6c. Revision lacks ownership or Clarification Required state → System rejects the revision without updating the request.

7a. Cloud storage fails → System creates no request and asks the Member to retry.

#### Business Rules

BR-35-01, BR-35-02, BR-35-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-35-01 | Reference images require the Member's purpose-specific consent before they are stored or reviewed. |
| BR-35-02 | Only an active signed-in Member may submit a request, with a description, up to five images and/or a saved ring design that the Member owns; only the owning Member may revise it, while it is in Clarification Required. |
| BR-35-03 | Image quality validation is a submission gate. No AI assesses the request or its images, decides feasibility or sets a price; Staff or a Manager decides feasibility and the review outcome. |
