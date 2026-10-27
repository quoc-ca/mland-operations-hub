# Research: Product Management

**Date created**: 2026-10-24
**Date updated**: 2026-10-26 (V1 không MFA theo 001; tên facade identity; tham chiếu 001 mới; T004 SDK Cloudinary, T092 Apache POI)
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Scope**: Phase 0 của speckit-plan cho [spec](spec.md), công việc tài liệu; không thay thế quyết định nghiệp vụ hoặc phê duyệt triển khai.

## Evidence from the repository

| Source | Observed fact |
| --- | --- |
| [Backend pom](../../../ppswbs_backend/pom.xml) | Java 21, Spring Boot 3.4.1, Modulith 1.3.1, Firebase Admin 9.11.0; JPA, Flyway, Thymeleaf (còn trong pom, không dùng cho UI theo constitution 3.0.0), Validation, Spring Security, JUnit/MockMvc có sẵn. |
| Backend source | Toàn bộ `ppswbs_backend/src` đã bị xóa ở commit `c47fefe` (ho-quoc, 2026-10-24). Hiện không có module, facade, security configuration, Firebase token verifier, migration hay test nào để kế thừa; backend chỉ còn pom, Maven wrapper và AGENT. Module, cấu hình bảo mật, migration và cấu hình test của FE-02 phải tạo mới; facade identity do FE-01 tạo, nguồn giữ hàng do FE-08 tạo. |
| [001 spec](../001-account-management/spec.md), [001 plan](../001-account-management/plan.md), [001 blockers](../001-account-management/blocker-report.md) | Member API dùng bearer; web dùng Firebase session cookie, revocation/trạng thái và CSRF. Scope chỉ Member, không cấp quyền nội bộ/MFA; còn là thiết kế, không phải khả năng đã có. MySQL 8.4 được chọn cho kiểm chứng. |
| [DB working document](../../DB.md) | Working document chưa đồng bộ quyết định vai trò mới. Ngày 2026-10-24 người dùng chốt MEMBER/STAFF/MANAGER/ADMIN, Manager riêng với OWNER; không tự sửa DB working document từ FE-02. Quy tắc cấm tự review chung không ghi đè Manager tự duyệt ở FE-02. |

Khảo sát không tìm thấy adapter lưu ảnh catalogue, cấu hình provider ảnh hoặc retail hold source. Không đọc credential để suy đoán provider. Hai nhánh nghiên cứu độc lập đã rà identity/security và data/availability; kết quả được đối chiếu với mã nguồn trên.

## Decisions for the draft design

### D-01 — Reuse the baseline without upgrading it

- **Decision**: Giữ các version trong pom; chọn MySQL 8.4/InnoDB cho kiểm chứng feature. Dùng JPA, Flyway và module productmanagement hiện có, không thêm framework/cache/search engine.
- **Rationale**: Tuân constitution, tránh lấy H2 làm bằng chứng tính đúng đồng thời.
- **Alternatives considered**: Nâng stack theo tài liệu mới, thêm database khác, dùng H2 cho toàn bộ nghiệm thu — loại vì không có yêu cầu và không chứng minh được các invariant cần thiết.

### D-02 — Identity-owned authorization, never inferred roles

- **Decision**: Catalogue nhận actor an toàn và kết quả capability qua facade identity của 001 (tên module do 001 chốt); xác thực Firebase bearer cho mọi REST API (frontend React dùng cùng bearer), kiểm tra thu hồi và trạng thái mỗi request theo 001. Thiết kế có danh sách capability và đường từ chối trong [contracts](contracts/interfaces.md). Tạo security chains đúng phạm vi, trước fallback hiện có, không đổi hành vi route cũ.
- **Rationale**: Không biến Firebase claims/ROLE_MEMBER thành quyền Staff/Manager; không nhập chính sách hồ sơ Member vào quản trị productmanagement.
- **Alternatives considered**: Gán Manager = OWNER, tự tạo RBAC trong productmanagement, sao chép phiên 001 như đã triển khai — đều bị loại.
- **Owner decision recorded 2026-10-24**: Người dùng đồng ý các mã MEMBER/STAFF/MANAGER/ADMIN, Manager là vai trò riêng, Admin tạo/cấp quyền nội bộ; đăng ký khách không tự nhận quyền. Không cần hỏi lại role-code mapping này. Model tài khoản và capability facade vẫn cần triển khai ở identity owner.
- **G-01 decision recorded 2026-10-24**: Người dùng đồng ý dùng chung cơ chế xác thực theo FE-01/001 và kiểm tra thêm quyền Staff/Manager từng action. MFA đặc quyền/recovery do identity owner thiết kế chung; chưa chọn phương thức cụ thể. G-01 đóng về hướng tích hợp/phiên của FE-02, còn dependency thiết kế MFA và triển khai facade/phiên. Khi chưa có boundary đáp ứng yêu cầu, management route từ chối an toàn.

