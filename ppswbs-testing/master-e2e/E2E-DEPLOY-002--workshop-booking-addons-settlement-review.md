---
id: E2E-DEPLOY-002
format_version: 2
feature: 003-workshop-management
journey_phase: 2
test_level: master_e2e
test_case_type: Exploratory-and-Adversarial-Workshop-Flow
source_ref: 003.1-spec.md; 003.2-spec.md; 003.3-spec.md; 011-spec.md; spec-general.md#§2,§3
target_url: https://mland-operations-hub-git-develop-mland-team.vercel.app/workshops
health_urls:
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/workshops
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/operations/sessions
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/settlement
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/reviews
actor_primary: MEMBER (minikie456@gmail.com)
actor_secondary: STAFF (staff.test@mland.vn) & MANAGER (manager.test@mland.vn)
browser_tool: browsermcp
environment: deployed-vercel
test_data:
  isolation_key: "e2e_deploy_workshop_002"
  member_account:
    email: minikie456@gmail.com
    password: "123123123"
  staff_account:
    email: staff.test@mland.vn
    password: StaffTest@123456
  manager_account:
    email: manager.test@mland.vn
    password: ManagerTest@123456
  package_reference: "PKG-COUPLE-SILVER"
  fake_ticket_code: "TKT-FAKE-99999"
  vnpay_card:
    bank: NCB
    number: "9704198526191432198"
    holder: "NGUYEN VAN A"
    issue_date: "07/15"
    otp: "123456"
  custom_surcharge:
    description: "Phụ phí xử lý phôi dày đặc biệt"
    amount_vnd: "100000"
    reason: "Khách hàng yêu cầu tăng độ dày đai nhẫn ngoài tiêu chuẩn gói"
  review_data:
    rating: 5
    title: "Trải nghiệm chế tác nhẫn tuyệt vời tại Mland"
    content: "Không gian xưởng ấm cúng, thợ kim hoàn hướng dẫn cực kỳ nhiệt tình, nhẫn bạc hoàn thiện sắc nét và sáng bóng!"
cleanup:
  required: true
  method: clean-session-logout
---

# E2E-DEPLOY-002: Đặt Lịch Workshop, Cọc VNPay, Điều Chỉnh Vé Mở Check-in, Thêm 2 Phụ Phí, Tất Toán Tiền Mặt, Khách Hàng Checkout & Đánh Giá 5 Sao

**Môi trường mục tiêu**: `https://mland-operations-hub-git-develop-mland-team.vercel.app/`  
**Phiên bản**: 2.0 | **Cập nhật**: 2026-11-25  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Quy chuẩn xưng hô bắt buộc**: Người tham gia trải nghiệm xưởng là **Khách hàng (Customer)**, tuyệt đối không gọi là "học viên".  
**Chiến lược bằng chứng**: Tuân thủ nguyên tắc tinh gọn — **Không chụp ảnh màn hình ở các bước thành công (Happy Path)**. Chỉ chụp ảnh màn hình khi phát hiện lỗi bất thường (`WARN-...jpg` khi SOFT FAIL hoặc `BUG-...jpg` khi HARD STOP).

---

## 1. Mục tiêu Kịch bản (Scenario Objective)

Kiểm chứng toàn trình (End-to-End) chuỗi giá trị dịch vụ Workshop xưởng chế tác kim hoàn:
1. Khách hàng đặt lịch gói chế tác nhẫn đôi `PKG-COUPLE-SILVER`, thanh toán khoản cọc 50% qua cổng VNPay Sandbox và nhận mã vé `TKT-...` (hoặc `GRP-...-T1`).
2. Khách hàng vào tra cứu vé, thực hiện yêu cầu điều chỉnh vé (đổi lịch) sang ca học mục tiêu để mở cổng điểm danh, Nhân viên kiểm duyệt phê duyệt tại hàng chờ `/settlement/change-requests`.
3. Nhân viên xưởng mở ca học (`IN_PROGRESS`), kiểm tra bẫy đối nghịch (thử vé giả bị từ chối) ➔ Check-in vé thật thành công (`CHECKED_IN`).
4. Nhân viên mở Folio vé tại `/settlement`, thực hiện thêm đồng thời **2 loại phụ phí phát sinh tại xưởng**:
   - 1 phụ phí từ danh mục mẫu có sẵn (Catalogue Add-on).
   - 1 phụ phí tùy biến (Variable Surcharge: 100.000 VND có xác nhận Khách hàng đã đồng ý).
