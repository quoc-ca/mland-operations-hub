# Feature Specification: Retail Order and Fulfillment

**Feature Branch**: `008-retail-order-and-fulfillment`

**Created**: 2026-10-22

**Status**: Draft

**Input**: User description: "Start the specification for FE-08, covering retail sales and order fulfillment, including the handoff from FE-02 product data to the Member purchase journey."

**Selected decisions for this draft**: automatic GHTK shipment creation after
verified payment; GHTK shipping fee included in checkout; prepaid delivery with
no COD; retry after provider failure; webhook-first status synchronization with
status-API fallback; contract-first development with a mock provider until real
credentials are available.

## Scope and source alignment

This feature specifies the Member retail purchase journey and the shop-side
fulfillment journey for ready-to-sell products. It covers the FE-08 use cases
from product selection through cart management, fulfilment selection, shipping
fee quotation, checkout, full payment, order review, pickup, and GHTK shipment
handoff.

The current project business baseline supplies the existing retail rules for
this draft. GHTK API-based quotation, shipment creation, and status tracking
are in scope for FE-08; the existing baseline inventories must be synchronized
with this decision before implementation:

- Retail checkout is available to authenticated active Members only.
- Before browsing or selecting products for purchase, the Member selects an
  active processing branch. Catalogue availability and purchase eligibility are
  evaluated in that branch context. Each product added to the cart records the
  branch selected at that time; changing the selected branch does not rewrite
  existing cart lines, and newly added products use the newly selected branch.
- At checkout, cart lines are grouped by their recorded branch as separate
  branch orders, similar to separate shops in a marketplace. Each branch order
  has its own fulfilment choice, shipping information, preparation state, and
  carrier status, while the Member may review the grouped checkout together.
- The Member makes one payment for the grouped checkout. The checkout summary
  MUST show each branch order's item subtotal and GHTK shipping fee separately,
  followed by the combined payable total.
- The whole cart is revalidated before the complete set of branch orders is
  accepted.
- A verified full payment is required before the retail sale is completed,
  except for an approved checkout whose payable total is zero.
- The Member chooses store pickup or GHTK fulfilment during checkout. GHTK
  recipient data is required before the shipping quote and payment are
  accepted.
- After verified full payment, the system creates the GHTK shipment through the
  provider API, stores the returned tracking identifiers, receives status
  webhooks, and uses status retrieval as a fallback.
- GHTK delivery status is visible to the Member, but this feature does not add
  delivery-failure resolution, returns, cancellation, or refund workflows.

The feature consumes product/catalogue data from FE-02, promotion and loyalty
eligibility from FE-06, payment capability from the shared payment domain, and
workflow email delivery from the shared notification capability. It does not
own catalogue maintenance, promotion policy administration, payment-provider
configuration, accounting, or executive reporting in FE-10.

### FE-02 to FE-08 ownership boundary

- FE-02 owns the published product catalogue, product and variant identity,
  configuration eligibility, displayed price inputs, availability inputs, and
  shipping attributes such as package weight and dimensions.
- FE-08 owns the Member purchase command and all purchase-side state: adding a
  configured variant to the Member's cart, editing the cart, checkout, payment,
  order history, order details, and retail fulfilment.
- A Product Details page may present an **Add to Cart** action, but the action
  belongs to the FE-08 purchase journey and MUST use the FE-02 catalogue data
  only as an eligibility and pricing source.
- Public browsing or viewing product details MUST NOT create a cart, order,
  payment attempt, stock hold, or sold-quantity deduction. Those effects begin
  only when an authenticated Member submits a valid FE-08 purchase action.
- FE-08 MUST revalidate the current FE-02 product and variant data before
  checkout and MUST freeze the accepted product facts in the retail order so
  later catalogue changes do not rewrite an accepted purchase.

## Clarifications

### Session 2026-10-22

- Q: Should an unpaid retail checkout hold expire after 15 minutes from checkout acceptance, releasing held quantities and benefits when no valid full payment is confirmed? → A: Yes. The system automatically expires the hold after a maximum of 15 minutes, releases the held quantities and benefits, and sends late payment evidence to manual operational review instead of reopening the expired hold automatically.
- Q: Should package weight and dimensions used for GHTK quotation come from each product or variant? → A: Yes. FE-02 owns the authoritative shipping weight and package dimensions for each eligible product or variant; FE-08 uses that data for the quote and shipment request and does not silently replace it with a Staff-entered value.
- Q: When GHTK returns multiple fee components, which components should be included in the Member's payable checkout total? → A: Include the delivery fee and only mandatory surcharges explicitly approved by Mland; do not automatically include GHTK insurance fees.
- Q: If VNPay confirms payment but GHTK shipment creation fails, how should the system handle the paid order? → A: Preserve the paid Mland order in a shipment-pending state, notify or surface the exception to Staff, and retry safely with the same partner order identifier; do not create a duplicate shipment or automatically refund the Member.
- Q: When a Member views a GHTK order, how much tracking information should be displayed? → A: Display only the latest normalized carrier status, the latest update time, and the provider-supplied estimated delivery date when available; do not expose the raw provider event history.

### Session 2026-10-23

