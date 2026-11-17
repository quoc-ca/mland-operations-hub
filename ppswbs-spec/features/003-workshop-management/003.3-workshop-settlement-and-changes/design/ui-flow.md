# UI Flow Design: 003.3 Workshop Settlement and Changes

**Date created**: 2026-11-02
**Date updated**: 2026-11-16
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

> **Superseded in part — 2026-11-15.** The booking-wide flow in "Actors,
> routes, and authorization", "Screen and interaction flows", "Final-payment
> recovery guardrails" and invariants 2–3 describes the whole-booking model,
> whose routes were removed (T106, T121, T123, T124). Direct bookings now use
> the per-ticket screens in "Per-ticket screens (2026-11-12 onward)" below.
> The booking-wide sections are kept for history; the settlement case list is
> still served. *(Refund-policy administration is NOT kept: the screen and the
> `/workshop-refund-policies` routes are removed by T140 (frontend) and T139
> (backend), decided 2026-11-15; FR-043 always refunds 100%.)*

## Scope and sources

- Business rules: `spec.md` (final bill/payment, direct cancellation, required-session changes, Mland-caused remedy/refund, add-ons).
- Transport contract: `contracts/workshop-settlement.openapi.yaml`; per-ticket
  contract `contracts/workshop-settlement-ticket.openapi.yaml` (FR-030–FR-057,
  added 2026-11-15).
- Current implementation reference: `ppswbs_frontend/src/workshop-settlement/`.

## Actors, routes, and authorization

| Actor | Route | Access rule |
| --- | --- | --- |
| Staff/Manager | `/settlement` | Staff operates in location scope; Manager has global Feature 003 scope. |
| Guest/Member | Booking lookup/detail | May view their own bill/case result and submit permitted whole-booking cancellation; ownership is server-verified. |
| Manager | Settlement/case actions | Required-session change, Mland-caused remedy/refund exception and policy administration are Manager-authorized. |

## Screen and interaction flows

| Screen / flow | Workflow/API reference | Required states and recovery |
| --- | --- | --- |
| Final bill workspace | `GET/POST /api/v1/workshop-bookings/{bookingCode}/final-bill` | Loading; folio not available (booking not deposit-confirmed, or legacy booking before backfill); immutable/snapshot lines; `FINALIZED` or `CLOSED` folio is read-only. |
| Add-on catalogue (read-only) | Booking detail / settlement workspace | Show eligible package/location add-ons and prices; no customer selection or checkout. For direct Mland bookings, Staff/Manager may confirm any active priced add-on, including laser engraving; the OTA-only free rule is not applied here. |
| Charge/add-on line | `/final-bill/charges` | Staff/Manager records workshop consent; server derives catalogue price; a direct booking appends only to its OPEN folio and a `FINALIZED` folio rejects the add-on read-only (correction only via the exceptional T040 flow); OTA uses the separate direct-Mland shop add-on bill (003.4) and free OTA laser engraving creates no bill; reject stale/duplicate charge. |
| Final payment | `/final-bill/payment-attempts`, `/final-bill/finalizations` | Separate `FINAL_PAYMENT` attempt; server-derived outstanding VND balance; payment attempt expires after 10 minutes; only verified IPN/QueryDR can settle. No deferred-credit path. |
| Direct customer cancellation | `/cancellation-cases` | Whole-booking request with reason; show non-refundable result; release/cancel through Operations handoff; never show a VNPay refund action. One case per booking. |
| Required-session change | `/required-session-change-cases` | Show eligible replacement options and stale-capacity conflict; no silent partial change; recover by reloading current sessions. |
| Mland-caused remedy/refund | `/mland-cancellation-remedy-cases` | Manager-only decision path; calculate from retained policy/payment evidence; show `PENDING_PROVIDER` only while awaiting verified provider evidence; do not mark complete on provider acceptance alone. |
| Refund-policy administration *(superseded 2026-11-15: screen to be removed by T140; routes removed by T139, FR-043)* | `/workshop-refund-policies` | Manager global; show effective time and percentage validation; preserve historical snapshots and display audit evidence. |
| Settlement result | Bill/case detail | Distinguish service state, bill state, payment state, and case state; show typed errors and correlation ID without secrets. |

## Final-payment recovery guardrails for regeneration

The final-payment screen uses only the state returned by Settlement and
verified `paymentintegration` evidence. It does not infer payment truth from a
provider redirect.

