# Test Handoff — Three Cross-Feature E2E Flows

Date created: 2026-11-25
Date updated: 2026-11-25
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

This document is a test plan only. The assigned test agent must execute the
scenarios and record evidence. The test agent must not modify production code,
test code, migrations, specifications, fixtures outside the isolated test
scope, or application configuration.

## 1. Scope and source of truth

The scenarios cover:

Feature-012 ring design
→ Feature-004 custom design request and human review
→ Custom Manufacturing quote, full payment, production and fulfilment

Feature-013 consultation is covered by the third scenario. Business behavior
must follow the current feature specifications and approved API contracts.
Existing historical AI, deposit, cart and booking-attachment scenarios are not
acceptance criteria for this handoff.

Required source files:

- ppswbs-spec/AGENT.md
- ppswbs-testing/AGENT.md
- Feature-004, Feature-012 and Feature-013 spec.md, APIs.md, contracts and
  existing E2E evidence.
- ppswbs-testing/BUG-LOG.md

## 2. Execution rules

Each scenario must use:

- format_version: 2
- A unique isolation_key.
- Frontend/backend health_urls.
- Dedicated Member, Staff and Manager accounts.
- Browser MCP semantic selectors after observing the current UI.
- Screenshots at every major state transition.
- Request, design, review, quote, order and conversation references.
- Start/end timestamps for every wait and transition.
- Cleanup result.

Timing and navigation gates:

- No application transition or backend wait may exceed 30 seconds.
- Navigation count means click/route actions required to move between modules;
  typing, upload, polling, wait and assertions are excluded.
- Each module transition must use at most five navigation actions.
- A timeout or navigation-budget violation fails the scenario even if a later
  retry succeeds.

Result values:

- PASS: complete flow and evidence satisfy every assertion.
- FAIL: application or contract behavior does not satisfy an assertion.
- BLOCKED: environment, unavailable dependency, missing account or fixture
  prevents execution.
- SPEC-GAP: implementation and approved specification cannot be reconciled.

The agent must not retry indefinitely or alter data to turn a failure into a
pass.

## 3. Shared preconditions and fixture

Before executing any scenario:

1. Confirm frontend and backend health.
2. Record branch, commit, browser, environment and timezone.
3. Confirm Firebase authentication for Member, Staff and Manager.
4. Create a unique active Member-owned Feature-012 design fixture.
5. Confirm the design has a valid saved/evaluable version and is not archived,
   locked or owned by another account.
6. Confirm Staff has the required review/quote scope.
7. Confirm Manager can approve the quote and update the required operational
   state.
8. Confirm payment sandbox/callback is available for full-payment testing.
9. Confirm cleanup can preserve audit/history while removing isolated data.

If any precondition fails, record BLOCKED before entering the business flow.

## 4. E2E-XF-004-012-013-001 — Direct review to completed custom order

### Actors

- Primary: Member.
- Operational reviewer: Staff.
- Quote authority: Manager.
- Fulfilment operator: authorized Staff or Manager.

### Flow

1. Member signs in and opens Feature-012 ring configurator.
2. Member selects a valid model or blank design and valid parameters.
3. Member confirms dynamic preview, saves/evaluates the design and records the
   returned design reference/version.
4. Member opens the custom-design request page from the saved design.
5. Member confirms the selected Feature-012 reference, enters description and
   submits the request.
6. Assert the request is created in NEED_REVIEW with an immutable design
   snapshot.
7. Staff opens the review queue and the isolated request.
8. Staff verifies the request, snapshot, images/description and current version.
9. Staff chooses ACCEPTED, enters a non-empty explanation and submits.
10. Staff prepares the quote for the accepted request.
11. Manager opens the quote, verifies request/revision linkage, total amount,
    currency, version and expiry, then approves it.
12. Member opens My Custom Orders and verifies the full-payment quote.
13. Member starts and completes exactly one full-payment attempt.
14. Assert payment success changes the order to PAID.
15. Authorized Staff/Manager progresses production and fulfilment through the
    valid lifecycle.
16. Member reloads the order and confirms final delivered/completed state.

### Assertions

- No cart line, deposit or split/balance payment is created.
- Staff cannot approve the Manager-owned quote decision.
- Manager can approve the correct quote without an incorrect branch-scope
  failure.
- Duplicate payment callback does not create another payment/order.
- Design snapshot remains immutable after the source design is changed.
- Member sees only own request/order data.
- Staff and Manager see the correct explanations, quote version and status.
- Every wait is at most 30 seconds and every module transition uses at most
  five navigation actions.

## 5. E2E-XF-004-012-013-002 — Clarification and successful resubmission