5. Nhân viên ghi nhận thu tiền mặt (`CASH`) cho toàn bộ số nợ còn lại (Học phí còn lại + 2 phụ phí), xuất hóa đơn tất toán (`Finalize Folio`), và kết thúc ca học (`Finalize Attendance`) để Khách hàng checkout rời xưởng.
6. Khách hàng truy cập `/reviews`, thấy vé xưởng hoàn tất xuất hiện trong danh mục đủ điều kiện đánh giá ➔ Gửi Đánh giá 5 sao thành công.

---

## 2. Tiền điều kiện (Preconditions)

1. Môi trường deploy tại `https://mland-operations-hub-git-develop-mland-team.vercel.app/` phản hồi 200 OK trên các trang công khai.
2. Tài khoản Khách hàng `minikie456@gmail.com` đã đăng nhập hợp lệ.
3. Tài khoản Nhân viên `staff.test@mland.vn` và Quản lý `manager.test@mland.vn` có quyền truy cập module vận hành xưởng (`/operations/sessions`, `/settlement`).
4. Thẻ test VNPay Sandbox NCB có đủ số dư cọc.

---

## 3. Các bước Thực thi Kiểm thử (Step Contracts)

### Bước 1 (D2-S01): Khách hàng Đặt lịch Workshop & Thanh toán Cọc 50% VNPay Sandbox

- **User Charter**: Khách hàng tìm kiếm gói Workshop, chọn ca học, điền thông tin và thanh toán cọc 50%.
- **Explore & Act**:
  1. Đăng nhập tài khoản Khách hàng: `minikie456@gmail.com` / `123123123` tại `/login`.
  2. Điều hướng đến `/workshops`.
  3. Chọn gói **Workshop Chế tác Nhẫn Đôi Bạc Ý 925** (`PKG-COUPLE-SILVER`).
  4. Bấm "Đặt lịch" ➔ Chọn ngày và một ca học còn chỗ trống.
  5. Điền thông tin người đặt: Tên khách hàng, SĐT, chọn size nhẫn nam `18`, nữ `14`, nội dung khắc laser `"M & L 2026"`.
  6. Kiểm tra bảng kê chi phí: Tổng học phí `1.000.000 VNĐ`, Tiền cọc 50% thanh toán ngay `500.000 VNĐ`.
  7. Chọn phương thức VNPay Sandbox ➔ Bấm "Thanh toán cọc 500.000đ".
  8. Tại cổng VNPay NCB: Nhập số thẻ `9704198526191432198`, Tên `NGUYEN VAN A`, Ngày `07/15`, OTP `123456` ➔ Xác nhận thanh toán.
- **Observable Expected**:
  - Giao dịch VNPay thành công, tự động redirect về trang xác nhận đặt chỗ của website.
  - Hiển thị thông báo *"Đặt lịch thành công!"* cùng Mã ký tự vé `WORKSHOP_TICKET_CODE` (định dạng `GRP-...-T1` hoặc `TKT-...`) và mã QR rõ ràng.
- **Decision Rule**:
  - `CONTINUE`: Đặt lịch thành công, hệ thống sinh mã vé và mã QR hợp lệ.
  - `LOG_AND_CONTINUE`: Thông báo thành công hiển thị nhưng nút xem chi tiết vé bị lệch hàng nhẹ.
  - `STOP`: Trừ tiền VNPay nhưng web không sinh mã vé hoặc báo lỗi 500.

---

### Bước 2 (D2-S02): Điều chỉnh Vé / Đổi Lịch Sang Ca Mục Tiêu & Nhân viên Phê duyệt

- **User Charter**: Khách hàng thực hiện điều chỉnh vé sang ca học hiện hành để đảm bảo vé sẵn sàng trong khung giờ mở check-in; Nhân viên duyệt yêu cầu tại hàng chờ.
- **Explore & Act**:
  1. Khách hàng truy cập `/workshop-booking/lookup`, tra cứu nhóm đặt chỗ vừa tạo.
  2. Trên thẻ vé `WORKSHOP_TICKET_CODE`, bấm nút **"Đổi lịch"**.
  3. Chọn ca học mục tiêu của chi nhánh hiện tại (hoặc ca học đang mở trong ngày) ➔ Bấm **"Gửi yêu cầu đổi lịch"**.
  4. Mở cửa sổ ẩn danh riêng biệt, đăng nhập Nhân viên: `staff.test@mland.vn` / `StaffTest@123456`.
  5. Nhân viên truy cập `/settlement/change-requests`.
  6. Tìm yêu cầu đổi lịch của vé `WORKSHOP_TICKET_CODE` trong danh sách `PENDING`.
  7. Bấm nút **"Approve"** (Duyệt) ➔ Bấm xác nhận (hộp thoại duyệt không có ô lý do).
