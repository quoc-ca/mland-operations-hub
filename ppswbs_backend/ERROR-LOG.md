# Backend ERROR-LOG

## BE-DESIGN-0005 — Khung ứng dụng dùng chung trên develop mâu thuẫn quyết định T102

- **Thời điểm**: 2026-10-26T03:00:26+07:00
- **Reporter**: Claude (agent triển khai, làm theo yêu cầu của Đặng Thị Khương)
- **Feature/task**: 002-product-management, T102 (khung ứng dụng dùng chung)
- **Trạng thái**: BLOCKED

**Phạm vi bị dừng và nguồn design.** Hoàn tất T102 ([tasks.md](../ppswbs-spec/features/002-product-management/tasks.md) T102; [blocker-report](../ppswbs-spec/features/002-product-management/blocker-report.md) mục "Implementation baseline — T001"). T102 ghi: nếu feature khác đã tạo khung thì dùng lại, không tạo bản thứ hai. Khung do 001 tạo đã vào `develop` ở commit `907d135` và được merge vào `khuong` ở commit `46c3075`.

**Khuyết/mâu thuẫn.**
1. `configuration/SecurityConfiguration.java` (001) cho mọi request `permitAll()` và tắt CSRF/CORS toàn cục. Quyết định T102 (Đặng Thị Khương, 2026-10-26) là route chưa khai báo phải bị từ chối. Constitution III/AGENT §4 cấm tắt CORS/CSRF toàn cục; tasks 002 T007 cấm fallback `permitAll`.
2. `src/main/resources/application.properties` và `src/test/resources/application-test.properties` có giá trị mặc định cho username `root` và một mật khẩu DB viết thẳng trong file đã commit. Constitution II cấm secret trong file commit; T102 yêu cầu không có giá trị mặc định chứa secret và không tự rơi về DB khi thiếu cấu hình.
3. Quy ước tên migration: 001 dùng `V5__account_management.sql` (số tăng dần). 002 đã được cấp `V20261026_1000__catalogue_schema.sql` (theo ngày giờ, chốt 2026-10-26). Nếu migration 002 chạy trước, mọi migration số nhỏ hơn (ví dụ `V6__…`) tạo sau đó sẽ bị Flyway từ chối vì nằm ngoài thứ tự.

**Bằng chứng.**
- Fact: đọc trực tiếp hai file cấu hình trên sau merge; `git log` cho thấy `907d135 Add code for feat 001`.
- Fact: khi `DefaultSecurityConfiguration` (deny-all, của 002) và `SecurityConfiguration` (permitAll, của 001) cùng tồn tại, cả hai chain đều khớp mọi request; Spring Security không cho hai chain như vậy cùng tồn tại khi khởi động. Chưa khởi động thử vì biến `JDBC_*` chưa có trên máy.
- Fact: `.\mvnw.cmd -q clean test "-Dtest=ModulithArchitectureTests,CatalogueValuesTests,CatalogueExceptionHandlerTests"` trên JDK 21: 49 test, 0 lỗi (sau khi gỡ `DefaultSecurityConfiguration`).
- Unknown: 001 có chủ ý dùng `permitAll` tạm thời hay không.

**Vì sao không thể tự quyết.** Sửa `SecurityConfiguration`, file cấu hình và quy ước migration là thay đổi cấu hình chung do 001 sở hữu, ảnh hưởng mọi feature. Giữ cả hai chain thì app không khởi động; giữ `permitAll` thì vi phạm quyết định T102 và constitution.

**Hiện trạng file.** Đã gỡ `configuration/DefaultSecurityConfiguration.java` khỏi source (bản sao lưu nằm ở scratchpad của phiên, không commit). Giữ nguyên các file của 001. Code 002 giữ nguyên: `common/` (ErrorResponse, CorrelationIdFilter), module `features/productmanagement` (package-info, facade/package-info, CatalogueValues, CatalogueErrorCode, CatalogueException, CatalogueExceptionHandler, CatalogueStrictJsonConverter, CatalogueWebConfiguration). Đã đổi package-info của productmanagement sang cách đặt tên module của 001 (bỏ `id`, tên logic `features.productmanagement`). T102 chưa tick.

**Cần chốt để tiếp tục** (người chốt: Phan Sơn, owner 001, cùng nhóm):
- Thay `permitAll` toàn cục bằng fallback từ chối, mỗi feature tự khai báo chain cho route của mình; bật lại CSRF/CORS theo origin.
- Bỏ giá trị mặc định chứa user/mật khẩu trong file cấu hình đã commit (và đổi mật khẩu DB nếu đó là mật khẩu thật).
- Chọn một quy ước tên migration chung cho mọi feature.

