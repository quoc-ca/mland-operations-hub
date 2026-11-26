### 7.7 Review & Confirm Retail Order

| Use Case ID | UC-58 | Use Case Name | Review & Confirm Retail Order |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff; Manager | Secondary Actors | None |

#### Description

As an authorized shop actor, I want to review a verified paid retail order and begin preparation for its selected method so that fulfilment starts with current authority and valid data.

#### Trigger

A Staff member or delegated Manager opens a paid retail order in the Đơn hàng cần chuẩn bị tab.

#### Preconditions

1. The platform is available and the retail order exists.
2. The actor is authenticated with verified identity, has an active account, and holds current authority.
3. The actor is assigned to the processing branch or holds applicable Manager action/branch delegation.
4. The order is verified paid with a valid selected pickup/GHTK method and required data.

#### Postconditions

1. Successful confirmation records readiness once with safe actor/time evidence and freezes method/recipient edits.
2. Rejected attempts preserve prior facts; no human paid override, repricing, new hold, or terminal completion occurs.

#### Normal Flow

1. Actor opens a paid retail order in the Đơn hàng cần chuẩn bị tab within authorized branch scope.
2. System rechecks action/branch authority and retrieves payment, hold, and frozen purchase facts.
3. Actor reviews the Đang chuẩn bị hàng order, packs it, and selects Đã chuẩn bị xong.
4. System rechecks account, branch authority, verified paid state, method, data, and order state.
5. System records method-specific readiness (UC59 for pickup, UC61 for GHTK).
6. System records attributable evidence and displays the new status to Actor and Member.

#### Alternative Flows

None.

#### Exception Flows

2a. Wrong branch, revoked delegation, or unauthorized role → System denies access without mutation or private exposure; Admin, Guest, and Member cannot perform internal preparation.

2b. Processing branch became inactive → System preserves the branch and original deadlines without silent transfer, cancellation, or refund; currently authorized assigned Staff/delegated Manager may complete paid orders.

4a. Pending, expired, or exception-only payment, or missing method/data → System rejects preparation and preserves state; Staff cannot assert paid (resume at step 1 after prerequisites are met).

5a. Member method edit races with preparing → System uses the current valid method/data when accepting preparation and rejects any stale edit after preparing takes effect.

5b. Already preparing, later milestone, terminal state, or stale conflict → System acknowledges an identical accepted effect without change and rejects incompatible or stale updates.

6a. Read or save failure → System reports a safe actual failure and preserves accepted facts without false transition.

#### Business Rules

BR-58-01, BR-58-02, BR-58-03, BR-58-04, BR-58-05, BR-58-06, BR-58-07

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-58-01 | Only assigned-branch Staff or explicitly action/branch-delegated Manager may confirm preparation; role names or price approval alone grant no authority. |
| BR-58-02 | Preparation requires verified paid status and a valid selected method/recipient data; human confirmation cannot assert payment or reprice the order. |
| BR-58-03 | A paid order is already being prepared; method and recipient are frozen at checkout and no backward correction or override is permitted. |
| BR-58-04 | Recheck readiness against current state and data; the fixed branch is preserved. |
| BR-58-05 | Identical recorded effects return successful no-change; stale/conflicting requests reject for review without duplicate transitions. |
| BR-58-06 | Inactive branches may finish accepted paid orders under current branch-scoped authority; no silent transfer, cancellation or refund is created. |
| BR-58-07 | Record each attempt and its actual outcome under the shared audit policy. |
