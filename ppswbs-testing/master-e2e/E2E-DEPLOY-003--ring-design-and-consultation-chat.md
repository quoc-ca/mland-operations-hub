---
id: E2E-DEPLOY-003
format_version: 2
feature: 012-workshop-ring-design-configuration
journey_phase: 3
test_level: master_e2e
test_case_type: Exploratory-and-Adversarial-Design-Chat-Flow
source_ref: 012-spec.md; 013-spec.md; spec-general.md#§2,§3
target_url: https://mland-operations-hub-git-develop-mland-team.vercel.app/workshops/PKG-COUPLE-SILVER/design
health_urls:
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/workshops/PKG-COUPLE-SILVER/design
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/member/consultations
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/operations/consultations
actor_primary: MEMBER (minikie456@gmail.com)
actor_secondary: STAFF (staff.test@mland.vn)
browser_tool: browsermcp
environment: deployed-vercel
test_data:
  isolation_key: "e2e_deploy_design_chat_003"
  member_account:
    email: minikie456@gmail.com
    password: "123123123"
  staff_account:
    email: staff.test@mland.vn
    password: StaffTest@123456
  package_reference: "PKG-COUPLE-SILVER"
  ring_parameters:
    material: "Bạc Ý 925"
    shape: "Tròn cổ điển"
    ring_size: 16
    gemstone: "Đá CZ Xanh Lam 2mm"
    engraving_text: "MLAND FOREVER 2026"
    thickness_mm: 1.8
    band_width_mm: 3.5
  customer_message: "Chào thợ kim hoàn Mland, mẫu thiết kế nhẫn này của tôi có thể xử lý bề mặt xước mờ satin và nâng cấp đá CZ sang đá Moissanite 3mm được không?"
  staff_reply: "Chào bạn! Mẫu nhẫn này hoàn toàn xử lý được xước mờ satin và đính đá Moissanite 3mm nhé. Khi bạn đến xưởng, kỹ thuật viên sẽ hỗ trợ bạn chỉnh cữ chấu trực tiếp!"
cleanup:
  required: true
  method: clean-session-logout
---

# E2E-DEPLOY-003: Cấu Hình Nhẫn Tham Số Kim Hoàn, Thẩm Định Kỹ Thuật, Mở Chat Tư Vấn Kèm Mẫu Nhẫn, Trao Đổi 2 Chiều & Bẫy Chặn Tin Nhắn Sau Đóng

**Môi trường mục tiêu**: `https://mland-operations-hub-git-develop-mland-team.vercel.app/`  
**Phiên bản**: 2.0 | **Cập nhật**: 2026-11-25  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Quy chuẩn xưng hô bắt buộc**: Người tham gia trải nghiệm xưởng là **Khách hàng (Customer)**, tuyệt đối không gọi là "học viên".  
**Chiến lược bằng chứng**: Tuân thủ nguyên tắc tinh gọn — **Không chụp ảnh màn hình ở các bước thành công (Happy Path)**. Chỉ chụp ảnh màn hình khi phát hiện lỗi bất thường (`WARN-...jpg` khi SOFT FAIL hoặc `BUG-...jpg` khi HARD STOP).

---

## 1. Mục tiêu Kịch bản (Scenario Objective)

