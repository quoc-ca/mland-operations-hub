---
id: JOURNEY-PHASE-1
feature: 001-account-management
journey_phase: 1
test_level: master_e2e
test_case_type: First-Time-User-Exploratory-Discovery
source_ref: spec.md#FR-001,FR-002,FR-003; spec-general.md#§2,§3
target_url: https://mland-operations-hub-git-develop-mland-team.vercel.app/
actor_primary: GUEST -> MEMBER (Browser 1)
actor_secondary: null
browser_tool: browsermcp
environment: mock-prod
test_data:
  member_login_method: google-sign-in
  member_google_account: minikie456@gmail.com
  member_signup_email_template: e2e_member_{timestamp}@mland.test
  member_signup_password: MemberSecure@2026
  member_full_name: "Nguyễn Thị Phương Uyên"
  member_phone: "0912345678"
  address_province: "Thành phố Hà Nội"
  address_commune: "Phường Hàng Bạc"
  address_detail: "Số 28 Phố Hàng Bạc"
cleanup:
  required: false
  method: handoff-to-phase-2
---

# JOURNEY-PHASE-1: Khám phá Trang chủ, Thử thách Dẫn đường AI, Định danh Hội viên & Sổ Địa chỉ Đầu tiên

**Phiên bản**: 3.0 (First-Time User Discovery & Friction Hunting) | **Cập nhật**: 2026-11-17  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tâm thế kiểm thử (Mindset)**: Người dùng mới lần đầu truy cập, hoàn toàn không biết cấu trúc URL hay vị trí các nút; phải chủ động quan sát, tìm kiếm dấu hiệu dẫn đường (Wayfinding), dò tìm nút bấm để đi tiếp và bắt các bẫy bế tắc (Dead-end).  
**Trình duyệt sử dụng**: **Browser 1 (Khách hàng / Client View)**  
**Môi trường**: Mock Prod (`https://mland-operations-hub-git-develop-mland-team.vercel.app/`)

---

## 1. Mục tiêu Kịch bản (Scenario Objective)

Kiểm chứng tính Dẫn dắt (Wayfinding) và Trực quan (Visual Affordance) của hệ sinh thái Mland đối với một khách hàng hoàn toàn mới:
- Tiếp cận trang chủ Mland Atelier từ con số 0, dò tìm các nút hành động chính (CTA) và kiểm tra xem các banner, thẻ dịch vụ có dẫn sang trang tiếp theo hay là ngõ cụt chết.
- Đánh giá khả năng co giãn responsive trên Smartphone: Menu Hamburger có dễ tìm, dễ bấm và hiển thị đầy đủ lối đi không.
- Thử nghiệm Chatbox AI từ góc nhìn người mới cần trợ giúp: Đặt câu hỏi tự nhiên và kiểm tra xem AI có cung cấp **đường link hoặc nút bấm trực tiếp** để khách click tiếp hay không.
- Thử thách bẫy bảo mật và bế tắc form: Chặn truy cập trái phép trang quản lý tài khoản (`/profile`) và hiển thị thông báo lỗi trường khi người mới gõ sai thông tin.
- Thực hiện định danh Hội viên qua Google Sign-In (`minikie456@gmail.com`) hoặc Đăng ký mới, đánh giá chuyển hướng sau đăng nhập.
- Vượt qua trở ngại thiết lập Sổ địa chỉ nhận hàng 2 cấp đầu tiên (Hà Nội), bảo đảm không bị kẹt form hay đứt gãy luồng dữ liệu.

---

## 2. Phạm vi Kiểm thử (Scope Boundaries)

### 2.1. Trong phạm vi kiểm thử (In-Scope):
- Trực quan giao diện, font chữ tiếng Việt UTF-8, chuyển đổi ngôn ngữ VI/EN.
- Khả năng nhận diện lối đi (Affordance) của các nút CTA trên Banner, Menu điều hướng Desktop và Hamburger Mobile.
- Tính hữu dụng của AI Chatbox: Có cung cấp CTA chuyển tiếp hay chỉ trả lời text suông.
- Cơ chế bảo vệ Route Guard (`SessionNavigationBoundary`) khi truy cập URL riêng tư.
- Luồng Đăng nhập Google (`minikie456@gmail.com`) và Form Đăng ký tài khoản.
- Form thêm địa chỉ nhận hàng 2 cấp theo chuẩn Shopee-style mới nhất.

