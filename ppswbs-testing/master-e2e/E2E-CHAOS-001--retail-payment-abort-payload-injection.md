---
id: E2E-CHAOS-001
format_version: 2
feature: 008-retail-orders
journey_phase: 1
test_level: master_e2e
test_case_type: Adversarial-and-Chaos-Retail-Flow
source_ref: 008-spec.md; spec-general.md#§2,§3
target_url: https://mland-operations-hub-git-develop-mland-team.vercel.app/products
health_urls:
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/products
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/member/cart
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/member/checkout
actor_primary: MEMBER (minikie456@gmail.com)
actor_secondary: CARRIER (carrier.test@mland.vn)
browser_tool: browsermcp
environment: deployed-vercel
test_data:
  isolation_key: "e2e_chaos_retail_001"
  member_account:
    email: minikie456@gmail.com
    password: "123123123"
  product:
    name: "Nhẫn bạc đính đá topaz xanh"
    id: 10
  adversarial_payload: "☼◙\tGhi chú giao hàng đặc biệt: Giao sau 18h & gọi trước 15p!☼◙"
  recipient:
    name: "NGUYEN VAN CHAOS"
    phone: "0912345678"
    address: "99 Pho Thoai Khoay, Hoan Kiem, Ha Noi"
cleanup:
  required: true
  method: clean-session-logout
---

# E2E-CHAOS-001: Gian Lận Giao Dịch Bán Lẻ, Chèn Payload Dị Ký Tự Điều Khiển, Bấm Hủy Cổng VNPay & Thanh Toán Lại Thành Công

**Môi trường mục tiêu**: `https://mland-operations-hub-git-develop-mland-team.vercel.app/`  
**Phiên bản**: 2.0 | **Cập nhật**: 2026-11-25  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Quy chuẩn xưng hô bắt buộc**: Người tham gia trải nghiệm xưởng là **Khách hàng (Customer)**, tuyệt đối không gọi là "học viên".  
**Chiến lược bằng chứng**: Tuân thủ nguyên tắc tinh gọn — **Không chụp ảnh màn hình ở các bước thành công (Happy Path)**. Chỉ chụp ảnh màn hình khi phát hiện lỗi bất thường (`WARN-...jpg` khi SOFT FAIL hoặc `BUG-...jpg` khi HARD STOP).

---

## 1. Mục tiêu Kịch bản (Scenario Objective)

Kiểm chứng tính bền bỉ và khả năng chống chịu lỗi (Resilience & Chaos Tolerance) của hệ thống Bán lẻ Ready Rings:
1. Chèn payload chuỗi ký tự điều khiển và Unicode lạ (`"☼◙\t"`) vào trường Ghi chú đơn hàng / Lời nhắn giao hàng.
2. Thử nghiệm hành vi click spam (Double-click / Rapid click) tại nút Đặt hàng để xác minh cơ chế Idempotency chống tạo đơn trùng lặp.
3. Kích hoạt bẫy Hủy giao dịch tại Cổng VNPay Sandbox: Bấm nút "Hủy / Trở về", xác minh hệ thống xử lý trạng thái giao dịch thất bại chuẩn xác, không bị treo đơn hoặc rò rỉ dữ liệu.
4. Tái thực hiện thanh toán lại lần 2 (Re-attempt checkout) bằng thẻ NCB Sandbox thành công, chứng minh không bị deadlock phiên giỏ hàng hay khóa cứng tồn kho.

---

## 2. Tiền điều kiện (Preconditions)

1. Môi trường deploy Vercel hoạt động ổn định.
2. Tài khoản Khách hàng `minikie456@gmail.com` đã đăng nhập tại Google Chrome (`b1`).
3. Sản phẩm Ready Ring còn tồn kho bán lẻ tại chi nhánh khả dụng.

---

## 3. Các bước Thực thi Kiểm thử (Step Contracts)

### Bước 1 (C1-S01): Thêm Sản phẩm vào Giỏ & Chèn Payload Ký tự Dị vào Ghi chú

- **Explore & Act**:
  1. Mở trang `/products`, chọn sản phẩm "Nhẫn bạc đính đá topaz xanh" (hoặc nhẫn có sẵn).
  2. Chọn chi nhánh và kích cỡ khả dụng, thêm vào giỏ.
  3. Mở `/member/cart`, chọn sản phẩm và bấm "Mua hàng" chuyển sang `/member/checkout`.
  4. Điền thông tin giao hàng: Họ tên `"NGUYEN VAN CHAOS"`, SĐT `"0912345678"`.
  5. Tại ô Ghi chú người mua / Lời nhắn, dán chuỗi: `"☼◙\tGhi chú giao hàng đặc biệt: Giao sau 18h & gọi trước 15p!☼◙"`.