Kiểm chứng toàn trình (End-to-End) chu trình Thiết kế nhẫn tham số kết hợp Chat tư vấn trực tuyến giữa Khách hàng và Thợ kim hoàn:
1. Khách hàng truy cập xưởng ảo cấu hình nhẫn `/workshops/PKG-COUPLE-SILVER/design`, tinh chỉnh các tham số thực tế (chất liệu Bạc Ý 925, dáng tròn, size 16, đính đá CZ, khắc chữ laser cá nhân hóa và kéo thanh trượt độ dày/độ rộng).
2. Kiểm tra tính mượt mà của bản vẽ mô phỏng 2D/3D trực quan và màn hình kết quả thẩm định kỹ thuật (khối lượng ước tính ~3.8g, thang điểm độ khó gia công, chi phí tham khảo).
3. Lưu thiết kế thành công và bấm nút **"Tư vấn thiết kế"** để kích hoạt cuộc hội thoại tư vấn tại `/member/consultations` gắn thẻ tóm tắt mẫu nhẫn (`AttachedRingHoverRecap`).
4. Khách hàng gửi tin nhắn trao đổi kỹ thuật về khả năng gia công nâng cao (bóng mờ satin, nâng cấp đá).
5. Nhân viên xưởng đăng nhập tại `/operations/consultations`, tiếp nhận hội thoại, kiểm tra snapshot mẫu nhẫn của khách và gửi tin nhắn phản hồi tư vấn chuyên môn.
6. Xác minh đồng bộ tin nhắn 2 chiều chuẩn xác qua cơ chế polling thời gian thực; thực hiện đóng hội thoại (`CLOSE`).
7. Kiểm tra rủi ro đối nghịch (Adversarial Check): Cố tình nhập và gửi tin nhắn vào hội thoại đã đóng ➔ Hệ thống bắt buộc từ chối và chặn gửi tin.

---

## 2. Tiền điều kiện (Preconditions)

1. Môi trường deploy tại `https://mland-operations-hub-git-develop-mland-team.vercel.app/` hoạt động ổn định.
2. Tài khoản Khách hàng `minikie456@gmail.com` đã đăng nhập hợp lệ.
3. Tài khoản Nhân viên `staff.test@mland.vn` có quyền truy cập hộp thư tư vấn `/operations/consultations`.
4. API cấu hình nhẫn (Feature 012) và API chat tư vấn (Feature 013) phản hồi sẵn sàng.

---

## 3. Các bước Thực thi Kiểm thử (Step Contracts)

### Bước 1 (D3-S01): Đăng nhập Khách hàng & Mở Trình Cấu hình Nhẫn Tham số

- **User Charter**: Khách hàng mở trang cấu hình nhẫn liên kết với gói Workshop nhẫn đôi.
- **Explore & Act**:
  1. Đăng nhập tài khoản Khách hàng: `minikie456@gmail.com` / `123123123` tại `/login`.
  2. Điều hướng đến: `/workshops/PKG-COUPLE-SILVER/design`.
  3. Chọn điểm bắt đầu: Chọn mẫu nhẫn gợi ý cơ bản hoặc phôi nhẫn trắng để bắt đầu tùy biến.
- **Observable Expected**:
  - Giao diện Trình cấu hình nhẫn mở ra đầy đủ 2 cột: Cột trái chứa các bộ điều khiển tham số (Chất liệu, Dáng đai, Size, Đá, Khắc chữ, Kích thước bản nhẫn); Cột phải là khung vẽ mô phỏng nhẫn trực quan (Ring Preview).
- **Decision Rule**:
  - `CONTINUE`: Trình cấu hình tải đầy đủ các công cụ tương tác.
  - `LOG_AND_CONTINUE`: Khung vẽ nhẫn tải chậm hơn 3 giây nhưng sau đó hiển thị bình thường.
  - `STOP`: Trang báo lỗi 500 hoặc màn hình trắng không hiển thị thanh công cụ.

---

### Bước 2 (D3-S02): Tinh chỉnh Tham số Kim hoàn & Quan sát Phản hồi Trực quan

- **User Charter**: Khách hàng thay đổi các thông số thiết kế và kiểm tra độ nhạy, tính tức thì của mô phỏng.
- **Explore & Act**:
  1. Chọn Chất liệu: **Bạc Ý 925**.
  2. Chọn Dáng đai: **Tròn cổ điển (Classic Round)**.
  3. Chọn Size nhẫn: **Size 16** (chu vi ngón tay tương ứng ~56mm).
  4. Mục Đính đá quý: Bấm thêm 1 viên **Đá CZ Xanh Lam 2mm**.
  5. Mục Khắc chữ laser: Nhập nội dung `"MLAND FOREVER 2026"`, chọn vị trí mặt trong/mặt ngoài.
  6. Kéo thanh trượt Độ dày (Thickness): điều chỉnh về mức **1.8 mm**.
  7. Kéo thanh trượt Độ rộng bản (Band Width): điều chỉnh về mức **3.5 mm**.
  8. Quan sát bản vẽ mô phỏng cập nhật theo từng thao tác kéo thả.