**Diễn biến 2026-10-26T03:17+07:00 — chạy thử app với MySQL 8.4.13 (database `ppswbs_test` trống), JDK 21, commit `46c3075` + code 002 chưa commit.**
- App khởi động thành công trong khoảng 17 giây; Flyway tạo `flyway_schema_history` và áp dụng `V5__account_management`.
- Gọi `GET /api/v1/products`, `GET /api/v1/catalogue-management/products` và một route không tồn tại đều trả 404 (chưa có controller). Với fallback từ chối theo quyết định T102 thì các route chưa khai báo phải bị từ chối (403); kết quả 404 xác nhận cấu hình hiện tại đang cho qua mọi request.
- Log khởi động in ra `generated security password: …` (Spring Boot tự tạo user mặc định vì khung 001 không tắt `UserDetailsServiceAutoConfiguration`). Constitution II cấm mật khẩu xuất hiện trong log. Bổ sung vào danh sách cần chốt: tắt user mặc định này.
- Cảnh báo khác (không chặn): Flyway báo MySQL 8.4 mới hơn phiên bản Flyway đã kiểm thử; Thymeleaf không tìm thấy `templates/`; khai báo `hibernate.dialect` thừa.
- Đã dừng app sau khi kiểm tra. Trạng thái vẫn `BLOCKED`.

## BE-DESIGN-0006 — AccountFacade của 001 chưa đủ cho T007 của 002

- **Thời điểm**: 2026-10-26T03:00:26+07:00
- **Reporter**: Claude (agent triển khai, làm theo yêu cầu của Đặng Thị Khương)
- **Feature/task**: 002-product-management, T002/T007 (tích hợp actor/capability)
- **Trạng thái**: BLOCKED (T007 chưa bắt đầu)

**Nguồn design.** 002 [interfaces.md](../ppswbs-spec/features/002-product-management/contracts/interfaces.md) mục security: identity owner cung cấp qua facade một actor gồm actorId, verified identity reference, accountActive và capability hiện hành; mọi REST dùng bearer đã xác minh, kiểm tra thu hồi/trạng thái mỗi request. 001 spec FR-026.

**Khuyết.**
1. `features/accountmanagement/facade/AccountFacade` chỉ có `resolveAccount(firebaseUid)`, `getAccountStatus`, `getLocationScope`. Không có thao tác xác minh bearer/thu hồi; `TokenVerificationService` nằm trong `service/` (nội bộ của 001) nên 002 không được gọi.
2. Gói `facade` của 001 không khai báo `@NamedInterface`, và `AccountSummary` dùng `entity.enums.AccountRole/AccountStatus`. Theo Spring Modulith, module khác dùng các kiểu này sẽ vi phạm ranh giới (constitution I: chỉ facade và boundary types được công khai).
3. Chưa có security filter nào đặt danh tính đã xác minh vào request cho các feature khác dùng.

**Vì sao không thể tự quyết.** 002 không được tự xác minh Firebase token hoặc import service/entity của 001 (tasks T007, AGENT §4). Tự thêm hàm vào facade 001 là sửa contract của module khác.

**Hiện trạng.** Không có file nào của T007 được tạo. Code 002 hiện tại không phụ thuộc 001.

**Cần chốt để tiếp tục** (Phan Sơn): facade 001 cung cấp (a) xác minh bearer + thu hồi, trả UID/accountId, role, status, emailVerified, locationIds dưới dạng boundary type không thuộc `entity`; hoặc (b) một security filter của 001 đặt actor đã xác minh vào request; và đánh dấu gói `facade` là `@NamedInterface`. Sau đó 002 làm T007 bằng adapter nối facade này.

**Diễn biến BE-DESIGN-0005 — 2026-10-26 (quy ước tên migration).** Đặng Thị Khương chốt quy ước cho 002: số tăng dần, báo số với nhóm trước khi tạo file, đặt tên theo feature như 001. 002 dùng `V6__product_management.sql`; Flyway đã áp dụng V6 sau V5 trên MySQL 8.4.13 (`CatalogueMySqlSchemaIT`). Mục 3 của bản ghi này đã có quyết định cho 002; quy ước chung cho cả nhóm vẫn cần các owner khác đồng ý. Mục 1 (security cho mọi request đi qua) và mục 2 (mật khẩu mặc định trong file cấu hình) vẫn `BLOCKED`.

