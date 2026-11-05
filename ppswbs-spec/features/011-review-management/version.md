# Version 1.1 — Feature-011: Review Management (JDK 21 Backend Verification, Contract Audit & Defect Fixes)

- **Feature**: 011 — Review Management
- **Author**: Ngô Tiến Đạt
- **Last updated by**: Ngô Tiến Đạt
- **Date created**: 2026-11-03
- **Date updated**: 2026-11-06
- **Source of truth**: Feature-011 `spec.md`, `data-model.md`, backend implementation, `V91__review_management.sql`, `V97__review_history_retention.sql`, OpenAPI contract (`review-management.openapi.yaml`) and synchronized project specifications

> This file is an implementation status index. Detailed behavior remains in
> the Feature-011 artifacts and source code. Phase labels are retained for
> tracing and audit. “Implemented” means code is present; it does not mean the
> implementation has passed final contract, migration or release verification.

---

## 1. Completed

### Phase A — Backend boundary and module structure

- Added the `features.reviewmanagement` backend module.
- Added controller, DTO, domain, service, eligibility and DAO layers.
- Added `WorkshopAttendanceReviewPort` and `RetailFulfilmentReviewPort`.
- Added internal adapters for Workshop attendance and Retail fulfilment checks.
- Review Management owns review data and does not directly use Workshop/Retail repositories through the feature service boundary.
- Added security integration through `IdentityGateFilter` and `SecurityConfiguration`.

### Phase B — Persistence and migration

- Added [`V91__review_management.sql`](../../../ppswbs_backend/src/main/resources/db/migration/V91__review_management.sql) after merge resolution. `develop` introduced separate `V89` and `V90` migrations, so retaining Feature-011 as `V89` would leave duplicate Flyway versions.
- Added logical persistence for `reviews`, `review_revisions`, `review_replies`, `review_moderation_events`, `guest_eligibility_handles` and `review_idempotency_records`.
- Added unique source protection through `(source_type, source_reference)`.
- Added rating constraint `1..5`, service-level source/target validation and `PUBLIC/HIDDEN/REMOVED` status model.
- Added realtime aggregate query support through `ReviewAggregateProjection`.

### Phase C — Eligibility and Guest handle

- Workshop eligibility verifies `CHECKED_IN` attendance through `InternalWorkshopAttendanceAdapter`.
- Retail eligibility verifies `PICKED_UP`/`DELIVERED` fulfilment through `InternalRetailFulfilmentAdapter`.
- Member eligible-source lookup uses authenticated account ownership.
- Guest lookup accepts email/phone and persists a SHA-256-backed opaque handle.
- Guest handle TTL is currently 30 minutes.
- Handle records retain the eligible source-reference set and masked contact value.
- Handle validation checks existence, expiry and source binding before Guest creation/edit.

### Phase D — Review application, moderation and reply

- `ReviewApplicationService` implements Member/Guest creation, duplicate-source prevention, revision creation and owner edit.
- Valid review creation starts at `PUBLIC`.
- Owner edits use optimistic `expectedVersion` validation and append a revision.
- `ReviewModerationService` implements visibility actions and audit event persistence.
- Staff is restricted to `HIDE` in the current service implementation.
- Manager supports `HIDE`, `RESTORE` and `REMOVE` in the current service implementation.
- `ReviewReplyService` implements one current reply and audited reply edits.
- `ReviewPublicReadServiceImpl` returns public reviews and realtime count/average data.
- `ReviewMaskingService` masks Member display names and Guest contact values.
- `ReviewIdempotencyService` implements SHA-256 request hashing and replay records for lookup, create, edit, visibility and reply commands.

### Phase E — REST API and exception handling

Implemented endpoint operations:

```text
POST  /api/v1/review-eligibility/lookup
GET   /api/v1/reviews/eligible-sources
POST  /api/v1/reviews
GET   /api/v1/reviews
GET   /api/v1/reviews/{reviewId}
PATCH /api/v1/reviews/{reviewId}
PATCH /api/v1/reviews/{reviewId}/visibility
GET   /api/v1/review-moderation
POST  /api/v1/reviews/{reviewId}/reply
PATCH /api/v1/reviews/{reviewId}/reply
```

- Added structured `ReviewManagementExceptionHandler` responses.
- Added domain error codes for invalid input, ineligible source, duplicate source, authorization, target mismatch, Guest handle failure and state conflict.
- Public listing filters by target and returns aggregate data.
- Moderation queue supports status/target filters and pagination.

### Phase F — JDK 21 Backend Verification & Fresh Test Evidence (2026-11-06)

