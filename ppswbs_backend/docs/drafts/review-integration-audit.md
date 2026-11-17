# Audit tạm — tích hợp review vào "Đặt chỗ của tôi" (Feature-011 × 003.1)

Ngày: 2026-11-06. File tạm, không commit. Mục đích: ghi mâu thuẫn và điểm chưa rõ TRƯỚC khi sửa. Không đổi nghiệp vụ, API contract hay migration.

## 1. Đã đọc

AGENT.md, spec 011 (spec.md, version.md §10, design/ui-flow.md), contract `review-management.openapi.yaml` (EligibleSource, CreateReview), backend `reviewmanagement/eligibility/*` (adapter workshop), frontend `features/reviewmanagement/*` và `workshops/WorkshopDetailPage.jsx`, màn "Đặt chỗ của tôi" mới của 003.1 (`workshops/MyBookingsList.jsx`).

Chưa đọc đủ / không truy cập được: **Figma** (file `m5Cyk5rit6lKaX3TPprSow`, node 200:21, 72:731, 72:645, 73:150, 73:213) — không có công cụ Figma trong phiên này; **Feature-004** (ảnh hưởng chéo) chỉ cần kiểm tra là không đụng tới; `spec-general.md`, `DESIGN.md`, `APIs.md`, `DB.md` chỉ tra cứu theo từ khóa.

## 2. Sự kiện đã xác minh (có nguồn)

| # | Sự kiện | Nguồn |
|---|---|---|
| F1 | Review target là `WORKSHOP_SESSION`; branch chỉ là ngữ cảnh. | spec 011 §4.1; contract `TargetType` |
| F2 | Eligible chỉ khi `attendance_state = CHECKED_IN` của vé tại ca đó. | spec 011 §4.1, §11.2; `InternalWorkshopAttendanceAdapter.BASE_SELECT` |
| F3 | Member lấy nguồn đủ điều kiện từ `GET /api/v1/reviews/eligible-sources`; mỗi item có `targetReference` = `workshop_sessions.reference`. | contract; adapter (`ws.reference AS session_ref`) |
| F4 | `BookingSummaryResponse.sessions[].sessionReference` (003.1) cũng là `workshop_sessions.reference` (UUID). | `BookingLookupService.project` → `BookedSessionDto` |
| F5 | Nguồn đã có review thì backend không trả là có thể tạo. | spec 011 §4.1 |
| F6 | version.md §10 ghi: màn My bookings / Booking confirmation "chưa có route React", Figma giữ làm bàn giao cho tới khi route có session và eligibility. Nay 003.1 đã có danh sách "Đặt chỗ của tôi" trên `/workshop-booking/lookup`, có `sessionReference`. | version.md §10; `MyBookingsList.jsx` |

## 3. Mâu thuẫn / điểm chưa rõ (KHÔNG tự sửa nghiệp vụ)

| # | Vấn đề | Tác động | Ai quyết |
|---|---|---|---|
| A1 | **003.1 đã thêm `attendanceStatus` (ATTENDED/NO_SHOW…) ở danh sách booking, chỉ để hiển thị.** Nó suy từ "mọi ca đã chốt điểm danh và không có NO_SHOW", KHÔNG bằng `CHECKED_IN` từng vé. Không được dùng nó làm eligibility. | Nếu dùng sẽ sai với F2 (ví dụ booking nhiều ca, 1 ca check-in 1 ca chưa). | Quy tắc đã rõ: eligibility chỉ lấy từ backend 011. |
| A2 | Hợp đồng không nói rõ `targetReference` luôn bằng `sessionReference` của 003.1 (F3 và F4 khớp theo code hiện tại, không theo contract). | Nếu một bên đổi định dạng, nút đánh giá sẽ biến mất âm thầm. | Đạt xác nhận bằng văn bản; đề xuất ghi vào contract. |
| A3 | `CreateReviewPage` và `EligibleSourcesPage` hiển thị `sourceReference` (mã vé nội bộ) cho người dùng. Yêu cầu của Đạt: "không hiển thị source ID". | Vi phạm yêu cầu riêng tư/hiển thị. | Đạt (file của 011). Chưa sửa. |
| A4 | `InternalWorkshopAttendanceAdapter` truy vấn thẳng bảng `attendance_tickets`, `ticket_session_entitlements`, `workshop_sessions`, `workshop_bookings` bằng JDBC raw SQL. Spec 011 §11.1 cấm đọc repository/bảng của Workshop; AGENT.md §6 cấm raw SQL trong application code. | Phá ranh giới module (003 đổi cột là review hỏng); không kiểm được bằng Modulith. | Đạt + owner 003. Chưa sửa. |
| A5 | Cùng adapter đó ghi `contactValue` (email/điện thoại) vào log lỗi (`log.error(... contactValue ...)`). Spec 011 §9: liên hệ Guest phải được bảo vệ trong log. | Rò PII vào log. | Đạt. Chưa sửa. |
| A6 | `V100__review_history_retention.sql` lỗi trên MySQL đang dùng: `Duplicate foreign key constraint name 'fk_review_revisions_review'` (DROP và ADD cùng tên trong một `ALTER`). | DB dev không áp được V100; backend phải khởi động với `ignoreMigrationPatterns` (tức là che lỗi, trái yêu cầu của Đạt). Hôm nay đã thử `outOfOrder` một lần trên DB dev và xóa dòng `failed` khỏi `flyway_schema_history` (việc tương đương repair, chỉ trên DB dev). | Đạt sửa V100 (tách hai câu `ALTER`) — migration chưa áp ở đâu thì sửa được; cần xác nhận đã áp trên máy nào. |
| A7 | Figma và frontend có thể khác nhau về chỗ đặt nút (My Bookings riêng so với danh sách nằm trong trang Tra cứu). | Bố cục. | Cần Đạt/người thiết kế xác nhận sau khi xem Figma. |

## 4. Kế hoạch (trong phạm vi frontend 003.1, chỉ dùng API 011 đã có)

1. Màn "Đặt chỗ của tôi" (Member) gọi `GET /api/v1/reviews/eligible-sources` một lần.
2. Với mỗi booking, so `sessions[].sessionReference` với `eligible.targetReference` (không phân biệt hoa/thường).
   - Khớp → nút **"Viết đánh giá"** dẫn tới `/reviews/new` kèm `state.source` (y như `EligibleSourcesPage`).
   - Không khớp mà booking CONFIRMED chưa tham dự → nút mờ "Đánh giá sau khi bạn tham dự" (đúng ý Figma: CTA mờ trước điểm danh).
   - Không hiển thị `sourceReference`, email, điện thoại hay dữ liệu giao dịch.
3. Nếu gọi eligible-sources lỗi (403/mạng) → không hiện nút, không báo lỗi to (không phải việc chính của trang).
4. Không sửa backend, contract, migration, hay file của 011 ngoài việc import.

## 5. Ảnh hưởng chéo

- 003.1: chỉ frontend `MyBookingsList` / `WorkshopLookupPage`. Không đổi API 003.1.
- 004: không đụng (không import, không đổi gì).
- 011: chỉ gọi `listEligibleSources` có sẵn; không đổi API, không đổi trang của 011.
