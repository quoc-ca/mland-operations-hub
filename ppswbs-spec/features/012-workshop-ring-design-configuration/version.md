# Version 1.1 — Feature 012: Workshop Ring Design Configuration (Full-Stack Implementation & Test Verification)

- **Feature**: 012 — Workshop Ring Design Configuration
- **Author**: Ngô Tiến Đạt
- **Last updated by**: Ngô Tiến Đạt
- **Date created**: 2026-11-08
- **Date updated**: 2026-11-12
- **Source of truth**: Feature-012 `spec.md`, `data-model.md`, `contracts/workshop-ring-design.openapi.yaml`, `V109__workshop_ring_design_configuration.sql`, backend Spring Boot implementation, frontend React 19 + Vite implementation, and synchronized project specifications

> This file is an implementation status index. Detailed behavior remains in
> the Feature-012 artifacts and source code. Phase labels are retained for
> tracing and audit.

---

## 1. Completed

### Phase A — Contract and OpenAPI Gap Resolution

- Đã rà soát toàn bộ đặc tả Feature-012 (`spec.md`, `data-model.md`, `contracts/workshop-ring-design.openapi.yaml`, `design/architecture.md`, `design/workflows.md`).
- **Khắc phục lỗ hổng contract OpenAPI:** Bổ sung schema `DesignOptionsResponse` (bị thiếu ở dòng 33 trong file `workshop-ring-design.openapi.yaml`) định nghĩa đầy đủ các trường:
  - `packageReference`, `rulesVersion`
  - `materials`: Danh sách kim loại/chất liệu (`reference`, `name`, `description`, `purity`, `unitPrice`, `currency`, `difficultyScore`, `imageUrl`)
  - `shapes`: Dáng nhẫn (`reference`, `name`, `description`, `priceAdjustment`, `currency`, `difficultyScore`, `imageUrl`)
  - `sizes`: Danh sách size nhẫn hợp lệ (`6`..`20`)
  - `components`: Đá quý/thành phần đính kèm (`reference`, `name`, `type`, `color`, `cutName`, `unitPrice`, `currency`, `difficultyScore`, `imageUrl`, `maxQuantity`, `allowedPlacements`)
  - `attachments`: Phụ kiện/họa tiết (`reference`, `name`, `type`, `unitPrice`, `currency`, `difficultyScore`, `imageUrl`, `maxQuantity`, `allowedPlacements`)
  - `engravingConfig`: Cấu hình khắc chữ (`supported`, `maxCharacters`, `allowedPlacements`, `priceAdjustment`, `currency`)
  - `models`: Danh sách mẫu thiết kế có sẵn/published models (`modelReference`, `name`, `description`, `imageUrl`, `materialReference`, `shapeReference`).
- Chuẩn hóa toàn bộ request/response DTOs: `CreateDesignRequest`, `SaveDesignVersionRequest`, `ComponentSelectionDto`, `AttachmentSelectionDto`, `EngravingInputDto`, `DesignResponseDto`, `ValidationResultDto`, `EvaluationResultDto`, `AttachDesignRequestDto`, `BookingDesignSnapshotDto`, `RingDesignErrorResponseDto`.

### Phase B — Persistence and Flyway Migration (V109)

