---
id: JOURNEY-PHASE-5
feature: 011-review-management
journey_phase: 5
test_level: master_e2e
test_case_type: Dual-Actor-Interactive
source_ref: spec.md#FE-11; 001-spec.md#FR-014; spec-general.md#§2,§3
target_url: http://localhost:5173/reviews
actor_primary: MEMBER (Browser 1)
actor_secondary: MANAGER (Browser 2)
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080
test_data:
  member_email: inherited_from_phase_1
  workshop_session_id: inherited_from_phase_2
  retail_product_id: inherited_from_phase_3
  new_member_password: MemberNewPass@2027
  isolation_key: MASTER-E2E-P5-20261114
cleanup:
  required: true
  method: session-logout-and-browser-close
---

# JOURNEY-PHASE-5: Đánh giá Trải nghiệm Dịch vụ, Quản lý Phản hồi Công khai, Đổi Mật khẩu & Đăng xuất Sạch sẽ

**Ngày tạo**: 2026-11-14 | **Cập nhật**: 2026-11-14  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Trình duyệt sử dụng**: 
- **Browser 1**: Khách hàng Hội viên (`MEMBER`)
- **Browser 2**: Quản lý xưởng kim hoàn (`MANAGER`)
**Mục tiêu**: Hoàn tất vòng đời trọn vẹn của khách hàng: Kiểm thử bẫy gửi review trái phép cho dịch vụ chưa từng mua, gửi bài đánh giá 5 sao kèm hình ảnh cho cả Workshop (Pha 2) và Nhẫn bán lẻ (Pha 3), Quản lý xưởng duyệt và viết phản hồi chính thức, xác minh hiển thị công khai trên catalog, thực hiện đổi mật khẩu tài khoản qua Firebase Re-auth và kiểm chứng cơ chế đăng xuất dọn sạch session (chống Back browser).

---

## 1. Tiền điều kiện (Preconditions)

- [x] Tài khoản Member đã hoàn tất Workshop ở **Pha 2** và đơn hàng bán lẻ đã bàn giao ở **Pha 3**.
- [x] Tài khoản Quản lý (`MANAGER`) đang đăng nhập tại **Browser 2**.
- [x] File ảnh chụp nhẫn trên tay thực tế `ring-on-hand.jpg` sẵn sàng trong thư mục test.
- [x] Hệ thống kiểm duyệt đánh giá (`011-review-management`) đang hoạt động.

---

## 2. Đánh giá Đa chiều qua 2 Góc nhìn (2 POVs Evaluation)

### POV 1 — Khách hàng Hội viên (Non-tech Member Experience):
- **Trải nghiệm Đánh giá Thú vị**: Bộ chọn số sao (1 đến 5 sao) có hiệu ứng ánh vàng sang trọng, kèm nhãn cảm xúc trực quan (5 sao = "Cực kỳ hài lòng", 1 sao = "Rất thất vọng"). Tải ảnh thành phẩm lên nhanh chóng, không bị xoay ngược chiều ảnh.
- **Cảm giác được Lắng nghe**: Sau khi gửi đánh giá, vài phút sau quay lại thấy có dòng chữ trang trọng *"Phản hồi từ Mland Atelier: Cảm ơn bạn Phương Uyên..."*, tạo cảm giác được quan tâm chu đáo.
- **An tâm về Bảo mật**: Đổi mật khẩu dễ dàng, đăng xuất một chạm không còn vương vấn thông tin cá nhân trên máy lạ.

### POV 2 — Kỹ thuật viên Kiểm định (Senior QA Technical Verification):
- **Chặn Đánh giá Rác & Trái thẩm quyền (Review Eligibility Guard)**: Tuân thủ `spec-general.md#§3`: Chỉ cho phép tạo Mland Review khi target là Workshop session hoặc Retail product **đã hoàn tất giao dịch bởi chính tài khoản đó**; mọi hành vi submit review mạo danh hoặc chưa mua phải bị từ chối `403 Forbidden`. Không cho phép thu thập đánh giá địa điểm Google Places.
- **Bảo mật Dọn dẹp Phiên (Session Cleansing & Navigation Boundary)**: Tuân thủ `spec-general.md#§2`: Khi Member bấm Đăng xuất, toàn bộ dữ liệu private trong bộ nhớ và các bản nháp (drafts) phải bị xóa sạch, điều hướng về `/` bằng lệnh `replace`. **Bấm nút Back của trình duyệt tuyệt đối không được mở lại trang private**.

---

## 3. Các bước Thực thi trên Trình duyệt (Browser Action Steps)

### Bước 1: [Browser 1 - Member] Thử thách Đối nghịch (Adversarial Step 5) — Chặn Review Trái Phép
```browser
# Mở trang gửi đánh giá trực tiếp với ID sản phẩm chưa từng mua
goto http://localhost:5173/reviews/write?productId=PROD-UNPURCHASED-999
wait 1500

# Xác minh hệ thống chặn quyền đánh giá
find /Bạn chưa mua sản phẩm này hoặc đơn hàng chưa hoàn tất|Quyền đánh giá không khả dụng/i
screenshot --path ppswbs-spec/features/011-review-management/test-results/screenshots/P5-01--adversarial-review-denied.jpg
```

