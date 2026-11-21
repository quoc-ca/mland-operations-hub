# Sổ Theo Dõi Lỗi Hệ Thống (Defect & Bug Tracking Log)

File nhật ký theo dõi lỗi tập trung do `ppswbs-testing/AGENT.md` quản lý.  
Mọi lỗi phát hiện trong quá trình kiểm thử Unit, Integration hoặc E2E bằng `browsermcp` đều phải được ghi nhận tại đây để bàn giao cho các bên liên quan xử lý.

---

## Danh sách lỗi đang theo dõi (Active Defects)

### Remediation update — 2026-11-21

- `BUG-E2E-0018`: code remediation is present in the current branch through
  `V191__widen_custom_order_payment_kind.sql` and the aligned JPA column length.
  Status remains **RETEST_REQUIRED** until Flyway upgrade, payment-owner
  persistence, VNPay callback and duplicate-callback tests pass on the target
  database.
- `BUG-CONTRACT-0002`: OpenAPI, `APIs.md` and public alias security/CORS rules
  have been aligned for Guest and Member creation. Status remains
  **RETEST_REQUIRED** until OpenAPI validation and anonymous Guest runtime tests
  pass for both `/design-requests` and `/custom-design-requests`.
- `BUG-E2E-0016` and `BUG-E2E-0017` remain **OPEN**. No expected price,
  difficulty or archive outcome has been changed without a source-of-truth
  decision and a fresh execution record.

## BUG-E2E-0018 — OPEN

- Ngày: 2026-11-21
- Phát hiện bởi: Senior QA & System Verification Engineer
- Feature: 004-design-request-and-human-review & Custom Manufacturing
- Kịch bản liên quan: [E2E-004-029](ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/e2e/E2E-004-029--member-custom-order-full-payment.md)
- Mức độ nghiêm trọng: CRITICAL / BLOCKER (Không thể khởi tạo cổng thanh toán VNPay cho đơn hàng gia công tùy chỉnh)
- Phân loại / Gán cho: Backend / Database Schema
- Mô tả lỗi: Khi Member bấm nút "Thanh toán toàn bộ ... qua VNPay" trên trang chi tiết đơn hàng gia công tùy chỉnh (`/account/my-custom-orders/:id`), Frontend gọi `POST /api/v1/members/me/custom-orders/{id}/payment`. Tại Backend, `CustomOrderService.startFullPayment` gọi `paymentOwnerRegistry.register(..., PaymentOwnerKind.CUSTOM_ORDER_FULL_PAYMENT, ...)`. Cột `payment_kind` trong bảng cơ sở dữ liệu `payment_owner_registrations` được định nghĩa là `varchar(24)`, trong khi chuỗi giá trị enum `CUSTOM_ORDER_FULL_PAYMENT` có độ dài 25 ký tự. Do đó, MySQL ném ngoại lệ `MysqlDataTruncation: Data truncation: Data too long for column 'payment_kind' at row 1`, khiến giao dịch bị trả về HTTP 500 `INTERNAL_SERVER_ERROR` và người dùng không thể chuyển tiếp sang VNPay Sandbox để thanh toán.
- Các bước tái hiện:
  1. Đăng nhập tài khoản Member (`member.test@mland.vn` / `Member@123456`).
  2. Truy cập `http://localhost:5173/account/my-custom-orders`.
  3. Mở đơn hàng gia công đã được Manager duyệt báo giá (trạng thái `FULL_PAYMENT_PENDING`, ví dụ `CO-D8FD507B`).
  4. Bấm nút "Thanh toán toàn bộ 4.500.000 đ qua VNPay".
- Kết quả mong đợi: Backend đăng ký giao dịch thành công và trả về HTTP 201 kèm URL thanh toán VNPay (`paymentUrl`), trình duyệt điều hướng sang cổng thanh toán VNPay Sandbox.
- Thực tế quan sát: Giao diện console báo lỗi `Payment start failed: ApiError: An unexpected error occurred`, backend Spring Boot ghi nhận `com.mysql.cj.jdbc.exceptions.MysqlDataTruncation: Data truncation: Data too long for column 'payment_kind' at row 1`.
- Bằng chứng:
  - Backend log: `Caused by: com.mysql.cj.jdbc.exceptions.MysqlDataTruncation: Data truncation: Data too long for column 'payment_kind' at row 1`
  - MySQL Schema: `payment_owner_registrations.payment_kind` là `varchar(24)`.
  - Enum value: `CUSTOM_ORDER_FULL_PAYMENT` dài 25 ký tự.

## BUG-CONTRACT-0002 — OPEN

- Ngày: 2026-11-21
- Phát hiện bởi: Senior QA & System Verification Engineer
- Feature: 004-design-request-and-human-review
- Kịch bản liên quan: [E2E-004-018](ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/e2e/E2E-004-018--guest-create-request-description.md)
- Mức độ nghiêm trọng: MINOR / CONTRACT-DEFECT
- Phân loại / Gán cho: Spec / OpenAPI Contract
- Mô tả lỗi: Tài liệu hợp đồng OpenAPI định nghĩa `POST /api/v1/design-requests` (và `/api/v1/custom-design-requests`) là Member-only (yêu cầu Bearer token xác thực). Tuy nhiên, `spec.md` của Feature-004 quy định rõ ràng: cả Khách vãng lai (Guest) và Thành viên (Member) đều được phép gửi yêu cầu thiết kế, trong đó Guest được quản lý qua daily rate limit (5 request/ngày) và nhận `X-Guest-Tracking-Token`. Implementation thực tế tại Backend và Frontend tuân thủ đúng theo `spec.md` (hỗ trợ cả Guest và Member). Bản mô tả OpenAPI cần được cập nhật để gỡ bỏ ràng buộc Member-only.
- Kết quả mong đợi: OpenAPI contract phản ánh chính xác phân quyền: Guest và Member đều có thể tạo design request.



## BUG-E2E-0016 — OPEN

