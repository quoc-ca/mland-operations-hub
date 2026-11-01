# Integration Gates — FE-06

**Date created**: 2026-10-24
**Date updated**: 2026-11-01 (đóng T072–T076/T078 với xác minh token giả lập — verification mục 31; hiện trạng G-01–G-07 2026-11-01 (chuẩn bị T075); nguồn workshop FE-03 và CORS — verification mục 28; T077 đạt — dùng nền frontend của 002; trước đó: 2026-10-27 (T002: cập nhật hiện trạng G-01–G-07 theo `khuong` và `origin/develop` `37d68cf`; T077: frontend chưa có))
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Kết luận**: CÔNG VIỆC TÀI LIỆU hoàn thành cho plan; chưa đủ điều kiện triển khai/bật toàn feature. Không có câu hỏi nghiệp vụ FE-06 còn chờ trả lời. Gates dưới đây là dependency thiết kế/implementation cần giao đúng owner, không phải bằng chứng quyền code/deploy.

| Gate | Evidence hiện có | Owner/việc còn phải có | Điều kiện đạt |
| --- | --- | --- | --- |
| G-01 Identity/capability | Backend đã bị xóa ở commit `c47fefe` (ho-quoc, 2026-10-24): chưa có facade identity hay security configuration; 001/002 có shared-auth design | FE-01/identity cung cấp verified bearer và phiên theo contract FE-01/001, revocation/state, current Member/Manager capabilities (Admin không có capability FE-06) và authorization guard (V1 không MFA theo 001, chốt 2026-10-27) | Contract/API/web checks với identity thật, deny thiếu quyền, expiry/revocation và existing Member journeys không regression. 001 giao contract và facade xác thực cho frontend React. Không chọn MFA method từ loyalty. |
| G-02 Catalogue targets/source sales | **Phần target (2026-10-26)**: `LoyaltyProductTargetAdapter` của productmanagement đã triển khai `PromotionTargetPort` cho scope PRODUCT, có test MySQL `LoyaltyProductTargetAdapterIT` và Module verify pass (xem mục "Bàn giao G-02 từ FE-02" bên dưới). **Phần source sales**: readyringsales/FE-08 chưa có code, 008 đang thiết kế | FE-02 cung cấp product/variant facts và guard qua `LoyaltyProductTargetAdapter` (T091 của feature 002, triển khai PromotionTargetPort sau T004 của FE-06); FE-08 sở hữu price/quantity source snapshot và accepted order | Không raw repository access/fake provider; create/quote/accept scope và source guard races qua provider thật, Module verify pass. |
| G-03 Retail owner | FE-08 chưa có order/payment runtime | FE-08 tích hợp preview/accept reserve atomic, full-paid/zero outcome, original deadline, expiry/arbitration và source delivery receipts nếu cần bridge | Rollback cả hai chiều, zero không gateway, callback duplicate/late, expired/unresolved không release sai, snapshot tiền/points không đổi. |
| G-04 Booking acceptance/deposit | Code workshopbooking cũ đã bị xóa ở commit `c47fefe` (ho-quoc, 2026-10-24); FE-03 thiết kế lại booking/cọc, chưa có owner lock contract đủ | FE-03 thêm selection/quote acknowledgement, net fee trước cọc, snapshot/boundary, lifecycle source lock | Invoice/cọc/remain đúng after-voucher/points, deposit settle một lần, không grant sớm; atomic creation/capacity/loyalty rollback và payment-vs-expiry test thật. |
| G-05 Workshop hậu xử lí | Spec 003.1 hoãn completion/cancel/no-show/final-payment; runtime chưa cấp authoritative outcomes đủ FE06 | FE-03 cung cấp xác nhận completed AND fully-paid, valid paid-cancellation trước completion, no-show final và phân xử late/conflicting outcomes | Chứng minh both conditions, policy cũ, valid cancellation restore đúng, no-show không auto restore; Guest import không retro-earn; source và loyalty history đồng bộ. |
| G-06 Data/module verification | Loyalty chưa có production/test/migration; hiện chưa có migration nào (backend đã bị xóa) | FE-06 implementation sau spec/plan/tasks review và analyze; migration number mới, MySQL8.4 upgrade, locks/receipts/audit | Empty + upgrade không mất data, invariants/concurrency và ApplicationModules.verify(); không H2-only evidence. |
| G-07 Governance before implementation | Nghiệp vụ/retention/shared-auth đã có xác nhận; plan/contracts đã Approved ngày 2026-10-24 | Review technical design, tạo tasks theo dependency, chạy speckit-analyze; yêu cầu triển khai riêng | Không unknown ảnh hưởng task, thiết kế public contract/locking/data được review, task dependencies đủ bằng chứng. Không dùng checklist spec thay phê duyệt plan. |