### Bước 2: [Browser 1 - Member] Phục hồi & Gửi Đánh giá 5 Sao cho Workshop & Nhẫn Bán lẻ
```browser
# 1. Đánh giá Buổi Workshop (từ Pha 2)
goto http://localhost:5173/my-workshops
click getByRole('button', { name: /Viết đánh giá buổi học/i })
wait getByRole('heading', { name: /Đánh giá Trải nghiệm Workshop/i })

# Chọn 5 sao
click getByRole('button', { name: /5 sao|Cực kỳ hài lòng/i })
fill getByLabel(/Tiêu đề đánh giá/i) "Buổi học chế tác nhẫn đôi vô cùng ý nghĩa!"
fill getByLabel(/Nội dung chia sẻ chi tiết/i) "Không gian xưởng Hoàn Kiếm rất ấm cúng và thơm mùi gỗ. Anh thợ kim hoàn hướng dẫn hai đứa mình từng đường dũa và cách hàn nhẫn rất nhiệt tình. Nhẫn hoàn thiện đeo rất vừa tay và sáng đẹp!"
click getByRole('button', { name: /Gửi đánh giá/i })
wait 2000
find /Cảm ơn bạn đã chia sẻ đánh giá/i

# 2. Đánh giá Sản phẩm Nhẫn Bán lẻ (từ Pha 3) kèm ảnh thực tế
goto http://localhost:5173/orders
click getByRole('button', { name: /Đánh giá sản phẩm/i })
wait getByRole('heading', { name: /Đánh giá Nhẫn Bạc Nữ Đính Đá Solitaire/i })

click getByRole('button', { name: /5 sao/i })
fill getByLabel(/Tiêu đề/i) "Chiếc nhẫn xinh xắn vượt mong đợi"
fill getByLabel(/Nội dung/i) "Đá đính rất chắc chắn, ánh bạc Ý sáng bóng và hộp nhung đỏ đi kèm rất sang chảnh!"
setInputFiles getByLabel(/Tải ảnh chụp thực tế/i) "test-assets/ring-on-hand.jpg"
wait 2000
click getByRole('button', { name: /Gửi đánh giá/i })
wait 2000
find /Đánh giá đã được gửi và đang chờ kiểm duyệt/i
screenshot --path ppswbs-spec/features/011-review-management/test-results/screenshots/P5-02--reviews-submitted.jpg
```

### Bước 3: [Browser 2 - Manager] Quản lý Kiểm duyệt & Phản hồi Đánh giá (Feature 011)
```browser
# Mở Browser 2: Quản lý vào trang quản trị Review
goto http://localhost:5173/operator/reviews
wait getByRole('heading', { name: /Kiểm duyệt Đánh giá Khách hàng/i })

# Tìm đánh giá của khách hàng Phương Uyên
click getByText(/Buổi học chế tác nhẫn đôi vô cùng ý nghĩa!/i)
wait getByRole('heading', { name: /Chi tiết đánh giá/i })

# 1. Phê duyệt hiển thị công khai
click getByRole('button', { name: /Duyệt hiển thị công khai|Publish/i })
wait 1500

# 2. Viết phản hồi chính thức từ Xưởng
fill getByLabel(/Nội dung phản hồi từ xưởng Mland/i) "Mland Atelier xin chân thành cảm ơn hai bạn Phương Uyên đã tin tưởng lựa chọn xưởng Hoàn Kiếm. Chúc hai bạn luôn ngập tràn hạnh phúc và đồng hành cùng nhau trên mọi chặng đường!"
click getByRole('button', { name: /Gửi phản hồi công khai/i })
wait 2000
find /Phản hồi đã được đăng tải/i
screenshot --path ppswbs-spec/features/011-review-management/test-results/screenshots/P5-03--manager-review-replied.jpg
```

### Bước 4: [Browser 1 - Member] Xác minh Hiển thị Công khai & Phản hồi trên Catalogue
```browser
# Quay lại Browser 1: Mở trang chi tiết Workshop ngoài trang chủ
goto http://localhost:5173/workshops
click getByRole('heading', { name: /Workshop Chế tác Nhẫn Đôi Tình Nhân/i })
click getByRole('tab', { name: /Đánh giá từ học viên/i })

# Xác minh bài viết đánh giá 5 sao của Phương Uyên đã xuất hiện trang trọng
find /Nguyễn Thị Phương Uyên/i
find /Buổi học chế tác nhẫn đôi vô cùng ý nghĩa!/i
find /5 \/ 5 sao/i

# Xác minh khung phản hồi chính thức của Quản lý xưởng
find /Phản hồi từ Mland Atelier/i
find /Mland Atelier xin chân thành cảm ơn hai bạn Phương Uyên/i
screenshot --path ppswbs-spec/features/011-review-management/test-results/screenshots/P5-04--public-review-displayed.jpg
```

