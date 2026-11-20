---
id: E2E-001-027
feature: 001-account-management
test_level: e2e
test_case_type: A
source_ref: spec.md#FR-003, spec.md#FR-017, spec.md#FR-046, tasks.md#T087
target_url: http://localhost:5173/carrier
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

**Ngày tạo**: 2026-11-20  
**Ngày cập nhật**: 2026-11-20  
**Người phụ trách**: Phan Sơn (Senior QA Engineer)  
**Người cập nhật cuối**: Phan Sơn  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Frontend Vite đang hoạt động tại `http://localhost:5173`.
- [x] Môi trường Backend Spring Boot đang hoạt động tại `http://localhost:8080`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [x] Tài khoản khách hàng thành viên `MEMBER`: `customer.test@mland.vn` / `MemberPass@123`.
- [x] Tài khoản đối tác vận chuyển `CARRIER`: `ghtk.test@mland.vn` / `CarrierTest@123456`.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Nguy cơ rò rỉ Cổng vận chuyển cho khách hàng lẻ (Unauthorized Portal Access / FR-003):** Người dùng thông thường (MEMBER) hoặc khách vãng lai khi cố tình nhập URL `http://localhost:5173/carrier` vào thanh địa chỉ trình duyệt không được xem thông tin vận chuyển. Hệ thống phải kích hoạt Route Guard nội bộ, hiển thị thông báo lỗi quyền truy cập và nút quay lại Trang chủ.
- **Ngăn chặn CARRIER tự ý can thiệp thông tin hồ sơ cá nhân (Carrier Profile Immutability / FR-017):**
  - CARRIER không có quyền tự chỉnh sửa hồ sơ (Họ tên, SĐT, Email). Chỉ Quản trị viên (Admin) mới có quyền bảo trì thông tin này.
  - Khi CARRIER cố tình gõ URL `/profile`, `/edit-profile` hoặc `/member/personal-profile`, `ProfilePage` phải phát hiện vai trò `CARRIER` và điều hướng cưỡng bức (replace) quay trở lại `/carrier`.
- **Ngăn chặn leo thang đặc quyền vào khu vực Quản trị Admin (Privilege Escalation Defense):**
  - Khi CARRIER cố tình truy cập vào các đường dẫn quản trị như `/admin/accounts`, `/admin/accounts/new` hoặc `/admin/roles`, `AdminRouteGuard` phải lập tức chặn lại và từ chối hiển thị.
- **Bảo toàn quyền tự phục vụ mật khẩu hợp lệ (Eligible Self-Service Password Flow / FR-017):**
  - Mặc dù bị chặn tự sửa hồ sơ, CARRIER vẫn được quyền thay đổi mật khẩu định kỳ để bảo đảm an toàn thông tin tại `/security/password`.
  - Luồng đổi mật khẩu phải thực thi xác thực lại tại chỗ (in-place re-authentication); nhập sai mật khẩu cũ phải bị từ chối với thông báo rõ ràng, không làm mất phiên đăng nhập.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Phase 1: Thử nghiệm đối nghịch với vai trò MEMBER truy cập /carrier

#### Step 1 — Đăng nhập bằng tài khoản MEMBER
```browser
goto http://localhost:5173/login
fill getByLabel(/email/i) "customer.test@mland.vn"
fill getByLabel(/mật khẩu/i) "MemberPass@123"
click getByRole('button', { name: /đăng nhập/i })
wait getByRole('heading', { name: /trang chủ|mland/i })
```

#### Step 2 — MEMBER cố tình nhập URL trực tiếp vào /carrier
```browser
goto http://localhost:5173/carrier
wait getByRole('alert')
wait getByText('Tài khoản của bạn không có quyền truy cập Cổng đối tác vận chuyển.')
wait getByRole('link', { name: 'Quay lại trang chủ' })
```

### Phase 2: Đăng xuất và đăng nhập tài khoản CARRIER

#### Step 3 — Đăng xuất và đăng nhập bằng tài khoản CARRIER
```browser
goto http://localhost:5173/login
fill getByLabel(/email/i) "ghtk.test@mland.vn"
fill getByLabel(/mật khẩu/i) "CarrierTest@123456"
click getByRole('button', { name: /đăng nhập/i })
wait getByRole('heading', { name: 'Cổng đối tác vận chuyển (GHTK)' })
```

### Phase 3: Thử nghiệm CARRIER truy cập các route Member bị cấm

#### Step 4 — CARRIER cố tình truy cập /profile và /edit-profile
```browser
goto http://localhost:5173/profile
# Route guard phát hiện CARRIER và chuyển hướng cưỡng bức về /carrier
wait getByRole('heading', { name: 'Cổng đối tác vận chuyển (GHTK)' })
get url
# Khẳng định URL bị đẩy về http://localhost:5173/carrier

goto http://localhost:5173/edit-profile
wait getByRole('heading', { name: 'Cổng đối tác vận chuyển (GHTK)' })
get url
# Khẳng định URL tiếp tục bị đẩy về http://localhost:5173/carrier
```

### Phase 4: Thử nghiệm CARRIER truy cập khu vực Admin

#### Step 5 — CARRIER cố tình truy cập /admin/accounts
```browser
goto http://localhost:5173/admin/accounts
# AdminRouteGuard chặn truy cập
wait getByText(/không có quyền truy cập|từ chối truy cập|403/i)
```

### Phase 5: Xác minh luồng tự phục vụ đổi mật khẩu hợp lệ của CARRIER

#### Step 6 — CARRIER truy cập /security/password từ Cổng vận chuyển
```browser
goto http://localhost:5173/security/password
wait getByRole('heading', { name: /đổi mật khẩu|bảo mật tài khoản/i })
# Khẳng định: Không bị redirect về /carrier vì đây là mode 'password'
```

#### Step 7 — Thử nghiệm đổi mật khẩu với mật khẩu hiện tại sai
```browser
fill getByLabel(/mật khẩu hiện tại/i) "WrongCurrentPassword123"
fill getByLabel(/mật khẩu mới/i) "NewCarrierPass@2026"
fill getByLabel(/xác nhận mật khẩu mới/i) "NewCarrierPass@2026"
click getByRole('button', { name: /lưu mật khẩu|cập nhật mật khẩu/i })
wait getByRole('alert')
wait getByText(/mật khẩu hiện tại không chính xác/i)
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Tài khoản của bạn không có quyền truy cập Cổng đối tác vận chuyển."
# Expected: 1 match khi MEMBER truy cập /carrier
get url
# Expected: http://localhost:5173/carrier khi CARRIER cố tình vào /profile hoặc /edit-profile
find "mật khẩu hiện tại không chính xác"
# Expected: 1 match khi CARRIER nhập sai mật khẩu cũ
console --errors
# Expected: không có lỗi uncaught runtime ngoại trừ mã trạng thái HTTP 403 mong đợi
```

## 5. Bằng chứng kiểm thử (Evidence)

- Ảnh chụp màn hình chặn quyền truy cập của MEMBER: `ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-027--member-blocked-from-carrier.jpg`
- Ảnh chụp màn hình chuyển hướng tự động khỏi Profile: `ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-027--carrier-redirected-from-profile.jpg`
- Lệnh chụp bằng chứng: `screenshot --full --path "ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-027--carrier-redirected-from-profile.jpg"`

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