### D-02a — Compatibility with FE-01 and Account Management 001

- **Observed FE-01**: Firebase xử lí email/password hoặc Google/Facebook, frontend gửi ID token, backend xác minh UID/revocation/trạng thái; sau thành công về `/`. Thiếu Member thì luồng khách tự provision. Chưa mô tả đăng nhập nội bộ, MFA, session duration hoặc quản trị/cấp role.
- **Observed 001**: [Spec 001 FR-001/010–016](../001-account-management/spec.md) chi tiết hóa bearer ID token có kiểm tra revocation cho API; web dùng Firebase session cookie, HttpOnly/Secure/SameSite=Lax/Path=/, CSRF và kiểm tra revocation/trạng thái trên request được bảo vệ. Session mới đòi auth_time không quá 5 phút (tolerance tương lai 30 giây); session hợp lệ không buộc đăng nhập/xác minh email lại mỗi lần. Thành công về `/`; không thêm policy gate cho profile. Đây là thiết kế đã thống nhất cho 001 (hiện còn theo mô hình web cookie/SSR), chưa phải code dùng được. FE-02 chỉ gọi REST nên dùng phần bearer (001 FR-001); contract xác thực cho React do 001 chốt.
- **Confirmed shared boundary, 2026-10-24**: Người dùng đã đồng ý kế thừa các primitive xác minh UID/token và revocation qua identity owner; giữ hành trình Member của người cùng nhóm. Catalogue kiểm tra actor active và capability nội bộ đã được Admin cấp trước mỗi action. Member tự provision hoặc trạng thái PENDING_POLICY_ACCEPTANCE của 001 không tự cấp quyền productmanagement. Admin không tự có quyền duyệt sản phẩm thay Manager.
- **Scope protection**: `/member-auth/profile-session`, cookie `__Host-mland_profile` và Member allowlist là contract riêng của 001. Không dùng chúng làm bằng chứng có phiên đặc quyền và không âm thầm thay đổi bootstrap/logout/security chains của 001. Identity owner quyết định contract dùng chung hoặc mở rộng có kiểm soát; FE-02 tiêu thụ facade và các primitive đã thống nhất, không sở hữu màn hình đăng nhập mới.
- **Session policy**: Mọi REST API dùng bearer đã xác minh theo 001 FR-001; hành vi hết phiên, đăng nhập lại và recovery do 001 định nghĩa. FE-02 không tự đặt cơ chế phiên riêng và không tuyên bố spec 001 đã tự cấp quyền nội bộ.
- **Identity-owned MFA dependency**: [General spec](../../spec-general.md) yêu cầu MFA cho tài khoản đặc quyền nhưng 001 không mô tả MFA; kiểm tra token/email verified không phải MFA. Người dùng đồng ý để MFA/recovery ở identity/FE-01; phương thức cụ thể, enrollment/recovery và contract bằng chứng MFA còn cần thiết kế tại owner đó trước bật quản trị. TOTP Authenticator chưa được chọn. FE-02 chỉ tiêu thụ kết quả xác thực đặc quyền và từ chối khi chưa đáp ứng, không thêm MFA vào luồng Member hoặc bỏ requirement vì dependency chưa có. **Cập nhật 2026-10-26**: người dùng chốt V1 không yêu cầu MFA cho vai trò nội bộ, theo 001 (Assumptions); spec-general.md dòng 51/190 cần cập nhật tương ứng ở cấp dự án.
- **Boundary**: Không sửa file 001, nâng Firebase plan/Identity Platform, sửa auth code hoặc mở rộng ngoại lệ xác thực từ bước cập nhật tài liệu này. Chính sách dùng chung/phiên nội bộ đã chốt; phương thức MFA của dependency FE-01 chưa được chọn.

