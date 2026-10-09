### 7.6 View Order Details

#### Primary Actors

Member; Staff; Manager; Admin.

#### Secondary Actors

None.

#### Description

As an authorized order viewer, I want the frozen retail purchase and recorded payment/internal fulfilment facts so that I can understand the order without repricing it or implying courier tracking.

#### Preconditions

1. The platform is available and an order reference is supplied.
2. The actor is authenticated with verified identity, has an active account, and holds current authority for the requested action.
3. The requested purpose fits own-order access, assigned-branch Staff access, applicable Manager read delegation or the limited Admin support policy.

#### Postconditions

- The authorized actor sees only purpose-appropriate recorded order information, or a safe denied/error result.
- No payment, hold, stock or order/fulfilment mutation occurs; historical and personal-data retirement boundaries remain intact.

#### Normal Flow

**View Order Details**

1. The actor opens a retail order reference for an authorized purpose.
2. The system rechecks current ownership/action/branch/account authority before revealing protected data.
3. For authorized business reads the system retrieves frozen purchase snapshots and reference/branch; for Admin technical support it retrieves only permitted technical diagnostics, excluding cart/product/options/customer data.
4. The system retrieves recorded payment, original deadlines, hold disposition, method and internal preparation/readiness/completion evidence.
5. The system applies purpose-based access/minimization and recipient-data retirement, distinguishing unknown legacy values from current facts.
6. The system displays permitted order details and eligible next-action links; each later action rechecks its own authority/state.

#### Alternative Flows

**Steps 1 and 2 — Unknown order, wrong owner/branch or missing/revoked delegation**

Return a safe unavailable/denied result without private line/payment/recipient data or mutation. Staff cannot read across unassigned branches; a Manager's title is not applicable delegation.

**Steps 2 and 5 — Admin technical support request**

Display only internal order/attempt reference, branch code, timestamps, payment/hold/fulfilment states and sanitized technical codes. Exclude cart/product/options, customer identity/contact/address/notes and authentication data; grant no mutation or impersonation.

**Steps 3 and 4 — Data retrieval fails or payment outcome is uncertain**

Display a safe failure or recorded pending/exception fact with appropriate guidance, not an empty order or fabricated payment/fulfilment success. Payment recovery belongs to eligible UC46 rules.

**Steps 3 and 5 — Catalogue changes or product-only legacy snapshot**

Retain accepted historical values, show unknown legacy option information without guessing and do not substitute current catalogue terms.

**Step 5 — Carrier personal data reached retirement**

Delete/irreversibly obscure retired recipient/contact/address values and applicable copies while retaining necessary minimized payment/custody evidence; do not expose old raw values through audit/details.

**Step 6 — Processing branch now inactive**

Preserve the recorded branch and history; no silent relocation occurs. An inactive branch cannot receive new checkout, but accepted orders retain their branch and original deadlines. Currently authorized assigned Staff/delegated Manager may complete paid orders; physical handover problems require operational follow-up without automatic transfer, cancellation or refund.

#### Business Rules

BR-48-01, BR-48-02, BR-48-03, BR-48-04, BR-48-05, BR-48-06, BR-48-07

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-48-01 | Members read own orders; Staff reads assigned-branch orders and Manager requires explicit applicable action/branch delegation. Recheck current authority. |
| BR-48-02 | Admin sees only shared-policy technical diagnostics, with no customer data, general detailed-order access, impersonation or business mutation. |
| BR-48-03 | Keep purchase snapshots, payment, hold and fulfilment facts distinct; viewing never changes them or grants payment/completion authority. |
| BR-48-04 | Preserve legacy snapshots without guessed options; new checkout requires configured variants. No public order lookup or courier tracking is provided. |
| BR-48-05 | Retain necessary carrier data until actual handoff, then retire private values/copies after 30 days while keeping minimized five-year evidence. |
| BR-48-06 | Inactive branches block new checkout but retain accepted orders, deadlines and currently authorized processing; no automatic transfer occurs. |
| BR-48-07 | Record each attempt and its actual outcome under the shared audit policy. |
