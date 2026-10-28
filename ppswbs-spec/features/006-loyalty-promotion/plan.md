# Implementation Plan: Loyalty & Promotion Management

**Date created**: 2026-10-24
**Date updated**: 2026-10-26
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Feature context**: `006-loyalty-promotion` | **Spec**: [spec.md](spec.md)  
**Git branch**: `khuong` giữ nguyên. `006-loyalty-promotion` là feature context của SpecKit, không phải nhánh Git được tạo trong tác vụ.  
**Status**: Approved — duyệt bởi Đặng Thị Khương ngày 2026-10-24, duyệt lại ngày 2026-10-26 cùng spec (xem đoạn "Cập nhật 2026-10-26" cuối tài liệu); thiết kế là baseline cho triển khai, các task phụ thuộc bên ngoài (FE-01 contract xác thực, FE-02 T088/T089/T091, FE-03 và FE-08 source owner theo integration gates G-01–G-05) chỉ bắt đầu khi dependency tương ứng đạt; không phê duyệt deploy.  
**Input**: FE-06 và quyết định1–10, bộ thông số điểm đã chốt; xác nhận bổ sung khi plan về retention tối thiểu5 năm và shared auth theo 001. Chỉ soạn thiết kế.

## Summary

Thêm module loyaltypromotion cho voucher sản phẩm/workshop, giảm tự động sản phẩm và điểm chung. Member kiếm/dùng điểm cả hai dịch vụ, không hạng. Giữ stack modular monolith; JPA transaction cùng source owner để accept+reserve nguyên tử, owner xác minh lifecycle rồi gọi settle/release/restore/earn. Immutable policy/program revisions giữ cam kết cũ, wallet/quota locks và receipts/ledger bảo đảm không dùng/trả/cộng trùng.

Bốn thông số khởi đầu 10.000đ→1 điểm,1 điểm→100đ,min10,trần10%; workshop voucher→điểm→cọc, chỉ completed AND fully-paid mới earn. [Gates](integration-gates.md) phân biệt dependency code thật còn thiếu với nghiệp vụ đã chốt.

## Technical Context

| Item | Selected/proposed design |
| --- | --- |
| Language/Version | Java21, giữ pom hiện tại. |
| Primary Dependencies | Spring Boot3.4.1, Modulith1.3.1, Firebase Admin9.11.0, Spring Data JPA/Security/Validation/Flyway theo BOM hiện tại (Thymeleaf còn trong pom nhưng không dùng cho UI); frontend React + Vite tách riêng, chưa khởi tạo; không nâng version hoặc thêm framework backend. |
| Storage | MySQL8.4/InnoDB baseline như001/002; JPA cho application persistence, additive Flyway. DECIMAL(19,0) tiền/điểm, BigInteger/BigDecimal exact, JSON decimal strings, UTC. |
| Testing | JUnit/Spring Boot Test/MockMvc/Modulith hiện có; MySQL thật cho migration/locks/collation/upgrade, H2 chỉ bổ trợ. Test classes trong guide là dự kiến. |
| Target Platform | Spring Boot REST trong monolith hiện có phục vụ frontend React + Vite tách riêng, dev Windows hỗ trợ Maven wrapper; không chọn host/topology production. |
| Project Type | Backend Java REST (Spring Modulith) cộng frontend React + Vite client-side rendering tách riêng; không service mới, backend không render UI. |
| Performance Goals | Ưu tiên correctness theo8 SC; chưa cam kết SLA/throughput production vì chưa có baseline. Pagination20/max100, scope queries indexed/batched, short local transactions, không remote calls dưới lock. Đo contention/latency trên fixture MySQL trước rollout, không tự coi benchmark đã đạt. |
| Constraints | Current local roles/ownership, V1 không MFA theo 001, shared session theo FE-01/001, UI React chỉ qua REST, no raw SQL application, không reset dữ liệu, không points expiry/tiers/retail returns. |
| Scale/Scope | Ba loại chương trình, một wallet/Member, một chương trình/retail hoặc voucher/booking; immutable revisions, component-level receipts. Tổng số Member/transactions production không phải giả định để tự đặt SLA. |

## Constitution Check

