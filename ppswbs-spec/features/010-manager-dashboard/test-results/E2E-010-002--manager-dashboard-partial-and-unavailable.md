---
id: E2E-010-002
feature: 010-manager-dashboard
test_level: e2e
test_case_type: A
source_ref: quickstart.md#QS-12,quickstart.md#QS-13,quickstart.md#QS-14
target_url: http://localhost:5173/manager/dashboard
actor_role: Manager
browser_tool: chrome
health_urls:
  - http://localhost:5173
  - http://localhost:8080
---

# E2E-010-002: Dashboard Manager — nguồn dữ liệu thiếu hoặc lỗi (ST_MD_04)

**Ngày tạo**: 2026-11-26
**Người phụ trách**: Bùi Trọng Thịnh

## 1. Tiền điều kiện

- Manager đã mở `/manager/dashboard` và thấy số liệu; Chrome DevTools mở tab Network.

## 2. Các bước

1. Thiếu một nguồn: chuột phải request `executive-reports` → **Override content**; sửa `status` thành `PARTIAL`, `totals.workshopRevenue` và `totals.totalRevenue` thành `null`; tải lại trang.
2. Lỗi tất cả: chuột phải request `executive-reports` → **Block request URL**; đổi kỳ (ví dụ chọn Tuần) để dashboard tải lại. Sau đó gỡ chặn và bấm **Thử lại**.

## 3. Kết quả mong đợi

- Bước 1: khung cảnh báo dữ liệu một phần; ô doanh thu workshop và tổng doanh thu để trống, không hiện 0.
- Bước 2: thông báo lỗi kèm nút Thử lại, số liệu cũ vẫn giữ; gỡ chặn và Thử lại thì tải lại được.
- Phía server đã được kiểm bằng ExecutiveReportApiTest (QS-12, QS-13, QS-14).

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-26, sau 02:39 (ngay sau E2E-010-001), local
- Executed by: Bùi Trọng Thịnh
- Browser tool: Chrome (DevTools: Override content, Block request URL)
- Test data: tài khoản Manager như E2E-010-001; request `GET /api/v1/executive-reports`.
- Actual result: Theo người chạy: bước 1 hiện khung cảnh báo dữ liệu một phần, ô doanh thu workshop và tổng doanh thu để trống (không hiện 0); bước 2 hiện thông báo lỗi kèm nút Thử lại, số liệu cũ vẫn giữ, gỡ chặn và Thử lại thì tải lại được.
- Evidence: không lưu ảnh chụp cho kịch bản này.
- Bug reference: không có
- Automated evidence (bổ trợ): 2026-11-26 `npx vitest run src/features/manager-dashboard` 47/47 passed, gồm "shows unknown values as a dash with a partial-data notice, never as zero", "offers Retry when the best sellers fail for any other reason", "names a trend source that could not be read and draws it as unknown"; backend ExecutiveReportApiTest QS-12, QS-13, QS-14 passed.
<!-- EXECUTION:END -->