## Hiện trạng gate và handoff (T002, 2026-10-27)

Nguồn: nhánh `khuong` (`73ac306` cộng thay đổi chưa commit của 006) và `origin/develop` (`37d68cf`, chưa ghép về `khuong`). Bảng trên giữ nội dung lúc lập plan; bảng này là hiện trạng mới. Không gate nào được đóng ở lượt này.

| Gate | Hiện trạng (fact) | Owner, việc còn thiếu | Trạng thái |
| --- | --- | --- | --- |
| G-01 Identity | `AccountFacade.resolveActor`, `CurrentAccountActor`, `ActorContext` của 001 đã có trên `khuong`; 006 dùng chain theo route `LoyaltySecurityConfiguration` và `LoyaltyAuthorization` (T007). `LoyaltySecurityIT` 9 đạt, 1 bỏ qua do preflight CORS 403 (BE-DESIGN-0012). | Owner 001: xác nhận bằng văn bản mục 1, 2 của BE-DESIGN-0005; owner 001 (và 003.2 nếu do `workshopOperationsCorsFilter`): sửa preflight CORS. | MỞ |
| G-02 Catalogue | Phần target PRODUCT đã bàn giao (mục dưới). Provider WORKSHOP đã có trên develop (`workshopoperations/service/LoyaltyWorkshopTargetAdapter`, Hồ Hoàng Quốc); `PromotionCreateContractIT` kiểm chứng target gói/phiên không tồn tại bị từ chối (2026-10-30). | FE-02 xong phần target; owner 003.2 (`workshopoperations`, chủ package theo BE-DESIGN-0008/V21 và session) cung cấp provider target WORKSHOP (gói/chi nhánh/phiên; SESSION key = `workshop_sessions.reference` UUID); FE-08 cung cấp giá/số lượng nguồn và serialize xóa sản phẩm với accept. | MỞ |
| G-03 Retail (FE-08) | Trên `origin/develop` có module `readyringsales` (`5748a08`, Bùi Trọng Thịnh). Port `RetailBenefitPort` chỉ có `preview` và `requireValidPreview`; bean duy nhất là `LocalRetailBenefitAdapter` (`@Profile("local")`, tự ghi là bản thay thế cục bộ, không đọc hay ghi sổ điểm thật). Chưa có lời gọi `loyaltypromotion::facade`. Plan, research (Decision 15) và data-model của 008 đã thống nhất gọi `previewBenefits`, `reserveAcceptedTransaction`, `settleAcceptedBenefits` + `grantEarnedPoints`, `releaseHeldBenefits` + `finishIneligibleEarn` theo T054/T064. | Owner FE-08 (Bùi Trọng Thịnh) viết `LoyaltyCheckoutAdapter`, `LoyaltyOutcomeAdapter` trong `readyringsales` thay bản local; 006 review và cung cấp test tích hợp (T054, T064). Cần chốt với owner: port hiện dùng `previewId` lưu trong bộ nhớ và `BigDecimal`, còn facade 006 dùng `termsDigest` khách đã xác nhận và `BigInteger` VND. **2026-10-31:** T054 tick — FE-08 nối đủ thanh toán (cổng còn mô phỏng), đơn 0đ, hủy trước thanh toán, hết hạn đã xác nhận (`e061e90`); xem trước trả `reasons`. G-03 vẫn MỞ cho tới khi có cổng thanh toán thật và phần nguồn thật của T062–T065. **2026-10-30 (kiểm chứng):** accept thật của FE-08 giữ voucher/điểm cùng transaction với giữ hàng, rollback cả hai khi loyalty từ chối, cùng key không giữ thêm (`LoyaltyRetailCheckoutIT`, verification.md mục 24). Còn thiếu: kết quả đơn, đơn 0đ, 3 điểm review. **2026-10-30:** FE-08 báo đã gọi `previewBenefits` (SourceLine, ship riêng, lưu quoteRef/termsDigest) và `reserveAcceptedTransaction` trong service nội bộ, chưa nối API tạo đơn; 006 thêm `RETAIL_CANCELLED_BEFORE_PAYMENT` cho Member hủy đơn trước thanh toán (release + finishIneligibleEarn). | MỞ |
| G-04 Booking (FE-03) | Trên `origin/develop` có nền `workshopbooking` mới (`8dfc3b7`, `3821937`, ho-quoc): `WorkshopBookingFacade` gồm projection tài chính cho 003.3, ghi nhận cọc đã xác minh, final balance, gửi lại vé, payload thông báo. Chưa có luồng accept booking, chưa có chỗ gọi preview/reserve của 006. Develop không biên dịch sạch (BE-DESIGN-0011). | Owner 003.1 (ho-quoc) chọn **Cách 2** (2026-10-29): `workshopbooking` gọi thẳng `LoyaltyFacade`. Booking tạo/lưu quote (`quoteRef`, termsDigest, các phiên), trong một transaction accept: giữ chỗ 003.2 cho mọi phiên rồi `reserveAcceptedTransaction`, lấy `postPromotionAmount` tính cọc; settle khi cọc đã xác minh (T059). 006 đã đổi `WorkshopFact` sang nhiều phiên/UUID và cung cấp [hướng dẫn](contracts/workshop-booking-integration.md) + `LoyaltyWorkshopFacadeIT`. | MỞ |
| G-05 Workshop hậu xử lý | Chưa có outcome hoàn thành + trả đủ, hủy hợp lệ, không đến trên develop. | Owner FE-03 cung cấp outcome có version và tự gọi `settleAcceptedBenefits`/`grantEarnedPoints`/`releaseHeldBenefits`/`restoreCancelledWorkshopBenefits`/`finishIneligibleEarn` qua facade (Cách 2); 006 review và chạy kiểm chứng (T060, T064). | MỞ |
| G-06 Data/module | V10, V11, V12 trên develop; 002 nay là V15 (đổi từ V9 ở `e143b52`), không còn migration trùng số. Toàn bộ test 006, 002 và `ModulithArchitectureTests` đạt trên **build thật** develop `2774c1e` (verification.md mục 20). | Không còn việc mở về migration; chạy lại khi có nguồn thật FE-03/FE-08. | ĐÃ ĐẠT (dữ liệu/module) |
| G-07 Governance | Tài liệu Approved; T001–T003 ghi tại verification.md mục 1, 3, 4 và 18. | Không còn việc của 006. | ĐẠT cho phần đã triển khai |

