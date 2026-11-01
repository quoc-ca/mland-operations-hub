# Validation Report: Product Management

**Date created**: 2026-10-26
**Date updated**: 2026-11-01 (T090 UI với API giả lập, tích hợp thật NOT RUN; T087 đồng bộ tài liệu; regression toàn hệ thống T086; chạy lại sau develop 365dcf3: T074, T085, T086; frontend React T088/T089 và màn hình 002; 2026-10-27: sau merge develop, T002/T007, REST API, T051, T073, T083, các task viết test; facade FE-08 phía 002)
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
| V-01 | PASS (backend); UI PASS (giả lập) | Thiếu bearer 401; token bị thu hồi 401 `TOKEN_REVOKED`; cookie không thay bearer; Admin bị 403 ở **mọi** operation quản trị; Member, `PENDING_SETUP` 403; Staff không duyệt được; Manager tự tạo/tự duyệt (`CatalogueApiIT`, `CatalogueContractIT`, `CatalogueImportApiIT`, `CatalogueReviewIT`). | Frontend: 401 kết thúc phiên đã test với phiên giả lập (T090); phiên Firebase thật NOT RUN: 001 chưa khởi tạo Firebase Admin và chưa có màn đăng nhập (Phan Sơn xác nhận 2026-11-01 không làm trong sprint này). |
| V-02 | PASS | Tạo nguyên khối qua REST, SKU đúng, size cố định, field lạ `UNKNOWN_FIELD`, thiếu `Idempotency-Key` 400, nháp không ảnh vẫn lưu (`CatalogueApiIT`, `CreateProductServiceTests`, `CatalogueMySqlSchemaIT`). | — |
| V-03 | PASS | Gửi/duyệt nguyên hồ sơ qua REST, một đề xuất chờ duyệt mỗi size kể cả gửi đồng thời, sửa trước khi duyệt tạo revision mới, snapshot không trôi, quyết định cũ bị từ chối, lý do bắt buộc, lỗi audit không duyệt một phần (`CatalogueReviewIT`, `CatalogueConcurrencyIT`, `CatalogueAuditFailureIT`, `CatalogueApiIT`). | "Cancel" là thao tác giao diện, không có lệnh backend. |
| V-04 | PASS | Duyệt chưa áp dụng thì không công khai; áp dụng đúng giá; điều khoản đổi `PRICE_TERMS_CHANGED`; revision bị thay thế `PRICE_NOT_APPROVED`; quyết định cũ không ghi đè; size hết hàng vẫn công khai; mất phản hồi gửi lại cùng key (`CataloguePublicationIT`, `CataloguePriceIT`, `CatalogueConcurrencyIT`, `CatalogueApiIT`). | — |
| V-05 | PASS | Sửa thẳng sau lần công khai đầu và khi đang ẩn; không gỡ được ảnh cuối của sản phẩm công khai; 10/11 ảnh, alt 200/201, vị trí; ảnh của người khác bị từ chối; upload thật lên Cloudinary `authenticated`; gửi lại theo checksum; lỗi provider rồi thử lại (`CatalogueContentEditIT`, `CatalogueMediaIT`, `CatalogueCloudinaryIT`, `CatalogueApiIT`). | — |
| V-06 | BLOCKED | Sản phẩm chưa từng công khai: số giữ = 0 đã chứng minh; đã từng công khai mà không có FE-08 thì 503 và không đổi gì (`CatalogueContentEditIT`, `CatalogueApiIT`). | Va chạm với giữ hàng/settlement cần adapter FE-08 thật. Phía 002 đã có `AvailabilityGuardPort` + `CatalogueSalesFacade`; số lượng đặt dưới guard và trừ bán một lần đã kiểm bằng nguồn giả chỉ trong test (`CatalogueSalesIT`), không phải bằng chứng tích hợp. |
| V-07 | PARTIAL | Ngừng/mở bán giữ số lượng và giá; ẩn rồi công khai lại kiểm tra lại điều kiện, giữ `firstPublishedAt`; xóa từ mọi trạng thái; key cũ sau khi xóa không làm sống lại; xóa và công khai đồng thời (`CatalogueSaleStatusAndDeleteIT`, `CatalogueLifecycleIT`, `CatalogueConcurrencyIT`). | Va chạm với giữ hàng mới, thanh toán cũ (FE-08). |
| V-08 | PASS (trừ phần giữ hàng) | Tên duy nhất kể cả danh mục ngừng, NFC/NFD, biên 200/201 và 5.000/5.001, tạo trùng đồng thời, tác động phân trang, ngừng chặn công khai không đổi cờ, kích hoạt lại không làm sống lại sản phẩm ẩn/xóa, đổi danh mục ngược chiều không deadlock, quyền qua REST (`CatalogueCategoryIT`, `CatalogueCategoryConcurrencyIT`, `CatalogueImportApiIT`). | Va chạm với giữ hàng mới (FE-08). |
| V-09 | PARTIAL | Lọc giá + "có thể mua" trên cùng size, biên khoảng giá có tính bằng, đề xuất/giá chưa áp dụng không lên trang công khai, từ khóa 200/201 code point, nhãn size ngừng bán, sắp xếp có định danh phụ, `%`/`_` là ký tự thường, phân trang 20 + 1 (`CataloguePublicIT` 11 test, nguồn giữ hàng giả `TestOnlyAvailabilityGuard` chỉ trong test). | Số giữ thật từ FE-08. |
| V-10 | PARTIAL | Ảnh bị mất ở provider → sản phẩm rời catalogue công khai mà cờ công khai không đổi, đọc lại được thì phục hồi, lỗi provider không bị coi là "mất"; thiếu FE-08 trả 503 chứ không giả định 0; ảnh công khai chỉ phục vụ khi sản phẩm còn công khai (`CatalogueMediaAvailabilityIT`, `CataloguePublicIT`, `CatalogueImportApiIT`, `CatalogueContentEditIT`). | Hiển thị phân biệt lỗi phụ thuộc/catalogue rỗng ở frontend. |
| V-11 | PASS | Cùng key cùng dữ liệu trả kết quả cũ, khác dữ liệu 409, gửi đồng thời chỉ một receipt, tra receipt chỉ của chính actor, mất quyền thì bị từ chối trước khi trả kết quả (`CatalogueCommandExecutorTests`, `CatalogueConcurrencyIT`, `CatalogueApiIT`). | — |
| V-12 | PASS | Cài mới và nâng cấp migration, 13 khóa ngoại RESTRICT, 30 CHECK, unique, InnoDB/utf8mb4; lỗi audit rollback; đồng thời thật (`CatalogueMySqlIT`, `CatalogueMySqlSchemaIT`, `CatalogueAuditFailureIT`, `Catalogue*ConcurrencyIT`). Regression Member/workshop chạy trong cùng bộ. | Không có bảng workshop cũ cần bảo toàn trong migration hiện có. |
| V-13 | PASS (giả lập) | Mọi màn hình 002 chạy qua REST client dùng chung với fetch giả lập theo DTO backend: loading, empty khác lỗi, field error giữ input, thành công chỉ từ response, 409 tải bản mới, 503 có Thử lại, 401 sau một lần làm mới token kết thúc phiên và ẩn dữ liệu (`screens.test.jsx` "T090"), nội dung từ server hiện dạng chữ; trang công khai chạy thật với backend local (T074). | Hành trình quản trị với phiên Firebase thật: NOT RUN: 001 chưa khởi tạo Firebase Admin và chưa có màn đăng nhập (Phan Sơn xác nhận 2026-11-01 không làm trong sprint này). |
| V-14 | PASS (trừ hạn giữ 5 năm) | Mốc 24 giờ/30 ngày, tham chiếu chặn dọn, DELETE_PENDING trước khi gọi provider, thử lại; Cloudinary thật: gửi lại không trùng/không ghi đè, URL không ký bị chặn, log không lộ bí mật, dọn xóa đúng object (`CatalogueMediaCleanupIT`, `CatalogueMediaIT`, `CatalogueCloudinaryIT`). | Hạn giữ audit/receipt 5 năm chưa có job để kiểm tra. |
| V-15 | PASS | Mẫu, kiểm tra, xem trước, xác nhận qua REST; mọi loại lỗi dòng; `.xls` 415; 500/501 dòng; lặp/mất phản hồi; danh mục ngừng giữa hai bước; hết hạn; Guest/Member/Admin bị từ chối; batch người khác 404; hai lần import đồng thời; báo cáo lỗi vô hiệu hóa `=`/`+`/`-`/`@` (`CatalogueImportTests`, `CatalogueImportBatchIT`, `CatalogueImportApiIT`). | — |

