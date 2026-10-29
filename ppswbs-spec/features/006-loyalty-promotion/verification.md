# Verification — FE-06 Loyalty & Promotion

**Date created**: 2026-10-26
**Date updated**: 2026-10-29 (mục 20: build thật sau khi develop sửa lỗi biên dịch và số migration; mục 19: test contract API T071)
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Feature**: `006-loyalty-promotion`  
**Trạng thái**: Đang triển khai — backend 006 đã có code, test và migration V10–V12 (chưa commit); bằng chứng runtime ở mục 8–18 chạy trên **build tạm** (BE-DESIGN-0011). Mục 1–7 là kết quả lúc lập tài liệu, giữ làm lịch sử.  
**Mục đích**: ghi kết quả T001–T003 (nguồn, review, analyze, readiness) theo [tasks.md](tasks.md). File này không phải phê duyệt và không cấp quyền code/deploy.

## 1. Nguồn đã đối chiếu (T001)

| Hạng mục | Kết quả |
| --- | --- |
| Spec | 41 FR, 66 acceptance scenarios, 8 SC. Duyệt lại 2026-10-26 theo chỉ thị của Đặng Thị Khương trong phiên làm việc. |
| Contract | OpenAPI 15 operations; interfaces.md; data-model.md thiết kế 10 bảng. |
| Tasks | 78 task (T001–T078), 26 task [P], không tham chiếu thiếu, không vòng phụ thuộc. |
| Constitution | v3.0.0 — không vi phạm (route `/api/v1/...`, React + Vite tách riêng, JPA/Flyway, auth theo FE-01). |
| Nhánh | `khuong`; không đổi nhánh, không commit/push. |
| Migration hiện có | `V5__account_management.sql` (001) và `V6__product_management.sql` (002, chưa commit). Số version của 006 phải được cấp tại T016 theo quy ước nhóm (xem mục 4). |

### 1.1 Đối chiếu lại nguồn (T001, 2026-10-27)

| Hạng mục | Kết quả |
| --- | --- |
| Nhánh, commit | `khuong` tại `73ac306` (merge `origin/develop` `4eaacaa`), cộng thay đổi chưa commit của 006. `origin/develop` hiện tại `37d68cf`, chưa ghép. |
| Stack | Spring Boot 3.4.1, Java 21, Spring Modulith 1.3.1, Flyway, MySQL 8.4 cục bộ (theo `ppswbs_backend/pom.xml`; không đổi version). |
| Migration trên `khuong` | V4 (004), V5, V6 (001), V7, V8 (003.2), V9 (002), V10, V11, V12 (006; V12 mới, chưa commit, cần báo nhóm). |
| Đếm lại | 41 FR, 66 acceptance scenario, 15 operation trong OpenAPI. |
| SHA-256 (16 ký tự đầu, lấy trước các sửa đổi tài liệu của lượt này) | spec.md `927de4e7acb197e1` (sau khi sửa `Date updated`: `65b4883bd833f614`); plan.md `95dfed8224f3ed50`; data-model.md `8457c7c3e6f47e84`; contracts/interfaces.md `11a66c3ca58da6e8`; contracts/loyalty-promotion.openapi.json `a04c8693d973d95a`; tasks.md `04dcc57930378947`; integration-gates.md `496c2ad10fe16978`; `.specify/memory/constitution.md` `dddab3ef5f7c7b10`. |
| Ngoài phạm vi | Code và cấu hình của 001, 002, 003, 004, 008; research tổng; frontend (chưa có, T077). |

## 2. Thay đổi sau lần duyệt 2026-10-24

1. Clarification 2026-10-26: giảm tự động luôn kết hợp điểm; phạm vi sản phẩm cho phép chưa công khai/ẩn/ngừng bán, từ chối đã xóa mềm.
2. Quyết định cọc workshop B (đồng bộ với owner 003): FE-06 tính `postPromotionAmount`; 003.1 tính `deposit = ceil(post/2)` và `remaining = post - deposit`; FE-06 chỉ snapshot `postPromotionAmount`, không tính/lưu cọc và còn lại (chốt 2026-10-26, V8 bỏ hai cột). Tên trường `frozenPostPromotionAmount`/`frozenRemainingAmount` là của 003, lấy từ tin nhắn của owner 003, **chưa xác nhận trong tài liệu 003** (không còn ảnh hưởng thiết kế dữ liệu của 006); bản `003.1-workshop-booking/spec.md` trong working tree này vẫn ghi "V1 MUST NOT apply a deposit-rounding rule" (thuộc owner 003, không chặn 006 nhưng cần đối chiếu khi merge).
3. Data model giảm từ 14 xuống 10 bảng (xem [plan.md](plan.md), đoạn "Cập nhật 2026-10-26").

## 3. Kết quả speckit-analyze (T003)

Lần 1 (2026-10-26, sau thay đổi): 0 CRITICAL, 3 HIGH, 4 MEDIUM, 2 LOW. F1, F4, F5, F7, F6, F8, F10 đã xử lý; F9 đã thỏa sẵn; F2 thuộc owner 003; F3 xử lý bằng việc duyệt lại.  
Lần 2 (2026-10-26, sau gộp bảng): 0 CRITICAL, 0 HIGH. G1 (cụm "component receipts" lỗi thời trong plan) đã sửa; G2 (plan chưa mô tả 10 bảng) đã sửa.  
Độ phủ: 41/41 FR có task; 15/15 operations có task. Kiểm tra tĩnh: `git diff --check` chỉ báo hai dấu cách cuối dòng cố ý ở dòng Status (cú pháp xuống dòng Markdown); không có liên kết nội bộ hỏng. Không có `.specify/extensions.yml` nên không có hook.

Lần 3 (2026-10-27, rà soát spec với implementation, T003): đọc lại toàn bộ spec. Không có CRITICAL hay HIGH mới. Ghi nhận:
- Metadata spec: nội dung đã có quyết định ngày 2026-10-27 (US8.7, FR-040, A-006) nhưng `Date updated` còn 2026-10-26; đã sửa thành 2026-10-27.
- Đã biết, không đổi: PATCH chương trình không cho đổi `targets` theo spec (OpenAPI cho phép); voucher đã lưu là cột `saved_at` trên `promotion_member_program` thay cho entity `SavedVoucher` của T067 (thiết kế 10 bảng).
- Giới hạn còn mở: FR-011 với chương trình workshop chưa lọc được phần công khai vì chưa có provider target WORKSHOP (G-02); FR-018, FR-021, FR-028, FR-033–FR-038 mới kiểm chứng phía loyalty, chưa có nguồn thật (G-03–G-05); FR-039, SC-008 chưa có UI (T077).
- Thứ tự quy trình: T001–T003 được ghi bổ sung sau khi đã triển khai T004 trở đi; bản ghi này không thay cho gate trước code.

