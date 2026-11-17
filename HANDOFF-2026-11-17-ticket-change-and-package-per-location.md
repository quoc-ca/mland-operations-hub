# Handoff 2026-11-17 — đổi vé 003.3, múi giờ, gán Staff, và việc kế tiếp: gói bán theo location

Dành cho phiên/tài khoản mới. Nhánh `quoc`. **Mã nguồn bên dưới đã commit và push lên `origin/quoc` (commit `ad7489cc`, `1d08ce5f`, `afc6deac`, merge develop `7ac6cbb5`).** Không commit file này.
Chủ feature 003 (duyệt quyết định 003.1–003.4): Hồ Hoàng Quốc.

**Quy tắc cập nhật file này:** sau khi xong mỗi task/bước (hoặc khi đổi quyết định, gặp blocker), thêm một dòng vào mục
"7. Nhật ký tiến độ" ở cuối file (ngày, việc đã xong, kiểm chứng đã chạy, còn lại/blocker, commit nếu có). Tick checkbox
trong mục 6 khi xong. Không xóa dòng cũ; sửa mục 1–5 chỉ khi sự thật ở đó đã thay đổi (ví dụ đã commit).

Đọc trước khi làm: `CLAUDE.md`, `ppswbs_backend/AGENT.md`, `ppswbs_frontend/AGENT.md`, `ppswbs-spec/AGENT.md`,
`ppswbs_backend/ERROR-LOG.md` và `ppswbs_frontend/ERROR-LOG.md` (nếu có). Nhắc: speckit skill + kiểm tra/đặt `.specify/feature.json`.

---

## 1. Đã làm trong phiên trước (đã commit và push, xem mục 7)

### 1.1 Danh sách buổi đổi lịch (003.1/003.3)
- Dropdown "Chọn buổi mới" chỉ liệt kê session đích **còn chỗ và còn lượt đổi** (reschedule quota, 003.2 FR-035). Session hết chỗ hoặc hết lượt bị ẩn.
- Mỗi dòng hiện "còn N chỗ · còn M lượt đổi"; dòng "Cần quản lý duyệt" chỉ còn khi vé đã dùng hết lần đổi hoặc quá hạn 24 giờ.
- Backend: `TicketChangeOptions.Option` đổi `boolean quotaAvailable` thành `int remainingRescheduleQuota`; `PairSession` thêm `remainingRescheduleQuota`; `Pair` bỏ `quotaAvailable`. `ChangeOptionDto` và `SessionSeatsDto` thêm `remainingRescheduleQuota`.
- Hợp đồng: `workshop-settlement-ticket.openapi.yaml` (`ChangeOption`) thêm `remainingRescheduleQuota`.
- FE: `TicketSessionPicker.jsx`, `GroupTicketList.jsx` (nhãn `quotaLeft`).

### 1.2 Cửa sổ 7 ngày
- `TicketReplacementRules.checkLateArrivalWindow`: áp cho **cả đổi trước buổi học và đổi khi đến muộn** (trừ Mland tự đổi). Buổi mới phải bắt đầu muộn nhất **hết ngày thứ 7 theo ngày lịch Asia/Ho_Chi_Minh** tính từ ngày buổi cũ kết thúc (không phải 168 giờ). Mã lỗi `LATE_ARRIVAL_WINDOW_EXPIRED`.
- Chưa cập nhật FR-034 trong `003.3 spec.md`.

### 1.3 Quyền đổi lịch của Staff (quy tắc 2)
- Staff đổi vé và xem danh sách buổi đổi cần: location của vé trong phạm vi **và** được gán vào một buổi mà vé đang giữ (`requireManagerOrAssignedStaff(actor, ctx.locationReference(), ctx.lineReference())`). Thiếu gán thì `SESSION_ASSIGNMENT_REQUIRED`. Manager toàn cục.
- Chưa làm: giới hạn buổi đích theo location của Staff.
- Files: `TicketChangeService.java`, `TicketChangeOptionsApiService.java`.

### 1.4 Tự động gán Staff vào session
- `StaffAutoAssignmentService` (workshopoperations): gán Staff đang hoạt động của location vào session mới (đơn lẻ và hàng loạt) và đồng bộ định kỳ (mặc định 60s sau khởi động rồi mỗi 5 phút; `workshop.staff-auto-assign.*`). Bản ghi `session_staff_assignments` vẫn thật, Manager thu hồi từng session, **session đã thu hồi không bị gán lại**. Audit `SESSION_STAFF_AUTO_ASSIGNED`.
- Không đụng module tài khoản (dùng `AccountFacade.findEligibleRoster`). Staff `PENDING_SETUP` chưa được gán đến khi kích hoạt.
- Spec 003.2 đã thêm ghi chú quyết định 2026-11-16. Spec 003.3 chưa.
- Repo thêm: `SessionStaffAssignmentRepository.findBySessionIdIn`, `WorkshopSessionRepository.findByWorkshopDateGreaterThanEqualAndStateIn`.

