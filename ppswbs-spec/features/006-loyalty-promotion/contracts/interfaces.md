# Interface Contracts: Loyalty & Promotion

**Date created**: 2026-10-24
**Date updated**: 2026-10-24
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Status**: Proposed contracts for review; các facade/routes dưới đây chưa tồn tại trong code.  
**Sources**: [spec](../spec.md), [data model](../data-model.md), [research](../research.md), [OpenAPI](loyalty-promotion.openapi.json).

## 1. Module boundaries

- `loyaltypromotion::facade` là named interface duy nhất public của module; controller/dto/dao/entity/service đóng.
- `readyringsales → loyaltypromotion::facade` và `workshopbooking → loyaltypromotion::facade`. Loyalty không import nguồn order/booking/productmanagement/payment.
- `loyaltypromotion → members::facade` cho verified actor/capabilities/authorization guard. Identity owner phải cung cấp contract này, không suy quyền từ MemberDto/email/ROLE_MEMBER.
- `PromotionTargetPort` thuộc loyaltypromotion facade, productmanagement/workshop triển khai trong adapter component độc lập; chỉ trả immutable scope facts/guard để kiểm tra cấu hình. Không inject WorkshopBookingService đang phụ thuộc loyalty vào port này.
- Không đọc repository/entity nguồn, không chuyển business entity vào common. Technical common chỉ correlation/error conventions.
- DAG thực tế được kiểm chứng bằng ApplicationModules.verify(); một allowedDependencies declaration không chứng minh dependency đã được triển khai.

## 2. Public REST

Xem schemas/status/security đầy đủ trong [OpenAPI](loyalty-promotion.openapi.json).

| Route | Methods | Actor | Meaning |
| --- | --- | --- | --- |
| /api/v1/promotions | GET | Public | Thẻ chương trình đang hiệu lực, lọc scope; không quyền quản trị hoặc danh sách Member. |
| /api/v1/promotions/{promotionId} | GET | Public | Điều kiện công khai; hết hạn/dừng không hiện như mã đang dùng được. Không xác nhận eligibility của đơn cụ thể. |
| /api/v1/promotion-programs | GET, POST | Manager | Quản lí danh sách/tạo ba loại chương trình. |
| /api/v1/promotion-programs/{programId} | GET, PATCH | Manager | Xem/sửa điều kiện, versioned; không đổi code/kind/scope. |
| /api/v1/promotion-programs/{programId}/pauses | POST | Manager | Tạm dừng chương trình mới, không hủy accepted holds. |
| /api/v1/promotion-programs/{programId}/resumptions | POST | Manager | Mở lại cùng chương trình/mã đang PAUSED, giữ quota/history; không tự gia hạn hoặc bảo đảm dùng được. |
| /api/v1/promotion-programs/{programId}/usage | GET | Manager | Hạn mức, held/consumed và tiền giảm hiện tại của chương trình; không tính preview/held hoặc booking đã ghi nhận hủy hợp lệ vào số tổng. Giữ lịch sử dùng/hoàn để tra cứu. |
| /api/v1/members/me/saved-vouchers | GET | Member | Kho chính mình, gồm tình trạng hiện tại. |
| /api/v1/members/me/saved-vouchers/{programId} | PUT | Member | Lưu idempotent; không giữ hạn mức. |
| /api/v1/members/me/loyalty-accounts/current | GET | Member | Balance/held/available và policy quy đổi hiện hành. |
| /api/v1/members/me/loyalty-transactions | GET | Member | Lịch sử cộng/dùng/hoàn và giữ/giải phóng liên quan chính mình. |
| /api/v1/loyalty-policies/current | GET, PUT | Admin | Xem/thay policy version. |

Không có public HTTP endpoint để reserve/consume/release/earn/restore, không nhận paid/completed/cancelled proof từ browser. Checkout quote/accept endpoints thuộc source owners FE-03/FE-08, contract loyalty cung cấp facade cho họ gọi.

### Wire rules

- ID/money/points/quota/version: decimal string, không scientific notation/negative/leading plus, max 19 digits cho money/points; BIGINT-range check cho ID/quota/version. Zero được phép theo từng field.
- Percentage string tối đa 6 decimals, (0,100]. Timestamps ISO-8601 UTC; local workshop dates YYYY-MM-DD.
- Program name 1–200, code 1–128 sau trim, không control characters; lengths theo code point. Name thay đổi được; code/scope/kind immutable.
- Strict write schemas reject unknown fields, client owner/role/counter/balance/payment facts. Limits và targets được kiểm tra tại owner boundaries.
- Public card không phải quote hoặc guarantee. Private responses Cache-Control: no-store; không dùng cache policy cho số dư/entitlement.
- Pagination default20/max100, stable order createdAt desc/id desc cho quản trị/lịch sử, savedAt desc/programId desc cho kho; opaque cursor không chứa PII. Public order createdAt desc/id desc.
- Mutation có UUID `Idempotency-Key`; updates/pause/resume/policy có `expectedVersion`. Resume chỉ chuyển PAUSED→ENABLED, không reset counters/history hoặc gia hạn; trạng thái/version không phù hợp trả409, cùng command đã commit trả receipt. PUT save lặp cùng resource không tạo thêm row/quyền lợi.

