---
id: E2E-001-018
feature: 001-account-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-041,design/ui-flow.md#Revision-R-SL
target_url: http://localhost:5173/profile
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
test_data:
  fixture: account-alias-guard-matrix
  account_ref: NONE
  isolation_key: E2E-001-018-20261106
cleanup:
  required: false
  method: clear-auth-session-between-matrix-rows
---

# E2E-001-018: Matrix alias Account/Address/Security — cùng guard, không lộ private UI

## 1. Tiền điều kiện

- [ ] Guest session sạch trước từng row.
- [ ] Router hiện hành khai báo đúng các alias trong matrix dưới đây; nếu một route bị đổi/xóa ngoài revision, cập nhật source/spec trước khi chạy.

## 2. Matrix và expected result

| Route mở trực tiếp khi Guest | Kết quả URL bắt buộc | DOM không được có trước/sau redirect |
| --- | --- | --- |
| `/profile`, `/personal-profile`, `/member/personal-profile`, `/member/profile`, `/me/profile` | `/login` bằng `replace` | heading/field Profile, email/name/phone fixture |
| `/profile/addresses`, `/member/addresses`, `/addresses` | `/login` bằng `replace` | address list, phone/recipient, address modal |
| `/edit-profile`, `/member/edit-profile` | `/login` bằng `replace` | form edit/profile draft |
| `/change-password`, `/member/change-password`, `/security/password`, `/security/email` | `/login` bằng `replace` | password/email-change controls hoặc values |

### Step 1 — Chạy từng row Guest

```browser
goto <route-in-matrix>
wait getByRole('heading', { name: 'Đăng nhập hệ thống' })
get url
snapshot -i
```

**Expected for every row:** exact URL `http://localhost:5173/login`; no private DOM named in matrix; no intermediate private screenshot is accepted as PASS.

### Step 2 — Kiểm tra canonical UI link của authenticated Member

```browser
goto http://localhost:5173/login
fill getByLabel('Địa chỉ email') "<member-email-from-secret-store>"
fill getByLabel('Mật khẩu') "<member-password-from-secret-store>"
click getByRole('button', { name: 'Đăng nhập' })
wait getByRole('main')
click getByRole('link', { name: 'Hồ sơ' })
get url
```

**Expected:** Home after sign-in is `/`; UI-generated link opens canonical `/profile`, not a compatibility alias. Address/security actions use `/profile/addresses`, `/security/password`, `/security/email` when activated.

## 3. Evidence bắt buộc

- URL/DOM snapshot cho mọi matrix row (có thể ghép thành một evidence bundle).
- Screenshot canonical Profile link sau authenticated step.

## 4. Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-06 20:08:33
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Kiểm thử toàn diện 14/14 alias routes của Account, Address Book và Security (/profile, /personal-profile, /member/personal-profile, /member/profile, /me/profile, /profile/addresses, /member/addresses, /addresses, /edit-profile, /member/edit-profile, /change-password, /member/change-password, /security/password, /security/email) khi ở trạng thái Guest đều lập tức được Route Guard chuyển hướng thay thế (replace) về /login. Tuyệt đối không để lộ bất kỳ thông tin Profile, địa chỉ hay form đổi mật khẩu nào.
- Evidence screenshot: screenshots/E2E-001-018--alias-matrix.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->
