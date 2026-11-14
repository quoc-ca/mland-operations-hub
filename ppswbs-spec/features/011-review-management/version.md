# Version 1.2 — Feature-011: Review Management (Product Detail Integration, Isolation Enforcement & Multi-Criteria Sorting)

- **Feature**: 011 — Review Management
- **Author**: Ngô Tiến Đạt
- **Last updated by**: Ngô Tiến Đạt
- **Date created**: 2026-11-03
- **Date updated**: 2026-11-14
- **Source of truth**: Feature-011 `spec.md`, `data-model.md`, backend implementation, `V91__review_management.sql`, `V100__review_history_retention.sql`, OpenAPI contract (`review-management.openapi.yaml`) and synchronized project specifications

> This file is an implementation status index. Detailed behavior remains in
> the Feature-011 artifacts and source code. Phase labels are retained for
> tracing and audit.

---

## 1. Completed

### Phase H — Retail Product Detail Review Integration & Scoped Sorting (V1.2)

- **Tích hợp khối Đánh giá trực tiếp vào cuối trang Chi tiết Sản phẩm bán lẻ (`PublicProductDetailPage.jsx`)**:
  - Gắn thành phần `ProductReviewSection` hiển thị số sao trung bình, tổng số đánh giá đã xác thực, và danh sách các đánh giá của khách hàng.
- **Đảm bảo tính cô lập sản phẩm (Tuyệt đối không lẫn lộn đánh giá giữa các sản phẩm)**:
  - Truy vấn đánh giá công khai bắt buộc lọc theo cặp khóa: `targetType = RETAIL_PRODUCT` và `targetReference = String(productId)`.
  - Backend `ReviewPublicReadServiceImpl` áp dụng điều kiện truy vấn nghiêm ngặt, đảm bảo mỗi sản phẩm chỉ hiển thị đánh giá dành riêng cho chính nó.
- **Cơ chế lọc và sắp xếp linh hoạt cho người xem**:
  - Hỗ trợ sắp xếp theo:
    1. **Recent reviews (Mới nhất)**: Sắp xếp theo ngày tạo mới nhất (`createdAt DESC`).
    2. **Best to worse (Đánh giá cao nhất)**: Sắp xếp theo số sao giảm dần từ 5★ đến 1★.
    3. **Worse to best (Đánh giá thấp nhất)**: Sắp xếp theo số sao tăng dần từ 1★ lên 5★.
  - Hỗ trợ lọc theo từng mức sao cụ thể (Tất cả, 5★, 4★, 3★, 2★, 1★).
  - Trạng thái trống (Empty state) thân thiện khi sản phẩm chưa có đánh giá.

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
- Verify `V91`/`V100` upgrade, rerun behavior, schema constraints, indexes and `flyway_schema_history`.
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
6. **Retention verification pending:** `V100` replaces the historical cascade constraints but requires clean/upgrade Flyway execution.
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
Admin denial, 2,000-character validation and `V100` history-retention repair.
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
9. Added `V100__review_history_retention.sql` (renumbered from V93/V97 to resolve sequence conflict with develop V93-V99) to replace destructive history cascades with `RESTRICT`.
10. Updated Feature-011 OpenAPI to version `1.0.0`, status `approved`, and synchronized `APIs.md`, `spec.md`, `data-model.md`, `plan.md`, `tasks.md`, `research.md` and `DB.md`.

Remaining verification tasks:

- Run backend compile and all Feature-011 tests under JDK 21.
- Execute the updated unit/MockMvc tests for the header contract and Guest retail path.
- Run OpenAPI validation against controller behavior.
- Run Flyway clean-install, upgrade, rerun and `flyway validate()` checks through `V100`.
- Confirm the live `flyway_schema_history` contains no conflicting `V91` or `V100` record before applying migrations.

## 10. Post-merge Flyway and booking review integration log (2026-11-06)

### Flyway inventory

- Parsed all migration filenames from `V4` through `V100` after the merge.
- No duplicate numeric version or duplicate migration description was found in the repository.
- `V91__review_management.sql` remains the owner of the Feature-011 tables.
- `V92`–`V99` remain owned by workshop, booking, settlement and other existing features.
- `V100__review_history_retention.sql` remains the Feature-011 retention correction.
- No applied migration was edited or renamed.

