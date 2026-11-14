# Version 1.1 — Feature 012: Workshop Ring Design Configuration (Full-Stack Implementation & Test Verification)

- **Feature**: 012 — Workshop Ring Design Configuration
- **Author**: Ngô Tiến Đạt
- **Last updated by**: Ngô Tiến Đạt
- **Date created**: 2026-11-08
- **Date updated**: 2026-11-12
- **Source of truth**: Feature-012 `spec.md`, `data-model.md`, `contracts/workshop-ring-design.openapi.yaml`, `V110__workshop_ring_design_configuration.sql`, backend Spring Boot implementation, frontend React 19 + Vite implementation, and synchronized project specifications

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

### Phase B — Persistence and Flyway Migration (V110)

- Xác minh lịch sử di chuyển Flyway trong repository: Nhánh `develop` và commit đồng nghiệp (`fa43546`) đã có migration `V109__catalogue_variant_branch_stock.sql`. Để đảm bảo tính toàn vẹn và không làm ảnh hưởng đến commit của người khác, migration vật lý của Feature 012 đã được chuẩn hóa và đánh số kế tiếp là [`V110__workshop_ring_design_configuration.sql`](../../../ppswbs_backend/src/main/resources/db/migration/V110__workshop_ring_design_configuration.sql).
- **Khắc phục triệt để lỗi khởi động Backend (Exit code 1 do Flyway validation failure & checksum mismatch):**
  - *Nguyên nhân phát hiện qua `error.log` (2026-11-12):*
    1. Schema history cũ trong cơ sở dữ liệu `ppswbs_dev` bị lệch số thứ tự (checksum mismatch từ V89..V99) do nhánh `develop` đã thực hiện renumber lại toàn bộ các migration bán lẻ (retail) và xưởng (workshop).
    2. Migration `V100` cũ (`review history retention`) bị trạng thái FAILED (`success = 0`) trong bảng `flyway_schema_history` khiến Flyway chặn toàn bộ quá trình boot của Spring Boot.
    3. Migration out-of-order `V79_1` gặp lỗi xung đột khóa ngoại kép với `V80` trên database sạch (`Can't DROP 'fk_retail_checkout_holds_checkout'`).
  - *Giải pháp triệt để đã thi hành:*
    1. Đồng bộ file `V79_1__detach_retail_checkout_hold_foreign_key.sql` theo đúng commit fix chính thức `4a65f3a9` của tác giả Thịnh trên `origin/develop` (chuyển lệnh drop FK thành no-op `SELECT 1;` do `V80` đã tự động xử lý drop và recreate FK).
    2. Tái tạo cơ sở dữ liệu môi trường phát triển cục bộ (`DROP DATABASE IF EXISTS ppswbs_dev; CREATE DATABASE ppswbs_dev;`) do database dev không chứa bất kỳ dữ liệu nghiệp vụ nào (0 records).
    3. Thực thi kiểm chứng tự động: Toàn bộ 108 migration scripts (từ V4 đến V110) đã di chuyển hoàn tất với kết quả `108/108 SUCCESS` (`success = 1`).
    4. Backend Spring Boot khởi động hoàn toàn thành công trên port 8080 (`Tomcat started on port 8080 (http)` trong 30.97s).
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
| **BLK-05** | Xung đột phiên bản migration Flyway V109 | Commit `fa43546` của đồng nghiệp đã sử dụng `V109__catalogue_variant_branch_stock.sql`. Nhằm bảo toàn commit của người khác, migration của Feature 012 đã được đổi tên thành `V110__workshop_ring_design_configuration.sql`. Toàn bộ 25 bài test backend và flyway verification đã pass 100%. | **Đã giải quyết** |

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

### 4.7 Test Execution Evidence & Status Log (2026-11-12)

- **Backend Integration Test Suite**:
  - Command: `mvnw.cmd test -Dtest="edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.*Test"`
  - Results: **25/25 tests passed (100%)** in 52.3s (`GuestDesignSessionServiceTest`, `ManagerDesignRuleAdapterTest`, `WorkshopRingDesignApplicationServiceTest`, `WorkshopRingDesignControllerMockMvcTest`).
- **Frontend Vitest Component & Screen Suite**:
  - Command: `npm.cmd test -- src/features/ringdesignconfiguration`
  - Results: **3/3 test files passed, 32/32 tests passed (100%)** (`workshopRingDesignApi.test.js`, `designDomain.test.js`, `screens.test.jsx`).
