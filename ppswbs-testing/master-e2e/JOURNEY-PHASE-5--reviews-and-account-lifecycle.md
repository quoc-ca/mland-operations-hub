---
id: JOURNEY-PHASE-5
feature: 001-account-management
journey_phase: 5
test_level: master_e2e
test_case_type: First-Time-User-Security-Lifecycle-Discovery
source_ref: spec.md#FE-01; spec-general.md#§2,§3; SessionNavigationBoundary.tsx; ProfilePage.tsx
target_url: https://mland-operations-hub-git-develop-mland-team.vercel.app/register
health_urls:
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/register
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/login
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/profile
actor_role: GUEST
actor_primary: GUEST -> NEW_MEMBER (Browser 1 - Chrome)
actor_secondary: null
browser_tool: browsermcp
environment: mock-prod
test_data:
  isolation_key: "e2e_phase5_account_lifecycle"
  new_member_email_template: "e2e_user_{timestamp}@mland.test"
  new_member_full_name: "Lê Minh Hoàng"
  initial_password: "PasswordOld@123"
  updated_password: "PasswordNew@456"
  invalid_password_mismatch: "PasswordMismatch@999"
  test_phone: "0987654321"
cleanup:
  required: true
  method: clean-session-logout
---

# JOURNEY-PHASE-5: Quản trị Tài khoản & Đổi Mật khẩu Mới, Thử thách Đăng xuất Sạch sẽ & Rào cản Điều hướng Đối nghịch

**Phiên bản**: 3.0 (Account Security, Password Lifecycle & Clean Logout) | **Cập nhật**: 2026-11-17  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tâm thế kiểm thử (Mindset)**: Người dùng mới tự tạo tài khoản cá nhân độc lập với mật khẩu ban đầu; trực tiếp trải nghiệm màn hình Hồ sơ & Bảo mật, thử nghiệm bẫy form đổi mật khẩu (nhập sai, không khớp), cập nhật mật khẩu mới, sau đó đóng vai kẻ tấn công tiềm tàng: thử đăng nhập lại bằng mật khẩu cũ, thực hiện đăng xuất sạch sẽ và cố tình bấm nút "Back" trên trình duyệt để kiểm tra xem hệ thống có rò rỉ dữ liệu phiên làm việc cũ hay không.  
**Trình duyệt sử dụng**: **Browser 1 (Khách hàng / Client View - Chrome)**  
**Môi trường**: Mock Prod (`https://mland-operations-hub-git-develop-mland-team.vercel.app/`)

---

## 1. Mục tiêu Kịch bản (Scenario Objective)

Kiểm chứng chu trình Vòng đời Tài khoản (Account Lifecycle), An ninh Phiên làm việc (Session Governance) và Rào cản Điều hướng Đối nghịch (Adversarial Route Guard):
- Khởi tạo một tài khoản người dùng Email/Mật khẩu hoàn toàn mới tại trang Đăng ký (`/register`), đánh giá tốc độ phản hồi và chuyển hướng sau khi đăng ký.
- Khám phá trang Hồ sơ cá nhân (`/profile`): Phân tích bố cục thị giác, thẻ thành viên, banner cảnh báo xác thực email và tính khả dụng của các form chức năng.
- Thực nghiệm tính năng Đổi mật khẩu: Thử thách bẫy nhập liệu (mật khẩu xác nhận không khớp, mật khẩu yếu) $\rightarrow$ Cập nhật thành công sang mật khẩu mới.
- Thực hiện Đăng xuất sạch sẽ (Clean Session Cleansing): Xác minh toàn bộ token JWT và thông tin nhạy cảm được gỡ bỏ triệt để khỏi bộ nhớ trình duyệt, đưa Navbar về trạng thái khách vãng lai.
- Thử thách Đăng nhập Đối nghịch: Kiểm tra máy chủ từ chối triệt để mật khẩu cũ và chấp nhận chính xác mật khẩu mới.
- Thử nghiệm tấn công nút Back trình duyệt (Back Button Adversarial Challenge): Bấm liên tục nút Back sau khi đăng xuất $\rightarrow$ Kiểm chứng `SessionNavigationBoundary` kích hoạt để chặn đứng việc đọc lại cache trang cá nhân và đá văng an toàn về trang chủ/đăng nhập.

---

