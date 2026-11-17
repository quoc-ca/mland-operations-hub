---
id: JOURNEY-PHASE-2
feature: 003-workshop-management
journey_phase: 2
test_level: master_e2e
test_case_type: First-Time-User-Exploratory-Discovery
source_ref: spec.md#003.1,003.2,003.3; spec-general.md#§2,§3,§4; 012-spec.md; 006-spec.md
target_url: https://mland-operations-hub-git-develop-mland-team.vercel.app/workshops
actor_primary: MEMBER (Browser 1)
actor_secondary: STAFF & MANAGER (Browser 2)
browser_tool: browsermcp
environment: mock-prod
test_data:
  member_email: inherited_from_phase_1
  staff_account:
    email: staff.test@mland.vn
    password: StaffTest@123456
  manager_account:
    email: manager.test@mland.vn
    password: ManagerTest@123456
  vnpay_card:
    bank: NCB
    number: "9704198526191432198"
    holder: "NGUYEN VAN A"
    issue_date: "07/15"
    otp: "123456"
  ring_customization:
    size_male: 18
    size_female: 14
    engraving_text: "M & L 2026"
cleanup:
  required: false
  method: handoff-to-phase-3
---

# JOURNEY-PHASE-2: Đặt Lịch Workshop, Cấu hình Nhẫn 3D, Cọc VNPay, Check-in Xưởng & Tất toán Hóa đơn

**Phiên bản**: 3.0 (First-Time User Discovery & Friction Hunting) | **Cập nhật**: 2026-11-17  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tâm thế kiểm thử (Mindset)**: Người dùng Hội viên lần đầu đặt lịch học chế tác nhẫn; lúng túng trong việc tìm kiếm ca học còn trống, bỡ ngỡ trước các thông số kỹ thuật nhẫn (size tay, khắc laser), cẩn trọng trước khoản tiền cọc 50%, và hoang mang tìm kiếm mã vé sau khi thanh toán xong. Phía xưởng, nhân viên thao tác trên giao diện vận hành thực tế.  
**Trình duyệt sử dụng**: 
- **Browser 1 (Client View)**: Khách hàng Hội viên (`MEMBER` — kế thừa từ Phase 1)
- **Browser 2 (Backstage View)**: Nhân viên xưởng (`STAFF`) và Quản lý xưởng (`MANAGER`)
**Môi trường**: Mock Prod (`https://mland-operations-hub-git-develop-mland-team.vercel.app/`)

---

## 1. Mục tiêu Kịch bản (Scenario Objective)

Kiểm chứng tính Dẫn dắt (Wayfinding), sự Trực quan và Khả năng xử lý bế tắc trong luồng dịch vụ Workshop cốt lõi:
- Khách hàng Hội viên tự dò tìm menu Workshop từ Trang chủ, khám phá danh mục gói dịch vụ và tìm kiếm ca học (Slot) phù hợp; kiểm tra xem các ca học đã kín chỗ có báo "Hết chỗ" rõ ràng hay gây bế tắc.
- Vượt qua rào cản nhận thức tại màn hình Cấu hình Nhẫn (Feature 012): Đánh giá xem hệ thống có hướng dẫn đo size tay không hay để khách tự mò; kiểm tra bẫy giới hạn ký tự khắc laser.
- Kiểm tra tính minh bạch tài chính của cơ chế giữ chỗ 10 phút (10-minute hold) và đặt cọc 50% qua cổng VNPay Sandbox.
- Bắt bẫy "Lạc đường sau thanh toán": Kiểm tra xem sau khi trừ tiền VNPay thành công, trang kết quả có nút bấm nổi bật dẫn thẳng đến Vé của tôi và hiển thị mã vé `TKT-...` / QR hay không.
- Phía vận hành: Nhân viên xưởng (`STAFF`) tại Browser 2 tìm kiếm màn hình check-in, thử thách với vé giả và check-in vé thật; Quản lý (`MANAGER`) xuất Hóa đơn tất toán và kích hoạt điểm thưởng Loyalty (`LOYALTY_POINTS`).

