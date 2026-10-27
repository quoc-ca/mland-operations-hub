# Verification — FE-06 Loyalty & Promotion

**Date created**: 2026-10-26
**Date updated**: 2026-10-27 (đổi mã BE-DESIGN và số migration trước khi ghép develop)
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Feature**: `006-loyalty-promotion`  
**Trạng thái**: Tài liệu — chưa có code, test hay migration nào được tạo hoặc chạy. Mọi kiểm chứng runtime là **NOT RUN**.  
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

## 2. Thay đổi sau lần duyệt 2026-10-24

1. Clarification 2026-10-26: giảm tự động luôn kết hợp điểm; phạm vi sản phẩm cho phép chưa công khai/ẩn/ngừng bán, từ chối đã xóa mềm.
2. Quyết định cọc workshop B (đồng bộ với owner 003): FE-06 tính `postPromotionAmount`; 003.1 tính `deposit = ceil(post/2)` và `remaining = post - deposit`; FE-06 chỉ snapshot `postPromotionAmount`, không tính/lưu cọc và còn lại (chốt 2026-10-26, V8 bỏ hai cột). Tên trường `frozenPostPromotionAmount`/`frozenRemainingAmount` là của 003, lấy từ tin nhắn của owner 003, **chưa xác nhận trong tài liệu 003** (không còn ảnh hưởng thiết kế dữ liệu của 006); bản `003.1-workshop-booking/spec.md` trong working tree này vẫn ghi "V1 MUST NOT apply a deposit-rounding rule" (thuộc owner 003, không chặn 006 nhưng cần đối chiếu khi merge).
3. Data model giảm từ 14 xuống 10 bảng (xem [plan.md](plan.md), đoạn "Cập nhật 2026-10-26").

## 3. Kết quả speckit-analyze (T003)

Lần 1 (2026-10-26, sau thay đổi): 0 CRITICAL, 3 HIGH, 4 MEDIUM, 2 LOW. F1, F4, F5, F7, F6, F8, F10 đã xử lý; F9 đã thỏa sẵn; F2 thuộc owner 003; F3 xử lý bằng việc duyệt lại.  
Lần 2 (2026-10-26, sau gộp bảng): 0 CRITICAL, 0 HIGH. G1 (cụm "component receipts" lỗi thời trong plan) đã sửa; G2 (plan chưa mô tả 10 bảng) đã sửa.  
Độ phủ: 41/41 FR có task; 15/15 operations có task. Kiểm tra tĩnh: `git diff --check` chỉ báo hai dấu cách cuối dòng cố ý ở dòng Status (cú pháp xuống dòng Markdown); không có liên kết nội bộ hỏng. Không có `.specify/extensions.yml` nên không có hook.

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
| R18 | ĐẠT CÓ ĐIỀU KIỆN | Còn mở: thông báo owner 003 rằng FE-06 chỉ gửi `postPromotionAmount`; MFA/assurance do FE-01. Không chặn nhóm task phạm vi. |
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
