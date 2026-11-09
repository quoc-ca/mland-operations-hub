---
id: ITC-007-001
feature: 007-notification-management
test_level: integration
test_case_type: A
source_ref: spec.md#FR-003
test_scope: Spring Boot Transaction & Facade Boundary
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationManagementIT
test_method: verifyRollbackSafetyOnBusinessTransactionException
---

# ITC-007-001: In-transaction notification request rolls back completely on caller business exception

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior QA Engineer

## 1. Mục tiêu kiểm thử tích hợp (Integration Objective)

Xác minh tính bất biến kiến trúc (FR-003, SC-001): Khi một nghiệp vụ nguồn (ví dụ xác nhận Booking hoặc Order) gọi `NotificationFacade.submitNotificationIntent(...)` nhưng sau đó transaction của nghiệp vụ bị rollback do lỗi (ví dụ lỗi thanh toán, lỗi lock DB), toàn bộ bản ghi notification request trong cơ sở dữ liệu phải được rollback hoàn toàn và GreenMail mock server không nhận được bất kỳ email nào.

## 2. Tiền điều kiện & Môi trường tích hợp (Preconditions & Environment)

- Ứng dụng chạy profile `test` với database thật (PostgreSQL test container hoặc H2).
- Mock SMTP server (GreenMail) đang lắng nghe trên cổng test `3025`.
- Đã seed dữ liệu mẫu cho template `BOOKING_CONFIRMED` với version 1 `ACTIVE`.

## 3. Dữ liệu thử nghiệm & Kịch bản thực thi (Test Data & Execution Steps)

### Dữ liệu thử nghiệm:
- `businessEventReference`: `"BK-TEST-ROLLBACK-001"`
- `templateCode`: `"BOOKING_CONFIRMED"`
- `recipientEmail`: `"customer.rollback@example.com"`
- `businessDataSnapshot`: `{"customerName": "Tester Rollback", "bookingCode": "BK-TEST-ROLLBACK-001"}`

### Các bước thực thi:
1. Mở một transaction dịch vụ mô phỏng luồng xác nhận đặt chỗ workshop.
2. Gọi `notificationFacade.submitNotificationIntent(command)`: nhận kết quả `status = ENQUEUED`.
3. Giả lập phát sinh ngoại lệ `PaymentGatewayException("Simulated payment confirmation failure")` trước khi transaction commit.
4. Bắt ngoại lệ và kiểm tra transaction đã được rollback bởi Spring `PlatformTransactionManager`.
5. Đợi worker polling cycle 30 giây (hoặc kích hoạt worker poll theo cách thủ công).

## 4. Kiểm chứng kết quả tích hợp (Expected Assertions & Invariants)

- **Kiểm tra DB:**
  ```sql
  SELECT COUNT(*) FROM notification_request WHERE business_event_reference = 'BK-TEST-ROLLBACK-001';
  ```
  Kết quả trả về chính xác là `0`.
- **Kiểm tra Mock SMTP:**
  GreenMail server nhận chính xác `0` email gửi tới `customer.rollback@example.com`.

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: NOT_RUN
- Executed at: NOT_RUN
- Executed by: NOT_RUN
- Command: NOT_RUN
- Actual result: NOT_RUN
<!-- EXECUTION:END -->