---

## 2. Phạm vi Kiểm thử (Scope Boundaries)

### 2.1. Trong phạm vi kiểm thử (In-Scope):
- Danh mục gói Workshop, hiển thị giá niêm yết, thời lượng và trạng thái khả dụng của từng ca học.
- Trình cấu hình nhẫn (Feature 012): Chọn size nhẫn, nhập nội dung khắc laser cá nhân hóa.
- Phân tích chi phí cọc 50% và tích hợp cổng thanh toán VNPay Sandbox.
- Quy trình xuất vé, hiển thị mã vé `TKT-...` và mã QR trên giao diện người dùng.
- Màn hình vận hành xưởng của Staff (`003.2`): Nhập mã vé, kiểm tra quyền chi nhánh, cập nhật trạng thái `CHECKED_IN`.
- Màn hình tất toán của Manager (`003.3`): Xuất hóa đơn tất toán, cập nhật trạng thái `COMPLETED` và tự động tích điểm Loyalty.

### 2.2. Ngoài phạm vi kiểm thử (Out-of-Scope — Tuyệt đối không thực hiện):
- Tuyệt đối không dùng thẻ ngân hàng thật hay tiền thật (chỉ dùng thẻ test NCB Sandbox).
- Không kiểm tra hộp thư email thật bên ngoài để tìm vé QR đính kèm file PDF.
- Không thay đổi hoặc xóa vĩnh viễn cấu hình địa điểm xưởng hay gói dịch vụ mẫu trong cơ sở dữ liệu.
- Không debug mã nguồn khi API trả lỗi; chỉ lưu lại HTTP status, response payload và screenshot hiện trường.

---

## 3. Tiền điều kiện & Dữ liệu Kiểm thử (Preconditions & Test Data)

- **Browser 1 (Member)**: Đang duy trì phiên đăng nhập của Hội viên từ Phase 1 (`minikie456@gmail.com`).
- **Browser 2 (Backstage)**: Cửa sổ Ẩn danh riêng biệt.
  - Tài khoản Staff: `staff.test@mland.vn` / `StaffTest@123456`
  - Tài khoản Manager: `manager.test@mland.vn` / `ManagerTest@123456`
- **Thông tin Thẻ VNPay Sandbox**:
  - Ngân hàng: `NCB` | Số thẻ: `9704198526191432198` | Tên: `NGUYEN VAN A` | Ngày: `07/15` | OTP: `123456`
- **Dữ liệu cấu hình nhẫn**:
  - Size nam: `18` | Size nữ: `14` | Khắc laser: `M & L 2026`

---

## 4. Chu trình Khám phá & Dò tìm Lối đi (Discovery & Wayfinding Flow)

### Bước 1 (P2-S01): Dò tìm Gói Workshop & Khám phá Lịch học Còn trống

- **(a) Quan sát & Dò tìm (Visual Discovery)**:
  - Khách hàng đang ở Trang chủ, mắt tìm kiếm danh mục Workshop trên thanh Navbar: Nút "Workshop" (hoặc "Khóa học chế tác") có nổi bật và dễ nhận biết không?
  - Bấm vào mục Workshop: Trang danh sách hiện ra danh mục các gói dịch vụ (Nhẫn đôi tình nhân, Nhẫn đơn cá tính...). Các card dịch vụ có hiển thị ảnh xưởng thật, giá niêm yết (VD: 1.000.000đ), và thời lượng học (VD: 3 tiếng) rõ ràng không?
