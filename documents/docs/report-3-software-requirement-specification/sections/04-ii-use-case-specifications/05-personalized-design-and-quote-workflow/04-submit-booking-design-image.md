### 5.4 Submit Custom Design Request

#### Primary Actors

- Member

#### Secondary Actors

- Cloud Storage Service

#### Description

This use case allows a Member to submit a custom design request with a description, up to five reference images, and/or a saved ring design of their own. The system validates image quality and consent and stores the images securely. The request is reviewed by Staff or a Manager. It is not attached to a workshop booking, no AI assesses it, and its images are not sent to an AI provider.

#### Preconditions

1. The Member is signed in and the account is active.
2. The request has at least one of: a description, a reference image, or a saved ring design owned by the Member.
3. When images are attached, the Member has consented to image processing.
4. Each attached image complies with the supported file type and size limits.

#### Normal Flow

**Submit Custom Design Request**

1. The Member opens the custom design request form in the design request workspace.
2. The system displays the description field, the saved-design choice, the image requirements and the image-processing consent.
3. The Member enters a description, attaches a saved design and/or selects reference images, and gives consent for any image.
4. The system checks each image's type, size and readable quality and shows the result.
5. The Member submits the request.
6. The system revalidates the account, ownership, consent, content, images (at most five) and submission rate limit.
7. The system stores the images in the Cloud Storage Service and, for an attached design, an immutable snapshot of it.
8. The system creates the request with status Need Review, routes it to Staff or Manager human review and shows it to the Member.

#### Alternative Flows

**Step 1 — Actor is a Guest**

The system asks the Guest to sign in and creates nothing. Staff and Managers cannot submit a request as a customer.

**Step 3 — Consent is not granted**

The system does not accept or store the images.

**Step 4 — Invalid or blurry image**

The system rejects an unsupported, too large or too blurry image and asks the Member to replace it.

**Step 6 — Empty request, more than five images or description too long**

The system rejects the request and shows the reason.

**Step 6 — Image submission rate limit reached**

The system rejects the request temporarily and shows when the Member may retry.

**Step 6 — Duplicate submission**

A repeated submission returns the original request and creates no second one.

**Step 7 — Storage failure**

The system creates no request and asks the Member to retry.

**Step 3 — Image limit reached**

When five images are attached, the system informs the Member and prevents another image from being added.

**Revision — Invalid ownership or request state**

The system rejects a revision of a request that the Member does not own or that is not in Clarification Required, and does not update the request.

#### Postconditions

- A valid request with status Need Review exists, owned by the Member; human review can accept it, reject it or return it for clarification.
- Any reference image is stored securely and linked to the request.
- Later edits to the source ring design do not change the snapshot.
- No booking, payment, quotation or manufacturing order is created; an image is not treated as an automatic manufacturing order or final quotation.

#### Business Rules

- BR-35-01
- BR-35-02
- BR-35-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-35-01 | Reference images require the Member's purpose-specific consent before they are stored or reviewed. |
| BR-35-02 | Only an active signed-in Member may submit a request, with a description, up to five images and/or a saved ring design that the Member owns; only the owning Member may revise it, while it is in Clarification Required. |
| BR-35-03 | Image quality validation is a submission gate. No AI assesses the request or its images, decides feasibility or sets a price; Staff or a Manager decides feasibility and the review outcome. |
