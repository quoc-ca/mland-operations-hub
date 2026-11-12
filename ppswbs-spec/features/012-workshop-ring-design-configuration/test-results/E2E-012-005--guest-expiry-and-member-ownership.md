---
scenario_id: E2E-012-005
feature: FE-012
actor: MEMBER
preconditions:
  - backend_health: FAIL
  - frontend_health: FAIL
traceability:
  - FE-012
  - AC-012-OWNERSHIP-EXPIRY
isolation_key: e2e-012-member-005
cleanup:
  - delete_design_record
id: E2E-012-005
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-012-005
target_url: http://localhost:5173/my/designs
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: member-history-fixture
  account_ref: MEMBER_001
  isolation_key: e2e-012-member-005
---

# E2E-012-005: Kiểm tra hết hạn phiên khách vãng lai sau 20 phút và phân quyền lịch sử hội viên

**Ngày tạo**: 2026-11-12  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tính năng**: 012-workshop-ring-design-configuration  

## 1. Tiền điều kiện (Preconditions)

- [ ] Môi trường Backend Spring Boot sẵn sàng tại `http://localhost:8080`.
- [ ] Môi trường Frontend Vite sẵn sàng tại `http://localhost:5173`.
- [ ] Tài khoản Member có ít nhất 1 thiết kế nhẫn đã lưu trữ (`DSG-MEMBER-01`).

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Hết hạn phiên Khách vãng lai sau 20 phút không hoạt động (Inactivity TTL 20 min):** Nếu phiên khách vãng lai không phát sinh tương tác trong 20 phút, request tiếp theo sẽ nhận HTTP 401 `DESIGN_SESSION_EXPIRED`. Hệ thống xóa token khỏi `sessionStorage` và hiển thị cảnh báo "Phiên làm việc đã hết hạn sau 20 phút không hoạt động."
- **Cách ly quyền sở hữu (Ownership Isolation):** Người dùng A không được phép đọc hoặc sửa thiết kế thuộc sở hữu của Người dùng B (nhận 403 `DESIGN_ACCESS_DENIED`).
- **Lưu trữ / Hủy bản nháp (Soft-delete Archive):** Thao tác hủy thiết kế chuyển trạng thái sang `ARCHIVED`, cấm xóa cứng dữ liệu trong DB. Thiết kế đã gắn vào lịch đặt (`ATTACHED_TO_BOOKING` hoặc `LOCKED`) không được phép lưu trữ/hủy.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Đăng nhập Member và truy cập trang lịch sử thiết kế cá nhân
```browser
goto http://localhost:5173/login
fill getByLabel(/Email/i) "member.test@mland.vn"
fill getByLabel(/Mật khẩu/i) "Member@123456"
click getByRole('button', { name: /Đăng nhập/i })
goto http://localhost:5173/my/designs
wait getByTestId("member-design-history-page")
wait --text "Lịch sử thiết kế nhẫn của bạn"
snapshot -i
```

### Step 2 — Xem chi tiết thiết kế đã lưu và kiểm tra thao tác Lưu trữ (Archive)
```browser
click getByRole('button', { name: /Xem chi tiết →/i })
wait getByTestId("design-evaluation-page")
click getByTestId("archive-design-btn")
wait --text "Đã lưu trữ"
snapshot -i
```

### Step 3 — Kiểm tra trường hợp phiên khách vãng lai hết hạn 20 phút
```browser
goto http://localhost:5173/workshops/PKG-COUPLE-01/design/DSG-EXPIRED-SESSION
wait --text "Phiên làm việc của bạn đã hết hạn sau 20 phút không hoạt động."
snapshot -i
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Lịch sử thiết kế nhẫn của bạn"
# Expected: one or more matches
find "Đã lưu trữ"
# Expected: one or more matches sau khi bấm Lưu trữ thiết kế
requests --filter "/api/v1/workshop-designs"
# Expected: POST archive thành công với status 200
```

## 5. Bằng chứng kiểm thử (Evidence)

- Screenshot thành công: `screenshots/E2E-012-005--success.jpg`
- Lệnh chụp: `screenshot --full --path "ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-005--success.jpg"`

## 6. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PARTIALLY_VERIFIED (Step 3: PASS; Step 1-2: BLOCKED by Firebase Admin credentials)
- Executed at: 2026-11-12 20:09:38
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Preconditions verification:
  - backend_health: PASS (http://localhost:8080/v3/api-docs)
  - frontend_health: PASS (http://localhost:5173)
- Execution steps summary:
  - Step 1 & 2: Route Guard chặn an toàn truy cập vào /my/designs khi chưa đăng nhập và chuyển hướng về /login. Bước đăng nhập Member bị BLOCKED do môi trường local chưa cấu hình private key Firebase Admin trong .env.
  - Step 3 (Phiên khách vãng lai hết hạn 20 phút): Điều hướng đến http://localhost:5173/workshops/PKG-COUPLE-01/design/DSG-EXPIRED-SESSION. Backend phản hồi chuẩn xác HTTP 401 UNAUTHORIZED với mã DESIGN_SESSION_EXPIRED. Giao diện hiển thị alert tiếng Việt: "Phiên làm việc của bạn đã hết hạn sau 20 phút không hoạt động. Vui lòng bắt đầu lại để tiếp tục." cùng nút "Bắt đầu phiên mới". Trạng thái: PASS.
- Evidence screenshot: [screenshots/E2E-012-005--session-expired.jpg](screenshots/E2E-012-005--session-expired.jpg)
- Bug reference: BUG-E2E-0004 (RESOLVED & CLOSED)
<!-- EXECUTION:END -->

