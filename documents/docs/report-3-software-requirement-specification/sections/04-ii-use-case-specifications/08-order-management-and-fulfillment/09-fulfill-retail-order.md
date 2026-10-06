### 8.9 Fulfill Retail Order

#### Primary Actors

Member.

#### Secondary Actors

None.

#### Description

As a Member, I want to choose pickup or GHTK after verified payment and provide necessary order-specific data so that the shop can prepare the paid order for the chosen method (UC51).

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

Identify missing/invalid fields and permit correction at Step 4 while the order remains editable. GHTK delivery is domestic Vietnam: recipient name 1–100 Unicode characters, address 10–500 Unicode characters, optional note at most 500 Unicode characters, with leading/trailing whitespace trimmed. Contact mobile number is 10 digits starting with 0 or its equivalent +84 form. (D04-b/c).

**Step 6 — Preparing started, including a concurrent shop confirmation**

Reject method/recipient changes and retain the existing choice/data. No reset, backward transition, branch transfer or special override; UC49 requires current valid method/data before starting preparing.

**Steps 3 and 7 — Processing branch became inactive**

Preserve the recorded branch/history and do not silently relocate. Branch inactivity blocks new checkout selection, but accepted orders retain the original branch and deadlines. Existing payment/holds continue under the original rules; paid orders may be completed by currently assigned Staff or explicitly action/branch-delegated Manager. Inability to physically hand over requires operational follow-up, not automatic relocation/cancellation/refund. (D05-a); changing pickup/GHTK cannot change the branch.

**Step 7 — Save failure**

Report a safe error and preserve previously accepted data/payment facts. The owner may retry only while current state remains editable; do not invent another order/payment.

**Steps 7 and 8 — Long wait before actual GHTK handoff**

Keep only recipient name/phone/address necessary to fulfil the pending handoff. The 30-day retirement clock begins at the original actual handoff, not at entry/editing or preparation (C4/D-07).

**Steps 4 and 6 — GHTK changes to pickup before preparing**

Remove obsolete GHTK recipient data when the valid change is accepted. Pickup collects no extra address or separate contact; branch and frozen payment terms remain unchanged (D07-a/c).

#### Business Rules

BR-51-01, BR-51-02, BR-51-03, BR-51-04, BR-51-05, BR-51-06, BR-51-07

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-51-01 | Only the active owning Member selects/edits method/recipient data after verified payment while paid before preparing. Internal actors coordinate through their own authorized use cases without impersonation (D-01/D-02). |
| BR-51-02 | Pickup and manual GHTK are the supported methods. GHTK requires order-specific recipient name/phone/address; GHTK delivery is domestic Vietnam: recipient name 1–100 Unicode characters, address 10–500 Unicode characters, optional note at most 500 Unicode characters, with leading/trailing whitespace trimmed. Contact mobile number is 10 digits starting with 0 or its equivalent +84 form. (D04-b/c). No global address book. |
| BR-51-03 | The Member-selected processing branch is fixed with checkout. Method selection/editing does not move the branch, alter frozen prices/totals, add shipping fees or create new payment/holds (C1/D-05). |
| BR-51-04 | Preparing requires current valid method/data and freezes subsequent owner edits. Only the selected method's approved readiness/completion chain is permitted; no override/backward correction. |
| BR-51-05 | Retain necessary GHTK recipient fields while actual handoff is pending; retire private values/copies 30 days after the original actual handoff. Pickup collects no separate delivery address/contact. Cart expires 30 days after its last Member edit without affecting established orders/holds/payments. Switching GHTK to pickup before preparing removes obsolete GHTK recipient data. Necessary pending-GHTK data remains until actual handoff; retire recipient/contact/address values and applicable copies 30 days after the original actual handoff. Minimized payment/exception/business-audit evidence is retained five years; sanitized ordinary technical logs 30 days. No raw recipient values are copied into logs. (D07-a–d). |
| BR-51-06 | V1 does not quote GHTK fees, call its APIs, track delivery, handle courier failure/returns or add cancellation/refund workflows. |
| BR-51-07 | Retain safe normally append-only actor/source, target, action/time, actual SUCCESS/REJECTED/FAILED outcome and reason, with permitted references/context. Rejected or failed attempts are not success; exclude credentials, raw tokens and avoidable personal data. Business audit follows five-year retention without extending recipient-data retention. |
