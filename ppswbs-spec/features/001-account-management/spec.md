# Feature Specification: Account Management

**Date created**: 2026-10-19
**Date updated**: 2026-11-14
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Feature Branch**: `son`

**Status**: Draft revision — the 2026-11-14 Manager-global authorization changes are proposed and pending Tech Lead approval. Historical approval in `approval.md` remains limited to its recorded scope.

**Input**: Replace the obsolete Member Profile scope with FE-001 Account Management, using `research.md` and the decisions recorded during the 2026-10-24–2026-10-25 clarification.

## Purpose and scope

FE-001 provides the account lifecycle for Mland: registration and sign-in, profile and credential self-service, and administration of internal Staff and Manager accounts. It provides a stable identity, role, status, location scope, and audit boundary for other business features.

The feature serves a small-to-medium business. It deliberately uses fixed roles and a small number of account states rather than configurable roles, permission rules, or complex account-linking flows.

### In scope

- Email/password registration and sign-in; Google and Facebook sign-in when those providers are configured.
- Sign-out, email verification, password reset, password change, and login-email change.
- Account bootstrap, own-profile management, and email synchronization after a verified email change.
- Member shipping address book management: a two-level Vietnam administrative address (province/city then commune/ward/special zone), sequential Shopee-style entry, default-address rules, and a versioned administrative directory managed by Admin.
- System-wide time standardization to Vietnam local time (`Asia/Ho_Chi_Minh`, UTC+7) for MySQL DB, JDBC connection, and API responses.
- An immutable shipping-address facade snapshot for a future FE-008 checkout integration; changing FE-008 checkout or GHTK fee calculation is not part of this revision.
- Admin management of versioned Vietnam administrative-directory reference data; only the active directory is available for new or edited Member addresses.
- Admin list/detail, internal-account creation, invitation resend, internal profile edit, role/location assignment, account suspension/restoration, and fixed-role reference.
- Account lifecycle audit, safe retry/recovery, and notification requests to FE-07.

### Manager authorization baseline (2026-11-14)

An `ACTIVE` canonical `MANAGER` has global operational scope for actions that an
owning feature explicitly authorizes to Manager. A Manager retains one or more
active Workshop Location assignments for organization and assignment records,
but those assignments do not narrow Manager authorization. An `ACTIVE` `STAFF`
member remains Workshop-Location-scoped. `ADMIN`-only, ownership-only, and
public actions remain under their explicit feature rules; Manager is not a
system superuser.

### Out of scope

- Dynamic RBAC: creating roles, changing permissions, or assigning multiple roles.
- A web UI/API to create, change, suspend, or restore an Admin account. Admin bootstrap is an operations process outside this feature.
- Automatic merging/linking of accounts that merely share an email address.
- Dashboard content, Home-page implementation, frontend application scaffolding, production deployment, and production identity-provider setup.
- Direct email delivery; FE-07 owns delivery after Account records a notification request.
- Account deletion workflow or endpoints. If a future approved account-deletion workflow removes an Account, this feature deletes its owned address-book records but retains only safe audit/log metadata.
- FE-008 checkout selection by `savedAddressId`, GHTK quoting, carrier aliases, carrier fallback, and Workshop Location pickup-address management.

### Address-book revision decisions (2026-11-14)

- These decisions supersede earlier FE-001 clarification text that describes a Province/District/Ward/Hamlet address hierarchy or public directory lookup.
- A new or edited Member address uses **Province/City -> Commune/Ward/Special Zone -> Detail address**. District, legacy ward and hamlet are not fields in the new model.
- The address form is progressive: recipient name, recipient phone, province/city, commune and detail address are enabled in that order. It starts blank; it does not prefill recipient data from Account Profile.
- The address request supplies official province and commune codes only. The server resolves and persists display names from the active directory; it never trusts administrative names supplied by the browser.
- The administrative directory is versioned reference data owned by FE-001. An Admin creates a new draft by copying the active version, edits it, then activates it. Each version records `version`, `sourceUrl`, and `effectiveFrom`; there is exactly one active version. Active versions are not edited in place. Data-shape changes require a code change; ordinary reference-data changes do not.
- Existing addresses retain their directory version. They are not automatically remapped when a new directory is activated. A future GHTK integration must require reselection against the current active directory before using a superseded address to quote shipping.
- Address-book CRUD and directory lookup require an authenticated Member. Administrative-directory version management requires Admin. No Staff, Manager or Admin endpoint exposes another Member's address book.

## Clarifications

### Session 2026-10-26

- Q: Bạn chốt dùng một cổng bảo mật chung do FE-001 sở hữu cho toàn backend không? → A: Một cổng chung xác minh bearer/revocation, nạp actor hiện hành, mặc định từ chối route không khai báo; từng feature tự quyết quyền nghiệp vụ từ actor.
- Q: Bạn có xác nhận các giá trị database mặc định đã commit phải được coi là credential đã lộ và cần xoay vòng không? → A: Các giá trị đó là dữ liệu giả, chưa từng dùng hoặc reuse; phải bỏ khỏi cấu hình versioned nhưng không cần xoay vòng incident.
- Q: Bạn có chốt quy ước Flyway chung cho toàn repo là version số nguyên tăng dần không? → A: Dùng một dãy số nguyên tăng dần chung; owner kiểm tra/reserve số cao nhất trước khi tạo migration, và chỉ migration chưa merge/chưa áp dụng mới được đổi số.

### Session 2026-10-27

- Q: Khi một feature cần facts của Account đích mà FE-001 hoặc location dependency không thể xác minh được, facade phải trả gì? → A: Dùng outcome typed `FOUND`, `NOT_FOUND`, hoặc `UNAVAILABLE`; consumer fail closed khi `UNAVAILABLE` và không coi đó là Account không tồn tại.
- Q: Workshop branch có phải là một location riêng cần mapping không? → A: Không; `Workshop Location` là site-and-scope concept chung, dùng duy nhất `workshopLocationId`. “Branch” chỉ là nhãn hiển thị, không có ID hay mapping riêng.
- Q: Facade có trả assignment Workshop Location thô hay scope đang dùng được? → A: Chỉ trả effective `activeWorkshopLocationIds` = assignment active ∩ Workshop Location currently active; không xác minh được nguồn Workshop Location thì trả `UNAVAILABLE`.
- Q: FE-001 có quyết định capability workshop cho account đích không? → A: Không; FE-001 chỉ công khai canonical Account facts. FE-003 tự đánh giá mọi quyền và trách nhiệm workshop từ facts đó cùng resource của mình.
- Q: V1 có đồng bộ quyền Account sang Workshop bằng event không? → A: Không; Workshop query current facts đồng bộ ngay trước mỗi thao tác nhạy cảm. Không cache hoặc suy quyền từ assignment lịch sử.
- Q: V1 có lock transaction xuyên Account và Workshop để đóng race tuyệt đối không? → A: Không; quyền có hiệu lực ở lần recheck kế tiếp. Một command đã recheck trước khi Account change commit có thể hoàn tất; các command sau phải bị từ chối.
- Q: Workshop chọn Staff đích bằng cách nào? → A: FE-001 cung cấp internal roster query hẹp theo `workshopLocationId`, chỉ trả `accountId`, display name, role cho Staff `ACTIVE` có effective scope phù hợp; UI không nhập ID thủ công. Roster không chọn hay authorize Manager.
- Q: Ai authorize việc đọc roster/facts cho Workshop? → A: FE-003 authorize actor và Session/resource trước khi gọi internal facade. FE-001 không có REST endpoint cho query này, chỉ dùng module boundary và dữ liệu trả về tối thiểu.
- Q: Consumer xử lý target facts không còn hợp lệ thế nào? → A: Mọi mismatch `NOT_FOUND`/role/status/scope được biểu diễn chung là `TARGET_NOT_ELIGIBLE`; chỉ `UNAVAILABLE` là lỗi tạm thời cần retry. FE-003 map chúng thành `409` và `503` tương ứng, không lộ lifecycle chi tiết.

