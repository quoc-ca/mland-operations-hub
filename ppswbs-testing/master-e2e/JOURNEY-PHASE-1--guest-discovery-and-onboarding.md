---
id: JOURNEY-PHASE-1
feature: 001-account-management
journey_phase: 1
test_level: master_e2e
test_case_type: Adversarial-Recovery
source_ref: spec.md#FR-001,FR-002,FR-003; spec-general.md#§2,§3
target_url: http://localhost:5173/
actor_primary: GUEST -> MEMBER
actor_secondary: null
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080
test_data:
  fixture: guest-to-member-onboarding
  member_email_template: e2e_member_{timestamp}@mland.test
  member_password: MemberSecure@2026
  isolation_key: MASTER-E2E-P1-20261114
cleanup:
  required: false
  method: handoff-to-phase-2
---

# JOURNEY-PHASE-1: Khám phá Danh mục, Chat AI, Đăng ký Hội viên & Thiết lập Hồ sơ Cá nhân

**Ngày tạo**: 2026-11-14 | **Cập nhật**: 2026-11-14  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Trình duyệt sử dụng**: **Browser 1 (Khách hàng / Client View)**  
**Mục tiêu**: Kiểm chứng toàn diện trải nghiệm của một khách hàng mới từ việc tiếp cận trang chủ, tương tác với AI tư vấn, cấu hình thử mẫu nhẫn 3D, vượt qua các thử thách bảo mật form và route guard, đăng ký tài khoản thành công và hoàn thiện sổ địa chỉ nhận hàng.

---

## 1. Tiền điều kiện (Preconditions)

- [x] Frontend Vite đang chạy tại `http://localhost:5173`.
- [x] Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [x] Cơ sở dữ liệu MySQL đã áp dụng đủ các bản migration Flyway (tối thiểu V145).
- [x] Dịch vụ Firebase Authentication sẵn sàng nhận lệnh tạo người dùng bằng Email/Password.
- [x] Browser 1 đã được mở tại `http://localhost:5173`.

---

## 2. Đánh giá Đa chiều qua 2 Góc nhìn (2 POVs Evaluation)

### POV 1 — Khách hàng Hội viên (Non-tech Member Experience):
- **Cảm xúc & Thị giác**: Giao diện mang phong cách xưởng kim hoàn cao cấp (Atelier Light), hình ảnh sản phẩm sáng sủa, không bị vỡ font chữ tiếng Việt, không bị tình trạng câu từ "nửa nạc nửa mỡ" khi chuyển đổi ngôn ngữ.
- **Tiện ích trên điện thoại**: Khi cầm điện thoại lướt xem nhẫn, thanh menu thu gọn thành nút 3 gạch (Hamburger) nhạy bén; form đăng ký không bị bàn phím ảo che mất nút "Tạo tài khoản".
- **Nhận thức & Thao tác**: AI Chatbox phản hồi thông minh, ngắn gọn, không giải thích dài dòng như robot; form nhập địa chỉ có sẵn gợi ý tỉnh thành giúp khách không phải tự gõ thủ công.

### POV 2 — Kỹ thuật viên Kiểm định (Senior QA Technical Verification):
- **Bảo mật & Route Guard**: Đảm bảo tuân thủ bất biến tại `spec-general.md#§2`: Khách chưa đăng nhập cố tình gõ thẳng `/profile` hoặc `/profile/addresses` trên thanh địa chỉ phải bị `SessionNavigationBoundary` chặn đứng và chuyển hướng về `/login` bằng phương thức `replace` (không lưu lịch sử để Back quay lại).
- **Quy chuẩn Backend Modulith**: Sau khi Firebase cấp UID, API `/api/v1/accounts/me` phải bootstrap đúng vai trò `MEMBER` với trạng thái `ACTIVE`.
- **Điều hướng Chuẩn FR-003**: Sau khi đăng nhập/đăng ký thành công, hệ thống **bắt buộc điều hướng về Trang chủ (`/`)**, tuyệt đối không tự ý đẩy vào trang profile hay dashboard nội bộ.

---

## 3. Các bước Thực thi trên Trình duyệt (Browser 1 — Action Steps)

### Bước 1: Khám phá Trang chủ trên Laptop (1280x800) & Kiểm tra Song ngữ VI/EN
```browser
# Thiết lập màn hình Laptop tiêu chuẩn
set_viewport 1280 800
goto http://localhost:5173/
wait getByRole('banner')
wait getByRole('heading', { name: /Mland Atelier|Xưởng Chế Tác Nhẫn/i })

# Chụp ảnh chứng thực giao diện Laptop
screenshot --path ppswbs-spec/features/001-account-management/test-results/screenshots/P1-01--laptop-home-vi.jpg

# Kiểm tra chuyển đổi ngôn ngữ sang Tiếng Anh
click getByRole('button', { name: /EN|English/i })
wait getByText(/Handcrafted Rings|Workshop Experience/i)
screenshot --path ppswbs-spec/features/001-account-management/test-results/screenshots/P1-02--laptop-home-en.jpg

# Đổi lại Tiếng Việt để tiếp tục hành trình
click getByRole('button', { name: /VI|Tiếng Việt/i })
wait getByText(/Khám phá bộ sưu tập/i)
```