Readiness cho phần còn lại: T054, T057, T059, T060, T062, T064, T065 và phần nguồn thật của T063 **KHÔNG ĐẠT** (R07: G-03–G-05 chưa có owner adapter); T030, T034, T040, T047, T055, T061, T070, T078 **KHÔNG ĐẠT** (R15: T077 chặn); T071–T076 phụ thuộc các task trên.

## 4. Readiness (theo [readiness-checklist](../../agent-design-kit/readiness-checklist.md))

Phạm vi đánh giá: nhóm task chưa phụ thuộc hệ thống ngoài — T001–T006, T008–T014, T019–T020 (module, kiểu giá trị, entity, công thức tính). Các task còn lại bị chặn bởi dependency bên ngoài và được đánh giá riêng khi dependency đạt.

| Tiêu chí | Kết quả | Nguồn / lý do |
| --- | --- | --- |
| R01 | ĐẠT | Công việc tài liệu tách khỏi triển khai; phạm vi nêu ở trên. |
| R02 | ĐẠT | Đã đọc đủ README, implementation-design-template, readiness-checklist, blocker-report-template của kit. |
| R03 | ĐẠT | Nguồn: spec/plan/data-model/interfaces của 006, constitution v3.0.0. |
| R04 | ĐẠT CÓ ĐIỀU KIỆN | Bằng chứng duyệt là chỉ thị trong phiên 2026-10-26 của người phụ trách; không có bản ghi duyệt ngoài phiên. |
| R05, R06 | ĐẠT | Traceability FR → task trong [tasks.md](tasks.md); acceptance có số liệu cụ thể. |
| R07 | ĐẠT CÓ ĐIỀU KIỆN | Thứ tự dependency rõ; G01–G05 chưa có provider thật (xem [integration-gates.md](integration-gates.md)). |
| R08–R14 | ĐẠT cho phạm vi đánh giá | Luồng, contract, quyền, schema, concurrency, tích hợp đã thiết kế ở spec/data-model/interfaces; chưa chạy. |
| R12 | ĐẠT CÓ ĐIỀU KIỆN cho T015–T016 | Số migration đã chốt `V7__loyalty_promotion.sql` (Đặng Thị Khương xác nhận 2026-10-26; V7 chưa bị chiếm trên `khuong` và `origin/develop`); chưa có xác nhận của owner feature khác; file chưa được tạo. |
| R15 | KHÔNG ÁP DỤNG | Nhóm task phạm vi này không có UI. |
| R16 | KHÔNG ĐẠT cho T007, T017 trở đi | Cấu hình security dùng chung còn blocker (mục 5). |
| R17 | ĐẠT | Analyze lần 2: 0 CRITICAL/HIGH. |
| R18 | ĐẠT CÓ ĐIỀU KIỆN | Ranh giới cọc với 003 đã xác nhận (mục 8.3); MFA/assurance không áp dụng ở V1 (theo 001, chốt 2026-10-27). Không chặn nhóm task phạm vi. |
| R19, R20 | ĐẠT | Kế hoạch kiểm chứng ở [quickstart.md](quickstart.md); tên bảng/cột/khóa được coi là contract. |

## 5. Blocker và giới hạn còn mở

Nguồn: [ERROR-LOG.md](../../../ppswbs_backend/ERROR-LOG.md) (đọc ngày 2026-10-26).

- **BE-DESIGN-0005 (trước là 0001) mục 1–2 (BLOCKED):** security chain dùng chung của 001 là `permitAll` và còn mật khẩu mặc định trong file cấu hình. Ảnh hưởng 006 ở T007 và T017 trở đi (cần security route-scoped).
- **BE-DESIGN-0005 (trước là 0001) mục 3 (đã chốt cho 006):** 006 dùng `V7__loyalty_promotion.sql`, một file cho 10 bảng kèm seed registry và policy. Cần báo lại nếu feature khác giành số 7 trước khi tạo file ở T016.
- **BE-DESIGN-0006 (trước là 0002):** `AccountFacade` của 001 chưa đủ; ảnh hưởng T007 của 006 tương tự 002.
- **Dependency ngoài:** G01 (FE-01 identity), G02 (T091 của feature 002), G03 (FE-08), G04/G05 (FE-03), T077 (frontend của 002).

## 6. Phản biện giả định (theo AGENT §1)

- Giả định "gộp bảng không mất nghiệp vụ": đã đối chiếu từng FR chống trùng (FR-030, FR-036), giữ cam kết cũ (FR-010, FR-027) và báo cáo hủy (FR-008) với cột/unique mới; **chưa chứng minh trên MySQL**. Hai điểm phải test: lưu voucher đồng thời với giữ lượt cùng bảng `promotion_member_program`; gửi lặp kết quả hủy dựa trên outcome key.
- Giả định "không cần bảng effect riêng": đánh đổi mất lịch sử từng hành động chi tiết; lịch sử còn được giữ qua ledger (điểm), cột thời điểm voucher và audit.
- Giả định "003 sẽ nhận `ceil`": chưa có bằng chứng trong tài liệu 003 trên nhánh này.

## 7. Kết luận

**CÔNG VIỆC TÀI LIỆU ĐÃ SOẠN/REVIEW.** Nhóm task độc lập (T001–T006, T008–T014, T019–T020) đạt điều kiện nhận việc nếu người phụ trách giao rõ phạm vi này cho giai đoạn code theo [AGENT backend](../../../ppswbs_backend/AGENT.md). Các task còn lại BỊ CHẶN theo mục 5 cho tới khi dependency và blocker tương ứng được giải quyết và gate kiểm tra lại. Kết luận này không thay phê duyệt, không chứng minh code hay test. Runtime, migration, Modulith, MySQL, concurrency và UI: **NOT RUN**.

## 8. Bằng chứng triển khai (2026-10-26)

