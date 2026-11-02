# Quickstart and Validation: Product Management

**Date created**: 2026-10-24
**Date updated**: 2026-10-26 (V1 không MFA theo 001; tên facade identity; tham chiếu 001 mới; lệnh và lớp test thực tế, liên kết validation-report; cập nhật 2026-10-27 sau REST API)
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Status**: Approved — duyệt bởi Đặng Thị Khương ngày 2026-10-24; kế hoạch kiểm chứng, chưa có implementation để chạy nghiệm thu.   Phần bổ sung import Excel (US9) được duyệt bởi Đặng Thị Khương ngày 2026-10-24.
**References**: [spec](spec.md), [plan](plan.md), [data model](data-model.md), [OpenAPI](contracts/product-management.openapi.json), [interfaces](contracts/interfaces.md).

## Prerequisites

1. Hướng A đã chốt: dùng chung cơ chế xác thực theo FE-01/001 (bearer, kiểm tra thu hồi và trạng thái mỗi request) và kiểm tra quyền từng action; Cloudinary/roles/retention/defaults cũng đã chốt. Trước enable quản trị, identity owner cần hoàn thiện thiết kế MFA/recovery và triển khai/kiểm chứng kết quả xác thực đặc quyền; chưa chọn phương thức cụ thể từ FE-02. Spec/plan/tasks được review và có phân tích trước code. Các adapter chưa có được sắp thứ tự như dependency; nghiệm thu tích hợp cần implementation thật, không yêu cầu code phải có sẵn để viết plan.
2. JDK 21, Maven wrapper của repository, disposable MySQL 8.4 được phép dùng. H2 hiện là default, không dùng H2-only làm kết luận migration/concurrency/collation pass.
3. Identity test environment đã được owner cung cấp, actor Guest/Member/Staff/Manager/Manager-self/inactive và capability đúng; không tạo role từ Firebase client claim hoặc suy quyền từ cookie profile 001. Cloudinary test namespace dùng authenticated assets, signed backend upload và stream cùng origin; cấu hình qua môi trường. Adapter FE-08 thật có guard theo contract.
4. Không dùng database, token, ảnh hoặc credential production. Không đọc/ghi/reset dữ liệu thực. Biến môi trường được cấp qua secret channel, không paste credential vào output/evidence.

## Commands after implementation

Từ workspace root trong PowerShell, với JDK 21 (`JAVA_HOME`) và MySQL 8.4 test đã chạy. Kết quả thực tế được ghi tại [validation-report](validation-report.md).

```powershell
Set-Location D:/mland-operations-hub/mland-operations-hub/ppswbs_backend
# Toàn bộ backend (Surefire của dự án chạy cả lớp *IT):
.\mvnw.cmd test
# Riêng productmanagement:
.\mvnw.cmd '-Dtest=Catalogue*Tests,Catalogue*IT,ModulithArchitectureTests' test
# Migration trên database trống và nâng cấp (T081):
.\mvnw.cmd -Dtest=CatalogueMySqlIT test
# Transaction đồng thời thật:
.\mvnw.cmd '-Dtest=CatalogueConcurrencyIT,CatalogueCategoryConcurrencyIT' test
```

Các lớp đã có (2026-10-27): xem bảng "Nhóm test của 002" trong [validation-report](validation-report.md) (đơn vị, MySQL tầng service, đồng thời thật, HTTP với security thật `CatalogueApiIT`/`CatalogueImportApiIT`/`CatalogueContractIT`, Cloudinary thật `CatalogueCloudinaryIT`, ranh giới `CatalogueDependenciesIT`). Còn là mục tiêu thiết kế, chưa tồn tại vì cần adapter FE-08 thật (không coi "không tìm thấy test" là pass): `CatalogueQuantityIT`, `CatalogueLifecycleConcurrencyIT`, `CatalogueAcceptedPriceIT`, `CataloguePublicApiIT`. Chạy `CatalogueCloudinaryIT` cần `CLOUDINARY_CLOUD_NAME`/`CLOUDINARY_API_KEY`/`CLOUDINARY_API_SECRET`; thiếu thì lớp này báo NOT RUN.

Bộ MySQL IT dùng profile `productmanagement-mysql-test`: datasource lấy từ `PM_TEST_JDBC_URL`/`PM_TEST_JDBC_USER`/`PM_TEST_JDBC_PASSWORD`, nếu thiếu thì `JDBC_URL`/`JDBC_USER`/`JDBC_PASSWORD`; không có giá trị mặc định, không H2. `CatalogueMySqlEnvironmentIT` từ chối chạy nếu server không phải MySQL 8.4. `CatalogueMySqlIT` tạo database tạm `ppswbs_pm_fresh_<ngẫu nhiên>` trên cùng server (tài khoản test cần quyền `CREATE`/`DROP` database) và tự xóa sau khi chạy; không đụng tới database test hay database thật. Username/password do môi trường kiểm chứng cấp qua biến môi trường, không ghi ở đây.