### Bước 2: Kiểm tra Đáp ứng Giao diện trên Điện thoại Di động (Smartphone 390x844)
```browser
# Chuyển sang kích thước màn hình iPhone 14
set_viewport 390 844
goto http://localhost:5173/
wait 2000

# Xác minh thanh điều hướng co về nút Hamburger và không bị cuộn ngang
find getByRole('button', { name: /menu|danh mục/i })
screenshot --path ppswbs-spec/features/001-account-management/test-results/screenshots/P1-03--mobile-home-viewport.jpg

# Mở menu trên mobile
click getByRole('button', { name: /menu|danh mục/i })
wait getByRole('link', { name: /Đăng nhập|Tài khoản/i })

# Trở lại màn hình Laptop để thực hiện các luồng thao tác chính
set_viewport 1280 800
```

### Bước 3: Tương tác với AI Chatbox Tư vấn Nhẫn & Workshop
```browser
# Tìm và mở widget AI Chatbox ở góc dưới màn hình
click getByRole('button', { name: /AI Tư vấn|Chatbox/i })
wait getByRole('dialog', { name: /Trợ lý AI Mland/i })

# Gửi câu hỏi tư vấn thực tế
fill getByPlaceholder(/Nhập câu hỏi của bạn/i) "Tôi muốn tìm hiểu khóa học làm nhẫn đôi cho 2 người vào cuối tuần, ngân sách khoảng 1 đến 2 triệu"
click getByRole('button', { name: /Gửi|Send/i })
wait 4000

# Kiểm tra câu trả lời của AI: súc tích, hiển thị gợi ý gói Workshop và nút chuyển tới Workshop
find /gói workshop|nhẫn đôi/i
screenshot --path ppswbs-spec/features/001-account-management/test-results/screenshots/P1-04--ai-chatbox-response.jpg
click getByRole('button', { name: /Đóng chat/i })
```

### Bước 4: Thử thách Đối nghịch (Adversarial Step 1) — Chặn Truy cập Trái phép & Validation Mật khẩu Yếu
```browser
# 1. Thử truy cập thẳng trang cá nhân khi chưa đăng nhập
goto http://localhost:5173/profile
wait 1500
get url
# Kỳ vọng: Bị route guard chặn, điều hướng về http://localhost:5173/login

# 2. Chuyển sang form Đăng ký tài khoản
click getByRole('link', { name: /Đăng ký tài khoản|Tạo tài khoản/i })
wait getByRole('heading', { name: /Đăng ký|Tạo tài khoản mới/i })

# 3. Cố tình nhập dữ liệu sai quy chuẩn: email thiếu @ và mật khẩu quá yếu (123)
fill getByLabel(/Địa chỉ email/i) "invalid-email-format"
fill getByLabel(/^Mật khẩu/i) "123"
fill getByLabel(/Xác nhận mật khẩu/i) "123"
click getByRole('button', { name: /Đăng ký|Tạo tài khoản/i })
wait 1000

# Xác minh các câu thông báo lỗi hiển thị ngay dưới trường nhập liệu
find /Email không hợp lệ/i
find /Mật khẩu phải chứa ít nhất 8 ký tự/i
screenshot --path ppswbs-spec/features/001-account-management/test-results/screenshots/P1-05--adversarial-signup-validation.jpg
```

### Bước 5: Phục hồi & Đăng ký Thành công Tài khoản Member Thực tế
```browser
# Điền thông tin chuẩn xác
fill getByLabel(/Họ và tên/i) "Nguyễn Thị Phương Uyên"
fill getByLabel(/Địa chỉ email/i) "e2e_member_{timestamp}@mland.test"
fill getByLabel(/^Mật khẩu/i) "MemberSecure@2026"
fill getByLabel(/Xác nhận mật khẩu/i) "MemberSecure@2026"

# Bấm Đăng ký
click getByRole('button', { name: /Đăng ký|Tạo tài khoản/i })
wait 4000

# Xác minh điều hướng chuẩn về Trang chủ (spec.md#FR-003)
get url
# Kỳ vọng: http://localhost:5173/
find /Xin chào|Phương Uyên/i
screenshot --path ppswbs-spec/features/001-account-management/test-results/screenshots/P1-06--signup-success-home.jpg
```

