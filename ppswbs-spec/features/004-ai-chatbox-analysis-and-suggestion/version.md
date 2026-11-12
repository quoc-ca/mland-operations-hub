# Version 1.5 — Feature 004: AI Chatbox Analysis and Suggestion (JDK 21 Verification, Contract Audit & 7 Blockers Resolution Plan)

- **Feature**: 004 — AI Chatbox Analysis and Suggestion
- **Author**: Ngô Tiến Đạt
- **Last updated by**: Ngô Tiến Đạt
- **Date created**: 2026-10-24
- **Date updated**: 2026-11-12
- **Source of truth**: Feature-004 `spec.md`, `design.md`, OpenAPI contract and `FE-004-DESIGN-INDEPENDENT-HANDOFF.md`

> This file is an implementation status index. Historical audit details are
> condensed here; detailed requirements and design remain in the feature
> artifacts. Phase labels are retained for tracing and audit.

---

## 1. Completed

### Phase A — Contract and boundary

- Feature-004 was rebased from a booking-dependent design flow to an independent Design Workspace.
- The Feature-004 artifacts, `ppswbs-spec/APIs.md`, `spec-general.md`, relevant Report 3 sections and `documents/PROJECT-STATUS.md` were aligned with the independent boundary.
- The independent flow has no booking, location, session, payment, deposit, capacity, ticket or check-in prerequisite or side effect.
- `FE-003` does not call `FE-004` and does not store `designApprovalReference`.

### Phase B — Independent aggregate and persistence

- Replaced the booking-specific request model with `DesignRequest` and `design_requests`.
- Removed booking fields and booking foreign-key assumptions from Feature-004 persistence.
- Refactored consent, analysis, review and image-reference records to use independent Design Request ownership/version data. The current V62 migration still evolves the pre-existing `image_consents` table; this must be verified during Flyway upgrade testing.
- Added append-only migration [`V62__independent_design_request.sql`](../../../ppswbs_backend/src/main/resources/db/migration/V62__independent_design_request.sql). Loyalty/Feature-006 retains the canonical V12 migration, settlement retains V60 and OTA retains V61.
- Preserved the separation between technical analysis state (`PENDING → PROCESSING → SUCCEEDED / FAILED`) and business review state (`NEED_REVIEW → ACCEPTED / REJECTED / CLARIFICATION_REQUIRED`).

### Phase C — Application services, security and authorization

- Removed Feature-004 calls to `WorkshopBookingFacade` and `getBookingEligibilityForDesign(...)`.
- Preserved the required validation and security rules: 2,000-character Basic AI prompt limit, current 4,000-character Design Request description limit, 10 MB/4096×4096 image limit, prompt-injection screening, structured provider validation, atomic three-attempts-in-five-minutes image rate limiting, idempotent retry and temporary image deletion.
- Preserved the authorization boundary:
  - Guest: Basic AI Support only; Guest Design Request creation is denied;
  - Member: own Design Request data under the approved ownership policy;
  - Staff/Manager: review only within approved scope;
  - Manager: business price decision;
  - Admin: technical/audit evidence only;
  - AI Provider and Cloud Storage: no Mland business authority.

### Phase D — API and backend implementation

- Implemented independent REST endpoints for AI support, Design Request creation/read, review queue/decision and price suggestion.
- Removed `bookingCode` from the Design Request controller contract.
- Retired `BookingDesignRequest` and its booking-specific repository.
- Added safe projections; raw images, provider payloads, credentials and unnecessary PII are not returned.
- Replaced the legacy booking review action with the independent `DESIGN_ACCEPTED` action.

### Phase E — Backend verification evidence (Historical)

The following results are historical evidence recorded from the implementation run on 2026-10-29. They must be reproduced after the final commit before release. A rerun during this documentation update was attempted but the Maven PowerShell wrapper failed before Maven started (`Cannot index into a null array`), so no new pass claim is made.

- Spring Modulith verification: **2 tests passed**.
- Feature-004 integration suite: **44/44 tests passed**.
- Coverage included Guest/Member access, invalid tokens, prompt injection, consent, image validation, idempotency, rate limiting, ownership isolation, human review conflicts and price-suggestion permissions.
- No Feature-004 production code imports the legacy Workshop Booking package or calls the booking eligibility facade.

### Phase F — Frontend React + Vite Implementation & Test Verification (2026-11-01)

- Built the complete standalone `F04-DESIGN-WORKSPACE` inside `ppswbs_frontend/src/features/designreview/`; fully independent of workshop booking, payment, session, ticket, and capacity hold flows.
- Implemented modules following project frontend guidelines (`AGENT.md`, React 19, react-router 7, Vitest):
  - `rules.js`: Limits (prompt 2,000, description 4,000, reason 2,000, image 10 MB, dimensions 4096×4096), allowed MIME types (`image/jpeg`, `image/png`, `image/webp`), consent purpose (`INDEPENDENT_DESIGN_IMAGE_ANALYSIS`), consent version (`v1.0`), currency (VND) and date formatters.
  - `messages.js`: Complete bilingual (Vietnamese / English) dictionary covering all screen copy, fields, advisory badges, and typed backend error codes (`AI_INPUT_INVALID`, `IMAGE_RATE_LIMITED`, `SELF_REVIEW_NOT_ALLOWED`, `STAFF_LIMIT_EXCEEDED`, `AI_PROMPT_INJECTION_DETECTED`, `AUTHENTICATION_REQUIRED`, `MEMBER_INACTIVE_OR_INVALID`, etc.).
  - `api/designReviewApi.js`: API client consuming the backend REST controller contract (`POST /api/v1/ai-support/messages`, multipart `POST /api/v1/design-requests` with `Idempotency-Key`, `GET /api/v1/design-requests/:id`, `GET /api/v1/design-review-requests`, `POST /api/v1/design-review-requests/:id/decisions`, `POST /api/v1/price-proposals/:id/ai-suggestions?targetType=...`, `GET /api/v1/price-proposals/:id/ai-suggestions`, `POST /api/v1/price-proposals/:id/ai-suggestions/decisions`).
  - `api/useDesignReviewApi.js`: Session-bound hook utilizing shared `useHttp()`.
  - `public/AiSupportPage.jsx` (`F04-AI-CHAT` - `/ai-support`): Basic AI Support for Guest and Member, 2,000-character limit counter, advisory disclaimer badge, chat bubble stream, error recovery and idempotent retry.
  - `workspace/DesignWorkspacePage.jsx` (`F04-DESIGN-WORKSPACE`, `F04-IMAGE-UPLOAD`, `F04-CONSENT` - `/design-requests/new`): Member design request submission with independent boundary banner, up to 4,000-character description, optional catalogue reference, client-side image validation (MIME, 10 MB limit), affirmative consent requirement, multipart submission with `Idempotency-Key`, rate limit (429) & error recovery preserving user form inputs.
  - `workspace/DesignDetailPage.jsx` (`F04-ANALYSIS-STATUS`, `F04-RESULT` - `/design-requests/:designRequestId`): Displays safe extracted candidate features (style, material, gemstone, structure, dimensions, preliminary estimated value), image quality indicators, model confidence, technical explanation, review status, and next action. Never exposes raw provider payload or raw image.
  - `review/ReviewQueuePage.jsx` (`F04-REVIEW-QUEUE` - `/design-review-requests`): Staff/Manager review queue with responsive table, version, status, and navigation to review detail.
  - `review/ReviewDetailPage.jsx` (`F04-REVIEW-DETAIL` - `/design-review-requests/:reviewRequestId`): Reviewer interface with extracted features, decision controls (`ACCEPTED`, `REJECTED`, `CLARIFICATION_REQUIRED`), reason validation (1–2,000 chars), optimistic `designVersion`, `ConfirmDialog` modal, and error recovery for self-review (403) and staff limit exceeded (403).
  - `pricing/PriceProposalAiPage.jsx` (`F04-PRICE-PROPOSAL`, `F04-ADMIN-AUDIT` - `/price-proposals/:proposalId/ai-suggestions`):
    - Manager view: Displays suggested price in VND, advisory note, re-request button, decision form (`ACCEPTED`, `ADJUSTED`, `REJECTED`, `MANUAL_FALLBACK`), final price input, note, and catalogue workflow reminder.
    - Admin view (`technicalAuditEvidenceOnly === true`): Displays technical audit evidence only (provider correlation ID, timestamps, status); strictly hides commercial pricing fields according to separation of duties.
  - `routes.jsx`: Registered all 6 feature routes with `RequireSession` guards where appropriate.
  - `src/app/router.jsx`: Integrated into main app router.
  - `src/app/AppLayout.jsx`: Added main navigation links for AI Support (`/ai-support`) and AI Design (`/design-requests/new`).