| Observed state | UI action permitted | UI action prohibited |
| --- | --- | --- |
| `PENDING` | Display the provider attempt deadline and prevent duplicate checkout. | Creating another final-payment URL. |
| `UNKNOWN` (network timeout, lost response, unverified provider result) | Retain the original `Idempotency-Key`; offer an explicit server status check or same-command retry with that key. | Automatic retry, a new key, a new attempt, output handover, or a generic reissue control. |
| `VERIFIED_PAID` | Refresh the server bill and allow only its eligible finalization/release action. | Using browser-return data as proof. |
| `EXPIRED` | Keep the bill unpaid and output withheld; show reconciliation status. | Immediate new checkout, credit, reminder, or payment-link reissue. |

After an `EXPIRED` or `UNKNOWN` outcome, any later attempt is exclusively a
server decision after QueryDR under `FR-011`; the UI does not manufacture a
recovery path.

## Invariants captured for regeneration

1. Direct customer cancellation after a confirmed direct booking is non-refundable; it does not create `PENDING_PROVIDER`, QueryDR, or VNPay refund.
2. VNPay refund remains only for an approved Mland-caused direct-payment remedy or a Manager post-FINALIZED overcharge correction (FR-004c), not for ordinary customer cancellation and not for OTA-collected base-package money. *(Superseded 2026-11-12/15 by spec FR-038: no VNPay Refund is used at all; every Mland refund is a bank transfer through the refund queue.)*
3. Final-payment checkout is separate from the deposit attempt and expires after 10 minutes.
4. A provider response is not verified completion; the case changes only after verified evidence.
5. No credit/deferred-payment workflow is exposed.
6. Customer add-on selection is not a booking step. Staff/Manager confirmation is
   required at the workshop; a direct booking appends only to its OPEN folio,
   and a `FINALIZED` direct folio accepts no add-on (the UI shows it read-only;
   correction uses only the exceptional T040 flow). All OTA bookings use the
   separate direct-Mland shop add-on bill; OTA base-package financial facts
   remain isolated.

## Per-ticket screens (2026-11-12 onward)

Sources: `contracts/workshop-settlement-ticket.openapi.yaml`, spec FR-030–FR-057,
and `ppswbs_frontend/src/workshop-settlement/routes.jsx` (pages in `pages/`).
Every screen shows server figures only, sends a new `Idempotency-Key` per
command, explains `403`/`404`/`409`/`422` by their typed code with the
correlation ID, and recovers by re-reading the server resource; a dangerous
command asks for confirmation and, where the contract requires it, a reason.

