# Validation Report: Product Management

**Date created**: 2026-10-26
**Date updated**: 2026-10-27 (sau merge develop, T002/T007, REST API, T051, T073, T083, các task viết test; facade FE-08 phía 002)
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Status**: Đang cập nhật — kết quả tạm thời, **chưa phải nghiệm thu**. Nguồn giữ hàng FE-08 và frontend React chưa có, nên các V-ID phụ thuộc chúng là NOT RUN hoặc BLOCKED. Không mục nào được ghi PASS chỉ nhờ fixture hoặc prototype; chỗ nào dùng stub (xác minh Firebase token, provider ảnh, nguồn giữ hàng) đều ghi rõ.
**References**: [quickstart](quickstart.md), [tasks](tasks.md), [blocker report](blocker-report.md), [spec](spec.md).

## Môi trường

| Mục | Giá trị |
| --- | --- |
| Revision | nhánh `khuong` ở `2774c1e` (= `origin/develop` ngày 2026-10-29) + sửa test theo số migration mới |
| JDK | 21.0.12.1+1-LTS |
| Database | MySQL 8.4.13, database trống `ppswbs_pm_sales_*` cho mỗi lần chạy rồi xóa; T081 dùng database tạm `ppswbs_pm_fresh_*` rồi tự xóa. `ppswbs_test` cũ (đã áp `V9__product_management`) cần tạo lại |
| Migration | V4 (004), V5, V6 (001), V7, V8 (003.2), V9 (003.1), V10–V12 (006), V13, V14 (008), **V15 (002, đổi từ V9 ở `e143b52`)**, V16–V21 (003) |
| Identity | facade 001 thật (`IdentityGateFilter`, `ActorContext`); trong test chỉ stub bước xác minh Firebase token và tra actor |
| Cloudinary | tài khoản test thật qua `CLOUDINARY_*` (biến môi trường), thư mục `ppswbs-it/<lần chạy>` tự xóa |
| Chưa có | adapter nguồn giữ hàng FE-08 (T003/T019), frontend React + Vite (T088) |

## Lệnh và kết quả

```powershell
Set-Location D:/mland-operations-hub/mland-operations-hub/ppswbs_backend
.\mvnw.cmd clean test
```

Kết quả ngày 2026-10-29 trên build thật (develop `2774c1e` biên dịch sạch, không loại file nào):

```powershell
.\mvnw.cmd -o test "-Dtest=edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.**.*,edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.**.*,ModulithArchitectureTests"
.\mvnw.cmd -o test '-Dtest=**/features/productmanagement/*/**/*,**/features/loyaltypromotion/*/**/*'
```

Lệnh thứ hai chạy các lớp trong package con (`controller`, `service`, `service/importing`, `service/media`) mà mẫu của lệnh đầu bỏ sót. **002: 308 test đều pass**; 006: 226 (1 bỏ qua, BE-DESIGN-0012); `ModulithArchitectureTests`: 5. Flyway áp 18 migration V4–V21 lên database trống.

Kết quả cũ ngày 2026-10-27: **481 test**, trong đó **295 test của productmanagement đều pass**. Ngoài 002 có 3 lỗi, không do 002:
- `AccountManagementSecurityIT.whenCorsPreflight_thenReturnsCorsHeaders` (001): đã xác nhận cũng hỏng trên bản `origin/develop` sạch.
- `WorkshopOperationsMySQLIT.shouldReturnInProgressOnConcurrentDuplicate` và `shouldHandleConcurrentInitialClaimRace` (003.2): hết thời gian chờ 5 giây khi chạy cả bộ; chạy riêng 2 lần đều 9/9 pass. Test phụ thuộc thời gian, thuộc owner 003.2.

| Nhóm test của 002 | Lớp |
| --- | --- |
| Đơn vị | `CatalogueValuesTests`, `CatalogueDomainTests`, `CatalogueEntityRulesTests`, `CatalogueCommandExecutorTests`, `CatalogueLockCoordinatorTests`, `CatalogueExceptionHandlerTests`, `CatalogueImportTests`, `ExcelLibraryConfigurationTests`, `CatalogueMediaStagingTests`, `CloudinaryMediaStorageAdapterTests`, `CreateProductServiceTests`, `ManagementProductQueryServiceTests` |
| MySQL, tầng service | `CatalogueMySqlEnvironmentIT`, `CatalogueMySqlSchemaIT`, `CatalogueMySqlIT`, `CatalogueReviewIT`, `CataloguePublicationIT`, `CataloguePriceProposalIT`, `CataloguePriceIT`, `CatalogueSaleStatusAndDeleteIT`, `CatalogueLifecycleIT`, `CatalogueContentEditIT`, `CatalogueMediaIT`, `CatalogueMediaCleanupIT`, `CatalogueMediaAvailabilityIT`, `CatalogueCategoryIT`, `CatalogueImportBatchIT`, `CataloguePublicIT`, `LoyaltyProductTargetAdapterIT`, `CatalogueAuditFailureIT` |
| Đồng thời thật | `CatalogueConcurrencyIT`, `CatalogueCategoryConcurrencyIT` |
| Facade cho FE-08 (nguồn giữ hàng giả chỉ trong test) | `CatalogueSalesIT` |
| HTTP + security thật | `CatalogueApiIT`, `CatalogueImportApiIT`, `CatalogueContractIT` |
| Provider thật | `CatalogueCloudinaryIT` |
| Ranh giới module | `CatalogueDependenciesIT`, `ModulithArchitectureTests` |