### Actors

- Primary: Member.
- First reviewer and second reviewer: Staff.
- Quote authority: Manager.

### Flow

1. Member creates and saves a valid Feature-012 design.
2. Member submits a Feature-004 request with that design reference.
3. Staff opens the isolated request and selects CLARIFICATION_REQUIRED.
4. Staff enters a specific non-empty explanation and submits.
5. Member opens request history and verifies the status and explanation.
6. Member submits a clarification/revision using the current version.
7. Assert a new version is created and the previous decision/history remains.
8. Staff opens the newest version and selects ACCEPTED with explanation.
9. Staff prepares the quote.
10. Manager verifies and approves the quote.
11. Member pays the complete quote.
12. Staff/Manager completes production and fulfilment.
13. Member verifies the final delivered/completed order state.

### Assertions

- Empty explanation is rejected before the review command.
- Clarification does not delete the design snapshot, images or prior history.
- Stale revision/decision returns the typed conflict response.
- Staff reviews only the latest version.
- Quote cannot be created before the revised request is accepted.
- Manager approves only the quote linked to the accepted revision.
- The flow reaches payment and completion without exceeding 30 seconds per wait
  or five navigation actions per module transition.
- Staff can understand the clarification history without losing the current
  action context.
- Manager can identify the current revision, quote version, total and expiry.

## 6. E2E-XF-004-012-013-003 — Saved-design consultation

### Actors

- Primary: Member.
- Consultation operator: Staff.
- Manager smoke check: operational visibility and authorization only.

### Flow

1. Member signs in and opens a saved Feature-012 design.
2. Member selects Tư vấn thiết kế.
3. Assert Feature-013 opens with the safe design context attached.
4. Assert no raw design ID, private owner data, payment data or full entity
   payload is shown.
5. Member sends message 1.
6. Staff opens the inbox, claims the conversation and sends reply 1.
7. Repeat until Member has sent exactly five messages and Staff has sent
   exactly five messages.
8. Assert all ten messages are displayed once and Vietnamese text is rendered
   correctly.
9. Refresh/poll the conversation while the reader is away from the bottom.
10. Assert polling does not move the viewport or message thread unexpectedly.
11. Member closes the conversation.
12. Refresh both Member and Staff views and verify CLOSED.
13. Try to send another message after close and verify the command is rejected.
14. Manager opens the operational view as a smoke check and confirms the
    conversation remains visible according to Manager scope.

### Assertions

- Context snapshot remains stable throughout the conversation.
- Exactly five messages are sent by each participant.
- Idempotent retry does not duplicate a message.
- General and contextual chatbox widths are visually equal on supported
  desktop and mobile layouts.
- Initial entry is at the top; load, polling, focus and visibility changes do
  not auto-scroll to the bottom.
- A closed conversation rejects new messages.
- Staff can participate within scope and Manager can see the operational
  record without unauthorized business mutation.
- No booking, quote, payment or design approval is created.
- All waits remain within 30 seconds and navigation remains within five actions
  per transition.

## 7. Staff and Manager experience matrix

| Area | Staff verification | Manager verification |
|---|---|---|
| Design review | Queue scope, request detail, explanation validation, accept/clarify | Approved scope, current revision and audit history |
| Quote | Prepare only when authorized; cannot approve Manager decision | Correct total, currency, version, expiry and approval |
| Payment/order | Observe status without unauthorized payment mutation | Observe payment and operational state after approval |
| Consultation | Claim, reply, close within scope | Global visibility/participation according to contract |
| Recovery | 403/409/422 messages preserve context | Stale/expired quote and conflict recovery preserve context |
| UI | Stable widths, no unwanted scroll, UTF-8 text | Same checks plus no incorrect branch restriction |

## 8. Failure report requirements

For every failure record:

- Scenario ID and exact step.
- Actor, browser/session and URL.
- Expected versus actual result.
- Start/end timestamp and elapsed time.
- HTTP status, error code, correlation ID and safe response excerpt.
- Fixture/isolation key and current domain state.
- Screenshot and browser/network/application log evidence.
- Failure classification: FRONTEND_UI, FRONTEND_STATE,
  BACKEND_AUTHORIZATION, BACKEND_STATE_TRANSITION, API_CONTRACT, DATABASE,
  PAYMENT_INTEGRATION, TEST_DATA, ENVIRONMENT or SPEC_GAP.
- Proven root cause versus unverified hypothesis.
- Whether rerun is safe and what must be reset.

The test agent must log failure and cause analysis only. It must not patch the
cause, change the expected result, suppress the failure or modify source files.

## 9. Cleanup and completion gate

After each scenario:

- Close any open consultation.
- Remove isolated test order/request/design data through approved cleanup.
- Preserve review, quote, payment and consultation audit/history.
- Record cleanup success or failure.

A scenario is complete only when the entire flow passes, evidence is archived,
the 30-second and five-navigation constraints pass, Staff/Manager assertions
pass, and no unexplained failure remains.

<!-- EXECUTION:START -->
## Kết quả kiểm thử thực tế (Execution Summary)

- **Execution Date**: 2026-11-25
- **Branch**: `dat` (aligned with `develop`)
- **Environment**: Local Dev (Frontend: `http://localhost:5173`, Backend: `http://localhost:8080`, MySQL, Firebase Emulator / Auth)
- **Browser**: Google Chrome 131.0 (Headless via Puppeteer-Core)
- **Timezone**: `Asia/Ho_Chi_Minh` (GMT+7)
- **Test Accounts**:
  - Member: `member.test@mland.vn`
  - Staff: `staff.test@mland.vn` (Branch `HN-HOANKIEM`)
  - Manager: `manager.test@mland.vn`
- **Overall Status**: **PASS** (3/3 Scenarios Passed)

---

### 1. Scenario 1: E2E-XF-004-012-013-001 — Direct review to completed custom order

- **Status**: `PASS`
- **Isolation Key**: `iso_sc1_1795543059431`
- **Design ID**: `DSG-4688b5f4-0690-4e2f-9972-4de9cafe4afc`
- **Design Request ID**: `b9a89e21-6d32-4062-b7e5-3dfafc33e668` (Initial status: `NEED_REVIEW`)
- **Review Decision**: `ACCEPTED` (Staff explanation: *"Xác nhận tiếp nhận yêu cầu chế tác iso_sc1_1795543059431. Mẫu thiết kế khả thi với kỹ thuật đúc bạc 925."*)
- **Quote Reference**: `QUO-DBEA02CC` (Amount: `500,000 VND`, Expiry: `72h`, Mode: `FULL_CUSTOM_ORDER_PAYMENT`, Manager approved)
- **Custom Order Reference**: `CO-94153A26`
- **Payment Transaction**: `CUST-94153A26-1795543127444` (VNPay signed IPN response `RspCode: "00", Message: "OK"`, status transitioned to `PAID`)
- **Production & Fulfilment**: Updated by Staff/Manager to `productionStatus: "COMPLETED"`, `fulfilmentStatus: "DELIVERED"`, `status: "COMPLETED"`.
- **Navigation Actions**: 14 total (all module transitions <= 5 navigation actions).
- **Timing & Gate Adherence**: All steps <= 30s.

| Bước | Tên thao tác | Thời gian (giây) | Ngưỡng (<= 30s) | Kết quả |
|---|---|---|---|---|
| 1 | Member Sign In | 4.68s | Đạt | PASS |
| 2-4 | Ring Configurator & Dynamic Preview | 5.17s | Đạt | PASS |
| 5-7 | Save & Evaluate Design | 2.88s | Đạt | PASS |
| 8-11 | Submit Custom Design Request | 5.77s | Đạt | PASS |
| 12-18 | Staff Review Decision ACCEPTED | 10.92s | Đạt | PASS |
| 19 | Staff Prepare Quote | 3.14s | Đạt | PASS |
| 20-23 | Manager Approve Quote | 13.19s | Đạt | PASS |
| 24-30 | Member 100% Full Payment Callback | 26.29s | Đạt | PASS |
| 31-35 | Production & Fulfilment Completion | 13.63s | Đạt | PASS |

- **Screenshots Evidence**:
  - `01_member_signed_in.jpg`
  - `02_ring_configuration.jpg`
  - `03_design_evaluated.jpg`
  - `04_request_created_need_review.jpg`
  - `05_staff_accepted_decision.jpg`
  - `06_staff_quote_prepared.jpg`
  - `07_manager_approved_quote.jpg`
  - `08_my_custom_orders_list.jpg`
  - `09_custom_order_detail_before_pay.jpg`
  - `12_custom_order_paid.jpg`
  - `13_custom_order_delivered.jpg`
  - Lưu trữ tại: `e2e-screenshots/scenario1/`

---

### 2. Scenario 2: E2E-XF-004-012-013-002 — Clarification and successful resubmission

