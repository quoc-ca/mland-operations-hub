# Feature 003 — thống kê đầu việc

> **Snapshot 2026-11-14**, nhánh `quoc` tại `dd9a6e01`. Số liệu đếm từ các dòng task `- [ ] Txxx` / `- [x] Txxx`
> trong `tasks.md` của từng sub-feature; `tasks.md` vẫn là nguồn sự thật, file này chỉ là bản tổng hợp.
> Task bị thay thế (superseded) được tính là đã đóng. Cập nhật lại file này khi tick hoặc mở lại task.

## Tổng quan

| Sub-feature | Tổng | Đã xong | Còn mở | Làm được ngay | Bị chặn |
| --- | ---: | ---: | ---: | ---: | ---: |
| 003.1 Workshop booking | 75 | 70 | 5 | 3 | 2 |
| 003.2 Workshop operations | 97 | 95 | 2 | 2 | 0 |
| 003.3 Settlement and changes | 59 | 55 | 4 | 2 | 2 |
| 003.4 OTA connectivity | 63 | 57 | 6 | 2 | 4 |
| **Tổng** | **294** | **277** | **17** | **9** | **8** |

Tỷ lệ hoàn thành theo số task: **94,2%** (277/294). Đây là tỷ lệ theo số dòng task, không theo effort.

## Còn mở — làm được ngay (9)

| Task | Loại | Việc | Phụ thuộc / ghi chú |
| --- | --- | --- | --- |
| 003.2 T097 | Backend | Xoá `BookingSettlementReadinessQuery`, `BookingCancellationEligibilityQuery`, `ReplaceRequiredSessionsCommand`, `CancelBookingCommand` theo cả booking | Gỡ được sau 003.3 T106 (đã xong); giữ phần OTA còn dùng |
| 003.1 T100 | Backend | Xoá phần còn lại của luồng đặt lẻ trực tiếp và facade legacy (`recordFinalBalanceResolved`, `getConfirmedBookingFinancialProjection`, …) | 7 controller đã xoá 2026-11-13; nên làm cùng 003.2 T097 |
| 003.2 T096 | Backend + test | MySQL IT còn lại; **thêm code** chặn huỷ, đổi ca, cấp lại QR cho vé OTA (FR-036) trong 3 service | Chặn OTA chưa có trong code |
| 003.1 T097 | Test | MySQL IT cho toàn luồng đặt theo nhóm, gồm giảm giá tự động | — |
| 003.3 T103 | Test | MySQL IT settlement: thu tiền hỗn hợp, chốt sổ, hoàn tiền, huỷ 24h, đổi ca | — |
| 003.1 T106 | Frontend | Hiện giá sau giảm tự động trên 5 màn hình công khai (FR-052) | Gate 006 T105 đã đạt |
| 003.3 T102 | Frontend | Màn Staff/Manager: folio, chốt sổ, hàng đợi hoàn tiền, huỷ/đổi vé | Backend các route đã xong |
| 003.4 T057 | Bằng chứng | Phân quyền Staff theo booking trên bề mặt add-on OTA | Chạy test và ghi evidence |
| 003.4 T058 | Bằng chứng | Ingress fail-closed tạm thời của `POST /api/v1/ota-provider/booking-events` | Chạy test và ghi evidence |

## Còn mở — bị chặn bởi bên ngoài (8)

| Task | Chặn bởi | Việc |
| --- | --- | --- |
| 003.1 T005 | Cấu hình VNPay thật | Merchant identity, secret HMAC, IPN HTTPS, quyền QueryDR/refund, evidence sandbox |
| 003.1 T046 | 003.1 T005 | Review evidence cấu hình trước khi bật VNPay thật |
| 003.3 T005 | Cấu hình VNPay thật | Như 003.1 T005, phía settlement |
| 003.3 T035 | 003.3 T005 | Review evidence cấu hình trước khi bật VNPay thật |
| 003.4 T004 | Readiness của provider OTA | Ghi evidence Klook/GetYourGuide đã duyệt trước khi bật `BOOKING_ENABLED` |
| 003.4 T038b | 003.4 T004 | Map sự kiện huỷ/hoàn thật của từng provider; V1 chỉ huỷ cả booking |
| 003.4 T051 | Onboarding provider | Booking, gửi vé/QR và quét trên sandbox của provider |
| 003.4 T054 | T004, T051 | Go-live review theo từng provider |

