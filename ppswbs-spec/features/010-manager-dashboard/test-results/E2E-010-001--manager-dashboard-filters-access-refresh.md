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
4. Để dashboard mở khoảng 2 phút không thao tác (DevTools → Network); sau đó nhấn **F5** (màn hình không có nút Làm mới, FR-023).

## 3. Kết quả mong đợi

- Bước 1: 5 thẻ KPI từ đầu tháng đến hiện tại, tất cả chi nhánh được giao; có biểu đồ xu hướng và Bán chạy nhất.
- Bước 2: KPI, xu hướng và top bán chạy tải lại đúng theo kỳ và chi nhánh.
- Bước 3: bị từ chối, không hiện số liệu.
- Bước 4: không có nút Làm mới; không có request tự động; nhấn F5 thì tải lại và đơn mới thanh toán được tính.

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-26 02:28–02:39, local `localhost:5173` / `localhost:8080`
- Executed by: Bùi Trọng Thịnh
- Browser tool: Chrome
- Test data: tài khoản Manager được giao 2 chi nhánh Mland Hà Nội – Hoàn Kiếm và Mland Hà Nội – Tây Hồ (theo người chạy: `manager.test@mland.vn`); Member `thinhbui732004`; đơn mới `ML2611265F7D9792`, `ML2611268A8F9193` (E2E-008-001).
- Actual result:
  + Bước 1 (02:28): kỳ Tháng 1/11/26 – 02:28 26/11/26, 2 chi nhánh, giờ Việt Nam; 5 thẻ KPI: Doanh thu bán lẻ 12.889.234 đ, Doanh thu workshop 0 đ, Tổng doanh thu 12.889.234 đ, Đơn bán lẻ đã thanh toán 21, Tỷ lệ lấp đầy workshop "—" (chưa có buổi đã chốt điểm danh); có Xu hướng bán lẻ và biểu đồ.
  + Bước 4: sau khi thanh toán 2 đơn mới, dashboard không tự đổi (theo người chạy); nhấn F5 lúc 02:38: Doanh thu bán lẻ 13.897.234 đ (+1.008.000 đ = 576.000 + 432.000), Đơn đã thanh toán 23 (+2).
  + Bước 2 (02:39): kỳ Tuần 23/11/26 – 02:39 26/11/26; chọn Hoàn Kiếm: 1.557.000 đ, 2 đơn, biểu đồ theo ngày có 576k ngày 26/11; chọn Tây Hồ: 1.522.000 đ, 2 đơn; KPI, xu hướng bán lẻ và workshop tải lại theo bộ lọc.
  + Bước 3: Member mở thẳng `/manager/dashboard` bị đưa về trang chủ, không hiện số liệu. Staff: theo người chạy cũng bị chặn, không có ảnh riêng.
- Evidence: screenshots/E2E-010-001--month-before.png, screenshots/E2E-010-001--month-after-f5.png, screenshots/E2E-010-001--week-hoan-kiem.png, screenshots/E2E-010-001--week-tay-ho.png, screenshots/E2E-010-001--member-redirected-home.png
- Bug reference: không có
- Automated evidence (bổ trợ): 2026-11-26 `npx vitest run src/features/manager-dashboard` 6 file, 47/47 passed (mặc định tháng hiện tại và 5 thẻ KPI, bộ lọc kỳ/chi nhánh, từ chối quyền, không tự tải lại theo giờ và không có nút Làm mới); backend ExecutiveReportApiTest 19/19, ExecutiveReportReadOnlyMySqlTest 1/1.
<!-- EXECUTION:END -->