- Q: Which feature owns purchase actions shown after a Member browses the FE-02
  catalogue? → A: FE-02 owns catalogue data and public browsing; FE-08 owns
  Add to Cart, cart management, checkout, payment, retail order history and
  details, and fulfilment. A Product Details page may invoke the FE-08 purchase
  action, but browsing alone creates no purchase-side state.
- Q: Is GHTK API integration allowed as part of the first FE-08 release? → A:
  Yes. GHTK API quotation, shipment creation, and status tracking are in scope;
  development uses a mock provider until real GHTK Partner credentials and
  access are available.
- Q: Who may process a retail order and hand it over within a branch? → A:
  Staff may process orders only within the assigned branch; a Manager may
  process orders only within an explicitly delegated branch and action scope.
  Staff or Managers from another branch must be denied access and operations.
- Q: When does the Member choose the processing branch? → A: The Member
  chooses an active processing branch before browsing or selecting products.
  Each cart line records the branch selected when that product was added. If the
  Member later changes branch, existing cart lines keep their original branch,
  while newly added products use the newly selected branch.
- Q: How should a cart containing products from multiple branches behave at
  checkout? → A: The cart behaves like a marketplace cart with multiple shops.
  The system groups cart lines by branch and creates a separate branch order for
  each group. Each branch order has its own fulfilment and carrier lifecycle,
  while the Member can review the grouped checkout together.
- Q: How should a Member pay for branch orders from one grouped checkout? →
  A: The Member makes one VNPay payment for the combined total. The checkout
  clearly shows each branch order's item subtotal and its own GHTK shipping fee
  separately, then shows the combined payable total. A verified result applies
  to all branch orders in that grouped checkout exactly once.

### Session 2026-10-24

- Q: Does FE-08 own the complete retail promotion and loyalty lifecycle through
  the FE-06 named interface? → A: Yes. FE-08 obtains benefit previews from
  trusted server-priced facts, calls `reserveAcceptedTransaction` through
  `loyaltypromotion::facade` in the same source transaction that creates the
  grouped checkout and all holds, settles benefits and grants earned points
  once after verified full payment or approved zero-total acceptance, and
  releases held benefits plus marks pending earn ineligible only after an
  authoritative unpaid payment failure or expiry. Ambiguous or conflicting payment evidence
  remains unresolved for reconciliation and does not release, consume, grant,
  or extend benefits.
- Q: When does FE-08 persist GHTK recipient data used to obtain a shipping
  quote? → A: The validated recipient draft remains only in the current
  checkout session while the Member requests or refreshes a quote. FE-08
  persists the immutable recipient snapshot only when the Member accepts the
  complete grouped checkout; it creates neither a server-side checkout draft
  nor a draft order merely to obtain a quote.
- Q: Can a Member change fulfilment after accepting checkout? → A: No. The
  accepted checkout freezes each branch's fulfilment method, recipient snapshot,
  and shipping fee. If an unpaid checkout needs a different fulfilment choice,
  the Member cancels that checkout and creates a new checkout, which obtains a
  new quote before acceptance.
- Q: Which feature owns the product-list API consumed by the retail journey?
  → A: FE-02 owns catalogue endpoints and authoritative product, variant, price,
  stock, and shipping facts. FE-08 owns cart and checkout commands and consumes
  only FE-02-validated facts; it does not publish a competing product-list API.
- Q: When may a Member cancel an accepted unpaid checkout? → A: The Member may
  cancel only before FE-08 initiates payment with VNPay. Cancellation immediately
  releases inventory and held benefits and restores the cart; once payment has
  been initiated, the checkout cannot be self-cancelled because provider payment
  evidence may still arrive and must be reconciled safely.
- Q: What if the cart is full or its variant quantity limit prevents restoring
  every cancelled item? → A: Complete the cancellation and release the holds,
  restore only what fits without removing existing cart items, and report the
  unrestored quantity to the Member. Repeated cancellation returns the saved result.
- Q: How is an approved zero-total grouped checkout completed? → A: FE-08 does
  not initiate VNPay. It records one idempotent internal zero-total acceptance,
  settles the reserved benefits, grants any eligible earned points, and proceeds
  to fulfilment using the same safeguards as a verified paid checkout.
- Q: Does FE-08 officially replace Report 3's manual-only GHTK rule with
  automatic GHTK API integration in the first release? → A: Yes. Automatic
  GHTK fee quotation, shipment creation, webhook/status synchronization and
  Member tracking are official V1 FE-08 scope. Development and automated tests
  use a mock provider until approved GHTK Partner access is available; Report 3
  must be synchronized with this decision.
- Q: If production does not have approved GHTK Partner access or the GHTK
  integration is disabled, which fulfilment choices remain available? → A: The
  system displays a safe notice asking the Member to contact Staff for support,
  disables GHTK selection, and continues to offer store pickup. It does not use
  mock GHTK responses or silently create a manual carrier workflow in production.
- Q: In a multi-branch checkout, what happens when GHTK is unavailable for only
  one branch? → A: Only the affected branch loses its GHTK option and requires
  the Member to select store pickup; other eligible branches may continue with
  GHTK. The system recalculates the grouped total and requires the Member to
  confirm it again without silently changing fulfilment or removing items.
- Q: If a GHTK quote expires or changes before the Member starts payment, how
  is the new total handled? → A: The system obtains a new quote, displays the
  fee difference and recalculated grouped total, and requires the Member to
  confirm the complete checkout again before creating a payment attempt. It
  does not silently update the payable amount or redirect to VNPay.
