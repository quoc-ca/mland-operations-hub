# Validation Report: Product Management

**Date created**: 2026-10-26
**Date updated**: 2026-11-10 (rà soát spec ↔ code và liên kết 002 ↔ 006: sửa interfaces PromotionTargetPort, phụ thuộc US10, ghi việc chờ FE-08/003.2/trang chủ; trước đó: 2026-11-05 (kiểm thử cuối trên màn thật trước khi gộp, sửa thông báo cũ ở trang chi tiết sản phẩm; Convergence T110–T113, chạy lại toàn bộ luồng 002 trên màn thật, sửa thông báo cũ ở màn Danh mục; trước đó: 2026-11-04 (T105–T109: sửa giá/số lượng nhiều size, áp dụng ngay sau khi duyệt, chỉ ra mục còn thiếu, thông báo kết quả; owner kiểm thử thực tế; trước đó: 2026-11-03 (rà soát spec–code–Figma: FR-003, FR-028, T103 xem ảnh quản trị, nhãn khu vực; trước đó: 2026-11-01 (T090 UI với API giả lập, tích hợp thật NOT RUN; T087 đồng bộ tài liệu; regression toàn hệ thống T086; chạy lại sau develop 365dcf3: T074, T085, T086; frontend React T088/T089 và màn hình 002; 2026-10-27: sau merge develop, T002/T007, REST API, T051, T073, T083, các task viết test; facade FE-08 phía 002)))
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Status**: Đang cập nhật — kết quả tạm thời, **chưa phải nghiệm thu**. Bảng mới nhất là "Ma trận V-01–V-15 cập nhật 2026-11-01" cộng mục "Rà soát task và trạng thái, 2026-11-03" ở cuối; bảng đầu tiên giữ làm lịch sử (lúc đó FE-08 và frontend React chưa có). Không mục nào được ghi PASS chỉ nhờ fixture hoặc prototype; chỗ nào dùng stub (xác minh Firebase token, provider ảnh, nguồn giữ hàng) đều ghi rõ.
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

## Cấu trúc frontend theo FRONTEND-ARCHITECTURE, 2026-11-02

- Chuyển `src/features/productmanagement` → `src/features/product-management` và `src/features/loyaltypromotion` → `src/features/loyalty-promotion` (`git mv`, giữ lịch sử), chia lớp `ui/`, `application/`, `domain/`, `infrastructure/`, `routes/`, `tests/`; mỗi feature có cổng công khai `index.js` (router chỉ import qua đó). Hành vi không đổi: frontend toàn ứng dụng 171/171, `npm run build` đạt.
- Hướng phụ thuộc đã kiểm: `domain` không import React/HTTP; `ui` không import `infrastructure` (ảnh công khai qua `application/useCatalogueApi.publicImageUrl`); `application` không import `ui` (nhãn đối tượng ưu đãi chuyển vào `domain/promotionRules`); không `fetch()`/Firebase trong `ui`; 002 và 006 không import code riêng của nhau.
- **Còn lệch chuẩn (ghi nhận):** lớp `infrastructure` chưa chuyển đổi DTO sang view model — màn hình vẫn đọc đúng shape response của backend; `shared/api` chưa đổi tên `shared/http` vì dùng chung với 001/003/FE-08. FRONTEND-ARCHITECTURE còn ở trạng thái chờ Product Owner duyệt.
- Đường dẫn `features/productmanagement/...`/`features/loyaltypromotion/...` trong tasks và bằng chứng cũ là lịch sử, ứng với vị trí mới cùng tên file trong lớp tương ứng.

## Chạy lại backend sau V78; V80 còn lỗi, 2026-11-02

Lần chạy 2026-11-02 sau khi owner 003 sửa V78 (develop `a64aa29`, commit `5556f45`):
- V78 đã qua. **V80** `V80__align_retail_grouped_checkout_reference_types.sql` (`76f104b`, 003/FE-08) cùng lỗi MySQL 1833: đổi `retail_grouped_checkouts.id` khi khóa ngoại `fk_retail_checkout_holds_checkout` còn tồn tại (thiếu `DROP FOREIGN KEY`). Quét tĩnh V1–V88 tìm thao tác đổi cột còn khóa ngoại: chỉ còn V80. Đã báo owner.
- Để biết phần còn lại, chạy với V78 của develop và V80 **vá tạm trên máy** (thêm `ALTER TABLE retail_checkout_holds DROP FOREIGN KEY fk_retail_checkout_holds_checkout;` đầu file); sau đó đã trả cả hai file về đúng bản của nhánh, không commit bản vá. Database trống `ppswbs_loyalty_build45`/`ppswbs_pm_sales45`, MySQL 8.4:
  - 006 + `ModulithArchitectureTests` + `WorkshopSellingLocationAdapterTest`: **258/258 đạt**.
  - 002: 297 chạy, 296 đạt; 1 lỗi `CatalogueSellingLocationIT$WithoutABranchSource` ("Too many connections" — `max_connections` về mặc định sau khi MySQL khởi động lại; kịch bản này cũng đã lỗi thời vì 003.2 đã có nguồn chi nhánh). Đã thay bằng `WithTheWorkshopOperationsSource` đọc qua `WorkshopSellingLocationAdapter` thật (chỉ chi nhánh đang hoạt động, sắp theo tên): `CatalogueSellingLocationIT` **3/3** khi chạy riêng.
- Kết luận chỉ có giá trị khi V80 được sửa đúng như bản vá; chạy lại trên develop sau khi owner sửa.

## Giao diện theo Figma "002-006" (đợt 1), 2026-11-03

- Nguồn: Figma page "002-006" (node 129:276, 100 frame), đọc qua Chrome đã đăng nhập (Figma MCP hết lượt cho View seat).
- 002: bộ biến `.pm-theme` (màu nền #F4F5F7, thanh bên #121316, nhấn #EEDC35, chữ #1A1A1A, viền #E5E7EB…) dùng cho khung quản trị (`.pm-shell`) và trang công khai (`.pm-public`); thanh lọc công khai dạng lưới, thẻ sản phẩm, chi tiết sản phẩm, khối thêm giỏ.
- 006: bộ biến `.lp-theme` cùng giá trị; khung quản lý ưu đãi có logo + topbar đường dẫn như 006-09; trang công khai/Member (`.lp-public`); `BenefitSelector` có biến riêng `--bs-*` vì FE-03/FE-08 nhúng vào trang của họ.
- Chỉ sửa `ui/` (JSX lớp/khung, CSS, chữ vi/en mới cho topbar); không đổi API, validation, phân quyền.
- Kiểm tra: `npm test` 171/171, `npm run build` đạt. Trình duyệt thật (backend local develop f2a17fd, chưa đăng nhập): /catalogue-management/products (khớp 002-01b), /products có dữ liệu thật (gần 002-07), /promotion-management/programs (khớp 006-09b), /promotions (rỗng).
- Chưa đối chiếu được: màn cần đăng nhập Staff/Manager (002-02…06 có dữ liệu, 006-10…13) — chờ tài khoản 001; màn Member (006-16/17) — cần đăng ký Member; bộ chọn ưu đãi trong trang FE-03/FE-08.

## Giao diện theo Figma "002-006" (đợt 2: màn quản trị), 2026-11-03

- Nguồn: ảnh xuất từ Figma page "002-006" (100 frame, `test-data/figma/`, không commit). So trên frontend thật (Vite local) với backend local (develop f2a17fd) và tài khoản Firebase thật của dự án: staff@gmail.com (STAFF), manager@gmail.com (MANAGER); vai trò đặt bằng cùng lệnh UPDATE như script set-admin-role của 001.
- 002: 02 (dữ liệu thật) khớp sau khi sửa ô chọn; 04d sửa đệm thẻ có bảng, viền đỏ khối Xóa, trạng thái size/công khai dạng chữ, nút Sửa/Ngừng bán có viền, bỏ nền dòng giá hiện hành; 03 sửa cao ô mô tả và khoảng cách tiêu đề nhóm; 05, 06 khớp. Figma ghi nhãn trên tiêu đề 05/06 là "QUẢN LÝ SẢN PHẨM" khác 02–04 ("STAFF / MANAGER"): giữ chung "STAFF / MANAGER", chờ nhóm chốt.
- Quyền: Staff vào được 002, trang quản lý ưu đãi 006 trả 403 "Tài khoản của bạn không có quyền thực hiện thao tác này" — đúng ma trận.
- 006 (Manager): 10 sửa nhãn phạm vi viết thường, mã voucher và nhãn phạm vi xuống dòng; 11 ô mã nền trắng, "Sinh mã khác"/"Tìm" là nút có viền; 12 tiêu đề thẻ dạng hàng với nhãn bên phải, điều khoản 2 cột (thời gian chiếm cả hàng), lượt dùng 4 cột; 13 hai cột, nút Sửa bên phải. Đo bằng computed style vì Chrome không chụp được ảnh khi cửa sổ bị ẩn.
- `npm test` 171/171, `npm run build` đạt.
- Chưa so: màn Member (006-14b/15/16/17, 002-08 có đăng nhập), các frame trạng thái phụ (lỗi, xác nhận, 409), BenefitSelector trong trang FE-03.

## Giao diện theo Figma "002-006" (đợt 3: màn Member), 2026-11-03

- Tài khoản member@gmail.com (MEMBER, tự đăng ký). Đo bằng computed style vì cửa sổ Chrome bị thu nhỏ (không chụp ảnh được).
- 006-16 Điểm của tôi: ba số điểm không khung, số 30px đen; danh sách quy tắc không thụt lề; bảng lịch sử có hàng tiêu đề nền xám.
- 006-14/15/17 thẻ ưu đãi và Kho Voucher: 2 thẻ mỗi hàng, hàng nhãn (phạm vi, trạng thái), mã voucher thành thanh nền xám, dòng mức giảm đậm 17px, điều khoản 2 cột; chi tiết ưu đãi có nhãn phạm vi và loại nằm bên trái.
- `npm test` 171/171, `npm run build` đạt.
- Chưa so: trạng thái phụ (lỗi tải, rỗng), 002-08 với khối thêm giỏ khi đã đăng nhập (chi nhánh thật của 003.2), BenefitSelector trong trang FE-03 (cần gói workshop thật).

## Bỏ header/footer chung, nút Đăng xuất ở thanh bên, 2026-11-03

- Theo yêu cầu của owner 002/006 (làm y hệt Figma page "002-006", không có header/footer chung): `src/app/router.jsx` đưa `productManagementRoutes` và `loyaltyPromotionRoutes` ra ngoài `AppLayout` (chỉ dời 2 dòng của 002/006, route của feature khác giữ nguyên). Khung quản trị và trang công khai/Member phủ toàn màn hình.
- Vì header chung chứa nút Đăng xuất và đổi ngôn ngữ, đã thêm nút "Đăng xuất" (bản dịch chung `session.signOut`) ở cuối thanh bên của khung 002 và 006, chỉ hiện khi đã đăng nhập. Trang công khai/Member không có nút này; đổi ngôn ngữ chưa có trên các màn 002/006.
- Sau đăng nhập, trang `/login` của 001 luôn chuyển về `/` (chưa có màn) và chưa đọc `returnTo`: đã đề nghị owner 001.
- FE-03 vẫn import `features/loyaltypromotion/checkout/BenefitSelector`; file chuyển tiếp ở đường cũ được giữ.
- `npm test` 171/171, `npm run build` đạt.

## Chạy lại toàn bộ trước khi gộp, 2026-11-03

- Trước khi gộp lên develop (nhánh `khuong` = develop `efa3c78` + phần 002/006): MySQL 8.4, database trống.
  - 006 + `ModulithArchitectureTests` + `WorkshopSellingLocationAdapterTest`: **258/258 đạt** (V78/V80 đã sửa trên develop).
  - 002: 297 chạy, 294 đạt, 3 lỗi "Too many connections" ở `CatalogueSellingLocationIT` (MySQL `max_connections` mặc định khi chạy dài); chạy riêng lớp đó **3/3 đạt**.
  - Frontend `npm test` 171/171, `npm run build` đạt.

## Nhập size nhanh trên màn Thêm sản phẩm, nhãn "QUẢN LÝ SẢN PHẨM", 2026-11-03

- Owner 002 chốt: nhãn trên tiêu đề màn Danh mục và Import là "QUẢN LÝ SẢN PHẨM" (en "PRODUCT MANAGEMENT"), các màn khác giữ "STAFF / MANAGER" như Figma. (Đây là quyết định hiện hành, xác nhận lại 2026-11-03: nhãn làm theo Figma.)
- Màn Thêm sản phẩm (phương án A + B do owner chọn): `ui/management/SizePicker.jsx` — 25 ô bấm size 5–29, chọn khoảng từ/đến, chọn tất cả/bỏ chọn; nhập một giá đề xuất và số lượng rồi "Áp dụng" cho mọi size đã chọn; bảng một dòng mỗi size (xếp tăng dần) để sửa riêng, có "Bỏ size". Body gửi backend không đổi (`variants[{size, proposedPrice, quantity}]`), luật size 5–29/tối đa 25/giá > 0/số lượng ≥ 0 vẫn do `checkVariants` và backend kiểm; lỗi theo ô hiển thị ở đúng dòng. Không chọn được size trùng hoặc ngoài khoảng.
- Test: 3 test tạo sản phẩm cập nhật theo cách chọn mới + 1 test cho ô size, khoảng, áp dụng chung và sửa riêng. `npm test` 172/172, `npm run build` đạt; kiểm tra trên Chrome thật (bấm nhanh nhiều size liên tiếp giữ đủ).
- Figma 002-03/03b/03c còn vẽ kiểu cũ (mỗi size một dòng gõ số): cần cập nhật ở phiên Figma.

## Nhãn khu vực và thanh lọc công khai, 2026-11-03

- ~~Owner chốt nhãn: mọi màn quản trị 002 (tiêu đề và nhóm trên thanh bên) là "QUẢN LÝ SẢN PHẨM" / "PRODUCT MANAGEMENT";~~ (phần 002 không còn hiệu lực: owner chốt lại 2026-11-03 làm theo Figma, xem mục rà soát bên dưới) màn tích điểm 006 (Chính sách điểm, Điểm của tôi) là "QUẢN LÝ TÍCH ĐIỂM" / "LOYALTY POINTS"; các màn chương trình ưu đãi giữ "MANAGER", Kho voucher giữ "MEMBER".
- Thanh lọc trang sản phẩm công khai xếp theo frame 002-07: hàng 1 Tìm kiếm (2 cột), Danh mục, Sắp xếp; hàng 2 Giá từ, Giá đến, Chỉ hiện sản phẩm có thể mua, Áp dụng.
- `npm test` 172/172, `npm run build` đạt. Danh sách chênh lệch Figma ↔ code đã gửi owner để cập nhật Figma.

## Rà soát spec–code–Figma, sửa FR-003/FR-028 và xem ảnh quản trị, 2026-11-03

- FR-003: danh sách quản trị hiện mã sản phẩm (`SP000042`) dưới tên; khi từ khóa là SKU, size có SKU khớp được đánh dấu và ghi "Khớp SKU … (size …)" (`productCode`/`skuMatch` trong `domain/productRules.js`). Fixture test đổi SKU sang đúng FR-006 (`SP000042-S12`).
- `GET catalogue-management/media/{mediaId}` (T103): chỉ phục vụ ảnh gắn với sản phẩm **chưa xóa mềm** (đồng nhất với chi tiết quản trị, sản phẩm đã xóa trả 404) hoặc ảnh do chính actor tải lên và chưa gắn vào sản phẩm nào (`ProductImageRepository.existsOnLiveProduct`). Thêm `CatalogueManagementMediaIT` (Staff/Manager xem ảnh nháp mà công khai không xem được, ảnh chưa gắn chỉ người tải lên xem, sản phẩm đã xóa 404, 401/403, 404/503 có kiểu) và test frontend cho `StaffImage` (bearer, blob URL, ô số thứ tự khi lỗi).
- Nhãn khu vực làm theo Figma (owner chốt 2026-11-03): nhóm trên thanh bên và tiêu đề màn 002-01…04 (danh sách, thêm, chi tiết) là "STAFF / MANAGER" (`nav.group`); màn 002-05 Danh mục và 002-06 Import là "QUẢN LÝ SẢN PHẨM" / "PRODUCT MANAGEMENT" (`nav.section`). Code vốn đã như vậy; đánh dấu câu "mọi màn quản trị 002 là QUẢN LÝ SẢN PHẨM" ở mục trước là không còn hiệu lực, sửa ngày các mục ghi nhầm 2026-11-04.
- FR-028: hook `application/useCatalogueCommand.js` (không sửa `shared/`): lệnh ghi mất phản hồi (status 0) thì Thử lại gọi `GET command-receipts/{key}?action=…` trước; có receipt thì dùng kết quả, không gửi lại; `COMMAND_RESULT_NOT_FOUND` thì gửi lại đúng key. Áp dụng cho chi tiết sản phẩm (mọi section), xóa, danh mục và tạo sản phẩm; import giữ cách đọc lại batch đã có. Tạo sản phẩm (không có expectedVersion) giữ key trong sessionStorage tới khi biết kết quả: sau khi tải lại trang, màn Thêm sản phẩm hỏi receipt và hiện liên kết tới sản phẩm đã tạo thay vì để tạo lần hai.
- Figma (chưa sửa: tài khoản là ghế View, MCP không ghi được): cần cập nhật 002-04/04b/04d…04k khối Ảnh (bỏ câu "Ảnh của sản phẩm chưa công khai không được xem trước từ máy chủ", vẽ ảnh thu nhỏ, ô số thứ tự khi đang tải/lỗi); 002-02/02d thêm mã sản phẩm dưới tên và size khớp SKU; mọi frame quản trị 002 thêm nút Đăng xuất cuối thanh bên (nhãn giữ nguyên như Figma); 002-03/03b/03c nhập size bằng ô bấm (đã ghi ở mục trước).
- Kiểm tra: frontend `npm test` 180/180 (002: 47, thêm `tests/recovery.test.jsx`), `npm run build` đạt. Backend MySQL 8.4, database trống: `CatalogueManagementMediaIT` 5/5, `CatalogueMediaAvailabilityIT` 2/2, `CataloguePublicApiIT` 6/6, `CatalogueMediaIT` 5/5, `CatalogueSaleStatusAndDeleteIT` 5/5 (23/23). Chưa chạy lại toàn bộ 002 và chưa kiểm trên trình duyệt với phiên Staff/Manager thật.

## Duyệt giá nhiều size (B1 + B2), 2026-11-03

Chỉ sửa frontend; không đổi spec, API hay backend. Mọi lệnh vẫn đi qua endpoint hiện có, mỗi lệnh một Idempotency-Key.

- B1, duyệt hồ sơ ban đầu (`sections/ReviewSection.jsx`): bảng giá duyệt có thêm cột ô chọn và cột "Chênh lệch" so với giá đề xuất (dòng đã chỉnh được tô nền). Khi có từ 2 size trở lên thì hiện thêm ô "Giá duyệt chung" với nút "Áp dụng cho N size đã chọn" và nút "Lấy lại giá đề xuất". Vẫn duyệt cả hồ sơ một lần (FR-010): ô chọn chỉ quyết định size nào nhận giá chung, body gửi vẫn có giá của mọi size.
- B2, giá từng size sau khi đã công khai (`sections/BatchPrices.jsx`, đặt ở đầu `PriceSection`):
  - Bảng "Giá đang chờ duyệt (N size)" hiện khi có từ 2 size Chờ duyệt. Mỗi dòng có: ô chọn, giá đang áp dụng, giá đề xuất, ô giá duyệt, chênh lệch. Có giá duyệt chung giống B1. Nút "Duyệt N size đã chọn" và "Từ chối N size đã chọn" (lý do dùng chung, bắt buộc theo `checkReason`).
  - Nút "Áp dụng giá đã duyệt cho N size": hiện cả trước và sau lần công khai đầu, không gửi số tiền (FR-015).
  - Nút "Gửi duyệt N size nháp": chỉ hiện sau lần công khai đầu.
  - Các lệnh của từng size chạy lần lượt (`decidePrice`, `applyPrice`, `submitPrice`). Mỗi size là một quyết định riêng (FR-013/FR-014): size lỗi không chặn size khác, kết quả hiện theo từng size ("Kết quả: 2/3 size thành công", kèm lý do của size lỗi), và tải lại sản phẩm nếu có ít nhất một size thành công.
  - Size bị mất phản hồi được giữ key: lần chạy sau hỏi `command-receipts` trước, rồi mới gửi lại đúng key đó (FR-028).
  - Các khối từng size bên dưới giữ nguyên.
- Test: thêm 3 test trong `tests/screens.test.jsx`:
  - B1: giá chung cho các size đã chọn, body gửi đủ mọi size.
  - B2: từ chối thiếu lý do không gửi gì; duyệt 3 size với key khác nhau, size 14 bị 409 được báo riêng, 2/3 thành công.
  - Áp dụng 2 giá đã duyệt một lần, không gửi số tiền.
- Kiểm tra: frontend `npx vitest run` 187/187, `npm run build` đạt. Chưa kiểm trên trình duyệt với phiên Manager thật.
- Figma: chưa có frame cho bảng duyệt nhiều size. Cần bổ sung nếu muốn Figma khớp code: 002-04 khối Duyệt hồ sơ (thanh giá chung, cột chọn, cột chênh lệch) và khối Đề xuất giá (bảng Giá đang chờ duyệt, các nút làm cho N size).

## Import: nhiều size trong một dòng (A1), 2026-11-03

Owner chọn phương án A1. Đã sửa spec FR-040 (thêm Clarification Session 2026-11-03 và edge case), `contracts/interfaces.md` (quy tắc ô Size, mã lỗi `INVALID_SIZE_LIST`) và `tasks.md` (T104).

- Backend `ProductImportValidator`:
  - Ô Size dạng văn bản có `,`/`;` hoặc khoảng giữa hai số (`12-20`, `20 – 21`) được đọc thành danh sách size, theo thứ tự đã viết. Mỗi size là một biến thể dùng giá đề xuất và số lượng của dòng. Ô số và ô văn bản không có dấu phân tách giữ quy tắc cũ (`5.0`, `+5`, `05`, `-5` vẫn `INVALID_NUMBER`).
  - Lỗi: sai cú pháp hoặc khoảng ngược → `INVALID_SIZE_LIST`; ngoài 5–29 → `OUT_OF_RANGE`; size lặp trong ô hoặc giữa các dòng cùng nhóm → `DUPLICATE_SIZE_IN_GROUP`. Mọi lỗi ghi đúng dòng, cột Size và giá trị ô.
  - Ô Size `12,5` giờ là danh sách size 12 và 5; test `12,5` dạng văn bản chuyển sang cột Giá (vẫn `INVALID_NUMBER`).
- `ProductImportService`: bước xác nhận báo danh mục cũ một lần cho mỗi dòng file (một dòng có thể ra nhiều biến thể).
- `ImportTemplateWriter`: hướng dẫn mới về cú pháp Size và giá dùng chung; ví dụ gồm `5-15`, `16,18,20` và một dòng một size.
- Không đổi DTO hay API: xem trước vẫn trả `sizes` cho mỗi nhóm. `rowCount` là số dòng file, `variantCount` là số size.
- Frontend:
  - Bước tải mẫu thêm gợi ý cú pháp cột Size.
  - Xem trước ghi "7 size: 5–9, 12, 14" (`compactSizes` trong `domain/productRules.js`).
  - Thêm thông điệp `importCode.INVALID_SIZE_LIST` (vi/en).
- Kiểm tra:
  - Backend MySQL 8.4 (database test mới `ppswbs_pm_sales_1103`): `CatalogueImportTests` 30/30 (thêm khoảng/danh sách, lỗi theo dòng, giới hạn 25 size, giá dạng văn bản) và `CatalogueImportApiIT` 7/7 (thêm tải file `5-9` + `12,14` → xem trước 7 size → xác nhận tạo 7 SKU, giá đề xuất đúng theo dòng).
  - Frontend `npx vitest run` 188/188, `npm run build` đạt.
  - Toàn bộ backend 002 (MySQL 8.4, database mới): 358 test, ban đầu lỗi 4. Sửa 1: `CatalogueContractIT` vẫn đếm 33 operation dù T103 đã thêm `getManagementProductMedia`, nên đổi thành 34. Chạy lại riêng thì đạt: `CatalogueContractIT` 4/4, `CatalogueSellingLocationIT` 3/3 (lần chạy chung lỗi do hết kết nối MySQL, không phải lỗi logic).
  - Chưa thử trên trình duyệt với phiên thật.

## Rà soát task và trạng thái, 2026-11-03

- `tasks.md`: 104/104 task đã tick (T001–T104). Không còn dấu chưa chốt (NEEDS CLARIFICATION/TBD) trong spec, plan, data-model, quickstart và contract. Code 002 không còn TODO/FIXME.
- Cập nhật V-ID so với bảng 2026-11-01:
  - V-01: phiên Firebase thật của 001 đã có. Đăng nhập thật bằng staff@gmail.com, manager@gmail.com, member@gmail.com: Staff vào được 002, còn khu ưu đãi 006 trả 403; Member không vào được khu quản trị. Trạng thái: PASS (backend + phiên thật ở mức vào màn hình).
  - V-13: PARTIAL. Màn quản trị đã mở với backend và phiên thật (danh sách, chi tiết, danh mục, import, đối chiếu Figma). **Chưa chạy trọn trên trình duyệt** hành trình nghiệp vụ: tạo → gửi duyệt → Manager duyệt → áp dụng giá → công khai → thấy trên trang công khai; import file thật; duyệt giá nhiều size (B2).
- Còn phụ thuộc người khác (không phải task 002):
  - 001 (Phan Sơn): sau đăng nhập `/login` luôn về `/`, chưa đọc `returnTo` hay chuyển theo vai trò.
  - FE-08 (Bùi Trọng Thịnh): trang `/member/cart` chưa có; nút "Thêm vào giỏ" thành công thì chuyển tới trang trống.
  - FE-03 (Quốc): còn import `BenefitSelector` qua đường cũ `features/loyaltypromotion` (006, file chuyển tiếp còn giữ).
- Figma: các thay đổi code chưa có frame (bộ chọn size 002-03, nút trên từng dòng 002-04, nút Đăng xuất, bảng duyệt giá nhiều size, gợi ý cột Size khi import). Prompt sửa Figma đã gửi owner.

## V-13: hành trình nghiệp vụ trên trình duyệt với phiên thật, 2026-11-03

Môi trường: Chrome đã đăng nhập Firebase thật bằng manager@gmail.com (MANAGER), frontend Vite local, backend local nhánh `khuong` (đã có A1, B1/B2), MySQL 8.4. Dữ liệu tạo ra nằm trong database local của máy dev.

1. **Tạo → duyệt → áp giá → công khai: ĐẠT.**
   - Thêm sản phẩm: chọn size 12, 14, 16 bằng ô bấm; áp giá chung 350000 và số lượng 5; sửa riêng size 16 thành 380000; tải ảnh. Kết quả SP000006 với SKU `SP000006-S12/S14/S16`.
   - Gửi duyệt, Manager duyệt với B1: bỏ chọn size 16, áp giá duyệt chung 360000 cho 12 và 14; cột chênh lệch hiện "+10.000 ₫"; size 16 "Bằng đề xuất".
   - "Áp dụng giá đã duyệt cho 3 size" (B2): 3/3 thành công. Công khai.
   - Trang `/products/6` hiện 360.000–380.000 ₫, 3 size "Có thể mua".
2. **Import có khoảng size: ĐẠT.** File `test-data/import-khoang-size.xlsx` (3 dòng: `5-9`, `12,14`, `16;18;20`):
   - Xem trước: "2 sản phẩm, 10 size", "7 size: 5–9, 12, 14".
   - Xác nhận: tạo SP000007 (7 SKU) và SP000008 (3 SKU). Giá đề xuất đúng theo dòng (5–9 = 350.000 ₫, 12 và 14 = 380.000 ₫).
3. **Duyệt giá nhiều size sau công khai (B2): ĐẠT.**
   - SP000006: đề xuất mới cho 3 size, "Gửi duyệt 3 size nháp" 3/3. Bảng "Giá đang chờ duyệt (3 size)": sửa riêng size 16 thành 385000, "Duyệt 3 size đã chọn" 3/3. "Áp dụng giá đã duyệt cho 3 size" 3/3. API công khai trả 370000/370000/385000.
   - Từ chối: đề xuất 999000 cho 2 size; bấm từ chối thiếu lý do thì báo "Cần nhập lý do." và không gửi; có lý do thì cả 2 size "Bị từ chối" kèm lý do, giá đang áp dụng giữ nguyên.
- **Lỗi tìm thấy và đã sửa:**
  - Trang chi tiết công khai hiện dòng đỏ "Chọn chi nhánh trước khi thêm vào giỏ." ngay khi mở trang, kể cả khi không có chi nhánh. Đổi thành gợi ý thường (`hint`), chỉ hiện khi có chi nhánh để chọn (`AddToCartPanel.jsx`).
- **Ghi chú môi trường:**
  - Lần đầu, bảng duyệt nhiều size bị vỡ cột. Nguyên nhân: Vite dev server chưa nhận phần CSS nối thêm bằng lệnh shell. Sau khi Vite tải lại file thì bảng hiển thị đúng 6 cột (đã kiểm `grid-template-columns`). Không phải lỗi code.
  - Database local chưa có chi nhánh đang hoạt động (003.2), nên chưa thử được "Thêm vào giỏ".
- **Kết luận:** V-13 **PASS** cho các hành trình quản trị 002 với phiên thật. Phần nút mua vẫn chờ chi nhánh (003.2) và trang `/member/cart` (FE-08).
- Kiểm tra sau khi sửa: frontend `npx vitest run` 187/188 khi chạy chung (1 test 006 quá 5 giây do máy bận; chạy riêng `loyalty-promotion` 30/30), `npm run build` đạt.

## Thao tác nhiều size, áp dụng ngay, mục còn thiếu, thông báo (Session 2026-11-04)

Chỉ sửa frontend 002; không đổi API, contract, data model hay backend. Spec: thêm Clarification Session 2026-11-04 và làm rõ FR-015 (duyệt không tự áp dụng; Manager có thể chọn gửi lệnh áp dụng ngay sau lệnh duyệt). Tasks: T105–T109.

- **T105 – Sửa giá đề xuất nhiều size** (`sections/BatchPrices.jsx`, `ProposeBatch`): chọn size hoặc khoảng size, giá chung, sửa từng dòng. Size đang Nháp/Chờ duyệt → `revisePrice` (Chờ duyệt bị rút về Nháp như sửa từng size), size khác → `proposePrice`. Sau lần công khai đầu có "Lưu và gửi duyệt" (lệnh `submitPrice` dùng `resourceId`/`version` của kết quả lưu) và "Chỉ lưu nháp". Chỉ size có giá mới được gửi; chưa nhập thì nút bị khóa; lưu xong ô nhập được xóa để không gửi lại cùng thay đổi.
- **T106 – Sửa số lượng nhiều size** (`sections/BatchQuantities.jsx`): *Cộng thêm* hoặc *Đặt bằng* cho các size đã chọn, sửa từng dòng; chỉ gửi size có số khác hiện tại qua `setQuantity`. Số nhỏ hơn "Đang giữ" bị chặn trên màn hình trước khi gửi (backend vẫn trả `QUANTITY_BELOW_HELD`). So sánh bằng BigInt vì số lượng có thể vượt giới hạn Number.
- **T107 – Áp dụng giá ngay sau khi duyệt**: ô chọn (mặc định bật) ở duyệt hồ sơ ban đầu (`ReviewSection`: sau `decideReview` đọc lại sản phẩm rồi `applyPrice` từng size đã duyệt), bảng "Giá đang chờ duyệt" và duyệt một size (`applyPrice` với phiên bản vừa duyệt). Duyệt và áp dụng vẫn là hai lệnh, hai Idempotency-Key, hai bản ghi. Áp dụng lỗi thì quyết định vẫn giữ; thử lại chỉ áp dụng size còn thiếu. Mục Duyệt hồ sơ có nút "Áp dụng giá đã duyệt cho N size" khi còn giá chưa áp dụng; nút cùng loại ở mục Đề xuất giá chỉ hiện sau lần công khai đầu (tránh hai nút giống nhau).
- **T108 – Chỉ ra mục còn thiếu** (`missingRequirements` trong `domain/productRules.js`): bấm Gửi duyệt mà thiếu ảnh, danh mục hoạt động hoặc giá đề xuất của size nào thì không gửi, không mở hộp xác nhận; mục thiếu có viền đỏ và dòng hướng dẫn, trang cuộn tới mục đầu tiên, cạnh nút có danh sách mục thiếu (bấm để nhảy tới). Lỗi điều kiện từ backend (`fieldErrors` khi gửi duyệt, `PUBLICATION_PREREQUISITES_MISSING` khi công khai) cũng tô mục thiếu. Bổ sung xong thì viền đỏ tự mất.
- **T109 – Thông báo kết quả** (`ui/management/notices.jsx`): mỗi thao tác hiện cùng một câu ở đầu trang và ngay dưới nút vừa dùng (dòng riêng, `.notice-at`), sau khi đã tải lại dữ liệu nên không nhấp nháy; giữ đến thao tác tiếp theo. Câu nêu việc đã làm, size/giá/số size hoặc tên, bước tiếp theo (37 câu vi/en, ví dụ "Đã sửa số lượng size 16: 1 → 4.", "Đã duyệt và áp dụng giá 999.000 ₫ cho size 20. Giá bán mới có hiệu lực."). Bảng nhiều size lỗi một phần: đầu trang khung đỏ "Đã xử lý x/y size…", dưới nút chỉ liệt kê size lỗi. Bỏ dòng tiến độ "Đang xử lý size…"; nút hiện "Đang lưu…" trong lúc chạy. Tạo sản phẩm báo ở đầu trang chi tiết; xóa báo ở đầu danh sách; danh mục báo ở đầu trang và dưới form/dòng danh mục.
- **Đối chiếu backend/contract**: chỉ dùng operation sẵn có (`proposePrice`, `revisePrice`, `submitPrice`, `decidePrice`, `applyPrice`, `decideReview`, `setQuantity`, `getProduct`, `getCommandReceipt`). Tên action khi hỏi receipt khớp hằng số backend (`price.propose/revise/submit/decide/apply`, `variant.quantity`, `product.review.*`). Apply vẫn cần quyền `price.apply` (interfaces mục quyền), không có trường số tiền.
- **Kiểm tra tự động**: frontend `npx vitest run` 202/202 (002: 58, thêm test cho sửa giá nhiều size, sửa số lượng nhiều size, chặn số dưới phần đang giữ, áp dụng ngay sau duyệt nhiều size và duyệt hồ sơ, mục còn thiếu khi gửi duyệt, thông báo ở hai chỗ); `vite build` đạt. Backend không đổi nên không chạy lại.
- **Kiểm thử thực tế**: owner 002 (Đặng Thị Khương) đã thử các luồng trên trình duyệt với backend local và phiên Staff/Manager thật ngày 2026-11-03/04 và xác nhận đạt; các chỉnh sửa giao diện phát sinh trong lúc thử (bảng tự đóng, nhấp nháy kết quả, vị trí và nội dung thông báo, khung thông báo lệch dòng) đã sửa trong cùng đợt.
- **Ghi nhận ngoài 002**:
  - Mỗi request có đăng nhập đi qua `IdentityGateFilter` (001) gọi `verifyIdToken(token, true)`, tức một lần gọi mạng tới Google (`getUserById`) mỗi request; thao tác nhiều size vì vậy chậm theo số size. Cần báo owner 001 (không sửa trong 002).
  - Thêm vào giỏ bằng tài khoản không phải Member: `RetailAccessDeniedException` bị `AccountExceptionHandler` (001, bắt mọi `Exception` cho toàn package) xử lí trước `RetailExceptionHandler` (008) nên trả 500 thay vì 403. Không sửa trong 002.
- **Figma** (file `m5Cyk5rit6lKaX3TPprSow`, page "002-006"): **chưa sửa**. Kiểm tra lại ngày 2026-11-04 bằng Figma MCP `whoami`: tài khoản dangkhuong28062004@gmail.com vẫn là ghế **View** (gói starter), MCP không ghi được. Danh sách cần người có ghế Edit vẽ thêm để Figma khớp code:
  - 002-04 (chi tiết sản phẩm), mục *Size và số lượng*: nút "Sửa số lượng nhiều size" và trạng thái mở của bảng (Từ size/Đến size/Chọn khoảng size; Cách nhập Cộng thêm/Đặt bằng; Số lượng; Áp dụng cho N size; bảng 6 cột: chọn, Size, Còn lại, Đang giữ, Số lượng mới, Chênh lệch; nút "Lưu số lượng N size"/Hủy; lỗi đỏ "Không được thấp hơn số đang giữ (n).").
  - 002-04, mục *Đề xuất giá*: nút "Sửa giá đề xuất nhiều size" và bảng mở (khoảng size, Giá đề xuất chung, cột Đề xuất hiện tại/Giá đề xuất mới/Chênh lệch, nút "Lưu và gửi duyệt N size", "Chỉ lưu nháp N size" hoặc "Lưu giá đề xuất N size" khi chưa từng công khai); bảng "Giá đang chờ duyệt" (đã có ghi chú từ đợt B2) thêm ô "Áp dụng giá ngay sau khi duyệt".
  - 002-04, khối *Quyết định của Manager* (duyệt hồ sơ ban đầu) và khối duyệt một size: ô "Áp dụng giá ngay sau khi duyệt (bỏ chọn nếu muốn áp dụng vào lúc khác)" trên nút Duyệt; khối Duyệt hồ sơ đã duyệt có nút "Áp dụng giá đã duyệt cho N size".
  - 002-04, trạng thái *thiếu điều kiện khi gửi duyệt*: mục Ảnh/Nội dung/Đề xuất giá viền đỏ với dòng hướng dẫn đỏ dưới tiêu đề; khung đỏ "Chưa gửi duyệt được, còn thiếu:" kèm danh sách liên kết cạnh nút Gửi duyệt.
  - Mọi frame quản trị 002 có thao tác: thông báo thành công xanh ở đầu trang và một khung xanh cùng nội dung ngay dưới nút vừa bấm (dòng riêng); nút đang chạy ghi "Đang lưu…"; bảng nhiều size lỗi một phần: khung đỏ đầu trang "Đã xử lý x/y size…" và danh sách size lỗi dưới nút.

## Convergence và kiểm thử lại trên màn thật (2026-11-05)

Môi trường: nhánh `khuong` (`d39b841` + thay đổi chưa commit của T110–T113), backend và frontend chạy local, MySQL 8.4 database `ppswbs_dev`, Firebase thật, phiên manager@gmail.com (MANAGER) đăng nhập trong khung Browser của Claude. Ảnh thử được tạo bằng canvas và đưa vào ô chọn file; file Excel lấy từ `test-data/` (phục vụ tạm qua Vite rồi xóa).

- **Tự động:** backend 002 + `ModulithArchitectureTests` (clean build) 371 test, 370 đạt; lỗi duy nhất `CatalogueSellingLocationIT` do MySQL "Too many connections" (`max_connections=151`), chạy riêng 3/3. Frontend toàn bộ 237/237, build đạt. Test mới: `CatalogueRejectionAuditIT` (2), `CatalogueImportBatchIT` (+2), `CatalogueCommandExecutorTests` (+4), `boundaries.test.js` (3).
- **Hành trình trên màn thật (đều đạt), đối chiếu `catalogue_audit_events`:**
  1. Danh mục: tạo (tên được trim), tạo trùng khác hoa/thường bị từ chối, audit `REJECTED` `DUPLICATE_CATEGORY_NAME`.
  2. Tạo sản phẩm size 12–16 bằng chọn khoảng, giá chung và sửa riêng, size 14 số lượng 0 → SP000011, SKU `SP000011-S12…S16`. Gửi duyệt khi chưa có ảnh: mục Ảnh viền đỏ, khung "còn thiếu"; alt text trống bị chặn; thêm ảnh xong viền đỏ mất.
  3. Gửi duyệt → Manager duyệt, chỉnh size 12 lên 460.000 ₫, bật áp dụng ngay → công khai. Audit duyệt và từng lần áp giá là bản ghi riêng. Trang công khai: khoảng giá 450.000–520.000 ₫, size 14 "Hết hàng" không chọn mua được, ảnh hiển thị, 2 chi nhánh.
  4. Sửa giá nhiều size 12–13 ("Lưu và gửi duyệt") → duyệt không áp dụng (giá cũ vẫn bán) → áp dụng riêng (audit giá cũ/mới 460.000 → 480.000). Sửa số lượng nhiều size (size 14: 0 → 3), ngừng bán size 15, ẩn (API công khai 404) rồi công khai lại (giữ `firstPublishedAt`).
  5. Import file lỗi: 7 lỗi theo dòng/cột, không tạo gì. Import `import-khoang-size.xlsx`: xem trước 2 sản phẩm/10 size → tạo SP000012 (7 SKU) và SP000013 (3 SKU); audit có `fileName`, `rowCount`, `productCount`, `variantCount`. Gắn ảnh hàng loạt: tự gắn theo Mã nhóm và gắn tay, cả hai lưu được, hồ sơ vẫn Nháp.
  6. Hai tab cùng sửa nội dung: tab lưu sau nhận "Dữ liệu đã được người khác thay đổi", giữ dữ liệu đã nhập, audit `REJECTED` `STALE_VERSION`.
  7. Xóa mềm SP000013: có hộp xác nhận, rời danh sách, thông báo ở đầu danh sách, dòng dữ liệu còn với `deleted_at`.
- **Lỗi tìm thấy và đã sửa:** màn Danh mục giữ khung xanh "Đã tạo danh mục…" của thao tác trước khi thao tác sau lỗi (lệch Session 2026-11-04: thông báo chỉ giữ đến thao tác tiếp theo). `CategoriesPage.jsx` giờ xóa thông báo trước mỗi lệnh (tạo, sửa, kích hoạt/ngừng, thử lại, cả khi báo thiếu tên), như `ProductDetailPage`. Test mới trong `screens.test.jsx` thất bại khi tắt bản sửa và đạt khi bật; frontend 002 62/62, build đạt; kiểm lại trên màn thật: sau tên trùng chỉ còn khung lỗi.
- **Ghi nhận, chưa sửa:** các nút "Ngừng bán size" trong bảng size không có nhãn riêng theo size cho trình đọc màn hình (không thuộc yêu cầu của spec).
- **Chưa chạy trên màn thật:** phiên Staff (Staff duyệt bị 403 chỉ có test tự động), thêm vào giỏ bằng Member (FE-08), batch import quá 60 phút.
- **Dữ liệu thử còn trong `ppswbs_dev`:** danh mục "Nhẫn kiểm thử 0511", "Nhẫn kiểm thử thông báo 0511"; SP000011 (đang công khai), SP000012 (nháp), SP000013 (đã xóa mềm).

## Kiểm thử cuối trên màn thật trước khi gộp (2026-11-05, chiều)

Môi trường: nhánh `khuong` (`c17573a` cùng thay đổi chưa commit của 006 T081), backend và frontend chạy local, MySQL 8.4 database `ppswbs_dev`, Firebase thật, phiên manager@gmail.com rồi member@gmail.com.

- **Kiểm tra tự động trước đó:** backend 002 + `ModulithArchitectureTests` trên database trống `ppswbs_pm_final1105b`: **365/365 đạt**. Lần chạy chung có 3 test của `CatalogueSellingLocationIT` lỗi vì MySQL "Too many connections"; chạy riêng thì 3/3 đạt. Lưu ý: kết quả này có được nhờ **vá tạm `V80`** của FE-08/003 trên máy (thêm `DROP FOREIGN KEY fk_retail_checkout_holds_checkout`), sau đó đã trả file về đúng bản của nhánh. `V80` trên nhánh (sau `4756bbc`) lỗi MySQL 1833 trên database trống và lệch checksum với `ppswbs_dev`, nên backend local phải chạy với `--spring.flyway.validate-on-migrate=false`. **Chặn gộp cho đến khi owner sửa `V80`.**
- **Hành trình trên màn thật, đều đạt:**
  - A1–A4: tạo danh mục, tên trùng khác hoa/thường bị từ chối, ngừng hoạt động có xác nhận và hiện số sản phẩm.
  - B1–B8 (SP000014): bỏ trống tên hoặc nhập giá sai thì báo lỗi ở từng ô và giữ dữ liệu; gửi duyệt khi chưa có ảnh bị chặn; Manager duyệt, sửa giá size 12, áp dụng ngay, công khai; trang công khai hiện 1.280.000–1.300.000 ₫.
  - B18–B20: size ngừng bán có nhãn; ẩn rồi công khai lại; đề xuất giá sau công khai đi qua nháp → gửi → duyệt không áp dụng (giá công khai chưa đổi) → áp dụng (giá đổi).
  - B9–B12, B21: lý do từ chối bỏ trống bị chặn; lịch sử có 2 lần duyệt; xóa có xác nhận.
  - C2–C5: file lỗi báo 7 lỗi theo dòng; file hợp lệ tạo #15 và #16; bấm xác nhận 2 lần không tạo trùng.
  - B22: Member vào khu quản trị sản phẩm thì bị 403.
- **Lỗi tìm thấy và đã sửa:**
  - **Hiện tượng:** trang chi tiết sản phẩm vẫn giữ thông báo "Đã tạo sản phẩm…" khi thao tác kế tiếp bị chặn ngay ở trình duyệt (gửi duyệt khi thiếu ảnh, ô nhập sai). Điều này lệch Session 2026-11-04.
  - **Sửa:** mọi handler của các section (`ContentSection`, `ImagesSection`, `PriceSection` ×2, `ReviewSection` ×2, `VariantsSection`, `BatchPrices` ×3, `BatchQuantities`) giờ gọi `setNotice(null)` trước khi kiểm tra dữ liệu.
  - **Test:** thêm 2 test trong `screens.test.jsx`. Hai test hỏng khi chưa có phần sửa và đạt khi có.
  - **Kết quả:** frontend 002 + 006 97/97; toàn bộ frontend 282/282; `npm run build` đạt. Trên màn thật với manager@gmail.com: tạo sản phẩm nháp #17 (chưa có ảnh) → bấm Gửi duyệt thì thông báo "Đã tạo sản phẩm…" biến mất, chỉ còn khung "còn thiếu". Lưu nội dung thành công ("Đã lưu tên, mô tả và danh mục.") rồi lưu với tên trống thì thông báo thành công biến mất, ô tên báo "Bắt buộc nhập.". Khung "còn thiếu ảnh" vẫn hiện vì là chỉ báo yêu cầu, chỉ tắt khi sản phẩm có ảnh.
- **Chưa chạy trên màn thật:** phiên Staff, gắn ảnh hàng loạt sau import, thêm vào giỏ (FE-08).
- **Dữ liệu thử còn trong `ppswbs_dev`:** danh mục "Nhẫn vàng 0511b" và "Nhẫn ngừng 0511b" (đã ngừng); SP000014 (đang công khai), #16 (đã duyệt), #15 (đã xóa mềm), #17 "Nhẫn kiểm tra thông báo 0511c" (nháp).

## Rà soát spec ↔ code và liên kết 002 ↔ 006 (2026-11-10)

- **Phạm vi:** tồn kho theo chi nhánh US10 (T116–T130, trừ T124), cảnh báo trùng tên FR-056 (T131), thêm nhanh FR-057 (T132), thẻ sản phẩm công khai FR-058 (T133); liên kết với 006 (voucher sản phẩm theo chi nhánh, giá sau giảm tự động trên thẻ — 006 FR-042).
- **Chạy ngày 2026-11-10 (nhánh `khuong`, chưa commit):** frontend 002 + 006 + landing 155/155, toàn bộ frontend 525/525, `npm run build` đạt; backend `LoyaltyProductTargetAdapterBranchTest` 3/3. Bằng chứng MySQL của T123–T130 xem ghi chú Phase 14 trong `tasks.md` (chưa chạy lại hôm nay).
- **Đã sửa tài liệu:** `contracts/interfaces.md` mục PromotionTargetPort ghi adapter nhận BRANCH; spec phần Phụ thuộc US10 ghi voucher sản phẩm theo chi nhánh đã triển khai ở 006; `tasks.md` thêm ghi chú liên feature về `LoyaltyProductTargetAdapter`.
- **Còn chờ owner khác:**
  - FE-08 (T124, FR-053) — **phần lớn đã làm trên develop `235eadd` (thinh, 2026-11-10), chưa có trên nhánh `khuong`**: giỏ, `isEligibleForCart`, checkout, thanh toán, hủy, hết hạn đều đọc/khóa/chốt theo (size, chi nhánh) qua `readBranchSaleFacts`, `withLockedBranchSaleContext`, `withLockedHistoricalBranchContext`, `withBranchGuard`, `recordAcceptedSale(op, variantId, locationId, qty)`; không còn lời gọi hàm theo size. Còn lại: bảng giữ hàng của FE-08 vẫn theo size và `RetailAvailabilityGuardService` chưa cài `tracksHoldsPerBranch`/`readBranchSnapshots`, nên guard theo chi nhánh đang dùng cài đặt mặc định theo size — giữ hàng đồng thời theo chi nhánh (SC-009) chưa có, FE-08 ghi là chờ migration giữ hàng theo chi nhánh. Sau đó 002 mới gỡ hàm theo size và `deductSaleWithoutBranch`. Ghép thử nhánh làm việc lên develop (2026-11-10): không xung đột; `CartJourneyTest.enforcesMaximumLineAndVariantQuantityLimitsAgainstPersistedCartData` của FE-08 lỗi cả trên develop gốc, không do 002/006. Cùng nhóm: FE-08 truyền `locationId` vào `SourceLine` cho voucher theo chi nhánh (006 T088) — develop `235eadd` vẫn chưa truyền.
  - 003.2: `SellingLocationPort` vẫn chỉ có `activeSellingLocations()`; hàm tra chi nhánh theo id gồm cả chi nhánh đã ngừng (spec Phụ thuộc US10) chưa có.
  - Owner trang chủ: gắn `PublicProductGrid` vào `features/landing/HomePage.jsx` (T133) cần đồng ý và commit riêng.
