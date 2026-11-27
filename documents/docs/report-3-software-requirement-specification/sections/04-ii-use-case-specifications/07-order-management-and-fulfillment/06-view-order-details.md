### 7.4 View Order Details

| Use Case ID | UC-57 | Use Case Name | View Order Details |
| --- | --- | --- | --- |
| Created By | ThinhBT | Created Date | 20/11/2026 |
| Primary Actors | Member; Staff; Manager; Admin | Secondary Actors | None |

#### Description

As an authorized order viewer, I want the frozen retail purchase and recorded payment/internal fulfilment facts so that I can understand the order without repricing it or implying GHTK provider tracking.

#### Trigger

An authorized actor opens a retail order reference.

#### Preconditions

1. The platform is available and an order reference is supplied.
2. The actor is authenticated with verified identity, has an active account, and holds current authority.
3. The purpose fits own-order access, assigned-branch Staff access, Manager delegation, or Admin technical support.

#### Postconditions

1. The authorized actor sees only purpose-appropriate recorded order information, or a safe denied/error result.
2. No payment, hold, stock, or fulfilment mutation occurs; historical and data-retirement boundaries remain intact.

#### Normal Flow

1. Actor opens a retail order reference for an authorized purpose.
2. System rechecks ownership, branch, and account authority before revealing protected data.
3. System retrieves frozen purchase snapshots, payment facts, deadlines, and fulfilment evidence.
4. System applies purpose-based minimization and 30-day carrier recipient-data retirement rules.
5. System displays permitted order details and eligible next-action links.

#### Alternative Flows

3a. Admin technical support request → System displays only internal order/attempt reference, branch code, timestamps, states, and sanitized technical codes, excluding customer, product, and auth data.

3b. Catalogue changed or product-only legacy snapshot → System retains accepted historical values and marks legacy options unknown without substituting current catalogue terms.

4a. Carrier personal data reached retirement → System obscures/deletes retired recipient, contact, and address values while retaining minimized payment/custody evidence.

#### Exception Flows

2a. Unknown order, wrong owner/branch, or missing/revoked delegation → System returns a safe denied/unavailable result without exposing private data or mutating state.

3c. Retrieval failure or uncertain payment outcome → System displays a safe failure or recorded pending/exception fact with guidance, without fabricating payment or fulfilment success.

5a. Processing branch became inactive → System preserves the recorded branch, history, and deadlines without silent relocation, allowing currently authorized Staff/delegated Manager to finish paid orders.

#### Business Rules

BR-57-01, BR-57-02, BR-57-03, BR-57-04, BR-57-05, BR-57-06, BR-57-07

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-57-01 | Members read own orders; Staff reads assigned-branch orders and Manager requires explicit applicable action/branch delegation. Recheck current authority. |
| BR-57-02 | Admin sees only shared-policy technical diagnostics, with no customer data, general detailed-order access, impersonation or business mutation. |
| BR-57-03 | Keep purchase snapshots, payment, hold and fulfilment facts distinct; viewing never changes them or grants payment/completion authority. |
| BR-57-04 | Show the current catalogue name, SKU and size when readable and the accepted snapshot otherwise; preserve legacy snapshots without guessed options; new checkout requires configured variants. No public order lookup or GHTK provider tracking is provided; only the shipment step recorded in the platform is shown. |
| BR-57-05 | Retain necessary carrier data until actual handoff, then retire private values/copies after 30 days while keeping minimized five-year evidence. |
| BR-57-06 | Inactive branches block new checkout but retain accepted orders, deadlines and currently authorized processing; no automatic transfer occurs. |
| BR-57-07 | Record each attempt and its actual outcome under the shared audit policy. |