Đã cấu hình và thực thi kiểm thử toàn diện trên môi trường **OpenJDK 21.0.7 LTS**:
- Lệnh thực thi: `mvnw.cmd test -Dtest="edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.*Test"`
- Kết quả kiểm thử: **21/21 tests passed (100%)** trong 33.6s:
  - `ReviewApplicationServiceTest`: 6/6 tests passed (Member workshop create, duplicate source 409 conflict, owner edit optimistic locking, self-review ownership authorization).
  - `ReviewEligibilityServiceTest`: 3/3 tests passed (Guest email/phone lookup, opaque handle generation, Member eligible sources retrieval).
  - `ReviewModerationServiceTest`: 3/3 tests passed (Staff HIDE action, Manager HIDE/RESTORE/REMOVE lifecycle, version mismatch 409 conflict).
  - `ReviewPublicReadServiceTest`: 2/2 tests passed (Realtime aggregate calculation over PUBLIC reviews, filtered public page).
  - `ReviewReplyServiceTest`: 3/3 tests passed (Manager create official reply, duplicate reply prevention, audited reply edit).
  - `ReviewControllerMockMvcTest`: 4/4 tests passed (REST endpoints, header-based `Idempotency-Key`, 400 validation, duplicate 409 conflict advice).
- Spring Modulith Architecture Verification:
  - Lệnh thực thi: `mvnw.cmd test -Dtest="ModulithArchitectureTests"`
  - Kết quả: **5/5 tests passed (100%)** (`ModulithArchitectureTests.java`), xác nhận module `features.reviewmanagement` hoàn toàn tuân thủ kiến trúc Spring Modulith, không có circular dependency, không vi phạm package boundary với các module khác.
- Regression Verification:
  - Lệnh thực thi: `mvnw.cmd test -Dtest="edu.fpt.sep490_g22.ppswbs_backend.designreview.*IT"`
  - Kết quả: **44/44 Feature 004 tests passed (100%)**, không có bất kỳ regression nào trong toàn bộ codebase.

### Phase G — Backend Conflict, Defect & OpenAPI Gap Resolution Log (2026-11-06)

Qua quá trình rà soát toàn diện mã nguồn Backend Feature 011 và đối chiếu với `spec.md` cùng contract OpenAPI `review-management.openapi.yaml`, các conflict và thiếu sót sau đã được phát hiện và giải quyết triệt để:

1. **Conflict Constructor Injection Ambiguity (Spring IoC Context)**:
   - *Phát hiện:* Các service `ReviewEligibilityService`, `ReviewModerationService`, `ReviewReplyService` chứa 2 constructor (constructor chính 5–7 tham số và constructor `@Deprecated` 4–6 tham số) nhưng thiếu annotation `@Autowired`, dẫn đến lỗi `UnsatisfiedDependencyException: No default constructor found` khi khởi động Spring Boot.
   - *Khắc phục:* Bổ sung tường minh `@Autowired` vào constructor chính của cả 3 service.
2. **Conflict Thiếu OwnerType trong Create Review Response**:
   - *Phát hiện:* Trong `ReviewApplicationService.createReview()`, khi gọi `mapToReviewDto(review, null)`, hàm mặc định gán `includeOwnerData = false`, khiến `ownerType` trong `ReviewDto` trả về cho người vừa tạo bị `null` (gây fail test `ReviewApplicationServiceTest.createReview_MemberWorkshop_Success`).
   - *Khắc phục:* Sửa thành `mapToReviewDto(review, null, true)` để trả về đầy đủ `ownerType` (`MEMBER` hoặc `GUEST`) cho chính chủ sở hữu review.
3. **Giải quyết triệt để Lỗi Encoding Tiếng Việt (Blocker 6)**:
   - *Phát hiện:* File `pom.xml` thiếu `<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>` và cấu hình compiler/surefire, khiến trên Windows javac và JVM mặc định sử dụng codepage `windows-1252` (ANSI) làm hỏng các ký tự Unicode tiếng Việt trong các exception message và log console (`Tr?i nghi?m ny ? ???c g?i ?nh gi`).
   - *Khắc phục:* Bổ sung cấu hình UTF-8 toàn diện trong `pom.xml`:
     - `<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>`
     - `<project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>`
     - `maven-compiler-plugin`: `<encoding>UTF-8</encoding>`
     - `maven-surefire-plugin`: `<argLine>-Dfile.encoding=UTF-8</argLine>`