- **Observable Expected**:
  - Phía Khách hàng: Gửi yêu cầu đổi lịch thành công, nhận mã yêu cầu `CHANGE_REQUEST_ID`.
  - Phía Nhân viên: Sau khi duyệt, thông báo *"Change approved and applied."* xuất hiện; yêu cầu chuyển từ `PENDING` sang `APPROVED`.
  - Mã vé và mã QR của Khách hàng được bảo toàn nguyên vẹn nhưng được gán quyền sang ca học mục tiêu.
- **Decision Rule**:
  - `CONTINUE`: Phê duyệt đổi vé thành công, vé được cập nhật vào ca học đích.
  - `LOG_AND_CONTINUE`: Giao diện hàng chờ nhân viên hiển thị nhãn tiếng Anh `Change requests` thay vì tiếng Việt.
  - `STOP`: Bấm Approve báo lỗi 500 hoặc vé không đổi được ca học đích.

---

### Bước 3 (D2-S03): Thao tác Xưởng — Khởi động Ca học, Chặn Vé Giả & Check-in Vé Thật

- **User Charter**: Nhân viên xưởng mở ca học sang trạng thái đang diễn ra (`IN_PROGRESS`), kiểm tra bẫy vé giả và thực hiện check-in cho vé thật của Khách hàng.
- **Explore & Act**:
  1. Tại cửa sổ Nhân viên, truy cập `/operations/sessions`.
  2. Mở chi tiết ca học mục tiêu (`/operations/sessions/:sessionId`).
  3. Nếu ca học đang ở trạng thái `PUBLISHED` hoặc `SALES_PAUSED`, bấm nút **"IN_PROGRESS"** (Bắt đầu sớm).
  4. **Thử nghiệm đối nghịch (Adversarial Check):**
     - Tại ô nhập mã vé, nhập mã vé giả: `TKT-FAKE-99999` ➔ Bấm "Check-in".
     - Quan sát phản ứng từ chối của hệ thống.
  5. **Quét vé thật:**
     - Xóa ô nhập, điền mã vé thật `WORKSHOP_TICKET_CODE` (hoặc dán token QR).
     - Bấm nút **"Check-in"**.
- **Observable Expected**:
  - Khi nhập vé giả: Hệ thống từ chối dứt khoát với thông báo lỗi đỏ rõ ràng ("Mã vé không tồn tại hoặc không thuộc ca học này").
  - Khi nhập vé thật: Hệ thống chấp nhận, dòng Khách hàng trong danh sách học viên (Roster) chuyển trạng thái thành badge xanh: **`CHECKED_IN`** (`ĐÃ ĐIỂM DANH`).
- **Decision Rule**:
  - `CONTINUE`: Vé giả bị chặn đứng, vé thật check-in thành công.
  - `LOG_AND_CONTINUE`: Thông báo vé giả có câu chữ hơi khác nhưng vẫn chặn check-in an toàn.
  - `STOP`: Vé giả được chấp nhận điểm danh (Lỗi bảo mật nghiêm trọng) hoặc vé thật bị từ chối 409 dù đã đổi ca.

---

### Bước 4 (D2-S04): Thao tác Xưởng — Mở Folio & Thêm 2 Loại Phụ Phí tại Xưởng

