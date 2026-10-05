# Unresolved Conflicts and Open Decisions

This file records conflicts, missing details, and weak points identified while reviewing `06-iv-proposed-solution/00-overview.md` against the current project context. These items are not resolved decisions. They should be confirmed by the project before the proposed solution is treated as a final requirement.

## 1. Customer access and customization journey

### 1.1 Guest permission for image-based customization

**Conflict:** `00-overview.md:3` states that both Guests and Members may submit a reference image or choose a custom-design path. Report 3 Actors states that a Guest cannot submit an image-based custom design request, while a Member can.

**Suggested resolution:** Define the access boundary explicitly: Guests may browse, choose a supported design/configuration path, and book a workshop; only Members may submit an image-based custom design request or complete retail checkout. If Guest image submission is intended, update the authentication and entitlement rules consistently.

### 1.2 Order of slot selection and design selection

**Unclear point:** The overview says that the customer selects a location and session and then chooses a design path, but it does not define whether this order applies to every journey or only workshop booking.

**Suggested resolution:** State separate flows for workshop booking, Member retail customization, and in-person consultation. For workshop booking, confirm whether slot selection must always happen before design selection and whether the selected slot constrains the available design components.

### 1.3 In-person consultation boundary

**Unclear point:** The overview allows the customer to wait to consult Staff in person, but it does not say whether this consultation requires an existing booking, creates a design request, or can be used by a Guest or only a Member.

**Suggested resolution:** Define the entry condition, actor permission, information captured, and next state after an in-person consultation.

## 2. AI, feasibility, and customization decisions

### 2.1 AI capability boundary

**Conflict:** `00-overview.md:5` describes the AI Vision API as extracting candidate ring features only. Report 3 also describes the AI API as supporting basic text chat, component classification from a Member image, and package-price suggestions for Managers. Other Report 1 feature descriptions mention AI valuation, recommendations, and chatbot functions.

**Suggested resolution:** Publish one capability list for the AI API. Separate image feature classification, support chat, and package-price suggestions from custom-ring feasibility and custom-design pricing. State explicitly that AI does not create the final design, determine custom-design difficulty, or approve a custom request.

### 2.2 Feasibility states and routing

**Conflict:** `00-overview.md:5` proposes `simple/auto-accept`, `medium/Staff review`, `advanced/Owner review`, and `impossible/auto-reject`. Report 3 says every custom request enters `need_review`, then ends as `accepted` or `rejected`; Staff reviews requests up to 3,000,000 VND and Manager reviews requests above that amount.

**Suggested resolution:** Choose one state model and one routing rule. At minimum, define request states, who may change each state, whether auto-accept/auto-reject is allowed, the meaning of the price threshold, and the required rejection reason.

### 2.3 Role responsible for feasibility review

**Conflict:** The proposed solution assigns advanced review to Owner, while Report 3 assigns high-value review to Manager. The project context also uses Owner for catalogue and feasibility governance.

**Suggested resolution:** Reconcile `Owner`, `Manager`, `Staff`, `Admin`, and `Admin Technical`. Define whether Owner configures rules only, whether Manager approves high-value requests, and when Staff may accept or reject a request.

### 2.4 Source and ownership of the estimate

**Unclear point:** The overview says the estimate comes from the Staff-maintained component catalogue, while other contexts refer to structural effort scores, AI package-price suggestions, and configurable pricing parameters. It is unclear which values are authoritative for a custom-ring estimate.

**Suggested resolution:** Define the estimate formula and ownership: catalogue components, material/gemstone prices, labour or effort parameters, VAT/currency, manual adjustments, rounding, estimate validity, and the actor who approves the final estimate.

### 2.5 Custom request status before payment or order creation

**Unclear point:** The overview does not state whether a feasible design automatically becomes a booking design, a retail cart item, a custom manufacturing order, or only an estimate awaiting customer acceptance.

**Suggested resolution:** Define the transition from design request to workshop booking, retail cart, custom order, or rejection. Record whether customer acceptance is required before invoice creation and payment.

## 3. Workshop booking, payment, and billing

### 3.1 Fixed deposit versus configurable deposit

**Conflict:** `00-overview.md:7` states that every workshop booking requires a fixed 50% deposit. Report 3 also includes material-based deposit configuration, and the DB proposal marks precedence between package and material rules as unresolved.

**Suggested resolution:** Confirm whether 50% is the permanent V1 rule or only the default. If material-based configuration remains, define precedence, effective date, currency, rounding, and the value captured in the booking invoice snapshot.

### 3.2 Email confirmation before payment

**Unclear point:** The overview moves from booking directly to deposit payment. The Member Authentication context requires a Guest's contact email to be confirmed before payment/settlement and committed capacity.

