# BÁO CÁO TOÀN DIỆN VỀ TRẢI NGHIỆM NGƯỜI DÙNG (UX/USABILITY AUDIT LOG)
## ĐÁNH GIÁ 3 KỊCH BẢN MASTER E2E TRÊN MÔI TRƯỜNG LIVE MOCK PROD

- **Hệ thống mục tiêu**: Mland Operations Hub & Atelier Portal (`https://mland-operations-hub-git-develop-mland-team.vercel.app/`)
- **Ngày thực hiện**: 2026-11-17
- **Người thực hiện**: Senior QA & System Verification Engineer (Persona ppswbs-testing/AGENT.md)
- **Phương pháp**: Black-box Observer thông qua công cụ tự động hóa trình duyệt `browsermcp` trên 2 session thực tế:
  - **Browser 1 (Chrome)**: Khách hàng / Thành viên (`minikie456@gmail.com`)
  - **Browser 2 (Edge)**: Nhân viên vận hành Backstage (`staff.test@mland.vn`)

---

## I. KHUNG TIÊU CHÍ ĐÁNH GIÁ TRẢI NGHIỆM CON NGƯỜI (5 HUMAN UX CRITERIA)

Dựa trên phản xạ sinh học và ngưỡng dung sai thực tế của người dùng:
1. **Default Eye Focus (Tiêu điểm mắt mặc định - Chính giữa màn hình)**: Mắt người dùng tự nhiên hướng vào trung tâm màn hình. Các thông tin cốt lõi, CTA dẫn dắt hoặc thông báo lỗi nếu bị đẩy ra góc khuất, bị che lấp bởi thanh sticky, hoặc để lại khoảng trắng mênh mông (Dead Void) đều bị đánh giá là lỗi dẫn đường (Wayfinding Friction).
2. **Response Latency Tolerance (Dung sai độ trễ phản hồi)**:
   - $\le 8$ giây: Chấp nhận được đối với luồng tải mạng/tính phí/tích hợp ngoài.
   - $> 15$ giây: Tự động đánh giá là hệ thống phản hồi kém, xuống cấp (Degraded).
3. **Screen Flickering / Jitter (Độ rung lắc / Chớp giật màn hình)**:
   - Người dùng không chịu được $\ge 3$ lần giật/chớp layout (layout shift, re-render hàng loạt).
4. **Typography & Spacing Density (Mật độ chữ & Khoảng cách thị giác)**:
   - Khối chữ nhỏ ($< 13\text{px}$), quá sát nhau, thiếu khoảng thở (breathing room) gây mỏi mắt và ức chế nhận thức.
5. **Visual Color & Styling Overload (Quá tải màu sắc & Kiểu dáng)**:
   - Màu sắc lòe loẹt, badge tương phản gắt gáp, quá nhiều màu sặc sỡ trên một khung nhìn làm loãng điểm nhấn thị giác.

---

## II. BẢNG TỔNG HỢP KẾT QUẢ THEO 5 TIÊU CHÍ TRẢI NGHIỆM