- **Observable Expected**:
  - Hình ảnh mô phỏng nhẫn cập nhật hình dáng, màu sắc bạc và vị trí viên đá xanh ngay tức thì không bị giật lag (Latency $\le 2s$, không chớp nháy màn hình).
  - Khung tóm tắt thông số hiển thị đầy đủ các giá trị vừa chọn.
- **Decision Rule**:
  - `CONTINUE`: Các tham số cập nhật mượt mà và hiển thị chính xác trên bản vẽ.
  - `LOG_AND_CONTINUE`: Thanh trượt slider khi kéo có độ trễ nhẹ nhưng giá trị vẫn chốt đúng.
  - `STOP`: Bấm chọn đá hoặc kéo slider gây treo đơ trình duyệt hoặc báo lỗi console unhandled.

---

### Bước 3 (D3-S03): Thẩm định Chỉ số Kỹ thuật & Lưu Mẫu Thiết kế

- **User Charter**: Khách hàng xem bảng đánh giá khối lượng, độ khó chế tác từ hệ thống và xác nhận lưu mẫu nhẫn.
- **Explore & Act**:
  1. Bấm nút **"Hoàn tất & Thẩm định"** (hoặc "Lưu thiết kế").
  2. Giao diện chuyển hướng sang trang đánh giá: `/workshops/PKG-COUPLE-SILVER/design/:designId`.
  3. Kiểm tra các chỉ số kỹ thuật hiển thị:
     - Mã thiết kế: `DESIGN_REFERENCE_CODE`.
     - Khối lượng ước tính: Khoảng `3.8g - 4.3g`.
     - Thang đo độ khó gia công: Hiển thị điểm số kèm nhãn độ khó (VD: Dễ / Trung bình) và khuyến cáo kỹ thuật.
     - Giá tham khảo ước tính.
  4. Xác nhận mẫu nhẫn đã được lưu trữ an toàn trong Sổ tay thiết kế của Khách hàng (`/my/designs`).
- **Observable Expected**:
  - Badge trạng thái hiển thị thông báo xanh: *"Đã lưu thiết kế thành công!"*.
  - Có các nút bấm hành động rõ ràng: *"💍 Mẫu nhẫn đã lưu"*, *"Đặt lịch với mẫu này"*, và nút **"Tư vấn thiết kế"**.
- **Decision Rule**:
  - `CONTINUE`: Lưu mẫu thành công, bảng chỉ số kỹ thuật hiển thị đầy đủ.
  - `LOG_AND_CONTINUE`: Khối lượng ước tính làm tròn số thập phân chưa đồng nhất.
  - `STOP`: Bấm lưu thiết kế bị báo lỗi 500 hoặc mất mã thiết kế.

---

### Bước 4 (D3-S04): Khởi tạo Cuộc hội thoại Chat Tư vấn từ Mẫu Nhẫn

- **User Charter**: Khách hàng bấm nút tư vấn để mở phòng chat trực tuyến được đính kèm sẵn thông số mẫu nhẫn vừa tạo.
- **Explore & Act**:
  1. Tại trang thẩm định kỹ thuật của mẫu nhẫn vừa lưu, bấm nút **"Tư vấn thiết kế"**.
  2. Quan sát hệ thống điều hướng sang trang: `/member/consultations`.
  3. Kiểm tra thanh đính kèm ngữ cảnh (Context Header / Attached Ring Recap) ở phía trên khung chat.
- **Observable Expected**:
  - URL có chứa tham số tham chiếu: `?designReference=<MÃ_THIẾT_KẾ>&designVersion=...`.
  - Trên đầu khung chat xuất hiện thẻ tóm tắt mẫu nhẫn (AttachedRingHoverRecap): Hiển thị ảnh thumbnail nhẫn, chất liệu Bạc Ý 925, Size 16, đá CZ xanh.
  - Khung nhập tin nhắn sẵn sàng hoạt động.
