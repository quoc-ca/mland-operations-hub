---
format_version: 2
id: E2E-001-025
feature: 001-account-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-011, spec.md#FR-012, tasks.md#T086
target_url: http://localhost:5173/
actor_role: Admin
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: admin-carrier-provisioning
  account_ref: ADMIN_001
  isolation_key: E2E-001-025-20261120
cleanup:
  required: false
  method: none-ephemeral-test-account
---

# E2E-001-025: Quản trị cấp tài khoản CARRIER — Form guard, mật khẩu chỉ-ghi, loại trừ cơ sở và kích hoạt trực tiếp ACTIVE

**Created**: 2026-11-20  
**Updated**: 2026-11-20  
**Owner**: Phan Sơn (Senior QA Engineer)  

## 1. Scenario Charter

- **User goal**: Quản trị viên (Admin) khởi tạo tài khoản đối tác giao vận GHTK với vai trò cố định `CARRIER` ở trạng thái `ACTIVE` trực tiếp, xác minh tính toàn vẹn của form bảo mật (mật khẩu chỉ-ghi write-only, loại trừ phân công cơ sở, loại trừ thư mời thiết lập) và kiểm thử các điểm gãy biên (dữ liệu rỗng, mật khẩu yếu, email sai định dạng, email trùng lặp).
- **Starting context**: Quản trị viên bắt đầu từ Trang chủ công khai (`http://localhost:5173/`) sau khi đã xác thực quyền Admin, sử dụng các yếu tố điều hướng hiển thị thực tế trên giao diện để tiếp cận module quản trị tài khoản.
- **Scope**:
  - Khám phá và điều hướng tới giao diện Cấp tài khoản nhân sự & đối tác từ thanh điều hướng khả dụng.
  - Chuyển đổi vai trò sang CARRIER và quan sát tính phản ứng của form: ẩn hoàn toàn phần chọn cơ sở, ẩn checkbox gửi thư mời, hiển thị trường Email bất biến và Mật khẩu khởi tạo kèm chú thích bảo mật.
  - Phân tích rủi ro đối nghịch: Submit form rỗng, kiểm tra validation mật khẩu < 6 ký tự, email sai cú pháp, thử nghiệm email trùng lặp với tài khoản đã tồn tại.
  - Hoàn tất cấp phát hợp lệ: Kiểm tra trạng thái `ACTIVE (Đang hoạt động)`, ghi chú mật khẩu chỉ-ghi, hướng dẫn bàn giao out-of-band và bảo đảm mật khẩu không bao giờ xuất hiện lại trong giao diện.
- **Out of scope**: Quy trình giao nhận vận đơn vật lý thực tế của GHTK; logic đối soát phí vận chuyển trong checkout (FE-08).

## 2. Preconditions and Test Data

- [ ] Các dịch vụ trong `health_urls` (Frontend `http://localhost:5173`, Backend `http://localhost:8080`) phản hồi sẵn sàng.
- [ ] Fixture `admin-carrier-provisioning` sẵn sàng với `isolation_key` tương ứng.
- [ ] Tài khoản kiểm thử vai trò `ADMIN` đã được xác thực phiên làm việc.
- [ ] Dữ liệu email tồn tại sẵn trong hệ thống để thử xung đột: `admin@mland.vn`.
- [ ] Dữ liệu email hợp lệ chưa từng tồn tại: `carrier.e2e.test@ghtk.vn`.
- [ ] Starting session: Đã đăng nhập vai trò Admin.

## 3. Exploratory Step Contract

### Step 1 — Khám phá và tiếp cận biểu mẫu Cấp tài khoản nhân sự & đối tác

- **User charter**: Quản trị viên tìm và mở biểu mẫu tạo tài khoản mới từ giao diện điều hướng thực tế của hệ thống.
- **Explore & act**: Quan sát thanh điều hướng trên cùng hoặc menu người dùng của Admin, tìm liên kết dẫn tới danh mục quản lý tài khoản. Từ trang danh sách tài khoản, tìm nút hoặc liên kết thêm mới tài khoản (như "Cấp tài khoản mới", "Thêm tài khoản", "Tạo tài khoản") và kích hoạt để mở form.
- **Expected**:
  - Tiêu đề trang hiển thị mục đích cấp hoặc tạo tài khoản cho nhân sự / đối tác.
  - Biểu mẫu xuất hiện với các trường nhập thông tin định danh và trường lựa chọn vai trò nội bộ.
