---
id: JOURNEY-PHASE-2
feature: 003-workshop-management
journey_phase: 2
test_level: master_e2e
test_case_type: Dual-Actor-Interactive
source_ref: spec.md#003.1,003.2,003.3; spec-general.md#§2,§3,§4; 012-spec.md; 006-spec.md
target_url: http://localhost:5173/workshops
actor_primary: MEMBER (Browser 1)
actor_secondary: STAFF & MANAGER (Browser 2)
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080
test_data:
  member_email: inherited_from_phase_1
  workshop_location_id: 1
  package_name: "Workshop Chế tác Nhẫn Đôi Tình Nhân"
  vnpay_card_number: "9704198526191432198"
  vnpay_card_holder: "NGUYEN VAN A"
  vnpay_card_issue_date: "07/15"
  vnpay_otp: "123456"
  isolation_key: MASTER-E2E-P2-20261114
cleanup:
  required: false
  method: handoff-to-phase-3
---

# JOURNEY-PHASE-2: Đặt Lịch Workshop, Cấu hình Nhẫn 3D, Cọc VNPay, Điểm danh Check-in & Tất toán Hóa đơn

**Ngày tạo**: 2026-11-14 | **Cập nhật**: 2026-11-14  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Trình duyệt sử dụng**: 
- **Browser 1**: Khách hàng Hội viên (`MEMBER`)
- **Browser 2**: Nhân viên xưởng (`STAFF`) và Quản lý xưởng (`MANAGER`)
**Mục tiêu**: Kiểm thử tương tác hai chiều thời gian thực giữa Khách hàng và Đội ngũ xưởng: từ lúc đặt cọc giữ chỗ 10 phút, cấu hình thông số nhẫn, giải quyết sự cố hủy cọc VNPay, xuất vé QR qua email, quét vé điểm danh tại xưởng, phát sinh phụ phí chế tác, xuất hóa đơn tất toán (Final Bill) và tự động tích lũy điểm thưởng Hội viên.

---

## 1. Tiền điều kiện (Preconditions)

- [x] Tài khoản Member từ **Pha 1** đã sẵn sàng và đang đăng nhập tại **Browser 1**.
- [x] **Browser 2** mở sẵn cửa sổ Ẩn danh tại trang đăng nhập nội bộ `http://localhost:5173/login`.
- [x] Chi nhánh xưởng kim hoàn `workshopLocationId = 1` (Xưởng Mland Hoàn Kiếm) đang hoạt động bình thường (`ACTIVE`).
- [x] Có ca học Workshop còn chỗ trống (Available Slot) trong tương lai.
- [x] Backend kết nối cổng thanh toán VNPay Sandbox phản hồi ổn định.

---

## 2. Đánh giá Đa chiều qua 2 Góc nhìn (2 POVs Evaluation)

### POV 1 — Khách hàng Hội viên (Non-tech Member Experience):
- **Minh bạch tài chính**: Giá gói cơ bản và công thức tính tiền cọc 50% hiển thị trực quan ngay tại bước chọn lịch, không bắt khách phải nhẩm tính.
- **Tiện dụng trên Mobile**: Card chọn khung giờ (Slot) có hiển thị rõ số chỗ còn lại (`Còn 4/8 chỗ`); khi chọn cấu hình nhẫn (`012`), bộ chọn size tay có hướng dẫn đo chu vi ngón tay rõ ràng.
- **Trải nghiệm tại Xưởng**: Đến xưởng chỉ cần giơ điện thoại cho nhân viên quét mã QR trong 2 giây là hoàn tất thủ tục; khi tính tiền phụ phí đính thêm đá, hóa đơn liệt kê rõ từng khoản mục không mập mờ.

### POV 2 — Kỹ thuật viên Kiểm định (Senior QA Technical Verification):
- **Bảo toàn Slot Giữ chỗ (10-minute Capacity Hold)**: Tuân thủ nghiêm ngặt `spec-general.md#§4`: Giữ chỗ tối đa 10 phút; nếu khách hủy giao dịch hoặc quá hạn, slot phải được hoàn trả vào kho chỗ trống, không được gây rò rỉ (capacity leak).
- **Phân định Thẩm quyền Modulith (RBAC Scope)**:
  - Chỉ tài khoản `STAFF` có phân công tại đúng `workshopLocationId = 1` mới được phép quét mã check-in vé của xưởng này.
  - Hóa đơn tất toán Final Bill (`003.3`) phải do tài khoản có thẩm quyền `MANAGER` phê duyệt và phát hành.
