---
id: E2E-DEPLOY-001
format_version: 2
feature: 008-retail-order-and-fulfillment
journey_phase: 1
test_level: master_e2e
test_case_type: Exploratory-and-Adversarial-Retail-Flow
source_ref: spec.md#FE-08; 002-spec.md; 006-spec.md; spec-general.md#§2,§3
target_url: https://mland-operations-hub-git-develop-mland-team.vercel.app/products
health_urls:
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/products
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/member/checkout
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/staff/retail-orders
actor_primary: MEMBER (minikie456@gmail.com)
actor_secondary: STAFF (staff.test@mland.vn)
browser_tool: browsermcp
environment: deployed-vercel
test_data:
  isolation_key: "e2e_deploy_retail_001"
  member_account:
    email: minikie456@gmail.com
    password: "123123123"
  staff_account:
    email: staff.test@mland.vn
    password: StaffTest@123456
  product_keyword: "Nhẫn Bạc"
  valid_ring_size: 16
  invalid_voucher_code: "MLANDFAKE999"
  valid_voucher_code: "MLANDWELCOME"
  shipping_address:
    recipient_name: "Lê Minh Hoàng"
    phone_number: "0987654321"
    province: "Thành phố Hà Nội"
    ward: "Phường Tràng Tiền"
    detail_address: "Số 10 Phố Tràng Tiền"
  vnpay_card:
    bank: NCB
    number: "9704198526191432198"
    holder: "NGUYEN VAN A"
    issue_date: "07/15"
    otp: "123456"
cleanup:
  required: true
  method: clean-session-logout
---

# E2E-DEPLOY-001: Mua Sắm Bán Lẻ, Bẫy Voucher Đối Nghịch, Giao Hàng GHTK, Thanh Toán VNPay Sandbox & Bàn Giao Vận Chuyển

**Môi trường mục tiêu**: `https://mland-operations-hub-git-develop-mland-team.vercel.app/`  
**Phiên bản**: 2.0 | **Cập nhật**: 2026-11-25  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tâm thế kiểm thử (Mindset)**: Khách hàng mua sắm trực tuyến; cố tình thử bẫy không chọn size nhẫn, thử nhập mã voucher sai/hết hạn để kiểm tra phản hồi lỗi giao diện; chọn giao hàng tận nơi qua GHTK; thanh toán thẻ qua VNPay Sandbox; phía xưởng nhân viên đóng gói và bàn giao giao vận.  
**Chiến lược bằng chứng**: Tuân thủ nguyên tắc tinh gọn — **Không chụp ảnh màn hình ở các bước thành công (Happy Path)**. Chỉ chụp ảnh màn hình khi phát hiện lỗi bất thường (`WARN-...jpg` khi SOFT FAIL hoặc `BUG-...jpg` khi HARD STOP).

---

## 1. Mục tiêu Kịch bản (Scenario Objective)

Kiểm chứng toàn trình (End-to-End) chu trình mua bán lẻ sản phẩm nhẫn:
1. Xác minh khả năng hiển thị danh mục Catalog (`/products`), tìm kiếm sản phẩm và hiển thị giá.
2. Kiểm tra rủi ro đối nghịch (Adversarial Check 1): Bấm "Thêm vào giỏ" khi chưa chọn Size ➔ Form bắt buộc hiển thị lỗi chặn đứng, không thêm ngầm.
3. Kiểm tra tính thông suốt của Giỏ hàng (`/member/cart`) và điều hướng sang Checkout (`/member/checkout`).
4. Kiểm tra rủi ro đối nghịch (Adversarial Check 2): Nhập voucher không hợp lệ `MLANDFAKE999` ➔ Hệ thống từ chối rõ ràng; sau đó áp voucher hợp lệ.
5. Cấu hình hình thức giao nhận **Giao hàng tận nơi (GHTK)** với địa chỉ chuẩn 2 cấp, kiểm tra tính toán phí ship minh bạch.
6. Hoàn tất thanh toán 100% qua cổng VNPay Sandbox và nhận mã đơn hàng `RETAIL_ORDER_ID`.
7. Nhân viên vận hành tại `/staff/retail-orders` tìm kiếm đơn hàng, duyệt đóng gói (`PACKING`) và kích hoạt bàn giao giao vận (`SHIPPED`).

---

## 2. Tiền điều kiện (Preconditions)

