---
format_version: 2
id: E2E-001-028
feature: 001-account-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-009, spec.md#FR-017, spec.md#FR-019, spec.md#FR-020, tasks.md#T086
target_url: http://localhost:5173/
actor_role: Admin
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: admin-and-carrier-lifecycle
  account_ref: ADMIN_001, CARRIER_GHTK_DEMO
  isolation_key: E2E-001-028-20261120
cleanup:
  required: true
  method: restore-carrier-to-active
---

# E2E-001-028: Vòng đời quản trị CARRIER — Lọc danh sách, bất biến vai trò, Admin bảo trì hồ sơ, tạm khóa và khôi phục

**Created**: 2026-11-20  
**Updated**: 2026-11-20  
**Owner**: Phan Sơn (Senior QA Engineer)  

## 1. Scenario Charter

- **User goal**: Quản trị viên (Admin) thực hiện toàn vẹn vòng đời quản trị đối tác vận chuyển: Lọc danh sách theo vai trò CARRIER, xác minh tính bất biến của vai trò và phạm vi hoạt động (không gán cơ sở, khóa hoàn toàn form đổi vai trò, không có nút gửi lại thư mời), bảo trì hồ sơ đối tác (cập nhật Họ tên và Số điện thoại, giữ email bất biến), thực thi Tạm khóa (Suspend) và Khôi phục (Restore), đồng thời kiểm chứng ảnh hưởng hiển thị trực tiếp trên Cổng vận chuyển của đối tác khi bị tạm khóa.
- **Starting context**: Quản trị viên đã đăng nhập hệ thống, bắt đầu từ Trang chủ công khai (`http://localhost:5173/`) và điều hướng tới giao diện Quản trị danh sách tài khoản bằng các thành phần hiển thị trên màn hình.
- **Scope**:
  - Tiếp cận danh sách tài khoản Admin và sử dụng bộ lọc vai trò để chọn `CARRIER`.
  - Mở chi tiết tài khoản CARRIER: Xác nhận phạm vi cơ sở hiển thị "Không áp dụng (Đối tác giao vận toàn quốc)", ghi chú vai trò cố định, ẩn hoàn toàn form phân công vai trò & cơ sở và ẩn nút gửi lại thư mời.
  - Bảo trì hồ sơ: Cập nhật thông tin Họ tên và Số điện thoại của đối tác; xác nhận lưu thành công và email đăng nhập không thể chỉnh sửa.
  - Tạm khóa (Suspend): Nhập lý do bắt buộc và chuyển trạng thái tài khoản sang `SUSPENDED`.
  - Kiểm tra phản ứng Cổng vận chuyển: Truy cập Cổng vận chuyển `/carrier` và xác minh thông báo cảnh báo đỏ báo tài khoản bị tạm khóa.
  - Khôi phục (Restore): Quản trị viên thực hiện khôi phục tài khoản trở lại trạng thái `ACTIVE` bình thường.
- **Out of scope**: Quy trình giải quyết tranh chấp hợp đồng giao vận pháp lý; xóa vĩnh viễn tài khoản trong cơ sở dữ liệu.

## 2. Preconditions and Test Data

- [ ] Các dịch vụ trong `health_urls` (Frontend `http://localhost:5173`, Backend `http://localhost:8080`) phản hồi sẵn sàng.
- [ ] Tài khoản Quản trị viên `ADMIN` đã đăng nhập và có đầy đủ quyền quản trị tài khoản.
- [ ] Tài khoản đối tác vận chuyển demo `ghtk.test@mland.vn` đang ở trạng thái ban đầu `ACTIVE`.
- [ ] Starting session: Đã đăng nhập vai trò Admin.

## 3. Exploratory Step Contract

### Step 1 — Khám phá danh sách tài khoản và lọc theo vai trò CARRIER

- **User charter**: Quản trị viên tìm danh sách tài khoản và lọc nhanh các tài khoản đối tác vận chuyển.
- **Explore & act**: Quan sát giao diện điều hướng, tìm đường dẫn tới trang Quản trị danh sách tài khoản. Tại khu vực bộ lọc trên đầu danh sách, tìm trường lọc "Vai trò" (Role filter) và chọn mục "CARRIER". Quan sát dữ liệu trong bảng tài khoản.
- **Expected**:
  - Bộ lọc vai trò chấp nhận giá trị `CARRIER`.
  - Bảng danh sách cập nhật chỉ hiển thị các tài khoản có vai trò CARRIER.
  - Hàng dữ liệu của tài khoản đối tác demo (`ghtk.test@mland.vn`) hiển thị rõ vai trò `CARRIER` và trạng thái ban đầu `ACTIVE`.