- Automated verification evidence:
  - Command: `npx vitest run src/features/designreview`
  - Results: **2/2 test files passed, 21/21 tests passed (100%)**.
    - `test/designReviewApi.test.js`: 8/8 tests passed (API endpoints, FormData, Idempotency-Key, query parameters, auth flags).
    - `test/screens.test.jsx`: 13/13 tests passed (Guest chat, 503 retry, session requirement, boundary notice, empty validation, image consent, 429 rate limit recovery, safe candidate projections, review queue, review decision submission, self-review 403 error, Manager price proposal, Admin audit projection).
  - Repository regression check: `npx vitest run src/features` — **6/6 test files passed, 68/68 tests passed (100%)**.
  - Production build: `npm run build` — **128 modules transformed, build passed in 2.04s**.

### Phase G — JDK 21 Backend Verification, Architecture Test & Fresh Test Evidence (2026-11-05)

- **JDK 21 Environment Verification**:
  - Environment: OpenJDK 21.0.7 LTS (Microsoft-11369940, 64-Bit Server VM).
  - Compilation: Full project clean build and `testCompile` of 1,046 source files and 283 test classes passed without error.
  - Resolved Spring context constructor ambiguity: Sửa đổi các service `ReviewEligibilityService`, `ReviewModerationService`, `ReviewReplyService` bằng việc bổ sung `@Autowired` rõ ràng cho constructor chính, loại bỏ xung đột với constructor `@Deprecated` cũ gây lỗi `UnsatisfiedDependencyException / No default constructor found`.
  - Configured test DDL auto: Cấu hình `spring.jpa.generate-ddl=true` và `spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop` trong `application-test.properties` để hỗ trợ H2 in-memory test database.
- **Verification Evidence (Fresh Execution on JDK 21)**:
  - Spring Modulith Architecture Tests: **5/5 tests passed (100%)** (`ModulithArchitectureTests.java`).
  - Feature 004 Integration Suite: **44/44 tests passed (100%)**:
    - `AiSupportIT.java`: 9/9 tests passed (Guest/Member public chat, workshop/store/FAQ scopes, prompt injection rejection, rate limit, token validation).
    - `DataRetentionIT.java`: passed (30-day retention cleanup).
    - `DesignRequestIT.java`: passed (Multipart submission, description/image validation, consent, ownership).
    - `HumanReviewIT.java`: passed (Review queue, reviewer authority, self-review 403, staff limit 3M VND).
    - `PriceProposalIT.java`: 15/15 tests passed (Manager price suggestions, decision lifecycle, Admin technical audit projection).
  - Frontend Vitest Suite: **21/21 tests passed (100%)** (`npx vitest run src/features/designreview`).

### Phase H — Full Backend DTO Standardization according to OpenAPI & Frontend Synchronization (2026-11-06)

- **Backend DTO & Controller Standardization (100% OpenAPI Compliance)**:
  - `AiSupportResponse`: Chuẩn hóa thuộc tính `answer`, `advisory` (mặc định `true`), `scope`, `correlationId`, kèm backward-compatible getter/setter cho `message` và `providerCorrelationId`.
  - `ReviewQueueDto`: Thêm DTO wrapper bao bọc envelope `{ items: List<DesignRequestViewDto> }` thay cho mảng phẳng thô, tuân thủ 100% schema `ReviewQueueResponse` trong OpenAPI.
  - `ReviewDecisionRequest`: Chuẩn hóa field `requestVersion` và ánh xạ alias `@JsonAlias({"designVersion", "requestVersion"})` hỗ trợ cả 2 tên trường.
  - `DesignRequestViewDto`: Bổ sung cấu trúc `safeResult` bao bọc an toàn các trường trích xuất (`candidateFeatures`, `imageQuality`, `confidence`, `safeExplanation`).
  - `PriceProposalController`: Nhận payload request JSON `PriceSuggestionRequest` (hoặc chuỗi JSON context) và chuẩn hóa mã trạng thái HTTP trả về thành `202 Accepted` (`HttpStatus.ACCEPTED`).
- **Backend Test Updates & Fresh Evidence**:
  - `HumanReviewIT.java`: Cập nhật assertion hàng đợi từ `$[?(@.id == ...)]` sang `$.items[?(@.id == ...)]`.
  - `PriceProposalIT.java`: Cập nhật 9 assertions từ `status().isOk()` sang `status().isAccepted()`.
  - Kết quả kiểm thử: **44/44 backend integration tests passed (100%)** trên JDK 21.0.7 (`mvnw.cmd test -Dtest="edu.fpt.sep490_g22.ppswbs_backend.designreview.*IT"` - Total time: 59.3s).
- **Frontend Synchronization**:
  - `api/designReviewApi.js`: Chuẩn hóa `recordReviewDecision` gửi `requestVersion` (kèm `designVersion` dự phòng); `requestPriceSuggestion` gửi JSON payload chuẩn.
  - `review/ReviewQueuePage.jsx`: Hỗ trợ đọc hàng đợi linh hoạt từ envelope `{ items }` lẫn fallback mảng trực tiếp `data?.items || data || []`.
  - `public/AiSupportPage.jsx`: Đọc song song `result.answer || result.message` và `result.correlationId || result.providerCorrelationId`.
  - `workspace/DesignDetailPage.jsx`: Trích xuất thông tin an toàn từ `data.safeResult` hoặc fallback trường phẳng.
  - `test/screens.test.jsx` & `test/designReviewApi.test.js`: Cập nhật assertions kiểm tra envelope `{ items }`, `requestVersion`, `answer`.
  - Kết quả kiểm thử Frontend: **21/21 unit/component tests passed (100%)** (`npx.cmd vitest run src/features/designreview`).
  - Production Build: `npm.cmd run build` hoàn thành trong **2.51s**, không có bất kỳ lỗi biên dịch hay cảnh báo cú pháp nào.


---

## 2. Incomplete / Next steps

### Giai đoạn 1: Frontend React + Vite (Đã hoàn thành)

- [x] Build the standalone `F04-DESIGN-WORKSPACE`; do not place it inside the booking flow.
- [x] Implement Basic AI Chat, Design Request description/image submission, consent and validation states, processing/result screens, owner-safe result view, Staff/Manager review queue, Manager price suggestion and Admin technical/audit projection.
- [x] Cover loading, empty, denied, validation, timeout, rate-limit, stale-version, conflict and retry states.
- [x] Do not add package, location/session, booking summary, payment or ticket screens to Feature-004.
- [x] Automated test suite: 21/21 tests passed (Vitest), production build passed in 2.04s.

### Giai đoạn 2: Gemini production integration and prompt evaluation

- Confirm the production Gemini model, quota, billing, timeout, retry and circuit-breaker policy.
- Provide production credentials only through the approved secret/configuration channel.
- Evaluate representative ring images and Vietnamese prompt-injection cases.
- Keep provider output advisory and schema-validated; it must never publish prices or make business decisions.

### Giai đoạn 3: Operations, retention and notifications

- Record the implemented policy explicitly: Guest Design Request creation is currently denied; only an active Member may submit; at least a description or image is required; text-only requests are accepted; image requests require consent; every created request enters human review. Revision/withdrawal/approval expiry remain incomplete because no corresponding endpoint/state transition is implemented.
- Confirm whether the current 4,000-character Design Request description limit and deterministic text-only estimate are approved product behavior or must be changed to the spec baseline.
- Decide whether review-result delivery uses polling, email, SSE, WebSocket or another approved notification boundary.
- Verify the scheduled retention cleanup in the target deployment environment.

### Giai đoạn 4: E2E, migration and release readiness

- Add React E2E coverage for the standalone Design Workspace.
- Resolve the repository-wide Flyway numbering conflict: duplicate V6, V7 and V9 ownership.
- Run clean-install and upgrade-path Flyway tests on MySQL.
- Reproduce compile, Modulith and Feature-004 test results from the final commit.
- Review and commit the complete backend/frontend change set together.
- Add operational handoff documentation for Staff and Manager users.

### Phase A — Remaining contract gate

- Update Feature-004 artifacts after the five policy decisions above are approved.
- Do not infer Guest access, review thresholds, retention or revision behavior from the old booking implementation.

### Phase B — Remaining migration gate

