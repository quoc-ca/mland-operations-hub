### 7.10 Mark Order as Prepared for Carrier

#### Primary Actors

Staff; Manager.

#### Secondary Actors

Delivery Service; Mail Gateway

#### Description

As an authorized shop actor, I want to record actual manual GHTK handoff of an already prepared paid order so that the shop's recorded responsibility ends at evidenced carrier custody. The inventory title is retained; preparation alone is the separate prepared_for_carrier milestone in UC50.

#### Preconditions

1. The platform is available and the retail order exists.
2. The actor is authenticated with verified identity, has an active account, and holds current authority for the requested action.
3. The actor has current assigned-branch or applicable Manager handoff action/branch delegation.
4. The order is verified paid, GHTK-selected and prepared_for_carrier, with necessary recipient name/phone/address. Actual manual carrier handoff must occur before completion is recorded.

#### Postconditions

- Successful confirmation records handed_to_carrier as the irreversible carrier terminal state, with carrier/handoff reference, confirming actor/time and safe minimized evidence.
- The original actual handoff starts the 30-day recipient-data retirement clock; retry does not reset it.
- No delivery/tracking/fee/API workflow, new payment/hold, repeat sale deduction, cancellation/refund or return is created. Rejected/failed requests preserve prior facts.

#### Normal Flow

**Mark Order as Prepared for Carrier**

1. The actor opens the prepared GHTK order within authorized branch scope.
2. The system checks current account/action/branch authority, verified paid state, fixed GHTK method and prepared_for_carrier readiness.
3. The actor reviews the necessary order-specific recipient name/phone/address and carrier handoff information.
4. The actor manually hands the prepared order to GHTK outside the platform.
5. The actor submits actual handoff confirmation with carrier and handoff reference.
6. The system rechecks current authority/state, required data and actual handoff evidence, then records prepared_for_carrier to handed_to_carrier once.
7. The system retains original handoff time, actor, carrier/reference and minimized custody evidence, and sets recipient-data retirement at 30 days after that original actual handoff.
8. The system displays the recorded terminal handoff; the shop's recorded responsibility ends there. Optional completion email follows the shared notification policy and never reverses completion.

#### Alternative Flows

**Steps 2 and 6 — Unauthorized/wrong branch or revoked delegation**

Reject without mutation/private exposure. Manager needs applicable handoff action/branch delegation; Admin has no business override.

**Steps 2 and 6 — Unpaid/unprepared/pickup order or incompatible terminal state**

Reject without skipping readiness or moving from pickup/backwards. Use UC46/UC49/UC50 for valid prerequisites; human paid flags do not authorize handoff.

**Steps 3 and 6 — Missing recipient data or handoff reference**

Reject with safe correction guidance; do not invent recipient values or evidence. GHTK delivery is domestic Vietnam: recipient name 1–100 Unicode characters, address 10–500 Unicode characters, optional note at most 500 Unicode characters, with leading/trailing whitespace trimmed. Contact mobile number is 10 digits starting with 0 or its equivalent +84 form. Actual GHTK handoff requires a waybill or receipt reference of 1–100 characters on one line, carrier, order, recording actor and actual handoff time; no document-image upload is required. Method/recipient edits remain frozen after preparing; no correction override is invented.

**Steps 4 to 6 — Packing/preparation recorded but no actual GHTK handoff**

Retain prepared_for_carrier and do not assert carrier custody. The UC53 title does not make preparing/packing evidence equivalent to actual handoff.

**Step 6 — Already-recorded handoff, duplicate or stale conflicting request**

Never create a second terminal effect, repeat quantity/benefit settlement or reset original handoff/retirement time. The system returns no-change for an identical accepted effect and rejects stale/incompatible changes.

**Steps 2 and 6 — Read/save failure, uncertain recording outcome**

Report a safe actual error and preserve accepted custody/payment facts; do not fabricate completion or repeat physical handoff merely because a response is missing.

**Steps 3 and 7 — Recipient entry is old or retirement becomes due**

Keep required recipient name/phone/address while actual handoff is still pending, even beyond 30 days from entry. After the original actual handoff, delete/irreversibly obscure recipient/contact/address data and applicable copies at 30 days while preserving minimized custody/payment evidence.

**Steps 2 and 6 — Processing branch became inactive**

An inactive branch cannot receive new checkout, but accepted orders retain their branch and original deadlines. Currently authorized assigned Staff/delegated Manager may complete paid orders; physical handover problems require operational follow-up without automatic transfer, cancellation or refund.

#### Business Rules

BR-53-01, BR-53-02, BR-53-03, BR-53-04, BR-53-05, BR-53-06, BR-53-07, BR-53-08

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-53-01 | Only assigned-branch Staff or explicitly handoff-action/branch-delegated Manager may record actual carrier handoff. |
| BR-53-02 | UC50 records prepared_for_carrier; UC53 records actual manual GHTK handoff afterward. Preparation alone never proves carrier custody. |
| BR-53-03 | Handoff requires verified paid GHTK readiness, valid recipient data, carrier, a 1–100-character single-line waybill/receipt reference, actor and actual handoff time. |
| BR-53-04 | Handed_to_carrier is terminal for shop fulfilment; no backward/cross-terminal override, courier tracking, GHTK API, shipping quote or cancellation/refund workflow is added. |
| BR-53-05 | Keep necessary recipient data while handoff is pending; retire private values/copies 30 days after original actual handoff, without resetting on retries. |
| BR-53-06 | Keep minimized custody/payment/audit evidence under five-year retention without preserving retired recipient/contact/address values. |
| BR-53-07 | Identical recorded handoff returns successful no-change; reject conflicts without another handoff, settlement or retirement-clock reset. |
| BR-53-08 | Record each attempt and its actual outcome under the shared audit policy. |
