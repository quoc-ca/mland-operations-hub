# E2E Test Case Template

Sử dụng cấu trúc template này khi tạo mới kịch bản kiểm thử E2E với `browsermcp`.  
File kịch bản được lưu tại: `ppswbs-spec/features/<feature>/test-results/E2E-<feature-number>-<NNN>--<lowercase-slug>.md`.

```markdown
---
id: E2E-001-001
feature: 001-account-management
test_level: e2e
test_case_type: N
source_ref: spec.md#FR-001
target_url: http://localhost:5173/login
actor_role: Customer
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: customer-e2e-001-001
  account_ref: E2E_CUSTOMER_001
  isolation_key: E2E-001-001-<timestamp>
cleanup:
  required: true
  method: delete-record-via-api
---

# E2E-001-001: [Mô tả ngắn gọn kịch bản kiểm thử]

**Ngày tạo**: YYYY-MM-DD  
**Ngày cập nhật**: YYYY-MM-DD  
**Người phụ trách**: [Tên thành viên hoặc QA]  
**Người cập nhật cuối**: [Tên thành viên hoặc QA]  

## 1. Tiền điều kiện (Preconditions)

- [ ] Môi trường Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [ ] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [ ] Dữ liệu mồi: fixture `customer-e2e-001-001`, với `isolation_key` ở YAML front matter.
- [ ] Tài khoản test chuyên dụng: `E2E_CUSTOMER_001`; secret lấy từ secret store/biến môi trường, không ghi vào file này.
- [ ] Phiên đăng nhập: Chưa đăng nhập.

## 2. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Đăng nhập bằng tài khoản test

```browser
goto http://localhost:5173/login
wait getByLabel('Email')
fill getByLabel('Email') "<email-tài-khoản-test>"
fill getByLabel('Mật khẩu') "<mật-khẩu-từ-secret-store>"
click getByRole('button', { name: 'Đăng nhập' })
wait getByRole('heading', { name: 'Bảng điều khiển' })
```

## 3. Kết quả kỳ vọng (Expected Assertions)

```browser
get url
# Expected: http://localhost:5173/dashboard
find "Bảng điều khiển"
# Expected: one or more matches
console --errors
# Expected: no output
requests --filter "/api/auth/login"
# Expected: POST 200
```

## 4. Bằng chứng kiểm thử (Evidence)

- Đường dẫn ảnh chụp màn hình: `screenshots/E2E-001-001--success.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-001--success.jpg"`

## 5. Kết quả thực thi (Execution History)

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
```

