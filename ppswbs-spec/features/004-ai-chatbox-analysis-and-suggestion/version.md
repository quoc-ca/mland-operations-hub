# Version 1.1 - Feature 004: AI Chatbox Analysis and Suggestion

- **Feature**: 004 - AI Chatbox Analysis and Suggestion
- **Version**: 1.0
- **Author**: Ngô Tiến Đạt
- **Edited by**: Ngô Tiến Đạt
- **Created on**: 2026-10-24
- **Last updated**: 2026-10-25

---

## 1. Accomplished (Những phần code đã hoàn thành)

Toàn bộ phần lõi **Backend** của tính năng đã được thiết kế, lập trình và kiểm thử tự động đạt 100% tỷ lệ pass:

### 1.1. Cơ sở dữ liệu & Entity Design (Flyway V4 & JPA)
- Đã tạo migration [`V4__ai_chatbox_and_design_review.sql`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/resources/db/migration/V4__ai_chatbox_and_design_review.sql) và tài liệu thiết kế chi tiết tại [`entities.md`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/entities.md).
- Triển khai 7 bảng cơ sở dữ liệu độc lập với đầy đủ constraints, index và foreign keys:
  1. `ai_text_interactions`: Lưu vết hỏi đáp AI Chatbox, phân loại scope (`PRODUCT`, `WORKSHOP`, `STORE`, `FAQ`, `OUT_OF_SCOPE`), kiểm tra prompt injection, quản lý retention expiration (30 ngày theo spec.md).
  2. `booking_design_requests`: Quản lý yêu cầu phân tích thiết kế đính kèm booking workshop, versioning thiết kế (`design_version`), quản lý idempotency key.
  3. `image_consents`: Lưu trữ xác nhận đồng thuận tường minh của Member đối với điều khoản phân tích hình ảnh (`v1.0.0`).
  4. `temporary_image_references`: Quản lý vòng đời ảnh đính kèm tạm thời.
  5. `design_analysis_results`: Lưu trữ đặc trưng hình học, chất lượng ảnh, độ tin cậy và định mức ước tính do AI trích xuất.
  6. `human_review_decisions`: Lưu quyết định duyệt/từ chối/yêu cầu làm rõ của Staff/Manager kèm lý do kiểm duyệt.
  7. `price_suggestions`: Lưu đề xuất định giá advisory do AI sinh ra dành riêng cho quản lý, lưu quyết định của Manager (`ACCEPTED`, `ADJUSTED`, `REJECTED`, `MANUAL_FALLBACK`) và giá cuối cùng (`final_price`).

### 1.2. Kiến trúc Clean Architecture / Ports & Adapters
- **Domain Ports**:
  - [`AiTextSupportPort`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/designreview/domain/AiTextSupportPort.java): Giao tiếp text hỏi đáp.
  - [`ImageFeatureAnalysisPort`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/designreview/domain/ImageFeatureAnalysisPort.java): Giao tiếp vision analysis.
  - [`AiPriceSuggestionPort`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/designreview/domain/AiPriceSuggestionPort.java): Giao tiếp advisory pricing.
  - [`ReferenceImageStoragePort`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/designreview/domain/ReferenceImageStoragePort.java): Lưu trữ tạm và dọn dẹp file tham chiếu.
- **Adapters**:
  - [`GeminiRestClientAdapter`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/designreview/infrastructure/GeminiRestClientAdapter.java): Adapter đa chế độ (tự động chuyển đổi giữa Google Gemini API và Mock Adapter thông minh khi chạy test hoặc khi cấu hình dev).
  - [`LocalStorageAdapter`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/designreview/infrastructure/LocalStorageAdapter.java): Lưu trữ tạm trên ổ đĩa nội bộ và xóa ngay lập tức sau phân tích.

### 1.3. Nghiệp vụ Ứng dụng (Application Services)
- [`ImageRateLimiter`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/designreview/application/ImageRateLimiter.java): Rate limiter dạng trượt thread-safe (3 lần/5 phút/Member), trả mã lỗi `429 IMAGE_RATE_LIMITED` kèm header `Retry-After: 300`, miễn trừ tiêu hao rate limit đối với yêu cầu gửi lại cùng `Idempotency-Key`.
- [`AiSupportService`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/designreview/application/AiSupportService.java): Lọc prompt injection độc hại, kiểm tra giới hạn độ dài văn bản (<= 2000 ký tự), gắn correlation id, thiết lập thời hạn lưu vết chính xác 30 ngày.
- [`DataRetentionCleanupJob`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/designreview/application/DataRetentionCleanupJob.java): Định kỳ hàng ngày quét và xóa các bản ghi text interaction quá hạn 30 ngày (`expires_at <= now`) và dọn dẹp các tệp ảnh tạm thời còn sót lại theo chính sách Zero-retention.
- [`DesignAnalysisService`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/designreview/application/DesignAnalysisService.java):
  - Xác thực tư cách hội viên active và trạng thái workshop booking hợp lệ qua [`WorkshopBookingFacade`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/facade/WorkshopBookingFacade.java).
  - Xác thực consent điều khoản phân tích hình ảnh.
  - Validate định dạng ảnh (JPEG, PNG, WEBP), dung lượng (<= 10MB), kích thước tối đa (<= 4096×4096 px).
  - Đảm bảo cơ chế xóa file tạm thời ngay lập tức trong khối `finally` sau khi AI phân tích xong (Zero-retention policy).
