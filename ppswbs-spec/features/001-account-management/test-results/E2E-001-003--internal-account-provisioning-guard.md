---
id: E2E-001-003
feature: 001-account-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-011
target_url: http://localhost:5173/admin/accounts/new
actor_role: Staff
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
test_data:
  fixture: admin-internal-account
  account_ref: ADMIN_001
  isolation_key: E2E-001-003-20261104
cleanup:
  required: false
  method: none-client-only
---

# E2E-001-003: Quản trị tài khoản nội bộ — Phân quyền động cơ sở (Staff vs Manager) và bảo toàn dữ liệu form

**Ngày tạo**: 2026-11-04  
**Ngày cập nhật**: 2026-11-04  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Người cập nhật cuối**: Senior QA & System Verification Engineer  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [x] Người dùng mở trang Khởi tạo tài khoản nội bộ `/admin/accounts/new`.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Ràng buộc phân công cơ sở Staff vs Manager:** Staff chỉ được phép gán vào ĐÚNG 1 cơ sở (phải dùng Radio button). Khi chuyển sang Manager, hệ thống phải cho phép phân công 1 hoặc nhiều cơ sở (chuyển sang Checkbox).
- **Validation form rỗng:** Bấm "Khởi tạo tài khoản" khi chưa nhập họ tên. Kỳ vọng hiển thị cảnh báo `Họ và tên là bắt buộc.`
- **Khả năng phục hồi và bảo toàn dữ liệu khi network lỗi (Error Resilience):** Khi backend chưa phản hồi hoặc kết nối lỗi (5xx / Failed to fetch), UI phải bắt lỗi và hiển thị thông báo alert, ĐỒNG THỜI giữ nguyên dữ liệu đã nhập (họ tên, vai trò đã chọn) mà không làm mất công người dùng.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang tạo tài khoản nội bộ
```browser
goto http://localhost:5173/admin/accounts/new
wait getByRole('heading', { name: 'Khởi tạo tài khoản nội bộ' })
```

### Step 2 — Xác minh hành vi phân công cơ sở cho STAFF
```browser
# Quan sát: Vai trò STAFF được chọn mặc định
# Quan sát: Cơ sở làm việc hiển thị dưới dạng Radio button (chỉ chọn được 1)
```

### Step 3 — Đổi vai trò sang MANAGER và kiểm tra chuyển đổi giao diện
```browser
select getByLabel('Vai trò nội bộ *') "Quản lý cơ sở (MANAGER)"
# Quan sát: Cơ sở làm việc chuyển thành Checkbox (cho phép chọn nhiều)
```

### Step 4 — Kiểm tra validation khi thiếu Họ và tên
```browser
click getByRole('button', { name: 'Khởi tạo tài khoản' })
wait getByText('Họ và tên là bắt buộc.')
```

### Step 5 — Nhập họ tên và kiểm tra tính năng bảo toàn dữ liệu khi backend ngắt kết nối
```browser
fill getByLabel('Họ và tên *') "Nguyen Van Quan Ly"
click getByRole('button', { name: 'Khởi tạo tài khoản' })
wait getByRole('alert')
# Assertion: Alert hiển thị lỗi kết nối
# Assertion: Ô input "Họ và tên *" vẫn giữ nguyên "Nguyen Van Quan Ly"
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Khởi tạo tài khoản nội bộ"
# Expected: one or more matches
get value getByLabel('Họ và tên *')
# Expected: "Nguyen Van Quan Ly"
```

## 5. Bằng chứng kiểm thử (Evidence)

- Kịch bản chạy thực tế qua Chrome bằng `browsermcp`.
- DOM snapshots và network logs được ghi nhận chính xác trong phiên kiểm thử.

## 6. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-04 04:25:25
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Logic phân quyền giao diện tuân thủ chặt chẽ đặc tả WF-03 (Staff 1 location radio, Manager multi-location checkbox). Form giữ dữ liệu an toàn khi có sự cố mạng.
- Evidence screenshot: DOM snapshot verified
- Bug reference: NONE
<!-- EXECUTION:END -->