- Xác minh lịch sử di chuyển Flyway trong repository: Nhánh `develop` và `dat` đã có migration đến `V108__workshop_booking_settlement_conclusion_statuses.sql`. Đánh số migration mới append-only là [`V109__workshop_ring_design_configuration.sql`](../../../ppswbs_backend/src/main/resources/db/migration/V109__workshop_ring_design_configuration.sql).
- Triển khai schema vật lý cho 8 bảng:
  1. `guest_design_sessions`: Quản lý phiên khách vãng lai, opaque token `session_reference`, hash SHA-256 `session_digest`, liên kết `design_reference`, `issued_at`, `last_activity_at`, `expires_at`, `status` (`ACTIVE`, `EXPIRED`, `REVOKED`).
  2. `ring_designs`: Lưu trữ phiên bản mẫu nhẫn cấu hình theo cơ chế bất biến row-per-version (`design_reference`, `design_version`, `owner_type`, `owner_reference`, `package_reference`, `source_model_reference`, `source_design_id`, `material_reference`, `shape_reference`, `ring_size`, `component_snapshot` JSON, `estimated_price`, `currency`, `difficulty_score`, `rules_version`, `status` (`DRAFT`, `VALIDATED`, `ATTACHED_TO_BOOKING`, `LOCKED`, `ARCHIVED`), `booking_reference`).
  3. `ring_design_idempotency_records`: Lưu trữ idempotency key cho các lệnh ghi.
  4. `ring_materials`: Danh mục kim loại/chất liệu bạc/vàng.
  5. `ring_shapes`: Danh mục phom dáng nhẫn cơ bản/vát cong/xoắn.
  6. `ring_gemstones`: Danh mục đá quý đính kèm (CZ, Moissanite).
  7. `ring_attachments`: Danh mục họa tiết trang trí (Milgrain, Satin matte, dập nổi tim).
  8. `ring_catalogue_models`: Mẫu nhẫn thiết kế sẵn của package làm điểm bắt đầu.
- Cung cấp initial seed data mẫu cho các chất liệu bạc 925, vàng 14K, các dáng nhẫn phổ biến, đá CZ, viền Milgrain, và các catalogue models tiêu chuẩn.

### Phase C — Guest Session Lifecycle & 20-Minute Inactivity Expiry

- Triển khai `GuestDesignSessionService`:
  - Khởi tạo session với opaque reference dạng `GDS-{UUID}` và lưu trữ SHA-256 digest (`session_digest`) phục vụ tra cứu bảo mật.
  - Áp dụng thời gian hết hạn sau 20 phút không hoạt động: Mỗi lần gọi API đọc/ghi hợp lệ sẽ tự động gọi `touch()` gia hạn thêm 20 phút (`expiresAt = now + 20 minutes`).
  - Khi session không hoạt động quá 20 phút hoặc đã hết hạn, hệ thống trả về mã lỗi HTTP `401 DESIGN_SESSION_EXPIRED` mà không làm lộ dữ liệu nội bộ.
  - Bổ sung tác vụ định kỳ `@Scheduled(fixedRate = 300000)` (quét 5 phút/lần) tự động dọn dẹp và cập nhật trạng thái `EXPIRED` cho các phiên không hoạt động.

### Phase D — Option Rules & Manager Advisory Evaluation

- Triển khai `DesignOptionRulesPort` và `ManagerDesignRuleAdapter`:
  - `loadPackageOptions(packageReference, branchReference)`: Đọc và trả về danh mục chất liệu, dáng nhẫn, đá quý, phụ kiện, quy chuẩn size 6..20, và cấu hình khắc chữ tối đa 50 ký tự.
  - `validateCompatibility(request, branchReference)`: Kiểm tra tính hợp lệ và tương thích của từng thuộc tính nhẫn. Kiểm tra ràng buộc chi nhánh (ví dụ: chi nhánh chưa hỗ trợ lò nung vàng sẽ trả về lỗi `DESIGN_BRANCH_INCOMPATIBLE`).
  - `evaluate(request)`: Tính toán giá tham khảo (`estimatedPrice`) và điểm độ khó (`difficultyScore` làm tròn 4 chữ số thập phân DECIMAL 10,4).
  - Thuộc tính `advisory: true` được bảo đảm; không làm thay đổi giá gói workshop hay tiền cọc 50%.

### Phase E — Application Service, Idempotency & Booking Facade

