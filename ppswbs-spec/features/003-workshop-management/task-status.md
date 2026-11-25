# Feature 003 — thống kê đầu việc

> **2026-11-25:** 003.4 OTA đã bị loại ([record](ota-removal-2026-11-25.md)); các dòng và quyết định của 003.4 được gỡ khỏi bảng, tổng tính lại. Ghi chú cũ còn nhắc OTA là lịch sử.

> **Snapshot 2026-11-15 (sau khi làm các task converge)**, nhánh `quoc`. Số liệu đếm thẳng từ `tasks.md` của từng sub-feature
> (regex `^- \[[xX]\] T` là xong, `^- \[ \] T` là mở); `tasks.md` vẫn là nguồn sự thật, file này chỉ là bản tổng hợp.
> Task bị thay thế (superseded) được tính là đã đóng. Cập nhật lại file này khi tick hoặc mở lại task.
> "Convergence" là các task do `$speckit-converge` thêm vào cuối `tasks.md` (chạy 2026-11-14 và 2026-11-15;
> hiện có 54 task, cả 54 đã đóng). Lần chạy 2026-11-15 thêm 003.1 T115, 003.3 T126, T127;
> 003.2 không thêm task. Cả 3 task này đã làm xong cùng ngày.

## 1. Tổng quan

| Sub-feature | Tổng | Đã xong | Còn mở | Tỷ lệ xong |
| --- | ---: | ---: | ---: | ---: |
| 003.1 Workshop booking | 83 | 81 | 2 | 97,6% |
| 003.2 Workshop operations | 104 | 104 | 0 | 100% |
| 003.3 Settlement and changes | 80 | 78 | 2 | 97,5% |
| **Tổng** | **267** | **263** | **4** | **98,5%** |

Tỷ lệ tính theo số dòng task, không theo effort. Cả 4 task mở đều bị chặn bởi bên ngoài repo (VNPay);
không còn task nào làm được trong repo.

### Còn mở: task cũ và task convergence

| Sub-feature | Task cũ | Convergence | Tổng mở |
| --- | ---: | ---: | ---: |
| 003.1 | 2 | 0 | 2 |
| 003.2 | 0 | 0 | 0 |
| 003.3 | 2 | 0 | 2 |
| **Tổng** | **4** | **0** | **4** |

## 2. Còn mở theo nhóm làm việc (4)

| Nhóm | Nội dung | Số task | Ghi chú |
| --- | --- | ---: | --- |
| A | Settlement: tiền, quyền, MySQL IT (003.3) | 0 | Xong 2026-11-14 (T103, T108–T121) |
| C | Vận hành và đặt chỗ, backend + dọn legacy (003.1, 003.2) | 0 | Nhóm C xong 2026-11-14; 003.1 T115 xong 2026-11-15 |
| D | Frontend (003.1, 003.2, 003.3) | 0 | Đã xong 003.1 T106, 003.2 T108/T112/T113, 003.3 T102/T122/T124/T125/T126/T127 |
| E | Contract và tài liệu | 0 | Xong 2026-11-15: 003.3 T123, đồng bộ sau analyze/converge |
| F | Còn chờ chủ sở hữu chốt | 0 task | Không task nào bị chặn; các điểm cần quyết định ở mục 4 |
| X | Bị chặn bởi bên ngoài (VNPay) | 4 | Không làm được trong repo |
| | **Tổng** | **4** | |

## 3. Toàn bộ 4 task còn mở

Loại: ngoài = phụ thuộc bên ngoài. Task cũ không có mức nên ghi "–".

### 003.1 Workshop booking (2)

| Task | Gốc | Loại | Mức | Nhóm | Việc |
| --- | --- | --- | --- | --- | --- |
| T005 | cũ | ngoài | – | X | Cấu hình VNPay thật (merchant, secret, IPN, QueryDR/refund) |
| T046 | cũ | ngoài | – | X | Review evidence cấu hình T005 trước khi bật VNPay thật |

### 003.2 Workshop operations (0)

Không còn task mở.

### 003.3 Settlement and changes (2)

| Task | Gốc | Loại | Mức | Nhóm | Việc |
| --- | --- | --- | --- | --- | --- |
| T005 | cũ | ngoài | – | X | Cấu hình VNPay thật, phía settlement |
| T035 | cũ | ngoài | – | X | Review evidence cấu hình T005 |

## 4. Điểm cần chủ sở hữu quyết định

Chỉ ghi nhận, chưa chốt. Không điểm nào chặn task trong repo; mỗi điểm cần Hồ Hoàng Quốc (hoặc chủ feature liên quan) quyết
và ghi vào spec/plan/contract tương ứng.

### 003.1 Workshop booking

| # | Điểm cần quyết định |
| --- | --- |
| 1.1 | Kiểm thử bằng fake-provider VNPay có đủ để coi T093/T097 là xong, hay phải đánh dấu "provider-pending" phụ thuộc T005/T046 |
| 1.2 | Ai sở hữu lệnh kết luận theo vé (`NO_SHOW`, `COMPLETED`, `REFUND_*`): 003.1 hay 003.3 |
| 1.3 | Chủ của FR-050 (003.1 hay 003.3) |
| 1.4 | Xoá hay giữ file contract giỏ hàng rỗng `workshop-booking-cart.openapi.yaml` |
| 1.5 | Cách xử lý các task cũ T019–T042 (hiện đã có banner superseded): giữ làm lịch sử hay dọn khỏi `tasks.md` |

### 003.2 Workshop operations

| # | Điểm cần quyết định |
| --- | --- |
| 2.1 | Có thêm success criteria cho đổi vé theo vé, quota, idempotency hay không (analyze F16) |
| 2.2 | Giữ hay đổi nhãn `1.0.0-draft` của OpenAPI 003.2 (analyze F17) |
| 2.3 | Xác nhận FR-005b (ghi chú 2026-11-15: đổi slot hoãn sau V1) và trường `version` của `PATCH /workshop-sessions/{id}` (T112, chốt 2026-11-15) là baseline |

### 003.3 Settlement and changes

| # | Điểm cần quyết định |
| --- | --- |
| 3.1 | Giữ hay xoá các path đánh dấu `x-removed` trong `workshop-settlement.openapi.yaml` |
| 3.2 | T005/T035: chờ cấu hình VNPay thật hay chấp nhận dùng sandbox |
| 3.3 | SC-001…SC-011 có còn áp dụng nguyên văn khi settlement đã chuyển sang theo vé không |

## 5. Quyết định của chủ sở hữu (lịch sử)

### 2026-11-15 (Hồ Hoàng Quốc)

| Mục | Quyết định |
| --- | --- |

### 2026-11-14 (Hồ Hoàng Quốc)

| Task | Quyết định |
| --- | --- |
| 003.3 T112 | Duyệt: tạo offer remedy theo vé từ handoff của 003.2; thông báo qua kênh handoff hiện có |
| 003.3 T117 | Duyệt: bảng yêu cầu đổi vé `PENDING/APPROVED/REJECTED/EXPIRED`, Manager duyệt qua route `decisions` |
| 003.3 T118 | Duyệt: `POST /workshop-booking-groups/{groupCode}/ticket-changes`, tất cả hoặc không |

## 6. Đã đóng sau snapshot 2026-11-14

003.1 T106, T115; 003.2 T108, T112, T113; 003.3 T102, T122, T123, T124, T125, T126, T127.
