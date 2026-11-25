---
id: E2E-010-001
feature: 010-manager-dashboard
test_level: e2e
test_case_type: A
source_ref: quickstart.md#QS-01,quickstart.md#QS-02,quickstart.md#QS-05,quickstart.md#QS-16
target_url: http://localhost:5173/manager/dashboard
actor_role: Manager
browser_tool: chrome
health_urls:
  - http://localhost:5173
  - http://localhost:8080
---

# E2E-010-001: Dashboard Manager — mặc định, bộ lọc, chặn quyền, làm mới (ST_MD_01, 02, 03, 05)

**Ngày tạo**: 2026-11-26
**Người phụ trách**: Bùi Trọng Thịnh

## 1. Tiền điều kiện

- Tài khoản Manager có gán chi nhánh; tài khoản Staff và Member để kiểm tra chặn quyền.

## 2. Các bước

1. Manager mở `/manager/dashboard`, không chọn gì.
2. Chọn lần lượt kỳ Ngày / Tuần / Tháng / Năm; chọn kỳ Tuần và một vài chi nhánh.
3. Đăng nhập Staff, rồi Member, mở thẳng `/manager/dashboard`.
4. Để dashboard mở khoảng 2 phút không thao tác (DevTools → Network); sau đó bấm **Làm mới**.

## 3. Kết quả mong đợi

- Bước 1: 5 thẻ KPI từ đầu tháng đến hiện tại, tất cả chi nhánh được giao; có biểu đồ xu hướng và Bán chạy nhất.
- Bước 2: KPI, xu hướng và top bán chạy tải lại đúng theo kỳ và chi nhánh.
- Bước 3: bị từ chối, không hiện số liệu.
- Bước 4: không có request tự động; chỉ tải lại khi bấm Làm mới.

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-26 (sau 00:18, giờ chính xác không ghi lại)
- Executed by: Bùi Trọng Thịnh
- Browser tool: Chrome
- Actual result: Bước 1–4 đạt trên local (theo xác nhận của người chạy). Chưa ghi lại riêng bước "thanh toán một đơn mới rồi bấm Làm mới thì đơn được tính". Tài khoản và chi nhánh đã chọn không ghi lại; không lưu ảnh chụp. Phía API đã có ExecutiveReportApiTest 19/19 và ExecutiveReportReadOnlyMySqlTest 1/1 (2026-11-26).
- Bug reference: không có
<!-- EXECUTION:END -->