- Ngày: 2026-11-17
- Phát hiện bởi: Senior QA & System Verification Engineer (Master E2E Phase 4 trên Mock Prod)
- Feature: 004-ring-design-configuration (FE-04 - Cấu hình nhẫn Workshop)
- Kịch bản liên quan: [JOURNEY-PHASE-4](master-e2e/JOURNEY-PHASE-4--custom-design-and-manufacturing.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Spec
- Mô tả lỗi: Các giá trị kỳ vọng về chi phí và độ khó trong kịch bản Phase 4 không khớp kết quả thẩm định của cùng cấu hình. Cần đối chiếu đặc tả/công thức và cập nhật đúng nguồn; chưa đủ bằng chứng để kết luận phía ứng dụng hay expected value bị sai.
- Các bước tái hiện:
  1. Đăng nhập bằng tài khoản MEMBER và mở gói `PKG-COUPLE-SILVER`.
  2. Chọn mẫu `MOD-WS-SILVER-CLASSIC`, Bạc Ý 925, dáng Tròn cổ điển, size 16, 1 viên CZ Lam 2 mm, khắc chữ, độ dày 1.8 mm và độ rộng 3.5 mm.
  3. Bấm "Lưu & Thẩm định" và xem các chỉ số kỹ thuật.
- Kết quả mong đợi: Theo kịch bản, chi phí tham khảo khoảng 372.400 đ, khối lượng 4.02 g và độ khó 2.0/10 (Dễ).
- Thực tế quan sát: Chi phí tham khảo 502.400 đ và độ khó 3.3/10 (Trung bình); khối lượng 4.02 g khớp. Chênh lệch chi phí là 130.000 đ.
- Bằng chứng:
  - Screenshot: Không lưu được tệp; ảnh Computer Use chỉ xuất hiện inline trong phiên.
  - Thiết kế: `DSG-8874b27b-5d24-4575-b410-b8c7af856b73`

---

## BUG-E2E-0017 — OPEN

- Ngày: 2026-11-17
- Phát hiện bởi: Senior QA & System Verification Engineer (Master E2E Phase 4 trên Mock Prod)
- Feature: 004-ring-design-configuration (FE-04 - Cấu hình nhẫn Workshop)
- Kịch bản liên quan: [JOURNEY-PHASE-4](master-e2e/JOURNEY-PHASE-4--custom-design-and-manufacturing.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Trên trang kết quả thẩm định, bấm "Lưu trữ mẫu thiết kế" không mở hộp thoại xác nhận và không tạo thay đổi trạng thái quan sát được. Thiết kế vẫn ở trạng thái "ĐÃ THẨM ĐỊNH", khiến không thể hoàn tất bước lưu trữ/sổ tay cá nhân.
- Các bước tái hiện:
  1. Mở trang kết quả của thiết kế `DSG-8874b27b-5d24-4575-b410-b8c7af856b73`.
  2. Bấm "Lưu trữ mẫu thiết kế".
- Kết quả mong đợi: Hiện hộp thoại xác nhận; sau khi chấp nhận, badge chuyển thành "Đã lưu trữ".
- Thực tế quan sát: Không có hộp thoại hoặc trạng thái mới; nút lưu trữ vẫn hiện và `/my/designs` chưa được kiểm tra.
- Bằng chứng:
  - Screenshot: Không lưu được tệp; ảnh Computer Use chỉ xuất hiện inline trong phiên.

---

## BUG-E2E-0014 — OPEN

- Ngày: 2026-11-17
- Phát hiện bởi: Senior QA & System Verification Engineer (Master E2E Phase 3 trên Mock Prod - UX Audit)
- Feature: 008-retail-order-and-fulfillment (FE-08 - Thanh toán đơn bán lẻ `/member/checkout`)
- Kịch bản liên quan: [JOURNEY-PHASE-3](master-e2e/JOURNEY-PHASE-3--retail-loyalty-and-fulfillment.md), [UX-AUDIT-LOG](master-e2e/UX-AUDIT-LOG.md)
- Mức độ nghiêm trọng: CRITICAL (Gây bế tắc luồng thanh toán)
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Khi người dùng bấm nút "Đặt hàng" trên trang `/member/checkout`, nếu backend trả về HTTP 409 `RETAIL_CONFLICT` (`Checkout quote changed or expired; review a new quote`), frontend hoàn toàn KHÔNG hiển thị thông báo lỗi nào (không toast, không alert, không modal). Nút "Đặt hàng" chỉ dừng loading rồi trở lại trạng thái bình thường. Người dùng tưởng nút bị liệt hoặc click trượt nên bấm liên tục (Rage clicks), dẫn đến bị kẹt vô phương cứu chữa.
- Các bước tái hiện:
  1. Thêm một sản phẩm vào giỏ hàng và tiến hành vào trang `/member/checkout`.
  2. Chọn phương thức giao hàng GHTK hoặc Pick-up, để quote hết hạn hoặc thay đổi.
  3. Bấm "Đặt hàng".
- Kết quả mong đợi: Hiển thị thông báo hoặc Modal: "Báo giá đơn hàng đã thay đổi hoặc hết hạn. Vui lòng bấm [Cập nhật lại] để tiếp tục thanh toán".
- Thực tế quan sát: Bị nuốt lỗi hoàn toàn (Silent Failure), không có bất kỳ phản hồi thị giác nào.
- Bằng chứng:
  - Screenshot: ![Silent 409 Defect](master-e2e/screenshots/ux_p3_06_silent_409_defect.png)
  - Network Request #785: `POST /api/v1/ready-ring-orders` -> Status `409` Body: `{"code":"RETAIL_CONFLICT","message":"Checkout quote changed or expired; review a new quote"}`

---

## BUG-E2E-0015 — OPEN

- Ngày: 2026-11-17
- Phát hiện bởi: Senior QA & System Verification Engineer (Master E2E Phase 2 trên Mock Prod - UX Audit)
- Feature: 003.1-workshop-booking (FE-03 - Giữ chỗ & Thanh toán cọc Workshop)
- Kịch bản liên quan: [JOURNEY-PHASE-2](master-e2e/JOURNEY-PHASE-2--workshop-booking-and-fulfillment.md), [UX-AUDIT-LOG](master-e2e/UX-AUDIT-LOG.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Frontend Layout / CSS Sticky
- Mô tả lỗi: Trên màn hình xác nhận giữ chỗ workshop (`/workshops/:id/book`), sau khi hệ thống chuyển sang trạng thái "Đã giữ chỗ cho 1 vé" kèm thanh đếm ngược 10 phút, nút CTA chính "Thanh toán ... qua VNPay" bị thanh `div.main-topbar-wrapper` che khuất một phần hoặc toàn bộ ở vị trí cuộn tự nhiên. Trình duyệt tự động hoá báo lỗi: `button is covered by <div.main-topbar-wrapper>. Close or dismiss it first`. Người dùng trên laptop nếu không cuộn trang lên xuống sẽ click nhầm vào navbar thay vì nút thanh toán.
- Các bước tái hiện:
  1. Vào đặt chỗ 1 ca workshop bất kỳ, nhập số điện thoại và bấm "Xác nhận và giữ chỗ".
  2. Quan sát nút "Thanh toán ... qua VNPay".
- Kết quả mong đợi: Nút CTA nằm hoàn toàn trong vùng hiển thị an toàn, có khoảng cách đệm (padding/margin) cách xa header cố định.
- Thực tế quan sát: Header cố định đè lên vị trí nút bấm, chặn sự kiện click tự nhiên.
- Bằng chứng:
  - Screenshot: ![Hold banner header cover](master-e2e/screenshots/ux_p2_06_hold_banner.png)

---



## BUG-E2E-0010 — OPEN

- Ngày: 2026-11-17
- Phát hiện bởi: Senior QA & System Verification Engineer (Master E2E Phase 3 trên Mock Prod)
- Feature: 008-retail-order-and-fulfillment (FE-08 - Giỏ hàng)
- Kịch bản liên quan: [JOURNEY-PHASE-3](master-e2e/JOURNEY-PHASE-3--retail-loyalty-and-fulfillment.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Thanh Header cố định (`header.main-topbar`) che khuất hoàn toàn checkbox "Chọn tất cả sản phẩm" ở đầu danh sách giỏ hàng (`/member/cart`). Khi kiểm thử qua trình duyệt, lệnh click bị chặn với lỗi `is covered by <header.main-topbar>`. Người dùng trên laptop không thể click chọn nhanh mà bắt buộc phải cuộn chuột xuống mới thao tác được.
- Các bước tái hiện:
  1. Thêm 1 sản phẩm vào giỏ hàng và truy cập `https://mland-operations-hub-git-develop-mland-team.vercel.app/member/cart`.
  2. Tại vị trí cuộn trang ở đỉnh (top: 0), bấm vào checkbox "Chọn tất cả sản phẩm" đầu tiên.
- Kết quả mong đợi: Checkbox nằm dưới Header an toàn và nhận click bình thường.
- Thực tế quan sát: Header đè lên checkbox, không nhận click.
- Bằng chứng:
  - Screenshot: ![Header che checkbox](master-e2e/screenshots/P3-04--cart-selected.jpg)

---

## BUG-E2E-0011 — OPEN

- Ngày: 2026-11-17
- Phát hiện bởi: Senior QA & System Verification Engineer (Master E2E Phase 2 trên Mock Prod)
- Feature: 003.1-workshop-booking (VNPay Integration)
- Kịch bản liên quan: [JOURNEY-PHASE-2](master-e2e/JOURNEY-PHASE-2--workshop-booking-and-fulfillment.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Backend / DevOps Environment Config
- Mô tả lỗi: Sau khi thanh toán cọc Workshop qua cổng VNPay Sandbox, VNPay redirect về URL ngrok trung gian (`affront-copier-barrier.ngrok-free.dev/payment/vnpay/return`) thay vì domain Vercel chính thức. Vì dùng ngrok free tier, người dùng bị vướng màn hình cảnh báo `ERR_NGROK_6024` và phải bấm nút "Visit Site" thủ công mới trở về được website Mland. Trong khi đó, luồng đơn hàng bán lẻ (Phase 3) lại redirect trực tiếp về Vercel (`member/orders`) rất mượt mà.
- Các bước tái hiện:
  1. Đặt cọc 1 vé Workshop và chuyển sang cổng VNPay Sandbox.
  2. Hoàn tất thanh toán OTP `123456`.
- Kết quả mong đợi: Cổng VNPay chuyển hướng thẳng về `https://mland-operations-hub-git-develop-mland-team.vercel.app/...`.
- Thực tế quan sát: Bị đá sang domain ngrok và hiện trang cảnh báo `ERR_NGROK_6024`.
- Bằng chứng:
  - Screenshot: ![Ngrok warning](master-e2e/screenshots/P2-06--ngrok-warning.jpg)

---

## BUG-E2E-0012 — OPEN

- Ngày: 2026-11-17
- Phát hiện bởi: Senior QA & System Verification Engineer (Master E2E Phase 3 trên Mock Prod)
- Feature: 002-product-management (Trang chi tiết sản phẩm `/products/:id`)
- Kịch bản liên quan: [JOURNEY-PHASE-3](master-e2e/JOURNEY-PHASE-3--retail-loyalty-and-fulfillment.md)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Trên trang chi tiết sản phẩm nhẫn (`/products/13`), combobox "Chi nhánh" hiển thị option đầu tiên ("Mland Hà Nội – Hoàn Kiếm") nhưng giá trị state nội bộ chưa được khởi tạo. Người dùng chọn Size nhẫn và bấm nút "Thêm Vào Giỏ Hàng" hoặc "Mua Ngay" thì không có phản ứng điều hướng, toast lỗi "Vui lòng chọn chi nhánh." biến mất quá nhanh và không có viền đỏ cảnh báo cạnh ô Chi nhánh, gây cảm giác tưởng như nút bấm bị đơ liệt.
- Các bước tái hiện:
  1. Mở `https://mland-operations-hub-git-develop-mland-team.vercel.app/products/13`.
  2. Chọn "Size 10" (không click vào dropdown Chi nhánh vì thấy nó đã hiển thị Hoàn Kiếm).
  3. Bấm "Thêm Vào Giỏ Hàng" hoặc "Mua Ngay".
- Kết quả mong đợi: Hoặc tự động nhận chi nhánh đang hiển thị làm mặc định, hoặc hiển thị lỗi viền đỏ trực quan tại ô Chi nhánh.
- Thực tế quan sát: Nút bấm không phản hồi, giỏ hàng vẫn giữ nguyên `(0)`.
- Bằng chứng:
  - Screenshot: ![Product detail](master-e2e/screenshots/P3-02--product-detail.jpg)

---

## BUG-E2E-0013 — OPEN

- Ngày: 2026-11-17
- Phát hiện bởi: Senior QA & System Verification Engineer (Master E2E Phase 1 trên Mock Prod)
- Feature: 001-account-management (i18n Trang chủ)
- Kịch bản liên quan: [JOURNEY-PHASE-1](master-e2e/JOURNEY-PHASE-1--guest-discovery-and-onboarding.md)
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Khi chọn ngôn ngữ "EN" trên combobox Language, chỉ một số nút bấm của phần Ready Rings đổi sang tiếng Anh ("See all rings", "Add to cart"), trong khi toàn bộ tiêu đề chính ("MỘT CHIẾC NHẪN, MỘT CÂU CHUYỆN RIÊNG.", "HỆ SINH THÁI DỊCH VỤ..."), các nút CTA chính ("ĐẶT WORKSHOP NGAY", "KHÁM PHÁ READY RINGS") và menu Navbar "Khám phá" vẫn hiển thị tiếng Việt, gây tình trạng song ngữ nửa nạc nửa mỡ.
- Bằng chứng:
  - Screenshot: ![Home EN](master-e2e/screenshots/P1-02--home-en.jpg)

---

## BUG-E2E-0009 — OPEN

- Ngày: 2026-11-15
- Phát hiện bởi: QA Agent (kiểm chứng cuối 006, `ppswbs-spec/features/006-loyalty-promotion/verification.md` mục 64)
- Feature: 003.1-workshop-booking (FE-03); liên quan 006 FR-012 (voucher đã lưu)
- Kịch bản liên quan: chưa có kịch bản E2E; phát hiện khi kiểm màn thật T109 của 006
- Mức độ nghiêm trọng: MINOR
- Phân loại / Gán cho: Frontend — owner FE-03 (Hồ Hoàng Quốc)
- Mô tả lỗi: Picker "Voucher MLAND" trên trang đặt workshop gợi ý mọi voucher Member đã lưu còn `ACTIVE`, kể cả voucher phạm vi Sản phẩm và voucher workshop giới hạn gói khác. `ppswbs_frontend/src/workshops/CartBenefitPicker.jsx:26` chỉ lọc `savedVouchers` theo `availability`, không theo phạm vi (`scope`/`packageIds`). Member chọn mã đó thì báo giá của 006 sẽ từ chối.
- Các bước tái hiện:
  1. Đăng nhập Member có voucher đã lưu gồm cả voucher sản phẩm (ví dụ `MLGIAM50K`, `MLHK12`) và voucher workshop chỉ cho gói #1 (`SV8B634DC5CE`).
  2. Mở `http://localhost:5173/workshops/2/book`.
  3. Bấm "Voucher MLAND — Chọn hoặc nhập mã", xem mục "Voucher đề xuất".
- Kết quả mong đợi: Chỉ gợi ý voucher phạm vi WORKSHOP áp được cho gói đang đặt (không có `packageIds` hoặc `packageIds` chứa gói hiện tại); hoặc nếu vẫn liệt kê thì đánh dấu không dùng được cho gói này.
- Thực tế quan sát: Danh sách có `MLGIAM50K · Trong Voucher của tôi`, `MLHK12 · Trong Voucher của tôi` (voucher sản phẩm) và `SV8B634DC5CE · Trong Voucher của tôi` (chỉ gói #1) khi đặt gói 2, cạnh `WR1579F4EEF0` và `ITBBF8230857`.
- Bằng chứng:
  - Màn thật 2026-11-15 trên `ppswbs_dev` (backend `:8080` chạy Flyway tắt, frontend `:5173`); kết quả `find` trên trình duyệt ghi ở verification 006 mục 64. Không lưu screenshot vào repo.
  - Code: `CartBenefitPicker.jsx:26` `savedVouchers.filter(item => item.availability === 'ACTIVE')`.

---

## Danh sách lỗi đã đóng / nghiệm thu (Resolved & Closed Defects)

## BUG-E2E-0010 — CLOSED

- Ngày mở: 2026-11-18
- Ngày đóng / nghiệm thu: 2026-11-18
- Phát hiện bởi: User & Senior QA / System Verification Engineer
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 011-review-management
- Kịch bản liên quan: [E2E-011-006](../ppswbs-spec/features/011-review-management/test-results/E2E-011-006--review-edit-ownership-authorization.md)
- Mức độ nghiêm trọng: MAJOR / IDOR (Insecure Direct Object Reference)
- Phân loại / Gán cho: Frontend & Backend (Review Detail & Edit Form Authorization Guard)
- Mô tả lỗi ban đầu: Người dùng đã đăng nhập có thể truy cập vào màn hình chỉnh sửa đánh giá (`/reviews/:reviewId/edit`) của người dùng khác bằng cách sửa URL hoặc bấm nút "Chỉnh sửa đánh giá của bạn" (nút này luôn render cho bất kỳ user nào đã đăng nhập tại trang chi tiết `/reviews/:reviewId`, bất kể ai là người tạo đánh giá). Mặc dù Backend `PATCH` từ chối cập nhật khi kiểm tra quyền, giao diện Frontend vẫn để lộ form sửa với toàn bộ nội dung đánh giá của người khác và cho phép gửi request.
- Các bước tái hiện ban đầu:
  1. Đăng nhập tài khoản Member (`member.test@mland.vn` / `Member@123456`).
  2. Mở đánh giá của người dùng khác tại `http://localhost:5173/reviews/rev-101-5star`.
  3. Thấy nút "Chỉnh sửa đánh giá của bạn" vẫn hiển thị.
  4. Bấm nút hoặc truy cập trực tiếp `http://localhost:5173/reviews/rev-101-5star/edit`: Form chỉnh sửa mở ra và pre-fill toàn bộ nội dung của người khác.
- Biện pháp khắc phục đã triển khai:
  1. **Backend**:
     - Cập nhật `ReviewDto` bổ sung trường `private Boolean canEdit;`.
     - Trong `ReviewApplicationService.java`, logic kiểm tra quyền xác định: `boolean owner = actor != null && actor.role() == ActorRole.MEMBER && review.getOwnerType() == OwnerType.MEMBER && String.valueOf(actor.accountId()).equals(review.getOwnerReference());`.
     - Tính toán `boolean canEdit = owner && review.getStatus() != ReviewStatus.REMOVED;` và truyền qua `mapToReviewDto(...)` để trả về cho client.
  2. **Frontend**:
     - `ReviewDetailPage.jsx`:
       * Truyền cờ xác thực vào `api.getReview(reviewId, status === 'signed-in')` để gửi kèm Bearer token giúp backend nhận diện `actor`.
       * Ẩn hoàn toàn nút chỉnh sửa nếu `!review?.canEdit`: `{status === 'signed-in' && Boolean(review?.canEdit) && (<Link to={...}>...)}`.
     - `EditReviewPage.jsx`:
       * Gọi `api.getReview(reviewId, true)` kèm Bearer token.
       * Thiết lập Route / Component Guard: Nếu `!resource.data?.canEdit`, chặn render `ReviewForm` và hiển thị màn hình thông báo từ chối truy cập:
         *"Bạn không có quyền chỉnh sửa đánh giá này. Chỉ chủ sở hữu đánh giá mới có thể chỉnh sửa."* kèm nút điều hướng quay lại.
- Kết quả kiểm chứng lại (QA Re-test 2026-11-18 qua Browser MCP):
  - Kịch bản `E2E-011-006` được thực thi trên Chrome thực tế bằng `browsermcp`:
    + Đăng nhập `member.test@mland.vn` và mở đánh giá của người khác (`/reviews/rev-101-5star`): Nút "Chỉnh sửa đánh giá của bạn" hoàn toàn biến mất (chỉ còn nút "Quay lại").
    + Cố tình đổi URL sang `/reviews/rev-101-5star/edit`: Form chỉnh sửa bị chặn hoàn toàn, hiển thị thông báo lỗi quyền sở hữu tiếng Việt rõ ràng, không để lộ dữ liệu form.
    + Mở đánh giá của chính mình (`/reviews/cc6446de-4851-4c95-8b81-7965213da398`): Nút "Chỉnh sửa đánh giá của bạn" hiển thị bình thường.
    + Bấm sửa: Form mở ra đầy đủ thông tin, chỉnh sửa nội dung và lưu thay đổi thành công (HTTP 200).
  - Trạng thái kiểm chứng: **PASS 100%**.
  - Bằng chứng screenshot:
    + [E2E-011-006--forbidden-edit-url-tampering.jpg](../ppswbs-spec/features/011-review-management/test-results/screenshots/E2E-011-006--forbidden-edit-url-tampering.jpg)
    + [E2E-011-006--other-user-no-edit-btn.jpg](../ppswbs-spec/features/011-review-management/test-results/screenshots/E2E-011-006--other-user-no-edit-btn.jpg)
    + [E2E-011-006--owner-edit-allowed.jpg](../ppswbs-spec/features/011-review-management/test-results/screenshots/E2E-011-006--owner-edit-allowed.jpg)
- Trạng thái xử lý: **CLOSED**

## BUG-E2E-0008 — CLOSED

- Ngày mở: 2026-11-14
- Ngày đóng / nghiệm thu: 2026-11-14
- Phát hiện bởi: Senior QA & System Verification Engineer
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 001-account-management
- Kịch bản liên quan: [E2E-001-002](../ppswbs-spec/features/001-account-management/test-results/E2E-001-002--member-address-book-flow.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Frontend & Backend (Chưa triển khai cập nhật đặc tả sổ địa chỉ 2 cấp ngày 2026-11-14)
- Mô tả lỗi ban đầu: Biểu mẫu modal "Thêm địa chỉ nhận hàng mới" trên Frontend và API lưu trữ Backend hoạt động theo mô hình 4 cấp GHTK cũ (Tỉnh -> Huyện -> Xã -> Thôn/Ấp), tự động pre-fill họ tên từ Profile, không tuân thủ luồng nhập liệu tuần tự Shopee-style. Mâu thuẫn với bản sửa đổi đặc tả ngày 2026-11-14 (FR-036, FR-037, FR-038, FR-041, FR-042) và OpenAPI schema `account-management.openapi.yaml`.
- Biện pháp khắc phục đã triển khai (Commit `b2b88bb6`):
  1. Backend: Bổ sung Flyway migration `V93__two_level_administrative_divisions_and_shipping_addresses.sql` và entities `AdministrativeProvince`, `AdministrativeCommune`, `AdministrativeDirectoryVersion`; refactor `MemberAddress` lưu mã và tên 2 cấp (`provinceCode`, `communeCode`, `detailAddress`); cập nhật `AdministrativeDivisionService` cung cấp API tra cứu địa giới 2 cấp và `MemberAddressService` lưu trữ snapshot 2 cấp.
  2. Frontend: Refactor `AddressModal.tsx` mở form ở trạng thái trống hoàn toàn (FR-036), kiểm soát luồng nhập liệu tuần tự Shopee-style theo cờ validation (`Họ tên` -> `SĐT` -> `Tỉnh/Thành` -> `Phường/Xã` -> `Địa chỉ chi tiết`) (FR-037), hỗ trợ ô lọc nhanh không dấu cho Phường/Xã (FR-041), và tự động reset các trường cấp con khi thay đổi cấp cha.
- Kết quả kiểm chứng lại (QA Re-test 2026-11-14 qua Browser MCP & Automated Suites):
  - Kịch bản `E2E-001-002` chạy lại trên Chrome thực tế qua `browsermcp`:
    + Form mở ra ở trạng thái trống, không pre-fill họ tên từ Profile (PASS FR-036).
    + Nhập liệu tuần tự: SĐT chỉ mở khóa khi nhập Họ tên; Tỉnh/Thành chỉ mở khóa khi nhập SĐT di động hợp lệ (chặn số bàn 0241234567, chuẩn hóa +84 912 345 678); Phường/Xã chỉ mở khóa khi chọn Tỉnh/Thành; Địa chỉ cụ thể chỉ mở khóa khi chọn Phường/Xã (PASS FR-037).
    + Ô tìm kiếm Phường/Xã lọc nhanh không dấu (nhập "trong" lọc ra "Phường Hàng Trống") (PASS FR-041).
    + Chọn đổi Tỉnh từ Hà Nội sang TP.HCM: Phường/Xã và Địa chỉ cụ thể tự động bị xóa và khóa lại (PASS FR-037 cascade reset).
    + Lưu địa chỉ thành công, hiển thị đúng địa giới 2 cấp: "Số 2 Lê Duẩn, Phường Bến Nghé, Thành phố Hồ Chí Minh" kèm badge "Mặc định", badge "Văn phòng" và quota "Đã lưu 1 trên 10 địa chỉ".
    + Xóa địa chỉ duy nhất thành công và trở lại trạng thái danh sách trống.
  - Automated Tests:
    + Frontend Vitest: 7 test suites, 94/94 tests PASS (bao gồm 15/15 tests trong `addressBook.test.tsx`).
    + Backend JUnit: `MemberAddressServiceTest` PASS 2/2 tests (BUILD SUCCESS).
- Trạng thái xử lý: **CLOSED**

## BUG-E2E-0007 — CLOSED

- Ngày mở: 2026-11-13
- Ngày đóng / nghiệm thu: 2026-11-13
- Phát hiện bởi: Senior QA & System Verification Engineer
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 012-workshop-ring-design-configuration
- Kịch bản liên quan: [E2E-012-005](../ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/E2E-012-005--guest-expiry-and-member-ownership.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Frontend (Auth Context Consumption in DesignEvaluationPage & RingConfigurationPage)
- Mô tả lỗi ban đầu: Trong `DesignEvaluationPage.jsx` (dòng 15-16) và `RingConfigurationPage.jsx` (dòng 17-18), mã nguồn sử dụng `const { user } = useAuth(); const isAuthenticated = Boolean(user);`. Tuy nhiên, `AuthProvider.jsx` trả về AuthContext không chứa thuộc tính `user` mà chứa `{ status, userId, source, signOut, subscribeOutcome }`. Do đó, `user` luôn là `undefined` và `isAuthenticated` luôn là `false`. Khi hội viên đã đăng nhập truy cập vào trang thẩm định thiết kế cá nhân (`/workshops/:packageRef/design/:designId`), hàm `api.getDesign(designId, null, isAuthenticated)` được gọi với `isAuthenticated = false`, không đính kèm header `Authorization: Bearer <token>`. Backend `WorkshopRingDesignController` không xác định được actor hội viên (`resolveMemberAccountId() == null`), coi request là khách vãng lai và ném ngoại lệ HTTP 403 `DESIGN_ACCESS_DENIED`, khiến hội viên không thể xem chi tiết hoặc lưu trữ (archive) thiết kế nhẫn của mình.
- Các bước tái hiện ban đầu:
  1. Đăng nhập tài khoản Member (`member.test@mland.vn` / `Member@123456`) tại `http://localhost:5173/login`.
  2. Điều hướng đến `http://localhost:5173/my/designs` (thấy danh sách thiết kế hợp lệ `DSG-DEMO-MEMBER-01`).
  3. Bấm "Xem chi tiết →" để điều hướng đến `http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/DSG-DEMO-MEMBER-01`.
- Biện pháp khắc phục đã triển khai:
  1. Sửa `ppswbs_frontend/src/features/ringcustomization/ui/DesignEvaluationPage.jsx` và `RingConfigurationPage.jsx`:
     - Thay `const { user } = useAuth(); const isAuthenticated = Boolean(user);`
     - Bằng `const { status, userId } = useAuth(); const isAuthenticated = status === 'signed-in' || Boolean(userId);`.
  2. Đảm bảo khi hội viên đã đăng nhập, `isAuthenticated` là `true` và đính kèm header `Authorization: Bearer <token>` vào request `GET /api/v1/workshop-designs/:id`.
- Kết quả kiểm chứng lại (QA Re-test 2026-11-13 qua Browser MCP):
  - Kịch bản `E2E-012-005` được chạy lại trên Chrome thực tế bằng `browsermcp`:
    + Member mở `/my/designs` thấy `DSG-DEMO-MEMBER-01`.
    + Bấm "Xem chi tiết →" mở `/workshops/PKG-COUPLE-SILVER/design/DSG-DEMO-MEMBER-01` thành công (HTTP 200 kèm Bearer token), hiển thị đầy đủ thông số nhẫn, độ khó 2.0/10 và nút "Lưu trữ mẫu thiết kế" (`archive-design-btn`).
    + Bấm "Lưu trữ mẫu thiết kế" gửi `POST /api/v1/workshop-designs/DSG-DEMO-MEMBER-01/archive` thành công với `If-Match-Version: 2`, status chuyển thành `ARCHIVED` trong DB và hiển thị badge "ĐÃ LƯU TRỮ".
  - Trạng thái: **PASS 100%**.
  - Bằng chứng screenshot: [E2E-012-005--success.jpg](../ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-005--success.jpg)
- Trạng thái xử lý: **CLOSED**

## BUG-E2E-0006 — CLOSED

- Ngày mở: 2026-11-13
- Ngày đóng / nghiệm thu: 2026-11-13
- Phát hiện bởi: Senior QA & System Verification Engineer
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 011-review-management
- Kịch bản liên quan: [E2E-011-004](../ppswbs-spec/features/011-review-management/test-results/E2E-011-004--staff-manager-moderation.md), [E2E-011-005](../ppswbs-spec/features/011-review-management/test-results/E2E-011-005--manager-reply-and-aggregate.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Backend & Frontend (Review Detail Authorization & Moderation Detail API)
- Mô tả lỗi ban đầu: Trong `ReviewApplicationService.getReviewDetail`, logic kiểm tra `if (review.getStatus() != ReviewStatus.PUBLIC && !owner)` chỉ cho phép `owner` (Member tác giả) đọc chi tiết review khi review không ở trạng thái `PUBLIC`. Khi review đã được Staff/Manager chuyển sang `HIDDEN` hoặc `REMOVED`, màn hình kiểm duyệt `/review-moderation/:reviewId` gọi `GET /api/v1/reviews/:reviewId` bị backend ném lỗi HTTP 404 `REVIEW_NOT_FOUND` (đồng thời Frontend `reviewManagementApi.getReview` mặc định không truyền `auth: true` và `IdentityGateFilter` bypass không trích xuất actor). Dẫn đến việc Manager không thể mở chi tiết đánh giá đã ẩn để khôi phục (`RESTORE`) hay phản hồi (`createReply`).
- Các bước tái hiện ban đầu:
  1. Thực hiện ẩn một đánh giá (chuyển sang `status = 'HIDDEN'`).
  2. Đăng nhập tài khoản Quản lý (`manager.test@mland.vn` / `ManagerTest@123456`).
  3. Mở trang `/review-moderation`, lọc trạng thái "HIDDEN" và bấm "Open".
- Biện pháp khắc phục đã triển khai:
  1. Backend `IdentityGateFilter.java`: Bổ sung điều kiện chỉ bypass xác thực cho `/api/v1/reviews/*` khi request KHÔNG có header `Authorization: Bearer`. Khi có header xác thực, request vẫn đi qua `AccountTokenAuthenticationFilter` để khởi tạo `ActorContext`.
  2. Backend `ReviewApplicationService.java`: Bổ sung điều kiện `isInternalModerator = actor != null && (actor.role() == AccountRole.STAFF || actor.role() == AccountRole.MANAGER)` cho phép nhân viên và quản lý nội bộ xem chi tiết review ngay cả khi review không ở trạng thái `PUBLIC`.
  3. Frontend `ModerationDetailPage.jsx`: Cập nhật gọi `api.getReview(reviewId, true)` (truyền cờ `auth = true`) để gửi kèm token xác thực.
- Kết quả kiểm chứng lại (QA Re-test 2026-11-13 qua Browser MCP):
  - Kịch bản `E2E-011-004` được chạy lại trên Chrome thực tế bằng `browsermcp`:
    + Staff ẩn review (`status = HIDDEN`) thành công (HTTP 200).
    + Staff bấm Gỡ đánh giá bị từ chối chính xác với HTTP 403 Forbidden (PASS RBAC).
    + Manager đăng nhập lọc "HIDDEN" và click "Open" mở trang `/review-moderation/:id` thành công (HTTP 200).
    + Manager bấm "Khôi phục công khai" (RESTORE) với lý do hợp lệ, backend xử lý thành công, status cập nhật sang `PUBLIC` trong DB.
  - Trạng thái: **PASS 100%**.
  - Bằng chứng screenshot: [E2E-011-004--moderation-success.jpg](../ppswbs-spec/features/011-review-management/test-results/screenshots/E2E-011-004--moderation-success.jpg)
- Trạng thái xử lý: **CLOSED**

## BUG-E2E-0005 — CLOSED

- Ngày mở: 2026-11-13
- Ngày đóng / nghiệm thu: 2026-11-13
- Phát hiện bởi: Senior QA & System Verification Engineer
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 004-ai-chatbox-analysis-and-suggestion
- Kịch bản liên quan: [E2E-004-002](../ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/E2E-004-002--member-design-request.md), [E2E-004-003](../ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/E2E-004-003--staff-manager-review.md)
- Mức độ nghiêm trọng: BLOCKER
- Phân loại / Gán cho: Backend (Database Schema / Flyway Migration)
- Mô tả lỗi ban đầu: Khi Member gửi yêu cầu phân tích thiết kế kèm ảnh và thỏa thuận xử lý ảnh (`POST /api/v1/design-requests`), Backend Spring Boot ném lỗi `DataIntegrityViolationException` và trả về HTTP 500. Bảng `temporary_image_references` (và các bảng phụ thuộc `design_analysis_results`, `human_review_decisions` từ `V4__ai_chatbox_and_design_review.sql`) vẫn giữ foreign key constraint cũ `fk_tir_request` tham chiếu tới bảng `booking_design_requests` thay vì bảng độc lập mới `design_requests` (được tạo ở `V62__independent_design_request.sql`).
- Các bước tái hiện ban đầu:
  1. Đăng nhập tài khoản Member (`member.test@mland.vn` / `Member@123456`) tại `http://localhost:5173/login`.
  2. Điều hướng đến `http://localhost:5173/design-requests/new`.
  3. Nhập mô tả ý tưởng, chọn tệp ảnh hợp lệ `ring-sample.png`, tích thỏa thuận consent và submit.
- Biện pháp khắc phục đã triển khai:
  1. Tạo append-only migration mới `ppswbs_backend/src/main/resources/db/migration/V119__independent_design_request_foreign_keys.sql`:
     - Xóa các foreign key constraint cũ trỏ về `booking_design_requests`.
     - Thêm các foreign key constraint mới `fk_tir_design_request`, `fk_dar_design_request`, `fk_hrd_design_request` trỏ về bảng độc lập `design_requests (id)`.
  2. Khởi động lại Spring Boot backend, Flyway tự động migrate thành công `119 | independent design request foreign keys | success = 1`.
- Kết quả kiểm chứng lại (QA Re-test 2026-11-13 qua Browser MCP):
  - Kịch bản `E2E-004-002` chạy lại trên Chrome thực tế bằng `browsermcp`:
    + Member tải ảnh `ring-sample.png`, tích consent, bấm "Gửi yêu cầu thiết kế".
    + Backend xử lý `POST /api/v1/design-requests` trả về HTTP 202 Accepted.
    + Giao diện chuyển hướng đến `/design-requests/:id`, hiển thị mã `DR-7B1BE554`, trạng thái `SUCCEEDED` / `NEED_REVIEW`, cùng toàn bộ candidate features và chỉ số chất lượng ảnh mà không để lộ raw provider payload.
  - Kịch bản `E2E-004-003` được unblock hoàn toàn:
    + Staff đăng nhập mở hàng đợi `/design-review-requests` thấy yêu cầu `DR-7B1BE554`.
    + Staff chọn quyết định `ACCEPTED`, nhập lý do và bấm xác nhận, lưu thành công vào `human_review_decisions` và chuyển `design_requests` sang `ACCEPTED` / `DESIGN_ACCEPTED`.
  - Trạng thái: **PASS 100%**.
  - Bằng chứng screenshot: [E2E-004-002--success.jpg](../ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/screenshots/E2E-004-002--success.jpg), [E2E-004-003--success.jpg](../ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/screenshots/E2E-004-003--success.jpg)
- Trạng thái xử lý: **CLOSED**

## BUG-E2E-0005 — CLOSED

- Ngày mở: 2026-11-14
- Ngày khắc phục: 2026-11-14
- Ngày đóng / nghiệm thu: 2026-11-14
- Phát hiện bởi: Senior QA & System Verification Engineer
- Khắc phục bởi: Senior React Frontend Implementation Engineer
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer (Phan Sơn)
- Feature: 001-account-management
- Kịch bản liên quan: [E2E-001-007](../ppswbs-spec/features/001-account-management/test-results/E2E-001-007--admin-role-catalogue-rbac-guard.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Frontend
- Mô tả lỗi ban đầu: Lệch trường dữ liệu hợp đồng (Contract Misalignment) giữa Frontend và Backend tại API `/api/v1/roles`. Backend trả về `{ code, displayName }` theo đúng đặc tả OpenAPI (`RoleEntryResponse`), nhưng Frontend `RoleCataloguePage.tsx` và `AccountManagementApiClient.ts` lại truy xuất `{ r.role, r.description }`. Kết quả là bảng danh mục 4 vai trò trên `/admin/roles` bị rỗng toàn bộ nội dung tên vai trò và mô tả quyền hạn.
- Các bước tái hiện:
  1. Đăng nhập tài khoản có quyền Admin (ví dụ: `qa-e2e-feat007@test.com`).
  2. Truy cập đường dẫn: `http://localhost:5173/admin/roles`.
  3. Quan sát bảng "Danh mục vai trò hệ thống (Fixed Roles)".
- Kết quả mong đợi: Bảng hiển thị 4 vai trò (MEMBER, STAFF, MANAGER, ADMIN) cùng mô tả phạm vi và quyền hạn tương ứng.
- Thực tế quan sát trước khi sửa: Bảng hiển thị 4 dòng nhưng cả cột "Vai trò" (badge rỗng) và cột "Mô tả phạm vi và quyền hạn" đều trắng tinh.
- Bằng chứng lỗi ban đầu:
  - API response từ `GET /api/v1/roles`: `{"roles":[{"code":"MEMBER","displayName":"Khách hàng thành viên / Member"},{"code":"STAFF","displayName":"Nhân viên cơ sở / Staff"},{"code":"MANAGER","displayName":"Quản lý cơ sở / Manager"},{"code":"ADMIN","displayName":"Quản trị viên / Admin"}]}`
  - DOM Table quan sát được: `<tr><td><span class="account-badge account-badge-readonly"></span></td><td></td></tr>`
- Nguyên nhân gốc rễ (Root Cause):
  - Đặc tả OpenAPI `account-management.openapi.yaml` định nghĩa `RoleEntryResponse` gồm 2 thuộc tính bắt buộc: `code` (chuỗi enum vai trò) và `displayName` (tên hiển thị vai trò).
  - Backend Spring Boot (`RoleCatalogueController.java`) trả về DTO chuẩn `{ roles: [{ code, displayName }] }`.
  - Frontend `RoleCataloguePage.tsx` và interface `RoleCatalogueEntry` trong `AccountManagementApiClient.ts` trước đó định nghĩa và bóc tách các trường phi tiêu chuẩn `{ role, description }`, dẫn đến `r.role` và `r.description` nhận giá trị `undefined` khi nhận payload thật từ Backend.
- Biện pháp khắc phục đã triển khai (Frontend Dev):
  1. `ppswbs_frontend/src/features/accountmanagement/api/AccountManagementApiClient.ts`:
     - Cập nhật interface `RoleCatalogueEntry` bổ sung đầy đủ trường OpenAPI chuẩn `code?: 'MEMBER' | 'STAFF' | 'MANAGER' | 'ADMIN'` và `displayName?: string`, đồng thời duy trì fallback tương thích ngược.
  2. `ppswbs_frontend/src/features/accountmanagement/components/RoleCataloguePage.tsx`:
     - Cập nhật rendering JSX bóc tách an toàn theo chuẩn hợp đồng: `const roleCode = r.code || r.role;` và `const roleDesc = r.displayName || r.description;`.
     - Hiển thị badge vai trò với `roleCode` và cột mô tả với `roleDesc`.
  3. `ppswbs_frontend/src/features/accountmanagement/tests/accountManagementComponents.test.tsx`:
     - Bổ sung unit/component test ca: `renders OpenAPI standard response with code and displayName properties (BUG-E2E-0005)`.
     - Toàn bộ 12 test trong suite component test và 92/92 test trong toàn feature `accountmanagement` đều PASS.
- Kết quả nghiệm thu thực tế của QA (QA Verification):
  - **Trình duyệt**: Live Chrome qua `browsermcp` tại `http://localhost:5173/admin/roles`.
  - **DOM Table trích xuất**:
    ```html
    <table class="account-table">
      <thead><tr><th style="width: 180px;">Vai trò</th><th>Mô tả phạm vi và quyền hạn</th></tr></thead>
      <tbody>
        <tr><td><span class="account-badge account-badge-readonly" style="font-size: 0.85rem;">MEMBER</span></td><td style="line-height: 1.6;">Khách hàng thành viên / Member</td></tr>
        <tr><td><span class="account-badge account-badge-readonly" style="font-size: 0.85rem;">STAFF</span></td><td style="line-height: 1.6;">Nhân viên cơ sở / Staff</td></tr>
        <tr><td><span class="account-badge account-badge-readonly" style="font-size: 0.85rem;">MANAGER</span></td><td style="line-height: 1.6;">Quản lý cơ sở / Manager</td></tr>
        <tr><td><span class="account-badge account-badge-readonly" style="font-size: 0.85rem;">ADMIN</span></td><td style="line-height: 1.6;">Quản trị viên / Admin</td></tr>
      </tbody>
    </table>
    ```
  - **Đánh giá**: Tất cả 4 vai trò cố định và mô tả hiển thị chính xác theo hợp đồng OpenAPI và Revision R-MGA.
- Trạng thái xử lý: **CLOSED**

---

## BUG-E2E-0004 — CLOSED

- Ngày mở: 2026-11-12
- Ngày đóng / nghiệm thu: 2026-11-12
- Phát hiện bởi: Senior QA & System Verification Engineer
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 012-workshop-ring-design-configuration
- Kịch bản liên quan: [E2E-012-003](../ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/E2E-012-003--save-evaluate-and-version-history.md), [E2E-012-005](../ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/E2E-012-005--guest-expiry-and-member-ownership.md)
- Mức độ nghiêm trọng: CRITICAL
- Phân loại / Gán cho: Full-Stack (Frontend & Backend)
- Mô tả lỗi ban đầu: Khi người dùng vãng lai (Guest) bấm nút "Lưu & Thẩm định" trên trang cấu hình nhẫn (`/workshops/:packageRef/design/configure`), Frontend gửi request lưu phiên bản mà không khởi tạo/đính kèm header `X-Guest-Design-Session`. Backend ném ngoại lệ `RingDesignException` nhưng không được xử lý chuẩn mà rơi vào `AccountExceptionHandler` trả về HTTP 500 Unhandled Error. Giao diện bị kẹt với thông báo "An unexpected error occurred".
- Các bước tái hiện ban đầu:
  1. Điều hướng đến `http://localhost:5173/workshops/PKG-COUPLE-01/design/configure?mode=BLANK`
  2. Chọn chất liệu Vàng 14K, Dáng xoắn vô cực, Size 12, Đính đá CZ Lam 2mm, Khắc chữ "Forever Together 2026".
  3. Bấm nút "Lưu & Thẩm định".
- Nguyên nhân gốc rễ (Root Cause):
  1. Backend `WorkshopRingDesignExceptionHandler` thiếu annotation `@Order(Ordered.HIGHEST_PRECEDENCE)`. Khi có ngoại lệ `RingDesignException`, Spring MVC ưu tiên gọi `AccountExceptionHandler` bao quát `basePackages = "edu.fpt.sep490_g22.ppswbs_backend"` dẫn đến fallback vào `Exception.class` và trả về HTTP 500.
  2. Khi khách vãng lai gọi `POST /api/v1/workshop-designs` tạo bản nháp mới, Backend đã sinh `GuestDesignSession` trong DB nhưng không trả về mã `sessionReference` cho Frontend qua DTO hoặc HTTP Header `X-Guest-Design-Session`. Do đó, request lưu phiên bản tiếp theo thiếu header phiên làm việc.
- Biện pháp khắc phục đã triển khai:
  1. Backend: Bổ sung `@Order(Ordered.HIGHEST_PRECEDENCE)` trên `WorkshopRingDesignExceptionHandler`. Bổ sung trường `guestSession` vào `DesignResponseDto` và trả về kèm header `X-Guest-Design-Session` tại `WorkshopRingDesignController.createWorkshopDesign`.
  2. Frontend: Cập nhật `useWorkshopDesignCommand.js` để tự động lưu `res.guestSession` vào `sessionStorage` qua `setStoredGuestSession`. Cập nhật `DesignEvaluationPage.jsx` để dùng `getErrorMessage(err)` ánh xạ chuẩn xác thông báo hết hạn phiên 20 phút bằng tiếng Việt.
- Kết quả kiểm chứng lại (QA Re-test 2026-11-12 qua Browser MCP):
  - Kịch bản `E2E-012-003` tái kiểm tra trên Chrome thực tế: Bấm "Lưu & Thẩm định" thành công chuyển sang `/design/{designId}`, hiển thị đầy đủ bảng chỉ số kỹ thuật và chi phí tham khảo ước tính (HTTP 201 + 200). Chuyển tiếp sang `/workshops/booking?...` đạt yêu cầu. Trạng thái: **PASS**. Bằng chứng JPEG: ![Thành công](../ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-003--success.jpg).
  - Kịch bản `E2E-012-005` (nhánh phiên hết hạn): Truy cập thiết kế với phiên hết hạn trả về HTTP 401 `DESIGN_SESSION_EXPIRED` và hiển thị alert tiếng Việt: *"Phiên làm việc của bạn đã hết hạn sau 20 phút không hoạt động. Vui lòng bắt đầu lại để tiếp tục."*. Trạng thái: **PASS**. Bằng chứng JPEG: ![Phiên hết hạn](../ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-005--session-expired.jpg).

## BUG-E2E-0004 — CLOSED

- Ngày mở: 2026-11-14
- Ngày khắc phục: 2026-11-14
- Ngày đóng / nghiệm thu: 2026-11-14
- Phát hiện bởi: Senior QA & System Verification Engineer
- Khắc phục bởi: Senior Java/Spring Backend Implementation Engineer & Database Architect
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 007-notification-management
- Kịch bản liên quan: [E2E-007-011](../ppswbs-spec/features/007-notification-management/test-results/e2e/E2E-007-011--redeliver-with-refreshed-credential.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Backend / Database Schema
- Mô tả lỗi ban đầu: 
  Endpoint `POST /api/v1/notification-requests/{id}/redeliver` trả về lỗi HTTP 500 (Internal Server Error) do xung đột ràng buộc toàn vẹn cơ sở dữ liệu `DataIntegrityViolationException`.
  Khi một yêu cầu gửi thông báo gốc (`original_request_id`) đã từng phát sinh một yêu cầu gửi lại (resend hoặc redeliver trước đó, ví dụ tạo request #1005 từ request gốc #1001), việc thực hiện thao tác redeliver (hoặc resend lần thứ 2) trên cùng request gốc #1001 sẽ cố gắng chèn một bản ghi mới với cùng bộ giá trị:
  `(business_event_reference, template_code, recipient_role, recipient_address_fingerprint, original_request_id = 1001)`.
  Ràng buộc `uq_nr_idempotency` trong Flyway migration `V111__notification_management.sql`:
  ```sql
  UNIQUE KEY uq_nr_idempotency (business_event_reference, template_code, recipient_role, recipient_address_fingerprint, original_request_id)
  ```
  từ chối bản ghi thứ 2, dẫn tới Crash 500 ở backend thay vì xử lý mượt mà hoặc cấp định danh/chu kỳ phân biệt.
- Biện pháp khắc phục đã triển khai:
  1. Flyway migration `V140__notification_original_request_idempotency.sql`:
     - Chuẩn hóa các chuỗi phả hệ derived request trỏ trực tiếp về root request.
     - Drop chỉ mục unique `uq_nr_idempotency`.
     - Bổ sung cột sinh `original_idempotency_scope TINYINT GENERATED ALWAYS AS (CASE WHEN original_request_id IS NULL THEN 1 ELSE NULL END) STORED`.
     - Tạo unique constraint mới `uq_nr_original_idempotency (business_event_reference, template_code, recipient_role, recipient_address_fingerprint, original_idempotency_scope)`. Do SQL bỏ qua duplicate NULLs, constraint chỉ bảo vệ tính duy nhất của request gốc và cho phép tạo không giới hạn derived requests.
  2. JPA Entity `NotificationRequest.java`: Bổ sung mapping cột sinh `@Column(name = "original_idempotency_scope", insertable = false, updatable = false)`.
  3. Bổ sung test tích hợp `NotificationManagementIT#verifyMultipleResendAndRedeliverOnSameRootSucceeds` (Task T034).
- Kết quả kiểm chứng lại (QA Re-test 2026-11-14 qua Browser MCP trên live Docker environment):
  - Kịch bản `E2E-007-011` được thực thi lại trên môi trường Docker cục bộ (`http://localhost:5174` và backend `http://localhost:8081`):
    + Quản lý mở chi tiết yêu cầu gốc #1001 (đã có request gửi lại trước đó).
    + Bấm "Gửi lại với link/vé mới", nhập lý do "Khách hàng liên hệ xin link mới do link 24h đã hết hạn" và xác nhận.
    + Backend xử lý thành công, trả về HTTP 201 Created và sinh request mới #1009 với `original_request_id = 1001`.
    + Không còn lỗi HTTP 500 hay `DataIntegrityViolationException`.
    + Bảng `notification_operational_audit` lưu vết chính xác hành động `REDELIVER_REFRESHED_CREDENTIAL`.
  - Trạng thái kiểm chứng: **PASS 100%**.
  - Bằng chứng screenshot: [E2E-007-011--redeliver-success.jpg](../ppswbs-spec/features/007-notification-management/test-results/e2e/screenshots/E2E-007-011--redeliver-success.jpg).
- Trạng thái xử lý: **CLOSED**


<!--
## BUG-E2E-0000 — OPEN

- Ngày: YYYY-MM-DD
- Phát hiện bởi: QA Agent (Tester)
- Feature: 003-workshop-management
- Kịch bản liên quan: [E2E-003-001](../ppswbs-spec/features/003-workshop-management/test-results/E2E-003-001--booking-flow.md)
- Mức độ nghiêm trọng: CRITICAL
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Nút "Xác nhận đặt lịch" bị vô hiệu hóa dù đã chọn đủ thời gian và số lượng người.
- Các bước tái hiện:
  1. goto http://localhost:5173/workshops/1
  2. click getByRole('button', { name: 'Chọn suất 09:00' })
  3. fill getByLabel('Số lượng khách') "2"
  4. Quan sát nút "Xác nhận đặt lịch"
- Kết quả mong đợi: Nút "Xác nhận đặt lịch" chuyển sang trạng thái enabled và cho phép click.
- Thực tế quan sát: Nút bị disabled vĩnh viễn, console log báo lỗi "Uncaught TypeError: selectedSlot.capacity is undefined".
- Bằng chứng:
  - Screenshot: ![Lỗi](../ppswbs-spec/features/003-workshop-management/test-results/screenshots/E2E-003-001--slot-disabled.jpg)
-->

## BUG-QA-0003 — RESOLVED

- Ngày phát hiện/khắc phục: 2026-11-06
- Phát hiện bởi: Senior QA & System Verification Engineer
- Feature: 001-account-management
- Phân loại / Gán cho: Test artifact / QA
- Mô tả: Các E2E artifact lịch sử ghi credential/định danh fixture trực tiếp và giữ trạng thái PASS dù shared session/route contract đã đổi.
- Tác động: Có nguy cơ lộ secret test và tạo acceptance sai cho revision R-SL.
- Khắc phục: Redact fixture/credential, đánh dấu evidence cũ là historical, đặt case revision thành `NOT_RUN`, và thêm E2E-001-013 đến E2E-001-020.
- Bằng chứng: Static scan không còn fixture credential hoặc current `PASS` trong `ppswbs-spec/features/001-account-management/test-results/`.

## BUG-E2E-0002 — CLOSED

- Ngày mở: 2026-11-05
- Ngày đóng: 2026-11-05
- Phát hiện bởi: QA Agent & User Verification
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 001-account-management
- Kịch bản liên quan: [E2E-001-008](../ppswbs-spec/features/001-account-management/test-results/E2E-001-008--google-signin-account-collision.md)
- Mức độ nghiêm trọng: CRITICAL
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Luồng Google Sign-In redirect không kích hoạt thông báo xử lý lỗi xung đột tài khoản khi quay trở lại trang đăng nhập `/login` trên môi trường thực tế. Khi người dùng chọn tài khoản Google `minikie456@gmail.com` (trùng email đã có mật khẩu), trình duyệt quay lại trang `/login` nhưng hoàn toàn không hiển thị thông báo cảnh báo nào trong vòng 5 giây (xảy ra hiện tượng Silent Failure).
- Các bước tái hiện:
  1. Mở trang `http://localhost:5173/login`.
  2. Bấm nút "Đăng nhập với Google".
  3. Tại màn hình Google OAuth, chọn tài khoản `minikie456@gmail.com`.
  4. Trình duyệt chuyển hướng quay lại `http://localhost:5173/login`.
  5. Quan sát phản hồi giao diện trong vòng 5 giây.
- Kết quả mong đợi: Trong vòng tối đa 5 giây, giao diện phải hiển thị thông báo alert:
  "Email này đã được đăng ký bằng Mật khẩu. Vui lòng đăng nhập bằng Email và Mật khẩu gốc."
- Thực tế quan sát trước khi sửa: Giao diện im lặng (Silent Failure), form đăng nhập hiển thị bình thường không có bất kỳ dòng thông báo lỗi nào, khiến người dùng không biết đăng nhập thất bại vì lý do gì.
- Bằng chứng lỗi ban đầu:
  - Screenshot: ![Lỗi im lặng không thông báo](../ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-008--google-collision-silent-fail.jpg)
- Biện pháp khắc phục (Frontend Dev):
  1. Nguyên nhân gốc rễ: Chrome 115+ kích hoạt tính năng Phân vùng bộ nhớ (Storage Partitioning / third-party storage blocking) đối với iframe ẩn của Firebase Auth trên localhost, dẫn đến `getRedirectResult()` bị nuốt token và luôn trả về `null` trên localhost.
  2. Triển khai phương thức `signInWithGooglePopup()` trong `FirebaseAuthClient.ts` và sử dụng trong `AuthPage.tsx` khi chạy trên trình duyệt web:
     - Cho phép nhận phản hồi xác thực trực tiếp qua `postMessage` trong popup mà không bị chặn bởi phân vùng bộ nhớ iframe cross-origin.
  3. Cơ chế phát hiện xung đột & bảo vệ tài khoản (FR-001, FR-002, FR-022):
     - Kiểm tra nếu user trả về đã tồn tại provider `password`: Hệ thống nhận diện xung đột phương thức đăng nhập, lập tức gọi `firebaseAuthClient.signOut()`, hủy hoàn toàn phiên và kích hoạt thông báo tiếng Việt:
       "Email này đã được đăng ký bằng Mật khẩu. Vui lòng đăng nhập bằng Email và Mật khẩu gốc."
     - Tuyệt đối không gọi hàm bootstrap backend `bootstrapOwnAccount()`.
- Kết quả kiểm chứng lại (QA Re-test 2026-11-05):
  - Kịch bản `E2E-001-008` được thực thi lại trên trình duyệt Chrome thực tế bằng `browsermcp`.
  - Kết quả: **`PASS 100%`**. Thông báo cảnh báo xuất hiện ngay lập tức trong vòng < 2 giây sau khi xác nhận tài khoản Google (đáp ứng xuất sắc tiêu chí <= 5 giây của người dùng).
  - Trạng thái phiên: `currentUser = null` (Guest), không phát sinh bất kỳ request trái phép nào.
  - Bằng chứng khắc phục:
    + Screenshot: ![Màn hình cảnh báo xung đột Google hợp lệ](../ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-008--google-collision.jpg)
- Trạng thái xử lý: **CLOSED**

## BUG-E2E-0001 — CLOSED

- Ngày mở: 2026-11-05
- Ngày đóng: 2026-11-05
- Phát hiện bởi: QA Agent (Senior QA & System Verification Engineer)
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 001-account-management
- Kịch bản liên quan: [E2E-001-006](../ppswbs-spec/features/001-account-management/test-results/E2E-001-006--admin-accounts-rbac-guard.md), [E2E-001-007](../ppswbs-spec/features/001-account-management/test-results/E2E-001-007--admin-role-catalogue-rbac-guard.md)
- Mức độ nghiêm trọng: CRITICAL
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Thiếu Route Guard phân quyền vai trò (Role-based Route Guard) ở tầng Frontend. Tài khoản người dùng vai trò `MEMBER` khi truy cập trực tiếp các đường dẫn quản trị nội bộ (`/admin/accounts` và `/admin/roles`) vẫn render toàn bộ giao diện quản trị Admin (Header, Admin Navigation Tabs, Nút tạo tài khoản nội bộ, Form bộ lọc và cấu trúc bảng), thay vì phải bị chặn hoàn toàn hoặc chuyển hướng về trang chủ (`/`) hoặc màn hình 403 Unauthorized.
- Các bước tái hiện:
  1. Đăng nhập hệ thống với tài khoản Member: `minikie456@gmail.com` / `123123123`.
  2. Truy cập trực tiếp đường dẫn: `http://localhost:5173/admin/accounts` hoặc `http://localhost:5173/admin/roles`.
  3. Quan sát giao diện hiển thị trên trình duyệt.
- Kết quả mong đợi: Hệ thống phải kiểm tra vai trò người dùng trước khi render trang. Nếu vai trò không phải `ADMIN`, Frontend phải chặn không hiển thị giao diện quản trị, thực hiện điều hướng an toàn về Trang chủ (`/`) hoặc hiển thị màn hình từ chối quyền truy cập (403 Unauthorized / Access Denied).
- Bằng chứng lỗi ban đầu:
  - Screenshot 1: ![Lộ giao diện Admin Accounts](../ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-006--admin-accounts-guard.jpg)
  - Screenshot 2: ![Lộ giao diện Admin Roles](../ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-007--admin-roles-guard.jpg)
- Biện pháp khắc phục (Frontend Dev):
  1. Khởi tạo component `AdminRouteGuard` (`src/features/accountmanagement/components/AdminRouteGuard.tsx`) tích hợp `useCurrentAccount()`:
     - Nếu chưa đăng nhập: Điều hướng về `/login`.
     - Nếu vai trò không phải `ADMIN`: Chặn hoàn toàn không render cây DOM con của Admin, hiển thị màn hình chuẩn 403 Forbidden kèm nút `← Quay lại` về trang chủ và thông báo `account-alert-error`.
     - Nếu vai trò là `ADMIN`: Render bình thường qua `<Outlet />`.
  2. Bọc toàn bộ các route quản trị Admin (`/admin/accounts`, `/admin/accounts/new`, `/admin/accounts/:accountId`, `/admin/roles`) dưới `AdminRouteGuard` trong `routes.tsx`.
- Kết quả kiểm chứng lại (QA Re-test 2026-11-05):
  - Kịch bản `E2E-001-006` và `E2E-001-007` được chạy lại trên Chrome thực tế bằng `browsermcp`.
  - Kết quả: **`PASS 100%`**. Toàn bộ giao diện Admin bị chặn hoàn toàn không render, hiển thị màn hình 403 Forbidden với nút "Về trang chủ".
  - Bằng chứng khắc phục:
    + Screenshot 1: ![Màn hình 403 chặn Admin Accounts](../ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-006--admin-accounts-guard-resolved.jpg)
    + Screenshot 2: ![Màn hình 403 chặn Admin Roles](../ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-007--admin-roles-guard-resolved.jpg)
- Trạng thái xử lý: **CLOSED**