- Triển khai `WorkshopRingDesignApplicationService`:
  - `createDraft`: Tạo bản nháp nhẫn ban đầu từ blank hoặc published model, gắn với Member ID hoặc Guest Session.
  - `getDesign`: Truy vấn phiên bản thiết kế mới nhất với kiểm tra quyền sở hữu nghiêm ngặt (Member account match hoặc Guest digest match), từ chối người dùng khác bằng `403 DESIGN_ACCESS_DENIED`.
  - `saveVersion`: Tạo phiên bản mới (`design_version + 1`) với trạng thái `VALIDATED`, lưu lineage `source_design_id`, kiểm tra khóa lạc quan qua header `If-Match-Version`, trả về `409 DESIGN_VERSION_CONFLICT` nếu dữ liệu bị thay đổi đồng thời.
  - `evaluateDesign`: Thẩm định lại và trả về phiên bản được định giá tham khảo.
  - `archive`: Đánh dấu `status = ARCHIVED` (soft-delete), cấm xóa cứng dữ liệu lịch sử theo chính sách lưu trữ 3 năm, cấm lưu trữ thiết kế đã gắn với booking (`LOCKED` hoặc `ATTACHED_TO_BOOKING`).
  - `attachToBooking`: Revalidate tương thích chi nhánh, gắn `bookingReference`, chuyển sang `ATTACHED_TO_BOOKING` và xuất `BookingDesignSnapshotDto`.
  - `RingDesignIdempotencyService`: Bảo vệ trùng lặp lệnh ghi qua `Idempotency-Key` và SHA-256 payload hashing, phát hiện xung đột dữ liệu payload bị thay đổi cùng key (`409 IDEMPOTENCY_CONFLICT`).
- Triển khai `WorkshopRingDesignFacade` & `WorkshopRingDesignFacadeImpl`:
  - Cung cấp giao diện một chiều phục vụ Feature 003 Workshop Booking:
    - `attachAndLockDesignForBooking(...)`: Thẩm định và gắn thiết kế trong giao dịch đặt lịch.
    - `finalizeLock(...)`: Khóa vĩnh viễn thiết kế (`LOCKED`) khi đặt lịch thành công.
    - `unlockDesignOnBookingRollback(...)`: Trả thiết kế về trạng thái `VALIDATED` để khách hàng thử đặt lại nếu quá trình thanh toán/booking thất bại.
    - `getDesignSnapshot(...)`: Trích xuất snapshot bất biến của thiết kế.

### Phase F — REST Controllers, Security Integration & Exception Handling

- Triển khai REST Controllers:
  - `WorkshopDesignOptionsController`: `GET /api/v1/workshop-design-options`
  - `WorkshopRingDesignController`:
    - `POST /api/v1/workshop-designs`
    - `GET /api/v1/workshop-designs/{designId}`
    - `POST /api/v1/workshop-designs/{designId}/versions`
    - `POST /api/v1/workshop-designs/{designId}/evaluate`
    - `POST /api/v1/workshop-designs/{designId}/archive`
    - `POST /api/v1/workshop-designs/{designId}/attach-to-booking`
- Triển khai `WorkshopRingDesignExceptionHandler`: Chuẩn hóa response theo contract OpenAPI:
  - `400 DESIGN_INPUT_INVALID`: Thiếu trường, định dạng sai, thiếu header bắt buộc.
  - `401 DESIGN_SESSION_EXPIRED`: Phiên làm việc khách vãng lai đã hết hạn.
  - `403 DESIGN_ACCESS_DENIED`: Truy cập trái phép thiết kế của người dùng khác.
  - `404 DESIGN_NOT_FOUND`: Không tìm thấy thiết kế nhẫn.
  - `409 DESIGN_VERSION_CONFLICT` / `DESIGN_LOCKED`: Xung đột phiên bản stale hoặc thiết kế đã khóa.
  - `422 DESIGN_OPTION_INVALID` / `DESIGN_BRANCH_INCOMPATIBLE`: Lỗi nghiệp vụ tương thích tùy chọn/chi nhánh.