4. **Chuẩn hóa Header `Guest-Eligibility-Handle` theo OpenAPI**:
   - *Phát hiện:* `ReviewController.createReview()` chỉ nhận `guestEligibilityHandle` từ JSON request body, trong khi schema OpenAPI và tài liệu cho phép gửi qua HTTP Header `Guest-Eligibility-Handle`.
   - *Khắc phục:* Bổ sung `@RequestHeader(value = "Guest-Eligibility-Handle", required = false) String guestHandleHeader` vào controller; tự động ánh xạ sang request nếu body chưa có.
5. **Bổ sung Cursor Pagination theo chuẩn OpenAPI `PublicReviewPage`**:
   - *Phát hiện:* Schema `PublicReviewPage` trong OpenAPI yêu cầu trường `nextCursor`, trong khi `PublicReviewPageDto` chỉ có các trường số trang truyền thống (`page`, `size`, `totalElements`, `totalPages`, `hasNext`).
   - *Khắc phục:* Bổ sung thuộc tính `private String nextCursor` vào `PublicReviewPageDto`, tính toán `nextCursor = page.hasNext() ? String.valueOf(page.getNumber() + 1) : null` trong `ReviewPublicReadServiceImpl`, giữ tương thích kép cho cả cursor và traditional pagination.
6. **Đồng bộ DTO JsonAlias cho `EligibleSourceListResponse` và `EligibleSourceDto`**:
   - *Phát hiện:* `EligibleSourceListResponse` dùng tên trường nội bộ `guestEligibilityHandle` và `sources`, trong khi OpenAPI dùng `eligibilityHandle` và `items`; `EligibleSourceDto` dùng `targetName` trong khi OpenAPI dùng `label`.
   - *Khắc phục:* Bổ sung `@JsonProperty` và `@JsonAlias` tương ứng (`@JsonAlias({"guestEligibilityHandle", "eligibilityHandle"})`, `@JsonAlias({"sources", "items"})`, `@JsonAlias({"label", "targetName"})`), đảm bảo tương thích 2 chiều hoàn hảo giữa backend và frontend.
7. **Bảo toàn Invariant và Authorization**:
   - Xác minh các quy tắc bảo vệ dữ liệu: Guest contact được mask bằng `ReviewMaskingService`, rating giới hạn 1–5, text giới hạn tối đa 2,000 ký tự (`@Size(max = 2000)`), Staff chỉ được HIDE, Manager được HIDE/RESTORE/REMOVE và tạo/sửa 1 reply duy nhất, Admin bị từ chối nghiệp vụ kiểm duyệt.

### Phase H — Frontend React + Vite Implementation & Test Verification (2026-11-06)

- **Frontend Architecture & Components**:
  - Triển khai toàn bộ cấu trúc slice `ppswbs_frontend/src/features/reviewmanagement/`:
    - `api/reviewManagementApi.js`: Adapter kết nối REST API tuân thủ 100% contract OpenAPI, hỗ trợ header `Idempotency-Key`, `If-Match-Version`, `Guest-Eligibility-Handle`, và query filtering unauthenticated cho public reads.
    - `api/useReviewManagementApi.js`: Hook gắn session `useHttp()`.
    - `messages.js`: Từ điển song ngữ (Vietnamese / English) cho mọi tiêu đề, nhãn, nút bấm, validation messages, và typed error codes.
    - `reviewmanagement.css`: Style layout responsive, thẻ review card, thanh công cụ aggregate rating, danh sách nguồn source card, và moderation actions.
    - `ui/PublicReviewListPage.jsx` (`F11-PUBLIC-REVIEW-LIST` - `/reviews`): Hiển thị danh sách đánh giá công khai, aggregate rating trung bình / tổng số review realtime, bộ lọc sao (1..5), và điều hướng tới Guest lookup / Member eligible sources.
    - `ui/GuestLookupPage.jsx` (`F11-GUEST-LOOKUP` - `/reviews/guest/lookup`): Tra cứu nguồn hợp lệ qua email/phone, nhận diện opaque `eligibilityHandle`, chọn nguồn và chuyển hướng tới form tạo đánh giá.
    - `ui/EligibleSourcesPage.jsx` (`F11-ELIGIBLE-SOURCES` - `/reviews/eligible-sources`): Hiển thị nguồn attendance/order line của tài khoản hội viên đã đăng nhập (bảo vệ bởi `RequireSession`).
    - `ui/CreateReviewPage.jsx` (`F11-CREATE-REVIEW` - `/reviews/new`): Tạo đánh giá cho nguồn đã chọn, kiểm soát độ dài 2,000 ký tự, sao 1..5, gửi kèm `Idempotency-Key` và handle (cho Guest).
    - `ui/EditReviewPage.jsx` (`F11-EDIT-REVIEW` - `/reviews/:reviewId/edit`): Chỉnh sửa đánh giá của chính chủ sở hữu, gửi `If-Match-Version` và `Idempotency-Key`, xử lý conflict 409 khi dữ liệu bị thay đổi đồng thời.
    - `ui/ReviewDetailPage.jsx` (`F11-PUBLIC-REVIEW-DETAIL` - `/reviews/:reviewId`): Hiển thị chi tiết đánh giá, masked display name, phản hồi chính thức từ Manager, và nút chỉnh sửa cho chủ sở hữu.
    - `ui/ModerationQueuePage.jsx` (`F11-MODERATION-QUEUE` - `/review-moderation`): Hàng đợi kiểm duyệt cho Staff/Manager, bảo vệ bằng `RequireSession`, hiển thị trạng thái và nút mở chi tiết.
    - `ui/ModerationDetailPage.jsx` (`F11-MODERATION-DETAIL` - `/review-moderation/:reviewId`): Chi tiết kiểm duyệt, hành động Ẩn (`HIDDEN`) / Gỡ (`REMOVED`) kèm lý do và version optimistic lock; form Quản lý phản hồi (`POST/PATCH /reply`) với `Idempotency-Key`.
    - `routes.jsx`: Đăng ký đầy đủ 8 routes theo đúng `spec.md` mục 13.2.
