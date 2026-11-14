# Feature 003 — thống kê đầu việc

> **Snapshot 2026-11-14**, nhánh `quoc` tại `c91c4db7`. Số liệu đếm từ các dòng task `- [ ] Txxx` / `- [x] Txxx`
> trong `tasks.md` của từng sub-feature; `tasks.md` vẫn là nguồn sự thật, file này chỉ là bản tổng hợp.
> Task bị thay thế (superseded) được tính là đã đóng. Cập nhật lại file này khi tick hoặc mở lại task.
> "Convergence" là các task do `$speckit-converge` chạy ngày 2026-11-14 thêm vào cuối `tasks.md` (47 task, 5 đã đóng).

## 1. Tổng quan

| Sub-feature | Tổng | Đã xong | Còn mở | Tỷ lệ xong |
| --- | ---: | ---: | ---: | ---: |
| 003.1 Workshop booking | 82 | 72 | 10 | 87,8% |
| 003.2 Workshop operations | 104 | 95 | 9 | 91,3% |
| 003.3 Settlement and changes | 77 | 56 | 21 | 72,7% |
| 003.4 OTA connectivity | 78 | 59 | 19 | 75,6% |
| **Tổng** | **341** | **282** | **59** | **82,7%** |

Tỷ lệ tính theo số dòng task, không theo effort.

### Còn mở: task cũ và task convergence

| Sub-feature | Task cũ | Convergence | Tổng mở |
| --- | ---: | ---: | ---: |
| 003.1 | 5 | 5 | 10 |
| 003.2 | 2 | 7 | 9 |
| 003.3 | 4 | 17 | 21 |
| 003.4 | 6 | 13 | 19 |
| **Tổng** | **17** | **42** | **59** |

Convergence đã đóng trong ngày: 003.1 T108, T113; 003.3 T107; 003.4 T105 (4 task CRITICAL); 003.4 T117 (owner quyết bỏ).

## 2. Còn mở theo nhóm làm việc (59)

| Nhóm | Nội dung | Số task | Ghi chú |
| --- | --- | ---: | --- |
| A | Settlement: tiền, quyền, MySQL IT (003.3) | 15 | Làm trước vì liên quan tiền |
| B | OTA lõi nghiệp vụ và bằng chứng (003.4) | 12 | T108, T109 cùng file, làm tuần tự |
| C | Vận hành và đặt chỗ, backend + dọn legacy (003.1, 003.2) | 12 | Gồm các task cũ 003.1 T097, T100; 003.2 T096, T097 |
| D | Frontend (003.1, 003.2, 003.3, 003.4) | 8 | Chờ API của A, C ổn định |
| E | Contract và tài liệu | 3 | Làm cuối |
| F | Còn chờ chủ sở hữu chốt (003.4 T113) | 1 | Xem mục 4 |
| X | Bị chặn bởi bên ngoài (VNPay, provider OTA) | 8 | Không làm được trong repo |
| | **Tổng** | **59** | |

## 3. Toàn bộ 59 task còn mở

Loại: BE = backend, FE = frontend, IT = test MySQL, DOC = tài liệu/contract, EVD = ghi bằng chứng.
Mức: **C** = CRITICAL, **H** = HIGH, **M** = MEDIUM, **L** = LOW (chỉ có ở task convergence; task cũ ghi "–").

### 003.1 Workshop booking (10)

| Task | Gốc | Loại | Mức | Nhóm | Việc |
| --- | --- | --- | --- | --- | --- |
| T005 | cũ | ngoài | – | X | Cấu hình VNPay thật (merchant, secret, IPN, QueryDR/refund) |
| T046 | cũ | ngoài | – | X | Review evidence cấu hình T005 trước khi bật VNPay thật |
| T097 | cũ | IT | – | C | MySQL IT cho toàn luồng đặt theo nhóm, gồm giảm giá tự động |
| T100 | cũ | BE | – | C | Xoá luồng đặt lẻ còn lại và các facade legacy |
| T106 | cũ | FE | – | D | Giá sau giảm trên 5 màn hình công khai (FR-052) |
| T109 | converge | BE | M | C | Tính lại số tiền thu từ snapshot vé trước khi tạo URL VNPay, lệch hoặc dưới 5.000 thì 409 |
| T110 | converge | BE | M | C | Giới hạn tần suất tra cứu Guest, nhả giữ chỗ, gửi lại (429) |
| T111 | converge | BE | M | C | Gửi email mẫu 5 khi giữ chỗ hết hạn (`GROUP_HOLD_EXPIRED`) |
| T112 | converge | BE | L | C | Chuẩn hoá số điện thoại khi lưu và so chứng minh |
| T114 | converge | DOC | L | E | Sửa tài liệu lệch: plan.md, `CreateIdempotencyKey`, ca test trong T097 |

