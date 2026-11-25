---
id: E2E-CHAOS-002
format_version: 2
feature: 013-design-review-and-custom-order
journey_phase: 2
test_level: master_e2e
test_case_type: Adversarial-and-Boundary-Custom-Design-Flow
source_ref: 013-spec.md; spec-general.md#§2,§3
target_url: https://mland-operations-hub-git-develop-mland-team.vercel.app/design-requests/new
health_urls:
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/design-requests/new
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/design-review-requests
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/account/my-custom-orders
actor_primary: MEMBER (minikie456@gmail.com)
actor_secondary: MANAGER (manager.test@mland.vn)
browser_tool: browsermcp
environment: deployed-vercel
test_data:
  isolation_key: "e2e_chaos_custom_design_002"
  member_account:
    email: minikie456@gmail.com
    password: "123123123"
  manager_account:
    email: manager.test@mland.vn
    password: ManagerTest@123456
  invalid_file_payload:
    name: "malicious_script.txt"
    content: "ALERT_XSS_MALICIOUS_MOCK_DATA"
  adversarial_description: "☼◙\tYêu cầu đúc phôi vàng tây nguyên khối, mặt khắc chìm biểu tượng bát quái và đính 8 viên đá phong thủy!☼◙"
cleanup:
  required: true
  method: clean-session-logout
---

# E2E-CHAOS-002: Tấn Công File Upload Biên, Khóa Cuộn Điều Khoản & Manager Duyệt Báo Giá Chế Tác Riêng

**Môi trường mục tiêu**: `https://mland-operations-hub-git-develop-mland-team.vercel.app/`  
**Phiên bản**: 2.0 | **Cập nhật**: 2026-11-25  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Quy chuẩn xưng hô bắt buộc**: Người tham gia trải nghiệm xưởng là **Khách hàng (Customer)**, tuyệt đối không gọi là "học viên".  
**Chiến lược bằng chứng**: Tuân thủ nguyên tắc tinh gọn — **Không chụp ảnh màn hình ở các bước thành công (Happy Path)**. Chỉ chụp ảnh màn hình khi phát hiện lỗi bất thường (`WARN-...jpg` khi SOFT FAIL hoặc `BUG-...jpg` khi HARD STOP).

---

## 1. Mục tiêu Kịch bản (Scenario Objective)

Kiểm chứng khả năng bảo vệ dữ liệu đầu vào và chu trình phê duyệt gia công riêng (`/design-requests/new` ➔ `/design-review-requests`):
1. **Tấn công file upload biên (Negative File Upload Testing)**: Cố tình upload file không đúng định dạng cho phép (file văn bản `.txt` thay vì ảnh PNG/JPG/WEBP), kiểm tra hệ thống từ chối với thông báo lỗi validation rõ ràng.
2. **Kiểm tra rào chắn Điều khoản (Terms & Conditions Scroll-Gate)**: Xác minh modal điều khoản bắt buộc người dùng phải cuộn chuột đến tận đáy (Scroll-to-bottom) trước khi kích hoạt nút "Đồng ý điều khoản".
3. **Chèn payload dị vào mô tả yêu cầu**: Nhập chuỗi ký tự điều khiển `"☼◙\t"` kèm yêu cầu chế tác độc bản, đính kèm mẫu nhẫn đã thẩm định.
4. **Phê duyệt & Định giá phía Quản lý**: Quản lý đăng nhập tại Microsoft Edge (`b2`), tiếp nhận yêu cầu, phân tích mô tả và nhập báo giá gia công sơ bộ, xác nhận Phê duyệt (`APPROVED`).
5. **Xác nhận phía Khách hàng**: Khách hàng kiểm tra tại `/account/my-custom-orders`, nhận được báo giá và phản hồi của xưởng.

---

## 2. Tiền điều kiện (Preconditions)

1. Môi trường deploy Vercel hoạt động ổn định.
2. Khách hàng `minikie456@gmail.com` đã đăng nhập tại Chrome (`b1`).
3. Quản lý `manager.test@mland.vn` đã đăng nhập tại Edge (`b2`).
4. Có sẵn mẫu thiết kế nhẫn đã lưu để liên kết (hoặc tạo mẫu độc bản mới).

---

## 3. Các bước Thực thi Kiểm thử (Step Contracts)

### Bước 1 (C2-S01): Mở Form Yêu Cầu Chế Tác & Tấn Công File Upload Biên

- **Explore & Act**:
  1. Khách hàng điều hướng đến `/design-requests/new`.
  2. Tạo một file giả định `test_payload.txt` (nội dung text).
  3. Thử upload file này vào ô "Tải ảnh bản vẽ mẫu".
- **Observable Expected**:
  - Hệ thống lập tức từ chối file, hiển thị thông báo lỗi màu đỏ (VD: *"Chỉ chấp nhận các định dạng ảnh PNG, JPG, JPEG, WEBP"*).
  - Không cho phép submit form khi file không hợp lệ.