- [`HumanReviewService`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/designreview/application/HumanReviewService.java):
  - Phân quyền hàng đợi kiểm duyệt theo vai trò: Staff chỉ được duyệt các yêu cầu có giá ước tính <= 3.000.000 VND; Manager/Owner duyệt các yêu cầu > 3.000.000 VND.
  - Chống stale version: Chặn cập nhật quyết định kiểm duyệt nếu `design_version` không trùng khớp với phiên bản mới nhất.
  - Ngăn chặn chỉnh sửa khi bản ghi đã rơi vào terminal state.
- [`PriceSuggestionService`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/designreview/application/PriceSuggestionService.java):
  - Chỉ cho phép Quản lý (`ROLE_OWNER`, `ROLE_MANAGER`) yêu cầu và đưa ra quyết định định giá (`ACCEPTED`, `ADJUSTED`, `REJECTED`, `MANUAL_FALLBACK`).
  - Phân quyền xem dữ liệu đề xuất giá chuẩn theo `spec.md`: Manager xem toàn bộ chi tiết (giá gợi ý, lý giải, quyết định, giá chốt); Admin (`ROLE_ADMIN_TECHNICAL`) chỉ xem technical/audit evidence (không thấy giá và lý giải kinh doanh); Staff, Guest và Member bị chặn hoàn toàn (403/401).

### 1.4. Web REST Controllers & Exception Handling
- [`AiSupportController`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/designreview/web/AiSupportController.java): `POST /api/v1/ai-support/messages`.
- [`DesignRequestController`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/designreview/web/DesignRequestController.java): `POST /api/v1/design-requests` (Multipart form-data) & `GET /api/v1/design-requests/{designRequestId}`.
- [`DesignReviewController`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/designreview/web/DesignReviewController.java): `GET /api/v1/design-review-requests` & `POST /api/v1/design-review-requests/{reviewRequestId}/decisions`.
- [`PriceProposalController`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/designreview/web/PriceProposalController.java):
  - `POST /api/v1/price-proposals/{proposalId}/ai-suggestions` (Manager request)
  - `GET /api/v1/price-proposals/{proposalId}/ai-suggestions` (Manager xem toàn bộ, Admin xem technical/audit evidence)
  - `POST /api/v1/price-proposals/{proposalId}/ai-suggestions/decisions` (Manager quyết định chốt giá)
- [`DesignReviewExceptionHandler`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/designreview/web/DesignReviewExceptionHandler.java): Chuẩn hóa mã lỗi và header `Retry-After` đồng bộ theo contract OpenAPI.

