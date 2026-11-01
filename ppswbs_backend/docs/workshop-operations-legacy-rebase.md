# Workshop Operations: Legacy-to-Target Rebase Inventory

**Date created**: 2026-10-26  
**Date updated**: 2026-10-26  
**Author**: Hồ Hoàng Quốc  
**Last updated by**: Hồ Hoàng Quốc  
**Status**: Approved rebase inventory and release guardrails for Phase 1 (Task T004)

---

## 1. Mục đích và bối cảnh

Tài liệu này ghi nhận hiện trạng kỹ thuật của mô hình Workshop Booking cũ (legacy model) và quy định chi tiết việc tái cấu trúc (rebase) sang kiến trúc mục tiêu của feature `003.2 Workshop Operations`.

Trong phiên bản prototype/baseline trước đây, quyền sở hữu (ownership) của phiên workshop (`WorkshopSession`) và vé tham dự (`BookingTicket`) bị đặt sai vị trí bên trong module `workshopbooking` (cụ thể là `edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain` và migration `V2__workshop_booking.sql`). Kiến trúc mục tiêu tách biệt dứt khoát ranh giới giữa hai module theo đúng Spring Modulith và Constitution:
- **`workshopoperations`**: Là nguồn chân lý duy nhất (single source of truth) về mặt giao dịch cho chi nhánh (`WorkshopBranch`), khung giờ (`WorkshopSlot`), phiên workshop (`WorkshopSession`), giữ chỗ công suất (`CapacityHold`), vé điểm danh cá nhân (`AttendanceTicket`), mã QR tra cứu/mã vé công khai, ghi nhận điểm danh (`AttendanceAction`) và phân công nhân sự tại phiên (`SessionStaffAssignment`, `SessionOperationalLead`).
- **`workshopbooking`**: Chỉ sở hữu giao dịch thương mại, thông tin thanh toán, hoá đơn và kênh gửi vé cho khách hàng. Booking chỉ tương tác với Operations qua named public interface `WorkshopOperationsFacade`. Module Operations không bao giờ gọi hay phụ thuộc vào Booking.

---

## 2. Bảng đối chiếu hiện trạng Legacy và Thiết kế mục tiêu

### 2.1. Thực thể `WorkshopSession`

| Tiêu chí | Mô hình cũ (Legacy Booking-owned) | Mô hình mục tiêu (`workshopoperations`) |
| :--- | :--- | :--- |
| **Vị trí & package** | `workshopbooking.domain.WorkshopSession` | `features.workshopoperations.entity.WorkshopSession` |
| **Module sở hữu** | `workshopbooking` | `workshopoperations` |
| **Quan hệ gói dịch vụ** | `package_id` gắn cứng vào session; mỗi session chỉ phục vụ một package cụ thể. | Tách rời: Khung giờ (`slot_id`) và chi nhánh (`branch_id`) chia sẻ công suất chung giữa các package. |
| **Địa điểm / Chi nhánh** | Trường `location VARCHAR(512)` dạng text tự do, không chuẩn hoá. | Chuẩn hoá `branch_id` liên kết với `WorkshopBranch` (đối chiếu `workshop_locations` canonical). |
| **Khung giờ làm việc** | Chỉ có `session_date DATE`, không có khung giờ tái sử dụng chuẩn hoá. | `WorkshopSlot` tái sử dụng, kiểm tra không chồng lấn (`no-overlap`), thời gian chuẩn `Asia/Ho_Chi_Minh`. |
| **Ràng buộc duy nhất** | Không có ràng buộc chặt theo khung giờ; dễ bị tạo trùng lặp. | Unique composite constraint `(branch_id, workshop_date, slot_id)`. |
| **Thời gian điểm danh** | Không có cấu hình mở cổng điểm danh. | `check_in_opens_at` (mặc định trước 60 phút hoặc Manager cấu hình ghi đè có audit). |
| **Vòng đời trạng thái** | 3 trạng thái đơn giản: `OPEN`, `FULL`, `CANCELLED`. | Máy trạng thái đầy đủ: `DRAFT`, `PUBLISHED`, `SALES_PAUSED`, `IN_PROGRESS`, `ATTENDANCE_FINALIZED`, `CANCELLATION_REQUESTED`, `CANCELLED`. |
| **Quản lý nhân sự** | Không có phân công nhân sự trên phiên. | `SessionStaffAssignment` (Staff được phân công) và `SessionOperationalLead` (tối đa 1 Manager điều hành onsite). |

### 2.2. Thực thể `BookingTicket`