| Tiêu chí UX | Mức độ Đạt / Chưa đạt | Điểm ma sát nghiêm trọng (Friction Hotspots) | Mức độ ảnh hưởng |
| :--- | :--- | :--- | :--- |
| **1. Eye Focus (Chính giữa màn hình)** | **CẢNH BÁO CAO (CHƯA ỔN)** | - Màn hình `/ai-support` ban đầu để trống hoàn toàn vùng trung tâm, không có gợi ý câu hỏi.<br>- Khi cuộn trang đặt workshop, thanh sticky header `<div.main-topbar-wrapper>` che phủ hoàn toàn nút CTA "Thanh toán qua VNPay".<br>- Trang Giỏ hàng không tự chọn sản phẩm vừa thêm, nút "Mua hàng" bị disable ở góc phải dưới mà không có mũi tên chỉ dẫn. | **HIGH (Gây ức chế & Click nhầm)** |
| **2. Response Latency ($\le 8s$)** | **ĐẠT CHỈ TIÊU ($\le 3s$)** | - AI Chatbot: ~0.66s.<br>- GHTK Shipping Quote: ~2.62s.<br>- VNPay Redirect & Callback: ~3.0s.<br>- Sổ địa chỉ & Giỏ hàng: ~0.5s - 1.2s. | **PASS (Rất mượt mà về tốc độ mạng)** |
| **3. Flickering / Jitter ($< 3$ Fliks)** | **TƯƠNG ĐỐI ỔN** | - Không xuất hiện hiện tượng giật khung hình trắng toàn trang.<br>- Tuy nhiên, form checkout âm thầm bắn hàng chục request `benefit-previews` ngầm gây xung đột dữ liệu quote. | **MEDIUM (Gặp vấn đề đồng bộ ngầm)** |
| **4. Typography & Spacing Density** | **TRUNG BÌNH - KHÁ DÀY** | - Form Sổ địa chỉ thiết kế đẹp nhưng khoá tuần tự (Sequential lock) quá khắt khe.<br>- Màn hình Check-in & Folio của Staff quá dày đặc thuật ngữ kỹ thuật tiếng Anh (`CUSTOMER_DEVICE_UNAVAILABLE`, `RECORDING_ERROR`, `BOOKING_TIME`). | **MEDIUM (Gây quá tải cho nhân viên mới)** |
| **5. Visual Color & Overload** | **TỐT (HÀI HOÀ)** | - Bảng màu đen - trắng - be kim hoàn sang trọng, tối giản, không lòe loẹt, độ tương phản text chuẩn WCAG AA. | **PASS (Thẩm mỹ tốt, dễ nhìn)** |

---

## III. NHẬT KÝ CHI TIẾT THỰC NGHIỆM TỪNG PHASE & CÁC ĐIỂM "SOFT FAIL / UX FRICTION"

### PHASE 1: GUEST DISCOVERY & ONBOARDING

#### 1. Bước 1: Khám phá Trang chủ Desktop & Chuyển ngữ VI/EN
- **Thao tác**: Truy cập `https://mland-operations-hub-git-develop-mland-team.vercel.app/`, quan sát Hero Banner, Navbar, chuyển ngôn ngữ `VI` sang `EN`.
- **Hiện tượng quan sát**:
  - Tiêu đề trung tâm: "MỘT CHIẾC NHẪN, MỘT CÂU CHUYỆN RIÊNG." với 2 CTA nổi bật "🔨 ĐẶT WORKSHOP NGAY" và "💍 KHÁM PHÁ READY RINGS".
  - Chuyển `Ngôn ngữ` sang `EN`: Hero banner vẫn hiển thị 100% tiếng Việt! Menu Navbar vẫn là "Cấu hình nhẫn", "Kho Voucher", "Tra cứu", "Trợ lý AI", "Đánh giá". Chỉ duy nhất nhãn cạnh combobox đổi thành "Language".
- **Đánh giá UX**:
  - *Eye Focus*: Trung tâm tốt, 2 CTA rõ ràng.
  - *Soft Fail*: Tính năng chuyển ngữ là "ảo" (Pseudo-localization), gây thất vọng cho khách quốc tế khi chọn English nhưng toàn bộ nội dung vẫn là tiếng Việt.
- **Bằng chứng**: `screenshots/ux_p1_01_homepage_desktop.png`.

#### 2. Bước 2: Thử thách Dẫn đường AI (`/ai-support`)
- **Thao tác**: Nhấp vào menu "Trợ lý AI", gửi câu hỏi: *"Tôi muốn tìm hiểu gói workshop chế tác nhẫn bạc cho 2 người vào cuối tuần, giá bao nhiêu và đặt như thế nào?"*.
- **Thời gian phản hồi**: **0.66 giây** (Cực nhanh).
- **Hiện tượng quan sát**:
  - Khi mới vào trang: Vùng trung tâm màn hình là một **khoảng trắng mênh mông, trống rỗng (Dead White Void)**. Không có lời chào của bot, không có badge gợi ý câu hỏi mẫu. Người dùng nhìn vào giữa màn hình không biết AI này có thể làm được gì.
  - Sau khi gửi câu hỏi: AI trả lời: *"Mland tổ chức các buổi workshop chế tác nhẫn bạc cơ bản và điêu khắc sáp phôi hàng tuần. Đặt chỗ yêu cầu đặt cọc 50% qua cổng VNPay."*