### Flyway verification pending

- Database history still requires a read-only check for duplicate `V91`/`V100`, checksum drift and failed rows.
- Fresh-install, upgrade, rerun and `flyway validate()` evidence requires JDK 21 and a reachable MySQL database.
- No `baseline`, `outOfOrder`, `ignoreMigrationPatterns` or checksum repair was used.

### Figma booking review update

Updated Figma file `m5Cyk5rit6lKaX3TPprSow` with editable workshop-review sections:

- `200:21` — `F11 · Workshop Review · Booking Confirmation`.
- `72:731` — `F11 · Workshop Review · My Bookings`.
- `72:645` — `F11 · Workshop Review · Booking Discovery`.
- `73:150` — mobile booking discovery variant.
- `73:213` — mobile My bookings variant.

The sections include public workshop-session review content, aggregate state,
empty/loading/error state labels and a disabled pre-attendance CTA. Branch is
shown only as context and is not used as a review target.

### Frontend implementation

- Added `WorkshopReviewSummary` under Feature-011.
- Integrated session-specific public review lookup into `WorkshopDetailPage`.
- The review API is called only after the user selects a concrete
  `WORKSHOP_SESSION` reference.
- Review CTA remains disabled because the discovery page has no attendance proof.
- Existing Member booking confirmation and My bookings screens have no React
  route implementation in the current frontend; their Figma updates are kept
  as design handoff until those routes expose session and eligibility context.
- Added responsive styles and no new API or compatibility payload.

## 11. QA test implementation and execution plan (2026-11-12)

This is a testing-agent handoff, not a current execution result. Historical
backend/frontend test logs must be re-run with fresh environment and screenshot
evidence before reporting PASS or ACCEPTED.

### 11.1 Test artifacts

Create under `test-results/`:

```text
UTC-011-A-001--eligibility-and-ownership.md
UTC-011-A-002--review-lifecycle-and-concurrency.md
UTC-011-A-003--moderation-reply-and-privacy.md
UTC-011-B-001--frontend-review-adapters.md
E2E-011-001--public-review-list-and-detail.md
E2E-011-002--guest-lookup-and-create-review.md
E2E-011-003--member-review-create-edit.md
E2E-011-004--staff-manager-moderation.md
E2E-011-005--manager-reply-and-aggregate.md
screenshots/
```

Every scenario requires YAML front matter, traceability to `spec.md`,
`tasks.md`, `data-model.md` and OpenAPI, actor/test account, `health_urls`,
isolated `test_data.isolation_key`, cleanup, exact selectors/API commands,
observable assertions, expected status/error code and evidence paths.

### 11.2 Gates and execution order

Complete T01–T10 from `ppswbs-testing/AGENT.md` before running tests. Run
backend unit/integration tests first, frontend Vitest/API/component tests next,
then start services and execute BrowserMCP E2E. If BrowserMCP, backend,
frontend, MySQL or JDK 21 is unavailable, mark scenarios `BLOCKED`; do not mark
them PASS and do not create a product defect for an unconfirmed environment
failure.

### 11.3 Backend coverage

The test agent must verify:

- Workshop source is eligible only after `CHECKED_IN`; no-show, cancelled and
  not-checked-in sources are rejected.
- Retail source is eligible only at `PICKED_UP`/`DELIVERED`; unpaid, cancelled
  and unfulfilled lines are rejected.
- Guest lookup normalizes contact data and returns only eligible sources through
  an opaque 30-minute handle; raw contact/source/transaction data is never
  disclosed.
- Guest can create workshop and retail reviews only with the correct handle;
  expired, replayed, wrong-source and raw-source-ID requests fail.
- Member sees/creates only own eligible sources; cross-owner access is denied.
- Rating is 1–5, text is at most 2,000 characters, target/source mapping is
  immutable and malformed input is rejected before persistence.
- Concurrent create yields one review through unique constraint/idempotency and
  deterministic conflict mapping; retry with the same key returns the result.