- Tích hợp Spring Security & Filter:
  - `SecurityConfiguration.java`: Cấu hình `permitAll()` cho các endpoint thiết kế nhẫn công khai của khách vãng lai và cấu hình CORS hỗ trợ header `If-Match-Version`, `X-Guest-Design-Session`.
  - `IdentityGateFilter.java`: Bỏ qua yêu cầu Bearer token khi không có header Authorization đối với các route của Feature 012 để Guest thao tác mượt mà.

### Phase G — JDK 21 Backend Verification & Automated Test Evidence

- Môi trường kiểm thử: **OpenJDK 21.0.7 LTS** (Microsoft-11369940).
- **Bộ kiểm thử Feature 012:**
  - Lệnh: `mvnw.cmd test -Dtest="edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.*Test"`
  - Kết quả: **4/4 test classes passed, 25/25 tests passed (100%)** trong 52.3s:
    - `GuestDesignSessionServiceTest`: 5/5 tests passed (Session issuance, SHA-256 digest, 20-min inactivity expiry, touch extension, design binding).
    - `ManagerDesignRuleAdapterTest`: 4/4 tests passed (Load options, compatibility validation, branch restriction, advisory pricing and difficulty score calculation).
    - `WorkshopRingDesignApplicationServiceTest`: 8/8 tests passed (Guest draft creation, Member draft creation, row-per-version increment, optimistic locking 409 conflict, ownership isolation 403, soft-delete archive, booking attachment, branch incompatibility 422).
    - `WorkshopRingDesignControllerMockMvcTest`: 8/8 tests passed (All REST endpoints, headers `Idempotency-Key`, `If-Match-Version`, `X-Guest-Design-Session`, validation 400, session expired 401, JSON serialization).
- **Kiểm thử kiến trúc Spring Modulith:**
  - Lệnh: `mvnw.cmd test -Dtest="ModulithArchitectureTests"`
  - Kết quả: **5/5 tests passed (100%)** (`ModulithArchitectureTests.java`), xác nhận mô-đun `features.ringdesignconfiguration` tuân thủ 100% ranh giới package, không vi phạm circular dependency.
- **Kiểm thử hồi quy (Regression Testing):**
  - Lệnh: `mvnw.cmd test -Dtest="edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.*Test"`
  - Kết quả: **33/33 Feature 011 tests passed (100%)**, đảm bảo không có bất kỳ regression nào trong toàn bộ hệ thống.

---

### Phase H — Frontend React 19 + Vite Implementation & Test Verification (2026-11-10)