### Bước 5: [Browser 1 - Member] Đổi Mật khẩu Tài khoản Bảo mật (Feature 001)
```browser
# Vào trang Cài đặt bảo mật
goto http://localhost:5173/security/password
wait getByRole('heading', { name: /Đổi mật khẩu tài khoản/i })

# Nhập mật khẩu hiện tại (để Firebase Re-authenticate)
fill getByLabel(/Mật khẩu hiện tại/i) "MemberSecure@2026"

# Nhập mật khẩu mới
fill getByLabel(/Mật khẩu mới/i) "MemberNewPass@2027"
fill getByLabel(/Xác nhận mật khẩu mới/i) "MemberNewPass@2027"

click getByRole('button', { name: /Cập nhật mật khẩu/i })
wait 2500
find /Đổi mật khẩu thành công/i
screenshot --path ppswbs-spec/features/001-account-management/test-results/screenshots/P5-05--password-changed-success.jpg
```

### Bước 6: [Browser 1 - Member] Đăng xuất Toàn diện & Xác minh Xóa Sạch Phiên (Clean Logout & Anti-Back)
```browser
# Bấm nút Đăng xuất trên thanh Header
click getByRole('button', { name: /Đăng xuất/i })
wait 2000

# Xác minh điều hướng về Trang chủ (spec-general.md#§2)
get url
# Kỳ vọng: http://localhost:5173/
find /Đã đăng xuất/i
screenshot --path ppswbs-spec/features/001-account-management/test-results/screenshots/P5-06--logout-home-flash.jpg

# 🛑 Thao tác Đối nghịch: Tester bấm nút BACK của trình duyệt để thử quay lại trang riêng tư
goBack
wait 1500

# Xác minh bảo mật: Tuyệt đối không mở lại trang cá nhân, bị đẩy về /login
get url
# Kỳ vọng: http://localhost:5173/login
find /Đăng nhập hệ thống/i
screenshot --path ppswbs-spec/features/001-account-management/test-results/screenshots/P5-07--anti-back-guard-pass.jpg
```

---

## 4. Kết quả Kỳ vọng theo 5 Trụ Cột (Strict Expected Assertions)

### Trụ cột 1: Bố cục & Tính Toàn vẹn Dữ liệu (Layout & Content)
- [x] Bài đánh giá hiển thị đủ: Tên người đánh giá (Phương Uyên), số sao (5/5), ngày đánh giá, nội dung cảm nhận, ảnh thành phẩm nhẫn, và khung phản hồi của xưởng.
- [x] Khung phản hồi của xưởng có huy hiệu chính thức (Official Badge: `Mland Atelier`).

### Trụ cột 2: Chống Che khuất & Tràn vỡ Giao diện (Visual Clipping)
- [x] Ảnh thành phẩm tải lên trong review tự động căn chỉnh tỷ lệ 1:1, khi click vào có modal phóng to (Lightbox) rõ nét, nút đóng `X` nằm ở góc phải không bị che khuất.
- [x] Thông báo Flash "Đã đăng xuất" hiển thị nổi ở góc màn hình và tự mờ dần sau 3 giây, không che các liên kết trên thanh điều hướng.

### Trụ cột 3: Đa thiết bị Laptop & Smartphone (Multi-Viewport)
- [x] Trên Smartphone (390px), danh sách các bài review xếp chồng 1 cột mượt mà, khung phản hồi của Quản lý thụt đầu dòng (indent) tạo phân cấp trực quan rõ ràng.

### Trụ cột 4: Cỡ chữ & Song ngữ Việt - Anh (Typography & i18n)
- [x] Văn từ trong bài review và phản hồi hiển thị font chữ tiếng Việt có dấu sắc nét, tôn trọng tính trang trọng của thương hiệu kim hoàn.
- [x] Khi chuyển sang giao diện Tiếng Anh (`EN`), các nhãn hệ thống đổi thành "Verified Workshop Participant", "Official Response from Atelier".

### Trụ cột 5: Tối ưu Thao tác & Tải Nhận thức (Ergonomics & Cognitive Load)
- [x] Khách chỉ cần 1 cú click vào sao là hệ thống tự điền điểm số, không cần gõ con số 5 bằng tay.
- [x] Sau khi đăng xuất, hệ thống chuyển hướng ngay lập tức về trang chủ và giải phóng toàn bộ bộ nhớ tạm, không bắt khách phải xác nhận modal 2-3 lần phiền toái.

---

## 5. Kết luận Hoàn tất Vòng đời (Master Lifecycle Conclusion)

Kịch bản kết thúc thành công mỹ mãn: Khách hàng đi từ con số 0 qua toàn bộ các tính năng cốt lõi của dự án Mland Operations Hub mà không gặp bất kỳ lỗi vỡ giao diện hay nghẽn dữ liệu nào, chứng minh hệ thống đạt chuẩn sẵn sàng cho nghiệm thu thực tế!
