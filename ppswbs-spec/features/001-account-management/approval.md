# Tech Lead Approval Record: Account Management (FE-001)

**Dự án**: PPSWBS / Mland
**Feature**: FE-001 Account Management
**Branch**: `son`
**Ngày phê duyệt**: 2026-11-03
**Người phê duyệt (Tech Lead)**: Phan Sơn

---

## 1. Quyết định phê duyệt thiết kế

Theo thẩm quyền Tech Lead dự án PPSWBS/Mland và căn cứ quy định tại [AGENT.md](../../AGENT.md) §5, tôi xác nhận phê duyệt toàn bộ bộ tài liệu đặc tả và thiết kế (Specification & Design Pack) của Feature **FE-001 Account Management** để chuyển giao sang giai đoạn triển khai (Implementation Phase).

Mọi blocker thiết kế (Gate D02, D06, D18, D19, D20) đã được rà soát và giải quyết dứt điểm:
1. **OpenAPI 3.1.0** đã được sinh đầy đủ tại `contracts/account-management.openapi.yaml`, phản ánh chính xác các route và DTOs đã duyệt.
2. **Kế hoạch kiểm chứng** tại `verification.md` đã được chuẩn hóa đầy đủ từ V-01 đến V-09, phân định rõ bằng chứng thiết kế (Design-level) và bằng chứng chạy thực tế (Implementation-level).
3. **Cơ chế lưu trữ AuditOutcome** đã chốt Option B (`AuditOutcome` Java enum + `VARCHAR(16) NOT NULL` append-only migration).
4. **Phạm vi MVP** được xác định rõ ràng: Phase 1–6 (T001–T037) cùng các task polish bắt buộc (T044–T046, T048–T050). US5 (T038–T043) và quét WCAG 2.2 AA (T047) được hoãn sang đợt bàn giao hoàn chỉnh (Full Delivery).

---

## 2. Danh mục tài liệu và phiên bản được phê duyệt

| STT | Tài liệu | Đường dẫn | Trạng thái phê duyệt |
|---|---|---|---|
| 1 | Feature Specification | `ppswbs-spec/features/001-account-management/spec.md` | **APPROVED** (FR-001–FR-034, SC-001–SC-007, US1–US5) |
| 2 | Implementation Plan | `ppswbs-spec/features/001-account-management/plan.md` | **APPROVED** (React 19, Spring Modulith, Route groups) |
| 3 | Physical Data Model | `ppswbs-spec/features/001-account-management/data-model.md` | **APPROVED** (6 entities, Option B AuditOutcome VARCHAR(16)) |
| 4 | Internal Facade Contracts | `ppswbs-spec/features/001-account-management/contracts/interfaces.md` | **APPROVED** (TargetFactsOutcome, RosterOutcome, CurrentAccountActor) |
| 5 | REST Transport Contract | `ppswbs-spec/features/001-account-management/contracts/account-management.openapi.yaml` | **APPROVED** (OpenAPI 3.1.0 production specification) |
| 6 | Architecture Design | `ppswbs-spec/features/001-account-management/design/architecture.md` | **APPROVED** (Production unit blueprint, CORS & Bearer policy) |
| 7 | Workflow Design | `ppswbs-spec/features/001-account-management/design/workflows.md` | **APPROVED** (WF-01 đến WF-08, gồm WF-08 Profile verification gate) |
| 8 | UI Flow Design | `ppswbs-spec/features/001-account-management/design/ui-flow.md` | **APPROVED** (Route authorization, screen states, error presentation) |
| 9 | Task Breakdown | `ppswbs-spec/features/001-account-management/tasks.md` | **APPROVED** (52 tasks, MVP scope T001–T037 + T044–T046, T048–T050) |
| 10 | Verification Report & Plan | `ppswbs-spec/features/001-account-management/verification.md` | **APPROVED** (V-01 đến V-09 matrix, acceptance evidence placeholders) |
| 11 | Phase 0 Research Log | `ppswbs-spec/features/001-account-management/research.md` | **APPROVED** (Decisions D-01 đến D-19) |
| 12 | Specification Checklist | `ppswbs-spec/features/001-account-management/checklists/requirements.md` | **APPROVED** (Audited 16/16 pass) |

---

## 3. Điều kiện tiên quyết & Hướng dẫn cho Persona Triển khai (Implementation Persona)

Khi persona triển khai code tiếp nhận tài liệu để lập trình:

1. **Thực thi và lưu bằng chứng cho T003 & T005 trước tiên**:
   - `T003`: Chạy migration và kiểm tra `spring.jpa.hibernate.ddl-auto=validate` trên MySQL (cả database mới và upgrade từ V5). Ghi log kiểm chứng vào Section 4.1 của `verification.md`.
   - `T005`: Chạy `npm run dev` trên `ppswbs_frontend/` và xác nhận dev server hoạt động tại `http://localhost:5173`.
2. **Refactor đồng bộ thuật ngữ trong Code**:
   - Đổi tên `activeLocationIds` trong code backend (entity, DTO, facade) thành `activeWorkshopLocationIds` để khớp hoàn toàn với spec FR-028/029 và contracts.
3. **Triển khai Frontend tuần tự theo MVP**:
   - Bắt đầu với US1 (T016, T017, T018: Auth client, current account hook, AuthPage).
   - Tiếp tục với US2 (T023, T024, T051, T052: Profile, email sync gate, 90s cooldown).
   - Tiếp tục với US3 & US4 (T031, T032, T037: Admin internal create, resend, suspend/restore).
   - T046: Hoàn thiện `AccountManagementApiClient.ts` bám sát `account-management.openapi.yaml`.
4. **Không thay đổi các quyết định đã duyệt**:
   - Không chuyển sang dùng session/cookie; chỉ dùng bearer Firebase ID token.
   - Không tự động sync quyền qua event hoặc cache scope Workshop.
   - Không bắt raw password hoặc link kích hoạt Firebase vào database hay log.

---

**Xác nhận phê duyệt bàn giao**:
*Tech Lead*: **Phan Sơn**
*Ngày*: 2026-11-03
*Kết luận*: **ĐỦ CHI TIẾT ĐỂ BÀN GIAO TRIỂN KHAI (READY FOR IMPLEMENTATION)**
