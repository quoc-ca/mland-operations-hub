### 8.9 Fulfill Retail Order

#### Primary Actors

Member.

#### Secondary Actors

None.

#### Description

As a Member, I want to choose pickup or GHTK after verified payment and provide necessary order-specific data so that the shop can prepare the paid order for the chosen method.

#### Preconditions

1. The platform is available and the retail order exists.
2. The actor is authenticated with verified identity, has an active account, and holds current authority for the requested action.
3. The Member owns the verified paid order. Method/data can be chosen or changed only while paid and before preparing starts.
4. The processing branch was selected and fixed at checkout; this use case cannot replace it.

#### Postconditions

- A valid owner submission records pickup or GHTK with necessary data without changing frozen purchase/payment/branch facts.
- Subsequent authorized preparation freezes method/recipient editing and continues along the chosen chain; this use case does not assert actual completion.
- Rejected/failed selections preserve existing data/state; no shipping fee, address book, provider call or new payment is created.

#### Normal Flow

**Fulfill Retail Order**

1. The owning Member opens fulfilment selection for the paid retail order.
2. The system rechecks owner/account authority, verified paid status and that preparing has not started.
3. The system shows the fixed processing branch and available Pickup and GHTK choices.
4. The Member chooses pickup or GHTK; for GHTK, supplies recipient name, phone and address with an optional note subject to the field policy.
5. The Member submits the selected method/data.
6. The system rechecks current ownership/state and validates method/required data.
7. The system saves the valid choice/data, retains safe evidence and shows the selected method without repricing or changing branch.
8. The order becomes eligible for authorized review/preparation in UC49 and readiness in UC50, followed by UC52 or UC53.

#### Alternative Flows

**Steps 2 and 6 — Wrong owner, invalid account or unpaid/expired order**

Reject without exposing another order, accepting data or changing payment facts. Staff/Manager/Admin cannot make the Member choice through customer impersonation.

**Step 6 — Invalid method or incomplete GHTK data**

Identify missing/invalid fields and permit correction at Step 4 while the order remains editable. GHTK delivery is domestic Vietnam: recipient name 1–100 Unicode characters, address 10–500 Unicode characters, optional note at most 500 Unicode characters, with leading/trailing whitespace trimmed. Contact mobile number is 10 digits starting with 0 or its equivalent +84 form.

**Step 6 — Preparing started, including a concurrent shop confirmation**

Reject method/recipient changes and retain the existing choice/data. No reset, backward transition, branch transfer or special override; UC49 requires current valid method/data before starting preparing.

**Steps 3 and 7 — Processing branch became inactive**

Preserve the recorded branch/history and do not silently relocate. An inactive branch cannot receive new checkout, but accepted orders retain their branch and original deadlines. Currently authorized assigned Staff/delegated Manager may complete paid orders; physical handover problems require operational follow-up without automatic transfer, cancellation or refund.

**Step 7 — Save failure**

Report a safe error and preserve previously accepted data/payment facts. The owner may retry only while current state remains editable; do not invent another order/payment.

**Steps 7 and 8 — Long wait before actual GHTK handoff**

Keep only recipient name/phone/address necessary to fulfil the pending handoff. The 30-day retirement clock begins at the original actual handoff, not at entry/editing or preparation.

**Steps 4 and 6 — GHTK changes to pickup before preparing**

Remove obsolete GHTK recipient data when the valid change is accepted. Pickup collects no extra address or separate contact; branch and frozen payment terms remain unchanged.

#### Business Rules

BR-51-01, BR-51-02, BR-51-03, BR-51-04, BR-51-05, BR-51-06, BR-51-07

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-51-01 | Only the active owning Member selects/edits method and recipient data after verified payment and before preparing; internal actors cannot impersonate the Member. |
| BR-51-02 | Support pickup or manual domestic GHTK; GHTK requires valid name, phone and address with optional note under the shared field limits, without an address book. |
| BR-51-03 | Fulfilment choice never changes the checkout branch, frozen prices/totals, shipping fees, payment or hold. |
| BR-51-04 | Preparation requires valid current method/data and freezes later owner edits; only the selected method’s readiness/completion chain is permitted. |
| BR-51-05 | Retain necessary pending GHTK data, retire it 30 days after original actual handoff, and remove obsolete data when switching to pickup before preparing. |
| BR-51-06 | No GHTK fee quote, API, tracking, delivery-failure/return or cancellation/refund workflow is provided. |
| BR-51-07 | Record each attempt and its actual outcome under the shared audit policy. |