- **Automated Verification Evidence (Vitest)**:
  - Lệnh: `npx.cmd vitest run src/features/reviewmanagement`
  - Kết quả: **2/2 test files passed, 20/20 tests passed (100%)** trong 4.85s:
    - `tests/reviewManagementApi.test.js`: 4/4 tests passed (idempotency key, If-Match-Version, explicit moderation/reply boundary, unauthenticated public read).
    - `tests/screens.test.jsx`: 16/16 tests passed:
      - Public list & aggregate score.
      - Public list empty state.
      - Guest lookup by email with opaque handle.
      - Guest lookup empty state.
      - Session requirement for Member eligible sources (signed out).
      - Member eligible sources rendering (signed in).
      - Direct navigation guard to create review without source.
      - Review creation success with multipart state and `Idempotency-Key`.
      - 2,000-character validation rejection before API call.
      - Owner edit review with `If-Match-Version` optimistic lock.
      - Version conflict notice (409).
      - Review detail with Manager reply.
      - Session requirement for moderation queue (signed out).
      - Moderation queue display and item navigation.
      - Hide action submission with reason and optimistic version.
      - Manager official reply creation with `Idempotency-Key`.
- **Integrated Repository Test Check**:
  - Lệnh: `npx.cmd vitest run src/features/designreview src/features/reviewmanagement`
  - Kết quả: **4/4 test files passed, 41/41 tests passed (100%)** không có regression giữa Feature 004 và Feature 011.
- **Production Build Evidence**:
  - Lệnh: `npm.cmd run build`
  - Kết quả: **`vite build` hoàn tất thành công trong 2.60s**, 212 modules transformed, không có lỗi biên dịch.

### Phase I — Comprehensive Frontend Refactoring, Invariant Rules & Navigation Integration (2026-11-06)

- **Domain Rules & Invariants (`rules.js`)**:
  - Tạo mới module `rules.js` định nghĩa giới hạn và hàm tiện ích chuẩn hóa cho Feature 011:
    - `LIMITS`: `ratingMin: 1`, `ratingMax: 5`, `bodyTextMax: 2000`, `reasonMax: 500`, `replyMax: 2000`, `contactValueMax: 254`, `handleExpiryMinutes: 30`.
    - `TARGET_TYPES`: `WORKSHOP_SESSION`, `RETAIL_PRODUCT`.
    - `SOURCE_TYPES`: `WORKSHOP_ATTENDANCE`, `RETAIL_ORDER_LINE`.
    - `REVIEW_STATUSES`: `PUBLIC`, `HIDDEN`, `REMOVED`.
    - Các helper functions: `formatDateTime()`, `formatStars()`, `validateRating()`, `validateBodyText()`, `validateReason()`, `validateReply()`, `validateContact()`.
- **Hoàn thiện Bộ Từ điển Song ngữ (`messages.js`)**:
  - Mở rộng đầy đủ bản dịch song ngữ `vi` và `en` bao quát 100% các nhãn, tiêu đề, nút bấm, trạng thái, và toàn bộ 12 mã lỗi từ OpenAPI contract (`error_REVIEW_INPUT_INVALID`, `error_REVIEW_SOURCE_NOT_ELIGIBLE`, `error_REVIEW_GUEST_HANDLE_INVALID`, `error_REVIEW_GUEST_HANDLE_EXPIRED`, `error_REVIEW_ALREADY_EXISTS`, `error_REVIEW_NOT_AUTHORIZED`, `error_REVIEW_MODERATION_ACTION_NOT_ALLOWED`, `error_REVIEW_NOT_FOUND`, `error_REVIEW_STATE_CONFLICT`, `error_REVIEW_TARGET_MISMATCH`, `error_REVIEW_REPLY_NOT_AUTHORIZED`, `error_REVIEW_IDEMPOTENCY_CONFLICT`).