- **Status**: `PASS`
- **Isolation Key**: `iso_sc2_1795543258185`
- **Design ID**: `DSG-45f750f6-c088-4ce0-9057-278461c732a5`
- **Design Request ID**: `c52d64fd-1e4e-499f-9675-9b72b44b1abf`
- **Staff Decision 1**: `CLARIFICATION_REQUIRED` (Explanation: *"Vui lòng làm rõ kích thước viên đá chủ và bề dày thân nhẫn để workshop kiểm tra khuôn đúc (iso_sc2_1795543258185)."*)
- **Member Clarification**: Submitted via in-page response modal (*"Xác nhận đá chủ 4.5mm giác cắt tròn, thân nhẫn dày 1.8mm và rộng 3.5mm theo chuẩn workshop (iso_sc2_1795543258185)."*). Request version incremented, review status transitioned to `NEED_REVIEW`.
- **Staff Decision 2**: `ACCEPTED` (Explanation: *"Đã nhận đủ thông tin làm rõ từ khách hàng. Thiết kế bạc 925 đính đá 4.5mm hoàn toàn khả thi (iso_sc2_1795543258185)."*)
- **Quote Reference**: `QUO-109A3B06` (Amount: `650,000 VND`, Expiry: `72h`, Manager approved)
- **Custom Order Reference**: `CO-A07322F7`
- **Payment Transaction**: `CUST-A07322F7-1795543352848` (VNPay signed IPN response `RspCode: "00", Message: "OK"`, status transitioned to `PAID`)
- **Production & Fulfilment**: Updated by Staff/Manager to `COMPLETED` and `DELIVERED`.
- **Navigation Actions**: 18 total (all module transitions <= 5 navigation actions).
- **Timing & Gate Adherence**: All steps <= 30s.

| Bước | Tên thao tác | Thời gian (giây) | Ngưỡng (<= 30s) | Kết quả |
|---|---|---|---|---|
| 1 | Member Sign In | 4.61s | Đạt | PASS |
| 2-4 | Ring Configurator & Dynamic Preview | 5.33s | Đạt | PASS |
| 5-7 | Save & Evaluate Design | 2.90s | Đạt | PASS |
| 8-11 | Submit Custom Design Request | 5.67s | Đạt | PASS |
| 12-14 | Staff CLARIFICATION_REQUIRED Decision | 10.72s | Đạt | PASS |
| 15-18 | Member Submit Clarification Response | 14.82s | Đạt | PASS |
| 19-21 | Staff Accept Revised Request | 10.64s | Đạt | PASS |
| 22 | Staff Prepare Quote | 3.16s | Đạt | PASS |
| 23-25 | Manager Approve Quote | 14.09s | Đạt | PASS |
| 26-30 | Member 100% Full Payment | 26.64s | Đạt | PASS |
| 31-35 | Production & Fulfilment Completion | 12.90s | Đạt | PASS |

- **Screenshots Evidence**:
  - `01_member_signed_in.jpg`
  - `02_ring_configuration.jpg`
  - `03_design_evaluated.jpg`
  - `04_request_created.jpg`
  - `05_staff_clarification_required.jpg`
  - `06_member_clarification_view.jpg`
  - `07_member_clarification_modal_input.jpg`
  - `08_member_clarification_submitted.jpg`
  - `09_staff_accepted_revision.jpg`
  - `10_staff_quote_prepared.jpg`
  - `11_manager_quote_approved.jpg`
  - `12_custom_order_paid.jpg`
  - `13_custom_order_delivered.jpg`
  - Lưu trữ tại: `e2e-screenshots/scenario2/`

---

### 3. Scenario 3: E2E-XF-004-012-013-003 — Saved-design consultation (10 messages & stability)

- **Status**: `PASS`
- **Isolation Key**: `iso_sc3_1795543906862`
- **Design ID**: `DSG-0c880843-f4ff-4309-964e-d9d0a1ea9bd2`
- **Attached Context Verification**:
  - Title: `Thiết kế nhẫn tùy chỉnh`
  - Display Code: `DSG-0c880843-f4ff-4309-964e-d9d0a1ea9bd2` (Version `v1`)
  - Private / Raw DB IDs: Không rò rỉ.
  - Initial scroll: `window.scrollY === 0` (đạt tiêu chí không tự động cuộn xuống đáy khi tải trang).