- Linearize all repository Flyway migrations without editing already-applied migrations.
- Verify upgrade compatibility and preserve existing data before claiming database readiness.
- Before applying V62 to an existing database, inspect `flyway_schema_history` for versions `12`, `60`, `61` and `62`. A database with only Loyalty V12, settlement V60 and OTA V61 may apply V62 normally; a database with Feature-004 recorded as V12, V60 or V61 requires a separate history-recovery decision and must not be repaired implicitly.

### Phase C — Remaining implementation gate

- Implement only behavior approved by the final ownership, review and retention policy.
- Keep Account Management access facade-only and preserve separation of duties.

### Phase D — Remaining frontend gate

- Implement the screens and states in `design.md` against the independent API contract.
- Do not reconnect Feature-004 to Workshop Booking or Payment.

### Phase E — Remaining verification gate

- Re-run all backend and frontend checks after the final policy and migration changes.
- Store exact commands, commit hash, environment and results in the next version entry.

---

## 3. Contract Discrepancy & The 7 Blockers Comprehensive Analysis

### 3.1 Contract Discrepancy Inventory (Backend Controller vs OpenAPI vs spec.md)

Nguồn sự thật cao nhất theo yêu cầu là `spec.md` và OpenAPI `contracts/ai-chatbox-analysis.openapi.yaml`. Rà soát đối chiếu thực tế phát hiện 5 điểm sai lệch:

1. **AI Support Endpoint (`/api/v1/ai-support/messages`)**:
   - *Backend Controller (`AiSupportResponse`)*: Trả về `{ message: string, scope: string, providerCorrelationId: string }`.
   - *OpenAPI (`AiSupportResponse`)*: Quy định `{ answer: string, advisory: boolean, scope: string, correlationId: string }`.
2. **Design Request Creation & Detail (`/api/v1/design-requests`)**:
   - *Backend Controller (`DesignRequestViewDto`)*: Trả về các trường phẳng (`candidateFeatures`, `imageQuality`, `confidence`, `safeExplanation`). Request nhận `designVersion`.
   - *OpenAPI (`DesignRequestProjection`)*: Quy định bọc kết quả trích xuất vào object `safeResult: { ... }`. Request schema không có `designVersion`.
3. **Review Queue Endpoint (`/api/v1/design-review-requests`)**:
   - *Backend Controller (`DesignReviewController`)*: Trả về mảng thô `List<DesignRequestViewDto>` (`[{...}]`).
   - *OpenAPI (`ReviewQueue`)*: Quy định bọc trong envelope object `{ items: [DesignRequestProjection] }`.
4. **Review Decision Endpoint (`/api/v1/design-review-requests/{id}/decisions`)**:
   - *Backend Controller (`ReviewDecisionDto`)*: Sử dụng trường tên `designVersion`.
   - *OpenAPI (`ReviewDecisionRequest`)*: Quy định trường tên `requestVersion`.
5. **Price Suggestion Endpoint (`/api/v1/price-proposals/{id}/ai-suggestions`)**:
   - *Backend Controller (`PriceProposalController`)*: Nhận `targetType` qua Query Parameter (`?targetType=...`), body là raw string `bodyContext`, trả về HTTP `200 OK`.
   - *OpenAPI (`PriceSuggestionRequest`)*: Quy định Request Body JSON `{ targetType, materials, components, pricingContext }`, trả về HTTP `202 Accepted`.

---

### 3.2 The 7 Blockers Analysis & Resolution Strategies (Feature 004 & Feature 011)

#### Blocker 1: Chưa xác minh build/test bằng JDK 21
- **Trạng thái**: **ĐÃ GIẢI QUYẾT XONG (RESOLVED)**.
- **Kết quả thực nghiệm**:
  - Môi trường: OpenJDK 21.0.7 LTS.
  - Đã fix lỗi `UnsatisfiedDependencyException` trong `ReviewEligibilityService`, `ReviewModerationService`, `ReviewReplyService` bằng cách thêm `@Autowired` rõ ràng vào constructor chính.
  - Đã fix cấu hình DDL trong `application-test.properties` để H2 khởi tạo đúng schema trong test environment.
  - Đã chạy thành công **100% Modulith Architecture Tests (5/5)** và **100% Feature-004 Integration Tests (44/44)**.

#### Blocker 2: Chưa chạy contract test giữa controller và OpenAPI
- **Trạng thái**: **ĐANG BỊ CHẶN BỞI SAI LỆCH SCHEMA (BLOCKED)**.
- **Lý do chưa giải quyết được hoàn toàn**: Tồn tại 5 điểm sai lệch danh nghĩa giữa Controller DTO và OpenAPI schema (chi tiết tại mục 3.1). Nếu chạy test contract nghiêm ngặt, cả 5 endpoint sẽ fail do khác tên trường và envelope structure.
- **Phương án giải quyết**:
  - *Phương án 1 (Khuyến nghị)*: Cập nhật Backend DTO và Controller để khớp 100% với OpenAPI specification (`answer`, `safeResult`, `{ items }`, `requestVersion`, `202 Accepted`).
    - *Ưu điểm*: Tuân thủ đúng chuẩn RESTful API First, chuẩn hóa hệ thống tài liệu và OpenAPI.
    - *Nhược điểm*: Cần sửa đổi DTO Backend và cập nhật lại assertion trong 44 integration tests hiện có.
  - *Phương án 2*: Cập nhật OpenAPI spec để phản ánh chính xác cấu trúc Backend DTO hiện tại.
    - *Ưu điểm*: Nhanh nhất, giữ nguyên 44 backend integration tests đang pass 100%.
    - *Nhược điểm*: Phải sửa tài liệu hợp đồng đã ban hành, một số tên trường ít tính chuẩn mực hơn.
  - *Phương án 3*: Hỗ trợ Dual-schema / Field Aliases trên Backend DTO (Jackson `@JsonAlias`).
    - *Ưu điểm*: Cho phép chấp nhận cả 2 dạng request/response, đảm bảo tương thích ngược 100%.
    - *Nhược điểm*: Tăng độ phức tạp mã nguồn backend.

#### Blocker 3: Chưa xác minh database hiện tại với Flyway V91–V93
- **Trạng thái**: **CHƯA THỂ CHẠY AUTOMATION TRONG AGENT SESSION (BLOCKED)**.
- **Lý do chưa giải quyết được**: Docker Desktop service (`com.docker.service`) trên máy host Windows đang ở trạng thái Stopped. Việc khởi động service này yêu cầu quyền Administrator (UAC elevation) mà process của agent không thể tự cấp quyền trong shell không tương tác. Do đó không thể kết nối tới MySQL container qua port 3306.
- **Phương án giải quyết**:
  - *Phương án 1 (Khuyến nghị)*: Người dùng tự khởi động ứng dụng Docker Desktop từ Start Menu hoặc chạy `Start-Service com.docker.service` bằng PowerShell với quyền Run as Administrator. Sau đó agent chạy script Flyway verification.
    - *Ưu điểm*: Đơn giản, an toàn, sử dụng đúng MySQL 8.0 container chuẩn của dự án.
    - *Nhược điểm*: Cần thao tác thủ công của người dùng trên desktop.
  - *Phương án 2*: Cài đặt và cấu hình MySQL native service trực tiếp trên Windows không qua Docker.
    - *Ưu điểm*: Không phụ thuộc vào Docker Desktop.
    - *Nhược điểm*: Tốn thời gian cài đặt, cấu hình môi trường và dễ xung đột port.
  - *Phương án 3*: Chạy Flyway dry-run / validate script ngoại tuyến thông qua H2 dialect trong backend test.
    - *Ưu điểm*: Chạy được ngay lập tức không cần database server.
    - *Nhược điểm*: Không kiểm tra được các câu lệnh DDL đặc thù của MySQL 8.0 (ví dụ `DROP FOREIGN KEY`, collation `utf8mb4_0900_ai_ci`).

#### Blocker 4: Một số integration facade cần kiểm tra với dữ liệu thực tế
- **Trạng thái**: **ĐANG BỊ GIỚI HẠN BỞI THIẾU TÀI NGUYÊN BÊN NGOÀI (BLOCKED)**.
- **Lý do chưa giải quyết được**:
  - `AiTextSupportPort` và `ImageFeatureAnalysisPort`: Đang dùng deterministic adapter (`GeminiRestClientAdapter`) do chưa cấu hình `GEMINI_API_KEY` production.
  - `WorkshopAttendanceReviewPort` và `RetailFulfilmentReviewPort`: Đã có adapter stub và unit test, nhưng chưa chạy được với database MySQL chứa dữ liệu booking/order thực tế do MySQL container chưa bật (phụ thuộc Blocker 3).