- **(b) Thử nghiệm & Bẫy bế tắc Lịch học (Dead-end Probing)**:
  - Bấm xem chi tiết gói "Workshop Chế tác Nhẫn Đôi Tình Nhân". Khách tìm nút đặt lịch ở đâu?
  - Giao diện mở ra bộ lịch chọn ngày và danh sách các ca học (Slot: Sáng 9h-12h, Chiều 14h-17h, Tối 18h-21h).
  - **Kiểm tra bẫy ngõ cụt**: Thử tìm một ca học đã kín chỗ (`0 chỗ trống`):
    + **ĐẠT CHUẨN**: Nút ca học đó bị làm mờ (disabled), hiển thị nhãn đỏ rõ ràng *"Hết chỗ"*, không cho click chọn.
    + **BẾ TẮC (UX Friction)**: Ca học hết chỗ nhưng vẫn cho click chọn, người dùng hí hửng điền thông tin đến bước cuối mới bị báo lỗi hoặc trừ tiền rồi báo hủy.
- **(c) Hành động Đi tiếp & Chuyển bước (Next Step Transition)**:
  - Chọn một ca học có trạng thái khả dụng (VD: "Còn 4/8 chỗ").
  - Nút bấm to bản ở mép dưới **"Tiếp tục — Cấu hình nhẫn"** sáng đèn nổi bật, mời gọi click tiếp.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Nếu danh mục workshop không tải được (500) hoặc không có bất kỳ ca học nào hoạt động.
  - **SOFT FAIL**: Ảnh bìa gói học bị lỗi vỡ layout nhẹ.

---

### Bước 2 (P2-S02): Vượt qua Rào cản Nhận thức tại Màn hình Cấu hình Nhẫn (Feature 012)

- **(a) Quan sát & Dò tìm (Visual Discovery)**:
  - Chuyển sang màn hình cấu hình thông số nhẫn (`012`): Màn hình yêu cầu chọn Size nhẫn nam, Size nhẫn nữ, Chất liệu kim loại và Khắc laser.
- **(b) Thử nghiệm Trở ngại Nhận thức & Bẫy bế tắc (Cognitive Friction & Dead-end Probing)**:
  - **Rào cản kinh điển của khách mới**: Khách hàng *hoàn toàn không biết ngón tay mình size bao nhiêu* (14? 16? 18?).
    + **Kiểm tra lối thoát**: Có nút bấm hoặc link *"Hướng dẫn đo size nhẫn"* (mở bảng tra chu vi ngón tay bằng mm) để khách tự đo không? Nếu thiếu mục này, khách sẽ bị bế tắc tâm lý và có xu hướng bỏ dở (Drop-off).
  - **Thử nghiệm ô Khắc laser**:
    + Thử gõ một câu quá dài (30 ký tự) hoặc ký tự lạ: Form có bộ đếm ký tự trực quan (`10/15`) và tự động giới hạn không?
- **(c) Hành động Đi tiếp & Chuyển bước (Next Step Transition)**:
  - Người dùng chọn: Size nam `18`, Size nữ `14`, Chất liệu `Bạc 925`, Khắc chữ `"M & L 2026"`.
  - Bấm nút **"Xác nhận cấu hình & Sang thanh toán"** (nằm ở vị trí cố định dễ bấm trên cả mobile và desktop).
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Form cấu hình bị đơ, nút tiếp tục bị disabled vĩnh viễn dù đã chọn đủ thông tin.
  - **SOFT FAIL**: Thiếu popup hướng dẫn đo size nhẫn (cần ghi nhận lỗi UX Friction).

---

### Bước 3 (P2-S03): Đánh giá Tính Minh bạch Khoản Cọc 50% & Lối đi sang Cổng VNPay

- **(a) Quan sát & Dò tìm (Visual Discovery)**:
  - Màn hình xác nhận đơn đặt lịch và tóm tắt chi phí hiện ra.
- **(b) Thử nghiệm & Đánh giá Minh bạch Tài chính (Financial Transparency)**:
  - Khách hàng kiểm tra bảng kê tiền: Có phân tách minh bạch 3 dòng rõ ràng không?
    1. Tổng giá trị gói: `1.000.000 VNĐ`
    2. **Tiền cọc giữ chỗ (50%) cần thanh toán ngay**: `500.000 VNĐ`
    3. Tiền còn lại thanh toán tại xưởng sau khi hoàn thành: `500.000 VNĐ`
  - **Kiểm tra cơ chế giữ chỗ 10 phút (10-minute hold)**: Có hiển thị đồng hồ đếm ngược thời gian giữ slot không?
