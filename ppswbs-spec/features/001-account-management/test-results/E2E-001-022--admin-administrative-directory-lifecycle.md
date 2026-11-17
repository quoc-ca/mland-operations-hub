---
id: E2E-001-022
feature: 001-account-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-042
target_url: http://localhost:5173/admin/administrative-divisions
actor_role: Admin
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080
test_data:
  fixture: admin-administrative-directory
  account_ref: ADMIN_001
  isolation_key: E2E-001-022-20261114
cleanup:
  required: true
  method: revert-active-directory
---

# E2E-001-022: Quản trị danh mục hành chính — Bản nháp, chỉnh sửa siêu dữ liệu và kích hoạt phiên bản duy nhất

**Ngày tạo**: 2026-11-14  
**Ngày cập nhật**: 2026-11-14  
**Người phụ trách**: Phan Sơn  
**Người cập nhật cuối**: Phan Sơn  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [x] Môi trường Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [x] Người dùng đăng nhập với vai trò Admin và mở trang Quản lý danh mục hành chính `/admin/administrative-divisions`.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Nguyên tắc duy nhất 1 phiên bản hiệu lực (Single Active Directory - FR-042):**
  - Tại mọi thời điểm, hệ thống chỉ có ĐÚNG 1 phiên bản danh mục ở trạng thái `ACTIVE`.
  - Phiên bản đang `ACTIVE` không được phép sửa trực tiếp (bất biến) để bảo đảm tính toàn vẹn địa chỉ đang dùng của khách hàng. Muốn sửa, Admin bắt buộc phải tạo bản nháp (`DRAFT`) bằng cách sao chép từ bản active hiện tại.
- **Ràng buộc siêu dữ liệu (Metadata Invariants):**
  - Bản nháp phải có đầy đủ: `version` (chuỗi định danh phiên bản không trùng lặp), `sourceUrl` (nguồn chuẩn hóa, ví dụ Quyết định Tổng cục Thống kê), và `effectiveFrom` (thời điểm bắt đầu áp dụng hợp lệ).
  - Thử kích hoạt (`Activate`) khi metadata thiếu hoặc chưa qua validation phải bị chặn lại.
- **Hiệu ứng chuyển đổi trạng thái nguyên tử (Atomic Activation):**
  - Khi một bản nháp được kích hoạt thành công: Phiên bản đó chuyển sang `ACTIVE`, còn phiên bản active cũ tự động chuyển sang trạng thái `SUPERSEDED` (đã lưu trữ). Các địa chỉ cũ vẫn giữ nguyên mã phiên bản lịch sử mà không bị re-mapping tự động.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang quản trị danh mục hành chính và kiểm tra phiên bản hiện tại
```browser
goto http://localhost:5173/admin/administrative-divisions
wait getByRole('heading', { name: 'Quản lý danh mục hành chính' })
# Assertion: Đúng một phiên bản có huy hiệu "Đang áp dụng" (ACTIVE)
# Assertion: Phiên bản ACTIVE không có nút "Chỉnh sửa trực tiếp"
```

### Step 2 — Bấm tạo bản nháp từ phiên bản đang hoạt động
```browser
click getByRole('button', { name: 'Tạo bản nháp mới từ phiên bản hiện tại' })
wait getByRole('dialog')
wait getByRole('heading', { name: 'Tạo bản nháp danh mục' })
```

### Step 3 — Nhập thông tin siêu dữ liệu cho bản nháp
```browser
fill getByLabel('Mã phiên bản (Version) *') "VN-ADMIN-2026-V2"
fill getByLabel('Nguồn tham chiếu (Source URL) *') "https://danhmuchanhchinh.gso.gov.vn"
click getByRole('button', { name: 'Lưu bản nháp' })
wait getByText('Bản nháp được khởi tạo thành công')
```

### Step 4 — Kiểm tra validation trước khi kích hoạt
```browser
# Mở chi tiết bản nháp vừa tạo
click getByRole('button', { name: 'Xem chi tiết bản nháp' })
# Thử bấm Kích hoạt khi chưa xác nhận thời điểm hiệu lực
click getByRole('button', { name: 'Kích hoạt phiên bản này' })
wait getByRole('dialog')
wait getByText('Xác nhận kích hoạt phiên bản')
click getByRole('button', { name: 'Xác nhận kích hoạt' })
```

### Step 5 — Kiểm tra chuyển đổi trạng thái thành công
```browser
wait getByText('Kích hoạt danh mục hành chính thành công')
# Assertion: Bản "VN-ADMIN-2026-V2" chuyển thành "Đang áp dụng" (ACTIVE)
# Assertion: Bản cũ chuyển thành trạng thái "Đã lưu trữ" (SUPERSEDED)
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "VN-ADMIN-2026-V2"
# Expected: one or more matches
find "Đang áp dụng"
# Expected: exactly 1 match
find "Đã lưu trữ"
# Expected: one or more matches
```

## 5. Bằng chứng kiểm thử (Evidence)

- Kịch bản chạy thực tế qua Chrome bằng `browsermcp`.
- Ảnh chụp màn hình:
  - Bản nháp và metadata: `screenshots/E2E-001-022--directory-draft.jpg`
  - Kích hoạt thành công: `screenshots/E2E-001-022--directory-activated.jpg`

## 6. Lịch sử thực thi

<!-- EXECUTION:START -->
- Latest status: NOT_RUN
- Note: Kịch bản mới tạo ngày 2026-11-14 theo quy định quản lý danh mục hành chính (FR-042).
<!-- EXECUTION:END -->
