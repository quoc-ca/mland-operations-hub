---
id: JOURNEY-PHASE-3
feature: 008-retail-order-and-fulfillment
journey_phase: 3
test_level: master_e2e
test_case_type: Dual-Actor-Interactive
source_ref: spec.md#FE-08; 002-spec.md; 006-spec.md; spec-general.md#§2,§3
target_url: http://localhost:5173/products
actor_primary: MEMBER (Browser 1)
actor_secondary: STAFF (Browser 2)
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080
test_data:
  member_email: inherited_from_phase_1
  shipping_address: inherited_from_phase_1
  loyalty_points_available: 50
  product_name: "Nhẫn Bạc Nữ Đính Đá Solitaire"
  product_price: 1200000
  voucher_code: "MLANDWELCOME"
  voucher_discount: 50000
  vnpay_card_number: "9704198526191432198"
  vnpay_card_holder: "NGUYEN VAN A"
  vnpay_card_issue_date: "07/15"
  vnpay_otp: "123456"
  isolation_key: MASTER-E2E-P3-20261114
cleanup:
  required: false
  method: handoff-to-phase-4-and-5
---

# JOURNEY-PHASE-3: Mua sắm Bán lẻ, Áp dụng Ưu đãi & Tiêu Điểm Loyalty, Thanh toán VNPay & Bàn giao Đơn vị Vận chuyển

**Ngày tạo**: 2026-11-14 | **Cập nhật**: 2026-11-14  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Trình duyệt sử dụng**: 
- **Browser 1**: Khách hàng Hội viên (`MEMBER`)
- **Browser 2**: Nhân viên vận hành xưởng (`STAFF`)
**Mục tiêu**: Kiểm chứng toàn diện luồng thương mại điện tử bán lẻ dành cho Hội viên: Từ việc chọn nhẫn có sẵn trong catalogue, tự động nạp sổ địa chỉ giao hàng từ Pha 1, kiểm thử xử lý mã voucher giả và tiêu điểm loyalty vượt hạn mức, minh bạch công thức khấu trừ tài chính, thanh toán VNPay 100%, trừ tồn kho và Staff tại xưởng đóng gói bàn giao mã vận đơn GHTK.

---

## 1. Tiền điều kiện (Preconditions)

- [x] Tài khoản Member đã hoàn tất Workshop ở **Pha 2** và có **50 Điểm Loyalty** trong ví điểm (`006`).
- [x] Sổ địa chỉ nhận hàng (Hà Nội) từ **Pha 1** đã được lưu trong tài khoản.
- [x] Sản phẩm "Nhẫn Bạc Nữ Đính Đá Solitaire" (Size 12) có trạng thái `PUBLISHED` và tồn kho khả dụng (`ATS > 0`).
- [x] Mã Voucher `MLANDWELCOME` (giảm 50,000 VNĐ cho đơn từ 1,000,000 VNĐ) đang hoạt động.
- [x] **Browser 2** đăng nhập sẵn tài khoản `STAFF` tại xưởng.

---

## 2. Đánh giá Đa chiều qua 2 Góc nhìn (2 POVs Evaluation)

### POV 1 — Khách hàng Hội viên (Non-tech Member Experience):
- **Cảm giác được tưởng thưởng**: Bước vào trang thanh toán thấy 50 điểm tích lũy vừa được cộng từ buổi workshop hôm trước có thể quy đổi ngay thành 50,000 VNĐ giảm giá, tạo cảm giác gắn bó với thương hiệu.
- **Tính toán cực kỳ trực quan**: Bảng tổng kết đơn hàng phân tách 4 dòng rõ ràng: Giá gốc ➔ Giảm voucher ➔ Giảm điểm thưởng ➔ Tổng cần thanh toán. Khách thấy rõ dòng thông báo màu xanh lá: *"Bạn đã tiết kiệm được 100.000 VNĐ!"*.
- **Tiện lợi trên Smartphone**: Nút bấm "Thanh toán ngay" luôn nổi ở mép dưới màn hình (Sticky Bottom Bar), không cần phải cuộn hết trang mới tìm thấy nút thanh toán.

