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
- Đổi slot vé (003.3 FR-044/FR-045): một lượt đổi mỗi vé, hạn 24 giờ, chỉ liệt kê buổi còn chỗ, xác nhận khoản tăng giá, bấm đúp; khách chỉ đổi trước buổi học, đến muộn chỉ Staff/Manager (FR-044b).
- Hàng chờ yêu cầu đổi lịch (`/settlement/change-requests`): Staff và Manager duyệt (không cần lý do) hoặc từ chối (bắt buộc lý do), phạm vi chi nhánh của Staff, Admin bị chặn (FR-045a).
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

### Bước 5b (P2-S05b): Đổi Slot Vé — Lối đi, Hạn mức 1 lần đổi & Bẫy hạn 24 giờ (Browser 1)

> Căn cứ: `003.3 spec.md` FR-044, FR-044a, FR-044b (2026-11-26), FR-045, FR-045a. Chèn sau P2-S05, trước khi Staff check-in, để vé ở Bước 6 là vé đã đổi slot.

- **(a) Quan sát & Dò tìm (Visual Discovery)**:
  - Không có màn "Vé của tôi". Khách vào **"Tra cứu vé & Booking"** (`/workshop-booking/lookup`), mở nhóm đặt chỗ, tìm nút **"Đổi lịch"** trên thẻ vé `WORKSHOP_TICKET_CODE` (nút cũng có ở trang `payment-return` ngay sau thanh toán). Nút có dễ thấy không, hay khách phải tự mò?
  - Nút **ẩn** khi nhóm hoặc vé chưa `CONFIRMED`, vé đã có điểm danh hoặc no-show; đó là hành vi đúng, không phải lỗi.
  - Khách vãng lai (Guest) xem danh sách buổi qua một liên hệ không cần OTP, nhưng **cần OTP để xác nhận đổi**.
  - Mở form đổi lịch: **không có ô "Loại đổi lịch"** (FR-044b: Member và Guest chỉ đổi trước buổi học), bộ chọn buổi hiện ngay.
- **(b) Thử nghiệm & Bẫy bế tắc (Dead-end Probing)**:
  - **Nhánh 1 — Đổi hợp lệ (happy path)**: Mở form đổi lịch, bộ chọn chỉ liệt kê các buổi có thể thay thế, kèm số chỗ còn lại.
    + **ĐẠT CHUẨN**: Buổi kín chỗ hoặc vi phạm thứ tự/khoảng cách gói **không xuất hiện** trong danh sách (FR-044a); mỗi dòng hiển thị chỗ trống và, nếu có, số tiền tăng thêm.
    + **BẾ TẮC (UX Friction)**: Buổi kín chỗ vẫn chọn được rồi mới báo lỗi ở bước xác nhận.
    + Chọn một buổi còn chỗ, **cách giờ học hiện tại ≥ 24 giờ**. Giá chỉ tăng khi vé đang có giảm giá voucher mà buổi mới nằm ngoài phạm vi voucher (cần chuẩn bị vé có voucher nếu muốn thử); không bao giờ giảm giá hay hoàn tiền (FR-045).
    + Bấm **"Xác nhận đổi lịch"**. Nếu giá tăng: lần gọi đầu **chưa áp dụng gì**, hiện *"Đổi lịch này làm giá vé tăng … Bạn có đồng ý không?"* kèm nút **"Đồng ý mức tăng giá"**; nút xác nhận chính bị khóa tới khi đồng ý; khoản tăng được cộng vào số còn phải trả khi check-in. Nếu giá không tăng: đổi ngay.
    + Thành công ➔ thông báo *"Đã đổi lịch cho vé …"*, thẻ vé hiển thị slot mới.
    + Hạn mức đổi của **buổi mới** giảm 1 ("còn N lượt đổi" ở bộ chọn giảm 1 khi xem lại).
  - **Nhánh 2 — Hết lượt đổi (Abnormal)**: Dùng vé **đã đổi 1 lần** (vé riêng, xem Dữ liệu cần chuẩn bị), thử đổi lần nữa ➔ **không bị từ chối mà được xếp hàng chờ**: bộ chọn hiện ghi chú *"Vé này đã dùng hết lần đổi lịch; mọi lựa chọn cần quản lý duyệt."*, mỗi dòng ghi *"Cần quản lý duyệt (đã dùng lần đổi của vé)"*, nút đổi thành **"Gửi yêu cầu đổi lịch"**; sau khi gửi hiện *"Đã gửi yêu cầu đổi lịch cho quản lý duyệt."* kèm mã yêu cầu (202). Vé **chưa bị đổi**, có đúng 1 yêu cầu `PENDING`.
  - **Nhánh 3 — Quá hạn 24 giờ (Boundary)**: Với vé (riêng) có buổi học bắt đầu **dưới 24 giờ** nữa, chưa bắt đầu, mở form đổi lịch ➔ ghi chú *"Đã quá hạn đổi lịch trước buổi học (24 giờ); lựa chọn cần quản lý duyệt."*, nút "Gửi yêu cầu đổi lịch", thông báo gửi như nhánh 2, vé **chưa bị đổi**. Gửi thêm lần nữa trên cùng vé ➔ `409 CHANGE_REQUEST_PENDING` (*"Vé này đang có một yêu cầu đổi lịch chờ quản lý duyệt."*): mỗi vé chỉ có một yêu cầu `PENDING`.
  - **Nhánh 4 — Chống bấm đúp**: Bấm "Xác nhận đổi lịch" hai lần liên tiếp ở nhánh 1 ➔ nút bị khóa khi đang gửi và vé chỉ có **một lượt đổi được tính**. (Mỗi lần gửi dùng khóa idempotency mới, nên chỉ nút bị khóa mới bảo vệ; một lần gửi lọt thêm sẽ thành yêu cầu chờ duyệt thay vì đổi lần hai. Nếu thấy yêu cầu thứ hai xuất hiện, ghi nhận SOFT FAIL.)
  - **Nhánh 5 — Buổi đã bắt đầu (Boundary, FR-044b)**: Với vé D có buổi học **đã bắt đầu** và chưa điểm danh/no-show, mở thẻ vé ➔ hiện *"Buổi 1 đã bắt đầu nên không đổi lịch trực tuyến được. Vui lòng liên hệ cửa hàng {tên chi nhánh} để được hỗ trợ."*, **không có nút "Đổi lịch"** (dữ liệu không có số điện thoại chi nhánh). Vé 2 buổi mới bắt đầu 1 buổi: vẫn có thông báo và vẫn đổi được buổi còn lại. Gọi API `ADVANCE` cho buổi đã bắt đầu ➔ `409 ADVANCE_CHANGE_WINDOW_EXPIRED`, không tạo yêu cầu chờ duyệt; không thử API thì ghi `NOT RUN`.
  - **Nhánh 6 — Khách gọi `LATE_ARRIVAL` (Abuse, FR-044b)**: Member hoặc Guest gọi API đổi một vé, đổi nhiều vé hoặc danh sách buổi (cả `change-option-lookups`) với `type=LATE_ARRIVAL` ➔ `403 LATE_ARRIVAL_STAFF_ONLY`, vé không đổi, không trừ lượt. Staff được phân công đúng chi nhánh (trang "Đổi vé", loại "Đến muộn") và Manager vẫn làm được. Không thử API thì ghi `NOT RUN`.
