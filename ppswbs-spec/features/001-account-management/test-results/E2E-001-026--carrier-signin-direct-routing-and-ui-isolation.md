---
format_version: 2
id: E2E-001-026
feature: 001-account-management
test_level: e2e
test_case_type: N
source_ref: spec.md#FR-003, spec.md#FR-013, spec.md#FR-046, tasks.md#T087
target_url: http://localhost:5173/login
actor_role: Carrier
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: carrier-demo-seed-v188
  account_ref: CARRIER_GHTK_DEMO
  isolation_key: E2E-001-026-20261120
cleanup:
  required: false
  method: none-fixture-seed-retained
---

# E2E-001-026: Đăng nhập tài khoản CARRIER — Điều hướng tức thì tới /carrier, cô lập Topbar và loại trừ dữ liệu Member

**Created**: 2026-11-20  
**Updated**: 2026-11-20  
**Owner**: Phan Sơn (Senior QA Engineer)  

## 1. Scenario Charter

- **User goal**: Đối tác vận chuyển (GHTK) đăng nhập hệ thống bằng email/mật khẩu, kiểm chứng cơ chế điều hướng thẳng tới Cổng vận chuyển chuyên biệt (`/carrier`), không bị chuyển hướng về Trang chủ bán lẻ (`/`) hay dashboard nội bộ của Staff/Manager, và xác minh toàn bộ thanh điều hướng, menu người dùng được cô lập hoàn toàn (không chứa giỏ hàng, menu mua sắm, điểm thưởng hay lối tắt nội bộ).
- **Starting context**: Người dùng là nhân viên đối tác vận chuyển GHTK, bắt đầu tại trang Đăng nhập hệ thống (`http://localhost:5173/login`) với thông tin tài khoản được cấp phát sẵn trong môi trường thử nghiệm.
- **Scope**:
  - Đăng nhập bằng tài khoản đối tác vận chuyển demo V188 (`ghtk.test@mland.vn`).
  - Quan sát điều hướng tức thì sau đăng nhập: Đích đến phải là Cổng đối tác vận chuyển (`/carrier`).
  - Kiểm tra tính đầy đủ của Cổng vận chuyển: Tên đối tác, trạng thái `CARRIER · ACTIVE`, phạm vi toàn quốc và các khối hành động (đơn vận chuyển, bảo mật mật khẩu).
  - Kiểm tra cô lập thanh điều hướng (`MainTopbar`): Chỉ hiển thị liên kết "Cổng vận chuyển", ẩn nút Giỏ hàng, ẩn toàn bộ danh mục mua sắm và tính năng cá nhân của Member.
  - Kiểm tra menu người dùng (User chip dropdown): Chỉ chứa 2 tùy chọn hợp lệ là "Cổng đối tác vận chuyển" và "Đổi mật khẩu".
- **Out of scope**: Hành động giao nhận kiện hàng chi tiết trong hệ thống logistics đối tác; sửa đổi thông tin thanh toán đơn hàng.

## 2. Preconditions and Test Data

- [ ] Các dịch vụ trong `health_urls` (Frontend `http://localhost:5173`, Backend `http://localhost:8080`) phản hồi sẵn sàng.
- [ ] Tài khoản đối tác vận chuyển V188 đã tồn tại sẵn trong hệ thống:
  - Email: `ghtk.test@mland.vn`
  - Mật khẩu: Lấy từ secret store / fixture demo (`CarrierTest@123456`)
  - Vai trò: `CARRIER`, Trạng thái: `ACTIVE`
  - Tên đối tác: `Đối tác vận chuyển (GHTK)`, Số điện thoại: `0900000000`
- [ ] Starting session: Chưa đăng nhập (Guest).

## 3. Exploratory Step Contract

### Step 1 — Nhập thông tin và thực hiện đăng nhập với vai trò CARRIER

