---
scenario_id: E2E-004-005
feature: FE-004
actor: GUEST, MEMBER, STAFF, ADMIN
preconditions:
  - backend_health: PASS
  - frontend_health: PASS
  - database_migration: V182__ai_guest_sessions_and_prompt_quota.sql
traceability:
  - FE-004
  - AC-004-AI-QUOTA
isolation_key: e2e-004-quota-005
cleanup:
  - none
id: E2E-004-005
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-004-001
target_url: http://localhost:5173/ai-support
actor_role: Guest, Member, Staff, Admin
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  guest_quota: 3
  member_quota: 10
  timezone: Asia/Ho_Chi_Minh
  model: gemini-2.5-flash
---

# E2E-004-005: Kiểm thử Giới hạn Hạn ngạch (Quota) AI Chat hàng ngày theo Actor, Tinh chỉnh Trả lời Nghiệp vụ AI và Ẩn Điều hướng cho Staff/Admin

**Ngày thực hiện**: 2026-11-18  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tính năng**: `004-ai-chatbox-analysis-and-suggestion`  
**Trạng thái**: **PASS (100%)**

---

## 1. Mục tiêu kiểm thử (Test Objectives)

Xác minh toàn bộ các quy tắc hạn ngạch AI Chat theo đặc tả Feature 004 và contract:
1. **Khách vãng lai (Guest)**: Giới hạn tối đa 3 prompt/ngày lịch Việt Nam (`Asia/Ho_Chi_Minh`, reset lúc 00:00). Định danh thông qua token opaque trong header `X-Guest-AI-Session`.
2. **Hội viên đã đăng nhập (Member)**: Giới hạn tối đa 10 prompt/ngày lịch Việt Nam (`Asia/Ho_Chi_Minh`, reset lúc 00:00), gắn với active Firebase Member account UID.
3. **Nhân viên / Quản lý / Quản trị viên (Staff/Manager/Admin)**: Không thuộc đối tượng sử dụng Basic AI Support công khai; hệ thống trả mã lỗi `403 AI_ACTOR_NOT_ALLOWED` mà không làm vỡ phiên hoặc logout tài khoản.
4. **Xử lý cạn kiệt Quota**: Khi hết quota, backend trả HTTP `429 AI_DAILY_QUOTA_EXCEEDED` kèm header `Retry-After: <số giây tới 00:00>`. Frontend khóa ô nhập câu hỏi, vô hiệu hóa nút gửi, hiển thị thông báo cảnh báo màu vàng và mốc reset lúc 00:00.
5. **Điều hướng phân quyền (Role Navigation Cleanup)**: Loại bỏ các liên kết đến trang Cấu hình nhẫn (`/workshops/PKG-COUPLE-SILVER/design`), Custom (`/design-requests/new`), và Trợ lý AI (`/ai-support`) đối với các vai trò nội bộ `STAFF` và `ADMIN`.
6. **Độ chính xác và tính đầy đủ của Chatbot AI**: Khắc phục lỗi trả lời lặp cụt ngủn hoặc sai lệch thông tin của trợ lý AI.

---

## 2. Tiền điều kiện & Môi trường (Preconditions & Environment)

- [x] Docker Container `ppswbs-mysql` (cổng 3306) đang chạy khỏe mạnh.
- [x] Migration Flyway `V182__ai_guest_sessions_and_prompt_quota.sql` đã áp dụng thành công (tạo bảng `ai_guest_sessions` và `ai_prompt_quota_usage`).
- [x] Backend Spring Boot chạy trên cổng 8080 với cấu hình timezone `Asia/Ho_Chi_Minh`, quota limits (guest=3, member=10), model `gemini-2.5-flash`.
- [x] Frontend Vite chạy trên cổng 5173.
- [x] Trình duyệt tự động hoá thông qua MCP tool `browsermcp`.

---

## 3. Các kịch bản kiểm thử chi tiết & Bằng chứng thực tế

