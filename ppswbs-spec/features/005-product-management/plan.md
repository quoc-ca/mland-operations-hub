# Implementation Plan: Product Management

**Feature context**: `005-product-management` | **Date**: 2026-10-24 | **Spec**: [spec.md](spec.md)  
**Git branch**: `khuong` được giữ nguyên; không tạo/switch nhánh. `005-product-management` là feature context của SpecKit, không phải git branch hiện tại.  
**Status**: Draft design — hoàn thành bộ tài liệu đề xuất; chưa đạt gate để triển khai toàn feature.  
**Input**: FE-02 có hai quyết định clarify ngày 2026-10-23 và các câu trả lời bổ sung ngày 2026-10-24 về roles, Cloudinary, retention, defaults và hướng A: dùng chung cơ chế xác thực theo FE-01/003, phiên nội bộ cố định 8 giờ, kiểm tra thêm quyền Staff/Manager; MFA/recovery do identity/FE-01 sở hữu. Chỉ hoàn thiện tài liệu, không code/test/migration/deploy.

## Summary

Triển khai nhẫn bán sẵn trong module catalogue: tạo sản phẩm với size cố định 5–29 và SKU tự sinh, gửi Manager duyệt, áp dụng đúng giá đã duyệt rồi chủ động công khai. Sửa nội dung trực tiếp sau công khai lần đầu; mọi đổi giá vẫn phải duyệt. Có quản lí ảnh, lượng bán chung, ngừng bán từng size, ẩn/công khai, xóa mềm không khôi phục, danh mục và public search/filter/sort theo spec.

Giữ module đóng; JPA/Flyway và MySQL 8.4, web form server-rendered cùng API versioned. Catalogue sở hữu quantity, FE-08 sở hữu hold; availability SPI giữ compile dependency readyringsales → catalogue và đồng bộ khóa. Không dùng fallback zero-hold, ROLE_MEMBER, provider giả hoặc thiết kế 003 chưa triển khai để lấp dependency.

## Technical Context

| Item | Selected/proposed design |
| --- | --- |
| Language/Version | Java 21 theo pom hiện hành. |
| Primary dependencies | Spring Boot 3.4.1, Modulith 1.3.1, Firebase Admin 9.11.0, JPA/Flyway, Security, Validation, Thymeleaf theo pom; không nâng version. |
| Storage | MySQL 8.4/InnoDB để kiểm chứng; bảng catalogue mới, DECIMAL(19,0), UTC timestamps, append-only migration. Cloudinary đã được chọn; adapter đề xuất signed server-side upload, authenticated assets và stream cùng origin. SDK version cần pin sau kiểm tra tương thích; không nâng dependency hiện có. |
| Testing | JUnit/Spring Boot Test/MockMvc hiện có; MySQL thật cho migration/locking/collation, H2 bổ trợ. Fixture dependency không phải integration evidence. |
| Target platform | Server-rendered web + REST; không chọn production host hoặc deployment topology. |
| Project type | Spring Modulith modular monolith hiện có, thêm nội dung vào module catalogue đang là shell. |
| Performance goals | Chưa baselined; không tự đặt throughput/latency/SLA. Thiết kế batch availability, phân trang và query theo product IDs; kiểm chứng tính đúng trước benchmark. |
| Constraints | Không khắc; size không thêm/đổi sau lưu; Manager tự duyệt được; giá duyệt/áp dụng/công khai riêng; no-JS core form; giữ giá/lịch sử giao dịch; không sửa role mapping ngầm. |
| Scale/scope | 1–25 variants/product, tối đa 10 ảnh 5.000.000 bytes; 20 product/page và q tối đa 200 đã chốt. Tổng catalogue/concurrency production chưa xác định. |

## Constitution Check

Đánh giá trước nghiên cứu và sau thiết kế. PASS DESIGN không phải phê duyệt production.