Môi trường: MySQL 8.4 cục bộ (`localhost:3306`), JDK 21, `mvnw`; lệnh `.\mvnw.cmd -q test "-Dtest=Loyalty*,ModulithArchitectureTests"`. Kết quả đọc từ `target/surefire-reports`.

| Kiểm tra | Kết quả |
| --- | --- |
| Test đơn vị (T019, T020, T006, T008, entity) | 21 + 12 + 3 + 22 = 58 đạt, 0 lỗi |
| `LoyaltyMySqlMigrationIT` (T015) trên database trống `ppswbs_loyalty_scratch` (V5, V6, V7 từ đầu) | 18/18 đạt |
| `LoyaltyMySqlMigrationIT` trên `ppswbs_test` đã có dữ liệu (upgrade) | 18/18 đạt; Flyway "applied 1 migration, now at v7" |
| Bảo toàn dữ liệu 001/002 khi áp V7 | Trước và sau: `accounts`=1, `account_profiles`=0, `catalogue_products`=499, `catalogue_variants`=950, `catalogue_categories`=603; flyway 5:1, 6:1 giữ nguyên, thêm 7:1 |
| `ModulithArchitectureTests` | 1/1 đạt, `loyaltypromotion` nhận diện là module riêng |
| Lần chạy đầu của T015 trước khi có V7 | Thất bại đúng kỳ vọng (bảng chưa tồn tại) |

Giới hạn: bằng chứng cưỡng chế ràng buộc của cơ sở dữ liệu (unique, check, khóa ngoại RESTRICT, binary code key, singleton registry) và ánh xạ 10 entity; chưa kiểm tra bất biến ở mức cơ sở dữ liệu của các cột "chỉ ghi một lần" (do entity `updatable=false` bảo vệ), chưa có test đồng thời/khóa (T063), chưa có repository hay service. `ppswbs_loyalty_scratch` là database tạm do lượt kiểm tra này tạo, có thể xóa.

### 8.1 Điều chỉnh sau khi chốt ranh giới cọc (2026-10-26)

Đặng Thị Khương chốt: cọc và số còn lại thuộc FE-03/003.1; FE-06 chỉ tính, trả và lưu `postPromotionAmount`. Đã bỏ phương thức `recordWorkshopPayable`, thêm `V8__loyalty_drop_workshop_payable.sql` bỏ hai cột `deposit_amount`, `remaining_amount` và đặt check mới (workshop có `post_promotion_amount` >= 1, retail không có), đồng bộ spec, data-model, interfaces, quickstart, plan, tasks.

| Kiểm tra | Kết quả |
| --- | --- |
| 77 test của 006 (21 công thức, 12 giá trị, 3 xử lý lỗi, 22 entity, 19 migration) + Modulith | Đạt trên `ppswbs_loyalty_scratch` (V7 lên V8) |
| `LoyaltyMySqlMigrationIT` trên `ppswbs_test` | 19/19 đạt; Flyway "applied 1 migration, now at v8"; flyway 5:1, 6:1, 7:1, 8:1; `accounts`=1, `catalogue_products`=499, `catalogue_variants`=950, `catalogue_categories`=603 giữ nguyên |

### 8.2 Đổi số migration trước khi ghép `develop` (2026-10-27)

`origin/develop` (`07fa8cf`) đã dùng `V6__account_schema_alignment.sql` (001), `V7__workshop_operations.sql` và `V8__workshop_operations_idempotency_ledger.sql` (003.2). Hai migration của 006 chưa commit nên được đổi số, nội dung SQL giữ nguyên: `V7__loyalty_promotion.sql` → **`V10__loyalty_promotion.sql`**, `V8__loyalty_drop_workshop_payable.sql` → **`V11__loyalty_drop_workshop_payable.sql`** (002 đổi `V6` → `V9`). Các bằng chứng ở mục 8 và 8.1 ghi theo số cũ là kết quả lịch sử, không chạy lại với số mới. `LoyaltyMySqlMigrationIT` đổi kỳ vọng thứ tự thành `5, 9, 10, 11`. Mã lỗi backend đổi: `BE-DESIGN-0001` → `BE-DESIGN-0005`, `BE-DESIGN-0002` → `BE-DESIGN-0006` (develop đã có 0001–0004). Cần báo nhóm số V9–V11 và tạo lại database test sau khi ghép.

### 8.3 Xác nhận ranh giới cọc với 003 (2026-10-27)

Câu "FE-006 phải gửi/snapshot ba giá trị này" trong yêu cầu của owner 003 là nhầm chủ ngữ. Đặng Thị Khương xác nhận lại với owner 003: FE-06 chỉ gửi và lưu `postPromotionAmount`; `deposit = ceil(post/2)` và `remaining` do 003.1 tính, 003 tự giữ (`frozenPostPromotionAmount`, `frozenRemainingAmount`). Khớp với spec, data-model, interfaces và V11 hiện tại, không cần sửa thêm. Xác nhận này được người phụ trách 006 chuyển lại, không phải bản ghi của owner 003; tên trường `frozen*` vẫn nằm ngoài thiết kế dữ liệu của 006.

## 9. Bằng chứng T007, T017, T018, T021–T023 trên build tạm (2026-10-27)

**Build tạm, không phải build thật của develop.** `origin/develop` không biên dịch sạch (BE-DESIGN-0011). Kiểm chứng chạy bằng một `pom.xml` riêng đặt ngoài repo (scratchpad của phiên, không commit): trỏ tới đúng `src/main` và `src/test` của `ppswbs_backend`, loại khỏi biên dịch `designreview/**`, `workshopbooking/**`, `configuration/FirebaseAuthenticationFilter.java`, `configuration/DefaultFirebaseTokenVerifier.java`, test `designreview/**` và `support/TestFirebaseTokenVerifier.java`, và loại migration `V4__ai_chatbox_and_design_review.sql` (của 004, tham chiếu bảng `members` không tồn tại nên không chạy được trên database trống). Không sửa hay xóa file nào của feature khác. Phải chạy lại trên build thật khi develop đã sửa.

Lệnh: `.\mvnw.cmd -q clean test "-Dtest=edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.**,ModulithArchitectureTests"` với `LOYALTY_TEST_JDBC_URL` trỏ tới database trống mới `ppswbs_loyalty_build3` trên MySQL 8.4 cục bộ. Flyway: "Successfully applied 7 migrations ..., now at version v11" (V5–V11). Thoát mã 0.

