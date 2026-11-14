---
id: E2E-007-017
feature: 007-notification-management
test_level: e2e
test_case_type: A
source_ref: spec.md#FR-021
target_url: http://localhost:5173/admin/notifications/templates/BOOKING_CONFIRMED
actor_role: Manager
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: manager-account-e2e
  account_ref: E2E_MGR_001
  isolation_key: E2E-007-017-20261114
cleanup:
  required: false
  method: none
---

# E2E-007-017: Template safe preview enforces iframe sandboxing against malicious script execution (XSS Gate)

**Ngày tạo**: 2026-11-14  
**Ngày cập nhật**: 2026-11-14  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Người cập nhật cuối**: Senior QA & System Verification Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [ ] Frontend Vite đang chạy tại `http://localhost:5173`.
- [ ] Tài khoản kiểm thử vai trò `MANAGER` (`manager.test@mland.vn` / `ManagerTest@123456`) đã đăng nhập.
- [ ] Template `BOOKING_CONFIRMED` tồn tại và sẵn sàng quản lý phiên bản.

## 2. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Điều hướng tới trang chi tiết mẫu thông báo BOOKING_CONFIRMED
```browser
goto http://localhost:5173/admin/notifications/templates/BOOKING_CONFIRMED
wait getByRole('heading', { name: /Xác nhận đặt lịch Workshop/i })
```

### Step 2 — Mở modal soạn thảo bản nháp (Create or Edit Draft)
```browser
click getByRole('button', { name: /Tạo bản nháp mới|Chỉnh sửa bản nháp/i })
wait getByRole('heading', { name: /Soạn thảo bản nháp mẫu thông báo/i })
```

### Step 3 — Nhập tiêu đề hợp lệ và chèn payload script đối nghịch vào ô nội dung HTML
```browser
fill getByLabel(/Tiêu đề email/i) "[Mland] Thử nghiệm an toàn đặt lịch #{{bookingCode}}"
fill getByLabel(/Nội dung HTML/i) "<script>window.top.__xss_leaked = true;</script><p>Kính chào {{customerName}}, đơn đặt #{{bookingCode}} đã ghi nhận.</p>"
fill getByLabel(/Nội dung văn bản thuần/i) "Kính chào {{customerName}}, đơn đặt #{{bookingCode}} đã ghi nhận."
click getByRole('button', { name: /Lưu bản nháp/i })
wait getByRole('status')
```

### Step 4 — Kích hoạt modal xem trước (Preview Modal) cho phiên bản vừa tạo
```browser
click getByRole('button', { name: 'Xem trước' })
wait getByRole('heading', { name: /Xem trước mẫu thông báo/i })
```

### Step 5 — Chuyển đổi giữa tab "Giao diện HTML" và tab "Nội dung Plain Text"
```browser
click getByRole('button', { name: 'Giao diện HTML' })
wait 500
click getByRole('button', { name: 'Nội dung Plain Text' })
wait 500
```

## 3. Kết quả kỳ vọng (Expected Assertions)

```browser
requests --filter "/preview"
# Expected: POST 200
find "Kính chào Nguyễn Văn A, đơn đặt #BK-SAMPLE-2026 đã ghi nhận."
# Expected: one or more matches in preview body
find "sandbox=\"allow-same-origin\""
# Expected: iframe attribute is present and strictly excludes 'allow-scripts'
console --errors
# Expected: no unhandled Javascript runtime crashes on parent page
```

- **Kiểm chứng biến Window của trang chính (Parent Context Isolation)**:
  - Biến `window.top.__xss_leaked` hoặc `window.__xss_leaked` trên trang chính phải có giá trị là `undefined`.
  - Trình duyệt chủ động ngăn chặn việc nạp và chạy thẻ `<script>` do thiếu cờ `allow-scripts` trong thuộc tính `sandbox` của iframe.
- **Kiểm chứng Tab Plain Text**:
  - Toàn bộ nội dung chuỗi thẻ `<script>` được render dưới dạng văn bản thô bên trong thẻ `<pre class="nm-code-box">`, không bị trình duyệt phân tích cú pháp thành DOM sống.

## 4. Bằng chứng kiểm thử (Evidence)

- Đường dẫn ảnh chụp màn hình: `screenshots/E2E-007-017--xss-sandbox-prevented.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/007-notification-management/test-results/e2e/screenshots/E2E-007-017--xss-sandbox-prevented.jpg"`

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14 16:09:10
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Soạn thảo bản nháp mẫu BOOKING_CONFIRMED chèn script độc hại <script>window.top.__xss_leaked = true;</script>. Khi mở modal xem trước, iframe cô lập an toàn với sandbox="allow-same-origin" (không có allow-scripts). Console trình duyệt thông báo chính xác: "Blocked script execution in 'about:srcdoc' because the document's frame is sandboxed and the 'allow-scripts' permission is not set". Đánh giá eval kiểm tra window.__xss_leaked trả về false (ngăn chặn XSS thành công 100%). Tab Plain text hiển thị nội dung thuần túy an toàn.
- Evidence screenshot: screenshots/E2E-007-017--xss-sandbox-prevented.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->