### 1.5 Múi giờ DB (lệch +7 giờ)
- Nguyên nhân: backend ghi/đọc `Instant` như UTC trong cột `DATETIME`, trong khi seed ghi giờ Việt Nam (spec 001 FR-035 quy định DATETIME = giờ Việt Nam).
- Sửa `application.properties`: `spring.jpa.properties.hibernate.jdbc.time_zone=Asia/Ho_Chi_Minh` và `spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type=TIMESTAMP` (cần cả hai). Kiểm chứng: session 09:30 trả `02:30Z`.
- **Cross-cutting**: ảnh hưởng mọi feature đọc/ghi `Instant`. Nên báo chủ 001, 006 và chạy lại test MySQL trước khi merge (tôi chỉ chạy test của phần đổi vé).

### 1.6 Dữ liệu dev (`ppswbs_workshop_test_20261029`, MySQL local)
- Đã xóa 269 session trùng thuộc bộ slot tiếng Anh ("Morning/Afternoon/Evening") của location 1 và 2, tắt 6 slot đó. Giữ session 2334 (có giữ chỗ cũ, giờ giữ chỗ đã bị xóa) — là bản trùng còn sót, có thể xóa.
- Đã xóa **toàn bộ giao dịch workshop** (61 bảng `booking_*`, `settlement_*`, `ticket_*` + `capacity_holds`, `attendance_*`, `group_*`, `late_payment_reviews`, báo giá, `operations_audit_events`, `facade_command_idempotency_records`, `event_publication`) bằng `DELETE` (cấu trúc còn nguyên). Tài khoản, sản phẩm, khuyến mãi giữ. Còn `loyalty_accepted_benefit` (20) và `reviews` (3) có thể tham chiếu đặt chỗ đã xóa, chưa kiểm.
- Đã gán tự động 284 bản ghi cho 2 Staff trên 145 session.
- Backup trước khi xóa (trong scratchpad cũ, có thể đã bị dọn): `...\scratchpad\ppswbs_workshop_test_20261029_before_slot_dedupe.sql` và `..._before_workshop_cleanup.sql`.
- `ppswbs_backend/scripts/seed-demo-data-all.sql` (chưa track): đã sửa để không tạo lại slot trùng (kiểm theo `start_time` và `active`). Seed này ghi giờ Việt Nam, nay khớp với backend đã sửa múi giờ.

### 1.6b Chạy local
- Backend chạy bằng `.claude/launch.json` (`backend`, cổng 8080, management 8081). Một tiến trình khác (PID 28156) đang giữ 8080 lúc kết thúc phiên; nó là bản có code mới (đã tự gán Staff). Frontend 5173.
- Khi chạy test Maven trong lúc backend đang chạy có thể gặp lỗi biên dịch ngẫu nhiên do tranh `target/`; chạy lại là hết.

### 1.7 Kiểm chứng đã chạy
`StaffAutoAssignmentServiceTest` (mới), `StaffSeesOnlyAssignedSessionsTest`, `TicketChangeOptionsServiceTest`, `TicketChangeOptionsApiServiceTest`, `TicketChangeServiceTest`, `GroupTicketChangeServiceTest`, `WorkshopOperationsSnapshotTest`, `ModulithArchitectureTests`: pass (68). FE Vitest `ticketChange.test.jsx` + `workshop-settlement`: 98 pass (chạy trước bước 7 ngày/Staff). **Chưa chạy toàn bộ suite, chưa thử bằng tay trên UI.**

### 1.8 File đã thay đổi (git status)
Backend main: `TicketChangeOptions.java`, `TicketChangeOptionsService.java`, `TicketReplacementRules.java`, `WorkshopSessionService.java`, `SessionStaffAssignmentRepository.java`, `WorkshopSessionRepository.java`, `TicketChangeDtos.java`, `TicketChangeOptionsApiService.java`, `TicketChangeService.java`, `StaffAutoAssignmentService.java` (mới), `application.properties`.
Backend test: `TicketChangeOptionsApiServiceTest`, `TicketChangeOptionsServiceTest`, `TicketChangeServiceTest`, `StaffSeesOnlyAssignedSessionsTest`, `StaffAutoAssignmentServiceTest` (mới).
Frontend: `TicketSessionPicker.jsx`, `GroupTicketList.jsx`. Spec: openapi 003.3, `003.2 spec.md`.

---

