---
id: JOURNEY-PHASE-3
feature: 008-retail-order-and-fulfillment
journey_phase: 3
test_level: master_e2e
test_case_type: First-Time-User-Exploratory-Discovery
source_ref: spec.md#FE-08; 002-spec.md; 006-spec.md; spec-general.md#§2,§3
target_url: https://mland-operations-hub-git-develop-mland-team.vercel.app/products
actor_primary: MEMBER (Browser 1)
actor_secondary: STAFF (Browser 2)
browser_tool: browsermcp
environment: mock-prod
test_data:
  member_email: inherited_from_phase_1
  shipping_address: inherited_from_phase_1
  loyalty_points_to_redeem: 50
  product_keyword: "Nhẫn Bạc"
  invalid_voucher_code: "MLANDFAKE999"
  valid_voucher_code: "MLANDWELCOME"
  staff_account:
    email: staff.test@mland.vn
    password: StaffTest@123456
  vnpay_card:
    bank: NCB
    number: "9704198526191432198"
    holder: "NGUYEN VAN A"
    issue_date: "07/15"
    otp: "123456"
cleanup:
  required: false
  method: handoff-to-phase-4-and-5
---

# JOURNEY-PHASE-3: Mua sắm Bán lẻ, Áp Voucher & Tiêu Điểm Loyalty, Thanh toán VNPay & Bàn giao Vận chuyển

**Phiên bản**: 3.0 (First-Time User Discovery & Friction Hunting) | **Cập nhật**: 2026-11-17  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tâm thế kiểm thử (Mindset)**: Người dùng Hội viên lần đầu mua nhẫn bán sẵn qua website; phải tự dò tìm danh mục sản phẩm, lúng túng khi chọn size nhẫn xem còn hàng hay hết hàng, bỡ ngỡ tìm đường vào giỏ hàng sau khi bấm thêm, hoang mang không biết mã voucher tìm ở đâu, và hào hứng muốn tiêu 50 điểm thưởng Loyalty vừa tích lũy được từ buổi Workshop hôm trước. Phía xưởng, nhân viên đóng gói và bàn giao đơn cho đơn vị vận chuyển.  
**Trình duyệt sử dụng**: 
- **Browser 1 (Client View)**: Khách hàng Hội viên (`MEMBER` — kế thừa địa chỉ từ Phase 1 và 50 điểm Loyalty từ Phase 2)
- **Browser 2 (Backstage View)**: Nhân viên vận hành xưởng (`STAFF`)
**Môi trường**: Mock Prod (`https://mland-operations-hub-git-develop-mland-team.vercel.app/`)

---

## 1. Mục tiêu Kịch bản (Scenario Objective)

Kiểm chứng tính Dẫn dắt (Wayfinding), sự Trực quan và Trải nghiệm thương mại điện tử mượt mà dành cho Hội viên:
- Khách hàng Hội viên tự dò tìm danh mục nhẫn bán lẻ trong Catalog, sử dụng bộ lọc và kiểm tra xem các size nhẫn đã hết hàng (`ATS = 0`) có cảnh báo rõ ràng hay để khách bấm bừa gây bế tắc.
- Kiểm tra tính Dẫn đường của Giỏ hàng (Cart Affordance): Bấm "Thêm vào giỏ" xong khách có thấy lối đi tiếp theo (Drawer / Toast có nút "Xem giỏ hàng") hay bị lạc đường tìm kiếm icon giỏ hàng.
- Vượt qua chông gai tại màn hình Checkout: Đánh giá khả năng tự động nạp địa chỉ mặc định từ Phase 1, tính năng chọn voucher từ danh sách có sẵn (thay vì bắt khách tự nhớ mã gõ tay), và cơ chế tiêu 50 điểm thưởng Loyalty với công thức quy đổi tiền minh bạch.
- Thanh toán toàn bộ đơn hàng qua VNPay Sandbox và kiểm tra tính trực quan của trang xác nhận đơn hàng (Mã đơn hàng, nút theo dõi đơn).
- Nhân viên xưởng (`STAFF`) tại Browser 2 tìm kiếm màn hình quản lý đơn hàng, thực hiện đóng gói và kích hoạt bàn giao cho đơn vị vận chuyển (GHTK), hoàn tất đơn hàng làm cơ sở viết Review ở Phase 5.

---

## 2. Phạm vi Kiểm thử (Scope Boundaries)

