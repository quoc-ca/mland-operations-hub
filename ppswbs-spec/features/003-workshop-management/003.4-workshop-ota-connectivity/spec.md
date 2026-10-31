# Feature Specification: Workshop OTA Connectivity

**Date created**: 2026-10-24
**Date updated**: 2026-11-01
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature Branch**: `003.4-workshop-ota-connectivity`

**Status**: Draft — not implemented; provider onboarding and commercial gates apply

**Input**: Migrate approved OTA decisions from the retiring shared research
document. Supporting evidence is retained locally in [research.md](research.md).

## Scope

This feature connects approved online travel agencies (OTAs) to Mland workshop
availability, booking, tickets, cancellation/refund evidence, and financial
reconciliation. Mland is the sole authority for sellable sessions, capacity,
channel bookings, and Mland-scannable tickets. Each OTA has a separate adapter;
an OTA cannot overwrite Mland capacity or impose its contract on the workshop
core or another OTA.

Klook and GetYourGuide are integration candidates. Neither is enabled or
commercially approved until its own ticket-delivery and provider go-live gates
are evidenced.

### In Scope

- Publishing Mland-owned availability and enabled booking updates to approved OTAs.
- Atomic channel booking, ticket-per-participant issuance, callback handling,
  cancellation/refund consequences, and reconciliation.
- Channel eligibility, pricing/policy snapshots, customer-payment/payout evidence,
  and audited direct Mland add-ons.

### Out of Scope

- Direct booking, deposit, final payment, and direct refund owned by 003.1/003.3.
- Initial OTA sale of `BASE_PLUS_EXTRA`, workshop-item/co-maker pricing, or
  group-size options.
- Participant profiles, Member creation, magic links, marketing use of OTA
  contacts, participant transfer, or direct participant ticket delivery.
- Publication before commercial, credential, callback, quota, and ticket gates pass.

## Clarifications

### Session 2026-10-27

- Q: Is the Branch carried by OTA synchronization a distinct identity from the
  workshop location used by Operations and Account Management? → A: No. It is
  the same canonical physical resource, `workshop_location.id`. OTA receives
  and authorizes using that location identity directly; public terminology is
  `location`/`locationId`, and there is no branch ID, mapping, or local copy.

### Session 2026-10-24

- Q: Những thay đổi lịch nào từ Mland phải tự động đồng bộ sang từng OTA candidate? → A: Tự động đồng bộ toàn bộ thay đổi ảnh hưởng lịch/availability; có retry, reconciliation và force re-sync.
- Q: Ai được phép force re-sync lịch/availability và xử lý reconciliation của một OTA? → A: Active Manager thực hiện các thao tác nghiệp vụ OTA trên mọi location; Admin quản lý connection/credentials và có quyền kỹ thuật/toàn cục đã được giao.
- Q: Manager OTA có bị giới hạn theo location được gán không? → A: Không. Mọi Manager active có cùng quyền nghiệp vụ OTA trên mọi location; không cần kiểm tra `activeLocationIds` để authorize Manager. Quyết định này không tự mở rộng quyền của Manager vào thao tác được dành riêng cho Admin hoặc Staff.
- Q: Sau khi retry đồng bộ lịch/availability thất bại và chưa reconcile được, Mland phải xử lý khả năng bán của OTA đó thế nào? → A: Đóng bán mapping bị lỗi sau retry; chỉ mở lại sau resync xác nhận.
- Q: Nếu một OTA gửi booking sau khi Mland đã đóng hoặc suspend mapping do lỗi sync, Mland phải xử lý booking đó thế nào? → A: Mland từ chối/không xác nhận booking đến muộn, không giữ capacity, và tạo reconciliation case có audit. Mapping chỉ được mở lại sau authoritative resync và xác nhận reconciliation; không dùng overbooking hoặc tự chuyển khách sang session khác.

### Session 2026-10-25