| Test | Kết quả |
| --- | --- |
| `LoyaltyAuthorizationTests` (T007) | 8/8 |
| `LoyaltySecurityIT` (T007) | 9 đạt, 1 bỏ qua (`@Disabled`, BE-DESIGN-0012: preflight CORS dùng chung trả 403 cả với origin đã cấu hình) |
| `service/LoyaltyLifecycleTest` (T021) | 14/14 |
| `LoyaltyCancellationIT` (T021, MySQL thật, khóa và rollback thật) | 6/6 |
| `LoyaltyMySqlMigrationIT` (V10, V11 trên database trống) | 19/19 |
| `LoyaltyCalculationTest`, `LoyaltyValuesTest`, `LoyaltyEntityRulesTests`, `LoyaltyExceptionHandlerTests` | 21/21, 12/12, 22/22, 3/3 |
| `ModulithArchitectureTests` | 2/2 |
| Tổng | 117 chạy, 116 đạt, 1 bỏ qua, 0 lỗi |

Ghi chú quy trình: T022, T023 được viết trước test T021 trong lượt này (không đúng thứ tự viết test trước), nên không có lần ghi nhận test thất bại ban đầu cho hai service này. `LoyaltyCancellationIT` lần chạy đầu thất bại 3/6 do lỗi của chính fixture (ví mở bằng `saveAndFlush` có id gán sẵn trả về bản managed khác), đã sửa fixture, không sửa code sản phẩm.

Không áp dụng: MFA/privileged assurance (V1 không MFA theo 001, chốt 2026-10-27). Chưa có: controller, quote/reserve/settle/earn, repository query cho báo cáo, test đồng thời (T063).

## 10. Bằng chứng US1 T024–T029 trên build tạm (2026-10-27)

Cùng build tạm và cách loại trừ như mục 9 (BE-DESIGN-0011). Database trống mới `ppswbs_loyalty_build4`, Flyway V5–V11. Lệnh: `.\mvnw.cmd -q clean test "-Dtest=edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.**,ModulithArchitectureTests"`.

| Test | Kết quả |
| --- | --- |
| `PromotionCreateContractIT` (T024: tạo, đọc, danh sách qua chain bảo mật thật, identity gate thật của 001 chỉ thay bước kiểm chữ ký Firebase, provider sản phẩm thật của 002) | 11/11 |
| Lần chạy đầu trước khi có cài đặt | 11/11 thất bại đúng kỳ vọng |
| Các test 006 khác (mục 9) | không đổi, đều đạt; `LoyaltySecurityIT` vẫn 1 bỏ qua (BE-DESIGN-0012) |
| Tổng 006 | 128 chạy, 127 đạt, 1 bỏ qua, 0 lỗi |
| `LoyaltyProductTargetAdapterIT` của 002 (provider PRODUCT, G-02), chạy với `PM_TEST_JDBC_URL` trỏ tới database mới | 6/6 |

Đã kiểm chứng: Manager ACTIVE tạo voucher sản phẩm và giảm tự động; mã trùng sau trim/không phân biệt hoa thường trả 409 CODE_EXISTS; giảm tự động luôn cho điểm (false hoặc có code/hạn mức thì 400); voucher workshop trả 503 DEPENDENCY_UNAVAILABLE vì chưa có provider workshop của FE-03; target không tồn tại, đã xóa mềm hoặc biến thể của sản phẩm khác trả 400, sản phẩm chưa công khai vẫn hợp lệ; Member, Staff, Admin trả 403, không bearer trả 401; cùng Idempotency-Key cùng nội dung trả lại đúng chương trình, khác nội dung 409; phân trang ổn định theo cursor, giới hạn 1–100.

Ghi chú: `AccountExceptionHandler` của 001 khai báo cho cả package ứng dụng nên chặn lỗi của feature khác thành 500; `LoyaltyExceptionHandler` được đặt `@Order(HIGHEST_PRECEDENCE)` giống cách 002 đã làm. T030 (màn hình React) chưa làm: chờ T077 (frontend chung của 002).

## 11. Bằng chứng US8 T031–T033 trên build tạm (2026-10-27)

Cùng build tạm như mục 9 (BE-DESIGN-0011). Database trống mới `ppswbs_loyalty_build5`, Flyway V5–V11. Lệnh như mục 10.

| Test | Kết quả |
| --- | --- |
| `LoyaltyPolicyContractIT` (T031) | 6/6; lần chạy đầu trước khi có cài đặt 6/6 thất bại đúng kỳ vọng |
| Tổng 006 | 134 chạy, 133 đạt, 1 bỏ qua (BE-DESIGN-0012), 0 lỗi |

Đã kiểm chứng: chính sách hiện hành là 10000/100/10/10%, làm tròn FLOOR; Manager cập nhật tạo revision mới, trỏ registry sang revision đó, revision cũ giữ nguyên và giao dịch đã chấp nhận vẫn trỏ chính sách cũ; giá trị không dương, tối thiểu không nguyên, phần trăm 0 hoặc trên 100 hoặc quá 6 số lẻ, trường lạ (kể cả `roundingMode`), thiếu trường, số gửi dạng JSON number đều 400 và chính sách hiện hành không đổi; biên 1/1/1/100% được nhận; `expectedVersion` cũ trả 409 STALE_VERSION; cùng key cùng nội dung trả lại, khác nội dung 409; không bearer 401; Admin, Staff, Member 403 cả khi đọc lẫn khi sửa.

`version` của chính sách là số revision; `expectedVersion` gửi lên so với số này. Test tự đưa chính sách về 10000/100/10/10 sau mỗi test (bằng một revision mới) để không ảnh hưởng test khác trên cùng database. T034 (màn hình React) chưa làm: chờ T077.

## 12. Bằng chứng US6 T035–T039 trên build tạm (2026-10-27)

Cùng build tạm như mục 9 (BE-DESIGN-0011). Database trống mới `ppswbs_loyalty_build6`, Flyway V5–V11. Lệnh như mục 10.

| Test | Kết quả |
| --- | --- |
| `LoyaltyEarnIT` (T035, MySQL thật) | 9/9; lần chạy đầu biên dịch thất bại đúng kỳ vọng (chưa có `LoyaltyEarnService`) |
| `LoyaltyAccountContractIT` (T036, qua chain bảo mật và identity gate thật) | 6/6 |
| Tổng 006 | 149 chạy, 148 đạt, 1 bỏ qua (BE-DESIGN-0012), 0 lỗi |

