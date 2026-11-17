# Bàn giao Feature 003 — bước 6: late payment, người nhận vé và đổi QR

- Ngày bàn giao: 2026-11-13.
- Branch làm việc: `quoc`.
- Remote đã push: `origin/quoc` tại commit `a23e4c6c`.
- Phạm vi tiếp nhận: backend của 003.1 T094–T095 và 003.2 T093; frontend và bước 7 chưa bắt đầu trong luồng này.
- Không sửa hoặc gom thư mục `.claude/` đang untracked; đây là thay đổi ngoài phạm vi bàn giao.

## 1. Trạng thái đã hoàn thành

### Hạ tầng kiểm thử

Commit `fb337eb2` — `test(backend): isolate MySQL suites and background jobs` đã:

- tách schema MySQL theo suite và giao schema cho Flyway;
- tắt Hibernate `create-drop` trong profile MySQL;
- tắt scheduler mặc định trong test;
- giới hạn Hikari pool và Spring test-context cache;
- xử lý vòng đời schema OTA sau khi Spring/Hikari đóng;
- loại bỏ chuỗi lỗi `Too many connections`, bảng bị suite khác xóa, scheduler chạy khi schema bị xóa và `serialized_event` sai kiểu.

### Regression thuộc bước 1–5 của 003

Commit `a23e4c6c` — `fix(003): align booking and bank refund regressions` đã:

- bổ sung test bean còn thiếu cho `TicketRecoveryIT`;
- cập nhật validation và route contract của booking group/cart;
- sửa giới hạn hoàn tiền để tính đúng cọc, verified final/add-on payment và khoản thu tại quầy theo ticket;
- chuyển toàn bộ allocation của một refund case sang bank-refund request, không dừng sau allocation đầu tiên;
- thêm migration append-only `V124__allow_pending_bank_refund_allocation.sql` (đổi thành `V125` ngày 2026-11-13 vì trùng `V124__retail_quote_branch_discount.sql` trên develop) để cho phép trạng thái `PENDING_BANK_REFUND`;
- cập nhật acceptance test từ VNPay Refund sang vòng đời hoàn tiền ngân hàng;
- đồng bộ Settlement contract với các route final-bill đã duyệt;
- sửa `WorkshopOtaSnapshotIT` để thiếu cấu hình OTA được skip đúng thay vì tạo 11 lỗi Spring context.

Bằng chứng gần nhất:

```powershell
cd ppswbs_backend
.\mvnw.cmd -q "-Dtest=**/workshopbooking/**,**/workshopoperations/**,**/workshopsettlement/**" test
```

Kết quả: exit code `0`; toàn bộ phạm vi 003 chạy thành công. Các suite OTA tùy chọn được skip khi không có `WORKSHOP_OTA_TEST_JDBC_*`.

## 2. Công việc tiếp theo cần thực hiện

### 003.1 T094 — late-payment review

Yêu cầu gốc: `003.1-workshop-booking/spec.md` FR-040 và FR-040a.

Luồng cần có:

1. Nhận signed VNPay evidence đến sau khi group hold đã hết.
2. Lưu một review idempotent cho verified evidence.
3. Kiểm tra năm điều kiện:
   - toàn nhóm còn capacity và channel quota;
   - chưa có session bắt đầu, session sớm nhất còn ít nhất 2 giờ;
   - giá/voucher/điểm tính lại theo policy đã ghi nhận bằng đúng tiền đã thu;
   - evidence đến không quá 60 phút sau `holdExpiresAt`;
   - buyer không có active booking khác trùng các session.
4. Nếu cả năm điều kiện đạt: giữ lại capacity mới cho cả nhóm và xác nhận atomic, audit `LATE_PAYMENT_RECONFIRMED`.
5. Nếu không: tạo `PENDING_MANAGER`, gửi email cho payer và cảnh báo Manager ngay; queue sắp theo session sớm nhất.
6. Manager chỉ được `CONFIRM` nếu phần thất bại chỉ là điều kiện 4 hoặc 5, buyer vẫn muốn đi, và điều kiện 1–3 vẫn đạt tại thời điểm duyệt.
7. `REFUND` phải mở hoàn tiền ngân hàng 100% qua 003.3.
8. Kết quả cuối phải đến buyer trong 24 giờ kể từ evidence hoặc trước session sớm nhất, tùy mốc nào đến trước.

