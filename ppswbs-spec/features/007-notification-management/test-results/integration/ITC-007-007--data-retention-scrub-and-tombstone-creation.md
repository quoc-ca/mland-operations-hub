---
id: ITC-007-007
feature: 007-notification-management
test_level: integration
test_case_type: N
source_ref: spec.md#FR-025
test_scope: Database Retention Sweep & Tombstone Generation
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationManagementIT
test_method: verifyRetentionJobScrubsPiiAndGeneratesTombstoneInDb
---

# ITC-007-007: Retention job sweeps requests older than 90 days from DB and creates persistent tombstone

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior QA Engineer

## 1. Mục tiêu kiểm thử tích hợp (Integration Objective)

Xác minh yêu cầu FR-025, FR-026 & SC-007: Cron job dọn dẹp dữ liệu (`RetentionService`) quét cơ sở dữ liệu thật, tìm các yêu cầu hoàn tất quá 90 ngày, xóa triệt để rendition và snapshot PII, đồng thời chèn một bản ghi `notification_tombstone` để lưu vết kiểm toán tối thiểu.

## 2. Tiền điều kiện & Môi trường tích hợp (Preconditions & Environment)

- Kết nối cơ sở dữ liệu thật.
- Khởi tạo 1 request với `terminal_at = now() - 95 days`, có rendition và snapshot email.

## 3. Dữ liệu thử nghiệm & Kịch bản thực thi (Test Data & Execution Steps)

### Dữ liệu thử nghiệm:
- Request ID: `888`
- `business_event_reference`: `"BK-RETENTION-888"`
- `recipient_snapshot`: `'{"email": "confidential@example.com"}'`

### Các bước thực thi:
1. Chèn request 888 và rendition 888 vào cơ sở dữ liệu.
2. Gọi thực thi `retentionService.sweepExpiredData(90)`.
3. Kiểm tra các câu lệnh SQL truy vấn trực tiếp DB.

## 4. Kiểm chứng kết quả tích hợp (Expected Assertions & Invariants)

- **Kiểm tra `notification_rendition`:**
  ```sql
  SELECT COUNT(*) FROM notification_rendition WHERE notification_request_id = 888;
  ```
  Trả về `0` (đã bị xóa).
- **Kiểm tra `notification_request`:**
  Cột `recipient_snapshot` và `business_data_snapshot` có giá trị `"[SCRUBBED]"`.
- **Kiểm tra `notification_tombstone`:**
  ```sql
  SELECT * FROM notification_tombstone WHERE original_request_id = 888;
  ```
  Trả về đúng 1 dòng với `business_event_reference = 'BK-RETENTION-888'` và `terminal_state = 'SENT'`.

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: NOT_RUN
- Executed at: NOT_RUN
- Executed by: NOT_RUN
- Command: NOT_RUN
- Actual result: NOT_RUN
<!-- EXECUTION:END -->