- **Tính Toàn vẹn Dữ liệu Tích điểm (Loyalty Integrity)**: Sau khi buổi học chuyển trạng thái `COMPLETED` và thanh toán xong 100%, sự kiện Domain Event phải bắn sang module `loyaltypromotion` để cộng chính xác điểm thưởng vào ví của Member.

---

## 3. Các bước Thực thi trên Trình duyệt (Browser Action Steps)

### Bước 1: [Browser 1 - Member] Chọn Gói Workshop, Chi nhánh & Khung giờ
```browser
# Mở trang danh mục Workshop
goto http://localhost:5173/workshops
wait getByRole('heading', { name: /Trải Nghiệm Workshop Chế Tác/i })

# Chọn gói Workshop đôi
click getByRole('heading', { name: /Workshop Chế tác Nhẫn Đôi Tình Nhân/i })
wait getByRole('heading', { name: /Chi tiết gói Workshop/i })

# Bấm Đặt lịch ngay
click getByRole('button', { name: /Đặt lịch ngay|Book now/i })
wait getByRole('heading', { name: /Chọn chi nhánh & Thời gian/i })

# Chọn chi nhánh Hoàn Kiếm và khung giờ ca chiều
click getByRole('button', { name: /Xưởng Hoàn Kiếm|Chi nhánh 1/i })
click getByRole('button', { name: /14:00 - 17:00/i })

# Xác minh hiển thị số chỗ và giá cọc 50%
find /Còn lại: [1-9] chỗ/i
find /Tiền cọc cần thanh toán: 500.000 VNĐ/i
screenshot --path ppswbs-spec/features/003-workshop-management/test-results/screenshots/P2-01--slot-selected.jpg
click getByRole('button', { name: /Tiếp tục cấu hình nhẫn/i })
```

### Bước 2: [Browser 1 - Member] Cấu hình Thông số Nhẫn 3D (Feature 012)
```browser
wait getByRole('heading', { name: /Cấu hình thông số nhẫn chế tác/i })

# Cấu hình Nhẫn 1: Size 14, Bạc 925
click getByRole('tab', { name: /Chiếc 1/i })
selectOption getByLabel(/Chất liệu/i) "Bạc Ý 925"
fill getByLabel(/Kích cỡ tay|Size nhẫn/i) "14"

# Cấu hình Nhẫn 2: Size 18, Khắc laser tên
click getByRole('tab', { name: /Chiếc 2/i })
selectOption getByLabel(/Chất liệu/i) "Bạc Ý 925"
fill getByLabel(/Kích cỡ tay|Size nhẫn/i) "18"
fill getByLabel(/Nội dung khắc laser/i) "M & L 2026"

# Xác minh bảng tóm tắt chi phí
find /Gói cơ bản: 1.000.000 VNĐ/i
find /Đặt cọc trước \(50%\): 500.000 VNĐ/i
screenshot --path ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/P2-02--ring-config-summary.jpg

# Bấm Tiến hành đặt cọc
click getByRole('button', { name: /Xác nhận & Đặt cọc VNPay/i })
wait 3000
```

### Bước 3: [Browser 1 - Member] Thử thách Đối nghịch (Adversarial Step 2) — Hủy Thanh toán VNPay & Phục hồi
```browser
# Trình duyệt chuyển hướng sang cổng VNPay Sandbox
wait 3000
get url
# Kiểm tra URL chứa vnpay.vn hoặc sandbox

# 🛑 Thao tác Đối nghịch: Khách bấm nút HỦY GIAO DỊCH trên cổng VNPay
click getByRole('button', { name: /Hủy|Quay lại/i })
wait 3000

# Trình duyệt quay về Mland với trạng thái Hủy an toàn
get url
# Kỳ vọng: http://localhost:5173/workshops/booking-cancel hoặc hiển thị thông báo hủy
find /Giao dịch đặt cọc chưa hoàn tất|Bạn đã hủy thanh toán/i
screenshot --path ppswbs-spec/features/003-workshop-management/test-results/screenshots/P2-03--vnpay-cancelled-safe.jpg

# Phục hồi: Bấm đặt lại và tiến hành thanh toán thật
click getByRole('button', { name: /Thử lại thanh toán|Đặt lại ca này/i })
wait 2000
click getByRole('button', { name: /Xác nhận & Đặt cọc VNPay/i })
wait 3000

# Nhập thẻ kiểm thử VNPay Sandbox (NCB)
fill getByPlaceholder(/Số thẻ/i) "9704198526191432198"
fill getByPlaceholder(/Tên chủ thẻ/i) "NGUYEN VAN A"
fill getByPlaceholder(/Ngày phát hành/i) "07/15"
click getByRole('button', { name: /Tiếp tục|Thanh toán/i })
wait 2000

# Nhập OTP kiểm thử
fill getByPlaceholder(/OTP/i) "123456"
click getByRole('button', { name: /Xác nhận/i })
wait 4000

# Xác minh thanh toán cọc thành công, hiển thị trang Đặt chỗ thành công
get url
find /Đặt lịch thành công|Đã xác nhận cọc/i
find /Mã vé: TKT-/i
screenshot --path ppswbs-spec/features/003-workshop-management/test-results/screenshots/P2-04--booking-confirmed-ticket.jpg
```