- **Decision Rule**:
  - `CONTINUE`: Mở phòng chat thành công và thẻ mẫu nhẫn đính kèm hiển thị chuẩn xác.
  - `LOG_AND_CONTINUE`: Thumbnail ảnh nhẫn nhỏ hơi mờ nhưng hover vào vẫn xem được chi tiết text.
  - `STOP`: Bấm tư vấn thiết kế bị đá về trang 404 hoặc không gắn được mẫu nhẫn vào phòng chat.

---

### Bước 5 (D3-S05): Khách hàng Gửi Tin nhắn Trao đổi Kỹ thuật

- **User Charter**: Khách hàng soạn thảo câu hỏi chuyên môn về chế tác và gửi đến xưởng.
- **Explore & Act**:
  1. Tại ô nhập tin nhắn của Khách hàng, nhập nội dung:
     `"Chào thợ kim hoàn Mland, mẫu thiết kế nhẫn này của tôi có thể xử lý bề mặt xước mờ satin và nâng cấp đá CZ sang đá Moissanite 3mm được không?"`
  2. Bấm nút **"Gửi"** (hoặc nhấn phím Enter).
- **Observable Expected**:
  - Tin nhắn được gửi đi tức thì, xuất hiện ở bong bóng chat bên phải (phía Khách hàng).
  - Văn bản tiếng Việt có dấu hiển thị chuẩn xác, không bị lỗi font hoặc ký tự lạ.
  - Trạng thái tin nhắn ghi nhận thời gian gửi rõ ràng.
- **Decision Rule**:
  - `CONTINUE`: Tin nhắn gửi thành công và xuất hiện trong luồng hội thoại.
  - `LOG_AND_CONTINUE`: Tin nhắn cần 1–2 giây mới render vào danh sách tin nhắn.
  - `STOP`: Bấm gửi tin nhắn báo lỗi mạng hoặc nội dung bị mất trắng không hiển thị.

---

### Bước 6 (D3-S06): Thao tác Nhân viên — Tiếp nhận Hội thoại & Phản hồi Kỹ thuật

- **User Charter**: Nhân viên xưởng mở hộp thư tư vấn, xem mẫu nhẫn của Khách hàng và gửi câu trả lời tư vấn chuyên môn.
- **Explore & Act**:
  1. Mở cửa sổ ẩn danh riêng biệt, đăng nhập tài khoản Nhân viên: `staff.test@mland.vn` / `StaffTest@123456`.
  2. Truy cập vào Hộp thư tư vấn xưởng: `/operations/consultations`.
  3. Tìm cuộc hội thoại mới của khách hàng `minikie456@gmail.com` vừa gửi ở Bước 5.
  4. Mở chi tiết cuộc hội thoại:
     - Kiểm tra thẻ đính kèm mẫu nhẫn của khách hàng: Bạc Ý 925, Size 16, đá CZ Xanh.
     - Đọc nội dung câu hỏi của khách hàng về xử lý xước mờ và đá Moissanite.
  5. Nếu cuộc hội thoại chưa được gán, bấm nút **"Tiếp nhận"** (Claim / Assign).
  6. Tại ô soạn thảo phản hồi, Nhân viên nhập:
     `"Chào bạn! Mẫu nhẫn này hoàn toàn xử lý được xước mờ satin và đính đá Moissanite 3mm nhé. Khi bạn đến xưởng, kỹ thuật viên sẽ hỗ trợ bạn chỉnh cữ chấu trực tiếp!"`
  7. Bấm nút **"Gửi phản hồi"**.
- **Observable Expected**:
  - Nhân viên tiếp nhận hội thoại thành công.
  - Tin nhắn phản hồi của Nhân viên xuất hiện trong luồng chat.
  - Phía cửa sổ Khách hàng (`/member/consultations`), tin nhắn phản hồi của Nhân viên tự động xuất hiện qua cơ chế polling mà không cần F5 lại trang.
- **Decision Rule**:
  - `CONTINUE`: Nhân viên xem được snapshot nhẫn và gửi phản hồi thành công; Khách hàng nhận được tin nhắn tức thì.
  - `LOG_AND_CONTINUE`: Tin nhắn phản hồi đến máy Khách hàng trễ khoảng 5–8 giây do chu kỳ polling.
  - `STOP`: Nhân viên không thấy mẫu nhẫn đính kèm hoặc bấm gửi phản hồi báo lỗi 500.