### Session 2026-10-28

- Q: Khi FE-001 hoặc Workshop Location không xác minh được scope, V1 xử lý mutation thế nào? → A: Dừng mutation, trả `UNAVAILABLE`/`503`, không cache, fallback, hay background retry; UI retry sau.
- Q: Audit roster/facts read và quyết định Workshop được sở hữu thế nào? → A: FE-001 chỉ audit Account mutation; FE-003 audit Staff assignment/Operational Lead decision. Không audit từng roster/facts read.
- Q: `accountId` được biểu diễn thế nào qua REST/UI và Java facade? → A: REST/UI dùng chuỗi opaque chứa số nguyên dương canonical; Java facade dùng boundary type rõ ràng. Không tạo Personnel ID hoặc UUID thứ ba.
- Q: Roster dùng tên nào để chọn Account? → A: `displayName` là `Account Profile.fullName` hiện tại; không tạo field/tên nhân sự riêng. UI thêm `accountId` để phân biệt tên trùng.
- Q: Bạn có chốt FE-001 chỉ công khai một actor context tối thiểu cho feature khác không? → A: Identity gate công khai accountId opaque, role, status và emailVerified; UID/token ở lại identity gate. Effective activeWorkshopLocationIds được tra cứu qua facade scope riêng cho action cần Workshop Location, còn mỗi feature tự map context sang quyền nghiệp vụ của mình.

### Session 2026-10-29

- Q: Bạn muốn FE-001 bàn giao đầy đủ facade cho FE-003 trong đợt này, hay tách FR-028–FR-032 thành scope sau? → A: Giữ FR-028–FR-032 trong scope FE-001 hiện tại; chuẩn hóa typed internal facade contract ngay.
- Q: Bạn chốt chuẩn accessibility và phạm vi browser nào cho các màn hình FE-001? → A: WCAG 2.2 AA; Chrome, Edge và Firefox hai bản major mới nhất trên desktop.
- Q: Bạn có chốt nghiệm thu FE-001 hiện chỉ ở môi trường local, còn staging/production origin, rollout và observability để ở scope release sau không? → A: Chỉ nghiệm thu local; staging/production là scope release sau.
- Q: Khi Workshop Location không xác minh được, bạn muốn chặn mọi protected request vì current actor không có effective scope, hay chỉ chặn action cần location scope? → A: Chỉ chặn action phụ thuộc Workshop Location; self-service Account và protected action không cần location scope vẫn hoạt động.
- Q: Khi một lệnh quản trị hoặc bảo mật Account đã qua authorization nhưng thất bại do stale version, dependency lỗi hoặc business rule, hệ thống có phải lưu FAILURE audit event không? → A: Audit SUCCESS và domain FAILURE sau authorization; không audit malformed hoặc unauthenticated request.
- Q: Nếu hệ thống không thể ghi audit cho một command Account đã được authorize, command đó phải xử lý thế nào? → A: Cho phép outcome nghiệp vụ gốc tiếp tục; lỗi audit không rollback/chặn mutation và không thay đổi response nghiệp vụ.

### Session 2026-10-31

- Q: Với Member mới bootstrap nhưng chưa từng lưu profile, bạn muốn AccountProfile chưa tồn tại hay đã tồn tại với full_name rỗng/null? → A: Tạo AccountProfile lúc bootstrap; full_name mặc định lấy từ tên email của người dùng.
- Q: “Tên email” bạn muốn dùng làm full_name mặc định là phần trước dấu @ hay toàn bộ địa chỉ email? → A: Dùng phần trước dấu `@`.

### Session 2026-11-01

- Q: Nếu một external provider xác minh danh tính nhưng không trả về email hợp lệ, FE-001 sẽ xử lý bootstrap thế nào? → A: Với email hợp lệ nhưng chưa verified, vẫn bootstrap Account/Profile và hiển thị yêu cầu xác thực. Với identity không có email, không bootstrap Account/Profile mới; hướng dẫn dùng phương thức khác hoặc recovery, không nhận email từ browser.
- Q: Bạn có chốt FE-001 chỉ công khai đúng hai method nội bộ cho FE-003: findTargetFacts(accountId) và findEligibleRoster(workshopLocationId) không? → A: Có; `findTargetFacts` phục vụ target/Staff location scope, `findEligibleRoster` chỉ trả Staff. Current Manager được authorize từ actor context, không có REST endpoint hoặc generic Account directory.

### Session 2026-11-14

- Q: Manager có cần giữ Workshop Location assignment không? → A: Có, Manager có một hoặc nhiều assignment active để phục vụ tổ chức/phân công; assignment không giới hạn quyền của Manager cho action mà feature đã quy định là Manager-authorized.
- Q: Khi authorize Manager, có cần lấy effective Workshop Location scope không? → A: Không. `CurrentAccountActor` xác nhận current role/status; action Manager-authorized được quyết định từ actor active và resource rule của feature. Chỉ Staff location-scoped action mới lấy scope và fail closed khi scope không kiểm chứng được.
- Q: Roster Workshop có dùng để chọn hoặc authorize Manager không? → A: Không. Roster chỉ là Staff picker theo Location; không có generic Manager directory trong FE-001.
- Q: Bạn muốn giữ task/plan cũ dưới dạng tài liệu lịch sử riêng hay thay trực tiếp bằng bộ artifact triển khai mới? → A: Thay trực tiếp artifact cũ; dùng Git history để xem bản trước.

### Session 2026-11-03