| Principle/gate | Before research | After design | Evidence/condition |
| --- | --- | --- | --- |
| I. Module ownership | PASS | PASS DESIGN | catalogue sở hữu entity/repo/use case; chỉ facade/SPI ra ngoài; không nghiệp vụ trong common. |
| I. Acyclic dependencies | RISK | PASS DESIGN / INTEGRATION OPEN | FE-08 implements catalogue SPI; lock contract tại [interfaces](contracts/interfaces.md); adapter chưa có. |
| I. JPA/migrations | PASS | PASS DESIGN | Khóa JPA, append-only migration, không raw SQL application, không thay V1–V3. |
| II. Identity/authority | OPEN | FE-02 DECISION CLOSED / IDENTITY DEPENDENCY OPEN | Roles, dùng chung xác thực và phiên nội bộ 8 giờ đã chốt. Phương thức MFA/recovery chung còn cần thiết kế tại identity/FE-01; facade/session/quyền cần triển khai. Không suy quyền từ Member cookie/ROLE_MEMBER/OWNER. |
| II. Sensitive data | PASS | POLICY CONFIRMED / IMPLEMENTATION PENDING | Retention ảnh 24h/30 ngày có reference guard, business audit/command result 5 năm đã chốt; cleanup/audit cần triển khai và kiểm chứng. |
| III. Contract/web | PASS | PASS DESIGN | OpenAPI 3.0.3, typed errors/DTO, CSRF cookie web, không JS-only form. |
| IV. Evidence | PASS FOR DOCS | PASS FOR DOCS | Static checks cho tài liệu; runtime/migration/security/MySQL evidence chỉ có sau implementation riêng. |
| V. Explicit decisions | OPEN | FE-02 QUESTIONS ANSWERED / MFA DEPENDENCY OPEN | Hướng A/phiên 8 giờ G-01, Cloudinary G-03, retention G-04, roles và A-001/A-002 đã chốt. Phương thức MFA chung chưa được chọn ở FE-01; không tự chọn hoặc coi dependency đó đã hoàn tất. |

**Gate outcome**: Người dùng đã chốt dùng chung cơ chế xác thực theo 003 và phiên nội bộ 8 giờ; MFA/recovery thuộc identity/FE-01, chưa chọn phương thức cụ thể. G-01 đóng về hướng tích hợp/phiên FE-02; dependency thiết kế MFA chung còn mở trước bật quản trị. Code identity/FE-08/media có thể sắp thứ tự task; nghiệm thu tích hợp cần implementation thật và bằng chứng. Bản plan vẫn Draft, không cấp quyền code/deploy hoặc tự ratify thiết kế MFA chưa có.

## Project Structure

### Documentation (this feature)

```text
ppswbs-spec/features/005-product-management/
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
  catalogue/
    facade/           CatalogueSalesFacade, AvailabilityGuardPort, immutable records
    web/              public/management DTOs, API/view controllers, scoped errors
    application/      commands, authorization boundary, state/price/quantity services
    domain/           product/category/variant/review/proposal/media/audit/receipt
    infrastructure/   JPA repositories, Cloudinary adapter for MediaStoragePort
  configuration/      route-scoped catalogue API/web security wiring only
  members/facade/     dependency: identity-owned capability/session contract
  readyringsales/      dependency: adapter for catalogue availability SPI
ppswbs_backend/src/main/resources/
  db/migration/        new catalogue migration, number allocated at implementation
  templates/catalogue/ public and management server-rendered forms
  i18n/                Vietnamese/English catalogue messages
ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/
  catalogue/           future contract/domain/MySQL/security/concurrency checks
```

**Structure Decision**: Tái sử dụng module catalogue hiện có, không tạo service mới hoặc làm chung module FE-08. Member Profile không được triển khai/sửa từ tác vụ này. Những file trên là vị trí dự kiến, không phải file production đã tạo.

## Phase 0 — Research and decisions

[research.md](research.md) ghi D-01–D-09 và D-02a sau khi đọc FE-01/003. Roles, hướng A/phiên nội bộ 8 giờ, Cloudinary, retention và defaults đã chốt; không còn câu hỏi FE-02 phải xác nhận lại. Phương thức MFA/recovery còn là dependency thiết kế identity/FE-01. JPA locks/business versions, SKU, snapshot review/price, availability SPI, media cleanup guard, audit/receipt và additive migration có thiết kế đề xuất; adapter thật vẫn cần trong thứ tự triển khai.

## Phase 1 — Proposed design

### Commands and transactions