- **(c) Hành động Đi tiếp & Chốt trạng thái (Next Step Transition)**:
  - Mở lại thẻ vé trong **"Tra cứu vé & Booking"**: slot mới hiển thị đúng ngày, giờ, chi nhánh.
  - **Assertion mã vé & QR (003.2 FR-010z)**: Trước khi đổi, ghi lại mã vé `GRP-…-L0-T1` và mã QR `QR-…`. Sau khi đổi, **mã vé và QR phải y hệt** (so sánh từng ký tự), không có vé thay thế mới trong danh sách, và QR cũ vẫn được Staff chấp nhận ở P2-S06 (không báo `TICKET_QR_REVOKED`). Khác bất kỳ điều nào ➔ **HARD STOP**, ghi lỗi gán Backend.
  - Chụp ảnh mã vé/QR trước và sau đổi để đối chiếu.
  - **Assertion email "Đã đổi lịch" (003.3 FR-064, template 10)**: Khi đổi **được áp dụng ngay** (nhánh tự duyệt), trong hộp thư giữ lại của môi trường test (`app.mail.adapter=captured`: thư mục `app.mail.outbox-dir`, mặc định `target/mail-outbox`; hoặc hộp thư thật khi bật `smtp`) có **đúng 1 email** tới email liên hệ của đơn, tiêu đề `MLAND – Đã đổi lịch vé {mãVé}`, nội dung nêu lịch cũ → lịch mới (giờ Việt Nam), chi nhánh, câu "mã vé và mã QR không đổi", và liên kết tới `/workshop-booking/lookup`. Nếu có tăng giá do mất phạm vi voucher: thêm câu số tiền cần thanh toán thêm (khớp số đã xác nhận ở bước đổi). **Không có email thứ hai kèm QR/PDF**. Nếu vé có người nhận riêng (khác email người mua): người nhận đó có 1 email cùng tiêu đề **không** có số tiền tăng giá; nếu người nhận trùng email người mua thì chỉ 1 email. Đơn không có email liên hệ: không có email và đổi lịch vẫn thành công. Replay cùng `Idempotency-Key` không tạo email mới.
  - **Assertion check-in theo slot (003.2 FR-010z)**: Vé chỉ còn quyền ở **slot mới**; không giữ quyền ở slot cũ.
    + Staff mở danh sách học viên (roster) của **slot cũ**: vé **không còn** trong danh sách (roster không có kiểm tra giờ mở check-in nên kiểm tra được bất cứ lúc nào). Tra theo mã đặt chỗ ở slot cũ trả không có vé hợp lệ (404).
    + Quét/nhập mã vé ở **slot cũ**: bị từ chối `409 TICKET_CONFLICT` (vé không có quyền ở buổi này), **không ghi nhận điểm danh**. **Lưu ý thứ tự kiểm tra của backend**: quyền Staff ➔ trạng thái buổi (phải `PUBLISHED`/`SALES_PAUSED`/`IN_PROGRESS`) ➔ giờ mở check-in ➔ vé ➔ quyền theo buổi. Vì vậy nếu slot cũ chưa đến giờ mở thì sẽ trả `409 CHECK_IN_NOT_OPEN` và **không chứng minh được** vé mất quyền; muốn khẳng định mã `TICKET_CONFLICT` phải dùng slot cũ đang trong giờ mở check-in, còn không thì chỉ dùng roster ở dòng trên làm bằng chứng và ghi rõ mã lỗi thực tế.
    + Staff mở roster **slot mới**: vé có mặt. Check-in thành công khi buổi mới ở trạng thái `PUBLISHED`/`SALES_PAUSED`/`IN_PROGRESS` và đã qua `checkInOpensAt` của buổi đó; trước giờ đó trả `409 CHECK_IN_NOT_OPEN` kèm `checkInOpensAt`. Trạng thái điểm danh của vé ở slot mới là chưa check-in (hệ thống đặt lại khi đổi).
    + Staff chỉ check-in được ở buổi thuộc chi nhánh mình được phân công; nếu slot mới ở chi nhánh khác thì dùng Staff của chi nhánh đó.
    + Check-in được ở slot cũ ➔ **HARD STOP** (vé giữ quyền ở buổi đã thay thế).
    + **Chỗ trống**: ca cũ nhả 1 chỗ, ca mới nhận 1 chỗ (kiểm tra số chỗ trống trên trang chi tiết gói trước và sau); tổng chỗ vé này chiếm là 1, không phải 2.
  - Ghi nhận `NEW_SLOT_REFERENCE` và `OLD_SLOT_FREED` (chỗ ca cũ được trả lại: số chỗ trống ca cũ tăng 1 khi xem lại trang chi tiết gói).
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Member hoặc Guest đổi được bằng `LATE_ARRIVAL`; mã vé hoặc QR thay đổi sau khi đổi slot; đổi lịch trừ lượt nhưng vé không đổi slot; hoặc đổi sang buổi đã kín chỗ; hoặc ca cũ không được trả chỗ; hoặc tạo hai lần đổi do bấm đúp.
  - **SOFT FAIL**: Thiếu nút "Đổi lịch" dễ thấy; câu chữ thông báo lỗi khác đặc tả nhưng vẫn chặn đúng; số chỗ trống hiển thị trễ.
