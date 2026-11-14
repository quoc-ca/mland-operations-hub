# REST API Contract: Notification Management

**Date created**: 2026-11-06
**Date updated**: 2026-11-06
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Base URL**: `/api/v1`
**Authorization**: `Authorization: Bearer <Firebase_ID_Token>`
**Scope**: In V1, all endpoints require `ADMIN` or `MANAGER` role at global scope (FR-027).

---

## 1. Template Registry & Version Endpoints

### 1.1. `GET /api/v1/notification-templates`
List all registered template definitions and their active versions.

- **Actor**: `ADMIN`, `MANAGER`
- **Response 200 OK**:
```json
[
  {
    "code": "BOOKING_CONFIRMED",
    "name": "Xác nhận đặt lịch Workshop",
    "description": "Gộp booking, tiền cọc và vé QR",
    "owningModule": "workshopbooking",
    "lifecycleStatus": "ACTIVE",
    "allowedPlaceholders": ["customerName", "bookingCode", "depositAmount", "workshopTitle", "startTime", "locationAddress", "ticketUrl"],
    "activeVersion": {
      "versionNumber": 2,
      "subjectTemplate": "[Mland] Xác nhận đặt lịch workshop #{{bookingCode}}",
      "activatedAt": "2026-11-05T10:00:00Z",
      "activatedBy": "acc_mgr_01"
    },
    "latestDraftVersionNumber": null
  },
  {
    "code": "RETAIL_ORDER_CONFIRMED",
    "name": "Xác nhận đơn hàng bán lẻ",
    "description": "Đơn hàng thanh toán VNPay hoặc 0 đồng",
    "owningModule": "readyringsales",
    "lifecycleStatus": "ACTIVE",
    "allowedPlaceholders": ["customerName", "orderCode", "orderTotal", "orderDetailsUrl"],
    "activeVersion": {
      "versionNumber": 1,
      "subjectTemplate": "[Mland] Xác nhận đơn hàng #{{orderCode}}",
      "activatedAt": "2026-11-01T08:00:00Z",
      "activatedBy": "acc_admin_01"
    },
    "latestDraftVersionNumber": 2
  },
  {
    "code": "AUTH_EMAIL_VERIFICATION",
    "name": "Xác minh Email",
    "description": "Xác minh tài khoản qua Firebase Auth",
    "owningModule": "firebase_auth",
    "lifecycleStatus": "EXTERNAL",
    "allowedPlaceholders": ["firebaseVerificationUrl"],
    "activeVersion": null,
    "latestDraftVersionNumber": null
  }
]
```

---

### 1.2. `GET /api/v1/notification-templates/{code}`
Get details of a template definition, including all draft, active, and historical versions.

- **Actor**: `ADMIN`, `MANAGER`
- **Response 200 OK**:
```json
{
  "code": "BOOKING_CONFIRMED",
  "name": "Xác nhận đặt lịch Workshop",
  "description": "Gộp booking, tiền cọc và vé QR",
  "owningModule": "workshopbooking",
  "lifecycleStatus": "ACTIVE",
  "allowedPlaceholders": ["customerName", "bookingCode", "depositAmount", "workshopTitle", "startTime", "locationAddress", "ticketUrl"],
  "versions": [
    {
      "versionNumber": 2,
      "status": "ACTIVE",
      "subjectTemplate": "[Mland] Xác nhận đặt lịch workshop #{{bookingCode}}",
      "htmlTemplate": "<html>...</html>",
      "plainTextTemplate": "Chào {{customerName}}...",
      "createdAt": "2026-11-05T09:00:00Z",
      "createdBy": "acc_mgr_01",
      "activatedAt": "2026-11-05T10:00:00Z",
      "activatedBy": "acc_mgr_01"
    },
    {
      "versionNumber": 1,
      "status": "DEACTIVATED",
      "subjectTemplate": "[Mland] Đặt lịch workshop #{{bookingCode}} thành công",
      "htmlTemplate": "<html>...</html>",
      "plainTextTemplate": "...",
      "createdAt": "2026-10-15T08:00:00Z",
      "createdBy": "acc_admin_01",
      "activatedAt": "2026-10-15T08:30:00Z",
      "activatedBy": "acc_admin_01"
    }
  ]
}
```

---

### 1.3. `POST /api/v1/notification-templates/{code}/versions`
Create a new `DRAFT` version for a template definition.

