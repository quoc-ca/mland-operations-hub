---
format_version: 2
id: E2E-001-021
feature: 001-account-management
test_level: e2e
test_case_type: A
source_ref: spec.md#FR-011, tasks.md#T028, tasks.md#T080, research.md#D-23
target_url: http://localhost:5173/admin/accounts
actor_role: Admin
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: admin-account-detail-conflict
  account_ref: ADMIN_001
  isolation_key: E2E-001-021-20261126
cleanup:
  required: false
  method: none-client-only
---

# E2E-001-021: Chi tiết tài khoản nội bộ — Phân biệt nhãn phân công Manager vs Staff & Phục hồi xung đột đồng thời 409

**Created**: 2026-11-26  
**Updated**: 2026-11-26  
**Owner**: Senior QA & System Verification Engineer  

## 1. Scenario Charter

- **User goal**: Quản trị viên (Admin) truy cập trang chi tiết tài khoản nội bộ để kiểm chứng việc phân biệt nhãn ngữ nghĩa phân công cơ sở giữa Nhân viên (Staff) và Quản lý (Manager) theo đặc tả R-MGA (T080), đồng thời kiểm chứng khả năng phục hồi an toàn của giao diện khi phát sinh xung đột ghi đồng thời (Optimistic Concurrency Conflict - HTTP 409) mà không làm sập giao diện hay mất trắng dữ liệu người dùng.
- **Starting context**: Quản trị viên đã đăng nhập hệ thống với tài khoản Admin (`admin.test@mland.vn`), bắt đầu từ trang Quản lý tài khoản (`http://localhost:5173/admin/accounts`).
- **Scope**:
  - Truy cập chi tiết tài khoản Staff (ACC-3 / `staff.test@mland.vn`): Kiểm tra nhãn header là "Cơ sở phân công:" và chú thích phạm vi: "Phân công cơ sở giới hạn phạm vi truy cập vận hành của Nhân viên (Staff).".
  - Truy cập chi tiết tài khoản Manager (ACC-2 / `manager.test@mland.vn`): Kiểm tra nhãn header là "Cơ sở phân công tổ chức:" và chú thích phạm vi: "Phân công cơ sở hỗ trợ ghi nhận tổ chức; phạm vi vận hành cho các tác vụ của Quản lý (Manager) là toàn hệ thống.".
  - Kiểm thử rủi ro đối nghịch xung đột đồng thời (409 CONFLICT): Khi dữ liệu phiên bản DB đã bị thay đổi bởi phiên khác (tăng trường `version`), thao tác lưu từ form mang version cũ bị từ chối 409. Giao diện phải bắt lỗi an toàn và hiển thị banner cảnh báo: "Dữ liệu đã bị thay đổi bởi phiên khác...".
- **Out of scope**: Quy trình gửi email SMTP thực tế khi cấp tài khoản mới (được bao phủ bởi E2E-001-003).

## 2. Preconditions and Test Data

- [x] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [x] Môi trường Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [x] Tài khoản Admin (`admin.test@mland.vn`) đã xác thực phiên làm việc.
- [x] Có sẵn tài khoản Staff (`staff.test@mland.vn`, ID: 3) và Manager (`manager.test@mland.vn`, ID: 2) trong hệ thống.

## 3. Exploratory Step Contract

### Step 1 — Mở danh sách tài khoản và truy cập chi tiết tài khoản Staff

- **User charter**: Quản trị viên mở trang quản lý tài khoản và chọn xem chi tiết tài khoản của một nhân viên cơ sở (Staff).
- **Explore & act**: Điều hướng tới `http://localhost:5173/admin/accounts`, định vị tài khoản `staff.test@mland.vn` (hoặc vai trò `STAFF`), bấm nút/liên kết "Chi tiết" để mở trang hồ sơ chi tiết.
- **Expected**:
  - Tiêu đề trang hiển thị thông tin tài khoản kèm mã ID (ví dụ: `ACC-3`).
  - Định danh đăng nhập hiển thị đúng `staff.test@mland.vn`.
  - Vai trò hệ thống hiển thị `STAFF`.
- **Decision rule**:
  - `CONTINUE`: Trang chi tiết tài khoản Staff hiển thị đầy đủ thông tin.
  - `LOG_AND_CONTINUE`: Thông tin hiển thị nhưng thời gian tải chậm (> 3s).
  - `STOP`: Không tải được trang chi tiết hoặc xuất hiện lỗi 404/500.

### Step 2 — Xác minh nhãn ngữ nghĩa phân công cho tài khoản Staff

- **User charter**: Quản trị viên kiểm tra nhãn ngữ nghĩa hiển thị cơ sở cho vai trò Staff phản ánh đúng ranh giới giới hạn quyền vận hành.
- **Explore & act**: Quan sát bảng Tổng quan tài khoản, kiểm tra tên hàng và nội dung giải thích tại mục cơ sở làm việc.
- **Expected**:
  - Hàng thông tin cơ sở hiển thị tiêu đề: `Cơ sở phân công:`.
  - Nội dung mô tả phạm vi hiển thị rõ ràng: `Phân công cơ sở giới hạn phạm vi truy cập vận hành của Nhân viên (Staff).`.
- **Decision rule**:
  - `CONTINUE`: Cả hai thành phần nhãn và thông điệp ngữ nghĩa xuất hiện chính xác.
  - `LOG_AND_CONTINUE`: Nhãn hiển thị nhưng câu chữ thiếu một vài từ phụ không ảnh hưởng bản chất ranh giới.
  - `STOP`: Nhãn hiển thị sai (ví dụ hiển thị nhãn của Manager) hoặc không có thông điệp giới hạn quyền.