- **Phương án giải quyết**:
  - *Phương án 1 (Khuyến nghị)*: Tiếp tục duy trì Deterministic Adapter cho môi trường Local/CI; khi có `GEMINI_API_KEY` trong `.env`, kích hoạt profile `prod-ai` để kiểm thử thực tế.
    - *Ưu điểm*: Không tốn chi phí quota Gemini API, kiểm thử ổn định, lặp lại được 100%.
    - *Nhược điểm*: Chưa đánh giá được latency và chất lượng nhận diện ảnh thực tế của Gemini Vision.
  - *Phương án 2*: Tạo fixture dataset đa dạng (JSON mocking) mô phỏng nhiều trường hợp phức tạp (nhẫn bạc 925, đính đá sapphire, ảnh mờ, prompt injection tiếng Việt tinh vi).
    - *Ưu điểm*: Nâng cao độ phủ test cho frontend và backend mà không cần API key.
    - *Nhược điểm*: Vẫn là dữ liệu giả lập.

#### Blocker 5: Cần chốt response/status thực tế Feature-004 và Feature-011 để tránh compatibility workaround
- **Trạng thái**: **CHỜ NGƯỜI DÙNG QUYẾT ĐỊNH (AWAITING USER DECISION)**.
- **Lý do**: Cần thống nhất chính thức giữa Team Backend và Team Frontend về format response để dọn dẹp các compatibility fallback layer trong code frontend (`res.answer || res.message`).
- **Phương án giải quyết**:
  - *Phương án 1 (Khuyến nghị)*: Chốt theo chuẩn OpenAPI cho cả hai feature. Sửa Backend DTO để trả về đúng chuẩn OpenAPI.
    - *Ưu điểm*: Đồng bộ toàn diện, tuân thủ nguyên tắc Single Source of Truth.
    - *Nhược điểm*: Phải sửa đổi DTO backend.
  - *Phương án 2*: Chốt theo Backend Controller hiện tại; cập nhật lại OpenAPI yaml.
    - *Ưu điểm*: Backend giữ nguyên, không cần sửa đổi test hiện có.
    - *Nhược điểm*: OpenAPI phải sửa theo hiện trạng.
  - *Phương án 3*: Giữ compatibility layer ở Frontend API client (như hiện tại).
    - *Ưu điểm*: Frontend chạy được với cả 2 phiên bản response.
    - *Nhược điểm*: Code frontend không sạch, tiềm ẩn lỗi khi bảo trì.

#### Blocker 6: Một số message backend đang lỗi encoding tiếng Việt
- **Trạng thái**: **ĐÃ XÁC ĐỊNH NGUYÊN NHÂN GỐC RỄ (ROOT CAUSE IDENTIFIED)**.
- **Lý do**:
  - Các chuỗi string literal tiếng Việt trong các file mã nguồn Java (`ReviewApplicationService.java` dòng 60, 169; `GeminiRestClientAdapter.java` dòng 128) đã bị lưu với encoding ANSI/Cp1252 làm biến dạng dấu tiếng Việt thành ký tự `?` hoặc ``.
  - Trong `ppswbs_backend/pom.xml`, thuộc tính `<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>` chưa được định nghĩa rõ ràng trong thẻ `<properties>`.
- **Phương án giải quyết**:
  - *Phương án 1 (Khuyến nghị)*: Bổ sung `<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>` vào `pom.xml`, sửa lại toàn bộ chuỗi tiếng Việt bị lỗi và đưa vào file tài nguyên `i18n/messages_vi.properties`.
    - *Ưu điểm*: Giải quyết triệt để vấn đề quốc tế hóa, hỗ trợ đa ngôn ngữ chuẩn mực.
    - *Nhược điểm*: Cần refactor các thông báo lỗi sang dạng message key.
  - *Phương án 2*: Sửa trực tiếp các string literal trong file Java thành chuỗi tiếng Việt UTF-8 chuẩn.
    - *Ưu điểm*: Sửa nhanh, không thay đổi logic ném exception.
    - *Nhược điểm*: Message vẫn bị phân tán trong code Java.

#### Blocker 7: Cần kiểm tra đầy đủ authorization test cho Guest, Member, Staff, Manager và Admin
- **Trạng thái**: **ĐÃ HOÀN THÀNH CHO FEATURE 004, CẦN BỔ SUNG CHO FEATURE 011 (PARTIALLY RESOLVED)**.
- **Lý do**: Feature 004 đã có 44 test bao phủ đầy đủ ma trận 5 roles (Guest chỉ hỏi chat AI; Member quản lý request của mình; Staff duyệt <= 3M; Manager duyệt mọi đơn và quyết định giá; Admin chỉ xem audit log kỹ thuật). Tuy nhiên Feature 011 cần bổ sung test ma trận tương tự cho các nghiệp vụ kiểm duyệt và phản hồi đánh giá.
- **Phương án giải quyết**:
  - *Phương án 1 (Khuyến nghị)*: Viết thêm Integration Test Suite phân quyền cho Feature 011 (`ReviewSecurityIT.java`) kiểm tra toàn bộ 5 role tương tự cấu trúc đã chạy thành công ở Feature 004.
    - *Ưu điểm*: Kiểm tra toàn diện từ HTTP filter, Spring Security đến service layer.
    - *Nhược điểm*: Cần bổ sung thêm khoảng 15-20 test cases.
  - *Phương án 2*: Viết Unit Test ở tầng Controller sử dụng `@WithMockUser`.
    - *Ưu điểm*: Nhẹ, chạy nhanh.
    - *Nhược điểm*: Không kiểm tra được cơ chế Security Context và Filter Chain thực tế.

---

## 4. Traceability index

| Phase | Current evidence | Remaining evidence |
| --- | --- | --- |
| Phase A | Independent contract and boundary artifacts | Approved ownership/review/retention decisions |
| Phase B | Independent entity model and V62 migration | Existing-database history check and MySQL upgrade test |
| Phase C | Services, security and authorization rebase | Final policy-driven behavior |
| Phase D | Independent REST backend contract | Full integration with live backend |
| Phase E | Historical Modulith and 44/44 integration results | Fresh final-commit verification and E2E evidence |
| Phase F | React + Vite Design Workspace (21/21 tests, build passed) | Live browser E2E in staging |
| Giai đoạn 1 | Frontend implementation (6 screens, 21 tests passed) | Live browser E2E in staging |
| Giai đoạn 2 | Provider port and validation boundary | Production Gemini configuration/evaluation |
| Giai đoạn 3 | Retention and review mechanisms exist | Policy and notification decisions |
| Giai đoạn 4 | Test plan exists | Migration, E2E and release evidence |

---

## 5. Current backend flow and data flow

This section describes the code flow currently present in commit `79a770f` (`[UPDATE] Sửa lại code backend luồng 004 độc lập khỏi quá trình booking`). It is an implementation trace, not a replacement for the Feature-004 contract. The flow has no call to Workshop Booking, Payment, Operations, Ticketing or Capacity.

### 5.1. Module and dependency flow

```text
HTTP request
  → designreview.web controller
  → designreview.application service
  → designreview.infrastructure repository / adapter
  → designreview.domain entity / port

designreview
  → accountmanagement.facade only
  → AI provider port implemented by GeminiRestClientAdapter
  → temporary image port implemented by LocalStorageAdapter
```

`designreview/package-info.java` allows only `accountmanagement :: facade`. Controllers do not access repositories directly. Services own the use-case transaction and map entities to safe DTO projections.

### 5.2. Basic AI Support flow

```text
POST /api/v1/ai-support/messages
  → AiSupportController
  → optional CurrentAccountActor extraction for an active Member
  → AiSupportService.askSupport(message, memberId, correlationId)
  → validate blank/length ≤ 2,000 characters
  → detect prompt injection and forbidden private/business context
  → AiTextSupportPort
  → GeminiRestClientAdapter.generateSupportResponse(...)
  → AiTextInteractionRepository.save(...)
  → AiSupportResponse
```

Guest requests are represented with `memberId = null` and `ActorScope.GUEST`. Member requests store the resolved account ID. Successful and rejected interactions are retained with a 30-day expiry. `DataRetentionCleanupJob` later removes expired text records.

### 5.3. Independent Design Request submission flow

```text
POST /api/v1/design-requests (multipart/form-data)
  → DesignRequestController
  → SecurityIdentityExtractor.requireUid(authentication)
  → AccountFacade.resolveAccount(callerUid)
  → require ACTIVE MEMBER
  → DesignRequestRepository idempotency lookup
  → validate description/image presence
  → validate description and prompt-injection patterns
  → if image: consent → ImageRateLimiter → MIME/size/dimensions validation
  → create DesignRequest with PROCESSING + NEED_REVIEW
  → image branch or text-only branch
  → return DesignRequestViewDto with HTTP 202
```

