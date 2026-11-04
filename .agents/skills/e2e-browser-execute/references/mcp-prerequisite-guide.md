# Hướng Dẫn Cài Đặt & Kích Hoạt `browsermcp`

Tài liệu này dùng để chẩn đoán và hướng dẫn đồng đội cài đặt công cụ `browsermcp` khi môi trường chưa có sẵn.

---

## 1. Dấu hiệu nhận biết máy chưa có `browsermcp`

Khi Agent cố gắng thực thi kịch bản E2E, nếu gặp một trong các dấu hiệu sau:
- Session không hiển thị công cụ `browser` hoặc gọi `call_mcp_tool` với `ServerName: "browsermcp"` báo lỗi *"Tool/Server not found"*.
- Thư mục cấu hình MCP cục bộ (`<appDataDir>/mcp/browsermcp`) không tồn tại.
- Agent báo lỗi Gate T01 vi phạm.

---

## 2. Các bước thiết lập `browsermcp`

### Cách 1: Cài Browser MCP chính thức
1. Cài extension **Browser MCP** vào Chrome/Chromium và ghim extension lên toolbar.
2. Đảm bảo Node.js 20+ và mở phần cấu hình MCP của AI client.
3. Khai báo MCP server:
   ```json
   {
     "mcpServers": {
       "browsermcp": {
         "command": "npx",
          "args": ["-y", "@browsermcp/mcp@latest"]
       }
     }
   }
   ```
4. Khởi động lại AI client. Ở lần gọi đầu tiên, đối chiếu mã sáu chữ số trong popup Browser MCP rồi chọn **Allow**.

### Cách 2: Kiểm tra quyền truy cập Trình duyệt Chrome
- `browsermcp` yêu cầu Chrome/Chromium và extension đã được bật.
- Nếu popup báo *Waiting for the Browser MCP server*, kiểm tra lại cấu hình server rồi khởi động lại AI client. Nếu báo cần cập nhật, cập nhật cả extension và package MCP.

---

## 3. Hành động khi chưa thể cài đặt ngay
Nếu chưa thể cài đặt `browsermcp` ngay lập tức:
1. QA Agent cập nhật execution history là `BLOCKED` và báo thiếu môi trường; không tự tạo defect sản phẩm.
2. Toàn bộ các bài kiểm thử Unit Test (Java JUnit 5, React Vitest) và API contract vẫn có thể chạy bình thường độc lập.
3. Chỉ khâu nghiệm thu giao diện tự động E2E mới bị tạm hoãn cho đến khi MCP được kích hoạt.

