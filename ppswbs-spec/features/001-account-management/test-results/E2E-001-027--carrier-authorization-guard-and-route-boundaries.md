---
format_version: 2
id: E2E-001-027
feature: 001-account-management
test_level: e2e
test_case_type: A
source_ref: spec.md#FR-003, spec.md#FR-017, spec.md#FR-046, tasks.md#T087
target_url: http://localhost:5173/
actor_role: Carrier
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: carrier-and-member-fixtures
  account_ref: MEMBER_001, CARRIER_GHTK_DEMO
  isolation_key: E2E-001-027-20261120
cleanup:
  required: false
  method: none-client-state-only
---

# E2E-001-027: Kiểm tra hàng rào phân quyền CARRIER — Chặn Member truy cập /carrier, chặn CARRIER tự sửa profile / địa chỉ và bảo vệ route Admin

**Created**: 2026-11-20  
**Updated**: 2026-11-20  
**Owner**: Phan Sơn (Senior QA Engineer)  

## 1. Scenario Charter

- **User goal**: Kiểm chứng hàng rào kiểm soát quyền truy cập đa chiều (Authorization Gate & Route Boundary Defense): Ngăn chặn khách hàng bán lẻ (MEMBER) tiếp cận Cổng đối tác vận chuyển; Ngăn chặn đối tác vận chuyển (CARRIER) tự ý chỉnh sửa thông tin hồ sơ/email của khách hàng thông qua việc tự động đẩy về `/carrier`; Ngăn chặn CARRIER leo thang đặc quyền vào khu vực Quản trị Admin; Đồng thời bảo toàn luồng tự phục vụ đổi mật khẩu hợp lệ với cơ chế xác thực lại tại chỗ (in-place re-auth).
- **Starting context**: Người kiểm thử luân phiên sử dụng hai danh tính: Tài khoản khách hàng thành viên `MEMBER` và Tài khoản đối tác vận chuyển `CARRIER`, bắt đầu từ Trang chủ công khai (`http://localhost:5173/`).
- **Scope**:
  - Giai đoạn 1 (Actor MEMBER): Khách hàng đăng nhập, sau đó thử truy cập trực tiếp vào Cổng đối tác vận chuyển (`/carrier`). Xác nhận cảnh báo lỗi từ chối quyền và nút quay về trang chủ.
  - Giai đoạn 2 (Actor CARRIER): Đối tác đăng nhập, sau đó thử truy cập vào các trang hồ sơ cá nhân của khách hàng (`/profile`, `/edit-profile`). Xác nhận route guard phát hiện vai trò CARRIER và điều hướng cưỡng bức (replace) quay lại `/carrier`.
  - Giai đoạn 3 (Actor CARRIER): Đối tác thử truy cập các trang quản trị của Admin (`/admin/accounts`). Xác nhận bị `AdminRouteGuard` chặn lại.
  - Giai đoạn 4 (Actor CARRIER): Đối tác truy cập vào luồng đổi mật khẩu tự phục vụ (`/security/password`). Xác nhận form đổi mật khẩu cho phép thao tác mà không bị redirect, và kiểm chứng cơ chế xác thực khi nhập sai mật khẩu cũ.
- **Out of scope**: Quy trình khôi phục tài khoản qua email của Firebase; thao tác thay đổi email liên kết của Member.

## 2. Preconditions and Test Data

- [ ] Các dịch vụ trong `health_urls` (Frontend `http://localhost:5173`, Backend `http://localhost:8080`) phản hồi sẵn sàng.
- [ ] Tài khoản khách hàng thành viên `MEMBER`: `customer.test@mland.vn` / `MemberPass@123`.
- [ ] Tài khoản đối tác vận chuyển `CARRIER`: `ghtk.test@mland.vn` / `CarrierTest@123456`.
- [ ] Starting session: Đăng nhập ban đầu bằng tài khoản MEMBER.

## 3. Exploratory Step Contract

### Step 1 — Khách hàng MEMBER thử truy cập vào Cổng đối tác vận chuyển