### D-03 — Availability SPI preserves the existing dependency direction

- **Decision**: Productmanagement sở hữu AvailabilityGuardPort trong productmanagement::facade; adapter của readyringsales triển khai interface đó. Catalogue sở hữu remainingUnsold, readyringsales sở hữu hold và vòng đời hold. Không import entity/repository của nhau, không thêm productmanagement → readyringsales.
- **Rationale**: readyringsales (FE-08) phụ thuộc productmanagement::facade; gọi ngược facade sẽ tạo cycle. SPI có batch snapshot và guarded callback trong cùng transaction để giữ khóa nguồn xuyên suốt cập nhật số lượng.
- **Alternatives considered**: Đọc tổng giữ rồi lưu ngoài khóa, mặc định tổng giữ bằng 0, cache lượng giữ, sao chép lifecycle hold vào productmanagement — đều không bảo đảm spec.
- **Dependency gate G-02**: FE-08 phải cung cấp adapter thật và quy trình tạo/chốt/giải phóng giữ theo cùng lock order. Fixture chỉ dùng kiểm thử có kiểm soát, không phải fallback production. Đọc khả dụng/cập nhật lượng không xác minh được nguồn trả 503 AVAILABILITY_DEPENDENCY_UNAVAILABLE.

### D-04 — Locks plus versions protect distinct business facts

- **Decision**: JPA PESSIMISTIC_WRITE trên category → product → variant theo ID tăng; sau đó khóa nguồn giữ bằng SPI. Có @Version cho stale edit và contentVersion riêng cho hồ sơ. Không giữ khóa trong lúc gọi provider ảnh hoặc chờ người duyệt. FE-08 phải vào sale gate của productmanagement trước nguồn giữ; không khóa theo chiều ngược lại.
- **Rationale**: Khóa hàng cần tồn tại kể cả chưa có hold; adapter FE-08 phải có guard ổn định theo variant để tránh phantom insert. Cập nhật số lượng không tự làm mất duyệt nội dung/giá; thay content/ảnh/giá trước lần công khai đầu theo contentVersion và proposal revision.
- **Alternatives considered**: Optimistic locking đơn lẻ không bảo vệ tổng giữ; khóa product sau khi đã khóa hold có thể deadlock. Không dùng raw SQL trong application code.
- **Evidence**: [Spring Data JPA locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html) mô tả khai báo lock mode tại repository; [MySQL locking reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html) mô tả khóa đến cuối transaction. Dùng nguyên tắc này với dependency đang pin, không suy ra quyền nâng version từ trang tài liệu hiện hành.

### D-05 — Persist reviews and prices independently

- **Decision**: Review snapshot và price proposal revision bất biến; trạng thái/hiệu lực riêng. Tối đa một pending proposal cho variant được bảo vệ bằng con trỏ pendingProposalId, khóa variant và unique constraint. Áp dụng giá kiểm tra revision hiện hành, approved amount và baseAppliedSequence; tăng sequence khi áp dụng để giá cũ không ghi đè giá mới.
- **Rationale**: Thực hiện hai câu trả lời clarify, cho phép Manager tự duyệt, giữ lịch sử và không gộp duyệt với công khai. Sản phẩm mới duyệt toàn bộ hồ sơ/giá trong một transaction, không để duyệt một phần.
- **Alternatives considered**: Chỉ lưu một cột price hoặc overwrite proposal cũ — không phân biệt được proposed/approved/applied và mất evidence.

### D-06 — Media is staged outside productmanagement mutations