- **Observable Expected**:
  - Giao diện chấp nhận hoặc chuẩn hóa chuỗi Unicode an toàn, không bị vỡ giao diện (CSS overflow / layout shift).

---

### Bước 2 (C1-S02): Thử nghiệm Click Spam & Điều hướng sang VNPay Sandbox

- **Explore & Act**:
  1. Chọn phương thức thanh toán VNPay.
  2. Bấm nút "Đặt hàng" (tiến hành double-click hoặc click nhanh 2 lần liên tiếp).
  3. Quan sát phản ứng hệ thống và điều hướng sang VNPay Sandbox.
- **Observable Expected**:
  - Nút bấm lập tức disabled hoặc hiển thị loading spinner để ngăn chặn gửi nhiều request trùng lặp.
  - Hệ thống chỉ sinh duy nhất 1 mã đơn hàng (`orderId`), điều hướng mượt mà sang `sandbox.vnpayment.vn`.

---

### Bước 3 (C1-S03): Kích hoạt Bẫy Hủy Giao dịch tại Cổng VNPay & Rollback Trạng thái

- **Explore & Act**:
  1. Tại màn hình cổng VNPay Sandbox, **không nhập thông tin thẻ**.
  2. Bấm vào nút "Hủy giao dịch" (hoặc "Quay lại").
  3. Xác nhận hủy trên hộp thoại (nếu có) để VNPay trả kết quả thất bại về Mland.
- **Observable Expected**:
  - URL quay về trang thanh toán trả về của Mland với mã phản hồi hủy giao dịch (VD: `vnp_ResponseCode=24`).
  - Giao diện hiển thị rõ ràng thông báo: "Giao dịch không thành công hoặc đã bị hủy".
  - Đơn hàng không bị chuyển nhầm sang trạng thái Đã thanh toán (`PAID`).

---

### Bước 4 (C1-S04): Thanh toán lại Lần 2 Thành công & Chốt Đơn Hợp lệ

- **Explore & Act**:
  1. Từ màn hình kết quả hoặc giỏ hàng, thực hiện thanh toán lại.
  2. Nhập thông tin thẻ NCB Sandbox hợp lệ (`9704198526191432198`, `NGUYEN VAN A`, `07/15`, OTP `123456`).
  3. Xác nhận thanh toán thành công và quay về Mland.
- **Observable Expected**:
  - Giao diện báo: "Đặt hàng thành công!".
  - Đơn hàng chuyển sang trạng thái chờ xử lý giao vận bình thường.

---

## 4. Lịch sử Thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASSED (100%)
- Executed at: 2026-11-25T20:20:00+07:00
- Executed by: Senior QA & System Verification Engineer
- Target URL: https://mland-operations-hub-git-develop-mland-team.vercel.app/
- Order Code: `ML26112587D60DCB`
- Transaction Amount: `543.397 ₫` (Sản phẩm: Nhẫn bạc peridot oval Size 8 + Ship GHTK 23.397 ₫ - Ưu đãi 30.000 ₫)
- Key Chaos Findings:
  1. **Control Character Defense**: Nhập chuỗi chứa ký tự điều khiển Alt+09 (`\t` / `\u0009`) vào form địa chỉ bị chặn triệt để bởi regex `!/[\u0000-\u001F\u007F-\u009F]/`, nút Hoàn thành bị disabled đúng chuẩn FR-037 / FR-040.
  2. **Exotic Unicode Resilience**: Chuỗi Unicode đặc thù `"☼ So 99 Pho Thoai Khoay ◙"` được lưu trữ và hiển thị an toàn trên toàn bộ UI thanh toán và đơn hàng, không xảy ra lỗi CSS overflow hay mã hóa lỗi.
  3. **Idempotency Anti-Spam**: Double-click liên tiếp (cách nhau 50ms) vào nút "Đặt hàng" được frontend debounce chuẩn xác; backend chỉ nhận duy nhất 1 request `POST 201 /api/v1/ready-ring-orders`, không sinh đơn rác.
  4. **Gateway Cancel & State Rollback**: Bấm "Xác nhận hủy" tại cổng VNPay Sandbox điều hướng an toàn về `/member/orders`. Đơn hàng `ML26112587D60DCB` chuyển sang trạng thái `Chờ thanh toán` kèm đồng hồ đếm ngược giữ hàng 10 phút.
  5. **Re-attempt Checkout Success**: Bấm nút "Thanh toán" tại đơn hàng chờ, thanh toán thành công qua thẻ NCB Sandbox (`9704198526191432198`). Đơn chuyển trạng thái hợp lệ sang `Chờ lấy hàng` (`Đang chuẩn bị hàng`).
<!-- EXECUTION:END -->