Đã kiểm chứng: đơn bán lẻ trả đủ được 73 điểm trên 730.000đ đã trả (không tính phí giao), một lần; kết quả 0 điểm ghi GRANTED nhưng không có dòng sổ cái; workshop không tích khi mới đặt cọc, chỉ tích 88 điểm khi hoàn thành và trả đủ; còn điểm đang HELD thì không cộng; tỷ lệ tích là chính sách lúc chấp nhận (20.000đ/điểm cho 44 điểm) dù chính sách hiện hành khác; no-show hoặc hết hạn chỉ đóng phần tích, không hoàn quyền lợi đã dùng; outcome sai loại trả xung đột; gọi ngoài giao dịch của owner bị từ chối; nguồn không tồn tại không tạo ví. My Loyalty: số dư 200, giữ 50, khả dụng 150 kèm chính sách hiện hành; Member chưa có ví đọc 0 và không tạo ví; lịch sử chỉ của chính mình, mới nhất trước, phân trang bằng cursor; tham số `memberId` gửi lên bị bỏ qua; Manager, Admin, Staff 403, không bearer 401; `limit` ngoài 1–100 hoặc cursor hỏng trả 400.

Chưa có: dịch vụ settle (T052, US4), nên test tích điểm dựng trạng thái CONSUMED trực tiếp trên entity. T040 (màn hình React) chờ T077.

## 13. Bằng chứng US2 T041–T046 trên build tạm (2026-10-27)

Cùng build tạm như mục 9 (BE-DESIGN-0011). Database trống mới `ppswbs_loyalty_build7`, Flyway V5–V11. Lệnh như mục 10.

| Test | Kết quả |
| --- | --- |
| `PromotionManagementContractIT` (T041) | 11/11 |
| `PromotionUsageIT` (T042, dùng dịch vụ restore thật) | 4/4; lần chạy đầu biên dịch thất bại đúng kỳ vọng (chưa có `PromotionUsageDtos`, `PromotionUsageQueryService`) |
| Tổng 006 | 164 chạy, 163 đạt, 1 bỏ qua (BE-DESIGN-0012), 0 lỗi |

Đã kiểm chứng: sửa tạo revision mới, giữ nguyên trường không gửi, revision 1 bất biến; đổi `code`, `scope`, `kind`, `targets`, `status` hoặc gửi chỉ `expectedVersion` đều 400; giá trị sai trả 400 và chương trình không đổi; giảm tự động không nhận `allowPoints=false` hay hạn mức; `expectedVersion` cũ trả 409 STALE_VERSION; đã dùng 6 + giữ 2 thì hạ tổng hạn mức xuống 7 trả 409 QUOTA_LIMIT_CONFLICT, xuống 8 được, phần giữ không bị xóa; hạn mức mỗi Member không hạ dưới mức một Member đã dùng cộng giữ; tạm dừng rồi mở lại giữ nguyên id, mã, revision, thời gian, bộ đếm; tạm dừng hai lần hoặc mở lại khi đang bật trả 409; cùng key gửi lại không tạo thêm lần chuyển trạng thái, khác nội dung 409; Member, Staff, Admin 403; không tồn tại 404. Báo cáo: phần giữ không tính là đã dùng; hủy hợp lệ loại khỏi tổng đúng một lần (gửi lặp không trừ thêm), lịch sử dùng/hoàn vẫn còn; một booking 100.000đ bị hủy cho 0 lượt và 0đ; tạm dừng không loại giao dịch đã chốt; giảm tự động chỉ đếm giao dịch đã chốt.

Hai điểm cần lưu ý về contract:
- OpenAPI `ProgramUpdate` cho phép gửi `targets` ("Targets must match original scope"), nhưng spec FR-006 và US2.2 cấm đổi phạm vi sau khi tạo. Code theo spec: `targets` trong PATCH luôn bị từ chối. Nên sửa mô tả OpenAPI cho khớp.
- `version` của chương trình là cột `@Version` của dòng `promotion_program`. Vì bộ đếm quota nằm trên cùng dòng (thiết kế 10 bảng), mỗi lần giữ hoặc chốt lượt cũng tăng `version`, nên khi đang có giao dịch, Manager có thể nhận 409 STALE_VERSION và phải tải lại. Nếu cần tránh, phải thêm cột phiên bản cấu hình riêng bằng một migration mới.

T047 (màn hình React) chờ T077.

## 14. Phiên bản cấu hình riêng cho chương trình — V12 (2026-10-27)

Quyết định của Đặng Thị Khương (owner 006): thêm cột phiên bản cấu hình để Manager sửa chương trình không bị 409 khi đang có giao dịch. `V12__loyalty_program_config_version.sql` thêm `promotion_program.config_version` (BIGINT, mặc định 0, check >= 0). Cột này chỉ tăng khi sửa, tạm dừng, mở lại và là `version`/`expectedVersion` của API; cột `version` cũ vẫn là khóa lạc quan của dòng (tăng cả khi bộ đếm đổi) và không còn lộ ra API. Số 12 chưa bị dùng trên `origin/develop` (`37d68cf`) và các nhánh `dat`, `quoc`, `son`, `thinh`, `main`, `khuong` tại thời điểm kiểm tra; cần báo nhóm trước khi ghép.

Test viết trước: 4 test mới (`salesBetweenLoadAndSaveDoNotMakeAnEditStale`, `aNewProgrammeStartsAtConfigurationVersionZero`, `programmesCarryAConfigurationVersionSeparateFromTheCounters`, thứ tự migration có 12) thất bại đúng kỳ vọng trước khi sửa code. Sau khi sửa, trên database trống mới `ppswbs_loyalty_build9` (Flyway V5–V12, build tạm như mục 9): 167 chạy, 166 đạt, 1 bỏ qua (BE-DESIGN-0012), 0 lỗi.

## 15. Bằng chứng US4 T048–T053 trên build tạm (2026-10-27)

Cùng build tạm như mục 9 (BE-DESIGN-0011). Database trống mới `ppswbs_loyalty_build10`, Flyway V5–V12.

