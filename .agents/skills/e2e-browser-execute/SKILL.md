---
name: e2e-browser-execute
description: Execute a validated PPSWBS E2E test scenario using browsermcp, capture screenshots, and record results in case and bug log.
metadata:
  short-description: Execute E2E test scenario via browsermcp with screenshot evidence
---

# E2E Browser Execute

Sử dụng kỹ năng này để thực thi một kịch bản kiểm thử E2E đã được lập tại `ppswbs-spec/features/<feature>/test-results/E2E-*.md` bằng công cụ `browsermcp`.

---

## 1. Điều kiện tiên quyết & MCP Availability Guard (BẮT BUỘC KIỂM TRA ĐẦU TIÊN)

<HARD-GATE-MCP-CHECK>
Trước khi đọc kịch bản hay thực hiện bất kỳ lệnh kiểm thử nào, Agent **BẮT BUỘC** kiểm tra sự hiện diện của công cụ `browsermcp` trong phiên làm việc hiện tại:

1. **Kiểm tra công cụ:** Xem danh sách công cụ MCP đã được nạp xem có công cụ `browser` thuộc server `browsermcp` (hoặc lazy tool `call_mcp_tool` với `ServerName: "browsermcp"`) hay không.
2. **Nếu CHƯA CÓ hoặc MCP chưa kết nối:**
   - **DỪNG NGAY LẬP TỨC!** Không cố gắng giả lập kịch bản hoặc đoán mò kết quả.
   - Xuất thông báo cảnh báo rõ ràng cho người dùng:
     > ⚠️ **[MCP BLOCKER] Chưa phát hiện công cụ `browsermcp` trong phiên làm việc!**  
     > Kịch bản kiểm thử E2E yêu cầu công cụ `browsermcp` để điều khiển Chrome. Vui lòng tham khảo [Hướng dẫn cài đặt browsermcp](references/mcp-prerequisite-guide.md) để cấu hình MCP server trước khi tiếp tục.
   - Cập nhật execution history của kịch bản thành `BLOCKED`, nêu lý do và không tạo bug sản phẩm. Chỉ ghi `BUG-LOG.md` khi người dùng yêu cầu hoặc khi có bằng chứng blocker thuộc môi trường dự án.
</HARD-GATE-MCP-CHECK>

---

## 2. Pre-flight Health Check (Kiểm tra dịch vụ)

Sau khi xác nhận `browsermcp` đã có:
1. Đọc `health_urls` trong YAML front matter. Kiểm tra từng endpoint; không mặc định một cổng cố định khi kịch bản không phụ thuộc dịch vụ đó.
2. Nếu `health_urls` không có, dùng `target_url` cho frontend và xác nhận với người dùng endpoint backend/health thật sự cần thiết trước khi chạy.
3. Nếu một endpoint cần thiết chưa sẵn sàng, dừng lại, cập nhật execution history là `BLOCKED`, rồi hướng dẫn người dùng theo [`RUN-PROJ-DEV.md`](../../../ppswbs-testing/RUN-PROJ-DEV.md).

---

## 3. Quy trình thực thi kiểm thử với `browsermcp`

1. **Nạp và kiểm tra kịch bản:** Xác nhận YAML có `id`, `target_url`, `actor_role`, `health_urls`, `test_data`, `cleanup`, `browser_tool: browsermcp`; kiểm tra `Browser Action Steps` có các tiêu đề `Step N` và fenced block chỉ chứa lệnh Browser MCP, còn `Expected Assertions` chứa lệnh cùng expected value. Thiếu mục nào thì cập nhật `BLOCKED`, không suy diễn hoặc tự bịa dữ liệu.
2. **Chuẩn bị dữ liệu và evidence:** Xác nhận fixture, `isolation_key` và cách cleanup; tạo thư mục `screenshots/` nếu chưa có.
3. **Thực thi các lệnh `browser`:**
   - Gửi các lệnh trong mục `Browser Action Steps` của kịch bản vào công cụ `browser` theo từng lô (batch) hợp lý.
   - Ví dụ:
     ```
     goto http://localhost:5173/workshops
     wait --text "Danh sách Workshop"
     click getByRole('button', { name: 'Xem chi tiết' })
     snapshot -i
     ```
4. **Kiểm tra Assertions:** Chạy đúng các lệnh trong `Expected Assertions`; lưu output thực tế cho từng assertion. Dùng `console --errors` và `requests --filter` khi kịch bản yêu cầu; không tự suy ra PASS từ screenshot.
5. **Chụp ảnh màn hình lưu bằng chứng:**
   - Browser MCP lưu JPEG qua `--path`. Dùng đường dẫn repository-relative, có quote:
     - Khi thành công: `screenshot --full --path "ppswbs-spec/features/<feature>/test-results/screenshots/E2E-<ID>--success.jpg"`
     - Khi thất bại: `screenshot --full --path "ppswbs-spec/features/<feature>/test-results/screenshots/E2E-<ID>--fail-step-<N>.jpg"`
6. **Cleanup:** Thực hiện `cleanup` của kịch bản. Nếu cleanup thất bại, không che giấu: ghi `FAIL` hoặc `BLOCKED` theo ảnh hưởng và nêu dữ liệu còn lại.

---

## 4. Ghi nhận kết quả (Dual-Tracking Protocol)

### 4.1. Cập nhật block EXECUTION trong file kịch bản
Mở file kịch bản `E2E-*.md` và cập nhật block execution:
```markdown
<!-- EXECUTION:START -->
- Latest status: PASS | FAIL | BLOCKED
- Executed at: YYYY-MM-DD HH:mm:ss
- Executed by: <Tên QA / Tester>
- Browser tool: browsermcp
- Failure step: None (hoặc Step N: Click nút X)
- Actual result: Hiển thị đúng dữ liệu mong đợi (hoặc Báo lỗi 500)
- Evidence screenshot: screenshots/E2E-xxx--success.jpg
- Bug reference: NONE (hoặc BUG-E2E-0001)
<!-- EXECUTION:END -->
```

### 4.2. Nếu kiểm thử thất bại (FAIL):
1. **Tuyệt đối không sửa code sản phẩm.**
2. Mở file [`BUG-LOG.md`](../../../ppswbs-testing/BUG-LOG.md).
3. Thêm một bản ghi lỗi mới lên đầu file:
   - Ghi rõ ID lỗi (ví dụ `BUG-E2E-0001`).
   - Phân loại component (`Frontend` / `Backend` / `Spec`).
   - Mô tả lỗi, các bước tái hiện qua lệnh browser, và đường dẫn screenshot bằng chứng.
4. Báo cáo lại cho người dùng và các bên liên quan để sửa lỗi.