**Thứ tự adapter sau facade:** (1) G-01 xong CORS; (2) FE-08 thay `LocalRetailBenefitAdapter` bằng adapter qua facade, chạy `LoyaltyRetailSourceIntegrationIT` với nguồn thật; (3) FE-03 thêm accept booking và hook cọc; (4) FE-03 thêm outcome hậu xử lý; (5) 006 chạy T057, T062, T065 và phần nguồn thật của T063. 006 không tự viết adapter thay owner.

**T077 — nền frontend dùng chung (2026-11-01): ĐẠT.** 002 T088/T089 đã dựng nền React + Vite (version ghim, `npm run build`, `npm test`, router, i18n vi/en, REST client có bearer/Idempotency-Key/typed error/401/409/503); FE-06 dùng chung, không khởi tạo bản thứ hai (xem [verification](verification.md) mục 27). Tích hợp thật còn chờ đăng nhập 001 và CORS (T078).

**T077 — nền frontend dùng chung (2026-10-27, lịch sử): CHẶN.** `ppswbs_frontend/` trên `khuong` và `origin/develop` chỉ có `AGENT.md`; T088 và T089 của 002 còn `[ ]`. Chưa có React + Vite, REST client hay lệnh build/test. Các task UI T030, T034, T040, T047, T055, T061, T070 và T078 chờ T088/T089. 006 không khởi tạo frontend thứ hai.

