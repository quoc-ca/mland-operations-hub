### 8.6 View Order Details

#### Primary Actors

Member; Staff; Manager; Admin.

#### Secondary Actors

None.

#### Description

As an authorized order viewer, I want the frozen retail purchase and recorded payment/internal fulfilment facts so that I can understand the order without repricing it or implying courier tracking (UC48).

#### Preconditions

1. The platform is available and an order reference is supplied.
2. The actor is authenticated with verified identity, has an active account, and holds current authority for the requested action.
3. The requested order/purpose must fit owner access, assigned Staff branch, delegated Manager read or the approved minimum-data technical support boundary. Admin support allowlist: internal order/attempt reference, branch code, timestamps, payment/hold/fulfilment states and sanitized technical error/correlation codes only. Exclude cart contents, products/options, customer identity/contact/address/notes and authentication data; no business mutation or impersonation. (D04-e).

#### Postconditions

- The authorized actor sees only purpose-appropriate recorded order information, or a safe denied/error result.
- No payment, hold, stock or order/fulfilment mutation occurs; historical and personal-data retirement boundaries remain intact.

#### Normal Flow

**View Order Details**

1. The actor opens a retail order reference for an authorized purpose.
2. The system rechecks current ownership/action/branch/account authority before revealing protected data.
3. For authorized business reads the system retrieves frozen purchase snapshots and reference/branch; for Admin technical support it retrieves only D04-e allowlisted diagnostics, excluding cart/product/options/customer data.
4. The system retrieves recorded payment, original deadlines, hold disposition, method and internal preparation/readiness/completion evidence.
5. The system applies purpose-based access/minimization and recipient-data retirement, distinguishing unknown legacy values from current facts.
6. The system displays permitted order details and eligible next-action links; each later action rechecks its own authority/state.

#### Alternative Flows

**Steps 1 and 2 — Unknown order, wrong owner/branch or missing/revoked delegation**

Return a safe unavailable/denied result without private line/payment/recipient data or mutation. Staff cannot read across unassigned branches; a Manager's title is not applicable delegation.

**Steps 2 and 5 — Admin technical support request**

Expose only approved minimum necessary diagnostic data, without customer impersonation, general detailed-order access or business mutations. Admin support allowlist: internal order/attempt reference, branch code, timestamps, payment/hold/fulfilment states and sanitized technical error/correlation codes only. Exclude cart contents, products/options, customer identity/contact/address/notes and authentication data; no business mutation or impersonation. (D04-e).

**Steps 3 and 4 — Data retrieval fails or payment outcome is uncertain**

Display a safe failure or recorded pending/exception fact with appropriate guidance, not an empty order or fabricated payment/fulfilment success. Payment recovery belongs to eligible UC46 rules.

**Steps 3 and 5 — Catalogue changes or product-only legacy snapshot**

Retain accepted historical values, show unknown legacy option information without guessing and do not substitute current catalogue terms.

**Step 5 — Carrier personal data reached retirement**

Delete/irreversibly obscure retired recipient/contact/address values and applicable copies while retaining necessary minimized payment/custody evidence; do not expose old raw values through audit/details.

**Step 6 — Processing branch now inactive**

Preserve the recorded branch and history; no silent relocation occurs. Branch inactivity blocks new checkout selection, but accepted orders retain the original branch and deadlines. Existing payment/holds continue under the original rules; paid orders may be completed by currently assigned Staff or explicitly action/branch-delegated Manager. Inability to physically hand over requires operational follow-up, not automatic relocation/cancellation/refund. Viewing still grants no mutation.

#### Business Rules

BR-48-01, BR-48-02, BR-48-03, BR-48-04, BR-48-05, BR-48-06, BR-48-07

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-48-01 | Owner Members read only own orders; assigned-branch Staff and explicitly delegated action/branch Managers receive necessary scoped reads. Recheck current account/authority; references alone grant no access (D-01). |
| BR-48-02 | Admin receives minimum-data technical support only, with no general detailed order access, Member impersonation or business override. Admin support allowlist: internal order/attempt reference, branch code, timestamps, payment/hold/fulfilment states and sanitized technical error/correlation codes only. Exclude cart contents, products/options, customer identity/contact/address/notes and authentication data; no business mutation or impersonation. (D04-e). |
| BR-48-03 | Frozen purchase/benefit terms and recorded payment/hold/internal progress are distinct; read access never mutates them or grants paid/fulfilment authority. |
| BR-48-04 | Preserve legacy snapshots without inferred variants; new checkout requires configured reviewed variants. No public order lookup or courier tracking is introduced. |
| BR-48-05 | Retain needed carrier recipient data while actual handoff is pending; retire private values/copies 30 days after the original handoff. Minimized audit/payment evidence follows five years, without overriding privacy retirement (C4/D-07). |
| BR-48-06 | Branch inactivity blocks new checkout selection, but accepted orders retain the original branch and deadlines. Existing payment/holds continue under the original rules; paid orders may be completed by currently assigned Staff or explicitly action/branch-delegated Manager. Inability to physically hand over requires operational follow-up, not automatic relocation/cancellation/refund. (D05-a). |
| BR-48-07 | Retain safe normally append-only actor/source, target, action/time, actual SUCCESS/REJECTED/FAILED outcome and reason, with permitted references/context. Rejected or failed attempts are not success; exclude credentials, raw tokens and avoidable personal data. Business audit follows five-year retention without extending recipient-data retention. |
