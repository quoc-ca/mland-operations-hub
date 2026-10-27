# Data Model: Loyalty & Promotion

**Date created**: 2026-10-24
**Date updated**: 2026-10-26
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Status**: Approved — duyệt bởi Đặng Thị Khương ngày 2026-10-24; thiết kế dữ liệu là baseline, chưa tạo entity hoặc migration.  
**Traceability**: [FR-003–FR-010, FR-023–FR-038, FR-041](spec.md), [research D-03–D-08](research.md).

## 1. Ownership và kiểu dữ liệu

Module `loyaltypromotion` sở hữu tất cả bảng `loyalty_*`/`promotion_*` dưới đây. Không map JPA association tới entity Member/product/order/booking; các ID nguồn là logical references được facade owner kiểm tra. FK chỉ giữa bảng loyalty. Không có bảng hạng thành viên, inventory hoặc payment.

- ID, revision, counters: BIGINT signed; ID/revision dương, counters không âm, kiểm tra overflow.
- Money/points: DECIMAL(19,0), Java BigInteger; 0..9.999.999.999.999.999.999, điểm ledger delta cho phép âm trong giới hạn đối xứng. Intermediate arithmetic dùng exact numbers và kiểm tra bounds trước persist.
- Discount/redeem percentages: DECIMAL(9,6), 0 < value <= 100; input decimal string, tối đa 6 chữ số thập phân, không tự làm tròn input.
- UTC Instant trong DB với độ chính xác microsecond; UI hiển thị Asia/Ho_Chi_Minh. Workshop local date/session do FE-03 cung cấp; không suy ngày từ timezone trình duyệt.
- Khoảng hiệu lực theo Instant là [startAt, endAt); startAt < endAt. Ngày workshop là tập/range local-date của owner.
- JSON/HTML truyền money/points/ID/quota/version bằng decimal string; không dùng float/JavaScript Number cho phép tính nghiệp vụ.
- Giới hạn biểu mẫu đề xuất: tên chương trình 1–200 ký tự sau trim, mã 1–128 ký tự sau trim, không control characters; mã so sánh qua uppercase Locale.ROOT, không bỏ dấu/không thêm chuẩn hóa ngoài trim/case. Phân trang mặc định 20, tối đa 100; đây là giới hạn thiết kế để review, không đổi mức giảm/ngưỡng thương mại.

## 2. Bảng/aggregate dự kiến