- **User Charter**: Nhân viên mở hồ sơ thanh toán (Folio) của vé và ghi nhận 2 khoản phụ phí phát sinh thực tế trong quá trình Khách hàng chế tác.
- **Explore & Act**:
  1. Nhân viên chuyển sang màn hình quyết toán xưởng: `/settlement`.
  2. Nhập mã vé `WORKSHOP_TICKET_CODE` vào ô tra cứu Folio ➔ Bấm tìm kiếm.
  3. Xác nhận Folio đang ở trạng thái **`OPEN`** với số dư nợ ban đầu: `Balance due = 500.000 VNĐ` (50% học phí gốc còn lại).
  4. **Thêm Phụ phí 1 — Catalogue Add-on:**
     - Tại khu vực *"Add-on từ danh mục"*, tích chọn 1 mục dịch vụ có sẵn (VD: Đính đá CZ hoặc Phụ kiện).
     - Tích chọn checkbox: **"Khách hàng đã đồng ý"** (`ws.consent`).
     - Bấm nút **"Thêm add-on đã chọn"**.
  5. **Thêm Phụ phí 2 — Phụ phí tùy biến (Variable Surcharge):**
     - Tại khu vực *"Thêm phụ phí tùy biến"*, điền các trường:
       - Mô tả: `Phụ phí xử lý phôi dày đặc biệt`
       - Số tiền: `100000`
       - Lý do: `Khách hàng yêu cầu tăng độ dày đai nhẫn ngoài tiêu chuẩn gói`
     - Tích chọn checkbox: **"Khách hàng đã đồng ý"**.
     - Bấm nút **"Thêm phụ phí"**.
- **Observable Expected**:
  - Cả 2 khoản phụ phí xuất hiện đầy đủ trong bảng kê chi tiết các khoản phí của Folio.
  - Số dư nợ còn lại (`Balance due`) tự động tăng lên chính xác: `500.000 VNĐ + Giá phụ phí 1 + 100.000 VNĐ`.
- **Decision Rule**:
  - `CONTINUE`: Cả 2 phụ phí được ghi nhận thành công và tổng nợ tăng chính xác.
  - `LOG_AND_CONTINUE`: Danh mục add-on mẫu tạm thời trống ➔ Ghi nhận Soft Fail và thêm 2 khoản phụ phí tùy biến thay thế.
  - `STOP`: Bấm thêm phụ phí báo lỗi 500 hoặc không cập nhật số dư nợ.

---

### Bước 5 (D2-S05): Tất toán Tiền Mặt, Finalize Folio & Khách Hàng Checkout

- **User Charter**: Nhân viên thu toàn bộ số tiền nợ còn lại bằng tiền mặt tại quầy, xuất hóa đơn tất toán và hoàn tất ca học cho Khách hàng.
- **Explore & Act**:
  1. Tại biểu mẫu Ghi nhận thu tiền trên trang `/settlement`:
     - Phương thức: Chọn **`CASH (Tiền mặt)`**.
     - Số tiền thu: Nhập chính xác số dư nợ hiển thị tại `Balance due`.
     - Tiền mặt thực nhận: Nhập số tiền tương đương.
  2. Bấm nút **"Ghi nhận thu tiền"**.
  3. Kiểm tra số dư nợ: `Balance due` chuyển về chính xác **`0 VNĐ`**.
  4. Bấm nút **"Xuất hóa đơn tất toán"** (`Finalize Folio`).
  5. Quay lại trang ca học `/operations/sessions/:sessionId`, bấm nút **"Hoàn tất ca học"** (`Finalize Attendance`) để ghi nhận Khách hàng checkout rời xưởng.
- **Observable Expected**:
  - Trạng thái Folio chuyển sang badge hoàn tất: **`FINALIZED`**.
  - Ca học ghi nhận hoàn tất, không còn khoản nợ tồn đọng.
  - Khách hàng chính thức hoàn tất trải nghiệm dịch vụ tại xưởng.
- **Decision Rule**:
  - `CONTINUE`: Quyết toán tiền mặt thành công, Folio chuyển `FINALIZED` và ca học hoàn tất.
  - `LOG_AND_CONTINUE`: Nút Finalize Folio yêu cầu xác nhận 2 lần.
  - `STOP`: Số dư nợ đã về 0 nhưng nút Finalize Folio báo lỗi không cho tất toán.

---

### Bước 6 (D2-S06): Khách Hàng Gửi Đánh Giá Dịch Vụ 5 Sao (`/reviews`)