### Bước 4: [Browser 2 - Staff] Đăng nhập & Quét Mã QR Check-in tại Xưởng (Feature 003.2)
```browser
# Mở Browser 2: Đăng nhập tài khoản Staff tại chi nhánh Hoàn Kiếm
goto http://localhost:5173/login
fill getByLabel(/Địa chỉ email/i) "staff_hoankiem@mland.test"
fill getByLabel(/^Mật khẩu/i) "StaffPass@2026"
click getByRole('button', { name: /Đăng nhập/i })
wait 2500

# Vào trang Điều hành Workshop tại xưởng
goto http://localhost:5173/operator/workshop-operations
wait getByRole('heading', { name: /Điều hành Ca học & Check-in/i })

# 🛑 Thử thách Đối nghịch: Quét thử mã vé giả mạo
fill getByPlaceholder(/Nhập mã vé hoặc quét QR/i) "TKT-FAKE-99999"
click getByRole('button', { name: /Kiểm tra vé/i })
wait 1000
find /Vé không tồn tại hoặc không thuộc ca học này/i
screenshot --path ppswbs-spec/features/003-workshop-management/test-results/screenshots/P2-05--staff-invalid-ticket.jpg

# Quét mã vé THẬT vừa sinh ra từ Browser 1
fill getByPlaceholder(/Nhập mã vé hoặc quét QR/i) "<Mã vé TKT lấy từ Browser 1>"
click getByRole('button', { name: /Kiểm tra vé/i })
wait 1500

# Xác minh hiển thị học viên Nguyễn Thị Phương Uyên và cấu hình nhẫn (Size 14, 18)
find /Nguyễn Thị Phương Uyên/i
find /Size 14/i
find /M & L 2026/i

# Bấm Check-in học viên vào bàn làm việc
click getByRole('button', { name: /Xác nhận Check-in/i })
wait 1500
find /Trạng thái: ĐÃ ĐIỂM DANH|CHECKED_IN/i
screenshot --path ppswbs-spec/features/003-workshop-management/test-results/screenshots/P2-06--staff-checkin-success.jpg
```

### Bước 5: [Browser 2 - Manager] Xuất Hóa đơn Tất toán Phụ phí Phát sinh (Feature 003.3)
```browser
# Chuyển quyền đăng nhập sang Manager (hoặc Manager đăng nhập trên Browser 2)
goto http://localhost:5173/operator/workshop-settlement
wait getByRole('heading', { name: /Tất toán & Hóa đơn Workshop/i })

# Tìm ca học của học viên Phương Uyên
click getByText(/Nguyễn Thị Phương Uyên/i)
wait getByRole('heading', { name: /Chi tiết tất toán/i })

# Thêm phụ phí: Học viên nâng cấp đính 1 viên đá Cubic Zirconia lớn
click getByRole('button', { name: /Thêm phụ phí chế tác/i })
fill getByLabel(/Mô tả phụ phí/i) "Đính thêm đá Cubic Zirconia 3.5mm"
fill getByLabel(/Số tiền/i) "150000"
click getByRole('button', { name: /Áp dụng/i })

# Xác minh tổng tiền hóa đơn Final Bill: 500.000đ (còn lại) + 150.000đ (đá) = 650.000 VNĐ
find /Tổng tiền tất toán: 650.000 VNĐ/i
click getByRole('button', { name: /Phát hành Hóa đơn Tất toán/i })
wait 2000
find /Hóa đơn đã phát hành/i
screenshot --path ppswbs-spec/features/003-workshop-management/test-results/screenshots/P2-07--manager-final-bill-issued.jpg
```

