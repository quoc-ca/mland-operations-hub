# Feature 003 — thống kê đầu việc

> **Snapshot 2026-11-14 (cập nhật cuối phiên nhóm C)**, nhánh `quoc`. Số liệu đếm từ các dòng task `- [ ] Txxx` / `- [x] Txxx`
> trong `tasks.md` của từng sub-feature; `tasks.md` vẫn là nguồn sự thật, file này chỉ là bản tổng hợp.
> Task bị thay thế (superseded) được tính là đã đóng. Cập nhật lại file này khi tick hoặc mở lại task.
> "Convergence" là các task do `$speckit-converge` chạy ngày 2026-11-14 thêm vào cuối `tasks.md` (47 task, 5 đã đóng).

## 1. Tổng quan

| Sub-feature | Tổng | Đã xong | Còn mở | Tỷ lệ xong |
| --- | ---: | ---: | ---: | ---: |
| 003.1 Workshop booking | 82 | 79 | 3 | 96,3% |
| 003.2 Workshop operations | 104 | 101 | 3 | 97,1% |
| 003.3 Settlement and changes | 77 | 71 | 6 | 92,2% |
| 003.4 OTA connectivity | 78 | 59 | 19 | 75,6% |
| **Tổng** | **341** | **310** | **31** | **90,9%** |

Tỷ lệ tính theo số dòng task, không theo effort.

### Còn mở: task cũ và task convergence

| Sub-feature | Task cũ | Convergence | Tổng mở |
| --- | ---: | ---: | ---: |
| 003.1 | 3 | 0 | 3 |
| 003.2 | 1 | 2 | 3 |
| 003.3 | 3 | 3 | 6 |
| 003.4 | 6 | 13 | 19 |
| **Tổng** | **13** | **18** | **31** |

Convergence đã đóng trong ngày: 003.1 T108, T113; 003.3 T107; 003.4 T105 (4 task CRITICAL); 003.4 T117 (owner quyết bỏ).

## 2. Còn mở theo nhóm làm việc (31)

| Nhóm | Nội dung | Số task | Ghi chú |
| --- | --- | ---: | --- |
| A | Settlement: tiền, quyền, MySQL IT (003.3) | 0 | Đã xong 2026-11-14 (T103, T108–T121) |
| B | OTA lõi nghiệp vụ và bằng chứng (003.4) | 13 | T108, T109 cùng file, làm tuần tự |
| C | Vận hành và đặt chỗ, backend + dọn legacy (003.1, 003.2) | 0 | Nhóm C xong 2026-11-14: 003.1 T097, T100, T109–T112, T114; 003.2 T096, T097, T107, T109–T111 |
| D | Frontend (003.1, 003.2, 003.3, 003.4) | 8 | Chờ API của A, C ổn định |
| E | Contract và tài liệu | 2 | Làm cuối |
| F | Còn chờ chủ sở hữu chốt | 0 | Xem mục 4 |
| X | Bị chặn bởi bên ngoài (VNPay, provider OTA) | 8 | Không làm được trong repo |
| | **Tổng** | **59** | |

## 3. Toàn bộ 59 task còn mở

Loại: BE = backend, FE = frontend, IT = test MySQL, DOC = tài liệu/contract, EVD = ghi bằng chứng.
Mức: **C** = CRITICAL, **H** = HIGH, **M** = MEDIUM, **L** = LOW (chỉ có ở task convergence; task cũ ghi "–").

### 003.1 Workshop booking (3)

| Task | Gốc | Loại | Mức | Nhóm | Việc |
| --- | --- | --- | --- | --- | --- |
| T005 | cũ | ngoài | – | X | Cấu hình VNPay thật (merchant, secret, IPN, QueryDR/refund) |
| T046 | cũ | ngoài | – | X | Review evidence cấu hình T005 trước khi bật VNPay thật |
| T106 | cũ | FE | – | D | Giá sau giảm trên 5 màn hình công khai (FR-052) |

### 003.2 Workshop operations (3)

| Task | Gốc | Loại | Mức | Nhóm | Việc |
| --- | --- | --- | --- | --- | --- |
| T108 | converge | FE | H | D | Form Manager tạo phiên hàng loạt |
| T112 | converge | FE | M | D | Sửa phiên (sức chứa, mở check-in) với xung đột phiên bản |
| T113 | converge | FE | M | D | Chỉnh quota đổi lịch theo phiên |

### 003.3 Settlement and changes (6)

| Task | Gốc | Loại | Mức | Nhóm | Việc |
| --- | --- | --- | --- | --- | --- |
| T005 | cũ | ngoài | – | X | Cấu hình VNPay thật, phía settlement |
| T035 | cũ | ngoài | – | X | Review evidence cấu hình T005 |
| T102 | cũ | FE | – | D | Màn Staff/Manager: folio, chốt sổ, hàng đợi hoàn tiền, huỷ/đổi vé |
| T122 | converge | FE | M | D | Trang khách trả lời remedy và xem folio |
| T123 | converge | DOC | L | E | Cập nhật `workshop-settlement-ticket.openapi.yaml` |
| T124 | converge | FE | L | D | Bỏ gọi các route booking-wide cũ trong `settlementApi.js` |