### 2.1. Trong phạm vi kiểm thử (In-Scope):
- Danh mục sản phẩm bán lẻ, thanh tìm kiếm, bộ lọc chất liệu/loại nhẫn và cảnh báo hết hàng của từng size nhẫn.
- Hành vi sau khi thêm vào giỏ hàng (Cart Drawer / Cart Page navigation).
- Màn hình Checkout (`/checkout`): Tự động nạp địa chỉ mặc định, danh sách voucher khả dụng, ô nhập điểm Loyalty, tính phí ship GHTK.
- Thanh toán 100% giá trị đơn hàng qua VNPay Sandbox.
- Trang xác nhận đơn hàng thành công, hiển thị mã đơn hàng `RETAIL_ORDER_ID` và trừ điểm Loyalty trong ví.
- Màn hình quản lý đơn hàng của Staff (`008`): Tìm đơn, chuyển trạng thái `PACKING` ➔ `SHIPPED`.

### 2.2. Ngoài phạm vi kiểm thử (Out-of-Scope — Tuyệt đối không thực hiện):
- Tuyệt đối không dùng thẻ thật hay tiền thật (chỉ dùng thẻ test NCB Sandbox).
- Không theo dõi shipper di chuyển ngoài đời thực hay gọi API ngoài GHTK thật nếu chưa cấu hình.
- Không spam click đặt đơn liên tục gây nghẽn kho.
- Không sửa code khi gặp lỗi tính giá; chỉ chụp màn hình và ghi nhận công thức sai lệch.

---

## 3. Tiền điều kiện & Dữ liệu Kiểm thử (Preconditions & Test Data)

- **Browser 1 (Member)**:
  - Duy trì phiên đăng nhập Hội viên (`minikie456@gmail.com`).
  - Đã có ít nhất 1 địa chỉ nhận hàng mặc định tại Hà Nội (kế thừa từ Phase 1).
  - Có sẵn **50 Điểm thưởng Loyalty** trong tài khoản (kế thừa từ Phase 2).
- **Browser 2 (Staff)**: Cửa sổ ẩn danh đăng nhập `staff.test@mland.vn` / `StaffTest@123456`.
- **Thông tin Thẻ VNPay Sandbox**:
  - Ngân hàng: `NCB` | Số thẻ: `9704198526191432198` | Tên: `NGUYEN VAN A` | Ngày: `07/15` | OTP: `123456`

---

## 4. Chu trình Khám phá & Dò tìm Lối đi (Discovery & Wayfinding Flow)

### Bước 1 (P3-S01): Dò tìm Nhẫn Bán lẻ trong Catalog & Bẫy Hết hàng (Stock Availability)

- **(a) Quan sát & Dò tìm (Visual Discovery)**:
  - Khách hàng đang ở Trang chủ, mắt tìm danh mục sản phẩm: Bấm vào mục **"Sản phẩm"** (hoặc "Cửa hàng") trên thanh Navbar.
  - Trang danh mục hiện ra: Có bộ lọc theo loại nhẫn (Nhẫn nữ, Nhẫn nam, Nhẫn đôi) hoặc chất liệu (Bạc, Vàng) không?
  - Thử gõ tìm kiếm từ khóa `"Nhẫn Bạc"`: Danh sách sản phẩm có lọc ra các mẫu nhẫn bạc sáng sủa kèm giá niêm yết rõ ràng không?
- **(b) Thử nghiệm & Bẫy bế tắc Hết hàng (Dead-end Probing)**:
  - Bấm vào một sản phẩm cụ thể (VD: "Nhẫn Bạc Nữ Đính Đá Solitaire").
  - Màn hình chi tiết sản phẩm hiển thị các tùy chọn Size nhẫn: Size 10, Size 12, Size 14, Size 16.
  - **Kiểm tra bẫy ngõ cụt hết hàng**: Thử tìm một size nhẫn đã hết hàng trong kho (`ATS = 0`):
    + **ĐẠT CHUẨN**: Nút kích thước đó bị gạch chéo hoặc mờ đi, nút bấm chính đổi thành **"Tạm hết hàng"** (disabled) và không cho bấm thêm vào giỏ.
    + **BẾ TẮC (UX Friction)**: Giao diện vẫn cho chọn size hết hàng bình thường, nút "Thêm vào giỏ" vẫn sáng đèn, khách bấm vào thì bị thông báo lỗi hoặc đến bước checkout mới báo thiếu hàng.
