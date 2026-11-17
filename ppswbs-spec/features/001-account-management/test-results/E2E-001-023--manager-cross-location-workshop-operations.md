---
id: E2E-001-023
feature: 001-account-management
test_level: e2e
test_case_type: N
source_ref: quickstart.md#Scenario V-07,spec.md#FR-011,spec-general.md,tasks.md#T079
target_url: http://localhost:5173/workshop-settlement
actor_role: Manager
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080
test_data:
  fixture: manager-cross-location
  account_ref: MANAGER_LOC_A
  isolation_key: E2E-001-023-20261114
cleanup:
  required: false
  method: none-client-only
---

# E2E-001-023: Vận hành Workshop xuyên cơ sở — Thẩm quyền toàn cục của Manager trên các chi nhánh

**Ngày tạo**: 2026-11-14  
**Ngày cập nhật**: 2026-11-14  
**Người phụ trách**: Phan Sơn  
**Người cập nhật cuối**: Phan Sơn  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [x] Môi trường Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [x] Tài khoản Manager test đang đăng nhập, tài khoản này được gán phân công tổ chức tại **Cơ sở A (Chi nhánh Hà Nội)**.
- [x] Hệ thống có các ca workshop và yêu cầu quyết toán tại **Cơ sở B (Chi nhánh TP.HCM)**.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Quyền hạn toàn cục của Manager (Revision R-MGA, T079):**
  - Khác với Staff bị giam quyền trong cơ sở được phân công, tài khoản Manager có thẩm quyền vận hành toàn cục đối với các chức năng phân quyền cho Manager.
  - Phân công cơ sở của Manager thuần túy là dữ liệu tổ chức/hồ sơ, tuyệt đối KHÔNG được trở thành rào cản phân quyền chặn Manager thao tác tại các cơ sở khác.
  - Khi Manager mở trang Quyết toán Workshop (`/workshop-settlement`) hoặc Quản lý ca workshop (`/workshops`), chọn xem và thực hiện thao tác duyệt/hoàn tiền/quyết toán cho ca workshop tại Cơ sở B (khác cơ sở gán), hệ thống phải chấp thuận thành công mà không báo lỗi `LOCATION_SCOPE_DENIED`.
  - Khi cơ chế kiểm tra scope cơ sở bị rỗng hoặc tạm thời không phản hồi, Manager vẫn thực hiện được hành động bình thường vì thẩm quyền được suy luận trực tiếp từ `CurrentAccountActor` (không phụ thuộc Location dependency).

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Đăng nhập với tài khoản Manager gán tại Cơ sở A và truy cập trang quyết toán
```browser
goto http://localhost:5173/workshop-settlement
wait getByRole('heading', { name: 'Quyết toán Workshop' })
```

### Step 2 — Chuyển bộ lọc chi nhánh sang Cơ sở B (khác cơ sở gán của Manager)
```browser
select getByLabel('Lọc theo cơ sở / chi nhánh') "Chi nhánh TP.HCM (Cơ sở B)"
wait 1000
# Assertion: Danh sách các đơn đặt/ca workshop của Cơ sở B hiển thị đầy đủ
```

### Step 3 — Mở chi tiết ca quyết toán tại Cơ sở B
```browser
click getByRole('button', { name: 'Xem quyết toán' }).first()
wait getByRole('dialog')
wait getByRole('heading', { name: 'Chi tiết quyết toán đơn' })
```

### Step 4 — Thực hiện thao tác quản lý/quyết toán hợp lệ
```browser
click getByRole('button', { name: 'Xác nhận quyết toán ca' })
wait getByRole('alert')
# Assertion: Alert thông báo thành công: "Quyết toán hoàn tất thành công"
# Assertion: Không xuất hiện lỗi bị từ chối quyền cơ sở (403 LOCATION_SCOPE_DENIED)
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Quyết toán hoàn tất thành công"
# Expected: one or more matches
find "LOCATION_SCOPE_DENIED"
# Expected: zero matches
find "Không có quyền thao tác trên cơ sở này"
# Expected: zero matches
```

## 5. Bằng chứng kiểm thử (Evidence)

- Kịch bản chạy thực tế qua Chrome bằng `browsermcp`.
- Ảnh chụp màn hình: `screenshots/E2E-001-023--manager-cross-location-settlement.jpg`.

## 6. Lịch sử thực thi

<!-- EXECUTION:START -->
- Latest status: NOT_RUN
- Note: Kịch bản mới tạo ngày 2026-11-14 kiểm chứng thẩm quyền toàn cục của Manager trên Feat 003 theo Revision R-MGA (T079).
<!-- EXECUTION:END -->