### Bước 6: [Browser 1 - Member] Thanh toán Tất toán VNPay & Nhận Điểm thưởng Loyalty
```browser
# Quay lại Browser 1: Member mở trang Chi tiết đơn đặt chỗ
goto http://localhost:5173/my-workshops
wait getByText(/Hóa đơn tất toán đã sẵn sàng/i)
click getByRole('button', { name: /Xem hóa đơn & Thanh toán/i })

# Kiểm tra phân rã hóa đơn minh bạch: Cọc đã trả 500k, Còn lại 500k, Phụ phí 150k -> Cần trả 650k
find /650.000 VNĐ/i

# Bấm Thanh toán tất toán qua VNPay
click getByRole('button', { name: /Thanh toán 650.000 VNĐ qua VNPay/i })
wait 3000

# Nhập thẻ NCB test thanh toán nốt
fill getByPlaceholder(/Số thẻ/i) "9704198526191432198"
fill getByPlaceholder(/Tên chủ thẻ/i) "NGUYEN VAN A"
fill getByPlaceholder(/Ngày phát hành/i) "07/15"
click getByRole('button', { name: /Tiếp tục/i })
wait 1500
fill getByPlaceholder(/OTP/i) "123456"
click getByRole('button', { name: /Xác nhận/i })
wait 4000

# Xác minh hoàn tất buổi học thành công
find /Hoàn tất buổi Workshop|Thanh toán thành công/i
find /Bạn đã nhận được: \+50 Điểm Thưởng/i
screenshot --path ppswbs-spec/features/006-loyalty-promotion/test-results/screenshots/P2-08--settlement-paid-loyalty-earned.jpg
```

---

## 4. Kết quả Kỳ vọng theo 5 Trụ Cột (Strict Expected Assertions)

### Trụ cột 1: Bố cục & Tính Toàn vẹn Dữ liệu (Layout & Content)
- [x] Số tiền cọc 500,000đ và tất toán 650,000đ được tính toán chính xác tuyệt đối, khớp dữ liệu với backend Spring Modulith.
- [x] Vé QR chứa đúng mã `TKT-XXXX`, tên người tham gia, chi nhánh Hoàn Kiếm, không hiển thị trường trống.

### Trụ cột 2: Chống Che khuất & Tràn vỡ Giao diện (Visual Clipping)
- [x] Modal cấu hình nhẫn 3D hiển thị cân đối, không làm tràn bảng chọn kích thước ngón tay; nút "Xác nhận & Cọc" luôn nằm trong vùng nhìn thấy được (in-viewport).
- [x] Giao diện điều hành của Staff trên Browser 2 hiển thị bảng danh sách học viên dạng bảng responsive, không bị méo cột.

### Trụ cột 3: Đa thiết bị Laptop & Smartphone (Multi-Viewport)
- [x] Khi Member mở vé QR trên điện thoại (390px), ảnh mã QR căn giữa màn hình, độ phân giải cao, độ sáng màn hình tối ưu để camera máy quét đọc dễ dàng.
- [x] Nút "Thanh toán tất toán" trên di động hiển thị dạng sticky bar cố định dưới cùng.

### Trụ cột 4: Cỡ chữ & Song ngữ Việt - Anh (Typography & i18n)
- [x] Hóa đơn tất toán ghi rõ ràng bằng tiếng Việt có dấu: "Tiền cọc đã thanh toán", "Số dư còn lại", "Phụ phí vật tư phát sinh", "Tổng thanh toán".
- [x] Cỡ chữ số tiền được in đậm, tối thiểu 18px để khách hàng nhìn rõ ràng, không gây nhầm lẫn chữ số 0.

### Trụ cột 5: Tối ưu Thao tác & Tải Nhận thức (Ergonomics & Cognitive Load)
- [x] Khách chỉ mất đúng 3 bước: Chọn ca học ➔ Chọn size nhẫn ➔ Chuyển sang thanh toán VNPay.
- [x] Công thức giá hiển thị tức thời khi đổi chất liệu hoặc thêm phụ phí, khách không phải tự làm tính nhẩm.

---

## 5. Dữ liệu Bàn giao sang Pha 3 (Data Handoff to Phase 3)

| Khóa dữ liệu | Giá trị thực tế sinh ra | Mục đích sử dụng ở Pha 3 |
| :--- | :--- | :--- |
| `LOYALTY_POINTS_BALANCE` | **50 điểm** (tương đương 50.000 VNĐ) | Dùng để cấn trừ trực tiếp khi thanh toán đơn hàng nhẫn bán lẻ ở Pha 3. |
| `WORKSHOP_SESSION_ID` | Mã định danh ca học vừa hoàn tất | Dùng để làm căn cứ cho Member viết Review buổi học ở Pha 5. |
| `MEMBER_EMAIL` | Kế thừa từ Pha 1 | Tiếp tục thực hiện mua sắm giỏ hàng ở Pha 3. |
