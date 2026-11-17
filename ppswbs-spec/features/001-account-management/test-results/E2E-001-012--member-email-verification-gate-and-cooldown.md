---
id: E2E-001-012
feature: 001-account-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-004,FR-005
target_url: http://localhost:5173/profile
actor_role: Customer
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080
test_data:
  fixture: member-email-verification-gate
  account_ref: E2E_MEMBER_012
  isolation_key: E2E-001-012-20261105
cleanup:
  required: false
  method: none-client-only
---

# E2E-001-012: Kiểm soát Cổng Xác thực Email (Gate Box) — Tải lại Trạng thái Xác thực, Banner Cảnh báo & Cooldown Gửi lại 90s

**Ngày tạo**: 2026-11-05  
**Ngày cập nhật**: 2026-11-05  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Người cập nhật cuối**: Senior QA & System Verification Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [ ] Môi trường Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [ ] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [ ] Đang mở trang Hồ sơ cá nhân `http://localhost:5173/profile`.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Ngăn chặn vòng lặp gọi API vô tận (Infinite loop guard / T051):** Hook `useVerifiedEmailGate` chỉ được kiểm tra một lần khi mount (`checkGate()`) hoặc khi người dùng chủ động nhấn nút tải lại. Tuyệt đối không được thiết lập interval polling gây nghẽn network hoặc crash trình duyệt.
- **Chống spam click nút Tải lại xác thực (Debounce / In-flight guard):** Khi người dùng click liên tiếp vào nút `🔄 Tải lại xác thực`, nút phải chuyển sang trạng thái disabled hiển thị text `Đang kiểm tra…`, sử dụng ref promise nội bộ để gộp các request đồng thời, không bắn nhiều request trùng nhau lên server.
- **Ràng buộc Cooldown 90 giây khi Gửi lại Email xác thực:** Khi bấm "Gửi lại email xác thực", nút phải chuyển thành đếm ngược `Gửi lại sau Xs` và bị vô hiệu hóa trong suốt 90 giây. Giá trị hết hạn được lưu an toàn trong `localStorage` theo `account_resend_cooldown_<id>` và vẫn tiếp tục đếm ngược chính xác kể cả khi người dùng F5 tải lại trang.
- **Chặn quyền Lưu hồ sơ khi chưa xác thực email (Security Gate FR-005):** Nếu trạng thái email là `unverified`, nút "Lưu thay đổi" trong form Chỉnh sửa hồ sơ phải bị disabled kèm thông báo giải thích: `* Bạn cần xác thực email trước khi có thể chỉnh sửa hồ sơ.`

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang Profile và quan sát khối Gate Box trạng thái xác thực
```browser
goto http://localhost:5173/profile
wait getByRole('heading', { name: 'Hồ sơ cá nhân & Bảo mật' })
wait getByText('Trạng thái xác thực email:')
```

### Step 2 — Bấm nút Tải lại trạng thái xác thực và kiểm tra phản hồi tức thì
```browser
click getByRole('button', { name: 'Kiểm tra lại trạng thái xác thực email' })
# Quan sát nút tạm thời hiển thị: "Đang kiểm tra…" và disabled
wait 2000
# Quan sát trạng thái hoàn tất trở về "Đã xác thực" hoặc "Chưa xác thực"
```

### Step 3 — Xác minh cơ chế chống spam request (In-flight guard)
```browser
# Assertion: Trong khi đang kiểm tra, click đúp không sinh ra request trùng lặp
requests --filter "/api/v1/accounts/me/sync-email"
# Expected: Request đồng bộ được gửi đúng 1 lần
```

### Step 4 — Kiểm tra hiển thị Badge trạng thái trong Profile Overview
```browser
# Assertion: Badge hiển thị "Email đã xác thực" (nền xanh lá) hoặc "Email chưa xác thực" (nền vàng)
```

### Step 5 — Thao tác kiểm chứng Cooldown và lưu trữ LocalStorage (khi unverified)
```browser
# Nếu tài khoản unverified:
# Click nút "Gửi lại email xác thực"
# Quan sát nút chuyển sang "Gửi lại sau 90s", bị disabled
# F5 reload trang: Đếm ngược vẫn tiếp tục từ thời gian thực trong LocalStorage
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Trạng thái xác thực email:"
# Expected: one match
find "Tải lại xác thực"
# Expected: one match
console --errors
# Expected: no infinite render or unhandled errors
```

## 5. Bằng chứng kiểm thử (Evidence)

- Ảnh chụp màn hình Gate box và trạng thái kiểm tra: `screenshots/E2E-001-012--email-gate-cooldown.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-012--email-gate-cooldown.jpg"`

## 6. Bằng chứng thực thi lịch sử (trước revision 2026-11-06)

<!-- EXECUTION:START -->
- Historical status: PASS
- Executed at: 2026-11-05 17:21:21
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result:
  - Khối Gate Box trạng thái xác thực hiển thị trực quan và chuẩn mực: Bấm nút 'Kiểm tra lại trạng thái xác thực email' (Tải lại xác thực) lập tức kích hoạt debounce in-flight guard, hiển thị 'Đang kiểm tra…' và disable tạm thời để chống click lặp / spam request.
  - Sau khi kiểm tra xong, trạng thái cập nhật mượt mà, phản ánh đúng trạng thái xác thực hiện tại của tài khoản mà không gây ra bất kỳ lỗi render hay reload loop nào.
- Evidence screenshot:
  - Gate Box kiểm tra xác thực: screenshots/E2E-001-012--email-gate-cooldown.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

## 7. Revision expected results (2026-11-06 — must re-run)

- **Steps 1–2:** profile remains `/profile`; checking state is neutral and does not reveal token/UID; unverified result keeps save disabled and presents Firebase resend guidance without navigation.
- **Step 3:** repeated reload click while pending creates one active check; button/indicator makes pending observable and re-enables after a safe error or completed check.
- **Steps 4–5:** the only persisted cooldown value is an expiry timestamp under `emailVerificationResendUntil.<opaqueAccountId>`; it must not contain email, Firebase UID, token, action code or password. Refresh honors 90 seconds; expiry re-enables resend.
- Missing Firebase user/session expiry is not accepted as a gate error: discard Profile draft and follow Home session-loss flow; a non-session `503` preserves safe input for explicit retry.

<!-- EXECUTION:START -->
- Latest status: NOT_RUN
- Previous status: PASS on 2026-11-04; persistence/session assertions added.
<!-- EXECUTION:END -->