- **(c) Hành động Đi tiếp & Chuyển cổng (Next Step Transition)**:
  - Chọn phương thức thanh toán: "Cổng thanh toán điện tử VNPay".
  - Bấm nút **"Thanh toán cọc 500.000đ"** ➔ Trình duyệt chuyển hướng mượt mà sang cổng VNPay Sandbox (`https://sandbox.vnpayment.vn/...`).
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Hệ thống bắt thanh toán 100% thay vì cọc 50%, hoặc bấm nút thanh toán báo lỗi 500 không sinh được link VNPay.

---

### Bước 4 (P2-S04): Thanh toán Thẻ Thử nghiệm trên VNPay Sandbox

- **(a) Thao tác trên Cổng VNPay**:
  - Chọn tab "Thẻ nội địa" ➔ Chọn ngân hàng `NCB`.
  - Nhập thông tin thẻ test:
    + Số thẻ: `9704198526191432198`
    + Tên chủ thẻ: `NGUYEN VAN A`
    + Ngày phát hành: `07/15`
  - Bấm "Tiếp tục" ➔ Nhập mã OTP: `123456` ➔ Bấm "Xác nhận".
- **(b) Quan sát Chuyển hướng Quay lại**:
  - Cổng VNPay báo "Giao dịch thành công", tự động đếm ngược và redirect quay trở lại trang web Mland (`/payment/vnpay/return` hoặc `/booking/success`).
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: VNPay trừ tiền thành công nhưng trình duyệt không quay về web hoặc web báo lỗi màn hình trắng.

---

### Bước 5 (P2-S05): "Lạc đường sau Thanh toán" — Dò tìm Mã vé & QR Code

- **(a) Quan sát & Dò tìm Sau Thanh toán (Post-Payment Wayfinding)**:
  - Người dùng vừa mất 500.000đ cọc, tâm lý rất cần xác nhận vé để yên tâm.
  - Màn hình kết quả hiển thị thế nào?
    + **ĐẠT CHUẨN**: Tiêu đề xanh lá "Đặt lịch thành công!", hiển thị **Mã QR to bản**, **Mã ký tự vé `TKT-...`**, ngày giờ học và địa chỉ xưởng Hoàn Kiếm.
    + **LỐI ĐI TIẾP THEO RÕ RÀNG**: Có 2 nút bấm nổi bật: **"Xem chi tiết Vé của tôi"** và **"Quay về Trang chủ"**.
    + **BẾ TẮC (Dead End)**: Màn hình chỉ báo text cụt lủn "Thành công" mà không có mã vé, không có nút bấm điều hướng tiếp, khiến khách phải tự bấm nút Back của trình duyệt hoặc không biết vé của mình được lưu ở đâu.
- **(b) Chốt bước & Lưu dữ liệu**:
  - Người dùng ghi nhận mã vé: `WORKSHOP_TICKET_CODE` (lưu lại để giao dịch tại xưởng ở Bước 6).
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Web báo cọc thành công nhưng không sinh ra mã vé `TKT-...`.

---

### Bước 6 (P2-S06): Thao tác Nhân viên Xưởng — Quét vé Điểm danh tại Xưởng (Browser 2)

- **(a) Quan sát & Dò tìm của Nhân viên (Backstage Discovery)**:
  - Mở **Browser 2 (Cửa sổ Ẩn danh)** tại `.../login`.
  - Đăng nhập tài khoản Staff: `staff.test@mland.vn` / `StaffTest@123456`.
  - Nhân viên nhìn vào thanh menu: Có thấy mục **"Điều hành xưởng & Check-in"** (`/operator/workshop-operations`) không?
