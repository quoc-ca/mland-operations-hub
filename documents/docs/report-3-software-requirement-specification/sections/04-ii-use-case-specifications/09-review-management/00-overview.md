## 9. Review Management

### 9.1 Browse Public Reviews

| Use Case ID | UC-74 | Use Case Name | Browse Public Reviews |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest; Member | Secondary Actors | None |

#### Description

As a Guest or Member, I want to read published reviews and rating aggregates of workshop sessions and retail products so that I can evaluate offerings before purchasing.

#### Trigger

A Guest or Member opens the public review list or a page that displays reviews.

#### Preconditions

1. The platform is available.

#### Postconditions

1. The actor sees public reviews and rating aggregates, or an empty state.
2. No review or transaction data is changed.

#### Normal Flow

1. Actor opens the public review list.
2. System retrieves only public reviews and their rating aggregates.
3. System displays rating, text, photos, masked/anonymous name, and current Manager reply.
4. Actor filters, pages through the results, and opens one review.

#### Alternative Flows

2a. No public review exists → System displays an empty state.

4a. Guest selects write a review → System asks the Guest to sign in before any private request.

#### Exception Flows

4b. Review is hidden, removed, or does not exist → System reports it as unavailable without revealing its details.

#### Business Rules

BR-74-01, BR-74-02

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-74-01 | Only public reviews appear in lists and aggregates. |
| BR-74-02 | Public output never exposes email, phone, owner or source identifiers, order or ticket references, or audit data. |

### 9.2 Create or Edit Own Review

| Use Case ID | UC-75 | Use Case Name | Create or Edit Own Review |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member | Secondary Actors | None |

#### Description

As a Member, I want to review an eligible workshop attendance or retail order line and edit my own review so that I can share feedback on my completed experience.

#### Trigger

A Member opens their eligible review sources or selects an own public review to edit.

#### Preconditions

1. The Member is signed in and the account is active.
2. To create, the Member owns an unreviewed checked-in workshop attendance or picked-up/delivered retail order line.
3. To edit, the review belongs to the Member and is public.

#### Postconditions

1. Each source has at most one review; a valid review is public and its history is retained.
2. No order, booking, or payment is changed.

#### Normal Flow

1. Member opens their eligible sources to create a review.
2. System displays only the Member's own eligible sources.
3. Member enters a rating, optional text and photos, and anonymity choice.
4. System validates ownership, eligibility, content, and uniqueness.
5. System publishes the review at once.
6. System records revision and audit history and displays the review.

#### Alternative Flows

1a. Member edits their own public review → System validates ownership and version, saves the edit, keeps it public, and records revision history.

#### Exception Flows

1b. Actor is a Guest → System asks the Guest to sign in without lookup by email or phone.

4a. Source is not eligible or not owned → System rejects the command and reveals no private data.

4b. Source already has a review → System creates no second review and returns the existing result or a conflict.

4c. Invalid rating, text, or photo → System rejects the input and displays the reason.

4d. Review changed since it was loaded → System returns a conflict and asks the Member to reload.

#### Business Rules

BR-75-01, BR-75-02, BR-75-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-75-01 | Only the owning active Member may create a review, from a checked-in workshop attendance or a picked-up or delivered retail order line; no-show, cancelled, unpaid and unfulfilled sources are not eligible. |
| BR-75-02 | Rating is a whole number from 1 to 5; text is optional and at most 2,000 characters. |
| BR-75-03 | One source has at most one review; only its owner may edit it, and Staff, Manager and Admin cannot create or edit reviews for a customer. |

### 9.3 Moderate Review

| Use Case ID | UC-76 | Use Case Name | Moderate Review |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff; Manager | Secondary Actors | None |

#### Description

As Staff or a Manager, I want to hide inappropriate reviews and, as a Manager, restore, remove, or reply to reviews so that public review content remains accurate and well-moderated.

#### Trigger

A Staff member or Manager opens the review moderation queue and selects a review.

#### Preconditions

1. The actor is signed in as Staff or Manager and the account is active.
2. The review exists.

#### Postconditions

1. The review is public, hidden, or removed, or has one current Manager reply.
2. Removed reviews are kept in history and cannot be restored.

#### Normal Flow

1. Actor opens the review moderation queue and selects a review.
2. System displays the review, its status, and current reply.
3. Actor chooses an allowed moderation action and enters a reason.
4. System validates role, state, version, and conflict of interest.
5. System applies the change and records an append-only audit event.

#### Alternative Flows

3a. Manager creates or edits a reply → System validates role, reply text, state, and version, saves one current Manager reply, and records an audit event.

#### Exception Flows

3b. Staff attempts to restore, remove, or reply → System denies the action.

3c. Reason or reply text is missing or too long → System rejects the command.

4a. Actor moderates or replies to their own review → System denies the action.

4b. Review changed, already removed, or already replied to → System returns a conflict (an existing reply is updated by editing it).

#### Business Rules

BR-76-01, BR-76-02, BR-76-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-76-01 | Staff may hide any review. Only a Manager may restore a hidden review, remove a review, or create and edit its reply. Admin makes no moderation or reply decision. |
| BR-76-02 | Every status change requires a reason and is recorded with actor, role, time and previous and new status. |
| BR-76-03 | Removal is final and is not a hard delete; a review has at most one current Manager reply. |
