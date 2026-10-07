### 8.8 Update Order Status

#### Primary Actors

Staff; Manager.

#### Secondary Actors

Mail Gateway.

#### Description

As an authorized shop actor, I want to record the next permitted internal readiness milestone so that the selected pickup or carrier workflow advances without manual payment or courier-state overrides.

#### Preconditions

1. The platform is available and the retail order exists.
2. The actor is authenticated with verified identity, has an active account, and holds current authority for the requested action.
3. The actor has current assigned-branch or applicable Manager action/branch authority.
4. The order is verified paid and in the appropriate preparing state with a fixed valid method/data. Completion actions retain their separate UC52/UC53 prerequisites.

#### Postconditions

- Successful readiness updates preparing to ready_for_pickup or prepared_for_carrier with safe actor/time evidence.
- No payment/hold/price change, premature pickup/handoff, backwards/other-terminal transition or courier progress is created. Rejected/failed requests preserve prior facts.

#### Normal Flow

**Update Order Status**

1. The actor opens the authorized order's internal progress controls.
2. The system checks current action/branch/account authority and retrieves verified payment, method and current milestone.
3. The actor selects Ready for Pickup for a preparing pickup order, or Prepared for Carrier for a preparing GHTK order.
4. The actor submits the selected readiness milestone.
5. The system rechecks current authority, verified paid status, fixed method/required data and the permitted next transition.
6. The system records that one next readiness milestone and safe attributable evidence.
7. The system displays the resulting status and applicable UC52/UC53 controls. At ready_for_pickup, optional email follows the shared notification policy and never reverses readiness.

#### Alternative Flows

**Steps 2 and 5 — Missing/revoked action/branch authority**

Deny without mutation/private exposure. Staff outside assigned branch and Manager without applicable delegation cannot act; Admin has no business mutation/override right.

**Steps 3 and 5 — Manual paid flag, skipped step, wrong method or backward/other-terminal request**

Reject the generic status change. Paid is established only through UC46 verified evidence; preparing begins through UC49. Only the selected method's next readiness milestone is permitted.

**Steps 3 and 7 — Pickup/handoff completion requested**

Continue through UC52/UC53, with owner verification or actual manual custody evidence respectively. Selecting a terminal label alone does not authorize completion.

**Step 6 — Already-recorded milestone, stale or conflicting update**

No second effective transition is allowed. The system returns successful no-change for an identical accepted effect and rejects stale/incompatible changes with review guidance.

**Steps 2 and 6 — Read/save failure**

Report a safe actual error and retain prior accepted facts.

**Steps 2 and 5 — Processing branch became inactive**

An inactive branch cannot receive new checkout, but accepted orders retain their branch and original deadlines. Currently authorized assigned Staff/delegated Manager may complete paid orders; physical handover problems require operational follow-up without automatic transfer, cancellation or refund.

#### Business Rules

BR-50-01, BR-50-02, BR-50-03, BR-50-04, BR-50-05, BR-50-06, BR-50-07

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-50-01 | Only assigned-branch Staff or explicitly action/branch-delegated Manager may record internal order progress. |
| BR-50-02 | Follow paid → preparing → ready_for_pickup → picked_up or paid → preparing → prepared_for_carrier → handed_to_carrier; no skipped, backward or cross-terminal transition. |
| BR-50-03 | UC50 records readiness only; UC46 verifies payment, UC49 starts preparation, UC52 records owner pickup and UC53 records actual carrier handoff. |
| BR-50-04 | Payment, hold and fulfilment remain separate; no courier-delivery, return, cancellation or refund state is added. |
| BR-50-05 | Readiness changes never alter frozen method/recipient data, prices, processing branch or payment/hold deadlines. |
| BR-50-06 | Identical recorded milestones return successful no-change; reject stale/conflicting submissions without duplicate effects. |
| BR-50-07 | Record each attempt and its actual outcome under the shared audit policy. |