### 1.5. Phân quyền & Bảo mật (Security & Modulith Boundaries)
- Cập nhật [`SecurityConfiguration`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/configuration/SecurityConfiguration.java) và [`FirebaseAuthenticationFilter`](file:///c:/Users/admin/Desktop/SEP/mland-operations-hub/ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/configuration/FirebaseAuthenticationFilter.java): Hỗ trợ trích xuất role từ token Firebase custom claim và fallback header `X-User-Role`.
- Tuân thủ nghiêm ngặt ranh giới Spring Modulith: module `designreview` chỉ giao tiếp qua facades, không import domain module khác.
- Kiểm thử tích hợp: **53/53 tests toàn bộ hệ thống pass 100%** (bao gồm 24 tests của `designreview`, `DataRetentionIT`, `PriceProposalIT` và `ModulithArchitectureTests`).

---

## 2. To do (Những công việc cần hoàn thiện & Điểm cần làm rõ)

### 2.1. Công việc cần thực hiện tiếp theo (Code Tasks)
1. **Triển khai Giao diện Frontend (Task T012)**:
   - Xây dựng component Widget AI Chatbox hỗ trợ hỏi đáp nhanh cho khách vãng lai và hội viên.
   - Xây dựng form upload ảnh thiết kế mẫu kèm checkbox chấp thuận điều khoản phân tích hình ảnh tại trang chi tiết Workshop Booking.
   - Xây dựng giao diện Hàng đợi kiểm duyệt (Review Queue) cho Staff và Manager: hiển thị ảnh trích xuất, thông số AI dự đoán, hạn mức kiểm duyệt, form gửi quyết định duyệt/từ chối/yêu cầu làm rõ.
   - Xây dựng modal xem và áp dụng đề xuất giá AI cho Quản lý xưởng khi thiết lập giá gói workshop.
2. **Cấu hình Cloud Object Storage Adapter (Production)**:
   - Bổ sung `S3StorageAdapter` hoặc `FirebaseStorageAdapter` để lưu trữ ảnh tạm thời trên cloud nếu môi trường production triển khai dạng containerized đa instance (thay vì local filesystem).

### 2.2. Những điểm cần làm rõ với các bên liên quan (Clarifications Needed)
1. **Mở rộng hỗ trợ Target Type trong Price Suggestion**:
   - Hiện tại logic phân giá advisory chỉ áp dụng cho `WORKSHOP_PACKAGE` (trả về lỗi `422 AI_FEATURE_NOT_APPLICABLE` nếu truyền `CUSTOM_MANUFACTURING_ORDER`). Cần làm rõ với đội ngũ phụ trách Feature 008 (Custom Manufacturing) về cấu trúc schema tham số đầu vào và định mức giá khi mở rộng cho đơn đặt hàng gia công theo yêu cầu.
2. **Cơ chế Thông báo Trạng thái (Realtime Notifications)**:
   - Cần làm rõ phương thức gửi thông báo cho Member khi Staff/Manager đưa ra quyết định kiểm duyệt (Accepted / Rejected / Clarification Required): Có cần tích hợp WebSocket / SSE hay Notification Service (email, push notification) hay chỉ cần polling API `GET /api/v1/design-requests/{id}`?
3. **Môi trường & Chi phí Quản lý Token Google Gemini**:
   - Xác định model production (ví dụ `gemini-1.5-flash` hay `gemini-1.5-pro`), giới hạn rate limit của API Key cấp phát từ tổ chức để có cơ chế backoff / circuit breaker phù hợp trên production.

---

## 3. Plan (Gợi ý kế hoạch tiếp theo để hoàn thiện)

### Giai đoạn 1: Triển khai Giao diện Khách hàng & Nội bộ (Frontend - React + Vite)
- **Bước 1.1**: Tích hợp màn hình Chatbox AI vào website công khai, kết nối endpoint `POST /api/v1/ai-support/messages`.
- **Bước 1.2**: Tích hợp quy trình tải ảnh mẫu cho Member tại trang quản lý Workshop Booking, hiển thị trạng thái `PROCESSING` -> `AWAITING_HUMAN_REVIEW` -> `ACCEPTED`/`REJECTED`.
- **Bước 1.3**: Tích hợp trang Dashboard Review cho Staff/Manager với bộ lọc trạng thái và phân quyền theo hạn mức 3.000.000 VND.
- **Bước 1.4**: Tích hợp chức năng AI Suggestion vào màn hình thiết lập giá Workshop Package dành cho Manager (xem và quyết định chốt giá).

### Giai đoạn 2: Tích hợp Dịch vụ Gemini Thực tế & Tinh chỉnh Prompt (Prompt Engineering)
- **Bước 2.1**: Cung cấp `GEMINI_API_KEY` thực tế vào biến môi trường production/staging.
- **Bước 2.2**: Kiểm thử độ chính xác của Gemini với bộ dữ liệu ảnh nhẫn thực tế (ảnh mờ, ảnh chụp tay đeo nhẫn, ảnh phác thảo chì, ảnh 3D CAD).
- **Bước 2.3**: Hoàn thiện bộ rules phát hiện prompt injection trong tiếng Việt để nâng cao độ an toàn cho hệ thống.

### Giai đoạn 3: Hoàn thiện Vận hành & Tích hợp Thông báo
- **Bước 3.1**: Vận hành `DataRetentionCleanupJob` chạy định kỳ vào 02:00 sáng hàng ngày để dọn dẹp các tương tác text quá 30 ngày và tệp ảnh tạm thời.
- **Bước 3.2**: Kích hoạt sự kiện domain event khi Staff/Manager review để gửi email thông báo tự động cho khách hàng.

### Giai đoạn 4: Kiểm thử E2E và Đóng gói (Testing & Deployment)
- **Bước 4.1**: Viết Cypress / Playwright E2E tests bao quát luồng từ Chatbox -> Đặt booking -> Gửi ảnh -> Nhân viên duyệt -> Xem kết quả.
- **Bước 4.2**: Bàn giao tài liệu hướng dẫn vận hành cho nhân viên xưởng và quản lý.