- Q: If GHTK fails or times out while the Member requests a shipping quote,
  what checkout data and recovery actions remain available? → A: The system
  preserves the cart and recipient draft within the current checkout session,
  creates no payment attempt, and lets the Member retry the quote or explicitly
  change only the affected branch to pickup.

### Session 2026-10-25

- Q: When must a benefit preview be recalculated and accepted again? → A: FE-08
  invalidates the prior preview/acceptance when the cart, selected benefit,
  fulfilment choice, or shipping quote and fee changes. The Member must review
  the recalculated complete total and accept it again before checkout.
- Q: How are stock and FE-06 benefits reserved at checkout acceptance? → A:
  `reserveAcceptedTransaction` is mandatory in the same source transaction as
  grouped checkout creation, inventory holds, and immutable snapshots. Any
  required failure rolls back the complete acceptance.
- Q: What happens after payment succeeds? → A: After the backend verifies a
  full VNPay payment, FE-08 settles the held benefit and then asks FE-06 to
  grant earned points exactly once under FE-06 policy. Shipping is excluded
  from the promotion and point-earning base.
- Q: What happens after an authoritative unpaid payment failure or expiry? →
  A: FE-08 releases the complete inventory/benefit hold bundle, marks pending
  point earning ineligible, and restores the source cart. Ambiguous or late
  payment evidence remains in reconciliation and does not release holds.
- Q: When may a Member cancel? → A: Only in `ACCEPTED_UNPAID`, before FE-08
  persists VNPay initiation. Once `PAYMENT_INITIATED`, self-cancellation is
  rejected and provider payment evidence is reconciled.

## Actors and permissions

| Actor | Permission |
| --- | --- |
| Member | Select an active processing branch before browsing products, add eligible configured variants to the Member's own cart, edit the cart, choose fulfilment, review shipping fees, checkout, pay, and view only the Member's own orders and permitted shipment status. |
| Staff | Review and process paid retail orders within the assigned branch, prepare orders, record customer pickup, and complete the physical GHTK handoff when authorized. |
| Manager | Perform retail operational actions within an explicitly delegated branch/action scope; a Manager does not automatically receive unrestricted cross-branch access. |
| Guest | Browse public products only; cannot create a retail cart checkout or retail order. |
| Admin | May manage technical/account configuration but cannot use administrative authority as a retail-order business override. |
| VNPay | Receive payment initiation requests and return provider payment evidence for server-side verification. |
| GHTK | Calculate shipping fees, accept shipment creation requests, return tracking identifiers, and notify Mland of shipment-status changes. |

## User Scenarios & Testing

### User Story 1 - Prepare a retail purchase (Priority: P1)

As a Member, I want to select configured products and manage my own cart so
that I can prepare a purchase without creating a reservation or order too
early.

**Why this priority**: Cart preparation is the entry point for every retail
purchase and must protect ownership, catalogue eligibility, and quantity
integrity before checkout.

**Independent Test**: An authenticated Member can add a valid configured
variant, change its quantity, remove it, and see the resulting own-cart state
without any order, payment, or stock-sale effect.

**Acceptance Scenarios**:

1. **Given** a published product with an eligible configured variant, **When**
   the Member submits a positive integer quantity, **Then** the item appears in
   the Member's own cart with its current indicative price.
2. **Given** the Member edits or removes a cart line, **When** the submission is
   valid and current, **Then** only that Member's cart is updated and no order
   or payment is created.
3. **Given** an unpublished product, unsupported variant, invalid quantity, or
   another Member's cart, **When** the request is submitted, **Then** the
   system rejects it without exposing protected data or creating a purchase
   effect.
4. **Given** a stale cart value or concurrent cart edit, **When** the Member
   submits the change, **Then** the system reports the conflict and shows
   current values for review instead of claiming false success.

---

### User Story 2 - Checkout and pay for a retail order (Priority: P1)

As a Member, I want to review a complete grouped quote and pay its full amount
so that the system creates exactly the branch orders and purchase terms that I
accepted.

**Why this priority**: Verified payment is the business gate for completing a
retail sale and for allowing fulfilment to begin.

**Independent Test**: A Member with a valid cart can review products grouped by
branch, choose fulfilment for each branch order, complete a matching VNPay
payment, and see the accepted branch orders become paid without partial orders
or duplicate payment effects.

**Acceptance Scenarios**:

1. **Given** a non-empty own cart containing one or more branch groups, **When**
   the Member opens checkout, **Then** the system revalidates every line and
   shows current variants, quantities, prices, branch groups, fulfilment
   choices, applicable benefits, each branch order's separate shipping fee when
   GHTK is selected, and the combined payable total.
2. **Given** every cart line, requested benefit, fulfilment choice, recipient
   data, and shipping quote is valid, **When** the Member accepts the complete
   quote, **Then** the system creates one complete branch order for each branch
   group, holds the exact selected quantities and benefits, snapshots each
   applicable shipping fee, and creates the payment attempt with the original
   deadlines.
3. **Given** any line is invalid, unavailable, or changed, **When** checkout is
   submitted, **Then** the system rejects the complete checkout and creates no
   partial branch order or partial hold.
