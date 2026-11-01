# Mland Operations Hub — UX Prototypes

Kho lưu trữ các bản mẫu giao diện tương tác độc lập (Pure Vanilla HTML5, CSS3, JavaScript ES6) phục vụ việc đánh giá và lựa chọn phong cách thiết kế UI/UX theo tiêu chuẩn `ppswbs-spec/DESIGN.md` và `ppswbs_frontend/AGENT.md`.

Các bản mẫu triển khai kịch bản nghiệp vụ hoàn chỉnh kết hợp giữa:
- **Feature 001 (Account Management & Member Profile)**: Định danh thành viên, trạng thái xác thực email, quản lý địa chỉ giao hàng và thông tin cá nhân.
- **Feature 008 (Retail Order & Fulfillment)**: Chọn chi nhánh duyệt mua trước khi xem catalogue, giỏ hàng đa chi nhánh tự động tách đơn (Marketplace-style split orders), tính phí ship GHTK theo từng kiện, thanh toán gộp 1 lần qua VNPay, theo dõi tiến trình đơn hàng (Placed → Paid → Packing → Dispatched → In Transit → Delivered), và bàn vận hành đóng gói cho nhân viên (Staff fulfillment).

---

## Cấu trúc 3 phong cách giao diện

### 1. Style 1: Atelier Noir — Craft Dark (`style-1-craft-dark/`)
- **Triết lý**: Không gian xưởng kim hoàn thủ công về đêm (Workshop Craft Canvas).
- **Màu sắc & Tokens**: Canvas tối sâu `#121316`, Card Surface `#1E1F24`, Điểm nhấn vàng nghệ thương hiệu `#EEDC35`, Viền tinh gọn `#2E3038`.
- **Typography**: Heading viết hoa, cô đọng bằng phông chữ `Oswald` / `Barlow Condensed`, nội dung dùng `Inter`.
- **Trải nghiệm**: Tạo cảm giác bí ẩn, sang trọng, tập trung tối đa sự chú ý của khách hàng vào các món trang sức lấp lánh và nút kêu gọi hành động (CTA).

### 2. Style 2: Boutique Épurée — Atelier Light (`style-2-boutique-light/`)
- **Triết lý**: Showroom trang sức đương đại với ánh sáng tự nhiên tinh tế.
- **Màu sắc & Tokens**: Nền sứ `#F8F9FA` / `#FFFFFF`, Card Surface `#FFFFFF`, Viền xám ấm `#E5E7EB`, Điểm nhấn vàng nghệ `#EEDC35` và than chì `#1A1A1A`.
- **Typography**: Heading `Oswald` thanh thoát kết hợp `Inter` với khoảng thở rộng rãi.
- **Trải nghiệm**: Thanh lịch, thoáng đãng, mang lại cảm giác mua sắm nhẹ nhàng, cao cấp và đạt chuẩn tương phản cao WCAG AA.

### 3. Style 3: Operations Hub — High-Density Console (`style-3-dense-console/`)
- **Triết lý**: Bàn điều khiển vận hành chuyên nghiệp (lấy cảm hứng từ Firebase Console & Developer Portal được định hướng trong `DESIGN.md`).
- **Màu sắc & Tokens**: Thanh điều hướng Sidebar Slate `#0F172A`, Nền dữ liệu `#F1F5F9`, Bảng dữ liệu viền mịn `#E2E8F0`, Mã vận đơn/SKU dùng phông đơn cách `JetBrains Mono`.
- **Typography**: `Inter` được tối ưu cho bảng biểu mật độ cao và nhãn trạng thái trực quan.
- **Trải nghiệm**: Tối ưu hóa tốc độ đóng gói và kiểm soát đơn hàng cho nhân viên Staff và Quản lý trên màn hình làm việc 1366×768.

---

## Hướng dẫn trải nghiệm & Đánh giá

Mở trực tiếp file `prototypes/index.html` trên trình duyệt web bất kỳ. Trang Hub trung tâm sẽ cung cấp:
1. Thẻ giới thiệu và nút mở trực tiếp từng bản mẫu.
2. Bảng ma trận so sánh các tiêu chí thiết kế, màu sắc, typography và mục tiêu trải nghiệm.
3. Trong mỗi prototype:
   - Chuyển đổi giữa góc nhìn **Khách hàng** và **Nhân viên (Staff)**.
   - Chuyển đổi ngôn ngữ tức thì giữa **Tiếng Việt (Mặc định)** và **English**.
   - Dữ liệu giỏ hàng, hồ sơ và trạng thái đóng gói đơn hàng được lưu trữ và cập nhật qua `localStorage`.
