# UI Flow Design: FE-007 Notification Management

**Date created**: 2026-11-06
**Date updated**: 2026-11-06
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Workflows**: [workflows.md](workflows.md)

---

## 1. Actors, Routes, and Authorization

In V1, all notification management UI screens are accessible only to internal users with **`ADMIN`** or **`MANAGER`** roles at **global scope** (FR-027). Public users (Guest, Member) and Staff have no access to these management screens.

| Route | View Name | Allowed Roles | Scope | Description |
|---|---|---|---|---|
| `/admin/notifications/templates` | `TemplateListPage` | `ADMIN`, `MANAGER` | Global | Overview of all registered email templates, statuses, and active version numbers. |
| `/admin/notifications/templates/:code` | `TemplateDetailView` | `ADMIN`, `MANAGER` | Global | Version history, current active content, draft editor, and preview modal. |
| `/admin/notifications/requests` | `NotificationRequestListPage` | `ADMIN`, `MANAGER` | Global | Delivery queue, logs, search by event ref/date, attempt count, state filters. |
| `/admin/notifications/requests/:id` | `NotificationRequestDetailDrawer` | `ADMIN`, `MANAGER` | Global | Attempt logs, error summaries, audit records, and action buttons. |

---

## 2. Screen and Interaction Flows

| Screen / Flow | Entry & Actor | Workflow ID | Component / Hook Link | API Reference | Loading / Empty / Validation / Success / Error States | Recovery |
|---|---|---|---|---|---|---|
| **Template Registry List** | Navigation bar `Thông báo > Quản lý mẫu thư` (Admin, Manager) | WF-05, WF-06 | `TemplateListPage.jsx`<br>`useTemplates.js` | `GET /api/v1/notification-templates` | - **Loading**: Skeleton table rows.<br>- **Empty**: "Chưa có mẫu thông báo nào được đăng ký."<br>- **Success**: Badge trạng thái (`ACTIVE`, `EXTERNAL`, `DRAFT`), số phiên bản hiện tại, nút "Xem chi tiết". | Refresh button on network error. |
| **Template Version Editor** | Click "Tạo bản nháp" hoặc "Sửa bản nháp" trong TemplateDetailView | WF-05 | `TemplateVersionEditorModal.jsx`<br>`useTemplateVersions.js` | `POST / PUT /api/v1/notification-templates/:code/versions` | - **Validation**: Cảnh báo ngay nếu biến trong `{{...}}` không nằm trong `allowed_placeholders`.<br>- **Success**: Toast "Đã lưu bản nháp v{N}".<br>- **Error**: Modal hiển thị lỗi cú pháp hoặc xung đột phiên bản. | Giữ nguyên nội dung soạn thảo khi submit lỗi, không mất dữ liệu người dùng. |
| **Template Preview** | Click "Xem trước" trong Editor hoặc Detail view | WF-07 | `TemplatePreviewModal.jsx` | `POST /api/v1/notification-templates/:code/preview` | - **Loading**: Spinner trong khung iframe preview.<br>- **Success**: Iframe render HTML với dữ liệu giả định; Tab thứ 2 xem nội dung Plain Text.<br>- **Safety Check**: Banner xanh "Dữ liệu mẫu an toàn - Không sử dụng dữ liệu khách hàng thật". | Người dùng có thể chỉnh sửa giá trị giả định mẫu trong bảng phụ. |
| **Activate Version** | Click "Kích hoạt" trên một bản nháp hợp lệ | WF-06 | `TemplateDetailView.jsx` | `POST /api/v1/notification-templates/:code/versions/:ver/activate` | - **Confirmation**: Modal hỏi "Kích hoạt phiên bản này sẽ thay thế phiên bản v{old} đang hoạt động cho các email gửi mới?".<br>- **Success**: Toast "Đã kích hoạt phiên bản v{new}". | Tự động cập nhật danh sách version mà không cần reload trang. |
| **Delivery Diagnostic List** | Navigation bar `Thông báo > Nhật ký gửi thư` (Admin, Manager) | WF-02, WF-03 | `NotificationRequestListPage.jsx`<br>`useNotificationRequests.js` | `GET /api/v1/notification-requests` | - **Filters**: Dropdown trạng thái (`PENDING`, `PROCESSING`, `RETRY_SCHEDULED`, `SENT`, `FAILED`, `UNKNOWN_OUTCOME`, `CANCELLED`), input mã đơn/booking, bộ lọc ngày.<br>- **Data Table**: Mã sự kiện, Mẫu thư, Người nhận đã che (`n***a@gmail.com`), Số lần gửi, Trạng thái (Pills màu sắc), Ngày tạo. | Auto-refresh toggle (30s) hoặc nút "Làm mới". |
| **Resend Action Modal** | Click "Gửi lại" trên dòng request trong danh sách hoặc drawer | WF-08 | `ResendConfirmationModal.jsx`<br>`useNotificationOperations.js` | `POST /api/v1/notification-requests/:id/resend` | - **Validation**: Ô nhập lý do gửi lại là bắt buộc (tối thiểu 10 ký tự).<br>- **Conditional UI**: Nếu state là `UNKNOWN_OUTCOME`: Manager bị disable nút; Admin thấy checkbox đỏ bắt buộc: "[ ] Tôi đã xác nhận với dịch vụ mail và chấp nhận rủi ro khách hàng có thể nhận thư trùng".<br>- **Success**: Toast "Yêu cầu gửi lại đã được xếp hàng". | Modal giữ form nếu API trả về 4xx/5xx. |
| **Cancel Action Modal** | Click "Hủy gửi" trên request đang `PENDING` hoặc `RETRY_SCHEDULED` | WF-09 | `CancelConfirmationModal.jsx`<br>`useNotificationOperations.js` | `POST /api/v1/notification-requests/:id/cancel` | - **Validation**: Ô nhập lý do hủy gửi là bắt buộc.<br>- **Disabled**: Disabled nếu request chuyển sang `PROCESSING` hoặc terminal state.<br>- **Success**: Toast "Đã hủy gửi yêu cầu thông báo". | Tải lại chi tiết request nếu bị từ chối do trạng thái đã đổi. |

---

## 3. Session, Data Handling, and Accessibility

### Session and Authorization Handling:
- Client attaches Firebase ID token in `Authorization: Bearer` header.
- Token expiration: Interceptor catches `401 Unauthorized` and invokes Firebase token refresh. If refresh fails, user is redirected to `/login` preserving target route.
- If response is `403 Forbidden`, user is presented with a standard "Truy cập bị từ chối: Yêu cầu quyền Quản trị viên hoặc Quản lý" screen.

### Data Masking & Client-Side Privacy:
- Recipient email address displayed on client is ALWAYS the masked version (`recipientAddressMasked`). Full raw email is not delivered to the frontend in list or detail views.
- Preview data uses synthetic fixture dictionaries only. Real customer phone numbers, passwords, card details, or token payloads never enter the frontend state.

### Accessibility Baseline:
- Colors for delivery state pills:
  - `SENT`: Green (`#16a34a`)
  - `PENDING` / `PROCESSING`: Blue (`#2563eb`)
  - `RETRY_SCHEDULED`: Amber (`#d97706`)
  - `FAILED`: Red (`#dc2626`)
  - `UNKNOWN_OUTCOME`: Purple / Warning (`#9333ea`)
  - `CANCELLED`: Slate Gray (`#475569`)
- All status badges include both color and textual label for screen-reader and color-blind accessibility.
- Modals trap focus and support `Esc` to close.