- **Dữ liệu cần chuẩn bị** (mỗi vé chỉ được một yêu cầu `PENDING`, nên cần vé riêng cho từng nhánh):
  - Vé A: buổi học ≥ 24 giờ tới, chưa đổi lần nào ➔ Nhánh 1 và 4 (thêm voucher nếu muốn thử tăng giá).
  - Vé B: đã đổi 1 lần (dùng hết lượt) ➔ Nhánh 2.
  - Vé C: buổi học bắt đầu < 24 giờ tới ➔ Nhánh 3 (vé Phase 2 đặt cho ngày mai thường rơi vào đây).
  - Vé D: buổi học đã bắt đầu, chưa điểm danh ➔ Nhánh 5.
  - Vé A, B, C đều thuộc chi nhánh của Staff test; yêu cầu thứ 3 cho P2-S05d xem bước đó.

---

### Bước 5c (P2-S05c): Staff Duyệt / Từ chối Yêu cầu Đổi lịch trong Hàng chờ (Browser 2)

> Căn cứ: `003.3 spec.md` FR-045a (sửa đổi 2026-11-17: hàng chờ do **Staff hoặc Manager** quyết định). Trang: `/settlement/change-requests` (menu **"Yêu cầu đổi vé"**, đứng trước tab **"Hỗ trợ đổi vé và hủy"**). Tiền điều kiện: có 2 yêu cầu `PENDING` tại chi nhánh của Staff, trên **2 vé khác nhau**: vé C (còn dưới 24 giờ, P2-S05b nhánh 3) và vé B (hết lượt đổi, nhánh 2); thêm 1 yêu cầu thứ 3 `PENDING` trên vé thứ ba cho P2-S05d.

- **(a) Quan sát & Dò tìm (Backstage Discovery)**:
  - Tại **Browser 2**, đăng nhập Staff `staff.test@mland.vn`. Nhân viên tìm mục **"Yêu cầu đổi vé"** trên menu bên: có thấy dễ không?
  - Mở trang: bộ lọc trạng thái là **ô chọn** (`PENDING`/`APPROVED`/`REJECTED`/`EXPIRED`, mặc định `PENDING`) kèm nút "Refresh", không phải tab; bảng có các cột Ticket, Type, Current session, Requested session, Blocked by, Price increase, Expires, Status, Decision.
  - Nhãn trang là **tiếng Anh** ("Change requests", "Status", "Details", "Approve", "Reject") trong khi menu là tiếng Việt: đây là hiện trạng đã biết, ghi nhận một lần như SOFT FAIL, không lặp lại ở từng bước.
  - Bấm **"Details"** ở một yêu cầu: hiển thị buổi hiện tại và buổi muốn đổi (ngày, giờ, chi nhánh), `Blocked by` hiển thị **bằng chữ**, không hiện mã thô (vé C: quá hạn 24 giờ; vé B: đã dùng lượt đổi), "Requested by" hiện **họ tên** người yêu cầu (nhân viên kèm "(Staff)"/"(Manager)", khách không tài khoản là "Walk-in guest"), không có mã yêu cầu, mã ca hay kênh, khoản tăng giá, hạn hết hạn. Nếu `Price increase` trống hoặc 0 mà yêu cầu có tăng giá, ghi là chưa xác minh.