| Screen (route) | Actor | Loading | Empty | Validation | Failure (contract codes) | Recovery | Success | Navigation |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Ticket folio workspace (`/settlement`, `SettlementWorkspacePage`) — collections, add-ons, charges, finalize, debt, handover exception, line/collection voids, correction view | Manager; Staff assigned to the ticket's group line | Spinner while `GET …/folio` and add-ons load | "No folio was found for this ticket."; "No collection recorded."; no add-on for the package/location | Ticket code required; amount > 0; VNPay part ≥ 5,000 warned before collecting (FR-033); cash received ≥ amount; transfer reference required; reason required for debt, handover, voids | 404 unknown/not visible; 403 not assigned; 409 pending part, `FINAL_BILL_NOT_OPEN`, `IDEMPOTENCY_CONFLICT`, closed cash day on a void; 422 business refusal | Reload the folio; pending VNPay part shows its checkout link until expiry and is reconciled by the server; Manager may confirm or fail a pending part | "Collection recorded." (a `VNPAY` part also shows "Open VNPay checkout" with its expiry), "Transfer confirmed.", "Collection marked as not paid.", "Add-on added.", "Charge added.", "Folio finalized.", "Debt recorded."/"Debt closed.", "Handover allowed and audited. The balance below is still owed.", "Line voided."/"Collection voided."; the folio is re-read so balance and state are the server's | Stays on `/settlement` with the same ticket; the VNPay checkout opens in a new tab; an overcharge is corrected from the Manager correction panel and its refund followed in `/settlement/refunds`; FR-057 record/void is on this page (planned, T136) |
| Cash close (`/settlement/cash-close`, `CashClosePage`) | Manager; Staff of the branch | Spinner while expected cash loads | Expected cash 0 for a day with no cash | Branch (`locationId`) numeric; working date; counted amount ≥ 0; reason required when counted ≠ expected | 400 malformed body; 403 out of scope; 409 day already closed or `IDEMPOTENCY_CONFLICT`; 422 difference without `differenceReason`; 400/404 on difference confirmation | Re-read expected cash; a close with a difference waits as `PENDING_MANAGER_CONFIRMATION` for the Manager's confirmation (reason) | "Cash book closed." with the returned `CashClose` (expected, counted, difference, status; a difference shows `PENDING_MANAGER_CONFIRMATION`); after the Manager's confirmation "Difference confirmed; the day is closed." | Stays on `/settlement/cash-close`; transfers and VNPay are checked in the reconciliation list `/settlement/reconciliation` (planned, T134) |
| Refund queue (`/settlement/refunds`, `RefundQueuePage`) | Manager (every transition in V1) | Spinner while the queue loads | "No refund requests found." | Status filter; counter account (bank, number, holder) complete; bank reference for `RECORD_TRANSFER` (the only required evidence; the proof note and the receipt image are optional, *Superseded 2026-11-16, fix A08*); reason for close/reopen | 403 non-Manager; 409 wrong state, `REFUND_ACCOUNT_ALREADY_ON_FILE`; 404/409 `REFUND_ACCOUNT_NOT_ON_FILE` on reveal; 422 `TRANSFER_PROOF_REQUIRED` (for a missing bank reference; T154 renames it to the proposed `BANK_TRANSACTION_REFERENCE_REQUIRED`) | Reload the queue; resend the account link; reopen a `CLOSED_UNREACHABLE` request; full number only on an audited reveal (`no-store`) | "Account recorded.", "Refund approved.", "Transfer recorded.", "Refund confirmed as REFUNDED.", "Refund closed as unreachable.", "Refund reopened with a fresh account link.", "A fresh account link was sent; the previous link no longer works."; the queue is re-read with the new status | Stays on `/settlement/refunds` (linked from `/settlement/cases`) |
| Refund account page (`/refund-account#token`, `RefundAccountPage`) | Payer (anonymous, token in the URL fragment) | Submit in progress | No token: page explains the link is missing | Bank, account number and holder name required *(2026-11-15: plus the explicit confirmation step below, proposed, T151)* | 404 unknown token; 410 `REFUND_ACCOUNT_LINK_NOT_USABLE` (used, revoked, expired) | Ask the shop to resend the link; the number is never shown again after submission | The form is replaced by "Thank you. Your bank account was received. We will transfer the refund after it is approved and email you when it is done." | None: the page ends there (the link is single-use) |
| Change requests queue (`/settlement/change-requests`, `ChangeRequestsPage`) | Manager decides; Staff sees their locations | Spinner while the queue loads | "No change requests found." | Decision and reason required | 403; 404; 409 already decided/expired or full quota; 422 | Reload; an expired request shows `EXPIRED` (expiry rule FR-045a; LATE_ARRIVAL: changed session end + 7 days) | "Change approved and applied." or "Change rejected."; the queue is re-read | Stays on `/settlement/change-requests` (reached from the 202 link on `/settlement/ticket-actions` and from `/settlement/cases`) |
| Settlement case list (`/settlement/cases`, `SettlementCasesPage`; fix A21 2026-11-16) | Manager; Staff sees their locations | Spinner while the cases load | "No settlement cases found." | Optional filters by type and status | 403; 404; 422 | Reload; open the case reference to read its detail; the refund-policy panel is removed by T140 | The list of `FOLIO_CORRECTION`, Mland remedy and `ADDON_REFUND` cases; a link leads to `/settlement/change-requests` | Stays on `/settlement/cases` |
| Ticket actions (`/settlement/ticket-actions` with `tab=cancel` or `tab=change`, `TicketActionsPage`) — cancel one or several tickets, change one or several, buyer verification, Manager extra-change grant | Staff/Manager on the buyer's behalf; Manager for the grant | Command in progress | — | Buyer verification reference for Staff on behalf; reason required; target sessions 1–2 per ticket and the matching from-session (T141; 422 if not the ticket's); price increase confirmed before repeating | 403 not buyer / out of scope; 404; 409 with `failingTicketCodes` (group all-or-none), `CHANGE_REQUEST_PENDING`; `BUYER_VERIFICATION_REQUIRED`/`FAILED`/`EXPIRED`; 422 | 202 shows the queued request reference; "Nothing has changed yet" until the buyer confirms an increase; retry with a new key | Cancel: "Cancellation recorded." with each ticket's outcome; change: "Change applied." (200) or "Queued for a Manager decision." (202, request reference shown); grant: "One extra change granted for … (allowance reference). The change itself is still to be made." | Stays on the same tab (`?tab=cancel` / `?tab=change`); a 202 links to `/settlement/change-requests` |
| Mland remedy (`/workshop-tickets/:ticketCode/remedy`, `MlandRemedyPage`) | Buyer (Member by bearer, Guest by buyer verification) | Spinner while the offer loads | No offer for the ticket | One offered selectable session or refund | 403 not buyer; 404; 409 `REPLACEMENT_SESSION_UNAVAILABLE`, `REPLACEMENT_SESSION_NOT_OFFERED`, `ANSWER_DEADLINE_EXPIRED` | Choose another session or refund; an answered offer is read-only | "Your new session is booked." or "Your refund request was recorded."; the offer is re-read and shown answered | Stays on `/workshop-tickets/:ticketCode/remedy`; a refund continues through the account link sent to the payer (`/refund-account#token`) |
| Customer folio (`/workshop-tickets/:ticketCode/folio`, `CustomerFolioPage`) | Member owner (bearer); Guest (`POST …/folio-lookups` with one contact in the body) | Spinner while reading | "No folio was found for this ticket." | One contact (email or phone) for a Guest | Same 404 for a wrong code or contact | Retry the lookup; a Member sees a posted correction read-only | The read-only folio (lines, collections, balance, state) replaces the lookup form | Stays on `/workshop-tickets/:ticketCode/folio` |
| Manager correction panel (`StaffWorkshopAddonPanel` in workshop-operations) | Manager | Command in progress | — | Overcharge amount > 0 and reason | 403; 409 cap or `IDEMPOTENCY_CONFLICT` | Read the correction (`FolioCorrectionLookup`); refund follows the bank refund queue | "Folio correction … created (status). A bank refund request was opened (refund id)."; re-readable through `FolioCorrectionLookup` | Stays in the panel; the refund is followed in `/settlement/refunds` |
| Reconciliation list (`/settlement/reconciliation`, FR-056, **planned**, T134; route fixed 2026-11-15, free in `routes.jsx`) | Manager only | Spinner while listing | "No transfer, VNPay or booking-time collection" for the filters | `locationId` and working date required; optional method (`BANK_TRANSFER`, `VNPAY`, `BOOKING_TIME`; `BOOKING_TIME` added 2026-11-16, A04) and state | 403 Staff; 400 bad filter | Change filters and reload; read-only, no reconciled mark | The rows of `ReconciliationCollection` (200): ticket code, group code, amount, method, state, reference, confirming actor and time. A `BOOKING_TIME` row (online VNPay money paid at booking, read through the new 003.1 facade operation `listBookingTimePayments`, no VNPay call) with an IPN-confirmed payment shows the amount received and counts into the confirmed total; an unverified row (QueryDR not yet run or failed) shows the requested amount with the label "chưa xác minh" and is NOT counted in the confirmed total; a late payment (money received, group not confirmed, awaiting review) shows as pending with the note 'late payment under review' and is not counted; a payment with sessions at several branches is one row listing all its branches (`locationIds`), appears under each of them, and a warning note tells the Manager not to add the per-branch totals (double counting; the whole-system day total counts it once) | Stays on `/settlement/reconciliation`; nothing to act on, no link out |
| Absorbed undercharges (FR-057, **planned**, T136; record/void action on the ticket folio page `/settlement`, list at `/settlement/absorbed-undercharges`; routes fixed 2026-11-15, free in `routes.jsx`) | Manager only | Spinner while listing | No record for the branch and period | Amount > 0 and reason to record; reason to void; branch and from/to dates to list | 403 Staff; 409 folio not `FINALIZED`, already voided, `IDEMPOTENCY_CONFLICT` | Reload; a voided record stays visible, marked `VOIDED`, outside the total | Record (201): the stored record (reference, amount, reason, actor, time); a same-key replay returns it unchanged; void (200): the record shown `VOIDED`; the list shows `totalVnd` of `ACTIVE` records | Record/void stays on `/settlement` with the same ticket; the list stays on `/settlement/absorbed-undercharges` |

