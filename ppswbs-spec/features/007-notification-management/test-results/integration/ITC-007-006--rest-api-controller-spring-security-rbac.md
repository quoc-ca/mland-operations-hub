---
id: ITC-007-006
feature: 007-notification-management
test_level: integration
test_case_type: B
source_ref: spec.md#FR-027
test_scope: Spring Security Filter Chain & Controller REST Endpoints
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationManagementIT
test_method: verifyRestEndpointsRbacMatrix
---

# ITC-007-006: REST controller endpoints enforce global role authorization matrix

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior QA Engineer

## 1. Mục tiêu kiểm thử tích hợp (Integration Objective)

Xác minh phân quyền truy cập API theo FR-016 & FR-027: Toàn bộ REST endpoint của Notification Management yêu cầu vai trò `ADMIN` hoặc `MANAGER` ở phạm vi toàn cầu; chặn hoàn toàn vai trò `STAFF` và `MEMBER`; chỉ riêng `ADMIN` mới được phép resend đối với `UNKNOWN_OUTCOME`.

## 2. Tiền điều kiện & Môi trường tích hợp (Preconditions & Environment)

- `MockMvc` được cấu hình với Spring Security filter chain.
- Các token giả lập đại diện cho các vai trò: `ANONYMOUS`, `MEMBER`, `STAFF`, `MANAGER`, `ADMIN`.

## 3. Dữ liệu thử nghiệm & Kịch bản thực thi (Test Data & Execution Steps)

### Ma trận kiểm thử các endpoint:
1. `GET /api/v1/notification-templates`
2. `POST /api/v1/notification-templates/{code}/versions`
3. `GET /api/v1/notification-requests`
4. `POST /api/v1/notification-requests/{id}/resend` (với request `SENT`)
5. `POST /api/v1/notification-requests/{id}/resend` (với request `UNKNOWN_OUTCOME`)

## 4. Kiểm chứng kết quả tích hợp (Expected Assertions & Invariants)

- **Người dùng chưa xác thực (Anonymous):**
  - Trả về `401 Unauthorized` cho tất cả các endpoint trên.
- **Người dùng với vai trò `STAFF` hoặc `MEMBER`:**
  - Trả về `403 Forbidden` cho tất cả các endpoint trên.
- **Người dùng với vai trò `MANAGER`:**
  - Trả về `200 OK` cho GET templates và GET requests.
  - Trả về `201 Created` cho resend request `SENT`.
  - Trả về `403 Forbidden` khi resend request `UNKNOWN_OUTCOME`.
- **Người dùng với vai trò `ADMIN`:**
  - Trả về `201 Created` khi resend request `UNKNOWN_OUTCOME` với `duplicateRiskAccepted: true`.

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: NOT_RUN
- Executed at: NOT_RUN
- Executed by: NOT_RUN
- Command: NOT_RUN
- Actual result: NOT_RUN
<!-- EXECUTION:END -->