- Q: Khi 003.4 phát hiện thiếu source version hoặc không xác định được thứ tự update từ 003.2 cho một mapping đang mở bán, mapping OTA đó phải xử lý thế nào? → A: Suspend riêng mapping OTA ngay và yêu cầu authoritative resync từ 003.2; chỉ mở lại sau resync thành công. Direct-Mland booking không bị ảnh hưởng.
- Q: Khi thời gian của một Basic Ring session thay đổi, 003.4 phải nhận lịch mới từ 003.2 bằng cách nào? → A: 003.2 cung cấp schedule snapshot nội bộ, authoritative và versioned, gồm opaque session identity, canonical location, scheduled start/end, source version/change identity và effective time. 003.4 không đọc entity hoặc repository của 003.2.
- Q: Khi 003.2 chuyển một Basic Ring session sang `SALES_PAUSED`, 003.4 phải phản ánh lên OTA thế nào? → A: Publish trạng thái closed/zero availability theo sale state session nhưng giữ mapping OTA hợp lệ; khi 003.2 resume sales, publish mở lại theo snapshot mới.
- Q: Nếu 003.4 phát hiện thiếu version hoặc không xác định thứ tự của schedule snapshot từ 003.2, có phải suspend mapping OTA giống availability gap không? → A: Có. Suspend riêng mapping OTA, authoritative resync schedule từ 003.2, chỉ mở lại sau khi xác nhận thành công; direct-Mland không đổi.
- Q: 003.2 nên cấp cho 003.4 một source version tăng dần chung cho mọi thay đổi OTA-observable của cùng session, hay version riêng cho schedule và availability? → A: Một source version tăng dần chung theo từng session cho mọi thay đổi schedule, availability và sale state gửi sang 003.4.

## Confirmed Decisions and Boundaries

- The initial OTA rollout publishes only `PER_PERSON` packages. A later
  fixed-price group-size option is a separate channel-by-channel decision.
- Mland may synchronize an eligible package's schedule, availability, and
  sales-closed status to a candidate OTA before its ticket gate passes, so the
  channel catalogue can be prepared and checked. This synchronization MUST NOT
  enable an OTA booking action or create a capacity commitment.
- Mland automatically sends each customer- or availability-affecting session
  change—creation, date/time, capacity, sales-open/closed state, and
  cancellation—to its mapped OTA candidates. Delivery failure uses retry and
  reconciliation; an authorized actor may request an audited force re-sync.
- If retry is exhausted before the update is reconciled, Mland closes sales for
  the affected OTA mapping. It continues to record inbound provider events for
  reconciliation and reopens sales only after a successful confirmed re-sync.
- A booking for N participants creates exactly N active Mland tickets. The OTA
  must deliver each ticket/QR individually before the option can be sold.
- A shared voucher/order code is only an authorized, audited post-go-live
  incident fallback; it cannot check in an entire booking automatically.
- OTA customers pay and are refunded under the policy shown by the OTA. Mland
  never uses its direct-payment gateway to refund OTA-collected money.
- Customer channel payment, OTA payout owed to Mland, actual payout, and a
  direct Mland add-on bill are separate financial facts. Corrections append
  evidence rather than editing history.
- For an OTA-origin workshop package, the customer pays the full amount due
  through the OTA checkout. Mland does not collect a deposit or final payment
  for that base package. The OTA's later supplier payout to Mland is a separate
  receivable and reconciliation flow; a payout is not customer-payment proof.
- Mland may confirm an OTA booking and issue its tickets only after an
  authenticated provider outcome establishes that the full accepted customer
  amount and currency were paid on the channel. If the provider's callback or
  reconciliation contract cannot establish that fact, the mapping stays
  sales-closed until the provider evidence is approved.
- Laser engraving is included free. Any other paid option needs a prior quote
  and customer acceptance, then a direct Mland bill with no OTA price, payout,
  commission, or refund effect.

## User Scenarios & Testing

### User Story 1 — OTA guest secures workshop places (Priority: P1)

An OTA guest needs a confirmed workshop booking that cannot oversell Mland
capacity and provides one Mland-scannable ticket per participant.

**Why this priority**: Capacity integrity and ticket issuance are the minimum
customer and shop value of a channel connection.

**Independent Test**: Submit an authenticated booking for N participants to an
enabled `PER_PERSON` session, then repeat it. One booking and N tickets exist;
the repeat returns the original result without another capacity deduction.

**Acceptance Scenarios**:

1. **Given** enough Mland capacity, **When** an approved OTA submits a valid
   booking for N participants with authenticated evidence of full payment of
   the accepted channel amount, **Then** Mland creates one channel booking,
   exactly N active tickets, and commits N places as one result. A booking
   lacking full-payment evidence is not confirmed and receives no tickets.
2. **Given** the same accepted request is delivered again, **When** Mland
   processes it, **Then** it returns the original result without duplicate
   booking, ticket, capacity, or financial effects.
3. **Given** capacity is insufficient or the mapping is disabled, **When** an
   OTA requests a booking, **Then** it receives a clear rejection and no change
   persists.
4. **Given** a channel delivers one shared voucher for N people, **When** its
   option is assessed, **Then** Mland keeps it disabled.

---

### User Story 2 — Manager publishes eligible channel inventory (Priority: P1)

A Manager needs to expose only approved Mland inventory and price terms to a
channel while Mland retains capacity authority.

**Why this priority**: Unapproved commercial terms or stale inventory produce
oversales and unreconcilable revenue.

**Independent Test**: Publish a valid mapping, then try to publish Klook before
commercial confirmation. Only the valid mapping is published; the gated one is
unpublished with an audit reason.

**Acceptance Scenarios**:

1. **Given** an approved mapping, **When** availability is published, **Then**
   the OTA receives Mland-owned availability without power to change capacity.
2. **Given** a candidate OTA has passed connection authorization but not its
   ticket-delivery gate, **When** Mland synchronizes schedule or availability,
   **Then** the channel may show prepared catalogue information but cannot
   accept an OTA booking or consume Mland capacity.
3. **Given** Klook price semantics, fees/tax, currency/FX, or payout terms are
   unconfirmed, **When** a Manager prepares price values, **Then** the mapping
   remains `PENDING_COMMERCIAL_CONFIRMATION` and cannot be published.
4. **Given** outbound availability delivery fails, **When** it is retried,
   **Then** no booking or independent capacity change is created; unresolved
   failure is visible for reconciliation.

---

### User Story 3 — OTA cancellation/refund protects inventory (Priority: P1)

Operations needs authenticated channel cancellation/refund results to change
tickets and capacity only after the OTA confirms the outcome.

**Why this priority**: Releasing inventory early risks selling committed places.

**Independent Test**: Deliver a cancellation request, duplicate request, and
confirmed refund callback. Mland records each event once and revokes tickets/
releases capacity only after confirmation.

**Acceptance Scenarios**:

1. **Given** a valid cancellation request without final outcome, **When** it is
   processed, **Then** Mland records it but retains tickets/capacity.
2. **Given** an authenticated confirmed outcome, **When** it is processed,
   **Then** unused tickets are revoked and eligible future capacity is released
   exactly once.
3. **Given** an invalid, duplicate, late, or out-of-order callback, **When** it
   arrives, **Then** it cannot make a second financial, ticket, or capacity
   effect and is safely audited or queued for reconciliation.

---

### User Story 4 — Finance reconciles channel revenue (Priority: P2)

Finance needs to distinguish customer money paid to an OTA, what the OTA owes
Mland, and what Mland received.

**Why this priority**: A generic “paid” state conceals refunds, supplier
receivables, and direct counter sales.

**Independent Test**: Reconcile an expected net amount, statement, payout, and
adjustment. Each remains separate and the position becomes `PAID_OUT` only when
a payout reference is matched.

**Acceptance Scenarios**:

1. **Given** a channel booking, **When** its payable conditions are met, **Then**
   the receivable moves through `NOT_ELIGIBLE`, `ACCRUED`, `INVOICED`, and
   `PAYOUT_PENDING` only with corresponding evidence.
2. **Given** a matched payout, **When** it is reconciled, **Then** it becomes
   `PAID_OUT` without rewriting gross, fee, refund, or expected-net evidence.
3. **Given** a variance/dispute, **When** it is recorded, **Then** it creates a
   linked adjustment/dispute and does not imply an OTA payout.

Manager-led reconciliation records separate eligibility, invoice, remittance,
and bank-receipt evidence for each booking. `PAID_OUT` requires booking-specific
allocation evidence whose received total and currency match the expected net
plus signed, evidenced adjustments and reversals. A customer refund or fee does
not silently change the supplier receivable: Manager records its contractual
impact as a linked adjustment. Missing or mismatched evidence opens a dispute
and does not advance the payout state. Grouped OTA payouts require a distinct
allocation reference for each booking; the system does not guess the split.
Replay of the same evidence returns the original fact, while a conflicting
replay is rejected.

