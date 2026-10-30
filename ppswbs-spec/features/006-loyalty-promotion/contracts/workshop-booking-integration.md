# Hướng dẫn tích hợp: Workshop Booking (003.1) gọi LoyaltyFacade

**Date created**: 2026-10-29
**Date updated**: 2026-10-29
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Status**: Draft cho owner 003.1 (Hồ Hoàng Quốc) review. Theo quyết định **Cách 2** (2026-10-29): `workshopbooking` gọi thẳng `LoyaltyFacade`, phụ thuộc một chiều `workshopbooking → loyaltypromotion::facade`. 006 không viết code trong `workshopbooking`.
**Sources**: [interfaces](interfaces.md) mục 4, [spec](../spec.md) FR-019–FR-022, FR-028–FR-038, Clarifications 2026-10-29, [integration gates](../integration-gates.md) G-04/G-05. Luồng mẫu chạy được: `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/loyaltypromotion/LoyaltyWorkshopFacadeIT.java` (test `referenceBookingFlowForATwoSessionPackage`).

## 1. Khai báo module

```java
// workshopbooking/package-info.java
@ApplicationModule(allowedDependencies = { ..., "loyaltypromotion :: facade" })
```

Chỉ dùng type trong `edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade`. Không import service/dao/entity của loyalty. Loyalty không import gì của `workshopbooking`, nên không có vòng phụ thuộc.

## 2. Bảng lời gọi

| Thời điểm (003.1) | Gọi | Transaction | Ghi chú |
| --- | --- | --- | --- |
| Member xem/tính lại báo giá | `previewBenefits(TrustedSourceContext, BenefitSelection)` | read-only, có hoặc không | Không giữ gì. Lưu `termsDigest` vào quote của booking. |
| Member xác nhận (accept) | `reserveAcceptedTransaction(AcceptedSourceContext, BenefitSelection, commandKey)` | **MANDATORY**, chung transaction với giữ chỗ 003.2 và lưu booking | Trả `AcceptedBenefitRef.postPromotionAmount` để 003.1 tính cọc. |
| Cọc đã xác minh | `settleAcceptedBenefits(outcome WORKSHOP_DEPOSIT_ACCEPTED)` | MANDATORY, chung transaction ghi nhận cọc | Chỉ consume voucher/điểm đang giữ, **không** cộng điểm. |
| Hoàn thành mọi phiên **và** trả đủ | `grantEarnedPoints(outcome WORKSHOP_COMPLETED_AND_FULLY_PAID)` | MANDATORY | Cộng điểm một lần theo policy lúc accept, trên `postPromotionAmount`. Thu phần còn lại không giảm lại. |
| Hết hạn cọc chưa trả (đã xác nhận) | `releaseHeldBenefits(UNPAID_EXPIRY_CONFIRMED)` rồi `finishIneligibleEarn(...)` | MANDATORY | Không suy hết hạn từ `originalDeadline`; chỉ gọi khi 003.1 đã phân xử. |
| Hủy hợp lệ | `restoreCancelledWorkshopBenefits(VALID_WORKSHOP_CANCELLATION)` rồi `finishIneligibleEarn(...)` | MANDATORY | HELD → release, CONSUMED → hoàn điểm và lượt. |
| No-show cuối | `finishIneligibleEarn(WORKSHOP_NO_SHOW_FINAL)` | MANDATORY | Không hoàn voucher/điểm đã dùng. |
| Đối soát | `findBenefitResult(SourceKind.WORKSHOP, bookingRef)` | read-only | Trạng thái từng phần và earn. |

Mọi lệnh ghi phải chạy trong transaction đang mở của 003.1 (cùng DataSource). Gọi ngoài transaction bị từ chối trước khi ghi.

## 3. Dựng `WorkshopFact` (gói nhiều phiên)

```java
WorkshopFact fact = new WorkshopFact(
        packageId,                 // ID số của gói (workshopoperations, V21)
        participantCount,          // > 0
        grossPackageAmount,        // phí CẢ GÓI do server định giá, VND BigInteger, không theo phiên
        List.of(WorkshopSessionFact.of(sessionRef1, branchId1, date1),   // mỗi phiên khách chọn một phần tử
                WorkshopSessionFact.of(sessionRef2, branchId2, date2)));
// gói một phiên:
WorkshopFact.singleSession(packageId, participantCount, fee, WorkshopSessionFact.of(sessionRef, branchId, date));

TrustedSourceContext context = new TrustedSourceContext(SourceKind.WORKSHOP, memberId, Instant.now(),
        List.of(), fact, BigInteger.ZERO);
```