## Theo phase

### 003.1 Workshop booking

| Phase | Xong / tổng | Còn mở |
| --- | ---: | --- |
| 1 Setup and shared boundaries | 7/8 | T005 |
| 2 Foundational booking integrity | 6/6 | |
| 3 US1 Discover a workshop package | 4/4 | |
| 4 US2 Create a pending workshop booking | 8/8 | |
| 5 US3 Pay the workshop deposit | 9/9 | |
| 6 US4 Look up and recover a booking | 7/7 | |
| 7 Cross-cutting verification and handoff | 13/14 | T046 |
| 8 Multi-ticket cart, per-ticket booking | 8/9 | T097 |
| 9 Direct booking is only a booking group | 6/7 | T100 |
| 10 Public package price shows the automatic discount | 1/2 | T106 |
| 11 Per-ticket facts for 003.3 | 1/1 | |

### 003.2 Workshop operations

| Phase | Xong / tổng | Còn mở |
| --- | ---: | --- |
| 1 Setup and architecture rebase | 4/4 | |
| 2 Foundational boundaries, persistence and security | 15/15 | |
| 3 US1 Manager configures slots and future sessions | 7/7 | |
| 4 US2 Authorized actor starts and operates a session | 6/6 | |
| 5 US3 Staff checks in one attendance ticket | 8/8 | |
| 6 US4 Recovery from lost ticket access | 7/7 | |
| 7 US5 Manager finalizes attendance and cancels safely | 7/7 | |
| 8 Polish, cut-over safety and verification | 8/8 | |
| 9 Settlement and required-session-change handoff | 5/5 | |
| 10 Workshop package master and public discovery | 16/16 | |
| 11 Wax casting foundry calendar rule | 3/3 | |
| 12 Manager REST command idempotency | 1/1 | |
| 13 Direct-booking open-folio attendance outcome | 2/2 | |
| 14 Per-ticket capacity, attendance, QR and changes | 6/8 | T096, T097 |

### 003.3 Settlement and changes

| Phase | Xong / tổng | Còn mở |
| --- | ---: | --- |
| 1 Setup and shared boundaries | 5/6 | T005 |
| 2 Foundational settlement integrity | 6/6 | |
| 3 US1 Staff settles a completed booking | 8/8 | |
| 4 US2 Customer cancels a whole booking | 4/4 | |
| 5 US3 Manager changes future required sessions | 3/3 | |
| 6 US4 Manager remedies Mland-caused disruption | 4/4 | |
| 7 Cross-cutting verification and handoff | 13/14 | T035 |
| 8 Per-ticket settlement, bank refunds, counter collection and changes | 12/14 | T102, T103 |

### 003.4 OTA connectivity

| Phase | Xong / tổng | Còn mở |
| --- | ---: | --- |
| 1 Setup and design-gate evidence | 4/5 | T004 |
| 2 Foundational safety and persistence | 10/10 | |
| 3 US2 Manager publishes eligible channel inventory | 10/10 | |
| 4 US1 OTA guest secures workshop places | 8/9 | T058 |
| 5 US3 OTA cancellation/refund protects inventory | 6/7 | T038b |
| 6 US4 Finance reconciles channel revenue | 6/6 | |
| 7 US5 Staff records a shop add-on | 7/8 | T057 |
| 8 Polish, verification and go-live control | 6/8 | T051, T054 |

## Đã đóng trong ngày 2026-11-14

003.1 T094 (email cảnh báo Manager qua FE-001), T107; 003.2 T092; 003.3 T093, T094, T096, T097, T099, T100,
T101 (phạm vi voucher do 006 quyết), T105, T106; 003.4 T059, T060, T061, T038 (superseded bởi T038a/T038b).