## Lỗi tìm thấy khi kiểm chứng (đã sửa)

- `lockProductWithTarget` lỗi khi không đổi danh mục; record kết quả JPQL cần `Boolean`.
- Transaction lệnh ở REPEATABLE READ đọc snapshot cũ sau khi chờ khóa → chuyển READ COMMITTED.
- `CatalogueStrictJsonConverter` đăng ký dạng bean làm Spring Boot bỏ bộ chuyển Jackson mặc định, mọi feature ghi ngày giờ dạng số → đăng ký qua `WebMvcConfigurer`.
- `AccountExceptionHandler` của 001 bắt lỗi của mọi feature (500) → handler của 002 đặt ưu tiên cao nhất cho controller của 002; đã báo owner 001.

## Frontend React (2026-11-01)

```powershell
Set-Location D:/mland-operations-hub/mland-operations-hub/ppswbs_frontend
npm.cmd test
npm.cmd run build
```

| Kiểm tra | Kết quả |
| --- | --- |
| `npm test` (Vitest 4.1.12, jsdom) | 4 file, 42 test pass — HTTP client theo bảng lỗi, API 002, màn hình với HTTP giả lập |
| `npm run build` (Vite 7.3.7) | Đạt; cảnh báo bundle > 500 kB (Firebase SDK) |
| Trình duyệt, Vite dev, không có backend | Lỗi kết nối có Thử lại; tiêu chí lọc đọc lại từ URL; khu quản trị báo chưa cấu hình Firebase; 375px không cuộn ngang |
| Tích hợp UI/API thật (V-13, T090) | **NOT RUN**: chưa có cấu hình Firebase / đăng nhập 001; CORS preflight bị `workshopOperationsCorsFilter` chặn (BE-DESIGN-0012) |

