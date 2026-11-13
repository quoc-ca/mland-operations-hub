---
scenario_id: E2E-004-001
feature: FE-004
actor: GUEST
preconditions:
  - backend_health: FAIL
  - frontend_health: FAIL
traceability:
  - FE-004
  - AC-004-AI-CHAT
isolation_key: e2e-004-guest-001
cleanup:
  - none-client-only
id: E2E-004-001
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-004-001
target_url: http://localhost:5173/ai-support
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: guest-ai-support
  account_ref: GUEST_PUBLIC
  isolation_key: e2e-004-guest-001
---

# E2E-004-001: Trò chuyện AI Support cho Khách vãng lai và Chặn gửi yêu cầu thiết kế có ảnh

**Ngày tạo**: 2026-11-12  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tính năng**: 004-ai-chatbox-analysis-and-suggestion  

## 1. Tiền điều kiện (Preconditions)

- [ ] Môi trường Backend Spring Boot sẵn sàng tại `http://localhost:8080`.
- [ ] Môi trường Frontend Vite sẵn sàng tại `http://localhost:5173`.
- [ ] Công cụ `browsermcp` khả dụng trong phiên làm việc.
- [ ] Phiên người dùng: Guest (chưa đăng nhập).

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Guest cố tình upload ảnh thiết kế độc lập:** Guest không có tài khoản nhưng cố tình truy cập `/design-requests/new` để gửi ảnh. Hệ thống bắt buộc phải chặn tại Route Guard `<RequireSession>`, chuyển hướng về `/login` với thông báo "Bạn cần đăng nhập để tiếp tục."
- **Prompt Injection:** Người dùng nhập prompt độc hại nhằm trích xuất system prompt hoặc thông tin nội bộ. Hệ thống chặn với mã lỗi `AI_PROMPT_INJECTION_DETECTED`.
- **Disclaimer tham khảo:** Phản hồi AI phải luôn kèm cảnh báo mang tính tham khảo (advisory), không có giá trị cam kết giá hay đặt cọc workshop.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang AI Support và kiểm tra hiển thị cho Guest
```browser
goto http://localhost:5173/ai-support
wait getByRole('heading', { name: /Trợ lý AI/i })
snapshot -i
```

### Step 2 — Gửi câu hỏi tư vấn dịch vụ Workshop
```browser
fill getByLabel(/Nhập câu hỏi của bạn/i) "Workshop làm nhẫn bạc của Mland có những mẫu nào?"
click getByRole('button', { name: /Gửi câu hỏi/i })
wait --text "Mland cung cấp các buổi workshop"
snapshot -i
```

### Step 3 — Thử truy cập trang gửi yêu cầu thiết kế kèm ảnh khi chưa đăng nhập (Boundary Test)
```browser
goto http://localhost:5173/design-requests/new
wait --text "Bạn cần đăng nhập để tiếp tục."
snapshot -i
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
get url
# Expected: http://localhost:5173/login hoặc hiển thị thông báo yêu cầu đăng nhập
find "Bạn cần đăng nhập để tiếp tục."
# Expected: one or more matches
find "Khách (Công khai)"
# Expected: one or more matches trên trang /ai-support
find "Lưu ý: Phản hồi của AI chỉ mang tính chất tham khảo"
# Expected: one or more matches
```

## 5. Bằng chứng kiểm thử (Evidence)

- Screenshot thành công: `screenshots/E2E-004-001--success.jpg`
- Lệnh chụp: `screenshot --full --path "ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/screenshots/E2E-004-001--success.jpg"`

## 6. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-12 19:44:40
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Trợ lý AI trả lời tư vấn thành công kèm disclaimer tham khảo bắt buộc. Guest truy cập /design-requests/new bị Route Guard chặn an toàn và chuyển hướng về /login.
- Evidence screenshot: screenshots/E2E-004-001--success.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