Accessibility and browsers for every row: WCAG 2.2 AA and the two latest major
desktop Chrome, Edge and Firefox (spec SC-015; verification task T138).

### Checklist follow-up of 2026-11-15 (screens and states, PROPOSED until approved; tasks T144–T171)

Sources: spec "Checklist follow-up (payment-error-access.md)", FR-036, FR-039,
FR-040, FR-042, FR-043, FR-053; contract names marked proposed. They add to the
rows above and supersede only where stated.

| Screen / state | Actor | Behaviour | Failure and recovery | Task |
| --- | --- | --- | --- | --- |
| Cash reversal entry on the ticket folio (`/settlement`) | Manager only; hidden for Staff | (R17, 2026-11-16: the button, "Reverse cash entry" or "Void", is chosen by the server field `cashDayClosed`, never derived from the date in the browser.) A `CONFIRMED` cash collection of a **closed** cash day (closed = counted amount submitted, including a close awaiting Manager confirmation, R04) shows "Reverse cash entry" (an open day keeps "Void"). A dialog asks for a reason and confirms; the result shows a negative cash entry dated the current working day and the server's new balance; the original collection stays visible. | 409 `FINAL_BILL_NOT_OPEN` (folio `FINALIZED`/`CLOSED`: use the correction and bank refund), `COLLECTION_UNDER_REFUND`, `CASH_DAY_NOT_CLOSED` (use Void), `IDEMPOTENCY_CONFLICT`; 403 Staff; re-read the folio. | T145 |
| Cash close cut-off note (`/settlement/cash-close`) | Manager; Staff of the branch | After "Submit counted amount" the page states that cash recorded from now on belongs to the next working day, even while a difference waits for the Manager. | 422 difference without reason, as before. | T147, T172 (R05, 2026-11-16: the note is built by the new FE task T172) |
| Account entry confirmation step — customer link page (`/refund-account#token`) and counter entry in the refund queue (`/settlement/refunds`) | Payer (link); Manager or assigned Staff (counter) | *(Supersedes the transfer-time holder-name comparison.)* After the form is filled, a review step shows bank, account number and holder name back and an explicit "These details are correct" confirmation is required before submit; the user can go back and edit. No bank-name lookup. The account number is never shown again after submission (mask only). | 422 `ACCOUNT_CONFIRMATION_REQUIRED` keeps the user on the review step with the entered values; 404 unknown token; 410 `REFUND_ACCOUNT_LINK_NOT_USABLE` (used, revoked, expired, or no longer existing): ask the shop to resend the link; 403 Staff not assigned to the ticket's session. | T151, T152 |
| Return to account entry (refund queue) | Manager | On a request in `PENDING_APPROVAL`, `PENDING_TRANSFER` or `PENDING_RECONCILIATION` a Manager sees "Return to account entry" (reason required, confirmation). The row then shows `PENDING_ACCOUNT` with the reason, the masked number only and a note that the earlier approval no longer applies and a new link was sent; a Manager approves again after the payer re-enters. | 409 wrong state; re-read the queue. | T151 |
| Optional transfer receipt image (refund queue, "Record transfer") | Manager (transferring actor) | The bank reference stays required; a second, optional field attaches an image of the receipt, shown only to a Manager and the transferring actor. *(Superseded 2026-11-16, fix A16: the earlier "Open (plan O-1)" text.)* The field is optional; accepted files are JPEG, PNG or WebP of at most 5,000,000 bytes, one image per transfer (decided 2026-11-15, plan O-1, T153). | Validation messages come from the decided limits (T153): a non-image or a file over 5,000,000 bytes is refused with the typed 415/413 message; a storage outage (503) does not block recording the transfer with the bank reference only. | T153, T154 |
| Voucher not restored notice (`TicketActionsPage` cancellation result, `MlandRemedyPage`, customer folio) | Buyer; Staff/Manager acting for the buyer | When the voucher-and-points owner reports reason `EXPIRED`, the result says the voucher had expired so it was not restored (never extended), the money already paid is still refunded by bank, and voucher value is never refunded as money. | None to retry; replays show the same stored outcome. | T163 |
| Refund `PENDING_ACCOUNT` after a failed transfer | Manager; payer | The queue shows the failure reason; the payer receives a fresh account link; no terminal rejection state is shown. | As the account entry rows above. | T146, T151 |