- **(c) Hành động Đi tiếp & Chuyển bước (Next Step Transition)**:
  - Chọn một size nhẫn còn hàng khả dụng (VD: Size 12).
  - Nút **"Thêm vào giỏ hàng"** sáng đèn nổi bật, sẵn sàng để click.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Trang danh mục hoặc trang chi tiết sản phẩm bị trắng trang (500) hoặc không load được thông tin kho hàng.
  - **SOFT FAIL**: Ảnh sản phẩm tải hơi chậm nhưng các nút chọn size và nút thêm giỏ vẫn bấm được.

---

### Bước 2 (P3-S02): "Lạc đường sau khi Thêm vào Giỏ" & Lối đi vào Giỏ hàng

- **(a) Hành động Thêm vào Giỏ**:
  - Người dùng bấm nút **"Thêm vào giỏ hàng"**.
- **(b) Quan sát Phản hồi & Trắc nghiệm Lối đi tiếp theo (Cart Wayfinding Test)**:
  - Khách hàng mới vừa bấm thêm xong, mắt họ tìm kiếm điều gì tiếp theo? Họ cần biết hàng đã vào giỏ chưa và làm sao để vào xem giỏ!
    + **ĐẠT CHUẨN**: Hệ thống mở một Drawer trượt từ cạnh phải hoặc Modal tóm tắt giỏ hàng, hiển thị rõ sản phẩm vừa thêm kèm 2 nút bấm to bản: **"Xem giỏ hàng"** và **"Tiến hành thanh toán"**.
    + **BẾ TẮC (UX Friction)**: Web chỉ nháy một dòng toast mờ 2 giây rồi biến mất, không mở drawer, khiến người mới lúng túng phải ngước mắt nhìn khắp mép trên màn hình để tìm xem biểu tượng Giỏ hàng nằm ở đâu.
- **(c) Hành động Đi tiếp & Chuyển bước (Next Step Transition)**:
  - Bấm vào nút "Xem giỏ hàng" (hoặc click icon Giỏ hàng trên Header có số `(1)`).
  - Trang `/cart` mở ra: Hiển thị đúng sản phẩm nhẫn bạc, size 12, đơn giá, số lượng `1` và tổng tiền tạm tính.
  - Bấm nút to bản **"Tiến hành đặt hàng / Thanh toán"** để chuyển sang màn hình Checkout.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Bấm thêm vào giỏ nhưng giỏ hàng vẫn báo rỗng `(0)` hoặc trang giỏ hàng crash 500.

---

### Bước 3 (P3-S03): Vượt qua Chông gai tại Checkout — Địa chỉ, Bẫy Voucher & Tiêu Điểm Loyalty

- **(a) Quan sát Địa chỉ Giao hàng**:
  - Màn hình Checkout (`/checkout`) mở ra.
  - **Kiểm tra tính tự động**: Mục địa chỉ giao hàng có tự động hiển thị sẵn địa chỉ mặc định từ Phase 1 ("Nguyễn Thị Phương Uyên - Số 28 Phố Hàng Bạc, Hà Nội") không? Có nút "Thay đổi địa chỉ" nếu khách muốn sửa không?
- **(b) Thử nghiệm Bẫy Voucher & Bế tắc Không nhớ mã (Voucher Friction Probing)**:
  - Khách hàng mới *hoàn toàn không thể tự nhớ các mã code bí mật*:
    + **ĐẠT CHUẨN**: Có nút bấm **"Chọn hoặc nhập mã ưu đãi"** (mở popup danh sách voucher có sẵn trong ví để click chọn một chạm).
    + **BẾ TẮC (UX Friction)**: Giao diện chỉ có một ô input trống trơn bắt khách tự gõ, nếu khách không nhớ mã thì hoàn toàn không có cách nào được giảm giá.
  - **Thử thách đối nghịch**: Thử gõ mã bừa `MLANDFAKE999` ➔ Bấm Áp dụng:
    + Form hiển thị câu báo lỗi màu đỏ rõ ràng: *"Mã giảm giá không tồn tại hoặc đã hết hạn"*.
  - Chọn áp dụng mã voucher hợp lệ (hoặc chọn từ danh sách ví voucher): Bảng giá giảm ngay số tiền tương ứng (VD: giảm 50.000đ).