- **Triển khai kiến trúc UI Frontend chuẩn hóa:**
  - `api/workshopRingDesignApi.js`: Adapter kết nối REST API backend, hỗ trợ đầy đủ 7 endpoint, quản lý `sessionStorage` token `mland_guest_design_session` cho khách vãng lai, tự động inject headers `X-Guest-Design-Session`, `If-Match-Version` và `Idempotency-Key`.
  - `api/useWorkshopRingDesignApi.js`: Custom hook tích hợp `useHttp` dùng chung.
  - `domain/designDraft.js`: Quản lý cấu trúc dữ liệu bản nháp (Blank hoặc Model template), xác thực phía client (size 6..20, nội dung khắc tối đa 50 ký tự, kiểm tra ký tự hợp lệ), chuẩn hóa payload gửi lên backend.
  - `domain/designPreview.js`: Chuyển đổi dữ liệu bản nháp thành Preview Model phản hồi tương tác thời gian thực:
    - Bảng màu kim hoàn (Bạc Ý 925, Vàng 14K, Vàng Hồng 14K, Vàng Trắng 14K) với gradient đa điểm (highlight, primary, shadow).
    - Kiểu phom dáng đai nhẫn (Tròn cổ điển, Vát cong lòng trong, Dáng xoắn vô cực, Búa gõ thủ công).
    - Màu sắc và ánh quang (glow) của đá quý CZ (Trắng, Lam, Đỏ) và Moissanite.
    - Tọa độ chiếu văn bản khắc chữ cá nhân hóa theo cung tròn lòng trong hoặc mặt ngoài đai nhẫn.
  - `domain/designErrors.js`: Bảng ánh xạ mã lỗi typed (`DESIGN_INPUT_INVALID`, `DESIGN_BRANCH_INCOMPATIBLE`, `DESIGN_SESSION_EXPIRED`, `DESIGN_VERSION_CONFLICT`, `DESIGN_LOCKED`, v.v.) sang thông báo người dùng thân thiện.
  - `messages.js`: Từ điển song ngữ Anh - Việt đầy đủ cho toàn bộ tính năng.
  - `ring-design.css`: Giao diện Atelier Light tối ưu 2 cột trên Desktop (cột trái điều khiển, cột phải Live Preview cố định) và tự động xếp chồng (stacked) trên thiết bị di động.
  - `application/`:
    - `useWorkshopDesignOptions.js`: Hook tải tùy chọn gói workshop.
    - `useWorkshopDesignDraft.js`: Hook quản lý trạng thái bản nháp nhẫn và cập nhật thông số reactive.
    - `useWorkshopDesignCommand.js`: Hook thực thi các lệnh ghi bất đồng bộ có bảo vệ Idempotency.
  - `ui/`:
    - `RingPreview.jsx`: Component hiển thị Live Preview 2D bằng SVG thuần, không phụ thuộc thư viện 3D nặng nề, tương thích 100% mọi trình duyệt.
    - `DesignControlField.jsx`: Component wrapper trường nhập liệu chuẩn hóa.
    - `RingDesignEntryPage.jsx` (`/workshops/:packageReference/design`): Màn hình điểm vào luồng, hiển thị banner cảnh báo giá tham khảo và 2 lựa chọn (Chọn mẫu catalogue / Tự thiết kế từ phôi trắng).
    - `RingModelSelectionPage.jsx` (`/workshops/:packageReference/design/models`): Danh sách mẫu gợi ý theo gói workshop kèm ảnh minh họa, độ khó và chi phí ước tính.
    - `RingConfigurationPage.jsx` (`/workshops/:packageReference/design/configure`): Trình cấu hình 2 cột chính, tích hợp Live Preview và các bộ điều khiển chất liệu, dáng nhẫn, size, đá quý, họa tiết, khắc chữ.
    - `DesignEvaluationPage.jsx` (`/workshops/:packageReference/design/:designId`): Kết quả thẩm định kỹ thuật, điểm độ khó, chi phí tham khảo, nút chuyển tiếp sang đặt lịch workshop và thao tác lưu trữ/xóa bản nháp.
    - `BranchCompatibilityPage.jsx` (`/workshops/:packageReference/design/recovery`): Màn hình phục hồi khi chi nhánh không hỗ trợ kỹ thuật chế tác đã chọn.
    - `MemberDesignHistoryPage.jsx` (`/my/designs`): Lịch sử thiết kế cá nhân của hội viên (bảo vệ bởi `<RequireSession>`).
  - `routes.jsx`: Khai báo 6 routes và tích hợp hoàn chỉnh vào [`ppswbs_frontend/src/app/router.jsx`](../../../ppswbs_frontend/src/app/router.jsx).

- **Kiểm thử tự động Frontend (Vitest + Testing Library trên jsdom):**
  - `src/features/ringdesignconfiguration/tests/workshopRingDesignApi.test.js`: **10/10 tests passed** (Session storage handling, full REST endpoints, headers `X-Guest-Design-Session`, `If-Match-Version`, `Idempotency-Key`).
  - `src/features/ringdesignconfiguration/tests/designDomain.test.js`: **9/9 tests passed** (Default blank draft, model populating, client validation, payload normalization, SVG preview model derivation, typed error translation).
  - `src/features/ringdesignconfiguration/tests/screens.test.jsx`: **13/13 tests passed** (Toàn bộ 6 màn hình giao diện, luồng điều hướng, cập nhật preview, tạo và lưu phiên bản, thẩm định và handoff đặt lịch).
  - **Tổng kết kiểm thử Feature 012 Frontend: 32/32 tests passed (100%)**.
  - **Kiểm thử hồi quy Frontend:**
    - `src/features/designreview/test/`: **21/21 tests passed (100%)** (Feature 011/004 regression suite).
  - **Kiểm tra Production Build:**
    - Lệnh: `npm.cmd run build` trong `ppswbs_frontend`.
    - Kết quả: **Vite build thành công 100% trong 3.27s**, không có lỗi cú pháp, bundle hay import.

