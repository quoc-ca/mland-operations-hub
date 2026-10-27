# Validation Report: Product Management

**Date created**: 2026-10-26
**Date updated**: 2026-10-26 (lần chạy đầu sau tầng service US1–US9, T081; bổ sung test từ khóa %/_, phân trang, import đồng thời; T051 Cloudinary thật)
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Status**: Đang cập nhật — kết quả tạm thời, **chưa phải nghiệm thu**. Controller/REST, phân quyền (T007), nguồn giữ hàng FE-08, Cloudinary thật và frontend chưa có, nên các V-ID phụ thuộc chúng là NOT RUN hoặc BLOCKED. Không mục nào được ghi PASS chỉ nhờ fixture hoặc prototype.
**References**: [quickstart](quickstart.md), [tasks](tasks.md), [blocker report](blocker-report.md), [spec](spec.md).

## Môi trường

| Mục | Giá trị |
| --- | --- |
| Revision | `46c3075` (nhánh `khuong`) + thay đổi chưa commit của feature 002 |
| JDK | 21.0.12.1+1-LTS |
| Database | MySQL 8.4.13, database test riêng `ppswbs_test` (biến môi trường `JDBC_*`); T081 tạo database tạm `ppswbs_pm_fresh_*` rồi tự xóa |
| Cấu hình test | profile `productmanagement-mysql-test` (chỉ lấy `PM_TEST_JDBC_*`/`JDBC_*`, không H2, không giá trị mặc định); `catalogue.scheduling.enabled=false` |
| Cloudinary | Tài khoản test thật qua `CLOUDINARY_CLOUD_NAME`/`CLOUDINARY_API_KEY`/`CLOUDINARY_API_SECRET` (biến môi trường, không ghi giá trị); mọi object test nằm trong `ppswbs-it/<lần chạy>` và bị xóa sau khi chạy |
| Chưa có | Facade xác thực 001 (T002/T007), adapter FE-08 (T003/T019), frontend React + Vite (T088) |

## Lệnh và kết quả

```powershell
Set-Location D:/mland-operations-hub/mland-operations-hub/ppswbs_backend
.\mvnw.cmd -o test
```

Kết quả ngày 2026-10-26 (sau T051): **327 test, 0 failure, 0 error, 0 skipped**, gồm 244 test của productmanagement (có `CatalogueCloudinaryIT` chạy với Cloudinary thật), 6 test của accountmanagement, `ModulithArchitectureTests` và các test của feature 006 vừa được thêm vào repo (migration của 006, nay là V10, V11). Surefire của dự án có chạy các lớp `*IT` (đã thấy trong `target/surefire-reports`), nên lệnh `test` không bỏ sót IT.

| Lớp test | Số test | Phạm vi |
| --- | ---: | --- |
| `CatalogueValuesTests` | 38 | Chuẩn hóa/giới hạn giá trị |
| `CatalogueDomainTests`, `CatalogueEntityRulesTests` | 38 | Luật entity |
| `CatalogueCommandExecutorTests` | 7 | Receipt, digest, replay |
| `CatalogueLockCoordinatorTests` | 3 | Thứ tự khóa |
| `CatalogueExceptionHandlerTests` | 10 | Ánh xạ lỗi, JSON nghiêm ngặt |
| `CatalogueImportTests`, `ExcelLibraryConfigurationTests` | 27 | Đọc/kiểm tra file Excel |
| `CatalogueMediaStagingTests`, `CloudinaryMediaStorageAdapterTests` | 21 | Staging ảnh, adapter (provider giả) |
| `CatalogueCloudinaryIT` | 5 | Cloudinary thật: upload có ký `authenticated`, đọc lại, gửi lại, trùng key khác nội dung, URL không ký, xóa, log không lộ bí mật, upload qua service rồi dọn |
| `CreateProductServiceTests`, `ManagementProductQueryServiceTests` | 15 | Tạo sản phẩm, truy vấn quản trị |
| `CatalogueMySqlEnvironmentIT`, `CatalogueMySqlSchemaIT`, `CatalogueMySqlIT` | 10 | MySQL 8.4, schema, migration mới/nâng cấp |
| `CatalogueReviewIT`, `CataloguePublicationIT`, `CataloguePriceProposalIT` | 20 | Duyệt, áp dụng giá, công khai, đề xuất giá |
| `CatalogueSaleStatusAndDeleteIT`, `CatalogueContentEditIT` | 15 | Ngừng bán, xóa mềm, sửa nội dung/ảnh/số lượng |
| `CatalogueCategoryIT`, `CatalogueImportBatchIT`, `CatalogueMediaCleanupIT`, `CataloguePublicIT` | 26 | Danh mục, lô import, dọn ảnh, catalogue công khai |
| `CatalogueConcurrencyIT`, `CatalogueCategoryConcurrencyIT` | 8 | Transaction đồng thời thật trên MySQL |