## Chạy lại sau develop `365dcf3` (T074, T085, T086), 2026-11-01

**Môi trường.** Nhánh `khuong` = develop `365dcf3` + thay đổi chưa commit của 002/006; Java 21.0.12, Spring Boot 3.4.1, MySQL 8.4.13 (dịch vụ `MySQL84`), mỗi lần chạy một database trống (`ppswbs_pm_sales50`–`53`, đã xóa sau khi chạy); Node 24.16, Vitest 4.1.12, Vite 7.3.7. Cloudinary: không có `CLOUDINARY_*` trong lần chạy này nên `CatalogueCloudinaryIT` tự bỏ qua; bằng chứng Cloudinary thật vẫn là lần chạy 2026-10-26 (T051).

```powershell
Set-Location D:/mland-operations-hub/mland-operations-hub/ppswbs_backend
$env:PM_TEST_JDBC_URL = "jdbc:mysql://localhost:3306/ppswbs_pm_salesNN?createDatabaseIfNotExist=true"
.\mvnw.cmd -o test "-Dtest=Catalogue*Tests,Catalogue*IT,ModulithArchitectureTests,**/features/productmanagement/*/**/*"
Set-Location ../ppswbs_frontend
npm.cmd test; npm.cmd run build
```

| Lệnh | Kết quả |
| --- | --- |
| 002 + Modulith (lệnh trên) | 342 test: 326 đạt trong lần chạy chung; `CatalogueSalesIT` (16) lỗi khởi tạo context vì MySQL "Too many connections" (giới hạn kết nối khi chạy nhiều context cùng backend local), chạy lại riêng 16/16 đạt → **342/342 đạt, 0 bỏ qua** (`CatalogueCloudinaryIT` không có trong lần chạy vì thiếu `CLOUDINARY_*`) |
| `CataloguePublicApiIT` (mới, T074) | 6/6 — trường công khai đúng tập (không lộ đề xuất/duyệt/số lượng/version/mediaId), `no-store`, không `Set-Cookie`, duyệt catalogue không tạo dòng nào ở `catalogue_audit_events`, `catalogue_command_receipts`, `retail_variant_holds`, `retail_carts`, `retail_cart_lines`, `retail_grouped_checkouts`, `retail_orders`; khoảng giá 150.000–180.000 và "có thể mua" trên cùng size; size ngừng bán giữ nhãn; `INVALID_SORT`, giá thập phân, `categoryId` sai, trang âm bị 400; từ khóa 200 nhận, 201 bị 400; 20 sản phẩm/trang, trang 2 có 1, giá bằng nhau xếp theo id; URL cũ của sản phẩm ẩn/xóa → 404 `PRODUCT_NOT_FOUND`, ảnh của sản phẩm ẩn → 404; nguồn giữ hàng FE-08 trả kết quả không kiểm chứng được → 503 `AVAILABILITY_DEPENDENCY_UNAVAILABLE` có correlation ID, phục hồi → 200 |
| Frontend `npm test` | 6 file, 66 test đạt; `npm run build` đạt |

