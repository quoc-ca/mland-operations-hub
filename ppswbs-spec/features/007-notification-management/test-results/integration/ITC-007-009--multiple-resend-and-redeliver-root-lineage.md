---
id: ITC-007-009
feature: 007-notification-management
test_level: integration
test_case_type: B
source_ref: spec.md#FR-006
test_scope: Multiple Resend and Redelivery Lineage & Original Idempotency Constraint Isolation (T034)
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationManagementIT
test_method: verifyMultipleResendAndRedeliverOnSameRootSucceeds
---

# ITC-007-009: Multiple eligible resend and redelivery requests linked to same root succeed without unique constraint collision

**Ngày tạo**: 2026-11-14  
**Ngày cập nhật**: 2026-11-14  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Người cập nhật cuối**: Senior Java/Spring Backend Implementation Engineer  

## 1. Mục tiêu kiểm thử tích hợp (Integration Objective)

Xác minh yêu cầu **FR-006, FR-015, FR-019**, Task **T034** và khắc phục dứt điểm khuyết tật **BUG-E2E-0004**:
- Ràng buộc duy nhất `uq_nr_original_idempotency` trên `notification_request` chỉ áp dụng cho request gốc (`original_idempotency_scope = 1`).
- Đối với các request con phát sinh từ hành động gửi lại (`resend`) hoặc cấp lại link (`redeliver`), `original_idempotency_scope` là `NULL`. Do cơ chế SQL bỏ qua duplicate NULLs, hệ thống cho phép tạo nhiều request con liên tiếp trỏ về cùng một request gốc mà không xảy ra ngoại lệ `DataIntegrityViolationException`.
- Khi kích hoạt `redeliver` từ một derived request (ví dụ request sinh ra từ lần resend trước), hệ thống phải tự động chuẩn hóa làm phẳng phả hệ (*lineage flattening*), đảm bảo trường `original_request_id` luôn trỏ trực tiếp về request gốc chuẩn tắc (canonical root ID).

## 2. Tiền điều kiện & Môi trường tích hợp (Preconditions & Environment)

- Cơ sở dữ liệu đã nạp Flyway migration `V140__notification_original_request_idempotency.sql` (hoặc tương đương) loại bỏ `uq_nr_idempotency` cũ và kích hoạt cột sinh `original_idempotency_scope` kèm index duy nhất mới.
- Spring Modulith context khởi tạo đầy đủ các bean `OperationalService`, `NotificationRequestRepository`, `OperationalAuditRepository`.
- Đã tồn tại một request gốc hợp lệ ở trạng thái `SENT` trong DB.

## 3. Dữ liệu thử nghiệm & Kịch bản thực thi (Test Data & Execution Steps)

### Dữ liệu thử nghiệm:
- Root Request: ID sinh tự động, `business_event_reference` = `"BK-MULTI-001"`, `delivery_state` = `"SENT"`.
- Actor: `CurrentAccountActor` vai trò `MANAGER` (ID: 1).
- Resend Command 1: `reason` = `"First resend by manager"`.
- Resend Command 2: `reason` = `"Second resend by manager"`.
- Redeliver Command 1: `reason` = `"First redeliver from root"`.
- Redeliver Command 2: `reason` = `"Second redeliver triggered on derived request"`.

### Các bước thực thi:
1. Tạo và lưu request gốc `root` (`state = SENT`, `originalRequest = null`).
2. Gọi `operationalService.resendRequest(root.getId(), managerActor, resendCmd1)`.
3. Gọi `operationalService.resendRequest(root.getId(), managerActor, resendCmd2)`.
4. Gọi `operationalService.redeliverWithRefreshedCredential(root.getId(), managerActor, redeliverCmd1)`.
5. Gọi `operationalService.redeliverWithRefreshedCredential(resend1.newRequestId(), managerActor, redeliverCmd2)`.
6. Truy vấn 4 bản ghi con được sinh ra từ repository để kiểm tra tính toàn vẹn dữ liệu và phả hệ.

## 4. Kiểm chứng kết quả tích hợp (Expected Assertions & Invariants)

- Cả 4 lệnh trên đều thực thi thành công (HTTP 201 Created / Service trả về `newRequestId != null`).
- Không phát sinh ngoại lệ `DataIntegrityViolationException` hay HTTP 500.
- Tất cả 4 request con (`resend1`, `resend2`, `redeliver1`, `redeliver2`) đều có:
  - `originalRequest.getId() == root.getId()` (Derived request sinh từ resend1 được làm phẳng trực tiếp về root).
  - `deliveryState == "PENDING"`.
- Request gốc `root` giữ nguyên trạng thái `SENT` và dữ liệu nghiệp vụ không bị ghi đè.

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14T16:07:57+07:00
- Executed by: Senior QA & System Verification Engineer
- Command: `.\mvnw.cmd test -Dtest=NotificationManagementIT#verifyMultipleResendAndRedeliverOnSameRootSucceeds`
- Surefire evidence: `target/surefire-reports/TEST-edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationManagementIT.xml` testcase `verifyMultipleResendAndRedeliverOnSameRootSucceeds`
- Actual result: BUILD SUCCESS — Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 35.75 s
- Runs: 1
- PASS: 1
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->