---

## 2. Blockers and Decisions Resolved

| ID | Vấn đề / Blockers | Quyết định đã chốt & Thực thi | Trạng thái |
|---|---|---|---|
| **BLK-01** | Truy cập Figma qua MCP bị gián đoạn do token ủy quyền | Người dùng và Tech Lead đã đồng thuận lấy bộ Node IDs Figma (`457:1553`, `457:1554`, `457:1655`, `457:1698`, `457:1749`, `457:1795`) làm visual baseline, mã hóa trực tiếp giao diện Atelier Light vào CSS và JSX chuẩn. | **Đã giải quyết** |
| **BLK-02** | Lưu trữ token phiên khách vãng lai `X-Guest-Design-Session` | Thống nhất sử dụng `sessionStorage` (khóa `mland_guest_design_session`). Token tồn tại xuyên suốt navigation trong phiên làm việc của tab và tự động bị xóa bỏ khi người dùng đóng tab, bảo đảm an toàn dữ liệu và tuân thủ TTL 20 phút. | **Đã giải quyết** |
| **BLK-03** | Cơ chế Live Preview (2D SVG vs 3D Canvas) | Thống nhất dựng Live Preview 2D bằng SVG tương tác thuần (client-side SVG reactivity), đổi màu gradient kim loại, độ dày đai nhẫn, đá quý và văn bản khắc tức thì mà không cần cài đặt thư viện ngoài (Three.js/Babylon) gây phình to bundle. | **Đã giải quyết** |
| **BLK-04** | Luồng chuyển tiếp sau thẩm định sang Đặt lịch Workshop | Thống nhất điều hướng khách hàng sang URL `/workshops/booking?packageReference=...&designId=...`. Chi phí ước tính hiển thị với nhãn Advisory, hoàn toàn không can thiệp hay thay đổi chính sách tiền cọc 50% hoặc giá gốc gói workshop. | **Đã giải quyết** |

---

## 4. QA test implementation and execution plan (2026-11-12)

This is the testing-agent handoff. Feature-012 contains historical
implementation/test notes, but the QA agent must re-run the scenarios with fresh
environment, command and evidence before reporting current PASS.

### 4.1 Required artifacts

Create under `test-results/`:

```text
UTC-012-A-001--guest-session-and-draft-lifecycle.md
UTC-012-A-002--versioning-validation-and-concurrency.md
UTC-012-A-003--booking-attachment-and-privacy.md
UTC-012-B-001--frontend-preview-and-api-adapter.md
E2E-012-001--model-or-blank-entry.md
E2E-012-002--live-ring-configuration.md
E2E-012-003--save-evaluate-and-version-history.md
E2E-012-004--branch-compatibility-recovery.md
E2E-012-005--guest-expiry-and-member-ownership.md
screenshots/
```

Each scenario must have YAML front matter, traceability to Feature-012
`spec.md`, `design/`, `tasks.md` when available and OpenAPI, actor/test account,
`health_urls`, isolated `test_data.isolation_key`, setup/cleanup, exact
selectors/API commands, observable assertions, expected status/error code and
evidence paths. Use the four testing skills prescribed by
`ppswbs-testing/AGENT.md`.

### 4.2 Gates and run order