---

### User Story 5 — Staff sells an OTA add-on transparently (Priority: P2)

Staff needs to provide an extra service without silently changing the OTA sale.

**Why this priority**: It protects customer consent and reconciliation.

**Independent Test**: Quote a paid option, record acceptance, and take direct
Mland payment. The counter bill remains separate from OTA financial facts.

**Acceptance Scenarios**:

1. **Given** an OTA booking, **When** laser engraving is recorded, **Then** it
   is included at zero charge.
2. **Given** a paid option lacks quote acceptance, **When** Staff attempts to
   add it, **Then** the option cannot be charged.
3. **Given** a guest accepts a quote, **When** Staff bills it, **Then** the bill
   snapshots item, price, currency, consent, actor, and payment reference and
   does not alter OTA financial facts.

### Edge Cases

- OTA requests compete for final session capacity.
- A provider replays a booking/cancellation/refund event after timeout.
- Availability delivery is late, fails, or has incompatible mapping/state.
- A partial customer refund and later statement/payout have different outcomes.
- Channel price changes after booking acceptance.
- An OTA supplies contact data that is not permitted or operationally necessary.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST retain Mland as the sole authority for sellable
  availability, capacity, channel bookings, and Mland tickets.
- **FR-002**: The system MUST isolate each OTA behind its own adapter boundary;
  one OTA's authentication, schema, voucher/ticket reference, or callback rule
  MUST NOT change workshop core behaviour or another OTA contract.
- **FR-003**: The system MUST publish only approved mappings and only
  `PER_PERSON` packages during the initial OTA release.
- **FR-004**: The system MUST require commercial, credential, callback, quota,
  and ticket-delivery gates before an OTA package/session can accept an OTA
  booking. It MAY synchronize schedule/availability for an authorized candidate
  OTA before that point, but MUST mark it sales-closed and create no capacity
  commitment.
- **FR-004a**: The system MUST automatically synchronize creation, date/time,
  capacity, sales-open/closed, and cancellation changes for each mapped session
  to the applicable OTA candidates. It MUST retry failed delivery, queue an
  unresolved difference for reconciliation, and permit an authorized, audited
  force re-sync without creating a booking or capacity commitment.
- **FR-004a1**: For a mapped Basic Ring session's creation or schedule change,
  003.4 MUST consume an authoritative, versioned internal 003.2 schedule
  snapshot containing only opaque session identity, canonical location, scheduled start/end,
  source version/change identity, and effective time. It MUST NOT read 003.2
  entities or repositories or infer schedule values from availability data.
- **FR-004a2**: When 003.2 reports `SALES_PAUSED` for a mapped Basic Ring
  session, 003.4 MUST publish that session as closed or zero-availability while
  retaining the OTA mapping as valid. When 003.2 resumes sales, 003.4 MUST
  publish the reopened state from the new authoritative snapshot. Neither normal
  transition may be treated as mapping sync failure or require re-onboarding.
- **FR-004b**: An active Manager MAY perform authorized OTA business sync and
  reconciliation actions across all locations; Manager authorization does not
  depend on `activeLocationIds`. Admin owns OTA credentials and connection
  configuration and MAY perform its authorized technical/recovery actions
  across locations. Every action
  MUST record actor, location, channel, scope, reason, and outcome; a display
  role name alone MUST NOT grant either authority.
- **FR-004b1**: An active Manager MAY read separated OTA financial evidence and
  perform payout reconciliation for any canonical location. These actions are
  Manager-authorized globally and MUST append immutable, auditable evidence;
  location assignment does not restrict access. Provider connection/credential
  changes and production booking enablement remain Admin-only.
- **FR-004c**: The system MUST close sales for an OTA mapping whose automatic
  synchronization retries are exhausted. It MUST keep inbound provider events
  available for reconciliation and MUST reopen sales only after a successful
  confirmed re-sync, with audit of the close and reopen outcomes.
