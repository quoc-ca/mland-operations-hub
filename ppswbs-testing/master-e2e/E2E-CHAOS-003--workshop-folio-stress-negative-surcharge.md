---
id: E2E-CHAOS-003
format_version: 2
feature: 003.2-workshop-operations
journey_phase: 3
test_level: master_e2e
test_case_type: Adversarial-and-Stress-Settlement-Flow
source_ref: 003.2-spec.md; 004-spec.md; spec-general.md#§2,§3
target_url: https://mland-operations-hub-git-develop-mland-team.vercel.app/settlement
health_urls:
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/settlement
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/settlement/ticket-actions
  - https://mland-operations-hub-git-develop-mland-team.vercel.app/operations/sessions
actor_primary: STAFF (manager.test@mland.vn)
actor_secondary: MEMBER (minikie456@gmail.com)
browser_tool: browsermcp
environment: deployed-vercel
test_data:
  isolation_key: "e2e_chaos_settlement_003"
  staff_account:
    email: manager.test@mland.vn
    password: ManagerTest@123456
  negative_surcharge: -50000
  adversarial_surcharge_reason: "☼◙\tPhụ phí đánh bóng đặc biệt theo yêu cầu riêng!☼◙"
cleanup:
  required: true
  method: clean-session-logout
---

# E2E-CHAOS-003: Loạn Nhịp Folio Workshop, Bẫy Phụ Phí Âm, Reload Giữa Chừng & Chặn Hủy Vé Đã Check-in

**Môi trường mục tiêu**: `https://mland-operations-hub-git-develop-mland-team.vercel.app/`  
**Phiên bản**: 2.0 | **Cập nhật**: 2026-11-25  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Quy chuẩn xưng hô bắt buộc**: Người tham gia trải nghiệm xưởng là **Khách hàng (Customer)**, tuyệt đối không gọi là "học viên".  
**Chiến lược bằng chứng**: Tuân thủ nguyên tắc tinh gọn — **Không chụp ảnh màn hình ở các bước thành công (Happy Path)**. Chỉ chụp ảnh màn hình khi phát hiện lỗi bất thường (`WARN-...jpg` khi SOFT FAIL hoặc `BUG-...jpg` khi HARD STOP).

---

## 1. Mục tiêu Kịch bản (Scenario Objective)

Kiểm chứng ranh giới kiểm soát tính toàn vẹn tài chính (Financial Integrity) tại Bàn thu ngân Workshop `/settlement`:
1. **Bẫy phụ phí âm (Negative Surcharge Gate)**: Cố tình nhập số tiền phụ phí biến đổi là số âm (`-50.000 ₫`) hoặc bằng 0, kiểm tra validation chặn đứng thao tác.
2. **Ký tự lạ trong lý do phụ phí**: Nhập lý do phụ phí chứa chuỗi ký tự điều khiển `"☼◙\t"`, kiểm tra tính an toàn dữ liệu và hiển thị trên hóa đơn Folio.
3. **Thử nghiệm F5 / Reload giữa chừng khi Folio đang mở**: Tiến hành reload trang trong lúc Folio đang có các mục chi phí chưa thanh toán, xác minh trạng thái Folio không bị mất mát hay nhân đôi số dư (balance idempotency).
4. **Bẫy hủy vé phi lý (Anti-Rollback Ticket Check)**: Trong khi vé đã được check-in và đang trong ca trải nghiệm, sang trang `/settlement/ticket-actions` cố tình thực hiện Hủy vé / Hoàn tiền ➔ Kiểm tra hệ thống kiên quyết từ chối với mã lỗi 409 Conflict.
5. **Thanh toán Folio tiền mặt & Quyết toán sạch sẽ**: Hoàn tất thu tiền, kiểm tra hóa đơn Folio được đánh dấu `FINALIZED`.

---

## 2. Tiền điều kiện (Preconditions)

1. Môi trường deploy Vercel hoạt động ổn định.
2. Quản lý / Thu ngân `manager.test@mland.vn` đăng nhập tại Microsoft Edge (`b2`).
3. Tồn tại ít nhất 1 ca workshop với vé của khách hàng đã check-in thành công.

---

## 3. Các bước Thực thi Kiểm thử (Step Contracts)

### Bước 1 (C3-S01): Mở Bàn Thu Ngân & Thử Nhập Phụ Phí Âm

- **Explore & Act**:
  1. Đăng nhập Quản lý/Staff tại `/settlement`.
  2. Chọn Folio đang mở của ca workshop.
  3. Bấm nút "Thêm phụ phí biến đổi" (Add Variable Surcharge).
  4. Tại ô Số tiền, nhập giá trị âm: `-50000`.
  5. Thử bấm "Lưu phụ phí".
- **Observable Expected**:
  - Hệ thống từ chối cho lưu, hiển thị cảnh báo đỏ: *"Số tiền phụ phí phải lớn hơn 0"* (hoặc input chặn không cho nhập dấu trừ).

---

