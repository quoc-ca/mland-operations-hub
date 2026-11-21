---
format_version: 2
document_type: test_handoff
date_created: 2026-11-21
date_updated: 2026-11-21
author: Ngô Tiến Đạt
last_updated_by: Ngô Tiến Đạt
scope: FE-004, FE-012, CUSTOM-MANUFACTURING
environment: local
frontend_url: http://localhost:5173
backend_url: http://localhost:8080
browser_tool: browsermcp
---

# Test handoff — Feature-004, Feature-012 và Custom Manufacturing

Tài liệu này là danh mục kiểm thử sau khi hoàn tất code của bốn agent. Agent QA
phải dùng `ppswbs-testing/AGENT.md` làm quy tắc thực thi. Đây là acceptance
baseline mới; các scenario AI cũ của Feature-004 chỉ là historical evidence.

## 1. Test gates và dữ liệu dùng chung

Trước khi chạy:

1. FE chạy tại `localhost:5173`, BE tại `localhost:8080`, MySQL và Flyway đã sẵn sàng.
2. `GET /v3/api-docs` trả thành công.
3. Chạy unit/integration test hiện có trước E2E.
4. Mỗi scenario dùng `test_data.isolation_key` riêng và cleanup sau khi chạy.
5. Dùng fixture/test account riêng cho Guest, Member, Staff, Manager và Admin.
6. Tiếng Việt trong fixture và selector phải là UTF-8 chuẩn, không chấp nhận mojibake.
7. Không dùng production data, raw token, raw image hoặc PII thật.

Fixture tối thiểu:

| Fixture | Mục đích |
|---|---|
| `guest-new` | Guest chưa có request |
| `guest-4-of-5` | Guest còn một quota request trong ngày |
| `guest-5-of-5` | Guest đã hết quota |
| `member-design-owner` | Member sở hữu DRAFT/VALIDATED design |
| `member-other` | Kiểm tra ownership isolation |
| `staff-reviewer` | Review và quote preparation trong scope |
| `manager-reviewer` | Review, quote approval và production authority |
| `admin-technical` | Technical/audit access, không business decision |
| `branch-compatible` | Branch hỗ trợ toàn bộ lựa chọn |
| `branch-incompatible` | Branch từ chối material/component |
| `payment-success`, `payment-failure`, `payment-duplicate` | Payment callback và idempotency |

Evidence bắt buộc:

- Screenshot `.jpg` tại thư mục `test-results/screenshots/` tương ứng feature.
- Request/response status và error code, không ghi token hoặc PII.
- ID fixture, isolation key, thời điểm chạy và cleanup result.
- Khi lỗi: dừng tại failed step, ghi `BUG-LOG.md`; nếu spec/API mâu thuẫn, ghi `SPEC-GAP`.

## 2. Thứ tự thực thi

```text
Feature-012 foundation
→ Feature-004 request and human review
→ quote and full payment
→ production and fulfilment
→ cross-feature regression
```

Không coi một test UI mock thành bằng chứng backend. Mọi command phải kiểm tra
idempotency; mọi command có version phải kiểm tra optimistic locking.

## 3. Feature-004 — Design Request và Human Review

### 3.1 Guest/Member intake

| ID | Scenario và expected result |
|---|---|
| E2E-004-018 | Guest nhập description hợp lệ, tạo request `NEED_REVIEW`, nhận tracking token opaque và xem được status. Không lộ raw source ID/email/phone. |
| E2E-004-019 | Guest upload image hợp lệ; quality pass; request tạo thành công. Guest được dùng description-only, image-only hoặc cả hai, nhưng phải có ít nhất một loại input. |
| E2E-004-020 | Member gửi request có Feature-012 `DRAFT` hoặc `VALIDATED` snapshot. Backend lưu immutable snapshot; thay đổi design gốc không đổi request. |
| E2E-004-021 | Upload đúng 5 ảnh thành công; ảnh thứ 6 bị chặn ở UI và API, request vẫn chỉ có 5 ảnh. |
| E2E-004-022 | Ảnh quá mờ hiển thị lỗi ngay sau upload và trước submit; backend cũng từ chối nếu client bypass validation. Không tạo request. |
| E2E-004-023 | Guest gửi request thứ 6 trong cùng ngày nhận `429 DESIGN_REQUEST_GUEST_DAILY_LIMIT_EXCEEDED`; không tạo request và không tăng storage. Concurrent requests không vượt 5. |
| E2E-004-024 | Guest tracking token chỉ xem được request của token đó; token hết hạn, replay hoặc đổi request reference đều bị từ chối an toàn. |
| E2E-004-032 | Kiểm tra toàn bộ form, gallery, status và explanation ở Vietnamese/English; không có ký tự lỗi, font lỗi hoặc text nửa Anh/nửa Việt. |
| E2E-004-033 | Design request không gọi AI, không hiển thị AI decision/price, không có cart, deposit hoặc remaining balance. |