- **Decision rule**:
  - `CONTINUE`: Form tạo tài khoản hiển thị đầy đủ, sẵn sàng cho thao tác nhập liệu.
  - `LOG_AND_CONTINUE`: Mở được form nhưng nhãn nút bấm hoặc tiêu đề trang có khác biệt nhỏ về từ ngữ so với tài liệu mô tả.
  - `STOP`: Không tìm thấy lối vào form tạo tài khoản, hoặc hệ thống báo lỗi từ chối quyền truy cập (403) đối với tài khoản Admin.

### Step 2 — Chuyển vai trò sang "Đối tác vận chuyển (GHTK)" và xác minh tính thích ứng của form

- **User charter**: Quản trị viên chọn vai trò đối tác vận chuyển và xác nhận giao diện thích ứng đúng ranh giới nghiệp vụ của CARRIER.
- **Explore & act**: Tìm trường chọn vai trò nội bộ, chọn giá trị "Đối tác vận chuyển (GHTK)" (hoặc CARRIER). Quan sát sự thay đổi của các trường trên biểu mẫu.
- **Expected**:
  - Xuất hiện trường "Email đăng nhập (bất biến)" và "Mật khẩu khởi tạo" dành riêng cho đối tác.
  - Khu vực phân công cơ sở làm việc (Workshop Locations) biến mất hoàn toàn khỏi biểu mẫu.
  - Tùy chọn gửi thông báo hướng dẫn thiết lập tài khoản qua email biến mất hoàn toàn.
  - Chú thích ngữ nghĩa vai trò hiển thị rõ: Đối tác vận chuyển (GHTK) được kích hoạt trực tiếp ở trạng thái ACTIVE; không phân công cơ sở làm việc và không có thư mời kích hoạt.
- **Decision rule**:
  - `CONTINUE`: Form thích ứng chính xác theo đặc tả CARRIER (hiển thị email/mật khẩu, ẩn cơ sở và ẩn checkbox thư mời).
  - `LOG_AND_CONTINUE`: Các trường hiển thị đúng nhưng phần giải thích chú thích vai trò bị thiếu hoặc sai chính tả.
  - `STOP`: Khu vực phân công cơ sở vẫn tồn tại hoặc không xuất hiện trường nhập mật khẩu khởi tạo cho CARRIER.

### Step 3 — Thử nghiệm dữ liệu rỗng và vi phạm định dạng biên (Negative Form Validation)

- **User charter**: Quản trị viên kiểm chứng hàng rào kiểm soát lỗi form phía trình duyệt khi người dùng để trống hoặc nhập dữ liệu sai quy cách.
- **Explore & act**: Để trống toàn bộ các ô nhập và bấm nút gửi form. Tiếp theo, nhập email sai định dạng ("email-sai-dinh-dang"), mật khẩu ngắn dưới 6 ký tự ("12345"), họ tên hợp lệ và số điện thoại hợp lệ, sau đó tiếp tục bấm gửi form.
- **Expected**:
  - Khi form rỗng: Hệ thống hiển thị cảnh báo lỗi bắt buộc cho các trường Họ tên, Email, Mật khẩu khởi tạo và Số điện thoại.
  - Khi dữ liệu sai quy cách: Hệ thống hiển thị thông báo "Địa chỉ email không đúng định dạng." và "Mật khẩu phải chứa ít nhất 6 ký tự.".
  - Biểu mẫu không gửi request không hợp lệ và giữ nguyên các nội dung đã nhập vào ô Họ tên, Số điện thoại.
- **Decision rule**:
  - `CONTINUE`: Mọi vi phạm dữ liệu biên đều bị chặn lại tại biểu mẫu với thông báo lỗi rõ ràng bên dưới từng trường.
  - `LOG_AND_CONTINUE`: Có thông báo lỗi chặn submit nhưng vị trí hiển thị lỗi chưa tối ưu hoặc câu chữ chưa thân thiện.
  - `STOP`: Hệ thống gửi request đi mà không chặn dữ liệu sai, hoặc form bị mất sạch dữ liệu sau khi báo lỗi.

### Step 4 — Thử nghiệm xung đột email trùng lặp (Duplicate Email Conflict)