### 2.2. Ngoài phạm vi kiểm thử (Out-of-Scope — Tuyệt đối không thực hiện):
- Không kiểm tra hộp thư email thật (Gmail inbox) để bấm link xác thực kích hoạt.
- Không thử nghiệm spam click chatbox hoặc submit form liên tục dạng DDoS.
- Không can thiệp mã nguồn backend/frontend khi gặp lỗi; chỉ quan sát và trích xuất bằng chứng bất thường.

---

## 3. Tiền điều kiện & Dữ liệu Kiểm thử (Preconditions & Test Data)

- **Trang đích**: `https://mland-operations-hub-git-develop-mland-team.vercel.app/`
- **Tài khoản kiểm thử**:
  - Đăng nhập: Dùng **Google Sign-In** với tài khoản `minikie456@gmail.com`.
  - Đăng ký mới (nhánh dự phòng): `e2e_member_{timestamp}@mland.test` / `MemberSecure@2026`.
- **Dữ liệu địa chỉ giao hàng**:
  - Họ tên: `Nguyễn Thị Phương Uyên` | SĐT: `0912345678`
  - Địa giới: `Thành phố Hà Nội` -> `Phường Hàng Bạc` -> `Số 28 Phố Hàng Bạc`.
  - Cờ mặc định: `true`.

---

## 4. Chu trình Khám phá & Dò tìm Lối đi (Discovery & Wayfinding Flow)

### Bước 1 (P1-S01): Tiếp cận Trang chủ Laptop (1280x800) — Dò tìm Nút Hành động & Kiểm tra Ngõ cụt

- **(a) Quan sát & Dò tìm (Visual Discovery)**:
  - Mở trang chủ tại `https://mland-operations-hub-git-develop-mland-team.vercel.app/`.
  - Người dùng mới nhìn vào màn hình: Có thấy rõ thanh điều hướng (Navbar) ở trên cùng không? Có thấy nút "Đăng nhập", "Đăng ký", menu "Workshop", "Sản phẩm" không?
  - Trên Hero Banner lớn: Có nút CTA nổi bật mời gọi click (như "Khám phá bộ sưu tập", "Đặt lịch ngay") không?
- **(b) Thử nghiệm & Kiểm tra Ngõ cụt (Dead-end Probing)**:
  - Thử click vào các thẻ giới thiệu Workshop hoặc Sản phẩm trên trang chủ: Các thẻ này có liên kết dẫn sang trang chi tiết không, hay chỉ là ảnh tĩnh không click được (Dead-end card)?
  - Tìm nút chuyển đổi ngôn ngữ (VI/EN) ở góc trên: Bấm sang `EN` ➔ Kiểm tra tiêu đề có đổi sang tiếng Anh không; bấm lại `VI` ➔ Kiểm tra font tiếng Việt có bị vỡ hoặc câu từ bị nửa nạc nửa mỡ không.
- **(c) Hành động Đi tiếp & Trạng thái kế tiếp (Next Step Transition)**:
  - Sau khi quan sát tổng thể trang chủ, xác định được lối đi rõ ràng vào danh mục Workshop và Cửa hàng.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Nếu trang chủ tải ra màn hình trắng, báo lỗi 502/503/504 hoặc thanh Navbar biến mất hoàn toàn.
  - **SOFT FAIL**: Nếu thẻ dịch vụ trên trang chủ không bấm được hoặc nhãn tiếng Anh bị dịch sót.

---

### Bước 2 (P1-S02): Kiểm tra Khả năng Nhận diện Lối đi trên Smartphone (390x844)