---

### Bước 2 (C2-S02): Nhập Payload Mô Tả Dị & Vượt Rào Cản Cuộn Điều Khoản

- **Explore & Act**:
  1. Upload 1 file ảnh hợp lệ (hoặc chọn mẫu nhẫn đã lưu trong danh sách dropdown).
  2. Tại ô Mô tả ý tưởng, nhập: `"☼◙\tYêu cầu đúc phôi vàng tây nguyên khối, mặt khắc chìm biểu tượng bát quái và đính 8 viên đá phong thủy!☼◙"`.
  3. Bấm vào liên kết "Xem điều khoản và chính sách chế tác".
  4. Modal điều khoản hiện lên. Thử bấm "Đồng ý" khi chưa cuộn ➔ Nút phải bị disabled.
  5. Cuộn thanh cuộn của modal xuống đáy 100% ➔ Nút "Đồng ý" được kích hoạt. Bấm xác nhận.
  6. Bấm nút "Gửi yêu cầu chế tác".
- **Observable Expected**:
  - Giao diện báo gửi yêu cầu thành công, sinh mã yêu cầu `REQ-...` (hoặc chuyển hướng sang màn hình theo dõi đơn gia công).

---

### Bước 3 (C2-S03): Quản lý Tiếp Nhận Yêu Cầu & Gửi Báo Giá Phê Duyệt

- **Explore & Act**:
  1. Tại Edge (`b2`), Quản lý truy cập `/design-review-requests`.
  2. Tìm yêu cầu mới của khách hàng `minikie456@gmail.com` chứa payload `"☼◙\t"`.
  3. Bấm mở chi tiết yêu cầu.
  4. Kiểm tra hiển thị chuỗi ký tự unicode an toàn, không bị XSS hay vỡ giao diện.
  5. Bấm "Tiếp nhận thẩm định" (Claim).
  6. Nhập đơn giá ước tính (VD: `3.500.000 ₫`) và ghi chú tư vấn: *"Đã thẩm định mẫu bát quái, xưởng có thể gia công đúc sáp mất 5 ngày"*.
  7. Bấm nút "Phê duyệt yêu cầu" (Approve).
- **Observable Expected**:
  - Trạng thái yêu cầu chuyển thành `APPROVED` (hoặc `QUOTED`).
  - Ghi nhận lịch sử duyệt thành công.

---

### Bước 4 (C2-S04): Khách Hàng Xác Nhận Trạng Thái Đã Duyệt & Báo Giá

- **Explore & Act**:
  1. Quay lại Chrome (`b1`), truy cập `/account/my-custom-orders`.
  2. Kiểm tra chi tiết đơn yêu cầu.
- **Observable Expected**:
  - Hiển thị đúng trạng thái Đã duyệt / Báo giá kèm mức giá `3.500.000 ₫`.

---

## 4. Lịch sử Thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASSED (100%)
- Executed at: 2026-11-25T20:24:00+07:00
- Executed by: Senior QA & System Verification Engineer
- Target URL: https://mland-operations-hub-git-develop-mland-team.vercel.app/
- Request Reference Code: `DR-85F88045` (ID: `85f88045-639c-4d3c-b1f9-8dbb32247d56`)
- Associated Ring Design: `Mẫu #FB19AC` (Dáng phẳng công thái học, Size 20)
- Quoted Amount: `3.500.000 ₫` (Ước tính gia công đúc vàng tây nguyên khối bát quái)
- Key Chaos Findings:
  1. **Negative File Upload Gate**: Tải file `.txt` và file ảnh vượt dung lượng 11MB (`oversized-sample.jpg`) bị hệ thống chặn tiếp nhận, không cho phép gửi request lỗi lên server.
  2. **Terms Scroll-to-Bottom Enforcement**: Modal điều khoản bản quyền chế tác khóa cứng nút "Chấp nhận" ở trạng thái `disabled`; chỉ kích hoạt sau khi sự kiện cuộn đạt đáy 100% (`scrollTop >= scrollHeight - 20`).
  3. **Adversarial Payload Resilience**: Chuỗi Unicode điều khiển `"☼◙\tYêu cầu đúc phôi vàng tây nguyên khối, mặt khắc chìm biểu tượng bát quái và đính 8 viên đá phong thủy!☼◙"` được lưu trữ toàn vẹn, hiển thị chuẩn xác ở cả giao diện Member và bàn làm việc Thẩm duyệt của Manager.
  4. **Multi-Browser Dual-Role Collaboration**: Quản lý tại Microsoft Edge duyệt yêu cầu thành công (`ACCEPTED`) kèm báo giá `3.500.000 ₫`. Khách hàng tại Google Chrome nhận kết quả tức thì với badge `ĐÃ CHẤP NHẬN THIẾT KẾ`.
<!-- EXECUTION:END -->