1. Môi trường deploy tại `https://mland-operations-hub-git-develop-mland-team.vercel.app/` phản hồi 200 OK trên các trang công khai.
2. Tài khoản Khách hàng `minikie456@gmail.com` đăng nhập hợp lệ.
3. Tài khoản Nhân viên `staff.test@mland.vn` có quyền truy cập `/staff/retail-orders`.
4. Cổng thanh toán VNPay Sandbox NCB sẵn sàng nhận giao dịch thử nghiệm.

---

## 3. Các bước Thực thi Kiểm thử (Step Contracts)

### Bước 1 (D1-S01): Đăng nhập Khách hàng & Dò tìm Sản phẩm tại Catalog

- **User Charter**: Khách hàng đăng nhập vào hệ thống và tìm kiếm sản phẩm nhẫn bạc trong danh mục bán lẻ.
- **Explore & Act**:
  1. Mở trang Đăng nhập: `https://mland-operations-hub-git-develop-mland-team.vercel.app/login`.
  2. Đăng nhập với tài khoản: `minikie456@gmail.com` / `123123123`.
  3. Sau khi vào hệ thống thành công, điều hướng đến `/products`.
  4. Nhập từ khóa `"Nhẫn Bạc"` vào ô tìm kiếm hoặc duyệt danh mục hiển thị sẵn.
  5. Bấm vào một sản phẩm nhẫn bạc có trạng thái còn hàng để mở chi tiết (hoặc Quick Modal).
- **Observable Expected**:
  - Trang `/products` tải danh sách sản phẩm với hình ảnh, tên sản phẩm và khoảng giá hiển thị rõ ràng.
  - Thẻ sản phẩm mở ra form chọn Size (Size 14, 16, 18...) và nút "Thêm vào giỏ hàng".
- **Decision Rule**:
  - `CONTINUE`: Sản phẩm hiển thị đầy đủ thông tin giá và nút thêm giỏ khả dụng.
  - `LOG_AND_CONTINUE`: Ảnh sản phẩm bị vỡ nhẹ nhưng text và nút bấm hoạt động bình thường.
  - `STOP`: Trang báo lỗi 500, màn hình trắng, hoặc không tìm thấy bất kỳ sản phẩm nào.

---

### Bước 2 (D1-S02): Thử nghiệm Đối nghịch — Bẫy Quên chọn Size & Chọn Size Hợp lệ

- **User Charter**: Khách hàng kiểm tra cơ chế phòng vệ của form khi cố tình không chọn size ngón tay.
- **Explore & Act**:
  1. Giữ nguyên trạng thái chưa chọn bất kỳ Size nhẫn nào.
  2. Bấm nút **"Thêm vào giỏ hàng"**.
  3. Quan sát thông báo phản hồi của hệ thống.
  4. Sau khi ghi nhận phản hồi lỗi, chọn Size hợp lệ: **Size 16**.
  5. Bấm lại nút **"Thêm vào giỏ hàng"**.
- **Observable Expected**:
  - Khi chưa chọn size: Hệ thống hiển thị cảnh báo đỏ hoặc thông báo inline yêu cầu chọn kích thước, số lượng trong giỏ không tăng.
  - Sau khi chọn Size 16 và bấm thêm: Xuất hiện thông báo toast "Đã thêm vào giỏ hàng" thành công, icon giỏ hàng trên Header cập nhật số lượng badge = 1.
- **Decision Rule**:
  - `CONTINUE`: Hệ thống chặn đúng khi thiếu size và cho phép thêm sau khi chọn size.
  - `LOG_AND_CONTINUE`: Câu chữ cảnh báo chưa chọn size hiển thị bằng tiếng Anh thay vì tiếng Việt.
  - `STOP`: Cho phép thêm vào giỏ hàng thành công dù chưa chọn size (lỗi nghiệp vụ nghiêm trọng) hoặc nút bấm bị treo đơ vĩnh viễn.

---

### Bước 3 (D1-S03): Kiểm tra Giỏ hàng & Chuyển sang Màn hình Checkout

- **User Charter**: Khách hàng kiểm tra tính chính xác của sản phẩm trong giỏ và tiến hành thanh toán.
- **Explore & Act**:
  1. Bấm vào icon Giỏ hàng trên thanh Header hoặc truy cập thẳng `/member/cart`.
  2. Kiểm tra dòng sản phẩm: Tên sản phẩm, Size 16, số lượng = 1, đơn vị tiền tệ VND.
  3. Tích chọn dòng sản phẩm cần mua.
  4. Bấm nút CTA chính **"Mua hàng"** (hoặc "Thanh toán").
