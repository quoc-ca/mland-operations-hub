## 11. Review Management (FE-11)

**Implementation status:** Review management backend/frontend slices exist; current public-read/member-write contract is the authority.

### 11.1 Class Diagram

The module owns review, revision, reply and moderation audit data. Public DTOs exclude source IDs, transaction references, email and phone. Guest can read public projections; only an active Member can create or edit.

### 11.2 Sequence Diagram — Member Creates Eligible Review

### 11.3 Sequence Diagram — Owner Edits Published Review

### 11.4 Sequence Diagram — Staff/Manager Moderates and Replies