- **Decision**: Người dùng xác nhận Cloudinary ngày 2026-10-24. MediaStoragePort giữ interface độc lập provider; adapter Cloudinary thực hiện upload có chữ ký từ backend sau kiểm tra dữ liệu, trước transaction gắn ảnh. Chỉ metadata READY và thuộc đúng actor/command được tham chiếu; failure không đổi sản phẩm.
- **Rationale**: Database transaction không rollback được remote upload; object chưa gắn phải không công khai. Không gọi provider dưới khóa sản phẩm. Public media endpoint chỉ phục vụ ảnh còn được phép xem công khai; FE-08 cần quyền riêng khi đọc ảnh lịch sử.
- **Alternatives considered**: URL client tùy ý, gọi upload trong transaction dài, dùng public bucket cho mọi ảnh, xóa object khi bỏ tham chiếu — bị loại.
- **Owner decisions recorded 2026-10-24**: Ảnh chưa gắn đủ 24 giờ được dọn nếu không còn dùng; ảnh đã gỡ đủ 30 ngày chỉ dọn khi không còn sản phẩm/hồ sơ/đơn/lịch sử tham chiếu; giữ ảnh lịch sử theo owning record. Cleanup kiểm tra lại tham chiếu, đánh dấu DELETE_PENDING trước remote delete, không tự xóa ảnh chỉ vì product soft-delete. G-04 đã đóng về chính sách, còn task triển khai/kiểm chứng cleanup.
- **Cloudinary adapter proposal**: Upload `resource_type=image`, `type=authenticated`, deterministic `public_id` từ media/command và `overwrite=false`; giữ checksum riêng để đối chiếu retry, không coi provider tự bảo đảm idempotency. Lưu asset_id/public_id/version/format để đọc và xóa đúng object. Backend giữ cloud_name/api_key/api_secret trong môi trường; không đưa secret/URL có chữ ký ra client/log. Pin Java SDK sau kiểm tra tương thích Java 21/pom hiện tại, không nâng stack.
- **Delivery proposal**: Object luôn ở authenticated kể cả sau publish. Backend mở stream bằng URL ký chỉ dùng nội bộ, trả bytes qua endpoint cùng origin sau kiểm tra quyền hiện tại, không redirect hoặc lộ URL Cloudinary; response no-store. Không coi delivery signature là URL tự hết hạn. Không dùng `private` đơn thuần vì derived image có thể public mặc định. Cleanup gọi signed destroy với đúng public_id/resource_type/type, chỉ sau reference guard và retention; missing là thành công, lỗi tạm retry.
- **Evidence**: [Java upload](https://cloudinary.com/documentation/java_image_and_video_upload), [authenticated delivery](https://cloudinary.com/documentation/control_access_to_media#authenticated_media_assets), [upload/destroy reference](https://cloudinary.com/documentation/image_upload_api_reference). Đây là lựa chọn adapter để review từ tài liệu chính thức, chưa có kết nối account hoặc kiểm chứng runtime.
- **G-03 provider decision closed**: Không còn câu hỏi chọn provider. Cloudinary adapter, namespace cấu hình, SDK pin và thử nghiệm quyền đọc/xóa là dependency triển khai; không kích hoạt billing hoặc tạo tài nguyên từ tác vụ này.
- **SDK pin (T004, Đặng Thị Khương duyệt 2026-10-26)**: dùng `com.cloudinary:cloudinary-http5:2.4.0` (phát hành 2026-06-01; kéo theo `cloudinary-core:2.4.0`). Bản này khai báo `httpclient5 5.3.1` và `httpcore5 5.3.5`; Spring Boot 3.4.1 đang quản lý `httpclient5 5.4.1` và `httpcore5 5.3.1`, nên Maven sẽ dùng bản của Boot: cùng dòng 5.3 với httpcore5 và mới hơn một bản nhỏ với httpclient5, không phải đổi phiên bản của Boot. Không chọn bản mới nhất `2.5.0` (2026-09-28) vì nó cần `httpclient5 5.6.4`/`httpcore5 5.4.3`; Boot sẽ hạ xuống 5.4.1/5.3.1 và có nguy cơ lỗi lúc chạy, còn ép bản mới là đổi phiên bản do Boot quản lý (đổi stack). Không dùng module `cloudinary-http44` (Apache HttpClient 4 đã ngừng phát triển). Lưu ý: SDK khai báo `commons-lang3 3.18.0`, Boot ghim `3.17.0`; phải kiểm chứng bằng build và test thật T051. Nguồn: `maven-metadata.xml` và POM của `com.cloudinary:cloudinary-http5` trên Maven Central; `spring-boot-dependencies-3.4.1.pom`.
- **Cấu hình môi trường (T004)**: `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, đọc qua biến môi trường/secret store của môi trường chạy; không có giá trị mặc định trong file đã commit, không ghi vào log, thiếu thì adapter trả `MEDIA_DEPENDENCY_UNAVAILABLE`. Chưa tạo tài khoản, chưa bật billing, chưa thêm dependency vào `pom.xml`.

### D-07 — Exact data, deterministic search

- **Decision**: Giá/số lượng dùng BigInteger và DECIMAL(19,0), JSON decimal string tối đa 19 chữ số, không exponent/fraction; đây là miền biểu diễn kỹ thuật, không phải trần kinh doanh đã chốt. ID dạng decimal string của positive BIGINT. Tên/mô tả/alt text đo Unicode code point sau trim Unicode đầu/cuối. SKU ASCII, mã sản phẩm pad tối thiểu 6, size 2 chữ số.
- **Decision**: Tên danh mục có normalized key: NFC → trim Unicode → lowercase Locale.ROOT; so unique bằng byte, giữ dấu tiếng Việt, không bỏ dấu. Không tự chọn collation accent-insensitive làm thay đổi rule. Search áp dụng cùng case normalization và escape wildcard để substring literal; cùng một variant phải thỏa giá/khả năng mua. Query hai bước lấy ordered product IDs rồi fetch variant/media để tránh phân trang trên collection join.
- **Rationale**: Tránh mất số nguyên trên client và xác định biên nhập liệu. A-001/A-002 đã được người dùng đồng ý ngày 2026-10-24; miền số/cách biểu diễn là thiết kế kỹ thuật đề xuất, không tự đặt trần giá kinh doanh.
- **Alternatives considered**: Floating point, JavaScript number cho mọi amount/ID, LIKE không escape, collection join pagination — bị loại.

### D-08 — Catalogue-owned audit and command receipts

- **Decision**: Audit nghiệp vụ productmanagement lưu cùng transaction với thay đổi và không phụ thuộc bảng audit authentication hiện tại. Receipt theo actor + command UUID + action giữ request digest và kết quả/resource/version tối thiểu, không lưu token hoặc full upload. Same key/same digest trả kết quả đã xác nhận sau khi kiểm tra quyền hiện tại; khác digest trả 409. Business reject được ghi bằng transaction tách sau rollback nếu audit còn hoạt động; lỗi audit không cho phép mutation thành công một phần.
- **Rationale**: Các actor nghiệp vụ chưa map sang member_id nên không tái sử dụng sai FK audit authentication. Receipt giải quyết mất phản hồi, không suy đoán trạng thái.
- **Retention confirmed**: Business audit giữ 5 năm từ sự kiện theo Report 3; kết quả thao tác tối thiểu giữ 5 năm từ commit theo câu trả lời người dùng ngày 2026-10-24. Không giữ full request/token. Bản ghi security authentication nằm ngoài phạm vi và theo identity owner.
- **Alternatives considered**: Ghi audit trong common business workflow, request log chứa payload/token, retry tự động mọi lỗi — bị loại.

### D-09 — Additive migration and compatibility

- **Decision**: Tạo migration mới cho các bảng `catalogue_*` sau khi kiểm tra high-water mark lúc triển khai (hiện chưa có migration nào vì `ppswbs_backend/src` đã bị xóa); không dành sẵn số version trong plan, không sửa migration đã áp dụng của feature khác, không seed role/provider hoặc reset dữ liệu. Kiểm chứng fresh MySQL và upgrade trên schema có dữ liệu của các feature khác đã có lúc triển khai.
- **Rationale**: Feature 001 có thể tạo migration trước khi 002 được triển khai. Soft-delete giữ toàn bộ identity/FK; rollback triển khai ưu tiên disable routes và migration sửa tiếp, không drop dữ liệu mới.

### D-10 — Excel import is a two-step, all-or-nothing alternative to create

- **Decision**: Người dùng chốt ngày 2026-10-24: import `.xlsx` theo mẫu, ≤ 5.000.000 bytes và 500 dòng, sheet đầu, không ảnh, tất cả-hoặc-không, chỉ tạo mới. Thiết kế: bước kiểm tra không tạo dữ liệu và lưu batch ngắn hạn (payload đã chuẩn hóa, hết hạn 60 phút — mặc định kỹ thuật) rồi bước xác nhận tái kiểm tra và tạo qua đúng `CreateProductService` trong một transaction. Không lưu file gốc; metadata/kết quả giữ 5 năm. Thư viện OOXML (đề xuất Apache POI) dùng streaming read, giới hạn zip-bomb, tắt XXE, không thực thi công thức.
- **Rationale**: Xem trước + báo cáo lỗi giúp sửa file lớn trước khi tạo; tái kiểm tra khi xác nhận giữ đúng FR-026 (danh mục có thể đổi giữa hai bước). Dùng lại service tạo tay để SKU, giá, số lượng, audit và receipt giống nhau, tránh đường ghi thứ hai.
- **Alternatives considered**: Tạo ngay khi tải lên (không có xem trước, khó rollback ở cấp UI) — loại; stateless confirm bằng tải lại cùng file (không cần bảng batch nhưng tốn băng thông và khó chứng minh "đúng file đã xem") — loại; import từng phần hợp lệ (partial) — trái FR-042; lưu file gốc trên Cloudinary/đĩa — không cần và thêm rủi ro dữ liệu.
- **Library pin (T092, Đặng Thị Khương duyệt 2026-10-26; đã so sánh với `org.dhatim:fastexcel`/`fastexcel-reader` 0.20.2 và không chọn vì reader không có API cho ô gộp, hyperlink, giới hạn zip-bomb và không nêu rõ chặn XXE, trong khi contract import bắt buộc các kiểm tra này)**: dùng `org.apache.poi:poi-ooxml:5.5.1` (phát hành 2025-11-30). Không chọn `6.0.0` vì mới phát hành 2026-10-22, nâng Java tối thiểu lên 17 và gỡ một số API cũ; 5.5.1 đã ổn định gần một năm. Dependency bắt buộc: `poi 5.5.1`, `poi-ooxml-lite 5.5.1`, `xmlbeans 5.3.0`, `commons-compress 1.28.0`, `commons-io 2.21.0`, `curvesapi 1.08`, `commons-collections4 4.5.0`, `log4j-api 2.24.3` (Boot 3.4.1 cũng ghim log4j2 2.24.3); các dependency chữ ký số/PDF/SVG là optional, không bị kéo theo. Cách dùng đề xuất: đọc bằng event API (`XSSFReader` + SAX, chỉ sheet đầu), không dùng `XSSFWorkbook` để đọc file tải lên; đặt giới hạn zip-bomb qua `ZipSecureFile` (`setMinInflateRatio`, `setMaxEntrySize`, `setMaxTextSize`; giá trị đã đặt trong `ExcelLibraryConfiguration` ngày 2026-10-26: tỷ lệ nén tối thiểu 0,01, entry giải nén tối đa 64 MiB, text tối đa 10.485.760 ký tự, dư nhiều so với file hợp lệ ≤ 5.000.000 bytes và ≤ 500 dòng); parser XML của POI đã chặn XXE mặc định qua `XMLHelper`, vẫn có test riêng. Ghi file mẫu/báo cáo lỗi bằng `XSSFWorkbook`/`SXSSFWorkbook` sinh mỗi request, vô hiệu hóa ô bắt đầu bằng `=`, `+`, `-`, `@`. Nguồn: `maven-metadata.xml` và POM `poi-ooxml-5.5.1` trên Maven Central; trang changes của Apache POI.

## Phase 0 outcome and remaining gates

Ngày 2026-10-24 đã chốt role codes/provisioning, dùng chung xác thực theo 001, Cloudinary, retention và các mặc định. G-01 đóng về hướng tích hợp/phiên FE-02; phương thức MFA/recovery vẫn là dependency thiết kế identity/FE-01 chưa được chọn. G-02 là dependency code/kiểm chứng FE-08, không yêu cầu người dùng quyết định lại quy tắc giữ hàng. G-03/G-04 đóng về quyết định. Plan có thể sắp thứ tự dependency; chưa được coi thiết kế MFA chung hoặc integration đã hoàn thành. Không code, migration hoặc deploy trong tác vụ cập nhật này.
