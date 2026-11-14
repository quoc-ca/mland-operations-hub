# Bàn giao Feature 003 — bước 7: thay đổi theo từng vé và API hoàn tiền

- Ngày bàn giao: 2026-11-13.
- Branch làm việc: `quoc`. Điểm bắt đầu: `c0781a11` (đã push).
- Phạm vi: backend 003.2 T091/T094/T095 và các HTTP route hoàn tiền còn thiếu của 003.3 T097.
- Ngoài phạm vi: 003.2 T092 (readiness/attendance outcome theo vé), T096 (IT tổng hợp),
  003.3 T099–T103 (hủy theo vé, Mland remedy, ticket changes, frontend), frontend bước 8.

## 1. Quyết định được owner duyệt trong phiên (2026-11-13)

Ba khoảng trống contract được phát hiện trước khi code, ghi tại `ppswbs_backend/ERROR-LOG.md`
(`BE-DESIGN-0004`, local-only) và được Hồ Hoàng Quốc chốt ngay trong phiên:

| Mã | Khoảng trống | Quyết định |
| --- | --- | --- |
| A | Không có route để Manager đổi reschedule quota của từng session | Route riêng `PUT /api/v1/workshop-sessions/{sessionId}/reschedule-quota` với `quota` (null = về mặc định, 0 = chặn đổi) và `reason` bắt buộc; Manager, `Idempotency-Key`, audit. `Session` trả thêm `rescheduleQuota`, `rescheduleQuotaOverridden`, `rescheduleQuotaUsed`. |
| B | `workshop.refund.execute` không tồn tại: codebase chỉ có role | V1 coi Manager là người giữ quyền đó; **mọi** transition của refund là Manager-only. Cấp quyền theo người để sau, cần FE-001. |
| C | `RefundRequest` bắt buộc `ticketCode` (refund late-payment là cấp group), `source` lệch `RefundReason`, thiếu route xem đủ số tài khoản / nhập tại quầy / gửi lại link | `ticketCode` nullable + thêm `groupCode`; `source` dùng 5 giá trị `RefundReason`; thêm `GET /{refundId}/account-number` (Manager, audit, `no-store`), `PUT /{refundId}/account` (Staff/Manager tại quầy), `POST /{refundId}/account-link-resends`. |

## 2. Đã hoàn thành

### Migration

`V131__per_ticket_release_replacement_quota.sql` (append-only):

- `workshop_sessions.reschedule_quota_override` (NULL = mặc định) và `reschedule_quota_used`;
- `ticket_entitlement_releases` — một dòng cho mỗi chỗ tương lai mà vé đã hủy trả lại;
- `ticket_session_replacements` — một dòng cho mỗi cặp (vé, source → target) đã dời,
  kèm `replacement_type` và `quota_consumed`, với check `MLAND` không bao giờ tiêu quota.

### 003.2 T091 — hủy theo từng vé (FR-031)

`WorkshopOperationsFacade.cancelTicket` (`TicketCancellationService`): xóa đúng các entitlement
tương lai chưa check-in, giữ một release row cho mỗi chỗ, giảm một chỗ trong confirmed hold của
line (hold về 0 thì RELEASED) nên chỗ thật sự được giải phóng, rồi đặt vé `CANCELLED`.
Session đã bắt đầu/finalize/cancel hoặc đã có attendance thì giữ nguyên entitlement và lịch sử.
Idempotent theo operation reference; vé khác trong nhóm không bị ảnh hưởng.

### 003.2 T094 — thay session theo vé (FR-034)

`WorkshopOperationsFacade.replaceTicketSessions` (`TicketSessionReplacementService`):
lock mọi session liên quan theo thứ tự reference, ghép source/target theo thứ tự giờ bắt đầu,
all-or-none trên toàn bộ các line, giữ QR và lịch sử attendance. Target phải tương lai và
`PUBLISHED`, capacity đọc bằng locking read như `GroupCapacityService`, luật package lấy từ
snapshot lúc booking qua `isValidTicketReplacement` (không kiểm lại min/max participant vì
dời một vé không đổi số người). `CUSTOMER_LATE_ARRIVAL` cho phép source đang `IN_PROGRESS` chưa
kết thúc và từ chối target muộn hơn `end + 7 ngày` (`LATE_ARRIVAL_WINDOW_EXPIRED`);
`CUSTOMER_ADVANCE` ra khỏi session đang chạy bị từ chối; `MLAND` nhận source đã cancel hoặc
đang chạy và không tiêu quota.