- **(c) Khám phá & Tiêu Điểm thưởng Loyalty (Loyalty Points Affordance)**:
  - Khách hàng nhớ mình vừa được thưởng 50 điểm từ buổi học hôm trước:
    + Mục Điểm thưởng có hiển thị rõ: *"Bạn đang có 50 điểm Loyalty (Tương đương 50.000 VNĐ)"* không?
    + Có ô nhập hoặc nút bấm nhanh **"Dùng 50 điểm"** không?
    + Bấm áp dụng điểm ➔ Bảng tổng kết chi phí có lập tức cập nhật tách bạch:
      1. Tạm tính: `1.200.000đ`
      2. Giảm giá Voucher: `-50.000đ`
      3. **Giảm trừ Điểm Loyalty**: `-50.000đ`
      4. Phí vận chuyển (GHTK quote): `+30.000đ` (hoặc tương đương)
      5. **Tổng thanh toán cuối cùng**: Tính toán số tiền chính xác, không bị `NaN` hay số âm.
- **(d) Hành động Đi tiếp & Chốt đơn (Next Step Transition)**:
  - Chọn phương thức: "Cổng thanh toán điện tử VNPay".
  - Nút bấm to bản ở mép dưới **"Thanh toán đơn hàng (Tổng tiền: ...đ)"** sáng đèn nổi bật.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Bảng tính tiền bị `NaN`/Undefined, hoặc trừ sai số tiền voucher/loyalty, hoặc nút thanh toán bị vô hiệu hóa không rõ lý do.
  - **SOFT FAIL**: Danh sách voucher gợi ý tải hơi chậm.

---

### Bước 4 (P3-S04): Thanh toán Đơn hàng qua Cổng VNPay Sandbox & Dò tìm Đơn hàng

- **(a) Thao tác trên Cổng VNPay**:
  - Bấm nút "Thanh toán đơn hàng" ➔ Chuyển hướng sang VNPay Sandbox.
  - Chọn ngân hàng `NCB` ➔ Nhập số thẻ `9704198526191432198` | Tên `NGUYEN VAN A` | Hạn `07/15`.
  - Bấm Tiếp tục ➔ Nhập OTP `123456` ➔ Bấm Xác nhận.
- **(b) Quan sát & Dò tìm Sau Thanh toán (Post-Payment Discovery)**:
  - VNPay báo thành công, chuyển hướng quay lại trang web Mland.
  - Màn hình kết quả hiển thị thế nào?
    + Hiển thị thông báo: "Đặt hàng thành công!".
    + Hiển thị rõ: **Mã đơn hàng `RETAIL_ORDER_ID`** (dạng `#ORD-...`), danh sách sản phẩm nhẫn đã mua, địa chỉ nhận hàng và phương thức VNPay đã thanh toán.
    + Có nút bấm nổi bật: **"Theo dõi đơn hàng của tôi"** hoặc **"Tiếp tục mua sắm"** để khách có lối đi tiếp theo, không bị bỏ rơi tại trang trống.
- **(c) Kiểm tra Tính Toàn vẹn Điểm thưởng**:
  - Vào xem thông tin tài khoản cá nhân: Xác nhận số dư điểm Loyalty đã bị trừ đúng 50 điểm đã tiêu.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Trừ tiền VNPay thành công nhưng trang web báo lỗi tạo đơn hoặc không sinh ra mã đơn hàng `RETAIL_ORDER_ID`.

---

### Bước 5 (P3-S05): Thao tác Nhân viên Xưởng — Đóng gói & Bàn giao Đơn vị Vận chuyển (Browser 2)

- **(a) Quan sát & Đăng nhập Staff (Backstage Discovery)**:
  - Mở **Browser 2 (Cửa sổ Ẩn danh)** tại `.../login`.
  - Đăng nhập tài khoản Staff: `staff.test@mland.vn` / `StaffTest@123456`.
  - Nhân viên nhìn vào thanh menu: Có thấy mục **"Quản lý Đơn hàng Bán lẻ"** (`/operator/retail-orders`) không?
- **(b) Tìm kiếm & Xử lý Đóng gói**:
  - Mở danh sách đơn hàng: Thấy đơn hàng `RETAIL_ORDER_ID` vừa đặt ở trạng thái Chờ xử lý (`PAID` / `PROCESSING`).
  - Nhân viên bấm nút **"Bắt đầu đóng gói"**: Trạng thái đơn hàng chuyển sang `PACKING`.