## 2. Phạm vi Kiểm thử (Scope Boundaries)

### 2.1. Trong phạm vi kiểm thử (In-Scope):
- Biểu mẫu Đăng ký tài khoản mới (`/register`): Kiểm tra các trường Họ tên, Email, Mật khẩu và tính năng auto-login sau đăng ký.
- Trang Hồ sơ cá nhân & Bảo mật (`/profile`): Đánh giá các trường thông tin, trạng thái cờ `isVerified`, liên kết quản lý Sổ địa chỉ và form Đổi mật khẩu.
- Form Đổi mật khẩu: Thử nghiệm thông báo lỗi đỏ inline khi mật khẩu xác nhận không khớp; thực hiện đổi mật khẩu thành công sang `PasswordNew@456`.
- Quy trình Đăng xuất (Logout) và cơ chế xóa phiên làm việc.
- Thử thách đăng nhập với mật khẩu cũ (`PasswordOld@123`) $\rightarrow$ Bắt thông báo lỗi từ máy chủ.
- Thử thách đăng nhập với mật khẩu mới (`PasswordNew@456`) $\rightarrow$ Thành công.
- Rào cản bảo mật `SessionNavigationBoundary` và Route Guard đối với các đường dẫn riêng tư (`/profile`, `/member/cart`, `/my/designs`) khi chưa đăng nhập.

### 2.2. Ngoài phạm vi kiểm thử (Out-of-Scope — Tuyệt đối không thực hiện):
- Không kiểm tra tính năng gửi/nhận email kích hoạt qua hộp thư Gmail/Yahoo thật bên thứ ba.
- Không thử nghiệm các kỹ thuật tấn công Brute-force mật khẩu hàng triệu lần dạng DDoS.
- Không can thiệp sửa mã nguồn Firebase hay Spring Security; chỉ quan sát và trích xuất bằng chứng HTTP status, console log và screenshot.

---

## 3. Tiền điều kiện & Dữ liệu Kiểm thử (Preconditions & Test Data)

- **Trình duyệt**: Browser 1 (Chrome).
- **Trạng thái ban đầu**: Bắt đầu từ trạng thái khách vãng lai (Guest) tại `/register` hoặc sau khi đã đăng xuất tài khoản cũ.
- **Dữ liệu đăng ký mới**:
  - Email: `e2e_user_{timestamp}@mland.test` (sử dụng timestamp Unix thời gian thực để chống trùng lặp).
  - Họ và tên: `Lê Minh Hoàng`.
  - Mật khẩu khởi tạo: `PasswordOld@123`.
- **Dữ liệu đổi mật khẩu**:
  - Mật khẩu hiện tại: `PasswordOld@123`.
  - Mật khẩu mới: `PasswordNew@456`.
  - Mật khẩu xác nhận thử lỗi: `PasswordMismatch@999`.
- **Số điện thoại kiểm tra hồ sơ**: `0987654321`.

---

## 4. Chu trình Khám phá & Dò tìm Lối đi (Discovery & Wayfinding Flow)

### Bước 1 (P5-S01): Đăng ký Tài khoản Mới Phục vụ Vòng đời (`/register`)

- **(a) Quan sát & Dò tìm (Visual Discovery)**:
  - Mở trang Đăng ký tại: `https://mland-operations-hub-git-develop-mland-team.vercel.app/register`.
  - Tiêu điểm mắt nhìn vào chính giữa màn hình (Center of Screen): Có thấy rõ tiêu đề *"Đăng ký tài khoản"*, các trường nhập: *Họ và tên, Địa chỉ email, Mật khẩu, Xác nhận mật khẩu* và nút CTA chính *"Đăng ký"* không?
- **(b) Thao tác Nhập liệu & Tạo Tài khoản (Account Creation)**:
  - Tạo một địa chỉ email duy nhất: `e2e_user_[timestamp]@mland.test`.
  - Nhập Họ và tên: `Lê Minh Hoàng`.
  - Nhập Mật khẩu: `PasswordOld@123`.
  - Nhập Xác nhận mật khẩu: `PasswordOld@123`.
  - Bấm nút **"Đăng ký"**.
