### 8.11 Mark Order as Prepared for Carrier

#### Primary Actors

Staff; Manager.

#### Secondary Actors

Delivery Service; Mail Gateway

#### Description

As an authorized shop actor, I want to record actual manual GHTK handoff of an already prepared paid order so that the shop's recorded responsibility ends at evidenced carrier custody (UC53). The inventory title is retained; preparation alone is the separate prepared_for_carrier milestone in UC50.

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
8. The system displays the recorded terminal handoff; the shop's recorded responsibility ends there. Optional completion email uses approved usable contact and the existing workflow, with at most two retries after 1/5 minutes from initial send; failure never undoes handoff (D08-e/A-08).

#### Alternative Flows

**Steps 2 and 6 — Unauthorized/wrong branch or revoked delegation**

Reject without mutation/private exposure. Manager needs applicable handoff action/branch delegation; Admin has no business override.

**Steps 2 and 6 — Unpaid/unprepared/pickup order or incompatible terminal state**

Reject without skipping readiness or moving from pickup/backwards. Use UC46/UC49/UC50 for valid prerequisites; human paid flags do not authorize handoff.

**Steps 3 and 6 — Missing recipient data or handoff reference**

Reject with safe correction guidance; do not invent recipient values or evidence. GHTK delivery is domestic Vietnam: recipient name 1–100 Unicode characters, address 10–500 Unicode characters, optional note at most 500 Unicode characters, with leading/trailing whitespace trimmed. Contact mobile number is 10 digits starting with 0 or its equivalent +84 form. Actual GHTK handoff requires a waybill or receipt reference of 1–100 characters on one line, carrier, order, recording actor and actual handoff time; no document-image upload is required. (D-04). Method/recipient edits remain frozen after preparing; no correction override is invented.

**Steps 4 to 6 — Packing/preparation recorded but no actual GHTK handoff**

Retain prepared_for_carrier and do not assert carrier custody. The UC53 title does not make preparing/packing evidence equivalent to actual handoff.

**Step 6 — Already-recorded handoff, duplicate or stale conflicting request**

Never create a second terminal effect, repeat quantity/benefit settlement or reset original handoff/retirement time. Confirmed A-06 returns no-change for an identical accepted effect and rejects stale/incompatible changes.

**Steps 2 and 6 — Read/save failure, uncertain recording outcome**

Report a safe actual error and preserve accepted custody/payment facts; do not fabricate completion or repeat physical handoff merely because a response is missing.

**Steps 3 and 7 — Recipient entry is old or retirement becomes due**

Keep required recipient name/phone/address while actual handoff is still pending, even beyond 30 days from entry. After the original actual handoff, delete/irreversibly obscure recipient/contact/address data and applicable copies at 30 days while preserving minimized custody/payment evidence.

**Steps 2 and 6 — Processing branch became inactive**

Branch inactivity blocks new checkout selection, but accepted orders retain the original branch and deadlines. Existing payment/holds continue under the original rules; paid orders may be completed by currently assigned Staff or explicitly action/branch-delegated Manager. Inability to physically hand over requires operational follow-up, not automatic relocation/cancellation/refund. (D05-a). Continue the established order flow when current action/state prerequisites are met; inactivity alone does not revoke an existing order's branch-scoped authority.

#### Business Rules

BR-53-01, BR-53-02, BR-53-03, BR-53-04, BR-53-05, BR-53-06, BR-53-07, BR-53-08

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-53-01 | Only assigned-branch Staff or explicitly delegated current handoff action/branch Manager may record actual handoff (D-01). |
| BR-53-02 | UC53 follows its inventory description and confirmed carrier chain: paid → preparing → prepared_for_carrier → handed_to_carrier. UC50 records preparation; UC53 requires actual manual GHTK custody, not a generic status label (D-02). |
| BR-53-03 | Require verified paid, fixed GHTK method, prepared_for_carrier, recipient name/phone/address and attributable actual handoff with carrier/reference/actor/time. GHTK delivery is domestic Vietnam: recipient name 1–100 Unicode characters, address 10–500 Unicode characters, optional note at most 500 Unicode characters, with leading/trailing whitespace trimmed. Contact mobile number is 10 digits starting with 0 or its equivalent +84 form. Actual GHTK handoff requires a waybill or receipt reference of 1–100 characters on one line, carrier, order, recording actor and actual handoff time; no document-image upload is required. (D-04). |
| BR-53-04 | Handed_to_carrier is terminal for V1 shop fulfilment. No backward/other-terminal override, courier delivered/tracking/failure/return state, GHTK API, fee quote, cancellation/refund or reconciliation is added. |
| BR-53-05 | Confirmed C4/D-07: retain recipient name/phone/address necessary while actual handoff is pending; retire private values/copies 30 days after original actual handoff. Entry/editing/preparation and retries do not start/reset that clock. |
| BR-53-06 | Preserve minimized necessary carrier custody, payment and safe audit evidence; five-year payment/audit retention does not preserve retired raw recipient/contact/address values. |
| BR-53-07 | Confirmed behavior (A-06): identical already-recorded effects return successful no-change; stale/conflicting requests reject with review guidance, without duplicate effects. No second handoff effect or paid-sale settlement is allowed. |
| BR-53-08 | Retain safe normally append-only actor/source, target, action/time, actual SUCCESS/REJECTED/FAILED outcome and reason, with permitted references/context. Rejected or failed attempts are not success; exclude credentials, raw tokens and avoidable personal data. Business audit follows five-year retention without extending recipient-data retention. |