Image branch:

```text
ImageConsentRepository.save
  → ReferenceImageStoragePort.storeTemporary
  → TemporaryImageReferenceRepository.save
  → ImageFeatureAnalysisPort.analyzeRingImage
  → GeminiRestClientAdapter.analyzeRingImage
  → finally: ReferenceImageStoragePort.deleteTemporary
  → TemporaryImageReference marked DELETED_AFTER_PROCESSING
  → DesignAnalysisResultRepository.save(safe JSON projection)
  → DesignRequest becomes SUCCEEDED / AWAITING_HUMAN_REVIEW
```

If image analysis fails, the image is still deleted in `finally`; the request becomes `FAILED` with a safe failure code and `SAFE_RETRY_AVAILABLE`. The current adapter is deterministic/mock-like and returns candidate features, image quality, confidence, explanation and an estimated value; it does not currently call a live Gemini HTTP endpoint.

Text-only branch:

```text
no image
  → no image consent, storage or image rate-limit slot
  → DesignRequest becomes SUCCEEDED / AWAITING_HUMAN_REVIEW
  → DesignAnalysisResultRepository.save(TEXT_DESCRIPTION_ONLY projection)
  → current code assigns estimatedValue = 1,800,000 VND
```

### 5.4. Design Request read flow

```text
GET /api/v1/design-requests/{designRequestId}
  → DesignRequestController
  → SecurityIdentityExtractor.requireUid
  → DesignRequestRepository.findById
  → DesignReviewPermissions.canViewDesignRequest(authorities)
     ├─ internal role: reviewer/admin projection allowed
     └─ otherwise: AccountFacade ownership check against ownerAccountId
  → DesignAnalysisResultRepository.findByDesignRequestId
  → parse safe JSON fields
  → DesignRequestViewDto
```

The response contains request state, safe candidate features, image quality, confidence and explanation. It does not return the raw image or raw provider payload.

### 5.5. Human review flow

```text
GET /api/v1/design-review-requests
  → DesignReviewController
  → SecurityIdentityExtractor.requireUid
  → HumanReviewService.getReviewQueue
  → DesignRequestRepository.findByReviewStatus(NEED_REVIEW)
  → Staff filter estimatedValue ≤ 3,000,000 VND
    or Manager/Owner sees all queued requests
  → map safe DesignRequestViewDto list
```

```text
POST /api/v1/design-review-requests/{id}/decisions
  → parse ACCEPTED / REJECTED / CLARIFICATION_REQUIRED
  → HumanReviewService.recordDecision
  → authorize Staff or Manager
  → load DesignRequest
  → reject self-review
  → reject terminal ACCEPTED/REJECTED state
  → validate design version and Staff value threshold
  → HumanReviewDecisionRepository.save
  → update DesignRequest.reviewStatus and nextAction
  → return safe projection
```

Current implementation keeps the former 3,000,000 VND Staff/Manager routing rule. Branch/delegation scope and explicit conflict-of-interest checks beyond self-review are not implemented in this flow and remain verification gaps.

### 5.6. AI price suggestion flow

```text
POST /api/v1/price-proposals/{proposalId}/ai-suggestions
  → PriceProposalController
  → SecurityIdentityExtractor.requireUid
  → PriceSuggestionService.requestSuggestion
  → Manager/Owner authority check
  → validate proposalId and WORKSHOP_PACKAGE/RETAIL_PRODUCT target
  → AiPriceSuggestionPort
  → GeminiRestClientAdapter.suggestPrice
  → PriceSuggestionRepository.save(advisory=true)
  → Manager-facing PriceSuggestionViewDto
```

```text
GET /api/v1/price-proposals/{proposalId}/ai-suggestions
  → PriceSuggestionService.getSuggestion
  → Manager sees business pricing fields
  → Admin sees technical/audit projection with pricing fields removed
  → Staff/Guest/Member are denied
```

```text
POST /api/v1/price-proposals/{proposalId}/ai-suggestions/decisions
  → Manager authority check
  → validate ACCEPTED / ADJUSTED / REJECTED / MANUAL_FALLBACK
  → validate finalPrice when required
  → PriceSuggestionRepository.save
  → return updated Manager projection
```

The current Feature-004 code stores the Manager decision in `PriceSuggestion`; applying or publishing the final catalogue price remains outside this module.

### 5.7. Persistence data flow

| Runtime data | Producer | Persistence | Consumer/output |
| --- | --- | --- | --- |
| Basic AI prompt/response | `AiSupportService` | `AiTextInteractionRepository` → `ai_text_interactions` | Guest/Member safe response; retention job |
| Independent request metadata | `DesignAnalysisService` | `DesignRequestRepository` → `design_requests` | Owner/reviewer safe projection; facade summary |
| Image consent | `DesignAnalysisService` | `ImageConsentRepository` → `image_consents` | Consent/audit evidence |
| Temporary image reference | image branch | `TemporaryImageReferenceRepository` + `LocalStorageAdapter` | Provider input, then deletion evidence |
| AI image result | image/text branch | `DesignAnalysisResultRepository` → analysis result record | Candidate features, quality, confidence and explanation |
| Human decision | `HumanReviewService` | `HumanReviewDecisionRepository` + `DesignRequestRepository` | Review queue state and owner-safe result |
| Advisory price | `PriceSuggestionService` | `PriceSuggestionRepository` → `price_suggestions` | Manager view or Admin technical projection |

### 5.8. Current implementation caveats

- The Maven wrapper could not be started in the current environment, so this update does not claim fresh test execution.
- Flyway previously contained duplicate V6, V7 and V9 filenames; those collisions were renumbered in the shared migration history. The later V12, V60 and V61 collisions are resolved by assigning Feature-004 to V62; V60 remains owned by settlement add-on bills and V61 by OTA add-on link details.
- `V62__independent_design_request.sql` still modifies the legacy `image_consents` schema and requires clean-install/upgrade verification against the actual baseline.
- `DesignAnalysisService` currently requires an active Member even though the entity contains Guest-proof fields.
- `consentPurpose` is accepted by the controller but the current service does not independently validate it against `INDEPENDENT_DESIGN_IMAGE_ANALYSIS`.
- The current in-memory `LocalStorageAdapter` is suitable for deterministic local/test processing, not multi-instance production deployment.

---

## 6. Frontend implementation specification for the next agent

**Status**: Frontend React/Vite implementation exists; Figma MCP design handoff is the next design activity.
**Decision**: Use Figma MCP to create/review the Feature-004 visual design. Figma is a design source and review artifact; it does not replace `spec.md`, API contracts, backend authorization or frontend tests.

### 6.1. Frontend baseline and rules

The implementation MUST use the existing frontend baseline:

- React 19, Vite 7 and `react-router` 7.
- Feature boundary: `ppswbs_frontend/src/features/designreview/`.
- Shared modules:
  - `src/shared/api/http.js`;
  - `src/shared/auth/AuthProvider.jsx` and `RequireSession.jsx`;
  - `src/shared/i18n/`;
  - `src/shared/ui/`.
- Add no state library, UI kit, form library or new dependency.
- Use real React Router paths for Feature-004; do not add Feature-004 screens to the hash-based prototype route.
- Use `useResource` for reads and `useCommand` for writes.
- Do not auto-retry requests. A retry of a write reuses the same `Idempotency-Key`.
- Use Firebase Bearer authentication through the shared HTTP client. Never store tokens in local/session storage, URLs or logs.
- Route guards and hidden buttons are UX only. Backend authorization remains authoritative.
- Do not store raw images, raw provider payloads or unnecessary PII in long-lived frontend state.
- Do not use mock success responses as evidence of backend integration.
- Reuse shared loading, empty, error, retry, field-error, status, dialog, bilingual and accessibility conventions from Features 002/006.

### 6.2. Route and screen inventory

The first frontend delivery uses these routes:

```text
/ai-support
/design-requests/new
/design-requests/:designRequestId
/design-review-requests
/design-review-requests/:reviewRequestId
/price-proposals/:proposalId/ai-suggestions
```

