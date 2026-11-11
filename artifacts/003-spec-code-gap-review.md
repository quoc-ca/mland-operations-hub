# Rà soát lệch giữa code và spec 003 (12/11/2026)

Phạm vi: các commit sửa code 003 (`workshopbooking`, `workshopoperations`, `workshopsettlement`, `otaconnectivity`, migration, frontend `workshops`/`operations`) từ 30/10 đến 06/11/2026 không sửa `ppswbs-spec/features/003-workshop-management`. Spec 003 thay đổi lần cuối ngày 05–06/11 (a909081, 4c74328). Cách đối chiếu: đọc nội dung từng commit, so endpoint trong controller với OpenAPI, so migration với data-model, so DTO với schema contract.

Mục đích: bổ sung các hành vi đã có trong code vào spec **trước** khi đồng bộ tài liệu `workshop-changes-to-implement.md`. Mục "Có thể là lỗi" cần xác nhận trước khi ghi vào spec.

## A. 003.1 Workshop Booking

| # | Hành vi trong code | Commit | Spec hiện tại | Cần bổ sung |
|---|---|---|---|---|
| A1 | `GET /api/v1/members/me/workshop-bookings`: Member xem danh sách booking của mình, mới nhất trước, tối đa 30 booking | b662e42 | OpenAPI chỉ có `GET .../members/me/workshop-bookings/{bookingCode}`; spec.md không có danh sách | spec.md (FR mới), OpenAPI, ui-flow |
| A2 | Booking `EXPIRED` chỉ hiện trong danh sách Member 24 giờ sau `holdExpiresAt`, sau đó ẩn | 6987155 | Không có | spec.md |
| A3 | Huỷ giữ chỗ chưa thanh toán (`pending-hold-release`) trả lại điểm và voucher đang giữ của quote Member, trừ khi đã có payment attempt hoặc bằng chứng thanh toán đến muộn (khi đó để kết quả thanh toán quyết định) | f707cbe | Spec chỉ nói release giải phóng chỗ, không nói quyền lợi | spec.md (FR-017/FR-020), data-model |
| A4 | `BookingSummary` có thêm `attendanceStatus` (`ATTENDED`, `NO_SHOW_PENDING` trong 24 giờ sửa điểm danh, `NO_SHOW`; null trước khi chốt điểm danh; chỉ để hiển thị) | b662e42 | Schema OpenAPI không có | OpenAPI, spec.md |
| A5 | `BookingSummary` có `ticketReferences` (tham chiếu vé mờ, chỉ trong view của Member chủ booking) để ghép với nguồn đánh giá của Feature 011; Member được mời đánh giá vé đã tham dự theo điều kiện của 011 | b662e42, c60056e, 12e7b76 | Không có | OpenAPI, spec.md (tích hợp 011) |
| A6 | Quote response có `reasonDetails[{code, message}]` với thông điệp tiếng Việt, fallback về text gốc | 78d79a3 | Schema chỉ có `reasons: string[]` | OpenAPI, spec.md |
| A7 | Màn hình đặt: chọn voucher từ "Kho Voucher" của Member, tự gọi quote, hiển thị điểm của Member; chỉ gửi mã voucher khi `choice = VOUCHER` | 46e72c0, 78d79a3, 0b78d86 | Không mô tả cách chọn voucher | ui-flow, spec.md |
| A8 | Sau khi giữ chỗ: trang thành công có nút thanh toán cọc VNPay và đồng hồ 10 phút; hết giờ thì tải lại booking đến khi server báo trạng thái mới; Member chọn booking từ "Đặt chỗ của tôi" thay vì nhập mã; trang chủ hiển thị buổi xác nhận sắp tới của Member | bfdb20e | Spec có countdown cho lookup pending, chưa có các hành vi còn lại | ui-flow |
| A9 | Mở booking từ Magic Link không cần đăng nhập, đọc token trong email | 881fd93 | Spec có Magic Link; cần kiểm tra route frontend khớp ui-flow | ui-flow (xác nhận) |
| A10 | Staff mở trang payment-return của thanh toán tại quầy được hướng quay lại final bill | 34012cf | Không có | ui-flow 003.3 |

## B. 003.2 Workshop Operations

