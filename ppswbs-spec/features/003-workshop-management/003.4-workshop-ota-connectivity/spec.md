# Feature Specification: Workshop OTA Connectivity

**Date created**: 2026-10-24
**Date updated**: 2026-11-15
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature Branch**: `003.4-workshop-ota-connectivity`

**Status**: Implemented in part; T004, T038b, T051, T054 open (new tasks T123–T129 from Clarifications Session 2026-11-15; T132–T144 from its "Checklist follow-up", answered 2026-11-15); provider onboarding and commercial gates apply. **Approved 2026-11-15 by Hồ Hoàng Quốc (owner Feature 003)** for the whole spec content of that date including Clarifications Session 2026-11-15. *(The "Checklist follow-up" decisions were given by Hồ Hoàng Quốc in session on 2026-11-15, after that approval; they are recorded here as his decisions of that date; no reviewer approval label is changed by this edit.)*

**Input**: Migrate approved OTA decisions from the retiring shared research
document. Supporting evidence is retained locally in [research.md](research.md).

## Staff booking-assignment access (owner decision, 2026-11-06)

### Route masking synchronization (2026-11-20)

`/ota-connectivity/*` is internal. `ADMIN`, `MEMBER`, and `CARRIER` direct navigation is replaced to `/404` before any OTA page request because Admin has no FE-003.4 route. `STAFF` may open only the approved check-in/add-on browser surfaces; `MANAGER` may open channel, reconciliation, and finance surfaces. Until the route registry distinguishes those Staff and Manager pages, the feature must not treat a broad `STAFF`/`MANAGER` route wrapper as final authorization. This does not change the existing API contract: discovery lookup non-disclosure remains its approved `404`, while the specified add-on/financial command denials remain `403`.