- **(b) Thử nghiệm Đối nghịch & Bẫy vé giả (Adversarial Probing)**:
  - Mở màn hình check-in: Ô nhập mã vé / quét QR nằm ở vị trí trung tâm.
  - **Thử nhập vé giả**: Gõ `TKT-FAKE-99999` ➔ Bấm "Kiểm tra vé".
    + Hệ thống hiển thị thông báo lỗi màu đỏ rõ ràng: *"Mã vé không tồn tại hoặc không thuộc ca học này"*.
  - **Quét vé thật**: Nhập mã vé `WORKSHOP_TICKET_CODE` vừa sinh từ Bước 5.
    + Màn hình hiển thị đầy đủ thông tin học viên: Nguyễn Thị Phương Uyên, Gói học, Cấu hình nhẫn (Size 14, 18, Khắc chữ "M & L 2026").
- **(c) Hành động Đi tiếp & Chốt trạng thái (Next Step Transition)**:
  - Nhân viên bấm nút **"Xác nhận Check-in"**.
  - Trạng thái vé đổi thành **"ĐÃ ĐIỂM DANH / CHECKED_IN"**. Nút check-in chuyển sang trạng thái disabled để chống nhân viên click nhầm lần 2.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Staff không đăng nhập được hoặc nhập đúng mã vé thật nhưng hệ thống báo không tìm thấy vé.
  - **SOFT FAIL**: Thông báo vé giả có câu chữ hơi khác nhưng vẫn chặn check-in.

---

### Bước 7 (P2-S07): Thao tác Quản lý — Xuất Hóa đơn Tất toán & Kích hoạt Điểm Loyalty (Browser 2)

- **(a) Quan sát & Đăng nhập Quản lý**:
  - Tại Browser 2, chuyển sang đăng nhập tài khoản Manager: `manager.test@mland.vn` / `ManagerTest@123456`.
  - Mở trang Quản lý thanh toán ca học (`/operator/workshop-settlement`).
- **(b) Thẩm tra & Xuất Hóa đơn Tất toán**:
  - Quản lý tìm ca học của học viên vừa check-in: Thấy trạng thái học viên đã điểm danh.
  - Kiểm tra các khoản phụ phí phát sinh (nếu có thêm đá quý/phụ kiện) hoặc giữ nguyên phụ phí = 0đ.
  - Bấm nút **"Xuất Hóa đơn Tất toán"** (`Final Settlement`).
- **(c) Chốt trạng thái & Bàn giao Dữ liệu**:
  - Ca học chuyển trạng thái thành **`COMPLETED`**.
  - Hệ thống Modulith tự động kích hoạt Domain Event: Tài khoản Member (`minikie456@gmail.com`) được cộng **`+50 Điểm thưởng Loyalty`** vào ví điểm.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Quản lý không xuất được hóa đơn tất toán hoặc ca học không kết thúc được.

---

## 5. Điều kiện Dừng Kiểm thử Khẩn cấp (Scenario Abort Triggers)

Kịch bản **JOURNEY-PHASE-2** sẽ bị **DỪNG NGAY LẬP TỨC (ABORT)** nếu:
1. **Danh mục hoặc Ca học tê liệt**: Không thể tải danh sách workshop hoặc mọi ca học đều lỗi 500.
2. **Cổng VNPay lỗi kết nối**: Bấm thanh toán cọc không ra link VNPay hoặc VNPay từ chối giao dịch.
3. **Mất tiền không có vé**: VNPay báo thành công nhưng web không xuất hiện mã vé `TKT-...`.
4. **Staff/Manager bị từ chối truy cập (401/403)**: Tài khoản xưởng không thể đăng nhập hoặc không thấy màn hình điều hành.
5. **Vé thật bị từ chối**: Nhân viên nhập đúng mã vé vừa mua nhưng hệ thống báo không tồn tại (lỗi đồng bộ cơ sở dữ liệu).

---

## 6. Kế hoạch Dữ liệu Nối tầng (Data Chaining to Phase 3)

