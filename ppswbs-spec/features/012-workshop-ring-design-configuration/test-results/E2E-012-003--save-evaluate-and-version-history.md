---
scenario_id: E2E-012-003
feature: FE-012
actor: MEMBER
preconditions:
  - backend_health: FAIL
  - frontend_health: FAIL
traceability:
  - FE-012
  - AC-012-EVALUATION
isolation_key: e2e-012-member-003
cleanup:
  - delete_design_record
id: E2E-012-003
test_level: e2e
test_case_type: N
source_ref: spec.md#FR-012-003
target_url: http://localhost:5173/workshops/PKG-COUPLE-01/design/configure?mode=BLANK
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: member-evaluate-fixture
  account_ref: MEMBER_001
  isolation_key: e2e-012-member-003
---

# E2E-012-003: Lưu nháp, thẩm định kỹ thuật, tính toán độ khó và chuyển tiếp đặt lịch

**Ngày tạo**: 2026-11-12  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tính năng**: 012-workshop-ring-design-configuration  

## 1. Tiền điều kiện (Preconditions)

- [ ] Môi trường Backend Spring Boot sẵn sàng tại `http://localhost:8080`.
- [ ] Môi trường Frontend Vite sẵn sàng tại `http://localhost:5173`.
- [ ] Đã đăng nhập tài khoản Member: `member.test@mland.vn`.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Kiểm soát phiên bản bất biến (Row-per-Version & If-Match-Version):** Mỗi lần lưu thay đổi sẽ tạo một record phiên bản tăng dần (`version 1 -> version 2`). Gửi kèm `If-Match-Version` để tránh ghi đè dữ liệu khi chỉnh sửa song song.
- **Tính toán độ khó và giá tham khảo:** Thẩm định trả về điểm độ khó (ví dụ `3.5 / 10`) và chi phí tham khảo ước tính. Nút hành động cho phép tiếp tục luồng đặt lịch workshop (`/workshops/booking?packageReference=...&designId=...`).

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Cấu hình nhẫn và bấm Lưu & Thẩm định
```browser
goto http://localhost:5173/workshops/PKG-COUPLE-01/design/configure?mode=BLANK
click getByTestId("material-card-MAT-SILVER-925")
click getByTestId("size-pill-10")
click getByTestId("save-and-evaluate-btn")
wait --text "Kết quả thẩm định thiết kế"
snapshot -i
```

### Step 2 — Kiểm tra thông số thẩm định kỹ thuật
```browser
wait --text "Chi phí tham khảo ước tính"
wait --text "Độ khó chế tác"
wait getByTestId("proceed-booking-btn")
snapshot -i
```

### Step 3 — Chuyển tiếp sang quy trình Đặt lịch Workshop
```browser
click getByTestId("proceed-booking-btn")
wait --text "Chọn chi nhánh và lịch workshop"
snapshot -i
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
get url
# Expected: chứa /workshops/booking và tham số designId
find "Đã thẩm định"
# Expected: one or more matches cho badge trạng thái VALIDATED
find "Chi phí tham khảo ước tính"
# Expected: one or more matches
requests --filter "/api/v1/workshop-designs"
# Expected: POST 201 tạo bản nháp và POST 200 thẩm định
```

## 5. Bằng chứng kiểm thử (Evidence)

- Screenshot thành công: `screenshots/E2E-012-003--success.jpg`
- Lệnh chụp: `screenshot --full --path "ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-003--success.jpg"`

## 6. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-12 20:09:10
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Preconditions verification:
  - backend_health: PASS (http://localhost:8080/v3/api-docs)
  - frontend_health: PASS (http://localhost:5173)
- Execution steps summary:
  1. Điều hướng http://localhost:5173/workshops/PKG-COUPLE-01/design/configure?mode=BLANK
  2. Chọn Size 10, chất liệu Bạc Ý 925, bấm "Lưu & Thẩm định"
  3. Hệ thống gửi POST tạo bản nháp 201 Created và POST lưu phiên bản 200 OK, tự động nhận và lưu token X-Guest-Design-Session.
  4. Trình duyệt chuyển hướng thành công đến http://localhost:5173/workshops/PKG-COUPLE-01/design/DSG-c5a6542c-2786-4138-a25b-308f86f79ea5
  5. Hiển thị đầy đủ bảng chỉ số kỹ thuật "Chi phí tham khảo ước tính" và "Độ khó chế tác".
  6. Bấm "Tiếp tục đặt lịch Workshop →" chuyển hướng chính xác đến /workshops/booking?packageReference=PKG-COUPLE-01&designId=...
- Evidence screenshot: [screenshots/E2E-012-003--success.jpg](screenshots/E2E-012-003--success.jpg)
- Bug reference: BUG-E2E-0004 (RESOLVED & CLOSED)
<!-- EXECUTION:END -->