- **(c) Hành động Đi tiếp & Trạng thái kế tiếp (Next Step Transition)**:
  - Hệ thống xử lý đăng ký trong $\le 2\text{ giây}$.
  - Quan sát phản ứng điều hướng: Hệ thống tự động đăng nhập và chuyển hướng mượt mà về Trang chủ (`/`) hoặc trang cá nhân, trên Navbar xuất hiện biểu tượng tài khoản mang tên `Lê Minh Hoàng` hoặc tiền tố email.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Nếu form đăng ký báo lỗi server 500 hoặc bị treo không phản hồi.
  - **SOFT FAIL**: Nếu thông báo chào mừng xuất hiện hơi chậm.

---

### Bước 2 (P5-S02): Khám phá Trang Hồ sơ & Phân tích Trạng thái Bảo mật (`/profile`)

- **(a) Quan sát & Dò tìm (Visual Discovery)**:
  - Mở menu tài khoản trên Header $\rightarrow$ Click chọn **"👤 Hồ sơ cá nhân"** (hoặc truy cập trực tiếp `/profile`).
  - Tiêu điểm mắt nhìn vào trung tâm màn hình:
    - Bố cục 2 cột: Cột trái là Thẻ thành viên (Hiển thị tên, cấp bậc Thành viên Đồng, Điểm = 0), liên kết Sổ địa chỉ.
    - Cột phải là các khối biểu mẫu: *"Chỉnh sửa thông tin hồ sơ"*, *"Đổi mật khẩu"*, *"Thay đổi địa chỉ email"*.
- **(b) Kiểm tra Chi tiết & Tính Khả dụng (Affordance & Density Probing)**:
  - Quan sát dải banner trên cùng: Có thông báo vàng về trạng thái xác minh email không?
  - Thử kiểm tra form *"Chỉnh sửa thông tin hồ sơ"*: Ô Họ và tên hiển thị sẵn `Lê Minh Hoàng`, ô SĐT để trống.
  - Nhập thử SĐT mới: `0987654321`.
  - **Kiểm tra Gate xác thực email**: Quan sát nút *"Lưu thay đổi"*: Nếu nút bị disable do email chưa xác thực (`!isVerified`), kiểm tra xem có tooltip hoặc dòng chú thích giải thích lý do hay không.
- **(c) Hành động Đi tiếp & Trạng thái kế tiếp (Next Step Transition)**:
  - Chuẩn bị chuyển sang khối form Đổi mật khẩu ngay bên dưới.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Nếu trang `/profile` báo lỗi màn hình trắng hoặc không load được thông tin tài khoản.
  - **SOFT FAIL**: Nếu khoảng cách giữa các form quá dày đặc (Typography & Spacing Density kém).

---

### Bước 3 (P5-S03): Thực nghiệm Đổi Mật khẩu — Thử thách Lỗi Nhập Liệu & Cập nhật Thành công

- **(a) Quan sát & Định vị Form (Form Wayfinding)**:
  - Cuộn màn hình đến khu vực **"Đổi mật khẩu"** (`#password-change-heading`).
  - Nhận diện 3 trường nhập liệu:
    1. *Mật khẩu hiện tại*
    2. *Mật khẩu mới*
    3. *Xác nhận mật khẩu mới*
    4. Nút bấm: *"Cập nhật mật khẩu"* (mặc định bị disable khi các ô còn trống).
- **(b) Thử thách Đối nghịch 1 — Lỗi Mật khẩu Không Khớp (Adversarial Input Validation)**:
  - Nhập Mật khẩu hiện tại: `PasswordOld@123`.
  - Nhập Mật khẩu mới: `PasswordNew@456`.
  - Nhập Xác nhận mật khẩu SAI: `PasswordMismatch@999`.
  - Bấm nút **"Cập nhật mật khẩu"**.
  - **Kỳ vọng bảo vệ**: Hệ thống PHẢI chặn lại ngay lập tức, hiển thị dòng cảnh báo lỗi đỏ rõ ràng: *"Mật khẩu xác nhận không khớp"* và KHÔNG gửi request rác lên server.