- `sessionRef` = `workshop_sessions.reference` (UUID **chữ thường** dạng chuẩn, ví dụ `UUID.toString()`). Không dùng ID số. Sai định dạng → `INVALID_INPUT` (`workshop.sessions`).
- `branchId` = location của phiên; `workshopDate` = ngày tham gia theo giờ địa phương của phiên.
- Danh sách không rỗng, không trùng `sessionRef`; thứ tự không quan trọng (loyalty tự sắp theo ngày rồi ref).
- **Quy tắc voucher (chốt 2026-10-29):** gói phải trong phạm vi gói của voucher (nếu có) và **mọi** phiên phải khớp giới hạn chi nhánh/ngày/phiên (nếu có). Một phiên ngoài phạm vi → preview trả lý do, accept trả `PROMOTION_INELIGIBLE`.
- Tất cả giá trị lấy từ dữ liệu authoritative của server (package, session đã giữ, member đã xác minh), không lấy số tiền từ browser.

## 4. quoteRef và termsDigest

Loyalty **không** lưu `quoteRef`; nó chỉ bắt buộc khác rỗng. Booking sở hữu quote:

1. Preview: 003.1 dựng context, gọi `previewBenefits`, rồi tạo bản ghi quote của booking (ví dụ bảng `booking_quote`, khóa = `quoteRef`). Gợi ý trường cần lưu: memberId, packageId, danh sách sessionRef (kèm branch/date), participantCount, grossPackageAmount, `BenefitSelection` (choice, voucherCode, requestedPoints), `termsDigest`, `postPromotionAmount`, cọc/còn lại đã hiển thị, `expiresAt` của quote.
2. Member xác nhận quote đó (gửi `quoteRef`, không gửi số tiền).
3. Accept: 003.1 đọc quote, kiểm tra chủ sở hữu và hạn, **dựng lại** `TrustedSourceContext` từ dữ liệu authoritative hiện tại (cùng package/sessions/participants), rồi gọi:

```java
new AcceptedSourceContext(context, bookingRef, sourceRevision, /*memberOrigin*/ true,
        acceptedAt, originalDeadline /* hạn trả cọc, sau acceptedAt */, quoteRef, quote.termsDigest());
```

Loyalty tính lại ở strict mode dưới khóa. Digest gắn kết quả tính (gross, giảm, điểm, post, policy), package, từng phiên (`ref@branch@date`) và số người. Khác digest → `QUOTE_CHANGED`: 003.1 rollback, preview lại, cho khách xác nhận quote mới. Ví dụ: quote với phiên A+B nhưng accept với A+C → `QUOTE_CHANGED` (test `acceptingOtherSessionsThanTheQuotedOnesIsQuoteChanged`).

## 5. Transaction accept (một transaction)

```java
@Transactional
public Booking accept(String quoteRef, ...) {
    Quote quote = quotes.lockOwned(quoteRef, member);                 // 1. guard nguồn trước
    operations.createAndReserve(bookingCode, sessionRefs, people, ...); // 2. giữ chỗ 003.2 cho MỌI phiên
    AcceptedBenefitRef ref = loyalty.reserveAcceptedTransaction(      // 3. giữ voucher/điểm
            accepted(quote), quote.selection(), "create-" + bookingRef);
    BigInteger post = ref.postPromotionAmount();
    BigInteger deposit = ceilHalf(post);                              // 4. 003.1 tính cọc: ceil(post / 2)
    return bookings.save(..., post, deposit, post.subtract(deposit)); // remaining = post - deposit
}
```

- Guard nguồn (quote/booking/capacity) lấy **trước** khóa của loyalty (interfaces, Transaction and lock order).
- Bất kỳ bước nào lỗi → rollback toàn bộ: không còn chỗ giữ, không còn voucher/điểm giữ. Không catch lỗi loyalty rồi tiếp tục lưu booking.
- `commandKey` ổn định theo booking (ví dụ `create-<bookingRef>`). Gửi lại cùng key + cùng nội dung trả lại kết quả đã commit; khác nội dung → `IDEMPOTENCY_CONFLICT`. Cùng `bookingRef` không giữ thêm lần nữa.
- Loyalty chỉ snapshot `postPromotionAmount`; cọc và còn lại do 003.1 tính, giữ và chuyển sang 003.3 (`frozenPostPromotionAmount`/`frozenRemainingAmount`).