- **Actor**: `ADMIN`, `MANAGER`
- **Request Body**:
```json
{
  "subjectTemplate": "[Mland] Cập nhật: Xác nhận đặt lịch workshop #{{bookingCode}}",
  "htmlTemplate": "<p>Chào {{customerName}}, đơn đặt #{{bookingCode}} đã xác nhận.</p>",
  "plainTextTemplate": "Chào {{customerName}}, đơn đặt #{{bookingCode}} đã xác nhận."
}
```
- **Response 201 Created**:
```json
{
  "versionNumber": 3,
  "status": "DRAFT",
  "subjectTemplate": "[Mland] Cập nhật: Xác nhận đặt lịch workshop #{{bookingCode}}",
  "htmlTemplate": "<p>Chào {{customerName}}, đơn đặt #{{bookingCode}} đã xác nhận.</p>",
  "plainTextTemplate": "Chào {{customerName}}, đơn đặt #{{bookingCode}} đã xác nhận.",
  "createdAt": "2026-11-06T15:00:00Z",
  "createdBy": "acc_admin_01"
}
```

---

### 1.4. `PUT /api/v1/notification-templates/{code}/versions/{versionNumber}`
Update an existing `DRAFT` version. Cannot update `ACTIVE` or `DEACTIVATED` versions.

- **Actor**: `ADMIN`, `MANAGER`
- **Request Body**:
```json
{
  "subjectTemplate": "[Mland] Xác nhận đặt lịch workshop #{{bookingCode}}",
  "htmlTemplate": "<p>Nội dung mới...</p>",
  "plainTextTemplate": "Nội dung mới..."
}
```
- **Response 200 OK**: Updated version representation.
- **Error 409 Conflict**: If version is not in `DRAFT` state (`CANNOT_EDIT_NON_DRAFT_VERSION`).

---

### 1.5. `POST /api/v1/notification-templates/{code}/versions/{versionNumber}/activate`
Activate a draft version. Invariant: automatically deactivates any existing `ACTIVE` version.

- **Actor**: `ADMIN`, `MANAGER`
- **Response 200 OK**:
```json
{
  "activatedVersionNumber": 3,
  "deactivatedVersionNumber": 2,
  "activatedAt": "2026-11-06T15:05:00Z",
  "activatedBy": "acc_admin_01"
}
```

---

### 1.6. `POST /api/v1/notification-templates/{code}/preview`
Render preview of a template using safe fixture/redacted data. Never uses real customer data or raw credentials.

- **Actor**: `ADMIN`, `MANAGER`
- **Request Body**:
```json
{
  "versionNumber": 3,
  "sampleData": {
    "customerName": "Nguyễn Văn A",
    "bookingCode": "BK-TEST-123",
    "depositAmount": "500,000 VND",
    "workshopTitle": "Làm nhẫn đôi cơ bản",
    "startTime": "09:00 15/11/2026",
    "locationAddress": "Số 123 Đường ABC, Hà Nội",
    "ticketUrl": "https://mland.vn/tickets/preview-sample"
  }
}
```
- **Response 200 OK**:
```json
{
  "renderedSubject": "[Mland] Xác nhận đặt lịch workshop #BK-TEST-123",
  "renderedHtml": "<html>...</html>",
  "renderedPlainText": "Chào Nguyễn Văn A..."
}
```

---

## 2. Notification Operations & Delivery Diagnostics Endpoints

### 2.1. `GET /api/v1/notification-requests`
Query notification requests with filtering and pagination.

- **Actor**: `ADMIN`, `MANAGER`
- **Query Parameters**:
  - `templateCode`: string (optional)
  - `deliveryState`: `PENDING` | `PROCESSING` | `RETRY_SCHEDULED` | `SENT` | `FAILED` | `UNKNOWN_OUTCOME` | `CANCELLED` (optional)
  - `businessEventReference`: string (optional)
  - `from`: ISO Instant (optional)
  - `to`: ISO Instant (optional)
  - `page`: integer (default 0)
  - `size`: integer (default 20)
- **Response 200 OK**:
```json
{
  "content": [
    {
      "id": 1001,
      "businessEventReference": "BK-20261106-001",
      "templateCode": "BOOKING_CONFIRMED",
      "recipientAddressMasked": "n***a@gmail.com",
      "deliveryState": "SENT",
      "attemptCount": 1,
      "createdAt": "2026-11-06T14:30:00Z",
      "terminalAt": "2026-11-06T14:30:25Z"
    },
    {
      "id": 1002,
      "businessEventReference": "ORD-20261106-042",
      "templateCode": "RETAIL_ORDER_CONFIRMED",
      "recipientAddressMasked": "t***b@yahoo.com",
      "deliveryState": "RETRY_SCHEDULED",
      "attemptCount": 1,
      "nextAttemptAt": "2026-11-06T14:32:00Z",
      "createdAt": "2026-11-06T14:31:00Z",
      "terminalAt": null
    }
  ],
  "totalElements": 2,
  "totalPages": 1
}
```