- **(c) Bàn giao Đơn vị Vận chuyển & Chốt bước**:
  - Nhân viên bấm nút **"Bàn giao đơn vị vận chuyển / GHTK"**.
  - Hệ thống sinh mã vận đơn giao hàng (`TRACKING_CODE_GHTK`) và chuyển trạng thái đơn sang **`SHIPPED`** (hoặc `DELIVERED`).
  - Đơn hàng chuyển sang trạng thái đã giao/hoàn tất, mở điều kiện hợp lệ cho khách hàng viết Review ở Phase 5.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Staff không tìm thấy đơn hàng trong danh sách hoặc bấm bàn giao báo lỗi 500.

---

## 5. Điều kiện Dừng Kiểm thử Khẩn cấp (Scenario Abort Triggers)

Kịch bản **JOURNEY-PHASE-3** sẽ bị **DỪNG NGAY LẬP TỨC (ABORT)** nếu:
1. **Catalog rỗng hoặc lỗi**: Không thể tải danh sách sản phẩm hoặc mọi sản phẩm đều lỗi tải trang chi tiết.
2. **Lỗi tính toán tài chính (Financial Breakdown Crash)**: Tổng tiền thanh toán bị `NaN`, số âm, hoặc trừ sai số tiền voucher/loyalty.
3. **Lỗi kết nối cổng VNPay**: Bấm đặt hàng báo lỗi 500 không tạo được giao dịch trên cổng thanh toán.
4. **Mất đơn hàng sau thanh toán**: Khách đã trừ tiền nhưng không có mã đơn hàng `RETAIL_ORDER_ID`.
5. **Staff không thao tác được**: Nhân viên không truy cập được đơn hàng để đóng gói và bàn giao vận chuyển.

---

## 6. Kế hoạch Dữ liệu Nối tầng (Data Chaining to Phase 4 & 5)

Dữ liệu đầu ra của Phase 3 bàn giao cho các Phase tiếp theo:
- `RETAIL_ORDER_ID`: Mã đơn hàng bán lẻ đã bàn giao vận chuyển thành công.
- `RETAIL_PRODUCT_ID`: ID sản phẩm nhẫn đã mua (sử dụng ở **JOURNEY-PHASE-5** để kiểm tra quyền viết Review sản phẩm).

---

## 7. Mẫu Báo cáo Hiện tượng Bất thường (Unexpected Finding Template)

```

---

## 8. Kết quả Thực thi Kiểm thử Thực tế (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS (có 2 lỗi UX / Che khuất phát hiện được)
- Executed at: 2026-11-17 19:08:30
- Executed by: Senior QA & System Verification Engineer
- Browser tools: 
  + Client (Browser 1): Chrome (Profile `minikie456`)
  + Backstage (Browser 2): Edge (Account `staff.test@mland.vn`)
- Actual result:
  + Danh mục Ready Rings hiển thị phong phú, trang chi tiết nhẫn cành lá citrine (`/products/13`) có bảng quy đổi kích cỡ chuẩn.
  + [BUG-E2E-0012] Nút "Thêm vào giỏ" đòi hỏi chọn chi nhánh trước nhưng combobox chi nhánh không auto-select state ban đầu, toast lỗi mất nhanh không có viền đỏ cảnh báo.
  + Thêm giỏ thành công tự động trượt sang trang `/member/cart`.
  + [BUG-E2E-0010] Header cố định `main-topbar` che khuất checkbox "Chọn tất cả sản phẩm" đầu tiên trên desktop, phải scroll chuột xuống mới click được.
  + Tự động áp dụng voucher `MLHK12` giảm 78.000đ; chọn hình thức giao hàng GHTK tính phí ship chuẩn `23.540đ`.
  + Tạo đơn hàng `5af7e4d2-8712-4ff0-9781-fa02c1c3ff58` (mã hiển thị `ML2611178E653EC8`), thanh toán VNPay Sandbox thành công `595.540đ` và chuyển hướng thẳng về Vercel.
  + Staff trên Edge vào "Đơn bán lẻ chi nhánh", tìm thấy đơn tại tab "Cần gửi hàng", bấm "Bàn giao GHTK" và xác nhận dialog mô phỏng thành công; đơn chuyển sang tab "Đang giao" kèm mã vận đơn `S2272345125`.
- Evidence screenshots:
  + [P3-01--products-catalog.jpg](screenshots/P3-01--products-catalog.jpg)
  + [P3-02--product-detail.jpg](screenshots/P3-02--product-detail.jpg)
  + [P3-03--added-to-cart.jpg](screenshots/P3-03--added-to-cart.jpg)
  + [P3-04--cart-selected.jpg](screenshots/P3-04--cart-selected.jpg)
  + [P3-05--checkout-page.jpg](screenshots/P3-05--checkout-page.jpg)
  + [P3-06--order-success.jpg](screenshots/P3-06--order-success.jpg)
  + [P3-07--staff-retail-orders.jpg](screenshots/P3-07--staff-retail-orders.jpg)
  + [P3-08--staff-handover-done.jpg](screenshots/P3-08--staff-handover-done.jpg)
- Bug references: BUG-E2E-0010, BUG-E2E-0012
<!-- EXECUTION:END -->
markdown
### [UNEXPECTED-P3-xxx] <Tiêu đề ngắn gọn về hiện tượng bất thường / Điểm bế tắc>
- Bước kiểm thử: P3-S0x
- Màn hình / URL thực tế: https://mland-operations-hub-git-develop-mland-team.vercel.app/...
- Mức độ: HARD STOP (Bế tắc luồng) | SOFT FAIL (Trở ngại nhận thức / Lỗi hiển thị)
- Điểm bế tắc quan sát được: <Size hết hàng không cảnh báo, thêm giỏ xong mất dấu, không thấy danh sách voucher, thanh toán xong không thấy đơn...>
- Thao tác người dùng đã thử: <Mô tả bước click/nhập liệu/áp mã>
- Hành vi kỳ vọng theo spec: <Phải có dấu hiệu trực quan và nút bấm rõ ràng>
- Bằng chứng:
  + Screenshot: `screenshots/P3-xx--<mô tả>.jpg`
  + Mã đơn hàng liên quan: RETAIL_ORDER_ID
  + Console / Network: <HTTP status, response error code>
```