| Test | Kết quả |
| --- | --- |
| `LoyaltyRetailQuoteTest` (T048, quy tắc thuần) | 14/14; lần đầu biên dịch thất bại đúng kỳ vọng (chưa có `RetailQuote`) |
| `LoyaltyRetailSourceIntegrationIT` (T049, qua `LoyaltyFacade` trên MySQL thật) | 9/9 |
| Tổng 006 | 190 chạy, 189 đạt, 1 bỏ qua (BE-DESIGN-0012), 0 lỗi |

Đã kiểm chứng: phần đủ điều kiện chỉ gồm dòng trong phạm vi (600.000 trong 800.000), voucher 10% trần 50.000; ngưỡng 700.000 không đạt dù cả giỏ 800.000; giảm cố định một lần, không vượt phần đủ điều kiện; phạm vi theo size thu hẹp dòng; nhiều giảm tự động chọn khoản lớn nhất, bằng nhau chọn chương trình tạo trước; voucher thay giảm tự động, không cộng dồn; 200 điểm = 20.000đ, cơ sở tích 730.000; dùng điểm khi không có chương trình; giảm tự động luôn cho điểm; voucher cấm điểm thì preview nêu lý do, accept trả POINTS_USE_NOT_ALLOWED; 9.900đ không dùng được mức tối thiểu 10 điểm. Qua facade: preview không ghi gì; accept giữ 200 điểm và 1 lượt; accept lặp cùng nguồn chỉ giữ một lần; settle rồi grant trong cùng giao dịch owner cho 500 − 200 + 73 = 373 điểm, sổ cái HOLD, CONSUME, EARN, gửi lặp không cộng thêm; giao dịch owner lỗi sau accept thì không còn gì bị giữ; điều khoản đổi trả QUOTE_CHANGED, không tự tính lại; voucher đang tạm dừng hoặc nguồn guest trả PROMOTION_INELIGIBLE; đơn 0đ chốt không cần cổng và tích 0 điểm; hai đơn tranh lượt cuối hoặc tranh cùng 200 điểm thì chỉ một đơn được, đơn kia nhận lỗi có kiểu; gọi ngoài giao dịch owner bị từ chối.

Sửa trong quá trình chạy (do test phát hiện):
- Đọc tham chiếu trước khi khóa bằng truy vấn trả id/giá trị (`findIdByCodeKey`, `findReferences`) thay vì nạp entity, vì entity đã nạp không được làm mới khi khóa sau đó, dẫn tới OptimisticLock khi tranh lượt cuối.
- Bộ theo dõi thứ tự khóa cho phép xin lại khóa đã giữ trong cùng giao dịch (settle rồi earn), vẫn chặn khóa mới ngược thứ tự; settle bán lẻ khóa (mở) ví trước benefit để earn sau đó không phải khóa ví ngược thứ tự.

Giới hạn: test đóng vai owner FE-08; đây không phải bằng chứng tích hợp G-03 (FE-08 thật chưa có trên nhánh này). Dòng hàng do owner gửi được coi là mua được; owner phải tự loại sản phẩm ẩn, ngừng bán, đã xóa. Workshop (US5) chưa có: preview hoặc accept workshop trả DEPENDENCY_UNAVAILABLE. T054 (adapter FE-08) và T055 (component React) chưa làm.

## 16. Bằng chứng US5 T056, T058 trên build tạm (2026-10-27)

Cùng build tạm như mục 9 (BE-DESIGN-0011). Database trống mới `ppswbs_loyalty_build11`, Flyway V5–V12.

| Test | Kết quả |
| --- | --- |
| `LoyaltyWorkshopQuoteTest` (T056, quy tắc thuần) | 11/11; lần đầu biên dịch thất bại đúng kỳ vọng (chưa có `WorkshopBenefitEvaluator`) |
| `LoyaltyWorkshopFacadeIT` (facade trên MySQL thật, test đóng vai owner booking) | 3/3 |
| Tổng 006 | 204 chạy, 203 đạt, 1 bỏ qua (BE-DESIGN-0012), 0 lỗi |

Đã kiểm chứng: voucher trước rồi điểm, phí 1.000.000 giảm 100.000 và 200 điểm còn 880.000 (`postPromotionAmount`, 003.1 tự tính cọc); trần điểm 900 tính trên phí sau voucher, không trên cọc; trần giảm áp một lần cho cả booking, không nhân số người; phí sau giảm lẻ 984.999 giữ nguyên; gói, chi nhánh, ngày, phiên phải khớp cùng lúc; ngưỡng tính trên phí gói; voucher không dùng được nêu lý do; không có giảm tự động cho workshop; phí sau giảm bằng 0 trả WORKSHOP_NET_AMOUNT_INVALID; voucher cấm điểm thì từ chối điểm. Qua facade: accept giữ và snapshot 880.000; cọc chốt điểm và lượt nhưng không tích; hoàn thành và trả đủ tích đúng 88 điểm một lần; voucher sai ngày bị từ chối.

T057 (tích hợp với FE-03 thật) chưa làm: module booking của 003.1 có trên `origin/develop` nhưng chưa ghép về nhánh này, và task phụ thuộc T055 (component React). `LoyaltyWorkshopFacadeIT` không thay T057 và không đóng G-04, G-05.

## 17. Bằng chứng US3 T066–T069 trên build tạm (2026-10-27)

Cùng build tạm như mục 9 (BE-DESIGN-0011). Database trống mới `ppswbs_loyalty_build12`, Flyway V5–V12.

| Test | Kết quả |
| --- | --- |
| `SavedVoucherContractIT` (T066, qua chain thật, provider sản phẩm thật của 002 với sản phẩm đã công khai thật) | 7/7; lần chạy đầu 7/7 thất bại đúng kỳ vọng |
| Tổng 006 | 211 chạy, 210 đạt, 1 bỏ qua (BE-DESIGN-0012), 0 lỗi |

Đã kiểm chứng: Guest xem thẻ không cần bearer; thẻ chỉ liệt kê sản phẩm đang công khai (sản phẩm nháp bị lược khỏi thẻ); chương trình không còn sản phẩm công khai, chưa bắt đầu, đã kết thúc hoặc tạm dừng không hiện ở danh sách và chi tiết trả 404; lọc theo phạm vi sản phẩm/workshop; Member lưu voucher idempotent (lưu lại giữ nguyên thời điểm lưu đầu), không giữ lượt nào; Kho Voucher hiện trạng thái hiện tại ACTIVE, PAUSED, SCHEDULED, EXPIRED, NO_PUBLIC_TARGET ("Hiện không có sản phẩm áp dụng"); kho riêng của từng Member, phân trang bằng cursor; Guest lưu nhận 401 (được hướng dẫn đăng nhập), Manager, Staff, Admin 403; giảm tự động hoặc chương trình không tồn tại không lưu được (404); thiếu Idempotency-Key trả 400.

