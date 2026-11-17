---
id: JOURNEY-PHASE-4
feature: 004-ai-chatbox-analysis-and-suggestion
journey_phase: 4
test_level: master_e2e
test_case_type: Dual-Actor-Interactive
source_ref: spec.md#FE-04; designreview; spec-general.md#§2,§3; 008-spec.md
target_url: http://localhost:5173/design-requests/new
actor_primary: MEMBER (Browser 1)
actor_secondary: MANAGER (Browser 2)
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080
test_data:
  member_email: inherited_from_phase_1
  custom_sketch_image: "test-assets/custom-lotus-ring-sketch.jpg"
  custom_quote_price: 3000000
  custom_deposit_amount: 1500000
  vnpay_card_number: "9704198526191432198"
  vnpay_card_holder: "NGUYEN VAN A"
  vnpay_card_issue_date: "07/15"
  vnpay_otp: "123456"
  isolation_key: MASTER-E2E-P4-20261114
cleanup:
  required: false
  method: handoff-to-phase-5
---

# JOURNEY-PHASE-4: Yêu cầu Thiết kế Nhẫn Riêng Độc bản, AI Tham vấn, Quản lý Thẩm định & Đặt cọc Sản xuất

**Ngày tạo**: 2026-11-14 | **Cập nhật**: 2026-11-14  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Trình duyệt sử dụng**: 
- **Browser 1**: Khách hàng Hội viên (`MEMBER`)
- **Browser 2**: Quản lý xưởng kim hoàn (`MANAGER`)
**Mục tiêu**: Kiểm thử toàn diện hành trình đặt làm nhẫn theo thiết kế riêng (Custom Ring Manufacturing): Từ việc khách hàng tải lên bản vẽ phác thảo, xử lý bẫy upload file sai quy chuẩn, AI Gemini phân tích sơ bộ thành phần kỹ thuật, cam kết bản quyền, Quản lý xưởng thẩm định tính khả thi và duyệt báo giá gia công, đến bước khách hàng chốt báo giá và thanh toán tiền cọc 50% qua cổng VNPay để chuyển sang sản xuất thực tế.

---

## 1. Tiền điều kiện (Preconditions)

- [x] Tài khoản Member đang đăng nhập tại **Browser 1**.
- [x] Tài khoản Quản lý (`MANAGER`) đang đăng nhập tại **Browser 2**.
- [x] File ảnh phác thảo hợp lệ `custom-lotus-ring-sketch.jpg` sẵn sàng trong thư mục test.
- [x] Cổng Gemini AI hỗ trợ phân tích bản vẽ nhẫn đang mở kết nối.
- [x] Cổng thanh toán VNPay Sandbox phản hồi ổn định.

---

## 2. Đánh giá Đa chiều qua 2 Góc nhìn (2 POVs Evaluation)

### POV 1 — Khách hàng Hội viên (Non-tech Member Experience):
- **Trực quan & Dễ hiểu**: Khi tải ảnh lên có ngay khung ảnh thu nhỏ xem trước (thumbnail preview), thanh tiến trình tải ảnh mượt mà, nút "Xóa/Thay đổi ảnh" nếu chọn nhầm.
- **Cam kết pháp lý trang nhã**: Cam kết bản quyền thiết kế được viết ngắn gọn trong 2 câu dễ hiểu, không dùng văn phong pháp lý rườm rà gây ngột ngạt.
- **Trạng thái minh bạch**: Sau khi gửi yêu cầu, giao diện hiển thị thẻ thông báo rõ ràng: *"Yêu cầu của bạn đang được Quản lý xưởng thẩm định kỹ thuật (Dự kiến phản hồi trong 24 giờ)"*. Khi có báo giá, khách thấy rõ số tiền cọc 50% cần thanh toán để bắt đầu đúc khuôn.

### POV 2 — Kỹ thuật viên Kiểm định (Senior QA Technical Verification):
- **Nguyên tắc Quyết định của Con người (Human-in-the-loop Guard)**: Tuân thủ tuyệt đối `spec-general.md#§2, §3`: AI chỉ đóng vai trò phân tích sơ bộ và gợi ý tham vấn; **AI tuyệt đối không được tự ý duyệt giá, chấp thuận hay từ chối đơn hàng**. Mọi quyết định thương mại và kỹ thuật bắt buộc phải do tài khoản `MANAGER` phê duyệt.
- **Bảo mật Tải tệp (File Upload & MIME Validation)**: Form tải ảnh bắt buộc kiểm tra định dạng và dung lượng tại cả client và backend; chặn đứng các tệp `.exe`, `.pdf`, `.svg` chứa script độc hại.
- **Ràng buộc Quyền Thành viên (Member-Only Route Guard)**: Tuyến đường `/design-requests/new` là đặc quyền chỉ dành riêng cho tài khoản đã xác thực (`MEMBER`).

---