## 2. Việc còn lại nhỏ (tùy chọn)
- Cập nhật spec 003.3: FR-034 (7 ngày theo ngày, áp cả đổi trước), FR-044a (ẩn session hết chỗ/hết lượt đổi), quyền Staff đổi vé (quy tắc 2).
- Báo chủ 001/006 về thay đổi múi giờ; chạy test MySQL/IT các feature.
- Giới hạn buổi đích theo location của Staff khi đổi vé.
- Ô chat với Staff: **hệ thống chưa có**, cần feature riêng (chỉ có `AiChatboxFacade` của 004). Hiện khách chỉ có luồng "gửi yêu cầu cho Manager".
- Dọn bản trùng session 2334; kiểm `loyalty_accepted_benefit`/`reviews` còn tham chiếu đặt chỗ đã xóa.

---

## 3. VIỆC TIẾP THEO: gói workshop bán theo location (mở phiên mới, đi qua speckit)

### Brief để dán vào phiên mới

```
Làm "gói workshop bán theo location" (package availability per location). Nhánh quoc. Đã xong ở phiên trước (đã commit và push lên origin/quoc, đã merge develop): đổi vé trong cùng location, 7 ngày theo ngày VN, Staff đổi vé theo gán buổi, tự gán Staff theo location, sửa múi giờ DB. Chi tiết trong HANDOFF-2026-11-17-ticket-change-and-package-per-location.md.
Đọc CLAUDE.md, ppswbs-spec/AGENT.md, ppswbs_backend/AGENT.md, ppswbs_frontend/AGENT.md và ERROR-LOG.md trước. Dùng speckit skill (specify → clarify → plan → tasks), kiểm tra và đặt .specify/feature.json đúng feature (gợi ý 003.2, ảnh hưởng 003.1 và 003.3).

Yêu cầu của chủ feature 003 (Hồ Hoàng Quốc):
1. Có package chỉ bán ở location này, không bán ở location kia. Giá giữa các location giống nhau, không cần bảng giá theo location.
2. Manager thấy tất cả location và tất cả package, và bật active/inactive cho từng package ở từng location (dạng ma trận package × location).
3. Khi package inactive ở một location: khách không thấy/không đặt được ở đó (xem session theo package+location, reserve đặt chỗ kiểm tra lại). Đặt chỗ đã có vẫn giữ nguyên.
4. Đổi vé: cho chuyển sang session ở location khác, chỉ khi package được bán (active) ở location đó. Gói Wax Casting (2 buổi) phải đổi cả hai buổi sang cùng một location có bán. Staff chỉ thao tác trong location của mình, vé chuyển sang location khác cần Manager.
5. Hiện trạng: workshop_packages không có cột location, same_location_required=1 ở cả 5 gói (chặn đổi chéo location), TicketChangeOptionsService.candidatesFor lọc theo cờ này, bản chụp chính sách đặt chỗ nằm ở workshop_booking_package_policy_snapshots.
6. Session theo location: các session ở từng location độc lập (sức chứa, trạng thái, reschedule quota, Staff gán riêng), dù cùng 3 ca giờ giống nhau. Hệ quả cần làm:
   - Danh sách buổi đổi và danh sách session khi đặt chỗ phải hiện tên location (ChangeOption/ListSession cần trả tên location, không chỉ id; GroupTicketList và TicketSessionPicker hiển thị "Chi nhánh – ngày giờ – còn N chỗ – còn M lượt đổi").
   - Không coi hai session cùng giờ ở hai location là trùng nhau (kiểm tra trùng/khoảng cách slot cùng ngày của gói nhiều buổi vẫn tính theo từng location).
   - Khi vé chuyển sang location khác: cập nhật location của vé/đặt chỗ, quyền Staff (theo location mới và gán buổi mới), và các bản ghi quyết toán/add-on đã gắn location cũ cần được liệt kê để chốt cách xử lý.
   - Reschedule quota của session đích tính theo session đích, không chung giữa các location.

Câu hỏi cần clarify: mặc định của 5 gói hiện có khi migration (đề xuất: active ở mọi location đang hoạt động; chủ feature chưa chốt); same_location_required còn nghĩa gì với gói 1 buổi; quyền xem ma trận của Staff/Admin; audit khi bật/tắt; hiển thị cho khách khi gói inactive ở location đang chọn; sau khi vé chuyển location thì folio, tiền đã thu và add-on gắn location cũ xử lý thế nào (giá giữa các location như nhau nên chủ yếu là ghi nhận đúng chi nhánh).
```

### Quyết định đã nhận từ chủ feature
- Làm qua speckit ở phiên mới (không code ngay).
- Manager thấy mọi location và mọi gói, bật/tắt active/inactive cho từng gói ở từng location.
- Đổi vé được sang location khác nếu gói được bán ở đó.
- Giá giống nhau giữa các location.
- Chưa chốt: mặc định của 5 gói hiện có khi migration.

---