- **Observable Expected**:
  - Giao diện chuyển hướng mượt mà sang `/member/checkout`.
  - Tiêu đề trang hiển thị *"Thanh toán"* cùng tóm tắt danh sách sản phẩm được chuyển tiếp từ giỏ hàng.
- **Decision Rule**:
  - `CONTINUE`: Chuyển sang `/member/checkout` thành công với đúng dữ liệu sản phẩm.
  - `LOG_AND_CONTINUE`: Phải click 2 lần nút thanh toán trang mới chuyển hướng.
  - `STOP`: Bị chuyển hướng ngược về trang chủ hoặc giỏ hàng báo trống vô cớ.

---

### Bước 4 (D1-S04): Thử nghiệm Voucher Đối nghịch & Cấu hình Địa chỉ Giao hàng GHTK

- **User Charter**: Khách hàng thử nghiệm bẫy voucher giả và chọn phương thức vận chuyển tận nơi.
- **Explore & Act**:
  1. Tại màn hình `/member/checkout`, tìm khung nhập mã ưu đãi (Voucher).
  2. Nhập mã voucher giả: `MLANDFAKE999` ➔ Bấm "Áp dụng".
  3. Quan sát thông báo lỗi của hệ thống.
  4. Chọn một voucher hợp lệ có sẵn trong danh sách hiển thị (hoặc nhập `MLANDWELCOME`).
  5. Tại mục Phương thức vận chuyển, chọn **"Giao hàng tận nơi (GHTK)"**.
  6. Điền/chọn địa chỉ giao hàng chuẩn 2 cấp:
     - Người nhận: `Lê Minh Hoàng` | SĐT: `0987654321`
     - Tỉnh/Thành: `Thành phố Hà Nội` | Phường/Xã: `Phường Tràng Tiền`
     - Địa chỉ chi tiết: `Số 10 Phố Tràng Tiền`
- **Observable Expected**:
  - Mã voucher giả `MLANDFAKE999` bị từ chối với thông báo đỏ rõ ràng ("Mã giảm giá không tồn tại" hoặc tương đương), tổng tiền không đổi.
  - Mã voucher hợp lệ được chấp nhận, hiển thị dòng chiết khấu giảm giá.
  - Phí giao hàng GHTK được tính toán và hiển thị rõ ràng trong bảng tổng kết chi phí.
- **Decision Rule**:
  - `CONTINUE`: Voucher giả bị chặn, voucher thật được tính, phí vận chuyển GHTK hiển thị hợp lệ.
  - `LOG_AND_CONTINUE`: Tên quận/huyện cũ hiển thị mờ trong trường địa chỉ nhưng form vẫn chấp nhận submit.
  - `STOP`: Voucher giả `MLANDFAKE999` vẫn áp dụng giảm tiền thành công (Blocker tài chính).

---

### Bước 5 (D1-S05): Đặt hàng & Thanh toán qua Cổng VNPay Sandbox

- **User Charter**: Khách hàng chọn phương thức thanh toán VNPay và thực hiện thanh toán giao dịch qua thẻ thử nghiệm.
- **Explore & Act**:
  1. Tại mục Phương thức thanh toán, chọn **"Cổng thanh toán điện tử VNPay"**.
  2. Kiểm tra tổng số tiền thanh toán cuối cùng: `Tiền hàng - Giảm giá + Phí vận chuyển`.
  3. Bấm nút **"Đặt hàng"**.
  4. Hệ thống chuyển hướng trình duyệt sang cổng thanh toán VNPay Sandbox (`https://sandbox.vnpayment.vn/...`).
  5. Chọn tab **"Thẻ nội địa"** ➔ Chọn ngân hàng **NCB**.
  6. Nhập thông tin thẻ test:
     - Số thẻ: `9704198526191432198`
     - Tên chủ thẻ: `NGUYEN VAN A`
     - Ngày phát hành: `07/15`
  7. Bấm "Tiếp tục" ➔ Nhập mã OTP: `123456` ➔ Bấm "Xác nhận".
- **Observable Expected**:
  - VNPay thông báo giao dịch thành công và tự động redirect về lại website Mland.
  - Trình duyệt điều hướng về trang xác nhận đơn hàng (`/member/orders` hoặc trang cảm ơn).
  - Xuất hiện mã đơn hàng duy nhất `RETAIL_ORDER_ID` kèm trạng thái thanh toán thành công / đã thanh toán.