Đối chiếu trước research và sau Phase1; PASS DESIGN không phải runtime evidence.

| Principle/gate | Before research | After design | Evidence/condition |
| --- | --- | --- | --- |
| I. Closed modules/ownership | PASS | PASS DESIGN | loyalty owns state, public facade only; source→loyaltypromotion, loyaltypromotion→members; target adapters qua SPI. Không common business logic. |
| I. JPA/Flyway | PASS | PASS DESIGN | JPA locks, append-only migrations, exact numbers; allocate migration number tại implementation, không sửa migration đã áp dụng. |
| I. Acyclic integration | REVIEW REQUIRED | PASS DESIGN / PROVIDERS OPEN | [interfaces](contracts/interfaces.md); actual verify cần implementation và source adapters. Không suy cycle từ stale allowedDependencies. |
| II. Identity/authority | DEPENDENCY OPEN | DECISION CONFIRMED / CODE OPEN | User chốt sharedFE01/001 auth; facade identity của FE-01 chưa có (backend đã bị xóa). Fail closed, không ROLE_MEMBER/claims/permitAll fallback. |
| II. Sensitive data/retention | POLICY QUESTION | DECISION CONFIRMED | User chọn tối thiểu5 năm, no purge referenced/unfinished; [research D08](research.md#d-08--retention-được-xác-nhận-cho-fe-06), data-model mục7. Không raw payload/token/PII thừa. |
| III. Contracts/separate frontend | PASS | PASS DESIGN | OpenAPI3.0.3, strict DTO/typed errors, protected routes, bearer cho mọi REST; frontend React + Vite tách riêng, backend không render UI. |
| IV. Fresh evidence | PASS FOR DOCS | PASS FOR DOCS | Static checks và source review cho tài liệu; runtime/migration/auth/concurrency NOT RUN vì chưa code. |
| V. Explicit decisions/simplicity | BUSINESS CLOSED | PASS DESIGN / REVIEW PENDING | Hai câu hỏi plan đã trả lời; không thêm tiers/provider/broker. Technical design Draft cần review, gates không tự đóng bởi checklist. |

**Gate outcome**: Không có constitution exception mới và không còn câu hỏi nghiệp vụ FE-06 chờ trả lời. Phase0/Phase1 có thể hoàn thành dưới dạng tài liệu; G01–G07 còn điều kiện code/integration/governance trước triển khai. Không tự triển khai một phần hoặc sửa source owner để vượt gate.

## Project Structure

### Documentation (this feature)

```text
ppswbs-spec/features/006-loyalty-promotion/
  spec.md
  plan.md
  research.md
  data-model.md
  quickstart.md
  integration-gates.md
  checklists/requirements.md
  contracts/interfaces.md
  contracts/loyalty-promotion.openapi.json
```

Không tạo tasks.md trong speckit-plan. Spec/checklist được đồng bộ riêng hai câu trả lời auth/retention; không sửa research tổng, feature 001/003 hoặc kit.

### Source Code (repository root, planned only)

```text
ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/
  features/loyaltypromotion/
    controller/      scoped API controllers, exception handler
    dto/             strict request/response DTOs, typed error DTOs, scalar codec
    dao/             JPA/Spring Data repositories
    entity/          programs/revisions/targets, quotas, wallet, ledger, benefits, receipts, audit
    service/         program/policy commands, quote/hold/effects, authorization, calculator
    facade/          LoyaltyFacade, PromotionTargetPort, immutable inputs/results
  members/facade/     dependency: verified actor/current capability/authorization guard
  features/productmanagement/  dependency adapter for product target validation
  features/workshopbooking/  source adapter for workshop target and accepted lifecycle (FE-03, chờ owner chốt layout)
  features/readyringsales/   source adapter for retail checkout/paid/zero/expiry (FE-08 theo 008 plan)
  configuration/    route-scoped API security/CORS wiring
ppswbs_backend/src/main/resources/
  db/migration/      new additive migration, number allocated at implementation
ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/
  features/loyaltypromotion/  future calculation, lifecycle, contract, security, MySQL checks
ppswbs_frontend/      (chưa khởi tạo; vị trí dự kiến)
  src/features/loyaltypromotion/  promotions, wallet/account, management, policy screens và checkout components
```

**Structure Decision**: Theo constitution I, backend tổ chức `features/<feature>/{controller,dto,dao,entity,service,facade}`; FE-06 là module Modulith riêng tên `loyaltypromotion` (gói `features/loyaltypromotion`, backend đã bị xóa ở commit `c47fefe` (ho-quoc, 2026-10-24), nên module tạo mới). Plan phải kiểm chứng bằng `ApplicationModules.verify()` rằng `loyaltypromotion` được nhận diện là module riêng, không chỉ gói grouping `features`. Trong văn bản tài liệu FE-06, chữ "loyalty" khi nói về module nghĩa là module `loyaltypromotion`; tên bảng `loyalty_*`/`promotion_*` và tên class `Loyalty*`/`Promotion*` giữ nguyên. Module riêng theo ownership FE06, không nhét vào billing/common hoặc dùng frontend-only local storage cho điểm. Danh sách source tree là vị trí dự kiến, chưa tạo code.

## Phase 0 — Research and decisions

[research.md](research.md) ghi D01–D10: stack, boundary, atomic source transaction, locks/receipts, exact arithmetic/snapshots, lifecycle, shared auth đã xác nhận, retention đã xác nhận, web/API và dependency evidence.

Hai câu trả lời được ghi vào spec A005/A006, FR040/FR041 và acceptanceUS6.9/US8.7. Không dùng retention5 năm của catalogue hoặc Member cookie để tự mở quyền FE06. Không còn NEEDS CLARIFICATION để đoán khi code; integration gates vẫn phải được giao/hoàn thiện.

## Phase 1 — Design and contracts

### Accepted source transaction

1. Owner xác thực current Member, lấy business guards theo FE02/FE03/FE08 và server-side facts. Browser gửi selection/acknowledgement, không gửi authoritative price/payment/member ownership.
2. Owner gọi read-only preview, hiển thị exact discount/points/max/totals. Preview không giữ quota/points; source-owned quote reference ràng buộc Member/terms được khách xác nhận.
3. Trong transaction accept, source kiểm tra/khóa nguồn, gọi MANDATORY reserve. Loyalty READ-lock registry, đọc policy/current candidates, chọn discount, lock program/wallet/counters theo thứ tự, so accepted terms và kiểm tra limits.
4. Ghi AcceptedBenefit/snapshot/holds/effects/receipt/audit cùng order/booking/invoice/source holds. Khác tổng/terms hoặc không đủ quyền lợi rollback tất cả; không silently giảm ít hơn/tự chọn mã khác. Không remote gateway/email dưới lock.
5. Khi mất phản hồi, same intent/key đọc committed result sau auth; key khác payload409. Không tạo source mới/gia hạn/chốt thêm chỉ vì retry.

### Lifecycle, reconciliation and source authority

- Retail full-paid/zero accepted: owner xác nhận rồi consume và grant một lần. Workshop deposit: consume, chưa grant; owner completed AND fully-paid mới grant theo acceptance policy.
- Source row/guard phân xử callback vs expiry/cancel/completion trước loyalty. Loyalty không suy failed/expired từ clock hoặc client redirect.
- Valid workshop cancel: HELD→release, CONSUMED→restore đúng component gốc, trả cả tổng/Member voucher quota dù mã đã hết hạn/dừng; không reactivation, cash conversion hoặc earn.
- No-show/invalid cancellation không auto restore. Guest history import không retro-earn.
- Ledger/receipt/state/counters cùng transaction; component states cho retry phần chưa hoàn. Unique constraint conflict phải rollback rồi đọc committed receipt trong transaction khác, không tiếp tục context rollback-only.
- Khi source cần bridge outcome đã commit/legacy async, durable source-owned delivery receipt là prerequisite; consumer gọi cùng facade idempotently. Không in-memory notification làm bằng chứng, không loyaltypromotion→source polling/cycle.
- Source cần đối soát thì giữ unresolved, không unlock vì hết deadline; không sửa original deadline. Xem [transaction contract](contracts/interfaces.md#transaction-and-lock-order).

### Stateless and retained state

Theo constitution V, mỗi tương tác của FE-06 được phân loại như sau. Không dùng Redis, session store, JWT do ứng dụng ký hoặc refresh-token riêng.

| Tương tác | Loại | Owner | Storage và lifecycle | Bảo mật và recovery |
| --- | --- | --- | --- | --- |
| Xác thực mọi REST (kể cả frontend) | Stateless: bearer được xác minh mỗi request | Identity (FE-01) | Không lưu trong loyaltypromotion. Theo contract xác thực FE-01/001 | Revocation, trạng thái và quyền hiện tại kiểm mỗi request; hết phiên/thu hồi trả 401; hành vi đăng nhập lại/recovery do 001 định nghĩa |
| Xem ưu đãi công khai | Stateless | loyaltypromotion | Không lưu; đọc lại điều kiện hiệu lực mỗi lượt | Dữ liệu private no-store; lỗi phụ thuộc khác empty |
| Voucher đã lưu | Retained | loyaltypromotion (Member sở hữu) | MySQL; PUT lặp cùng resource không tạo thêm quyền lợi hay quota hold | Idempotent; không lộ voucher người khác |
| Chương trình, revision, đích áp dụng | Retained | loyaltypromotion | MySQL; revision bất biến, đổi qua version mới | expectedVersion, 409 xem latest |
| Quota và bộ đếm | Retained, thay đổi có khóa | loyaltypromotion | MySQL; khóa theo thứ tự đã chốt, mở lại không reset quota | Timeout/deadlock rollback, retry cùng intent |
| Ví điểm và ledger | Retained | loyaltypromotion | MySQL; giữ tối thiểu 5 năm, không có hết hạn điểm | Không purge dữ liệu còn tham chiếu hoặc chưa xong |
| Quyền lợi đã chấp nhận (reserve, settle, release, restore) | Retained, cùng transaction với nguồn | Owner FE-03/FE-08 điều phối, loyaltypromotion ghi | MySQL; snapshot chính sách và số tiền tại thời điểm chấp nhận | Replay theo sourceRef và sourceOutcomeVersion; mâu thuẫn trạng thái terminal trả conflict để owner đối soát |
| Command receipt | Retained | loyaltypromotion | MySQL; giữ tối thiểu 5 năm | Cùng digest trả receipt đã commit; khác digest trả 409 |
| Chính sách điểm | Retained, có version | loyaltypromotion (Manager cập nhật) | MySQL; chỉ ảnh hưởng giao dịch mới | Snapshot cũ giữ nguyên |
| Lựa chọn ưu đãi trong checkout (nháp) | Client-side, tạm thời | Frontend của owner FE-03/FE-08 | Không là nguồn sự thật; quote do API của owner trả | Mất khi tải lại; tính lại qua nút Tính lại |

Trạng thái xác thực và trạng thái nghiệp vụ được tách riêng: cookie hay cơ sở dữ liệu một mình không xác lập mô hình xác thực.

### Security and web

- API bearer đã verify/revocation; private responses no-store. Frontend React dùng cùng bearer, phiên identity theo FE-01/001, current role/ownership; V1 không MFA theo 001, không làm login/MFA riêng.
- Identity chưa đủ authority thì fail closed. Technical management chains không dùng permitAll; không thay global security hoặc Member journeys từ feature này.
- Manager forms quản lí/sửa/dừng/mở lại programs và chính sách điểm; Member kho/My Loyalty; checkout components do màn hình của owner FE-03/FE-08 nhúng. Resume PAUSED→ENABLED dùng cùng quyền, registry WRITE lock, command UUID/expectedVersion và audit; giữ quota/history, không gia hạn hoặc đổi accepted snapshots. React gọi REST với field/conflict errors theo typed error, điều hướng client sau thành công.
- Không public reserve/paid/completed/earn/refund endpoint. API input strict, safe correlation errors; không log full source proof/token.

### Data, calculation and revisions

[Data model](data-model.md) mô tả fields, constraints, transitions, indices, ledger deltas và formulas. Eligible subtotal dùng xét ngưỡng/voucher, toàn net hàng/phí gói dùng trần điểm/earn sau điểm, shipping ngoài cả hai. Program/policy revisions immutable, counters mutable guarded. Mỗi giao dịch Member accepted có policy snapshot kể cả không dùng ưu đãi/points; không suy snapshot từ config hiện hành khi grant.

### Migration, retention and operations

- Thêm migration mới sau kiểm tra version folder hiện tại, không lấy sốfeature006 làm version, không sửa/drop/reset migration đã áp dụng hoặc retro-credit source cũ thiếu facts.
- MySQL8.4 empty + upgrade populated baseline; FK nội loyalty, cross-module logical refs; test decimals/collation/unique/check constraints/locks thật. Không chỉ H2.
- History/audit/results tối thiểu5 năm, source chưa terminal/refs vẫn protected; không purge v1, không xóa wallet/code registry/dedup keys để mở replay/reuse. Không chứa raw credential/PII không cần thiết.
- Theo dõi correlation, command/sourceRef, conflict/lock timeout, delivery backlog, imbalance invariant errors và component retry; không tạo consumer tự quyết business outcome.
- Rollout sau owner adapters thật + spec/plan/tasks review + analyze và required tests; disable loyaltypromotion entrypoints khi cần recovery, giữ schema/ledger/receipts và xử lí cam kết đã chấp nhận qua owner. Không toggle off để release/restore hàng loạt, không down migration.
- Deploy/DB operations không được thực hiện trong tác vụ plan.

## Verification strategy and traceability

| Requirements | Design artifacts | Planned evidence |
| --- | --- | --- |
| FR-001–FR-002 | interfaces mục1–3, gatesG01 | Role/ownership/public scope, no internal access from Member cookie. |
| FR-003–FR-007 | data-model mục2/3, OpenAPI creates/patches, interfaces mục2 | Three program types, unique normalized code, immutable fields, validation/lost-response replay. |
| FR-008–FR-010 | quota locks, revisions/registry, data-model mục4/5; US2.8–US2.9 | Usage hiện tại loại booking hủy hợp lệ nhưng giữ history; lower-limit concurrency, pause/resume/expiry vs existing holds. |
| FR-011–FR-013 | OpenAPI public/member paths, webcontracts | Cards/detail limits/allowPoints, own wallet, save no hold, status/errors/no-store. |
| FR-014–FR-018 | calculation/snapshot and atomic source contract | Mixed eligible rows, floor/fixed cap, best auto, points min/cap, quote changes, paid/zero. |
| FR-019–FR-022 | booking source contract, gatesG04/G05 | Net-fee-before-deposit, single voucher cap, deposit consume only, zero reject/rollback. |
| FR-023–FR-027 | policy/wallet immutable revisions, Manager policy API | Shared balance, exact conversion, version policy change and old snapshot/no-early-use. |
| FR-028–FR-031 | earn effects/ledger and MyLoyalty | Completed+paid only, net earning/floor, once/retry, own consistent balance/history. |
| FR-032–FR-038 | lock order/effect states/source proof | Last-slot/same-wallet races, callback vs expiry/cancel, restore partial retry, no guessing unresolved. |
| FR-039–FR-040 | frontend/error/security and shared identity | React journeys over REST, revoked/current roles, no MFA in V1, no Member regression. |
| FR-041 | data-model mục7, researchD08 | Retention/ref/unfinished guards, no destructive purge, tombstones and migration preservation. |

[quickstart.md](quickstart.md) có commands, prerequisites, fixture và expected outcomes; runtime suites là kế hoạch, chưa được chạy. Documentation verification sẽ kiểm tra file links, IDs/traceability, OpenAPI syntax/refs/security/key/version coverage và whitespace.

## Complexity Tracking

Không có constitution exception mới. Registry shared/exclusive gate cần để ổn định eligible candidate set/current policy khi accept; không phải global exclusive mutex cho mọi checkout. Ledger/outcome keys trên accepted benefit và immutable snapshots trực tiếp đáp ứng double-effect/old-policy rules. Không distributed transaction/broker/cache balance/second payment owner.

## Handoff

Bước tiếp theo sau review là speckit-tasks, rồi speckit-analyze trước yêu cầu implementation riêng. [Integration gates](integration-gates.md) là điều kiện trước code/activate; spec checklist chỉ xác nhận chất lượng yêu cầu. Không coi tài liệu này hoặc câu trả lời chọn chính sách là bằng chứng code/MFA/provider đã tồn tại.

## Documentation verification performed

Ngày2026-10-24: đã resolve feature qua setup-plan, đọc template/constitution/kit và source liên quan, nghiên cứu hai nhánh read-only platform/integration, đối chiếu primary docs. Kiểm chứng ban đầu của plan xác nhận 8 tài liệu Markdown, 44 liên kết local tồn tại, 4 citations primary; spec lúc đó có 41 FR/64 acceptance scenarios (mốc cũ) và mapping đầy đủ sang plan/checklist. OpenAPI3.0.3 ban đầu có 11 paths/14 operations/33 schemas và 5 mutation idempotency keys (mốc cũ). Các số liệu này là mốc trước clarification; thay đổi resume được kiểm tra lại sau khi cập nhật. Không dùng external full OpenAPI/schema validator; structural checks không thay runtime contract tests.

Review tích hợp không thấy mâu thuẫn atomicity/lock order/lineage; đã làm rõ delivery settle trước grant và không grant khi component còn HELD. Research tổng giữ nguyên SHA-256 so với lúc bắt đầu; nhánh khuong không đổi. Không có before_plan/after_plan hooks vì không tồn tại extensions.yml; không tạo tasks/code/test/migration/deploy. git diff --check không có lỗi whitespace; cảnh báo LF/CRLF trên research tổng không phải lỗi nội dung.

Runtime/auth/MySQL migration/concurrency/Modulith/source integration tests đều NOT RUN — chưa có implementation/dependency thật. Không ghi static pass thành triển khai hoàn tất hoặc tự đóng G01–G07.

Cập nhật 2026-10-26 (đồng bộ spec): (1) giảm tự động sản phẩm luôn cho kết hợp điểm, `allowPoints` chỉ cấu hình ở voucher; (2) phạm vi sản phẩm chấp nhận sản phẩm chưa công khai/ẩn/ngừng bán, từ chối đã xóa mềm, chương trình không tự dừng khi sản phẩm bị ẩn/xóa sau đó; (3) quyết định cọc workshop B: FE-06 tính `postPromotionAmount`, 003.1 tính `deposit = ceil(post/2)` và `remaining = post - deposit`, FE-06 chỉ snapshot `postPromotionAmount`, không tính/làm tròn/lưu cọc; 003.1→003.3 truyền `frozenPostPromotionAmount`/`frozenRemainingAmount` (chốt 2026-10-26: cọc và còn lại không được lưu trong FE-06, V8 bỏ hai cột tương ứng). (4) thiết kế dữ liệu gọn còn 10 bảng: quota chương trình gộp vào `promotion_program`; quota theo Member và voucher đã lưu gộp thành `promotion_member_program`; effect gộp vào `loyalty_accepted_benefit` (state + outcome key có version) và ledger unique (acceptedBenefitId, entryKind); command receipt gộp vào `loyalty_audit`. Giữ riêng `loyalty_registry`, `promotion_revision` và `promotion_revision_target` để bảo toàn gate khóa, điều khoản bất biến và index tra cứu. Các thay đổi này đã phản ánh ở spec, data-model, interfaces, quickstart, tasks; cần duyệt lại baseline vì phát sinh sau ngày Approved.

Clarification session2026-10-24 chốt2 câu A/A: mở lại cùng program/code không reset quota/history, và report hiện tại loại booking đã hủy hợp lệ nhưng giữ lịch sử. Đã đồng bộ spec FR-008/FR-009, US2.1/US2.8/US2.9, Edge Cases/SC-001, model, research, interfaces, OpenAPI và quickstart; không thay các quyết định đã chốt khác. Số liệu hiện hành sau cập nhật (thay các mốc cũ ở trên):41 FR,66 acceptance scenarios,12 API paths/15 operations/34 schemas,6 mutations có command key; checklist re-evaluate giữ16/16, không sửa nội dung/checkmarks không đổi của checklist. Không còn câu hỏi nghiệp vụ cần clarify; benchmark/SLA production và implementation evidence vẫn theo phạm vi plan/gates.

