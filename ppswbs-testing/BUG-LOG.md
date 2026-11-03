# Sổ Theo Dõi Lỗi Hệ Thống (Defect & Bug Tracking Log)

File nhật ký theo dõi lỗi tập trung do `ppswbs-testing/AGENT.md` quản lý.  
Mọi lỗi phát hiện trong quá trình kiểm thử Unit, Integration hoặc E2E bằng `browsermcp` đều phải được ghi nhận tại đây để bàn giao cho các bên liên quan xử lý.

---

## Danh sách lỗi đang theo dõi (Active Defects)

<!-- Ghi nhận lỗi mới nhất lên trên đầu theo mẫu dưới đây -->

<!--
## BUG-E2E-0001 — OPEN

- Ngày: YYYY-MM-DD
- Phát hiện bởi: QA Agent (Tester)
- Feature: 003-workshop-management
- Kịch bản liên quan: [E2E-003-001](../ppswbs-spec/features/003-workshop-management/test-results/E2E-003-001--booking-flow.md)
- Mức độ nghiêm trọng: CRITICAL
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Nút "Xác nhận đặt lịch" bị vô hiệu hóa dù đã chọn đủ thời gian và số lượng người.
- Các bước tái hiện:
  1. goto http://localhost:5173/workshops/1
  2. click getByRole('button', { name: 'Chọn suất 09:00' })
  3. fill getByLabel('Số lượng khách') "2"
  4. Quan sát nút "Xác nhận đặt lịch"
- Kết quả mong đợi: Nút "Xác nhận đặt lịch" chuyển sang trạng thái enabled và cho phép click.
- Thực tế quan sát: Nút bị disabled vĩnh viễn, console log báo lỗi "Uncaught TypeError: selectedSlot.capacity is undefined".
- Bằng chứng:
  - Screenshot: ![Lỗi](../ppswbs-spec/features/003-workshop-management/test-results/screenshots/E2E-003-001--slot-disabled.jpg)
-->

*(Hiện tại chưa có lỗi nào được ghi nhận)*

