# Implementation Plan: Product Management

**Date created**: 2026-10-24
**Date updated**: 2026-10-24 (bổ sung thiết kế import Excel — US9, FR-039–FR-045)
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Feature context**: `002-product-management` | **Spec**: [spec.md](spec.md)  
**Git branch**: `khuong` được giữ nguyên; không tạo/switch nhánh. `002-product-management` là feature context của SpecKit, không phải git branch hiện tại.  
**Status**: Approved — duyệt bởi Đặng Thị Khương ngày 2026-10-24; thiết kế là baseline cho triển khai, các task phụ thuộc bên ngoài (FE-01 contract xác thực, FE-08 nguồn giữ hàng, FE-06 T004, Cloudinary, kế hoạch frontend) chỉ bắt đầu khi dependency tương ứng đạt.   Phần bổ sung import Excel (US9) được duyệt bởi Đặng Thị Khương ngày 2026-10-24.
**Input**: FE-02 có hai quyết định clarify ngày 2026-10-23 và các câu trả lời bổ sung ngày 2026-10-24 về roles, Cloudinary, retention, defaults và hướng A: dùng chung cơ chế xác thực theo FE-01/001, kiểm tra thêm quyền Staff/Manager; MFA/recovery do identity/FE-01 sở hữu. Chỉ hoàn thiện tài liệu, không code/test/migration/deploy.

## Summary

Triển khai nhẫn bán sẵn trong module productmanagement: tạo sản phẩm với size cố định 5–29 và SKU tự sinh, gửi Manager duyệt, áp dụng đúng giá đã duyệt rồi chủ động công khai. Sửa nội dung trực tiếp sau công khai lần đầu; mọi đổi giá vẫn phải duyệt. Có quản lí ảnh, lượng bán chung, ngừng bán từng size, ẩn/công khai, xóa mềm không khôi phục, danh mục và public search/filter/sort theo spec.

Bổ sung import Excel (US9): tải file mẫu `.xlsx`, tải lên để kiểm tra (không tạo dữ liệu), xem trước/báo cáo lỗi theo dòng–cột, rồi xác nhận để tạo mọi sản phẩm nháp trong một transaction qua đúng `CreateProductService`. Không thay đổi vòng đời duyệt/giá/công khai và không có ảnh trong file.

Giữ module đóng; JPA/Flyway và MySQL 8.4, REST API versioned phục vụ frontend React + Vite tách riêng. Catalogue sở hữu quantity, FE-08 sở hữu hold; availability SPI giữ compile dependency readyringsales → productmanagement và đồng bộ khóa. Không dùng fallback zero-hold, ROLE_MEMBER, provider giả hoặc thiết kế 001 chưa triển khai để lấp dependency.

## Technical Context