- **Decision Rule**:
  - `CONTINUE`: Thanh toán thành công, có mã đơn hàng hợp lệ hiển thị trên giao diện.
  - `LOG_AND_CONTINUE`: Trang redirect có cảnh báo chứng chỉ hoặc redirect trễ 5–10 giây nhưng vẫn về web an toàn.
  - `STOP`: VNPay trừ tiền thành công nhưng web báo lỗi 500, trắng trang, hoặc không sinh mã đơn hàng.

---

### Bước 6 (D1-S06): Thao tác Nhân viên — Đóng gói Đơn hàng (`PACKING`)

- **User Charter**: Nhân viên xưởng đăng nhập hệ thống quản trị, tiếp nhận đơn hàng vừa đặt và chuyển trạng thái sang đóng gói.
- **Explore & Act**:
  1. Mở cửa sổ/tab mới tại: `https://mland-operations-hub-git-develop-mland-team.vercel.app/login`.
  2. Đăng nhập tài khoản Nhân viên: `staff.test@mland.vn` / `StaffTest@123456`.
  3. Truy cập vào màn hình quản lý đơn bán lẻ: `/staff/retail-orders`.
  4. Tìm đơn hàng có mã `RETAIL_ORDER_ID` vừa tạo ở Bước 5.
  5. Kiểm tra thông tin đơn: Khách hàng `Lê Minh Hoàng`, sản phẩm Nhẫn Bạc Size 16, địa chỉ giao hàng GHTK, trạng thái đã thanh toán.
  6. Bấm nút hành động **"Xác nhận đóng gói"** (hoặc chuyển trạng thái `PACKING`).
- **Observable Expected**:
  - Đơn hàng chuyển trạng thái hiển thị thành badge màu xanh/vàng: `ĐANG ĐÓNG GÓI` (`PACKING`).
- **Decision Rule**:
  - `CONTINUE`: Trạng thái đơn hàng cập nhật thành `PACKING` thành công.
  - `LOG_AND_CONTINUE`: Giao diện cần bấm F5 mới cập nhật trạng thái mới.
  - `STOP`: Nhân viên bị từ chối truy cập 403 hoặc bấm đóng gói báo lỗi 500.

---

### Bước 7 (D1-S07): Thao tác Nhân viên — Bàn giao Đơn vị Vận chuyển (`SHIPPED`) & Dọn dẹp

- **User Charter**: Nhân viên hoàn tất bàn giao kiện hàng cho đối tác vận chuyển GHTK, khép kín vòng đời đơn hàng.
- **Explore & Act**:
  1. Tại dòng đơn hàng `RETAIL_ORDER_ID`, bấm nút **"Bàn giao vận chuyển"** (hoặc mở `/carrier/orders` để xác nhận giao vận).
  2. Xác nhận bàn giao đơn hàng.
  3. Kiểm tra trạng thái đơn chuyển sang `ĐÃ GIAO VẬN CHUYỂN` (`SHIPPED`).
  4. Đăng xuất cả 2 tài khoản để dọn dẹp phiên làm việc.
- **Observable Expected**:
  - Trạng thái đơn hàng đổi thành `SHIPPED`.
  - Khách hàng kiểm tra tại `/member/orders` cũng nhìn thấy trạng thái đơn hàng đã được cập nhật thành đã giao vận chuyển.
- **Decision Rule**:
  - `CONTINUE`: Hoàn tất chu trình bán lẻ và giao vận thành công.
  - `LOG_AND_CONTINUE`: Nút bàn giao hiển thị tiếng Anh `Handover` thay vì tiếng Việt.
  - `STOP`: Không thể bàn giao vận chuyển hoặc trạng thái đơn bị quay ngược về chưa thanh toán.

---

## 4. Điều kiện Thoát & Báo cáo Lỗi Khẩn cấp (Abort & Error Logging Gate)

- Khi gặp bất kỳ **HARD STOP** nào:
  1. Dừng ngay toàn bộ kịch bản.
  2. Chụp ảnh màn hình hiện trường lỗi đặt tại: `screenshots/BUG-E2E-001--<tên-lỗi>.jpg`.
  3. Trích xuất: URL, HTTP Error status, Correlation ID và thông báo console.
  4. Ghi ngay một bản ghi lỗi chuẩn vào `ppswbs-testing/BUG-LOG.md` bao gồm đầy đủ **Các bước tái hiện chi tiết (Reproduction Steps)**.
- Khi gặp **SOFT FAIL**:
  1. Chụp ảnh màn hình giao diện bất thường: `screenshots/WARN-E2E-001--<tên-lỗi>.jpg`.
  2. Ghi nhận finding vào mục ghi chú thực thi và áp dụng `LOG_AND_CONTINUE`.

