### 5.4 Submit Custom Design Request

#### Primary Actors

- Member

#### Secondary Actors

- Cloud Storage Service

#### Description

This use case allows a Member to submit a custom design request with a description, up to five reference images, and/or a saved ring design of their own. The request is reviewed by Staff or a Manager. It is not attached to a workshop booking, and no AI assesses it.

#### Preconditions

1. The Member is signed in and the account is active.
2. The request has at least one of: a description, a reference image, or a saved ring design owned by the Member.
3. When images are attached, the Member has consented to image processing.

#### Normal Flow

**Submit Custom Design Request**

1. The Member opens the custom design request form.
2. The system displays the description field, the saved-design choice, the image requirements and the image-processing consent.
3. The Member enters a description, attaches a saved design and/or selects reference images, and gives consent for any image.
4. The system checks each image's type, size and readable quality and shows the result.
5. The Member submits the request.
6. The system revalidates the account, ownership, consent, content, images and submission rate limit.
7. The system stores the images in the Cloud Storage Service and, for an attached design, an immutable snapshot of it.
8. The system creates the request with status Need Review and shows it to the Member.

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

#### Postconditions

- A valid request with status Need Review exists, owned by the Member.
- Later edits to the source ring design do not change the snapshot.
- No booking, payment, quotation or manufacturing order is created.

#### Business Rules

- BR-35-01
- BR-35-02
- BR-35-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-35-01 | Reference images require the Member's consent before they are stored. |
| BR-35-02 | Only an active signed-in Member may submit a request, with a description, up to five images and/or a saved ring design that the Member owns. |
| BR-35-03 | No AI assesses the request or its images, decides feasibility or sets a price. |