| Screen ID | Route | Actor | Required behavior |
| --- | --- | --- | --- |
| `F04-AI-CHAT` | `/ai-support` | Guest/Member | Basic public-scope AI support, 2,000-character limit, advisory label, loading, validation, provider error and retry. |
| `F04-DESIGN-WORKSPACE` | `/design-requests/new` | Active Member | Description up to 4,000 characters and optional catalogue reference/image. No package, location, session, booking, payment or ticket fields. |
| `F04-CONSENT` | Part of new-request flow | Active Member with image | Display consent purpose/version and require affirmative confirmation before submit. |
| `F04-IMAGE-UPLOAD` | Part of new-request flow | Active Member with image | Validate JPEG/PNG/WEBP, 10 MB, 4096×4096 and display typed validation/rate-limit errors. |
| `F04-ANALYSIS-STATUS` | `/design-requests/:designRequestId` | Request owner | Display actual `PROCESSING`, `SUCCEEDED` or `FAILED` state returned by the backend. Do not assume asynchronous polling is available. |
| `F04-RESULT` | `/design-requests/:designRequestId` | Request owner | Display safe candidate features, image quality, confidence, explanation, review state and next action. Never display raw provider payload/image. |
| `F04-REVIEW-QUEUE` | `/design-review-requests` | Staff/Manager | Load `NEED_REVIEW` requests. Do not add branch/session filtering until backend scope data exists. |
| `F04-REVIEW-DETAIL` | `/design-review-requests/:reviewRequestId` | Staff/Manager | Display safe result, version, estimated value and reason form; submit human decision and recover from 403/409. |
| `F04-PRICE-PROPOSAL` | `/price-proposals/:proposalId/ai-suggestions` | Manager/Admin | Manager sees advisory business data and decides; Admin sees technical/audit projection only. No catalogue publication action is owned by Feature-004. |

Guest access is limited to `F04-AI-CHAT`. Design Request creation currently requires an active Member because that is what `DesignAnalysisService` enforces.

### 6.3. Backend-current API mapping

The feature API client MUST call the backend controller shape currently present in `ppswbs_backend`:

```text
POST /api/v1/ai-support/messages
POST /api/v1/design-requests
GET  /api/v1/design-requests/{id}
GET  /api/v1/design-review-requests
POST /api/v1/design-review-requests/{id}/decisions
POST /api/v1/price-proposals/{id}/ai-suggestions
GET  /api/v1/price-proposals/{id}/ai-suggestions
POST /api/v1/price-proposals/{id}/ai-suggestions/decisions
```

Current payload rules:

- Basic AI response fields are `message`, `scope` and `providerCorrelationId`.
- Design Request is multipart with `description`, `catalogueReference`, `consentVersion`, `consentPurpose`, `designVersion`, optional `image` and required `Idempotency-Key`.
- Design Request POST returns the current `DesignRequestViewDto` with HTTP `202`.
- Review decision uses `designVersion`, not OpenAPI's `requestVersion`.
- Price suggestion currently receives `targetType` as a request parameter and `bodyContext` as a raw request body.
- Current price suggestion controller returns HTTP `200`, not the `202` described in OpenAPI.
- The frontend MUST NOT silently transform these fields to the OpenAPI shape. Contract synchronization is a separate backend/spec task.

Known OpenAPI/backend mismatches to retain as blockers:

| Topic | OpenAPI | Current backend | Frontend rule |
| --- | --- | --- | --- |
| Basic AI answer | `answer` | `message` | Read `message` |
| Review version | `requestVersion` | `designVersion` | Send `designVersion` |
| Price context | JSON object | raw `bodyContext` plus `targetType` parameter | Follow controller until contract is synchronized |
| Price response status | `202` | `200` | Accept current `200` |

### 6.4. Frontend and backend data flow

```text
React Router route
  → Feature-004 page component
  → feature API hook/client
  → shared/api/http.js
  → Firebase getIdToken()
  → Authorization: Bearer <Firebase ID token>
  → designreview web controller
  → designreview application service
  → domain port/repository/adapter
  → safe JSON response
  → page-local state
  → loading/success/error/recovery UI
```

Write flow:

```text
User action
  → useCommand.run()
  → crypto.randomUUID() creates Idempotency-Key
  → API request
  → pending/disabled action state
  → safe response or typed ApiError
  → success state or translated error state
  → retry reuses the same key
```

Read flow:

```text
Page mount or route parameter change
  → useResource()
  → GET request
  → latest-request-wins protection
  → loading / empty / error / data state
```

Authorization flow:

```text
Route opens
  → AuthProvider observes Firebase session
  → UI applies only fail-safe navigation hints
  → backend verifies role/status/ownership
  → 401: shared client refreshes token once, then signs out if still rejected
  → 403: show access denied without signing out
```

Backend data flow by use case:

```text
Basic AI:
AiSupportController
  → AiSupportService
  → AiTextSupportPort / GeminiRestClientAdapter
  → AiTextInteractionRepository
  → AiSupportResponse

Design Request with image:
DesignRequestController
  → DesignAnalysisService
  → AccountFacade.resolveAccount
  → DesignRequestRepository
  → ImageConsentRepository
  → ImageRateLimiter
  → ReferenceImageStoragePort / LocalStorageAdapter
  → ImageFeatureAnalysisPort / GeminiRestClientAdapter
  → TemporaryImageReferenceRepository
  → DesignAnalysisResultRepository
  → DesignRequestViewDto

Human review:
DesignReviewController
  → HumanReviewService
  → DesignRequestRepository
  → HumanReviewDecisionRepository
  → DesignRequestViewDto

Price suggestion:
PriceProposalController
  → PriceSuggestionService
  → AiPriceSuggestionPort / GeminiRestClientAdapter
  → PriceSuggestionRepository
  → Manager or Admin-safe PriceSuggestionViewDto
```

### 6.5. Implementation tasks

#### Phase 1 — Frontend foundation

- Create `src/features/designreview/` with route, API, message and style modules.
- Register Feature-004 routes in `src/app/router.jsx`.
- Reuse `shared/api/http.js`, `useResource`, `useCommand`, `AuthProvider`, `RequireSession`, `ErrorNotice`, `Loading`, `Field`, `ConfirmDialog` and bilingual message conventions.
- Do not import internal components from Feature-002 or Feature-006.

#### Phase 2 — Customer flow

- Implement Basic AI Support.
- Implement Member Design Request form.
- Implement consent and image upload validation.
- Implement request submission with multipart data and idempotency.
- Implement status/result view using the POST/GET response shape.
- Implement owner-safe access, rate-limit errors and retry while preserving form data.

#### Phase 3 — Staff/Manager/Admin operations

- Implement review queue and review detail.
- Implement reason validation and decision submission.
- Implement stale-version, duplicate-decision and forbidden recovery.
- Implement Manager price suggestion request/view/decision.
- Implement Admin technical/audit projection without commercial pricing fields.

#### Phase 4 — Frontend verification

- API client tests: Bearer token, multipart payload, Idempotency-Key, 401 refresh, 403, 409, 429 `Retry-After`, 503 retry and current backend field names.
- Screen tests: loading, empty, validation, success, forbidden, rate limited, provider failure, stale version, conflict and retry preserving input.
- Permission tests: Guest cannot create Design Request; Member sees only own result; Staff/Manager review rules; Admin technical projection; Staff/Member price denial.
- Run `npm test` and `npm run build` from `ppswbs_frontend`.

### 6.6. Frontend blockers and unresolved gaps

1. Feature-004 has no approved Figma design pack yet; the existing `design.md` is the behavior and implementation handoff.
2. OpenAPI does not match current backend DTOs/statuses.
3. No account bootstrap contract currently supplies role/status to the frontend; UI must remain fail-safe and rely on backend 401/403.
4. Backend retains compatibility with `ROLE_OWNER` although the current business baseline uses Manager.
5. Review queue has no real branch/delegation scope data.
6. Revision and withdrawal endpoints/state transitions are not implemented.
7. Image analysis executes synchronously inside the Design Request POST; no frontend polling contract is available.
8. `GeminiRestClientAdapter` currently returns deterministic/mock-like results rather than calling the live provider.
9. `LocalStorageAdapter` is in-memory and unsuitable for multi-instance production deployment.
10. Maven wrapper execution failed in the current environment before Maven started; no fresh backend integration evidence is available for this frontend handoff.
11. The source-level V12/V60/V61 collisions are resolved by moving Feature-004 to V62; existing databases must still be checked for a previously applied Feature-004 V12, V60 or V61 history row before rollout.
12. `consentPurpose` is accepted by the controller but is not independently validated by `DesignAnalysisService`; the frontend must send `INDEPENDENT_DESIGN_IMAGE_ANALYSIS` and the backend contract should be hardened separately.

### 6.7. Merge conflict resolution log (2026-11-01)

The following four conflicts were identified while preparing the `dat` branch for merge with `develop`:

1. **Application router conflict** — `develop` introduced `workshopRoutes` and `NotImplementedPage`, while `dat` introduced `designReviewRoutes` and retained the prototype fallback. Resolution: use the production router structure from `develop`, compose product, loyalty, workshop and Feature-004 routes together, and use `NotImplementedPage` for the catch-all route. The hash-based `PrototypeApp` fallback was removed.
2. **Application layout conflict** — `develop` converted Workshop and Login navigation to real paths, while `dat` added Feature-004 navigation. Resolution: keep `/workshops` and `/login` from `develop`, preserve `/ai-support` and `/design-requests/new`, and retain the bilingual Feature-004 labels.
3. **Task status conflict** — `develop` still marked T012 incomplete, while `dat` contained the implemented frontend. Resolution: retain T012 as completed and keep the V62 migration note reserving V12 for Loyalty/Feature-006, V60 for settlement add-on bills and V61 for OTA add-on link details.
4. **Version/audit document conflict** — `develop` contained stale migration wording and committed conflict markers, while `dat` contained Version 1.4 frontend evidence. Resolution: keep the Version 1.4 frontend delivery record, remove stale conflict markers, retain V62 as the Feature-004 migration and document the remaining live-backend, E2E and migration-history verification gaps.

Verification after resolution: no conflict markers, no source-level duplicate Flyway versions, `git diff --check` clean. The frontend test/build suite must be rerun after the actual branch merge.

## 7. Frontend specification synchronization (2026-11-05)

The current React/Vite implementation is now reflected in the feature-specific
frontend artifacts:

- Guest is limited to Basic AI Support; `RequireSession` protects Design Request routes.
- Member Design Requests accept description-only, image-only or combined input.
- The Design Request flow remains independent from booking, payment, package, location and session.
- Current routes are `/ai-support`, `/design-requests/new`, `/design-requests/:designRequestId`,
  `/design-review-requests`, `/design-review-requests/:reviewRequestId` and
  `/price-proposals/:proposalId/ai-suggestions`.
- `design.md` records the implemented flat `designreview` slice and separately identifies
  the future `ui/application/domain/infrastructure` normalization direction.
- `spec.md`, `plan.md` and `tasks.md` distinguish completed frontend behavior from
  unresolved backend/OpenAPI integration work.

No root `ppswbs-spec/DESIGN.md` change was made. It remains the shared visual and
accessibility baseline. The frontend documentation does not claim live backend,
provider, migration or E2E verification that has not been rerun.

## 8. Figma MCP design handoff (2026-11-05)

### 8.1. Purpose and authority

Figma MCP may be used to create and iterate the visual design for Feature-004.
The Figma file is authoritative for visual composition, component variants,
responsive frames and interaction presentation after explicit design approval.
It is not authoritative for business rules, permission, API payloads, validation
limits, retention, review decisions or backend state. Those remain in `spec.md`,
`design.md`, the OpenAPI contract and the shared `DESIGN.md` baseline.

### 8.2. Required Figma input before generation

The Figma agent must receive or discover an approved handoff containing:

- Figma file key/URL and target page or destination for the new design;
- screen scope and route mapping from section 6.2;
- actor and access boundary for every screen;
- approved bilingual copy or a clearly marked copy placeholder;
- desktop/mobile frame requirements and responsive priority;
- reusable component and token references from the project design system;
- required variants for loading, empty, validation, forbidden, rate-limit, conflict, failure, success and disabled states;
- assets, icon policy and image-handling restrictions;
- reviewer, approval revision and approval date.

If any item changes observable product behavior, the Figma agent must stop and
record the question instead of inventing a flow or permission.

### 8.3. Required Figma output

The design handoff must include:

- a page/frame for each Feature-004 route and required state;
- named frames using the IDs `F04-AI-CHAT`, `F04-DESIGN-WORKSPACE`,
  `F04-CONSENT`, `F04-IMAGE-UPLOAD`, `F04-ANALYSIS-STATUS`, `F04-RESULT`,
  `F04-REVIEW-QUEUE`, `F04-REVIEW-DETAIL` and `F04-PRICE-PROPOSAL`;
- component and variant mapping to existing shared UI patterns;
- responsive frames for customer mobile-first and operational desktop-first layouts;
- accessible names, focus/disabled/error states and bilingual text placement;
- a traceability note linking each frame to `spec.md`, API mapping and acceptance states.

The agent must not add booking, package, location, session, payment, ticket,
catalogue-publication or custom-manufacturing steps to the Figma flow.

### 8.4. Figma-to-frontend verification

Before implementation uses the new design, review must confirm:

1. Every approved frame maps to an existing route or explicitly approved new route.
2. Every visible action maps to a documented API command or a local presentation action.
3. Permission-sensitive controls are shown only as UX affordances; server authorization remains required.
4. All required error and recovery states are represented.
5. The design uses shared tokens/components and does not introduce an unapproved dependency or visual system.
6. The approved Figma revision is recorded in the feature handoff before code changes begin.

## 9. Figma MCP implementation record (2026-11-05)

Figma MCP was used to create editable, bilingual, desktop/mobile design frames in
file `m5Cyk5rit6lKaX3TPprSow`.

- Public page sections: `F04 · AI Support & Independent Design Request` (`413:2`)
  and `F11 · Review Management` (`413:203`).
- Customer frames include `F04-AI-CHAT`, `F04-DESIGN-WORKSPACE`, `F04-CONSENT`,
  `F04-IMAGE-UPLOAD`, `F04-ANALYSIS-STATUS`, `F04-RESULT`, `F11-PUBLIC-REVIEW-LIST`,
  `F11-PUBLIC-REVIEW-DETAIL`, `F11-GUEST-LOOKUP`, `F11-ELIGIBLE-SOURCES`,
  `F11-CREATE-REVIEW` and `F11-EDIT-REVIEW`.
- Staff section: `F04 + F11 · Staff Operations` (`414:1083`) with desktop and
  mobile review/moderation queue/detail frames.
- Manager section: `F04 + F11 · Manager Operations` (`414:2983`) with price
  proposal and Manager reply frames in desktop/mobile variants.

The frames use editable text and vector layers and follow the existing Inter/
Oswald visual baseline. The design has not been self-marked as approved; the
responsible reviewer must record the approved revision/date before treating the
frames as a final visual baseline. Figma does not change Feature-004 business,
permission, API, AI, retention or pricing rules.

Frontend implementation remains on the existing `designreview` slice. The
Figma work is a visual handoff, not a replacement for backend/OpenAPI mismatch
resolution or integration verification.

## 10. QA test implementation and execution plan (2026-11-12)

This is a testing-agent handoff, not a current test result. Historical code or
test logs must be re-run under `ppswbs-testing/AGENT.md` before reporting PASS.

### 10.1 Test artifacts and traceability

Create under `test-results/`:

```text
UTC-004-A-001--ai-input-security.md
UTC-004-A-002--design-request-invariants.md
UTC-004-A-003--review-routing-and-privacy.md
UTC-004-B-001--frontend-command-and-preview.md
E2E-004-001--guest-ai-support.md
E2E-004-002--member-design-request.md
E2E-004-003--staff-manager-review.md
E2E-004-004--manager-price-suggestion.md
screenshots/
```

Each file must contain the required YAML front matter, links to Feature-004
`spec.md`/`tasks.md`/OpenAPI, actor and test account, `health_urls`, isolated
`test_data.isolation_key`, setup/cleanup, exact selectors or API commands,
observable assertions, expected status/error code and evidence paths. Use
`$unit-test-specify`/`$unit-test-execute` for unit/integration cases and
`$e2e-test-specify`/`$e2e-browser-execute` for BrowserMCP scenarios.

### 10.2 Mandatory gates and run order

Complete T01–T10 before execution: BrowserMCP availability for E2E, frontend
and backend health, developer unit-test handoff, scenario traceability,
adversarial cases, correct actor accounts, stable selectors, observable
assertions, JPEG screenshot directory and isolated cleanup. A missing
environment gate produces `BLOCKED`, not PASS.

Run backend unit/integration tests first, then frontend Vitest tests, then
regression suites and finally BrowserMCP E2E. Stop at the first failed
assertion, preserve the failed evidence and add a `BUG-E2E-*` or appropriate
defect record to `ppswbs-testing/BUG-LOG.md`.

### 10.3 Backend/security coverage

The test agent must cover:

- Guest Basic AI Support succeeds, but Guest image submission is always `403`.
- Member identity, active status, consent, ownership and design context are
  validated before storage/provider calls.
- Empty/overlong prompt, unsupported topic, private-data request and business
  decision request fail with the documented error before mutation.
- Prompt injection returns `AI_PROMPT_INJECTION_DETECTED`, calls no provider and
  changes no business state.