**Suggested resolution:** Document the sequence and states explicitly: booking draft, email confirmation pending, confirmed email, payment pending, payment verified, booking confirmed, expired/cancelled. State whether the rule applies to Members, Guests, or both.

### 3.3 Payment verification and reconciliation

**Unclear point:** The overview says the gateway provider contract, reconciliation, retry, and duplicate-event handling are unresolved, but also says a gateway status or transaction reconciliation is required for confirmation.

**Suggested resolution:** Define the minimum server-side evidence for a verified payment and mark the remaining provider-specific details as TBD. Do not use a browser redirect as confirmation. Define idempotency and late/duplicate callback handling before implementation.

### 3.4 Invoice creation and capacity commitment

**Unclear point:** “Every workshop booking creates an invoice” does not clarify whether an unpaid invoice reserves capacity, whether capacity is held temporarily, or whether only a confirmed payment commits the participant count.

**Suggested resolution:** Link invoice states to capacity states. Specify temporary hold duration, release conditions, group participant counting, and the behaviour when email confirmation or payment expires.

### 3.5 Attendance fee and invoice adjustment

**Unclear point:** The 100,000 VND fee applies to each unbooked additional participant, but the overview does not define how Staff count participants, calculate the fee, add it to the invoice, or handle disagreement.

**Suggested resolution:** Define the check-in count, fee calculation, invoice line, customer consent, payment deadline, and the result when the customer does not accept the adjustment.

### 3.6 Adjustment authority and consent order

**Unclear point:** Staff may add an adjustment with a reason and customer consent, while Owner approval is required for a waiver or fee change. It is unclear whether consent occurs before or after approval and whether all adjustments require Owner approval.

**Suggested resolution:** Separate normal surcharge, waiver, discount, and correction flows. Define who proposes, who approves, when the customer consents, and which immutable audit records are stored.

### 3.7 Final balance calculation

**Unclear point:** The final balance is described as the unpaid 50% plus approved adjustments, but it is not clear whether this includes additional-participant fees, custom-order charges, material changes, taxes, or other approved charges.

**Suggested resolution:** Define the invoice line types and snapshot rules for package price, deposit, attendance fee, material/component charges, adjustments, VAT, and final settlement.

## 4. Custom orders, ready-ring retail, and fulfilment

### 4.1 Workshop-made ring versus shop-made custom order

**Unclear point:** The overview allows a feasible design to become either a workshop-made ring or a shop-made custom order, but it does not define separate payment, fulfilment, status, and handoff rules for the two paths.

**Suggested resolution:** Create separate lifecycle descriptions for workshop participation, shop-made custom manufacturing, and ready-ring retail. Define when each path creates an order, invoice, payment intent, and fulfilment record.

### 4.2 Shop-made custom order fulfilment method

**Conflict/ambiguity:** `00-overview.md:7` says shop-made orders use simple fulfilment statuses through pickup, while the ready-ring flow supports pickup or carrier handoff and Report 3 includes custom manufacturing order payments.

**Suggested resolution:** Confirm whether shop-made custom orders are pickup-only in V1. If carrier handoff is allowed, reuse the same delivery-contact, address, handoff-evidence, and out-of-scope tracking rules as retail orders.

### 4.3 Continuation booking and custody

**Unclear point:** The overview says Staff create a continuation booking when a later session has capacity, but does not define the relationship to the original booking, a new invoice/deposit, additional fees, customer consent, or failure when no later slot is available.

**Suggested resolution:** Define continuation states, parent/child booking linkage, capacity reservation, fee/deposit policy, customer request evidence, and the work-item state when continuation is unavailable.

### 4.4 Custody intake and release evidence

**Unclear point:** The overview requires an intake photo and release verification but does not specify the minimum custody events, permitted actors, timestamps, storage, or whether release requires both booking code and Staff verification.

**Suggested resolution:** Define append-only custody events for intake, storage/transfer, release, actor, location, time, photo/object reference, and verification result. Keep customer contact and image-retention rules as explicit TBD items where unresolved.

### 4.5 Temporary retail hold versus paid reservation

**Conflict:** `00-overview.md:13` says the system reserves one ready-ring unit only after full payment and that the reservation has no automatic expiry. Report 2 describes a 15-minute checkout hold before payment, while the DB proposal records the lifecycle conflict as unresolved.

**Suggested resolution:** Distinguish `temporary checkout hold` from `paid inventory reservation`. Define hold expiry, release, payment race conditions, and the rule that only a verified full payment creates the durable paid reservation.

### 4.6 Retail fulfilment prerequisites