## 6. AuthoritativeOutcome

```java
new AuthoritativeOutcome(SourceKind.WORKSHOP, bookingRef, "workshopbooking",
        sourceOutcomeKey /* duy nhất cho mỗi kết quả, ví dụ "deposit-<paymentId>" */,
        sourceOutcomeVersion /* tăng dần theo booking */, OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED,
        decisionAt, originalDeadline, snapshotRef, /*memberOrigin*/ true, proofRef /* tham chiếu ngắn, không payload gateway */);
```

- 003.1 khóa bản ghi booking trước khi gọi, serialize theo `sourceOutcomeVersion`. Gửi lặp cùng key → trả receipt, không áp dụng lại.
- Paid flow: settle trước, grant sau (grant từ chối khi còn component HELD). Không gọi `grantEarnedPoints` với outcome cọc.
- Kết quả mâu thuẫn trạng thái cuối → `BENEFIT_STATE_CONFLICT`: đối soát, không ép trạng thái.

## 7. Lỗi và cách xử lý ở 003.1

| Code | Khi nào | 003.1 làm gì |
| --- | --- | --- |
| `INVALID_INPUT` | Fact/context sai (sessionRef không phải UUID, danh sách rỗng...) | Lỗi lập trình hoặc dữ liệu nguồn; trả 400. |
| `PROMOTION_INELIGIBLE` | Mã không có, sai phạm vi (kể cả một phiên ngoài phạm vi), hết hạn/dừng/hết lượt, chưa đủ phí tối thiểu, chọn AUTO cho workshop | Rollback; preview lại và hiện lý do. |
| `POINTS_USE_NOT_ALLOWED`, `POINTS_LIMIT_CHANGED` | Điểm không hợp lệ/không đủ lúc accept | Rollback; preview lại. |
| `QUOTE_CHANGED` | Digest khác quote khách đã xác nhận | Rollback; preview lại, khách xác nhận lại. |
| `WORKSHOP_NET_AMOUNT_INVALID` | Phí sau giảm ≤ 0 | Rollback; khách đổi lựa chọn. |
| `QUOTA_LIMIT_CONFLICT` | Hết lượt voucher do tranh chấp | Rollback; preview lại. |
| `IDEMPOTENCY_CONFLICT`, `BENEFIT_STATE_CONFLICT` | Cùng key khác nội dung / outcome mâu thuẫn | Đối soát; không tự đổi key. |
| `RETRYABLE_CONTENTION` | Lock timeout/deadlock (đã rollback sạch) | Thử lại cả transaction với **cùng** key và nội dung. |
| `DEPENDENCY_UNAVAILABLE` | Thiếu dependency hoặc nguồn tạm lỗi | 503. |

## 8. Việc phía 003.2 (provider target WORKSHOP)

`PromotionTargetPort` cho scope WORKSHOP đặt ở `workshopoperations` (chủ package theo BE-DESIGN-0008, `V21__workshop_packages`, và session), phụ thuộc `workshopoperations → loyaltypromotion::facade`. Nhận `TargetRef` PACKAGE (ID số), BRANCH (ID số), WORKSHOP_DATE (`YYYY-MM-DD`), SESSION (`workshop_sessions.reference` UUID) và trả `TargetFact` exists/softDeleted. Khi chưa có provider, Manager tạo voucher workshop nhận `DEPENDENCY_UNAVAILABLE`; preview/accept booking không cần provider.

## 9. Kiểm chứng

- 006: `LoyaltyWorkshopQuoteTest` (tính toán, mọi phiên phải khớp, validate session fact) và `LoyaltyWorkshopFacadeIT` (luồng mẫu 2 phiên, voucher chỉ phủ một phiên bị từ chối, đổi phiên → `QUOTE_CHANGED`, rollback chung transaction, settle cọc không earn, earn một lần).
- 003.1 cần thêm test của owner với luồng accept thật (T057 `LoyaltyWorkshopSourceIntegrationIT`): rollback hai chiều giữa giữ chỗ và loyalty, cọc lẻ `ceil`, callback trễ/lặp dưới guard nguồn.
- Lưu ý code hiện có: `BookingLifecycleService` đang đổi ID số sang UUID bằng `new UUID(0L, id)`; khi gọi loyalty phải truyền đúng `workshop_sessions.reference` của phiên đã giữ, không phải giá trị sinh từ ID.