Complete T01–T10 before any execution: BrowserMCP, backend/frontend health,
developer unit-test handoff, scenario traceability, adversarial cases, correct
Guest/Member actors, stable selectors, observable assertions, JPEG evidence
directory and isolated cleanup. Run backend unit/integration tests, frontend
Vitest tests and Feature-003/004/011 regression tests before BrowserMCP E2E.
An unavailable service, MySQL, JDK 21 or BrowserMCP produces `BLOCKED`, not
PASS. Stop at the first failed assertion and record the evidence.

### 4.3 Backend coverage

Verify:

- Guest session returns only an opaque reference, binds to the correct design,
  touches activity on permitted requests, expires after 20 minutes inactivity
  and rejects mutation/attach with `DESIGN_SESSION_EXPIRED`.
- Member can read/write only own designs; cross-owner access is safely denied.
- Published model and blank design use package-scoped server options; inactive,
  unpublished, unknown, package-incompatible and branch-incompatible options are
  rejected before version persistence.
- Shape/material/size/component/attachment/engraving, quantity, placement and
  text constraints are enforced server-side.
- Client estimate/difficulty/rules version cannot override server evaluation;
  estimate remains advisory and cannot mutate package price, deposit, payment or
  capacity.
- Every successful Save/Evaluate creates an immutable row-per-version with
  lineage; concurrent stale writes return `DESIGN_VERSION_CONFLICT` without a
  partial version, and duplicate idempotent commands replay deterministically.
- Archive is soft-delete and retains version/evaluation/audit history.
- `attachAndLockDesignForBooking(...)` validates ownership/session, package,
  branch and `VALIDATED` state, returns the immutable snapshot and locks only on
  booking commit.
- Booking rollback preserves a retryable design and Feature-012 creates no
  payment, capacity hold or ticket side effect.
- Feature-003 cannot access Feature-012 repositories/entities directly and the
  Modulith boundary remains valid.

### 4.4 Frontend coverage

Test the `ringdesignconfiguration` slice for model/blank loading and empty
states, server-driven controls, immediate 2D SVG preview updates, current value
and helper text, advisory estimate warning, 1440px two-column and 390px stacked
layouts, keyboard/accessibility behavior, Save/Evaluate pending state,
idempotency/version headers, validation focus, 401 expiry, 403 ownership, 409
stale version, 422 incompatibility, 503 retry, Member history/archive and
branch recovery without booking/payment side effects.

### 4.5 E2E scenarios and evidence

`E2E-012-001` covers package entry, model/blank selection and option loading.
`E2E-012-002` covers material/gemstone/size/band-width changes and live preview.
`E2E-012-003` covers Save/Evaluate, validation, Member history, versioning and
retry. `E2E-012-004` covers branch incompatibility and correction. `E2E-012-005`
covers Guest expiry, Guest isolation and Member ownership.

Capture JPEG screenshots for initial load, model/blank selection, live preview,
pending command, validated result, field error, stale conflict, branch recovery
and expired session under `test-results/screenshots/`. Record failures in
`ppswbs-testing/BUG-LOG.md` using the mandated `BUG-*` template.

### 4.6 QA acceptance gate

Feature-012 is QA accepted only when all applicable UTC/E2E scenarios PASS with
reproducible evidence, frontend/backend regression is green, migration/Flyway
history is verified, the booking facade is exercised, and no open
BLOCKER/CRITICAL defect remains. Unresolved physical schema, Guest session,
rule-facade or booking-facade approval remains a release blocker.

## 5. Incomplete / Next Steps (Release Gates)

### Giai đoạn 1: Quản trị danh mục động cho Manager (FE-09)
- Xây dựng màn hình quản lý danh mục nhẫn cho Manager để cập nhật động các mẫu đá quý, chất liệu và phụ kiện thay vì sử dụng seed ban đầu.

### Giai đoạn 2: Phê duyệt chính thức Revision Figma
- Cập nhật mã revision Figma chính thức khi Tech Lead và UI Designer hoàn thiện rà soát giao diện visual.