## Không hỏi lại các quyết định đã có

- Voucher/points dùng chung hai dịch vụ, không hạng.
- 10.000đ→1 điểm, 1 điểm→100đ, tối thiểu10, trần10%, floor từng giao dịch.
- Quyết định 1–10 trong hội thoại đã chốt trước specify.
- Lịch sử/audit/receipts tối thiểu5 năm, bảo vệ refs/unfinished transactions.
- Dùng chung FE-01/001, session theo 001, current rights; V1 không MFA/privileged assurance (theo 001, chốt 2026-10-27).

## Handoff boundaries

- Tác vụ speckit-plan không sửa production/test code, migration, pom, auth config hoặc feature 003/002.
- Không production adapter trả allowAll/zero-held/fully-paid giả. Test double chỉ kiểm tra logic độc lập, không đóng G-01–G-05.
- Các gate có thể được đưa thành dependency-ordered tasks sau khi review; chưa tự chia scope triển khai hoặc bật một nửa feature từ lần lập plan này.
- Khi muốn triển khai, đánh giá readiness lại bằng [kit](../../agent-design-kit/readiness-checklist.md). Không tự mark ready chỉ vì bộ tài liệu đủ file.


## Bàn giao G-02 từ FE-02 (T091, 2026-10-26)

- **Provider**: `edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.LoyaltyProductTargetAdapter` (Spring bean, implement `PromotionTargetPort`). `supports(PRODUCT)` = true, `supports(WORKSHOP)` = false. productmanagement chỉ import `loyaltypromotion::facade` (khai báo trong `allowedDependencies` của productmanagement); loyaltypromotion không phụ thuộc ngược.
- **Khóa target**: ID sản phẩm (PRODUCT) hoặc ID size (VARIANT) dạng số thập phân dương, không dấu, không số 0 đầu. Khóa sai định dạng hoặc không tìm thấy → `exists=false`.
- **Fact trả về** (một fact cho mỗi target, đúng thứ tự yêu cầu):
  - `exists`: có trong catalogue (kể cả đã xóa mềm).
  - `belongsToParent`: PRODUCT luôn true; VARIANT true khi sản phẩm của nó nằm trong các target PRODUCT của cùng yêu cầu, hoặc khi yêu cầu không có target PRODUCT nào (vì các dimension kết hợp bằng AND).
  - `softDeleted`: sản phẩm (hoặc sản phẩm của size) đã xóa mềm.
  - `publicVisible`: đủ điều kiện catalogue công khai (đang công khai, chưa xóa, danh mục hoạt động, có ảnh dùng được, mọi size có giá đã áp dụng).
  - `purchasable`: `publicVisible` và đang bán (PRODUCT: ít nhất một size bật bán; VARIANT: size đó bật bán). Không dùng số lượng/giữ hàng của FE-08.