- Image MIME/signature/size/dimensions/metadata/malware/ownership validation
  occurs before Gemini; invalid attempts still count toward the Member quota.
- The fourth attempt in five rolling minutes returns `429 IMAGE_RATE_LIMITED`,
  includes `Retry-After`, stores no image, calls no Gemini and creates no review.
- Concurrent submissions cannot bypass the atomic quota; duplicate idempotent
  retries do not consume another slot.
- Provider output schema/policy validation rejects unsafe output with
  `AI_OUTPUT_POLICY_VIOLATION`; AI cannot change review, booking, payment or
  catalogue price state.
- Temporary images are deleted after provider success and failure; raw image,
  raw provider payload, token and unnecessary PII are not logged or retained.
- Every valid image request reaches human review; Staff/Manager threshold,
  scope, stale version and conflict-of-interest rules are enforced.
- Price suggestion is Manager-only, advisory, range-validated, manually
  recoverable and never automatically applied/published.

### 10.4 Frontend coverage

Test `src/features/designreview/` for Guest chat scope, prompt limits,
unsupported-topic/timeout/retry states, Member description-only/image-only/both
submissions, consent-before-multipart, file validation, rate-limit handling,
401 refresh once, 403 denial, 409 conflict and form/idempotency preservation.
Also verify safe candidate-feature/image-quality rendering, no raw provider
payload, Staff/Manager review controls, Admin technical projection and hidden
price suggestion for Staff/customers.

### 10.5 E2E evidence and acceptance

`E2E-004-001` covers Guest chat and image denial; `E2E-004-002` covers Member
consent, valid submission, processing/result and failure retry;
`E2E-004-003` covers Staff/Manager review, clarification and conflict;
`E2E-004-004` covers Manager price suggestion, invalid fallback and visibility.
Capture JPEG screenshots after submit, pending, success, denial and recovery in
`test-results/screenshots/`. Feature-004 is QA accepted only when all applicable
UTC/E2E cases PASS with reproducible evidence and no open BLOCKER/CRITICAL defect
exists in `BUG-LOG.md`.

### 10.6 Test Execution Evidence & Status Log (2026-11-12)

- **Backend Integration Test Suite**:
  - Command: `mvnw.cmd test -Dtest="edu.fpt.sep490_g22.ppswbs_backend.designreview.*IT"`
  - Results: **44/44 tests passed (100%)** in 2m 04s (`AiSupportIT`, `DataRetentionIT`, `DesignRequestIT`, `HumanReviewIT`, `PriceProposalIT`).
- **Frontend Vitest Component & Screen Suite**:
  - Command: `npm.cmd test -- src/features/designreview`
  - Results: **2/2 test files passed, 21/21 tests passed (100%)** (`designReviewApi.test.js`, `screens.test.jsx`).
- **E2E BrowserMCP Layer & Preflight T01–T10 Gate Execution**:
  - Scenarios generated under `test-results/`:
    - `E2E-004-001--guest-ai-support.md`: Guest chat & image upload denial.
    - `E2E-004-002--member-design-request.md`: Member consent, 10MB limit, 429 rate limit (4th upload in 5m), candidate features, safe projections.
    - `E2E-004-003--staff-manager-review.md`: Staff review, 403 unauthorized reviewer/self-review, 409 stale version conflict.
    - `E2E-004-004--manager-price-suggestion.md`: Advisory price calculation, retry idempotency, Admin technical audit projection.
  - Preflight Health Check:
    - Gate T01 (`browsermcp` tool availability): **PASS** (Đã kết nối Chrome qua Browser MCP extension).
    - Gate T02 (Service health check): **PASS** (Frontend `http://localhost:5173` HTTP 200, Backend `http://localhost:8080` HTTP 401 active).
  - Kết quả thực thi thực tế qua Browser MCP (Live Chrome):
    - `E2E-004-001`: **PASS** — Trợ lý AI trả lời câu hỏi tư vấn workshop thành công, hiển thị đúng disclaimer tham khảo và nhãn KHÁCH (CÔNG KHAI); Route Guard chặn an toàn khách vãng lai truy cập `/design-requests/new` và chuyển hướng về `/login`. Bằng chứng JPEG: [`screenshots/E2E-004-001--success.jpg`](test-results/screenshots/E2E-004-001--success.jpg).
    - `E2E-004-002`, `E2E-004-003`, `E2E-004-004`: **BLOCKED** tại bước xác thực tài khoản Member/Staff/Manager do môi trường backend local chưa cấu hình file private key `FIREBASE_ADMIN_CREDENTIALS_PATH` trong `.env` để backend verify Firebase JWT token.

### 10.7 QA progress update and next test wave (2026-11-12)

> **Thông báo chỉ đạo (User Instruction - 2026-11-12):** Tạm thời ngưng các công việc liên quan đến triển khai kiểm thử cho các kịch bản cần đến các vai trò đặc quyền (Admin, Manager, Staff) cho đến khi môi trường backend cấu hình đầy đủ credentials/service account. Tiếp tục duy trì và bảo toàn các luồng Guest, Member và kiểm chứng tích hợp nội bộ.

Theo xác nhận hiện tại, phạm vi kiểm thử Guest đã hoàn thành (E2E-004-001 PASS). Các dòng
`BLOCKED` lịch sử ở mục 10.6 đối với các kịch bản Member/Staff/Manager phản ánh việc tạm ngưng theo chỉ đạo hoặc thiếu credentials môi trường.

#### Đợt kiểm thử tiếp theo — privileged roles and cross-boundary behavior

1. **Staff review boundary**
   - Đăng nhập Staff bằng test account riêng, mở review queue và chỉ xử lý request
     trong phạm vi được backend trả về.
   - Xác nhận Staff có thể ghi `ACCEPTED`, `REJECTED` hoặc
     `CLARIFICATION_REQUIRED` trong authority/value threshold được cấp.
   - Xác nhận Staff không thể request/view price suggestion, không thể review
     request do chính mình tạo và không thể ghi đè version cũ.
   - Kiểm tra 403 cho role sai, 409 cho stale/duplicate decision và audit event
     sau quyết định.

2. **Manager review and price boundary**
   - Kiểm tra Manager xử lý request vượt ngưỡng 3.000.000 VND hoặc được đánh dấu
     cần Manager authority.
   - Kiểm tra Manager xem price suggestion, điều chỉnh hoặc dùng manual fallback;
     AI suggestion không được tự đổi catalogue price, invoice, payment hoặc
     custom-manufacturing order.
   - Kiểm tra retry cùng `Idempotency-Key`, invalid/out-of-range suggestion,
     provider timeout và quyết định được audit.

3. **Admin technical projection**
   - Xác nhận Admin chỉ xem correlation ID, provider status, timestamp và failure
     evidence tối thiểu; không xem suggested price, commercial explanation hoặc
     thực hiện review/price decision.
   - Kiểm tra Staff, Guest và Member đều bị từ chối khi truy cập projection này.

4. **Security and failure regression**
   - Chạy lại prompt injection, private-data request, unsafe provider output,
     image metadata stripping, temporary image deletion và text retention.
   - Kiểm tra provider không bị gọi khi schema, consent, ownership, rate limit
     hoặc security screening thất bại.

#### Blocker resolution plan

- **BLK-004-01 — Firebase privileged-role credentials:** cấu hình
  `FIREBASE_ADMIN_CREDENTIALS_PATH` bằng secret local được cấp riêng, kiểm tra
  health/auth trước E2E và không commit private key. Nếu chưa có credential,
  ghi `BLOCKED`, không giả lập PASS.
- **BLK-004-02 — Contract verification:** đối chiếu controller, OpenAPI,
  frontend adapter và scenario cho `message/answer`, `designVersion`, price
  response status, error code và role mapping; thêm contract test trước E2E.
- **BLK-004-03 — Database/Flyway and facade evidence:** chạy validation trên
  database sạch và database hiện hữu, xác minh retention, temporary-image
  cleanup, review routing và branch/authority scope; không sửa migration đã chạy.
- **BLK-004-04 — Release evidence:** cập nhật từng scenario bằng command,
  actor, status code, screenshot JPEG và log; chỉ đóng blocker sau khi có bằng
  chứng lặp lại được và không còn BLOCKER/CRITICAL trong `BUG-LOG.md`.

#### Required regression order

```text
Firebase/auth setup
→ backend integration/security
→ frontend Vitest/build
→ Staff/Manager/Admin BrowserMCP
→ Guest/Member smoke regression
→ Feature-011/012 cross-feature regression
→ final Flyway and release audit
```