- **User charter**: Người dùng thành viên kiểm tra xem hệ thống có bảo vệ Cổng vận chuyển khỏi sự tiếp cận của khách hàng thông thường hay không.
- **Explore & act**: Đăng nhập bằng tài khoản MEMBER. Sau khi đăng nhập, thực hiện truy cập trực tiếp vào đường dẫn Cổng vận chuyển (`/carrier`) thông qua thanh địa chỉ trình duyệt.
- **Expected**:
  - Hệ thống chặn hiển thị nội dung vận đơn của Cổng đối tác.
  - Xuất hiện thông báo cảnh báo lỗi truy cập rõ ràng: "Tài khoản của bạn không có quyền truy cập Cổng đối tác vận chuyển.".
  - Cung cấp nút hoặc liên kết điều hướng an toàn "Quay lại trang chủ" để người dùng thoát khỏi trang bị hạn chế.
- **Decision rule**:
  - `CONTINUE`: Khách hàng bị chặn truy cập hoàn toàn và nhìn thấy thông báo lỗi quyền kèm nút về trang chủ.
  - `LOG_AND_CONTINUE`: Bị chặn nhưng thông báo lỗi hiển thị không đúng định dạng cảnh báo chuẩn.
  - `STOP`: Khách hàng nhìn thấy nội dung giao diện Cổng vận chuyển hoặc danh sách vận đơn của GHTK.

### Step 2 — Đối tác CARRIER thử truy cập vào trang Hồ sơ cá nhân của khách hàng

- **User charter**: Đối tác vận chuyển kiểm tra xem hệ thống có ngăn chặn CARRIER tự ý can thiệp vào các trang tự sửa hồ sơ của khách hàng hay không.
- **Explore & act**: Đăng xuất khỏi tài khoản MEMBER và đăng nhập vào tài khoản CARRIER. Sau khi vào Cổng vận chuyển, thử nhập đường dẫn trang hồ sơ cá nhân (`/profile`) hoặc trang chỉnh sửa hồ sơ (`/edit-profile`) trên thanh địa chỉ.
- **Expected**:
  - Cơ chế bảo vệ vai trò (Role navigation guard) lập tức phát hiện vai trò người dùng là `CARRIER`.
  - Hệ thống thực hiện điều hướng cưỡng bức (replace) đưa người dùng quay trở lại ngay lập tức Cổng đối tác vận chuyển (`/carrier`).
  - Người dùng không thể ở lại trang `/profile` hay nhìn thấy biểu mẫu tự sửa họ tên, số điện thoại, sổ địa chỉ.
- **Decision rule**:
  - `CONTINUE`: Hệ thống tự động chuyển hướng người dùng quay lại `/carrier`, không cho phép truy cập trang hồ sơ cá nhân.
  - `LOG_AND_CONTINUE`: Người dùng bị chuyển hướng về `/carrier` nhưng xuất hiện màn hình trắng trong tích tắc trước khi chuyển.
  - `STOP`: Đối tác CARRIER truy cập được vào trang chỉnh sửa thông tin hồ sơ `/profile` hoặc `/edit-profile`.

### Step 3 — Đối tác CARRIER thử truy cập vào các trang Quản trị nội bộ Admin

- **User charter**: Đối tác vận chuyển kiểm tra hệ thống ngăn chặn việc leo thang đặc quyền vào khu vực quản lý tài khoản của Quản trị viên.
- **Explore & act**: Trong phiên đăng nhập của CARRIER, thử nhập đường dẫn khu vực quản trị tài khoản (`/admin/accounts` hoặc `/admin/roles`) trên thanh địa chỉ.
- **Expected**:
  - Bộ bảo vệ tuyến đường quản trị (`AdminRouteGuard`) chặn yêu cầu truy cập.
  - Người dùng không thể nhìn thấy danh sách tài khoản toàn hệ thống hay danh mục cấu hình vai trò.
  - Hệ thống hiển thị trạng thái từ chối truy cập (403 / Không có quyền) hoặc chuyển hướng an toàn.