- **(a) Quan sát & Dò tìm (Visual Discovery)**:
  - Đổi kích thước màn hình sang iPhone 14 standard (`390 x 844`).
  - Thanh Navbar co lại: Biểu tượng 3 gạch (Menu Hamburger) có nằm ở góc trên bên phải hoặc trái dễ với ngón tay cái không?
- **(b) Thử nghiệm & Kiểm tra Ngõ cụt (Dead-end Probing)**:
  - Bấm vào nút Menu Hamburger: Menu có trượt ra mượt mà không?
  - Kiểm tra xem nút "Đăng nhập / Đăng ký" có bị chìm khuất, bị che bởi mép màn hình, hoặc bị cắt mất chữ không?
  - Kiểm tra trang chủ có bị tình trạng thanh cuộn ngang khó chịu (`horizontal scroll`) do tràn phần tử không.
- **(c) Hành động Đi tiếp & Trạng thái kế tiếp (Next Step Transition)**:
  - Bấm đóng menu Hamburger và đưa kích thước màn hình trở lại Laptop (`1280 x 800`) để tiếp tục hành trình.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Nếu trên mobile menu bị kẹt không mở được, khiến người dùng không có cách nào điều hướng.
  - **SOFT FAIL**: Menu hơi giật lag hoặc icon bị lệch vị trí nhẹ.

---

### Bước 3 (P1-S03): Thử thách Trợ lý Chatbox AI — "Có Chỉ đường được Không?"

- **(a) Quan sát & Dò tìm (Visual Discovery)**:
  - Người dùng mới muốn làm nhẫn nhưng chưa biết bắt đầu từ đâu. Mắt tìm kiếm trợ giúp: Nút tròn AI Chatbox nổi ở góc phải bên dưới có dễ nhận thấy không?
  - Kiểm tra xem nút Chatbox này có che khuất bất kỳ nút quan trọng nào khác ở chân trang không.
- **(b) Thử nghiệm & Kiểm tra Ngõ cụt (Dead-end Probing)**:
  - Bấm mở hộp thoại Chatbox. Có khung nhập tin nhắn và gợi ý câu hỏi ban đầu không?
  - Gõ câu hỏi tự nhiên của người mới: *"Tôi muốn tìm hiểu khóa học làm nhẫn đôi cho 2 người vào cuối tuần, ngân sách khoảng 1 đến 2 triệu"* ➔ Bấm nút Gửi.
  - **Điểm kiểm tra quyết định (Wayfinding Test)**: AI trả lời sau 3-7 giây.
    + **ĐẠT CHUẨN**: Phản hồi súc tích, tóm tắt đúng gói nhẫn đôi VÀ cung cấp **nút bấm hoặc đường link có thể click ngay** (VD: *"Xem chi tiết Workshop nhẫn đôi →"*).
    + **BẾ TẮC (UX Friction)**: AI chỉ trả lời một đoạn văn bản dài dòng, không có link hay nút bấm, buộc người dùng phải tự đóng chat và tự đi mò menu để tìm trang workshop.
- **(c) Hành động Đi tiếp & Trạng thái kế tiếp (Next Step Transition)**:
  - Bấm nút "Đóng chat" (icon X hoặc thu nhỏ), kiểm tra xem chatbox có đóng gọn gàng và không làm đơ trang web không.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Nếu chatbox bị lỗi JS làm treo đơ toàn bộ trang web hoặc nút gửi bị vô hiệu hóa vĩnh viễn.
  - **SOFT FAIL**: Nếu câu trả lời AI hơi dài hoặc thiếu nút bấm dẫn tiếp trực tiếp.

---

### Bước 4 (P1-S04): Thử thách Đối nghịch — Bẫy Route Guard & Bẫy Form Đăng ký

- **(a) Quan sát & Dò tìm (Visual Discovery)**:
  - Thử hành vi của người dùng tò mò hoặc copy link từ người khác: Gõ trực tiếp địa chỉ `.../profile` trên thanh URL khi chưa hề đăng nhập.