**Shared authentication — confirmed**: Kế thừa thiết kế xác minh Firebase token/cookie, revocation, CSRF và điều hướng thành công `/` từ [003 spec](../003-member-profile/spec.md). Phiên web nội bộ cố định 8 giờ, không gia hạn trượt; điều kiện auth_time khi tạo phiên và cookie flags theo D-02a. Giữ nguyên luồng Member; catalogue kiểm tra quyền nội bộ từng action qua facade. Không coi `/member-auth/profile-session` hoặc cookie `__Host-mland_profile` tự cấp quyền quản trị. MFA/recovery do identity/FE-01 thiết kế và cung cấp kết quả đạt yêu cầu đặc quyền; chưa chọn phương thức cụ thể, không viết luồng login/MFA mới trong catalogue.

1. Resolve verified identity/capability hiện tại bằng facade identity; không trả dữ liệu nội bộ trước authorization. Mọi command có actor, command UUID, expected version và digest của payload chuẩn hóa.
2. Same actor/action/key + same digest trả receipt đã commit sau authorization; khác digest trả 409. Nếu chưa có receipt, lấy khóa theo contract, validate latest state, thay đổi aggregate + audit + receipt trong cùng transaction.
3. Lock order: authorization owner prefix → categories tăng ID → products tăng ID → variants tăng ID → stable FE-08 guard tăng variant ID. Không giữ FE-08 guard rồi gọi catalogue để lấy product lock. Category đổi hoạt động serialize bằng category lock; publication/delete/hold creation dùng product gate.
4. Quantity edit giữ guard từ lúc lấy tổng giữ hợp lệ đến commit cập nhật; không chỉ đọc sum rồi unlock. New product chưa public có identity mới, chưa thể có hold nên khởi tạo lượng ban đầu không cần giả định mọi existing variant có held=0.
5. Khóa ngắn; không gọi remote media/AI dưới khóa. Deadlock/timeout rollback và trả typed retryable error, không tự replay command mới không có key.
6. Media upload vào staging private trước attach; object sau DB rollback không public. Policy đã chốt: ảnh chưa gắn đủ 24h, ảnh gỡ không còn tham chiếu đủ 30 ngày mới eligible cleanup; ảnh lịch sử giữ theo owning record. Gắn ảnh/retain và cleanup dùng media-reference guard, đặt DELETE_PENDING trước remote delete ngoài transaction; lỗi provider retry idempotent, không xóa ảnh còn tham chiếu.

### Lifecycle and UI

- Hồ sơ mới: Nháp → Chờ duyệt → Đã duyệt/Bị từ chối; sửa content/ảnh/giá trước lần đầu public làm version duyệt cũ mất hiệu lực. Review và approved variant prices commit chung cho lần duyệt mới.
- Price: Nháp → Chờ duyệt → Đã duyệt/Bị từ chối; revision mới supersedes bản cũ, chỉ một pending/size. Áp dụng là receipt riêng, kiểm tra approved revision và baseAppliedSequence. Không overwrite quyết định lịch sử.
- Product: unpublished/published và deletedAt riêng; firstPublishedAt không bị xóa khi ẩn. Sau firstPublishedAt, sửa content hợp lệ không cần duyệt lại. Product đã xóa không được restore qua command thường.
- Core forms GET/POST theo [interfaces](contracts/interfaces.md): field errors giữ dữ liệu hợp lệ đã nhập, 409 cho xem latest rồi resubmit, PRG khi thành công; public empty khác dependency error. Không autosubmit, không đòi frontend JS để upload/duyệt/xóa.

### Data, API and module contracts

Xem [data-model](data-model.md), [OpenAPI](contracts/product-management.openapi.json), [web/facade/security contracts](contracts/interfaces.md). ID/amount/quantity truyền decimal string, ngày UTC ISO-8601; strict DTO không nhận extra fields/size mutation/role/approved amount từ client ngoài action được phép.

### Migration, rollout and recovery

