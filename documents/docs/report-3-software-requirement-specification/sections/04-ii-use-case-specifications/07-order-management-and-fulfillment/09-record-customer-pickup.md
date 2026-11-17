### 7.9 Record Customer Pickup

#### Primary Actors

Staff; Manager.

#### Secondary Actors

Member; Mail Gateway.

#### Description

As an authorized shop actor, I want to verify the current authenticated owner and actual handover of a ready pickup order so that picked-up completion is attributable.

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
7. The system stores safe confirming actor/time, verification method/result and minimized reference, and displays the recorded pickup completion. Optional completion email follows the shared notification policy and never reverses completion.

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

Never record a second effective pickup or alternate terminal. The system acknowledges an identical accepted completion without change and rejects stale/conflicting submissions.

**Steps 4 and 6 — Verification/read/save failure**

Report a safe actual failure and preserve accepted state/evidence, without false completed pickup.

**Steps 2 and 6 — Processing branch became inactive**

An inactive branch cannot receive new checkout, but accepted orders retain their branch and original deadlines. Currently authorized assigned Staff/delegated Manager may complete paid orders; physical handover problems require operational follow-up without automatic transfer, cancellation or refund.

#### Business Rules

BR-52-01, BR-52-02, BR-52-03, BR-52-04, BR-52-05, BR-52-06, BR-52-07

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-52-01 | Assigned-branch Staff or explicitly pickup-action/branch-delegated Manager records completion; the Member provides ownership verification only. |
| BR-52-02 | Only the signed-in owning Member opening the current matching order may collect; screenshots, references alone and third-party collection are insufficient. |
| BR-52-03 | Record ready_for_pickup → picked_up only after verified payment, pickup readiness and actual handover to the authenticated owner. |
| BR-52-04 | Retain actor/time, verification method/result and minimized order reference; collect no identity-document copy, pickup OTP, extra contact/address or raw credentials. |
| BR-52-05 | Pickup never charges again, changes prices/deadlines or repeats quantity/benefit settlement. |
| BR-52-06 | Identical recorded completion returns successful no-change; reject stale/conflicting requests without another handover effect. |
| BR-52-07 | Record each attempt and its actual outcome under the shared audit policy. |