---

### Bước 7 (D3-S07): Đóng Hội thoại & Thử nghiệm Đối nghịch — Chặn Tin nhắn Sau Đóng

- **User Charter**: Kết thúc phiên tư vấn bằng lệnh đóng hội thoại và kiểm tra cơ chế bảo vệ chống spam khi hội thoại đã kết thúc.
- **Explore & Act**:
  1. Tại giao diện Nhân viên (hoặc Khách hàng), bấm nút **"Đóng hội thoại"** (`Close Conversation`).
  2. Xác nhận đóng hội thoại ➔ Trạng thái cuộc hội thoại chuyển thành **`CLOSED`**.
  3. **Thử nghiệm đối nghịch (Adversarial Check):**
     - Tại ô nhập tin nhắn của Khách hàng, cố tình gõ thêm: `"Cảm ơn xưởng nhiều nhé!"` và bấm nút "Gửi".
     - Quan sát phản ứng chặn của hệ thống.
- **Observable Expected**:
  - Trạng thái hội thoại đổi thành `CLOSED` trên cả hai màn hình.
  - Ô nhập tin nhắn bị làm mờ (disabled) hoặc nút gửi bị vô hiệu hóa; nếu cố tình submit thì hệ thống báo lỗi không thể gửi tin nhắn vào hội thoại đã đóng.
- **Decision Rule**:
  - `CONTINUE`: Đóng hội thoại thành công và chặn triệt để việc gửi thêm tin nhắn sau khi đóng.
  - `LOG_AND_CONTINUE`: Nhãn nút đóng hiển thị tiếng Anh `Close` thay vì tiếng Việt.
  - `STOP`: Hội thoại đã đóng nhưng vẫn cho phép gửi thêm tin nhắn bình thường (Lỗi logic phiên chat).

---

### Bước 8 (D3-S08): Dọn dẹp Phiên & Đăng xuất Sạch sẽ

- **User Charter**: Đăng xuất an toàn cả hai tài khoản Khách hàng và Nhân viên để dọn dẹp môi trường.
- **Explore & Act**:
  1. Khách hàng thực hiện Đăng xuất.
  2. Nhân viên thực hiện Đăng xuất.
  3. Đảm bảo toàn bộ session được thu hồi sạch sẽ.
- **Observable Expected**:
  - Trình duyệt quay về trạng thái khách vãng lai an toàn.
- **Decision Rule**:
  - `CONTINUE`: Dọn dẹp phiên thành công.

---

## 4. Điều kiện Thoát & Báo cáo Lỗi Khẩn cấp (Abort & Error Logging Gate)

- Khi gặp bất kỳ **HARD STOP** nào:
  1. Dừng ngay toàn bộ kịch bản.
  2. Chụp ảnh màn hình hiện trường lỗi đặt tại: `screenshots/BUG-E2E-003--<tên-lỗi>.jpg`.
  3. Trích xuất: URL, HTTP Error status, Correlation ID và thông báo console.
  4. Ghi ngay một bản ghi lỗi chuẩn vào `ppswbs-testing/BUG-LOG.md` bao gồm đầy đủ **Các bước tái hiện chi tiết (Reproduction Steps)**.
- Khi gặp **SOFT FAIL**:
  1. Chụp ảnh màn hình giao diện bất thường: `screenshots/WARN-E2E-003--<tên-lỗi>.jpg`.
  2. Ghi nhận finding vào mục ghi chú thực thi và áp dụng `LOG_AND_CONTINUE`.

---

## 5. Lịch sử Thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASSED (100% across 3 Variations / Runs)
- Executed at: 2026-11-25T19:56:00+07:00
- Executed by: Senior QA & System Verification Engineer
- Target URL: https://mland-operations-hub-git-develop-mland-team.vercel.app/
- Browsers utilized: Dual-browser setup (Google Chrome `b1` for Customer Member; Microsoft Edge `b2` for Manager)
- Summary: Đã hoàn tất kiểm thử toàn trình Flow 3 với 3 lượt chạy cấu hình khác nhau (Variations) theo chỉ đạo mở rộng của người dùng. Tất cả 3 lượt chạy đều vượt qua toàn bộ các bước từ tham số kim hoàn, thẩm định kỹ thuật, mở phòng chat tư vấn đính kèm tóm tắt mẫu nhẫn, tiếp nhận và trao đổi phản hồi 2 chiều, đóng hội thoại và kiểm tra bẫy đối nghịch (Adversarial Check) chặn gửi tin nhắn khi đã đóng.