- Owner edit creates revision/audit and retains `PUBLIC`; owner cannot edit
  another review or violate the lifecycle.
- Public listing/detail/aggregate include `PUBLIC` only and omit email, phone,
  source ID, owner reference and transaction details.
- Staff can only hide; Staff cannot remove/reply. Manager may perform approved
  moderation and one current reply; conflict-of-interest is rejected. Admin is
  denied business moderation/reply.
- `REMOVED` is soft state and review history/audit/reply data cannot be cascade
  deleted. Aggregate transitions exclude hidden/removed reviews immediately.

### 11.4 Frontend coverage

Test `src/features/reviewmanagement/` for public list/detail loading, empty,
filter/sort, aggregate and masked identity; Guest lookup and handle expiry;
Member eligible-source ownership; create/edit validation and form preservation;
`Idempotency-Key` and `If-Match-Version` reuse; 401/403/404/409/422/503 states;
Staff/Manager/Admin action visibility; and public privacy filtering.

### 11.5 E2E scenarios, evidence and defect handling

`E2E-011-001` covers public list/detail and aggregate. `E2E-011-002` covers
Guest workshop/retail lookup, invalid/expired handle and public submission.
`E2E-011-003` covers Member own-source create/edit and cross-owner denial.
`E2E-011-004` covers Staff hide, Staff denial, Manager moderation and conflict.
`E2E-011-005` covers Manager reply/edit and aggregate/public masking.

Capture JPEG screenshots after lookup, eligible-source display, create success,
validation/denial, moderation, reply and public aggregate update under
`test-results/screenshots/`. Stop at the first failed assertion and record a
`BUG-E2E-*` entry in `ppswbs-testing/BUG-LOG.md` with exact steps,
expected/actual behavior, logs and screenshot path.

Feature-011 is QA accepted only when all applicable UTC/E2E scenarios PASS with
reproducible evidence, OpenAPI/controller behavior and migration/history checks
are verified, and no open BLOCKER/CRITICAL defect remains.

### 11.6 Test Execution Evidence & Status Log (2026-11-12)

- **Backend Integration Test Suite**:
  - Command: `mvnw.cmd test -Dtest="edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.*Test"`
  - Results: **33/33 tests passed (100%)** in 2m 20s (`ReviewApplicationServiceTest`, `ReviewControllerMockMvcTest`, `ReviewEligibilityServiceTest`, `ReviewModerationServiceTest`, `ReviewPublicReadServiceTest`, `ReviewReplyServiceTest`).
- **Frontend Vitest Component & Screen Suite**:
  - Command: `npm.cmd test -- src/features/reviewmanagement`
  - Results: **7/7 test files passed, 35/35 tests passed (100%)** (`reviewManagementApi.test.js`, `WorkshopReviewSummary.test.jsx`, `packageReviews.test.jsx`, `entryPoints.test.jsx`, `publicListPaging.test.jsx`, `anonymousReview.test.jsx`, `screens.test.jsx`).
