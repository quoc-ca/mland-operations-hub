---
id: E2E-007-006
feature: 007-notification-management
test_level: e2e
test_case_type: N
source_ref: spec.md#FR-022
target_url: http://localhost:5173/admin/notifications/requests
actor_role: Manager
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: seed-delivery-requests
  account_ref: E2E_MGR_001
  isolation_key: E2E-007-006-20261109
cleanup:
  required: false
  method: none
---

# E2E-007-006: Delivery log filtering and client-side PII recipient email masking

**Ngày tạo**: 2026-11-09  
**Ngày cập nhật**: 2026-11-09  
**Người phụ trách**: Senior QA Engineer  
**Người cập nhật cuối**: Senior QA Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Đăng nhập với tài khoản `MANAGER`.
- [ ] Database có sẵn các bản ghi gửi email với nhiều trạng thái (`SENT`, `FAILED`, `PENDING`).

## 2. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở danh sách nhật ký gửi thư
```browser
goto http://localhost:5173/admin/notifications/requests
wait getByRole('heading', { name: 'Nhật ký gửi thông báo (Delivery Logs)' })
```

### Step 2 — Lọc theo trạng thái Đã gửi (SENT)
```browser
select getByLabel('Trạng thái gửi') "SENT"
wait getByText('SENT')
```

### Step 3 — Tìm kiếm theo mã sự kiện
```browser
fill getByPlaceholder('VD: BK-2026..., ORD-...') "BK-"
wait 1000
```

## 3. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Nhật ký gửi thông báo (Delivery Logs)"
# Expected: Exactly one match
find "SENT"
# Expected: One or more matches
find "@"
# Expected: All displayed emails match pattern `*@*` with asterisks (e.g. `n***a@gmail.com`)
requests --filter "/api/v1/notification-requests"
# Expected: GET 200 with deliveryState=SENT
```

## 4. Bằng chứng kiểm thử (Evidence)

- Đường dẫn ảnh chụp màn hình: `screenshots/E2E-007-006--log-filtering-masked.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/007-notification-management/e2e/screenshots/E2E-007-006--log-filtering-masked.jpg"`

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