### 003.4 OTA connectivity (20)

| Task | Gốc | Loại | Mức | Nhóm | Việc |
| --- | --- | --- | --- | --- | --- |
| T004 | cũ | ngoài | – | X | Evidence readiness của provider OTA đã duyệt |
| T038b | cũ | ngoài | – | X | Map sự kiện huỷ/hoàn thật của provider (V1 huỷ cả booking) |
| T051 | cũ | ngoài | – | X | Booking, vé/QR, quét trên sandbox provider |
| T054 | cũ | ngoài | – | X | Go-live review theo từng provider |
| T057 | cũ | EVD | – | B | Evidence phân quyền Staff theo booking trên bề mặt add-on OTA |
| T058 | cũ | EVD | – | B | Evidence ingress fail-closed tạm thời |
| T106 | converge | BE | H | B | Booking callback gửi lặp trả lại kết quả đã lưu |
| T107 | converge | BE | H | B | Huỷ gửi lặp trả lại kết quả đã lưu |
| T108 | converge | BE | H | B | Trạng thái huỷ không đi ngược (REQUESTED muộn sau CANCELLED) |
| T109 | converge | BE | H | B | Chỉ cần nhận báo huỷ của OTA và nhả chỗ một lần; bỏ logic hoàn tiền (owner quyết, OTA tự lo) |
| T110 | converge | BE | H | B | Tạo `OtaPayoutPosition` khi xác nhận booking |
| T111 | converge | BE | M | B | Snapshot chính sách huỷ, giờ chốt, phí thay cho giá trị gán cứng |
| T112 | converge | BE | M | B | Từ chối khi điều khoản giá hết hạn (`effectiveTo`) |
| T113 | converge | BE | M | B | Bỏ so sánh số tiền khách trả với giá Mland (OTA luôn có phí sàn); lấy số vé từ số người |
| T114 | converge | BE | M | B | Ghi sổ cái `GROSS_SALE`, `COMMISSION_FEE`, `CUSTOMER_REFUND` |
| T115 | converge | BE | M | B | Việc đối soát cho callback không có mapping |
| T116 | converge | BE | M | B | Gắn việc đối soát huỷ vào sự kiện để `/resolve` chạy được |
| T118 | converge | DOC | L | E | Sửa mô tả `X-Ota-Signature` trong OpenAPI và contract |
| T119 | converge | FE | L | D | Lọc theo loại trên trang đối soát |

## 4. Quyết định của chủ sở hữu (Hồ Hoàng Quốc, 2026-11-14)

| Task | Quyết định |
| --- | --- |
| 003.3 T112 | Duyệt: tạo offer remedy theo vé từ handoff của 003.2; thông báo qua kênh handoff hiện có |
| 003.3 T117 | Duyệt: bảng yêu cầu đổi vé `PENDING/APPROVED/REJECTED/EXPIRED`, Manager duyệt qua route `decisions` |
| 003.3 T118 | Duyệt: `POST /workshop-booking-groups/{groupCode}/ticket-changes`, tất cả hoặc không |
| 003.4 T109 | Thu hẹp: chỉ cần nhận báo huỷ của OTA và nhả chỗ; điều kiện huỷ và việc hoàn đủ tiền là logic của OTA |
| 003.4 T117 | Bỏ: vé OTA là vé đơn như vé Mland, voucher của OTA nằm ở hệ thống OTA (spec FR-009 để người phụ trách spec sửa) |
| 003.4 T113 | Bỏ so sánh số tiền khách trả trên OTA với giá Mland (OTA luôn khác vì phí sàn và phí khác); số vé lấy từ số người, số tiền và tiền tệ chỉ ghi lại để đối soát (bỏ luôn kiểm tra tiền tệ; giữ kiểm tra thiếu bằng chứng thanh toán) |

Không còn điểm nào chờ chốt. Spec FR-009 và FR-012 của 003.4 để người phụ trách spec sửa theo các quyết định trên.

## 5. Thống kê theo mức độ (task convergence còn mở, 42)

| Mức | Số task |
| --- | ---: |
| CRITICAL | 0 (4 đã đóng) |
| HIGH | 15 |
| MEDIUM | 21 |
| LOW | 6 |

## 6. Đã đóng trong ngày 2026-11-14

003.1 T094 (email cảnh báo Manager qua FE-001), T107, T108, T113; 003.2 T092; 003.3 T093, T094, T096, T097, T099,
T100, T101 (phạm vi voucher do 006 quyết), T105, T106, T107; 003.4 T059, T060, T061, T038 (superseded), T105, T117 (owner quyết bỏ).