## 3. Các bước Thực thi trên Trình duyệt (Browser Action Steps)

### Bước 1: [Browser 1 - Member] Mở Cổng Yêu cầu Thiết kế Riêng (Feature 004)
```browser
# Điều hướng tới cổng tạo yêu cầu thiết kế riêng
goto http://localhost:5173/design-requests/new
wait getByRole('heading', { name: /Đặt Chế Tác Nhẫn Theo Yêu Cầu/i })

# Điền tiêu đề và mô tả ý tưởng
fill getByLabel(/Tên ý tưởng thiết kế/i) "Nhẫn Cưới Hoa Văn Sen Cổ Điển"
fill getByLabel(/Mô tả chi tiết ý tưởng/i) "Nhẫn đôi vàng 10K hoặc bạc phủ rhodium, đai nhẫn khắc họa tiết cánh sen chìm, ổ chấu 4 giữ viên đá chủ 4mm"
```

### Bước 2: [Browser 1 - Member] Thử thách Đối nghịch (Adversarial Step 4) — Chặn Tệp Sai Quy Chuẩn
```browser
# 🛑 Thử thách Đối nghịch: Cố tình tải lên file PDF hoặc file dung lượng quá 10MB
# (Giả lập chọn tệp tài liệu sai định dạng)
setInputFiles getByLabel(/Tải lên hình ảnh phác thảo/i) "test-assets/invalid-document.pdf"
wait 1000

# Xác minh thông báo lỗi validation tức thì
find /Định dạng tệp không được hỗ trợ|Vui lòng tải tệp ảnh JPG, PNG hoặc WEBP/i
screenshot --path ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/screenshots/P4-01--adversarial-invalid-file.jpg
```

### Bước 3: [Browser 1 - Member] Phục hồi & Tải Ảnh Hợp lệ, AI Phân tích & Gửi Yêu cầu
```browser
# Tải tệp ảnh phác thảo hợp lệ
setInputFiles getByLabel(/Tải lên hình ảnh phác thảo/i) "test-assets/custom-lotus-ring-sketch.jpg"
wait 2500

# Xác minh ảnh thu nhỏ (Thumbnail preview) hiển thị sắc nét
find getByRole('img', { name: /Ảnh phác thảo thiết kế/i })

# AI phân tích sơ bộ thành phần kỹ thuật
wait getByText(/Phân tích sơ bộ từ AI Mland/i)
find /Kiểu dáng: Nhẫn họa tiết chạm khắc|Ổ chấu: 4 chấu|Độ khó chế tác: Trung bình/i
screenshot --path ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/screenshots/P4-02--ai-analysis-preview.jpg

# Tick chọn cam kết bản quyền hợp lệ
click getByLabel(/Tôi cam kết sở hữu hoặc có quyền sử dụng hình ảnh thiết kế này/i)

# Bấm Gửi yêu cầu thẩm định
click getByRole('button', { name: /Gửi yêu cầu tới Quản lý xưởng/i })
wait 3000

# Xác minh gửi thành công, cấp mã REQ-XXXX
find /Gửi yêu cầu thành công/i
find /Mã yêu cầu: REQ-/i
find /Trạng thái: CHỜ THẨM ĐỊNH|PENDING_REVIEW/i
screenshot --path ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/screenshots/P4-03--request-submitted.jpg
```

### Bước 4: [Browser 2 - Manager] Quản lý Thẩm định Kỹ thuật & Phát hành Báo giá Gia công
```browser
# Mở Browser 2: Quản lý vào màn hình kiểm duyệt thiết kế riêng
goto http://localhost:5173/operator/design-review
wait getByRole('heading', { name: /Thẩm định Yêu cầu Thiết kế Riêng/i })

# Chọn yêu cầu REQ-XXXX vừa gửi của học viên Phương Uyên
click getByText(/Nhẫn Cưới Hoa Văn Sen Cổ Điển/i)
wait getByRole('heading', { name: /Chi tiết thẩm định yêu cầu/i })

# Quản lý xem xét bản vẽ và ghi nhận đánh giá chuyên môn
fill getByLabel(/Đánh giá kỹ thuật của xưởng/i) "Bản vẽ sắc nét, họa tiết hoa sen khả thi với kỹ thuật đúc sáp tay truyền thống. Đề xuất đúc Bạc 925 phủ Rhodium cao cấp"
fill getByLabel(/Thời gian gia công dự kiến \(ngày\)/i) "7"
fill getByLabel(/Tổng chi phí gia công \(VNĐ\)/i) "3000000"

# Bấm Phê duyệt & Phát hành Báo giá
click getByRole('button', { name: /Phê duyệt & Gửi báo giá cho khách/i })
wait 2000
find /Báo giá đã được gửi tới khách hàng/i
screenshot --path ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/screenshots/P4-04--manager-quote-approved.jpg
```