- **Đánh giá UX**:
  - *Dead-end friction*: Câu trả lời hoàn toàn không có đường link hoặc nút bấm dẫn sang `/workshops`. Người dùng đọc xong bị "kẹt lại", phải tự ngước mắt lên header tìm chữ "Workshop".
- **Bằng chứng**: `screenshots/ux_p1_02_ai_support_empty.png`, `screenshots/ux_p1_03_ai_support_answered.png`.

#### 3. Bước 3: Hồ sơ cá nhân (`/profile`) & Cảnh báo Email
- **Thao tác**: Mở menu tài khoản `minikie456`, chọn `Hồ sơ cá nhân`.
- **Hiện tượng quan sát**:
  - Dù đăng nhập bằng Google OAuth (`minikie456@gmail.com`), trên đầu trang vẫn xuất hiện một dải banner cảnh báo màu vàng to tướng: *"Cảnh báo bảo mật: Email của bạn chưa được xác minh..."*.
  - Thông báo này chiếm trọn sự chú ý ở phần trên màn hình, gây hoang mang không cần thiết cho người dùng Google Sign-in.
- **Bằng chứng**: `screenshots/ux_p1_04_profile_page.png`.

#### 4. Bước 4: Thiết lập Sổ địa chỉ Shopee-style 2 cấp (`/profile/addresses`)
- **Thao tác**: Bấm "+ Thêm địa chỉ mới", kiểm tra form pop-up modal.
- **Hiện tượng quan sát**:
  - Modal mở ngay giữa màn hình, layout chuẩn mực, sạch sẽ.
  - **Cơ chế khoá tuần tự (Sequential lock)**: Trường "Số điện thoại" bị disable, yêu cầu *"Nhập Họ và tên trước"*. Trường "Tỉnh/Thành" bị disable, yêu cầu *"Nhập Số điện thoại hợp lệ trước"*. Trường "Phường/Xã" bị disable, yêu cầu *"Chọn Tỉnh/Thành trước"*.
- **Đánh giá UX**:
  - Khắt khe quá mức so với thói quen người dùng hiện đại. Người dùng muốn dán (paste) địa chỉ hoặc chọn Tỉnh/Thành trước đều bị chặn cứng, buộc phải điền cứng nhắc từng bước một.
- **Bằng chứng**: `screenshots/ux_p1_05_address_modal.png`.

---

### PHASE 2: WORKSHOP BOOKING & FULFILLMENT

#### 1. Bước 1: Danh sách Workshop & Chọn ca (`/workshops`)
- **Thao tác**: Xem danh sách gói, click "Chọn ca & đặt" trên gói "Signature Workshop - Silver 925" (400.000 ₫).
- **Hiện tượng quan sát**:
  - Khi chuyển hướng sang `/workshops/145?focus=schedule`, màn hình KHÔNG cuộn mượt xuống bảng lịch mà dừng ở đỉnh trang.
  - Trung tâm mắt người dùng nhìn thấy khối banner Voucher giảm giá dài ngoằng thay vì bảng chọn ngày giờ ca học. Người dùng bắt buộc phải cuộn chuột xuống mới thấy bảng chọn ca.
- **Bằng chứng**: `screenshots/ux_p2_01_workshops_list.png`, `screenshots/ux_p2_02_workshop_schedule_selection.png`.

#### 2. Bước 2: Giữ chỗ & Lỗi che khuất nút CTA thanh toán VNPay
- **Thao tác**: Chọn ca 18/11/2026 (13:00 - 16:00), bấm "Đặt ngay", kiểm tra form giữ chỗ.
- **Hiện tượng quan sát**:
  - Thử nghiệm Soft Fail: Bấm "Xác nhận và giữ chỗ" khi chưa điền số điện thoại $\rightarrow$ Hệ thống hiển thị dòng đỏ *"Số điện thoại không hợp lệ cho Việt Nam (+84)"* ngay dưới ô input. Phản hồi tức thì, không tải lại trang (Tốt).
  - Điền SĐT `0912345678`, bấm "Xác nhận và giữ chỗ" $\rightarrow$ Dải banner đếm ngược 10 phút xuất hiện: *"1 giao dịch đang chờ thanh toán, còn 10:12"*.
  - **SỰ CỐ UX NGHIÊM TRỌNG (STICKY HEADER COVER BUG)**:
    Khi trang hiển thị trạng thái "Đã giữ chỗ cho 1 vé", nút bấm chính **"Thanh toán 200.000 ₫ qua VNPay"** bị thanh `div.main-topbar-wrapper` đè lên trên ở vị trí cuộn tự nhiên.
    Tự động hóa báo lỗi: `button is covered by <div.main-topbar-wrapper>. Close or dismiss it first`.
    Người dùng thật nếu không chủ động cuộn trang lên xuống sẽ click trượt hoặc click nhầm vào navbar thay vì nút thanh toán!
