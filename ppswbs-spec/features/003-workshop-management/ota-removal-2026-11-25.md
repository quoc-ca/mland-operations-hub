# Quyết định: loại OTA khỏi Feature 003 (2026-11-25)

**Người quyết định:** Hồ Hoàng Quốc, chủ sở hữu 003.1–003.4.

**Quyết định:** xoá hẳn OTA (Klook, GetYourGuide và mọi kênh bán đối tác) khỏi
code, spec và report.

**Lý do:** chưa có tài khoản/sandbox đối tác nên không kiểm chứng và không go-live được.

## Hiệu lực trên spec

- Feature `003.4-workshop-ota-connectivity` bị gỡ toàn bộ (spec, plan, tasks,
  contracts, design, provider-readiness). Nội dung cũ xem trong lịch sử git
  (commit `58b4eae1` trở về trước còn đủ thư mục). Số 003.4 không dùng lại.
- Mọi yêu cầu, quyết định, task và contract về OTA trong 003.1–003.3 và tài liệu
  chung **không còn hiệu lực**. ID giữ nguyên, không đánh số lại.
- Đoạn còn nhắc OTA/003.4 trong lịch sử (Clarifications, approval, review,
  checklist, research, evidence, task đã đóng) chỉ là ghi chép tại thời điểm đó.
- Bị gỡ: 003.2 FR-010l–FR-010v và snapshot OTA (T053–T055); thao tác thu hồi vé
  OTA (T123); nhận booking OTA (003.1 T047, 003.2 T076, T156); hoá đơn add-on tại
  quầy cho booking OTA, thu và hoàn tiền của nó (003.3 D12, D16–D18, FR-038a,
  T036, T091, T092); route `/api/v1/ota-addon-bills/**`; quyền miễn phí khắc laser
  riêng cho khách OTA.
- Còn lại và vẫn đúng: Mland là nguồn duy nhất về session và sức chứa; booking
  trực tiếp (booking group), vé, folio và hoàn tiền ngân hàng không đổi.

## Database và code còn lại (bước 3, 2026-11-25)

Chủ sở hữu chốt xoá toàn bộ dấu vết OTA trong database, kèm code chỉ để đọc dữ
liệu cũ. Flyway `V203__remove_ota_database_objects.sql` (**làm mất dữ liệu**).

| Đã xoá | Ghi chú |
| --- | --- |
| 17 bảng `ota_*` và `workshop_session_ota_snapshots` | Xoá cả dữ liệu trong bảng |
| `settlement_addon_bills`, `settlement_addon_payment_attempts`, `settlement_addon_refunds` (V99, cả cột `mland_booking_reference` của V104) | Chỉ phục vụ hoá đơn add-on của booking OTA; kèm entity, repository, payment kind `SHOP_ADDON` |
| `attendance_tickets.sales_channel`, `TicketSalesChannel`, `OTA_TICKET_NOT_SUPPORTED` | Mọi vé đều do Mland bán |
| Giá trị `OTA_ADDON_OVERCHARGE` của `settlement_refund_requests.reason`, case type `ADDON_REFUND` |  |
| Guard folio correction với booking mã `OTA-` (`OTA_BOOKING_NOT_SUPPORTED`) | Folio không thuộc booking group vẫn trả 404 |

V203 dừng trước khi thay đổi bất cứ thứ gì (lỗi
`ck_v203_no_leftover_ota_rows`) nếu còn dòng vé OTA, refund/case/evidence của
add-on OTA, hoá đơn add-on hoặc booking mã `OTA-`. Cách xử lý: sao lưu, tạo lại
database local (hoặc tự quyết định với các dòng đó), xoá dòng 203 lỗi trong
`flyway_schema_history` rồi chạy lại.

**Giữ lại** (còn luồng không phải OTA dùng):

- `workshop_bookings`, `booking_session_commitments` và các bảng booking đơn đi
  kèm: 003.3 booking-wide settlement, nguồn Loyalty single-booking, báo cáo doanh
  thu và điều kiện review còn đọc (V133, quyết định 003.3 còn mở).
- Giá trị vé `REVOKED`, `REFUNDED` (V7): thuộc vòng đời vé chung, hiện không có
  nơi phát sinh.
- Migration đã áp dụng và biến môi trường test `WORKSHOP_OTA_TEST_JDBC_*`, tiền tố
  schema `ppswbs_ota_migration_` (chỉ là tên cấu hình test).
