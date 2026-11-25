## 9. Review Management

### 9.1 Browse Public Reviews

#### Primary Actors

- Guest
- Member

#### Secondary Actors

- None

#### Description

This use case (use case 74) allows a Guest or Member to read published reviews and rating aggregates of workshop sessions and retail products.

#### Preconditions

1. The platform is available.

#### Normal Flow

**Browse Public Reviews**

1. The actor opens the public review list, or a page that shows reviews.
2. The system retrieves only public reviews and their aggregates.
3. The system displays rating, text, photos, the masked or anonymous name and the current Manager reply.
4. The actor may filter, page through the results and open one review.

#### Alternative Flows

**Step 2 — No public review**

The system shows an empty state.

**Step 4 — Review is hidden, removed or does not exist**

The system reports it as unavailable without revealing its details.

**Any step — Guest selects write a review**

The system asks the Guest to sign in before any private request.

#### Postconditions

- No review or transaction data is changed.

#### Business Rules

- BR-74-01
- BR-74-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-74-01 | Only public reviews appear in lists and aggregates. |
| BR-74-02 | Public output never exposes email, phone, owner or source identifiers, order or ticket references, or audit data. |

### 9.2 Create or Edit Own Review

#### Primary Actors

- Member

#### Secondary Actors

- None

#### Description

This use case (use case 75) allows a Member to review an eligible workshop attendance or retail order line, and to edit their own review.

#### Preconditions

1. The Member is signed in and the account is active.
2. To create, the Member owns a checked-in workshop attendance, or a retail order line of a picked-up or delivered order, that has no review.
3. To edit, the review belongs to the Member and is public.

#### Normal Flow

**Create or Edit Own Review**

1. The Member opens their eligible sources or their own review.
2. The system displays only the Member's own sources.
3. The Member enters a rating, optional text and photos, and chooses on creation whether to post anonymously.
4. The system validates ownership, eligibility, content, uniqueness and, on edit, the version.
5. The system publishes the review at once, or saves the edit and keeps it public.
6. The system records the revision and audit history and shows the review.

#### Alternative Flows

**Step 1 — Actor is a Guest**

The system asks the Guest to sign in. There is no lookup by email or phone.

**Step 4 — Source is not eligible or not owned**

The system rejects the command and reveals no private data.

**Step 4 — Source already has a review**

The system creates no second review; a repeated command returns the existing result or a conflict.

**Step 4 — Invalid rating, text or photo**

The system rejects the input and shows the reason.

**Step 4 — Review changed since it was loaded**

The system returns a conflict and asks the Member to reload.

#### Postconditions

- Each source has at most one review; a valid review is public and its history is retained.
- No order, booking or payment is changed.

#### Business Rules

- BR-75-01
- BR-75-02
- BR-75-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-75-01 | Only the owning active Member may create a review, from a checked-in workshop attendance or a picked-up or delivered retail order line; no-show, cancelled, unpaid and unfulfilled sources are not eligible. |
| BR-75-02 | Rating is a whole number from 1 to 5; text is optional and at most 2,000 characters. |
| BR-75-03 | One source has at most one review; only its owner may edit it, and Staff, Manager and Admin cannot create or edit reviews for a customer. |

### 9.3 Moderate Review

#### Primary Actors

- Staff
- Manager

#### Secondary Actors

- None

#### Description

This use case (use case 76) allows Staff to hide a review, and a Manager to hide, restore, remove or reply to it.

#### Preconditions

1. The actor is signed in as Staff or Manager and the account is active.
2. The review exists.

#### Normal Flow

**Moderate Review**

1. The actor opens the review moderation queue and selects a review.
2. The system displays the review, its status and the current reply.
3. The actor chooses an action allowed for their role and enters a reason for a status change, or the reply text.
4. The system validates role, state, version and conflict of interest.
5. The system applies the change and records an append-only audit event.

#### Alternative Flows

**Step 3 — Staff attempts to restore, remove or reply**

The system denies the action.

**Step 3 — Reason or reply text is missing or too long**

The system rejects the command.

**Step 4 — Actor moderates or replies to their own review**

The system denies the action.

**Step 4 — Review changed, already removed, or already replied to**

The system returns a conflict; an existing reply is changed by editing it.

#### Postconditions

- The review is public, hidden or removed, or has one current Manager reply.
- Removed reviews are kept in history and cannot be restored.

#### Business Rules

- BR-76-01
- BR-76-02
- BR-76-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-76-01 | Staff may hide any review. Only a Manager may restore a hidden review, remove a review, or create and edit its reply. Admin makes no moderation or reply decision. |
| BR-76-02 | Every status change requires a reason and is recorded with actor, role, time and previous and new status. |
| BR-76-03 | Removal is final and is not a hard delete; a review has at most one current Manager reply. |