- **Bằng chứng**: `screenshots/ux_p2_06_hold_banner.png`.

#### 3. Bước 3: Cổng thanh toán VNPay Sandbox & Xác nhận vé
- **Thao tác**: Thanh toán 200.000 ₫ cọc bằng thẻ NCB Sandbox (`9704198526191432198` / OTP `123456`).
- **Thời gian xử lý**: ~3 giây chuyển hướng, callback trực tiếp về Vercel (`payment-return`).
- **Hiện tượng quan sát**:
  - Vé `GRP-D9F21767B811439EB4B7-L0-T1` được tạo kèm mã QR trực quan ngay giữa màn hình. Hiển thị rõ số tiền đã thanh toán (200.000 ₫) và số tiền còn lại phải trả tại quầy (600.000 ₫).
- **Bằng chứng**: `screenshots/ux_p2_07_payment_success.png`.

#### 4. Bước 4: Thao tác Nhân viên (Edge) - Check-in & Quyết toán Folio
- **Thao tác**: Đăng nhập `staff.test@mland.vn`, mở ca `bc2ff6f9-d92f-11f1-81a4-98bd80539bba` ngày 18/11/2026 (`/operations/sessions/...`).
- **Hiện tượng quan sát**:
  - Thử check-in vé giả `GRP-FAKE-TICKET-999`: Báo lỗi 409 rõ ràng: *"Check-in opens at 12:30:00 18/11/2026"*.
  - Mở Folio vé (`/settlement?ticket=...`):
    - Hệ thống tự động điền sẵn số tiền nợ còn lại `600000` vào ô `Amount (VND)`. Đây là điểm cộng UX rất lớn cho thu ngân, không phải tự nhẩm tính.
    - Chọn hình thức `Cash` $\rightarrow$ bấm "Record collection" $\rightarrow$ Số dư về 0 ₫, trạng thái chuyển tức thì sang `FINALIZED`.
  - *Điểm trừ UX*: Màn hình dùng lẫn lộn tiếng Anh kỹ thuật (`Attendance roster`, `Ticket folio`, `Record a collection`, `CUSTOMER_DEVICE_UNAVAILABLE`) với tiếng Việt, gây rào cản cho nhân viên xưởng.
- **Bằng chứng**: `screenshots/ux_p2_08_staff_session_desk.png`, `screenshots/ux_p2_09_staff_settlement_folio.png`.

---

### PHASE 3: RETAIL, LOYALTY VOUCHER & GHTK FULFILLMENT

#### 1. Bước 1: Danh mục Sản phẩm Ready Rings (`/products`) & Bẫy Thêm vào Giỏ
- **Thao tác**: Bấm "Thêm Nhẫn bạc 999 vào giỏ hàng" trực tiếp từ thẻ sản phẩm.
- **Hiện tượng quan sát & Điểm ức chế liên tiếp (Double-Friction Trap)**:
  - Một popup modal mở ra: `dialog "Thêm vào giỏ: Nhẫn bạc 999"`.
  - Trên popup:
    - Nút chọn Size (21, 22, 23, 24) chưa chọn cái nào.
    - Dropdown "Chi nhánh" chưa chọn chi nhánh nào.
    - Nhưng 2 nút CTA "Thêm Vào Giỏ Hàng" và "Mua Ngay" lại có màu đen/trắng trông hoàn toàn KHẢ DỤNG (không bị mờ xám).
  - Người dùng bấm "Thêm Vào Giỏ Hàng" $\rightarrow$ Bị chặn lần 1: Dòng chữ đỏ *"Vui lòng chọn size"* xuất hiện.
  - Người dùng bấm chọn Size 21 $\rightarrow$ bấm "Thêm Vào Giỏ Hàng" lần nữa $\rightarrow$ Bị chặn lần 2: Dòng chữ đỏ *"Vui lòng chọn chi nhánh"* xuất hiện!