## 6. Checklist việc cần làm (tick khi xong, rồi ghi một dòng ở mục 7)

Phiên mới (gói bán theo location):
- [x] speckit specify: spec cho "gói bán theo location" (003.2 chính; ảnh hưởng 003.1, 003.3) — xong 2026-11-17, `spec.md` FR-037–FR-046
- [x] speckit clarify: chốt mặc định 5 gói hiện có, ý nghĩa `same_location_required`, quyền xem ma trận, audit, hiển thị khi inactive, xử lý folio/add-on khi vé đổi location — xong 2026-11-17 (cả OTA, location của vé, Mland thay buổi)
- [x] speckit plan — xong 2026-11-17 (`plan.md` Phase 18, `data-model.md`, OpenAPI, facade contract, `research.md`, `quickstart.md`)
- [x] feature-design pack (`design/architecture.md`, `workflows.md`, `ui-flow.md`) và `approval.md` (PARTIALLY APPROVED, Tech Lead Hồ Hoàng Quốc) — xong 2026-11-17
- [x] speckit tasks — xong 2026-11-17, Phase 18 = T144–T162 trong `tasks.md`
- [x] Triển khai Phase 18 backend (T144–T162) — phiên `be-pkg-location`, nhánh `claude/pkg-location-backend-157686`, commit `dcf1a203` đã push, T144–T162 đã tick trong `tasks.md`. **Chưa merge vào `quoc`**; kết quả test chưa được xác minh lại ở phiên điều phối, xem báo cáo của phiên đó trước khi merge
- [x] Merge nhánh `claude/pkg-location-backend-157686` vào `quoc` — xong 2026-11-17, merge commit `a4969c65`, không xung đột, chưa push (V178 vẫn cần kiểm Flyway ở T168)
- [ ] 003.1 (chủ: Hồ Hoàng Quốc): đổi `getTicketFolioContext` suy location từ buổi hiện tại của vé; xử lý `PACKAGE_NOT_AVAILABLE_AT_LOCATION` ở giỏ; hiển thị tên location (B2). **Đính chính 2026-11-17: task 003.1 ĐÃ có**, nằm trên `quoc` (không phải nhánh worktree): Phase 18 T154–T164 trong `003.1-workshop-booking/tasks.md`, commit `6a53a31c`, `07e7e1b8`, `33865a60` (có approval). Phiên `tasks-003-1-loc` báo "không có gì để commit" vì đã ghi thẳng vào `quoc`. Việc còn lại: implement T154–T164 (BE T154–T159, T164; FE T160–T162; contract T163 chờ owner duyệt FR-055)**
- [x] 003.3 spec và tasks (B3): phiên `tasks-003-3-loc`, nhánh `claude/pkg-location-003-3-tasks-325d2e`, commit `3bc64711` đã push (`docs(003.3): record owner approval of changes across locations (T182)`); có Phase 14 (12 task, T182 duyệt spec đã xong). Chưa merge vào `quoc`
- [ ] 003.3 backend: 4 task làm được ngay (T185 cửa sổ 7 ngày, T183 `locationName`, T184 đổi khác chi nhánh, T188 Mland offer); phiên đó đã viết sẵn brief, chưa mở phiên; 8 task còn lại làm sau theo báo cáo của phiên
- [x] UI ma trận Manager (B1) — Figma xong và đã duyệt 2026-11-17: https://www.figma.com/design/lNOLBLlL9DEeyU5EvXJnPd (11 frame, node ID trong `003.2.../design/ui-flow.md`); quyết định hàng Đà Nẵng vẫn tick được; commit `18261b50` đã push. Task FE đã sinh 2026-11-17: T163–T168 (`tasks.md` mục "Frontend: Manager matrix page"), B1 đã gỡ. Việc còn lại: implement FE theo `ppswbs_frontend/AGENT.md`, rồi T174 chạy với backend thật
- [ ] FE ma trận Manager T163–T168: T163–T166 xong và tick; T167 và T168 CHƯA tick (2026-11-17). T167: test 857/857 và build đạt, thiếu lint (không có trong FE) và so Figma 1440 px. T168: Manager bật/tắt, F5, 375 px đạt; còn (1) Staff gõ URL bị đưa về trang chủ thay vì trang từ chối, blocker `FE-DESIGN-0006` trong `ppswbs_frontend/ERROR-LOG.md`, chờ Hồ Hoàng Quốc và Phan Sơn (chủ 001) chốt; (2) chưa thử Admin
- [ ] 003.4: bước đẩy cập nhật lên OTA, quyết có làm hay không (B4)
- [ ] Implement backend (bảng package × location, migration, API Manager, lọc xem session/đặt chỗ, lọc đổi vé, tên location trong ChangeOption)
- [ ] Implement frontend (ma trận Manager, hiển thị tên location trong danh sách đặt chỗ và đổi vé)
- [ ] Test (unit, Modulith, MySQL IT) và bàn giao QA

