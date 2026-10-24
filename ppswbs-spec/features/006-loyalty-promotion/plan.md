# Implementation Plan: Loyalty & Promotion Management

**Date created**: 2026-10-24
**Date updated**: 2026-10-24
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Feature context**: `006-loyalty-promotion` | **Spec**: [spec.md](spec.md)  
**Git branch**: `khuong` giữ nguyên. `006-loyalty-promotion` là feature context của SpecKit, không phải nhánh Git được tạo trong tác vụ.  
**Status**: Draft technical design — bộ tài liệu Phase0/Phase1; chưa triển khai hoặc phê duyệt code/deploy.  
**Input**: FE-06 và quyết định1–10, bộ thông số điểm đã chốt; xác nhận bổ sung khi plan về retention tối thiểu5 năm và shared auth/session fixed8h (tính từ auth_time cho React SPA). Chỉ soạn thiết kế.

## Summary

Thêm module loyalty cho voucher sản phẩm/workshop, giảm tự động sản phẩm và điểm chung. Member kiếm/dùng điểm cả hai dịch vụ, không hạng. Giữ stack modular monolith; JPA transaction cùng source owner để accept+reserve nguyên tử, owner xác minh lifecycle rồi gọi settle/release/restore/earn. Immutable policy/program revisions giữ cam kết cũ, wallet/quota locks và receipts/ledger bảo đảm không dùng/trả/cộng trùng.

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
| Constraints | Current local roles/ownership, privileged assurance từ FE01, shared8h fixed session tính từ auth_time, UI React chỉ qua REST, no raw SQL application, không reset dữ liệu, không points expiry/tiers/retail returns. |
| Scale/Scope | Ba loại chương trình, một wallet/Member, một chương trình/retail hoặc voucher/booking; immutable revisions, component-level receipts. Tổng số Member/transactions production không phải giả định để tự đặt SLA. |

## Constitution Check

Đối chiếu trước research và sau Phase1; PASS DESIGN không phải runtime evidence.

| Principle/gate | Before research | After design | Evidence/condition |
| --- | --- | --- | --- |
| I. Closed modules/ownership | PASS | PASS DESIGN | loyalty owns state, public facade only; source→loyalty, loyalty→members; target adapters qua SPI. Không common business logic. |
| I. JPA/Flyway | PASS | PASS DESIGN | JPA locks, append-only migrations, exact numbers; allocate migration number tại implementation, không sửaV1–V3. |
| I. Acyclic integration | REVIEW REQUIRED | PASS DESIGN / PROVIDERS OPEN | [interfaces](contracts/interfaces.md); actual verify cần implementation và source adapters. Không suy cycle từ stale allowedDependencies. |
| II. Identity/authority | DEPENDENCY OPEN | DECISION CONFIRMED / CODE OPEN | User chốt sharedFE01/001/session8h (tính từ auth_time cho SPA); MemberFacade chưa có capabilities/assurance. Fail closed, không ROLE_MEMBER/claims/permitAll fallback. |
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
  loyalty/
    facade/          LoyaltyFacade, PromotionTargetPort, immutable inputs/results
    web/             scoped API/view controllers, strict DTOs, safe errors
    application/     program/policy commands, quote/hold/effects, authorization
    domain/          programs/revisions, quotas, wallet, benefits, ledger/receipts/audit
    infrastructure/  JPA repositories and mappings
  members/facade/     dependency: verified actor/current capability/authorization guard
  catalogue/         dependency adapter for product target validation
  workshopbooking/   source adapter for workshop target and accepted lifecycle
  readyringsales/    source adapter for retail checkout/paid/zero/expiry
  configuration/    route-scoped API security/CORS wiring
ppswbs_backend/src/main/resources/
  db/migration/      new additive migration, number allocated at implementation
ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/
  loyalty/           future calculation, lifecycle, contract, security, MySQL checks
ppswbs_frontend/      (chưa khởi tạo; vị trí dự kiến)
  src/features/loyalty/  promotions, wallet/account, management, policy screens và checkout components