### POV 2 — Kỹ thuật viên Kiểm định (Senior QA Technical Verification):
- **Bảo toàn Tồn kho & Snapshot Giá (Cart & Pricing Guard)**: Tuân thủ `spec-general.md#§3`: Toàn bộ giỏ hàng được revalidate và snapshot giá cố định tại thời điểm checkout; giữ tồn kho tối đa 15 phút.
- **Ràng buộc Ví điểm (Loyalty Ledger Constraint)**: Module `loyaltypromotion` phải kiểm tra số dư tại server-side; nghiêm cấm việc client chỉnh sửa payload để tiêu số điểm lớn hơn số dư hiện có trong DB.
- **Quy trình GHTK Thủ công**: Tuân thủ thỏa thuận `spec-general.md#§2`: V1 không tích hợp API tự động với hãng vận chuyển GHTK; Staff xưởng ghi nhận mã vận đơn thủ công và hệ thống cập nhật trạng thái đơn hàng sang `SHIPPED`.

---

## 3. Các bước Thực thi trên Trình duyệt (Browser Action Steps)

### Bước 1: [Browser 1 - Member] Tìm Nhẫn Bán lẻ & Thêm vào Giỏ hàng (Feature 002 & 008)
```browser
# Mở trang danh mục sản phẩm bán lẻ
goto http://localhost:5173/products
wait getByRole('heading', { name: /Bộ Sưu Tập Nhẫn Cưới & Nhẫn Bạc/i })

# Lọc theo danh mục Nhẫn đính đá
click getByRole('button', { name: /Nhẫn Nữ|Nhẫn Đính Đá/i })
wait 1500

# Chọn sản phẩm "Nhẫn Bạc Nữ Đính Đá Solitaire"
click getByRole('heading', { name: /Nhẫn Bạc Nữ Đính Đá Solitaire/i })
wait getByRole('heading', { name: /Nhẫn Bạc Nữ Đính Đá Solitaire/i })

# Xác minh thông tin hiển thị đầy đủ: Giá 1.200.000 VNĐ, chất liệu Bạc 925
find /1.200.000 VNĐ/i
find /Bạc Ý 925 cao cấp/i

# Chọn Size 12
selectOption getByLabel(/Kích cỡ|Size/i) "12"

# Bấm Thêm vào giỏ hàng
click getByRole('button', { name: /Thêm vào giỏ hàng/i })
wait 1500
find /Đã thêm sản phẩm vào giỏ hàng/i
screenshot --path ppswbs-spec/features/008-retail-order-and-fulfillment/test-results/screenshots/P3-01--added-to-cart.jpg

# Mở Giỏ hàng
click getByRole('link', { name: /Giỏ hàng/i })
wait getByRole('heading', { name: /Giỏ hàng của bạn/i })
click getByRole('button', { name: /Tiến hành đặt hàng/i })
```

### Bước 2: [Browser 1 - Member] Thử thách Đối nghịch (Adversarial Step 3) — Bẫy Voucher & Tiêu Điểm Vượt Mức
```browser
# Vào trang Thanh toán
wait getByRole('heading', { name: /Xác nhận đơn hàng & Thanh toán/i })

# Xác minh địa chỉ giao hàng mặc định từ Pha 1 đã được tự động điền sẵn
find /Số 28 Phố Hàng Bạc, Phường Hàng Bạc, Quận Hoàn Kiếm, Hà Nội/i

# 🛑 Thử thách 1: Nhập Voucher không tồn tại
fill getByPlaceholder(/Nhập mã ưu đãi/i) "VOUCHER_FAKE_2026"
click getByRole('button', { name: /Áp dụng voucher/i })
wait 1000
find /Mã ưu đãi không tồn tại hoặc đã hết hạn/i
screenshot --path ppswbs-spec/features/006-loyalty-promotion/test-results/screenshots/P3-02--fake-voucher-rejected.jpg

# 🛑 Thử thách 2: Cố tình nhập số điểm Loyalty vượt số dư hiện có (Nhập 9999 trong khi ví có 50 điểm)
fill getByLabel(/Nhập số điểm muốn dùng/i) "9999"
wait 500
# Xác minh cảnh báo tức thì ngay dưới ô nhập
find /Số điểm sử dụng không được vượt quá số dư hiện có \(50 điểm\)/i
screenshot --path ppswbs-spec/features/006-loyalty-promotion/test-results/screenshots/P3-03--loyalty-overflow-blocked.jpg
```