- **FR-004d**: An inbound OTA booking received while its mapping is
  sales-closed or suspended MUST be rejected/not confirmed, MUST NOT reserve
  capacity or issue tickets, and MUST create an audited reconciliation case.
  Mland MUST NOT overbook or automatically move the customer to another
  session. The mapping reopens only after authoritative resync and confirmed
  reconciliation. Before an OTA may enable bookings, its documented callback
  acknowledgement, timeout/retry/idempotency, and capacity-responsibility
  behaviour must support this fixed Mland rule; until then, the mapping remains
  sales-closed preparation mode.
- **FR-004e**: When the OTA consumer detects a missing 003.2 source version or
  cannot establish the order of availability/sale-state updates for an
  open-for-sale mapping, it MUST suspend only that OTA mapping and request an
  authoritative 003.2 resync. It MUST reopen the mapping only after successful
  resync confirmation. This condition MUST NOT change 003.2 capacity or sale
  state, or direct-Mland booking behaviour.
- **FR-004f**: When the OTA consumer detects a missing 003.2 source version or
  cannot establish the order of schedule snapshots for an open-for-sale mapping,
  it MUST suspend only that OTA mapping and request an authoritative 003.2
  schedule resync. It MUST reopen the mapping only after successful resync
  confirmation and MUST NOT change 003.2 or direct-Mland booking behaviour.
- **FR-004g**: For each session, 003.2 MUST supply one monotonically increasing
  source version for every OTA-observable schedule, availability, or sale-state
  change. 003.4 MUST use that one per-session sequence for duplicate/older
  update rejection, gap detection, and authoritative resync; it MUST NOT merge
  independent schedule and availability version streams.
- **FR-005**: Klook configuration MUST remain
  `PENDING_COMMERCIAL_CONFIRMATION` until price semantics, commission,
  taxes/fees, currency/FX, and payout terms are confirmed.
- **FR-006**: The system MUST atomically validate capacity and create exactly
  one channel booking and one active Mland ticket per participant.
- **FR-007**: The system MUST use external booking reference and idempotency key
  to return the original result for repeats and prevent duplicate bookings,
  tickets, capacity deductions, and financial effects.
- **FR-008**: The system MUST not enable an option until an end-to-end test proves
  booking N participants delivers N individual Mland ticket/QR values that
  Staff can scan.
- **FR-009**: A shared OTA voucher MUST be an authorization-protected,
  audited incident fallback and MUST NOT automatically check in all participants.
- **FR-010**: The system MUST authenticate callbacks, deduplicate events, retain
  safe correlation/reference data, and never log credentials, raw tokens,
  signing secrets, or avoidable PII.
- **FR-011**: The system MUST distinguish a channel cancellation request from a
  confirmed cancellation/refund outcome and release tickets/capacity only after
  confirmation.
- **FR-012**: The system MUST record customer channel-payment states as
  `PAID_ON_CHANNEL`, `REFUND_REQUESTED`, `PARTIALLY_REFUNDED`, or `REFUNDED`.
  `PAID_ON_CHANNEL` MUST represent authenticated evidence that the full
  accepted customer amount and currency were collected by the OTA for the
  booking. An incomplete, mismatched, or absent payment outcome MUST NOT
  confirm the channel booking or issue tickets. These states MUST NOT be read
  as a direct-Mland payment, booking, or supplier-payout state. Mland MUST NOT
  create a `DEPOSIT` or `FINAL_PAYMENT` attempt for the OTA-collected base
  package.
- **FR-013**: The system MUST snapshot sales channel, displayed policy/version,
  cutoff/timezone, price terms, commission/fee facts, and accepted currency
  amounts for each OTA booking; later configuration cannot rewrite the snapshot.
- **FR-014**: The system MUST keep supplier payout state separate as
  `NOT_ELIGIBLE` → `ACCRUED` → `INVOICED` → `PAYOUT_PENDING` → `PAID_OUT`.
- **FR-015**: The system MUST retain an append-only reconciliation ledger for
  gross sale, commission/fee, customer refund, expected/received payout, FX
  variance, compensation, and adjustment, with amount, currency, time,
  reference, reason, and related records.
- **FR-016**: The system MUST correct channel financial evidence through linked
  adjustment/reversal records and MUST NOT overwrite historical facts.
