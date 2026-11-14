# Sổ Theo Dõi Lỗi Hệ Thống (Defect & Bug Tracking Log)

File nhật ký theo dõi lỗi tập trung do `ppswbs-testing/AGENT.md` quản lý.  
Mọi lỗi phát hiện trong quá trình kiểm thử Unit, Integration hoặc E2E bằng `browsermcp` đều phải được ghi nhận tại đây để bàn giao cho các bên liên quan xử lý.

---

## Danh sách lỗi đang theo dõi (Active Defects)

<!-- Ghi nhận lỗi mới nhất lên trên đầu theo mẫu dưới đây -->

## BUG-E2E-0005 — CLOSED

- Ngày mở: 2026-11-14
- Ngày khắc phục: 2026-11-14
- Ngày đóng / nghiệm thu: 2026-11-14
- Phát hiện bởi: Senior QA & System Verification Engineer
- Khắc phục bởi: Senior React Frontend Implementation Engineer
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer (Phan Sơn)
- Feature: 001-account-management
- Kịch bản liên quan: [E2E-001-007](../ppswbs-spec/features/001-account-management/test-results/E2E-001-007--admin-role-catalogue-rbac-guard.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Frontend
- Mô tả lỗi ban đầu: Lệch trường dữ liệu hợp đồng (Contract Misalignment) giữa Frontend và Backend tại API `/api/v1/roles`. Backend trả về `{ code, displayName }` theo đúng đặc tả OpenAPI (`RoleEntryResponse`), nhưng Frontend `RoleCataloguePage.tsx` và `AccountManagementApiClient.ts` lại truy xuất `{ r.role, r.description }`. Kết quả là bảng danh mục 4 vai trò trên `/admin/roles` bị rỗng toàn bộ nội dung tên vai trò và mô tả quyền hạn.
- Các bước tái hiện:
  1. Đăng nhập tài khoản có quyền Admin (ví dụ: `qa-e2e-feat007@test.com`).
  2. Truy cập đường dẫn: `http://localhost:5173/admin/roles`.
  3. Quan sát bảng "Danh mục vai trò hệ thống (Fixed Roles)".
- Kết quả mong đợi: Bảng hiển thị 4 vai trò (MEMBER, STAFF, MANAGER, ADMIN) cùng mô tả phạm vi và quyền hạn tương ứng.
- Thực tế quan sát trước khi sửa: Bảng hiển thị 4 dòng nhưng cả cột "Vai trò" (badge rỗng) và cột "Mô tả phạm vi và quyền hạn" đều trắng tinh.
- Bằng chứng lỗi ban đầu:
  - API response từ `GET /api/v1/roles`: `{"roles":[{"code":"MEMBER","displayName":"Khách hàng thành viên / Member"},{"code":"STAFF","displayName":"Nhân viên cơ sở / Staff"},{"code":"MANAGER","displayName":"Quản lý cơ sở / Manager"},{"code":"ADMIN","displayName":"Quản trị viên / Admin"}]}`
  - DOM Table quan sát được: `<tr><td><span class="account-badge account-badge-readonly"></span></td><td></td></tr>`
- Nguyên nhân gốc rễ (Root Cause):
  - Đặc tả OpenAPI `account-management.openapi.yaml` định nghĩa `RoleEntryResponse` gồm 2 thuộc tính bắt buộc: `code` (chuỗi enum vai trò) và `displayName` (tên hiển thị vai trò).
  - Backend Spring Boot (`RoleCatalogueController.java`) trả về DTO chuẩn `{ roles: [{ code, displayName }] }`.
  - Frontend `RoleCataloguePage.tsx` và interface `RoleCatalogueEntry` trong `AccountManagementApiClient.ts` trước đó định nghĩa và bóc tách các trường phi tiêu chuẩn `{ role, description }`, dẫn đến `r.role` và `r.description` nhận giá trị `undefined` khi nhận payload thật từ Backend.
- Biện pháp khắc phục đã triển khai (Frontend Dev):
  1. `ppswbs_frontend/src/features/accountmanagement/api/AccountManagementApiClient.ts`:
     - Cập nhật interface `RoleCatalogueEntry` bổ sung đầy đủ trường OpenAPI chuẩn `code?: 'MEMBER' | 'STAFF' | 'MANAGER' | 'ADMIN'` và `displayName?: string`, đồng thời duy trì fallback tương thích ngược.
  2. `ppswbs_frontend/src/features/accountmanagement/components/RoleCataloguePage.tsx`:
     - Cập nhật rendering JSX bóc tách an toàn theo chuẩn hợp đồng: `const roleCode = r.code || r.role;` và `const roleDesc = r.displayName || r.description;`.
     - Hiển thị badge vai trò với `roleCode` và cột mô tả với `roleDesc`.
  3. `ppswbs_frontend/src/features/accountmanagement/tests/accountManagementComponents.test.tsx`:
     - Bổ sung unit/component test ca: `renders OpenAPI standard response with code and displayName properties (BUG-E2E-0005)`.
     - Toàn bộ 12 test trong suite component test và 92/92 test trong toàn feature `accountmanagement` đều PASS.
- Kết quả nghiệm thu thực tế của QA (QA Verification):
  - **Trình duyệt**: Live Chrome qua `browsermcp` tại `http://localhost:5173/admin/roles`.
  - **DOM Table trích xuất**:
    ```html
    <table class="account-table">
      <thead><tr><th style="width: 180px;">Vai trò</th><th>Mô tả phạm vi và quyền hạn</th></tr></thead>
      <tbody>
        <tr><td><span class="account-badge account-badge-readonly" style="font-size: 0.85rem;">MEMBER</span></td><td style="line-height: 1.6;">Khách hàng thành viên / Member</td></tr>
        <tr><td><span class="account-badge account-badge-readonly" style="font-size: 0.85rem;">STAFF</span></td><td style="line-height: 1.6;">Nhân viên cơ sở / Staff</td></tr>
        <tr><td><span class="account-badge account-badge-readonly" style="font-size: 0.85rem;">MANAGER</span></td><td style="line-height: 1.6;">Quản lý cơ sở / Manager</td></tr>
        <tr><td><span class="account-badge account-badge-readonly" style="font-size: 0.85rem;">ADMIN</span></td><td style="line-height: 1.6;">Quản trị viên / Admin</td></tr>
      </tbody>
    </table>
    ```
  - **Đánh giá**: Tất cả 4 vai trò cố định và mô tả hiển thị chính xác theo hợp đồng OpenAPI và Revision R-MGA.
- Trạng thái xử lý: **CLOSED**

---

## BUG-E2E-0004 — CLOSED

- Ngày mở: 2026-11-12
- Ngày đóng / nghiệm thu: 2026-11-12
- Phát hiện bởi: Senior QA & System Verification Engineer
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 012-workshop-ring-design-configuration
- Kịch bản liên quan: [E2E-012-003](../ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/E2E-012-003--save-evaluate-and-version-history.md), [E2E-012-005](../ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/E2E-012-005--guest-expiry-and-member-ownership.md)
- Mức độ nghiêm trọng: CRITICAL
- Phân loại / Gán cho: Full-Stack (Frontend & Backend)
- Mô tả lỗi ban đầu: Khi người dùng vãng lai (Guest) bấm nút "Lưu & Thẩm định" trên trang cấu hình nhẫn (`/workshops/:packageRef/design/configure`), Frontend gửi request lưu phiên bản mà không khởi tạo/đính kèm header `X-Guest-Design-Session`. Backend ném ngoại lệ `RingDesignException` nhưng không được xử lý chuẩn mà rơi vào `AccountExceptionHandler` trả về HTTP 500 Unhandled Error. Giao diện bị kẹt với thông báo "An unexpected error occurred".
- Các bước tái hiện ban đầu:
  1. Điều hướng đến `http://localhost:5173/workshops/PKG-COUPLE-01/design/configure?mode=BLANK`
  2. Chọn chất liệu Vàng 14K, Dáng xoắn vô cực, Size 12, Đính đá CZ Lam 2mm, Khắc chữ "Forever Together 2026".
  3. Bấm nút "Lưu & Thẩm định".
- Nguyên nhân gốc rễ (Root Cause):
  1. Backend `WorkshopRingDesignExceptionHandler` thiếu annotation `@Order(Ordered.HIGHEST_PRECEDENCE)`. Khi có ngoại lệ `RingDesignException`, Spring MVC ưu tiên gọi `AccountExceptionHandler` bao quát `basePackages = "edu.fpt.sep490_g22.ppswbs_backend"` dẫn đến fallback vào `Exception.class` và trả về HTTP 500.
  2. Khi khách vãng lai gọi `POST /api/v1/workshop-designs` tạo bản nháp mới, Backend đã sinh `GuestDesignSession` trong DB nhưng không trả về mã `sessionReference` cho Frontend qua DTO hoặc HTTP Header `X-Guest-Design-Session`. Do đó, request lưu phiên bản tiếp theo thiếu header phiên làm việc.
- Biện pháp khắc phục đã triển khai:
  1. Backend: Bổ sung `@Order(Ordered.HIGHEST_PRECEDENCE)` trên `WorkshopRingDesignExceptionHandler`. Bổ sung trường `guestSession` vào `DesignResponseDto` và trả về kèm header `X-Guest-Design-Session` tại `WorkshopRingDesignController.createWorkshopDesign`.
  2. Frontend: Cập nhật `useWorkshopDesignCommand.js` để tự động lưu `res.guestSession` vào `sessionStorage` qua `setStoredGuestSession`. Cập nhật `DesignEvaluationPage.jsx` để dùng `getErrorMessage(err)` ánh xạ chuẩn xác thông báo hết hạn phiên 20 phút bằng tiếng Việt.
- Kết quả kiểm chứng lại (QA Re-test 2026-11-12 qua Browser MCP):
  - Kịch bản `E2E-012-003` tái kiểm tra trên Chrome thực tế: Bấm "Lưu & Thẩm định" thành công chuyển sang `/design/{designId}`, hiển thị đầy đủ bảng chỉ số kỹ thuật và chi phí tham khảo ước tính (HTTP 201 + 200). Chuyển tiếp sang `/workshops/booking?...` đạt yêu cầu. Trạng thái: **PASS**. Bằng chứng JPEG: ![Thành công](../ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-003--success.jpg).
  - Kịch bản `E2E-012-005` (nhánh phiên hết hạn): Truy cập thiết kế với phiên hết hạn trả về HTTP 401 `DESIGN_SESSION_EXPIRED` và hiển thị alert tiếng Việt: *"Phiên làm việc của bạn đã hết hạn sau 20 phút không hoạt động. Vui lòng bắt đầu lại để tiếp tục."*. Trạng thái: **PASS**. Bằng chứng JPEG: ![Phiên hết hạn](../ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-005--session-expired.jpg).

<!--
## BUG-E2E-0000 — OPEN

- Ngày: YYYY-MM-DD
- Phát hiện bởi: QA Agent (Tester)
- Feature: 003-workshop-management
- Kịch bản liên quan: [E2E-003-001](../ppswbs-spec/features/003-workshop-management/test-results/E2E-003-001--booking-flow.md)
- Mức độ nghiêm trọng: CRITICAL
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Nút "Xác nhận đặt lịch" bị vô hiệu hóa dù đã chọn đủ thời gian và số lượng người.
- Các bước tái hiện:
  1. goto http://localhost:5173/workshops/1
  2. click getByRole('button', { name: 'Chọn suất 09:00' })
  3. fill getByLabel('Số lượng khách') "2"
  4. Quan sát nút "Xác nhận đặt lịch"
- Kết quả mong đợi: Nút "Xác nhận đặt lịch" chuyển sang trạng thái enabled và cho phép click.
- Thực tế quan sát: Nút bị disabled vĩnh viễn, console log báo lỗi "Uncaught TypeError: selectedSlot.capacity is undefined".
- Bằng chứng:
  - Screenshot: ![Lỗi](../ppswbs-spec/features/003-workshop-management/test-results/screenshots/E2E-003-001--slot-disabled.jpg)
-->

## BUG-QA-0003 — RESOLVED

- Ngày phát hiện/khắc phục: 2026-11-06
- Phát hiện bởi: Senior QA & System Verification Engineer
- Feature: 001-account-management
- Phân loại / Gán cho: Test artifact / QA
- Mô tả: Các E2E artifact lịch sử ghi credential/định danh fixture trực tiếp và giữ trạng thái PASS dù shared session/route contract đã đổi.
- Tác động: Có nguy cơ lộ secret test và tạo acceptance sai cho revision R-SL.
- Khắc phục: Redact fixture/credential, đánh dấu evidence cũ là historical, đặt case revision thành `NOT_RUN`, và thêm E2E-001-013 đến E2E-001-020.
- Bằng chứng: Static scan không còn fixture credential hoặc current `PASS` trong `ppswbs-spec/features/001-account-management/test-results/`.

## BUG-E2E-0002 — CLOSED

- Ngày mở: 2026-11-05
- Ngày đóng: 2026-11-05
- Phát hiện bởi: QA Agent & User Verification
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 001-account-management
- Kịch bản liên quan: [E2E-001-008](../ppswbs-spec/features/001-account-management/test-results/E2E-001-008--google-signin-account-collision.md)
- Mức độ nghiêm trọng: CRITICAL
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Luồng Google Sign-In redirect không kích hoạt thông báo xử lý lỗi xung đột tài khoản khi quay trở lại trang đăng nhập `/login` trên môi trường thực tế. Khi người dùng chọn tài khoản Google `minikie456@gmail.com` (trùng email đã có mật khẩu), trình duyệt quay lại trang `/login` nhưng hoàn toàn không hiển thị thông báo cảnh báo nào trong vòng 5 giây (xảy ra hiện tượng Silent Failure).
- Các bước tái hiện:
  1. Mở trang `http://localhost:5173/login`.
  2. Bấm nút "Đăng nhập với Google".
  3. Tại màn hình Google OAuth, chọn tài khoản `minikie456@gmail.com`.
  4. Trình duyệt chuyển hướng quay lại `http://localhost:5173/login`.
  5. Quan sát phản hồi giao diện trong vòng 5 giây.
- Kết quả mong đợi: Trong vòng tối đa 5 giây, giao diện phải hiển thị thông báo alert:
  "Email này đã được đăng ký bằng Mật khẩu. Vui lòng đăng nhập bằng Email và Mật khẩu gốc."
- Thực tế quan sát trước khi sửa: Giao diện im lặng (Silent Failure), form đăng nhập hiển thị bình thường không có bất kỳ dòng thông báo lỗi nào, khiến người dùng không biết đăng nhập thất bại vì lý do gì.
- Bằng chứng lỗi ban đầu:
  - Screenshot: ![Lỗi im lặng không thông báo](../ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-008--google-collision-silent-fail.jpg)
- Biện pháp khắc phục (Frontend Dev):
  1. Nguyên nhân gốc rễ: Chrome 115+ kích hoạt tính năng Phân vùng bộ nhớ (Storage Partitioning / third-party storage blocking) đối với iframe ẩn của Firebase Auth trên localhost, dẫn đến `getRedirectResult()` bị nuốt token và luôn trả về `null` trên localhost.
  2. Triển khai phương thức `signInWithGooglePopup()` trong `FirebaseAuthClient.ts` và sử dụng trong `AuthPage.tsx` khi chạy trên trình duyệt web:
     - Cho phép nhận phản hồi xác thực trực tiếp qua `postMessage` trong popup mà không bị chặn bởi phân vùng bộ nhớ iframe cross-origin.
  3. Cơ chế phát hiện xung đột & bảo vệ tài khoản (FR-001, FR-002, FR-022):
     - Kiểm tra nếu user trả về đã tồn tại provider `password`: Hệ thống nhận diện xung đột phương thức đăng nhập, lập tức gọi `firebaseAuthClient.signOut()`, hủy hoàn toàn phiên và kích hoạt thông báo tiếng Việt:
       "Email này đã được đăng ký bằng Mật khẩu. Vui lòng đăng nhập bằng Email và Mật khẩu gốc."
     - Tuyệt đối không gọi hàm bootstrap backend `bootstrapOwnAccount()`.
- Kết quả kiểm chứng lại (QA Re-test 2026-11-05):
  - Kịch bản `E2E-001-008` được thực thi lại trên trình duyệt Chrome thực tế bằng `browsermcp`.
  - Kết quả: **`PASS 100%`**. Thông báo cảnh báo xuất hiện ngay lập tức trong vòng < 2 giây sau khi xác nhận tài khoản Google (đáp ứng xuất sắc tiêu chí <= 5 giây của người dùng).
  - Trạng thái phiên: `currentUser = null` (Guest), không phát sinh bất kỳ request trái phép nào.
  - Bằng chứng khắc phục:
    + Screenshot: ![Màn hình cảnh báo xung đột Google hợp lệ](../ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-008--google-collision.jpg)
- Trạng thái xử lý: **CLOSED**

## BUG-E2E-0001 — CLOSED

- Ngày mở: 2026-11-05
- Ngày đóng: 2026-11-05
- Phát hiện bởi: QA Agent (Senior QA & System Verification Engineer)
- Xác minh đóng lỗi bởi: Senior QA & System Verification Engineer
- Feature: 001-account-management
- Kịch bản liên quan: [E2E-001-006](../ppswbs-spec/features/001-account-management/test-results/E2E-001-006--admin-accounts-rbac-guard.md), [E2E-001-007](../ppswbs-spec/features/001-account-management/test-results/E2E-001-007--admin-role-catalogue-rbac-guard.md)
- Mức độ nghiêm trọng: CRITICAL
- Phân loại / Gán cho: Frontend
- Mô tả lỗi: Thiếu Route Guard phân quyền vai trò (Role-based Route Guard) ở tầng Frontend. Tài khoản người dùng vai trò `MEMBER` khi truy cập trực tiếp các đường dẫn quản trị nội bộ (`/admin/accounts` và `/admin/roles`) vẫn render toàn bộ giao diện quản trị Admin (Header, Admin Navigation Tabs, Nút tạo tài khoản nội bộ, Form bộ lọc và cấu trúc bảng), thay vì phải bị chặn hoàn toàn hoặc chuyển hướng về trang chủ (`/`) hoặc màn hình 403 Unauthorized.
- Các bước tái hiện:
  1. Đăng nhập hệ thống với tài khoản Member: `minikie456@gmail.com` / `123123123`.
  2. Truy cập trực tiếp đường dẫn: `http://localhost:5173/admin/accounts` hoặc `http://localhost:5173/admin/roles`.
  3. Quan sát giao diện hiển thị trên trình duyệt.
- Kết quả mong đợi: Hệ thống phải kiểm tra vai trò người dùng trước khi render trang. Nếu vai trò không phải `ADMIN`, Frontend phải chặn không hiển thị giao diện quản trị, thực hiện điều hướng an toàn về Trang chủ (`/`) hoặc hiển thị màn hình từ chối quyền truy cập (403 Unauthorized / Access Denied).
- Bằng chứng lỗi ban đầu:
  - Screenshot 1: ![Lộ giao diện Admin Accounts](../ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-006--admin-accounts-guard.jpg)
  - Screenshot 2: ![Lộ giao diện Admin Roles](../ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-007--admin-roles-guard.jpg)
- Biện pháp khắc phục (Frontend Dev):
  1. Khởi tạo component `AdminRouteGuard` (`src/features/accountmanagement/components/AdminRouteGuard.tsx`) tích hợp `useCurrentAccount()`:
     - Nếu chưa đăng nhập: Điều hướng về `/login`.
     - Nếu vai trò không phải `ADMIN`: Chặn hoàn toàn không render cây DOM con của Admin, hiển thị màn hình chuẩn 403 Forbidden kèm nút `← Quay lại` về trang chủ và thông báo `account-alert-error`.
     - Nếu vai trò là `ADMIN`: Render bình thường qua `<Outlet />`.
  2. Bọc toàn bộ các route quản trị Admin (`/admin/accounts`, `/admin/accounts/new`, `/admin/accounts/:accountId`, `/admin/roles`) dưới `AdminRouteGuard` trong `routes.tsx`.
- Kết quả kiểm chứng lại (QA Re-test 2026-11-05):
  - Kịch bản `E2E-001-006` và `E2E-001-007` được chạy lại trên Chrome thực tế bằng `browsermcp`.
  - Kết quả: **`PASS 100%`**. Toàn bộ giao diện Admin bị chặn hoàn toàn không render, hiển thị màn hình 403 Forbidden với nút "Về trang chủ".
  - Bằng chứng khắc phục:
    + Screenshot 1: ![Màn hình 403 chặn Admin Accounts](../ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-006--admin-accounts-guard-resolved.jpg)
    + Screenshot 2: ![Màn hình 403 chặn Admin Roles](../ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-007--admin-roles-guard-resolved.jpg)
- Trạng thái xử lý: **CLOSED**