## 3. Auth/session/error

- REST API (kể cả gọi từ React): bearer Firebase token được backend xác minh, kiểm tra revocation và local identity state/capabilities hiện tại. API không chấp nhận web cookie thay bearer.
- Frontend React gửi cùng bearer; phiên theo contract FE-01/001; role/ownership kiểm mỗi thao tác. API không dùng cookie thay bearer theo [001](../../001-account-management/plan.md); loyaltypromotion không tạo cookie auth mới.
- Manager quản lí programs; Admin policy; Member own resources. Một vai trò không tự suy là vai trò kia. Nội bộ phải có privileged assurance do FE-01 cung cấp; chưa có dependency thì từ chối, không fallback permitAll.
- Trusted lifecycle continuation không đòi Member còn đang active tại callback để xử lí cam kết đã chấp nhận; chỉ source component nội bộ có quyền gọi, không credential của browser.
- 400 INVALID_INPUT cho malformed/type/bounds; 401 INVALID_TOKEN và mã hết phiên theo contract của 001; 403 ACCESS_DENIED/PRIVILEGED_AUTH_REQUIRED; 404 PROMOTION_NOT_FOUND; 409 CODE_EXISTS, STALE_VERSION, IDEMPOTENCY_CONFLICT, QUOTA_LIMIT_CONFLICT, BENEFIT_STATE_CONFLICT, QUOTE_CHANGED, POINTS_LIMIT_CHANGED; 422 PROMOTION_INELIGIBLE, POINTS_USE_NOT_ALLOWED, WORKSHOP_NET_AMOUNT_INVALID; 503 DEPENDENCY_UNAVAILABLE hoặc RETRYABLE_CONTENTION.
- Error envelope `code,message,correlationId,timestamp,fieldErrors` theo common convention; không stack trace, token, member data hoặc raw source proof. Retryable contention không tự đổi key hoặc total; client kiểm tra/retry cùng intent.
- Frontend hiển thị lỗi theo trường và giữ dữ liệu hợp lệ đã nhập; stale/quote changed hiển thị latest để khách xác nhận lại; 401 đưa về đăng nhập Firebase của owner; sau thành công điều hướng client.

## 4. Internal facade records and operations

Tên interface là thiết kế public của module; không phải entity/HTTP DTO. Synchronous mutation bắt buộc transaction nguồn cùng DataSource/JPA transaction manager.

### Trusted inputs

`AcceptedSourceContext`:

- sourceKind RETAIL/WORKSHOP, sourceRef, sourceRevision, memberId từ identity owner.
- `originEligibility` MEMBER_WEBSITE_AT_ACCEPTANCE; không retrofit từ guest-import memberId.
- acceptedAt, originalDeadline, currency VND.
- server-priced line facts productId/variantId/quantity/unitAmount hoặc workshop packageId/branchId/workshopDate/sessionId/participantCount và grossPackageAmount do owner tạo.
- shippingAmount riêng retail; không tham gia discount/earn points.
- source-owned accepted quote reference + terms digest + expected quote acknowledgement; không dùng browser amount làm proof.

`BenefitSelection`:

- choice NONE/AUTO/VOUCHER, voucherCode chỉ khi VOUCHER, requestedPoints.
- AUTO retail chọn server candidate tối ưu theo khoản giảm; workshop không AUTO.
- `NONE` cho phép không chọn chương trình; điểm vẫn được dùng theo policy, trừ khi có chương trình được chọn cấm kết hợp.

`AuthoritativeOutcome`:

- sourceKind/sourceRef, ownerModule, sourceOutcomeKey, monotonic sourceOutcomeVersion.
- outcome RETAIL_FULLY_PAID / RETAIL_ZERO_ACCEPTED / WORKSHOP_DEPOSIT_ACCEPTED / WORKSHOP_COMPLETED_AND_FULLY_PAID / UNPAID_EXPIRY_CONFIRMED / VALID_WORKSHOP_CANCELLATION / WORKSHOP_NO_SHOW_FINAL.
- decisionAt, originalDeadline, snapshotRef, member-origin flag, minimal proof reference (không gateway raw payload).
- Source đã serialize trạng thái nguồn và chứng minh outcome; loyalty đối chiếu nguồn/ref/member/snapshot và effect history, không tự diễn giải callback provider.