- **FR-017**: The system MUST retry failed outbound notification without a
  second booking/capacity effect; exhausted retry, mapping failure, or state
  difference MUST create an auditable reconciliation work item.
- **FR-018**: The system MUST retain only contractually permitted, operationally
  necessary OTA customer data and MUST NOT create a Member, participant profile,
  magic link, or marketing audience from it.
- **FR-019**: Laser engraving MUST be included free. Other paid options MUST
  receive a prior quote and customer acceptance.
- **FR-020**: The system MUST record an accepted paid OTA add-on as a separate
  Mland counter bill with immutable item/price/currency/consent/actor/payment
  evidence, without changing OTA price, channel payment, commission, payout,
  settlement, or channel refund.
- **FR-021**: The system MUST authorize configuration, publication,
  reconciliation resolution, and fallback check-in on the server using active
  Mland authority and resource scope; OTA claims/UI visibility grant no power.
- **FR-022**: The system MUST return safe typed, correlation-linked failures
  without exposing other booking data or sensitive integration information.

### Key Entities

- **Channel Mapping**: Approved OTA-to-Mland package/session/option mapping
  with eligibility, publish state, availability state, and go-live evidence.
- **Channel Booking**: One OTA booking with safe external reference, participant
  count, ticket mapping, policy/price snapshot, and event correlation.
- **Channel Event**: Authenticated external booking/cancellation/refund/state
  event retained for idempotency, audit, and reconciliation.
- **Channel Price Snapshot**: Accepted retail/target-net/currency/fee terms and
  subsequent actual financial evidence.
- **Channel Payout Position**: Supplier receivable lifecycle and immutable ledger.
- **Direct OTA Add-on Bill**: Separate Mland charge excluded from OTA calculations.
- **Reconciliation Work Item**: Unresolved delivery, mapping, state, or financial
  difference with safe correlation and authorized resolution.

## Success Criteria

- **SC-001**: 100% of duplicate-booking/callback acceptance tests create no
  duplicate booking, ticket, capacity, or financial effect.
- **SC-002**: 100% of enabled channel options in go-live verification create and
  deliver exactly one Mland-scannable ticket per participant.
- **SC-003**: 100% of confirmed cancellation/refund tests revoke only applicable
  unused tickets and release capacity once; unconfirmed requests do neither.
- **SC-004**: 100% of reconciliation tests keep channel customer payment,
  expected payout, received payout, and direct Mland add-on totals distinct.
- **SC-005**: 100% of publication attempts lacking commercial approval, supported
  pricing, or ticket evidence are rejected.
- **SC-006**: 100% of OTA failure log/audit test views redact credentials, raw
  tokens, secrets, and unnecessary customer data.

## Assumptions and Dependencies

- 003.1 supplies booking/ticket outcomes; 003.2 owns session capacity, check-in,
  and attendance. This feature consumes rather than duplicates them.
- 003.3 owns direct-Mland final payment/refund. OTA money never uses its direct
  deposit/refund gateway flow.
- Provider contracts, credentials, callback verification, quotas, commercial
  terms, and production onboarding remain per-OTA go-live gates. The fixed
  late-booking/capacity rule is rejection without capacity commitment; each
  provider must evidence that its callback acknowledgement/retry behaviour can
  honor it before booking enablement.
- Payment/audit retention follows the approved baseline. The feature stores no
  card data, secret, raw token, or unapproved participant PII.
- Channel availability/event processing needs durable business/audit state;
  public browsing is stateless and no global session policy is implied.

## Review Findings and Handoff Limits

**Design critique**: Treating an OTA as just another checkout would allow stale
capacity, shared-voucher check-in, and one ambiguous “paid” value to conceal
customer refunds, supplier receivables, and direct counter sales. Separate
adapters, ticket gates, immutable financial facts, idempotency, and reconciliation
are required boundaries.

**Provider release gates remain**: Provider contracts, commercial terms,
credential/callback configuration, quotas, late-booking behavior, and deployment
ownership need approved follow-up evidence. Finance authorization is resolved:
any active Manager may read financial evidence and perform payout reconciliation
globally. This specification grants neither provider publication nor production
booking enablement authority.