### Kịch bản 1: Hội viên (Member) sử dụng Trợ lý AI & Trả lời đầy đủ từ Gemini 2.5 Flash
- **Tài khoản**: `member.test@mland.vn` (Role: `MEMBER`).
- **Thực hiện**:
  1. Đăng nhập với tài khoản Member, truy cập `http://localhost:5173/ai-support`.
  2. Gửi prompt: `"Mland có workshop làm nhẫn đôi không? Giá bao nhiêu và thời lượng thế nào?"`.
  3. Chatbot gọi Google Gemini 2.5 Flash với context nghiệp vụ Mland được nạp sẵn.
  4. Phản hồi AI trả về cực kỳ đầy đủ, chuẩn xác, bao gồm:
     - Tên gói: Gói Workshop Nhẫn đôi bạc 925 cơ bản (Couple Silver).
     - Trải nghiệm: Tự tay cán bạc, uốn, hàn, dũa, đánh bóng và khắc laser tên/ngày kỷ niệm.
     - Mức giá: Từ 490.000 VNĐ/người.
     - Thời lượng: 1 buổi (2.5 - 3 tiếng).
     - Đặc biệt: Nghệ nhân hướng dẫn 1-kèm-1, cam kết 100% tự tay làm ra sản phẩm.
     - Quy trình đặt lịch: Đặt trên website và đặt cọc 50% qua VNPay (link giữ chỗ 10 phút), đổi ca miễn phí trước 24 giờ.
- **Bằng chứng ảnh chụp màn hình**:
  ![Live Gemini Full Answer](screenshots/e2e-004-live-gemini-full-answer.jpg)

---

### Kịch bản 2: Khách vãng lai (Guest) sử dụng Trợ lý AI và Cạn kiệt Quota (3/3)
- **Actor**: Guest (Chưa đăng nhập / Đã đăng xuất).
- **Thực hiện**:
  1. Truy cập `http://localhost:5173/ai-support`.
  2. Khởi tạo: Badge "Khách (Công khai)" và quota `"Còn 3/3 lượt hỏi hôm nay • Reset lúc 00:00"`.
  3. Gửi lần lượt 3 prompt: Quota giảm 2/3 -> 1/3 -> 0/3.
  4. Tại 0/3: Hệ thống kích hoạt banner cảnh báo: `"Bạn đã sử dụng hết số lượt hỏi AI trong ngày. Lượt hỏi sẽ được làm mới vào lúc 00:00."`, textarea và nút "Gửi câu hỏi" bị vô hiệu hóa (`disabled`).
- **Bằng chứng ảnh chụp màn hình**:
  - Trạng thái khởi tạo: ![Guest Initial Quota](screenshots/e2e-004-guest-initial-quota.jpg)
  - Trạng thái cạn kiệt: ![Guest Quota Exhausted](screenshots/e2e-004-guest-quota-exhausted.jpg)

---

### Kịch bản 3: Ẩn các liên kết Cấu hình nhẫn, Custom, Trợ lý AI cho Role STAFF
- **Tài khoản**: `staff.reviewer@mland.vn` (Role: `STAFF`).
- **Thực hiện**:
  1. Đăng nhập với quyền Staff, truy cập trang chủ `http://localhost:5173`.
  2. Thanh điều hướng Topbar (`MainTopbar`):
     - Chỉ hiển thị: *Workshop*, *Ready Rings*, *Kho Voucher*, *Tra cứu*, *Đánh giá*.
     - Hoàn toàn **KHÔNG CÓ** các nút: *Cấu hình nhẫn*, *Custom*, *Trợ lý AI*.
  3. Khối Tiện ích hệ sinh thái (`ECOSYSTEM_SERVICES`):
     - Hoàn toàn **ẨN** 2 ô: *🎨 Chế tác theo yêu cầu* và *🤖 Trợ lý AI tư vấn*.
- **Bằng chứng ảnh chụp màn hình**:
  ![Staff Hidden Links](screenshots/e2e-004-staff-hidden-links.jpg)

---

### Kịch bản 4: Ẩn các liên kết Cấu hình nhẫn, Custom, Trợ lý AI cho Role ADMIN
- **Tài khoản**: `admin.audit@mland.vn` (Role: `ADMIN`).
- **Thực hiện**:
  1. Đăng nhập với quyền Admin, truy cập trang chủ `http://localhost:5173`.
  2. Thanh điều hướng Topbar (`MainTopbar`):
     - Chỉ hiển thị các mục công khai phù hợp, đã loại bỏ toàn bộ: *Cấu hình nhẫn*, *Custom*, *Trợ lý AI*.
