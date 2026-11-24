# IV. Proposed Solution {#proposed-solution}

The Personalized Product Sales and Workshop Booking System is a bilingual system with Guest and Member journeys. A customer may discover a workshop, configure a ring, submit a design request, consult Staff, purchase a ready-made ring, or continue an eligible accepted request into a custom-manufacturing order. The system separates customer-facing design configuration from the accountable human review and quotation process.

## 1. Workshop discovery and configuration

Feature-012 lets Guest and Member choose a published workshop package, configure a ring from active catalogue options, preview the result, and save the design. The saved-design page may recommend Silver Clay when the design contains more than five gemstones, Wax Casting when the difficulty score is above nine, or a suitable Signature package otherwise. A Wax Casting recommendation explains that the workshop requires two sessions on different days. Recommendations are advisory only: other workshop links remain visible, and the recommendation does not create a booking, payment, order, or ticket.

## 2. Design request and human review

Feature-004 lets Guest and Member submit a design request containing a description, up to five images, or both. At least one is required. Image quality is checked immediately in the interface and again by the backend; a blurry or failed-quality image blocks submission. Guest requests are limited to five requests per calendar day. A valid request enters `NEED_REVIEW`. Staff or Manager may accept, reject, or request clarification, and every decision requires a non-empty explanation. The owner can view the current status and explanation through a safe projection. Feature-004 does not use AI analysis, AI pricing, automated feasibility decisions, booking attachment, or payment mutation.

## 3. Custom-manufacturing order

An eligible Member may attach an owner-verified Feature-012 design snapshot to a request. After human acceptance, Staff or Manager prepares a quote and the Manager approves the commercial decision. The custom order is shown in the Member's personal order area. It is not added to the retail cart and does not use a deposit or deferred balance. The Member must pay the complete approved quote before its deadline; if verified full payment is not received, the quote/order expires or is cancelled according to the approved policy. Production and fulfilment statuses remain separate from the workshop booking lifecycle.

## 4. Workshop booking, payment, and fulfilment

Workshop booking remains a separate flow with configurable branches and three daily session templates at 09:30–12:00, 13:00–15:30, and 16:00–18:30. A workshop booking uses the approved deposit rule, participant capacity, attendance, check-in, continuation, custody, and settlement process. The workshop deposit must not be confused with the custom-order full-payment flow. Only verified gateway evidence confirms payment; a browser redirect alone is insufficient.

## 5. Ready-made retail and governance

Members may buy published ready-made rings with full online payment before the durable reservation is recorded. Fulfilment stops at pickup or permitted Staff-recorded carrier handoff. Staff and Manager own business decisions; Admin Technical owns technical configuration, credentials, monitoring, and technical audit. Feature-013 staff consultation is a supporting dependency under review, not a Major Feature implementation baseline in this report.