### Bước 3: [Browser 1 - Member] Phục hồi & Áp dụng Ưu đãi Hợp lệ, Thanh toán VNPay
```browser
# 1. Nhập Voucher hợp lệ "MLANDWELCOME"
fill getByPlaceholder(/Nhập mã ưu đãi/i) "MLANDWELCOME"
click getByRole('button', { name: /Áp dụng voucher/i })
wait 1500
find /Áp dụng mã MLANDWELCOME thành công: -50.000 VNĐ/i

# 2. Chọn tiêu toàn bộ 50 Điểm Loyalty hợp lệ
fill getByLabel(/Nhập số điểm muốn dùng/i) "50"
wait 1000
find /Quy đổi điểm thưởng: -50.000 VNĐ/i

# 3. Kiểm tra Bảng tổng hợp tài chính minh bạch
find /Tạm tính: 1.200.000 VNĐ/i
find /Mã giảm giá: -50.000 VNĐ/i
find /Điểm tích lũy: -50.000 VNĐ/i
find /Phí vận chuyển: Miễn phí/i
find /Tổng thanh toán: 1.100.000 VNĐ/i
find /Bạn đã tiết kiệm được: 100.000 VNĐ/i
screenshot --path ppswbs-spec/features/008-retail-order-and-fulfillment/test-results/screenshots/P3-04--checkout-summary-transparent.jpg

# Bấm Thanh toán VNPay
click getByRole('button', { name: /Thanh toán 1.100.000 VNĐ qua VNPay/i })
wait 3000

# Điền thẻ NCB test tại cổng VNPay Sandbox
fill getByPlaceholder(/Số thẻ/i) "9704198526191432198"
fill getByPlaceholder(/Tên chủ thẻ/i) "NGUYEN VAN A"
fill getByPlaceholder(/Ngày phát hành/i) "07/15"
click getByRole('button', { name: /Tiếp tục/i })
wait 1500
fill getByPlaceholder(/OTP/i) "123456"
click getByRole('button', { name: /Xác nhận/i })
wait 4000

# Xác minh đơn hàng được tạo thành công với mã ORD-XXXX
get url
find /Đặt hàng thành công/i
find /Mã đơn hàng: ORD-/i
screenshot --path ppswbs-spec/features/008-retail-order-and-fulfillment/test-results/screenshots/P3-05--retail-order-placed.jpg
```

### Bước 4: [Browser 2 - Staff] Tiếp nhận Đơn hàng, Đóng gói & Bàn giao GHTK
```browser
# Mở Browser 2: Vào bảng điều khiển quản lý đơn hàng bán lẻ
goto http://localhost:5173/operator/retail-fulfillment
wait getByRole('heading', { name: /Quản lý Đơn hàng Bán lẻ & Đóng gói/i })

# Tìm đơn hàng ORD-XXXX vừa tạo của khách hàng Phương Uyên
click getByText(/Nguyễn Thị Phương Uyên/i)
wait getByRole('heading', { name: /Chi tiết đơn hàng/i })

# Xác minh thông tin đơn: Nhẫn Bạc Nữ Đính Đá Solitaire, Size 12, Đã thanh toán 1.100.000 VNĐ
find /ĐÃ THANH TOÁN|PAID/i
find /Nhẫn Bạc Nữ Đính Đá Solitaire/i
find /Size 12/i

# Thao tác Đóng gói & Bàn giao GHTK thủ công
click getByRole('button', { name: /Bắt đầu chuẩn bị hàng/i })
wait 1000
click getByRole('button', { name: /Bàn giao cho đơn vị vận chuyển/i })

# Nhập mã vận đơn GHTK thực tế
fill getByLabel(/Mã vận đơn GHTK/i) "GHTK-HN-202611-001"
fill getByLabel(/Ghi chú đóng gói/i) "Hộp nhẫn nhung cao cấp + Khăn lau bạc kèm theo"
click getByRole('button', { name: /Xác nhận bàn giao/i })
wait 2000

# Xác minh đơn hàng chuyển trạng thái ĐANG VẬN CHUYỂN
find /Trạng thái: ĐANG GIAO HÀNG|SHIPPED/i
screenshot --path ppswbs-spec/features/008-retail-order-and-fulfillment/test-results/screenshots/P3-06--staff-dispatched-ghtk.jpg
```