### 003.2 T095 — reschedule quota (FR-035)

Mặc định `max(1, capacity / 4)`, tự tính lại khi capacity đổi; Manager override giữ nguyên qua
mọi lần đổi capacity; `quota = 0` chặn đổi; `used` không bao giờ reset. Một customer change tiêu
một quota cho mỗi vé dời, hết thì từ chối `RESCHEDULE_QUOTA_EXHAUSTED` kèm các line lỗi.

### 003.3 T097 — HTTP route hoàn tiền

`RefundRequestController`: queue `GET /api/v1/workshop-refund-requests?status=`,
`POST /{refundId}/transitions` (APPROVE / RECORD_TRANSFER / CONFIRM_REFUNDED /
CLOSE_UNREACHABLE / REOPEN, Manager-only, `RECORD_TRANSFER` bắt buộc cả bank reference và
proof), `PUT /{refundId}/account` (Staff/Manager nhập tại quầy),
`POST /{refundId}/account-link-resends`, và `GET /{refundId}/account-number` (Manager,
audit `REFUND_ACCOUNT_REVEALED`, `Cache-Control: no-store`). Response chỉ trả mask; refund cấp
group trả `ticketCode = null` kèm `groupCode`.

### Contract và tasks đã cập nhật

- `003.2-workshop-operations/contracts/workshop-operations.openapi.yaml`: route quota, `RescheduleQuotaRequest`, 3 field mới của `Session`.
- `003.3-.../contracts/workshop-settlement-ticket.openapi.yaml`: 3 route mới, `RefundRequest` sửa `ticketCode`/`groupCode`/`source`, mô tả transition Manager-only.
- `contracts/workshop-interfeature-facades.md`: hai dòng per-ticket cancel/replace ghi hợp đồng thật đã implement.
- `tasks.md`: 003.2 T091/T094/T095 tick kèm bằng chứng; 003.3 T097 ghi phần route đã xong và phần quyền còn mở.

## 3. Bằng chứng kiểm thử

```powershell
cd ppswbs_backend
.\mvnw.cmd -o "-Dtest=PerTicketChangeIT" test
.\mvnw.cmd -o "-Dtest=RefundRequestControllerTest" test
.\mvnw.cmd -o "-Dtest=**/workshopbooking/**,**/workshopoperations/**,**/workshopsettlement/**" test
```

- `PerTicketChangeIT` (MySQL thật, 12/12 PASS): hủy một vé chỉ trả chỗ của chính nó, replay không
  trả hai lần, session đã check-in giữ lịch sử; quota mặc định theo capacity (12 → 3, 2 → 1,
  20 → 5), override giữ qua đổi capacity và clear thì về mặc định; advance dời một vé và tiêu một
  quota, quota 0 từ chối cả hai line không dời phần nào, Mland miễn quota, late-arrival đúng/ngoài
  cửa sổ 7 ngày, advance từ session đang chạy bị từ chối, target đầy từ chối theo capacity, vé đã
  hủy không dời được.
- `RefundRequestControllerTest` (8/8 PASS): queue và transition Manager-only, Staff bị chặn,
  `RECORD_TRANSFER` thiếu proof bị từ chối trước khi gọi service, Staff nhập tài khoản tại quầy và
  gửi lại link được, số tài khoản đầy đủ chỉ Manager + `no-store` + audit, refund cấp group trả
  `ticketCode` null, request không có actor bị từ chối trước mọi lệnh gọi service.

## 4. Phần còn lại và điều cần người tiếp nhận kiểm

1. `workshop.refund.execute` theo người vẫn mở (phụ thuộc FE-001). Hiện Manager làm mọi bước.
2. 003.2 T092 (readiness/outcome theo vé) và T096 (IT tổng hợp Phase 14) chưa làm.
3. 003.3 T099–T101 sẽ gọi `cancelTicket` và `replaceTicketSessions`; chưa có caller nào trong
   003.3 hoặc 003.1, nên hai facade mới hiện chỉ được chứng minh qua IT.
4. `moveCommittedSeat` tạo confirmed hold mới cho target khi line chưa có hold ở đó. Cần xác nhận
   với 003.1 rằng cách này không phá giả định nào về `expectedHoldReferences` khi release sau này.
5. Quota chỉ tính khi vé thật sự dời; vé giữ nguyên session trong cùng một lệnh không tiêu quota.
   Nếu nghiệp vụ muốn tính theo lệnh thay vì theo vé dời thì phải chốt lại FR-035.