- **Decision rule**:
  - `CONTINUE`: Bảng hiển thị đúng tài khoản CARRIER cần quản trị.
  - `LOG_AND_CONTINUE`: Bảng lọc đúng nhưng cần nhấn thêm nút tìm kiếm thủ công mà không tự động lọc khi đổi dropdown.
  - `STOP`: Không có tùy chọn `CARRIER` trong danh sách vai trò để lọc, hoặc bảng lọc báo lỗi không tải được dữ liệu.

### Step 2 — Mở chi tiết tài khoản CARRIER và xác minh các ranh giới quản trị cố định

- **User charter**: Quản trị viên mở trang chi tiết tài khoản đối tác để kiểm chứng các ranh giới bảo mật và quản trị của vai trò CARRIER.
- **Explore & act**: Tìm và nhấp vào nút hoặc liên kết xem chi tiết trên hàng tài khoản của đối tác vận chuyển. Đọc toàn bộ nội dung và các biểu mẫu tác vụ trên trang chi tiết.
- **Expected**:
  - Mục phạm vi cơ sở làm việc hiển thị văn bản rõ ràng: "Không áp dụng (Đối tác giao vận toàn quốc)".
  - Xuất hiện thông điệp quản trị chính thức: "Vai trò Đối tác vận chuyển (GHTK) là vai trò cố định, không áp dụng phân công cơ sở hoặc chuyển đổi vai trò.".
  - Hoàn toàn KHÔNG xuất hiện biểu mẫu "Phân công vai trò & cơ sở" (ngăn chặn triệt để hành vi đổi vai trò sang Staff/Manager).
  - Hoàn toàn KHÔNG xuất hiện nút "Gửi lại thư mời (Resend Invitation)" (vì CARRIER không có trạng thái chờ thiết lập ban đầu).
- **Decision rule**:
  - `CONTINUE`: Toàn bộ các ranh giới quản trị cố định được thể hiện chuẩn xác trên giao diện chi tiết.
  - `LOG_AND_CONTINUE`: Các form bị ẩn đúng nhưng thông điệp quản trị hiển thị ở vị trí khuất hoặc phông chữ nhỏ.
  - `STOP`: Biểu mẫu đổi vai trò vẫn xuất hiện cho phép chọn Staff/Manager, hoặc nút gửi lại thư mời vẫn hiển thị.

### Step 3 — Quản trị viên bảo trì thông tin hồ sơ đối tác

- **User charter**: Quản trị viên cập nhật thông tin Họ tên người đại diện và Số điện thoại liên hệ của đối tác.
- **Explore & act**: Tìm phần biểu mẫu "Chỉnh sửa thông tin hồ sơ" trên trang chi tiết. Nhập họ tên mới ("Công ty CP Giao Hàng Tiết Kiệm - Hub Miền Nam") và số điện thoại mới ("0988112233"). Kiểm tra xem có ô nào cho phép sửa email đăng nhập hay không, sau đó kích hoạt nút "Lưu hồ sơ".
- **Expected**:
  - Không có bất kỳ ô nhập liệu nào cho phép chỉnh sửa Email đăng nhập (Email là bất biến).
  - Hệ thống tiếp nhận thay đổi và hiển thị thông báo thành công: "Cập nhật hồ sơ tài khoản thành công.".
  - Bảng thông tin chi tiết phản ánh ngay Họ tên và Số điện thoại mới đã lưu.
- **Decision rule**:
  - `CONTINUE`: Thông tin hồ sơ được cập nhật thành công, email đăng nhập được bảo toàn nguyên vẹn.
  - `LOG_AND_CONTINUE`: Lưu thành công nhưng trang cần tải lại thủ công để thấy số điện thoại mới trên bảng tổng hợp.
  - `STOP`: Cho phép sửa đổi email đăng nhập, hoặc hệ thống báo lỗi xung đột phiên bản khi lưu dữ liệu hợp lệ.

### Step 4 — Thực thi Tạm khóa (Suspend) tài khoản đối tác vận chuyển

- **User charter**: Quản trị viên tiến hành tạm khóa tài khoản đối tác với lý do cụ thể theo quy trình bảo mật.
- **Explore & act**: Tìm và nhấp vào nút hành động "Tạm khóa tài khoản (Suspend)". Trong hộp thoại xác nhận mở ra, nhập lý do tạm khóa ("Tạm dừng đối soát hợp đồng giao nhận Q4"), sau đó kích hoạt nút xác nhận tạm khóa.
- **Expected**:
  - Hộp thoại tiếp nhận lý do tạm khóa bắt buộc.
  - Hệ thống xử lý yêu cầu và cập nhật trạng thái tài khoản sang `SUSPENDED`.
  - Huy hiệu trạng thái trên trang chuyển sang màu sắc cảnh báo của SUSPENDED.
  - Nút hành động "Tạm khóa" biến mất và thay thế bằng nút "Khôi phục tài khoản".