Unit/integration phải bổ sung: schema validation, MIME/signature, kích thước,
blur result, quota atomicity, opaque token digest, ownership, privacy filtering,
idempotency và transaction rollback.

### 3.2 Human review

| ID | Scenario và expected result |
|---|---|
| E2E-004-025 | Staff mở request `NEED_REVIEW`, chọn `ACCEPTED`, nhập explanation không rỗng, submit thành công. Explanation xuất hiện ở Member/Guest projection. |
| E2E-004-026 | Manager chọn `REJECTED` với explanation; request terminal, customer thấy trạng thái và lý do. Explanation rỗng bị chặn trước API. |
| E2E-004-027 | Staff/Manager chọn `CLARIFICATION_REQUIRED`; customer gửi revision với `If-Match-Version`; request quay lại `NEED_REVIEW`, lịch sử cũ vẫn giữ nguyên. |
| E2E-004-028 | Hai reviewer submit cùng version: một command thành công, command còn lại trả `409` stale/duplicate. Retry cùng Idempotency-Key trả outcome cũ. |
| E2E-004-031 | Guest/Member không vào review queue; Admin không accept/reject; Staff/Manager có quyền business đã chốt; request khác owner không đọc được. |

Human-review integration phải xác nhận `ACCEPTED`, `REJECTED` và
`CLARIFICATION_REQUIRED` đều có audit event; không auto-accept, auto-reject,
AI mutation hoặc quyết định dựa trên giá trị cũ.

## 4. Custom Manufacturing — Quote, Payment, Production, Fulfilment

### 4.1 Quote

| ID | Scenario và expected result |
|---|---|
| E2E-004-029 | Sau `ACCEPTED`, Staff chuẩn bị quote; Manager approve; Member thấy quote generation, tổng tiền, currency và deadline. |
| E2E-004-030 | Quote hết hạn không thể thanh toán; order chuyển policy `EXPIRED/CANCELLED`; Member yêu cầu quote mới, generation tăng và quote cũ vẫn là history. |
| E2E-004-034 | Quote command duplicate với cùng idempotency trả cùng quote; payload khác với cùng key trả conflict. Manager không thể approve quote đã hết hạn. |

### 4.2 Full payment và lifecycle

| ID | Scenario và expected result |
|---|---|
| E2E-004-035 | Member mở `/account/my-custom-orders`, chỉ thấy CTA thanh toán toàn bộ. Không có Add to Cart, deposit, balance hoặc split payment. |
| E2E-004-036 | Thanh toán 100% tạo đúng một payment context `CUSTOM_ORDER_FULL_PAYMENT`; success chuyển order `PAID`. |
| E2E-004-037 | Payment failure/timeout không làm mất quote hoặc tạo order thứ hai; retry dùng cùng command semantics. |
| E2E-004-038 | Duplicate callback/IPN/QueryDR không ghi nhận thanh toán hai lần và không chuyển trạng thái lặp sai. |
| E2E-004-039 | Order chuyển hợp lệ `PAID → IN_PRODUCTION → FULFILLED → CLOSED`; transition sai thứ tự, sửa order đã đóng hoặc thanh toán lại đều bị từ chối. |
| E2E-004-040 | Member chỉ đọc custom order của mình; Guest không truy cập Member order page; public projection không lộ raw source, PII hoặc audit payload. |

Payment integration phải chạy với sandbox/mock gateway đã được cấu hình trong
local environment; không gọi cổng thanh toán thật. Kiểm tra redirect, return,
callback, unknown outcome, retry và reconciliation evidence.

## 5. Feature-012 — Workshop Ring Design Configuration

### 5.1 Configuration và validation

