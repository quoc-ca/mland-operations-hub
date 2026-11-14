---
id: E2E-001-024
feature: 001-account-management
test_level: e2e
test_case_type: B
source_ref: quickstart.md#Scenario V-07,spec.md#FR-011,spec-general.md,tasks.md#T078
target_url: http://localhost:5173/workshops
actor_role: Staff
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080
test_data:
  fixture: staff-location-and-roster
  account_ref: STAFF_LOC_A
  isolation_key: E2E-001-024-20261114
cleanup:
  required: false
  method: none-client-only
---

# E2E-001-024: Phân quyền Staff theo cơ sở & Roster phân công nhân sự chỉ chứa Staff (Loại trừ Manager)

**Ngày tạo**: 2026-11-14  
**Ngày cập nhật**: 2026-11-14  
**Người phụ trách**: Phan Sơn  
**Người cập nhật cuối**: Phan Sơn  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [x] Môi trường Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [x] Tài khoản Staff đăng nhập được gán cơ sở trực thuộc là **Cơ sở A (101)**.
- [x] Cơ sở A có cả nhân viên Staff và tài khoản Manager được gán cùng cơ sở này.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Giới hạn phạm vi cơ sở của Staff (Location Scoping - T079):**
  - Trái ngược với Manager, Staff bắt buộc bị giới hạn trong phạm vi cơ sở trực thuộc.
  - Khi Staff cố tình truy cập hoặc gửi request thao tác trên ca workshop thuộc **Cơ sở B (202)**: Hệ thống phải từ chối truy cập ngay lập tức với thông báo lỗi rõ ràng (`LOCATION_SCOPE_DENIED`), giữ nguyên tính toàn vẹn của ca workshop cơ sở khác.
- **Tính chuẩn xác của Roster phân công nhân sự (T078, D-23):**
  - Tại màn hình phân công nhân sự đứng lớp/hướng dẫn ca workshop (`Workshop Session Assignment`), danh sách gợi ý nhân sự (Roster) lấy từ `AccountFacade.findEligibleRoster(locationId)`.
  - Hợp đồng quy định: Roster CHỈ chứa các nhân viên `STAFF` đang hoạt động tại cơ sở đó.
  - Tuyệt đối KHÔNG được hiển thị tài khoản `MANAGER` trong danh sách nhân sự phụ trách lớp học, nhằm ngăn chặn việc phân công sai vai trò hoặc nhầm lẫn giữa quản lý tổ chức và nhân sự trực tiếp đứng lớp.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Phần A — Kiểm tra chặn thao tác ngoài cơ sở trực thuộc của Staff
```browser
# Đăng nhập với tài khoản Staff Cơ sở A
goto http://localhost:5173/workshops
wait getByRole('heading', { name: 'Quản lý lịch Workshop' })
```

### Step 2 — Thử chọn xem hoặc thao tác trên Cơ sở B
```browser
select getByLabel('Cơ sở làm việc') "Chi nhánh TP.HCM (Cơ sở B)"
# Thử bấm vào một ca workshop của Cơ sở B để chỉnh sửa hoặc check-in
click getByRole('button', { name: 'Điểm danh / Check-in' }).first()
wait getByRole('alert')
# Assertion: Alert thông báo lỗi từ chối quyền hạn vì không thuộc cơ sở trực thuộc (LOCATION_SCOPE_DENIED)
```

### Phần B — Kiểm tra danh sách nhân sự phân công (Roster) tại Cơ sở A
```browser
# Chọn lại Cơ sở A (cơ sở hợp lệ của Staff)
select getByLabel('Cơ sở làm việc') "Chi nhánh Hà Nội (Cơ sở A)"
wait 1000
# Mở modal phân công nhân sự cho một ca workshop
click getByRole('button', { name: 'Phân công nhân sự' }).first()
wait getByRole('dialog')
wait getByRole('heading', { name: 'Phân công nhân sự đứng lớp' })
```

### Step 4 — Mở dropdown chọn nhân sự và kiểm tra thành phần Roster
```browser
click getByLabel('Chọn nhân sự hướng dẫn *')
# Assertion: Hiển thị danh sách các nhân viên có vai trò STAFF (ví dụ: "Nguyen Van Staff A")
# Assertion: Tuyệt đối KHÔNG xuất hiện tên các tài khoản MANAGER (ví dụ: "Tran Thi Manager A")
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "LOCATION_SCOPE_DENIED"
# Expected: match khi Staff cố thao tác trên Cơ sở B
find "Nguyen Van Staff A"
# Expected: one or more matches trong dropdown phân công
find "Tran Thi Manager A"
# Expected: zero matches trong dropdown phân công nhân sự
```

## 5. Bằng chứng kiểm thử (Evidence)

- Kịch bản chạy thực tế qua Chrome bằng `browsermcp`.
- Ảnh chụp màn hình:
  - Chặn quyền ngoài cơ sở: `screenshots/E2E-001-024--staff-location-denied.jpg`
  - Roster loại trừ Manager: `screenshots/E2E-001-024--staff-only-roster.jpg`

## 6. Lịch sử thực thi

<!-- EXECUTION:START -->
- Latest status: NOT_RUN
- Note: Kịch bản mới tạo ngày 2026-11-14 kiểm chứng ranh giới quyền cơ sở của Staff và Roster nhân sự hợp đồng Feat 001 <-> Feat 003 theo T078–T079.
<!-- EXECUTION:END -->