| Tiêu chí | Mô hình cũ (Legacy Booking-owned) | Mô hình mục tiêu (`workshopoperations`) |
| :--- | :--- | :--- |
| **Vị trí & package** | `workshopbooking.domain.BookingTicket` | `features.workshopoperations.entity.AttendanceTicket` |
| **Module sở hữu** | `workshopbooking` | `workshopoperations` |
| **Tỷ lệ vé / người tham gia** | **1 vé duy nhất cho mỗi đơn booking** (`uk_ticket_booking` trên `booking_id`), bất kể `participant_count` là bao nhiêu người. | **1 vé cho mỗi người tham gia cá nhân** (`participant_count = N` thì phát hành đúng N vé riêng biệt). |
| **Định danh & bảo mật** | `qr_value VARCHAR(512)` chứa token ký trực tiếp. | Tách biệt: Opaque QR lookup reference bí mật và unique public ticket code để nhập tay. Không lộ PII hay token. |
| **Hành trình đa phiên** | Không hỗ trợ; vé chỉ có `valid_until` gắn theo thời gian phiên đơn. | Hỗ trợ qua `TicketSessionEntitlement`: 1 vé có thể có quyền tham dự 1 hoặc nhiều phiên (ví dụ gói Silverclay 2 phiên). |
| **Trạng thái điểm danh** | Chỉ có cờ thu hồi `revoked` (boolean), không có trạng thái điểm danh riêng từng người. | Điểm danh độc lập trên từng cặp (vé, phiên): `NOT_CHECKED_IN`, `CHECKED_IN`, `NO_SHOW`. |
| **Lịch sử & kiểm toán** | Không có bảng lưu vết lần quét, ai quét, lý do ngoại lệ. | `AttendanceAction` bất biến ghi nhận chi tiết thời điểm, actor, kênh quét, reason code và ghi chú kiểm toán. |
| **Trưng xuất cho Booking** | Booking đọc trực tiếp entity `BookingTicket`. | Booking chỉ nhận được hình chiếu an toàn `DeliverySafeTicketSet` qua facade sau khi cam kết công suất thành công. |

### 2.3. Quản lý công suất (`CapacityHold`)

- **Legacy**: `capacity_holds` nằm trong `workshopbooking`, gắn cứng 1 `booking_id` với 1 `session_id`. Thiếu cơ chế khoá đồng thời và không thể xử lý cam kết all-or-nothing cho đơn đặt nhiều phiên.
- **Mục tiêu**: `CapacityHold` nằm trong `workshopoperations`. Khung giao dịch bảo vệ chống bán vượt công suất (`oversell`). Các phiên trong cùng một đơn tạo thành một commitment set: giữ được toàn bộ số chỗ ở tất cả các phiên yêu cầu hoặc không giữ phiên nào cả.

---

## 3. Ràng buộc di chuyển dữ liệu và an toàn phát hành (Release & Migration Guardrails)

Nhằm ngăn chặn sự cố nghiệp vụ làm từ chối quyền vào cửa của khách hàng đã thanh toán hoặc làm lộ/sai lệch vé, các quy định sau đây mang tính bắt buộc:

1. **Tuyệt đối cấm tự động tách vé (No Automatic Ticket Split) trong migration schema**:
   - Migration Flyway không được tự ý chạy script SQL để phân chia 1 vé booking cũ thành N vé con. Số lượng người tham gia thực tế và định danh người nhận cần quy trình rà soát được duyệt.
2. **Tuyệt đối cấm tự động huỷ bỏ hoặc reset vé (No Automatic Revoke or Reset)**:
   - Migration không được set `revoked = TRUE` hàng loạt hoặc xoá trắng các vé cũ đang có hiệu lực của khách hàng.
3. **Tuyệt đối cấm tự động gửi email/vé lại trong lúc migrate (No Automatic Delivery/Email)**:
   - Việc chuyển đổi cấu trúc dữ liệu không được kích hoạt các sự kiện gửi email ngoài ý muốn làm phiền khách hàng.
4. **Kỷ luật Migration Flyway nối tiếp (Append-only)**:
   - Các file migration đã áp dụng (như `V5__account_management.sql`) là bất biến, không được sửa đổi.
   - Các bảng mới của `workshopoperations` phải nằm trong migration mới tiếp theo (ví dụ `V6__...`), tuân thủ schema thiết kế trong `data-model.md`.
5. **Kế hoạch xử lý dữ liệu legacy độc lập (Separate Reissue Runbook - Task T051)**:
   - Trước khi chính thức cho về hưu và xoá hoàn toàn các bảng/lớp legacy, phải có một runbook chuyển đổi dữ liệu được phê duyệt bởi người có thẩm quyền.
   - Chạy kiểm thử dry-run đối chiếu giữa booking cũ và vé phát hành mới, lưu vết kiểm toán đầy đủ trước khi kích hoạt trên môi trường thật.

---

## 4. Xác nhận kiến trúc Spring Modulith

- Package `edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations` được đăng ký là module độc lập.
- Chỉ các kiểu dữ liệu trong `edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade` được công khai ra ngoài module.
- Package `controller`, `dto`, `dao`, `entity`, `service` được giữ nội bộ (`package-private`/internal).
- Module `common` ở ngoài thư mục `features/` giữ vai trò technical kernel thuần tuý, không chứa logic nghiệp vụ của workshop.