- **(b) Thử nghiệm & Hành động (Action Execution)**:
  - **Nhánh 1 — Staff duyệt**: Với yêu cầu của vé C (còn dưới 24 giờ), bấm **"Approve"** ➔ hộp thoại xác nhận **không có ô lý do** ➔ xác nhận. Kỳ vọng: thông báo *"Change approved and applied."*, yêu cầu rời danh sách `PENDING`, sang `APPROVED`; hạn mức đổi của buổi mới giảm 1 (lúc duyệt, không phải lúc xếp hàng chờ). Duyệt có thể **thất bại** và yêu cầu vẫn `PENDING` không đổi dữ liệu (`INSUFFICIENT_CAPACITY`, `PACKAGE_NOT_AVAILABLE_AT_LOCATION`, `TICKET_FOLIO_NOT_OPEN`, `CONFIRM_INCREASE_REQUIRED`...): ghi lại mã nếu gặp.
  - **Nhánh 2 — Staff từ chối**: Với yêu cầu của vé B (hết lượt đổi), bấm **"Reject"** ➔ nhập lý do ➔ xác nhận. Kỳ vọng: *"Change rejected."*, sang `REJECTED`, `Decision reason` hiển thị lý do vừa nhập; vé **không đổi slot**.
  - **Nhánh 3 — Lý do trống (Abnormal)**: Thử xác nhận hộp thoại **Reject** khi để trống lý do ➔ hộp thoại hiện *"A reason is required."* và không gửi gì; yêu cầu vẫn `PENDING`. (Nếu gọi API trực tiếp với `REJECT` không lý do: `422 REASON_REQUIRED`; `APPROVE` không lý do được chấp nhận.)
  - **Nhánh 4 — Ngoài phạm vi (Abuse)**: Danh sách `PENDING` đã lọc theo chi nhánh của Staff, nên yêu cầu của chi nhánh khác **không hiện** với Staff này; qua giao diện không thể thử duyệt. Assertion: yêu cầu ngoài phạm vi (nếu có) không có trong danh sách của Staff. `403 LOCATION_SCOPE_DENIED` chỉ gặp khi gọi API trực tiếp bằng mã yêu cầu; không có dữ liệu hoặc không thử API thì ghi `NOT RUN`.
  - **Nhánh 5 — Chống bấm đúp**: Bấm **"Approve"** xác nhận hai lần liên tiếp ➔ nút xác nhận bị khóa khi đang gửi, chỉ ghi một quyết định và một lần đổi. Nếu một lần gửi lọt thêm: `409 CHANGE_REQUEST_NOT_PENDING`, không đổi lần hai.
- **(c) Đối chiếu phía Khách (Browser 1)**:
  - Sau khi duyệt, mã vé và QR của vé **không đổi** so với trước (cùng assertion như P2-S05b).
  - Khách (Browser 1, **hội viên đã đăng nhập**) mở lại thẻ vé trong "Tra cứu vé & Booking": yêu cầu được duyệt hiện *"Quản lý đã duyệt yêu cầu đổi lịch."* và vé hiển thị slot mới; yêu cầu bị từ chối hiện *"Quản lý đã từ chối yêu cầu đổi lịch."* và vé giữ slot cũ. Thông báo luôn ghi "Quản lý" kể cả khi **Staff** quyết định (ghi nhận là hiện trạng). Trang tự cập nhật mỗi ~20 giây, khi tải lại và khi quay lại tab (tạm dừng lúc tab ẩn); khách vãng lai không có cập nhật tự động.
  - Số chỗ trống ca cũ tăng 1, ca mới giảm 1 sau khi duyệt.
  - **Assertion email theo quyết định (003.3 FR-064)**: yêu cầu được **duyệt** (P2-S05c nhánh 1) ➔ 1 email "MLAND – Đã đổi lịch vé {mãVé}" như P2-S05b; yêu cầu bị **từ chối** (P2-S05c nhánh 2) ➔ 1 email "MLAND – Yêu cầu đổi lịch vé {mãVé} không được chấp nhận", nêu lịch cũ được giữ và QR hiện tại vẫn dùng được, **không lộ lý do nội bộ của Staff**; vé giữ nguyên lịch. Người nhận vé riêng (nếu có) nhận bản tương ứng. Duyệt hai lần (bấm đúp) hoặc replay quyết định không tạo email thứ hai.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Approve báo thành công nhưng vé không đổi slot (hoặc đổi hai lần); Reject làm vé bị đổi; Staff duyệt được yêu cầu ngoài phạm vi chi nhánh.
  - **SOFT FAIL**: Thông báo cho khách chậm hơn ~20 giây khi tab đang mở; thiếu `Blocked by`. (Nhãn tiếng Anh của trang hàng chờ là hiện trạng đã biết, ghi một lần.)

---

### Bước 5d (P2-S05d): Manager Duyệt Yêu cầu Đổi lịch & Kiểm tra Admin Bị Chặn (Browser 2)

- **(a) Quan sát & Đăng nhập Quản lý**:
  - Tại Browser 2, đăng nhập Manager `manager.test@mland.vn`, mở **"Yêu cầu đổi vé"** (`/settlement/change-requests`).
  - Manager có phạm vi toàn cục: thấy yêu cầu `PENDING` của mọi chi nhánh, kể cả yêu cầu mà Staff không thấy hoặc không được quyết ở P2-S05c nhánh 4.
