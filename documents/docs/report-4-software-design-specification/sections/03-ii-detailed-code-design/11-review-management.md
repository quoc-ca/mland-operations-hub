## 11. Review Management (FE-11)

**Implementation status:** Review management backend/frontend slices exist; current public-read/member-write contract is the authority.

### 11.1 Class Diagram

<!-- Temporarily disabled: r4-cd-fe11-review-management. The approved Drive image will be reconnected after the shared asset update. -->

The module owns review, revision, reply and moderation audit data. Public DTOs exclude source IDs, transaction references, email and phone. Guest can read public projections; only an active Member can create or edit.

Reviews are independent from the ring-design recommendation and custom-manufacturing flows. A workshop recommendation does not create a review source; a custom design reference does not become a review target. Review creation still requires the Member's own eligible workshop attendance or fulfilled retail order source.

### 11.2 Sequence Diagram — Member Creates Eligible Review

<!-- Temporarily disabled: r4-sd-fe11-create-review. The approved Drive image will be reconnected after the shared asset update. -->

### 11.3 Sequence Diagram — Owner Edits Published Review

<!-- Temporarily disabled: r4-sd-fe11-edit-review. The approved Drive image will be reconnected after the shared asset update. -->

### 11.4 Sequence Diagram — Staff/Manager Moderates and Replies

<!-- Temporarily disabled: r4-sd-fe11-moderate-review. The approved Drive image will be reconnected after the shared asset update. -->