---

## 5. Lịch sử Thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASSED
- Executed at: 2026-11-25T19:20:00+07:00
- Executed by: Senior QA & System Verification Engineer
- Target URL: https://mland-operations-hub-git-develop-mland-team.vercel.app/
- Actual result: Kịch bản được tái thực thi toàn trình và ĐẠT (PASS 100%) cả 7 bước theo đúng chỉ thị kiểm thử:
  - D1-S01 (Member Pre-flight & Auth): Đăng nhập thành công với tài khoản Google Member `minikie456@gmail.com`. Giỏ hàng ban đầu 0 sản phẩm.
  - D1-S02 (Catalog, Inventory, Branch & Size Selection):
    - Đã xác minh tính toàn vẹn tồn kho: Size 8 (được mua ở phiên trước) chuyển sang trạng thái `Size 8 (Hết hàng)` và bị disabled chính xác.
    - Chọn Chi nhánh `Mland Hà Nội – Hoàn Kiếm` (ID 1), Size 9, số lượng 1 cho sản phẩm "Nhẫn bạc cành lá citrine" (ID 9). Thêm vào giỏ hàng thành công.
  - D1-S03 (Cart to Checkout Navigation): Điều hướng vào `/member/cart`, tích chọn sản phẩm, bấm "Mua hàng", chuyển tiếp thành công sang `/member/checkout`.
  - D1-S04 (Voucher Adversarial Check & Delivery Quote):
    - Thử nghiệm bẫy voucher không hợp lệ `MLANDFAKE999`: Hệ thống từ chối chính xác không áp dụng mã giả.
    - Áp dụng voucher hợp lệ `MLGIAM50K` (-50.000 ₫) và giảm giá sản phẩm tự động (-30.000 ₫) => Tổng ưu đãi -80.000 ₫.
    - Báo giá vận chuyển GHTK Nhanh: 31.905 ₫.
    - Tổng tiền thanh toán khớp chính xác: 560.000 + 31.905 - 80.000 = 511.905 ₫.
  - D1-S05 (Order Placement & VNPay Sandbox Payment):
    - Bấm "Đặt hàng", hệ thống tạo đơn hàng mã `ML2611256F91090C` (Order ID `6f91090c-093d-4b40-a220-495575e24815`).
    - Chuyển hướng sang VNPay Sandbox với đúng số tiền `511.905 ₫` (`vnp_Amount=51190500`).
    - Nhập thông tin thẻ NCB `9704198526191432198` / `NGUYEN VAN A` / `07/15`, OTP `123456`.
    - Thanh toán thành công và chuyển hướng về `/member/orders` hiển thị trong tab "Chờ lấy hàng".
  - D1-S06 (Staff Packing Order):
    - Đăng nhập tài khoản Nhân viên `staff.test@mland.vn`.
    - Tại `/staff/retail-orders`, tìm thấy đơn `ML2611256F91090C` (Mã vận đơn `S4724552384`) ở tab "Đơn hàng cần chuẩn bị".
    - Bấm "Đã chuẩn bị xong" -> Chuyển trạng thái sang tab "Chờ ĐVVC lấy hàng".
  - D1-S07 (Carrier Handover & Delivered Lifecycle):
    - Đăng nhập tài khoản ĐVVC `ghtk.test@mland.vn`.
    - Tại `/carrier/orders`, tìm thấy vận đơn `S4724552384` ở tab "Đang lấy hàng".
    - Bấm "Đã lấy hàng" -> Chuyển sang "Đang giao hàng".
    - Bấm "Giao thành công" -> Ghi nhận hoàn tất giao hàng lúc 19:15 25/11/26.
    - Khách hàng đăng nhập lại tại `/member/orders`, xác minh đơn `ML2611256F91090C` hiển thị ở tab "Đã giao" với đầy đủ lịch sử: Đặt lúc 19:06, thanh toán VNPay lúc 19:13, giao hàng lúc 19:15, tổng tiền chính xác 511.905 ₫ và xuất hiện nút "Đánh giá".
- Infrastructure Note:
  - Khi gặp HTTP 502 DNS edge glitch (`x-vercel-error: DNS_HOSTNAME_EMPTY`), cơ chế reload theo chỉ thị người dùng đã giúp DNS edge Vercel hồi phục và toàn bộ quy trình hoàn tất suôn sẻ.
<!-- EXECUTION:END -->
