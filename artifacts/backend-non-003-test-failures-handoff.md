# Bàn giao lỗi backend ngoài phạm vi 003

- Ngày kiểm chứng: 2026-11-13.
- Môi trường: Java 21, MySQL 8.4.13, Flyway 11.15.0.
- Lệnh: `./mvnw -q test` với các biến test trong `ppswbs_backend/.env`.
- Kết quả suite ổn định: **2.288 test, 11 failure, 9 error, 16 skipped**.
- Trong đó lỗi ngoài phạm vi 003 là **3 failure và 3 error** như dưới đây. Các lỗi Workshop Booking, Workshop Operations và Workshop Settlement được giữ lại cho luồng triển khai 003.

## 1. OTA connectivity

`OtaSyncDeliveryPersistenceIT` còn:

- 1 failure: `salesClosedMappingWithoutConfirmedPriceCannotBeEnabled` mong thông báo chứa `commercial terms`, nhưng implementation trả `Mapping is not an eligible initial per-person workshop offer`.
- 2 error: fixture của `incompleteAdminReadinessDecision...` và `completeAdminReadinessDecision...` không còn thỏa điều kiện initial per-person workshop offer.

Đề nghị owner 006 đối chiếu lại eligibility hiện hành và cập nhật fixture hoặc implementation theo contract, không chỉ đổi chuỗi assertion.

## 2. Ready-ring sales

- `CartJourneyTest.enforcesMaximumLineAndVariantQuantityLimitsAgainstPersistedCartData`: test mong thao tác vượt giới hạn giỏ phải ném lỗi, nhưng code không ném lỗi.
- `GroupedCheckoutIntegrationTest.createsAnIdempotentMixedLocationCheckoutThenCompletesLocalMockPayment`: checkout hold đã hết hạn hoặc không còn khả dụng trước lúc khởi tạo thanh toán.

Đề nghị owner Ready-ring kiểm tra guard giới hạn giỏ và cách fixture cố định thời gian sống của hold.

## 3. Payment integration

- `PaymentIngressServiceTest.deduplicatesSuccessfulDeliveryWhileAllowingConsumerProjectionRepair`: callback lặp thực tế trả mã `00`, test mong `02`.

Đề nghị owner Payment Integration chốt lại contract idempotency/callback trước khi sửa code hoặc test.

## 4. Trạng thái hạ tầng test đã sửa

- MySQL profile dùng schema riêng và để Flyway làm schema owner; Hibernate DDL bị tắt ở các profile MySQL.
- Ba schema lâu dài `ppswbs_workshop_test_it`, `ppswbs_loyalty_test_it`, `ppswbs_pm_test_it` đã được dựng lại đến V123; `event_publication.serialized_event` là `LONGTEXT`.
- Hikari pool test giới hạn tối đa 3 connection và Spring test-context cache giới hạn 8 context.
- Scheduler ứng dụng và Spring Modulith Moments bị tắt trong test; production vẫn bật mặc định.
- Schema OTA tạm chỉ bị xóa sau khi Spring đóng context/Hikari.
- Suite ổn định không còn `Too many connections`, checksum mismatch, bảng bị test khác xóa, `Unexpected error occurred in scheduled task` hoặc Flyway validation error.

Lưu ý: `.env` bị Git ignore, nên mỗi máy/CI vẫn phải cấu hình bốn URL test tách biệt và chỉ trỏ tới database test dùng một lần.