- **Nâng cấp Hệ thống Giao diện & CSS (`reviewmanagement.css`)**:
  - Viết lại toàn bộ stylesheet từ dạng minified sang dạng chuẩn mực, dễ bảo trì, responsive:
    - Thẻ tổng hợp điểm đánh giá lớn (`.review-aggregate-card`, `.aggregate-score-box`).
    - Huy hiệu trạng thái theo màu sắc ngữ nghĩa (`.badge-status`, `.badge-status-hidden`, `.badge-status-removed`, `.badge-verified`).
    - Khung phản hồi chính thức của Manager với đường viền xanh nổi bật (`.manager-reply-box`).
    - Thẻ chọn nguồn giao dịch với hiệu ứng tương tác mượt mà (`.source-card`).
    - Bố cục responsive hỗ trợ điện thoại và máy tính bàn.
- **Tái cấu trúc và Nâng cấp Toàn bộ UI Components**:
  - `PublicReviewListPage.jsx`: Thêm thẻ aggregate điểm số lớn, đếm số lượt đánh giá, bộ lọc sao, nút điều hướng Guest và Member.
  - `GuestLookupPage.jsx`: Form nhập liệu có kiểm tra định dạng email/sđt, hiển thị các trải nghiệm đủ điều kiện và mã xác thực 30 phút.
  - `EligibleSourcesPage.jsx`: Danh sách nguồn giao dịch hợp lệ của hội viên đã đăng nhập kèm nút chuyển sang viết đánh giá.
  - `CreateReviewPage.jsx`: Hiển thị thông tin nguồn đã chọn, thanh chọn sao trực quan, bộ đếm ký tự trực tiếp (`X/2.000 ký tự`), gửi dữ liệu kèm `Idempotency-Key`.
  - `EditReviewPage.jsx`: Tải đánh giá hiện tại, cho phép chỉnh sửa sao và nội dung, bảo toàn trạng thái `PUBLIC` và gửi version optimistic lock.
  - `ReviewDetailPage.jsx`: Trình bày chi tiết đánh giá, masked author name, badge loại trải nghiệm, phản hồi chính thức của quản lý, và nút chỉnh sửa cho chủ sở hữu.
  - `ModerationQueuePage.jsx`: Hàng đợi kiểm duyệt có bộ lọc trạng thái (ALL, PUBLIC, HIDDEN, REMOVED), hướng dẫn phân quyền Staff vs Manager, và liên kết tới trang chi tiết.
  - `ModerationDetailPage.jsx`: Xử lý Ẩn (`HIDDEN`), Gỡ (`REMOVED`), Khôi phục (`PUBLIC`), nhập lý do kiểm duyệt gửi kèm `If-Match-Version`; Quản lý tạo/sửa phản hồi chính thức với `Idempotency-Key`.
  - `ReviewCard.jsx`: Thẻ hiển thị đánh giá độc lập, sao vàng trực quan, ngày giờ định dạng chuẩn địa phương hóa, và phản hồi của quản lý.
  - `ReviewForm.jsx`: Component form dùng chung cho tạo và chỉnh sửa, tích hợp client validation và hiển thị lỗi server tự động.
- **Tích hợp Điều hướng Ứng dụng (`AppLayout.jsx`)**:
  - Bổ sung các liên kết điều hướng `{t('reviews')}` và `{t('reviewModeration')}` vào thanh menu chính với từ điển song ngữ.
- **Kiểm thử Tự động & Build Sản phẩm**:
  - Vitest: **20/20 tests passed (100%)** trong `src/features/reviewmanagement`.
  - Không gây regression cho Feature 004: **21/21 tests passed (100%)** trong `src/features/designreview`.
  - Vite production build: **Hoàn tất thành công (213 modules, 2.65s, 0 errors, 0 warnings)**.
  - Backend tests (JDK 21): **26/26 tests passed (100%)** bao gồm 21 reviewmanagement tests và 5 Modulith architecture tests.

---

## 2. Incomplete / Next steps

### Giai đoạn 1 — Backend contract alignment