- **(b) Thử nghiệm & Hành động**:
  - **Nhánh 1 — Manager duyệt**: Mở **"Details"** yêu cầu thứ 3 ➔ **"Approve"** ➔ xác nhận (không có ô lý do). Kỳ vọng: *"Change approved and applied."*, sang `APPROVED`, có `Decided` (thời điểm); `Decision reason` là "—". **Người quyết định không hiển thị trên giao diện**: muốn xác minh "quyết định ghi dưới tài khoản Manager" phải kiểm tra qua API hoặc audit, nếu không thì ghi `NOT RUN`.
  - **Nhánh 2 — Yêu cầu đã quyết (Boundary)**: Đổi ô chọn trạng thái sang `APPROVED` rồi `REJECTED`: các yêu cầu ở P2-S05c và P2-S05d hiện đúng trạng thái; yêu cầu đã quyết **không còn nút Approve/Reject**.
  - **Nhánh 3 — Tranh chấp quyết định (Race)**: Nếu Staff vừa quyết một yêu cầu, Manager mở trang chưa tải lại và bấm Approve cùng yêu cầu ➔ `409 CHANGE_REQUEST_NOT_PENDING` (hoặc `CHANGE_REQUEST_EXPIRED` nếu đã hết hạn), **không** đổi lần hai.
  - **Nhánh 4 — Hết hạn**: Yêu cầu quá `Expires` được đánh dấu `EXPIRED` khi tải danh sách; chọn trạng thái `EXPIRED` thấy yêu cầu đó, không còn nút quyết định, và quyết định qua API trả `409 CHANGE_REQUEST_EXPIRED`. Hạn là thời điểm sớm hơn giữa giờ bắt đầu buổi cũ và giờ bắt đầu các buổi đích tương lai; không tạo được dữ liệu hết hạn thì ghi `NOT RUN`.
    + **Email hết hạn (003.3 FR-064)**: Khi yêu cầu qua `Expires`, bộ lập lịch (mặc định 60 giây, `workshop.settlement.change-request-expiry-interval-ms`) tự đánh dấu `EXPIRED` **không cần mở hàng chờ**; chờ tối đa ~2 phút rồi kiểm tra hộp thư: đúng 1 email "MLAND – Yêu cầu đổi lịch vé {mãVé} đã hết hạn xử lý", nêu lịch cũ được giữ và QR vẫn dùng được. Mở hàng chờ sau đó không tạo thêm email hay thêm dòng audit `TICKET_CHANGE_REQUEST_EXPIRED`. **HARD STOP**: thiếu email, hoặc gửi hai email cho một yêu cầu.
  - **Nhánh 5 — Admin bị chặn (Abuse)**: Đăng nhập tài khoản ADMIN (nếu có) ➔ không có mục **"Yêu cầu đổi vé"** trên menu; mở thẳng `/settlement/change-requests` chỉ thấy *"Admin accounts have no part in ticket changes…"*; gọi API trả `403 STAFF_OR_MANAGER_REQUIRED`. Không có tài khoản Admin test thì ghi `NOT RUN`.
- **(c) Chốt trạng thái**:
  - Ghi lại `CHANGE_REQUEST_APPROVED_REF`, `CHANGE_REQUEST_REJECTED_REF` và slot cuối cùng của vé dùng cho check-in ở P2-S06.
  - Ghi lại `CHANGE_EMAIL_APPLIED_COUNT`, `CHANGE_EMAIL_REJECTED_COUNT`, `CHANGE_EMAIL_EXPIRED_COUNT` (số email đã thấy trong hộp thư cho từng kết cục, kỳ vọng mỗi loại 1 cho mỗi người nhận).
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Manager duyệt mà vé không đổi; yêu cầu đã quyết bị quyết lần hai; Admin truy cập được hàng chờ.
  - **SOFT FAIL**: Lỗi tranh chấp hiển thị chung chung. (Giao diện không hiện người quyết định là hiện trạng đã biết, không tính lỗi.)

---

### Bước 6 (P2-S06): Thao tác Nhân viên Xưởng — Quét vé Điểm danh tại Xưởng (Browser 2)

- **(a) Quan sát & Dò tìm của Nhân viên (Backstage Discovery)**:
  - Mở **Browser 2 (Cửa sổ Ẩn danh)** tại `.../login`.
  - Đăng nhập tài khoản Staff: `staff.test@mland.vn` / `StaffTest@123456`.
  - Nhân viên nhìn vào thanh menu: Có thấy mục **"Điều hành xưởng & Check-in"** (`/operator/workshop-operations`) không?