- **(c) Thực hiện Đổi Mật khẩu Hợp lệ & Đo lường Độ trễ**:
  - Sửa lại ô Xác nhận mật khẩu mới thành: `PasswordNew@456`.
  - Bấm nút **"Cập nhật mật khẩu"**.
  - Đo thời gian xử lý của hệ thống: Phải hoàn tất trong $\le 2\text{ giây}$ (đáp ứng tiêu chí Response Latency $\le 8s$).
  - **Kỳ vọng thành công**: Một dải thông báo màu xanh lá xuất hiện trang trọng: *"Đổi mật khẩu thành công!"*, các ô nhập mật khẩu được tự động xóa trắng an toàn.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Nếu đổi mật khẩu hợp lệ nhưng server trả lỗi 500 hoặc crash trang.
  - **SOFT FAIL**: Nếu thông báo thành công biến mất quá nhanh dưới 1 giây.

---

### Bước 4 (P5-S04): Đăng xuất Sạch sẽ (Clean Session Cleansing)

- **(a) Thao tác Đăng xuất (Logout Execution)**:
  - Mở menu Tùy chọn tài khoản ở góc trên bên phải thanh Navbar.
  - Tìm và bấm vào nút: **"🚪 Đăng xuất"**.
- **(b) Kiểm tra Dọn sạch Phiên làm việc (Session Cleansing Verification)**:
  - Trang web chuyển hướng an toàn về Trang chủ (`/`) hoặc trang Đăng nhập (`/login`).
  - Mắt người dùng nhìn vào thanh Navbar trên cùng:
    - Menu tài khoản cá nhân mang tên `Lê Minh Hoàng` PHẢI biến mất hoàn toàn.
    - Thanh Navbar quay về trạng thái khách vãng lai: Hiển thị lại các nút **"Đăng nhập"** và **"Đăng ký"**.
  - Không còn bất kỳ dấu vết nào của tài khoản trên giao diện.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Nếu bấm đăng xuất nhưng trang web không chuyển hướng hoặc thanh Navbar vẫn lưu thông tin tài khoản cũ.
  - **SOFT FAIL**: Nếu đăng xuất mất hơn 3 giây.

---

### Bước 5 (P5-S05): Thử thách Đăng nhập Đối nghịch (Mật khẩu Cũ vs Mật khẩu Mới)

- **(a) Thử thách 1 — Chặn Tuyệt đối Mật khẩu Cũ (Old Password Rejection)**:
  - Truy cập trang Đăng nhập tại `/login`.
  - Nhập Email: `e2e_user_[timestamp]@mland.test`.
  - Nhập **MẬT KHẨU CŨ**: `PasswordOld@123`.
  - Bấm nút **"Đăng nhập"**.
  - **Kỳ vọng an ninh**: Máy chủ PHẢI TỪ CHỐI đăng nhập, hiển thị thông báo lỗi màu đỏ rõ ràng: *"Tài khoản hoặc mật khẩu không chính xác"*. Tuyệt đối không cho phép truy cập.
- **(b) Thử nghiệm 2 — Đăng nhập Thành công bằng Mật khẩu Mới (New Password Acceptance)**:
  - Xóa ô mật khẩu, nhập lại bằng **MẬT KHẨU MỚI**: `PasswordNew@456`.
  - Bấm nút **"Đăng nhập"**.
  - **Kỳ vọng thành công**: Hệ thống xác thực thành công trong $\le 2\text{ giây}$, chuyển hướng vào bên trong, Header hiển thị lại đúng tên `Lê Minh Hoàng`.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Nếu mật khẩu cũ vẫn đăng nhập thành công (Lỗ hổng bảo mật nghiêm trọng - Critical Security Bug).
  - **SOFT FAIL**: Nếu thông báo lỗi nhập sai mật khẩu viết bằng tiếng Anh thay vì tiếng Việt.

---

### Bước 6 (P5-S06): Thử thách Nút Back Trình duyệt & Rào cản Route Guard (`SessionNavigationBoundary`)

- **(a) Thiết lập Trạng thái Thử nghiệm**:
  - Khi đang ở phiên đăng nhập thành công, điều hướng vào trang riêng tư: `/profile` hoặc `/my/designs`.
  - Xác nhận trang hiển thị đầy đủ thông tin cá nhân.
  - Bấm nút **"🚪 Đăng xuất"** một lần nữa để đưa phiên làm việc về trạng thái đóng sạch sẽ.