---

### 2.2. `GET /api/v1/notification-requests/{id}`
Inspect detailed diagnostics for a single request, including attempt history and operational audit.

- **Actor**: `ADMIN`, `MANAGER`
- **Response 200 OK**:
```json
{
  "id": 1002,
  "businessEventReference": "ORD-20261106-042",
  "templateCode": "RETAIL_ORDER_CONFIRMED",
  "templateVersionId": 1,
  "recipientRole": "CUSTOMER",
  "recipientAddressMasked": "t***b@yahoo.com",
  "deliveryState": "RETRY_SCHEDULED",
  "attemptCount": 1,
  "nextAttemptAt": "2026-11-06T14:32:00Z",
  "createdAt": "2026-11-06T14:31:00Z",
  "terminalAt": null,
  "originalRequestId": null,
  "attempts": [
    {
      "attemptNumber": 1,
      "startedAt": "2026-11-06T14:31:05Z",
      "completedAt": "2026-11-06T14:31:10Z",
      "outcomeCategory": "NETWORK_TEMPORARY",
      "providerMessageId": null,
      "safeErrorSummary": "Connection timed out connecting to mail host"
    }
  ],
  "audits": []
}
```

---

### 2.3. `POST /api/v1/notification-requests/{id}/resend`
Manually resend an eligible sent or remediated failed request with an auditable reason.

Repeated eligible manual resend actions are permitted in V1. This endpoint has no client-supplied idempotency-key contract; each accepted invocation creates one derived request.

- **Actor**: `ADMIN`, `MANAGER`
- **Eligibility**:
  - Request must be in `SENT` or `FAILED`.
  - Prohibited if `PENDING`, `PROCESSING`, `RETRY_SCHEDULED`.
  - For `UNKNOWN_OUTCOME`, ONLY `ADMIN` can resend and must explicitly set `duplicateRiskAccepted: true`.
- **Request Body**:
```json
{
  "reason": "Khách hàng báo chưa nhận được email xác nhận trong hộp thư đến",
  "duplicateRiskAccepted": false
}
```
- **Response 201 Created**:
```json
{
  "newRequestId": 1005,
  "originalRequestId": 1002,
  "deliveryState": "PENDING",
  "createdAt": "2026-11-06T15:30:00Z",
  "message": "Resend request enqueued successfully."
}
```
- **Error 400 Bad Request**: If state is not eligible or reason is empty.
- **Error 403 Forbidden**: If Manager attempts resend of `UNKNOWN_OUTCOME`.

---

### 2.4. `POST /api/v1/notification-requests/{id}/cancel`
Cancel a pending or scheduled delivery request with an auditable reason.

- **Actor**: `ADMIN`, `MANAGER`
- **Eligibility**:
  - Request must be in `PENDING` or `RETRY_SCHEDULED`.
  - Prohibited if `PROCESSING`, `SENT`, `FAILED`, `CANCELLED`.
- **Request Body**:
```json
{
  "reason": "Khách hàng hủy đặt lịch ngay sau khi xác nhận, dừng gửi email."
}
```
- **Response 200 OK**:
```json
{
  "requestId": 1002,
  "deliveryState": "CANCELLED",
  "terminalAt": "2026-11-06T15:31:00Z",
  "cancelledBy": "acc_mgr_01",
  "reason": "Khách hàng hủy đặt lịch ngay sau khi xác nhận, dừng gửi email."
}
```
- **Error 409 Conflict**: If request is already `PROCESSING` or in terminal state.

---

### 2.5. `POST /api/v1/notification-requests/{id}/redeliver`
Request redelivery with a refreshed credential (when original credential/link expired or was revoked).

The service resolves the root/original request for lineage, asks the owning source feature to authorize and supply a replacement credential/artifact, and creates the derived request only when that succeeds. It MUST NOT reuse the expired or revoked credential. Repeated eligible manual redelivery actions are permitted in V1; this endpoint has no client-supplied idempotency-key contract.

- **Actor**: `ADMIN`, `MANAGER`
- **Request Body**:
```json
{
  "reason": "Link vé QR của khách đã hết hạn sau 24h, cấp link mới."
}
```
- **Response 201 Created**:
```json
{
  "newRequestId": 1008,
  "originalRequestId": 1001,
  "deliveryState": "PENDING",
  "createdAt": "2026-11-06T15:45:00Z",
  "message": "Redelivery request enqueued with replacement credential."
}
```
- **Error 422 Unprocessable Entity**: If source feature denies authorization or cannot provide fresh credential.