| Item | Selected/proposed design |
| --- | --- |
| Language/Version | Java 21 theo pom hiện hành. |
| Primary dependencies | Spring Boot 3.4.1, Modulith 1.3.1, Firebase Admin 9.11.0, JPA/Flyway, Security, Validation theo pom (Thymeleaf còn trong pom nhưng không dùng cho UI); frontend React + Vite tách riêng, chưa khởi tạo; không nâng version. |
| Storage | MySQL 8.4/InnoDB để kiểm chứng; bảng catalogue mới, DECIMAL(19,0), UTC timestamps, append-only migration. Cloudinary đã được chọn; adapter đề xuất signed server-side upload, authenticated assets và stream cùng origin. SDK version cần pin sau kiểm tra tương thích; không nâng dependency hiện có. |
| Testing | JUnit/Spring Boot Test/MockMvc hiện có; MySQL thật cho migration/locking/collation, H2 bổ trợ. Fixture dependency không phải integration evidence. |
| Target platform | Frontend React + Vite (client-side rendering) tách riêng gọi Spring Boot REST; không chọn production host hoặc deployment topology. |
| Project type | Spring Modulith modular monolith hiện có, thêm nội dung vào module productmanagement đang là shell. |
| Performance goals | Chưa baselined; không tự đặt throughput/latency/SLA. Thiết kế batch availability, phân trang và query theo product IDs; kiểm chứng tính đúng trước benchmark. |
| Constraints | Không khắc; size không thêm/đổi sau lưu; Manager tự duyệt được; giá duyệt/áp dụng/công khai riêng; UI React chỉ qua REST; giữ giá/lịch sử giao dịch; không sửa role mapping ngầm. |
| Scale/scope | 1–25 variants/product, tối đa 10 ảnh 5.000.000 bytes; 20 product/page và q tối đa 200 đã chốt. Import Excel: ≤ 5.000.000 bytes, ≤ 500 dòng/file (đã chốt). Tổng catalogue/concurrency production chưa xác định. |
| Excel (US9) | Thư viện đọc/ghi OOXML (đề xuất Apache POI `poi-ooxml`; version pin sau kiểm tra tương thích với Java 21/Boot 3.4.1 và tiền lệ T004, chưa thêm vào pom từ tác vụ tài liệu). Đọc bằng event/streaming API với giới hạn zip-bomb (tỉ lệ nén, kích thước giải nén, số entry) và tắt XXE/external entity; không thực thi công thức/liên kết ngoài. Đặt sau ranh giới `ProductImportParser`/`ImportTemplateWriter` trong `service/importing/` (thay được bằng test double). |

## Constitution Check

Đánh giá trước nghiên cứu và sau thiết kế. PASS DESIGN không phải phê duyệt production.

| Principle/gate | Before research | After design | Evidence/condition |
| --- | --- | --- | --- |
| I. Module ownership | PASS | PASS DESIGN | productmanagement sở hữu entity/repo/use case; chỉ facade/SPI ra ngoài; không nghiệp vụ trong common. |
| I. Acyclic dependencies | RISK | PASS DESIGN / INTEGRATION OPEN | FE-08 implements productmanagement SPI; productmanagement implements `PromotionTargetPort` của FE-06 (chỉ dùng `loyaltypromotion::facade`, không có chiều ngược lại); lock contract tại [interfaces](contracts/interfaces.md); adapter chưa có. |
| I. JPA/migrations | PASS | PASS DESIGN | Khóa JPA, append-only migration, không raw SQL application, không sửa migration đã áp dụng (hiện chưa có migration nào). |
| II. Identity/authority | OPEN | FE-02 DECISION CLOSED / IDENTITY DEPENDENCY OPEN | Roles, dùng chung xác thực theo 001 đã chốt. Phương thức MFA/recovery chung còn cần thiết kế tại identity/FE-01; facade/session/quyền cần triển khai. Không suy quyền từ Member cookie/ROLE_MEMBER/OWNER. |
| II. Sensitive data | PASS | POLICY CONFIRMED / IMPLEMENTATION PENDING | Retention ảnh 24h/30 ngày có reference guard, business audit/command result 5 năm đã chốt; cleanup/audit cần triển khai và kiểm chứng. |
| III. Contract/web | PASS | PASS DESIGN | OpenAPI 3.0.3, typed errors/DTO, bearer cho mọi REST, frontend React tách riêng; backend không render UI. |
| IV. Evidence | PASS FOR DOCS | PASS FOR DOCS | Static checks cho tài liệu; runtime/migration/security/MySQL evidence chỉ có sau implementation riêng. |
| V. Explicit decisions | OPEN | FE-02 QUESTIONS ANSWERED / MFA DEPENDENCY OPEN | Hướng A G-01, Cloudinary G-03, retention G-04, roles và A-001/A-002 đã chốt. Phương thức MFA chung chưa được chọn ở FE-01; không tự chọn hoặc coi dependency đó đã hoàn tất. |