| Entity/table | Fields chính | Ràng buộc và vai trò |
| --- | --- | --- |
| LoyaltyRegistry / loyalty_registry | singleton id=1, currentPolicyId, version | Gate cho current policy và tập chương trình. Migrations khởi tạo đúng một row. READ lock khi accept, WRITE khi thay cấu hình/chương trình. |
| PromotionProgram / promotion_program | id, kind VOUCHER/AUTO_DISCOUNT, scope PRODUCT/WORKSHOP, codeOriginal/codeKey nullable cho auto, status ENABLED/PAUSED, currentRevisionId, totalLimit/memberLimit/heldCount/consumedCount (chỉ VOUCHER, NULL cho auto), createdAt/by, version | Unique codeKey binary toàn bộ lịch sử; auto chỉ PRODUCT. Không đổi kind/scope/code sau tạo. Hết hạn là trạng thái suy ra theo thời gian, không tái dùng code. Quota gộp vào bảng này (quan hệ 1-1): held+consumed <= totalLimit; limits ở current configuration, snapshot giữ điều kiện lần accept; update hạn mức không giảm dưới current held+consumed; counter chỉ đổi dưới program lock. |
| PromotionRevision / promotion_revision | id, programId, revisionNo, name, startAt/endAt, discountKind PERCENT/FIXED, percent/fixedAmount, maxDiscountAmount, minimumEligibleAmount, allowPoints, createdAt/by | Unique(programId,revisionNo); immutable. Percent hoặc fixed đúng một loại, trần nguyên dương, ngưỡng không âm. AUTO_DISCOUNT luôn allowPoints=true (chốt 2026-10-26); chỉ VOUCHER được đặt false. Booking min là phí gói, retail min là eligible subtotal trước giảm. |
| PromotionTarget / promotion_revision_target | revisionId, dimension PRODUCT/VARIANT/PACKAGE/BRANCH/WORKSHOP_DATE/SESSION, targetKey | Unique(revisionId,dimension,targetKey). Không BRANCH cho PRODUCT; PACKAGE/... chỉ workshop. Tập ngày/phạm vi kết hợp theo contract owner, không coi OR giữa các dimensions. PRODUCT/VARIANT phải tồn tại, variant thuộc product và chưa xóa mềm lúc lưu revision; chưa công khai/đang ẩn/ngừng bán vẫn hợp lệ. Target bị ẩn/xóa sau đó vẫn giữ trong revision (không đổi phạm vi), chỉ không đủ điều kiện lúc quote. |
| MemberProgram / promotion_member_program | programId, memberId, savedAt nullable, heldCount, consumedCount, version | Gộp quota theo Member và voucher đã lưu. Unique(programId,memberId); program phải VOUCHER; held+consumed <= current memberLimit. Row quota tạo dưới program lock; lưu voucher chỉ ghi savedAt (count = 0), không giữ quota, không là entitlement; lưu lặp không đổi gì. Trả voucher giảm consumed, không xóa lịch sử dùng gốc. |
| LoyaltyPolicy / loyalty_policy | id, revisionNo, earnUnitAmount, redeemPointValue, minRedeemPoints, maxRedeemPercent, roundingMode FLOOR, createdAt/by | Immutable; unit/value dương, min nguyên dương, percent (0,100]. Defaults 10000/100/10/10. Registry trỏ policy hiện hành. |
| LoyaltyWallet / loyalty_wallet | memberId PK, balancePoints, heldPoints, version | 0 <= heldPoints <= balancePoints. available tính balance-held; không tách ví theo source, không expiresAt. |
| AcceptedBenefit / loyalty_accepted_benefit | id, sourceKind RETAIL/WORKSHOP, sourceRef, memberId, memberOriginEligible, acceptedAt, originalDeadline, sourceRevision, acceptedTermsDigest, policyId, programId/revisionId nullable, snapshotAmounts (workshop: postPromotionAmount dương; retail không có; cọc và còn lại thuộc FE-03, không lưu ở đây), requestedPoints, pointDiscount, earnBase, pointsState, voucherState, earnState, settledAt/settlementOutcomeKey nullable, releaseOutcomeKey/restoreOutcomeKey/earnOutcomeKey nullable, sourceOutcomeVersion, voucherConsumedAt/voucherRestoredAt nullable, earnedPoints nullable, sourceTerminalAt | Unique(sourceKind,sourceRef). Accepted snapshots immutable; status/effect fields có version. Ghi record cả khi không dùng voucher/điểm để chốt policy và khả năng tích sau đó. settledAt/outcomeKey ghi một lần từ source proof kể cả khi các component đều NONE; không suy đã thanh toán từ việc không có component đang giữ. Guest history import không tự tạo record đủ điều kiện. |
| PointLedger / loyalty_point_ledger | id, memberId, acceptedBenefitId, entryKind, deltaBalance, deltaHeld, balanceAfter, heldAfter, occurredAt, originalDebitId nullable | Unique(acceptedBenefitId,entryKind) thay cho bảng effect riêng: chống cộng/trừ/hoàn điểm trùng. Append-only, delta != 0 ít nhất một thành phần; RESTORE trỏ debit gốc. Không lưu thông tin khách/thanh toán thừa. |
| LoyaltyAudit / loyalty_audit | id, actorRef/systemOwner, action, objectRef, before/after safe fields hoặc revision refs, correlationId, occurredAt, actorKey/commandId UUID/canonicalDigest/minimalResult (nullable, chỉ với lệnh có command key) | Gộp command receipt. Unique(actorKey,action,commandId) khi commandId không NULL: cùng digest replay result sau auth, khác digest 409. Ghi cùng transaction thay đổi; không chứa raw request/token/session/PII/secret. |

Chống double effect của voucher/earn dùng state có version và outcome key trên AcceptedBenefit (đã gồm settlement/release/restore/earn outcome key + sourceOutcomeVersion); cùng key khác evidence gây conflict. Điểm dùng unique ledger ở trên. Không còn bảng effect hay receipt riêng.

Tên trên là contract thiết kế dữ liệu, không phải migration SQL đã tạo. CodeKey unique dùng binary collation sau chuẩn hóa ứng dụng; không dựa collation accent-insensitive để vô tình đồng nhất mã khác dấu.

## 3. Phép tính

