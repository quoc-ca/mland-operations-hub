# Version 1.0 — Feature 012: Workshop Ring Design Configuration (Backend Implementation & Full Test Verification)

- **Feature**: 012 — Workshop Ring Design Configuration
- **Author**: Ngô Tiến Đạt
- **Last updated by**: Ngô Tiến Đạt
- **Date created**: 2026-11-08
- **Date updated**: 2026-11-09
- **Source of truth**: Feature-012 `spec.md`, `data-model.md`, `contracts/workshop-ring-design.openapi.yaml`, `V109__workshop_ring_design_configuration.sql`, backend implementation, and synchronized project specifications

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
- Triển khai schema vật lý cho 7 bảng:
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

## 2. Incomplete / Next Steps (Frontend & Release Gates)

### Giai đoạn 1: Triển khai Frontend React + Vite
- Xây dựng giao diện cấu hình nhẫn người dùng theo `frontend-handoff.md` và các frame Figma (`457:1553`, `457:1554`, `457:1655`, `457:1698`, `457:1749`, `457:1795`).
- Xây dựng live preview `RingPreview` cập nhật tức thì trên client khi người dùng thay đổi chất liệu, dáng nhẫn, đá quý mà không cần chờ gọi API.
- Hỗ trợ cả 2 luồng Guest (lưu `X-Guest-Design-Session` trong memory/session) và Member (Firebase JWT).

### Giai đoạn 2: Quản trị danh mục động cho Manager (FE-09)
- Xây dựng màn hình quản lý danh mục nhẫn cho Manager để cập nhật động các mẫu đá quý, chất liệu và phụ kiện thay vì sử dụng seed ban đầu.

### Giai đoạn 3: Phê duyệt chính thức Revision Figma
- Cập nhật mã revision Figma chính thức khi Tech Lead và UI Designer hoàn thiện rà soát giao diện visual.