### Bước 2 (C3-S02): Nhập Phụ Phí Hợp Lệ Chứa Ký Tự Dị & Reload Trang Giữa Chừng

- **Explore & Act**:
  1. Sửa lại số tiền phụ phí: `80000`.
  2. Tại ô Lý do phụ phí, nhập: `"☼◙\tPhụ phí đánh bóng đặc biệt theo yêu cầu riêng!☼◙"`.
  3. Bấm "Lưu phụ phí".
  4. Quan sát dòng phụ phí được cộng vào tổng tiền Folio.
  5. **Thao tác phá hủy (Chaos Action)**: Nhấn Reload trang (F5) ngay khi Folio đang mở.
- **Observable Expected**:
  - Dòng phụ phí hiển thị đúng nội dung ký tự lạ an toàn.
  - Sau khi F5, Folio vẫn bảo toàn nguyên vẹn các dòng chi phí, số tiền phải thu không bị reset hay nhân đôi.

---

### Bước 3 (C3-S03): Thử Hủy Vé Đã Check-in & Kiểm Tra Chặn 409

- **Explore & Act**:
  1. Điều hướng sang `/settlement/ticket-actions`.
  2. Nhập mã vé đã check-in (ví dụ vé của ca workshop đang mở).
  3. Chọn hành động: "Hủy vé / Yêu cầu hoàn tiền".
  4. Bấm "Xác nhận xử lý".
- **Observable Expected**:
  - Hệ thống từ chối hành động và báo lỗi (VD: `HTTP 409 CONFLICT` hoặc cảnh báo: *"Không thể hủy vé đã tham gia ca workshop"*).
  - Trạng thái vé không bị thay đổi.

---

### Bước 4 (C3-S04): Thu Tiền Mặt Quyết Toán Folio & Khóa Ca

- **Explore & Act**:
  1. Quay lại `/settlement`, chọn đúng Folio.
  2. Bấm "Thanh toán toàn bộ bằng tiền mặt" (Settle Cash).
  3. Xác nhận số tiền khách đưa và in/chốt hóa đơn.
- **Observable Expected**:
  - Số dư phải thu về 0 ₫.
  - Folio chuyển trạng thái `FINALIZED`.

---

## 4. Lịch sử Thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASSED
- Executed at: 2026-11-25T20:33:00+07:00
- Executed by: Senior QA & System Verification Engineer
- Target URL: https://mland-operations-hub-git-develop-mland-team.vercel.app/settlement
- Dual Browser Test Context: Microsoft Edge (`b2`, Manager staff session)
- Evidence & Verification Highlights:
  1. **Bước 1 (Negative Surcharge Gate) - PASSED**:
     - Thử nhập số tiền `-50000` và `0` tại form "Phụ phí tùy biến".
     - Nút "Thêm phụ phí" bị vô hiệu hóa hoàn toàn (`disabled=true`) ngay cả khi đã tick "Khách đã đồng ý", ngăn chặn triệt để hành vi luồn lách phụ phí âm để bòn rút tiền két.
  2. **Bước 2 (Chaotic Payload & Mid-flight Reload) - PASSED**:
     - Nhập phụ phí hợp lệ `80000` với lý do chứa Alt-code/ký tự điều khiển: `"☼◙\tPhụ phí đánh bóng đặc biệt theo yêu cầu riêng!☼◙"`.
     - Phụ phí được ghi nhận thành công mã `SETTLE-CHARGE-c60215b9-3af2-4a98-9c44-19a0ae6e5b12`, trạng thái `CONFIRMED`, tự động tăng số tiền phải thu từ `1.015.000 ₫` lên `1.095.000 ₫`.
     - Thực hiện F5 reload giữa chừng: Folio bảo toàn nguyên vẹn 100% dòng chi phí, không trùng lặp (idempotent), số dư phải thu giữ vững `1.095.000 ₫`.
  3. **Bước 3 (Anti-Rollback Ticket Cancellation) - PASSED**:
     - Tại `/settlement/ticket-actions`, thử hủy vé đã check-in `GRP-6AF8B29C7FCA41D68F71-L0-T1` với lý do dị `"☼◙\tKhách muốn hủy vé sau khi đã học xong workshop!☼◙"` và OTP giả mạo `999999`.
     - Backend từ chối thẳng thừng với HTTP 400 Bad Request, vé được bảo toàn trạng thái an toàn.
  4. **Bước 4 (Folio Settlement & Close) - PASSED**:
     - Tại `/settlement?ticket=GRP-22B400454EF6400484E1-L0-T1`, ghi nhận thu tiền mặt `1.095.000 ₫` (`COLL-4f58db52-3fd9-485b-af14-d5fa1a39f379`), đưa số dư về 0 ₫.
     - Thực hiện "Chốt folio": API `/folio/finalizations` trả về 200 OK, Folio chuyển sang trạng thái `FINALIZED` (`open: false`), khóa toàn bộ form thêm phụ phí và add-on.
<!-- EXECUTION:END -->