- Kiểm tra lại migration directory trước cấp số; không dựa số feature để chọn version. Không chỉnh V1–V3 và không backfill role/provider từ phỏng đoán.
- Apply schema mới trên MySQL trống và bản nâng cấp có member/workshop/price/hold fixture; verify FK, unique name/SKU, pending proposal và rollback audit.
- Enable route sau khi dependency identity đã hoàn thiện thiết kế MFA chung và triển khai/kiểm chứng auth/session/capability, các dependency cần cho route đã sẵn sàng và spec/plan/tasks review + speckit-analyze. Dùng chung xác thực/phiên 8 giờ/Cloudinary/retention đã chốt không hỏi lại. Không mở quyền qua global fallback; default deny khi boundary bắt buộc không có.
- Có thể disable riêng catalogue routes để recovery; giữ additive tables/history. Không down-migrate/drop hoặc hard-delete sản phẩm, không vô hiệu transaction đã chấp nhận. Tác vụ không triển khai production rollout.

## Verification strategy and traceability

| FR group | Design | Planned evidence |
| --- | --- | --- |
| FR-001–FR-003 | Shared identity, fixed eight-hour session, capability boundary, management views | API/web denial, expiry/revocation/CSRF, no internal authority from Member cookie, privileged-auth gate, Manager self-approval, existing-route regression. |
| FR-004–FR-008 | Exact data, fixed variants, media metadata | Validation boundaries, SKU >6 digits, upload failures, no partial creation. |
| FR-009–FR-018 | Immutable review/price and separate publication | Invalidated revisions, one pending/size, exact approved apply, stale application, sold-out publish. |
| FR-019–FR-025 | Guarded quantities, product gate, soft delete | Real-source hold races, delete/hold ordering, immutable old transaction snapshots. |
| FR-026–FR-030 | Receipt/audit, locks and normalized category key | Lost response replay, audit rollback, concurrent duplicate category/activation. |
| FR-031–FR-038 | Public predicates, two-step paging, server forms | Same-variant price/buyability filter, hidden price isolation, empty/error/media restoration, no-JS journeys. |

[quickstart.md](quickstart.md) ghi lệnh, điều kiện chạy và kết quả mong đợi. Chưa chạy runtime tests vì chưa có implementation; không đánh dấu contract/migration/security/FE-08 integration là pass bằng fixture hoặc static checks.

## Complexity Tracking

Không có constitution exception đề xuất. SPI availability là dependency inversion để không tạo cycle và giữ atomic check/save; receipt và business version cần cho FR-011/015/026/028, không thay bằng cache hoặc queue. Không thêm distributed transaction, search engine, queue hoặc lifecycle hold thứ hai.

## Documentation verification performed

Ngày 2026-10-24, sau chốt hướng A và Cloudinary: đã kiểm tra tĩnh 8 tài liệu Markdown của feature (gồm spec/checklist hiện có), 43 liên kết nội bộ tồn tại và 5 liên kết nguồn ngoài được dẫn chiếu, thứ tự 38 FR và ánh xạ đủ mọi FR trong plan. OpenAPI JSON parse được, có 23 paths/27 operations/43 schemas, local reference/operationId hợp lệ, management operation có security và mutation có idempotency key. Có 7 câu trả lời đã ghi nhận (2 ngày 2026-10-23, 5 ngày 2026-10-24), gồm xác thực dùng chung/phiên nội bộ 8 giờ; không coi phương thức MFA cụ thể đã được chọn. 16 marker checklist giữ nguyên. `git diff --check` không có lỗi whitespace; có thông báo chuẩn hóa LF/CRLF của Git cho research tổng, không phải lỗi nội dung. Không có after_plan hook được cấu hình.

Giới hạn kiểm chứng: đây là structural validator tự kiểm tra tài liệu, chưa chạy external full OpenAPI validator. Không chạy ứng dụng, test catalogue, MySQL migration/concurrency hoặc adapter thật vì chưa có implementation/prerequisites; không dùng static pass làm evidence runtime. Không tạo tasks.md hoặc sửa production/test files.

## Handoff and open gates

Xem [blocker-report.md](blocker-report.md): các câu hỏi FE-02 đã trả lời; G-01 còn dependency thiết kế MFA chung và triển khai identity, G-02 còn tích hợp FE-08, adapter Cloudinary/cleanup cần triển khai. Không hỏi lại hướng A/phiên 8 giờ/provider hoặc giả định các adapter đã có. Sau review thiết kế mới tới speckit-tasks/speckit-analyze và yêu cầu implementation riêng; quản trị chỉ enable khi dependency đặc quyền đầy đủ.