---

## 8. Kết quả Thực thi Kiểm thử Thực tế (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS (có 2 lỗi UX / Che khuất phát hiện được)
- Executed at: 2026-11-17 19:08:30
- Executed by: Senior QA & System Verification Engineer
- Browser tools: 
  + Client (Browser 1): Chrome (Profile `minikie456`)
  + Backstage (Browser 2): Edge (Account `staff.test@mland.vn`)
- Actual result:
  + Danh mục Ready Rings hiển thị phong phú, trang chi tiết nhẫn cành lá citrine (`/products/13`) có bảng quy đổi kích cỡ chuẩn.
  + [BUG-E2E-0012] Nút "Thêm vào giỏ" đòi hỏi chọn chi nhánh trước nhưng combobox chi nhánh không auto-select state ban đầu, toast lỗi mất nhanh không có viền đỏ cảnh báo.
  + Thêm giỏ thành công tự động trượt sang trang `/member/cart`.
  + [BUG-E2E-0010] Header cố định `main-topbar` che khuất checkbox "Chọn tất cả sản phẩm" đầu tiên trên desktop, phải scroll chuột xuống mới click được.
  + Tự động áp dụng voucher `MLHK12` giảm 78.000đ; chọn hình thức giao hàng GHTK tính phí ship chuẩn `23.540đ`.
  + Tạo đơn hàng `5af7e4d2-8712-4ff0-9781-fa02c1c3ff58` (mã hiển thị `ML2611178E653EC8`), thanh toán VNPay Sandbox thành công `595.540đ` và chuyển hướng thẳng về Vercel.
  + Staff trên Edge vào "Đơn bán lẻ chi nhánh", tìm thấy đơn tại tab "Cần gửi hàng", bấm "Bàn giao GHTK" và xác nhận dialog mô phỏng thành công; đơn chuyển sang tab "Đang giao" kèm mã vận đơn `S2272345125`.
- Evidence screenshots:
  + [P3-01--products-catalog.jpg](screenshots/P3-01--products-catalog.jpg)
  + [P3-02--product-detail.jpg](screenshots/P3-02--product-detail.jpg)
  + [P3-03--added-to-cart.jpg](screenshots/P3-03--added-to-cart.jpg)
  + [P3-04--cart-selected.jpg](screenshots/P3-04--cart-selected.jpg)
  + [P3-05--checkout-page.jpg](screenshots/P3-05--checkout-page.jpg)
  + [P3-06--order-success.jpg](screenshots/P3-06--order-success.jpg)
  + [P3-07--staff-retail-orders.jpg](screenshots/P3-07--staff-retail-orders.jpg)
  + [P3-08--staff-handover-done.jpg](screenshots/P3-08--staff-handover-done.jpg)
- Bug references: BUG-E2E-0010, BUG-E2E-0012
<!-- EXECUTION:END -->