### Bước 6: Cập nhật Hồ sơ Cá nhân & Tạo Sổ Địa chỉ Nhận hàng Mặc định
```browser
# Mở trang Hồ sơ cá nhân
click getByRole('link', { name: /Hồ sơ|Tài khoản/i })
wait getByRole('heading', { name: /Hồ sơ cá nhân/i })

# Cập nhật số điện thoại chuẩn Việt Nam (10 chữ số)
fill getByLabel(/Số điện thoại/i) "0912345678"
click getByRole('button', { name: /Lưu thay đổi|Cập nhật/i })
wait 2000
find /Cập nhật thông tin thành công/i

# Chuyển sang tab Sổ địa chỉ
click getByRole('link', { name: /Sổ địa chỉ|Địa chỉ giao hàng/i })
wait getByRole('heading', { name: /Sổ địa chỉ/i })
click getByRole('button', { name: /Thêm địa chỉ mới/i })

# Nhập thông tin địa chỉ giao hàng mẫu
fill getByLabel(/Họ tên người nhận/i) "Nguyễn Thị Phương Uyên"
fill getByLabel(/Số điện thoại nhận hàng/i) "0912345678"
fill getByLabel(/Tỉnh\/Thành phố/i) "Hà Nội"
fill getByLabel(/Quận\/Huyện/i) "Hoàn Kiếm"
fill getByLabel(/Phường\/Xã/i) "Hàng Bạc"
fill getByLabel(/Địa chỉ chi tiết/i) "Số 28 Phố Hàng Bạc"
click getByLabel(/Đặt làm địa chỉ mặc định/i)

click getByRole('button', { name: /Lưu địa chỉ/i })
wait 2000

# Xác minh địa chỉ đã hiển thị với nhãn "Mặc định"
find /Số 28 Phố Hàng Bạc, Phường Hàng Bạc, Quận Hoàn Kiếm, Hà Nội/i
find /Mặc định/i
screenshot --path ppswbs-spec/features/001-account-management/test-results/screenshots/P1-07--address-book-created.jpg
```

---

## 4. Kết quả Kỳ vọng theo 5 Trụ Cột (Strict Expected Assertions)

Mọi bước trong Pha 1 phải thỏa mãn trọn vẹn 5 trụ cột kiểm định sau:

### Trụ cột 1: Bố cục & Tính Toàn vẹn Dữ liệu (Layout & Content)
- [x] URL chuyển hướng chính xác: Mở `/profile` khi chưa đăng nhập bị đổi URL thành `http://localhost:5173/login`.
- [x] Sau khi đăng ký, URL đích là `http://localhost:5173/`, thanh Header hiển thị tên "Phương Uyên" và email tương ứng.
- [x] Không xuất hiện bất kỳ giá trị hiển thị rác nào như `undefined`, `null`, hay `NaN` trên thanh tài khoản hoặc trong bảng địa chỉ.

### Trụ cột 2: Chống Che khuất & Tràn vỡ Giao diện (Visual Clipping)
- [x] Header cố định (Sticky Topbar) có độ trong suốt trang nhã, không đè lên tiêu đề trang chủ hay form đăng nhập.
- [x] Khối địa chỉ hiển thị gọn gàng trong card, không bị thanh cuộn ngang xuất hiện ở đáy trình duyệt (`document.body.scrollWidth === window.innerWidth`).

### Trụ cột 3: Đa thiết bị Laptop & Smartphone (Multi-Viewport)
- [x] **Laptop (1280px)**: Danh mục sản phẩm hiển thị dạng lưới 3-4 cột cân đối, thanh điều hướng trải ngang đầy đủ các mục.
- [x] **Mobile (390px)**: Thanh điều hướng co gọn vào nút Menu Hamburger; form đăng ký có độ rộng 100% lề an toàn 16px, nút "Tạo tài khoản" có chiều cao tối thiểu 44px dễ dàng bấm bằng ngón tay cái.

### Trụ cột 4: Cỡ chữ & Song ngữ Việt - Anh (Typography & i18n)
- [x] Tiêu đề trang hiển thị font serif thanh lịch, cỡ chữ body từ 14px - 16px sắc nét, đạt tỷ lệ tương phản màu tối thiểu 4.5:1.
- [x] Khi bấm đổi sang `EN`, 100% nhãn form chuyển sang tiếng Anh ("Full Name", "Email Address", "Password"); khi đổi về `VI` hiển thị đúng tiếng Việt có dấu, không lỗi encoding UTF-8.

### Trụ cột 5: Tối ưu Thao tác & Tải Nhận thức (Ergonomics & Cognitive Load)
- [x] Khách chỉ mất đúng 2 bước để chuyển từ xem thông tin sang form đăng ký.
- [x] Khi nhập form sai mật khẩu, thông báo lỗi chỉ ra rõ ràng điều kiện còn thiếu (ít nhất 8 ký tự), giúp khách biết chính xác cần sửa gì mà không phải đoán mò.

---

## 5. Dữ liệu Bàn giao sang Pha 2 (Data Handoff to Phase 2)

| Khóa dữ liệu | Giá trị thực tế sinh ra | Mục đích sử dụng ở Pha 2 |
| :--- | :--- | :--- |
| `MEMBER_EMAIL` | `e2e_member_{timestamp}@mland.test` | Dùng để đăng nhập hoặc duy trì phiên đặt lịch Workshop. |
| `MEMBER_PASSWORD` | `MemberSecure@2026` | Dùng để đăng nhập lại nếu cần thiết. |
| `MEMBER_NAME` | Nguyễn Thị Phương Uyên | Dùng để đối chiếu thông tin người tham gia trên vé QR tại xưởng. |
| `SHIPPING_ADDRESS_ID` | Địa chỉ: 28 Hàng Bạc, Hoàn Kiếm, Hà Nội | Bàn giao sang Pha 3 để tự động điền địa chỉ giao hàng bán lẻ. |