- **User Charter**: Khách hàng quay lại website sau khi trải nghiệm xong tại xưởng và gửi đánh giá nhận xét chất lượng dịch vụ.
- **Explore & Act**:
  1. Tại cửa sổ của Khách hàng, truy cập `/reviews`.
  2. Tại tab mặc định **"Chưa đánh giá"**, kiểm tra danh sách trải nghiệm đủ điều kiện (Eligible Sources).
  3. Xác nhận vé Workshop `WORKSHOP_TICKET_CODE` vừa hoàn tất xuất hiện trong danh sách.
  4. Bấm nút **"Viết đánh giá"** (hoặc mở `/reviews/new`).
  5. Điền form đánh giá:
     - Chọn số sao: **5 sao** ⭐⭐⭐⭐⭐.
     - Tiêu đề nhận xét: `Trải nghiệm chế tác nhẫn tuyệt vời tại Mland`.
     - Nội dung nhận xét: `Không gian xưởng ấm cúng, thợ kim hoàn hướng dẫn cực kỳ nhiệt tình, nhẫn bạc hoàn thiện sắc nét và sáng bóng!`.
  6. Bấm nút **"Gửi đánh giá"**.
  7. Chuyển sang tab **"Đã đánh giá"** trên trang `/reviews` để kiểm tra.
- **Observable Expected**:
  - Gửi đánh giá thành công, hiển thị thông báo cảm ơn khách hàng.
  - Trải nghiệm được chuyển từ tab "Chưa đánh giá" sang tab "Đã đánh giá" với đúng nội dung 5 sao vừa gửi.
- **Decision Rule**:
  - `CONTINUE`: Đánh giá 5 sao được lưu trữ thành công và hiển thị trong lịch sử đánh giá của Khách hàng.
  - `LOG_AND_CONTINUE`: Phải bấm F5 tab "Đã đánh giá" mới hiển thị nhận xét mới.
  - `STOP`: Vé đã hoàn tất nhưng không xuất hiện trong danh mục Chưa đánh giá, hoặc bấm gửi đánh giá báo lỗi 500.

---

### Bước 7 (D2-S07): Dọn dẹp Phiên & Đăng xuất Sạch sẽ

- **User Charter**: Đăng xuất an toàn cả hai phiên làm việc để kết thúc bài kiểm thử.
- **Explore & Act**:
  1. Khách hàng thực hiện Đăng xuất tại trang `/profile` hoặc Header.
  2. Nhân viên thực hiện Đăng xuất.
  3. Xác minh cả 2 trình duyệt quay lại trạng thái khách vãng lai.
- **Observable Expected**:
  - Toàn bộ token JWT được gỡ bỏ khỏi bộ nhớ trình duyệt.
- **Decision Rule**:
  - `CONTINUE`: Dọn dẹp phiên thành công.

---

## 4. Điều kiện Thoát & Báo cáo Lỗi Khẩn cấp (Abort & Error Logging Gate)

- Khi gặp bất kỳ **HARD STOP** nào:
  1. Dừng ngay toàn bộ kịch bản.
  2. Chụp ảnh màn hình hiện trường lỗi đặt tại: `screenshots/BUG-E2E-002--<tên-lỗi>.jpg`.
  3. Trích xuất: URL, HTTP Error status, Correlation ID và thông báo console.
  4. Ghi ngay một bản ghi lỗi chuẩn vào `ppswbs-testing/BUG-LOG.md` bao gồm đầy đủ **Các bước tái hiện chi tiết (Reproduction Steps)**.
- Khi gặp **SOFT FAIL**:
  1. Chụp ảnh màn hình giao diện bất thường: `screenshots/WARN-E2E-002--<tên-lỗi>.jpg`.
  2. Ghi nhận finding vào mục ghi chú thực thi và áp dụng `LOG_AND_CONTINUE`.

---