### Operations

| Operation | Input | Result / semantics |
| --- | --- | --- |
| previewBenefits | TrustedSourceContext, BenefitSelection, verified actor | Breakdown, normalized code/selected revision, policy revision, eligible subtotal, max points, display reasons and terms digest. Read-only; không giữ quota/điểm, không tạo accepted benefit. |
| reserveAcceptedTransaction | AcceptedSourceContext, selection, accepted terms proof, source command key | Kiểm tra lại current program/policy/availability, so với terms khách đã đồng ý; AcceptedBenefitRef + exact money/points/policy snapshot. MANDATORY transaction, giữ cùng source accept. Nếu khác điều khoản hoặc không đủ quota/điểm thì rollback, không silently choose lại. |
| settleAcceptedBenefits | AuthoritativeOutcome paid/deposit/zero | Consume từng component đang HELD đúng lượng gốc; ghi settlement fact/outcome key một lần cả khi các component đều NONE để thống kê giảm tự động. Cùng result replay; expired/released/conflicting outcome không tự consume lại. |
| grantEarnedPoints | AuthoritativeOutcome RETAIL_FULLY_PAID/RETAIL_ZERO_ACCEPTED hoặc WORKSHOP_COMPLETED_AND_FULLY_PAID | Policy acceptance + earnBase snapshot; một grant/giao dịch. Các component đã dùng phải CONSUMED hoặc NONE trước grant; không cấp điểm khi debit còn HELD. Workshop deposit không được gọi để grant. Zero earn ghi result0, không ledger giả. |
| releaseHeldBenefits | AuthoritativeOutcome UNPAID_EXPIRY_CONFIRMED hoặc valid cancel | Chỉ HELD→RELEASED; không suy failure từ originalDeadline. |
| restoreCancelledWorkshopBenefits | AuthoritativeOutcome VALID_WORKSHOP_CANCELLATION | HELD thì release, CONSUMED thì restore từng component; trả points gốc và cả hai quota. Không đổi tiền hoặc grant; receipt theo component để retry phần thiếu. |
| finishIneligibleEarn | AuthoritativeOutcome unpaid expiry/valid cancel/no-show final | PENDING→INELIGIBLE, không tự hoàn CONSUMED nếu no-show. |
| findBenefitResult | Source reference + trusted owner context | Current component/earn result để source reconciliation; không trả dữ liệu giao dịch khác. |

Facade mutation MANDATORY, không REQUIRES_NEW. Nontransactional call bị từ chối trước ghi. Method validation không thay source ownership checks. Mọi interface failure typed, không expose entity.

### Target port

`PromotionTargetPort.validateAndGuard(scope, targets)` được gọi trước registry lock, trong transaction Manager khi cần kiểm tra target tồn tại/hợp lệ. PRODUCT provider ở productmanagement; WORKSHOP provider ở workshopbooking. Provider trả guard/immutable references, không remote call dưới khóa. Không có provider thì DEPENDENCY_UNAVAILABLE; không bỏ qua phạm vi hoặc dùng list giả.

Loyalty không gọi target port trong reserve sau khi giữ registry: source owner đã giữ business guards và cung cấp authoritative line/session facts. Cần test owner serialize product deletion/schedule change với accept để không tạo quyền lợi trên nguồn không còn hợp lệ.

## Transaction and lock order

1. Identity resolve/guard (nếu tương tác người dùng), kiểm tra ownership/capability; không giữ registry rồi gọi identity.
2. Source resource guards theo contracts của productmanagement/workshop/retail; callbacks khóa source lifecycle record để phân xử payment/expiry/cancel.
3. Registry READ cho accept, WRITE cho program/policy mutation.
4. Program rows tăng ID; selected voucher quota serialized bằng program lock.
5. Wallet Member, sau đó MemberQuota tăng program ID.
6. AcceptedBenefit/effects/receipts/ledger/audit; unique business keys và version kiểm tra lại trước commit.
7. Commit toàn bộ; remote gateway/email không trong transaction dưới khóa.

Terminal effects có thể bắt đầu ở source lifecycle guard rồi đến registry/program/wallet; không cần active Member guard để hoàn tất cam kết cũ. Không có code path lấy wallet rồi quay lại registry/program/source.