- **Decision rule**:
  - `CONTINUE`: Quyền quản trị Admin được bảo vệ tuyệt đối, CARRIER không thể xem danh sách tài khoản.
  - `LOG_AND_CONTINUE`: Bị chặn nhưng thông báo từ chối truy cập hiển thị bằng tiếng Anh thay vì tiếng Việt chuẩn.
  - `STOP`: Đối tác CARRIER xem được dữ liệu danh sách tài khoản hoặc truy cập được vào các trang thuộc `/admin/*`.

### Step 4 — Đối tác CARRIER thực hiện luồng đổi mật khẩu tự phục vụ hợp lệ

- **User charter**: Đối tác vận chuyển kiểm tra quyền tự đổi mật khẩu định kỳ của mình và xác minh cơ chế phòng thủ khi nhập sai mật khẩu cũ.
- **Explore & act**: Từ Cổng đối tác vận chuyển, nhấp vào liên kết "Đổi mật khẩu tài khoản" trên thẻ Bảo mật (hoặc mở menu người dùng chọn "Đổi mật khẩu", dẫn tới `/security/password`). Tại biểu mẫu đổi mật khẩu, thử nhập mật khẩu hiện tại sai, mật khẩu mới hợp lệ, và bấm lưu mật khẩu.
- **Expected**:
  - Trang đổi mật khẩu tải lên bình thường, KHÔNG bị điều hướng về `/carrier` (vì đây là tác vụ tự phục vụ hợp lệ duy nhất của CARRIER).
  - Khi nhập sai mật khẩu hiện tại: Hệ thống kích hoạt cơ chế xác thực lại tại chỗ (in-place re-auth) và báo lỗi rõ ràng: "Mật khẩu hiện tại không chính xác.".
  - Ô mật khẩu hiện tại được dọn sạch, trong khi ô mật khẩu mới được giữ nguyên để người dùng không phải nhập lại. Phiên đăng nhập của người dùng vẫn được bảo toàn.
- **Decision rule**:
  - `CONTINUE`: Biểu mẫu đổi mật khẩu hoạt động ổn định, bắt đúng lỗi mật khẩu cũ sai mà không làm đứt phiên đăng nhập.
  - `LOG_AND_CONTINUE`: Báo lỗi đúng nhưng cả ba ô mật khẩu đều bị xóa sạch khiến người dùng phải nhập lại từ đầu.
  - `STOP`: Trang đổi mật khẩu bị chặn truy cập hoặc bị chuyển hướng về `/carrier`, hoặc việc nhập sai mật khẩu cũ làm người dùng bị đăng xuất ngay lập tức.

## 4. Evidence and Findings

- Capture a screenshot for every `LOG_AND_CONTINUE` or `STOP` outcome.
- A `LOG_AND_CONTINUE` record contains: problem description, reproduction steps, screenshot path, and (when confirmed defect) its `BUG-LOG.md` reference.
- Capture console or network output only when it materially helps classify or reproduce the finding.

## 5. Execution History

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-20T18:17:45+07:00
- Executed by: Senior QA Engineer
- Browser tool: browsermcp
- Final step: Step 4 — Đối tác CARRIER thực hiện luồng đổi mật khẩu tự phục vụ hợp lệ
- Actual result:
  - Step 1: Member attempting to access /carrier was blocked with alert 'Tài khoản của bạn không có quyền truy cập Cổng đối tác vận chuyển.' and safe Home link.
  - Step 2: Carrier attempting to access /profile and /edit-profile was immediately forced back to canonical /carrier via replace navigation.
  - Step 3: Carrier attempting to access /admin/accounts was blocked by AdminRouteGuard with 403 Forbidden screen.
  - Step 4: Carrier accessing /security/password rendered dedicated change password form without redirect; invalid current password triggered in-place re-auth rejection 'Mật khẩu hiện tại không chính xác.', cleared current password while retaining new password inputs, and preserved session.
- Findings: NONE
- Evidence screenshots:
  - screenshots/E2E-001-027--member-blocked-from-carrier.jpg
  - screenshots/E2E-001-027--carrier-redirected-from-profile.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