Accessibility and browsers for these rows follow SC-015 (T138, extended by T164).

**QA list verified by T138 (extended by T164, 2026-11-16):** every row of the table above, that is: ticket folio (including the Reverse cash entry dialog, the absorbed-undercharge record and void dialogs, and the booking-time allocation label), cash close (including the cut-off note in both states), refund queue (counter account review step, Return to account entry dialog, optional receipt image upload and view, record transfer with the bank reference only), refund account page (review step and its 404/410/422 states), ticket cancellation/change and the EXPIRED voucher notice, change requests, Mland remedy, customer folio, settlement case list, reconciliation list (`/settlement/reconciliation`, including the 503 and multi-branch warning states) and absorbed undercharges (`/settlement/absorbed-undercharges`). Each is checked against WCAG 2.2 AA on the two latest major desktop Chrome, Edge and Firefox.

### Changes across locations (FR-058–FR-063; rows for T189) — APPROVED 2026-11-17 by Hồ Hoàng Quốc (in session, as proposed)

**Status: APPROVED 2026-11-17 by Hồ Hoàng Quốc in the implementation session, "as proposed" (all three open questions at their defaults; no Figma).** Written from spec FR-058–FR-063 and the contract
(`ChangeOption.locationName`, `SessionSeats.locationName`, Mland `offeredSessions[].locationName`,
`previous-location-add-ons`); not shown in Figma (no node IDs). Approval is the owner's direct answer in the session; it covers
the rows and the proposed copy below. `approval.md` is not edited here.
Existing screens, routes and the existing picker behaviour stay as in the rows above; only what is listed changes.