- **Đánh giá UX**:
  - Trải nghiệm thêm giỏ hàng bị ngắt quãng 2 lần liên tiếp. Thay vì vô hiệu hóa nút bấm và đánh dấu rõ các trường bắt buộc, hệ thống lại bắt người dùng tự "dò mìn" từng lỗi một.
- **Bằng chứng**: `screenshots/ux_p3_02_product_quick_modal.png`, `screenshots/ux_p3_03_quick_modal_validation_error.png`.

#### 2. Bước 2: Giỏ hàng (`/member/cart`) & Checkbox Mua hàng
- **Thao tác**: Vào giỏ hàng để mua sản phẩm vừa thêm.
- **Hiện tượng quan sát**:
  - Sản phẩm vừa thêm vào giỏ KHÔNG được chọn sẵn (Unchecked by default).
  - Nút "Mua hàng (0)" ở góc phải bị mờ (disabled). Người dùng mới nhìn vào trung tâm thấy sản phẩm nằm đó nhưng không hiểu vì sao không bấm mua được, cho đến khi để ý thấy ô checkbox nhỏ bên trái.
- **Bằng chứng**: `screenshots/ux_p3_04_cart_page.png`.

#### 3. Bước 3: Trang Checkout & Tính phí ship GHTK (`/member/checkout`)
- **Thao tác**: Chọn phương thức nhận hàng "Giao tận nơi qua GHTK Nhanh".
- **Thời gian tính phí**: **2.62 giây** (Gọi API tính cước GHTK: 23.540 ₫).
- **Hiện tượng quan sát**:
  - Giao diện tự động vô hiệu hóa lựa chọn "Tiền mặt tại quầy" và hiển thị chú thích rõ: *"Chỉ áp dụng khi mọi chi nhánh đều nhận tại cửa hàng"*. Đây là điểm xử lý logic rất tốt và minh bạch.
- **Bằng chứng**: `screenshots/ux_p3_05_checkout_page.png`.

#### 4. Bước 4: LỖI NGHIÊM TRỌNG NHẤT HỆ THỐNG - SILENT 409 CONFLICT TRÊN NÚT "ĐẶT HÀNG"
- **Thao tác**: Bấm nút "Đặt hàng" (Tổng thanh toán: 1.023.539 ₫ qua VNPay).
- **Hiện tượng quan sát**:
  - Màn hình KHÔNG chuyển hướng sang VNPay.
  - KHÔNG có thông báo lỗi (No toast, no alert, no dialog).
  - Nút "Đặt hàng" sau một lúc quay vòng thì đứng yên như chưa có gì xảy ra.
- **Kiểm tra kỹ thuật sâu (Black-box Network Inspection)**:
  - Request: `POST https://affront-copier-barrier.ngrok-free.dev/api/v1/ready-ring-orders` trả về mã lỗi HTTP `409 Conflict`.
  - Response Body từ Backend:
    ```json
    {
      "code": "RETAIL_CONFLICT",
      "message": "Checkout quote changed or expired; review a new quote",
      "correlationId": "4b453b25-4cc8-4f4b-bd6e-9b7a92686870"
    }
    ```
  - **Nguyên nhân UX**: Frontend nuốt chửng (swallow) mã lỗi 409 này mà không hiển thị ra bất kỳ câu cảnh báo nào trên giao diện!
  - Người dùng bấm nút "Đặt hàng" tưởng rằng mạng bị lag hoặc nút không ăn, sẽ bấm liên tục (Rage clicks), dẫn đến bị kẹt vô phương cứu chữa (Dead-end deadlock).
- **Bằng chứng**: `screenshots/ux_p3_06_silent_409_defect.png`.

---

## IV. BẢNG ĐỐI CHIẾU TRẢI NGHIỆM VÀ ĐỀ XUẤT NÂNG CẤP UX CẤP BÁCH