Ghi chú: T067 nêu entity `SavedVoucher` riêng; theo thiết kế 10 bảng, voucher đã lưu là cột `saved_at` trên `promotion_member_program`, nên dùng `MemberProgram` và `MemberProgramRepository` (không có bảng mới). Thẻ workshop hiển thị phạm vi như cấu hình vì chưa có provider workshop để lọc phần công khai. T070 (màn hình React) chờ T077.

## 18. Test đồng thời phía loyalty (một phần T063) và sửa accept trùng nguồn, trên build tạm (2026-10-27)

Cùng build tạm như mục 9 (BE-DESIGN-0011). Test mới `LoyaltyMySqlConcurrencyIT`: transaction thật trên MySQL 8.4, hai luồng bắt đầu cùng lúc qua barrier, test đóng vai owner nguồn.

| Kịch bản | Kết quả kỳ vọng |
| --- | --- |
| Retail và workshop cùng dùng 200 điểm của một ví | Đúng một bên giữ được; bên kia nhận lỗi có kiểu; giữ 200, số dư 200 |
| Manager hạ tổng hạn mức xuống 1 trong lúc Member thứ hai accept (đã giữ 1/2) | Chỉ một bên thắng; `held + consumed <= totalLimit`; bên sửa thua nhận `QUOTA_LIMIT_CONFLICT` |
| Tạm dừng trong lúc accept | Tạm dừng luôn thành công; phần giữ đã chấp nhận vẫn `HELD` (FR-010) |
| Đổi chính sách điểm (10.000 → 5.000đ/điểm) trong lúc accept | Accept thành công thì điểm tích theo đúng chính sách đã ghi trong snapshot; nếu không thì `QUOTE_CHANGED`, không ghi gì (FR-027) |
| Hai lần cộng điểm đồng thời cho Member chưa có ví | Không tạo ví trùng; lỗi (nếu có) chỉ là `RETRYABLE_CONTENTION`, thử lại thì đủ 80 + 80 điểm |
| Accept trùng cùng `sourceRef` (có điểm) | Cả hai trả cùng `acceptedBenefitId`, giữ 200 một lần |
| Accept trùng cùng `sourceRef` (không điểm, không voucher) | Ít nhất một thành công; lỗi chỉ là `RETRYABLE_CONTENTION`, không lỗi DB thô |
| Cùng outcome đã thanh toán gửi hai lần đồng thời | Sổ điểm đúng HOLD, CONSUME, EARN; số dư 500 − 200 + 78 |
| Đã thanh toán và hết hạn đến cùng lúc | Đúng một kết quả có hiệu lực: hoặc chốt (số dư 300, voucher đã dùng) hoặc giải phóng (số dư 500, voucher trả lại), không bao giờ cả hai |

**Lần chạy đầu (trước khi sửa code)**, database trống mới `ppswbs_loyalty_build14`: 9 chạy, 7 đạt, 2 thất bại. Cả hai cùng một lỗi thật: hai accept đồng thời cùng nguồn đều qua bước kiểm tra "đã có" trước khi giữ khóa; bên sau đụng khóa duy nhất `uk_loyalty_benefit_source` và nhận `DataIntegrityViolationException` thô, trái với interfaces.md (mọi lỗi của facade là `LoyaltyException` có kiểu) và FR-017/FR-032 (accept lặp không tạo hiệu ứng mới).

**Sửa (code 006):**
- `LoyaltyReservationService.reserve`: sau khi đã giữ khóa registry, chương trình, ví và bộ đếm Member, khóa và đọc lại dòng accepted benefit của nguồn (`LoyaltyLockCoordinator.lockBenefit`, đúng bước cuối trong thứ tự khóa); nếu accept đồng thời đã commit thì trả lại snapshot đó (cùng digest) hoặc `IDEMPOTENCY_CONFLICT` (khác digest).
- `DefaultLoyaltyFacade.reserveAcceptedTransaction`: nếu vẫn đụng `uk_loyalty_benefit_source` (hai accept không có khóa chung nào để xếp hàng), trả `RETRYABLE_CONTENTION`; owner thử lại sẽ nhận snapshot đã có. Ràng buộc khác vẫn ném nguyên lỗi.

**Sau khi sửa:** `LoyaltyMySqlConcurrencyIT` 9/9 trên `ppswbs_loyalty_build15`. Toàn bộ 006 trên database trống mới `ppswbs_loyalty_build17` (Flyway V5–V12, 8 migration): **220 chạy, 219 đạt, 1 bỏ qua (BE-DESIGN-0012), 0 lỗi**, thoát mã 0. Một lần chạy giữa (`build16`) thất bại 1 test `LoyaltyMySqlMigrationIT.seedHasOneRegistryRowPointingToTheDefaultPolicy` do chính test mới để registry trỏ sang policy khác sau khi dọn; đã sửa phần dọn của test (trỏ registry về đúng policy ban đầu), không sửa code sản phẩm.

**Chưa phủ (nên T063 chưa tick):** phân xử phía nguồn giữa thanh toán và hết hạn/hủy/hoàn thành, proof và version của FE-03/FE-08 (G-03–G-05); deadlock và lock timeout được ép có chủ đích rồi thử lại. Kết quả đồng thời phụ thuộc thời điểm, mỗi kịch bản chấp nhận mọi thứ tự hợp lệ và kiểm tra bất biến.

Đổi mã blocker: `BE-DESIGN-0007` → `BE-DESIGN-0011`, `BE-DESIGN-0008` → `BE-DESIGN-0012` (develop đã dùng 0007–0010 cho FE-08 và FE-10); mọi tham chiếu trong file này đã đổi theo. Kiểm tra lại develop: `origin/develop` vẫn ở `37d68cf`, hai blocker chưa được sửa (ERROR-LOG, diễn biến 2026-10-27).

## 19. Chạy lại sau khi ghép `origin/develop` mới và test contract API (một phần T071, T073), trên build tạm (2026-10-28)