- **(b) Thử thách Đối nghịch Nút Back Trình duyệt (Browser Back Button Attack)**:
  - Đóng vai kẻ xấu sử dụng chung máy tính mượn: Bấm nút **"Back (Quay lại)"** trên thanh điều hướng trình duyệt 1 đến 2 lần liên tiếp.
  - **Kỳ vọng bảo vệ tối thượng (`SessionNavigationBoundary`)**:
    + Hệ thống KHÔNG ĐƯỢC PHÉP hiển thị lại trang `/profile` từ cache bộ nhớ.
    + `SessionNavigationBoundary` phải kích hoạt ngay lập tức, chặn đứng truy cập và tự động điều hướng an toàn về `/login` hoặc `/`.
    + Người dùng không thể xem trộm được bất kỳ dữ liệu cá nhân nào của phiên trước.
- **(c) Thử thách Gõ URL Trực tiếp (Direct Private Route Guarding)**:
  - Thử gõ trực tiếp trên thanh địa chỉ URL:
    1. `.../profile`
    2. `.../member/cart`
    3. `.../my/designs`
  - **Kỳ vọng**: Mọi đường dẫn riêng tư đều bị chặn 100% và đá về `/login` kèm thông báo yêu cầu đăng nhập.
- **Đánh giá & Mức độ Dừng**:
  - **HARD STOP**: Nếu bấm Back trên trình duyệt mà vẫn xem lại được dữ liệu cá nhân hoặc các mẫu nhẫn trong sổ tay (Lỗi vi phạm an ninh phiên làm việc).
  - **SOFT FAIL**: Nếu chuyển hướng bị chớp màn hình nhẹ 1 lần.

---

## 5. Ma trận Các bước Thực thi Nghiệp vụ (Business Step Matrix)

| Bước | Actor | Thao tác chính | Dữ liệu đầu vào | Kết quả mong đợi | Mức dừng |
| :---: | :--- | :--- | :--- | :--- | :--- |
| **P5-S01** | GUEST | Đăng ký tài khoản mới | Email test timestamp, Họ tên, Pass `PasswordOld@123` | Đăng ký thành công $\le 2s$, tự động đăng nhập | HARD STOP nếu lỗi 500 |
| **P5-S02** | NEW_MEMBER | Phân tích trang hồ sơ | Mở `/profile`, xem thẻ thành viên, nhập thử SĐT | Bố cục 2 cột rõ ràng, cờ email hiển thị đúng | HARD STOP nếu trắng trang |
| **P5-S03** | NEW_MEMBER | Thực nghiệm đổi mật khẩu | Nhập pass cũ, pass mới `PasswordNew@456`, thử pass lệch | Bắt lỗi pass không khớp; Cập nhật đúng thành công | HARD STOP nếu server lỗi |
| **P5-S04** | NEW_MEMBER | Đăng xuất sạch sẽ | Bấm "Đăng xuất" từ Header | Token bị xoá sạch, Navbar về khách vãng lai | HARD STOP nếu vẫn lưu phiên |
| **P5-S05** | GUEST | Đăng nhập đối nghịch | Thử pass cũ `PasswordOld@123` -> Thử pass mới `PasswordNew@456` | Pass cũ bị từ chối 100%; Pass mới đăng nhập thành công | HARD STOP nếu pass cũ vào được |
| **P5-S06** | GUEST | Thử thách Back button & Route Guard | Bấm nút Back trình duyệt liên tục; gõ link `/profile` | `SessionNavigationBoundary` chặn đứng, redirect về login | HARD STOP nếu lộ dữ liệu cũ |

---