**Unclear point:** The overview says the Member chooses pickup or carrier handoff but does not mention the required recipient and delivery-address data for carrier fulfilment.

**Suggested resolution:** Add the carrier branch precondition: complete delivery contact and address data before preparation/handoff. State that carrier fees, live tracking, delivery failure, returns, and refunds are outside V1.

## 5. Branch, schedule, and data rules

### 5.1 Location capability data

**Unclear point:** The overview says location product capabilities are operational data to approve but does not define whether capability is configured per branch, package, material, component, or session.

**Suggested resolution:** Define the capability model, managing role, effective dates, and validation behaviour when a selected design is unavailable at the chosen branch.

### 5.2 Schedule, capacity, and exceptions

**Unclear point:** The three daily sessions are stated as approved, but the overview does not define location timezone, holidays, temporary closure, capacity configuration, or whether an inactive location can accept bookings.

**Suggested resolution:** Reference the session, exception, timezone, and capacity rules explicitly. Keep exact capacity values and unresolved exception precedence as TBD rather than implying that the three sessions alone make a slot bookable.

### 5.3 Reference-image consent and retention

**Unclear point:** The overview requires consent for a reference image but does not define storage, access, AI transfer, retention, deletion, or the behaviour after rejection or completion.

**Suggested resolution:** Specify consent wording/version, storage owner, access boundary, AI-processing boundary, retention period, deletion trigger, and audit record. If unresolved, mark each item as a separate TBD decision.

## 6. Guest access, notifications, and review features

### 6.1 Guest booking lookup security

**Conflict/ambiguity:** The overview says Guests use a tracking code, while the authentication context requires a booking code plus matching contact data.

**Suggested resolution:** Describe Guest lookup as a two-factor business lookup using booking code and matching email/phone, with minimum-field disclosure and rate limiting if approved by the security plan.

### 6.2 Notification events and recipients

**Unclear point:** “Booking and review or order notifications” does not identify which review is meant, which events trigger messages, who receives them, or what happens after delivery failure.

**Suggested resolution:** List notification types separately: email confirmation, payment receipt, QR ticket, design review result, estimate/quote, order status, pickup readiness, and internal approval request. Define recipient, trigger, retry, and audit requirements.

### 6.3 Google review link and import

**Unclear point:** The overview combines a public Google review link/QR with an Owner-triggered Google Business Profile import, but their purposes, data direction, visibility, and governance are not clearly separated.

**Suggested resolution:** Treat customer-authored review submission and one-way review import as two separate features. Define verified access, attribution, retention, moderation/appeal, public visibility, and the exact role of Google Maps/Google Business Profile.

### 6.4 Salary and bonus restriction

**Unclear point:** The statement that review import must not calculate employee pay or bonuses is consistent with the exclusion list but appears without a surrounding business rule.

**Suggested resolution:** Move or cross-reference this as an explicit review-governance constraint so it is clear why the restriction exists and which data must not be used for compensation decisions.

## 7. Roles, authority, and system boundary

### 7.1 Role vocabulary

**Conflict:** Different contexts use `Owner`, `Manager`, `Admin`, and `Admin Technical` for overlapping responsibilities. The working agreement uses `OWNER` and `ADMIN_TECHNICAL`, while Report 3 uses `Manager` and `Admin`.

**Suggested resolution:** Ratify one role vocabulary and a permission matrix. Do not describe a role as having authority over a workflow until the role mapping is approved.

### 7.2 Admin versus Admin Technical

**Unclear point:** The overview says Admin Technical cannot mutate business records, but it does not distinguish technical configuration from operational parameters, user/role management, credentials, audit records, and integration monitoring.

**Suggested resolution:** Define which settings are business-owned, which are technical-owned, and which actions require audit. Keep technical administration separate from catalogue, pricing, feasibility, promotion, booking, and payment decisions.

### 7.3 Inventory boundary

**Conflict:** The overview excludes full inventory management, while other feature descriptions mention stock tracking. Report 2 defines only manually maintained available-to-sell quantities and explicitly excludes warehouse inventory management.

**Suggested resolution:** State that V1 supports catalogue availability and manual available-to-sell quantity maintenance only. Exclude warehouse locations, stock movements, procurement, serial/lot management, and inventory ledger functionality.

### 7.4 Audit and traceability boundary

**Unclear point:** The overview mentions recorded reasons and audit trails for several actions but does not define one consistent audit policy for design overrides, invoice adjustments, custody, payments, review imports, and role changes.

**Suggested resolution:** Identify the audit events required for each business action, including actor, timestamp, reason, before/after context, customer consent, and correlation/reference identifiers. Keep immutable historical snapshots separate from mutable catalogue data.