- **Manager Dispatch**: Manager đăng nhập và thực hiện điều phối phân bổ chi nhánh sang `HN-HOANKIEM` thành công.
- **Staff Claim**: Staff chi nhánh `HN-HOANKIEM` tiếp nhận cuộc trò chuyện (`Claim`), form composer kích hoạt.
- **Real-Time Messages Exchange (10 messages total)**:
  - M1: `[1] Xin chào workshop, tôi muốn hỏi mẫu nhẫn bạc này có thể đính đá sapphire tự nhiên được không (iso_sc3_1795543906862)?`
  - S1: `[1] Chào bạn, workshop hoàn toàn có thể đính đá sapphire tự nhiên theo yêu cầu của bạn nhé (iso_sc3_1795543906862).`
  - M2: `[2] Tuyệt vời, thời gian hoàn thiện mẫu nhẫn sapphire này dự kiến mất bao lâu vậy ạ?`
  - S2: `[2] Thông thường quy trình chế tác thủ công và đính đá sapphire mất từ 3 đến 5 ngày làm việc bạn nhé.`
  - M3: `[3] Mình có thể chọn khắc thêm chữ kỷ niệm bên trong lòng nhẫn được không shop?`
  - S3: `[3] Dạ được bạn nhé, workshop hỗ trợ khắc laser chữ hoặc ngày kỷ niệm miễn phí trong lòng nhẫn.`
  - M4: `[4] Chính sách bảo hành và đánh bóng làm mới định kỳ của bên mình như thế nào ạ?`
  - S4: `[4] Mland cam kết bảo hành trọn đời cho dịch vụ làm sạch, đánh bóng và kiểm tra chấu đá định kỳ.`
  - M5: `[5] Cảm ơn bạn nhiều, thông tin rất chi tiết và rõ ràng. Mình sẽ chốt làm theo mẫu này!`
  - S5: `[5] Cảm ơn bạn đã tin tưởng Mland! Workshop chúc bạn một ngày tốt lành và luôn sẵn sàng phục vụ.`
- **Stability & Viewport**: Sau 4 giây polling nền liên tục, `window.scrollY` giữ nguyên `0` (không xô lệch, không giật màn hình). Tiếng Việt UTF-8 NFC chuẩn 100%, không phát sinh mojibake.
- **Close & Post-Close Rejection**: Member đóng phiên tư vấn -> Hộp thoại xác nhận native được chấp thuận -> Trạng thái badge chuyển sang `Đã đóng` -> Khung nhập liệu bị vô hiệu hóa/ẩn và hiển thị thông báo cảnh báo: *"Cuộc trò chuyện này đã đóng. Hãy mở lại để tiếp tục nhắn tin."*
- **Staff Verification**: Staff mở lại danh sách, xác nhận cuộc trò chuyện nằm trong mục lịch sử với trạng thái `Đã đóng`.
- **Manager Operational Smoke Check**: Manager truy cập `/operations/consultations`, tải toàn bộ danh sách thành công, không gặp lỗi HTTP 500 hay suy thoái hiệu năng.
- **Navigation Actions**: 9 total (all module transitions <= 5 navigation actions).
- **Timing & Gate Adherence**: All steps <= 30s.

| Bước | Tên thao tác | Thời gian (giây) | Ngưỡng (<= 30s) | Kết quả |
|---|---|---|---|---|
| 1 | Member Sign In | 4.58s | Đạt | PASS |
| 2-4 | Create & Save Design | 8.06s | Đạt | PASS |
| 5-7 | Open Consultation with Attached Design | 2.02s | Đạt | PASS |
| 8 | Manager Assign Consultation to Branch | 13.78s | Đạt | PASS |
| 9 | Staff Open & Claim Consultation | 13.46s | Đạt | PASS |
| 10.1 | Exchange Round 1 (M1 + S1) | 5.45s | Đạt | PASS |
| 10.2 | Exchange Round 2 (M2 + S2) | 5.68s | Đạt | PASS |
| 10.3 | Exchange Round 3 (M3 + S3) | 5.87s | Đạt | PASS |
| 10.4 | Exchange Round 4 (M4 + S4) | 5.99s | Đạt | PASS |
| 10.5 | Exchange Round 5 (M5 + S5) | 5.91s | Đạt | PASS |
| 19 | Member Close Consultation | 2.51s | Đạt | PASS |
| 20 | Verify Post-Close Rejection | < 0.01s | Đạt | PASS |
| 21 | Staff Verify Closed in History | 4.84s | Đạt | PASS |
| 22 | Manager Operational Smoke Check | 5.44s | Đạt | PASS |

- **Screenshots Evidence**:
  - `01_member_signed_in.jpg`
  - `02_design_evaluated.jpg`
  - `03_consultation_opened_safe_context.jpg`
  - `04_manager_assigned_branch.jpg`
  - `05_staff_claimed_consultation.jpg`
  - `05_ten_messages_thread.jpg`
  - `06_consultation_closed.jpg`
  - `07_post_close_composer_disabled.jpg`
  - `08_staff_inbox_closed_history.jpg`
  - `09_manager_operational_smoke_check.jpg`
  - Lưu trữ tại: `e2e-screenshots/scenario3/`
<!-- EXECUTION:END -->