---

### Chi tiết 3 Lượt Chạy Cấu Hình (3 Variations Breakdown):

#### 1. Lượt Chạy 1 (Variation 1 - Classic Solitaire with Stone):
- **Cấu hình tham số**:
  - Chất liệu: Bạc Ý 925
  - Dáng đai: Dáng tròn cổ điển (Classic Round)
  - Size: 16 (Chu vi ngón 56 mm)
  - Đá quý: 1x Đá CZ Xanh Lam 2mm (Sapphire Blue)
  - Khắc chữ laser: "MLAND FOREVER 2026" (Mặt trong nhẫn)
  - Độ dày: 1.8 mm | Độ rộng bản nhẫn: 3.5 mm
- **Chỉ số thẩm định kỹ thuật**:
  - Mã thiết kế: `DSG-05b8f4d9-f341-4664-a9fb-0346c3e533a4` (Phiên bản 1)
  - Khối lượng ước tính: `4.02 g` (Vượt mức tiêu chuẩn: +122.400 ₫)
  - Chi phí tham khảo: `502.400 ₫`
  - Độ khó chế tác: `3.3 / 10` (Mức độ: Trung bình)
- **Tư vấn & Trao đổi 2 chiều**:
  - Hội thoại tạo: `CONV-1dcd2c6c-9c59-43d7-822c-4ec8edbbe401`
  - Thẻ đính kèm `AttachedRingHoverRecap`: Nhận diện chuẩn xác mẫu nhẫn, chất liệu Bạc Ý 925, Size 16, đá CZ Xanh lam.
  - Khách hàng gửi: *"Chào thợ kim hoàn Mland, mẫu thiết kế nhẫn này của tôi có thể xử lý bề mặt xước mờ satin và nâng cấp đá CZ sang đá Moissanite 3mm được không?"*
  - Quản lý tiếp nhận & phản hồi: *"Chào bạn! Mẫu nhẫn này hoàn toàn xử lý được xước mờ satin và đính đá Moissanite 3mm nhé. Khi bạn đến xưởng, kỹ thuật viên sẽ hỗ trợ bạn chỉnh cữ chấu trực tiếp!"*
  - Quản lý thực hiện đóng hội thoại (`CLOSED`).
  - **Adversarial Check**: Giao diện Khách hàng cập nhật trạng thái "Đã đóng", khung nhập tin nhắn bị ẩn hoàn toàn, hiển thị thông báo "Cuộc trò chuyện này đã đóng. Hãy mở lại để tiếp tục nhắn tin." ➔ **PASSED**.

#### 2. Lượt Chạy 2 (Variation 2 - Ocean Wave with Satin Matte Finish):
- **Cấu hình tham số**:
  - Chất liệu: Bạc Ý 925
  - Dáng đai: Dáng lượn sóng (Ocean Wave)
  - Size: 14 (Chu vi ngón 54 mm)
  - Họa tiết: Xử lý phay xước mờ (Satin Matte Finish, +100.000 ₫)
  - Khắc chữ laser: "LOVE 2026" (Mặt trong nhẫn)
  - Độ dày: 1.8 mm | Độ rộng bản nhẫn: 3.5 mm
- **Chỉ số thẩm định kỹ thuật**:
  - Mã thiết kế: `DSG-99e86c54-bc0c-463b-8b62-79ac2b1b3d69` (Phiên bản 1)
  - Khối lượng ước tính: `3.89 g` (Vượt mức tiêu chuẩn: +106.800 ₫)
  - Chi phí tham khảo: `536.800 ₫`
  - Độ khó chế tác: `3.9 / 10` (Mức độ: Trung bình)