| # | Hành vi trong code | Commit | Spec hiện tại | Cần bổ sung |
|---|---|---|---|---|
| B1 | Danh mục reason code cố định gồm 9 mã: `TICKET_REDELIVERY_UNAVAILABLE` (cần ghi chú + evidence), `CUSTOMER_DEVICE_UNAVAILABLE`, `EARLY_CHECKIN`, `PHYSICAL_VOUCHER_VERIFIED`, `INSTRUCTOR_UNAVAILABLE` (cần ghi chú), `FACILITY_ISSUE` (cần ghi chú), `WEATHER_EMERGENCY` (cần ghi chú), `RECORDING_ERROR` (cần ghi chú), `OTHER_REQUIRES_NOTE` (cần ghi chú) | 97301e1 và trước đó | Spec chỉ nêu có danh mục cố định, `OTHER_REQUIRES_NOTE`, `TICKET_REDELIVERY_UNAVAILABLE`; OpenAPI không có enum | spec.md, OpenAPI enum |
| B2 | Cấu hình `workshop.operations.visible-location-ids` (env `WORKSHOP_OPERATIONS_VISIBLE_LOCATION_IDS`) lọc các location được hiển thị ở discovery công khai và vận hành; rỗng = tất cả | 8d8e5a4 | Không có | spec.md (assumption/cấu hình), quickstart |
| B4 | UI làm mờ nút chuyển trạng thái session mà lifecycle không cho phép | 0a21bcb | Không có | ui-flow |
| B5 | `WorkshopOperationsFacade` cung cấp `SellingLocationPort` cho Feature 002 (location workshop dùng làm chi nhánh bán) và lệnh huỷ booking đã xác nhận (`CancelBookingCommand`) | ed92c6b | `workshop-interfeature-facades.md` không có | contracts/workshop-interfeature-facades.md |

## C. 003.3 Settlement

| # | Hành vi trong code | Commit | Spec hiện tại | Cần bổ sung |
|---|---|---|---|---|
| C1 | `GET /workshop-bookings/{bookingCode}/final-bill/operator` cho Staff/Manager | 9a5c877 | Có trong spec.md, **không có** trong OpenAPI | OpenAPI |
| C2 | `GET /workshop-bookings/{bookingCode}/final-bill/add-ons`: danh sách add-on catalogue của package + location cho operator | 9a5c877 | Không có trong OpenAPI | OpenAPI, spec.md |
| C3 | Bằng chứng xác nhận của operator trên charge: `consentReference` (`CONSENT-<id>`), `consentRecordedBy`, `consentRecordedAt` (V103) | 9a5c877 | spec.md có; data-model "Charge Evidence" không có | data-model |
| C4 | `loyaltyProcessedAt` trên final bill (V97): đánh dấu folio đã quyết định kết quả Loyalty, không quét lại; scheduler là trigger/retry duy nhất, không gọi Loyalty trong transaction huỷ/remedy/finalize/attendance | ade3df0 | Có trong tasks T089; data-model và spec.md không có | data-model, spec.md |
| C5 | Nguồn Loyalty của booking Member được giữ ở các trạng thái `CONFIRMED`, `COMPLETED`, `NO_SHOW`, `CANCELLED` | 4fa224e | Không nêu | spec.md / interfeature facade |
| C6 | Thanh toán và hoàn tiền của bill add-on shop cho OTA: `settlement_addon_payment_attempts`, `settlement_addon_refunds` (V99); `mland_booking_reference` trên addon bill (V104) | 6e2ca7a | data-model 003.3 chỉ nhắc ngắn | data-model 003.3 |
| C7 | Settlement problem handler ưu tiên cao nhất để 404/409 không bị trả 500; variable charge gửi số VND nguyên | 9a5c877 | Không cần ở spec (chi tiết triển khai) | Không bổ sung |

## D. 003.4 OTA Connectivity

| # | Hành vi trong code | Commit | Spec hiện tại | Cần bổ sung |
|---|---|---|---|---|
| D1 | `POST /api/v1/ota-provider/booking-events`: nhận callback booking từ OTA, header `X-Ota-Signature`, body gồm provider, environment, externalEventReference, externalBookingReference, providerProductReference, providerOptionReference, participantCount, acceptedAmountMinor, currencyCode, externalPaymentEvidenceReference, correlationReference; trả acknowledgement có channelBookingReference, mlandBookingReference, ticketReferences, rejectionReason | 1fe139a | spec.md nói có ghi nhận inbound event; **OpenAPI không có endpoint** | OpenAPI, spec.md |
| D2 | Adapter huỷ theo provider (GetYourGuide, Klook) ánh xạ huỷ từ OTA | 148801d, 4694c7a | Có trong tasks, provider-readiness | Xác nhận spec.md đã có quy tắc ánh xạ |

## Trạng thái bổ sung (12/11/2026)

Đã bổ sung vào spec các mục A1–A10, B1, B2, B4, B5, C1–C6, D1:

- 003.1: `spec.md` (FR-006a, FR-006b, FR-017j, FR-021a, FR-021b, FR-021c), `contracts/workshop-booking.openapi.yaml` (danh sách Member, `attendanceStatus`, `ticketReferences`, `reasonDetails`, mô tả release), `design/ui-flow.md` (mục "Behaviour backfilled from implementation").
- 003.2: `spec.md` (bảng reason code, cấu hình visible-location), `contracts/workshop-operations.openapi.yaml` (enum `reasonCode`), `design/ui-flow.md` (nút trạng thái session).
- 003 chung: `contracts/workshop-interfeature-facades.md` (`CancelBookingCommand`, `CancelConfirmedBookingCommand`, nguồn Loyalty, `SellingLocationPort`).
- 003.3: `contracts/workshop-settlement.openapi.yaml` (`final-bill/operator`, `final-bill/add-ons`), `data-model.md` (`loyaltyProcessedAt`, bằng chứng xác nhận operator, bill add-on OTA), `spec.md` (xử lý kết quả Loyalty).
- 003.4: `contracts/ota-connectivity.openapi.yaml` và `spec.md` (`POST /ota-provider/booking-events`, ghi rõ là ingress tạm).

Tất cả mục bổ sung đều gắn nhãn "backfilled 2026-11-12" kèm commit nguồn. Bốn file OpenAPI đã kiểm tra parse YAML thành công. D2 không cần sửa: quy tắc adapter huỷ đã có trong tasks/provider-readiness của 003.4.

## E. Có thể là lỗi (cần xác nhận trước khi ghi spec)

- **E3 (D1):** `OtaProviderEventController` kiểm tra `X-Ota-Signature` bằng cách so sánh chuỗi với `credentialReference` của connection, không phải chữ ký thật; khi `ota.auth.allow-test-tokens` bật thì chấp nhận mọi giá trị bắt đầu bằng `test-`. Spec đã ghi đây là ingress tạm, phải thay bằng callback có chữ ký của từng provider trước go-live, và cần đảm bảo cờ test tắt ở production.

- **E1 — ĐÃ SỬA (12/11/2026):** `BookingDepositReconciliationService.markFailed` nay chấp nhận booking `CANCELLED` với `paymentStatus = UNPAID` (huỷ giữ chỗ trước khi trả cọc) bên cạnh `EXPIRED`. Khi rà lại còn phát hiện lỗi rộng hơn: QueryDR trả `91` (không có giao dịch, tức khách mở link nhưng không trả) bị bỏ qua, nên voucher/điểm bị giữ cho cả booking `EXPIRED`; nay `91` có chữ ký hợp lệ với attempt đã hết hạn được ghi `NOT_FOUND_VERIFIED` và tính là không thu tiền. Test: `BookingDepositReconciliationServiceTest` (7 ca). Spec: FR-017k trong 003.1. Mô tả gốc bên dưới giữ để tham chiếu.
- **E1 (mô tả gốc, A3):** Huỷ giữ chỗ sau khi đã tạo payment attempt nhưng thanh toán không thành công: điểm/voucher không được trả ở bước huỷ mà "để kết quả thanh toán quyết định". `BookingDepositReconciliationService` chỉ xử lý booking `PENDING_PAYMENT` (dòng 64, 92), nên sau khi booking đã `CANCELLED` thì quyền lợi có thể bị giữ mãi nếu thanh toán thất bại hoặc không bao giờ hoàn tất. Cần sửa code (trả quyền lợi khi thanh toán có kết quả cuối không thành công) hoặc chốt quy tắc khác trước khi ghi vào spec.
- **E2:** Commit fbc2f22 ghi frontend gọi `direct-addon-bills` chưa có backend. Kiểm tra lại ngày 12/11: không còn lời gọi này trong `ppswbs_frontend/src`. Không cần xử lý.

## Không lệch (đã kiểm tra)

- Quy tắc add-on/variable charge trên folio OPEN, session đã bắt đầu, xác nhận của operator (9a5c877): đã có trong 003.3 spec.md.
- Kết quả Loyalty theo điểm danh và trạng thái folio (ade3df0): đã có trong 003.3 spec.md (`WORKSHOP_COMPLETED_AND_FULLY_PAID`, `WORKSHOP_NO_SHOW_FINAL`, `VALID_WORKSHOP_CANCELLATION`).
- Chốt điểm danh chỉ qua `attendance-finalizations`, lifecycle endpoint từ chối `ATTENDANCE_FINALIZED` (c0f550d): đã có trong 003.2 spec.md dòng 48, 98.
- Package management chỉ Manager (94f7392 đảo lại 97301e1): khớp spec.
- Endpoint còn lại của 003.1–003.4 đều có trong OpenAPI tương ứng.
- Đối soát cọc (`BookingDepositReconciliation`, 69bc88c): đã có trong 003.1 data-model.
- Quy tắc ngày làm việc xưởng của Wax Casting (V89): đã có trong 003.2 data-model và T089.
