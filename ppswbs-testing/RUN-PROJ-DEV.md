# Runbook Kiểm thử Toàn diện (QA Runbook)

Tài liệu hướng dẫn thiết lập môi trường, kiểm tra tiền điều kiện và vận hành kiểm thử hệ thống. File này là tham chiếu vận hành local-only.

---

## 1. Yêu cầu tiên quyết (Prerequisites)

Để thực hiện kiểm thử tự động và E2E, môi trường máy phát triển phải đáp ứng:

1. **Công cụ MCP Browser (`browsermcp`):**
   - Đã cài extension Browser MCP, được cấp quyền **Allow** ở lần ghép nối đầu tiên, và được cấu hình trong MCP settings của AI client.
   - Cung cấp công cụ `browser` điều khiển Chrome.
   - Nếu chưa có: Xem mục §4 để cài đặt/kích hoạt trước khi test E2E.
2. **Backend Spring Boot:**
   - Đang chạy tại `http://localhost:8080`.
   - Kết nối MySQL và hoàn thành Flyway migration.
   - Lệnh khởi động (tại `ppswbs_backend`):
     ```powershell
     $env:JDBC_URL = 'jdbc:mysql://localhost:3306/ppswbs_dev?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
     $env:JDBC_USER = 'root'
     $env:JDBC_PASSWORD = '<password>'
     .\mvnw.cmd spring-boot:run
     ```
3. **Frontend Vite React:**
   - Đang chạy tại `http://localhost:5173`.
   - Đã nạp biến môi trường `.env` (`VITE_FIREBASE_*`, `VITE_API_BASE_URL=http://localhost:8080`).
   - Lệnh khởi động (tại `ppswbs_frontend`):
     ```powershell
     npm run dev
     ```

---

## 2. Lệnh Pre-flight Health Check (Kiểm tra nhanh)

Trước khi kích hoạt Agent kiểm thử E2E, chạy lệnh PowerShell sau để kiểm tra xem cả hai dịch vụ đã sẵn sàng hay chưa:

```powershell
# Kiểm tra Frontend :5173
try {
    $fe = Invoke-WebRequest -Uri "http://localhost:5173" -UseBasicParsing -TimeoutSec 3
    Write-Host "✅ Frontend Vite is UP (Status: $($fe.StatusCode))" -ForegroundColor Green
} catch {
    Write-Host "❌ Frontend Vite is DOWN on port 5173! Vui lòng chạy 'npm run dev' tại ppswbs_frontend" -ForegroundColor Red
}

# Kiểm tra Backend :8080
try {
    $be = Invoke-WebRequest -Uri "http://localhost:8080/v3/api-docs" -UseBasicParsing -TimeoutSec 3
    Write-Host "✅ Backend Spring Boot is UP" -ForegroundColor Green
} catch {
    Write-Host "❌ Backend Spring Boot is DOWN on port 8080! Vui lòng khởi động backend" -ForegroundColor Red
}
```

---

## 3. Quy trình thực thi kiểm thử

### 3.1. Chạy Unit Test kiểm chứng (Backend Surefire)
```powershell
# Chạy một test case cụ thể qua script của repo:
ppswbs-spec/.specify/scripts/powershell/run-unit-test-case.ps1 -CasePath "ppswbs-spec/features/<feature>/test-results/UTC-xxx.md" -ExecutedBy "<TesterName>"
```

### 3.2. Chạy E2E Test với `browsermcp`
1. Đảm bảo mọi endpoint trong `health_urls` của scenario đều sẵn sàng.
2. Kiểm tra `browsermcp` sẵn sàng.
3. Tester Agent đọc kịch bản tại `ppswbs-spec/features/<feature>/test-results/E2E-xxx.md`.
4. Gọi kỹ năng `$e2e-browser-execute` để Agent điều khiển trình duyệt và chụp screenshot vào `ppswbs-spec/features/<feature>/test-results/screenshots/`.

---

## 4. Hướng dẫn xử lý khi thiếu `browsermcp`

Nếu đồng đội trong nhóm clone dự án về nhưng session chưa có `browsermcp`:
1. **Kiểm tra trạng thái MCP:** Kiểm tra AI client có nạp server `browsermcp` hay chưa; cấu hình chính thức dùng `npx @browsermcp/mcp@latest`.
2. **Kiểm tra extension:** Mở popup Browser MCP, bảo đảm extension đang bật; nếu có mã sáu chữ số, đối chiếu với AI client rồi chọn **Allow**.
3. **Cập nhật khi cần:** Nếu popup yêu cầu cập nhật, cập nhật cả extension và MCP server, sau đó khởi động lại AI client.
4. **Báo cáo Blocker:** Nếu chưa thể cấu hình, cập nhật scenario là `BLOCKED`; không tự ghi `BUG-LOG.md` trừ khi blocker thuộc môi trường dự án đã được xác nhận.