- Manager quota edit dùng registry WRITE/program lock, đọc tất cả member counts dưới cùng exclusion; giảm limit dưới bất kỳ count nào thì reject.
- Insert race wallet được serialize qua identity Member guard; counters mới qua program guard; sourceRef uniqueness/receipt constraints vẫn bắt buộc.
- JPA unique violation rollback trước khi read lại receipt ở transaction mới. Không catch rồi tiếp tục save trong transaction rollback-only.
- Same actor/action/command key + same canonical payload returns committed minimal result sau auth; khác payload 409. Canonical digest chứa normalized selection, refs/versions và amounts đã xác minh, không token/correlation.
- Owner accepted source row, loyalty reserve và source invoice/quantity/capacity holds commit cùng transaction; same sourceRef repeat trả đúng accepted snapshot, không giữ thêm.
- Atomic path là mặc định. Nếu owner cần bridge outcome legacy đã commit, owner phải ghi durable delivery receipt trong chính transaction nguồn và retry vào loyalty với cùng proof. Không dùng event in-memory, không cho loyalty tự polling source repo; route bridge chỉ enable sau contract/integration tests của owner.
- Delivery của một sourceRef phải được owner serialize theo sourceOutcomeVersion; paid outcome xử lí settle rồi grant trong cùng transaction delivery. Outcome cũ đã áp dụng thì replay receipt, outcome mâu thuẫn trạng thái terminal thì conflict để owner đối soát, không cưỡng ép state hoặc bỏ qua debit để grant trước.
- Timeout/deadlock rollback; không external side effect trước commit. Expiry/payment unresolved do source giữ trạng thái cần đối soát, không tự release và không gia hạn.

## 5. Frontend contracts (React + Vite, REST-only)

Frontend là ứng dụng React + Vite tách riêng (`ppswbs_frontend/`, chưa khởi tạo), render phía client và chỉ gọi REST. Backend không render UI hoặc HTML form. Client route, thư viện routing/state và i18n do kế hoạch frontend chốt, không chọn ở đây. Mọi lệnh ghi gửi UUID `Idempotency-Key`; update/pause/resume/policy gửi `expectedVersion`.

| Màn hình / thành phần | Actor | REST operations | Core behavior |
| --- | --- | --- | --- |
| Danh sách / chi tiết ưu đãi công khai | Public | `listPublicPromotions`, `getPublicPromotion` | Scope-labelled cards và điều kiện; login trước khi lưu. |
| Kho Voucher | Member | `listOwnSavedVouchers`, `saveOwnVoucher` | Kho chính mình, status/lý do không dùng, save không quota hold. |
| My Loyalty | Member | `getOwnLoyaltyAccount`, `listOwnLoyaltyTransactions` | Balance, held, available, conversion/history; lỗi tải không hiện số cũ như confirmed. |
| Danh sách & tạo chương trình | Manager | `listPromotionPrograms`, `createPromotionProgram` | List/filter và form tạo theo kind/scope; lỗi theo trường, giữ input. |
| Sửa / dừng / mở lại chương trình | Manager | `getPromotionProgram`, `updatePromotionProgram`, `pausePromotionProgram`, `resumePromotionProgram`, `getPromotionUsage` | expectedVersion, UUID command, không đổi immutable fields; nút Mở lại cho PAUSED, giữ history/quota và xem current nếu stale. |
| Chính sách điểm | Admin | `getCurrentLoyaltyPolicy`, `updateCurrentLoyaltyPolicy` | Form có version, ảnh hưởng tương lai. |
| Thành phần chọn ưu đãi trong checkout FE-03/FE-08 | Member | Dữ liệu quote/breakdown từ API của owner FE-03/FE-08; không thêm endpoint public mới | FE-06 cung cấp component chọn mã/điểm và breakdown để màn hình của owner nhúng; owner gửi accept, FE-06 không tự tạo booking/order. |

Hành vi chung: UI không tự gửi lệnh khi đổi lựa chọn; có nút Tính lại và Xác nhận. Chọn ưu đãi có thể không dùng, nhập mã hoặc chọn đã lưu. Khách phải thấy max usable points; lý do chặn kết hợp/minimum rõ ràng. Mỗi màn hình có loading, empty, validation, success và failure/recovery; không hiển thị thành công trước khi response xác nhận. Ẩn nút hoặc route guard phía client không thay authorization phía server.

Empty states: chưa lưu mã, chưa có lịch sử/điểm; không đồng nhất empty với dependency error. Accessibility: label/input association, field errors và summary, keyboard operability; money/points bằng text đọc được.

## 6. Compatibility and retention

- Không đổi API/contract đã triển khai của 001/003 trong tài liệu này. Source integration cần owner review và additive contract versioning.
- Không retrofit loyalty cho booking/đơn cũ không có snapshot Member-origin và accepted policy.
- Command/audit/effect evidence tối thiểu5 năm, protected references/unfinished source; code/source-effect tombstones tồn tại để giữ uniqueness. Không log full payload/raw tokens/PII.
- Operation schema/private output là proposed; runtime, migration, authorization và owner adapters phải được kiểm chứng trước activate.

