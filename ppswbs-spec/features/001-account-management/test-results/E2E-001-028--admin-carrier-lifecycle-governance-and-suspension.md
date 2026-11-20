---
id: E2E-001-028
feature: 001-account-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-009, spec.md#FR-017, spec.md#FR-019, spec.md#FR-020, tasks.md#T086
target_url: http://localhost:5173/admin/accounts
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

**Ngày tạo**: 2026-11-20  
**Ngày cập nhật**: 2026-11-20  
**Người phụ trách**: Phan Sơn (Senior QA Engineer)  
**Người cập nhật cuối**: Phan Sơn  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Frontend Vite đang hoạt động tại `http://localhost:5173`.
- [x] Môi trường Backend Spring Boot đang hoạt động tại `http://localhost:8080`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [x] Tài khoản Quản trị viên `ADMIN` đã đăng nhập tại `http://localhost:5173`.
- [x] Tài khoản đối tác vận chuyển demo `ghtk.test@mland.vn` đang ở trạng thái ban đầu `ACTIVE`.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Nguy cơ chuyển đổi vai trò trái phép (Role Mutation Breach / FR-009):** Vai trò CARRIER là vai trò cố định (fixed role), đại diện cho pháp nhân đối tác bên ngoài. Tuyệt đối không được phép chuyển đổi CARRIER sang STAFF/MANAGER hoặc ngược lại. Trang chi tiết tài khoản Admin phải ẩn hoàn toàn form "Phân công vai trò & cơ sở" để chặn triệt để hành động chuyển đổi từ UI.
- **Rò rỉ nút gửi lại thư mời (Invitation Resend Contradiction / FR-011):** CARRIER không có trạng thái `PENDING_SETUP` hay thư mời kích hoạt Firebase ban đầu. Nút "Gửi lại thư mời (Resend Invitation)" không được hiển thị đối với tài khoản CARRIER.
- **Tính bất biến của Email đăng nhập (Email Immutability / FR-017):** Admin chỉ có quyền bảo trì họ tên và số điện thoại liên hệ của đối tác; email đăng nhập của CARRIER là bất biến và không có bất kỳ ô input nào cho phép sửa đổi email.
- **Ảnh hưởng tức thì của Tạm khóa (Immediate Suspension Enforcement / FR-019):**
  - Khi Admin thực hiện tạm khóa (Suspend) với lý do bắt buộc, trạng thái tài khoản phải chuyển sang `SUSPENDED` ngay lập tức.
  - Khi đối tác CARRIER mở Cổng vận chuyển `/carrier`, giao diện phải hiển thị cảnh báo đỏ nổi bật báo tài khoản bị tạm khóa.
- **Khôi phục phiên hoạt động an toàn (Safe Account Restoration / FR-020):** Admin bấm khôi phục (Restore), hệ thống chuyển trạng thái trở lại `ACTIVE`, hoàn nguyên quyền truy cập bình thường.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở danh sách tài khoản Admin và lọc theo vai trò CARRIER
```browser
goto http://localhost:5173/admin/accounts
wait getByRole('heading', { name: 'Quản trị danh sách tài khoản' })
select getByLabel('Vai trò') "CARRIER"
wait getByText('ghtk.test@mland.vn')
```

### Step 2 — Mở trang chi tiết tài khoản đối tác vận chuyển
```browser
click getByRole('link', { name: /chi tiết|xem/i })
wait getByRole('heading', { name: /thông tin tài khoản/i })
```

### Step 3 — Xác minh cơ chế quản trị đối tác (Governance Assertions)
```browser
# Xác minh hiển thị phạm vi cơ sở
wait getByText('Không áp dụng (Đối tác giao vận toàn quốc)')

# Xác minh thông báo quản trị vai trò cố định
wait getByText(/Vai trò Đối tác vận chuyển \(GHTK\) là vai trò cố định/i)

# Khẳng định: Hoàn toàn không có form 'Phân công vai trò & cơ sở' và không có nút 'Gửi lại thư mời'
snapshot -i
```

### Step 4 — Admin bảo trì hồ sơ đối tác (Cập nhật Họ tên và SĐT)
```browser
fill getByLabel('Họ và tên') "Công ty CP Giao Hàng Tiết Kiệm (Hub Miền Nam)"
fill getByLabel('Số điện thoại') "0988112233"
click getByRole('button', { name: 'Lưu hồ sơ' })
wait getByText('Cập nhật hồ sơ tài khoản thành công.')
```

### Step 5 — Thực hiện Tạm khóa tài khoản CARRIER (Suspension)
```browser
click getByRole('button', { name: 'Tạm khóa tài khoản (Suspend)' })
wait getByRole('dialog')
fill getByLabel(/lý do tạm khóa/i) "Tạm dừng đối soát hợp đồng giao nhận Q4"
click getByRole('button', { name: /xác nhận tạm khóa|đồng ý/i })
wait getByText(/Tài khoản đã được chuyển sang trạng thái tạm khóa/i)
wait getByText('SUSPENDED')
```

### Step 6 — Xác minh giao diện Cổng vận chuyển khi tài khoản bị SUSPENDED
```browser
goto http://localhost:5173/carrier
wait getByText(/Tài khoản đang bị tạm khóa \(SUSPENDED\)/i)
```

### Step 7 — Admin thực hiện Khôi phục tài khoản (Restoration)
```browser
goto http://localhost:5173/admin/accounts
select getByLabel('Vai trò') "CARRIER"
click getByRole('link', { name: /chi tiết|xem/i })
wait getByRole('button', { name: 'Khôi phục tài khoản' })
click getByRole('button', { name: 'Khôi phục tài khoản' })
wait getByText(/Khôi phục tài khoản thành công/i)
wait getByText('ACTIVE')
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Không áp dụng (Đối tác giao vận toàn quốc)"
# Expected: 1 match
find "Phân công vai trò & cơ sở"
# Expected: 0 matches (bị ẩn đối với role CARRIER)
find "Gửi lại thư mời"
# Expected: 0 matches (bị ẩn đối với role CARRIER)
find "Công ty CP Giao Hàng Tiết Kiệm (Hub Miền Nam)"
# Expected: 1 match sau khi lưu hồ sơ
find "SUSPENDED"
# Expected: 1 match sau khi hoàn tất tạm khóa
find "Tài khoản đang bị tạm khóa (SUSPENDED)"
# Expected: 1 match trên trang /carrier khi ở trạng thái SUSPENDED
find "ACTIVE"
# Expected: 1 match sau khi khôi phục thành công
console --errors
# Expected: no errors
requests --filter "/api/v1/accounts/"
# Expected: PUT update profile trả về 200, POST suspend trả về 200, POST restore trả về 200
```

## 5. Bằng chứng kiểm thử (Evidence)

- Ảnh chụp màn hình trang chi tiết CARRIER và bất biến vai trò: `ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-028--admin-carrier-detail-governed.jpg`
- Ảnh chụp màn hình sau khi tạm khóa: `ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-028--admin-carrier-suspended.jpg`
- Lệnh chụp bằng chứng: `screenshot --full --path "ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-028--admin-carrier-suspended.jpg"`

## 6. Lịch sử thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: NOT_RUN
- Executed at: NOT_RUN
- Executed by: NOT_RUN
- Browser tool: browsermcp
- Failure step: NOT_RUN
- Actual result: NOT_RUN
- Evidence screenshot: NOT_RUN
- Bug reference: NONE
<!-- EXECUTION:END -->
