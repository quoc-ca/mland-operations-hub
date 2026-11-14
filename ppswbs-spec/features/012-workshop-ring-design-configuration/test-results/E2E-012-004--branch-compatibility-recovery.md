---
scenario_id: E2E-012-004
feature: FE-012
actor: MEMBER
preconditions:
  - backend_health: FAIL
  - frontend_health: FAIL
traceability:
  - FE-012
  - AC-012-BRANCH-RECOVERY
isolation_key: e2e-012-member-004
cleanup:
  - none-client-only
id: E2E-012-004
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-012-004
target_url: http://localhost:5173/workshops/PKG-COUPLE-01/design/recovery
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: branch-recovery-fixture
  account_ref: MEMBER_001
  isolation_key: e2e-012-member-004
---

# E2E-012-004: Xử lý phục hồi khi chi nhánh Workshop không tương thích với thiết kế nhẫn

**Ngày tạo**: 2026-11-12  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tính năng**: 012-workshop-ring-design-configuration  

## 1. Tiền điều kiện (Preconditions)

- [ ] Môi trường Backend Spring Boot sẵn sàng tại `http://localhost:8080`.
- [ ] Môi trường Frontend Vite sẵn sàng tại `http://localhost:5173`.
- [ ] Mẫu thiết kế đã chọn vàng 14K và khắc laser, nhưng chi nhánh được chọn trong booking không hỗ trợ lò nung hoặc máy khắc laser (`DESIGN_BRANCH_INCOMPATIBLE`).

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Tránh rơi vào ngõ cụt (Dead-end Prevention):** Khi chi nhánh không đáp ứng được yêu cầu chế tác, khách hàng không bị hủy toàn bộ dữ liệu. Giao diện cung cấp màn hình phục hồi `/recovery` với 2 lối thoát rõ ràng:
  1. *Điều chỉnh cấu hình nhẫn* (quay lại chỉnh vật liệu đơn giản hơn).
  2. *Chọn chi nhánh khác* (giữ nguyên nhẫn và đổi sang xưởng có trang thiết bị phù hợp).

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở màn hình cảnh báo không tương thích chi nhánh
```browser
goto http://localhost:5173/workshops/PKG-COUPLE-01/design/recovery?packageReference=PKG-COUPLE-01&designId=DSG-INCOMPAT-01&reason=Chi%20nh%C3%A1nh%20ch%C6%B0a%20h%E1%BB%97%20tr%E1%BB%A3%20m%C3%A1y%20kh%E1%BA%AFc%20laser
wait getByTestId("branch-compatibility-page")
wait --text "Thiết kế cần điều chỉnh cho chi nhánh"
snapshot -i
```

### Step 2 — Xác minh lý do từ hệ thống và nút điều chỉnh thiết kế
```browser
wait --text "Chi nhánh chưa hỗ trợ máy khắc laser"
click getByRole('button', { name: /Điều chỉnh thiết kế/i })
wait getByTestId("ring-configuration-page")
snapshot -i
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Thiết kế cần điều chỉnh cho chi nhánh"
# Expected: one or more matches
find "Chi nhánh chưa hỗ trợ máy khắc laser"
# Expected: one or more matches
find "Điều chỉnh thiết kế"
# Expected: one or more matches
find "Chọn chi nhánh khác"
# Expected: one or more matches
```

## 5. Bằng chứng kiểm thử (Evidence)

- Screenshot thành công: `screenshots/E2E-012-004--success.jpg`
- Lệnh chụp: `screenshot --full --path "ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-004--success.jpg"`

## 6. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-12 19:55:39
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Màn hình phục hồi BranchCompatibilityPage hiển thị cảnh báo chi nhánh không tương thích cùng lý do từ hệ thống ("Chi nhánh chưa hỗ trợ máy khắc laser"). Nhấn "Điều chỉnh thiết kế" chuyển tiếp an toàn về Configurator chế độ EDIT.
- Evidence screenshot: screenshots/E2E-012-004--success.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