### 5.1. Browser Action Steps
```bash
# Step 1: Đăng ký Tài khoản Mới Phục vụ Vòng đời
goto "https://mland-operations-hub-git-develop-mland-team.vercel.app/register"
wait 2000
fill getByPlaceholder(/họ và tên|name/i) "Lê Minh Hoàng"
fill getByPlaceholder(/email/i) "e2e_user_{timestamp}@mland.test"
fill getByPlaceholder(/^mật khẩu|password/i).first() "PasswordOld@123"
fill getByPlaceholder(/xác nhận|confirm/i) "PasswordOld@123"
click getByRole('button', { name: /đăng ký/i })
wait 3000

# Step 2: Khám phá Trang Hồ sơ & Phân tích Trạng thái Bảo mật
goto "https://mland-operations-hub-git-develop-mland-team.vercel.app/profile"
wait 2000
snapshot -i

# Step 3: Thực nghiệm Đổi Mật khẩu
fill getByPlaceholder(/mật khẩu hiện tại/i) "PasswordOld@123"
fill getByPlaceholder(/mật khẩu mới/i) "PasswordNew@456"
fill getByPlaceholder(/xác nhận mật khẩu/i) "PasswordMismatch@999"
click getByRole('button', { name: /cập nhật mật khẩu/i })
wait 1000
fill getByPlaceholder(/xác nhận mật khẩu/i) "PasswordNew@456"
click getByRole('button', { name: /cập nhật mật khẩu/i })
wait 3000

# Step 4: Đăng xuất Sạch sẽ
click getByRole('button', { name: /tài khoản|user|hồ sơ/i }).first()
wait 500
click getByRole('button', { name: /đăng xuất|logout/i })
wait 2000

# Step 5: Thử thách Đăng nhập Đối nghịch (Mật khẩu Cũ vs Mật khẩu Mới)
goto "https://mland-operations-hub-git-develop-mland-team.vercel.app/login"
wait 2000
fill getByPlaceholder(/email/i) "e2e_user_{timestamp}@mland.test"
fill getByPlaceholder(/mật khẩu|password/i) "PasswordOld@123"
click getByRole('button', { name: /đăng nhập/i })
wait 2000
fill getByPlaceholder(/mật khẩu|password/i) "PasswordNew@456"
click getByRole('button', { name: /đăng nhập/i })
wait 3000

# Step 6: Thử thách Nút Back Trình duyệt & Rào cản Route Guard
click getByRole('button', { name: /tài khoản|user|hồ sơ/i }).first()
wait 500
click getByRole('button', { name: /đăng xuất|logout/i })
wait 2000
goto "https://mland-operations-hub-git-develop-mland-team.vercel.app/profile"
wait 2000
snapshot -i
```

### 5.2. Expected Assertions
```bash
# Assertion Step 1: Đăng ký thành công & tự động đăng nhập
get url
find "mland"

# Assertion Step 2: Mở trang hồ sơ cá nhân
get url
find "/profile"
get text getByRole('heading', { level: 1 })
find "Hồ sơ cá nhân"

# Assertion Step 3: Bắt lỗi xác nhận không khớp & cập nhật thành công
find "Mật khẩu xác nhận không khớp"
find "Đổi mật khẩu thành công"

# Assertion Step 4: Đăng xuất sạch sẽ
get url
find "Đăng nhập"

# Assertion Step 5: Thử thách mật khẩu cũ bị từ chối, mật khẩu mới chấp nhận
find "không chính xác"
find "Lê Minh Hoàng"

# Assertion Step 6: Chặn đứng truy cập trái phép sau đăng xuất
get url
find "/login"
```

---

## 6. Tiêu chuẩn Đo lường Trải nghiệm Con người (Human UX Criteria Checklist)

- [ ] **Default Eye Focus**: Các thông báo lỗi xác thực mật khẩu không khớp có xuất hiện ngay tại trường nhập liệu không? Form đổi mật khẩu có nằm ở vị trí trung tâm thuận mắt không?
- [ ] **Response Latency**: Thời gian xử lý đăng ký tài khoản và cập nhật mật khẩu có đạt chuẩn $\le 3\text{ giây}$ (chấp nhận tối đa $\le 8\text{s}$) không?
- [ ] **Screen Flickering / Jitter**: Quá trình đăng xuất và chuyển hướng có giật chớp màn hình ($\ge 3$ fliks) không?
- [ ] **Typography & Spacing Density**: Các nhãn trường mật khẩu, gợi ý độ dài tối thiểu có rõ ràng, font chữ $\ge 13\text{px}$ và có khoảng thở hợp lý không?
- [ ] **Visual Color & Styling Overload**: Màu sắc thông báo đỏ (lỗi) và xanh (thành công) có tương phản rõ ràng, chuẩn phong cách Atelier và không gây nhức mắt không?

<!-- EXECUTION:START -->
- Latest status: READY_FOR_EXECUTION
- Scheduled for: External/Peer QA Agent
- Browser tool: browsermcp
- Preflight readiness: VERIFIED (actor_role, health_urls, isolation_key, Action Steps & Assertions defined)
- Evidence screenshots directory: screenshots/
- Bug reference: NONE
<!-- EXECUTION:END -->