- **User charter**: Đối tác vận chuyển nhập thông tin xác thực để truy cập vào hệ thống.
- **Explore & act**: Quan sát biểu mẫu đăng nhập, tìm ô nhập Email và Mật khẩu. Điền thông tin tài khoản đối tác vận chuyển demo, sau đó kích hoạt nút hành động "Đăng nhập".
- **Expected**:
  - Biểu mẫu tiếp nhận thông tin và tiến hành xác thực danh tính với hệ thống.
  - Quá trình đăng nhập diễn ra thành công không có thông báo lỗi xác thực.
- **Decision rule**:
  - `CONTINUE`: Thông tin xác thực được chấp thuận và bắt đầu quá trình chuyển trang.
  - `LOG_AND_CONTINUE`: Đăng nhập thành công nhưng thời gian phản hồi giao diện chậm hơn bình thường.
  - `STOP`: Xuất hiện lỗi sai thông tin đăng nhập, hoặc hệ thống báo lỗi không kết nối được dịch vụ xác thực.

### Step 2 — Xác minh đích đến sau đăng nhập chuyển thẳng tới Cổng đối tác vận chuyển

- **User charter**: Đối tác vận chuyển quan sát đích đến điều hướng của hệ thống ngay sau khi đăng nhập thành công.
- **Explore & act**: Quan sát URL trên thanh địa chỉ trình duyệt và nội dung trang được tải lên.
- **Expected**:
  - Trình duyệt điều hướng thẳng tới đường dẫn Cổng đối tác vận chuyển (`http://localhost:5173/carrier`).
  - Tuyệt đối không bị điều hướng về Trang chủ bán lẻ (`/`) hoặc các trang nội bộ của Staff/Manager (`/staff/home`, `/manager/home`).
  - Tiêu đề trang hiển thị rõ ràng: "Cổng đối tác vận chuyển (GHTK)".
- **Decision rule**:
  - `CONTINUE`: Người dùng hạ cánh chính xác tại Cổng đối tác vận chuyển với URL `/carrier`.
  - `LOG_AND_CONTINUE`: Hạ cánh đúng `/carrier` nhưng có hiện tượng nhấp nháy chuyển trang qua URL trung gian.
  - `STOP`: Người dùng bị đẩy về Trang chủ bán lẻ (`/`), hoặc báo lỗi không tìm thấy trang (404).

### Step 3 — Khám phá và kiểm tra thông tin định danh trên Cổng vận chuyển

- **User charter**: Đối tác vận chuyển kiểm tra các thông tin hồ sơ và các khu vực tác vụ hiển thị trên Cổng.
- **Explore & act**: Đọc các bảng và thẻ thông tin trên trang Cổng đối tác vận chuyển.
- **Expected**:
  - Huy hiệu vai trò hiển thị chính xác: `CARRIER · ACTIVE`.
  - Bảng thông tin định danh hiển thị: Email đăng nhập `ghtk.test@mland.vn`, Vai trò hệ thống `CARRIER`, Phạm vi cơ sở: "Toàn quốc (Không giới hạn theo cơ sở đơn lẻ)".
  - Hiển thị hai thẻ hành động chuyên biệt: "Đơn vận chuyển cần giao" (có liên kết tiếp nhận đơn hàng) và "Bảo mật & Mật khẩu" (có liên kết đổi mật khẩu).
- **Decision rule**:
  - `CONTINUE`: Toàn bộ các thông tin định danh và thẻ chức năng hiển thị đầy đủ, chính xác.
  - `LOG_AND_CONTINUE`: Thông tin hiển thị đúng nhưng thiếu biểu tượng cảm xúc hoặc cách căn chỉnh khoảng cách chưa đồng đều.
  - `STOP`: Bảng thông tin hiển thị sai vai trò, sai email hoặc xuất hiện trường gán cơ sở làm việc cụ thể.

### Step 4 — Kiểm tra cô lập thanh điều hướng chính (MainTopbar Isolation)