**Diễn biến BE-DESIGN-0005 — 2026-10-26T12:50:57+07:00 — feature 006 (loyalty-promotion) phụ thuộc mục 1, 2 và quy ước migration.**
- Reporter: Claude (agent, theo yêu cầu của Đặng Thị Khương, owner 006). Không có thay đổi code nào trong lượt này.
- Fact: đọc lại `configuration/SecurityConfiguration.java` (CSRF và CORS bị tắt, `/api/v1/**` và `anyRequest()` đều `permitAll`) và `src/main/resources/application.properties` (mặc định `JDBC_USER=root` và có mật khẩu DB viết thẳng) — vẫn như mô tả ở trên. Thư mục migration hiện có `V5__account_management.sql` và `V6__product_management.sql`.
- Ảnh hưởng 006: T007 (`LoyaltySecurityConfiguration`) và T017 trở đi cần security route-scoped và fallback từ chối; T016 cần số migration. Nhóm task không đụng cấu hình chung (T001–T006, T008–T014, T019–T020) không bị chặn.
- Đề xuất 006 gửi owner 001 (Phan Sơn), chưa phải quyết định: (1) thêm chain fallback `anyRequest().denyAll()` ở thứ tự thấp nhất, mỗi feature khai báo chain riêng bằng `securityMatcher` cho route của mình; (2) CORS theo origin frontend, không dùng `*`; CSRF chỉ tắt cho route bearer và ghi lý do; (3) loại `UserDetailsServiceAutoConfiguration` để log không in mật khẩu sinh tự động; (4) bỏ mặc định `JDBC_USER` và mật khẩu DB, thiếu cấu hình thì không khởi động; đổi mật khẩu nếu là mật khẩu thật.
- Đề xuất migration của 006: `V7__loyalty_promotion.sql` (một file cho 10 bảng kèm seed registry và policy 10000/100/10/10), chỉ giữ nếu nhóm chưa cấp số 7 cho feature khác; cần báo nhóm trước khi tạo file ở T016.
- `AccountFacade` của 001 (BE-DESIGN-0006): Đặng Thị Khương xác nhận chờ 001 bổ sung sau.
- Trạng thái mục 1 và 2 vẫn `BLOCKED`; điều kiện tiếp tục với 006: owner 001 dựng fallback từ chối và xử lý mật khẩu mặc định, hoặc chốt cách khác bằng văn bản.

**Diễn biến BE-DESIGN-0005 — 2026-10-26T13:05+07:00 — số migration của 006.**
- Reporter: Claude (agent), ghi theo xác nhận của Đặng Thị Khương (owner 006): 006 dùng `V7__loyalty_promotion.sql`.
- Fact: `ppswbs_backend/src/main/resources/db/migration/` có `V5__account_management.sql` (001) và `V6__product_management.sql` (002, chưa commit); `origin/develop` chỉ có V5. Chưa có file V7 ở cả hai nơi. Chưa tạo file V7.
- Giới hạn: xác nhận này đến từ owner 006; chưa có bằng chứng các owner khác (003, 004…) đã ghi nhận số 7. Nếu feature khác cũng cần số 7 trước khi 006 tạo file ở T016, báo lại để đổi số trước khi tạo (không đổi tên file đã áp dụng).
- Mục 3 (quy ước tên migration) coi như đã có quyết định cho 001, 002 và 006; mục 1 và 2 vẫn `BLOCKED`.

**Diễn biến BE-DESIGN-0005 và BE-DESIGN-0006 — 2026-10-27 — đổi mã bản ghi và số migration trước khi ghép với `develop`.**
- Reporter: Claude (agent), theo yêu cầu của Đặng Thị Khương.
- Đổi mã: hai bản ghi này trước đây mang mã `BE-DESIGN-0001` (khung ứng dụng) và `BE-DESIGN-0002` (AccountFacade) trên nhánh `khuong`. `origin/develop` (`07fa8cf`) đã có `ERROR-LOG.md` riêng với `BE-DESIGN-0001`–`BE-DESIGN-0004` (feature 003.2), nên hai bản ghi này đổi thành `BE-DESIGN-0005` và `BE-DESIGN-0006` để không trùng mã. Nội dung các diễn biến cũ giữ nguyên.
- Đổi số migration: `origin/develop` đã dùng `V6__account_schema_alignment.sql` (001), `V7__workshop_operations.sql` và `V8__workshop_operations_idempotency_ledger.sql` (003.2). Các file chưa commit/chưa chia sẻ của nhánh `khuong` đổi số: `V6__product_management.sql` → `V9__product_management.sql` (002), `V7__loyalty_promotion.sql` → `V10__loyalty_promotion.sql`, `V8__loyalty_drop_workshop_payable.sql` → `V11__loyalty_drop_workshop_payable.sql` (006). Nội dung SQL không đổi (chỉ dòng chú thích đầu file). Cần báo nhóm (Phan Sơn, Hồ Hoàng Quốc) số V9–V11 đã được 002/006 dùng. Database test `ppswbs_test` đã áp dụng các số cũ nên phải tạo lại sau khi ghép.
- Fact trên `origin/develop` (`07fa8cf`, chưa merge vào `khuong`, sẽ kiểm chứng lại sau khi merge): `SecurityConfiguration` không còn `permitAll` (`/api/**` cần xác thực, route khác `denyAll`, CORS theo origin); `application.properties` không còn giá trị mặc định cho user/mật khẩu DB; `AccountFacade` có `resolveActor`, `CurrentAccountActor`, `ActorContext`, gói `facade` là `@NamedInterface`, `ActorRole`/`ActorStatus` là enum của facade; `IdentityGateFilter` xác minh bearer cho mọi `/api/**`. Nếu đúng sau khi merge: mục 1, 2 của BE-DESIGN-0005 và cả BE-DESIGN-0006 được giải quyết; còn lại cho T102 là `ddl-auto=validate` và tắt Thymeleaf. Điểm mới cần xử lý ở T007: `IdentityGateFilter` đòi bearer cho cả route catalogue công khai của 002 (khách không đăng nhập).