- **(b) Thử nghiệm Đối nghịch & Bẫy vé giả (Adversarial Probing)**:
  - Mở màn hình check-in: nút **"Quét bằng camera"** và ô nhập mã vé nằm ở bàn check-in (003.2 FR-057; không còn ô nhập mã QR). Bấm hàng vé trong danh sách điểm danh cũng check-in được sau khi xác nhận (FR-058).
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
- `NEW_SLOT_REFERENCE`: Slot mới sau khi đổi ở P2-S05b / P2-S05c / P2-S05d (Staff check-in ở P2-S06 dùng vé này).
- `CHANGE_REQUEST_APPROVED_REF`, `CHANGE_REQUEST_REJECTED_REF`: mã yêu cầu đã duyệt / từ chối.
- `WORKSHOP_SESSION_ID`: ID ca học đã hoàn tất (dùng để viết Review ở Phase 5).
- `LOYALTY_POINTS_EARNED`: **50 Điểm thưởng Loyalty** đã nằm trong ví của Member, sẵn sàng để tiêu ở Phase 3.

---

## 7. Mẫu Báo cáo Hiện tượng Bất thường (Unexpected Finding Template)

```markdown
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
- Latest status: PASS_WITH_FINDINGS. 9/10 PASS (C1–C7 chạy lần này; W2, W3 theo các lần chạy trước của QuocHH), W1 khác kỳ vọng kịch bản (đúng spec 003.2, chờ Khương chốt). Lỗi mới: BUG-E2E-0024 (MAJOR), 0025, 0026 (MINOR)
- Executed at: 2026-11-26 03:40–03:52 (giờ Việt Nam) cho W1, C1–C7; W2 ngày 26/11 00:10; W3 ngày 25/11 (loyalty ghi lúc 21:59)
- Executed by: Hồ Hoàng Quốc (QuocHH)
- Browser tool: khung trình duyệt tích hợp của Claude desktop app (không dùng `browsermcp`), viewport 1280x900; người chạy tự đăng nhập Firebase cho từng vai trò. Thanh toán qua trang giả lập `PAYMENT_SIMULATOR_ENABLED=true`, không dùng VNPay sandbox
- Environment: local, frontend `http://localhost:5173`, backend `http://localhost:8080` (khởi động lại 03:38 từ develop `2826ce79`, có bản sửa BUG-E2E-0021/0022), DB `ppswbs_workshop_test_20261029` (MySQL 8.4), `APP_MAIL_ADAPTER=smtp`
- Test data:
  + Member `customer.test@mland.vn` (account 48), Staff `staff.test@mland.vn` (account 47, chi nhánh 1 Hoàn Kiếm), Manager `manager.test@mland.vn` (account 46), Admin `admin.test@mland.vn` (account 45). Gói 146 Signature Workshop - Brass
  + Vé W1: `GRP-86AE0B241EAD430CAD15-L0-T1` (đặt mới, làm buổi 26/11 16:00 Hoàn Kiếm `bc2ff85a…` kín 8/8)
  + Vé A: `GRP-F246349E9CC84E88BCFF-L0-T1` (C1) và `-L0-T2` (C4), đặt mới buổi 28/11 09:30 Hoàn Kiếm `bc2ff8bf…`
  + Vé B: `GRP-9FBC6B68D4CF4244B1CE-L0-T2` (đã đổi 1 lần ngày 26/11 00:55, buổi 30/11 13:00 Hoàn Kiếm)
  + Vé C: `GRP-960A60B3C5EF45148D37-L0-T4` (buổi 26/11 16:00, còn dưới 24 giờ)
  + Email: kiểm qua bảng `notification_deliveries` (loại, trạng thái `SENT`, số dòng theo mã đổi/mã yêu cầu); không mở hộp thư thật