4. **Given** a valid grouped payment attempt whose amount includes the sum of
   every branch order subtotal and its applicable GHTK shipping fee, **When**
   VNPay returns matching verified evidence for the full amount and currency,
   **Then** the system marks the grouped payment and every branch order as
   accepted exactly once.
5. **Given** a valid grouped checkout has an approved payable total of zero,
   **When** the Member accepts the complete quote, **Then** the system records
   one idempotent zero-total acceptance without initiating VNPay and makes the
   branch orders available for fulfilment exactly once.
5. **Given** a browser redirect, invalid signature, mismatched amount, late
   result, or duplicate provider event, **When** payment processing occurs,
   **Then** the system does not falsely mark the order as paid or repeat sale
   effects.
6. **Given** the same checkout command is retried unchanged, **When** the
   request is received again, **Then** the system returns the existing result
   without creating another branch order, hold, or payment attempt.

---

### User Story 3 - Review and choose order fulfilment (Priority: P1)

As a Member, I want to choose pickup or GHTK delivery during checkout and view
the resulting shipment progress so that I know how the order will be completed
and where the carrier currently reports it.

**Why this priority**: Fulfilment choice connects the paid retail sale to the
shop's preparation and handover process.

**Independent Test**: A Member can select fulfilment before payment, provide
valid GHTK recipient data when needed, and later view the returned carrier
status and estimated delivery information for the own order.

**Acceptance Scenarios**:

1. **Given** a Member has a valid cart, **When** the Member chooses pickup,
   **Then** the system excludes GHTK shipping fees and keeps the accepted order
   branch, product terms, and payment terms unchanged.
2. **Given** the Member chooses GHTK, **When** valid recipient name, phone,
   address, and optional note are submitted, **Then** the system requests a
   shipping quote and shows the resulting fee before payment acceptance.
3. **Given** GHTK does not support the recipient address or cannot provide a
   valid quote, **When** the Member submits checkout, **Then** the system blocks
   the GHTK option and provides safe correction or pickup guidance.
4. **Given** the Member has accepted checkout, **When** the Member needs a
   different fulfilment choice before payment, **Then** the system requires the
   Member to cancel the unpaid checkout and create a new checkout with a fresh
   quote; it does not modify the accepted checkout, order state, or payment
   quote.
5. **Given** a paid GHTK order has a provider tracking identifier, **When** the
   Member views the order, **Then** the system displays the mapped carrier
   status, last update time, and estimated delivery information when supplied,
   without presenting the estimate as a guarantee.

---

### User Story 4 - Process and complete a retail order (Priority: P1)

**Owner decision (2026-10-30):** A Staff member assigned to the order's
location may mark a pickup order prepared at that location. Cross-location
Staff access is not permitted. For pickup, the customer shows their order
information to Staff, who checks it in person and records handover; no
additional QR scanner or second-account verification is required by FE-08.

As an authorized Staff member or delegated Manager, I want to process a paid
retail order and record the actual handover so that responsibility and order
milestones are auditable.

**Why this priority**: The shop must be able to complete pickup and physical
carrier handoff while the system creates and tracks the paid GHTK shipment
without unauthorized branch access or invalid state transitions.

**Independent Test**: An authorized shop actor can open an eligible paid order
within scope, prepare it, and record either a Staff-confirmed customer pickup or
the physical GHTK handoff after a successful provider shipment creation.

**Acceptance Scenarios**:

1. **Given** a paid order is within the actor's assigned branch/action scope,
   **When** the actor opens the retail order list, **Then** the system shows
   only orders the actor is permitted to process.
2. **Given** a paid order has a valid fulfilment choice, **When** the actor
   records preparation, **Then** the system advances the order to the valid
   preparation milestone and prevents later Member fulfilment edits.
3. **Given** a prepared pickup order and the customer shows the order to Staff,
   **When** an authorized Staff member confirms the actual handover, **Then**
   the system records pickup once with the Staff actor and time.
4. **Given** a paid order has GHTK fulfilment selected, **When** the system
   creates the shipment, **Then** it stores the provider tracking identifiers
   exactly once and makes the order eligible for preparation.
5. **Given** a prepared GHTK order with a valid provider tracking identifier,
   **When** the actor physically hands it to GHTK, **Then** the system records
   carrier custody once and starts the delivery-data retention clock.
6. **Given** an unpaid, wrong-branch, wrong-method, stale, duplicate, provider
   failure, or already-terminal order, **When** the actor or integration submits
   an operation, **Then** the system rejects or retries it safely without
   skipping milestones or repeating settlement.

---

### User Story 5 - View retail order history and details (Priority: P2)

**Owner decision (2026-10-30):** Display the latest catalogue product name,
SKU and size where available. The purchased variant, quantity and financial
amounts remain the immutable facts of the order. When current catalogue data
cannot be read, keep showing the saved order facts and leave those display
fields empty.

As a Member, I want to review my order history and order details so that I can
understand what I purchased and the current fulfilment milestone.

**Why this priority**: Order visibility supports customer self-service after
checkout without allowing access to another Member's data.

**Independent Test**: A Member can list and open only the Member's own orders,
including frozen items, amounts, payment state, and fulfilment evidence.

**Acceptance Scenarios**:

1. **Given** an authenticated Member requests order history, **When** the
   request is authorized, **Then** the system returns only that Member's
   permitted orders.
