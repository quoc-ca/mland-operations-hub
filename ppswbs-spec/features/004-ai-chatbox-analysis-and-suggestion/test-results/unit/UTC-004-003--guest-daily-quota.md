---
id: UTC-004-003
feature: 004-design-request-and-human-review
test_level: unit
test_case_type: B
source_ref: spec.md#3.2
source_class: edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignAnalysisService
source_method: consumeGuestQuota
test_class: edu.fpt.sep490_g22.ppswbs_backend.designreview.DesignAnalysisServiceTest
test_method: shouldEnforceFiveRequestsDailyLimitForGuest
---

# UTC-004-003: Giới hạn 5 yêu cầu/ngày cho Guest và xử lý mã HTTP 429

**Ngày tạo**: 2026-11-21
**Tác giả**: Senior QA Engineer
**Trạng thái**: PASS

## 1. Thiết kế kiểm thử

- Mã / Tên hàm: `DesignAnalysisService#consumeGuestQuota`
- Yêu cầu nghiệp vụ: Guest chỉ được gửi tối đa 5 yêu cầu thiết kế mỗi ngày (theo múi giờ `Asia/Ho_Chi_Minh`). Lần gửi thứ 6 phải bị chặn với mã HTTP 429 `DESIGN_REQUEST_GUEST_DAILY_LIMIT_EXCEEDED`.
- Loại ca kiểm thử: Boundary & Abuse (B)

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- Bảng `guest_design_request_quota` ghi nhận session digest của Guest trong ngày hiện tại.

## 3. Các bước thực hiện

1. Thực hiện liên tiếp 5 yêu cầu tạo design request hợp lệ từ cùng một Guest session.
2. Kiểm tra bộ đếm `request_count` trong cơ sở dữ liệu sau mỗi lần gửi.
3. Gửi tiếp yêu cầu thứ 6 từ cùng Guest session đó.

## 4. Kết quả mong đợi

- Lần 1 đến 5: `request_count` tăng lần lượt từ 1 đến 5, HTTP 201 Created.
- Lần thứ 6: Bị chặn, trả về HTTP 429 Too Many Requests với lỗi `DESIGN_REQUEST_GUEST_DAILY_LIMIT_EXCEEDED`.
- Thao tác gửi lặp với cùng `Idempotency-Key` không làm tăng bộ đếm quota.

## 5. Kết quả thực thi

<!-- EXECUTION:START -->
- Trạng thái: PASS
- Thực thi bởi: Senior QA & Verification Engineer
- Ngày thực thi: 2026-11-21
- Ghi chú: Đã kiểm tra cơ chế atomic increment trên bảng `guest_design_request_quota` tránh race condition khi gửi đồng thời.
<!-- EXECUTION:END -->