- Actual result:
  + **W1 — P2-S01(b) Buổi hết chỗ: KHÁC KỲ VỌNG KỊCH BẢN (đúng spec 003.2).** Trước khi đặt, buổi 26/11 16:00 hiện "Còn 1 chỗ". Đặt 1 vé (`GRP-86AE…`, cọc 200.000 ₫, CONFIRMED) thì buổi kín 8/8. Xem lại danh sách ca: buổi này **không còn hiển thị**, không có thẻ mờ và nhãn "Hết chỗ" như kịch bản đòi. Mở thẳng URL đặt chỗ với buổi đó: trang báo "Có ca bạn chọn không còn khả dụng. Vui lòng quay lại trang chi tiết gói để chọn ca khác.", không đặt được. Bộ chọn đổi lịch cũng không liệt kê buổi kín (FR-044a). Kỳ vọng "nhãn Hết chỗ" chờ Khương chốt; ghi đúng hành vi quan sát được. Phát hiện phụ BUG-E2E-0025: ở bề rộng 580px ảnh gói che danh sách ca, không bấm chọn được
  + **C1 — P2-S05b nhánh 1: PASS.** Vé A T1 (28/11 09:30, chưa đổi) chọn "Đổi lịch trước buổi học" sang CN 29/11 13:00 Hoàn Kiếm (bộ chọn ghi "còn 8 chỗ · còn 2 lượt đổi"). Bấm "Xác nhận đổi lịch" → "Đã đổi lịch cho vé GRP-F246349E9CC84E88BCFF-L0-T1.", thẻ vé hiện buổi mới. Mã vé và QR `QR-89ef3c1e-9dba-4408-8fd9-bcf11bccbcd5` giữ nguyên trước/sau. DB: 1 dòng `TCHG-78012b5c…` ADVANCE counted=1, tăng giá 0; buổi mới `reschedule_quota_used` 0→1, buổi cũ nhả chỗ. Xem lại bộ chọn: buổi 29/11 13:00 thành "còn 7 chỗ · còn 1 lượt đổi". Email: đúng 1 `TICKET_CHANGED` SENT (id 319). Không có tăng giá nên không kiểm folio
  + **C2 — P2-S05b nhánh 2: PASS.** Vé B mở form đổi: ghi chú "Vé này đã dùng hết lần đổi lịch; mọi lựa chọn cần quản lý duyệt.", mỗi dòng "Cần quản lý duyệt (đã dùng lần đổi của vé)", nút "Gửi yêu cầu đổi lịch". Gửi sang 01/12 13:00 → `POST …/changes` **202**, "Đã gửi yêu cầu đổi lịch cho quản lý duyệt. Mã yêu cầu: CHG-REQ-f0570237-e7f3-42e6-b034-d6d7840c063b". DB: 1 yêu cầu PENDING `CHANGE_ALLOWANCE_EXHAUSTED`, vé không có lần đổi mới
  + **C3 — P2-S05b nhánh 3: PASS.** Vé C: ghi chú "Đã quá hạn đổi lịch trước buổi học (24 giờ); lựa chọn cần quản lý duyệt.", dòng "Cần quản lý duyệt (còn dưới 24 giờ)". Gửi sang 26/11 09:30 Hoàn Kiếm → **202**, mã `CHG-REQ-867a211b-c056-47cc-8700-795cb1b72c3a`, PENDING `ADVANCE_CHANGE_WINDOW_EXPIRED`, vé chưa đổi. Sau lần gửi đầu, giao diện ẩn nút "Đổi lịch" nên lần gửi thứ hai làm qua API bằng phiên Member (Idempotency-Key mới, đích khác) → **409 `CHANGE_REQUEST_PENDING`**
  + **C4 — P2-S05b nhánh 4: PASS.** Vé A T2 chọn 28/11 13:00 Hoàn Kiếm, bấm đúp "Xác nhận đổi lịch": nút chuyển "Đang gửi…" và khóa, chỉ có **1** `POST …/L0-T2/changes` (200). DB: đúng 1 dòng đổi `TCHG-7c07d0c3…` counted=1, **0** yêu cầu chờ duyệt, 1 email `TICKET_CHANGED` (id 320)
  + **C5 — P2-S05c nhánh 1 và 3: PASS.** Staff mở "Vé và hoàn tiền › Yêu cầu đổi vé": 2 yêu cầu PENDING (vé C, vé B), cột "Bị chặn bởi" hiện mã thô. Bấm "Duyệt" để trống lý do → hộp thoại báo "Cần nhập lý do." và không gửi. Nhập lý do, duyệt → "Đã duyệt và áp dụng thay đổi." DB: APPROVED, `decided_by_account_id=47`, lý do lưu đúng, `applied_change_reference=TCHG-6a649195…`; vé C sang 26/11 09:30, QR `QR-509857b4-0f00-4fc0-bf11-1942296f7240` giữ nguyên, điểm danh NOT_CHECKED_IN; 1 email `TICKET_CHANGED` (id 321). Trang hàng chờ nay là tiếng Việt (kịch bản ghi nhãn tiếng Anh "Change approved and applied." là hiện trạng cũ)
  + **C6 — P2-S05c nhánh 2: PASS (kèm 2 lỗi phụ).** Staff "Từ chối" vé B có lý do → "Đã từ chối thay đổi."; DB REJECTED, lý do lưu đúng, vé B giữ 30/11 13:00; 1 email `TICKET_CHANGE_REJECTED` (id 322). Lỗi phụ: hộp thoại từ chối ghi câu của hộp thoại duyệt (BUG-E2E-0026); chọn bộ lọc "Đã duyệt"/"Đã từ chối" thì Staff bị đẩy về trang chủ "Bạn không có quyền truy cập chức năng này." (BUG-E2E-0024). Huy hiệu "Yêu cầu đổi vé" vẫn hiện 2 ngay sau khi hàng chờ trống (chưa kiểm lại sau khi tải trang)
  + **C7 — P2-S05d nhánh 5: PASS.** Admin: menu không có mục "Yêu cầu đổi vé"; "Vé và hoàn tiền" chỉ có tab "Hủy"/"Đổi". Mở thẳng `/settlement/change-requests` → "Tài khoản Admin không tham gia xử lý đổi vé. Staff và Manager xử lý hàng đợi này." API: `GET /api/v1/workshop-change-requests?status=PENDING` → **403 `STAFF_OR_MANAGER_REQUIRED`**; `POST /api/v1/workshop-change-requests/CHG-REQ-f0570237…/decisions` → **403 `STAFF_OR_MANAGER_REQUIRED`**, yêu cầu giữ REJECTED
  + **W2 — P2-S06(c) Check-in, không check-in 2 lần: PASS (QuocHH chạy nhiều lần trước, không chạy lại lần này).** Lần gần nhất: vé `GRP-960A60B3C5EF45148D37-L0-T1`, buổi 26/11 16:00 Hoàn Kiếm (`bc2ff85a…`), check-in lúc 00:10 ngày 26/11 (DB: `attendance_state=CHECKED_IN`); quét lần hai hiện "Vé đã check-in trước đó lúc 00:10 26 thg 11, 2026.", danh sách vẫn 1/8 (xem bước kiểm lại của BUG-E2E-0021, sửa ở `c2c8746c`). Lần chạy này không có ảnh mới
  + **W3 — P2-S07 Quyết toán cuối buổi cộng điểm: PASS (QuocHH chạy nhiều lần trước, không chạy lại lần này).** App không có trang `/operator/workshop-settlement` và không có trạng thái buổi `COMPLETED`: chốt điểm danh ở trang điều hành buổi (buổi sang `ATTENDANCE_FINALIZED`), quyết toán theo folio từng vé ở `/settlement`; điểm danh của vé chỉ chốt sau `attendance_finalized_at` + 24 giờ, nên các lần chạy trước đã lùi mốc này trong DB. Dấu vết trong DB lúc 03:55 ngày 26/11: folio `SETTLE-BILL-f881ab23…` (`GRP-5E849940018F442D8C9C-L1-T1`), `SETTLE-BILL-2aec0bed…` (`GRP-D510B6FEF4094CD58F98-L0-T1`), `SETTLE-BILL-467cbf60…` (`GRP-796F5D8BA3AC49818D2E-L0-T1`) đều `FINALIZED`, `loyalty_processed_at` 25/11 21:59; `loyalty_point_ledger` có các dòng EARN tương ứng (id 40, 41: +79, +85 cho member 36; id 42: +80 cho member 50). Lần chạy này không có ảnh mới