| ID | Scenario và expected result |
|---|---|
| E2E-012-012 | Thêm gemstone đến tổng 10 viên thành công; viên thứ 11 bị chặn, hiển thị thông báo song ngữ và không làm thay đổi draft. |
| E2E-012-013 | Thêm tối đa 2 engraving text; engraving text thứ 3 bị chặn và giải thích giới hạn. |
| E2E-012-014 | Thêm tối đa 2 engraving image; ảnh thứ 3 bị chặn và không ghi persistence command. |
| E2E-012-015 | Contrast/sharpness có range `0..100`, giá trị thay đổi cập nhật preview; giá trị ngoài range bị từ chối ở server. |
| E2E-012-016 | Preview đồng thời hiển thị toàn bộ gemstone, 2 text và 2 image engraving, đúng placement; không mất item khi thêm item mới. |
| E2E-012-017 | Save/evaluate rồi reload: khôi phục đầy đủ text, image, crop, contrast, sharpness, position, material và size. Không được xuất hiện ảnh trắng mặc định. |
| E2E-012-018 | Guest session hết hạn sau 20 phút inactivity trả `401 DESIGN_SESSION_EXPIRED`; không lộ design nội bộ và cho phép bắt đầu session mới. |
| E2E-012-019 | Member xem/sửa design của mình; Member khác nhận denied/not found; history giữ các immutable versions. |
| E2E-012-020 | Branch incompatible trả reason code rõ ràng; người dùng được quay lại chỉnh material/component, không mất draft hợp lệ. |
| E2E-012-023 | Toàn bộ configurator không có mojibake, label và error message nhất quán giữa Vietnamese/English. |

Unit/integration phải kiểm tra tổng gemstone, loại engraving, preprocessing,
snapshot restore, option compatibility, rules version, guest session digest,
ownership và optimistic locking.

### 5.2 Booking attachment

| ID | Scenario và expected result |
|---|---|
| E2E-012-021 | Attach một validated design vào booking qua facade; backend tạo immutable snapshot và lock design sau transaction commit. |
| E2E-012-022 | Booking submit thất bại/rollback; design không bị lock hoặc mất, người dùng retry được. Design đã attach/locked không sửa trực tiếp được. |
| E2E-012-024 | Booking có nhiều vé phải tuân theo policy hiện hành; không tự suy luận gắn design cho từng vé nếu response không cung cấp capability. Ghi `BLOCKED` nếu contract thiếu dữ liệu. |

Không tạo payment, capacity hold hoặc ticket mutation trực tiếp trong Feature-012.

## 6. Cross-feature journey

### E2E-XF-001 — Design snapshot đến custom order

1. Member tạo và evaluate Feature-012 design.
2. Gửi immutable snapshot vào Feature-004 custom request.
3. Xác nhận request chỉ chứa snapshot tại thời điểm submit.
4. Staff/Manager review với explanation bắt buộc.
5. Manager duyệt quote.
6. Member thanh toán toàn bộ.
7. Staff/Manager cập nhật production và fulfilment.
8. Xác nhận design gốc, request snapshot, quote và order snapshot không bị thay đổi trái phép.

Expected: rejected request không tạo quote/payment; clarification giữ lịch sử;
quote chỉ dùng snapshot đã review; custom order không đi qua retail cart.

### E2E-XF-002 — Design snapshot đến workshop booking

1. Member tạo design hợp lệ.
2. Chọn package, branch và session.
3. Backend revalidate compatibility.
4. Attach design qua facade.
5. Submit booking.
6. Kiểm tra lock/snapshot và rollback khi payment/booking thất bại.

Expected: target là workshop/session, không phải branch; design không bị mất khi
booking thất bại; không tạo payment ngoài booking flow.

## 7. Unit/integration checklist cho agent

- Backend service/controller tests cho mọi error code và permission boundary.
- Database unique/idempotency/concurrency tests.
- Flyway clean install, upgrade, rerun và `validate`.
- Frontend API adapter tests: Bearer token, multipart, idempotency, `If-Match-Version`, `401`, `403`, `409`, `422`, `429`, `503`.
- Frontend screen tests: loading, empty, validation, success, denied, conflict, retry và form preservation.
- Payment callback and reconciliation tests.
- Snapshot immutability and cross-module facade tests.
- UTF-8/locale regression tests.

Commands:

```text
npm test
npm run build
mvn test
flyway validate
git diff --check
```

## 8. Evidence status policy

- `PASS`: expected result và evidence đầy đủ.
- `FAIL`: lỗi thực thi; không sửa code trong lúc QA.
- `BLOCKED`: thiếu service, fixture, account hoặc contract.
- `SPEC-GAP`: tài liệu và implementation không thống nhất.

Các scenario AI cũ (`E2E-004-001`, `E2E-004-004`, `E2E-004-005`) không được
dùng làm acceptance evidence cho baseline human-review hiện tại.
