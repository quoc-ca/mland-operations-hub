# Research: Loyalty & Promotion Management

**Date created**: 2026-10-24
**Date updated**: 2026-10-24
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Status**: Thiết kế đề xuất từ speckit-plan; nghiệp vụ và hai xác nhận bổ sung đã chốt, chưa có code FE-06.  
**Sources**: [spec](spec.md), [FE-06](../../research.md#fe-06), [constitution](../../.specify/memory/constitution.md), hai tác vụ nghiên cứu read-only về platform và tích hợp.

## D-01 — Stack và môi trường kiểm chứng

**Decision**: Giữ Java 21, Spring Boot 3.4.1, Spring Modulith 1.3.1, Firebase Admin 9.11.0, JPA, Flyway, Security/Validation theo [pom hiện có](../../../ppswbs_backend/pom.xml). Thymeleaf còn trong pom nhưng không dùng cho UI theo constitution 3.0.0. MySQL 8.4/InnoDB là baseline kiểm chứng như plan 001/002; H2 chỉ bổ trợ. Không thêm Redis, broker, dịch vụ loyalty hoặc nhà cung cấp mới.

**Rationale**: Constitution đã chốt modular monolith; nghiệp vụ giữ điểm/lượt cần transaction cùng DB với đơn/booking. application.properties hiện mặc định H2, không phải bằng chứng MySQL compatibility.

**Alternatives considered**: Microservice/saga hoặc cache làm nguồn số dư tạo ranh giới và partial failure không cần thiết. Không nâng dependency theo phiên bản docs mới.

## D-02 — Ownership và hướng phụ thuộc

**Decision**: Module mới `loyaltypromotion` sở hữu voucher/ưu đãi, policy, wallet, ledger, snapshots/receipts. Chỉ `loyaltypromotion::facade` xuất interface/immutable records. `readyringsales` và `workshopbooking` gọi facade; loyalty chỉ phụ thuộc `members::facade` và technical common, không import entity/repository nguồn. Source-specific target validation qua `PromotionTargetPort` thuộc loyaltypromotion facade, được productmanagement/workshop triển khai bằng adapter riêng; không inject source application service quay ngược loyalty.

**Rationale**: Toàn bộ `ppswbs_backend/src` đã bị xóa ở commit `c47fefe` (ho-quoc, 2026-10-24); productmanagement, readyringsales và facade identity đều chưa có code. Quyền loyalty và provider scope là dependency có thật phải bổ sung, không dùng entity xuyên module để né chúng.

**Alternatives considered**: Loyalty gọi lại booking/order để tự hiểu paid/cancelled dễ tạo cycle và owner thứ hai. Chuyển nghiệp vụ vào common vi phạm constitution. Identity SPI riêng chưa cần khi DAG loyaltypromotion → members và sources → loyaltypromotion giữ một chiều.

[Spring Modulith 1.3 verification](https://docs.spring.io/spring-modulith/reference/1.3/verification.html) mô tả kiểm tra cycle, truy cập nội bộ và allowed dependencies. Đây là cơ sở kiểm chứng thiết kế, không phải kết quả verify của code FE-06.

## D-03 — Reserve và accept nguyên tử

**Decision**: Owner source mở transaction, khóa/kiểm tra identity và nguồn hàng/lịch, dựng trusted checkout facts, gọi `reserveAcceptedTransaction` với propagation MANDATORY, rồi ghi đơn/booking/invoice/holds trong cùng transaction/transaction manager. Kết quả khác báo giá đã xác nhận phải rollback và trả yêu cầu xác nhận lại. Không REQUIRES_NEW/HTTP nội bộ cho reserve; không gọi gateway dưới khóa.

**Rationale**: Không có cửa sổ tạo booking fail nhưng giữ điểm thành công. Source facts không lấy giá/tổng tiền/memberId từ browser. Source refs được cấp trước insert nhưng chỉ công khai sau commit.

**Alternatives considered**: Browser gọi giữ điểm trước rồi tạo đơn cần compensation không cần thiết. Event in-memory sau commit không bảo đảm quyền lợi và source được ghi đồng bộ.

[Spring Framework 6.2 transaction propagation](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-propagation.html) giải thích transaction tham gia và transaction mới độc lập. Chọn MANDATORY là quyết định thiết kế của feature để buộc tồn tại transaction nguồn, không phải đề xuất thay Spring version.

## D-04 — Concurrency, edits và thứ tự khóa

**Decision**: JPA pessimistic locks cho quyền lợi; version kiểm tra sửa cũ là lớp riêng. Thứ tự: identity guard → source business guards theo owner → singleton loyalty registry gate → programs tăng ID → wallet Member → member-quota rows tăng program ID → benefit/receipts. Không gọi ngược source để lấy thêm khóa sau registry.

Registry dùng PESSIMISTIC_READ cho accept; Manager tạo/sửa/dừng chương trình và đổi policy dùng PESSIMISTIC_WRITE. Nó ổn định tập chương trình/policy khi chọn giảm tự động, tránh vừa tính vừa thêm chương trình. Program/quota và wallet dùng PESSIMISTIC_WRITE. Lượt giữ chưa có được tạo dưới khóa program; wallet chưa có tạo dưới identity Member guard. Không dùng exists-then-save như bảo đảm chống trùng.

**Rationale**: Khóa registry chia sẻ không serial hóa mọi checkout; tranh quota cùng chương trình/wallet vẫn được khóa riêng. Quota giảm kiểm tra committed current state của tất cả Member bị ảnh hưởng. Không giữ khóa qua remote calls. Timeout/deadlock rollback toàn transaction và trả retryable conflict, retry với cùng key.

**Alternatives considered**: Chỉ @Version hoặc chỉ SUM không giữ khóa không đủ cho tranh lượt cuối. Global exclusive lock mọi checkout đơn giản nhưng gây nghẽn không cần thiết.

[JPA locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html) hỗ trợ khai báo lock mode; [MySQL 8.4 locking reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html) giải thích đọc khóa cho cập nhật counter. Cơ chế được tham khảo từ primary docs; thứ tự khóa trên là thiết kế riêng cần test trên MySQL thật.

## D-05 — Chính xác số và snapshot policy

**Decision**: Tiền/điểm/quantity không dùng float. Money/points `DECIMAL(19,0)`, Java BigInteger; percentage `DECIMAL(9,6)` theo phần trăm, BigDecimal. ID/quota/version là signed BIGINT dương hoặc không âm theo trường. JSON decimal strings cho money/points/ID/quota/version và percentage string. Bounds/overflow được kiểm tra trước ghi; chuyển sang Long tại booking adapter phải exact, không cắt số.

Bộ khởi đầu: earnUnit=10000, redeemValue=100, minRedeem=10, maxRedeemPercent=10. Mỗi policy/program revision immutable. Accepted transaction giữ revision, gross/net, cơ sở đủ điều kiện, khoản giảm, số điểm dùng/quy đổi và earn basis. Mọi phép tính sau accept dùng snapshot, không đọc current policy để tính lại.

**Rationale**: DECIMAL/BigInteger và decimal strings đồng nhất hướng catalogue 002, tránh JavaScript precision loss và overflow khi nhân phần trăm. Ngưỡng voucher xét phần eligible trước giảm; trần điểm xét toàn tiền hàng/phí gói sau một ưu đãi, trước điểm.

**Alternatives considered**: long-only cần kiểm soát intermediate arithmetic vẫn có thể overflow; float/double sai tại biên đồng/điểm.

## D-06 — Lifecycle có owner và retry an toàn

**Decision**: Source phân xử lifecycle trước rồi gọi settle/release/restore/earn trong cùng transaction qua facade. Retail full-paid/zero accepted chốt rồi grant; workshop deposit chỉ chốt, completed AND fully-paid mới grant. FE-03 hủy hợp lệ mới restore; loyalty không tự giải phóng theo đồng hồ.

Benefit có trạng thái points/voucher riêng (NONE/HELD/CONSUMED/RELEASED/RESTORED), earn riêng (PENDING/GRANTED/INELIGIBLE). Receipt unique theo source/action/component hoặc actor/action/commandId cùng digest. Same key/same intent trả kết quả cũ sau auth; khác digest 409. Wallet/counters, ledger, receipt và source outcome commit chung; rollback không báo success. Wallet không âm và available=balance-held.

**Rationale**: Trả lượng điểm đã trừ không phụ thuộc quy đổi mới; trả quota cả tổng/Member không kích hoạt mã hết hạn. Component states cho retry hoàn tất phần thiếu, không trả trùng.

**Alternatives considered**: Loyalty scheduler tự đoán payment failed khi quá giờ hoặc browser tự báo paid/completed bị loại. Callback receipts hiện có của workshop không tự trở thành loyalty receipts.

## D-07 — Auth dùng chung, được xác nhận cho FE-06

**Decision confirmed by user 2026-10-24**: Kế thừa FE-01/001 và hướng FE-02: mọi REST API (kể cả frontend React) dùng bearer đã xác minh; phiên theo contract FE-01/001; revocation/trạng thái/quyền hiện tại; V1 không yêu cầu MFA/privileged assurance (theo 001, làm rõ 2026-10-27). Không tạo login/MFA mới, không lấy ROLE_MEMBER/Firebase claim/email làm quyền Manager/Admin.

**Rationale**: Cùng cơ chế cho Member/Manager mà không chia danh tính theo feature; Admin không có quyền nghiệp vụ FE-06. MemberFacade/cookie/capability trong 001/002 là thiết kế phụ thuộc, chưa có code.

**Alternatives considered**: Dùng global permitAll/CSRF disabled sẽ bỏ qua ranh giới đã chốt. Tự chọn phương thức MFA từ loyalty vượt phạm vi identity.

## D-08 — Retention được xác nhận cho FE-06

**Decision confirmed by user 2026-10-24**: History điểm/lượt, audit và operation results tối thiểu 5 năm; không dọn khi còn được tham chiếu hoặc source chưa terminal. Mốc tối thiểu cho record gắn giao dịch là sau thời điểm muộn hơn giữa phát sinh record và source terminal; record quản trị tính từ commit.

Không implement destructive purge trong feature v1. Thiết kế giữ evidence/tombstone mã đã cấp và source/action keys đủ lâu để không reuse/double-effect; không xóa số dư/held balances. Khi xây cleanup sau này phải kiểm tra retention + references + terminal, có governance review riêng; không mặc định xóa hết sau 5 năm.

**Rationale**: Nonexpiring points và nonreusable codes đòi bảo toàn bằng chứng gốc, không chỉ request payload. Audit/receipts chỉ giữ safe refs/digest/counts/amounts/status, không raw token/full payload/PII thừa.

**Alternatives considered**: Tự kế thừa 5 năm của catalogue khi chưa hỏi đã bị loại; đã có câu trả lời riêng cho FE-06. Xóa toàn bộ receipts theo tuổi có thể mở replay nên không dùng.

## D-09 — Giao diện và contracts

**Decision**: Frontend React + Vite tách riêng gọi REST với `Idempotency-Key`, expectedVersion và hiển thị typed error. Public ưu đãi, kho/My Loyalty, Manager programs và Manager policy có API versioned riêng trong [OpenAPI](contracts/loyalty-promotion.openapi.json). Quote/accept/payment/cancel thuộc owner FE-03/FE-08, không thêm endpoint public để reserve/settle/earn/restore.

**Clarification confirmed 2026-10-24**: Manager được mở lại chương trình PAUSED theo lựa chọn A; giữ cùng program/code, quota/history và accepted snapshots, không tự gia hạn/reset lượt. Resume dùng expectedVersion, command receipt, cùng quyền và registry WRITE lock như pause; giao dịch mới vẫn kiểm tra điều kiện hiện hành.

**Reporting clarification confirmed 2026-10-24**: Người dùng chọn A: loại booking workshop đã ghi nhận hủy hợp lệ khỏi lượt đã dùng/tổng tiền giảm hiện tại; giữ lịch sử dùng/hoàn. Report dùng current consumed benefits và tiền giảm chương trình tương ứng, không đếm preview/held hoặc trừ lại khi nhận kết quả hủy lặp. Chương trình hết hạn/dừng không loại các giao dịch đã chốt chưa bị hủy khỏi số liệu.

**Rationale**: Chỉ source owner có trusted prices và payment/lifecycle evidence. Contract tách public cards khỏi management data; không lộ audit/member quotas của người khác.

**Alternatives considered**: Thymeleaf/htmx server-rendered forms bị loại vì constitution 3.0.0 yêu cầu frontend React + Vite tách riêng. Public grant/refund endpoint mở đường client tự tạo quyền lợi.

## D-10 — Gaps hiện có và điều kiện tích hợp

**Decision**: Hoàn thành thiết kế loyalty nhưng fail closed khi dependency chưa có. [Integration gates](integration-gates.md) ghi owner và điều kiện đạt, không coi fixture là provider thật.

**Evidence**:

- Toàn bộ `ppswbs_backend/src` đã bị xóa ở commit `c47fefe` (ho-quoc, 2026-10-24): chưa có security configuration, facade identity, code workshop booking hay migration nào. Routes loyaltypromotion cần security chain riêng (bearer, CORS theo origin frontend); facade identity do FE-01 tạo; lifecycle booking/cọc do FE-03 thiết kế lại ([spec 003](../003-workshop-management/003.1-workshop-booking/spec.md) hoãn hậu xử lí).
- [FE-02 plan](../002-product-management/plan.md) là thiết kế catalogue/availability, chưa có provider thật. Retail (FE-08) đang ở giai đoạn thiết kế ([008 plan](../008-retail-order-and-fulfillment/plan.md)).
- Hiện chưa có migration nào; FE-06 thêm migration mới và allocate version tại implementation để không đụng feature khác.

**Rationale**: Các gap là prerequisite triển khai/tích hợp, không phải câu hỏi nghiệp vụ cần người dùng chốt lại. Plan không tự sửa code nguồn/feature 003/002 hoặc nhận permission deploy.

**Alternatives considered**: Stub production trả 0 held/allowAll/fullyPaid giả làm tính năng có vẻ chạy nhưng vi phạm invariants.

## Kết luận nghiên cứu

Không còn câu hỏi nghiệp vụ mới chờ người dùng trong FE-06 sau xác nhận auth/retention. Các quyết định kỹ thuật D-01–D-10 là bản thiết kế để review; dependency implementation/gates vẫn mở và phải được chứng minh trước bật hành trình tương ứng. Chưa chạy runtime tests, migration hoặc Modulith verification vì chỉ làm tài liệu.

