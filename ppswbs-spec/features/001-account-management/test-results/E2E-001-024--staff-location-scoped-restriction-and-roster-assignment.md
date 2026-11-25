---
format_version: 2
id: E2E-001-024
feature: 001-account-management
test_level: e2e
test_case_type: B
source_ref: quickstart.md#Scenario V-07, spec.md#FR-011, spec-general.md, tasks.md#T078, tasks.md#T079, research.md#D-23
target_url: http://localhost:5173/operations/sessions
actor_role: Staff, Manager
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: staff-location-and-roster
  account_ref: STAFF_001, MANAGER_001
  isolation_key: E2E-001-024-20261126
cleanup:
  required: false
  method: none-client-only
---

# E2E-001-024: Phân quyền Staff theo cơ sở & Roster phân công nhân sự chỉ chứa Staff (Loại trừ Manager)

**Created**: 2026-11-26  
**Updated**: 2026-11-26  
**Owner**: Senior QA & System Verification Engineer  

## 1. Scenario Charter

- **User goal**: Kiểm chứng quy tắc ranh giới phân quyền phân công nhân sự giữa Feature 001 (Account Management) và Feature 003 (Workshop Operations): (1) Nhân viên (Staff) bị giới hạn nghiêm ngặt trong phạm vi cơ sở trực thuộc và không có quyền phân công nhân sự; (2) Quản lý (Manager) có quyền phân công nhân sự cho ca workshop nhưng danh sách gợi ý nhân sự (Roster do `AccountFacade.findEligibleRoster` cung cấp) CHỈ chứa các tài khoản vai trò `STAFF`, tuyệt đối loại trừ 100% tài khoản `MANAGER` theo quyết định D-23 (Revision R-MGA).
- **Starting context**: Người dùng bắt đầu bằng việc đăng nhập vai trò Staff (`staff.test@mland.vn`), truy cập Bảng điều hành Workshop tại `http://localhost:5173/operations/sessions`. Sau đó đăng nhập chuyển đổi sang vai trò Manager (`manager.test@mland.vn`) để kiểm tra quyền và thành phần Roster.
- **Scope**:
  - Giao diện vận hành của Staff (`staff.test@mland.vn`): Kiểm tra danh sách hiển thị đúng cơ sở trực thuộc, khi vào chi tiết ca session không xuất hiện công cụ phân công nhân sự.
  - Giao diện vận hành của Manager (`manager.test@mland.vn`): Mở chi tiết ca workshop, tiếp cận panel phân công nhân sự đứng lớp.
  - Phân tích rủi ro đối nghịch Roster: Kiểm tra các mục chọn trong dropdown nhân sự hướng dẫn — đảm bảo có nhân viên vai trò STAFF, nhưng tuyệt đối không được có tên bất kỳ tài khoản nào mang vai trò MANAGER.
- **Out of scope**: Quy trình check-in quét mã QR hoặc hoàn tiền vé (thuộc Feature 003/006).

## 2. Preconditions and Test Data

- [x] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [x] Môi trường Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [x] Tài khoản Staff: `staff.test@mland.vn` (Mật khẩu: `StaffTest@123456`, phân công tại Cơ sở Hoàn Kiếm).
- [x] Tài khoản Manager: `manager.test@mland.vn` (Mật khẩu: `ManagerTest@123456`).
- [x] Hệ thống có ít nhất một ca workshop đang hoạt động tại `/operations/sessions`.

## 3. Exploratory Step Contract

### Step 1 — Đăng nhập tài khoản Staff và truy cập Bảng điều hành Workshop

- **User charter**: Nhân viên đăng nhập vào hệ thống và mở giao diện điều hành ca workshop.
- **Explore & act**: Đăng nhập với tài khoản `staff.test@mland.vn`, điều hướng tới `http://localhost:5173/operations/sessions`.
- **Expected**:
  - Giao diện Bảng điều hành Workshop hiển thị thành công.
  - Hiển thị dòng thông tin cơ sở: "Cơ sở của bạn:" kèm tên cơ sở trực thuộc của nhân viên.
- **Decision rule**:
  - `CONTINUE`: Bảng điều hành hiển thị đúng với thông tin cơ sở của Staff.
  - `LOG_AND_CONTINUE`: Giao diện tải được nhưng danh sách phiên cần bấm nút lọc lại.
  - `STOP`: Không thể truy cập trang hoặc bị lỗi xác thực 401/403 đối với Staff.

### Step 2 — Kiểm tra giới hạn quyền của Staff trong chi tiết ca workshop

- **User charter**: Kiểm tra nhân viên Staff không thể thực hiện các thao tác quản lý phân công nhân sự trong ca workshop.
- **Explore & act**: Từ danh sách ca workshop, bấm "Mở" trên một ca session bất kỳ để vào trang chi tiết session (`/operations/sessions/:id`). Quan sát các hành động khả dụng trên trang.
- **Expected**:
  - Trang chi tiết ca workshop hiển thị thông tin ca và danh sách vé tham dự (Roster điểm danh).
  - Tuyệt đối KHÔNG xuất hiện form/panel "Phân công nhân sự hướng dẫn" (Staff Assignment) hay nút xóa/gán nhân sự (vì chỉ Manager mới có quyền này).
- **Decision rule**:
  - `CONTINUE`: Quyền hạn được kiểm soát đúng ranh giới (Staff chỉ thấy điểm danh, không thấy phân công nhân sự).
  - `LOG_AND_CONTINUE`: Giao diện ẩn form phân công nhưng còn sót nút vô hiệu hóa (disabled).
  - `STOP`: Staff có thể nhìn thấy hoặc tương tác được với form phân công nhân sự.