**Lỗi tìm thấy và đã sửa (002 frontend).** `ImageEditor`: ảnh vừa chọn biến mất khỏi danh sách ngay khi kết quả tải lên trả về, vì lần cập nhật trạng thái dựa trên danh sách của lần render trước. Sửa: mọi thay đổi đi qua `commit` cập nhật danh sách ngay. Test `T085 remaining management journeys` (tải ảnh lỗi 503 → Thử lại cùng key → gắn bằng `mediaId`).

**Hành trình trình duyệt (T074, T090 phần công khai).** Vite dev `http://localhost:5173` + backend local `http://localhost:8080` (database `ppswbs_dev` trống, chạy bằng `.env` với `SPRING_JPA_HIBERNATE_DDL_AUTO=none`, xem ghi chú dưới):
- `/products?q=nhan&sort=price-desc&canBuy=true` → `GET /api/v1/products?q=nhan&canBuy=true&sort=price-desc` 200, form đọc lại đủ tiêu chí từ URL, trạng thái rỗng "Không có sản phẩm khớp với tiêu chí hiện tại.".
- `/products?minimumPrice=1.5` → 400, thông báo lỗi kèm mã tra cứu từ backend.
- `/products/999999` → 404, "Sản phẩm không tồn tại hoặc không còn được công khai".
- Không có header `Authorization` trên các GET công khai. Database trống nên chưa xem được thẻ sản phẩm thật: tạo sản phẩm cần khu quản trị, mà khu quản trị chưa đăng nhập được (001).

**Ghi chú môi trường (không phải lỗi của 002).** Develop `f5e29a4` (001) đặt mặc định `spring.jpa.hibernate.ddl-auto=validate`; entity `BookingLatePaymentEvidence.currency_code` (FE-03, `varchar(3)`) lệch với migration V40 (`CHAR(3)`), nên ứng dụng không khởi động trên database mới. Test vẫn đạt vì profile test dùng `ddl-auto=none`. Chạy local dùng `SPRING_JPA_HIBERNATE_DDL_AUTO=none`; cần owner 001/003 thống nhất.

### Ma trận V-01–V-15 cập nhật 2026-11-01

| ID | Trạng thái | Bằng chứng mới so với bảng trên | Còn lại |
| --- | --- | --- | --- |
| V-01 | PASS (backend) | Không đổi; CORS dùng chung đã gỡ (006 `LoyaltySecurityIT` 11/11) | Phiên Firebase thật NOT RUN (001 chưa khởi tạo Firebase Admin) |
| V-02 | PASS | Không đổi | — |
| V-03 | PASS | Không đổi | — |
| V-04 | PASS | `CatalogueAcceptedPriceIT`: đơn đã accept giữ giá cũ khi áp giá mới | — |
| V-05 | PASS | Không đổi; Cloudinary thật lần 2026-10-26 | — |
| V-06 | PASS | Nguồn giữ hàng FE-08 thật: `CatalogueQuantityIT` (10), `CatalogueContentEditIT` | — |
| V-07 | PASS | `CatalogueLifecycleConcurrencyIT` (5): checkout accept trước khi ẩn/xóa vẫn thanh toán/hủy được; xóa tranh với accept thật không bán sản phẩm đã xóa | — |
| V-08 | PASS | Giữ hàng thật khi đổi danh mục (`CatalogueCategoryConcurrencyIT`) | — |
| V-09 | PASS | `CataloguePublicApiIT` qua HTTP (mục trên); số đang giữ thật trừ vào "có thể mua" | Thẻ sản phẩm trên trình duyệt với dữ liệu thật chờ khu quản trị (001) |
| V-10 | PASS | Nguồn giữ hàng không kiểm chứng được → 503 rồi phục hồi (`CataloguePublicApiIT`) | — |
| V-11 | PASS | Không đổi | — |
| V-12 | PASS | Chạy lại trên database trống với 59 migration của develop | — |
| V-13 | PARTIAL | Frontend React có: build đạt, test component/API giả lập (66 test đạt), hành trình trình duyệt trang công khai với backend thật | Màn quản trị với backend thật NOT RUN (chưa có phiên 001); nút mua (T072) chờ FE-08 |
| V-14 | PASS | `CatalogueRetentionIT` (2): job dọn ở mốc 6 năm không xóa receipt/audit/ảnh của sản phẩm đã xóa mềm | — |
| V-15 | PASS | Không đổi | — |