- Q: V1 có dùng Mland custom email-action handler, Firebase callback/webhook, hay background job để đồng bộ email verification không? → A: Không. Firebase dùng email-action handler mặc định; FE-001 không nhận/lưu `oobCode`, không custom handler và không chạy polling/job nền.
- Q: Sau khi Member xác minh email tại Firebase, hệ thống phát hiện và đồng bộ thế nào? → A: Chỉ khi Member vào một capability yêu cầu email verified hoặc chọn kiểm tra lại trạng thái, FE chủ động `reload` Firebase user, ép ID token mới, rồi gọi endpoint đồng bộ email của FE-001. Không được suy email verification từ browser payload; BE xác minh token mới cho từng command.
- Q: Ai gửi lại email verification cho Member? → A: Firebase Web SDK ở FE gửi khi Member đang đăng nhập và chủ động chọn resend. Đây là email identity-provider, không phải email nghiệp vụ do FE-07 gửi; FE-07 vẫn sở hữu delivery của invitation/lock/unlock/role-change notification.
- Q: Trong FE-001 hiện tại, capability nào phải chạy verification gate trước khi thực hiện? → A: Chỉ thao tác lưu/chỉnh Profile. Kích hoạt Staff/Manager vẫn do BE kiểm tra email verified theo FR-013; Admin commands không có gate email riêng. Capability khác chỉ được thêm sau khi có thay đổi spec rõ ràng.
- Q: Khi Member mở Profile, verification gate chạy lúc nào và thao tác kiểm tra lại hiển thị thế nào? → A: Tự chạy khi mở Profile; Member có thể chạy lại bằng icon reload trong phần trạng thái verification. Chỉ cho phép Save sau khi đồng bộ thành công. Icon phải có accessible name/tooltip, trạng thái đang tải và lỗi để dùng được bằng bàn phím/screen reader.
- Q: Sau khi Member resend email verification từ Profile, UI giới hạn tần suất thế nào? → A: Sau khi Firebase xác nhận gửi thành công, UI áp dụng cooldown 90 giây. Firebase vẫn là nguồn quyết định quota; lỗi/quota không tự động gửi lại.
- Q: Nếu Profile gate không còn Firebase session hoặc token không hợp lệ, giữ Profile draft thế nào? → A: Không giữ draft. Chuyển người dùng về đăng nhập để không lưu dữ liệu Profile tạm trong browser.
- Q: Khi Member đổi email, Firebase cập nhật email mới trước hay xác thực email mới trước? → A: Dùng `verifyBeforeUpdateEmail`: Firebase và DB giữ email cũ đến khi Member xác thực email mới; sau đó Profile gate đồng bộ email mới đã verified. Không dùng luồng đổi ngay rồi chờ sync.
- Q: Nếu Firebase tạo Member thành công nhưng lần gửi email verification đầu tiên thất bại, hệ thống làm gì? → A: Vẫn bootstrap và chuyển Member tới Home, hiển thị reminder an toàn và cho Member chủ động resend từ Profile; không retry nền và không rollback Account.
- Q: Cooldown resend verification 90 giây có giữ sau refresh/đóng-mở browser không? → A: Có. Dùng `localStorage` theo Account ID, chỉ lưu mốc hết hạn và tự hết hạn sau 90 giây; không dùng email, Firebase UID, token hay action code làm storage key/value.
- Q: Về việc lưu trữ thời gian trong DB bị sai lệch 7 tiếng so với thực tế ở Việt Nam, hệ thống chuẩn hóa thế nào? → A: Chuẩn hóa toàn diện sang UTC+7 (`Asia/Ho_Chi_Minh`) cho DB, JDBC connection (`connectionTimeZone=Asia/Ho_Chi_Minh&forceConnectionTimeZoneToSession=true`), Spring Boot default timezone và Jackson serializer. Các cột `DATETIME` trong MySQL phản ánh trực tiếp giờ Việt Nam. Dữ liệu trong DB local/dev là dữ liệu kiểm thử, cho phép reset/re-seed sạch mà không cần migration bù giờ cũ.
- Q: Danh sách các cấp hành chính (Tỉnh/Thành, Quận/Huyện, Phường/Xã) và địa chỉ cấp 4 của GHTK được quản lý và cung cấp thế nào? → A: Backend (FE-001) cung cấp API danh mục hành chính chuẩn tương thích GHTK (Tỉnh -> Huyện -> Xã). Frontend dùng cascading dropdown để Member chọn. Lưu cả tên chuẩn và mã định danh. Schema hỗ trợ sẵn `hamlet_id` và `hamlet_name` (nullable) cho địa chỉ cấp 4 của GHTK (thôn/ấp/xóm/bản) khi cần.
- Q: Quy tắc nghiệp vụ sổ địa chỉ theo chuẩn Shopee được quy định ra sao? → A: Mỗi Member được lưu tối đa 10 địa chỉ. Luôn có tối đa 1 địa chỉ mặc định; địa chỉ đầu tiên tạo tự động là mặc định; khi đặt mặc định mới thì tự động bỏ cờ mặc định cũ. Không cho phép xóa địa chỉ đang là mặc định nếu tài khoản còn các địa chỉ khác (phải đổi mặc định trước). Thao tác xóa là Hard Delete (xóa vật lý). Có nhãn loại địa chỉ: Nhà riêng (`HOME`) hoặc Văn phòng (`OFFICE`).
- Q: Số điện thoại người nhận được chuẩn hóa và kiểm tra thế nào? → A: Tự động chuẩn hóa (loại bỏ ký tự phân cách như dấu cách, gạch ngang, chấm; chuyển đổi `+84` hoặc `84` thành `0`), sau đó kiểm tra regex chuẩn 10 chữ số di động Việt Nam (`^0[35789]\d{8}$`) và lưu dạng 10 số sạch.
- Q: Trải nghiệm UX khi Member thêm địa chỉ đầu tiên có hỗ trợ điền trước không? → A: Có. Tự động điền trước (pre-fill) họ tên từ `AccountProfile.fullName` và số điện thoại từ `AccountProfile.phone` (nếu có) khi mở form thêm địa chỉ đầu tiên; Member có thể chỉnh sửa tự do.
- Q: Hợp đồng kết nối và chia sẻ dữ liệu địa chỉ giữa FE-001 và FE-008 hoạt động như thế nào? → A: FE-001 cung cấp REST API `/api/v1/accounts/me/addresses` cho Frontend và Spring Modulith Facade (`AccountFacade.findDefaultShippingAddress` và `findShippingAddressById`) trả về `MemberAddressSnapshot` cho Backend FE-008. Khi checkout, FE-008 sao chép lưu độc lập snapshot địa chỉ vào đơn hàng; việc sửa/xóa địa chỉ trong Profile của FE-001 sau đó hoàn toàn không làm đổi thông tin đơn hàng cũ.

### Session 2026-11-04