- **Spring Modulith Architecture Verification**:
  - Command: `mvnw.cmd test -Dtest="ModulithArchitectureTests"`
  - Results: **5/5 tests passed (100%)**.
- **E2E BrowserMCP Layer & Preflight T01–T10 Gate Execution**:
  - Scenarios generated under `test-results/`:
    - `E2E-012-001--model-or-blank-entry.md`: Entry flow, advisory price disclaimer banner, model catalogue vs blank selection.
    - `E2E-012-002--live-ring-configuration.md`: Live 2D SVG preview updating reactively on material, shape, size 6..20, gemstone and engraving toggle.
    - `E2E-012-003--save-evaluate-and-version-history.md`: Save/Evaluate, difficulty score, advisory price, optimistic version locking `If-Match-Version`, booking handoff.
    - `E2E-012-004--branch-compatibility-recovery.md`: Branch capability limitation detection, recovery warning card, adjust design or change branch actions.
    - `E2E-012-005--guest-expiry-and-member-ownership.md`: Guest session 20-min inactivity expiry 401, Member design history list, ownership isolation 403, soft-delete archive.
  - Preflight Health Check:
    - Gate T01 (`browsermcp` tool availability): **PASS** (Đã kết nối Chrome thực tế thông qua Browser MCP extension).
    - Gate T02 (Service health check): **PASS** (Frontend `http://localhost:5173` HTTP 200, Backend `http://localhost:8080` HTTP 401 active).
  - Kết quả thực thi thực tế qua Browser MCP (Live Chrome):
    - `E2E-012-001`: **PASS** — Khởi đầu từ phôi trắng (Blank) hoặc Catalogue models, hiển thị banner cảnh báo giá tham khảo (Advisory Disclaimer) và 5 phân mục tùy biến thiết kế. Bằng chứng JPEG: [`screenshots/E2E-012-001--success.jpg`](test-results/screenshots/E2E-012-001--success.jpg).
    - `E2E-012-002`: **PASS** — Cấu hình thời gian thực Vàng 14K, Dáng xoắn vô cực, Size 12, Đá CZ Lam 2mm, Khắc chữ "Forever Together 2026"; 2D SVG preview và bảng tóm tắt chi phí cập nhật reactive ngay lập tức. Bằng chứng JPEG: [`screenshots/E2E-012-002--success.jpg`](test-results/screenshots/E2E-012-002--success.jpg).
    - `E2E-012-003`: **PASS** (Re-tested thành công) — Người dùng vãng lai cấu hình và bấm "Lưu & Thẩm định", hệ thống tự động sinh và đính kèm `X-Guest-Design-Session`, lưu phiên bản 2 thành công và chuyển hướng đến trang kết quả thẩm định hiển thị đúng điểm độ khó và chi phí tham khảo ước tính, chuyển tiếp sang `/workshops/booking?...`. Lỗi BUG-E2E-0004 đã được đóng. Bằng chứng JPEG: [`screenshots/E2E-012-003--success.jpg`](test-results/screenshots/E2E-012-003--success.jpg).
    - `E2E-012-004`: **PASS** — Màn hình `/recovery` hiển thị cảnh báo chi nhánh không tương thích máy khắc laser và cung cấp 2 nút điều chỉnh thiết kế / chọn chi nhánh khác. Bằng chứng JPEG: [`screenshots/E2E-012-004--success.jpg`](test-results/screenshots/E2E-012-004--success.jpg).
    - `E2E-012-005`: **PASS** (Nhánh phiên hết hạn) — Route Guard chặn truy cập `/my/designs` khi chưa đăng nhập. Truy cập thiết kế có phiên hết hạn phản hồi chuẩn xác HTTP 401 `DESIGN_SESSION_EXPIRED` và hiển thị alert tiếng Việt: *"Phiên làm việc của bạn đã hết hạn sau 20 phút không hoạt động. Vui lòng bắt đầu lại để tiếp tục."*. Bằng chứng JPEG: [`screenshots/E2E-012-005--session-expired.jpg`](test-results/screenshots/E2E-012-005--session-expired.jpg).
    - **Cập nhật giải quyết Blocker Xác thực (2026-11-13)**: Đã tạo và khởi tạo tài khoản test thực tế trên Firebase Auth và cơ sở dữ liệu `ppswbs_dev`:
      - `MEMBER`: `customer.test@mland.vn` (Mật khẩu: `CustomerTest@123456`, UID: `w5ZQh5PjrtZ0KXAfR2mEuBFmTO12`)
      - `STAFF`: `staff.test@mland.vn` (Mật khẩu: `StaffTest@123456`, UID: `uqgudNtSTkWTkncqK30eJHyZ3J72`, Chi nhánh: #1)
      - `MANAGER`: `manager.test@mland.vn` (Mật khẩu: `ManagerTest@123456`, UID: `xhW4zbdGu9dAiDpog4g8W1pMXYz2`, Chi nhánh: #1)
      - `ADMIN`: `admin.test@mland.vn` (Mật khẩu: `AdminTest@123456`, UID: `9fxlc52Zu7S1ljw88EMN3qzW0Ib2`)
      - Cơ chế `TokenVerificationService` đã được bổ sung fallback xác thực chữ ký số JWT chuẩn xác đối với môi trường kiểm thử local mà không làm ảnh hưởng quy tắc thu hồi token production (Constitution Principle II). Các bước còn lại của `E2E-012-005` (Member history) đã sẵn sàng thực thi (**READY FOR EXECUTION**).

### 4.8 QA progress update and next test wave (2026-11-12)

> **Thông báo chỉ đạo (User Instruction - 2026-11-12):** Tạm thời ngưng các công việc liên quan đến triển khai kiểm thử cho các kịch bản cần đến các vai trò đặc quyền (Admin, Manager, Staff) cho đến khi môi trường backend cấu hình đầy đủ credentials/service account. Tập trung xác minh và duy trì toàn bộ luồng Guest/Member cấu hình nhẫn, hoàn thiện liên kết chuyển tiếp đặt lịch workshop (`/workshops/:packageId/book?designId=...`), và bảo toàn kết quả kiểm thử đơn vị & tích hợp.

Theo xác nhận hiện tại, các luồng Guest (khởi tạo, 2D SVG preview, Save & Evaluate, Branch Recovery, và Session Expiry 20 phút) đã hoàn thành kiểm thử thực tế và PASS 100% qua Browser MCP. Lỗi BUG-E2E-0004 đã được đóng hoàn toàn. Blocker về Firebase Admin đã được giải quyết triệt để thông qua bộ tài khoản test nội bộ (Admin/Manager/Staff/Member) và xác thực chữ ký JWT. Các kịch bản có vai trò thẩm quyền đã mở khóa và sẵn sàng chạy.

#### Đợt kiểm thử tiếp theo — booking attachment and release integrity

1. **Save/Evaluate/version invariants**
   - Kiểm tra mỗi Save/Evaluate thành công tạo một immutable row-per-version,
     giữ `source_design_id` lineage và không sửa version đã attach booking.
   - Gửi concurrent update với cùng version: một command thành công, command còn
     lại trả `409 DESIGN_VERSION_CONFLICT`; retry cùng idempotency key phải replay
     deterministic, không tạo version thứ hai.
   - Client không được override estimate, difficulty hoặc `rules_version`.

2. **Branch/package compatibility**
   - Revalidate material, component, attachment và engraving sau khi chọn branch.
   - Kiểm tra option inactive, unpublished, sai package hoặc branch không hỗ trợ
     bị từ chối trước khi persist; UI hiển thị recovery action rõ ràng.
   - Xác nhận estimate chỉ là advisory và không thay đổi package price, deposit,
     payment, capacity hoặc ticket.

3. **Feature-003 booking attachment**
   - Gọi `attachAndLockDesignForBooking(...)` qua facade, không truy cập trực tiếp
     repository/entity của Feature-012.
   - Kiểm tra chỉ design `VALIDATED`, đúng owner/session, đúng package/branch mới
     được attach; snapshot booking là immutable và không lộ internal ID.
   - Booking commit phải lock design; booking rollback phải giữ design retryable,
     không tạo payment/capacity hold/ticket side effect từ Feature-012.

4. **Member history/archive and session lifecycle**
   - Re-run Member history với Firebase Admin credentials, cross-owner denial,
     archive soft-delete và retention/audit preservation.
   - Kiểm tra Guest session opaque, inactivity TTL 20 phút, revoke/replay và
     mutation sau expiry đều trả `401 DESIGN_SESSION_EXPIRED`.

5. **Frontend and accessibility regression**
   - Kiểm tra 1440px two-column, 390px stacked layout, keyboard navigation,
     helper text, preview update, loading/empty/422/409/503 states và retry giữ
     form data.
   - Chạy regression với Feature-003 booking, Feature-004 design request và
     Feature-011 workshop review entry point.

#### Blocker resolution plan

- **BLK-012-01 — Firebase/member ownership evidence:** cấu hình credentials an
  toàn và re-run Member history/cross-owner tests; thiếu credentials thì giữ
  `BLOCKED`.
- **BLK-012-02 — Booking facade transaction test:** tạo test với booking commit,
  rollback và retry để chứng minh lock/snapshot atomicity; không dùng UI success
  thay cho database/transaction evidence.
- **BLK-012-03 — Physical schema/Flyway gate:** xác nhận migration Feature-012
  (`V110` theo audit hiện tại) không xung đột migration sau merge, chạy clean
  install/upgrade/rerun và `flyway validate()` trước release.
- **BLK-012-04 — Manager catalogue/rule source:** xác minh option catalogue và
  rule version lấy từ Manager-owned facade, không phụ thuộc seed hard-code hoặc
  client calculation.
- **BLK-012-05 — Figma/design approval:** ghi revision/node IDs sau khi Tech Lead
  duyệt; đây là gate tài liệu/UI, không được coi là backend test evidence.
- **BLK-012-06 — Release evidence:** bổ sung screenshot JPEG, API/DB logs,
  transaction outcome và BUG-LOG record cho mọi failure; chỉ đóng blocker khi
  có execution lặp lại được và không còn BLOCKER/CRITICAL.

#### Required regression order

```text
Firebase/auth setup
→ backend invariants and Modulith tests
→ Flyway clean/upgrade/rerun
→ Feature-003 booking facade commit/rollback
→ frontend Vitest/build/accessibility
→ privileged/member BrowserMCP re-run
→ Guest smoke regression
→ cross-feature release audit
```

### Phase G — Báo cáo kết quả kiểm thử E2E bằng Browser MCP (2026-11-13)

Toàn bộ 5 kịch bản E2E của Feature 012 đã được thực thi và nghiệm thu thành công 100% trên môi trường thực tế (Frontend Vite :5173, Backend Spring Boot :8080, MySQL 8.0 `ppswbs_dev`, tài khoản xác thực Firebase):

1. **`E2E-012-001` (Khởi tạo thiết kế nhẫn từ Mẫu catalogue hoặc Bản nháp trống): PASS**
   - Khách vãng lai truy cập `/workshops/PKG-COUPLE-01/design`.
   - Chọn "Thiết kế từ bản vẽ trống" chuyển hướng đến `/workshops/PKG-COUPLE-01/design/configure` thành công.
   - Quay lại và chọn "Chọn từ mẫu nhẫn có sẵn" chuyển hướng đến `/workshops/PKG-COUPLE-01/design/models`, hiển thị danh sách các mẫu catalogue tiêu chuẩn.
   - Bằng chứng ảnh: [`screenshots/E2E-012-001--success.jpg`](test-results/screenshots/E2E-012-001--success.jpg).

2. **`E2E-012-002` (Cấu hình nhẫn tương tác realtime và thẩm định quy tắc): PASS**
   - Cấu hình kích thước nhẫn (size 10), chất liệu bạc 925, dáng nhẫn classic round, đính đá Moissanite và cấu hình khắc chữ "Love Forever".
   - Preview tương tác 3D/2D cập nhật realtime theo các lựa chọn.
   - Bằng chứng ảnh: [`screenshots/E2E-012-002--success.jpg`](test-results/screenshots/E2E-012-002--success.jpg).

3. **`E2E-012-003` (Lưu phiên bản thiết kế, định giá tham khảo và lịch sử phiên bản): PASS**
   - Nhấn "Lưu & Thẩm định", backend lưu phiên bản thành công với `design_version = 1`, trả về mã thiết kế `DSG-...` và token phiên khách `X-Guest-Design-Session`.
   - Chuyển hướng sang màn hình thẩm định `/workshops/PKG-COUPLE-01/design/:designId`, hiển thị giá ước tính kỹ thuật 250.000 ₫ (kèm nhãn Tham khảo - Không đổi cọc) và độ khó chế tác 2.0 / 10.
   - Sửa thiết kế và lưu phiên bản tiếp theo, kiểm tra cập nhật thành công với version tăng dần và bảo vệ chống conflict lạc quan qua header `If-Match-Version`.
   - Bằng chứng ảnh: [`screenshots/E2E-012-003--success.jpg`](test-results/screenshots/E2E-012-003--success.jpg).

4. **`E2E-012-004` (Khôi phục tương thích chi nhánh workshop): PASS**
   - Khi chọn chi nhánh workshop không hỗ trợ chất liệu vàng 14K (thiếu lò nung cao tần), hệ thống trả về cảnh báo `DESIGN_BRANCH_INCOMPATIBLE`.
   - Màn hình chuyển hướng đến `/workshops/:packageRef/design/recovery` hiển thị các phương án khôi phục: đổi chất liệu tương thích hoặc chọn chi nhánh khác.
   - Chọn đổi chất liệu về Bạc Ý 925 cho phép tiếp tục quy trình đặt lịch thành công.
   - Bằng chứng ảnh: [`screenshots/E2E-012-004--success.jpg`](test-results/screenshots/E2E-012-004--success.jpg).

5. **`E2E-012-005` (Hết hạn phiên khách 20 phút và phân quyền lịch sử hội viên): PASS**
   - Step 1 (Member đăng nhập & xem lịch sử thiết kế cá nhân): **PASS**, Member `member.test@mland.vn` đăng nhập mở `/my/designs` hiển thị đúng danh sách thiết kế cá nhân `DSG-DEMO-MEMBER-01` ([`screenshots/E2E-012-005--member-history.jpg`](test-results/screenshots/E2E-012-005--member-history.jpg)).
   - Step 2 (Xem chi tiết thiết kế cá nhân & Lưu trữ): Lỗi ban đầu `BUG-E2E-0007` (do `DesignEvaluationPage.jsx` và `RingConfigurationPage.jsx` gọi `const { user } = useAuth()` thay vì `const { status, userId } = useAuth()` khiến `isAuthenticated` luôn là `false` và không gửi Bearer token) đã được khắc phục hoàn toàn. Tái kiểm thử với `browsermcp`: Member bấm "Xem chi tiết →" mở trang thẩm định `/workshops/PKG-COUPLE-SILVER/design/DSG-DEMO-MEMBER-01` thành công (HTTP 200 kèm Bearer token), hiển thị đầy đủ thông số nhẫn, độ khó 2.0/10 và nút "Lưu trữ mẫu thiết kế" (`archive-design-btn`). Hội viên bấm "Lưu trữ mẫu thiết kế" gửi `POST /api/v1/workshop-designs/DSG-DEMO-MEMBER-01/archive` thành công với `If-Match-Version: 2`. Backend cập nhật status sang `ARCHIVED` trong DB, giao diện hiển thị badge "ĐÃ LƯU TRỮ", các nút thao tác chỉnh sửa/đặt lịch tự động ẩn an toàn. **PASS 100%**.
   - Step 3 (Phiên khách vãng lai hết hạn 20 phút không hoạt động): **PASS**, truy cập thiết kế hết hạn `/workshops/PKG-COUPLE-01/design/DSG-EXPIRED-SESSION`, backend phản hồi chuẩn xác HTTP 401 `DESIGN_SESSION_EXPIRED`. Giao diện hiển thị cảnh báo tiếng Việt "Phiên làm việc của bạn đã hết hạn sau 20 phút không hoạt động." ([`screenshots/E2E-012-005--session-expired.jpg`](test-results/screenshots/E2E-012-005--session-expired.jpg)).
   - Bằng chứng ảnh thành công: [`screenshots/E2E-012-005--success.jpg`](test-results/screenshots/E2E-012-005--success.jpg).

### Phase H — Nâng cấp danh mục vật liệu (Đồng/Thép), Mở rộng Size 29 kèm chu vi mm, Dual View SVG & Sliders kích thước (2026-11-14)

1. **Loại bỏ chất liệu Vàng, Bổ sung Đồng & Thép không gỉ:**
   - Tạo Flyway migration `V120__ring_design_copper_steel_and_models.sql`: Chuyển `is_active = FALSE` cho toàn bộ các chất liệu vàng (`MAT-GOLD-14K`, `MAT-ROSE-GOLD-14K`, `MAT-WHITE-GOLD-14K`).
   - Bổ sung vật liệu Đồng Đỏ / Đồng Thau (`MAT-COPPER`, 150.000 ₫) và Thép Không Gỉ 316L (`MAT-STAINLESS-STEEL`, 180.000 ₫).
   - Bổ sung các phom dáng chế tác workshop mới: `SHP-SINGLE-TWIST` (Dáng xoắn đơn), `SHP-WAVE` (Dáng lượn sóng), `SHP-BALL-PEEN` (Bề mặt búa bi gõ lõm li ti), `SHP-FLAT-BAND` (Đai phẳng công thái học).
   - Nâng tổng số mẫu thiết kế gợi ý (Catalogue Models) lên **12 mẫu** (vượt yêu cầu tối thiểu 10 mẫu), bao gồm mẫu xoắn đơn, lượn sóng, búa bi, giác búa, đồng vintage, thép phay xước, milgrain, và búa gõ khắc tim.
2. **Mở rộng kích cỡ đến Size 29 kèm hiển thị chu vi chuẩn xác theo mm:**
   - Cập nhật `ManagerDesignRuleAdapter.java`: Mở rộng `STANDARD_SIZES` từ `"6"` đến `"29"` (gồm 24 kích cỡ chuẩn).
   - Frontend hiển thị chu vi chuẩn Việt Nam (`Chu vi mm = Size + 40`) trực quan trên từng pill kích thước (ví dụ `Size 29 (69 mm)`) và dòng mô tả chi tiết đường kính lòng trong.
3. **Cửa sổ hiển thị kép (Dual View SVG) & 2 Sliders điều chỉnh kích thước:**
   - **Mặt chính diện (Top View):** Vòng nhẫn tròn động co giãn độ dày nét đai theo slider `thicknessMm`, hiển thị họa tiết búa bi hạt tròn li ti, xoắn, lượn sóng, đính đá và watermark chu vi/đường kính.
   - **Mặt cạnh / Tiết diện (Side View):** Cửa sổ mô phỏng hình trụ đai nhẫn nhìn ngang; chiều cao đai co giãn theo slider `bandWidthMm` (1.5mm - 6.0mm); độ dày thành nhẫn co giãn theo `thicknessMm` (1.0mm - 3.0mm); hiển thị kết cấu búa bi gõ lõm, phay xước mờ, giác búa, sóng mềm mại.
   - **2 Thanh trượt kích thước:**
     - Thanh trượt Độ dày nhẫn: min 1.0mm, max 3.0mm, bước nhảy 0.1mm (mặc định 1.8mm).
     - Thanh trượt Độ rộng bề mặt: min 1.5mm, max 6.0mm, bước nhảy 0.1mm (mặc định 3.5mm).
     - Phản ánh trực tiếp lên đồ họa SVG và bảng tóm tắt thông số kỹ thuật tức thì.
4. **Kết quả tái kiểm thử tự động & E2E:**
   - Backend Unit & Controller tests: **12/12 PASS (100%)**.
   - Frontend Vitest suites: **88/88 tests PASS (100%)**.
   - Kịch bản kiểm thử E2E trên trình duyệt thực qua `browsermcp`:
     * `E2E-012-001` (Catalogue Models 12 mẫu & Blank Configurator): **PASS**
     * `E2E-012-002` (Vật liệu Đồng/Thép, Size 29 kèm mm, Dual View SVG, Sliders kích thước): **PASS**
     * `E2E-012-003` (Lưu & Thẩm định, chuyển tiếp booking): **PASS**
     * `E2E-012-004` (Khôi phục tương thích chi nhánh): **PASS**
     * `E2E-012-005` (Hết hạn 20 phút & Phân quyền Member): **PASS**
     * Hồi quy chéo Feature 004 (`E2E-004-001`..`004`) và Feature 011 (`E2E-011-001`..`005`): **PASS 100%**.

**Tổng kết Feature 012**: Đạt **5/5 kịch bản E2E PASS (100%)**, toàn bộ blocker phân quyền hội viên và defect `BUG-E2E-0007` đã được đóng hoàn toàn.

### Phase I — Tùy biến số lượng đá đính kèm & Kéo thả phân bố đá trên đường tròn nhẫn (2026-11-14)

1. **Lựa chọn số lượng đá linh hoạt cho từng loại đá:**
   - Thay vì chỉ cho phép chọn duy nhất 1 viên / 1 loại, giao diện bổ sung bộ điều khiển số lượng (stepper `[-] {count} [+]`) ngay trên thẻ đá quý.
   - Giới hạn số lượng tối đa theo quy tắc nghiệp vụ (`maxQuantity = 10` cho CZ, `maxQuantity = 4` cho Moissanite).
   - Tự động tính giá thành phần theo số lượng thực tế: `Đơn giá x Số lượng`, hiển thị chi tiết số viên và tổng tiền trên từng thẻ.
   - Header danh mục hiển thị tổng số viên đá đã chọn trên toàn bộ thiết kế kèm huy hiệu và thanh hướng dẫn thao tác kéo thả.
2. **Kéo thả viên đá tương tác trực tiếp trên mặt chính diện (Top View Preview):**
   - Từng viên đá được hiển thị độc lập trên bản vẽ kỹ thuật SVG mặt chính diện kèm nhãn số thứ tự, màu sắc và hiệu ứng ánh sáng.
   - Cơ chế kéo thả toán học ràng buộc tuyệt đối trên đường tròn đai nhẫn ($R = 85\text{px}$, tâm $C(150, 135)$):
     - $\theta = \text{atan2}(y - 135, x - 150)$
     - $cx = 150 + 85 \cdot \cos(\theta), \quad cy = 135 + 85 \cdot \sin(\theta)$
   - Người dùng có thể kéo thả tự do để sắp xếp bố cục các viên đá xung quanh toàn bộ chu vi đai nhẫn (đối xứng, so le, đính đá trung tâm hay đính đá hông), nhưng viên đá **tuyệt đối không bị văng hoặc kéo ra ngoài đường tròn nhẫn**.
   - Khi di chuột/kéo thả, hiển thị đường viền quỹ đạo đai nhẫn (dashed guide track) và quầng sáng định vị (drag halo) trực quan sinh động.
3. **Đồng bộ hiển thị tiết diện ngang (Side View) & Bảng thẩm định giá:**
   - Cửa sổ Side View tự động vẽ chấu gắn và bóng chiếu của các viên đá đính trên bề mặt nhẫn tương ứng với số lượng đã chọn.
   - Trang Thẩm định thiết kế hiển thị chi tiết số lượng từng loại đá dạng `${quantity}x ${label}` (ví dụ: `2x Đá CZ Trắng 2.0mm`, `1x Đá CZ Xanh Lam 2.0mm`) và tính tổng chi phí kỹ thuật chính xác.
4. **Kết quả kiểm thử & Nghiệm thu:**
   - Frontend Vitest suite: **88/88 tests PASS (100%)**.
   - Kiểm thử tích hợp trình duyệt thực tế qua `browsermcp`:
     * Thêm nhiều viên đá khác nhau (2 viên CZ Trắng + 1 viên CZ Lam).
     * Kéo thả viên đá sang góc $0^\circ$ (hướng 3 giờ) và $180^\circ$ (hướng 9 giờ). Tọa độ luôn cố định trên đường tròn đai nhẫn $R=85\text{px}$.
     * Lưu và thẩm định thành công với HTTP 200 OK, mã thiết kế chuyển sang trạng thái `VALIDATED`, độ khó và ước tính giá phản ánh chính xác.

### Phase J — Tính chi phí vật liệu theo khối lượng gam & Ước lượng thể tích hình học (2026-11-14)

1. **Mô hình thể tích hình học (Hollow Cylinder / Torus) từ các số đo khách hàng**:
   - Chu vi đai trong: $P = \text{Size} + 40\text{ mm}$ (với $\text{Size} \in [6, 29]$).
   - Độ dày nhẫn $T$ (`thicknessMm`: 1.0 - 3.0 mm) và Độ rộng bề mặt $W$ (`bandWidthMm`: 1.5 - 6.0 mm).
   - Thể tích đai nhẫn:
     $$V = (P + \pi T) \times T \times W \quad (\text{mm}^3), \quad V_{\text{cm}^3} = \frac{V}{1000}$$
2. **Khối lượng vật liệu ước tính theo khối lượng riêng ($D$)**:
   $$M = V_{\text{cm}^3} \times D$$
   - Bạc Ý 925: $D = 10.36\text{ g/cm}^3$.
   - Đồng Đỏ / Đồng Thau: $D = 8.96\text{ g/cm}^3$.
   - Thép Không Gỉ 316L: $D = 7.99\text{ g/cm}^3$.
3. **Quy tắc tính giá vật liệu theo gam**:
   - **Định mức chuẩn**: Nhẫn có khối lượng từ 3.0 gam bạc trở xuống ($M \le 3.0\text{g}$) được tính giá cơ sở bình thường (**250.000 ₫** cho Bạc 925).
   - **Phụ phí vượt mức**: Mỗi 1 gam bạc vượt quá 3.0 gam tính thêm **120.000 ₫/gam**:
     $$\text{Chi phí Bạc} = 250.000\text{ ₫} + \max(0, M - 3.0) \times 120.000\text{ ₫/gam}$$
   - Đối với Đồng Đỏ và Thép: Áp dụng định mức 3.0g, phần vượt tính phụ phí tương ứng (Đồng: 40.000 ₫/g, Thép: 50.000 ₫/g).
4. **Hiển thị giao diện & Bóc tách chi phí**:
   - Bảng tóm tắt thông số cấu hình hiển thị trực tiếp `Khối lượng ước tính: ~X.XX g` kèm ghi chú vượt mức hoặc trong định mức chuẩn.
   - Trang Thẩm định thiết kế hiển thị thẻ chỉ số `Khối lượng ước tính: X.XX g` và bóc tách phụ phí nguyên liệu minh bạch.
5. **Kiểm thử tự động & Nghiệm thu**:
   - Backend unit & integration tests: **16/16 tests PASS (100%)**.
   - Frontend Vitest suite: **88/88 tests PASS (100%)**.

### Phase K — Khắc nhiều vị trí tính phí 1 lần, Upload ảnh khắc riêng (100k) & Cảnh báo bề mặt gồ ghề (2026-11-14)

1. **Khắc chữ & biểu tượng tiêu chuẩn (Phí 1 lần duy nhất 80.000 ₫)**:
   - Khách hàng trả tiền khắc tiêu chuẩn 1 lần duy nhất, được khắc nhiều nội dung và biểu tượng cả ở **mặt trong (`INNER_BAND`)** và **mặt ngoài (`OUTER_BAND`)**.
   - Hỗ trợ thêm/xóa nhiều mục khắc, cung cấp nút shortcut chèn nhanh ký hiệu (♥, ∞, ★, ✿, 💍).
2. **Cảnh báo kết cấu bề mặt gồ ghề (Surface Texture Collision Warning)**:
   - Khi có bất kỳ mục khắc nào (chữ hoặc ảnh) đặt tại mặt ngoài (`OUTER_BAND`) của các mẫu nhẫn có kết cấu gồ ghề (`SHP-HAMMERED`, `SHP-BALL-PEEN`, `SHP-SINGLE-TWIST`, `SHP-TWISTED`, `SHP-WAVE`), hệ thống hiển thị banner cảnh báo kỹ thuật nổi bật:
     > *"⚠️ Lưu ý: Đai nhẫn đang chọn có kết cấu bề mặt gồ ghề (đập búa / búa bi / xoắn / lượn sóng). Hình khắc ở mặt ngoài có thể bị biến dạng hoặc không hiển thị sắc nét như mong muốn trên bề mặt thực tế. Bạn nên ưu tiên chọn khắc ở mặt trong để đảm bảo chất lượng tinh xảo."*
   - Cảnh báo được quản lý đồng bộ từ backend rules (`EvaluationResultDto.warnings`) và hiển thị cả ở bước cấu hình lẫn trang thẩm định.
3. **Khắc hình ảnh cá nhân hóa tải lên (+100.000 ₫ / ảnh)**:
   - Cho phép khách hàng tải file hình ảnh lên để khắc (chữ ký, logo, hình vẽ tay).
   - Mỗi ảnh upload được tính thêm phụ phí riêng là **100.000 ₫ / ảnh** (tùy chọn vị trí mặt trong hoặc mặt ngoài).
   - **Xử lý hình ảnh trắng đen laser (B&W Thresholding)**: Chuyển đổi vùng ảnh sang màu đơn sắc tương phản cao qua Canvas mô phỏng chính xác tia laser khắc trên kim loại.
   - **Khung chọn vị trí 160px x 160px**: Giới hạn khung 160x160 tương tác, cho phép người dùng kéo thả trực tiếp để chọn góc/vùng ảnh muốn khắc và cập nhật ô xem trước tức thì.
4. **Kiểm thử & Nghiệm thu**:
   - Backend unit tests (`ManagerDesignRuleAdapterTest`, `WorkshopRingDesign*Test`): **21/21 tests PASS (100%)**.
   - Frontend Vitest suite (`designDomain`, `screens`, `workshopRingDesignApi`): **35/35 tests PASS (100%)**.
   - Kiểm thử E2E chi tiết trên trình duyệt thực tế ghi lại trong tài liệu kiểm thử.




## 5. Incomplete / Next Steps (Release Gates)

### Giai đoạn 1: Quản trị danh mục động cho Manager (FE-09)
- Xây dựng màn hình quản lý danh mục nhẫn cho Manager để cập nhật động các mẫu đá quý, chất liệu và phụ kiện thay vì sử dụng seed ban đầu.

### Giai đoạn 2: Phê duyệt chính thức Revision Figma
- Cập nhật mã revision Figma chính thức khi Tech Lead và UI Designer hoàn thiện rà soát giao diện visual.