Dữ liệu đầu ra của Phase 2 bàn giao cho **JOURNEY-PHASE-3**:
- `WORKSHOP_TICKET_CODE`: Mã vé đã check-in thành công.
- `WORKSHOP_SESSION_ID`: ID ca học đã hoàn tất (dùng để viết Review ở Phase 5).
- `LOYALTY_POINTS_EARNED`: **50 Điểm thưởng Loyalty** đã nằm trong ví của Member, sẵn sàng để tiêu ở Phase 3.

---

## 7. Mẫu Báo cáo Hiện tượng Bất thường (Unexpected Finding Template)

```

---

## 8. Kết quả Thực thi Kiểm thử Thực tế (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS (có 1 lỗi Hạ tầng / Redirect ngrok)
- Executed at: 2026-11-17 19:04:30
- Executed by: Senior QA & System Verification Engineer
- Browser tools: 
  + Client (Browser 1): Chrome (Profile `minikie456`)
  + Backstage (Browser 2): Edge (Account `staff.test@mland.vn`)
- Actual result:
  + Chọn gói Signature Silver 925, chọn ca 18/11/2026, điền SĐT và kích hoạt giữ chỗ 10 phút thành công.
  + Áp dụng voucher workshop giảm 100k, thanh toán cọc 200.000đ qua VNPay Sandbox (thẻ NCB) thành công.
  + [BUG-E2E-0011] VNPay redirect về ngrok tunnel (`affront-copier-barrier.ngrok-free.dev`) vướng cảnh báo ERR_NGROK_6024 trước khi về lại web.
  + Sinh mã đặt chỗ: `GRP-A7D055972BCC4DEAB803`, mã vé: `GRP-A7D055972BCC4DEAB803-L0-T1`, mã QR: `QR-27dd0ee5-6c1b-401c-9262-b4fcc6fc6c00`.
  + Staff check-in: Vé giả bị chặn 409; vé thật ngày mai phản hồi `409 CHECK_IN_NOT_OPEN` (nghiệp vụ chính xác: chỉ mở check-in trước giờ học 30 phút).
  + Staff quyết toán Folio (`/settlement`): Thu nốt 600.000đ tiền mặt, ghi nhận collection và Finalize Folio thành công (trạng thái `FINALIZED`, `Balance due: 0 VND`).
- Evidence screenshots:
  + [P2-01--workshop-list.jpg](screenshots/P2-01--workshop-list.jpg)
  + [P2-02--workshop-detail.jpg](screenshots/P2-02--workshop-detail.jpg)
  + [P2-03--booking-page.jpg](screenshots/P2-03--booking-page.jpg)
  + [P2-04--payment-or-hold.jpg](screenshots/P2-04--payment-or-hold.jpg)
  + [P2-05--vnpay-page.jpg](screenshots/P2-05--vnpay-page.jpg)
  + [P2-06--ngrok-warning.jpg](screenshots/P2-06--ngrok-warning.jpg)
  + [P2-07--after-ngrok.jpg](screenshots/P2-07--after-ngrok.jpg)
  + [P2-08--staff-logged-in.jpg](screenshots/P2-08--staff-logged-in.jpg)
  + [P2-09--staff-sessions.jpg](screenshots/P2-09--staff-sessions.jpg)
  + [P2-10--staff-session-detail.jpg](screenshots/P2-10--staff-session-detail.jpg)
  + [P2-11--staff-checked-in.jpg](screenshots/P2-11--staff-checked-in.jpg)
  + [P2-12--ticket-folio.jpg](screenshots/P2-12--ticket-folio.jpg)
  + [P2-13--folio-finalized.jpg](screenshots/P2-13--folio-finalized.jpg)
- Bug references: BUG-E2E-0011
<!-- EXECUTION:END -->
markdown
### [UNEXPECTED-P2-xxx] <Tiêu đề ngắn gọn về hiện tượng bất thường / Điểm bế tắc>
- Bước kiểm thử: P2-S0x
- Màn hình / URL thực tế: https://mland-operations-hub-git-develop-mland-team.vercel.app/...
- Mức độ: HARD STOP (Bế tắc luồng) | SOFT FAIL (Trở ngại nhận thức / Lỗi hiển thị)
- Điểm bế tắc quan sát được: <Ca học hết chỗ không ghi rõ, thiếu hướng dẫn size, thanh toán xong không thấy vé...>
- Thao tác người dùng đã thử: <Mô tả bước click/nhập liệu>
- Hành vi kỳ vọng theo spec: <Phải có dấu hiệu trực quan và nút bấm rõ ràng>
- Bằng chứng:
  + Screenshot: `screenshots/P2-xx--<mô tả>.jpg`
  + Mã vé liên quan: WORKSHOP_TICKET_CODE
  + Console / Network: <HTTP status, response error code>
```