- Q: Cơ chế Re-authentication cho tính năng Đổi mật khẩu của tài khoản Email/Password được triển khai như thế nào? → A: In-place Re-auth trên form Đổi mật khẩu gồm 3 trường bắt buộc: Mật khẩu hiện tại, Mật khẩu mới, Xác nhận mật khẩu mới. Khi submit, FE gọi `reauthenticateWithCredential(user, EmailAuthProvider.credential(user.email, currentPassword))` trước khi gọi `updatePassword` để làm mới credential, loại bỏ hoàn toàn lỗi `auth/requires-recent-login`.
- Q: Mục Đổi mật khẩu hiển thị và xử lý như thế nào đối với tài khoản bên thứ ba (Google/Facebook không có mật khẩu)? → A: Kiểm tra danh sách nhà cung cấp qua `user.providerData`: mục Đổi mật khẩu (cả form và nút điều hướng nhanh) chỉ hiển thị với tài khoản có provider `password`. Với tài khoản không có mật khẩu (OAuth), ẩn form hoặc hiển thị thông báo hướng dẫn quản lý tại nhà cung cấp tương ứng.
- Q: Cơ chế Re-authentication cho tính năng Thay đổi email (`verifyBeforeUpdateEmail`) được triển khai như thế nào? → A: Form Thay đổi email gồm 2 trường: Mật khẩu hiện tại và Địa chỉ email mới. Khi bấm gửi yêu cầu, FE gọi `reauthenticateWithCredential` trước khi gọi `verifyBeforeUpdateEmail` nhằm đảm bảo credential luôn tươi mới và chống đánh cắp email trái phép.
- Q: Khi xảy ra lỗi xác thực (sai mật khẩu hiện tại hoặc lỗi mạng), chính sách giữ/xóa dữ liệu trên form xử lý ra sao? → A: Nếu sai mật khẩu hiện tại, chỉ xóa trường Mật khẩu hiện tại và báo lỗi inline để người dùng nhập lại; giữ nguyên Mật khẩu mới hoặc Email mới trong bộ nhớ form tạm thời (React state, tuyệt đối không lưu vào `localStorage/sessionStorage`). Chỉ xóa sạch toàn bộ các trường khi thao tác thành công. Nếu phiên đăng nhập bị thu hồi hoặc hết hạn hoàn toàn, hủy bản nháp và chuyển hướng người dùng về trang Đăng nhập kèm đường dẫn quay lại.
- Q: Đăng nhập bằng Google có thuộc phạm vi triển khai bắt buộc của FE-001 không và cơ chế đăng nhập là gì? → A: Thuộc phạm vi bắt buộc của FE-001. Sử dụng phương thức `signInWithRedirect` của Firebase Auth; khi người dùng bấm nút "Đăng nhập bằng Google", ứng dụng chuyển hướng sang Google và xử lý kết quả tại trang Auth qua `getRedirectResult`. Facebook Login tiếp tục được hoãn lại (Deferred).
- Q: Điều kiện hiển thị nút Đăng nhập bằng Google trên giao diện như thế nào? → A: Mặc định luôn hiển thị nút "Đăng nhập bằng Google" trên màn hình Đăng nhập (`signin`) và Đăng ký (`register`) khi ứng dụng có cấu hình Firebase hợp lệ (`hasFirebaseConfig()`).
- Q: Xử lý thế nào khi đăng nhập bằng Google nhưng email đó đã được đăng ký bằng Mật khẩu trước đó? → A: Khi Firebase trả về lỗi `auth/account-exists-with-different-credential`, hệ thống không tự động merge hoặc link tài khoản (tuân thủ FR-001 và FR-022); hiển thị thông báo hướng dẫn: "Email này đã được đăng ký bằng Mật khẩu. Vui lòng đăng nhập bằng Email và Mật khẩu gốc."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Register and use a personal account (Priority: P1)

A customer creates or signs in to a personal account through an allowed identity method. The system recognizes the person by their verified identity rather than by a client-supplied email or role. The customer reaches Home after sign-in and can complete the self-service actions for which they meet the requirements.

**Why this priority**: Account access is the entry point for every customer journey that needs a known person.

**Independent Test**: A new and a returning customer can register/sign in, reach Home, and repeatedly bootstrap the same account without receiving a duplicate account or another person's data.

**Acceptance Scenarios**:

1. **Given** a person completes an allowed registration or sign-in, **When** their identity is verified, **Then** the system creates or returns exactly one account for that identity and sends them to Home.
2. **Given** the same verified identity retries account bootstrap after a lost response, **When** the request is repeated, **Then** the system returns the existing account without changing its role, status, or business history.
3. **Given** a different identity has the same-looking email as an existing account, **When** it signs in, **Then** the system does not merge, link, or grant access to the existing account and gives safe recovery guidance.
4. **Given** a customer on the Sign-in or Register page, **When** they click "Đăng nhập bằng Google", **Then** the browser initiates `signInWithRedirect` to Google; upon successful return, `getRedirectResult` resolves their verified identity, bootstraps their `MEMBER` account, and navigates them to Home.
5. **Given** a customer signs in with a Google account whose email matches an existing email/password account, **When** Google redirect returns, **Then** the system receives `auth/account-exists-with-different-credential`, denies the sign-in, displays guidance to sign in with their password credential, and creates no duplicate account.

---

### User Story 2 - Complete account verification and self-service (Priority: P1)

A signed-in person can see that their email still needs verification, verify it explicitly, manage their own profile, and use supported credential recovery/change flows without the business system ever receiving their password.

**Why this priority**: Customers need a safe path to recover access and maintain the personal information used by later features.

**Independent Test**: A customer with an unverified email can sign in and reach Home but is prevented from using verification-required profile functions until verification completes; a verified customer can update their profile and credential-related information safely.

**Acceptance Scenarios**:

1. **Given** a signed-in person whose email is not verified, **When** they open Home or Profile, **Then** they see a non-blocking verification reminder and can explicitly request another verification email from Firebase; after a successful send, the resend action has a 90-second UI cooldown.
2. **Given** that person opens Profile, **When** the FE automatically reloads the Firebase user and obtains a forced-fresh ID token, **Then** a newly verified identity is synchronized by the server before Profile save proceeds; no browser email or boolean is accepted as authority.
3. **Given** that person is still unverified after the Profile check, **When** they try to save Profile, **Then** the command is denied without losing safe entered form data and the person is guided to verify or resend; the FE does not poll or retry the command automatically.
4. **Given** verification was completed in a different browser/device without a current Firebase session, **When** the person later signs in and opens Profile, **Then** the same check-and-sync path completes without requiring a Firebase callback or a background job.
5. **Given** a verified active account, **When** its owner saves a valid full name and optional phone number, **Then** the account shows the newly saved values and no role, status, email, or location changes.
6. **Given** an owner completes an identity-provider email change and returns with a current verified identity, **When** the system synchronizes the account, **Then** it records the current email for the same account and never accepts an email value supplied only by the browser.
7. **Given** an owner requests an email change, **When** Firebase sends verification to the proposed new email, **Then** the current Firebase and Account email remain unchanged until that new email is verified; after the owner returns to Profile, the normal gate synchronizes the verified new email.
8. **Given** Firebase registration succeeds but the initial verification-email send fails, **When** bootstrap completes, **Then** the Member reaches Home with safe verification reminder/recovery guidance; no Account is rolled back and no background resend occurs.
9. **Given** an account owner with a password-authenticated account (`password` provider in `providerData`) in Profile, **When** they submit a password change with valid current password and matching new password ($\ge 6$ characters), **Then** the FE performs in-place re-authentication with current credentials before updating the password through Firebase, clears all password form fields, and displays a success alert; if current password verification fails, only the current password field is cleared, an inline error is shown, and the proposed new password is retained in component memory.
10. **Given** an account owner without a `password` provider (e.g. third-party OAuth), **When** they view Profile, **Then** the password-change form and action button are hidden or replaced with an advisory notice directing credential management to the external identity provider.

---

### User Story 3 - Admin provision and maintain an internal account (Priority: P1)

An Admin creates a Staff or Manager account with a fixed role and valid location scope. The invited person completes their own setup before receiving internal access. The Admin can later maintain allowed information and role/location scope without accidentally changing unrelated security state.

**Why this priority**: Internal operations cannot begin until the business can safely provision and scope employees.

**Independent Test**: An Admin can create one pending Staff or Manager account, resend a setup invitation, and activate it only after its intended identity completes the required setup; duplicate create attempts do not create another account or invitation.

**Acceptance Scenarios**:

1. **Given** an Admin supplies a new Staff or Manager with valid location scope, **When** the creation is accepted, **Then** exactly one internal account is created in `PENDING_SETUP` and a post-commit invitation request is recorded.
2. **Given** an Admin retries the same interrupted create action, **When** the request is retried unchanged, **Then** the original result is returned; a reuse of the retry key with changed details is rejected.
3. **Given** a pending internal account signs in with its intended verified identity and has valid active location scope, **When** it completes account bootstrap, **Then** it becomes `ACTIVE` and is never replaced by a Member account.
4. **Given** an Admin edits internal details, **When** they change profile, role/location, or status, **Then** each action is separate, records an audit event, and cannot silently overwrite a newer Admin change.

---

### User Story 4 - Admin protect the business from an unsafe account (Priority: P1)

An Admin can find an allowed account, suspend it with a reason, and later restore it only after existing sessions have been invalidated. Suspension takes effect even when a downstream provider or email delivery is temporarily unavailable.

**Why this priority**: Immediate account containment is a core operational safety need.

**Independent Test**: An Admin suspends an account and its next protected action is denied immediately; restoration cannot make a stale session valid or bypass incomplete setup.

**Acceptance Scenarios**:

1. **Given** an allowed Member, Staff, or Manager account, **When** an Admin suspends it with a reason, **Then** protected access is denied immediately and the suspension is audited.
2. **Given** the identity-provider session invalidation is still pending, **When** an Admin tries to restore the account, **Then** restoration is unavailable until that invalidation completes.
3. **Given** an account is restored after session invalidation, **When** its owner returns, **Then** they must sign in again and the system restores the account's prior eligible state rather than granting new rights.

---

### User Story 5 - Understand fixed access without configuring permissions (Priority: P2)

An Admin can search and inspect accounts, see the fixed role catalogue, and make only the role/location assignments supported by the business. The Admin cannot create a new role, edit permissions, create another Admin, or use account administration to obtain unrelated business powers.

**Why this priority**: Clear administration avoids role mistakes while keeping V1 manageable.

**Independent Test**: An Admin can filter the account list and view the static role descriptions, while every attempt to configure dynamic permissions, assign an unsupported role, or manage an Admin account is rejected.

**Acceptance Scenarios**:

1. **Given** an Admin searches by email or full name and filters by role/status, **When** the list is displayed, **Then** results are paginated, ordered by recent change/creation, and do not expose credentials, provider identifiers, or unrelated business records.
2. **Given** a Staff assignment, **When** an Admin saves it, **Then** it has exactly one active location; a Manager assignment has one or more active locations.
3. **Given** an Admin opens the role catalogue, **When** it is displayed, **Then** it explains the fixed role scope without offering permission or role editing.

---

### User Story 6 - Manage a two-level personal shipping address book (Priority: P1)

A signed-in Member can view, add, edit, physically delete, and set a default shipping address from Profile. The address has Province/City, Commune/Ward/Special Zone and a detail address selected from the active administrative-directory version. Entry follows a progressive form: name, phone, province, commune, then detail address. A future checkout consumer may obtain an immutable address snapshot; checkout behavior itself is outside this revision.

**Why this priority**: Fast, accurate shipping destination entry is mandatory for physical retail fulfillment and live carrier fee quoting.

**Independent Test**: A Member can save up to 10 addresses using only an active directory's valid province/commune pair; exactly one default is maintained; stale writes are rejected; and client-provided administrative names cannot alter stored canonical names.

**Acceptance Scenarios**:

1. **Given** a Member with no saved addresses opens the form, **When** it is displayed, **Then** recipient name and phone are blank, and later fields remain disabled until each preceding required field is valid.
2. **Given** a Member adds an address with a phone number containing whitespace, punctuation, or `+84`/`84`, **When** the address is saved, **Then** the system normalizes the number to clean 10-digit format matching `^0[35789]\d{8}$` and rejects non-Vietnamese or malformed numbers.
3. **Given** a Member with multiple addresses attempts to delete their default address, **When** the deletion is requested, **Then** the system rejects the deletion with an actionable error instructing them to designate another address as default first.
4. **Given** a Member already has 10 saved addresses, **When** they submit an 11th address, **Then** the system returns an address quota conflict (`409 ADDRESS_LIMIT_EXCEEDED`).
5. **Given** a Member submits a commune code that does not belong to the selected province, **When** the server validates the address, **Then** it rejects the request and persists no client-supplied administrative name.
6. **Given** an address is changed by another browser session after a Member opened it, **When** the Member saves or deletes using the earlier version, **Then** the system rejects the stale command and requires reload.

### Edge Cases