Để chạy ứng dụng sau implementation, cấp datasource/identity/media/FE-08 settings theo contract cấu hình đã review rồi dùng `.\mvnw.cmd spring-boot:run` cho backend; chạy frontend React + Vite riêng sau khi `ppswbs_frontend/` được khởi tạo. Không suy rằng các settings/adapter này đã tồn tại. G-01 đã chốt hướng/phiên nhưng quản trị vẫn không được enable khi dependency MFA/session/capability chưa đáp ứng; không dùng fallback permitAll để bỏ qua.

## Acceptance fixtures

- Một category hoạt động và một category inactive; tên chuẩn hóa trùng khác case/outer whitespace, tên có dấu khác nhau.
- Product A có size 5, 12; Product B cùng size nhưng SKU khác. Mã sản phẩm 123 và mã hơn 6 chữ số; không tạo thêm size sau lưu.
- Hai size giá khác nhau, một size có remaining=10/held=2, một size ngừng bán vẫn còn lượng, một product mọi size hết hàng.
- Ảnh hợp lệ JPEG/PNG/WebP, 10/11 ảnh, tệp ngay dưới/đúng/trên byte limit, alt text biên, media unavailable rồi phục hồi; staging upload chưa attach phải private.
- File Excel import (tạo bằng công cụ test, không dữ liệu thật): file hợp lệ 2 sản phẩm (size 5, 12 và size 29), file có từng loại lỗi dòng (tên 201 ký tự, size 4/30/5.0, trùng size, giá 0/âm/`12.5`/`1.000`, số lượng âm, danh mục sai/inactive, thiếu Mã nhóm, nhóm khác tên), file 500/501 dòng, tiêu đề thiếu/thừa cột, đuôi `.xls`/`.xlsm`/file hỏng/zip-bomb nhỏ, ô công thức, giá trị bắt đầu bằng `=`. Danh mục hoạt động bị ngừng giữa lúc xem trước và xác nhận; file có nhiều lỗi (tới 4.000 phần tử); batch quá hạn 60 phút.
- FE-08 hold/accepted order thật có frozen price/deadline trước đổi giá/ẩn/xóa; mọi fixture phải có nguồn authoritative, không chỉ set field catalogue “held”.

## Verification matrix