2. **Given** the Member opens an own-order reference, **When** the order exists,
   **Then** the system displays immutable purchase snapshots and current
   payment/fulfilment states.
3. **Given** the reference belongs to another Member or does not exist, **When**
   the request is submitted, **Then** the system does not reveal protected
   order information.

### Edge Cases

- The cart is empty, contains too many lines, or requests a quantity outside
  the permitted integer range.
- A product is unpublished, a variant is disabled, or available quantity
  changes between browsing and checkout.
- A promotion or loyalty request is no longer eligible or its policy is
  incomplete at checkout.
- Two Members compete for the last available quantity or benefit quota.
- The Member double-submits checkout, payment, fulfilment selection, or a
  retry after an uncertain response.
- Payment expires, fails, arrives late, has a mismatched amount/currency, or
  produces a duplicate/out-of-order provider event.
- The processing branch becomes inactive after an order is accepted.
- The Member submits incomplete GHTK recipient data or GHTK does not support
  the destination.
- GHTK fee quotation, shipment creation, status retrieval, or webhook delivery
  times out or returns an invalid response.
- A GHTK fee request fails or times out; the cart and current recipient draft
  remain available, no payment attempt is created, and the Member may retry or
  explicitly select pickup for the affected branch.
- A GHTK quote expires or changes before payment; the system shows the new fee
  and grouped total and requires explicit reconfirmation before payment.
- Approved GHTK Partner access is absent or the production integration is
  disabled; GHTK selection is unavailable with contact-Staff guidance while
  store pickup remains available.
- GHTK is unavailable for only one branch in a grouped checkout; that branch
  requires an explicit pickup selection and grouped-total reconfirmation while
  unaffected branches retain their eligible fulfilment choices.
- A shipment creation response is lost after GHTK has already accepted the
  partner order identifier.
- The same GHTK webhook is delivered more than once or arrives out of order.
- GHTK reports delivery delay, delivery failure, cancellation, or return; the
  system displays the mapped status but does not invent a refund or return
  resolution workflow.
- A Staff/Manager actor attempts a cross-branch operation or a transition
  that does not match the current state.
- Delivery recipient data reaches its retention deadline while an order's
  minimized handoff evidence must remain available.

## Requirements

### Functional Requirements

- **FR-08-001**: The system MUST allow only an authenticated active Member to
  create or modify a retail cart owned by that Member.
- **FR-08-002**: The system MUST accept only published, eligible, configured
  product variants, MUST limit a cart to at most 50 lines, and MUST accept an
  integer quantity from 1 through 99 per variant across all lines, subject to
  current logical availability.
- **FR-08-003**: The system MUST keep cart values indicative until checkout and
  MUST not create an order, payment, hold, or sold-quantity deduction from a
  cart edit alone.
- **FR-08-004**: The system MUST revalidate the entire cart, processing branch,
  current price, quantity, promotion, loyalty eligibility, fulfilment choice,
  recipient data, delivery coverage, and shipping quote before accepting
  checkout.
- **FR-08-005**: The system MUST group cart lines by their recorded processing
  branch and create either one complete branch order for every group together
  with the complete hold/benefit bundle, or no accepted branch orders and no
  partial bundle.
- **FR-08-006**: The system MUST freeze the accepted product/variant identity,
  quantity, branch, fulfilment method, recipient snapshot when applicable,
  currency, price, shipping fee, discount, payable amount, and applicable policy
  evidence for the order.
- **FR-08-007**: The system MUST allow at most one approved voucher and only
  the configured loyalty benefit combination; it MUST not silently invent,
  remove, or round a requested benefit.
- **FR-08-008**: The system MUST use a 10-minute payment-link deadline and a
  maximum 15-minute unpaid checkout-hold deadline; when no valid full payment
  is confirmed within that hold deadline, it MUST expire the hold and release
  the held quantities and benefits, while routing any later payment evidence to
  manual operational review.
- **FR-08-009**: The system MUST require one verified full payment matching the
  grouped checkout target, combined amount, and currency before marking the
  grouped payment and all branch orders complete, except that an approved
  zero-total grouped checkout MUST complete through one idempotent internal
  acceptance without initiating VNPay.
- **FR-08-010**: The system MUST reject browser-only success, invalid provider
  evidence, mismatched payment data, late payment, and duplicate payment
  effects without falsely completing the sale.
- **FR-08-011**: The system MUST make unchanged checkout and payment retries
  idempotent and MUST reject conflicting reuse of an existing command key.
- **FR-08-012**: The system MUST allow the order-owning Member to choose pickup
  or GHTK fulfilment independently for each branch order before accepting
  checkout; after acceptance, the chosen fulfilment method is immutable.
- **FR-08-013**: The system MUST validate GHTK recipient name, phone, address,
  and optional note before requesting a shipping quote; the validated recipient
  draft MUST remain only in the current checkout session until checkout
  acceptance, and the system MUST persist an order-specific immutable recipient
  snapshot only when the Member accepts the complete grouped checkout. Recipient
  name MUST contain 1–100 Unicode characters,
  domestic mobile phone MUST use 10 digits beginning with `0` or the equivalent
  `+84` form, address MUST contain 10–500 Unicode characters, and the optional
  note MUST contain at most 500 characters after trimming leading/trailing
  whitespace. The system MUST not assume an address book or copy a profile
  address automatically.