## Ma trận V-01–V-15

Ký hiệu: **PASS (service)** = đạt ở tầng service trên MySQL, chưa qua REST; **PARTIAL** = một phần đạt, phần còn lại ghi rõ; **BLOCKED** = thiếu dependency; **NOT RUN** = chưa có gì để chạy.

| ID | Trạng thái | Đã kiểm chứng (bằng chứng) | Chưa kiểm chứng / lý do |
| --- | --- | --- | --- |
| V-01 | BLOCKED | Manager tự tạo và tự duyệt hồ sơ (`CatalogueReviewIT.managerMaySelfApproveWithAdjustedFinalAmounts`). | 401/403/503 theo bearer, token hết hạn/thu hồi, Staff gọi approve, inactive: cần facade 001 và T007. Controller chưa có. |
| V-02 | PARTIAL | Tạo nguyên khối, SKU đúng, size cố định, lỗi trường, bản nháp không ảnh vẫn lưu (`CreateProductServiceTests`, `CatalogueMySqlSchemaIT.createProductAndManagementQueryRunOnRealTables`, `failedCreationLeavesNothingBehind`). | Qua REST và từ chối field lạ ở tầng HTTP (đã có test converter, chưa có controller). |
| V-03 | PARTIAL | Gửi và quyết định nguyên hồ sơ, một đề xuất chờ duyệt mỗi size, sửa trước khi duyệt tạo revision mới, quyết định cũ bị từ chối, từ chối cần lý do (`CatalogueReviewIT`); hai lần gửi đồng thời chỉ một thành công (`CatalogueConcurrencyIT.twoSubmissionsAtTheSameTimeSubmitOnce`). | Lỗi ghi audit làm rollback ở luồng duyệt; Staff bị 403 khi duyệt (T007). |
| V-04 | PASS (service) | Duyệt chưa áp dụng thì không công khai được, áp dụng đúng giá đã duyệt, quyết định cũ không ghi đè giá mới, size hết hàng không chặn công khai (`CataloguePublicationIT`); hai lệnh áp dụng đồng thời chỉ áp dụng một lần (`CatalogueConcurrencyIT`). | Qua REST. |
| V-05 | PARTIAL | Sửa trực tiếp sau lần công khai đầu, sửa trước đó đưa hồ sơ về nháp, đổi thứ tự/gỡ ảnh, không gỡ được ảnh cuối của sản phẩm đang công khai, không gắn ảnh của người khác (`CatalogueContentEditIT`); upload thật qua `CatalogueMediaService` lên Cloudinary ở chế độ `authenticated` (`CatalogueCloudinaryIT`). | Qua REST/multipart (T044). |
| V-06 | BLOCKED | Sản phẩm chưa từng công khai: số giữ = 0 đã chứng minh; đã từng công khai mà không có FE-08 thì trả 503 và không đổi gì (`CatalogueContentEditIT`). | Toàn bộ va chạm với giữ hàng/settlement cần adapter FE-08 thật. |
| V-07 | PARTIAL | Ngừng/mở bán size giữ nguyên số lượng và giá, ẩn rồi công khai lại giữ `firstPublishedAt`, xóa mềm chặn mọi lệnh sau đó (`CatalogueSaleStatusAndDeleteIT`, `CataloguePublicationIT`); xóa và công khai đồng thời không để sản phẩm vừa xóa vừa công khai (`CatalogueConcurrencyIT`). | Va chạm với giữ hàng mới, thanh toán cũ (FE-08). |
| V-08 | PASS (service) | Tên duy nhất kể cả danh mục đã ngừng, tạo trùng đồng thời chỉ một thành công, xem tác động, ngừng danh mục chặn công khai mà không đổi cờ, kích hoạt lại không làm sống lại sản phẩm ẩn/xóa, đổi danh mục ngược chiều không deadlock (`CatalogueCategoryIT`, `CatalogueCategoryConcurrencyIT`). | Va chạm với giữ hàng mới (FE-08); qua REST. |
| V-09 | PARTIAL | Lọc giá và "có thể mua" trên cùng một size, size ngừng bán vẫn có nhãn, sắp xếp tên/giá/mới nhất với định danh phụ, không lộ đề xuất giá, từ khóa chứa `%`/`_` được hiểu là ký tự thường (đã thử bỏ escape thì test hỏng), 21 sản phẩm chia đúng 20 + 1 không trùng và tổng đúng (`CataloguePublicIT`, dùng `VariantHoldReader` giả chỉ khai báo trong test). | Số giữ thật từ FE-08; qua REST. |
| V-10 | PARTIAL | Ảnh không đọc được làm sản phẩm biến khỏi catalogue rồi hiện lại khi khôi phục, cờ công khai không đổi (`CataloguePublicIT`); thiếu FE-08 trả 503 chứ không giả định 0 (`CatalogueContentEditIT`). | Phân biệt lỗi phụ thuộc và catalogue rỗng ở REST/UI; endpoint ảnh công khai (T070/T073). |
| V-11 | PARTIAL | Cùng key + cùng dữ liệu trả kết quả cũ, khác dữ liệu trả `IDEMPOTENCY_KEY_REUSED` (`CatalogueCommandExecutorTests`); cùng key gửi đồng thời chỉ tạo một sản phẩm và một receipt (`CatalogueConcurrencyIT`). | Mất quyền trước khi tra receipt (T007). |
| V-12 | PASS | Cài mới trên database trống: đủ 12 bảng, 13 khóa ngoại RESTRICT, khóa vòng variant → proposal, 30 CHECK, các unique, InnoDB/utf8mb4_unicode_ci; nâng cấp từ V5 giữ nguyên dữ liệu tài khoản; chạy lại không đổi gì và validate được (`CatalogueMySqlIT`). CHECK/unique/rollback trên DB thật (`CatalogueMySqlSchemaIT`); transaction đồng thời thật (`Catalogue*ConcurrencyIT`). | Chưa có bảng workshop trong các migration hiện có nên không có dữ liệu workshop để kiểm tra. |
| V-13 | NOT RUN | — | `ppswbs_frontend/` chưa khởi tạo (T088 cần T002). Prototype `prototypes/002-product-management/` chỉ để thử màn hình, không phải bằng chứng. |
| V-14 | PARTIAL | Mốc 24 giờ/30 ngày, tham chiếu đang hoạt động và ảnh của sản phẩm đã xóa chặn dọn, DELETE_PENDING commit trước khi gọi provider và chặn gắn lại, lỗi tạm thời được thử lại (`CatalogueMediaCleanupIT`, provider giả). Với Cloudinary thật (`CatalogueCloudinaryIT`): gửi lại cùng nội dung trả đúng object cũ, khác nội dung bị từ chối và không ghi đè, URL gốc/biến thể không ký và kiểu `upload` công khai đều bị từ chối, xóa đúng object (API quản trị báo MISSING), xóa lần nữa vẫn thành công, log và lỗi không chứa API key/secret/URL có chữ ký, job dọn xóa object không tham chiếu và giữ object đang gắn. | Timeout thật giữa chừng (chỉ mô phỏng bằng lần gửi lại); hạn giữ audit/receipt 5 năm chưa có job dọn để kiểm tra; ảnh công khai qua endpoint (T070). |
| V-15 | PARTIAL | Đọc file (định dạng, macro, kích thước, 500/501 dòng, công thức, mọi mã lỗi dòng), kiểm tra → xem trước → xác nhận tạo sản phẩm nháp đúng SKU/giá/số lượng, lặp cùng key, danh mục bị ngừng giữa hai bước (`IMPORT_PREVIEW_STALE`), hết hạn (`IMPORT_BATCH_EXPIRED`), báo cáo lỗi vô hiệu hóa ô `=`/`+`/`-`/`@`, batch của người khác trả 404, job dọn payload, hai lệnh xác nhận cùng một lô đồng thời chỉ tạo sản phẩm một lần, hai lô khác nhau xác nhận đồng thời đều thành công (`CatalogueImportTests`, `CatalogueImportBatchIT`). | Multipart/REST và từ chối Guest/Member/Admin/inactive (T007, T099). |

## Lỗi tìm thấy khi kiểm chứng (đã sửa)

- `CatalogueLockCoordinator.lockProductWithTarget` ném lỗi khi không đổi danh mục (dùng `Set.of` với hai phần tử trùng).
- Record kết quả JPQL của catalogue công khai dùng `boolean` thay vì `Boolean`, Hibernate không khởi tạo được.
- Transaction lệnh chạy ở REPEATABLE READ khiến lệnh chờ khóa vẫn đọc snapshot cũ; đã chuyển sang READ COMMITTED (chi tiết trong blocker-report).
