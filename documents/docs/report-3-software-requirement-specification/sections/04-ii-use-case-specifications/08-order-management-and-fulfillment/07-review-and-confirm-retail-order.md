### 8.7 Review & Confirm Retail Order

#### Primary Actors

Staff; Manager.

#### Secondary Actors

None.

#### Description

As an authorized shop actor, I want to review a verified paid retail order and begin preparation for its selected method so that fulfilment starts with current authority and valid data (UC49).

#### Preconditions

1. The platform is available and the retail order exists.
2. The actor is authenticated with verified identity, has an active account, and holds current authority for the requested action.
3. The actor is assigned to its processing branch or holds applicable Manager action/branch delegation.
4. The order is verified paid with a selected valid pickup/GHTK method and required data; these conditions are rechecked on submission.

#### Postconditions

- Successful confirmation changes paid to preparing once with safe actor/time evidence and freezes method/recipient edits.
- Rejected/failed attempts preserve prior business facts. No human paid override, repricing, new hold or terminal completion occurs.

#### Normal Flow

**Review & Confirm Retail Order**

1. The actor opens a retail order within authorized processing scope.
2. The system rechecks current action/branch authority and retrieves payment/hold/frozen purchase facts.
3. The actor reviews the verified paid order, selected method and necessary recipient data.
4. The actor submits confirmation to begin preparation.
5. The system rechecks current account/delegation, verified paid state, selected valid method/data and current order state.
6. The system records the paid-to-preparing transition and freezes Member method/recipient editing.
7. The system records safe attributable evidence and displays preparing status; the actor may continue to UC50 for method-specific readiness.

#### Alternative Flows

**Steps 2 and 5 — Wrong branch, revoked delegation or unauthorized role**

Deny without mutation/private exposure. Manager requires applicable current delegation; Admin, Guest and Member cannot perform internal preparation.

**Step 5 — Pending/expired/exception-only payment or missing method/data**

Reject preparation and preserve state. Staff cannot assert paid; eligible payment belongs to UC46 and owner method/data selection to UC51. Resume at Step 1 after prerequisites are met.

**Steps 5 and 6 — Member method edit races with preparing**

Use the current selected valid method/data when accepting preparation. An edit accepted after preparing is effective must be rejected; no stale edit may overwrite frozen data or move the processing branch.

**Step 6 — Already preparing, later milestone, terminal or stale conflict**

Do not duplicate preparation or move backwards/from the other branch. Confirmed A-06 acknowledges an identical accepted effect without change and rejects incompatible/stale updates.

**Steps 2 and 6 — Read/save failure**

Report a safe actual failure and preserve accepted facts without false transition.

**Steps 2 and 5 — Processing branch became inactive**

Branch inactivity blocks new checkout selection, but accepted orders retain the original branch and deadlines. Existing payment/holds continue under the original rules; paid orders may be completed by currently assigned Staff or explicitly action/branch-delegated Manager. Inability to physically hand over requires operational follow-up, not automatic relocation/cancellation/refund. (D05-a). Continue the established order flow when current action/state prerequisites are met; inactivity alone does not revoke an existing order's branch-scoped authority.

#### Business Rules

BR-49-01, BR-49-02, BR-49-03, BR-49-04, BR-49-05, BR-49-06, BR-49-07

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-49-01 | Only current assigned-branch Staff or explicitly delegated action/branch Manager may review/confirm preparation; approval or role name alone grants no action (D-01). |
| BR-49-02 | Only verified paid orders with a selected valid method/data enter preparing. Human confirmation does not replace signed VNPay evidence or reprice frozen terms. |
| BR-49-03 | Accept only paid to preparing; Member method/recipient editing is then frozen. No backward correction, cancellation/refund or override is added (D-02). |
| BR-49-04 | Concurrent Member edits and shop submission must recheck current state/data; after preparing starts a method/recipient change is rejected and the processing branch stays fixed. |
| BR-49-05 | Confirmed behavior (A-06): identical already-recorded effects return successful no-change; stale/conflicting requests reject with review guidance, without duplicate effects. No duplicate effective transition is allowed. |
| BR-49-06 | Branch inactivity blocks new checkout selection, but accepted orders retain the original branch and deadlines. Existing payment/holds continue under the original rules; paid orders may be completed by currently assigned Staff or explicitly action/branch-delegated Manager. Inability to physically hand over requires operational follow-up, not automatic relocation/cancellation/refund. (D05-a). |
| BR-49-07 | Retain safe normally append-only actor/source, target, action/time, actual SUCCESS/REJECTED/FAILED outcome and reason, with permitted references/context. Rejected or failed attempts are not success; exclude credentials, raw tokens and avoidable personal data. Business audit follows five-year retention without extending recipient-data retention. |