- **User charter**: Quản trị viên kiểm tra hệ thống từ chối cấp phát tài khoản CARRIER khi email đã được đăng ký bởi một tài khoản khác trong hệ thống.
- **Explore & act**: Nhập địa chỉ email đã có sẵn trong cơ sở dữ liệu (`admin@mland.vn`), nhập mật khẩu hợp lệ từ 6 ký tự trở lên, nhập tên đối tác và số điện thoại hợp lệ, rồi kích hoạt nút tạo tài khoản.
- **Expected**:
  - Hệ thống từ chối thao tác và hiển thị thông báo cảnh báo lỗi an toàn (Conflict / email đã tồn tại).
  - Biểu mẫu giữ nguyên các trường đã nhập (họ tên, số điện thoại), không làm crash giao diện và không lộ vết lỗi kỹ thuật hệ thống.
- **Decision rule**:
  - `CONTINUE`: Xuất hiện cảnh báo từ chối trùng lặp email hợp lệ, biểu mẫu duy trì trạng thái ổn định.
  - `LOG_AND_CONTINUE`: Báo lỗi thành công nhưng thông báo lỗi hiển thị chung chung không nêu rõ nguyên nhân do trùng email.
  - `STOP`: Hệ thống cho phép tạo đè lên tài khoản đang có, hoặc backend trả về mã lỗi 500 chưa được xử lý an toàn.

### Step 5 — Hoàn tất cấp phát tài khoản CARRIER hợp lệ và kiểm tra tính chất chỉ-ghi (Write-Only Password)

- **User charter**: Quản trị viên nhập thông tin đối tác hợp lệ mới, hoàn thành khởi tạo tài khoản và xác minh kết quả bàn giao an toàn.
- **Explore & act**: Điền email mới (`carrier.e2e.test@ghtk.vn`), mật khẩu khởi tạo an toàn ("GhtkPass@2026"), tên đối tác ("Công ty CP Giao Hàng Tiết Kiệm") và số điện thoại liên hệ ("0909999888"). Bấm nút tạo tài khoản và quan sát màn hình kết quả.
- **Expected**:
  - Màn hình thông báo "Khởi tạo thành công!" xuất hiện.
  - Trạng thái tài khoản hiển thị chính xác là `ACTIVE (Đang hoạt động)`.
  - Thông tin định danh hiển thị đúng email `carrier.e2e.test@ghtk.vn` và vai trò `CARRIER`.
  - Hiển thị ghi chú rõ ràng: Mật khẩu khởi tạo mang tính chất chỉ-ghi (write-only), không được lưu trữ và cần bàn giao trực tiếp cho đối tác.
  - Mật khẩu khởi tạo tuyệt đối KHÔNG xuất hiện trên màn hình kết quả và không còn lưu trong ô nhập liệu.
- **Decision rule**:
  - `CONTINUE`: Tài khoản được tạo thành công ở trạng thái ACTIVE kèm theo ghi chú bảo mật write-only đầy đủ.
  - `LOG_AND_CONTINUE`: Khởi tạo thành công nhưng thiếu thông điệp nhắc nhở bàn giao out-of-band.
  - `STOP`: Tài khoản bị khởi tạo ở trạng thái `PENDING_SETUP` hoặc mật khẩu khởi tạo bị in lộ ra trên giao diện.

## 4. Evidence and Findings

- Capture a screenshot for every `LOG_AND_CONTINUE` or `STOP` outcome.
- A `LOG_AND_CONTINUE` record contains: problem description, reproduction steps, screenshot path, and (when confirmed defect) its `BUG-LOG.md` reference.
- Capture console or network output only when it materially helps classify or reproduce the finding.

## 5. Execution History

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-20T18:15:55+07:00
- Executed by: Senior QA Engineer
- Browser tool: browsermcp
- Final step: Step 5 — Hoàn tất cấp phát tài khoản CARRIER hợp lệ và kiểm tra tính chất chỉ-ghi (Write-Only Password)
- Actual result:
  - Form adapted dynamically upon selecting CARRIER: Workshop locations and invitation checkbox were hidden; login email and write-only password fields rendered.
  - Client form boundary validation prevented empty submits and flagged invalid email / short password (< 6 chars).
  - Duplicate email submit (admin.test@mland.vn) rejected by backend with 409 Conflict.
  - Valid CARRIER created with accountId #6, role CARRIER, status ACTIVE, and write-only guidance. Password never displayed on summary screen.
- Findings: NONE
- Evidence screenshots:
  - screenshots/E2E-001-025--carrier-provisioning-success.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