### Regression toàn backend (T086), 2026-11-01

Sau khi tăng tạm `max_connections` của MySQL từ 151 lên 500 (`SET GLOBAL`, mất khi dịch vụ khởi động lại; Đặng Thị Khương đồng ý), chạy `.\mvnw.cmd -o test` (cấu hình Surefire hiện hành) với database trống riêng cho từng profile: `JDBC_URL` → `ppswbs_reg_app`, `PM_TEST_JDBC_URL` → `ppswbs_reg_pm`, `LOYALTY_TEST_JDBC_URL` → `ppswbs_reg_loyalty`, `WORKSHOP_OPERATIONS_TEST_JDBC_*` → `ppswbs_reg_ws`.

| Phạm vi | Kết quả |
| --- | --- |
| Toàn backend | 1.463 test: 21 failure, 62 error, 64 bỏ qua |
| 002 (productmanagement) + 006 (loyaltypromotion) + `ModulithArchitectureTests` | **613 chạy, 0 failure, 0 error** |
| Feature khác (lớp lỗi: số test, failure, error) | features.readyringsales.GroupedCheckoutIntegrationTest (1, F0, E1); features.readyringsales.LocalCheckoutCancellationIntegrationTest (1, F0, E1); workshopbooking.WorkshopBookingQuotePersistenceIT (1, F0, E1); workshopoperations.WorkshopPackageFacadeIT (11, F0, E10); workshopoperations.WorkshopOtaSnapshotIT (11, F0, E11); workshopoperations.AttendanceTicketContractIT (11, F1, E0); workshopoperations.SettlementChangeFacadeIT (13, F0, E13); workshopoperations.OtaBookingFacadeIT (3, F0, E3); features.readyringsales.CartJourneyTest (4, F0, E4); workshopsettlement.RequiredSessionChangeIT (5, F0, E5); workshopoperations.WorkshopOperationsQuickstartIT (6, F0, E6); workshopbooking.WorkshopBookingDtoTests (6, F1, E0); workshopbooking.WorkshopBookingMigrationIT (7, F0, E7); workshopoperations.WorkshopLifecycleContractIT (7, F3, E0); workshopoperations.WorkshopOperationsSnapshotTest (8, F2, E0); workshopoperations.CapacityHoldIT (8, F6, E0); workshopoperations.CheckInIT (8, F8, E0) |

Các lỗi ngoài 002/006 đến từ cách dựng database test của feature khác, không do 002: `readyringsales` (profile `test` dùng H2 trống, "Table RETAIL_CARTS not found"), `workshopbooking`/`workshopsettlement`/`workshopoperations` (database của `WORKSHOP_OPERATIONS_TEST_JDBC_URL` chưa có bảng `workshop_locations`, và một số assertion phụ thuộc dữ liệu/thứ tự). `CatalogueCloudinaryIT` bỏ qua vì không có `CLOUDINARY_*` (Cloudinary thật: lần chạy 2026-10-26). V-01/V-13 với phiên 001 thật vẫn NOT RUN.

## T090 — kiểm thử UI/API frontend 002, 2026-11-01

- **Lệnh** (từ `ppswbs_frontend`, theo T088): `npm test` → **73/73 đạt** (6 file, gồm 006); `npm run build` → đạt (cảnh báo kích thước chunk, không lỗi).
- **Phạm vi:** T028, T034, T040, T044, T050, T057, T064, T071, T072, T079 (và T101) — loading, empty, validation, success, failure, recovery, 401/409/503. Test mới: 401 vẫn bị từ chối sau khi làm mới token một lần → `signOut`, màn quản trị hiện thông báo đăng nhập, không còn dữ liệu, token không nằm trong URL.
- **Cách giả lập:** phiên là `fakeSession` (token test cố định), API là fetch giả lập trả đúng shape DTO backend. Ứng dụng không có chế độ đăng nhập giả và không tắt kiểm tra token (AGENT.md frontend).
- **Tích hợp thật:** trang công khai và CORS đã chạy với backend local (T074); hành trình cần phiên Manager/Staff/Member — NOT RUN: 001 chưa khởi tạo Firebase Admin và chưa có màn đăng nhập (Phan Sơn xác nhận 2026-11-01 không làm trong sprint này). Khi 001 có đăng nhập: chạy lại V-01/V-13 qua trình duyệt theo quickstart.