### Bước 5: [Browser 1 - Member] Kiểm tra Lịch sử Đơn hàng & Mã Vận đơn
```browser
# Quay lại Browser 1: Member mở trang Quản lý đơn hàng cá nhân
goto http://localhost:5173/orders
wait getByRole('heading', { name: /Đơn hàng của tôi/i })

# Xác minh hiển thị đơn hàng ORD-XXXX với nhãn Đang giao hàng
find /ORD-/i
find /Đang vận chuyển/i
find /Mã vận đơn GHTK: GHTK-HN-202611-001/i
screenshot --path ppswbs-spec/features/008-retail-order-and-fulfillment/test-results/screenshots/P3-07--member-order-shipped.jpg
```

---

## 4. Kết quả Kỳ vọng theo 5 Trụ Cột (Strict Expected Assertions)

### Trụ cột 1: Bố cục & Tính Toàn vẹn Dữ liệu (Layout & Content)
- [x] Đầy đủ thông số: Tên sản phẩm, size 12, đơn giá, địa chỉ nhận hàng, mã đơn `ORD-XXXX`, mã vận đơn GHTK.
- [x] Số dư Loyalty còn lại sau khi mua hàng trở về `0 điểm` (do đã tiêu hết 50 điểm), không bị âm điểm hoặc hiển thị sai lệch.

### Trụ cột 2: Chống Che khuất & Tràn vỡ Giao diện (Visual Clipping)
- [x] Bảng tóm tắt chi phí (Order Summary) nằm ở cột phải trên màn hình Desktop, không bị nhảy dòng làm méo các con số tài chính.
- [x] Các thông báo lỗi khi nhập sai voucher hiển thị dưới dạng Inline Validation ngay dưới ô nhập, không che khuất nút bấm "Đặt hàng".

### Trụ cột 3: Đa thiết bị Laptop & Smartphone (Multi-Viewport)
- [x] Trên màn hình Smartphone (390px), danh sách sản phẩm trong giỏ hàng hiển thị dạng danh sách cuộn dọc, ảnh sản phẩm vuông 80x80px sắc nét.
- [x] Nút "Thanh toán 1.100.000 VNĐ qua VNPay" neo cố định ở đáy màn hình điện thoại (Sticky bottom bar).

### Trụ cột 4: Cỡ chữ & Song ngữ Việt - Anh (Typography & i18n)
- [x] Cỡ chữ giá tiền in đậm rõ nét, đơn vị tiền tệ chuẩn `VNĐ`.
- [x] Tên voucher và số tiền giảm giá hiển thị với màu sắc tương phản nổi bật (màu xanh ngọc atelier hoặc xanh lá cây), không gây chói mắt.

### Trụ cột 5: Tối ưu Thao tác & Tải Nhận thức (Ergonomics & Cognitive Load)
- [x] Khách không phải gõ lại địa chỉ giao hàng vì hệ thống đã tự động lấy từ Sổ địa chỉ mặc định được tạo ở Pha 1.
- [x] Số tiền tiết kiệm được hiển thị minh bạch 100,000 VNĐ, người mua nhìn vào nắm được ngay quyền lợi mà không cần tự tính.

---

## 5. Dữ liệu Bàn giao sang Pha 4 và Pha 5 (Data Handoff)

| Khóa dữ liệu | Giá trị thực tế sinh ra | Mục đích sử dụng ở Pha 5 |
| :--- | :--- | :--- |
| `RETAIL_PRODUCT_ID` | Sản phẩm "Nhẫn Bạc Nữ Đính Đá Solitaire" | Cung cấp căn cứ hợp lệ để Member viết Review sản phẩm ở Pha 5. |
| `RETAIL_ORDER_ID` | `ORD-XXXX` (Trạng thái `SHIPPED`) | Chứng minh đơn hàng đã hoàn tất giao dịch để đủ điều kiện đánh giá. |