- **(b) Thử nghiệm & Kiểm tra Ngõ cụt (Dead-end Probing)**:
  - Route Guard (`SessionNavigationBoundary`) kích hoạt: Người dùng có bị chuyển hướng ngay về trang `/login` không? Có thông báo giải thích *"Vui lòng đăng nhập để tiếp tục"* không?
  - Tại trang `/login`, người dùng tìm kiếm nút chuyển sang Đăng ký: Có thấy dòng chữ/link *"Chưa có tài khoản? Đăng ký ngay"* rõ ràng không?
  - Bấm sang form Đăng ký. Thử nghiệm nhập dữ liệu ẩu của người mới:
    + Ô Email: Nhập `invalid-email-format`
    + Ô Mật khẩu: Nhập `123`
    + Bấm nút "Đăng ký"
  - **Kiểm tra bế tắc**: Form có hiển thị câu thông báo lỗi cụ thể ngay dưới từng ô nhập liệu ("Email không đúng định dạng", "Mật khẩu tối thiểu 8 ký tự") không? Hay form đứng im lặng (Silent Failure) khiến người dùng không hiểu vì sao bấm không được?
- **(c) Hành động Đi tiếp & Trạng thái kế tiếp (Next Step Transition)**:
  - Người dùng hiểu rõ nguyên nhân lỗi và biết cách sửa hoặc quay lại trang Đăng nhập.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Nếu hệ thống cho phép khách chưa đăng nhập vào thẳng trang `/profile` mà không chặn; hoặc form crash trắng trang khi bấm submit lỗi.
  - **SOFT FAIL**: Câu chữ báo lỗi hơi khác đặc tả nhưng vẫn chặn submit thành công.

---

### Bước 5 (P1-S05): Định danh Hội viên qua Google Sign-In & Dò tìm Trạng thái Đăng nhập

- **(a) Quan sát & Dò tìm (Visual Discovery)**:
  - Quay lại trang `/login`. Người dùng thấy nút bấm to bản nổi bật: **"Đăng nhập với Google"** (hoặc icon Google).
- **(b) Thử nghiệm & Hành động (Action Execution)**:
  - Bấm vào nút "Đăng nhập với Google".
  - Trình duyệt hiển thị popup/redirect Google OAuth: Chọn tài khoản `minikie456@gmail.com`.
  - Chờ hệ thống xác thực và nhận JWT token từ Firebase/Backend.
- **(c) Hành động Đi tiếp & Kiểm tra Lối đi (Next Step & Wayfinding)**:
  - Sau khi đăng nhập thành công, hệ thống đưa người dùng về đâu?
  - **Kỳ vọng**: Đưa về Trang chủ (`/`), thanh Navbar cập nhật ngay lập tức:
    + Nút "Đăng nhập / Đăng ký" biến mất.
    + Thay bằng: Tên người dùng hiển thị (Phương Uyên), Avatar cá nhân, và Menu tài khoản dạng thả xuống (Dropdown).
  - Người dùng bấm thử vào Menu tài khoản: Thấy các lối đi tiếp theo rõ ràng: "Hồ sơ cá nhân", "Sổ địa chỉ", "Vé của tôi", "Đơn mua", "Đăng xuất".
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Đăng nhập xong bị kẹt vòng lặp redirect (Redirect Loop), báo lỗi 500, hoặc Header vẫn hiện nút "Đăng nhập" như chưa có gì xảy ra.

---

### Bước 6 (P1-S06): Thiết lập Sổ Địa chỉ Nhận hàng 2 cấp Đầu tiên (Hà Nội)

- **(a) Quan sát & Dò tìm (Visual Discovery)**:
  - Từ Menu tài khoản trên Header, người dùng bấm chọn mục **"Sổ địa chỉ"** (hoặc vào `/profile/addresses`).
  - Trang mở ra: Người mới chưa có địa chỉ nào. Có thông báo trống trang nhã và nút bấm nổi bật: **"Thêm địa chỉ mới"** không?