- Align `bodyText` validation with the approved 2,000-character rule. Current DTOs accept up to 4,000 characters in create/edit/reply paths.
- Align idempotency transport. Current implementation reads `idempotencyKey` from request bodies; the approved API contract expects `Idempotency-Key` request-header behavior.
- Align idempotency and optimistic-version enforcement consistently for moderation and reply commands; current DTOs carry fields, but services do not use the complete shared idempotency lifecycle.
- Decide and enforce the final Guest handle replay/consumption policy. The migration has `consumed_at`, but current validation does not consistently consume or replay-bind the handle.
- Align the OpenAPI contract with current DTO names, status codes and body/header shapes.

### Giai đoạn 2 — Eligibility and authorization gaps

- Current Guest creation explicitly allows only `WORKSHOP_ATTENDANCE`; Guest retail-product review remains unimplemented although Feature-011 scope includes Guest retail eligibility.
- Verify the exact `DELIVERED` source state and ownership evidence returned by the Fulfilment adapter.
- Remove or explicitly approve Admin business authority. Current controllers permit Admin to access moderation queue and create/edit Manager replies, while the approved baseline grants Admin technical/audit access only.
- Add server-side ownership checks to `GET /api/v1/reviews/{reviewId}`. Current service maps a review detail without an explicit public/owner/authorized-scope decision at this method boundary.
- Add conflict-of-interest/self-action checks for Staff and Manager moderation/reply operations if required by the approved invariant.

### Giai đoạn 3 — Persistence and migration safety

- Review `ON DELETE CASCADE` on `review_revisions`, `review_replies` and `review_moderation_events`. The approved business-history policy requires audit/revision/reply history to survive normal review removal; migration behavior must be changed or explicitly approved before production.
- Run Flyway validation against a clean MySQL database and an existing database containing migrations through `V92`.
- Verify `V91`/`V97` upgrade, rerun behavior, schema constraints, indexes and `flyway_schema_history`.
- Add a recovery plan for partial migration failure; do not use checksum repair, `baseline` or `outOfOrder` to conceal version conflicts.

### Giai đoạn 4 — Verification and release readiness

- Install/use JDK 21 and reproduce the 21 Feature-011 tests.
- Add tests for Guest retail review, Guest handle expiry/replay, raw source-ID rejection, concurrent idempotent commands and Admin denial.
- Add tests for 2,001-character text rejection and header-based idempotency.
- Add persistence tests proving audit/revision/reply history is retained after `REMOVED`.
- Add API contract validation against the updated OpenAPI document.
- Add architecture/Spring Modulith verification for the new module.
- Add frontend integration after the backend contract is finalized.

### Giai đoạn 5 — Frontend handoff

- Implement Guest eligibility lookup and handle storage with the agreed security policy.
- Implement Member eligible-source list.
- Implement create/edit review forms with 1–5 stars and 2,000-character text limit.
- Implement public review cards, filters and realtime aggregate display.
- Implement Staff moderation and Manager moderation/reply screens.
- Do not expose Guest handle, source ownership evidence, raw contact data or moderation audit details in public UI.

---

## 3. Active blockers and audit notes

1. **Environment blocker:** Current Java runtime is 17.0.10; Maven project requires Java 21. Test execution cannot be treated as fresh evidence until JDK 21 is active.
2. **Contract verification pending:** Header-based idempotency/version inputs and 2,000-character limits are implemented but require JDK 21 test evidence.
3. **Business-permission verification pending:** Admin denial and Staff/Manager boundaries require controller/integration tests.
4. **Eligibility verification pending:** Guest retail review is implemented through the fulfilment adapter but requires database-backed contact matching tests.
5. **Privacy verification pending:** Public/owner/moderation projections require response-shape tests.
6. **Retention verification pending:** `V97` replaces the historical cascade constraints but requires clean/upgrade Flyway execution.
7. **Evidence status:** The 21 test cases are present and previously reported, but no new pass result is claimed from the current Java 17 environment.
8. **Migration status:** Feature-011 was renamed from `V89` to `V91` after merging `develop`, which already contains `V89` and `V90` migrations. Clean-install and existing-database validation remain outstanding.

---

## 4. Traceability index

| Phase | Current evidence | Remaining evidence |
| --- | --- | --- |
| Phase A | `features/reviewmanagement` module, ports, adapters and security integration | Final Modulith dependency verification |
| Phase B | `V91__review_management.sql`, six logical tables and unique source constraint | Retention-safe FK policy and MySQL upgrade evidence |
| Phase C | Guest handle, Member lookup, Workshop/Retail adapters | Guest retail path, handle replay/consumption and final ownership evidence |
| Phase D | Application, moderation, reply, masking and idempotency services | Admin boundary, conflict-of-interest and complete idempotency enforcement |
| Phase E | Controllers and exception handler for ten endpoint operations | OpenAPI/DTO/header alignment and endpoint authorization tests |
| Phase F | 21 Feature-011 test cases exist | Fresh execution under JDK 21 and expanded edge-case coverage |
| Phase G | No Feature-011 frontend implementation recorded here | React/Vite implementation and integration verification |