- **Lỗi**: scope khác PRODUCT hoặc dimension khác PRODUCT/VARIANT → `INVALID_INPUT`; không đọc được catalogue → `DEPENDENCY_UNAVAILABLE` (không bao giờ cho phép mặc định). Không khóa dòng, không gọi remote.
- **Đã chốt 2026-10-26** (Đặng Thị Khương, phương án A): VARIANT là một SKU cụ thể; quy tắc `belongsToParent` ở trên được giữ, không đổi contract `TargetRef`. Ghi tại `spec.md` (Clarifications, Session 2026-10-26) và `contracts/interfaces.md` (Target port). Không có loại phạm vi "mọi nhẫn cùng con số size".
- **Chưa đóng G-02**: phần source sales (giá/số lượng/đơn đã chấp nhận) thuộc FE-08; test owner serialize xóa sản phẩm với accept của FE-06 cần FE-08.

**Diễn biến 2026-11-01 — develop `8d69792`.** FE-03 (003.1/003.3) đã nối accept, cọc đã xác minh, hết hạn chưa trả, hủy hợp lệ, no-show và hoàn thành + trả đủ vào `LoyaltyFacade`; 006 đã review và kiểm chứng qua service thật (verification mục 28, T057/T059/T060). CORS dùng chung không còn bị `workshopOperationsCorsFilter` chặn (`LoyaltySecurityIT` 11/11). Còn mở: test nguồn thật cho outcome trễ/gửi lặp và hủy sai (T062), tranh chấp phía nguồn (T063), đăng nhập 001 cho tích hợp UI thật (T072/T078; backend chưa khởi tạo Firebase Admin).

## Hiện trạng gate 2026-11-01 (chuẩn bị T075, chưa tick)

Đối chiếu tại `khuong` sau khi ghép develop `365dcf3`; bằng chứng chi tiết ở [verification](verification.md) mục 27–30. Không gate nào được đánh đóng chỉ vì tài liệu đủ.

| Gate | Hiện trạng | Còn thiếu / owner |
| --- | --- | --- |
| G-01 Identity | Chain dùng chung và `AccountFacade` của 001 có trên develop; CORS không còn bị chặn (`LoyaltySecurityIT` 11/11). | Backend chưa gọi `FirebaseApp.initializeApp` nên chưa xác minh được token thật; chưa có màn đăng nhập và cách tạo Admin/Manager đầu tiên — Phan Sơn (001). |
| G-02 Catalogue | Target PRODUCT (002) và WORKSHOP (003) dùng thật; nguồn bán hàng FE-08 thật. | Không còn thiếu phía 006. |
| G-03 Retail (FE-08) | Accept, thanh toán, đơn 0đ, hủy trước thanh toán, hết hạn đã xác nhận và gửi trùng đều chạy qua service thật (mục 24–26, 29). | Nhúng `BenefitSelector`, trả `maxPoints` trong `BenefitPreviewView` — Bùi Trọng Thịnh (T055). |
| G-04 Booking (FE-03) | Quote/accept thật giữ voucher + điểm cùng giữ chỗ; cọc ceil(post/2) (mục 28). | Nhúng `BenefitSelector context="workshop"`, trả `maxPoints` trong `WorkshopBookingQuoteResponse` — Hồ Hoàng Quốc (T061). |
| G-05 Workshop hậu xử lý | Cọc xác minh, hết hạn, hủy hợp lệ, no-show, hoàn thành + trả đủ đều do code FE-03 quyết định, kể cả gửi lặp và tranh chấp (mục 28–29). | Không còn thiếu phía 006. |
| G-06 Data/module | Migration loyalty V10–V12 trên develop; `ModulithArchitectureTests` 5/5; toàn bộ 006 248/248 (mục 29). | Đã chạy lại 2026-11-01: 257/257 đạt, 0 bỏ qua (verification mục 31). |
| G-07 Governance | Tài liệu Approved; chênh lệch tài liệu cần sửa khi đóng T076 liệt kê ở verification mục 30. | T076 đóng 2026-11-01: sửa chênh lệch tài liệu; speckit-analyze không có trong phiên, phân tích thủ công (verification mục 31). |
