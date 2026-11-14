---
id: E2E-007-016
feature: 007-notification-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-015
target_url: http://localhost:5173/admin/notifications/requests
actor_role: Manager
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: seed-sent-request
  account_ref: E2E_MGR_001
  isolation_key: E2E-007-016-20261114
cleanup:
  required: false
  method: none
---

# E2E-007-016: Multiple resend and redeliver actions on same root request and lineage display

**Ngày tạo**: 2026-11-14  
**Ngày cập nhật**: 2026-11-14  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Người cập nhật cuối**: Senior QA & System Verification Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [ ] Frontend Vite đang chạy tại `http://localhost:5173`.
- [ ] Tài khoản kiểm thử vai trò `MANAGER` (`manager.test@mland.vn` / `ManagerTest@123456`) đã được đăng nhập.
- [ ] Dữ liệu mồi: Tồn tại request gốc có trạng thái `SENT` (ví dụ `#1001`) tại `/admin/notifications/requests`.

## 2. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở danh sách yêu cầu và mở ngăn chi tiết (Drawer) của request gốc #1001
```browser
goto http://localhost:5173/admin/notifications/requests
wait getByRole('heading', { name: /Nhật ký gửi thư/i })
click getByRole('button', { name: 'Xem chi tiết' })
wait getByRole('heading', { name: /Chi tiết yêu cầu gửi thư/i })
```

### Step 2 — Thực hiện gửi lại lần 1 (Resend Action)
```browser
click getByRole('button', { name: 'Gửi lại thư' })
wait getByRole('heading', { name: /Gửi lại email thông báo/i })
fill getByLabel(/Lý do gửi lại/i) "Khách hàng báo chưa nhận được email xác nhận lần 1"
click getByRole('button', { name: 'Xác nhận gửi lại' })
wait getByRole('status')
```

### Step 3 — Mở lại ngăn chi tiết request gốc và thực hiện cấp lại liên kết (Redeliver Action)
```browser
click getByRole('button', { name: 'Xem chi tiết' })
wait getByRole('heading', { name: /Chi tiết yêu cầu gửi thư/i })
click getByRole('button', { name: 'Gửi lại với link/vé mới' })
wait getByRole('heading', { name: /Cấp lại liên kết và gửi lại/i })
fill getByLabel(/Lý do cấp lại/i) "Khách hàng xin link mới sau khi link 24h hết hạn"
click getByRole('button', { name: 'Xác nhận cấp lại và gửi' })
wait getByRole('status')
```

### Step 4 — Mở ngăn chi tiết của request con vừa được tạo để xác minh thông tin phả hệ
```browser
click getByRole('button', { name: 'Đóng' })
click getByRole('button', { name: 'Xem chi tiết' })
wait getByRole('heading', { name: /Chi tiết yêu cầu gửi thư/i })
```

## 3. Kết quả kỳ vọng (Expected Assertions)

```browser
requests --filter "/resend"
# Expected: POST 201
requests --filter "/redeliver"
# Expected: POST 201
find "Yêu cầu gửi lại đã được xếp hàng"
# Expected: one or more matches
find "Yêu cầu gửi lại với liên kết mới đã được xếp hàng"
# Expected: one or more matches
find "Gửi lại từ yêu cầu:"
# Expected: one or more matches displaying canonical root reference
console --errors
# Expected: no unhandled Javascript crashes or HTTP 500 errors
```

## 4. Bằng chứng kiểm thử (Evidence)

- Đường dẫn ảnh chụp màn hình: `screenshots/E2E-007-016--multiple-resend-success.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/007-notification-management/test-results/e2e/screenshots/E2E-007-016--multiple-resend-success.jpg"`

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14 16:08:05
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Thực hiện liên tiếp thao tác cấp lại vé (Redeliver tạo #1009) và gửi lại thư (Resend tạo #1010) trên cùng nhánh phả hệ. Cả hai thao tác đều thành công, tạo bản ghi con trỏ về root #1001, ngăn chi tiết hiển thị chính xác "Gửi lại từ yêu cầu: #1001", không xảy ra xung đột unique constraint DB hay lỗi 500.
- Evidence screenshot: screenshots/E2E-007-016--multiple-resend-success.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->