---

## 6. Merge resolution log — `develop` into `dat`

The merge completed without textual conflict markers, but migration analysis
found semantic Flyway version collisions:

1. Feature-011 `V89__review_management.sql` conflicted with `develop`’s occupied `V89` versions. It was renamed to `V91__review_management.sql`.
2. `develop` contained two separate `V89` scripts: `V89__member_shipping_addresses.sql` and `V89__workshop_package_foundry_working_day_rule.sql`. The account migration remains `V89` because its feature artifacts and verification reference that version; the workshop package migration was renamed to `V92__workshop_package_foundry_working_day_rule.sql` and its header was updated.
3. `V90__workshop_operations_global_idempotency_key.sql` remains unchanged.

The repository must still be validated against `flyway_schema_history` before
any database application. No `baseline`, checksum repair or out-of-order
execution was used to hide the duplicate-version problem.

---

## 5. Current backend flow and data flow

### 5.1 Guest eligibility flow

```text
POST /api/v1/review-eligibility/lookup
  → ReviewEligibilityController
  → ReviewEligibilityService.lookupGuestEligibility
  → normalize contact input
  → InternalWorkshopAttendanceAdapter.findEligibleByContact
  → ReviewRepository excludes already-reviewed source references
  → generate random Guest handle
  → SHA-256 handle hash
  → GuestEligibilityHandleRepository.save
  → return masked contact, eligible sources, handle and expiry
```

Current implementation creates Guest handles from Workshop attendance results.
The Guest retail lookup path is not yet wired into Guest creation.

### 5.2 Member eligibility flow

```text
GET /api/v1/reviews/eligible-sources
  → ActorContext.require current Member actor
  → ReviewEligibilityService.getMemberEligibleSources
  → WorkshopAttendanceReviewPort.findEligibleByMemberId
  → RetailFulfilmentReviewPort.findEligibleByMemberId
  → ReviewRepository checks existing source reviews
  → return eligible source list with masked Member display name
```

### 5.3 Review creation flow

```text
POST /api/v1/reviews
  → ReviewController
  → ActorContext resolves authenticated Member or Guest path
  → ReviewApplicationService.createReview
  → ReviewIdempotencyService lookup/record pending
  → ReviewRepository duplicate-source check
  → Member: verify source through Workshop/Retail port
  → Guest: validate handle + source binding + Workshop attendance port
  → verify target type/reference
  → create Review(status = PUBLIC)
  → create ReviewRevision(revision = 1)
  → save review and revision transactionally
  → record idempotency success
  → return ReviewDto
```

### 5.4 Public read flow

```text
GET /api/v1/reviews?targetType=...&targetReference=...
  → ReviewController
  → ReviewPublicReadFacade
  → ReviewPublicReadServiceImpl
  → query status = PUBLIC
  → calculate count and averageRating in realtime
  → map masked ReviewDto and current reply
  → return PublicReviewPageDto
```

### 5.5 Moderation and reply flow

```text
PATCH /api/v1/reviews/{id}/visibility
  → ReviewModerationController
  → ActorContext current Staff/Manager/Admin
  → ReviewModerationService.changeVisibility
  → role/action validation
  → ReviewRepository.load
  → update status
  → ReviewModerationEventRepository.save
  → return ModerationItemDto

POST/PATCH /api/v1/reviews/{id}/reply
  → ReviewModerationController
  → role validation
  → ReviewReplyService.createReply/editReply
  → ReviewReplyRepository
  → ReviewModerationEventRepository audit
  → return ReplyDto
```

The flows above describe the ratified contract and current implementation
direction. Runtime execution and database verification are still required before
they are treated as integration evidence.

## 7. Frontend specification synchronization (2026-11-05)

Feature-011 now has a frontend implementation handoff aligned with the approved
OpenAPI/spec and the backend contract-alignment changes.

### 7.1 Frontend target

- React 19 + Vite 7 with real `react-router` routes.
- Feature-owned `routes`, `ui`, `application`, `domain`, `infrastructure` and `tests` layers.
- Shared HTTP, Firebase auth, i18n and UI primitives; no new state library or UI kit.
- Backend authority for eligibility, ownership, permission, status, aggregate and privacy filtering.
- One `Idempotency-Key` per write command; retries reuse the same key.
- `If-Match-Version` for owner edit, moderation and reply edit where defined by OpenAPI.