- **User charter**: Đối tác vận chuyển kiểm tra thanh điều hướng trên cùng để đảm bảo các tính năng mua sắm và giỏ hàng của khách hàng không bị rò rỉ.
- **Explore & act**: Quan sát toàn bộ thanh điều hướng ngang trên cùng của giao diện.
- **Expected**:
  - Thanh điều hướng chính chỉ hiển thị duy nhất liên kết "Cổng vận chuyển" ở trạng thái đang kích hoạt (active).
  - Không xuất hiện bất kỳ liên kết mua sắm nào của khách hàng: Workshop, Cấu hình nhẫn, Ready Rings, Custom, Kho Voucher, Tra cứu vé, Trợ lý AI, Đánh giá, Tư vấn.
  - Nút Giỏ hàng cùng số lượng sản phẩm hoàn toàn biến mất khỏi thanh điều hướng.
  - Không hiển thị điểm thưởng tích lũy (loyalty points) trên thanh công cụ.
- **Decision rule**:
  - `CONTINUE`: Thanh điều hướng được cô lập hoàn toàn, không có bất kỳ yếu tố thương mại bán lẻ nào.
  - `LOG_AND_CONTINUE`: Giỏ hàng đã ẩn nhưng một liên kết thông tin chung (như Đánh giá) vẫn còn sót lại.
  - `STOP`: Nút Giỏ hàng hoặc các menu đặt hàng mua sắm xuất hiện trên thanh điều hướng của CARRIER.

### Step 5 — Mở menu tài khoản và xác minh danh sách tùy chọn được bảo vệ

- **User charter**: Đối tác vận chuyển mở menu cá nhân để kiểm tra các tùy chọn quản lý tài khoản được cho phép.
- **Explore & act**: Tìm và nhấp vào biểu tượng/tên người dùng trên thanh điều hướng để mở danh sách menu thả xuống (dropdown). Quan sát các mục bên trong menu.
- **Expected**:
  - Menu người dùng mở ra và chỉ chứa đúng hai mục tác vụ nghiệp vụ: "🚚 Cổng đối tác vận chuyển" và "🔑 Đổi mật khẩu".
  - Hoàn toàn KHÔNG xuất hiện các mục cá nhân của khách hàng: Hồ sơ cá nhân, Mẫu nhẫn đã lưu, Đơn hàng của tôi, Điểm thưởng tích lũy, Voucher của tôi, Tra cứu vé.
  - Hoàn toàn KHÔNG xuất hiện các lối tắt quản trị của nhân viên nội bộ (Staff Vận hành, Quản lý sản phẩm, Roster).
- **Decision rule**:
  - `CONTINUE`: Menu người dùng được tinh gọn chính xác, chỉ cung cấp Cổng vận chuyển và Đổi mật khẩu.
  - `LOG_AND_CONTINUE`: Tên hiển thị trong phần đầu menu bị ngắt dòng hoặc thiếu avatar.
  - `STOP`: Menu người dùng để lộ liên kết "Hồ sơ cá nhân", "Đơn hàng của tôi" hoặc các cổng nghiệp vụ Staff/Manager.

## 4. Evidence and Findings

- Capture a screenshot for every `LOG_AND_CONTINUE` or `STOP` outcome.
- A `LOG_AND_CONTINUE` record contains: problem description, reproduction steps, screenshot path, and (when confirmed defect) its `BUG-LOG.md` reference.
- Capture console or network output only when it materially helps classify or reproduce the finding.

## 5. Execution History

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-20T18:08:50+07:00
- Executed by: Senior QA Engineer
- Browser tool: browsermcp
- Final step: Step 5 — Mở menu tài khoản và xác minh danh sách tùy chọn được bảo vệ
- Actual result:
  - Sign-in with ghtk.test@mland.vn succeeded without errors.
  - Browser routed immediately to canonical portal URL http://localhost:5173/carrier.
  - Carrier Portal rendered correct metadata: CARRIER · ACTIVE badge, ghtk.test@mland.vn, nationwide scope, and 2 action cards.
  - MainTopbar strictly isolated: nav bar renders only 'Cổng vận chuyển'; cart icon and loyalty points completely absent.
  - User dropdown menu strictly isolated: contains only '🚚 Cổng đối tác vận chuyển', '🔑 Đổi mật khẩu', and '🚪 Đăng xuất'; zero Member or internal staff links.
- Findings: NONE
- Evidence screenshots:
  - screenshots/E2E-001-026--carrier-portal-isolated.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