This amendment narrows decision D18. Wherever this document lets an in-scope
Staff member look up an OTA booking at the shop, create or accept a paid-option
quote, or create its direct bill, the Staff member must also be actively
assigned to a session that the OTA booking's Mland booking
(`mlandBookingReference`) holds a valid ticket for (003.2 "Staff
session-assignment access"). An unassigned Staff member is refused (`403`); the
booking lookup answers as if the booking did not exist. A Manager remains
global.

Payment and refund of the resulting add-on bill (003.3) follow the same rule
through the Mland booking reference recorded on the bill; a bill without that
reference is Manager-only for Staff. The free laser-engraving record, OTA
checkout and channel settlement are unchanged.
*(Restated 2026-11-14, superseding "and refund" above: payment of the OTA
add-on bill follows this assignment rule. A refund of that bill is the
Manager-approved bank refund of 003.3 FR-038a; Staff may only enter the
payer's account at the counter (003.3 T104). There is no Staff-approved or
gateway refund of the add-on bill.)*

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
- Q: Ai được phép force re-sync lịch/availability và xử lý reconciliation của một OTA? → A: Active Manager thực hiện các thao tác nghiệp vụ OTA trên mọi location; Admin quản lý connection/credentials và có quyền kỹ thuật/toàn cục đã được giao. *(Superseded 2026-11-15, Hồ Hoàng Quốc, checklist CHK026: Admin không còn quyền nào trong 003.4; mọi hành động nghiệp vụ OTA, kể cả booking-enable, thuộc Manager; connection/credential là cấu hình môi trường, không có route REST. Xem "Checklist follow-up" ở Session 2026-11-15.)*
- Q: Manager OTA có bị giới hạn theo location được gán không? → A: Không. Mọi Manager active có cùng quyền nghiệp vụ OTA trên mọi location; không cần kiểm tra `activeLocationIds` để authorize Manager. Quyết định này không tự mở rộng quyền của Manager vào thao tác được dành riêng cho Admin hoặc Staff.
- Q: Sau khi retry đồng bộ lịch/availability thất bại và chưa reconcile được, Mland phải xử lý khả năng bán của OTA đó thế nào? → A: Đóng bán mapping bị lỗi sau retry; chỉ mở lại sau resync xác nhận.
- Q: Nếu một OTA gửi booking sau khi Mland đã đóng hoặc suspend mapping do lỗi sync, Mland phải xử lý booking đó thế nào? → A: Mland từ chối/không xác nhận booking đến muộn, không giữ capacity, và tạo reconciliation case có audit. Mapping chỉ được mở lại sau authoritative resync và xác nhận reconciliation; không dùng overbooking hoặc tự chuyển khách sang session khác.

### Session 2026-10-25

- Q: Khi 003.4 phát hiện thiếu source version hoặc không xác định được thứ tự update từ 003.2 cho một mapping đang mở bán, mapping OTA đó phải xử lý thế nào? → A: Suspend riêng mapping OTA ngay và yêu cầu authoritative resync từ 003.2; chỉ mở lại sau resync thành công. Direct-Mland booking không bị ảnh hưởng.
- Q: Khi thời gian của một Basic Ring session thay đổi, 003.4 phải nhận lịch mới từ 003.2 bằng cách nào? → A: 003.2 cung cấp schedule snapshot nội bộ, authoritative và versioned, gồm opaque session identity, canonical location, scheduled start/end, source version/change identity và effective time. 003.4 không đọc entity hoặc repository của 003.2.
- Q: Khi 003.2 chuyển một Basic Ring session sang `SALES_PAUSED`, 003.4 phải phản ánh lên OTA thế nào? → A: Publish trạng thái closed/zero availability theo sale state session nhưng giữ mapping OTA hợp lệ; khi 003.2 resume sales, publish mở lại theo snapshot mới.
- Q: Nếu 003.4 phát hiện thiếu version hoặc không xác định thứ tự của schedule snapshot từ 003.2, có phải suspend mapping OTA giống availability gap không? → A: Có. Suspend riêng mapping OTA, authoritative resync schedule từ 003.2, chỉ mở lại sau khi xác nhận thành công; direct-Mland không đổi.
- Q: 003.2 nên cấp cho 003.4 một source version tăng dần chung cho mọi thay đổi OTA-observable của cùng session, hay version riêng cho schedule và availability? → A: Một source version tăng dần chung theo từng session cho mọi thay đổi schedule, availability và sale state gửi sang 003.4.

### Session 2026-11-15

- Q: How is a booking's expected net payout computed from the price terms'
  `targetNetAmount`? → A: `targetNetAmount` is the net price **per person**
  (V1 sells only `PER_PERSON` packages). The booking's expected net is
  `targetNetAmount × participant count N`, in the price terms' currency,
  snapshotted when the channel booking is confirmed (FR-013) and used by the
  payout position (FR-014, T110). *(Closes analyze finding F03.)*
- Q: When the OTA collects the customer's money in a foreign currency, which
  currency is the reference for reconciling the payout? → A: The currency of the
  expected net / the payout actually received (the price terms' currency). The
  customer-paid amount and currency stay as evidence only (T113) and are never
  used to match a payout. ~~Any difference caused by conversion is recorded as an
  evidenced FX-variance adjustment (FR-015), not as a mismatch of the payout.~~
  *(Closes analyze finding F04. Superseded 2026-11-15, Hồ Hoàng Quốc, checklist
  CHK003/CHK004: the price terms and the `BANK_RECEIPT` are both in VND, the
  currency Mland is credited in; V1 has no FX variance. Every difference between
  the expected net and the money received is a payout dispute, and a Manager
  closes it with an evidenced `ADJUSTMENT` carrying a reason. See "Checklist
  follow-up" below.)*
- Q: How are a partial refund and a partial cancellation from the OTA handled?
  → A: Refund size and cancellation policy are the OTA's own (owner statement,
  2026-11-15). Mland records the amounts the OTA reports (FR-012) and derives
  nothing from them; the only thing that releases tickets and capacity is the OTA
  confirming a cancellation (FR-011). The OTA's booking page sells only a
  participant **quantity** (1 ticket = 1 person, no named person per ticket;
  Klook listing observed by the owner 2026-11-15, max 4 per booking), and every
  ticket of an OTA booking is interchangeable (same session, same Package).
  *(Decided 2026-11-15 after first choosing "whole-booking only", which is
  superseded: U1 is replaced as follows.)* When the OTA **confirms** a
  cancellation of k participants and k is less than the booking's active
  tickets, Mland revokes k tickets by a fixed rule — tickets not yet checked in
  first, then the highest ticket sequence first — and releases k seats; if the
  payload names the ticket or voucher, that ticket is revoked instead of the rule
  choosing. ~~If k is at least the active tickets, the whole booking is
  cancelled.~~ *(Superseded 2026-11-15 by the "Checklist follow-up" below: if k
  equals the active tickets the whole booking is cancelled; if k is greater than
  the active tickets, or than the not-yet-checked-in tickets, nothing is revoked
  and an `INBOUND_EVENT` work item opens; a checked-in ticket is never revoked;
  k is always the cancellation count of that one event.)*
  A request (not yet confirmed) revokes nothing. A partial revocation is fully
  automatic and is recorded in the audit trail naming the revoked tickets
  (visible to a Manager on the booking detail); it opens **no** work item. A work
  item is opened only when automation cannot decide (k exceeds the active
  tickets or the not-yet-checked-in tickets, unknown booking, unmapped event). No ticket is ever revoked from an
  unconfirmed or unauthenticated event. At the door, a guest holding a revoked QR while the booking still has an
  unused valid ticket is handled by Staff through the booking-code lookup
  (FR-003b of 003.2) and an audited check-in of a remaining ticket.
  `PARTIALLY_REFUNDED` stays a payment state recorded only if the OTA reports it
  (T109). Klook's actual support for quantity cancellation is verified in
  T004/T038b from real payloads.
- Q: Does Mland notify the OTA customer when an OTA booking is cancelled? → A:
  No. Mland sends no email or SMS to an OTA customer; the OTA informs its
  customer under its own policy. The outbound notification of FR-017 is only the
  synchronization of state and availability to the OTA; Mland's record of a
  cancellation is the audit trail.
- Q: How does 003.4 revoke OTA tickets when 003.2 FR-036 refuses
  `cancelTicket` for OTA-origin tickets? → A: 003.2 adds a separate facade
  operation for OTA-confirmed cancellation of named tickets of an OTA booking,
  callable only by 003.4. 003.2 FR-036 still refuses the general operations
  (`cancelTicket`, `replaceTicketSessions`, `reissueTicketQr`) for every other
  caller. The new operation releases each ticket's seats and revokes its
  entitlements and QR without rewriting attendance history, idempotently per
  OTA event. 003.4 chooses the tickets (fixed rule or payload) and 003.2 revokes
  exactly those; ticket ownership stays in 003.2.
- Q: May the FR-013 booking snapshot have empty fields when the OTA does not
  send them? → A: The required snapshot terms (sales channel, price terms
  version, `targetNetAmount`, commission, currency, policy/cutoff) come from the
  active price terms entered in Mland and are always complete; a booking with no
  valid active price terms is rejected. Values the OTA reports (customer-paid
  amount and currency, fees) are evidence fields and may be null. Later
  configuration never rewrites the snapshot.
- Q: What are the outbound retry count and spacing for availability/state
  delivery to an OTA? → A: ~~3 attempts spaced 30 seconds, 2 minutes and 10
  minutes (back-off, under 15 minutes in total).~~ *(Clarified 2026-11-15,
  checklist CHK013: one initial send plus 3 retries, each retry waiting 30
  seconds, 2 minutes and 10 minutes after the previous failure, so 4 sends in
  all; the delays sum to 12.5 minutes and, with the up-to-30-second poll after
  each failure, the last send is at most about 14.5 minutes after the first,
  under 15 minutes.)* When the attempts are
  exhausted, only that mapping closes sales and a work item opens (FR-017). The
  values are configuration defaults, adjustable when a provider's contract
  requires different ones; they are not provider commitments. *(Closes the
  "3 retries / 30 s" question; plan.md Performance Goals "no approved numeric
  retry target" is superseded by this default.)*
- Q: Does the work item category `SHARED_VOUCHER_INCIDENT` stay? → A: No. The
  shared-voucher fallback no longer exists (FR-009, T117), so the category is
  removed from the contract enum. If existing rows carry it, the value stays
  readable, marked deprecated, and is never created again; a task checks the
  data first (owner decision 2026-11-15: the value stays in the enum as
  deprecated; with no rows using it, it is removed in a later cleanup).
- Q: Is the V1 OTA offer "Basic Ring" or Signature Workshop — Brass? → A: The
  single V1 OTA offer is the cheapest Package, **Signature Workshop — Brass**
  (750,000 VND direct; the OTA lists its own higher price, e.g. 1,600,000 VND per
  person on Klook as observed by the owner 2026-11-15). "Basic Ring" is the
  legacy name of the same offer: wherever this feature says "Basic Ring" it means
  the catalogue Package Signature Workshop — Brass. The mapping is still
  configured by a Manager, never hard-coded.

#### Checklist follow-up (payment-error-access.md), answered 2026-11-15

*(Final decisions of Hồ Hoàng Quốc, owner Feature 003 (owner decision Hồ Hoàng
Quốc, 2026-11-15), on checklist items CHK002, CHK003, CHK004, CHK007, CHK009,
CHK010, CHK013, CHK018, CHK024 and CHK026, plus documentation sync with no new
decision for CHK020, CHK027, CHK028, CHK032, CHK036, CHK037, CHK038 and CHK039.
Rule text is in the FRs and SC-003, SC-007–SC-009 named below; code and test
work is tasks T132–T144 (and the amended T123–T125, T127). Where this section differs from Session 2026-11-15
above, this section governs and the older wording is struck or marked
superseded in place.)*

- Q (CHK009): May a partial confirmed cancellation revoke a ticket that is
  already checked in? → A: No, never. Only not-yet-checked-in tickets can be
  revoked; "highest ticket sequence first" is only the rule that chooses among
  them (or the payload names the ticket). If k is greater than the number of
  not-yet-checked-in tickets, no ticket is revoked at all (all or none, no
  partial apply) and an `INBOUND_EVENT` work item opens for a Manager. Attendance
  history is never changed. *(The owner notes that Klook and GetYourGuide require
  cancellation before the session starts, so a cancellation of a checked-in
  ticket should be rare or abnormal; this is the owner's statement, not yet
  checked against the real provider policy, and the cancellation deadline can
  differ per OTA and package.)*
- Q (CHK010): What happens at the k boundaries? → A: The boundaries do not
  overlap. k **equal to** the booking's active (still valid) tickets cancels the
  whole booking. k **greater than** the active tickets revokes nothing and opens
  an `INBOUND_EVENT` work item (OTA and Mland disagree, which automation must
  not guess). k below the active tickets is the partial case above.
- Q (k): Is k the cancellation count of this event or a running total? → A: k is
  always the number cancelled by that one event. Each provider adapter converts
  the provider's data (a cumulative total or a remaining count) into that number,
  based on the real payloads (T004/T038b). If the payload does not carry enough
  to convert, the event opens an `INBOUND_EVENT` work item and revokes nothing.
- Q (CHK002): Does the expected net change when k of N participants cancel? →
  A: No. The expected net stays `targetNetAmount × N` as snapshotted (FR-013,
  FR-016 forbids rewriting history). The Manager records the contractual effect
  as an evidenced `ADJUSTMENT` (for example a minus of the cancelled
  participants' net, or a smaller amount if the OTA still pays part). If the
  Manager does nothing and the OTA pays a different amount, the payout dispute
  opens by itself (FR-014). The same rule applies to a whole-booking
  cancellation. Mland never posts that adjustment by itself.
- Q (CHK007): Does a refund event release tickets, and how do the refund states
  move? → A: Only a confirmed cancellation event releases tickets (FR-011). The
  OTA events `REFUNDED` and `PARTIALLY_REFUNDED` only record money evidence
  (FR-012); an OTA that sends only `REFUNDED` with no cancellation event opens an
  `INBOUND_EVENT` work item and releases nothing. One booking has 1 to N tickets
  (one ticket = one person; Klook allows at most 4 per booking), so a refund
  label never means "the whole booking". The transition table and the one-way
  order are in FR-011 and FR-012. *(A refund event is the OTA telling Mland it
  refunded the customer, information only; it is not the payout, which is the
  OTA transferring money to Mland per settlement period.)*
- Q (CHK003): What if the payout actually received is in a different currency
  than the price terms? → A: The price terms are entered in VND, the currency in
  which Mland's bank is credited, and a `BANK_RECEIPT` is always the amount the
  bank credited in VND. The owner states that Klook collects from the customer in
  any currency and pays Mland in VND the agreed amount per settlement period
  (weekly, fortnightly or monthly by contract); this still has to be verified
  against the real Klook contract. A foreign-currency document from the OTA is
  only evidence (a `REMITTANCE` or note). A `BANK_RECEIPT` whose currency differs
  from the position's currency opens a payout dispute.
- Q (CHK004): Is there an FX variance in V1? → A: No. There is no exchange-rate
  risk in V1 because the payout is in VND, so no `FX_VARIANCE` fact is created and
  no exchange-rate entry is built. Every difference between the expected net
  (plus evidenced adjustments) and the money received is a payout dispute; the
  Manager closes it with an `ADJUSTMENT` that carries evidence and a reason.
  `FX_VARIANCE` would only apply if an OTA paid in a foreign currency, which is
  outside V1. The earlier Q2 wording about an "FX-variance adjustment" is
  superseded.
- Q (CHK013): What exactly is the retry schedule? → A: One initial send plus 3
  retries; each retry waits 30 seconds, 2 minutes and 10 minutes after the
  previous failure (4 sends; the worst case is about 14.5 minutes including the
  poll of at most 30 seconds, under 15 minutes). The first send happens at the
  first poll after the change is accepted. Only after the last retry fails does
  the affected mapping close sales (FR-004c, FR-017). The configuration is
  `max-retries = 3` plus the list of waits, and each outbound change carries a
  `next_attempt_at`. The sales-close control command (`SALES_CLOSE` in the
  control outbox) follows the same schedule and needs a waiting state; Mland
  has already closed the mapping on its side, so later bookings are refused
  (FR-004d) and a slower close on the OTA side cannot overbook.
- Q (CHK018): In what order are provider events processed? → A: In the order
  Mland receives them, through a one-way state machine on the channel booking:
  `OPEN` → `CANCELLATION_REQUESTED` → `CANCELLED` → `REFUNDED`. An event that
  would move the booking backwards is refused as a late event
  (`LATE_REQUEST_AFTER_CANCELLATION`), is audited, opens no work item and has no
  effect (this is the behaviour of T108). A partial cancellation counts only its
  own k and is idempotent per event, so the order between partial events does not
  matter. A cancellation for a booking Mland does not know opens an
  `INBOUND_EVENT` work item with reason `UNKNOWN_BOOKING_REFERENCE`; if that booking arrives later it is
  confirmed normally and the older work item stays for a Manager. Ordering by
  the OTA's `occurredAt` was not chosen because the provider timestamp is not
  yet known to be reliable (T004); this can be reviewed after T004.
- Q (CHK024): Are secret rotation, replay windows and revocation required for
  the interim ingress? → A: Deferred, explicitly. The interim ingress is not a
  production authentication scheme (E3 blocks `PRODUCTION`). Rotating the secret
  means changing the value in the approved secret channel by runbook, with no
  window where two secrets work. Revoking means changing the value or setting
  the connection to non-`ACTIVE`. Replay protection rests on the dedupe key (A1).
  A signature with timestamp, nonce, replay window and key rotation is a
  go-live condition for each provider ("Callback authentication" in
  provider-readiness), not part of the interim ingress.
- Q (CHK026): What can an Admin do in 003.4? → A: Nothing. All Manager
  business actions in this feature belong to an active Manager: viewing
  channels and mappings, creating mappings, sync and force re-sync, viewing and
  resolving work items, and the booking-enable decision. Admin has no REST route
  in 003.4; provider connection and credential configuration is environment
  configuration, not an API. The cost is that go-live no longer needs two
  different people; it is offset by the audit trail naming the Manager who
  decided. This replaces the earlier split in which Admin owned connection and
  booking-enable and could work work items (FR-004b, FR-004b1, C2).
- Q (alerts): Does 003.4 send an email when a work item opens, when retries run
  out or when a mapping closes? → A: Not in V1. A Manager sees work items on the
  Reconciliation page. To be reviewed after go-live. If an alert is added later it
  goes only to Managers through `AccountFacade.listActiveManagerEmails()`, in
  coordination with Feature 007.
- Documentation sync with no new decision: CHK020 (ingress status codes, now
  stated in "Interim provider booking ingress"), CHK027 (Staff and Admin get
  `403` before the booking is looked up, FR-004b1), CHK028 (the lookup answers
  `404`, the quote, acceptance and direct-bill routes answer `403`, plan and
  contract), CHK032 (the shared facade contract lists the 003.2 OTA ticket
  revocation operation), CHK036 (contract no longer says the whole booking is
  cancelled), CHK037, CHK038 and CHK039 (SC-007, SC-008, SC-009 and the rewritten
  SC-003).
- Error and dependency conventions, aligned with the 003.2 decisions of the same
  day: a same-`Idempotency-Key` request with a different command, or a
  conflicting payout-evidence replay, returns `409 IDEMPOTENCY_CONFLICT`; when
  the Account facade cannot supply the actor's role, status or location scope,
  an authenticated route fails closed with `503 DEPENDENCY_UNAVAILABLE` and no
  side effect (FR-022); test tokens are honoured only for an `ACTIVE`
  `TEST` connection; a refused sensitive action (booking-enable, price terms,
  payout reconciliation, resolving a work item) is recorded in the audit trail
  as a denied attempt, in the pattern of 003.2 FR-007b. These four conventions
  (`409 IDEMPOTENCY_CONFLICT`, `503 DEPENDENCY_UNAVAILABLE` fail-closed, test
  token only for an `ACTIVE` `TEST` connection, audit of the refused attempt)
  were confirmed by Hồ Hoàng Quốc on 2026-11-15 as applying to 003.4 the same
  way as to 003.2.

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
- **Interim provider booking ingress (backfilled 2026-11-12, 1fe139a):** before
  per-provider onboarding contracts are approved, inbound booking callbacks use
  the provider-neutral `POST /api/v1/ota-provider/booking-events`. It identifies
  provider, environment, external booking/event references, provider
  product/option, participant count, accepted amount and currency; a confirmed
  outcome returns the Mland booking and ticket references, a rejection returns a
  reason. Caller authentication compares `X-Ota-Signature` in constant time
  with the secret that the `ACTIVE` connection's `credential_reference` (a
  non-secret lookup name) resolves to from the environment or secret channel
  (T059, T118); a reference that resolves to nothing, or is blank, fails closed
  in every environment, `TEST` included. Failure statuses are fixed: a missing or
  wrong `X-Ota-Signature`, a blank or unresolvable credential reference, or no
  `ACTIVE` connection for the provider and environment returns `401`; a
  `PRODUCTION` callback from a provider not listed in
  `approved-production-providers` returns `403` before the signature is checked.
  Both use the safe typed error body and reveal neither which check failed nor
  whether a connection exists. This is not an approved
  provider signature scheme, so the endpoint fails closed (E3, 2026-11-12): a
  `PRODUCTION` callback is refused with 403 unless the provider is listed in
  `ota.provider-callbacks.approved-production-providers` (environment
  `OTA_APPROVED_PRODUCTION_CALLBACK_PROVIDERS`, empty by default). A provider
  may be listed only after its real signed-callback procedure is approved in
  provider-readiness ("Callback authentication") and implemented, which needs
  the provider's partner account and documentation. Test tokens are honoured
  only for an `ACTIVE` `TEST` connection and only when `ota.auth.allow-test-tokens` is on
  (off by default); a test token never replaces the connection check.
  *(Deferred 2026-11-15, Hồ Hoàng Quốc, CHK024, FR-010: this static-secret
  ingress is not a production authentication scheme, and secret rotation,
  timestamp/nonce replay windows and two-secret rotation are not built. Rotating
  a secret means changing its value in the secret channel by runbook; revoking
  means changing the value or setting the connection to non-`ACTIVE`; replay
  protection rests on the dedupe key below. A signature with timestamp, nonce,
  replay window and key rotation is a go-live condition of each provider.)*
  Replays are deduplicated by external event reference and
  payload fingerprint. *(Corrected 2026-11-14 to the implemented key, see A1
  below: the dedupe key is per connection, SHA-256 of the provider plus the
  external event reference, falling back to the external booking reference for
  a booking callback; the payload fingerprint is used only when no reference
  exists.)*
  **Decided 2026-11-14 (Hồ Hoàng Quốc, owner Feature 003; D1, tracked by
  T059):** the comparison above treats `credential_reference` stored in MySQL
  as the bearer secret, which conflicts with Constitution II. The fix is to
  **resolve the reference to a secret held in the approved secret channel**:
  `credential_reference` MUST stay a non-secret lookup name (for example
  `OTA_KLOOK_INGRESS_SECRET`), the backend MUST read the secret value for that
  name from the approved environment or secret channel, and the comparison with
  `X-Ota-Signature` MUST use that value, in constant time as before. A
  connection whose reference resolves to nothing MUST fail closed in every
  environment. No constitution exception is recorded, and `TEST` works the same
  way, so a secret can never reach MySQL in any environment. The E3
  production fail-closed rule stays as it is. *(Implemented and verified
  2026-11-14 by T059; the comparison wording above aligned 2026-11-15, T118.)*
- A booking for N participants creates exactly N active Mland tickets. The OTA
  must deliver each ticket/QR individually before the option can be sold.
- ~~A shared voucher/order code is only an authorized, audited post-go-live
  incident fallback; it cannot check in an entire booking automatically.~~
  *(Superseded 2026-11-14, recorded 2026-11-15 by the FR-009 amendment, T117:
  there is no shared-voucher fallback; an OTA ticket is a single ticket,
  checked in one at a time.)*
- OTA customers pay and are refunded under the policy shown by the OTA.
  ~~Mland never uses its direct-payment gateway to refund OTA-collected
  money.~~ *(Superseded 2026-11-14: OTA bookings keep their WorkshopBooking
  and have no Mland folio; 003.3 per-ticket settlement does not apply; only
  the D12 add-on bill exists on the Mland side.)*
- Customer channel payment, OTA payout owed to Mland, actual payout, and a
  direct Mland add-on bill are separate financial facts. Corrections append
  evidence rather than editing history.
- For an OTA-origin workshop package, the customer pays the full amount due
  through the OTA checkout. Mland does not collect a deposit or final payment
  for that base package. The OTA's later supplier payout to Mland is a separate
  receivable and reconciliation flow; a payout is not customer-payment proof.
- Mland may confirm an OTA booking and issue its tickets only when the
  authenticated provider outcome carries payment evidence. The amount and
  currency are kept as the OTA reported them and are not compared with Mland's
  retail price (T113, FR-012 amendment). If the provider's callback or
  reconciliation contract cannot supply payment evidence, the mapping stays
  sales-closed until the provider evidence is approved. *(Aligned 2026-11-15 to
  the 2026-11-14 decision, T113; the earlier full-amount-and-currency rule is
  superseded.)*
- Add-ons are not sold through the OTA checkout. After an OTA-origin customer
  arrives at the Mland shop, laser engraving is included free and creates only
  a zero-charge operational record. Any other paid option needs a prior quote
  and customer acceptance at the shop, then a separate direct Mland bill with
  no OTA price, payout, commission, or refund effect. The paid option's price
  comes from the active 003.2 catalogue add-on (server-derived, D17), and
  in-scope Staff or any active Manager may handle it (D18). *(Amended
  2026-11-06: an in-scope Staff member may handle it only while actively
  assigned to a session the OTA booking's Mland booking holds a valid ticket
  for; see "Staff booking-assignment access" above.)*

## Open decisions and release notes (review 2026-11-14)

- **D1 — Decided 2026-11-14 (Hồ Hoàng Quốc):** `credential_reference` stays a
  non-secret lookup name and the backend resolves it to a secret held in the
  approved secret channel before comparing `X-Ota-Signature`; an unresolvable
  reference fails closed in every environment. No constitution exception is
  recorded (see the interim ingress text above; implemented by T059, done and verified
  2026-11-14 (11/11), so it no longer blocks T054; an
  `approved-production-providers` entry still needs its approved callback
  procedure, E3).
- **A1 — inbound replay key (implemented behaviour, read from
  `OtaInboundEventService`):** the key is unique per connection and equals
  SHA-256 of `EVENT:<provider>:<reference>`, where the reference is the
  external event reference or, for a booking callback without one, the
  external booking reference; `PAYLOAD:<payload fingerprint>` is used only when
  no reference is supplied. A cancellation callback without an event reference
  currently gets a random reference, so it is not deduplicated at the
  inbound-event layer (its idempotency rests on the channel booking state). A
  second event with the same reference but a different payload fingerprint
  returns the stored prior outcome; no conflict is raised. *(Superseded
  2026-11-14 by the decision below, T060: the random reference and the silent
  prior outcome are not the intended behaviour; a same-reference,
  different-fingerprint event is refused as a conflict and opens an
  `INBOUND_EVENT` work item, and an adapter never invents an event reference.)*
  **Decided 2026-11-14 (Hồ Hoàng Quốc, owner Feature 003):** the key above is
  **confirmed as the one dedupe key**, and two gaps MUST be closed (T060):
  1. **Conflict rule:** a second event carrying the same reference but a
     different `payloadFingerprint` MUST be refused as a conflict and MUST open
     an `INBOUND_EVENT` reconciliation work item; it MUST NOT overwrite or
     silently reuse the stored outcome, which is what US4 already requires. A
     true replay — same reference and same fingerprint — keeps returning the
     stored outcome.
  2. **No invented reference:** when a provider sends no event reference, the
     adapter MUST leave it empty so the service falls back to
     `PAYLOAD:<fingerprint>`, which is the branch meant for that case. The
     Klook cancellation adapter currently invents `klk-ccl-ev-<random>`, so
     every redelivery looks new and the inbound layer never deduplicates it.
- **U1 — Decided 2026-11-14 (Hồ Hoàng Quốc); superseded 2026-11-15 by
  Clarifications Session 2026-11-15 (partial confirmed cancellation revokes k
  tickets by a fixed rule; no longer whole-booking only; tasks added in the sync
  step):** OTA cancellation stays
  ~~**whole-booking only in V1**, and no ticket subset is ever revoked from an
  OTA event. The reason is what the providers actually send: the Klook and
  GetYourGuide cancellation adapters and `OtaCancellationEventCommand` carry
  only the external booking and event references, a cancellation kind
  (`REQUESTED`, `CONFIRMED_CANCELLED`, `REFUNDED`), a refund amount, currency
  and reason — no participant or ticket count — so a partial cancellation
  cannot be mapped to tickets without guessing which ticket to revoke
  (`PARTIALLY_REFUNDED` is a payment state, not a ticket outcome). A provider
  event that nonetheless indicates fewer participants MUST NOT revoke any
  ticket: it opens a reconciliation work item for Staff or a Manager to settle
  with the provider. Whether per-ticket OTA cancellation becomes possible is
  decided in T038b from the real, contract-approved provider payloads (T004),
  not here.~~ *(Struck 2026-11-15: replaced by Clarifications Session 2026-11-15 Q3.)*
- **C2 — Decided 2026-11-14 (Hồ Hoàng Quốc):** `INBOUND_EVENT` reconciliation
  work items (unknown booking, unmapped event, and from T060 a dedupe conflict)
  MUST be listed and resolved through the **existing**
  `GET /api/v1/ota-reconciliation-work-items` and
  `POST /api/v1/ota-reconciliation-work-items/{workItemId}/resolve`, not a new
  API. The listing MUST stop restricting itself to mapping-linked items
  (`OtaReconciliationService.listWorkItems` calls `findMappingLinked…` today,
  so these items can never appear), MUST accept an optional `category` filter,
  and MUST return `mappingId` as null for an item that has no mapping.
  ~~Authority is unchanged: Manager or Admin.~~ *(Superseded 2026-11-15, Hồ
  Hoàng Quốc, CHK026: Manager only; Admin has no authority in 003.4, and a
  `PAYOUT_DISPUTE` item is a money decision that only a Manager closes.)*
  Implemented by T061; the Admin removal is T138.
- **G1 — release note:** every OTA mapping stays `SALES_CLOSED` (or earlier)
  until T051 sandbox/ticket/scan evidence and the T054 go-live review are
  recorded for that provider.

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
   booking for N participants with authenticated payment evidence (amount
   and currency kept as the OTA reported them, T113), **Then** Mland creates one channel booking,
   exactly N active tickets, and commits N places as one result. A booking
   lacking payment evidence is not confirmed and receives no tickets.
2. **Given** the same accepted request is delivered again, **When** Mland
   processes it, **Then** it returns the original result without duplicate
   booking, ticket, capacity, or financial effects.
3. **Given** capacity is insufficient or the mapping is disabled, **When** an
   OTA requests a booking, **Then** it receives a clear rejection and no change
   persists.
4. **Given** a channel delivers one shared voucher for N people, **When** its
   option is assessed, **Then** Mland keeps it disabled. *(Note 2026-11-15,
   T117: there is no shared-voucher fallback; an OTA ticket is a single ticket.)*

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
   exactly once. *(Added 2026-11-15: a confirmed partial cancellation revokes k tickets, Clarifications Q3, replacing U1; the whole booking only when k equals the active tickets, and k greater than the active or the not-yet-checked-in tickets revokes nothing and opens an `INBOUND_EVENT` work item (Checklist follow-up, CHK009/CHK010); refund facts are only what the OTA reports, T109, and never release a ticket.)*
3. **Given** an invalid, duplicate, late, or out-of-order callback, **When** it
   arrives, **Then** it cannot make a second financial, ticket, or capacity
   effect and is safely audited or queued for reconciliation. Provider events
   are applied in the order Mland receives them through the one-way booking
   states of FR-011; an event that would move a booking backwards is refused as
   late, audited, and opens no work item.

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
   linked adjustment/dispute and does not imply an OTA payout. *(Clarified
   2026-11-15: in V1 a "variance" is simply a payout difference, recorded as a
   dispute; there is no separate FX variance.)*

Manager-led reconciliation records separate eligibility, invoice, remittance,
and bank-receipt evidence for each booking. `PAID_OUT` requires booking-specific
allocation evidence whose received total and currency match the expected net
plus signed, evidenced adjustments and reversals. A customer refund or fee does
not silently change the supplier receivable: Manager records its contractual
impact as a linked adjustment. *(Clarified 2026-11-15, CHK002: this includes a
partial or whole-booking OTA cancellation. The expected net stays
`targetNetAmount × N` and the system never posts the adjustment itself; if the
Manager records none and the OTA pays another amount, the dispute opens.)*
Missing or mismatched evidence opens a dispute
and does not advance the payout state. *(Clarified 2026-11-15, CHK003/CHK004: the
price terms and the bank receipt are both in VND, a bank receipt in another
currency is a dispute, and V1 has no FX variance: every difference that is not
an evidenced `ADJUSTMENT` is a dispute, which a Manager closes with an
`ADJUSTMENT` carrying a reason.)* Grouped OTA payouts require a distinct
allocation reference for each booking; the system does not guess the split.
Replay of the same evidence returns the original fact, while a conflicting
replay is rejected.

---

### User Story 5 — Staff records an add-on at the Mland shop transparently (Priority: P2)

Staff needs to provide an extra service at the Mland shop after an OTA booking
without silently changing the OTA sale.

**Why this priority**: It protects customer consent and reconciliation.

**Independent Test**: At the Mland counter, record free laser engraving or
quote/accept a paid option and take direct Mland payment. The counter bill
remains separate from OTA financial facts.

**Acceptance Scenarios**:

1. **Given** an OTA booking, **When** the customer requests laser engraving at
   the Mland shop, **Then** it is recorded at zero charge.
2. **Given** a paid option is requested at the Mland shop without quote
   acceptance, **When** Staff attempts to add it, **Then** the option cannot be
   charged.
3. **Given** a guest accepts a shop quote, **When** Staff bills it directly,
   **Then** the bill snapshots item, price, currency, consent, actor, and
   payment reference and does not alter OTA financial facts.

### Edge Cases

- OTA requests compete for final session capacity.
- A provider replays a booking/cancellation/refund event after timeout.
- Availability delivery is late, fails, or has incompatible mapping/state.
- A partial customer refund and later statement/payout have different outcomes.
  *(Note 2026-11-15: a confirmed partial OTA cancellation revokes k tickets by the fixed rule (Clarifications Q3, replacing U1), and a request revokes nothing; a refund fact is recorded only as the OTA reports it, T109.)*
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
  The OTA `saleState` enum is `{PUBLISHED, SALES_PAUSED, CLOSED}`, mapped from
  session states: `PUBLISHED` to `PUBLISHED`, `SALES_PAUSED` to `SALES_PAUSED`,
  and `IN_PROGRESS`, `ATTENDANCE_FINALIZED`, `CANCELLATION_REQUESTED`,
  `CANCELLED` to `CLOSED`. Any value other than `PUBLISHED` means the session is
  not sellable at the OTA. *(Added 2026-11-16, F04 reopened, option B.)*
- **FR-004b**: An active Manager MAY perform authorized OTA business sync and
  reconciliation actions across all locations; Manager authorization does not
  depend on `activeLocationIds`. ~~Admin owns OTA credentials and connection
  configuration and MAY perform its authorized technical/recovery actions
  across locations.~~ *(Superseded 2026-11-15, Hồ Hoàng Quốc, CHK026: an Admin
  has no authority and no REST route in 003.4. The Manager actions are: view
  channels and mappings, create and update mappings, record price terms, sync and
  force re-sync, close sales, view and resolve reconciliation work items,
  record the booking-enable decision, read financial evidence and reconcile
  payouts. Provider connection and credential configuration is environment
  configuration, not an API.)* Every action
  MUST record actor, location, channel, scope, reason, and outcome; a display
  role name alone MUST NOT grant either authority. An Admin, and any other role
  that is not an active Manager (or Staff where a Staff action is named), is
  refused with `403`.
- **FR-004b1**: An active Manager MAY read separated OTA financial evidence and
  perform payout reconciliation for any canonical location. These actions are
  Manager-authorized globally and MUST append immutable, auditable evidence;
  location assignment does not restrict access. ~~Provider connection/credential
  changes and production booking enablement remain Admin-only.~~ *(Superseded
  2026-11-15, CHK026: booking enablement is a Manager decision, and provider
  connection/credential changes are environment configuration with no REST
  route.)* Staff, including Staff assigned to a session of the booking, and
  Admin MUST NOT read OTA financial evidence or record payout reconciliation;
  they receive `403` before the channel booking is looked up, so the answer
  never shows whether the booking exists.
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
- **FR-009**: ~~A shared OTA voucher MUST be an authorization-protected,
  audited incident fallback and MUST NOT automatically check in all participants.~~
  *(Superseded 2026-11-14, owner decision Hồ Hoàng Quốc, recorded 2026-11-15
  (T117): there is no shared-voucher fallback. OTA vouchers and discounts live in
  the OTA's own system; at check-in an OTA ticket is a single ticket equivalent
  to a Mland ticket, checked in one at a time. `OtaVoucherFallbackService` is
  left unchanged and unexposed.)*
- **FR-010**: The system MUST authenticate callbacks, deduplicate events, retain
  safe correlation/reference data, and never log credentials, raw tokens,
  signing secrets, or avoidable PII. *(Clarified 2026-11-15, CHK024 and CHK020:
  callback authentication fails closed with the statuses fixed under "Interim
  provider booking ingress"; secret rotation, timestamp/nonce replay windows and
  revocation beyond changing the secret value or deactivating the connection are
  deferred to each provider's go-live, and the interim ingress is not a
  production authentication scheme.)*
- **FR-011**: The system MUST distinguish a channel cancellation request from a
  confirmed cancellation/refund outcome and release tickets/capacity only after
  confirmation. *(Revocation: whole-booking only in V1 was decision U1,
  superseded 2026-11-15: a confirmed partial cancellation of k participants
  revokes k tickets by the fixed rule in Clarifications Session 2026-11-15.)*
  Refund
  facts are only what the OTA reports (T109).
  *(Amended 2026-11-15, owner decision Hồ Hoàng Quốc, CHK007/CHK009/CHK010/CHK018:
  only a confirmed-cancellation event releases tickets; a refund event
  (`REFUNDED`, `PARTIALLY_REFUNDED`, `REFUND_REQUESTED`) never does. For a
  confirmed cancellation of k participants, k being that event's own count: k
  below the active tickets revokes k not-yet-checked-in tickets (the payload's
  named ticket, else the highest ticket sequence first); k equal to the active
  tickets cancels the whole booking; k above the active tickets, or above the
  not-yet-checked-in tickets, revokes nothing and opens an `INBOUND_EVENT` work
  item; a checked-in ticket is never revoked and attendance history is never
  changed; an OTA that sends only `REFUNDED` with no cancellation event opens an
  `INBOUND_EVENT` work item. The channel booking moves one way,
  `OPEN` → `CANCELLATION_REQUESTED` → `CANCELLED` → `REFUNDED`, and events are
  applied in the order received. The transition table below was confirmed by
  Hồ Hoàng Quốc on 2026-11-15, including the two rows for a refund reported
  while the booking is `OPEN` or `CANCELLATION_REQUESTED` and for a second
  confirmed-cancellation event after `CANCELLED`.)*

  | Event received | Booking state before | Effect |
  | --- | --- | --- |
  | Cancellation requested | `OPEN` | `CANCELLATION_REQUESTED`; `REFUND_REQUESTED` evidence if the OTA reports it; nothing revoked. |
  | Cancellation requested (again, new reference) | `CANCELLATION_REQUESTED` | No new evidence or state change. |
  | Cancellation requested | `CANCELLED` or `REFUNDED` | Refused as `LATE_REQUEST_AFTER_CANCELLATION`; audited; no work item; no effect. |
  | Cancellation confirmed (k) | `OPEN` or `CANCELLATION_REQUESTED` | Revocation by the rule above, once per OTA event; whole-booking cancellation moves to `CANCELLED`. |
  | Cancellation confirmed | `CANCELLED` | Replay of the same event: stored outcome. A different event with k above the (now zero) active tickets: nothing is revoked and an `INBOUND_EVENT` work item opens (the k-above-active rule). |
  | `REFUNDED` / `PARTIALLY_REFUNDED` reported | `CANCELLED` | Money evidence only, in the amount the OTA reported; `REFUNDED` also moves the booking to `REFUNDED`; no ticket effect. |
  | `REFUNDED` / `PARTIALLY_REFUNDED` reported | `OPEN` or `CANCELLATION_REQUESTED` | Money evidence recorded; no ticket effect; an `INBOUND_EVENT` work item opens because the OTA reported a refund with no cancellation event. |
  | `REFUNDED` again, or `REFUND_REQUESTED` / `PARTIALLY_REFUNDED` | `REFUNDED` | Refused as a late event; audited; no work item; no second evidence. |
  | Any event | booking unknown | `INBOUND_EVENT` work item (reason `UNKNOWN_BOOKING_REFERENCE`); if the booking arrives later it is confirmed normally and the older item stays for a Manager. |
- **FR-012**: The system MUST record customer channel-payment states as
  `PAID_ON_CHANNEL`, `REFUND_REQUESTED`, `PARTIALLY_REFUNDED`, or `REFUNDED`.
  `PAID_ON_CHANNEL` MUST represent authenticated evidence that the OTA
  collected payment for the booking, with the amount and currency kept as the
  OTA reported them and not compared with Mland's retail price (T113). An
  absent payment outcome MUST NOT confirm the channel booking or issue
  tickets. `PARTIALLY_REFUNDED` is recorded only if the OTA itself reports a
  partial refund; Mland never derives it (T109). These states MUST NOT be read
  as a direct-Mland payment, booking, or supplier-payout state. Mland MUST NOT
  create a `DEPOSIT` or `FINAL_PAYMENT` attempt for the OTA-collected base
  package.
  *(Amended 2026-11-14, owner decision Hồ Hoàng Quốc, recorded 2026-11-15
  (T113, T109): the price an OTA customer pays always differs from Mland's retail
  price (platform fee and other OTA charges), so Mland MUST NOT compare the
  customer's amount with its own price and MUST NOT reject a booking for a
  smaller amount or a different currency; the amount and currency are kept as
  the OTA reported them, for reconciliation and payout only. The number of
  tickets follows the participant count. Absent payment evidence still does not
  confirm the booking. Refunds are the OTA's own logic: Mland records the
  facts the OTA reports and releases tickets and capacity only when the OTA
  confirms a cancellation (FR-011).)*
  *(Clarified 2026-11-15, CHK007: customer-payment evidence is an append-only
  list. Each refund event writes the amount the OTA reported (a
  `CUSTOMER_REFUND` ledger fact); Mland never adds them up and never derives
  `REFUNDED` or `PARTIALLY_REFUNDED` from a total. The current state is the
  latest evidence in order of receipt. Several different `PARTIALLY_REFUNDED`
  events are all recorded. `REFUNDED` is final: a later `REFUND_REQUESTED` or
  `PARTIALLY_REFUNDED` is refused as a late event (audited, no work item), and a
  second `REFUNDED` with a different amount is a late event with an audit entry,
  never silently ignored. A replay of the same event returns the stored outcome.
  Refund events release no ticket (FR-011).)*
- **FR-013**: The system MUST snapshot sales channel, displayed policy/version,
  cutoff/timezone, price terms, commission/fee facts, and accepted currency
  amounts for each OTA booking; later configuration cannot rewrite the snapshot.
- **FR-014**: The system MUST keep supplier payout state separate as
  `NOT_ELIGIBLE` → `ACCRUED` → `INVOICED` → `PAYOUT_PENDING` → `PAID_OUT`.
- **FR-015**: The system MUST retain an append-only reconciliation ledger for
  gross sale, commission/fee, customer refund, expected/received payout, FX
  variance, compensation, and adjustment, with amount, currency, time,
  reference, reason, and related records. *(Clarified 2026-11-15, CHK004: in V1
  `FX variance` is not recorded: the payout is in the price terms' currency
  (VND), so a payout difference is a dispute closed by an evidenced
  `ADJUSTMENT`. The `FX_VARIANCE` kind would apply only if an OTA paid in a
  foreign currency, which is outside V1, and no exchange-rate entry is built.)*
- **FR-016**: The system MUST correct channel financial evidence through linked
  adjustment/reversal records and MUST NOT overwrite historical facts.
- **FR-017**: The system MUST retry failed outbound notification without a
  second booking/capacity effect; exhausted retry, mapping failure, or state
  difference MUST create an auditable reconciliation work item. *(Clarified
  2026-11-15, CHK013: retry is one initial send plus 3 retries, 30 seconds,
  2 minutes and 10 minutes after the previous failure; the `SALES_CLOSE` control
  command follows the same schedule; only the affected mapping closes sales and
  at most one open work item exists for it. CHK026 follow-up: V1 sends no email
  or other alert for a new work item, exhausted retry or sales closure; a Manager
  sees the item on the Reconciliation page, to be reviewed after go-live. A
  later alert goes to Managers only, through
  `AccountFacade.listActiveManagerEmails()`. "Outbound notification" in this FR
  remains the synchronization of state to the OTA, never a message to the OTA
  customer (Q4).)*
- **FR-018**: The system MUST retain only contractually permitted, operationally
  necessary OTA customer data and MUST NOT create a Member, participant profile,
  magic link, or marketing audience from it.
- **FR-019**: For an OTA-origin booking, laser engraving requested at the Mland
  shop MUST be included free and recorded with zero charge. Other paid options
  MUST receive a prior shop quote and customer acceptance before billing.
  **Updated 2026-11-05 (D17/D18):** a paid option MUST be an active 003.2
  catalogue add-on for the booking's package/location; the server derives its
  description and price, and the actor MUST NOT supply an amount. In-scope
  Staff or any active Manager may create, accept and bill the quote.
  *(Amended 2026-11-06: "in-scope Staff" means location scope AND an active
  assignment to a session the Mland booking holds a valid ticket for.)*
- **FR-020**: The system MUST record an accepted paid add-on sold at the Mland
  shop as a separate Mland counter bill with immutable
  item/price/currency/consent/actor/payment evidence, without changing OTA
  price, channel payment, commission, payout, settlement, or channel refund.
- **FR-021**: The system MUST authorize configuration, publication,
  reconciliation resolution~~, and fallback check-in~~ *(withdrawn 2026-11-15, T117)* on the server using active
  Mland authority and resource scope; OTA claims/UI visibility grant no power.
- **FR-022**: The system MUST return safe typed, correlation-linked failures
  without exposing other booking data or sensitive integration information.
  *(Clarified 2026-11-15: a missing, wrong or expired Mland token is `401`; a
  non-Manager or out-of-scope actor is `403`, and only the lookup by Mland booking
  reference answers an unassigned or out-of-scope Staff member `404`, the quote,
  acceptance and direct-bill routes taking an opaque UUID and answering `403`
  (owner decision 2026-11-06); a repeated `Idempotency-Key` with a different
  command, or a conflicting payout-evidence replay, is `409 IDEMPOTENCY_CONFLICT`;
  when the Account facade cannot supply the actor's role, status or location
  scope the route fails closed with `503 DEPENDENCY_UNAVAILABLE` and no side
  effect; callback authentication statuses are fixed under "Interim provider
  booking ingress".)*

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
- **Direct Mland Shop Add-on Bill**: Separate Mland charge for an optional
  service requested at the shop, excluded from OTA calculations.
- **Reconciliation Work Item**: Unresolved delivery, mapping, state, or financial
  difference with safe correlation and authorized resolution.

## Success Criteria

- **SC-001**: 100% of duplicate-booking/callback acceptance tests create no
  duplicate booking, ticket, capacity, or financial effect.
- **SC-002**: 100% of enabled channel options in go-live verification create and
  deliver exactly one Mland-scannable ticket per participant.
- **SC-003**: ~~100% of confirmed cancellation/refund tests revoke only applicable
  unused tickets and release capacity once; unconfirmed requests do neither. A confirmed partial revocation of k tickets is idempotent and audited (Q3, replacing U1); refund facts are only what the OTA reports (T109).~~
  *(Rewritten 2026-11-15, checklist CHK039, owner decision on CHK009:)* 100% of
  confirmed cancellation tests revoke only not-yet-checked-in tickets (k of them,
  chosen by the Q3 rule or named by the payload) and release their future
  capacity exactly once; checked-in tickets and attendance history are never
  changed; unconfirmed requests and refund events revoke nothing. A replay of
  the same OTA event has no second effect and every revocation is audited with
  the revoked tickets. A k above the active or the not-yet-checked-in tickets
  revokes nothing and opens an `INBOUND_EVENT` work item. Refund facts are only
  what the OTA reports (T109).
- **SC-004**: 100% of reconciliation tests keep channel customer payment,
  expected payout, received payout, and direct Mland add-on totals distinct.
- **SC-005**: 100% of publication attempts lacking commercial approval, supported
  pricing, or ticket evidence are rejected.
- **SC-006**: 100% of OTA failure log/audit test views redact credentials, raw
  tokens, secrets, and unnecessary customer data.
- **SC-007** *(added 2026-11-15, CHK037)*: 100% of payout-position tests open the
  position with expected net equal to `targetNetAmount × N` in the price terms'
  currency (tested with N = 1 and N = 3) and keep it unchanged by later
  price-term changes and by a partial or whole-booking cancellation; 100% of
  tests with a customer payment in a foreign currency reconcile the payout in the
  price terms' currency and never use the customer-paid amount to match it; a
  payout difference that is not an evidenced `ADJUSTMENT` opens a dispute.
- **SC-008** *(added 2026-11-15, CHK038)*: 100% of callbacks with a missing or
  wrong signature, an unresolvable credential reference, no `ACTIVE` connection,
  or an unapproved `PRODUCTION` provider are refused (`401`/`403`) with no
  inbound-event effect on booking, ticket, capacity or financial records.
- **SC-009** *(added 2026-11-15, CHK038)*: 100% of outbound-retry tests follow the
  configured default schedule (one initial send, then retries 30 seconds,
  2 minutes and 10 minutes after the previous failure, the last send within 15
  minutes of the first); on exhaustion only the affected mapping is sales-closed,
  the close is audited, and at most one open reconciliation work item exists for
  that mapping. The `SALES_CLOSE` control command follows the same schedule.

## Assumptions and Dependencies

- 003.1 supplies booking/ticket outcomes; 003.2 owns session capacity, check-in,
  and attendance. This feature consumes rather than duplicates them.
- 003.3 owns direct-Mland final payment/refund. ~~OTA money never uses its
  direct deposit/refund gateway flow.~~ *(Superseded 2026-11-14: OTA bookings
  keep WorkshopBooking and have no Mland folio; 003.3 per-ticket settlement
  does not apply; only the D12 add-on bill exists. Its refund is the 003.3
  FR-038a bank refund.)*
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

**Provider release gates remain** *(note 2026-11-15: after T113, any "payment semantics" gate means payout and payment-evidence presence only, not an amount or currency check against Mland's price)*: Provider contracts, commercial terms,
credential/callback configuration, quotas, late-booking behavior, and deployment
ownership need approved follow-up evidence. Finance authorization is resolved:
any active Manager may read financial evidence and perform payout reconciliation
globally. This specification grants neither provider publication nor production
booking enablement authority.