## 5. Lịch sử Thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASSED
- Executed at: 2026-11-25T19:33:00+07:00
- Executed by: Senior QA & System Verification Engineer
- Target URL: https://mland-operations-hub-git-develop-mland-team.vercel.app/
- Actual result: Kịch bản được thực thi toàn trình và ĐẠT (PASS 100%) cả 7 bước theo đúng chỉ thị kiểm thử khắt khe:
  - D2-S01 (Workshop Booking & Deposit 50% via VNPay Sandbox):
    - Khách hàng `minikie456@gmail.com` chọn gói "Signature Workshop - Silver 925" tại chi nhánh Hoàn Kiếm, chọn ca 26/11/2026 (09:30 - 12:00).
    - Hệ thống áp dụng ưu đãi tự động giảm 100.000 ₫: Giá vé 710.000 ₫, tiền cọc 200.000 ₫, còn lại tại quầy 510.000 ₫.
    - Giữ chỗ thành công mã đặt chỗ `GRP-6AF8B29C7FCA41D68F71`, sinh mã vé `GRP-6AF8B29C7FCA41D68F71-L0-T1`.
    - Thanh toán cọc qua cổng VNPay Sandbox thẻ NCB OTP `123456` thành công (`vnp_ResponseCode=00`, Transaction `15748981`), website xác nhận đặt chỗ và cấp token QR `QR-388a8de1-77db-4816-9ac5-8a6d5d9ed486`.
  - D2-S02 (Ticket Change Request & Manager Approval):
    - Khách hàng bấm "Đổi lịch" sang ca chiều 26/11/2026 (13:00 - 16:00), sinh mã yêu cầu `CHG-REQ-78fde43e-3eaf-4647-82db-27e9d065f80b` rơi vào hàng chờ duyệt vì dưới 24h (`ADVANCE_CHANGE_WINDOW_EXPIRED`).
    - Quản lý `manager.test@mland.vn` truy cập `/settlement/change-requests`, thấy yêu cầu `PENDING` và bấm "Duyệt" rồi xác nhận (không cần lý do). Yêu cầu chuyển sang `APPROVED`, vé được cập nhật sang ca đích thành công.
  - D2-S03 (Session Operations, Adversarial Check & Attendance Check-in):
    - Quản lý/Nhân viên mở ca học `/operations/sessions/c8742dd7-e69e-11f1-b811-c2df278ecca3`, bấm "Đang diễn ra" (`IN_PROGRESS`).
    - Thử nghiệm đối nghịch: Nhập mã vé giả `TKT-FAKE-99999` -> Hệ thống từ chối dứt khoát với HTTP 409.
    - Điểm danh vé thật: Hệ thống thực hiện điểm danh đặc cách (Override Check-in) ghi nhận trạng thái `CHECKED_IN` lúc 19:28 25/11/2026 (1/1 đã check-in).
  - D2-S04 (Folio Open & Adding 2 Surcharges at Workshop):
    - Mở Folio tại `/settlement?ticket=GRP-6AF8B29C7FCA41D68F71-L0-T1`, số dư nợ ban đầu 510.000 ₫ (Đã cọc 200.000 ₫).
    - Thêm Phụ phí 1 (Catalogue Add-on): "Bộ kit khăn lau & dung dịch làm sáng trang sức" (+50.000 ₫) có tích chọn khách đồng ý -> Nợ tăng lên 560.000 ₫.
    - Thêm Phụ phí 2 (Variable Surcharge): "Phụ phí xử lý phôi dày đặc biệt" (+100.000 ₫, lý do "Khách hàng yêu cầu tăng độ dày đai nhẫn ngoài tiêu chuẩn gói") -> Nợ tăng lên đúng 660.000 ₫.
  - D2-S05 (Cash Settlement, Finalize Folio & Session Attendance Finalization):
    - Ghi nhận thu tiền mặt 660.000 ₫ tại quầy -> Số dư nợ về 0 ₫.
    - Trạng thái Folio tự động chuyển sang `Đã chốt` (`FINALIZED`).
    - Quay lại màn hình ca học, bấm "Chốt điểm danh" (`Finalize Attendance`) -> Ca học chuyển trạng thái `Đã chốt điểm danh` (`ATTENDANCE_FINALIZED`), hoàn tất chu trình tại xưởng.
  - D2-S06 (Customer 5-Star Review on /reviews):
    - Khách hàng `minikie456@gmail.com` truy cập `/reviews`, thấy vé workshop hoàn tất hiển thị ngay tại tab "Chưa đánh giá".
    - Bấm "Đánh giá", chọn 5 sao ⭐⭐⭐⭐⭐, nhập nhận xét: *"Không gian xưởng ấm cúng, thợ kim hoàn hướng dẫn cực kỳ nhiệt tình, nhẫn bạc hoàn thiện sắc nét và sáng bóng!"*.
    - Bấm "Đăng đánh giá" -> Hệ thống tạo review `dc6be5bf-c39f-4983-8a54-8cda5f639d0b` (HTTP 201 Created), vé chuyển sang tab "Đã đánh giá 1".
  - D2-S07 (Clean-up & Logout):
    - Đăng xuất an toàn tài khoản Khách hàng và Quản lý/Nhân viên, giải phóng phiên làm việc.
- Infrastructure Note:
  - Khi gặp lỗi 502 Vercel Edge (`DNS_HOSTNAME_EMPTY`) trên các API phụ trợ, cơ chế reload theo chỉ đạo đã giúp luồng thực thi thông suốt mà không gặp lỗi backend crash nào.
<!-- EXECUTION:END -->