**Build tạm** như mục 9, cập nhật cho HEAD `f24d3d4` (nhánh `khuong` sau khi ghép `origin/develop` `37d68cf`, có thêm module `readyringsales`, `fulfilment`, `reporting`, `features/workshopbooking`). Ngoài các loại trừ của mục 9, build tạm loại thêm ba migration của feature khác đang **trùng số** trên develop: `V6__retail_cart.sql`, `V7__retail_mock_checkout.sql` (FE-08) và `V9__workshop_booking.sql` (003.1); trùng với `V6__account_schema_alignment.sql`, `V7__workshop_operations.sql`, `V9__product_management.sql` nên Flyway không khởi động được với nguyên trạng develop. Không sửa file của feature khác; cần owner đổi số. Mỗi lần chạy dùng database trống mới rồi xóa sau khi chạy.

| Lần chạy | Database | Kết quả |
| --- | --- | --- |
| Toàn bộ 006 + `ModulithArchitectureTests`, trước khi thêm test mới | `ppswbs_loyalty_build18` | 220 chạy, 219 đạt, 1 bỏ qua (BE-DESIGN-0012), 0 lỗi; Flyway V5, V6, V7, V8, V9, V10, V11, V12 |
| Toàn bộ 006 + Modulith, sau khi thêm `LoyaltyApiContractIT` | `ppswbs_loyalty_build20` | **228 chạy, 227 đạt, 1 bỏ qua, 0 lỗi** |

`LoyaltyApiContractIT` (T071, 8 test, qua chain thật của 001, chỉ giả lập bước xác minh Firebase token):
- Đúng 15 operation của OpenAPI được ánh xạ bởi controller loyaltypromotion, không thừa route nào (không có reserve/settle/earn/release/restore công khai); contract có 34 schema.
- Ma trận quyền trên cả 15 operation với không bearer, MEMBER, STAFF, MANAGER, ADMIN: route công khai không cần bearer; chương trình và chính sách chỉ MANAGER; ví và voucher đã lưu chỉ MEMBER; STAFF/ADMIN bị 403; không bearer bị 401. Mọi response của loyalty có `no-store`.
- Mọi mutation thiếu `Idempotency-Key` hoặc key không phải UUID → 400 `INVALID_INPUT`; body JSON có field lạ, số thay cho chuỗi thập phân, mảng hoặc JSON hỏng → 400.
- Phân trang: `limit` 0/101/`x` và cursor sai → 400, `limit` 1 và 100 → 200; cursor đi tiếp không trùng phần tử.
- JSON của cả 15 operation và envelope lỗi (400, 403, 404, 404, 409) khớp schema: đủ field bắt buộc, không thừa field, pattern chuỗi thập phân, enum, timestamp UTC.

**Lệch contract tìm thấy và đã sửa (tài liệu 006, không sửa code):** OpenAPI khai báo `Error.fieldErrors` là map `{field: message}`, trong khi `interfaces.md` ghi "theo common convention" và `common.ErrorResponse` dùng chung trả mảng `[{field, message}]` (giống contract 002). Đã sửa `contracts/loyalty-promotion.openapi.json` thành mảng.

Lần chạy đầu của test mới cũng lộ ra hai điểm của chính test (đã sửa test, không sửa code sản phẩm): bộ kiểm tra schema bỏ qua `required` trong nhánh `anyOf`; danh sách dùng chung database còn có chương trình do suite khác seed thẳng vào repository (ví dụ chương trình workshop không có target), nên test chỉ kiểm tra phần tử do chính nó tạo qua API. Lần chạy đầy đủ đầu tiên (`build19`) có 2 lỗi do test mới đổi chính sách điểm toàn cục; phần dọn giờ trỏ registry về policy ban đầu như `LoyaltyMySqlConcurrencyIT`.

**Chưa tick T071, T073:** T071 còn phụ thuộc các màn hình frontend và nguồn thật (T070, T065, T047, T034, T040); T073 cần build thật của develop (BE-DESIGN-0011 và migration trùng số) và các suite nguồn thật.

## 20. Build thật sau khi develop sửa lỗi biên dịch và số migration (một phần T073), 2026-10-29

`khuong` ghép tới `origin/develop` `2774c1e` (fast-forward). Develop biên dịch sạch (`c0dda7d` của 004) và không còn migration trùng số (`e143b52`: FE-08 `V6`/`V7` → `V13`/`V14`, 002 `V9` → `V15`), nên **không còn build tạm**: dùng đúng `pom.xml`, `src` và migration của repo. `BE-DESIGN-0011` đã gỡ (diễn biến 2026-10-29 trong ERROR-LOG).

Sửa `LoyaltyMySqlMigrationIT.migrationsAreAppliedInOrderWithTenTables`: thứ tự yêu cầu là `5, 10, 11, 12` (006 chỉ cần 001 trước nó; bảng catalogue của 002 nay đến từ `V15` chạy sau và không được 006 tham chiếu). Bản cũ `5, 9, 10, 11, 12` vẫn đạt chỉ vì `V9__workshop_booking` cũng mang số 9.

Lệnh (database trống `ppswbs_loyalty_build22`, `ppswbs_pm_sales_2910`, rồi lần hai `*_23`, `*_2911`; đều xóa sau khi chạy):

```powershell
.\mvnw.cmd -o test "-Dtest=edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.**.*,edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.**.*,ModulithArchitectureTests"
.\mvnw.cmd -o test '-Dtest=**/features/productmanagement/*/**/*,**/features/loyaltypromotion/*/**/*'
```

Lệnh thứ hai chạy các lớp trong package con (`controller.LoyaltyExceptionHandlerTests`, `service.LoyaltyLifecycleTest` và các lớp tương ứng của 002) mà mẫu của lệnh đầu bỏ sót.

| Phạm vi | Kết quả |
| --- | --- |
| Flyway | 18 migration V4–V21 trên database trống |
| 006 | 226 chạy, 225 đạt, 1 bỏ qua (`LoyaltySecurityIT` preflight CORS, BE-DESIGN-0012), 0 lỗi |
| 002 | 308 đạt |
| `ModulithArchitectureTests` | 5 đạt |

**Chưa tick T073:** task còn phụ thuộc T071, T072 (chờ frontend) và các suite nguồn thật của FE-03/FE-08; BE-DESIGN-0012 (CORS của 001) chưa gỡ.
