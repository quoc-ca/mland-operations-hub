---
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

**Ngày tạo**: 2026-11-20  
**Ngày cập nhật**: 2026-11-20  
**Người phụ trách**: Phan Sơn (Senior QA Engineer)  
**Người cập nhật cuối**: Phan Sơn  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Frontend Vite đang hoạt động tại `http://localhost:5173`.
- [x] Môi trường Backend Spring Boot đang hoạt động tại `http://localhost:8080`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [x] Tài khoản fixture đối tác vận chuyển V188 đã tồn tại và sẵn sàng:
  - Email: `ghtk.test@mland.vn`
  - Mật khẩu: `CarrierTest@123456`
  - Role: `CARRIER`, Status: `ACTIVE`
  - Tên đối tác: `Đối tác vận chuyển (GHTK)`, SĐT: `0900000000`
- [x] Trình duyệt chưa đăng nhập bất kỳ phiên nào (Guest).

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Sai lệch đích đến sau đăng nhập (Post-signin Redirection Flaw / FR-003, FR-046):** Người dùng thông thường (Member) sau khi đăng nhập thành công sẽ được điều hướng về Trang chủ (`/`), Staff về `/staff/home`, Manager về `/manager/home`. Đối tác vận chuyển (CARRIER) tuyệt đối không được điều hướng về `/` hoặc lộ các dashboard nội bộ, mà phải được chuyển thẳng lập tức tới `/carrier`.
- **Rò rỉ giao diện bán lẻ và đặc quyền Member (Member UI Pollution / FR-003, FR-026):** CARRIER là đối tác giao vận logistics B2B, không phải khách hàng mua sắm. Khi CARRIER đăng nhập:
  - Thanh điều hướng (`MainTopbar`) không được hiển thị bất kỳ tính năng thương mại nào: Workshop, Cấu hình nhẫn, Ready Rings, Custom, Kho Voucher, Tra cứu vé, Trợ lý AI, Đánh giá, Tư vấn.
  - Nút Giỏ hàng (`/cart`) và số lượng giỏ hàng phải bị ẩn hoàn toàn.
  - Điểm thưởng tích lũy (Loyalty points) không được hiển thị trên chip người dùng.
- **Cô lập danh mục tùy chọn tài khoản (User Dropdown Isolation / FR-027):** Menu dropdown tài khoản của CARRIER chỉ được phép chứa đúng 2 liên kết nghiệp vụ: `🚚 Cổng đối tác vận chuyển` (`/carrier`) và `🔑 Đổi mật khẩu` (`/security/password`). Tuyệt đối không xuất hiện các mục cá nhân của khách hàng (Hồ sơ cá nhân, Mẫu nhẫn, Đơn hàng của tôi, Điểm thưởng, Voucher) hay các lối tắt quản trị chi nhánh (Staff Vận hành, Quản lý sản phẩm, Roster).

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang đăng nhập hệ thống tại /login
```browser
goto http://localhost:5173/login
wait getByRole('heading', { name: /đăng nhập/i })
```

### Step 2 — Nhập thông tin đăng nhập tài khoản CARRIER fixture
```browser
fill getByLabel(/email/i) "ghtk.test@mland.vn"
fill getByLabel(/mật khẩu/i) "CarrierTest@123456"
click getByRole('button', { name: /đăng nhập/i })
```

### Step 3 — Xác minh điều hướng tức thì tới /carrier
```browser
wait getByRole('heading', { name: 'Cổng đối tác vận chuyển (GHTK)' })
get url
# Khẳng định: URL hiện tại là http://localhost:5173/carrier
```

### Step 4 — Kiểm tra thông tin định danh và giao diện Cổng đối tác vận chuyển
```browser
wait getByText('CARRIER · ACTIVE')
wait getByText('ghtk.test@mland.vn')
wait getByText('Toàn quốc (Không giới hạn theo cơ sở đơn lẻ)')
wait getByText('🚚 Đơn vận chuyển cần giao')
wait getByText('🔑 Bảo mật & Mật khẩu')
```

### Step 5 — Kiểm tra cô lập thanh điều hướng chính (MainTopbar Isolation)
```browser
# Xác minh: Chỉ có link 'Cổng vận chuyển' trong nav
wait getByRole('link', { name: 'Cổng vận chuyển' })

# Khẳng định: Không tồn tại giỏ hàng và các liên kết mua sắm của khách hàng
snapshot -i
```

### Step 6 — Mở menu tài khoản và xác minh danh sách tùy chọn được bảo vệ
```browser
click getByRole('button', { name: /tùy chọn tài khoản/i })
wait getByRole('menu')
wait getByRole('menuitem', { name: /cổng đối tác vận chuyển/i })
wait getByRole('menuitem', { name: /đổi mật khẩu/i })
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
get url
# Expected: http://localhost:5173/carrier
find "Cổng đối tác vận chuyển (GHTK)"
# Expected: 1 match
find "CARRIER · ACTIVE"
# Expected: 1 match
find "Workshop"
# Expected: 0 matches trong MainTopbar
find "Giỏ hàng"
# Expected: 0 matches trong MainTopbar
find "Hồ sơ cá nhân"
# Expected: 0 matches trong user dropdown menu
find "Đơn hàng của tôi"
# Expected: 0 matches trong user dropdown menu
console --errors
# Expected: no errors
requests --filter "/api/v1/accounts/me"
# Expected: GET 200 OK, response role: "CARRIER", status: "ACTIVE"
```

## 5. Bằng chứng kiểm thử (Evidence)

- File ảnh chụp màn hình Cổng đối tác và Topbar cô lập: `ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-026--carrier-portal-isolated.jpg`
- Lệnh chụp bằng chứng: `screenshot --full --path "ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-026--carrier-portal-isolated.jpg"`

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