```

**Structure Decision**: Module riêng theo ownership FE06, không nhét vào billing/common hoặc dùng frontend-only local storage cho điểm. Danh sách source tree là vị trí dự kiến, chưa tạo code.

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
- Khi source cần bridge outcome đã commit/legacy async, durable source-owned delivery receipt là prerequisite; consumer gọi cùng facade idempotently. Không in-memory notification làm bằng chứng, không loyalty→source polling/cycle.
- Source cần đối soát thì giữ unresolved, không unlock vì hết deadline; không sửa original deadline. Xem [transaction contract](contracts/interfaces.md#transaction-and-lock-order).

### Security and web

- API bearer đã verify/revocation; private responses no-store. Frontend React dùng cùng bearer, phiên identity fixed8h tính từ auth_time, current role/ownership, privileged assurance; không làm login/MFA riêng.
- Identity chưa đủ authority thì fail closed. Technical management chains không thừa hưởng permitAll hiện tại; không thay global security hoặc Member journeys từ feature này.
- Manager forms quản lí/sửa/dừng/mở lại programs; Admin policy; Member kho/My Loyalty; checkout components do màn hình của owner FE-03/FE-08 nhúng. Resume PAUSED→ENABLED dùng cùng quyền, registry WRITE lock, command UUID/expectedVersion và audit; giữ quota/history, không gia hạn hoặc đổi accepted snapshots. React gọi REST với field/conflict errors theo typed error, điều hướng client sau thành công.
- Không public reserve/paid/completed/earn/refund endpoint. API input strict, safe correlation errors; không log full source proof/token.

### Data, calculation and revisions

[Data model](data-model.md) mô tả fields, constraints, transitions, indices, ledger deltas và formulas. Eligible subtotal dùng xét ngưỡng/voucher, toàn net hàng/phí gói dùng trần điểm/earn sau điểm, shipping ngoài cả hai. Program/policy revisions immutable, counters mutable guarded. Mỗi giao dịch Member accepted có policy snapshot kể cả không dùng ưu đãi/points; không suy snapshot từ config hiện hành khi grant.

### Migration, retention and operations

- Thêm migration mới sau kiểm tra version folder hiện tại, không lấy sốfeature006 làm version, không sửa/drop/resetV1–V3 hoặc retro-credit source cũ thiếu facts.
- MySQL8.4 empty + upgrade populated baseline; FK nội loyalty, cross-module logical refs; test decimals/collation/unique/check constraints/locks thật. Không chỉ H2.
- History/audit/results tối thiểu5 năm, source chưa terminal/refs vẫn protected; không purge v1, không xóa wallet/code registry/dedup keys để mở replay/reuse. Không chứa raw credential/PII không cần thiết.
- Theo dõi correlation, command/sourceRef, conflict/lock timeout, delivery backlog, imbalance invariant errors và component retry; không tạo consumer tự quyết business outcome.
- Rollout sau owner adapters thật + spec/plan/tasks review + analyze và required tests; disable loyalty entrypoints khi cần recovery, giữ schema/ledger/receipts và xử lí cam kết đã chấp nhận qua owner. Không toggle off để release/restore hàng loạt, không down migration.
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
| FR-023–FR-027 | policy/wallet immutable revisions, Admin API | Shared balance, exact conversion, version policy change and old snapshot/no-early-use. |
| FR-028–FR-031 | earn effects/ledger and MyLoyalty | Completed+paid only, net earning/floor, once/retry, own consistent balance/history. |
| FR-032–FR-038 | lock order/effect states/source proof | Last-slot/same-wallet races, callback vs expiry/cancel, restore partial retry, no guessing unresolved. |
| FR-039–FR-040 | frontend/error/security and shared identity | React journeys over REST, fixed8h/revoked/current roles, privileged assurance, no Member regression. |
| FR-041 | data-model mục7, researchD08 | Retention/ref/unfinished guards, no destructive purge, tombstones and migration preservation. |

[quickstart.md](quickstart.md) có commands, prerequisites, fixture và expected outcomes; runtime suites là kế hoạch, chưa được chạy. Documentation verification sẽ kiểm tra file links, IDs/traceability, OpenAPI syntax/refs/security/key/version coverage và whitespace.

## Complexity Tracking

Không có constitution exception mới. Registry shared/exclusive gate cần để ổn định eligible candidate set/current policy khi accept; không phải global exclusive mutex cho mọi checkout. Ledger/component receipts và immutable snapshots trực tiếp đáp ứng double-effect/old-policy rules. Không distributed transaction/broker/cache balance/second payment owner.

## Handoff

Bước tiếp theo sau review là speckit-tasks, rồi speckit-analyze trước yêu cầu implementation riêng. [Integration gates](integration-gates.md) là điều kiện trước code/activate; spec checklist chỉ xác nhận chất lượng yêu cầu. Không coi tài liệu này hoặc câu trả lời chọn chính sách là bằng chứng code/MFA/provider đã tồn tại.

## Documentation verification performed

Ngày2026-10-24: đã resolve feature qua setup-plan, đọc template/constitution/kit và source liên quan, nghiên cứu hai nhánh read-only platform/integration, đối chiếu primary docs. Kiểm chứng ban đầu của plan xác nhận 8 tài liệu Markdown, 44 liên kết local tồn tại, 4 citations primary; spec có 41 FR/64 acceptance scenarios và mapping đầy đủ sang plan/checklist. OpenAPI3.0.3 ban đầu có 11 paths/14 operations/33 schemas và 5 mutation idempotency keys. Các số liệu này là mốc trước clarification; thay đổi resume được kiểm tra lại sau khi cập nhật. Không dùng external full OpenAPI/schema validator; structural checks không thay runtime contract tests.

Review tích hợp không thấy mâu thuẫn atomicity/lock order/lineage; đã làm rõ delivery settle trước grant và không grant khi component còn HELD. Research tổng giữ nguyên SHA-256 so với lúc bắt đầu; nhánh khuong không đổi. Không có before_plan/after_plan hooks vì không tồn tại extensions.yml; không tạo tasks/code/test/migration/deploy. git diff --check không có lỗi whitespace; cảnh báo LF/CRLF trên research tổng không phải lỗi nội dung.

Runtime/auth/MySQL migration/concurrency/Modulith/source integration tests đều NOT RUN — chưa có implementation/dependency thật. Không ghi static pass thành triển khai hoàn tất hoặc tự đóng G01–G07.

Clarification session2026-10-24 chốt2 câu A/A: mở lại cùng program/code không reset quota/history, và report hiện tại loại booking đã hủy hợp lệ nhưng giữ lịch sử. Đã đồng bộ spec FR-008/FR-009, US2.1/US2.8/US2.9, Edge Cases/SC-001, model, research, interfaces, OpenAPI và quickstart; không thay các quyết định đã chốt khác. Sau cập nhật:41 FR,66 acceptance scenarios,12 API paths/15 operations/34 schemas,6 mutations có command key; checklist re-evaluate giữ16/16, không sửa nội dung/checkmarks không đổi của checklist. Không còn câu hỏi nghiệp vụ cần clarify; benchmark/SLA production và implementation evidence vẫn theo phạm vi plan/gates.