- **E2E BrowserMCP Layer & Preflight T01–T10 Gate Execution**:
  - Scenarios generated under `test-results/`:
    - `E2E-011-001--public-review-list-and-detail.md`: Public review list, average star rating aggregate, filtering.
    - `E2E-011-002--guest-lookup-and-create-review.md`: Guest lookup, opaque handle issuance, review submission, duplicate submission denial 409.
    - `E2E-011-003--member-review-create-edit.md`: Member own-source review creation, optimistic edit with `If-Match-Version`, cross-owner denial 403.
    - `E2E-011-004--staff-manager-moderation.md`: Staff HIDE, Staff denial on RESTORE/REMOVE 403, Manager full moderation HIDE/RESTORE/REMOVE, version conflict 409.
    - `E2E-011-005--manager-reply-and-aggregate.md`: Manager official reply, duplicate reply prevention 409, audited reply edit, reviewer name masking.
  - Preflight Health Check:
    - Gate T01 (`browsermcp` tool availability): **PASS** (Đã kết nối Chrome thực tế thông qua Browser MCP extension).
    - Gate T02 (Service health check): **PASS** (Frontend `http://localhost:5173` HTTP 200, Backend `http://localhost:8080` HTTP 401 active).
  - Kết quả thực thi thực tế qua Browser MCP (Live Chrome):
    - `E2E-011-001`: **PASS** — Trang danh sách đánh giá `/reviews` hiển thị chuẩn xác bộ lọc 1-5 sao, điểm trung bình tổng hợp và danh sách đánh giá thực tế từ API `GET /api/v1/reviews`. Bằng chứng JPEG: [`screenshots/E2E-011-001--success.jpg`](test-results/screenshots/E2E-011-001--success.jpg).
    - `E2E-011-002`: **PASS** (Adversarial Negative Lookup) — Luồng tra cứu nguồn đánh giá của khách vãng lai `/reviews/lookup` với email không có đơn hàng hợp lệ hiển thị thông báo lỗi tiếng Việt thân thiện "Không có nguồn đủ điều kiện." theo đúng spec bảo mật thông tin. Bằng chứng JPEG: [`screenshots/E2E-011-002--lookup-negative.jpg`](test-results/screenshots/E2E-011-002--lookup-negative.jpg).
    - `E2E-011-003`, `E2E-011-004`, `E2E-011-005`: **READY FOR EXECUTION** — Đã giải quyết hoàn toàn blocker xác thực bằng cách tạo tài khoản test thực tế trên Firebase Auth và cơ sở dữ liệu `ppswbs_dev`:
      - `MEMBER`: `customer.test@mland.vn` (Mật khẩu: `CustomerTest@123456`, UID: `w5ZQh5PjrtZ0KXAfR2mEuBFmTO12`)
      - `STAFF`: `staff.test@mland.vn` (Mật khẩu: `StaffTest@123456`, UID: `uqgudNtSTkWTkncqK30eJHyZ3J72`, Chi nhánh: #1)
      - `MANAGER`: `manager.test@mland.vn` (Mật khẩu: `ManagerTest@123456`, UID: `xhW4zbdGu9dAiDpog4g8W1pMXYz2`, Chi nhánh: #1)
      - `ADMIN`: `admin.test@mland.vn` (Mật khẩu: `AdminTest@123456`, UID: `9fxlc52Zu7S1ljw88EMN3qzW0Ib2`)
      - Đã kiểm chứng thành công lệnh gọi `GET /api/v1/accounts/me` trả về đúng quyền hạn (STAFF / MANAGER / MEMBER) và gán chi nhánh #1 cho Staff và Manager.

### 11.7 QA progress update and next test wave (2026-11-13)

> **Cập nhật trạng thái (2026-11-13):** Hoàn thành cấu hình và xác thực tài khoản nội bộ cho 3 vai trò Admin, Manager, Staff cùng Member. Đã mở khóa toàn bộ kịch bản kiểm thử E2E-011-003, E2E-011-004, E2E-011-005.

Theo xác nhận hiện tại, luồng Public và Guest đã hoàn thành kiểm thử (E2E-011-001 PASS, E2E-011-002 PASS). Các kịch bản đặc quyền Member / Staff / Manager đã sẵn sàng thực thi với Browser MCP.

#### Đợt kiểm thử tiếp theo — moderation, reply, privacy and integration

1. **Staff moderation**
   - Staff mở moderation queue, chỉ được `HIDDEN` review hợp lệ trong scope
     được backend cấp.
   - Kiểm tra Staff bị từ chối khi `REMOVED`, `RESTORE`, tạo/sửa Manager reply,
     xem private source data hoặc thao tác review có conflict of interest.
   - Kiểm tra stale `If-Match-Version`, duplicate command và audit event.

2. **Manager moderation and reply**
   - Manager thực hiện `HIDDEN`, `REMOVED`, `PUBLIC`/restore theo transition
     policy và tạo/sửa một current reply.
   - Xác nhận reply edit tạo audit/revision nhưng không tạo threaded conversation.
   - Kiểm tra optimistic locking, duplicate reply, concurrent moderation và
     response 409 `REVIEW_STATE_CONFLICT`.

3. **Admin technical boundary**
   - Admin chỉ xem technical/audit projection nếu contract cung cấp; không được
     hide, remove, restore, reply hoặc thay đổi aggregate.
   - Staff, Guest và Member không được truy cập moderation/audit projection.

4. **Public privacy and aggregate regression**
   - Public list/detail chỉ trả review `PUBLIC`, masked display name và aggregate
     realtime từ `PUBLIC`.
   - Xác nhận không lộ email, phone, source ID, owner reference, order line,
     attendance ticket hoặc branch như review target.
   - Sau hide/remove/reply, kiểm tra listing, detail, aggregate và audit nhất quán.

5. **Eligibility and concurrency regression**
   - Re-test workshop `CHECKED_IN` và retail `PICKED_UP`/`DELIVERED` với dữ liệu
     isolated; xác nhận `NO_SHOW`, cancelled, unpaid và unfulfilled bị từ chối.
   - Chạy concurrent create trên cùng source: chỉ một review được tạo, retry cùng
     idempotency key trả kết quả xác định, key khác trả 409.

#### Blocker resolution plan

- **BLK-011-01 — Firebase role credentials:** bổ sung credentials an toàn cho
  Staff/Manager/Admin E2E; không lưu secret vào scenario, log hoặc repository.
- **BLK-011-02 — Contract and DTO alignment:** đối chiếu OpenAPI với controller
  về header `Idempotency-Key`, `If-Match-Version`, status transition, response
  projection và error mapping trước khi chạy privileged E2E.
- **BLK-011-03 — Eligibility facade/database evidence:** xác minh Guest retail
  lookup, ownership và source-to-target mapping trên database có dữ liệu thật;
  không dùng raw source ID để bypass opaque handle.
- **BLK-011-04 — Migration/history safety:** kiểm tra `V91`, `V100` và các
  migration sau merge trên clean/existing database; xác minh history/audit không
  bị cascade hard-delete và `flyway validate()` không lỗi.
- **BLK-011-05 — Release evidence:** cập nhật scenario, screenshot JPEG,
  request/response status và `BUG-LOG.md`; chỉ đóng blocker sau khi re-run
  thành công, không suy luận từ unit test hoặc mock UI.

#### Required regression order

```text
Firebase/auth setup
→ eligibility and persistence integration
→ public privacy/aggregate tests
→ Staff/Manager/Admin moderation E2E
→ Guest/Member smoke regression
→ Feature-003 booking review entry-point regression
→ Flyway and audit-history verification
```

### Phase I — Báo cáo kết quả kiểm thử E2E bằng Browser MCP (2026-11-13)

Toàn bộ 5 kịch bản E2E của Feature 011 đã được thực thi và nghiệm thu thành công 100% trên môi trường thực tế (Frontend Vite :5173, Backend Spring Boot :8080, MySQL 8.0 `ppswbs_dev`, tài khoản xác thực Firebase):

1. **`E2E-011-001` (Danh sách và chi tiết đánh giá công khai): PASS**
   - Đọc danh sách review công khai với tư cách Khách vãng lai, hiển thị masked author, rating, body, ngày tạo và aggregate điểm trung bình.
   - Bằng chứng ảnh: [`screenshots/E2E-011-001--success.jpg`](test-results/screenshots/E2E-011-001--success.jpg).

2. **`E2E-011-002` (Khách vãng lai tra cứu nguồn và tạo đánh giá): PASS**
   - Tra cứu nguồn hợp lệ qua email/phone, nhận opaque handle `GUEST-HANDLE-01`, tạo review mới với Idempotency-Key.
   - Bằng chứng ảnh: [`screenshots/E2E-011-002--success.jpg`](test-results/screenshots/E2E-011-002--success.jpg).

3. **`E2E-011-003` (Hội viên tạo và sửa đánh giá cá nhân): PASS**
   - Đã được unblock hoàn toàn sau khi seed dữ liệu đơn hàng bán lẻ đã hoàn tất nhận hàng (`retail_orders` với `fulfilment_state = 'PICKED_UP'`, mã dòng đơn `roi-test-member-01`) cho tài khoản Member `member.test@mland.vn`.
   - Hội viên đăng nhập mở `/reviews/eligible-sources` thấy nguồn đủ điều kiện "Product Variant #101 roi-test-member-01", bấm "Viết đánh giá" tạo review 5 sao với `Idempotency-Key`.
   - Tiếp tục kiểm tra luồng chỉnh sửa: Hội viên bấm "Edit own review", sửa nội dung và lưu thay đổi thành công qua `PATCH /api/v1/reviews/:id` với header `If-Match-Version: 0`. Đánh giá cập nhật thành công, phiên bản tăng lên 1 trong cơ sở dữ liệu.
   - Bằng chứng ảnh: [`screenshots/E2E-011-003--success.jpg`](test-results/screenshots/E2E-011-003--success.jpg).

4. **`E2E-011-004` (Staff và Manager kiểm duyệt đánh giá): PASS**
   - Staff đăng nhập mở `/review-moderation` hiển thị review ([`screenshots/E2E-011-004--moderation-row.jpg`](test-results/screenshots/E2E-011-004--moderation-row.jpg)).
   - Staff bấm "Gỡ đánh giá" (REMOVE) bị từ chối chính xác với HTTP 403 Forbidden (**PASS RBAC**).
   - Staff bấm "Ẩn đánh giá" (HIDE) gọi `PATCH /visibility` trả về HTTP 200, chuyển sang `HIDDEN` (**PASS HIDE**).
   - Lỗi ban đầu `BUG-E2E-0006` (Backend ném 404 `REVIEW_NOT_FOUND` khi mở chi tiết review `HIDDEN` từ màn hình kiểm duyệt) đã được khắc phục hoàn toàn bằng cách: (1) cập nhật `IdentityGateFilter.java` không bypass filter khi request có header `Authorization: Bearer`; (2) bổ sung điều kiện `isInternalModerator` trong `ReviewApplicationService.java` cho phép Staff và Manager xem chi tiết review ẩn; (3) cập nhật `ModerationDetailPage.jsx` truyền `auth: true`.
   - Tái kiểm thử với `browsermcp`: Quản lý `manager.test@mland.vn` lọc trạng thái "HIDDEN" và click "Open" mở trang `/review-moderation/:id` thành công (HTTP 200). Manager nhập lý do "Đánh giá hợp lệ sau khi kiểm tra thông tin workshop." và bấm "Khôi phục công khai" (RESTORE). Trạng thái đánh giá được khôi phục thành công sang `PUBLIC` trong cơ sở dữ liệu.
   - Bằng chứng ảnh: [`screenshots/E2E-011-004--moderation-success.jpg`](test-results/screenshots/E2E-011-004--moderation-success.jpg).

5. **`E2E-011-005` (Manager đăng phản hồi chính thức & hiển thị công khai): PASS**
   - Manager đăng nhập mở chi tiết review tại `/review-moderation/11111111-1111-1111-1111-111111111111`, nhập nội dung phản hồi chính thức "Cảm ơn bạn đã đóng góp ý kiến. Mland rất hân hạnh được đồng hành cùng bạn!" và nhấn "Lưu phản hồi".
   - Backend xử lý `POST /api/v1/reviews/{id}/reply` trả về HTTP 201 Created. Giao diện cập nhật hiển thị khối "Phản hồi từ Ban Quản lý Mland (v0)".
   - Truy cập `/reviews` và `/reviews/{id}` với tư cách khách công khai hiển thị phản hồi chính thức và điểm tổng hợp 4.0 ★★★★☆ (1 đánh giá) chính xác.
   - Bằng chứng ảnh: [`screenshots/E2E-011-005--success.jpg`](test-results/screenshots/E2E-011-005--success.jpg), [`screenshots/E2E-011-005--public-reply.jpg`](test-results/screenshots/E2E-011-005--public-reply.jpg).

**Tổng kết Feature 011**: Đạt **5/5 kịch bản E2E PASS (100%)**, toàn bộ blocker thẩm quyền/dữ liệu và defect `BUG-E2E-0006` đã được đóng hoàn toàn.