- **Bằng chứng ảnh chụp màn hình**:
  ![Admin Hidden Links](screenshots/e2e-004-admin-hidden-links.jpg)

---

## 4. Phân tích Nguyên nhân Chatbot trả lời chưa đúng & Giải pháp khắc phục

### 4.1 Nguyên nhân gốc rễ (Root Causes)
1. **Lệch Model Identifier (HTTP 404 NOT_FOUND)**:
   - Cấu hình cũ đặt `gemini.model=gemini-1.5-flash`.
   - Trên endpoint v1beta hiện tại của Google, model `gemini-1.5-flash` không còn được hỗ trợ và trả mã lỗi `404 NOT_FOUND` (yêu cầu chuyển sang các model hiện hành như `gemini-2.5-flash` hoặc `gemini-flash-latest`).
2. **Kẹt vòng lặp Fallback 1 câu tĩnh**:
   - Khi API gọi ngoài gặp lỗi 404, adapter rơi vào khối `catch` và gán lại câu trả lời mặc định là một câu cứng nhắc duy nhất (VD: *"Mland tổ chức các buổi workshop chế tác nhẫn bạc cơ bản và điêu khắc sáp phôi hàng tuần. Đặt chỗ yêu cầu đặt cọc 50% qua cổng VNPay."*). Mọi câu hỏi về workshop đều nhận chung 1 câu trả lời cụt ngủn này.
3. **Thiếu System Context về dữ liệu đặc thù Mland**:
   - Mô hình nền tảng Gemini không có dữ liệu ngữ cảnh nội bộ của xưởng Mland (địa chỉ Tràng Tiền / Nguyễn Đình Chiểu, giá 490k / 890k, thời lượng 2.5-3 tiếng, bảo hành đánh bóng trọn đời, v.v.). Khi không được nạp prompt nghiệp vụ, mô hình tự nhận là trợ lý Google và từ chối trả lời về Mland.
4. **Phân tích cú pháp JSON bằng Regex mong manh**:
   - Biểu thức regex cũ dễ bị đứt đoạn hoặc bỏ sót khi phản hồi trả về có dấu xuống dòng, ngoặc kép thoát ký tự hoặc khối suy luận.

### 4.2 Giải pháp đã thực hiện (Solutions Applied)
1. **Nâng cấp Model**:
   - Cập nhật `gemini.model=gemini-2.5-flash` trong `application.properties` và `GeminiRestClientAdapter.java`.
2. **Nạp Hệ tri thức Nghiệp vụ chi tiết (Domain System Instruction)**:
   - Bổ sung toàn bộ tài liệu về: Gói Couple Silver (490k, 1 buổi), Gói Wax Carving (890k, 2 buổi), quy định cọc VNPay 50%, đổi ca trước 24h, bảo hành trọn đời bạc 925, hệ thống showroom tại HN & HCM, hotline 1900 6868.
3. **Bộ Parser Jackson ObjectMapper chuẩn**:
   - Sử dụng Jackson `ObjectMapper` duyệt qua các node `candidates[0].content.parts[*].text`, xử lý chuẩn xác 100% định dạng unicode tiếng Việt, markdown bullet point và văn bản nhiều dòng.
4. **Làm giàu Fallback Offline**:
   - Nâng cấp các kịch bản dự phòng khi mất kết nối mạng để luôn cung cấp thông tin đa chiều, cụ thể và chuyên nghiệp.

---

## 5. Kết quả kiểm thử tự động (Automated Test Execution)

### 5.1 Backend Integration Tests
```cmd
mvnw.cmd test "-Dtest=AiSupportIT"
```
- Kết quả: **13/13 tests PASS** (100%).
- Thời gian thực thi: 64.44s.

### 5.2 Frontend Vitest Suites
```cmd
npx vitest run src/features/landing src/features/designreview src/features/reviewmanagement
```
- `src/features/landing/HomePage.test.jsx`: **17/17 tests PASS**
- `src/features/designreview`: **26/26 tests PASS**
- `src/features/reviewmanagement`: **42/42 tests PASS**
- **Tổng cộng**: **85/85 tests PASS** (100%).