Gọi G là tiền hàng/phí gói trước ưu đãi, E là eligible subtotal, D là khoản giảm chương trình, V là giá trị mỗi điểm, P là điểm dùng và R là trần phần trăm policy. Shipping ở ngoài mọi cơ sở.

- Ngưỡng retail: E >= minimumEligibleAmount; E chỉ từ dòng thuộc phạm vi, quantity nhân giá nguồn đã xác minh. Workshop xét tổng phí gói theo context FE-03.
- PERCENT: D=min(E, maxDiscountAmount, floor(E × percent / 100)); FIXED: D=min(E, maxDiscountAmount, fixedAmount), một lần cho giao dịch. Với workshop dùng phí gói đủ điều kiện làm E; D không nhân số người.
- NetBeforePoints=G-D, không âm.
- MaxPoints=min(wallet.available, floor(NetBeforePoints × R / (100 × V))).
- P=0 hoặc minRedeemPoints <= P <= MaxPoints, P nguyên. Không được kết hợp thì P=0.
- PointDiscount=P×V; EarnBase=NetBeforePoints-PointDiscount.
- Retail total=EarnBase+shipping. Workshop netFee=EarnBase, phải >0, post_promotion_amount=netFee; owner FE-03/003.1 tính deposit=ceil(netFee/2), remaining=netFee-deposit; FE-06 chỉ snapshot `post_promotion_amount` lúc accept, không tính, không làm tròn và không lưu cọc/còn lại.
- EarnedPoints=floor(EarnBase / policy.earnUnitAmount), một lần/giao dịch đủ điều kiện; remainder không lưu sang lần sau.
- Không promotion vẫn dùng G, D=0 và current policy để chọn điểm. Không dùng điểm vẫn ghi policy khi accept để tích đúng tỷ lệ lúc đó.
- Không tìm phương án tự động theo tổng lợi ích cả điểm: chọn AUTO_DISCOUNT theo D lớn nhất, bằng D theo createdAt rồi ID tăng; sau đó khách quyết định điểm hoặc thay bằng voucher.

## 4. State transitions

### Program/policy

- Program tạo ENABLED, nhưng chỉ hiệu lực nếu startAt <= now < endAt; tạo tương lai không áp dụng ngay.
- Manager có thể cập nhật revision, chuyển ENABLED→PAUSED hoặc mở lại PAUSED→ENABLED. Mở lại cùng program/code với expectedVersion và command receipt; không reset quota/history, không gia hạn thời gian hoặc sửa accepted snapshots. Chương trình sau mở lại chỉ áp dụng mới nếu đạt điều kiện thời gian/phạm vi/hạn mức. Không có delete/reuse code hoặc đổi kind/scope.
- Policy tạo revision mới và đổi current pointer nguyên tử. AcceptedBenefit vẫn trỏ revision cũ.
- Quota mutable counters không nằm trong immutable revision; điều kiện quota tại lúc accept được snapshot nhưng bảo toàn phần giữ đã chấp nhận.

### Points/voucher components

| Current | Trigger đã được owner xác nhận | Next | Effect |
| --- | --- | --- | --- |
| NONE | Giao dịch không dùng component đó | NONE | Không tạo khoản hoàn/giữ giả. |
| Chưa có AcceptedBenefit | Accept với P>0/voucher | HELD | heldPoints += P; held quota +=1. |
| HELD | Retail paid/zero accepted hoặc workshop deposit accepted | CONSUMED | Points: balance -=P, held -=P; quota: held -=1, consumed +=1. |
| HELD | Source xác nhận hết hạn chưa paid hoặc valid workshop cancel | RELEASED | heldPoints -=P hoặc held quota -=1; balance không tăng. |
| CONSUMED | Valid workshop cancel trước completion | RESTORED | Points: balance +=P gốc; quota: consumed -=1 tổng/Member; không gia hạn mã. |
| CONSUMED/RELEASED/RESTORED | Cùng kết quả hợp lệ gửi lặp | Giữ nguyên | Replay receipt, không thêm delta. |

Không cho RELEASED/RESTORED quay lại HELD/CONSUMED trên cùng sourceRef. Cancel vs payment/expiry phải được source phân xử và cung cấp terminal/version hợp lệ; state conflict không tự chạy compensation.

### Earn