| Vấn đề UX phát hiện | Vị trí màn hình | Tác động tới tâm lý người dùng | Đề xuất khắc phục từ góc độ QA |
| :--- | :--- | :--- | :--- |
| **1. Lỗi nuốt lỗi 409 khi Đặt hàng (Silent 409 Defect)** | `/member/checkout` | Gây hoang mang cực độ, người dùng tưởng hệ thống hỏng, bấm nút nhiều lần rồi bỏ giỏ hàng (Cart abandonment). | Hiển thị ngay Modal/Toast thông báo: *"Báo giá đơn hàng đã thay đổi hoặc hết hạn. Vui lòng bấm [Cập nhật lại báo giá] để tiếp tục thanh toán"*. |
| **2. Sticky Header đè nút CTA VNPay** | `/workshops/:id/book` | Người dùng bị ức chế vì không bấm được nút thanh toán cọc khi ở vị trí cuộn tự nhiên. | Thêm thuộc tính `scroll-margin-top` hoặc tăng `padding-top` cho container nội dung chính để tránh bị thanh navbar đè lên. |
| **3. Bẫy lỗi 2 lần khi Thêm vào giỏ** | Popup nhanh `/products` | Làm gián đoạn cảm xúc mua sắm, tạo cảm giác hệ thống khó tính, bắt bẻ. | Tự động chọn sẵn Size đầu tiên còn hàng và Chi nhánh mặc định của người dùng; hoặc vô hiệu hoá nút CTA kèm tooltip hướng dẫn. |
| **4. Khoảng trắng chết trên Chatbox AI** | `/ai-support` | Người dùng vào không biết bắt đầu từ đâu, thiếu tính kích thích tương tác. | Đặt 3-4 chip gợi ý câu hỏi phổ biến ngay giữa màn hình (VD: *"Workshop cuối tuần", "Giá nhẫn đôi", "Thời gian hoàn thành"*). |
| **5. Phản hồi AI không có đường link tiếp** | `/ai-support` | Người dùng phải tự tìm đường đi tiếp sau khi AI giải đáp, làm đứt gãy luồng mua sắm. | Câu trả lời của AI phải kèm theo thẻ liên kết có thể bấm được (VD: `[Xem lịch Workshop ngay ->]`). |
| **6. Khoá tuần tự form địa chỉ quá khắt khe** | `/profile/addresses` | Gây khó chịu cho người quen thao tác nhanh hoặc muốn paste địa chỉ tổng hợp. | Mở khoá toàn bộ các trường; chỉ kích hoạt kiểm tra tính hợp lệ khi người dùng blur hoặc bấm "Lưu". |
| **7. Thuật ngữ tiếng Anh trên màn hình Staff** | `/operations/sessions`, `/settlement` | Nhân viên vận hành khó hiểu, dễ thao tác sai hoặc phải qua đào tạo phức tạp. | Bản địa hóa 100% các nhãn và lý do ngoại lệ sang tiếng Việt dễ hiểu cho nhân viên xưởng. |

---

## V. KẾT LUẬN CỦA SENIOR QA

Cảm giác của bạn về việc **"thao tác của người dùng với hệ thống chưa ổn lắm về UX" là hoàn toàn chính xác và có cơ sở thực nghiệm vững chắc**. 

Hệ thống sở hữu thế mạnh rất lớn về **tốc độ phản hồi (đều dưới 3 giây)** và **thiết kế thị giác tinh tế, sang trọng, không bị lòe loẹt hay giật lag**. Tuy nhiên, điểm yếu cốt tử hiện nay nằm ở **sự thiếu đồng cảm với hành vi tự nhiên của con người (Lack of Empathy & Affordance)**:
- Bắt người dùng điền theo một trật tự độc đoán (Form địa chỉ, Modal chọn size).
- Bỏ rơi người dùng khi có lỗi ngầm xảy ra (Lỗi nuốt mã 409 lúc Đặt hàng).
- Bẫy ngõ cụt khi tư vấn xong mà không đưa nút bấm đi tiếp (AI Support).
- Lỗi bố cục giao diện che khuất nút hành động chính (Sticky Topbar che nút thanh toán).

Việc khắc phục các điểm ma sát nêu trên sẽ ngay lập tức biến hệ thống từ trạng thái "gây bối rối" thành một trải nghiệm mượt mà, chuyên nghiệp xứng tầm thương hiệu kim hoàn cao cấp.