- **FR-08-014**: The system MUST freeze the fulfilment method, recipient
  snapshot, and shipping fee after checkout acceptance. A Member needing a
  different fulfilment choice for an unpaid checkout MUST cancel it and create a
  new checkout with a fresh quote; no fulfilment change may reprice or change an
  accepted or paid order.
- **FR-08-015**: The system MUST restrict Staff and Manager order operations by
  current account status, assigned branch, delegated action, and current order
  state.
- **FR-08-016**: The system MUST require verified payment, valid preparation,
  and the correct current state before recording pickup or carrier handoff.
- **FR-08-017**: Pickup MUST verify the signed-in owning Member at the counter;
  an order code, screenshot, or unrelated third party MUST NOT be sufficient.
- **FR-08-018**: The system MUST record carrier, provider tracking identifier,
  operational actor, and actual handoff time for a completed GHTK handoff.
- **FR-08-019**: The system MUST expose mapped GHTK shipment statuses and
  provider-supplied estimated delivery information when available, while not
  treating an estimate as a delivery guarantee or creating a refund/return
  resolution workflow.
- **FR-08-020**: The system MUST allow a Member to view only the Member's own
  retail order history and details, including frozen item and payment facts.
- **FR-08-021**: The system MUST record safe audit evidence for accepted,
  rejected, duplicate, stale, and failed retail-order attempts without storing
  raw credentials, payment secrets, or unnecessary personal data.
- **FR-08-022**: The system MUST send only approved workflow notifications and
  MUST not treat email delivery as a business decision or payment proof.
- **FR-08-023**: The system MUST delete or irreversibly obscure GHTK recipient,
  contact, address, and optional note data 30 days after the original actual
  handoff while preserving minimized order, payment, and custody evidence.
- **FR-08-024**: The system MUST request and snapshot the GHTK delivery fee and
  any explicitly approved mandatory surcharge separately for each branch order
  before payment when GHTK fulfilment is selected, MUST display those fees as
  separate checkout line items, MUST exclude unapproved optional components
  such as insurance fees, and MUST include the sum of accepted charges in the
  Member's single VNPay payable amount.
- **FR-08-025**: After verified full payment, the system MUST create the GHTK
  shipment once using the accepted partner order identifier and MUST persist the
  returned provider label/tracking identifiers without creating COD collection.
- **FR-08-026**: A GHTK shipment-creation timeout or ambiguous response MUST be
  retryable and MUST not create a duplicate provider shipment or change the
  already accepted payment result.
- **FR-08-027**: The system MUST accept GHTK webhook updates idempotently,
  acknowledge successfully received valid updates, and retain safe normalized
  status evidence without storing raw provider secrets or payloads.
- **FR-08-028**: The system MUST use the GHTK status-retrieval API as a fallback
  when webhook delivery is missing, stale, or requires reconciliation.
- **FR-08-029**: The system MUST map provider status codes to a controlled
  customer-facing status vocabulary and MUST preserve the provider code for
  authorized operational diagnosis.
- **FR-08-030**: Customer-facing tracking MUST expose only the Member's own
  permitted order status, last update time, and estimated delivery information;
  it MUST not expose provider credentials, raw callback data, or unnecessary
  recipient PII.
- **FR-08-031**: When approved GHTK Partner access is unavailable or the
  production GHTK integration is disabled, the system MUST display safe
  contact-Staff guidance, MUST prevent GHTK selection, MUST continue to offer
  store pickup, and MUST NOT substitute mock responses or an unapproved manual
  carrier workflow.
- **FR-08-032**: When GHTK is unavailable for only one branch in a grouped
  checkout, the system MUST require the Member to select pickup for that branch,
  MUST preserve eligible fulfilment choices for unaffected branches, MUST
  recalculate and require confirmation of the complete grouped total, and MUST
  NOT silently change fulfilment or remove items.
- **FR-08-033**: When an accepted GHTK quote expires or changes before payment
  initiation, the system MUST obtain a new quote, MUST display the fee
  difference and recalculated grouped total, and MUST require the Member to
  confirm the complete checkout again before creating a payment attempt; it
  MUST NOT silently change the payable amount or redirect to VNPay.
- **FR-08-034**: When a GHTK fee request fails or times out, the system MUST
  preserve the Member's cart and recipient draft within the current checkout
  session, MUST create no payment attempt, and MUST allow the Member to retry
  the quote or explicitly change only the affected branch to pickup.
- **FR-08-035**: FE-08 MUST request promotion and loyalty benefit previews
  through `loyaltypromotion::facade` using trusted server-priced checkout facts;
  it MUST NOT trust browser-supplied prices, discounts, point values, or payable
  totals. Its accepted selection MUST use the FE-06 canonical `choice`,
  `voucherCode`, and `requestedPoints` fields, and shipping fees MUST be
  excluded from the discount and point-earning base. A cart, selected-benefit,
  fulfilment, or shipping-quote/fee change MUST invalidate the prior
  preview/acceptance and require the Member to accept the recalculated complete
  total again.
- **FR-08-036**: When the Member accepts a grouped checkout, FE-08 MUST call
  `reserveAcceptedTransaction` in the same source transaction that creates all
  branch orders, inventory holds, immutable price snapshots, and benefit holds;
  failure of any required operation MUST roll back the complete acceptance.