---

## 8. Kết quả Thực thi Kiểm thử Thực tế (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS (có 1 lỗi Hạ tầng / Redirect ngrok)
- Executed at: 2026-11-17 19:04:30
- Executed by: Senior QA & System Verification Engineer
- Browser tools: 
  + Client (Browser 1): Chrome (Profile `minikie456`)
  + Backstage (Browser 2): Edge (Account `staff.test@mland.vn`)
- Actual result:
  + Chọn gói Signature Silver 925, chọn ca 18/11/2026, điền SĐT và kích hoạt giữ chỗ 10 phút thành công.
  + Áp dụng voucher workshop giảm 100k, thanh toán cọc 200.000đ qua VNPay Sandbox (thẻ NCB) thành công.
  + [BUG-E2E-0011] VNPay redirect về ngrok tunnel (`affront-copier-barrier.ngrok-free.dev`) vướng cảnh báo ERR_NGROK_6024 trước khi về lại web.
  + Sinh mã đặt chỗ: `GRP-A7D055972BCC4DEAB803`, mã vé: `GRP-A7D055972BCC4DEAB803-L0-T1`, mã QR: `QR-27dd0ee5-6c1b-401c-9262-b4fcc6fc6c00`.
  + Staff check-in: Vé giả bị chặn 409; vé thật ngày mai phản hồi `409 CHECK_IN_NOT_OPEN` (nghiệp vụ chính xác: chỉ mở check-in trước giờ học 30 phút).
  + Staff quyết toán Folio (`/settlement`): Thu nốt 600.000đ tiền mặt, ghi nhận collection và Finalize Folio thành công (trạng thái `FINALIZED`, `Balance due: 0 VND`).
- Evidence screenshots:
  + [P2-01--workshop-list.jpg](screenshots/P2-01--workshop-list.jpg)
  + [P2-02--workshop-detail.jpg](screenshots/P2-02--workshop-detail.jpg)
  + [P2-03--booking-page.jpg](screenshots/P2-03--booking-page.jpg)
  + [P2-04--payment-or-hold.jpg](screenshots/P2-04--payment-or-hold.jpg)
  + [P2-05--vnpay-page.jpg](screenshots/P2-05--vnpay-page.jpg)
  + [P2-06--ngrok-warning.jpg](screenshots/P2-06--ngrok-warning.jpg)
  + [P2-07--after-ngrok.jpg](screenshots/P2-07--after-ngrok.jpg)
  + [P2-08--staff-logged-in.jpg](screenshots/P2-08--staff-logged-in.jpg)
  + [P2-09--staff-sessions.jpg](screenshots/P2-09--staff-sessions.jpg)
  + [P2-10--staff-session-detail.jpg](screenshots/P2-10--staff-session-detail.jpg)
  + [P2-11--staff-checked-in.jpg](screenshots/P2-11--staff-checked-in.jpg)
  + [P2-12--ticket-folio.jpg](screenshots/P2-12--ticket-folio.jpg)
  + [P2-13--folio-finalized.jpg](screenshots/P2-13--folio-finalized.jpg)
- Bug references: BUG-E2E-0011
<!-- EXECUTION:END -->