### Step 3 — Quay lại danh sách và mở chi tiết tài khoản Manager

- **User charter**: Quản trị viên chuyển sang xem chi tiết tài khoản của Quản lý cơ sở (Manager) để đối chiếu nhãn ngữ nghĩa.
- **Explore & act**: Bấm liên kết "Quay lại danh sách tài khoản" hoặc truy cập `/admin/accounts`, tìm tài khoản `manager.test@mland.vn` (hoặc vai trò `MANAGER`) và chọn "Chi tiết".
- **Expected**:
  - Tiêu đề trang chi tiết hiển thị mã ID tài khoản Manager (ví dụ: `ACC-2`).
  - Định danh đăng nhập hiển thị `manager.test@mland.vn`.
  - Hàng thông tin cơ sở hiển thị tiêu đề phân công tổ chức: `Cơ sở phân công tổ chức:`.
  - Nội dung mô tả phạm vi ghi nhận rõ: `Phân công cơ sở hỗ trợ ghi nhận tổ chức; phạm vi vận hành cho các tác vụ của Quản lý (Manager) là toàn hệ thống.`.
- **Decision rule**:
  - `CONTINUE`: Nhãn phân công tổ chức và thông điệp vận hành toàn hệ thống của Manager xuất hiện chính xác.
  - `LOG_AND_CONTINUE`: Nhãn hiển thị đúng nhưng định dạng danh sách cơ sở có lỗi xuống dòng nhỏ.
  - `STOP`: Không phân biệt được nhãn Manager (hiển thị nhãn giới hạn của Staff) hoặc thiếu chú thích toàn hệ thống.

### Step 4 — Thử nghiệm xung đột đồng thời 409 (Optimistic Concurrency Conflict) và kiểm tra khả năng phục hồi

- **User charter**: Quản trị viên kiểm chứng hệ thống xử lý an toàn khi xảy ra xung đột chỉnh sửa phiên bản đồng thời trên cùng một tài khoản.
- **Explore & act**:
  1. Tại form "Chỉnh sửa thông tin hồ sơ" đang mở, chuẩn bị sửa đổi tên người dùng.
  2. Kích hoạt thay đổi phiên bản trên hệ thống (bằng cách gửi cập nhật làm tăng trường `version` trong DB từ phiên khác).
  3. Bấm "Lưu hồ sơ" với phiên bản cũ tại form hiện tại.
  4. Quan sát phản hồi của giao diện.
- **Expected**:
  - Backend phản hồi mã lỗi HTTP 409 CONFLICT (`DATA_CONFLICT`).
  - Giao diện KHÔNG bị crash (không trắng trang), form người dùng giữ nguyên nội dung nhập liệu.
  - Hiển thị banner cảnh báo lỗi xung đột phiên bản: `"Dữ liệu đã bị thay đổi bởi phiên khác. Đã tải lại thông tin mới nhất."` (hoặc thông điệp xung đột phiên bản tương đương).
- **Decision rule**:
  - `CONTINUE`: Giao diện bắt đúng lỗi 409, giữ trạng thái ổn định và hiển thị thông báo xung đột rõ ràng.
  - `LOG_AND_CONTINUE`: Bắt được lỗi 409 nhưng thông báo lỗi hiển thị bằng tiếng Anh mặc định.
  - `STOP`: Form bị sập (crash component) hoặc hệ thống vô tình ghi đè mất mát dữ liệu mà không báo 409.

## 4. Evidence and Findings

- Ảnh chụp màn hình:
  - Nhãn ngữ nghĩa Staff: `screenshots/E2E-001-021--staff-semantics.jpg`
  - Nhãn ngữ nghĩa Manager: `screenshots/E2E-001-021--manager-semantics.jpg`
  - Xung đột 409 phục hồi: `screenshots/E2E-001-021--conflict-recovery.jpg`

## 5. Execution History

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-26T02:34:00+07:00
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Final step: Step 4 — Thử nghiệm xung đột đồng thời 409 (Optimistic Concurrency Conflict) và kiểm tra khả năng phục hồi
- Actual result:
  - Step 1 & 2: Mở chi tiết tài khoản Staff (ACC-3 / staff.test@mland.vn) thành công. Tiêu đề hàng cơ sở hiển thị chính xác "Cơ sở phân công:". Chú thích ngữ nghĩa hiển thị rõ: "Phân công cơ sở giới hạn phạm vi truy cập vận hành của Nhân viên (Staff).".
  - Step 3: Mở chi tiết tài khoản Manager (ACC-2 / manager.test@mland.vn) thành công. Tiêu đề hàng cơ sở hiển thị "Cơ sở phân công tổ chức:". Chú thích ngữ nghĩa hiển thị rõ: "Phân công cơ sở hỗ trợ ghi nhận tổ chức; phạm vi vận hành cho các tác vụ của Quản lý (Manager) là toàn hệ thống.".
  - Step 4: Kích hoạt xung đột phiên bản DB đồng thời (version tăng từ 0 lên 3). Khi submit từ form lưu thông tin với version cũ, Backend trả về HTTP 409 Conflict (DATA_CONFLICT). Giao diện không crash, tự động gọi loadDetail để cập nhật version mới nhất và hiển thị alert lỗi cảnh báo an toàn: "Dữ liệu đã bị thay đổi bởi phiên khác (xung đột phiên bản). Đã tải lại thông tin mới nhất từ máy chủ, vui lòng kiểm tra và thử lại.".
- Findings: NONE
- Evidence screenshots:
  - screenshots/E2E-001-021--staff-semantics.jpg
  - screenshots/E2E-001-021--manager-semantics.jpg
  - screenshots/E2E-001-021--conflict-recovery.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
