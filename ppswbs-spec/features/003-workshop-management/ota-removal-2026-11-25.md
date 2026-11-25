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

## Dấu vết còn trong code và database (rà lại sau bước 3)

Đối chiếu bằng cách đọc working tree nhánh `quoc-remove-ota` ngày 2026-11-25
(backend đang được sửa, chưa commit). **Chưa chốt** giữ hay xoá bảng/cột.

| Thứ còn lại | Ghi chú |
| --- | --- |
| Các bảng `ota_*`, `workshop_session_ota_snapshots` (migration V19, V22–V29, V48–V51, V61, V68–V74, V186) | Không còn chức năng dùng; migration đã áp dụng không sửa |
| `attendance_tickets.sales_channel` (V154), `TicketSalesChannel.OTA`; `cancelTicket`, `replaceTicketSessions`, `reissueTicketQr` và change options vẫn từ chối bằng `OTA_TICKET_NOT_SUPPORTED` | Guard cho dữ liệu cũ; không còn đường tạo vé OTA |
| `settlement_addon_bills`, bảng payment attempt/refund của nó (V99); entity bill và attempt, nhánh `SHOP_ADDON` của VNPay ingress | Chỉ để ghi nhận bằng chứng cho bill đã tồn tại; không còn route tạo/thu/hoàn |
| `RefundReason.OTA_ADDON_OVERCHARGE` (cột ENUM V122), case type `ADDON_REFUND` | Không còn nơi phát sinh mới |
| Folio correction từ chối booking mã `OTA-` | Guard cho dữ liệu cũ |