Việc nhỏ tồn đọng từ phiên trước:
- [ ] Cập nhật spec 003.3 (FR-034 7 ngày theo ngày, FR-044a ẩn session hết chỗ/hết lượt, quyền Staff đổi vé)
- [ ] Báo chủ 001/006 về thay đổi múi giờ; chạy lại test MySQL/IT các feature
- [ ] Giới hạn buổi đích theo location của Staff khi đổi vé
- [ ] Quyết định có làm chat với Staff (feature riêng) hay không
- [ ] Dọn session 2334; kiểm `loyalty_accepted_benefit` và `reviews` còn tham chiếu đặt chỗ đã xóa
- [x] Commit và push các thay đổi của phiên trước (xong 2026-11-17, commit `ad7489cc`, `1d08ce5f`, `afc6deac`, merge `7ac6cbb5`)

## 7. Nhật ký tiến độ

Mẫu: `YYYY-MM-DD | người/phiên | đã xong | kiểm chứng | còn lại/blocker | commit`

- 2026-11-17 | phiên trước (quoc) | mục 1.1–1.6, handoff này | test mục 1.7 | mục 2 và 6 | chưa commit
- 2026-11-17 | phiên trước (quoc) | merge `origin/develop` (15 commit, không xung đột), commit và push lên `origin/quoc` | BE 75 test pass (gồm Modulith, `ExecutiveTrendServiceTest` mới), FE 180 test pass (`workshops`, `workshop-settlement`); chưa chạy toàn bộ suite, chưa thử UI | develop có migration mới V166, V177 chưa chạy thử với backend; không commit `backend-startup.log`, `ppswbs_frontend/ERROR-LOG.md`, `.claude/`, `flyway-checksum-fix.md`, `seed-demo-data-all.sql`, file handoff này | `ad7489cc` (múi giờ), `1d08ce5f` (đổi vé, Staff, tự gán), `afc6deac` (spec), `7ac6cbb5` (merge develop); push `9947f913..7ac6cbb5`
- 2026-11-17 | phiên gói theo location (quoc) | specify, clarify, plan, feature-design, `approval.md`, tasks cho 003.2 Phase 18 (T144–T162) | YAML OpenAPI parse được, 328 `$ref` hợp lệ (SnakeYAML); `$speckit-analyze` Phase 18: 0 CRITICAL, 0 HIGH, 4 MEDIUM/LOW đã xử lý; chưa chạy code hay test | chưa triển khai; chưa có task 003.1/003.3 (B2, B3), UI chờ Figma (B1), 003.4 đẩy OTA (B4); tên gói trong DB thật chưa kiểm, chỉ khớp `V161__add_data.sql` | `803e83bc`, push `7ac6cbb5..803e83bc`
- 2026-11-17 | phiên điều phối (quoc) + 3 phiên con ở worktree riêng | backend 003.2 T144–T162 (`be-pkg-location`); spec và tasks 003.3 (`tasks-003-3-loc`); mở phiên Figma ma trận (`figma-pkg-matrix`) | chưa xác minh lại test của backend ở phiên điều phối (kết quả nằm trong báo cáo phiên `be-pkg-location`); chưa merge nhánh nào vào `quoc` | task 003.1 đã có trên `quoc` (T154–T164, đính chính: lúc đầu ghi nhầm là chưa viết); merge hai nhánh; mở phiên backend 003.3; UI chờ Figma; 003.4 đẩy OTA | backend `dcf1a203` (`claude/pkg-location-backend-157686`), 003.3 `3bc64711` (`claude/pkg-location-003-3-tasks-325d2e`); cả hai đã push
- 2026-11-17 | phiên `figma-pkg-matrix` (quoc) | Figma trang ma trận (8 trạng thái desktop, 2 mobile, ghi chú a11y), ghi duyệt vào `ui-flow.md`, `approval.md` (mục "UI approval"), `tasks.md` (B1) | đã chụp kiểm 01, 04, 06, 08, 10, 11; chưa xem lại 02, 03, 05, 07, 09; tương phản tính tay | chưa có task FE; hiển thị 003.1/003.3 chưa có Figma | `18261b50`, merge `2a30d254`, đã push
- 2026-11-17 | phiên `tasks-fe-pkg-matrix` (quoc) | task FE trang ma trận Manager: T163–T168 (API client, component, trang, CSS, route, menu Manager-only, test, gate FE, E2E với backend thật), B1 gỡ trong bảng Blocked, hash `tasks.md` ghi vào `approval.md`; `.specify/feature.json` đã trỏ 003.2 sẵn (không đổi) | analyze rút gọn: FR-037/038/040/044 phủ đủ, 0 CRITICAL/HIGH; 2 giả định đã ghi cần Tech Lead xác nhận: breakpoint thẻ mobile 768 px (Figma chỉ có 375 px) và ô chỉ đổi sau khi server xác nhận (kiểm frame 05); chưa chạy code/test; chưa xem lại Figma bằng công cụ trong phiên này, chỉ dùng `ui-flow.md` | chưa implement FE; nhánh backend `claude/pkg-location-backend-157686` đã tick T144–T162 nên merge vào `quoc` có thể xung đột ở `tasks.md` (hai bên cùng sửa Phase 18); E2E trình duyệt thuộc `ppswbs-testing/` | commit xem `git log` nhánh `quoc`
- 2026-11-17 | phiên `fe-pkg-matrix` (quoc) | Bước 0: `git merge refs/heads/claude/pkg-location-backend-157686` vào `quoc` sạch (`a4969c65`, không xung đột, không push). FE T163–T166 xong, tick `tasks.md`: `api/packageAvailabilityApi.js` (4/4 test), `components/PackageAvailabilityMatrix.jsx` + CSS `.pam-*` cuối `workshop-operations.css` (9/9), `pages/PackageAvailabilityMatrixPage.jsx` (12/12), route `/operations/package-availability` trong `routes.jsx`, menu "Ma trận gói × chi nhánh" Manager-only trong `OperationsSidebar.jsx`, thêm test vào `operationsSidebarRoles.test.jsx` (10/10) | chạy test hẹp từng file như trên; `npm run build` OK (358 module, chỉ cảnh báo chunk >500 kB có sẵn); xem Figma frame 01, 02(chưa), 03, 04, 05, 06, 07, 08 bằng screenshot: frame 04/05 không có optimistic flip (ô chỉ spinner, tick sau khi server xác nhận) => giả định 2 đúng | **T167 dở:** `npm test` toàn bộ bị OOM ("Zone Allocation failed", máy thiếu RAM, kể cả `--max-old-space-size=4096`); đã chạy lại `npx vitest run --maxWorkers=1 --no-file-parallelism` ở nền, kết quả chưa đọc. **Không có script lint và không có cấu hình ESLint** trong `ppswbs_frontend` (không thêm dependency; báo lại). Chưa so trang với Figma ở 1440 px và 375 px bằng trình duyệt; chưa xem frame 02, 09, 10, 11. **T168 chưa làm** (chưa kiểm Flyway V178, chưa chạy backend/frontend, chưa đăng nhập tài khoản dev). Phát hiện cần báo: (a) breakpoint 768 px: `workshop-operations.css` không có luật 768 (chỉ 600/700/900/1100), đã dùng `(max-width: 767.98px)` qua matchMedia = giả định cần xác nhận; (b) `useHttp` khi gặp 403 gọi `reportAccessDenied` → `SessionNavigationBoundary` điều hướng về `/` kèm thông báo, nên trang từ chối quyền (frame 06) có thể không hiện trong app thật dù test đơn vị đạt; cần quan sát ở T168 và nếu đúng thì ghi `ppswbs_frontend/ERROR-LOG.md` + hỏi chủ feature, không sửa `shared/` khi chưa được duyệt; (c) khác Figma đã biết: dùng `ota-topline` thay breadcrumb "VẬN HÀNH › …" và nhãn "Manager" góc phải; thêm nút "Thử lại" (503/mất mạng, cùng Idempotency-Key) cạnh "Tải lại ma trận" ở banner lỗi, frame 08 chỉ có nút thứ hai | Chưa commit. File chưa commit: `tasks.md`, `OperationsSidebar.jsx`, `operationsSidebarRoles.test.jsx`, `routes.jsx`, `workshop-operations.css`, và mới: `packageAvailabilityApi.js`, `PackageAvailabilityMatrix.jsx`, `PackageAvailabilityMatrixPage.jsx`, 3 file test `packageAvailability*.test.*`. Phiên mới: đọc kết quả vitest nền (hoặc chạy lại `npx vitest run --maxWorkers=1 --no-file-parallelism`), làm nốt T167 (so Figma 1440/375), rồi T168 theo `ppswbs_frontend/RUN-PROJ-DEV.md`
- 2026-11-17 | phiên `fe-pkg-matrix` tiếp (quoc, acc mới) | T167 gate test: `npx vitest run --maxWorkers=1 --no-file-parallelism` = 97 file / 857 test pass (240 s). T168 một phần: backend chạy được trên DB dev, Flyway V178 đã áp dụng (success=1), bảng `workshop_package_location_availability` có 8 dòng; backend :8080/:8081 và frontend :5173 đang chạy | trang `/operations/package-availability` hiện "cần đăng nhập Staff hoặc Manager" (đăng nhập Google redirect, tôi không nhập thông tin đăng nhập) | còn lại: người dùng đăng nhập Manager trong pane trình duyệt, rồi quan sát ma trận thật, bật/tắt ô, kiểm 403 bằng Staff (xem nghi vấn useHttp→SessionNavigationBoundary), so Figma 1440/375 để đóng T167, tick T167/T168 | chưa commit
- 2026-11-17 | phiên `fe-pkg-matrix` tiếp (quoc) | T168 chạy tay với backend thật: Manager bật/tắt ô lưu DB + audit `WORKSHOP_PACKAGE_AVAILABILITY_CHANGED`; F5 giữ ô và phiên; 375 px ra dạng thẻ giống Figma (người dùng xác nhận); Staff bị 403 + audit DENIED. Dọn DB dev: xóa location 101, 102, package 2 (backup trong scratchpad `ppswbs_before_junk_cleanup.sql`) | spec FR-038 nói "tất cả gói", nên gói RETIRED hiện trong ma trận là đúng | **BLOCKER FE-DESIGN-0006:** Staff gõ URL bị đưa về trang chủ, không hiện trang từ chối (Figma 06) do `shared` useHttp/SessionNavigationBoundary; chờ chủ feature chốt. T167/T168 CHƯA tick (không có lint; Admin chưa thử) | chưa commit
- 2026-11-17 | phiên `fe-pkg-matrix` (quoc) | chốt trạng thái cuối phiên: T168 chưa xong, còn 2 việc: (1) quyết định hành vi 403 của Staff (`FE-DESIGN-0006`), (2) thử Admin (menu không có mục, gõ URL bị từ chối). T167 còn thiếu lint và so Figma 1440 px | chạy tay đã nêu ở dòng trước; backend :8080 và frontend :5173 vẫn đang chạy | phiên mới: sau khi chủ feature chốt FE-DESIGN-0006, làm nốt Admin, rồi tick T167/T168 và commit code FE (file chưa commit liệt kê ở dòng 2026-11-17 `fe-pkg-matrix` đầu tiên, thêm `ppswbs_frontend/ERROR-LOG.md` là local-only, không commit) | chưa commit
- 2026-11-17 | phiên 003.1 Phase 18 (quoc) | T154–T162, T164 (BE+FE) tick trong 003.1 `tasks.md`; T163 (contract) chưa làm, chờ chủ feature duyệt FR-055 | BE: hồi quy workshopbooking+workshopsettlement+Modulith 956 test 0 fail (5 skip) trên schema MySQL mới `ppswbs_workshop_t18_it`; FE: vitest 869/869, build ok | schema dùng chung `ppswbs_workshop_test_it` đang có V178 lỗi (success=0), cần drop/tạo lại, chưa đụng; IT 003.1 khác có thể cần dòng availability cho gói fixture (đã sửa LatePaymentReviewIT); T156 giới hạn: scope theo mọi location hiện tại của dòng + gán trên dòng | chưa commit
- 2026-11-17 | phiên `audit-review` (quoc) | speckit cho màn hình audit của Manager: US7, FR-047–FR-055, SC-013 trong spec 003.2 (đã clarify với chủ feature: Operations + Settlement hai tab, metadata whitelist, actor id + tên, lý do settlement hiện đầy đủ), plan Phase 19, data-model, OpenAPI `/workshop-audit/operations-events` và `/settlement-records`, tasks T169–T180, blocker B5 (facade 003.3) và B6 (chưa có Figma), ghi `approval.md` ở trạng thái chưa duyệt | OpenAPI YAML parse được, 346 `$ref` hợp lệ (SnakeYAML); chưa chạy `$speckit-analyze`, chưa chạy code | chưa có duyệt Tech Lead, mockup UI đã được chủ feature duyệt (`design/audit-review-mockup.html`, B6 gỡ); cần chủ 003.3 đồng ý facade (T173); xác minh `actor_reference` của settlement có phải account id (T169); `.specify/feature.json` đã trả về 003.1 | chưa commit
- 2026-11-17 | phiên `audit-review` tiếp (quoc) | chủ feature duyệt thiết kế backend và facade 003.3 (T169, B5): `design/architecture.md`, `workflows.md` WF-10/WF-11, contract thêm `actorReference` (settlement `actor_reference` là `account:<id>` hoặc `SYSTEM`), ghi `approval.md` kèm mã băm | OpenAPI parse được, 346 `$ref` hợp lệ; chưa chạy `$speckit-analyze`, chưa có code | sẵn sàng làm T170–T174 (BE trước, FE sau); mở phiên implement riêng | chưa commit
- 2026-11-17 | phiên `audit-review` (quoc) | $speckit-analyze Phase 19: 1 CRITICAL (route quyết toán trong Operations tạo vòng module) đã sửa: route/service/query quyết toán chuyển vào `workshopsettlement` (T173), bỏ facade; sửa F2–F8; `approval.md` ghi revision mới | OpenAPI parse được, 346 `$ref` hợp lệ | chạy lại analyze, rồi mở phiên implement BE (T170–T176) và FE (T177–T180) | chưa commit
- 2026-11-17 | phiên `audit-review` (quoc) | gộp task Phase 19 còn T169–T174 theo yêu cầu chủ feature (BE Operations T170, Quyết toán T171, kiểm BE T172, FE T173, kiểm FE T174) | chưa chạy lại analyze sau gộp | mở phiên implement | chưa commit
- 2026-11-17 | phiên `be-003-3-phase14` (quoc) | 003.3 Phase 14 backend T183–T188, T190 xong và tick (T189 FE, T191–T193 để phiên sau). Bước 0: KHÔNG merge được nhánh `claude/pkg-location-003-3-tasks-325d2e` vì 6 file 003.2 của phiên audit chưa commit bị merge ghi đè; đã chép riêng 4 file (003.3 `plan/spec/tasks.md`, `contracts/workshop-interfeature-facades.md`) từ nhánh đó (quoc chưa đổi chúng), 6 file 003.2 của nhánh đó (approval/ui-flow/workflows/plan/spec/tasks, vài dòng) chưa vào, cần merge sau khi phiên audit commit. Thay đổi: helper `TicketChangeWindow` (hết ngày thứ 7 VN), `locationName` + openapi, audit `TICKET_LOCATION_CHANGED` (reason `from=;to=`), tên chi nhánh trong Mland offer, migration V179 `settlement_evidence_conclusions.branch_id` (EXCESS giữ chi nhánh lúc ghi), facade 003.2 `getWorkshopAddOnLocation`, route Manager `GET /folio/previous-location-add-ons` | 693 test (workshopsettlement, workshopoperations, Modulith, contract IT) 0 fail; chưa chạy MySQL IT (T191), chưa commit
- 2026-11-17 | phiên `fe-test-003-3-phase14` (quoc) | 003.3 Phase 14: T189 FE (UI duyệt trong phiên), T191 MySQL IT một phần (CrossLocationChangeMysqlIT 4/4; facade 003.1/003.2 mock), T192 tài liệu, T193 chạy tổng hợp (BE 7/7 Modulith, 4/4 contract, IT settlement pass; FE 884/884). Còn lại: WCAG của T138, 2 test 003.2 FacadeOperationsMySqlIT lỗi do dữ liệu seed V178 (chủ 003.2). Chưa commit. Nhắc: 006 T110 do Hồ Hoàng Quốc tự làm (Khương ủy quyền).
- 2026-11-17 | phiên chính (quoc) | bỏ hẳn ngoại lệ `EARLY_CHECKIN` (quyết định chủ feature Hồ Hoàng Quốc; lý do: đã có `checkInOpensAt` per-session, ngoại lệ không giới hạn thời gian). Sửa: `StandardizedReasonCode.java` (bỏ enum), `OperationsSessionPage.jsx` (bỏ lý do, nút "Chuẩn bị override", mặc định đổi sang `CUSTOMER_DEVICE_UNAVAILABLE`, bỏ câu gợi ý), `checkInNotOpen.test.jsx`; spec 003.2 (FR-005, bảng lý do, ghi chú quyết định), contract (enum + mô tả), quickstart | FE `checkInNotOpen` + `sessionAdministration` 16/16; BE `WorkshopErrorCatalogueContractTest` 6, `ReasonCatalogueAndAddOnVisibilityIT` 4, `CheckInServiceTest` 4, `TicketRecoveryContractIT` 10, `WorkshopOperationsOpenApiIT` 12 đều pass | chưa sửa: `tasks.md` T126 còn nhắc EARLY_CHECKIN (lịch sử, giữ), `design/*`, `artifacts/003-spec-code-gap-review.md`; dòng cũ trong DB `attendance_actions.reason_code='EARLY_CHECKIN'` (nếu có) vẫn đọc được vì là chuỗi; chưa kiểm UI bằng tay | chưa commit
- 2026-11-17 | phiên `fe-test-003-3-phase14` tiếp (quoc) | T191 hoàn tất: thêm CrossLocationRealModulesMySqlIT (003.1+003.2+003.3 thật, 3/3), CrossLocationChangeMysqlIT 5/5 (có Staff chi nhánh cũ bị từ chối). Sửa gốc lỗi 2 test FacadeOperationsMySqlIT: TicketFolioContextMoveMySqlIT truncate locations/packages nhưng sót bảng tham chiếu (availability, ticket_session_replacements...) nên id tái dùng; đã bổ sung danh sách truncate, bộ IT chạy chung pass.
