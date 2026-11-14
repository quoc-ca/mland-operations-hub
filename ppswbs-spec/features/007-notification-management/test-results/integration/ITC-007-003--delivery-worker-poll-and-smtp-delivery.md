---
id: ITC-007-003
feature: 007-notification-management
test_level: integration
test_case_type: N
source_ref: spec.md#FR-011
test_scope: Delivery Worker Scheduler & Mock SMTP Server
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationManagementIT
test_method: verifyWorkerPollAndGreenMailDelivery
---

# ITC-007-003: Scheduled delivery worker polls pending requests and delivers rendered email via SMTP

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior Java/Spring Backend Implementation Engineer

## 1. Mục tiêu kiểm thử tích hợp (Integration Objective)

Xác minh luồng hoàn chỉnh từ lúc enqueue đến khi giao vận thư (FR-011, FR-012, FR-028, WF-02): Delivery worker chạy theo chu kỳ 30s, truy vấn các request `PENDING`, tự động sinh `Rendition` (thay thế biến {{...}}), gửi thư qua SMTP (GreenMail), cập nhật DB sang `SENT` và ghi nhận bản ghi `DeliveryAttempt`.

## 2. Tiền điều kiện & Môi trường tích hợp (Preconditions & Environment)

- GreenMail mock SMTP server khởi chạy tại `localhost:3025`.
- Backend cấu hình `mail.host=localhost`, `mail.port=3025`, `mail.sender=noreply@mland.vn`.
- Template `BOOKING_CONFIRMED` có nội dung HTML chứa các biến: `{{customerName}}`, `{{bookingCode}}`, `{{ticketUrl}}`.

## 3. Dữ liệu thử nghiệm & Kịch bản thực thi (Test Data & Execution Steps)

### Dữ liệu thử nghiệm:
- Enqueue request `businessEventReference = "BK-E2E-INT-001"`.
- `recipientEmail = "customer.real@example.com"`.
- `businessDataSnapshot = {"customerName": "Nguyen Van A", "bookingCode": "BK-E2E-INT-001", "ticketUrl": "https://mland.vn/tickets/sample-123"}`.

### Các bước thực thi:
1. Gọi `notificationFacade.submitNotificationIntent(...)` và commit giao dịch.
2. Xác nhận request nằm ở DB với `delivery_state = 'PENDING'`.
3. Kích hoạt phương thức `deliveryWorkerService.pollAndDeliver()`.
4. Chờ GreenMail nhận được email (hoặc dùng `greenMail.waitForIncomingEmail(5000, 1)`).

## 4. Kiểm chứng kết quả tích hợp (Expected Assertions & Invariants)

- GreenMail nhận được đúng 1 email gửi tới `customer.real@example.com`.
- Email nhận được có:
  - Header `From: noreply@mland.vn`
  - `Subject` chứa `"BK-E2E-INT-001"`
  - Body HTML chứa `"Nguyen Van A"` và liên kết `href="https://mland.vn/tickets/sample-123"`
- **Kiểm tra DB:**
  - `notification_request.delivery_state` chuyển thành `'SENT'`.
  - `notification_request.attempt_count` = 1.
  - `notification_request.terminal_at` được ghi nhận.
  - Bảng `notification_rendition` chứa nội dung đã render tương ứng.
  - Bảng `notification_delivery_attempt` có 1 bản ghi với `outcome_category = 'SUCCESS'`.

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-09T09:43:36Z
- Executed by: Senior Java/Spring Backend Implementation Engineer
- Command: `mvn -q -Dtest=NotificationManagementIT#verifyWorkerPollAndGreenMailDelivery test`
- Surefire evidence: `target/surefire-reports/TEST-edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationManagementIT.xml` testcase `verifyWorkerPollAndGreenMailDelivery`
- Actual result: PASS
- Runs: 1
- PASS: 1
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->