**Gate outcome**: Người dùng đã chốt dùng chung cơ chế xác thực theo 001; MFA/recovery thuộc identity/FE-01, chưa chọn phương thức cụ thể. G-01 đóng về hướng tích hợp/phiên FE-02; dependency thiết kế MFA chung còn mở trước bật quản trị. Code identity/FE-08/media có thể sắp thứ tự task; nghiệm thu tích hợp cần implementation thật và bằng chứng. Bản plan vẫn Draft, không cấp quyền code/deploy hoặc tự ratify thiết kế MFA chưa có.

## Project Structure

### Documentation (this feature)

```text
ppswbs-spec/features/002-product-management/
  spec.md
  plan.md
  research.md
  data-model.md
  quickstart.md
  blocker-report.md
  checklists/requirements.md
  contracts/interfaces.md
  contracts/product-management.openapi.json
```

Không tạo tasks.md trong speckit-plan; không sửa checklist reviewer/spec/research tổng để nâng trạng thái phê duyệt.

### Source Code (repository root, planned only)

```text
ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/
  features/productmanagement/
    controller/       public/management API controllers, scoped exception handler
    dto/              request/response DTOs, typed error DTOs
    dao/              JPA/Spring Data repositories
    entity/           product/category/variant/review/proposal/media/audit/receipt
    service/          commands, authorization boundary, state/price/quantity services,
                      media staging/cleanup; service/media/ holds the Cloudinary adapter
                      for MediaStoragePort (gói phụ: ranh giới ra dịch vụ ngoài, thay được bằng test double)
                      service/importing/ holds ProductImportService (validate/confirm),
                      ProductImportValidator, ProductImportParser, ImportTemplateWriter,
                      ImportErrorReportWriter, ProductImportPayloadCleaner (US9)
    facade/           CatalogueSalesFacade, AvailabilityGuardPort, immutable records
  configuration/      route-scoped catalogue API security/CORS wiring only
  members/facade/     dependency: identity-owned capability/session contract
  readyringsales/      dependency: adapter for catalogue availability SPI
  features/loyaltypromotion/facade/  dependency: PromotionTargetPort (FE-06) do productmanagement triển khai
ppswbs_backend/src/main/resources/
  db/migration/        new catalogue migration, number allocated at implementation
ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/
  features/productmanagement/  future contract/domain/MySQL/security/concurrency checks
ppswbs_frontend/        (chưa khởi tạo; vị trí dự kiến)
  src/features/productmanagement/  public/ và management/ screens, REST client, vi/en messages
```

**Structure Decision**: Theo constitution I, backend tổ chức `features/<feature>/{controller,dto,dao,entity,service,facade}`. Feature 002 là module Modulith riêng tên `productmanagement` (Java package hợp lệ; slug tài liệu là `product-management`). Toàn bộ `ppswbs_backend/src`, kể cả shell `catalogue` cũ, đã bị xóa ở commit `c47fefe` (ho-quoc, 2026-10-24); `productmanagement` là module mới tạo từ đầu, không kế thừa code cũ. Plan phải kiểm chứng bằng `ApplicationModules.verify()` rằng `productmanagement` được nhận diện là module riêng, không chỉ gói grouping `features`. Không tạo service mới hoặc làm chung module FE-08. Tên bảng `catalogue_*`, route `/api/v1/catalogue-management/...` và tên class `Catalogue*` giữ nguyên trong tài liệu này (không thuộc yêu cầu đổi gói). Member Profile không được triển khai/sửa từ tác vụ này. Những file trên là vị trí dự kiến, không phải file production đã tạo.

## Phase 0 — Research and decisions

[research.md](research.md) ghi D-01–D-09 và D-02a sau khi đọc FE-01/001. Roles, hướng A, Cloudinary, retention và defaults đã chốt; không còn câu hỏi FE-02 phải xác nhận lại. Phương thức MFA/recovery còn là dependency thiết kế identity/FE-01. JPA locks/business versions, SKU, snapshot review/price, availability SPI, media cleanup guard, audit/receipt và additive migration có thiết kế đề xuất; adapter thật vẫn cần trong thứ tự triển khai.