- **FR-08-037**: After verified full payment or approved zero-total acceptance,
  FE-08 MUST settle reserved benefits and then grant earned points exactly once,
  using the same source reference and idempotent outcome version for retries.
- **FR-08-038**: After authoritative unpaid payment failure or expiry, FE-08
  MUST release held benefits, mark pending point earning ineligible, release
  inventory holds, and restore the source cart. Ambiguous, late, or conflicting
  payment evidence MUST remain unresolved for reconciliation and MUST NOT
  automatically release, consume, grant, or extend benefits.
- **FR-08-039**: Before FE-08 initiates payment with VNPay, the order-owning
  Member MUST be able to cancel an accepted unpaid checkout. The system MUST
  immediately release its inventory and held benefits, restore the cart, and
  create no payment initiation. The grouped checkout MUST move from
  `ACCEPTED_UNPAID` to `PAYMENT_INITIATED` only when the server has persisted
  the VNPay initiation attempt and its `paymentInitiatedAt` time. Once in
  `PAYMENT_INITIATED`, the system MUST reject self-cancellation and retain the
  checkout for payment-result reconciliation.

### Logical Data Ownership

| Logical record | Required evidence |
| --- | --- |
| Member cart | Owner, variant identity, quantity, indicative terms, last edit time, and safe edit outcome. |
| Retail order | One branch-specific order containing the Member, processing branch, lifecycle state, checkout key, immutable amount/benefit snapshots, and deadlines. |
| Order item | Product/variant identity, options, quantity, unit price, and line amount frozen at checkout. |
| Grouped checkout payment | Grouped checkout and branch-order targets, combined amount, currency, provider reference when supplied, verified outcome, timestamps, and duplicate/rejection decision. |
| Fulfilment information | Pickup or GHTK method, recipient data while required, preparation state, carrier reference, actor, and handoff time. |
| GHTK shipment | Mland partner order identifier, provider label/tracking identifiers, creation outcome, normalized status, last update time, estimated delivery information, and safe provider correlation. |
| Carrier status event | Provider identifier, status code, action time, normalized outcome, duplicate/replay decision, and processing result without raw callback payload. |
| Audit evidence | Actor, action, scope, outcome, safe correlation reference, and timestamp without raw sensitive payloads. |

## State Model

The grouped checkout has one payment outcome, while each branch order exposes
its own fulfilment and carrier facts separately.

```text
Checkout accepted
        ↓
ACCEPTED_UNPAID ── Member cancels ──> Hold released / cart restored
        ├── payment expires/fails ──> Hold released
        └── VNPay initiation persisted ──> PAYMENT_INITIATED
                                              ↓ verified full payment
Paid / fulfilment selected
        ├── Pickup selected ──> Preparing ──> Ready for pickup ──> Picked up
        └── GHTK selected ──> Creating shipment ──> Shipment created
                                      └─> Preparing ──> Handed to carrier
                                                     └─> Carrier status updates
```

Terminal pickup and carrier-handoff outcomes are not reversed by a repeated
request. Carrier status updates are separate from the Mland fulfilment state;
they may report delivery delay, failure, or return without automatically
creating a refund, cancellation, or return workflow. Payment confirmation must
not be inferred from a browser redirect.

## Error, retry, and authorization behavior

- Every protected operation rechecks the current Member ownership or shop
  authority at submission time.
- Stale values, duplicate commands, and concurrent state changes produce safe
  conflict results and do not create a second business effect.
- A failed or ambiguous provider response preserves known payment/order facts;
  it does not authorize a second sale or silently extend an expired deadline.
- A failed or ambiguous GHTK shipment-creation response preserves the paid
  order and schedules safe retry using the same partner order identifier.
- A GHTK webhook is acknowledged only after its required fields are validated;
  duplicate or late events do not move a shipment backwards or repeat a
  business effect.
- If a webhook is missing or stale, a controlled status-retrieval operation may
  reconcile the shipment using the stored provider identifier.
- A valid provider callback is accepted only when its signature/evidence,
  target, amount, currency, and current order state match.
- Errors expose safe business guidance and correlation information only; raw
  provider payloads, secrets, tokens, and unnecessary personal data are not
  returned or logged.

## External boundaries

- **Payment gateway**: Initiates the full retail payment and provides evidence
  that the system verifies before applying the paid state.
- **Workflow mail sender**: Delivers approved payment and order-status messages
  after the relevant business result is committed.
- **GHTK fee API**: Calculates delivery fee and delivery coverage from the
  configured pickup location, recipient destination, weight, value, and
  selected service before payment.
- **GHTK order API**: Creates the shipment after verified payment and returns
  provider label/tracking identifiers. The Mland partner order identifier is
  reused on safe retries.
- **GHTK status API**: Returns the current provider status, last update, and
  estimated delivery information when supplied.
- **GHTK webhook**: Sends shipment-status changes to an Mland HTTPS endpoint;
  the receiver validates, normalizes, deduplicates, and acknowledges events.
- **GHTK physical handoff**: Receives the prepared package outside the system;
  Staff records actual custody after a provider shipment exists.
- **Product, promotion, and loyalty owners**: Provide current eligibility and
  approved terms through their owning business boundaries.

## Non-functional and security requirements

- The system MUST enforce server-side authorization for every cart, order,
  payment, fulfilment, and staff-operation request.