- Evidence (thư mục `screenshots/` cạnh file này):
  + W1: [P2-W1-01--buoi-16h-con-1-cho.jpg](screenshots/P2-W1-01--buoi-16h-con-1-cho.jpg), [P2-W1-02--buoi-16h-kin-bi-an.jpg](screenshots/P2-W1-02--buoi-16h-kin-bi-an.jpg), [P2-W1-03--url-truc-tiep-buoi-kin-bi-chan.jpg](screenshots/P2-W1-03--url-truc-tiep-buoi-kin-bi-chan.jpg), [P2-W1-00--viewport-580-anh-goi-che-danh-sach-ca.jpg](screenshots/P2-W1-00--viewport-580-anh-goi-che-danh-sach-ca.jpg)
  + C1: [P2-C1-01--ve-A-truoc-doi.jpg](screenshots/P2-C1-01--ve-A-truoc-doi.jpg), [P2-C1-02--chon-buoi-moi.jpg](screenshots/P2-C1-02--chon-buoi-moi.jpg), [P2-C1-03--da-doi-lich.jpg](screenshots/P2-C1-03--da-doi-lich.jpg)
  + C2: [P2-C2-01--ve-B-het-luot-can-duyet.jpg](screenshots/P2-C2-01--ve-B-het-luot-can-duyet.jpg), [P2-C2-02--da-gui-yeu-cau.jpg](screenshots/P2-C2-02--da-gui-yeu-cau.jpg)
  + C3: [P2-C3-01--ve-C-qua-24h-can-duyet.jpg](screenshots/P2-C3-01--ve-C-qua-24h-can-duyet.jpg), [P2-C3-02--da-gui-yeu-cau.jpg](screenshots/P2-C3-02--da-gui-yeu-cau.jpg)
  + C4: [P2-C4-01--truoc-bam-dup.jpg](screenshots/P2-C4-01--truoc-bam-dup.jpg), [P2-C4-02--nut-khoa-dang-gui.jpg](screenshots/P2-C4-02--nut-khoa-dang-gui.jpg), [P2-C4-03--da-doi-1-lan.jpg](screenshots/P2-C4-03--da-doi-1-lan.jpg)
  + C5: [P2-C5-01--hang-cho-staff-2-yeu-cau.jpg](screenshots/P2-C5-01--hang-cho-staff-2-yeu-cau.jpg), [P2-C5-02--ly-do-trong-bi-chan.jpg](screenshots/P2-C5-02--ly-do-trong-bi-chan.jpg), [P2-C5-03--da-duyet.jpg](screenshots/P2-C5-03--da-duyet.jpg)
  + C6: [P2-C6-01--hop-thoai-tu-choi.jpg](screenshots/P2-C6-01--hop-thoai-tu-choi.jpg), [P2-C6-02--da-tu-choi-badge-van-2.jpg](screenshots/P2-C6-02--da-tu-choi-badge-van-2.jpg), [P2-C6-03--loc-da-tu-choi-bi-day-ve-trang-chu.jpg](screenshots/P2-C6-03--loc-da-tu-choi-bi-day-ve-trang-chu.jpg)
  + C7: [P2-C7-01--admin-menu-khong-co-yeu-cau-doi-ve.jpg](screenshots/P2-C7-01--admin-menu-khong-co-yeu-cau-doi-ve.jpg), [P2-C7-02--admin-ve-va-hoan-tien-chi-co-huy-doi.jpg](screenshots/P2-C7-02--admin-ve-va-hoan-tien-chi-co-huy-doi.jpg), [P2-C7-03--admin-mo-thang-hang-cho-bi-chan.jpg](screenshots/P2-C7-03--admin-mo-thang-hang-cho-bi-chan.jpg)
- Bug reference: BUG-E2E-0024 (C6, MAJOR, Frontend), BUG-E2E-0025 (W1, MINOR, Frontend), BUG-E2E-0026 (C6, MINOR, Frontend)
<!-- EXECUTION:END -->

### Lần chạy trước: 2026-11-17

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