- A configured redirect sign-in provider is unavailable or reports an account-method conflict: no local account is created until a valid identity is established, and the person receives retry/alternative-method guidance. Popup is not a supported recovery path.
- A password-reset or verification request must not reveal whether an email account exists.
- If Firebase registration succeeds but its initial verification-email send fails, bootstrap remains successful and Home presents safe reminder/recovery guidance; the Member can explicitly resend from Profile, with no rollback or background retry.
- A person can sign in with an unverified email but cannot bypass an operation's verified-email requirement, even by refreshing the UI or presenting stale identity information.
- An email change that succeeds at the identity provider but initially fails to synchronize can be retried from the current identity without reverting to an older email.
- An email-change request uses Firebase `verifyBeforeUpdateEmail` after required reauthentication. The proposed new email is not persisted as Account login email or treated as current identity before Firebase verifies it; the existing verified email remains current until the Profile gate synchronizes the completed change.
- If the Profile verification gate has no current Firebase user or receives an invalid/expired-session outcome, it discards the unsaved Profile draft through the shared session boundary and directs the person to Home with the generic session-ended flash; it does not persist that draft in browser storage.
- A location becomes inactive after a Staff/Manager account is active: the account's role/status is not changed automatically, but actions in that location are denied.
- An invitation expires, is not delivered, or an internal creation partly fails: the original account remains pending; an authorized resend/recovery never creates a second identity or account.
- A notification-delivery failure does not undo a committed account, role, status, or audit change; it remains recoverable through FE-07.
- A concurrent Admin edit is rejected rather than overwriting the latest profile, role/location, or status state.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST create or return one Account for each verified identity and MUST use that identity, not a client-supplied email, account ID, role, or provider profile, to determine account ownership.
- **FR-002**: The system MUST support email/password registration and sign-in, and MUST support Google sign-in via Firebase `signInWithRedirect` whenever Firebase configuration is present (`hasFirebaseConfig()`). The Google sign-in action MUST be prominently available on both sign-in and registration views; upon return from the redirect, the system MUST process `getRedirectResult`, bootstrap the account, and route to Home. If the Google account's email conflicts with an existing password-authenticated account (`auth/account-exists-with-different-credential`), the system MUST NOT automatically merge or link accounts and MUST display safe guidance directing the person to sign in with their original password method. Facebook sign-in remains deferred for future release.
- **FR-003**: The system MUST send every successful sign-in to Home and MUST NOT make a role dashboard or policy acceptance a sign-in prerequisite.
- **FR-004**: The system MUST permit a person with an unverified email to sign in and see a clear verification reminder; each protected function MUST independently enforce its verified-email requirement on the server.
- **FR-005**: The system MUST let an account owner explicitly sign out, request or resend email verification through the Firebase Web SDK while signed in, reset an eligible password, change an eligible password after required reauthentication, and change login email only through Firebase `verifyBeforeUpdateEmail` after required reauthentication. For eligible password change: accounts with a `password` provider in `providerData` MUST provide an in-place form requiring current password, new password ($\ge 6$ characters), and confirmation; form submission MUST perform in-place re-authentication via `reauthenticateWithCredential` using current password and identity email before invoking `updatePassword`. The password-change section and its navigation trigger MUST NOT be displayed for accounts without a `password` provider (e.g. OAuth). For login-email change: the form MUST require current password and proposed new email; submission MUST perform in-place re-authentication via `reauthenticateWithCredential` before invoking `verifyBeforeUpdateEmail`. On re-authentication failure (incorrect current password), the form MUST clear only the current password field, present an actionable inline error message, and retain proposed new values in ephemeral component memory; it MUST NOT store password values in browser storage (`localStorage`, `sessionStorage`, cookies). Upon successful mutation, all form fields MUST be cleared and a success notification displayed. If an unrecoverable session failure occurs (`auth/user-token-expired` or `auth/user-disabled`), unsaved draft data MUST be discarded and the user directed to sign in. Firebase retains the current email until the proposed new email is verified; the Profile gate then synchronizes that verified current identity email. After Firebase confirms a Member verification-email send, the Profile UI MUST disable further resend for 90 seconds. This cooldown persists across refresh/browser restart in `localStorage`, keyed only by opaque Account ID and storing only an expiry timestamp; it expires after 90 seconds and MUST NOT store email, Firebase UID, bearer/token, or action code. Firebase remains the quota authority, and a send/quota failure is shown with safe guidance without automatic retry. V1 uses Firebase’s default email-action handler; it has no Mland custom action handler, no Firebase callback/webhook, and no scheduled/polling verification job. FE-07 does not deliver Member verification email.
- **FR-006**: The system MUST synchronize the current login email and verification snapshot only from a current Firebase identity for the same Account. In the current FE-001 scope, on Profile entry the FE MUST reload the Firebase user and obtain a forced-fresh Firebase ID token; the owner may invoke the same check again through a verification-status reload icon. The icon MUST expose an accessible name/tooltip plus loading and failure states. If no current Firebase user exists or the session is invalid/expired, the FE MUST discard unsaved Profile draft and direct the owner to sign in; it MUST NOT persist the draft in browser storage. The BE MUST verify that token and reject stale or browser-supplied email/verification data as an authority source. Profile save is enabled only after successful synchronization and is never automatically retried after synchronization. Staff/Manager activation independently enforces verified email under FR-013; Admin commands have no additional email-verification gate. A future capability may use this gate only after an explicit specification change.
- **FR-007**: The system MUST create an Account Profile with every Account bootstrap. Its initial full name is the portion before `@` of the current identity email, never a client-supplied value or provider display name. An active, verified account owner can later view and replace their own full name and optional phone number. A full name is required after outer-whitespace trimming, contains 1–100 Unicode characters, and contains no ISO control character. A phone is optional, trimmed, 7–30 characters when present, and is neither normalized, verified by OTP, nor required to be unique.
- **FR-008**: The system MUST keep profile, role/location, status, email, credential, and identity changes as distinct actions. Profile editing MUST NOT change role, status, login email, identity, or location scope.
- **FR-009**: The system MUST support exactly the fixed roles `MEMBER`, `STAFF`, `MANAGER`, and `ADMIN`. Each Account has exactly one role; provider claims and frontend visibility MUST NOT grant a business role.
- **FR-010**: The system MUST expose a read-only fixed-role catalogue to Admins. It MUST NOT provide user-configurable permissions, custom roles, multiple role assignment, or a dynamic RBAC administration interface.
- **FR-011**: The system MUST allow an Admin to create only Staff or Manager internal accounts. An internal account starts as `PENDING_SETUP`; an Admin account is created and managed only by the separate operations bootstrap process.
- **FR-012**: The system MUST require exactly one active Workshop Location assignment for Staff and one or more active Workshop Location assignments for Manager. `workshopLocationId` is the sole shared site-and-scope identifier. Staff assignments define the Staff location scope. Manager assignments are organizational records and MUST NOT narrow an active Manager's authorization for an action explicitly authorized to Manager. The system MUST validate assignments against the authoritative workshop-location source and MUST NOT own a duplicate location record or a separate branch mapping.
- **FR-013**: The system MUST activate a pending Staff/Manager account only when its intended identity signs in, its email is verified, and its assigned location scope remains valid. This activation MUST NOT create or overwrite a Member account.
- **FR-014**: The system MUST make internal-account creation safely retryable and MUST preserve one result for a repeated unchanged creation request. A changed request using the same retry key MUST be rejected.
- **FR-015**: The system MUST let an Admin resend a setup invitation only for a pending internal account, subject to a five-minute resend cooldown. A resend uses a fresh identity-provider setup link and MUST NOT retain the raw link in audit or log records.
- **FR-016**: The system MUST let an Admin search and filter allowed account records by full name/email, role, and status; paginate the results; order them with recently changed/created records first; and exclude credentials, provider identifiers, and unrelated business data from the list/detail responses.
- **FR-017**: The system MUST allow an Admin to edit only full name and phone for Staff/Manager accounts. Member profile values are self-managed; login-email change remains an owner flow.
- **FR-018**: The system MUST make Admin changes to internal profile, role/location, and status conflict-safe. A change based on an older displayed version MUST be rejected and require reload rather than overwrite current data.
- **FR-019**: The system MUST allow an Admin to suspend or restore only Member, Staff, or Manager accounts. Suspension requires a reason, takes effect immediately for protected access, and does not depend on notification delivery or identity-provider invalidation completing first.
- **FR-020**: The system MUST invalidate prior identity sessions after suspension. Restoration MUST wait until the invalidation is complete, restore the eligible pre-suspension state (`ACTIVE` or `PENDING_SETUP`), and require the person to sign in again.
- **FR-021**: The system MUST use the states `ACTIVE`, `PENDING_SETUP`, and `SUSPENDED`; email verification is a separate condition. Deactivating a location MUST NOT itself change account role/status, but location-scoped access MUST be denied.
- **FR-022**: The system MUST never automatically merge, link, upgrade, or reuse accounts merely because emails match while identities differ. It MUST provide safe guidance to use the original sign-in method or contact support.
- **FR-023**: Every account-security or administrative command that has passed server authorization and entered the Account domain MUST attempt to create a safe audit record with actor, target, action, outcome, time, and correlation information. Both committed mutation (`SUCCESS`) and domain failure (`FAILURE`, including stale version, dependency error, or business-rule rejection) are audit outcomes; malformed or unauthenticated requests do not create an Account audit event. An audit persistence failure MUST NOT roll back, block, or replace the command's original business outcome, and the system MUST emit a non-sensitive operational failure signal with the correlation information rather than claim the audit was stored. Required role/status/location before/after data and suspension reason may be included; raw passwords, tokens, action links, full names, and phone values MUST NOT be included. Audit/security evidence successfully retained is kept for five years.
- **FR-024**: The system MUST record account invitation, lock, unlock, and role-change notification requests together with their successful account mutation. FE-07 delivery occurs after that mutation; a delivery failure MUST NOT roll back the accepted account decision.
- **FR-025**: The system MUST provide safe, actionable recovery for invalid identity, unverified email, suspended account, forbidden role/location, invalid input, stale Admin change, duplicate creation retry, missing record, and temporarily unavailable dependency. Error responses MUST not expose protected data, credentials, tokens, stack traces, or another account's existence beyond what the caller is authorized to know.
- **FR-026**: The system MUST ensure that every protected action uses the current Account role, status, and any applicable ownership or location scope on the server. An active Manager action explicitly authorized to Manager does not have an applicable Location-scope condition. Hiding a frontend control or retaining an old token/UI state MUST NOT preserve a revoked capability.
- **FR-027**: The system MUST provide one shared server-side identity gate for the backend. It MUST admit only explicitly declared public routes; for all other routes it verifies the bearer identity and revocation state, resolves a safe current Account actor, and denies access when this cannot be done. The actor contract exposes only opaque account ID, role, status, and email-verification state; raw identity-provider UID/token remain inside the gate. The gate MUST NOT resolve Workshop Location scope as an admission prerequisite. An action explicitly authorized to an active Manager uses this current actor without a Location lookup. A Staff location-scoped action independently obtains current effective `activeWorkshopLocationIds` (active Account assignments intersected with currently active authoritative Workshop Locations) from the named Account facade immediately before authorization or mutation; if that scope cannot be verified, only that Staff location-scoped action fails closed with `UNAVAILABLE`/`503`. The gate MUST NOT own or dynamically configure another feature's business capabilities; each feature derives its action authority from the current actor under its own approved rules.
- **FR-028**: The system MUST provide a named internal facade query for an opaque target Account ID when an approved consuming feature needs current canonical Account facts for a target or a Staff location-scoped action. Its typed outcome is `FOUND` with account ID, fixed role, lifecycle status, and effective `activeWorkshopLocationIds`, defined as the intersection of active Account assignments and currently active authoritative Workshop Locations; `NOT_FOUND` only when the Account is conclusively absent; or `UNAVAILABLE` when Account or the required Workshop Location scope dependency cannot be verified. It MUST NOT expose provider identity, profile/contact data, entities, repositories, or a cross-feature business-permission decision. A consumer MUST obtain current facts immediately before each dependent target or Staff location-scoped command, deny the action when the outcome is `UNAVAILABLE`, and MUST NOT substitute cached, event-synchronized, or locally inferred Account facts in V1. An active Manager acting as the current requester is authorized through FR-027 and MUST NOT call this query merely to establish global Manager authority. V1 does not hold a cross-module transaction/lock to eliminate a concurrent change after recheck; a command already rechecked before that change commits may complete, while subsequent commands MUST use the changed facts.
- **FR-029**: The system MUST provide an internal, scope-filtered roster query for an approved consuming feature to select a Workshop Staff target. Given a `workshopLocationId`, it returns only `accountId`, display name, and canonical `STAFF` role for `ACTIVE` Staff Accounts whose effective active Workshop Location scope includes that ID. Display name is the current Account Profile full name; no separate personnel/display-name field is created, and a consumer may show the Account ID to distinguish duplicate names. It MUST NOT expose email, phone, provider identity, lifecycle detail, a generic account directory, or an HTTP endpoint for this query. The consuming feature MUST authorize its actor and resource before invoking the facade, then revalidate the selected Staff target through FR-028 immediately before its mutation; roster inclusion is not authorization evidence. Manager selection is outside this roster contract.
- **FR-030**: The internal facade contract MUST let an approved consumer distinguish a retryable `UNAVAILABLE` outcome from a target that is not eligible. `NOT_FOUND` and any `FOUND` facts that fail the consumer's approved role/status/Workshop Location rule MUST be treated as one generic target-ineligible condition, without exposing lifecycle detail. The consuming Workshop API maps the target-ineligible condition to `409 TARGET_NOT_ELIGIBLE` and `UNAVAILABLE` to `503`; these mappings do not create an Account Management HTTP endpoint.
- **FR-031**: When the target-facts or effective Workshop Location scope query is `UNAVAILABLE`, a dependent mutation MUST not start or commit. The V1 facade and its consumer MUST NOT use cached facts, a fallback scope, or background retries to continue the mutation; the consumer returns a retryable `503` and preserves the user's unsaved input for an explicit retry.
- **FR-032**: The target-facts and roster queries MUST be read-only and MUST NOT create Account audit events. Account Management audits its own Account security or administrative mutations. A consuming Workshop feature owns audit of its responsibility mutations and records its actor Account ID, target Account ID, resource ID, Workshop Location ID, action, outcome, correlation reference, and time without copying provider identity, contact data, or target lifecycle facts into that audit record.
- **FR-033**: The shared Account ID is opaque outside Account Management. REST/UI contracts represent it as a canonical positive-integer string; malformed, padded, decimal, signed, blank, provider-UID, or alternate-ID representations are invalid. Java facade boundary types represent the same identifier without creating a Personnel ID, UUID, or mapping layer. The identifier is not a credential or authorization proof.
- **FR-034**: Bootstrap for an unknown verified UID MUST require a syntactically valid identity email claim. When that email is unverified, the system still creates the Account and initial Profile, then applies the existing verification reminder and verified-email gates. When an unknown identity has no valid email claim, the system MUST create neither Account nor Profile, MUST NOT accept a browser-supplied email as a substitute, and MUST give safe alternative-method/recovery guidance. A pre-existing Account identified by UID remains resolvable without overwriting its email or Profile from a missing email claim.
- **FR-035**: The system MUST store and process all date-time data using the fixed business timezone `Asia/Ho_Chi_Minh` (UTC+7). The MySQL database connection MUST enforce `connectionTimeZone=Asia/Ho_Chi_Minh&forceConnectionTimeZoneToSession=true`, the application JVM default timezone MUST be set to `Asia/Ho_Chi_Minh`, and API serialization MUST output ISO-8601 with `+07:00` offset. All database `DATETIME` columns reflect local Vietnam wall-clock time directly.
- **FR-036**: The system MUST allow only an authenticated owning Member to maintain up to 10 shipping addresses. An address has recipient name, normalized Vietnamese mobile phone, province code/name, commune code/name/type, detail address, directory version, `HOME`/`OFFICE`, default flag and optimistic-lock version. No profile prefill is applied when opening a new address form.
- **FR-037**: Address entry MUST be progressive: recipient name, recipient phone, province/city, commune/ward/special zone and detail address are enabled in that order. Changing a province clears the selected commune and detail address; changing a commune clears the detail address. Save is unavailable until all required fields are valid.
- **FR-038**: The server MUST resolve province and commune names from the active directory using supplied codes, reject a nonexistent code or a commune whose parent province differs, and never persist browser-supplied administrative names. An address from a non-active directory version remains readable but is not automatically remapped.
- **FR-039**: The system MUST enforce at most one default address per Member. The first created address becomes default; designating another atomically replaces the prior default; deleting a default while other addresses exist is rejected; deleting the sole address is allowed. All address mutations serialize per owning account and enforce the quota and default invariant.
- **FR-040**: The system MUST normalize recipient phones by stripping permitted delimiters, converting `+84`/`84` to `0`, then requiring a 10-digit Vietnamese mobile number beginning `03`, `05`, `07`, `08`, or `09`. Recipient name and detail address are outer-trimmed, required and reject ISO control characters.
- **FR-041**: The system MUST expose authenticated lookup of the active directory's provinces and the selected province's communes. The browser loads communes only after province selection and may filter that province's results locally without diacritic or case sensitivity.
- **FR-042**: An Admin MAY create a draft directory version by copying the active version, edit its provinces/communes and `version`, `sourceUrl`, `effectiveFrom` metadata, then activate it after validation. There MUST be exactly one active version; an active version cannot be edited in place; activation records safe metadata only.
- **FR-043**: Address update, delete and default commands MUST carry the currently observed optimistic-lock version. A stale command fails without changing the address/default invariant and returns a conflict outcome requiring reload.
- **FR-044**: The system MUST expose internal `AccountFacade` queries for the default address and an owned address ID, returning an immutable two-level `MemberAddressSnapshot`. A future consumer copies the snapshot into its own record; later address edits/deletes do not mutate that consumer's historical snapshot.
- **FR-041**: The browser MUST apply the shared session and navigation lifecycle in `spec-general.md` to all Account/Profile/Address/Security canonical routes and compatibility aliases. `AuthProvider` is the only session source; a Router-level boundary alone performs terminal cleanup, transient flash creation, and navigation. Protected-route guards show no PII while `loading`, show an unavailable state without navigation when Firebase is unconfigured, and send a resolved signed-out Guest to `/login` with `replace`. `/sign-in` must `replace` to canonical `/login`; successful sign-in always goes to `/`. A valid `403` retains session and routes to Home with a safe notice, while the first session-loss outcome from Firebase or `401` clears private UI state/drafts and routes to Home. This requirement standardizes session guard mechanics only; backend authorization and feature-owned role/scope rules are unchanged.