## Phase 1 — Proposed design

### Commands and transactions

**Shared authentication — confirmed**: Kế thừa thiết kế xác minh Firebase token, revocation và điều hướng thành công `/` từ [001 spec](../001-account-management/spec.md). phiên theo contract FE-01/001; mọi REST API (kể cả gọi từ React) dùng bearer theo 001 FR-001. Contract xác thực cho frontend React do 001 chốt (D-02a). Giữ nguyên luồng Member; productmanagement kiểm tra quyền nội bộ từng action qua facade. Không coi `/member-auth/profile-session` hoặc cookie `__Host-mland_profile` tự cấp quyền quản trị. MFA/recovery do identity/FE-01 thiết kế và cung cấp kết quả đạt yêu cầu đặc quyền; chưa chọn phương thức cụ thể, không viết luồng login/MFA mới trong productmanagement.

1. Resolve verified identity/capability hiện tại bằng facade identity; không trả dữ liệu nội bộ trước authorization. Mọi command có actor, command UUID, expected version và digest của payload chuẩn hóa.
2. Same actor/action/key + same digest trả receipt đã commit sau authorization; khác digest trả 409. Nếu chưa có receipt, lấy khóa theo contract, validate latest state, thay đổi aggregate + audit + receipt trong cùng transaction.
3. Lock order: authorization owner prefix → categories tăng ID → products tăng ID → variants tăng ID → stable FE-08 guard tăng variant ID. Không giữ FE-08 guard rồi gọi productmanagement để lấy product lock. Category đổi hoạt động serialize bằng category lock; publication/delete/hold creation dùng product gate.
4. Quantity edit giữ guard từ lúc lấy tổng giữ hợp lệ đến commit cập nhật; không chỉ đọc sum rồi unlock. New product chưa public có identity mới, chưa thể có hold nên khởi tạo lượng ban đầu không cần giả định mọi existing variant có held=0.
5. Khóa ngắn; không gọi remote media/AI dưới khóa. Deadlock/timeout rollback và trả typed retryable error, không tự replay command mới không có key.
6. Media upload vào staging private trước attach; object sau DB rollback không public. Policy đã chốt: ảnh chưa gắn đủ 24h, ảnh gỡ không còn tham chiếu đủ 30 ngày mới eligible cleanup; ảnh lịch sử giữ theo owning record. Gắn ảnh/retain và cleanup dùng media-reference guard, đặt DELETE_PENDING trước remote delete ngoài transaction; lỗi provider retry idempotent, không xóa ảnh còn tham chiếu.

### Stateless and retained state

Theo constitution V, mỗi tương tác của FE-02 được phân loại như sau. Không dùng Redis, session store, JWT do ứng dụng ký hoặc refresh-token riêng.