- The system MUST preserve the separation between Member-owned data and
  assigned-branch operational data.
- The system MUST not expose raw payment credentials, provider secrets, raw
  signed callbacks, or unnecessary delivery PII.
- The system MUST preserve accepted order snapshots when catalogue prices,
  product publication, promotion policy, or Member profile data changes later.
- The React client MUST provide explicit loading, empty, validation, pending,
  success, failure, stale-session, and retry states for applicable FE-08
  requests without presenting client state as authorization or payment proof.
- Payment, hold release, order settlement, and fulfilment transitions MUST be
  safe to retry without duplicating terminal business effects.
- GHTK credentials MUST remain server-side, and frontend clients MUST NOT call
  GHTK directly.
- Provider fee, creation, status, and webhook interactions MUST have safe
  timeout, retry, idempotency, and correlation behavior.

## Non-goals

- Guest retail checkout.
- Product catalogue maintenance or available-to-sell quantity administration.
- Creation or administration of vouchers, promotions, or loyalty policies.
- SPX or Viettel Post integration.
- Automated resolution of delivery failure, return, cancellation, or refund;
  the system may display the provider status without implementing those
  business workflows.
- Accounting, reconciliation, warehouse management, stock transfer, or ERP.
- Executive revenue dashboards and reports owned by FE-10.

## Success Criteria

### Measurable Outcomes

- **SC-08-001**: 100% of checkout attempts containing an invalid or unavailable
  line create no partial accepted order or partial quantity hold.
- **SC-08-002**: 100% of completed grouped checkouts have exactly one matching
  verified full-payment evidence record, or exactly one approved zero-total
  acceptance record, before their branch orders are marked paid.
- **SC-08-003**: Repeating an unchanged checkout, payment callback, pickup
  confirmation, or carrier handoff request creates zero additional terminal
  business effects.
- **SC-08-004**: An authenticated Member can complete the normal cart and
  checkout review journey in under 3 minutes, excluding time spent on the
  external payment provider.
- **SC-08-005**: 100% of protected order-history and order-detail requests
  return only data owned by the requesting Member.
- **SC-08-006**: 100% of recorded pickups and carrier handoffs contain the
  required actor, time, state, and verification evidence.
- **SC-08-007**: Eligible GHTK recipient/contact/address data is deleted or
  irreversibly obscured within the approved 30-day post-handoff boundary while
  minimized custody evidence remains available.
- **SC-08-008**: 100% of GHTK orders include an accepted shipping-fee snapshot
  in the paid checkout total before shipment creation.
- **SC-08-009**: Duplicate, delayed, and out-of-order GHTK webhook events do
  not create duplicate shipment records or regress the customer-facing status.
- **SC-08-010**: For a valid GHTK shipment, the Member can view the latest
  normalized carrier status and provider-supplied estimated delivery
  information without receiving provider credentials or raw callback data.

## Assumptions and unresolved implementation gates

- An unpaid checkout hold expires automatically after a maximum of 15 minutes;
  held quantities and benefits are released, and late payment evidence is sent
  to manual operational review. The payment link remains valid for at most 10
  minutes, subject to the earlier hold expiry.
- FE-02 supplies valid published product variants, approved prices, and logical
  availability. FE-06 owns promotion and loyalty policy and state and exposes
  the approved `loyaltypromotion::facade`; FE-08 owns trusted retail facts and
  source lifecycle arbitration for preview, atomic reserve, paid or zero-total
  settlement and grant, and authoritative unpaid payment-failure or expiry
  release.
- Exact physical role-code mapping and Manager delegation rules remain an
  approved identity/authorization decision.
- Exact payment-provider event fields, provider reference uniqueness, and
  recovery behavior require a feature plan and contract review.
- Automatic GHTK fee calculation, shipment creation, webhook/status
  synchronization and Member tracking are approved V1 FE-08 scope. Until
  approved production credentials and provider access are available, feature
  development and automated tests use a mock provider contract; mock responses
  do not constitute a production carrier operation.
- The customer pays the quoted GHTK delivery fee and explicitly approved
  mandatory surcharges through the Mland checkout; optional insurance fees are
  not automatically charged. The shipment uses prepaid delivery with no COD
  collection. Provider settlement and shop-side carrier invoicing remain
  outside this feature.
- The approved GHTK fee quote is based on an authoritative pickup location,
  recipient destination, package weight, declared value, and selected service.
  FE-02 must provide authoritative package weight and any required dimensions
  for each eligible product or variant before quoting; Staff does not override
  those values silently during fulfilment.
- If the provider fee changes between quote and shipment creation, the system
  does not silently reprice the already paid order; it records an operational
  exception for Staff follow-up.
- GHTK API creation, fee calculation, status retrieval, and webhook access are
  server-to-server boundaries; their exact authentication, payload, timeout,
  retry, and status-mapping contract must be approved before implementation.
- This approved FE-08 V1 decision supersedes the current manual-only GHTK rule
  for retail orders. Report 3 and the API/background-job inventories must be
  synchronized before implementation so that no active baseline continues to
  require contradictory manual-only behavior. Until approved GHTK Partner
  access is available, the contract is exercised through a mock provider for
  development and automated testing only.
- Cart and recipient validation limits in this specification follow the current
  Report 3 rules. Retention behavior and typed error codes must remain aligned
  with the final contract before implementation.