### Key Entities *(include if feature involves data)*

- **Account**: The business account for one verified identity; holds current login email, fixed role, lifecycle state, location-scope relationship, concurrency version, and security-recovery state.
- **Account Profile**: The owner-editable full name and optional phone attached to one Account.
- **Account Location Assignment**: The relationship between a Staff/Manager Account and authoritative workshop location(s), constrained by the Account role.
- **Internal Account Provisioning**: The retryable record of one Admin request to create and invite a Staff/Manager account, including its setup progress.
- **Account Audit Event**: Redacted evidence of an account/security action and its outcome, retained for support and accountability.
- **Notification Request**: The post-commit request consumed by FE-07 to deliver an invitation or account-change message.
- **Member Address**: The personal shipping destination owned by a Member Account; contains recipient contact, structured administrative division data (levels 1-4), detailed street address, type (`HOME`/`OFFICE`), default flag, and audit timestamps in `Asia/Ho_Chi_Minh`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Each supported registration/sign-in method can create or return exactly one account for the same identity across repeated bootstrap attempts, with no account created for a failed identity flow.
- **SC-002**: A verified active account owner can complete profile save, password-recovery initiation, and verified email synchronization without an operator handling the credential or manually editing the account.
- **SC-003**: Every tested Staff/Manager creation retry results in at most one internal account and one intended identity; no pending internal account can access internal work before activation.
- **SC-004**: Every tested suspension denies the account's next protected action immediately; no restoration succeeds while prior-session invalidation remains incomplete, and a restored user must sign in again.
- **SC-005**: Every tested Admin mutation records a redacted audit event and remains effective when the corresponding notification delivery is delayed or fails.
- **SC-006**: Every documented unauthorized, stale, invalid, or provider-unavailable scenario returns a safe recovery outcome without revealing credentials, provider identifiers, or another account's protected information.
- **SC-007**: Every FE-001 user-facing screen and recovery state meets WCAG 2.2 AA and is verified on the two latest major desktop versions of Chrome, Edge, and Firefox.
- **SC-008**: In 100% of tested address operations, a Member can add up to 10 valid addresses, exactly one default address is maintained, deleting a default address while others exist is prevented, and phone numbers are normalized to 10-digit format.
- **SC-009**: In 100% of tested database timestamp writes, `created_at` and `updated_at` across all tables match Vietnam wall-clock time (`Asia/Ho_Chi_Minh`, UTC+7) without a 7-hour discrepancy.

