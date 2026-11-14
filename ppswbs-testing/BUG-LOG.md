# Sổ Theo Dõi Lỗi Hệ Thống (Defect & Bug Tracking Log)

File nhật ký theo dõi lỗi tập trung do `ppswbs-testing/AGENT.md` quản lý.  
Mọi lỗi phát hiện trong quá trình kiểm thử Unit, Integration hoặc E2E bằng `browsermcp` đều phải được ghi nhận tại đây để bàn giao cho các bên liên quan xử lý.

---

## Danh sách lỗi đang theo dõi (Active Defects)

<!-- Ghi nhận lỗi mới nhất lên trên đầu theo mẫu dưới đây -->

## BUG-E2E-0004 — OPEN

- Ngày phát hiện: 2026-11-14
- Phát hiện bởi: Senior QA & System Verification Engineer
- Feature: 007-notification-management
- Kịch bản liên quan: [E2E-007-011](../ppswbs-spec/features/007-notification-management/test-results/e2e/E2E-007-011--redeliver-with-refreshed-credential.md)
- Mức độ nghiêm trọng: MAJOR
- Phân loại / Gán cho: Backend / Database Schema
- Mô tả lỗi: 
  Endpoint `POST /api/v1/notification-requests/{id}/redeliver` trả về lỗi HTTP 500 (Internal Server Error) do xung đột ràng buộc toàn vẹn cơ sở dữ liệu `DataIntegrityViolationException`.
  Khi một yêu cầu gửi thông báo gốc (`original_request_id`) đã từng phát sinh một yêu cầu gửi lại (resend hoặc redeliver trước đó, ví dụ tạo request #1005 từ request gốc #1001), việc thực hiện thao tác redeliver (hoặc resend lần thứ 2) trên cùng request gốc #1001 sẽ cố gắng chèn một bản ghi mới với cùng bộ giá trị:
  `(business_event_reference, template_code, recipient_role, recipient_address_fingerprint, original_request_id = 1001)`.
  Ràng buộc `uq_nr_idempotency` trong Flyway migration `V111__notification_management.sql`:
  ```sql
  UNIQUE KEY uq_nr_idempotency (business_event_reference, template_code, recipient_role, recipient_address_fingerprint, original_request_id)
  ```
  từ chối bản ghi thứ 2, dẫn tới Crash 500 ở backend thay vì xử lý mượt mà hoặc cấp định danh/chu kỳ phân biệt.
- Các bước tái hiện:
  1. Đăng nhập hệ thống với tài khoản Quản trị / Quản lý có quyền (`ADMIN` hoặc `MANAGER`).
  2. Truy cập màn hình Quản lý Yêu cầu gửi thông báo: `http://localhost:5173/admin/notifications/requests`.
  3. Mở chi tiết request #1001 (đã có request con gửi lại #1005).
  4. Bấm "Gửi lại với link/vé mới".
  5. Nhập lý do: "Khách hàng liên hệ xin link mới do link 24h đã hết hạn" và bấm "Xác nhận cấp lại và gửi".
- Kết quả mong đợi: Hệ thống tạo yêu cầu gửi lại thành công với mã liên kết mới (HTTP 201 Created) hoặc thông báo rõ ràng về giới hạn số lần gửi lại.
- Thực tế quan sát: Backend throw `DataIntegrityViolationException: Duplicate entry 'BK-20261106-001-BOOKING_CONFIRMED-CUSTOMER-fp1001-1001' for key 'notification_request.uq_nr_idempotency'`, Frontend hiển thị lỗi 500.
- Bằng chứng:
  - Screenshot: ![Lỗi Redeliver 500](../ppswbs-spec/features/007-notification-management/test-results/e2e/screenshots/E2E-007-011--redeliver-with-refreshed-credential.jpg)
- Đề xuất khắc phục (Backend / Database):
  - Xem xét lại thiết kế ràng buộc `uq_nr_idempotency`: Nếu cho phép nhiều lần gửi lại hoặc redelivery trên cùng một request gốc, cần bổ sung cột version / attempt_number hoặc sequence id vào unique constraint, hoặc chỉ áp dụng idempotency cho initial requests (`original_request_id IS NULL`), còn requests con (`original_request_id IS NOT NULL`) cần có idempotency key riêng (ví dụ `client_token` hoặc `resend_cycle`).


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