| Tương tác | Loại | Owner | Storage và lifecycle | Bảo mật và recovery |
| --- | --- | --- | --- | --- |
| Xác thực mọi REST (kể cả frontend React) | Stateless: bearer được xác minh mỗi request | Identity (FE-01) | Không lưu trong productmanagement. Theo contract xác thực FE-01/001 | Revocation/trạng thái kiểm tra mỗi request; hết phiên/thu hồi trả 401; hành vi đăng nhập lại/recovery do 001 định nghĩa |
| Tìm/lọc/sắp xếp/phân trang công khai | Stateless | productmanagement | Không lưu; tiêu chí nằm trong query, đọc lại điều kiện công khai mỗi lượt | Trang cũ không cấp quyền mua; lỗi phụ thuộc khác empty |
| Command receipt (chống gửi lặp) | Retained | productmanagement | MySQL, giữ 5 năm theo G-04 | Cùng actor/action/key và cùng digest trả kết quả đã commit; khác digest trả 409 |
| Import Excel: file tải lên | Stateless, không lưu | productmanagement | Đọc trong request, không ghi đĩa/Cloudinary; chỉ giữ SHA-256 và tên file | Quá giới hạn/hỏng bị từ chối; người dùng tải lại |
| Import Excel: batch xem trước | Retained, ngắn hạn + metadata dài hạn | productmanagement | MySQL `catalogue_import_batches`: payload đã chuẩn hóa chỉ khi VALIDATED, hết hạn sau 60 phút rồi xóa payload và errors; metadata/kết quả giữ 5 năm | Xác nhận cần expectedVersion + command key; hết hạn → tải lại; `getProductImport` để kiểm tra sau mất phản hồi |
| Hồ sơ, review, đề xuất giá, publication, xóa mềm | Retained | productmanagement | MySQL, có version, không ghi đè lịch sử | expectedVersion, 409 cho xem latest rồi xác nhận lại |
| Ảnh staging và tham chiếu | Retained, ngắn hạn | productmanagement | Staging private trên Cloudinary; chưa gắn 24 giờ, gỡ không còn tham chiếu 30 ngày mới được dọn | Reference guard, DELETE_PENDING, retry cùng key |
| Lượng bán và hold | Retained, tách owner | productmanagement giữ lượng bán; FE-08 giữ hold | MySQL, khóa theo thứ tự đã chốt | Guard xuyên suốt compare/save/audit, không cancel hold để giảm lượng |
| Trạng thái màn hình frontend (bộ lọc, form chưa gửi) | Client-side, tạm thời | Frontend | Không là nguồn sự thật nghiệp vụ; nơi lưu do kế hoạch frontend chốt | Mất khi tải lại; không lưu token hay dữ liệu nội bộ ngoài thiết kế |

Trạng thái xác thực và trạng thái nghiệp vụ được tách riêng: cookie hay cơ sở dữ liệu một mình không xác lập mô hình xác thực.

### Excel import flow (US9)

1. **Template**: `ImportTemplateWriter` sinh `.xlsx` (sheet dữ liệu, hướng dẫn, danh mục hoạt động) theo từng request, không lưu file; chỉ cần `product.create`.
2. **Validate** (`POST product-imports`): authorize → kiểm tra size/MIME/chữ ký/macro trước khi giải nén → `ProductImportParser` đọc sheet đầu, đúng 7 cột, ≤ 500 dòng, loại ô công thức/ô gộp/hyperlink → `ProductImportValidator` áp dụng `CatalogueValues` (cùng quy tắc FR-004/005), nhóm theo Mã nhóm, tra category theo name key đang hoạt động, thu thập toàn bộ lỗi (không cắt bớt; tối đa 4.000 phần tử) → ghi batch (VALIDATED với payload chuẩn hóa hoặc REJECTED không payload) + audit + receipt trong một transaction. Không gọi Cloudinary/remote dưới khóa; không cấp product id/SKU.
3. **Confirm** (`POST product-imports/{batchId}/confirmations`): authorize → receipt replay → khóa batch (optimistic version) → kiểm tra chưa hết hạn → khóa category theo ID tăng dần, tái kiểm tra category active và quy tắc FR-004/005 trên payload → gọi lõi `CreateProductService.createDraft` (không receipt/transaction riêng; receipt duy nhất là của lệnh xác nhận) cho từng nhóm trong **một** transaction (tạo product, variants/SKU, review DRAFT, proposal DRAFT, audit từng sản phẩm với nguồn IMPORT + audit batch + receipt) → batch IMPORTED. Bất kỳ lỗi (kể cả audit) rollback toàn bộ; lỗi tái kiểm tra ghi batch REJECTED ở transaction riêng và trả 409 `IMPORT_PREVIEW_STALE`.
4. **Idempotency**: validate dùng `Idempotency-Key` với digest = SHA-256 file; confirm dùng key + batchId + expectedVersion. Mất phản hồi: `getProductImport`.
5. **Giới hạn đồng thời**: không cần khóa sản phẩm (chỉ tạo mới); hai import đồng thời không tranh chấp ngoài category lock; không có unique theo tên sản phẩm.
6. **Dọn dẹp**: `ProductImportPayloadCleaner` (job nền theo lịch) xóa `parsed_payload` và `errors` của batch quá hạn, đặt EXPIRED (VALIDATED); không xóa metadata/audit/receipt.
7. **Bảo mật**: bearer + capability `product.create` ở mọi bước; batch chỉ thuộc actor tạo; không log nội dung file/giá trị ô, chỉ số dòng/mã lỗi; báo cáo lỗi xuất file vô hiệu hóa ô bắt đầu bằng `=`, `+`, `-`, `@`; Admin không dùng.