### Bước 5: [Browser 1 - Member] Chấp thuận Báo giá & Thanh toán Cọc Sản xuất 50%
```browser
# Quay lại Browser 1: Member mở trang theo dõi yêu cầu thiết kế riêng
goto http://localhost:5173/design-requests
wait getByText(/Báo giá đã sẵn sàng/i)

# Bấm xem chi tiết báo giá
click getByRole('button', { name: /Xem báo giá & Tiến độ/i })
wait getByRole('heading', { name: /Chi tiết báo giá gia công/i })

# Xác minh thông tin chi phí minh bạch
find /Tổng chi phí: 3.000.000 VNĐ/i
find /Tiền cọc cần thanh toán \(50%\): 1.500.000 VNĐ/i
find /Thời gian dự kiến hoàn thành: 7 ngày/i
screenshot --path ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/screenshots/P4-05--member-view-quote.jpg

# Bấm Đồng ý & Đặt cọc 50% qua VNPay
click getByRole('button', { name: /Chấp thuận & Đặt cọc 1.500.000 VNĐ/i })
wait 3000

# Nhập thẻ NCB test tại cổng VNPay Sandbox
fill getByPlaceholder(/Số thẻ/i) "9704198526191432198"
fill getByPlaceholder(/Tên chủ thẻ/i) "NGUYEN VAN A"
fill getByPlaceholder(/Ngày phát hành/i) "07/15"
click getByRole('button', { name: /Tiếp tục/i })
wait 1500
fill getByPlaceholder(/OTP/i) "123456"
click getByRole('button', { name: /Xác nhận/i })
wait 4000

# Xác minh đơn hàng chuyển sang trạng thái ĐANG SẢN XUẤT
find /Đặt cọc thành công|Đã chuyển sang xưởng sản xuất/i
find /Trạng thái: ĐANG CHẾ TÁC|IN_PRODUCTION/i
screenshot --path ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/screenshots/P4-06--custom-order-in-production.jpg
```

---

## 4. Kết quả Kỳ vọng theo 5 Trụ Cột (Strict Expected Assertions)

### Trụ cột 1: Bố cục & Tính Toàn vẹn Dữ liệu (Layout & Content)
- [x] Đầy đủ thông tin báo giá: Chi phí 3,000,000đ, tiền cọc 1,500,000đ, thời hạn gia công 7 ngày, mã yêu cầu `REQ-XXXX`.
- [x] Trạng thái đơn hàng chuyển đổi đúng quy trình: `PENDING_REVIEW` ➔ `QUOTED` ➔ `IN_PRODUCTION`.

### Trụ cột 2: Chống Che khuất & Tràn vỡ Giao diện (Visual Clipping)
- [x] Khung ảnh xem trước giữ đúng tỷ lệ khung hình (Aspect Ratio), không bị méo mó ảnh gốc; nút "Xóa ảnh" dễ bấm.
- [x] Bảng báo giá hiển thị dạng Card trang trọng, các số liệu tài chính căn chỉnh lề phải rõ ràng.

### Trụ cột 3: Đa thiết bị Laptop & Smartphone (Multi-Viewport)
- [x] Form tải ảnh trên Smartphone (390px) cho phép người dùng chọn ảnh từ thư viện máy hoặc chụp trực tiếp từ camera.
- [x] Nút "Đặt cọc 1.500.000 VNĐ" hiển thị dạng thanh nổi cố định trên di động.

### Trụ cột 4: Cỡ chữ & Song ngữ Việt - Anh (Typography & i18n)
- [x] Câu gợi ý của AI và nhận xét của Quản lý hiển thị bằng tiếng Việt chuẩn ngữ pháp, font chữ dễ đọc, cỡ chữ tối thiểu 14px.
- [x] Các thuật ngữ chuyên ngành (Bạc 925, Rhodium, 4 chấu) được chú thích rõ ràng.

### Trụ cột 5: Tối ưu Thao tác & Tải Nhận thức (Ergonomics & Cognitive Load)
- [x] Quy trình gửi ảnh chỉ gồm 3 trường nhập liệu chính (Tên ý tưởng, Mô tả, Tải ảnh), không bắt khách trả lời bảng câu hỏi dài dòng.
- [x] Tiền cọc 50% được tính sẵn tự động, khách không phải tự chia đôi số tiền 3,000,000 VNĐ.

---

## 5. Dữ liệu Bàn giao sang Pha 5 (Data Handoff)

| Khóa dữ liệu | Giá trị thực tế sinh ra | Mục đích sử dụng ở Pha 5 |
| :--- | :--- | :--- |
| `MEMBER_EMAIL` | Kế thừa từ Pha 1 | Đăng nhập tài khoản để viết Đánh giá (Review) và kiểm tra bảo mật ở Pha 5. |
| `CUSTOM_ORDER_ID` | Đơn hàng thiết kế riêng đang chế tác | Bổ sung vào hồ sơ hoạt động của Member. |