| ID | Execute | Expected result and reference |
| --- | --- | --- |
| V-01 | Guest/Member/inactive truy cập quản trị; Staff gọi approve; Manager tự tạo/tự duyệt. Kiểm tra bearer API và frontend React với bearer token hết hạn, token revoked và thiếu kết quả đạt xác thực đặc quyền. | Từ chối đúng 401/403/503 theo contract; không trượt thời hạn phiên, không suy quyền từ cookie Member, không truy cập quản trị khi thiếu assurance từ identity. Self-approval hợp lệ; giữ luồng Member, không policy-profile redirect. US1.8–10, US2.3/6. |
| V-02 | Tạo bản nháp và lỗi field/size/quantity/price, thử tạo thêm/đổi size/khắc/SKU. | Atomic create, SKU đúng, size cố định, lỗi trường rõ, bản nháp chưa có ảnh vẫn lưu. US1, US4.4. |
| V-03 | Gửi cùng hồ sơ mới, sửa trước approve, stale decision, reject/resubmit, Manager cancel, audit failure. | Review/price revision đúng, một pending proposal/size kể cả gửi đồng thời, không partial decision. US2; Clarifications Q1. |
| V-04 | Duyệt nhưng chưa apply; apply đủ rồi publish; zero quantity; apply amount khác/old proposal. | Không publish sớm, tách giá và publication, old application không ghi đè, sold-out vẫn public. US3/5. |
| V-05 | Sửa nội dung sau first-public và khi tạm ẩn, thay/gỡ ảnh cuối, lỗi upload/attach. | Direct valid edit, giữ lịch sử, không mất ảnh cuối public, staging private/replay-safe. US4. |
| V-06 | Quantity edit đồng thời new hold/settlement/release bằng adapter FE-08 thật. | Compare/save cùng guard transaction, quantity không dưới valid held; một settlement trừ đúng một lần, không oversell. FR-019/024/026. |
| V-07 | Ngừng bán size, ẩn rồi public lại, xóa mọi trạng thái và race new hold/approve/apply. | Khả năng mua đúng; không restore/new hold sau deleted; old valid payment/history tiếp tục đúng điều khoản. US6. |
| V-08 | Tạo/đổi tên danh mục đồng thời, inactive tên trùng, deactivate impact, activate lại. | Unique normalized kể cả inactive, ảnh hưởng public đúng, không auto-unhide hoặc xóa history. US7. |
| V-09 | Lọc giá + Có thể mua với rẻ-ngừng-bán/đắt-đang-bán; search literal `%`/`_`, sort tie, paging. | Cùng một size thỏa predicate, không lộ proposed prices, tên case-insensitive giữ dấu, thứ tự deterministic trên cùng dữ liệu. US8. |
| V-10 | Mất toàn bộ ảnh/dependency error/empty catalogue và media khôi phục. | Safe error khác empty, không fake held=0; visibility phục hồi đúng, publication flag/history không bị đổi. US8.9/FR-036. |
| V-11 | Same command retry sau commit mất response; khác payload cùng key; mất quyền trước receipt lookup. | Không duplicate; digest conflict 409; receipt không cấp quyền hoặc lộ protected result. FR-028. |
| V-12 | Fresh/upgrade migration MySQL, rollback failure/audit/unique, baseline member/workshop regression. | Preserve dữ liệu của các migration đã có lúc triển khai, correct FK/check/index, zero changes migration cũ, real concurrency evidence. |
| V-13 | Chạy frontend React + Vite với backend REST: public search/filter/paging và mọi hành trình quản trị cốt lõi; kiểm tra loading/empty/validation/success/failure/recovery. | Journeys hoàn thành chỉ qua REST; backend không trả HTML UI; hết phiên/thu hồi trả 401 và UI xử lý theo hành vi đăng nhập lại/recovery do 001 định nghĩa. FR-038. |
| V-14 | Kiểm tra mốc 24h ảnh chưa gắn, 30 ngày sau ảnh gỡ; attach/retain đồng thời cleanup; audit/receipt dưới 5 năm. Kiểm tra Cloudinary upload timeout/retry, unsigned access và ảnh sau unpublish/delete. | Chỉ dọn object không tham chiếu, DELETE_PENDING chặn attach; retry không tạo trùng/ghi đè. Provider URL không lộ, original/derived unsigned bị từ chối; public media bị chặn khi không còn tham chiếu công khai hợp lệ, quyền đọc lịch sử kiểm tra riêng. Không xóa ảnh lịch sử hoặc purge audit/receipt trước hạn. US4.9, US5.6. |
| V-15 | Tải mẫu; tải lên file hợp lệ → xem trước → xác nhận; file có từng loại lỗi dòng/file; 500/501 dòng; xác nhận lặp/mất phản hồi; danh mục bị ngừng giữa hai bước; batch hết hạn; Guest/Member/Admin/inactive gọi mọi operation import; hai import đồng thời. | Sản phẩm nháp đúng SKU/giá đề xuất/số lượng như tạo tay, Chưa công khai, không ảnh, không tự gửi duyệt/áp dụng giá; file có lỗi hoặc xác nhận thất bại không tạo product/variant/SKU/audit một phần; báo cáo lỗi nêu đủ mọi lỗi (không cắt), đúng dòng/cột, tải sau khi hết hạn trả 409, và vô hiệu hóa ô `=`/`+`/`-`/`@`; lặp cùng key không trùng; stale/expired trả 409 đúng mã; không lưu file gốc, log không chứa nội dung ô. US9, FR-039–045, SC-008. |

Chạy V-06/V-07 có barrier điều phối hai transaction để thực sự va chạm, không chỉ gọi tuần tự. Lock order được kiểm tra cả productmanagement và source FE-08, gồm source không có hold row để bắt phantom race. Nếu adapter chưa có, ghi NOT RUN/BLOCKED, không dùng fixture-only kết quả thay integration pass.

## Evidence and recovery

Lưu kết quả mới cho từng V-ID/FR/US: revision, môi trường, MySQL version, command, fixtures không nhạy cảm, expected/actual và pass/fail/not-run. Artifact tài liệu này không phải test evidence. Không log token, password, provider key hoặc nội dung ảnh.

Đối với documentation-only planning, static checks về links, OpenAPI JSON/refs, FR traceability và placeholders là kiểm chứng phù hợp; không tạo test code chỉ để chứng minh tài liệu đã viết. Runtime/security/MySQL/real-adapter scenarios chưa chạy vì implementation/dependency chưa tồn tại; manual reading không thay các gate này.

Khi validation fail: giữ nguyên dữ liệu, kiểm tra command receipt trước retry, xác định correlation ID; không reset schema hoặc xóa giữ để ép test pass. Rollout/recovery production phải có phê duyệt riêng; plan chỉ đề xuất disable routes productmanagement và additive fix, không hard-delete/down-migrate.