- **(b) Thử nghiệm Dò tìm trên Form Địa chỉ 2 cấp (Friction & Form Probing)**:
  - Bấm "Thêm địa chỉ mới" ➔ Modal mở ra.
  - Kiểm tra xem form có mở ở trạng thái sạch (không pre-fill bừa bãi) không.
  - Trải nghiệm tự nhiên của người mới:
    + Nhập Họ tên: `Nguyễn Thị Phương Uyên`
    + Nhập Số điện thoại: `0912345678`
    + Tại ô Tỉnh/Thành phố: Nhập tìm kiếm và chọn `Thành phố Hà Nội`.
    + **Kiểm tra lối đi tiếp**: Sau khi chọn Hà Nội, ô Phường/Xã có tự động mở khóa (unblock) không? Có thanh tìm kiếm gõ nhanh không dấu ("hang bac" ra "Phường Hàng Bạc") không?
    + Nhập Địa chỉ cụ thể: `Số 28 Phố Hàng Bạc`.
    + Đánh dấu ô: "Đặt làm địa chỉ mặc định".
- **(c) Hành động Đi tiếp & Chốt bước (Next Step Transition)**:
  - Bấm nút **"Lưu địa chỉ"**.
  - Modal đóng lại, danh sách cập nhật ngay lập tức card địa chỉ:
    + Hiển thị đầy đủ: Họ tên, SĐT, "Số 28 Phố Hàng Bạc, Phường Hàng Bạc, Thành phố Hà Nội".
    + Có badge màu xanh lá **"Mặc định"**.
  - Kiểm tra xem trên trang có nút hoặc link điều hướng nhanh để quay lại mua sắm (VD: "Tiếp tục khám phá", nút Trang chủ) không.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Nếu bấm Lưu địa chỉ báo lỗi 500, hoặc form bắt quay lại mô hình 4 cấp GHTK cũ (bắt nhập Quận/Huyện riêng) khiến người dùng không thể lưu được địa chỉ mặc định.
  - **SOFT FAIL**: Vị trí badge Mặc định bị lệch nhẹ.

---

## 5. Điều kiện Dừng Kiểm thử Khẩn cấp (Scenario Abort Triggers)

Kịch bản **JOURNEY-PHASE-1** sẽ bị **DỪNG NGAY LẬP TỨC (ABORT)** nếu:
1. **Trang chủ sập hoàn toàn (502/503/Connection Refused)** hoặc mất thanh điều hướng Navbar khiến người dùng không có cách nào tương tác.
2. **Lỗ hổng Route Guard**: Người dùng gõ thẳng `/profile` mà hệ thống không chặn, để lộ dữ liệu cá nhân.
3. **Google Sign-In thất bại**: Bấm đăng nhập Google bị treo, lỗi redirect loop không thể thoát ra.
4. **Không lưu được địa chỉ giao hàng**: Lỗi 500 khi lưu địa chỉ, gây đứt gãy hoàn toàn dữ liệu đầu vào của Phase 3.

---

## 6. Kế hoạch Dữ liệu Nối tầng (Data Chaining to Phase 2)

Dữ liệu và trạng thái bàn giao sau khi hoàn tất Phase 1:
- `MEMBER_SESSION`: Phiên đăng nhập hợp lệ của Hội viên (`minikie456@gmail.com`).
- `SHIPPING_ADDRESS_ID`: Địa chỉ giao hàng mặc định 2 cấp tại Hà Nội (sẵn sàng cho Phase 3).
- `MEMBER_NAME`: Nguyễn Thị Phương Uyên.

---

## 7. Mẫu Báo cáo Hiện tượng Bất thường (Unexpected Finding Template)