## Ma trận V-01–V-15

Ký hiệu: **PASS** = đạt qua REST với security thật trên MySQL; **PASS (service)** = đạt ở tầng service; **PARTIAL** = một phần, phần còn lại ghi rõ; **BLOCKED** = thiếu dependency; **NOT RUN** = chưa có gì để chạy.

| ID | Trạng thái | Đã kiểm chứng (bằng chứng) | Chưa kiểm chứng / lý do |
| --- | --- | --- | --- |
| V-01 | PASS (backend) | Thiếu bearer 401; token bị thu hồi 401 `TOKEN_REVOKED`; cookie không thay bearer; Admin bị 403 ở **mọi** operation quản trị; Member, `PENDING_SETUP` 403; Staff không duyệt được; Manager tự tạo/tự duyệt (`CatalogueApiIT`, `CatalogueContractIT`, `CatalogueImportApiIT`, `CatalogueReviewIT`). | Phía frontend React (NOT RUN). Xác minh Firebase token thật chỉ có ở test của 001. |
| V-02 | PASS | Tạo nguyên khối qua REST, SKU đúng, size cố định, field lạ `UNKNOWN_FIELD`, thiếu `Idempotency-Key` 400, nháp không ảnh vẫn lưu (`CatalogueApiIT`, `CreateProductServiceTests`, `CatalogueMySqlSchemaIT`). | — |
| V-03 | PASS | Gửi/duyệt nguyên hồ sơ qua REST, một đề xuất chờ duyệt mỗi size kể cả gửi đồng thời, sửa trước khi duyệt tạo revision mới, snapshot không trôi, quyết định cũ bị từ chối, lý do bắt buộc, lỗi audit không duyệt một phần (`CatalogueReviewIT`, `CatalogueConcurrencyIT`, `CatalogueAuditFailureIT`, `CatalogueApiIT`). | "Cancel" là thao tác giao diện, không có lệnh backend. |
| V-04 | PASS | Duyệt chưa áp dụng thì không công khai; áp dụng đúng giá; điều khoản đổi `PRICE_TERMS_CHANGED`; revision bị thay thế `PRICE_NOT_APPROVED`; quyết định cũ không ghi đè; size hết hàng vẫn công khai; mất phản hồi gửi lại cùng key (`CataloguePublicationIT`, `CataloguePriceIT`, `CatalogueConcurrencyIT`, `CatalogueApiIT`). | — |
| V-05 | PASS | Sửa thẳng sau lần công khai đầu và khi đang ẩn; không gỡ được ảnh cuối của sản phẩm công khai; 10/11 ảnh, alt 200/201, vị trí; ảnh của người khác bị từ chối; upload thật lên Cloudinary `authenticated`; gửi lại theo checksum; lỗi provider rồi thử lại (`CatalogueContentEditIT`, `CatalogueMediaIT`, `CatalogueCloudinaryIT`, `CatalogueApiIT`). | — |
| V-06 | BLOCKED | Sản phẩm chưa từng công khai: số giữ = 0 đã chứng minh; đã từng công khai mà không có FE-08 thì 503 và không đổi gì (`CatalogueContentEditIT`, `CatalogueApiIT`). | Va chạm với giữ hàng/settlement cần adapter FE-08 thật. Phía 002 đã có `AvailabilityGuardPort` + `CatalogueSalesFacade`; số lượng đặt dưới guard và trừ bán một lần đã kiểm bằng nguồn giả chỉ trong test (`CatalogueSalesIT`), không phải bằng chứng tích hợp. |
| V-07 | PARTIAL | Ngừng/mở bán giữ số lượng và giá; ẩn rồi công khai lại kiểm tra lại điều kiện, giữ `firstPublishedAt`; xóa từ mọi trạng thái; key cũ sau khi xóa không làm sống lại; xóa và công khai đồng thời (`CatalogueSaleStatusAndDeleteIT`, `CatalogueLifecycleIT`, `CatalogueConcurrencyIT`). | Va chạm với giữ hàng mới, thanh toán cũ (FE-08). |
| V-08 | PASS (trừ phần giữ hàng) | Tên duy nhất kể cả danh mục ngừng, NFC/NFD, biên 200/201 và 5.000/5.001, tạo trùng đồng thời, tác động phân trang, ngừng chặn công khai không đổi cờ, kích hoạt lại không làm sống lại sản phẩm ẩn/xóa, đổi danh mục ngược chiều không deadlock, quyền qua REST (`CatalogueCategoryIT`, `CatalogueCategoryConcurrencyIT`, `CatalogueImportApiIT`). | Va chạm với giữ hàng mới (FE-08). |
| V-09 | PARTIAL | Lọc giá + "có thể mua" trên cùng size, nhãn size ngừng bán, sắp xếp có định danh phụ, `%`/`_` là ký tự thường, phân trang 20 + 1 (`CataloguePublicIT`, dùng `VariantHoldReader` giả chỉ trong test). | Số giữ thật từ FE-08. |
| V-10 | PARTIAL | Ảnh bị mất ở provider → sản phẩm rời catalogue công khai mà cờ công khai không đổi, đọc lại được thì phục hồi, lỗi provider không bị coi là "mất"; thiếu FE-08 trả 503 chứ không giả định 0; ảnh công khai chỉ phục vụ khi sản phẩm còn công khai (`CatalogueMediaAvailabilityIT`, `CataloguePublicIT`, `CatalogueImportApiIT`, `CatalogueContentEditIT`). | Hiển thị phân biệt lỗi phụ thuộc/catalogue rỗng ở frontend. |
| V-11 | PASS | Cùng key cùng dữ liệu trả kết quả cũ, khác dữ liệu 409, gửi đồng thời chỉ một receipt, tra receipt chỉ của chính actor, mất quyền thì bị từ chối trước khi trả kết quả (`CatalogueCommandExecutorTests`, `CatalogueConcurrencyIT`, `CatalogueApiIT`). | — |
| V-12 | PASS | Cài mới và nâng cấp migration, 13 khóa ngoại RESTRICT, 30 CHECK, unique, InnoDB/utf8mb4; lỗi audit rollback; đồng thời thật (`CatalogueMySqlIT`, `CatalogueMySqlSchemaIT`, `CatalogueAuditFailureIT`, `Catalogue*ConcurrencyIT`). Regression Member/workshop chạy trong cùng bộ. | Không có bảng workshop cũ cần bảo toàn trong migration hiện có. |
| V-13 | NOT RUN | — | `ppswbs_frontend/` chưa khởi tạo. Prototype `prototypes/002-product-management/` không phải bằng chứng. |
| V-14 | PASS (trừ hạn giữ 5 năm) | Mốc 24 giờ/30 ngày, tham chiếu chặn dọn, DELETE_PENDING trước khi gọi provider, thử lại; Cloudinary thật: gửi lại không trùng/không ghi đè, URL không ký bị chặn, log không lộ bí mật, dọn xóa đúng object (`CatalogueMediaCleanupIT`, `CatalogueMediaIT`, `CatalogueCloudinaryIT`). | Hạn giữ audit/receipt 5 năm chưa có job để kiểm tra. |
| V-15 | PASS | Mẫu, kiểm tra, xem trước, xác nhận qua REST; mọi loại lỗi dòng; `.xls` 415; 500/501 dòng; lặp/mất phản hồi; danh mục ngừng giữa hai bước; hết hạn; Guest/Member/Admin bị từ chối; batch người khác 404; hai lần import đồng thời; báo cáo lỗi vô hiệu hóa `=`/`+`/`-`/`@` (`CatalogueImportTests`, `CatalogueImportBatchIT`, `CatalogueImportApiIT`). | — |

## Lỗi tìm thấy khi kiểm chứng (đã sửa)

- `lockProductWithTarget` lỗi khi không đổi danh mục; record kết quả JPQL cần `Boolean`.
- Transaction lệnh ở REPEATABLE READ đọc snapshot cũ sau khi chờ khóa → chuyển READ COMMITTED.
- `CatalogueStrictJsonConverter` đăng ký dạng bean làm Spring Boot bỏ bộ chuyển Jackson mặc định, mọi feature ghi ngày giờ dạng số → đăng ký qua `WebMvcConfigurer`.
- `AccountExceptionHandler` của 001 bắt lỗi của mọi feature (500) → handler của 002 đặt ưu tiên cao nhất cho controller của 002; đã báo owner 001.