### Lifecycle and UI

- Hồ sơ mới: Nháp → Chờ duyệt → Đã duyệt/Bị từ chối; sửa content/ảnh/giá trước lần đầu public làm version duyệt cũ mất hiệu lực và hồ sơ trở về Nháp (revision mới ở DRAFT, phải gửi lại mới sang Chờ duyệt). Review và approved variant prices commit chung cho lần duyệt mới.
- Price: Nháp → Chờ duyệt → Đã duyệt/Bị từ chối; revision mới supersedes bản cũ, chỉ một pending/size. Áp dụng là receipt riêng, kiểm tra approved revision và baseAppliedSequence. Không overwrite quyết định lịch sử.
- Product: unpublished/published và deletedAt riêng; firstPublishedAt không bị xóa khi ẩn. Sau firstPublishedAt, sửa content hợp lệ không cần duyệt lại. Product đã xóa không được restore qua command thường.
- Frontend React + Vite gọi REST theo [interfaces](contracts/interfaces.md): field errors gắn đúng trường và giữ dữ liệu đã nhập, 409 cho xem latest rồi gửi lại, điều hướng client sau thành công; public empty khác dependency error. Không autosubmit; mọi màn hình có loading/empty/validation/success/failure/recovery.

### Data, API and module contracts

Xem [data-model](data-model.md), [OpenAPI](contracts/product-management.openapi.json), [web/facade/security contracts](contracts/interfaces.md). ID/amount/quantity truyền decimal string, ngày UTC ISO-8601; strict DTO không nhận extra fields/size mutation/role/approved amount từ client ngoài action được phép.

### Migration, rollout and recovery

- Kiểm tra lại migration directory trước cấp số; không dựa số feature để chọn version. Không sửa migration đã áp dụng của feature khác và không backfill role/provider từ phỏng đoán.
- Apply schema mới trên MySQL trống và bản nâng cấp có member/workshop/price/hold fixture; verify FK, unique name/SKU, pending proposal và rollback audit.
- Enable route sau khi dependency identity đã hoàn thiện thiết kế MFA chung và triển khai/kiểm chứng auth/session/capability, các dependency cần cho route đã sẵn sàng và spec/plan/tasks review + speckit-analyze. Dùng chung xác thực/Cloudinary/retention đã chốt không hỏi lại. Không mở quyền qua global fallback; default deny khi boundary bắt buộc không có.
- Có thể disable riêng routes productmanagement để recovery; giữ additive tables/history. Không down-migrate/drop hoặc hard-delete sản phẩm, không vô hiệu transaction đã chấp nhận. Tác vụ không triển khai production rollout.

## Verification strategy and traceability