### 7.2 Frontend delivery phases

1. Foundation: routes, API adapter, shared auth/i18n/UI integration.
2. Customer flow: Guest lookup, Member eligible sources, create/edit review and public listing/detail.
3. Operations flow: Staff hide, Manager hide/remove/reply/edit-reply and Admin technical projection.
4. Verification: API mapping, permission, ownership, privacy, validation, conflict and recovery tests, then `npm test` and `npm run build`.

### 7.3 Current verification gate for live frontend integration

- Header-based idempotency, optimistic versions and deterministic conflicts require runtime tests.
- Guest retail-product eligibility/create requires integration verification against fulfilment data.
- Review detail ownership and cross-user enumeration behavior require security tests.
- Admin technical-only authority requires authorization tests.
- DTO names, status codes, headers and migration/API behavior require contract validation.

The frontend agent must not add a compatibility layer or infer a different
payload without a separately approved versioned contract.

## 8. Figma MCP and frontend implementation record (2026-11-05)

Figma MCP created the editable visual handoff in file
`m5Cyk5rit6lKaX3TPprSow`:

- Public customer section: `F11 · Review Management` (`413:203`).
- Public list/detail: `F11-PUBLIC-REVIEW-LIST` (`413:204`) and
  `F11-PUBLIC-REVIEW-DETAIL` (`416:24`), both with mobile variants.
- Guest lookup: `F11-GUEST-LOOKUP` (`413:264`).
- Member sources: `F11-ELIGIBLE-SOURCES` (`413:302`).
- Create/edit: `F11-CREATE-REVIEW` (`413:350`) and `F11-EDIT-REVIEW` (`413:392`).
- Staff operations: `F04 + F11 · Staff Operations` (`414:1083`) with
  `F11-MODERATION-QUEUE` (`414:1128`) and `F11-MODERATION-DETAIL` (`414:1153`)
  desktop/mobile variants.
- Manager operations: `F04 + F11 · Manager Operations` (`414:2983`) with
  `F11-MODERATION-DETAIL · Manager Reply` (`414:3002`) desktop/mobile variants.

The frames explicitly keep branch outside the review target and do not expose
email, phone or transaction details in public projections. The visual revision
still requires reviewer approval; no agent self-approval is recorded here.

The React/Vite implementation now contains the Feature-011 route slice under
`ppswbs_frontend/src/features/reviewmanagement/`, including the OpenAPI API
adapter, public list/detail, guest lookup, Member eligible sources, create/edit
review, Staff/Manager moderation and Manager reply UI. It uses shared HTTP,
Firebase auth, i18n, `useResource`, `useCommand`, idempotency keys and optimistic
version headers. Backend remains authoritative.

The backend contract-alignment implementation is now present for header-based
idempotency/version inputs, Guest retail eligibility, owner-safe public detail,
Admin denial, 2,000-character validation and `V97` history-retention repair.
Fresh verification remains pending because Maven Wrapper execution is blocked in
the current environment and JDK 21 is required.

## 9. Backend contract ratification implementation log (2026-11-05)

Completed in this update:

1. Standardized `Idempotency-Key` on lookup, create, edit, visibility and reply commands.
2. Required `If-Match-Version` for review edit, visibility change and reply edit.
3. Added Guest retail eligibility through the fulfilment adapter using account contact matching; raw contact data remains outside public DTOs.
4. Added source-type-aware Guest handle binding for workshop and retail sources with the existing 30-minute TTL.
5. Restricted create/eligible-source operations to Member when Firebase identity is present; Staff/Manager/Admin cannot create Member reviews.
6. Restricted Staff to hide, Manager to moderate/reply, and Admin from business moderation/reply endpoints.
7. Added owner-safe/public projection behavior and removed technical owner identifiers from public replies.
8. Added conflict-of-interest checks for moderation and Manager replies.
9. Added `V97__review_history_retention.sql` (renumbered from V93 to resolve sequence conflict with develop V93-V96) to replace destructive history cascades with `RESTRICT`.
10. Updated Feature-011 OpenAPI to version `1.0.0`, status `approved`, and synchronized `APIs.md`, `spec.md`, `data-model.md`, `plan.md`, `tasks.md`, `research.md` and `DB.md`.

Remaining verification tasks:

- Run backend compile and all Feature-011 tests under JDK 21.
- Execute the updated unit/MockMvc tests for the header contract and Guest retail path.
- Run OpenAPI validation against controller behavior.
- Run Flyway clean-install, upgrade, rerun and `flyway validate()` checks through `V97`.
- Confirm the live `flyway_schema_history` contains no conflicting `V91` or `V97` record before applying migrations.
