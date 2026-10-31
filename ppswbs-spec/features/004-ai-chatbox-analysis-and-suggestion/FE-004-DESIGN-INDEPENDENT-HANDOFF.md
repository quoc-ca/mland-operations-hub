# FE-004 — Rebase Design thành chức năng độc lập

## Quyết định kiến trúc đã chốt

Design/AI Design Review là chức năng độc lập, không thuộc Workshop Booking.

FE-004 không tạo, đọc, sửa hoặc phụ thuộc vào:

- Workshop Booking;
- booking code hoặc booking status;
- `PENDING_PAYMENT` hoặc `CONFIRMED`;
- payment, deposit hoặc VNPay;
- workshop location hoặc session;
- capacity hold;
- ticket hoặc check-in;
- eligibility của booking.

FE-003 không gọi FE-004 và không lưu `designApprovalReference`.

## Việc FE-004 cần làm

### 1. Rebase phạm vi nghiệp vụ trong spec

Sửa `spec.md` để Design Request là yêu cầu độc lập:

```text
Khách/Member tạo yêu cầu design
→ gửi mô tả và/hoặc ảnh
→ AI phân tích/gợi ý
→ có thể Staff/Manager review
→ APPROVED / REJECTED / NEEDS_REVISION / WITHDRAWN
```

Loại bỏ toàn bộ wording kiểu:

- booking eligible for design;
- Design Request thuộc booking;
- chỉ booking đã thanh toán mới được design;
- package/location/session/booking summary trước design;
- design được tạo trước hoặc sau checkout.

### 2. Chốt ownership và actor

FE-004 cần chốt trong spec:

- Ai được tạo Design Request: chỉ Member hay Guest cũng được.
- Nếu Guest được dùng, họ chứng minh quyền truy cập thế nào mà không dùng booking.
- Ai review: Staff, Manager hay cả hai.
- Khi nào AI tự approve và khi nào chuyển human review.
- Khách có được withdraw, gửi revision hoặc tạo request mới không.
- Approval/rejection có thời hạn hay không.

Các quyết định trên thuộc riêng FE-004, không đẩy sang FE-003.

### 3. Rebase data model

`DesignRequest` không có các trường hoặc foreign key sau:

```text
bookingId
bookingCode
sessionId
locationId
holdId
ticketId
paymentId
depositId
```

Data model chỉ giữ dữ liệu thuộc design, ví dụ:

```text
designRequestReference
ownerAccountId hoặc guest ownership proof phù hợp
status
design description
uploaded asset reference
AI analysis/result reference
review decision
reviewer account reference
timestamps
reason / revision note khi cần
```

Nếu Design cần product/package để gợi ý phong cách hoặc vật liệu, chỉ lưu opaque catalogue/product reference và đọc qua `catalogue::facade`; không dùng Booking.

### 4. Rebase OpenAPI

Loại bỏ khỏi request, response và path bất kỳ field nào liên quan:

```text
bookingCode
bookingId
bookingStatus
sessionId
locationId
paymentStatus
ticketCode
```

Các API FE-004 chỉ xoay quanh Design Request, asset upload/reference, AI analysis, review decision và safe projection. Không tạo API gọi sang FE-003 để kiểm tra booking.

### 5. Gỡ dependency legacy Booking

Trong code/module declaration FE-004:

- Bỏ dependency `workshopbooking::facade`.
- Bỏ toàn bộ import từ `edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.**`.
- Bỏ gọi `getBookingEligibilityForDesign(...)`.
- Xóa hoặc rebase test seed các legacy type `WorkshopBooking`, `BookingStatus`, `BookingPaymentStatus` và `WorkshopBookingRepository`.

`DesignAnalysisService` đang là caller production chính của legacy facade; service này phải chạy độc lập, không cần booking.

### 6. Chỉnh dependency Account

Nếu FE-004 cần biết người tạo hoặc reviewer, chỉ dùng public Account Management facade:

- Không import account entity/repository nội bộ.
- Không dùng `members::facade` cũ nếu package đó không tồn tại.
- Dùng canonical account identity (`accountId`) cùng role/status/scope semantic khi thực sự cần.

### 7. Dependency hợp lệ sau rebase

```text
FE-004 → accountmanagement::facade
FE-004 → catalogue::facade  (chỉ nếu Design thực sự cần product/package context)
```

Không có dependency:

```text
FE-004 → 003 Booking
FE-004 → 003 Operations
FE-004 → 003 Settlement
```

### 8. Rebase tasks và test

Task cũ phụ thuộc Booking phải bị thay hoặc retire. Bổ sung test chứng minh:

- Tạo/review/approve/reject Design Request không tạo booking.
- Không gọi capacity hold, payment hoặc ticket.
- Không thay đổi availability workshop.
- Không có booking data trong response, log hoặc audit.
- Owner khác không đọc/sửa request trái phép.
- Staff/Manager review đúng quyền/scope đã chốt.
- Module build được mà không còn import legacy `ppswbs_backend.workshopbooking.**`.

### 9. Retire package legacy sau khi không còn caller

Sau rebase, kiểm tra toàn repo xem còn code nào dùng `ppswbs_backend.workshopbooking.**` không. Nếu không còn caller hợp lệ, tech lead/owner được chỉ định có thể retire package legacy đó.

Không được sửa tạm legacy bằng cách dựng lại `domain`/`infrastructure` cũ, vì FE-003 đã có module canonical `features/workshopbooking/**`.

## Không yêu cầu từ FE-004

- Không sửa FE-003.1, FE-003.2, FE-003.3 hoặc FE-003.4.
- Không đưa Design Approval sang Booking.
- Không tạo facade giữa FE-004 và FE-003.
- Không thay đổi capacity hold TTL.
- Không thay đổi VNPay.

## Điều kiện hoàn tất

FE-004 hoàn tất rebase khi Design Request chạy độc lập, không còn dependency/caller tới legacy Booking và build toàn backend không cần Maven exclude hoặc Flyway-ignore workaround.