**Row format (FR-063).** Every offered session shows, in this order and separated by " – " / " · ", the location name,
date and time, seats left and changes left, for example `Mland Hà Nội – Hoàn Kiếm – 09:00 20/11 – còn 3 chỗ – còn 2 lượt đổi`.
A pair row shows the location name once per session with its own numbers (`<Chi nhánh A> 09:00 20/11 (còn 3 chỗ, còn 2 lượt đổi) → <Chi nhánh B> 14:00 20/11 (còn 5 chỗ, còn 1 lượt đổi)`).
A session with no free seat or no quota is not returned by the server, so the UI never invents a row for it. No sorting or
filtering by location in the client. If `locationName` is null the row shows `Chi nhánh <locationReference>` (never an empty
name); the Staff/Manager pages are English (existing convention), the customer pages Vietnamese.

| Screen (route) | Actor | Change (T189) | Loading | Empty | Failure and recovery | Task |
| --- | --- | --- | --- | --- | --- | --- |
| Customer ticket card, "Đổi lịch" picker (`GroupTicketList` → `TicketSessionPicker`) | Buyer (Member bearer; Guest with contact then OTP) | Each option and each pair member shows the location name per the row format; options of several locations are mixed in one list as the server returns them. No "needs a Manager" note appears because of a location (FR-059); the existing note stays for the three Manager rules only. | Existing "Đang tải các buổi có thể đổi…" | Existing "Hiện chưa có buổi nào thay thế được buổi này." (now meaning no location has a free session) | `409 PACKAGE_NOT_AVAILABLE_AT_LOCATION` → message below; the picker is reloaded, the choice cleared, the ticket unchanged and no change is consumed. `409 LATE_ARRIVAL_WINDOW_EXPIRED` → message below; the form stays open with the type "Tôi đến muộn hoặc đã lỡ buổi" and the user may close it; the request is not queued. | T189 |
| Staff ticket actions, tab `change` (`/settlement/ticket-actions?tab=change`, `TicketActionsPage` → `TicketSessionPicker`) | Staff in scope and assigned; Manager | Same row format (English labels: `<location> – <date time> – N seats left – M changes left`). The "added to the change" list also shows the target location name. After a successful cross-location move the page says the ticket now belongs to the new location and the Staff of the old location can no longer open it (FR-060). | Existing spinner text | Existing "No other session can replace this one right now." | Same two codes, shown by the existing `ProblemNotice` with the English text below and the correlation ID; `SESSION_ASSIGNMENT_REQUIRED` stays as today. A group change is all-or-none: `failingTicketCodes` listed as today. | T189 |
| Mland remedy (`/workshop-tickets/:ticketCode/remedy`, `MlandRemedyPage`) | Buyer | Each offered session line shows `<location name> – <date time>` (the offer's `locationName`; for an offer saved before 2026-11-17 `locationName` is null and the line shows the time only, as today). The confirm dialog names the chosen location. No Manager, no change count. | Existing spinner | Existing "no offer" notice; an empty offered list shows only the refund choice | `REPLACEMENT_SESSION_UNAVAILABLE` text widened to cover a session whose location was switched off (below); `LATE_ARRIVAL_WINDOW_EXPIRED` never occurs (Mland exempt, FR-062). | T189 |
| Manager change-request queue (`/settlement/change-requests`, `ChangeRequestsPage`) | Manager; Staff view | **Proposed: no new column.** The `ChangeRequest` contract carries no location, so the queue cannot show one without a backend field. Approval that fails with `PACKAGE_NOT_AVAILABLE_AT_LOCATION` shows the message below, the request stays `PENDING` (unapplied, no allowance used) and the Manager may reject it. `LATE_ARRIVAL_WINDOW_EXPIRED` is not expected here (such a request is never queued). | Existing | Existing | Message below; "Load latest" re-reads the queue. | T189 |
| Add-ons sold at the old location (Manager panel on the ticket folio, `/settlement`, `GET /workshop-tickets/{ticketCode}/folio/previous-location-add-ons`) | Manager only; not requested for Staff or Member | A read-only block "Add-ons sold at the previous location" shown **only when the list is non-empty** (almost always empty): one line per charge with description, amount and both location references. The contract returns location **ids only** (`addOnLocationReference`, `ticketLocationReference`), no names, so the lines read `Sold at location <id> · ticket now at location <id>`. No action buttons (nothing about the add-ons changes, decided Q3/Q4). | "Loading add-ons from the previous location…" (not blocking the folio) | Block not rendered | `403` for a non-Manager: block not rendered, no error shown; `404`/other: small inline notice with a "Try again" button; the folio itself is unaffected. | T189 (after T187) |

**Copy (proposed).**

| Code | Customer (vi) | Staff / Manager (en) |
| --- | --- | --- |
| `PACKAGE_NOT_AVAILABLE_AT_LOCATION` | "Gói này hiện không còn bán tại chi nhánh bạn chọn. Vé của bạn chưa bị thay đổi và chưa mất lượt đổi. Hãy chọn buổi ở chi nhánh khác." | "This package is no longer sold at the chosen location. Nothing was changed and no change was used. Choose a session at another location (for a queued request, reject it)." |
| `LATE_ARRIVAL_WINDOW_EXPIRED` | "Đã quá thời hạn đổi lịch: bạn chỉ có thể đổi đến hết ngày thứ 7 sau ngày buổi học kết thúc (theo ngày ở Việt Nam). Vé của bạn chưa bị thay đổi." | "The change window has closed: a change must land by the end of the 7th calendar day after the replaced session ends (Vietnam date). It cannot be queued or lifted by a Manager." |
| `REPLACEMENT_SESSION_UNAVAILABLE` (Mland, widened) | "Buổi này không còn chỗ hoặc không còn mở bán tại chi nhánh đó. Hãy chọn buổi khác hoặc nhận hoàn tiền." | (customer page only) |

**Accessibility and states.** Location name is part of the option text (a native `<option>` has text only, so no icon or
colour carries it); the two error messages use the existing `role="alert"`. WCAG 2.2 AA and browsers per SC-015 (T138);
T138's QA list gains these five rows.

**Open questions for the approver** (each has the default above if not answered):
(1) change-request queue without a location column (default) or a backend field first; (2) add-on block with location
ids (default) or a backend field with names first; (3) customer wording of the two messages above.

## Session, data handling, and accessibility

- Customer views must expose only their own booking/bill/case data.
- Never display gateway secrets, raw callback payloads, card data, or unredacted PII.
- On payment timeout, keep the bill snapshot and show the limited `UNKNOWN`
  recovery action rather than silently retrying. Do not call it failed or offer
  reissue until server-side reconciliation has produced an eligible outcome.
- Tables, totals, status badges, and confirmation dialogs must be keyboard accessible and expose text equivalents for color/state.

## Open decisions and dependencies

- **DRAFT**: exact Figma revision/node IDs and final customer-facing copy for remedy/case statuses.
- **Dependency**: verified payment/refund evidence remains owned by `paymentintegration`; the UI must consume the settlement facade/contract only.
