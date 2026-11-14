---
id: E2E-001-021
feature: 001-account-management
test_level: e2e
test_case_type: A
source_ref: spec.md#FR-011,tasks.md#T028,tasks.md#T080,research.md#D-23
target_url: http://localhost:5173/admin/accounts
actor_role: Admin
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080
test_data:
  fixture: admin-account-detail-conflict
  account_ref: ADMIN_001
  isolation_key: E2E-001-021-20261114
cleanup:
  required: false
  method: none-client-only
---

# E2E-001-021: Chi tiết tài khoản nội bộ — Phân biệt nhãn phân công Manager vs Staff & Phục hồi xung đột đồng thời 409

**Ngày tạo**: 2026-11-14  
**Ngày cập nhật**: 2026-11-14  
**Người phụ trách**: Phan Sơn  
**Người cập nhật cuối**: Phan Sơn  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [x] Môi trường Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [x] Người dùng đăng nhập với vai trò Admin và có sẵn ít nhất một tài khoản Staff và một tài khoản Manager trong hệ thống.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Nhãn ngữ nghĩa phân công trên trang chi tiết (T080):**
  - Khi xem tài khoản **STAFF**: Nhãn cơ sở phải hiển thị chính xác là *"Cơ sở trực thuộc (phạm vi vận hành bắt buộc)"*.
  - Khi xem tài khoản **MANAGER**: Nhãn cơ sở phải hiển thị chính xác là *"Cơ sở phân công (quản lý tổ chức, không giới hạn quyền vận hành)"*.
- **Xung đột sửa đổi đồng thời (Optimistic Concurrency Conflict - 409):**
  - Trong quá trình Admin A đang mở form chỉnh sửa hồ sơ hoặc phân công vai trò/cơ sở, Admin B ở một phiên khác đã submit thay đổi làm tăng trường `version` trong DB.
  - Khi Admin A bấm lưu với `version` cũ: Backend trả về lỗi 409 CONFLICT (`DATA_CONFLICT`).
  - Giao diện Admin Detail KHÔNG được sập (crash), KHÔNG được xóa trắng form người dùng đang nhập dở, mà phải hiển thị banner cảnh báo xung đột phiên bản kèm nút hành động *"Tải lại dữ liệu"* (Reload) để người dùng chủ động đồng bộ lại trạng thái mới nhất.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở danh sách tài khoản và truy cập chi tiết tài khoản Staff
```browser
goto http://localhost:5173/admin/accounts
wait getByRole('heading', { name: 'Quản lý tài khoản' })
click getByRole('link', { name: 'Chi tiết' }).first()
wait getByRole('heading', { name: 'Chi tiết tài khoản' })
```

### Step 2 — Xác minh nhãn ngữ nghĩa cho tài khoản Staff
```browser
# Assertion: Hiển thị nhãn: "Cơ sở trực thuộc (phạm vi vận hành bắt buộc)"
```

### Step 3 — Quay lại danh sách và mở chi tiết tài khoản Manager
```browser
goto http://localhost:5173/admin/accounts
click getByRole('link', { name: 'Chi tiết' }).nth(1)
wait getByRole('heading', { name: 'Chi tiết tài khoản' })
# Assertion: Hiển thị nhãn: "Cơ sở phân công (quản lý tổ chức, không giới hạn quyền vận hành)"
```

### Step 4 — Mở modal/chế độ chỉnh sửa và mô phỏng xung đột đồng thời 409
```browser
click getByRole('button', { name: 'Chỉnh sửa' })
fill getByLabel('Họ và tên *') "Phan Son Updated"
# Mô phỏng backend trả về 409 Conflict do phiên bản cũ
click getByRole('button', { name: 'Lưu thay đổi' })
wait getByRole('alert')
```

### Step 5 — Kiểm tra hiển thị thông báo xung đột và phục hồi bằng nút Tải lại
```browser
# Assertion: Alert hiển thị thông điệp xung đột phiên bản
wait getByRole('button', { name: 'Tải lại dữ liệu' })
click getByRole('button', { name: 'Tải lại dữ liệu' })
# Assertion: Giao diện gọi lại API lấy dữ liệu mới nhất thành công, đóng alert lỗi và khôi phục trạng thái ổn định
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Cơ sở trực thuộc (phạm vi vận hành bắt buộc)"
# Expected: one or more matches khi xem Staff
find "Cơ sở phân công (quản lý tổ chức, không giới hạn quyền vận hành)"
# Expected: one or more matches khi xem Manager
find "Dữ liệu tài khoản đã bị thay đổi bởi phiên làm việc khác"
# Expected: match khi phát sinh xung đột 409
find "Tải lại dữ liệu"
# Expected: one or more matches
```

## 5. Bằng chứng kiểm thử (Evidence)

- Kịch bản chạy thực tế qua Chrome bằng `browsermcp`.
- Ảnh chụp màn hình:
  - Nhãn ngữ nghĩa: `screenshots/E2E-001-021--account-detail-semantics.jpg`
  - Phục hồi xung đột 409: `screenshots/E2E-001-021--conflict-recovery.jpg`

## 6. Lịch sử thực thi

<!-- EXECUTION:START -->
- Latest status: NOT_RUN
- Note: Kịch bản mới tạo ngày 2026-11-14 theo Revision R-MGA (T080) và cơ chế khóa lạc quan (T028).
<!-- EXECUTION:END -->
