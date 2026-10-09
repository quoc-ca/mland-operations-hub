### 8.7 Review & Confirm Retail Order

#### Primary Actors

Staff; Manager.

#### Secondary Actors

None.

#### Description

As an authorized shop actor, I want to review a verified paid retail order and begin preparation for its selected method so that fulfilment starts with current authority and valid data.

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

Do not duplicate preparation or move backwards/from the other branch. The system acknowledges an identical accepted effect without change and rejects incompatible/stale updates.

**Steps 2 and 6 — Read/save failure**

Report a safe actual failure and preserve accepted facts without false transition.

**Steps 2 and 5 — Processing branch became inactive**

An inactive branch cannot receive new checkout, but accepted orders retain their branch and original deadlines. Currently authorized assigned Staff/delegated Manager may complete paid orders; physical handover problems require operational follow-up without automatic transfer, cancellation or refund.

#### Business Rules

BR-49-01, BR-49-02, BR-49-03, BR-49-04, BR-49-05, BR-49-06, BR-49-07

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-49-01 | Only assigned-branch Staff or explicitly action/branch-delegated Manager may confirm preparation; role names or price approval alone grant no authority. |
| BR-49-02 | Preparation requires verified paid status and a valid selected method/recipient data; human confirmation cannot assert payment or reprice the order. |
| BR-49-03 | Confirming paid → preparing freezes Member method/recipient edits; no backward correction or override is permitted. |
| BR-49-04 | Recheck concurrent edits and preparation against current state/data; accepted preparation prevents later edits and preserves the fixed branch. |
| BR-49-05 | Identical recorded effects return successful no-change; stale/conflicting requests reject for review without duplicate transitions. |
| BR-49-06 | Inactive branches may finish accepted paid orders under current branch-scoped authority; no silent transfer, cancellation or refund is created. |
| BR-49-07 | Record each attempt and its actual outcome under the shared audit policy. |