### Step 3 — Đăng xuất Staff và đăng nhập tài khoản Quản lý (Manager)

- **User charter**: Quản lý đăng nhập vào hệ thống để thực hiện quyền phân công nhân sự.
- **Explore & act**: Bấm nút "Đăng xuất" từ thanh điều hướng, sau đó đăng nhập bằng tài khoản `manager.test@mland.vn` (`ManagerTest@123456`).
- **Expected**:
  - Đăng nhập thành công, phiên làm việc chuyển sang vai trò `MANAGER`.
  - Thanh tác vụ hiển thị định danh của Manager.
- **Decision rule**:
  - `CONTINUE`: Đăng nhập Manager thành công.
  - `LOG_AND_CONTINUE`: Đăng nhập thành công nhưng phải tải lại trang để cập nhật vai trò mới trên header.
  - `STOP`: Không thể đăng nhập bằng tài khoản Manager.

### Step 4 — Mở chi tiết ca workshop dưới quyền Manager và tiếp cận phân công nhân sự

- **User charter**: Quản lý mở ca workshop trên bảng điều hành và mở khu vực phân công nhân sự đứng lớp.
- **Explore & act**: Truy cập `http://localhost:5173/operations/sessions`, mở cùng ca workshop (hoặc một ca hợp lệ) để vào trang chi tiết session. Cuộn xuống khu vực quản lý phân công nhân sự.
- **Expected**:
  - Dưới quyền Manager, xuất hiện khu vực quản lý phân công nhân sự hướng dẫn cho ca workshop.
  - Hiển thị danh sách/dropdown lựa chọn nhân viên để gán vào ca.
- **Decision rule**:
  - `CONTINUE`: Manager nhìn thấy đầy đủ công cụ phân công nhân sự.
  - `LOG_AND_CONTINUE`: Form phân công hiển thị sau khi bấm mở rộng một panel phụ.
  - `STOP`: Manager không nhìn thấy chức năng phân công nhân sự.

### Step 5 — Xác minh thành phần danh sách nhân sự (Roster) chỉ chứa Staff, loại trừ Manager

- **User charter**: Quản lý kiểm tra danh sách gợi ý nhân sự để đảm bảo chỉ có nhân viên Staff, không có tài khoản Quản lý.
- **Explore & act**: Mở dropdown/danh sách chọn nhân viên hướng dẫn. Kiểm tra từng lựa chọn khả dụng trong danh sách.
- **Expected**:
  - Danh sách chứa các nhân viên có vai trò `STAFF` (ví dụ: `staff.test@mland.vn` hoặc nhân sự Staff cơ sở).
  - Kiểm chứng đối nghịch: Tuyệt đối KHÔNG xuất hiện tên hoặc email của tài khoản vai trò `MANAGER` (`manager.test@mland.vn`, Quản lý) trong danh sách ứng viên đứng lớp.
- **Decision rule**:
  - `CONTINUE`: Roster chỉ chứa Staff và loại trừ hoàn toàn Manager theo đúng hợp đồng D-23.
  - `LOG_AND_CONTINUE`: Danh sách hiển thị đúng nhân sự Staff nhưng định dạng tên hiển thị chưa có mã ID.
  - `STOP`: Xuất hiện tài khoản vai trò Manager trong danh sách ứng viên phân công nhân sự.

## 4. Evidence and Findings

- Ảnh chụp màn hình:
  - Giới hạn quyền Staff: `screenshots/E2E-001-024--staff-session-view.jpg`
  - Bảng phân công Manager & Roster loại trừ Manager: `screenshots/E2E-001-024--manager-staff-roster.jpg`

## 5. Execution History

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-26T02:38:15+07:00
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Final step: Step 5 — Xác minh thành phần danh sách nhân sự (Roster) chỉ chứa Staff, loại trừ Manager
- Actual result:
  - Step 1 & 2 (Actor: Staff): Đăng nhập thành công với staff.test@mland.vn. Tại /operations/sessions, hệ thống hiển thị chính xác phạm vi "Chi nhánh của bạn: Mland Hà Nội – Hoàn Kiếm" và chỉ hiển thị 135 ca thuộc cơ sở Hoàn Kiếm (không hiển thị ca Tây Hồ). Mở chi tiết ca session 8531cc0a-e8f0-11f1-a75e-a6dbd948c690: Staff chỉ có quyền xem thông tin và quầy check-in, hoàn toàn KHÔNG có panel/nút phân công nhân sự.
  - Step 3 (Actor: Manager): Đăng xuất Staff và đăng nhập thành công với manager.test@mland.vn.
  - Step 4 & 5 (Actor: Manager): Mở chi tiết ca workshop nói trên. Dưới vai trò Manager, khu vực "Phân công Staff" xuất hiện đầy đủ chức năng. Dropdown "Chọn nhân viên (Staff) *" hiển thị chính xác danh sách nhân sự vai trò STAFF: "Nhân Viên Workshop Hoàn Kiếm (staff.test@mland.vn) — ID: 3". Kiểm chứng đối nghịch: Tài khoản Manager (manager.test@mland.vn, Quản Lý Chuỗi Chi Nhánh) bị loại trừ 100% khỏi danh sách nhân sự đứng lớp theo đúng hợp đồng D-23 (R-MGA).
- Findings: NONE
- Evidence screenshots:
  - screenshots/E2E-001-024--staff-session-view.jpg
  - screenshots/E2E-001-024--manager-staff-roster.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