- PENDING → GRANTED khi retail paid/accepted zero hoặc workshop completed AND fully-paid; 0 điểm vẫn ghi receipt GRANTED với amount=0, không tạo ledger giả không thay đổi số dư.
- PENDING → INELIGIBLE khi source xác nhận cancel/expiry/no-show kết thúc mà chưa đủ điều kiện.
- GRANTED không được grant lần nữa hoặc thu hồi trong scope v1.
- Chốt/restore component không tự cấp earn. Workshop cọc chỉ consume, không grant.
- Trước grant, mọi component được dùng phải CONSUMED hoặc NONE; không grant trên earnBase đã trừ điểm nếu điểm vẫn HELD/chưa được debit. Owner paid delivery chạy settle rồi grant cùng transaction, versioned theo source outcome.

## 5. Ledger và invariants

- HOLD: deltaBalance=0, deltaHeld=+P.
- CONSUME: deltaBalance=-P, deltaHeld=-P.
- RELEASE: deltaBalance=0, deltaHeld=-P.
- RESTORE: deltaBalance=+P, deltaHeld=0, liên kết debit ban đầu.
- EARN: deltaBalance=+earned, deltaHeld=0.
- Wallet state, quota counters, component state/outcome key, ledger, audit (kèm command receipt) và source outcome commit trong một transaction khi effect đồng bộ; rollback không lộ partial success.
- Đọc My Loyalty trong consistent read snapshot; không ráp balance mới với held cũ từ nhiều reads độc lập.
- Index: wallet(member), ledger(member,occurredAt,id), benefit(sourceKind,sourceRef), benefit(member,acceptedAt,id), programs(scope,status,start/end), targets(dimension,targetKey,revisionId), memberProgram(programId,memberId), memberProgram(memberId,savedAt), audit(unique actorKey,action,commandId khi có commandId).
- Report voucher usage lấy AcceptedBenefit có voucherState=CONSUMED, loại booking đã ghi nhận hủy hợp lệ và hoàn lượt khỏi số tổng hiện tại. Giảm tự động không có voucher component thì lấy settlement fact đã xác minh (settledAt/outcomeKey), không dựa vào voucherState hoặc việc có dùng điểm. consumedCount và tổng tiền giảm chương trình không tính preview/held hoặc booking bị hủy; không gộp giảm bằng điểm vào khoản giảm riêng của chương trình. Lịch sử consume/restore bất biến vẫn được giữ để tra cứu. Kết quả hủy lặp không làm giảm số tổng lần nữa; thời hạn/trạng thái hiện tại của chương trình không loại các giao dịch đã chốt chưa bị hủy khỏi báo cáo.

## 6. Locks, uniqueness và transaction

Đọc [interfaces](contracts/interfaces.md#transaction-and-lock-order). Registry shared lock ổn định chương trình/policy trước program/wallet locks; writer sửa cấu hình dùng exclusive gate. Không khóa wallet rồi đi lấy source/capability lock. Unique constraints là lớp cuối cho insert race; nhận duplicate phải rollback transaction lỗi rồi đọc receipt trong transaction mới, không tiếp tục cùng persistence context đã rollback-only.

Tạo wallet đầu tiên dưới Member creation/authorization guard do identity owner cung cấp; settlement dùng wallet đã có và không đòi Member vẫn đang active để nuốt quyền lợi đã chấp nhận. Không tự tạo active entitlement cho callback.

## 7. Migration và retention

- Append-only Flyway migration mới, cấp số sau kiểm tra directory lúc implementation; không sửa migration đã áp dụng, không DROP/reset/backfill giả điểm cho giao dịch cũ.
- Khởi tạo registry/policy một lần trong migration; wallet tạo lazy cho Member khi accept. Member chưa có wallet đọc số dư 0, không cần write ở GET.
- FK nội module, unique keys và CHECK invariants phải kiểm chứng trên MySQL 8.4 trống và upgrade trên schema có dữ liệu của các feature khác lúc triển khai; không chỉ H2.
- Tối thiểu 5 năm từ mốc muộn hơn của record event/commit và source terminal nếu có. Chưa terminal hoặc còn references thì không eligible cleanup. Current wallet/policy, code registry và minimal source/action uniqueness evidence không bị xóa vì tuổi.
- V1 không có purge/delete history command. Future cleanup phải giữ tombstones/dedup keys và lịch sử cần cho nonexpiring points; cần review riêng trước thao tác phá hủy.
- Recovery disable route/consumer mới, giữ schema/ledger/receipts; không down-migrate hoặc sửa trực tiếp balance để “khớp” sự cố.

