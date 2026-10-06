### 8.10 Record Customer Pickup

#### Primary Actors

Staff; Manager.

#### Secondary Actors

Member; Mail Gateway.


#### Description

As an authorized shop actor, I want to verify the current authenticated owner and actual handover of a ready pickup order so that picked-up completion is attributable (UC52).

#### Preconditions

1. The platform is available and the retail order exists.
2. The actor is authenticated with verified identity, has an active account, and holds current authority for the requested action.
3. The actor has current assigned-branch or applicable Manager pickup delegation.
4. The order is verified paid, selected for pickup and ready_for_pickup. The owner must sign in and open the current own order at the counter.

#### Postconditions

- Successful confirmation records picked_up as the irreversible pickup terminal state with confirming actor/time, verification method/result and minimized order-reference evidence.
- Rejected/failed attempts preserve prior state. No duplicate release, payment/quantity settlement, document copy, pickup OTP or courier/cancellation/refund state is introduced.

#### Normal Flow

**Record Customer Pickup**

1. The authorized actor opens the ready pickup order at its processing branch.
2. The system rechecks current shop account/action/branch authority and verified paid pickup readiness.
3. The owning Member signs in and opens the current order at the counter.
4. The system verifies current Member identity/account authority and ownership of that matching order.
5. The shop actor checks the matching ready order and confirms actual handover to that owner.
6. The system rechecks current state and both applicable ownership/shop conditions, then records ready_for_pickup to picked_up once.
7. The system stores safe confirming actor/time, verification method/result and minimized reference, and displays the recorded pickup completion. Optional completion email uses approved usable contact and the existing workflow, with at most two retries after 1/5 minutes from initial send; failure never undoes completion (D08-e/A-08).

#### Alternative Flows

**Steps 2 and 6 — Wrong branch, inactive account or missing/revoked delegation**

Reject completion without private exposure or state change. Manager requires applicable pickup action/branch delegation; Admin/Guest/Member cannot record shop completion.

**Steps 2 and 6 — Unpaid, unready, wrong method or incompatible terminal state**

Reject without skipping milestones or changing another terminal result. A paid flag alone or preparing state is insufficient; use the appropriate preceding use case.

**Steps 3 and 4 — Screenshot/reference only, different Member or third-party collection**

Reject pickup verification. Only the owning Member signed in with the current matching order may collect; a known order code, photo or another person is insufficient. Resume at Step 3 when the owner can authenticate.

**Steps 5 and 6 — No actual handover or state changed during verification**

Do not record pickup completion from a view/verification alone. Recheck current facts; reject an incompatible/concurrent change without an extra release.

**Step 6 — Pickup already recorded or stale submission**

Never record a second effective pickup or alternate terminal. Confirmed A-06 acknowledges an identical accepted completion without change and rejects stale/conflicting submissions.

**Steps 4 and 6 — Verification/read/save failure or inactive branch**

Report a safe actual failure and preserve accepted state/evidence, without false completed pickup.

**Steps 2 and 6 — Processing branch became inactive**

Branch inactivity blocks new checkout selection, but accepted orders retain the original branch and deadlines. Existing payment/holds continue under the original rules; paid orders may be completed by currently assigned Staff or explicitly action/branch-delegated Manager. Inability to physically hand over requires operational follow-up, not automatic relocation/cancellation/refund. (D05-a). Continue the established order flow when current action/state prerequisites are met; inactivity alone does not revoke an existing order's branch-scoped authority.

#### Business Rules

BR-52-01, BR-52-02, BR-52-03, BR-52-04, BR-52-05, BR-52-06, BR-52-07

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-52-01 | Only assigned-branch Staff or explicitly delegated current action/branch Manager may record pickup; the owning Member supplies verification but cannot record shop completion (D-01). |
| BR-52-02 | Confirmed C3/D-04: only the owner may collect, after signing in and opening the current matching order at the counter; the system confirms ownership. Screenshot/order reference alone and third-party collection are rejected. |
| BR-52-03 | Require verified paid, pickup method, ready_for_pickup and actual handover to that authenticated owner. Record only ready_for_pickup to picked_up; no skipped/backward/other-terminal transition. |
| BR-52-04 | Retain confirming actor/time, verification method/result and minimized order reference/context. Do not collect identity-document copies, pickup OTP or raw authentication credentials/tokens. |
| BR-52-05 | Pickup completion does not charge again, create/extend a hold, reprice the order or repeat paid-sale quantity/benefit settlement. |
| BR-52-06 | Confirmed behavior (A-06): identical already-recorded effects return successful no-change; stale/conflicting requests reject with review guidance, without duplicate effects. Pickup collects no additional address/contact (D07-a); existing minimized business audit follows five years. |
| BR-52-07 | Retain safe normally append-only actor/source, target, action/time, actual SUCCESS/REJECTED/FAILED outcome and reason, with permitted references/context. Rejected or failed attempts are not success; exclude credentials, raw tokens and avoidable personal data. Business audit follows five-year retention without extending recipient-data retention. |