### 003.1 T095 + 003.2 T093 — người nhận vé và QR rotation

Yêu cầu gốc: 003.1 FR-041 và 003.2 FR-033.

Luồng cần có:

1. Buyer có thể tùy chọn gửi từng vé tới một email người nhận sau khi group đã xác nhận.
2. Gửi lại cùng email giữ nguyên QR.
3. Đổi sang email khác phải revoke QR cũ ngay, gọi Operations phát hành QR opaque mới cho cùng entitlements, rồi gửi vé mới.
4. Check-in phải từ chối QR đã revoke.
5. Không được đổi recipient sau khi bất kỳ entitlement nào của vé đã check-in.
6. Tối đa ba lần đổi online cho mỗi vé; vượt giới hạn phải đến shop.
7. Recipient chỉ có quyền xem vé của mình, không có quyền quản lý group, đổi lịch hoặc đổi recipient.
8. Audit chỉ giữ email cũ/mới đã mask, actor, channel, thời gian và correlation; không ghi QR/raw token.
9. Dùng nội dung email đã được duyệt trong `artifacts/workshop-email-templates-draft.md`.

## 3. Design blocker phải chốt trước khi code

### Blocker A — chưa có proof/authority cho endpoint đổi recipient

Contract hiện tại:

```text
PUT /api/v1/workshop-tickets/{ticketCode}/recipient
RecipientRequest { recipientEmail }
security: public hoặc bearer
```

Request không có contact proof, OTP, Magic-Link credential hoặc evidence cho việc Staff đã xác minh buyer. Backend vì vậy không thể phân biệt Guest buyer, recipient và người lạ biết ticket code.

Phương án đề xuất để owner/Tech Lead duyệt:

- Member buyer: Firebase bearer + server kiểm tra ownership của group/ticket.
- Guest buyer: OTP gửi tới email booking contact ban đầu; không gửi OTP xác minh quyền quản lý tới email recipient.
- Staff tại shop: dùng một `buyerVerificationReference` do server cấp sau khi buyer xác minh OTP; không nhận boolean `buyerVerified` từ client.
- Người nhận vé không tự đổi recipient; họ nhờ buyer hoặc đến shop.
- Giới hạn ba lần chỉ tính thay đổi online của buyer; Staff tại shop được xử lý thêm nhưng bắt buộc reason + audit.

Cần cập nhật OpenAPI/DTO/error contract trước khi viết controller/service/test.

### Blocker B — thiếu facade 003.1 → 003.3 để mở late-payment bank refund

FR-040 yêu cầu Manager chọn `REFUND` thì mở hoàn tiền ngân hàng 100%, nhưng `WorkshopSettlementFacade` chưa có command tương ứng. 003.1 không được gọi thẳng `BankRefundService` nội bộ của 003.3.

Facade cần được duyệt tối thiểu:

- command chứa group/review/evidence reference, operation reference và correlation reference;
- nguồn có thẩm quyền của tổng tiền đã thu và cách phân bổ về từng ticket;
- kết quả `ACCEPTED`, `PRIOR_EQUIVALENT` hoặc `REJECTED` với safe reason;
- idempotency/replay rule khi Manager bấm lại hoặc callback đến lặp;
- transaction boundary: quyết định review không được báo hoàn tất nếu 003.3 chưa tạo durable bank-refund request;
- trạng thái phản hồi khi thiếu thông tin tài khoản hoàn tiền.

Không giải quyết blocker bằng cách thêm dependency trực tiếp từ workshopbooking tới service/entity/repository của workshopsettlement.

### Các điểm người tiếp nhận phải kiểm chứng thêm

- `WorkshopOperationsFacade.reserveGroupCapacity` có thực sự kiểm tra cả channel quota cho late reconfirm hay chỉ capacity.
- Nguồn query authoritative để phát hiện buyer có active booking khác trên cùng session.
- 006 có thể recompute voucher/points theo đúng recorded policy revision sau expiry hay cần một facade method bổ sung.
- Cách tạo Manager queue/alert và notification snapshot không chứa PII trong event.

Nếu một trong các điểm này thiếu contract, phải ghi design blocker thay vì tự chọn hành vi.

## 4. Vị trí code và tài liệu cần đọc trước

Tài liệu:

- `ppswbs-spec/features/003-workshop-management/003.1-workshop-booking/spec.md` — FR-040, FR-040a, FR-041.
- `ppswbs-spec/features/003-workshop-management/003.1-workshop-booking/tasks.md` — T094, T095, T097.
- `ppswbs-spec/features/003-workshop-management/003.1-workshop-booking/data-model.md` — `LatePaymentReview`, `TicketRecipient`.
- `ppswbs-spec/features/003-workshop-management/003.1-workshop-booking/contracts/workshop-booking-cart.openapi.yaml`.
- `ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/spec.md` — FR-033.
- `ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/tasks.md` — T093, T096.
- `ppswbs-spec/features/003-workshop-management/contracts/workshop-interfeature-facades.md`.
- `artifacts/workshop-email-templates-draft.md`.

Code hiện có liên quan:

- `features/workshopbooking/service/GroupPaymentEvidenceService.java`
- `features/workshopbooking/service/GroupConfirmationService.java`
- `features/workshopbooking/service/GroupHoldExpiryScheduler.java`
- `features/workshopbooking/service/WorkshopCartBenefitService.java`
- `features/workshopbooking/entity/BookingGroup.java`
- `features/workshopbooking/entity/TicketBooking.java`
- `features/workshopbooking/facade/WorkshopOperationsAdapter.java`
- `features/workshopoperations/facade/WorkshopOperationsFacade.java`
- `features/workshopoperations/service/GroupCapacityService.java`
- `features/workshopsettlement/facade/WorkshopSettlementFacade.java`
- `features/workshopsettlement/service/BankRefundService.java`

Schema nền đã có từ V113:

- `ticket_recipients`
- `ticket_recipient_changes`
- `late_payment_reviews`

Người tiếp nhận phải kiểm tra cột/constraint thực tế trong migration trước khi tạo entity; chỉ thêm migration append-only, không sửa migration đã áp dụng.

## 5. Thứ tự triển khai đề nghị

1. Chốt và ghi lại hai contract blocker ở mục 3.
2. Viết test cho 003.2 QR revoke/reissue và check-in từ chối QR cũ; implement 003.2 T093.
3. Viết test authorization/idempotency cho recipient; implement persistence, audit, notification và endpoint 003.1 T095.
4. Bổ sung facade late-payment refund giữa 003.1 và 003.3 cùng contract test.
5. Viết test năm điều kiện, auto/manual/refund/replay/deadline; implement 003.1 T094.
6. Chạy focused tests sau từng task.
7. Chạy toàn bộ phạm vi 003 bằng lệnh ở mục 1.
8. Chỉ đánh dấu task `[x]` sau khi có bằng chứng test và cập nhật metadata đúng policy; không sửa checklist reviewer-owned.

## 6. Acceptance tối thiểu khi bàn giao lại

- QR cũ bị từ chối ngay sau recipient email change; same-email resend không đổi QR.
- Người lạ/recipient không thể đổi người nhận; Member/Guest/Staff đi đúng proof flow.
- Check-in hoặc quá ba lần online bị từ chối đúng contract, không có side effect một phần.
- Duplicate recipient command và duplicate payment evidence không tạo QR/review/refund thứ hai.
- Late auto-confirm không được partial; race capacity chỉ có một kết quả thắng hợp lệ.
- Manager `CONFIRM` bị chặn nếu điều kiện 1–3 không còn đúng.
- Manager `REFUND` tạo đúng durable bank-refund request và không gọi VNPay Refund.
- Email/notification event, audit và log không chứa email đầy đủ, raw QR, OTP, Magic Link hoặc payment payload.
- Migration chạy được trên MySQL 8.4 từ baseline hiện tại và Spring Modulith verification vẫn pass.

## 7. Phần chưa làm và ngoài phạm vi bàn giao này

- 003.2 T091 per-ticket cancellation, T094 replacement và T095 quota thuộc bước 7.
- Frontend cart/recipient/late-payment management thuộc bước 8 sau khi backend contract ổn định.
- Các lỗi ngoài 003 được ghi riêng trong `artifacts/backend-non-003-test-failures-handoff.md` và giao đúng owner, không sửa chung trong bước 6.

`ppswbs_backend/ERROR-LOG.md` có bản ghi local-only `BE-DESIGN-0003`, nhưng file bị Git ignore. Tài liệu bàn giao này là bản có thể chia sẻ trong repository và chứa đầy đủ nội dung blocker cần thiết.