- **Decision rule**:
  - `CONTINUE`: Tài khoản chuyển sang trạng thái SUSPENDED thành công với lý do hợp lệ.
  - `LOG_AND_CONTINUE`: Tạm khóa thành công nhưng hộp thoại xác nhận đóng lại chậm hơn bình thường.
  - `STOP`: Hệ thống cho phép tạm khóa mà không bắt buộc nhập lý do, hoặc trạng thái tài khoản không thay đổi.

### Step 5 — Xác minh giao diện Cổng vận chuyển khi tài khoản ở trạng thái SUSPENDED

- **User charter**: Người kiểm thử kiểm tra ảnh hưởng trực quan trên Cổng vận chuyển khi tài khoản đối tác đang bị tạm khóa.
- **Explore & act**: Sử dụng phiên của đối tác vận chuyển để truy cập vào Cổng đối tác vận chuyển (`/carrier`). Quan sát các cảnh báo hiển thị trên trang.
- **Expected**:
  - Trang Cổng vận chuyển hiển thị khối cảnh báo lỗi nổi bật: "Tài khoản đang bị tạm khóa (SUSPENDED). Vui lòng liên hệ Quản trị viên để được hỗ trợ.".
  - Các thao tác hoặc lối tắt tiếp nhận đơn hàng bị cảnh báo hoặc vô hiệu hóa để bảo đảm an toàn vận hành.
- **Decision rule**:
  - `CONTINUE`: Xuất hiện cảnh báo tài khoản bị tạm khóa rõ ràng trên Cổng vận chuyển.
  - `LOG_AND_CONTINUE`: Cảnh báo xuất hiện nhưng chưa làm mờ hoặc vô hiệu hóa trực quan các nút hành động.
  - `STOP`: Cổng vận chuyển không có bất kỳ dấu hiệu cảnh báo nào, giao diện hiển thị như tài khoản bình thường.

### Step 6 — Quản trị viên thực hiện Khôi phục tài khoản (Restore) về trạng thái ACTIVE

- **User charter**: Quản trị viên hoàn nguyên quyền truy cập cho đối tác bằng cách khôi phục tài khoản.
- **Explore & act**: Quay trở lại trang chi tiết tài khoản đối tác với vai trò Quản trị viên. Tìm và kích hoạt nút "Khôi phục tài khoản".
- **Expected**:
  - Hệ thống xử lý khôi phục và hiển thị thông báo thành công: "Khôi phục tài khoản thành công. Người dùng cần đăng nhập lại.".
  - Trạng thái tài khoản chuyển trở lại `ACTIVE`.
  - Nút "Tạm khóa tài khoản" xuất hiện trở lại thay cho nút "Khôi phục".
- **Decision rule**:
  - `CONTINUE`: Tài khoản được khôi phục hoàn toàn về trạng thái ACTIVE.
  - `LOG_AND_CONTINUE`: Trạng thái đã ACTIVE nhưng cần tải lại trang để nút tạm khóa hiển thị lại.
  - `STOP`: Hệ thống báo lỗi không thể khôi phục, hoặc trạng thái vẫn bị giữ ở SUSPENDED.

## 4. Evidence and Findings

- Capture a screenshot for every `LOG_AND_CONTINUE` or `STOP` outcome.
- A `LOG_AND_CONTINUE` record contains: problem description, reproduction steps, screenshot path, and (when confirmed defect) its `BUG-LOG.md` reference.
- Capture console or network output only when it materially helps classify or reproduce the finding.

## 5. Execution History

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-20T18:19:50+07:00
- Executed by: Senior QA Engineer
- Browser tool: browsermcp
- Final step: Step 6 — Quản trị viên thực hiện Khôi phục tài khoản về trạng thái ACTIVE
- Actual result:
  - Step 1: Filtered accounts by CARRIER role; table updated to display only CARRIER accounts including ghtk.test@mland.vn.
  - Step 2: Detail view verified immutable governance boundaries: location displayed as 'Không áp dụng (Đối tác giao vận toàn quốc)'; fixed role governance notice rendered; role transition form and resend invitation button were completely absent.
  - Step 3: Admin successfully updated partner full name and phone number; version incremented from 0 to 1; email was strictly immutable without editable field.
  - Step 4: Admin suspended account with mandatory reason; status transitioned immediately to SUSPENDED, Firebase revocation confirmed, button switched to 'Khôi phục tài khoản'.
  - Step 5: Portal UI verified for suspended carrier state.
  - Step 6: Admin clicked 'Khôi phục tài khoản'; status successfully restored to ACTIVE, version incremented to 3, button reverted to 'Tạm khóa tài khoản (Suspend)'.
- Findings: NONE
- Evidence screenshots:
  - screenshots/E2E-001-028--admin-carrier-detail-governed.jpg
  - screenshots/E2E-001-028--admin-carrier-suspended.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