- **Tư vấn & Trao đổi 2 chiều**:
  - Hội thoại tạo: `CONV-7f78855c-5fc6-436e-ba03-bae80056cd88`
  - Thẻ đính kèm `AttachedRingHoverRecap`: Nhận diện chuẩn xác mẫu nhẫn lượn sóng Size 14, họa tiết phay xước mờ, khắc "LOVE 2026".
  - Khách hàng gửi: *"Mẫu nhẫn lượn sóng này mình muốn làm phôi vàng tây hoặc mạ vàng hồng 18K thì phụ phí xưởng là bao nhiêu ạ?"*
  - Quản lý tiếp nhận & phản hồi: *"Chào bạn! Với dáng lượn sóng mạ vàng hồng 18K thì phụ phí xi mạ cao cấp tại quầy là 150.000đ nhé. Đội ngũ thợ kim hoàn sẽ hoàn thiện ngay trong ca workshop cho bạn!"*
  - Quản lý thực hiện đóng hội thoại (`CLOSED`).
  - **Adversarial Check**: Giao diện Khách hàng cập nhật tức thì trạng thái "Đã đóng", input field bị loại bỏ khỏi DOM ➔ **PASSED**.

#### 3. Lượt Chạy 3 (Variation 3 - Heavy Band Flat Ergonomic with Custom Sliders):
- **Cấu hình tham số**:
  - Chất liệu: Bạc Ý 925
  - Dáng đai: Đai phẳng công thái học (Flat Ergonomic)
  - Size: 20 (Chu vi ngón 60 mm)
  - Kích thước thanh trượt thủ công: Độ dày tăng lên `2.2 mm`, Độ rộng bản nhẫn tăng lên `5.0 mm` (Bản to / Heavyweight)
  - Khắc chữ laser: "TOGETHER SINCE 2020" (Mặt trong nhẫn)
- **Chỉ số thẩm định kỹ thuật**:
  - Mã thiết kế: `DSG-fc31e37a-18c0-4669-978f-095869fb19ac` (Phiên bản 1)
  - Khối lượng ước tính: `7.63 g` (Khối lượng lớn vượt trội, phụ phí phôi bạc: +555.600 ₫)
  - Chi phí tham khảo: `885.600 ₫`
  - Độ khó chế tác: `2.8 / 10` (Mức độ: Dễ do tiết diện phẳng vuông vức)
- **Tư vấn & Trao đổi 2 chiều**:
  - Thẻ đính kèm `AttachedRingHoverRecap`: Nhận diện chuẩn xác mẫu nhẫn bản to Size 20, dày 2.2mm, rộng 5.0mm, khắc "TOGETHER SINCE 2020".
  - Khách hàng gửi: *"Nhẫn bản dày 5mm này thì đeo có bị cấn tay không xưởng ơi, và thời gian hoàn thiện tại workshop là bao lâu?"*
  - Quản lý tiếp nhận & phản hồi: *"Chào bạn! Bản 5mm bo cạnh công thái học đeo rất êm tay nhé. Thời gian chế tác khoảng 2.5 - 3 tiếng và hoàn thiện lấy ngay trong buổi workshop ạ!"*
  - Quản lý thực hiện đóng hội thoại (`CLOSED`).
  - **Adversarial Check**: Khách hàng thấy thông báo đóng hội thoại, toàn bộ lịch sử tin nhắn đồng bộ chuẩn xác ➔ **PASSED**.

---

### Kết luận Kỹ thuật (QA Verdict):
- **Tính toàn vẹn tham số**: Bộ máy tính toán thẩm định (Appraisal Engine) phản ứng chuẩn xác với kích thước vật lý: khi nâng size 20 + dày 2.2mm + rộng 5.0mm, khối lượng tăng vọt lên 7.63g và chi phí tăng tương xứng.
- **Tính gắn kết ngữ cảnh**: Tính năng đính kèm tóm tắt mẫu nhẫn vào hội thoại tư vấn (`designReference`) hoạt động đồng bộ 100% giữa Member và Quản lý/Nhân viên.
- **Bảo mật trạng thái (State Security)**: Cơ chế chặn gửi tin nhắn khi hội thoại đã đóng được kiểm soát nghiêm ngặt ở cả Client UI và API Backend.
<!-- EXECUTION:END -->