| FR group | Design | Planned evidence |
| --- | --- | --- |
| FR-001–FR-003 | Shared identity per 001, capability boundary, management views | API denial, expiry/revocation, no internal authority from Member cookie, privileged-auth gate, Manager self-approval, existing-route regression. |
| FR-004–FR-008 | Exact data, fixed variants, media metadata | Validation boundaries, SKU >6 digits, upload failures, no partial creation. |
| FR-039–FR-045 | Template/validate/confirm flow, retained batch, shared `CreateProductService`, streaming OOXML parser | Parser boundary tests (header, ô công thức, số dạng văn bản, 500/501 dòng, zip-bomb), all-or-nothing, stale preview, expiry, replay, denied actors (gồm Admin), SKU/giá/số lượng bằng với tạo tay, audit per product + batch. |
| FR-009–FR-018 | Immutable review/price and separate publication | Invalidated revisions, one pending/size, exact approved apply, stale application, sold-out publish. |
| FR-019–FR-025 | Guarded quantities, product gate, soft delete | Real-source hold races, delete/hold ordering, immutable old transaction snapshots. |
| FR-026–FR-030 | Receipt/audit, locks and normalized category key | Lost response replay, audit rollback, concurrent duplicate category/activation. |
| FR-031–FR-038 | Public predicates, two-step paging, React screens over REST | Same-variant price/buyability filter, hidden price isolation, empty/error/media restoration, UI journeys over REST. |

[quickstart.md](quickstart.md) ghi lệnh, điều kiện chạy và kết quả mong đợi. Chưa chạy runtime tests vì chưa có implementation; không đánh dấu contract/migration/security/FE-08 integration là pass bằng fixture hoặc static checks.

## Complexity Tracking

Không có constitution exception đề xuất. Import Excel thêm một bảng retained ngắn hạn (`catalogue_import_batches`) và một dependency thư viện OOXML; cả hai cần cho xem-trước-rồi-xác-nhận có kiểm tra lại và idempotency (FR-041/042/044), không thay bằng queue, bộ nhớ phiên hay lưu file gốc. SPI availability là dependency inversion để không tạo cycle và giữ atomic check/save; receipt và business version cần cho FR-011/015/026/028, không thay bằng cache hoặc queue. Không thêm distributed transaction, search engine, queue hoặc lifecycle hold thứ hai.

## Documentation verification performed

Ngày 2026-10-24, sau chốt hướng A và Cloudinary: đã kiểm tra tĩnh 8 tài liệu Markdown của feature (gồm spec/checklist hiện có), 43 liên kết nội bộ tồn tại và 5 liên kết nguồn ngoài được dẫn chiếu (số liệu trước bổ sung US9 ngày 2026-10-24; hiện 45 FR, 32 operations, 47 schemas), thứ tự 38 FR và ánh xạ đủ mọi FR trong plan. OpenAPI JSON parse được, có 23 paths/27 operations/43 schemas, local reference/operationId hợp lệ, management operation có security và mutation có idempotency key. Có 7 câu trả lời đã ghi nhận (2 ngày 2026-10-23, 5 ngày 2026-10-24), gồm xác thực dùng chung theo 001; không coi phương thức MFA cụ thể đã được chọn. 16 marker checklist giữ nguyên. `git diff --check` không có lỗi whitespace; có thông báo chuẩn hóa LF/CRLF của Git cho research tổng, không phải lỗi nội dung. Không có after_plan hook được cấu hình.

Giới hạn kiểm chứng: đây là structural validator tự kiểm tra tài liệu, chưa chạy external full OpenAPI validator. Không chạy ứng dụng, test catalogue, MySQL migration/concurrency hoặc adapter thật vì chưa có implementation/prerequisites; không dùng static pass làm evidence runtime. Không tạo tasks.md hoặc sửa production/test files.

## Handoff and open gates

Xem [blocker-report.md](blocker-report.md): các câu hỏi FE-02 đã trả lời; G-01 còn dependency thiết kế MFA chung và triển khai identity, G-02 còn tích hợp FE-08, adapter Cloudinary/cleanup cần triển khai. Không hỏi lại hướng A/provider hoặc giả định các adapter đã có. Sau review thiết kế mới tới speckit-tasks/speckit-analyze và yêu cầu implementation riêng; quản trị chỉ enable khi dependency đặc quyền đầy đủ.