```

---

## 8. Kết quả Thực thi Kiểm thử Thực tế (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS (có 2 ghi nhận Unexpected Soft Fail)
- Executed at: 2026-11-17 18:59:00
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp (Chrome Profile `minikie456`)
- Actual result:
  + Trang chủ tải ổn định, responsive, các liên kết danh mục hoạt động.
  + Trợ lý AI tại `/ai-support` phản hồi tốt thông tin dịch vụ và cọc 50% VNPay nhưng chỉ trả về text suông, thiếu link/nút CTA dẫn tiếp sang Workshop (UX Friction).
  + Chọn ngôn ngữ EN: tiêu đề và menu chính vẫn là tiếng Việt (BUG-E2E-0013).
  + Sổ địa chỉ 2 cấp Shopee-style: Form tuần tự Tên -> SĐT -> Tỉnh/Thành -> Phường/Xã hoạt động chính xác, lưu thành công địa chỉ mặc định tại Hoàn Kiếm, Hà Nội.
- Evidence screenshots:
  + [P1-01--home-laptop.jpg](screenshots/P1-01--home-laptop.jpg)
  + [P1-02--home-en.jpg](screenshots/P1-02--home-en.jpg)
  + [P1-03--ai-support-page.jpg](screenshots/P1-03--ai-support-page.jpg)
  + [P1-04--ai-response.jpg](screenshots/P1-04--ai-response.jpg)
  + [P1-05--profile-page.jpg](screenshots/P1-05--profile-page.jpg)
  + [P1-06--address-book-page.jpg](screenshots/P1-06--address-book-page.jpg)
  + [P1-07--address-modal.jpg](screenshots/P1-07--address-modal.jpg)
  + [P1-08--address-saved.jpg](screenshots/P1-08--address-saved.jpg)
- Bug references: BUG-E2E-0013
<!-- EXECUTION:END -->
markdown
### [UNEXPECTED-P1-xxx] <Tiêu đề ngắn gọn về hiện tượng bất thường / Điểm nghẽn bế tắc>
- Bước kiểm thử: P1-S0x
- Màn hình / URL thực tế: https://mland-operations-hub-git-develop-mland-team.vercel.app/...
- Mức độ: HARD STOP (Bế tắc luồng) | SOFT FAIL (Trở ngại nhận thức / Lỗi hiển thị)
- Điểm bế tắc quan sát được: <Nút bấm bị ẩn, không có lối đi tiếp, thiếu liên kết điều hướng...>
- Thao tác người dùng đã thử: <Mô tả các thao tác click tìm kiếm đã làm>
- Hành vi kỳ vọng theo spec: <Phải có nút CTA rõ ràng để người dùng đi tiếp>
- Bằng chứng:
  + Screenshot: `screenshots/P1-xx--<mô tả>.jpg`
  + Console / Network: <HTTP status, response error code nếu có>
```

---

## 8. Kết quả Thực thi Kiểm thử Thực tế (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS (có 2 ghi nhận Unexpected Soft Fail)
- Executed at: 2026-11-17 18:59:00
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp (Chrome Profile `minikie456`)
- Actual result:
  + Trang chủ tải ổn định, responsive, các liên kết danh mục hoạt động.
  + Trợ lý AI tại `/ai-support` phản hồi tốt thông tin dịch vụ và cọc 50% VNPay nhưng chỉ trả về text suông, thiếu link/nút CTA dẫn tiếp sang Workshop (UX Friction).
  + Chọn ngôn ngữ EN: tiêu đề và menu chính vẫn là tiếng Việt (BUG-E2E-0013).
  + Sổ địa chỉ 2 cấp Shopee-style: Form tuần tự Tên -> SĐT -> Tỉnh/Thành -> Phường/Xã hoạt động chính xác, lưu thành công địa chỉ mặc định tại Hoàn Kiếm, Hà Nội.
- Evidence screenshots:
  + [P1-01--home-laptop.jpg](screenshots/P1-01--home-laptop.jpg)
  + [P1-02--home-en.jpg](screenshots/P1-02--home-en.jpg)
  + [P1-03--ai-support-page.jpg](screenshots/P1-03--ai-support-page.jpg)
  + [P1-04--ai-response.jpg](screenshots/P1-04--ai-response.jpg)
  + [P1-05--profile-page.jpg](screenshots/P1-05--profile-page.jpg)
  + [P1-06--address-book-page.jpg](screenshots/P1-06--address-book-page.jpg)
  + [P1-07--address-modal.jpg](screenshots/P1-07--address-modal.jpg)
  + [P1-08--address-saved.jpg](screenshots/P1-08--address-saved.jpg)
- Bug references: BUG-E2E-0013
<!-- EXECUTION:END -->