### 003.2 Workshop operations (9)

| Task | Gốc | Loại | Mức | Nhóm | Việc |
| --- | --- | --- | --- | --- | --- |
| T096 | cũ | IT+BE | – | C | MySQL IT còn lại; thêm chặn vé OTA trong 3 service (FR-036) |
| T097 | cũ | BE | – | C | Xoá query/command theo cả booking của 003.2 |
| T107 | converge | BE | H | C | Cửa sổ sửa điểm danh tính theo từng vé |
| T108 | converge | FE | H | D | Form Manager tạo phiên hàng loạt |
| T109 | converge | BE | M | C | Chuyển loyalty outcome sang điểm danh theo vé, xoá `BookingAttendanceOutcome` |
| T110 | converge | BE | M | C | Giữ danh sách dòng lỗi khi replay đổi phiên bị từ chối |
| T111 | converge | BE | M | C | `initialState` cho tạo phiên đơn lẻ |
| T112 | converge | FE | M | D | Sửa phiên (sức chứa, mở check-in) với xung đột phiên bản |
| T113 | converge | FE | M | D | Chỉnh quota đổi lịch theo phiên |

### 003.3 Settlement and changes (21)

| Task | Gốc | Loại | Mức | Nhóm | Việc |
| --- | --- | --- | --- | --- | --- |
| T005 | cũ | ngoài | – | X | Cấu hình VNPay thật, phía settlement |
| T035 | cũ | ngoài | – | X | Review evidence cấu hình T005 |
| T102 | cũ | FE | – | D | Màn Staff/Manager: folio, chốt sổ, hàng đợi hoàn tiền, huỷ/đổi vé |
| T103 | cũ | IT | – | A | MySQL IT settlement (làm sau các task A) |
| T108 | converge | BE | H | A | Tạo merchant reference và URL thanh toán VNPay phía server cho folio vé |
| T109 | converge | BE | H | A | Route đánh dấu khoản chờ là thất bại (`failures`) và đường QueryDR/IPN-failure |
| T110 | converge | BE | H | A | Tiền VNPay đến muộn: ghi thành tiền thừa và mở hoàn tiền |
| T111 | converge | BE | H | A | Thu tiền phải kiểm Staff được phân công, lấy chi nhánh từ ticket |
| T112 | converge | BE | H | A | Nối huỷ/dời phiên từ 003.2 sang đề nghị remedy theo vé |
| T113 | converge | BE | H | A | Chỉ đóng folio của remedy khi hoàn tiền tới `REFUNDED` |
| T114 | converge | BE | H | A | Bỏ thông tin liên hệ khỏi URL của route đọc correction |
| T115 | converge | BE | H | A | Trần correction dùng tiền thật đã thu, bỏ số giao dịch bịa |
| T116 | converge | BE | M | A | Kiểm trạng thái và địa điểm của Staff khi thao tác thay khách |
| T117 | converge | BE | M | A | Hàng đợi yêu cầu đổi cần Manager cho phép |
| T118 | converge | BE | M | A | Route đổi nhiều vé cùng nhóm, tất cả hoặc không |
| T119 | converge | BE | M | A | Xác nhận chuyển khoản gắn đúng folio, có Idempotency-Key |
| T120 | converge | BE | M | A | Bỏ route `/workshop-cash-days/*`, bắt buộc Idempotency-Key cho `/workshop-cash-closes` |
| T121 | converge | BE | M | A | Xoá nhánh thanh toán theo cả booking cũ trong `FinalPaymentService` |
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
| T113 | converge | BE | M | F | Giá `PER_PERSON` theo số người (chờ xác nhận hợp đồng provider) |
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

**Còn chờ chốt:** 003.4 T113 — giá OTA "theo từng người" (giá lẻ trong snapshot là giá một người, tối thiểu = giá × số người); cần xác nhận với hợp đồng provider.

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