## Assumptions and dependencies

- A separate frontend foundation supplies routing, identity-client initialization, current-authentication state, and authenticated request transport. FE-001 does not create that foundation or Home/dashboard content.
- FR-028–FR-032 are in the current FE-001 delivery scope. FE-001 must publish their typed internal facade contract before any FE-003 implementation consumes Account facts or roster data; they are not deferred to a follow-up feature.
- Current feature handoff is local-only. Staging/production origins, deployment/rollback, operational observability and production identity-provider setup are a separately approved release scope and are not acceptance gates for this handoff.
- The configured identity provider remains responsible for passwords, verification messages, password reset, external sign-in, and proof of identity. V1 does not require MFA, an additional identity tier, a forced eight-hour re-login, or a separate application password/session store.
- The shared identity gate is the sole backend bearer-verification path. It has an explicit public-route allowlist and a deny-by-default fallback; CORS and CSRF behavior are designed per declared frontend origin and bearer transport rather than disabled globally.
- Database connection configuration is supplied through the approved environment/secret channel with no committed default credential. The previously observed defaults were confirmed as synthetic and not reused, so no credential-rotation incident is required.
- Schema migrations use one repository-wide, append-only sequence of increasing integer versions. Existing applied/merged versions retain their number; an owner checks and reserves the next number before creating a new migration.
- Google sign-in via redirect is active whenever Firebase configuration is available; Facebook sign-in remains deferred for future release. Local manual evidence uses the existing Firebase project/configuration with Google provider enabled, Firebase-authorized `localhost`, and origin `http://localhost:5173`. Popup is unsupported. Failure of Google sign-in does not block email/password sign-in; collision uses the FR-002 password-method guidance and other redirect errors use safe retry/alternative-method guidance. Production Firebase Hosting/domain/`authDomain` is outside this feature revision.
- The Workshop Location owner supplies authoritative active-`workshopLocationId` validation and display data through its permitted boundary; “branch” is display-only and has no separate identifier or mapping.
- FE-07 accepts and delivers post-commit notification requests. It is responsible for mail delivery status/retry, while Account remains authoritative for the account decision and audit record.
- An operations procedure creates the initial Admin identity and bootstraps its Mland Account outside the web UI. That procedure is a dependency, not a self-service feature requirement.
- Existing `plan.md`, `tasks.md`, `data-model.md`, and `contracts/member-profile.openapi.yaml` describe the superseded Member Profile design. Current execution artifacts are replaced directly from this specification before implementation; Git history preserves prior versions, and old task markers must not be treated as current instructions.

## Supersession note

This specification replaces the legacy FE-001 Member Profile/SSR/cookie-session scope at the same feature path. It is a business specification, not evidence that a backend, frontend, migration, identity-provider configuration, or operational bootstrap has been implemented or approved for release.
