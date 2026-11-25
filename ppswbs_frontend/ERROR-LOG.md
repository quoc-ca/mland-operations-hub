# Frontend design blockers

## FE-DESIGN-0001 — BLOCKED

- Time: 2026-10-31T19:16:50+07:00.
- Reporter: Codex. Scope: project-wide frontend and Figma screen implementation.
- Sources: `AGENT.md` sections 2–5; `../ppswbs-spec/screen.md`; Figma file `chUTTiTlLrFW7Dv0IdN4gC`, frame `1:543`.
- Evidence: `ppswbs_frontend` currently contains only `AGENT.md`; there is no application scaffold or package manifest. The supplied Figma frame is a generic Home Page with placeholder content. The screen flow enumerates many journeys, while corresponding complete screen designs, UI states, API bindings, and an approved frontend implementation plan/version are not established by that frame alone.
- Impact: production implementation of the whole system would require choosing missing design and behavior, dependencies and integration contracts. In particular, navigation and authenticated/payment state must not be inferred from a generic visual sample.
- User-provided design direction: square-looking buttons, existing color palette, concise responsive layouts, banner and product-image carousels; create Figma screens and real frontend.
- Changes before stopping: no frontend code, Figma edits, or tests. Figma Home Page metadata and design context were read.
- To resume: establish the first screen's complete design and applicable behavior/API contracts, and a frontend scaffold plan with dependency versions; clarify ownership and unresolved cross-feature integration as necessary. The user requested both Figma design and implementation, so design scope should be coordinated with this gate.

### Resolution update — 2026-10-31T19:25:00+07:00

- The user explicitly approved creating the frontend foundation and requested that the work continue from the Home Page.
- The current implementation scope is limited to the public Home Page: responsive presentation, banner carousel, and product-image carousel. Product, account, cart, workshop booking, payment, and role-specific journeys remain outside this resolved scope until their screen behavior and API contracts are supplied.

### Resolution update — 2026-10-31T20:10:00+07:00

- The user explicitly expanded the work to cover all project screens, including areas owned by other team members.
- A React/Vite prototype now registers 109 screen routes from `../ppswbs-spec/screen.md`. It includes responsive UI flows for public catalogue and workshops, Member cart/checkout/orders, Staff retail operations, Manager dashboard, and role-scoped workspace shells for the remaining screen inventory.
- These are presentation prototypes with local example data. They do not claim live authorization, payment, delivery, booking, product, or loyalty integration until the owning feature contracts are connected.

## FE-DESIGN-0002 — BLOCKED

- Time: 2026-10-31T20:35:00+07:00.
- Reporter: Codex. Scope: FE-08 production frontend integration for the Member cart, checkout, payment initiation, and order history.
- Sources checked: `ppswbs_backend/.../readyringsales/controller/CartController.java`, `CheckoutController.java`, `dto/CartDtos.java`, `dto/CheckoutDtos.java`; `ppswbs-spec/APIs.md` rows for `readyringsales`; `ppswbs-spec/features/008-retail-order-and-fulfillment/tasks.md` T004 and T020.
- Facts: backend exposes typed cart and checkout routes, all of which require an active Member resolved from a verified Firebase bearer identity. The existing frontend has no approved Firebase client/session provider, no defined API-origin configuration, and no approved token lifecycle/error-recovery contract. T004 remains open and explicitly records these frontend decisions as missing.
- Why work is stopped: wiring calls with a browser-invented token, `X-Debug-Account-Id`, local role, guessed backend URL, or invented retry/session behavior would bypass or contradict the documented authorization contract. Payment and GHTK paths additionally require their owner contracts.
- Files changed before stopping: none for this integration attempt. Existing prototype files remain local-data presentation only. Checks performed: source/contract inspection only; no API request was made.
- Needed to resume: approved frontend API base URL and Firebase client/session contract (how the current ID token is obtained/refreshed and made available to FE-08), plus the intended user-facing handling for `401`, `403`, validation, conflict, and integration-unavailable API errors. Then Cart, Checkout, payment-initiation, cancellation, and order-history requests can be connected without guessing.

### Partial delivery update — 2026-10-31T20:48:00+07:00

- The public FE-02 catalogue contract is independent of Firebase identity and is now consumed by the frontend through `src/api/catalogue.js`: product list, categories, product detail and public media URLs.
- Catalogue and product-detail views implement loading, empty, error and retry states. The Member-only add-to-cart action remains disabled pending the FE-08 identity/session contract.
- `npm.cmd run build` passed. Manual live-API check to `http://127.0.0.1:8080/api/v1/products` timed out because the local backend was not responding; this is an environment observation, not proof that the endpoint contract is invalid.

### FE-08 UI preparation update — 2026-10-31T21:05:00+07:00

- Product detail now supports public-product variant selection and a 1–99 quantity picker; it does not claim an inventory count that the public catalogue contract does not expose.
- Cart UI supports local quantity edits, removal, branch grouping and the 50-line presentation limit. Checkout validates recipient fields, presents delivery/pickup, voucher/points and branch shipping summaries. Member order filtering and Staff-operation progress are interactive UI states.
- `src/api/retail.js` encodes the known protected FE-08 API routes and typed error shape. It deliberately requires a caller-provided Firebase ID token and makes no request without one.
- Verification: `npm.cmd run build` passed. These interactions are client-side preparation, not evidence of Member API integration.

### GHTK quote integration check — 2026-11-01T00:00:00+07:00

- Scope requested: connect the FE-08 checkout UI to the local, read-only GHTK fee-quote endpoint.
- Backend contract now expects a verified Member bearer token and `POST /api/v1/ready-ring-shipping-quotes` with `locationId` plus recipient address/province/district/ward. It returns a server quote or `deliveryAvailable=false`; it does not create a shipment.
- The existing `RetailCheckoutWorkspace` is a local-data prototype. `src/api/retail.js` deliberately requires a caller-provided Firebase ID token, but no approved Firebase session provider/token lifecycle/API-origin recovery flow exists in the rendered application.
- No frontend source was changed. Calling the protected endpoint with a browser-invented token or a debug account header would violate the Member authorization contract.
- To resume this narrow integration: provide the existing FE-01 auth/session hook or provider that yields the current Firebase ID token for the Member checkout route, and confirm the API-origin/error-recovery contract. The existing GHTK recipient form can then call `createRetailClient(getIdToken).quoteShipping(...)` without exposing the provider token.

### Resolution update — 2026-11-06T17:30:00+07:00

- Revision R-SL (Shared session lifecycle & Google redirect-only handoff) is fully implemented under FE-001:
  - `AuthProvider` and `useHttp` provide the shared Firebase ID token lifecycle, terminal session-loss reporting (`SESSION_LOST`), access denial (`ACCESS_DENIED`), and authenticated client creation.
  - `SessionNavigationBoundary` and `RequireSession` enforce canonical route protection, draft cleanup, and safe Home navigation.
  - All 83 automated frontend tests in `src/features/accountmanagement/tests/` (including 28 router integration tests and 18 redirect/re-auth tests) pass cleanly.
  - Production Vite bundle builds with zero errors (`npm run build`).

## FE-DESIGN-0003 — RESOLVED

- Time: 2026-11-06.
- Reporter: Claude. Scope: FE-08 retail checkout, payment result, Staff shipment management, and Manager payment reconciliation.
- Facts: the Firebase session provider from FE-01 is now available (`useHttp`, `useAuth`, `FirebaseAuthClient`), so the earlier blocker on identity/session is closed for FE-08. Retail API calls go through `features/retail-order/infrastructure/retailApi.js` with the current ID token.
- Delivered: Member cart, checkout, and order history (`/member/cart`, `/member/checkout`, `/member/orders`); Manager payment reconciliation (`/manager/retail-payment-reconciliations`); Staff GHTK shipment management (`/staff/retail-shipments`) with status transitions PREPARING → HANDED_TO_CARRIER → IN_TRANSIT → DELIVERED.
- Navigation: the shared `MainTopbar` dropdown now exposes Cart, Staff GHTK shipments and Manager payment reconciliation, restricted by role. Before this change the merge with the shared-topbar refactor had left these pages reachable only by typing the URL.
- Verification: `npm run build` passed after the merge (243 modules, 5.74 s). Chunk-size warning above 500 kB is pre-existing and unrelated to FE-08.

### Cleanup — 2026-11-06

- Removed the superseded `src/features/readyringsales/` tree. The remote branch had already renamed it to `src/features/retail-order/`; only `WorkshopBenefitPanel.jsx` still imported its stylesheet, now pointing at `features/retail-order/ui/readyringsales.css` (byte-identical file).

## FE-DESIGN-0004 — RESOLVED (2026-11-15)

- Ngày: 2026-11-14
- Reporter: Claude (Claude Code)
- Người yêu cầu: dangkhuong06 (brief nhóm D1, làm thay Hồ Hoàng Quốc trên nhánh `quoc`)
- Feature/task: 003.2 T112 — Manager sửa phiên (PATCH /api/v1/workshop-sessions/{id})
- Lỗi/blocker: backend PATCH không đổi được khung giờ/địa điểm/ngày, và contract không có trường xác nhận cảnh báo giờ mở check-in (FR-005b); contract cũng không có trường phiên bản cho xung đột stale.

**Nguồn design:** `003.2-workshop-operations/tasks.md` T112; spec FR-001a, FR-005b, clarification "Direct slot/branch changes are allowed only when neither exists" (spec.md ~dòng 250, 278, 736); contract `workshop-operations.openapi.yaml` `PATCH /workshop-sessions/{sessionId}` với body `SessionWrite` (locationId, slotId, workshopDate, capacity bắt buộc; checkInOpensAt).

**Fact (đọc code nhánh `quoc` @ b28ede32):**
- `WorkshopSessionService.updateSession` chỉ áp `capacity` (qua `validateCapacityReduction` → `WorkshopCapacityConflictException`, 409) và `checkInOpensAt` (đặt `MANAGER_SET`). `locationId`, `slotId`, `workshopDate` trong body bị bỏ qua im lặng: FE gửi khung giờ mới sẽ nhận 200 mà phiên không đổi.
- FR-005b đòi hiển thị giờ check-in cũ + cảnh báo trước khi lưu khi đổi slot của phiên có override, và audit "acknowledgement"; contract `SessionWrite` không có trường acknowledgement.
- Không có `version`/`If-Match` trong contract hay DTO. Backend chỉ trả `409 OPTIMISTIC_LOCK_CONFLICT` khi hai transaction đụng nhau; không phát hiện được form đã cũ.

**Vì sao không làm đúng được nếu không tự quyết:** phần "slot/location while uncommitted" của T112 không có API thật; tự làm form sẽ báo thành công giả. Phần cảnh báo FR-005b cần trường contract chưa có.

**Hiện trạng lúc dừng:** chưa sửa code FE nào cho T112. Phần sức chứa + ghi đè mở check-in khớp backend.

**Cần quyết định / điều kiện tiếp tục:** owner 003.2 (Hồ Hoàng Quốc) chốt: (1) backend PATCH hỗ trợ đổi slot/location/ngày khi chưa có cam kết, hay T112 thu hẹp còn sức chứa + check-in override; (2) trường xác nhận cảnh báo FR-005b trong `SessionWrite`; (3) "stale-version" là 409 `OPTIMISTIC_LOCK_CONFLICT` hiện có hay cần `version` trong contract.

### Diễn biến — 2026-11-14
- Người yêu cầu chọn: làm phần không bị chặn. Đã làm sức chứa + giờ mở check-in qua `SessionEditForm` (`ppswbs_frontend/src/workshop-operations/components/SessionAdminForms.jsx`): body giữ nguyên location/slot/date hiện tại của phiên; hiện lỗi `INSUFFICIENT_CAPACITY` và 409 `OPTIMISTIC_LOCK_CONFLICT` kèm nút "Tải bản mới nhất". Test `sessionAdministration.test.jsx` khối T112 4/4.
- Phần đổi slot/location/ngày và cảnh báo FR-005b CHƯA làm; T112 vẫn `[ ]` trong tasks.md, chờ quyết định ở trên.

## FE-DESIGN-0005 — RESOLVED (2026-11-15)

- Ngày: 2026-11-14
- Reporter: Claude (Claude Code)
- Người yêu cầu: dangkhuong06 (brief nhóm D1, làm thay Hồ Hoàng Quốc trên nhánh `quoc`)
- Feature/task: 003.4 T119 — cột và bộ lọc "loại" trên OtaReconciliationPage
- Lỗi/blocker: response work item không có `category`, và contract không liệt kê giá trị hợp lệ của `category`.

**Nguồn design:** `003.4-workshop-ota-connectivity/tasks.md` T119; spec C2; contract `ota-connectivity.openapi.yaml` `GET /ota-reconciliation-work-items` (dòng 152–171) và schema `ReconciliationWorkItem` (dòng 544–558, `additionalProperties: false`).

**Fact:**
- Query `category` có trong contract (kiểu string tự do, "e.g. INBOUND_EVENT") và trong `OtaReconciliationController` (đã có trong code; chưa kiểm tra đã deploy).
- `ReconciliationWorkItem` chỉ có workItemId, mappingId, status, correlationId, safeSummary. `OtaReconciliationWorkItemViewResponse` cũng không trả `category` → cột "loại" không có dữ liệu.
- Backend ghi các category: `INBOUND_EVENT`, `PAYOUT_DISPUTE`, `SHARED_VOUCHER_INCIDENT`, `INVENTORY_DELIVERY_FAILURE` và hai hằng `SOURCE_ORDER_CATEGORY`/`SOURCE_GAP_CATEGORY` trong `OtaSyncDeliveryService`; không có danh sách nào trong contract.

**Vì sao không làm đúng được nếu không tự quyết:** cột "loại" cần trường mà contract cấm (`additionalProperties: false`); danh sách giá trị lọc phải đoán từ code backend.

**Hiện trạng lúc dừng:** chưa sửa code FE nào cho T119.

**Cần quyết định / điều kiện tiếp tục:** owner 003.4 chốt: thêm `category` (enum hoặc string) vào `ReconciliationWorkItem` + response backend, và danh sách giá trị hợp lệ cho bộ lọc.

### Diễn biến — 2026-11-14

- Người yêu cầu chọn: làm phần không bị chặn. Đã truyền `category` qua `listReconciliationWorkItems` (trim, bỏ khi rỗng) và thêm bộ lọc trên `OtaReconciliationPage` (ô nhập tự do + nút INBOUND_EVENT + "All categories"); work item có `mappingId` null hiện "None (Global)". Test `otaReconciliationCategory.test.jsx` 3/3.
- Cột "loại" CHƯA làm; T119 vẫn `[ ]` trong tasks.md, chờ quyết định ở trên.

### Diễn biến gỡ chặn — 2026-11-15 (FE-DESIGN-0004)

- Nguồn thiết kế: `003.2-workshop-operations/tasks.md` T112 "Done 2026-11-14 (scope cut, owner decision)": đổi slot/location/ngày của phiên đã tạo nằm ngoài V1; backend `WorkshopSessionService.updateSession` từ chối thay đổi đó bằng 400 (không còn bỏ qua im lặng) và contract đã ghi điều này (commit 216d355e). Người chốt: Hồ Hoàng Quốc.
- Kết quả: câu hỏi (1) đã trả lời — V1 chỉ sửa sức chứa và giờ mở check-in, đúng phần `SessionEditForm` đã làm; frontend không cần thêm gì. Kiểm tra lại 2026-11-15: `sessionAdministration.test.jsx` xanh.
- Chưa chốt, không chặn V1: trường xác nhận cảnh báo FR-005b và `version` cho xung đột stale. Theo tasks, chúng thành task 003.2 riêng nếu cần; hiện giữ 409 `OPTIMISTIC_LOCK_CONFLICT` có sẵn. Mục này RESOLVED cho phạm vi V1.

### Diễn biến gỡ chặn — 2026-11-15 (FE-DESIGN-0005)

- Nguồn thiết kế: `ota-connectivity.openapi.yaml` `ReconciliationWorkItem.category` (enum INBOUND_EVENT, PAYOUT_DISPUTE, SHARED_VOUCHER_INCIDENT, INVENTORY_DELIVERY_FAILURE, SOURCE_VERSION_GAP, SOURCE_ORDER_UNCERTAIN, MANUAL_FULL_RESYNC) và `003.4-workshop-ota-connectivity/tasks.md` T119 `[x]` (commit 216d355e).
- Kết quả: `OtaReconciliationPage` đã hiển thị cột "Type" từ `item.category` cùng bộ lọc; `otaReconciliationCategory.test.jsx` kiểm tra cả hai (INBOUND_EVENT và PAYOUT_DISPUTE). Kiểm tra lại 2026-11-15: ota-connectivity xanh. Mục này RESOLVED.

### Cập nhật — 2026-11-15 (đóng mục, FE-DESIGN-0004 và FE-DESIGN-0005)

- FE-DESIGN-0004 (003.2 T112): tiêu đề đã RESOLVED. Đổi khung giờ/địa điểm/ngày của phiên đã tạo hoãn sau V1; `version` ĐÃ được thêm (chốt 2026-11-15, owner Hồ Hoàng Quốc): `SessionResponse.version` luôn trả về, `SessionRequest.version` tùy chọn khi PATCH, lệch version thì 409 `OPTIMISTIC_LOCK_CONFLICT`. Câu "chưa chốt `version`" ở trên không còn đúng.
- FE-DESIGN-0005 (003.4 T119): tiêu đề đã RESOLVED; enum `category` đã có trong contract. Hai điểm còn lại ở contract, không chặn: tham số query `category` là chuỗi tự do và `category` chưa `required` trên `ReconciliationWorkItem`. Ngoài ra 003.4 Clarifications 2026-11-15 (Q8, T128) sẽ bỏ `SHARED_VOUCHER_INCIDENT` khỏi enum; frontend cần bỏ nó khỏi bộ lọc khi T128 làm xong.

## FE-DESIGN-0006 — BLOCKED

- Ngày: 2026-11-17
- Reporter: Claude (Claude Code)
- Người yêu cầu: Hồ Hoàng Quốc (nhánh `quoc`)
- Feature/task: 003.2 T168 — trang ma trận gói × chi nhánh, đường từ chối quyền của Staff
- Lỗi/blocker: T168 yêu cầu "as Staff … the URL shows the denial page" (Figma frame 06), nhưng trong app thật Staff gõ `/operations/package-availability` bị đưa về trang chủ, không thấy trang từ chối.

**Nguồn design:** `003.2-workshop-operations/tasks.md` T168; Figma frame 06 (node ID trong `design/ui-flow.md`); spec FR-038 (Staff/Admin 403).

**Fact:** backend trả 403 `FORBIDDEN` và ghi audit `PACKAGE_AVAILABILITY_MATRIX_READ` `DENIED` cho tài khoản Staff (id 33), hai request, 2026-11-17 03:25. Người dùng quan sát: Staff bị đưa về trang chủ. Test đơn vị của trang 403 đạt vì chạy không có `SessionNavigationBoundary`.
**Suy luận (chưa kiểm mã):** `useHttp` gặp 403 gọi `reportAccessDenied`, `SessionNavigationBoundary` điều hướng về `/`, nên trang từ chối của ma trận không bao giờ hiển thị.
**Unknown:** có thông báo kèm khi về trang chủ hay không; hành vi của Admin chưa thử.

**Vì sao không làm đúng nếu không tự quyết:** hành vi 403 chung nằm ở `shared/` (`useHttp`, `SessionNavigationBoundary`, thuộc feature 001); sửa hoặc né nó trong trang ma trận là tự chọn policy điều hướng.

**Hiện trạng:** code T163–T166 xong. Đạt: toàn bộ vitest 857/857, `vite build`; Manager bật/tắt ô lưu và có audit, F5 giữ trạng thái và phiên; 375 px ra dạng thẻ giống Figma (theo người dùng). Chưa tick T167 (không có lint, Figma 1440/375 chỉ kiểm bằng mắt người dùng) và T168 (đường từ chối quyền lệch; Admin chưa thử).

**Cần quyết định:** Hồ Hoàng Quốc/Phan Sơn (chủ 001) chốt: chấp nhận Staff bị về trang chủ (sửa T168 và Figma frame 06), hoặc cho phép trang hiển thị 403 tại chỗ (đổi shared).

## FE-DESIGN-0007 — BLOCKED

- Ngày: 2026-11-24
- Reporter: Senior React Frontend Implementation Engineer (AGENT.md)
- Người yêu cầu: Không xác định trong session
- Feature/task: FE-001 (Phase 15: T112, T113, T115) & FE-007 (Phase 8: T040–T042) / Revision R-INV
- Lỗi/blocker: R-INV spec revision 2026-11-24 chưa được Tech Lead phê duyệt (approval.md: PENDING FINAL REVIEW); toàn bộ backend contracts (T106–T111, T038–T042) chưa được implement; chưa có UI wireframe/design chi tiết cho review collision, export JSON, countdown 48h, manual mail content copy và setup exchange.

**Nguồn design:**
- `ppswbs-spec/features/001-account-management/approval.md` (mục "R-INV — 2026-11-24 Admin internal-account repair": `PENDING FINAL REVIEW; not approved for implementation handoff`).
- `ppswbs-spec/features/007-notification-management/approval.md` (mục "R-INV invitation extension (2026-11-24)": `PENDING FINAL REVIEW; not approved for R-INV implementation handoff`).
- `ppswbs-spec/features/001-account-management/tasks.md` (Phase 15: ghi rõ `implementation handoff is blocked`, thứ tự bắt buộc: `T105 contract reconciliation → explicit Tech Lead approval gate → T106–T107 → T108 → T109–T111 → T112–T113 → T114–T116`).
- `ppswbs-spec/features/007-notification-management/tasks.md` (Phase 8: thứ tự bắt buộc: `FE-001 T105 and FE-007 T044 document reconciliation → explicit Tech Lead approval → FE-001 T109/T110 event/facade → T038–T039 → T040–T042 → T043/T045`).
- `ppswbs-spec/features/001-account-management/contracts/r-inv.openapi.yaml` & `ppswbs-spec/features/007-notification-management/contracts/notification-api.md`.

**Fact:**
1. Bản ghi duyệt tại cả 2 file `approval.md` đều xác định rõ trạng thái `PENDING FINAL REVIEW` do các thay đổi về thời hạn link (48 giờ từ khi tạo), chính sách thử lại mail (tối đa 2 lần) và trích xuất thủ công nội dung mail khi `FAILED` được chọn ở trạng thái "chờ chốt".
2. Backend Java (`ppswbs_backend`) hoàn toàn chưa triển khai các task tiền đề T106–T111 (FE-001) và T038–T043 (FE-007). Các endpoint `/api/v1/admin/accounts/email-collisions`, `/api/v1/admin/accounts/{accountId}/replacement-review`, `/api/v1/admin/accounts/{accountId}/retire-for-replacement`, `/api/v1/internal-account-invitations/exchanges`, `/api/v1/internal-account-invitations/completions`, và `/api/v1/admin/notification-requests/{requestId}/manual-invitation` chưa tồn tại trong code backend.
3. Về UI/UX: Chưa có màn hình/dialog chi tiết (Figma/wireframe) cho quy trình đối soát xung đột MEMBER collision review (hiển thị địa chỉ, giỏ hàng retail lưu trên server, lịch sử đơn hàng retail, lịch sử booking workshop), nút xuất JSON, giao diện hiển thị đếm ngược 48h từ `createdAt`, modal copy thủ công nội dung email khi SMTP thất bại 2 lần, và luồng `/invitations/setup` hỗ trợ liên kết password/Google.

**Suy luận:**
- Nếu tự ý implement frontend bằng mock/giả lập hoặc tự suy diễn UI layout và state machine:
  - Sẽ vi phạm nghiêm trọng Điều 1 và Điều 3 (§Gate trước code: F01, F02, F03, F04) của `AGENT.md`.
  - Tự sáng tác hành vi điều hướng, form validation và flow xác thực khi backend chưa chốt và chưa chạy thực tế.
  - Xóa trang `/admin/roles` và route `/admin/roles` (T113) khi quyết định R-INV chưa được Tech Lead phê duyệt chính thức sẽ làm hỏng chức năng hiện hữu đang hoạt động ổn định và được cover bởi test suite.

**Unknown:**
- Thời điểm Tech Lead Phan Sơn hoàn thành review và ký duyệt chính thức vào `approval.md`.
- Thiết kế giao diện chi tiết (Figma frames hoặc UI specification) cho các popup/drawer đối soát và luồng thiết lập tài khoản.

**Vì sao không thể implement nếu không tự quyết:**
- Mọi giải pháp code frontend lúc này bắt buộc phải tự bịa đặt API mock, tự thiết kế UI flow còn thiếu, hoặc tự quyết định bỏ role catalogue trái với approval record hiện hành. `AGENT.md` nghiêm cấm hành vi này.

**Hiện trạng lúc dừng:**
- Giữ nguyên toàn bộ mã nguồn hiện tại của frontend.
- Bộ test Vitest của frontend đạt `151/151 passed` (14 test files trong `accountmanagement` và `notification-management`).
- `npm run build` đạt kết quả sạch (`469 modules transformed`, không có lỗi cú pháp hay typecheck).

**Cần quyết định / Điều kiện tiếp tục:**
1. Tech Lead Phan Sơn hoàn tất review và cập nhật trạng thái `APPROVED FOR IMPLEMENTATION` trong cả 2 file `ppswbs-spec/features/001-account-management/approval.md` và `ppswbs-spec/features/007-notification-management/approval.md`.
2. Đội ngũ Backend triển khai xong và kiểm chứng các task nền tảng T106–T111 (FE-001) và T038–T042 (FE-007) trên môi trường tích hợp.
3. Cung cấp UI/Figma design hoặc đặc tả màn hình đã duyệt cho các flow: Collision Review Drawer/Dialog, JSON Export, Expiry Countdown Timer, Manual Mail Content Copy Modal, và Invitation Setup Page.

### Diễn biến gỡ chặn — 2026-11-25 (FE-DESIGN-0007 — RESOLVED CHO SCOPED FRONTEND)

- **Nguồn thiết kế & phê duyệt:**
  - Tech Lead Phan Sơn đã chính thức phê duyệt phạm vi triển khai có kiểm soát (scoped implementation) cho T106–T116 (FE-001) và T038–T043/T045 (FE-007) vào ngày 2026-11-25 trong cả hai file `ppswbs-spec/features/001-account-management/approval.md` và `ppswbs-spec/features/007-notification-management/approval.md`.
  - Hợp đồng REST API và DTO đã được chuẩn hóa tại `ppswbs-spec/features/001-account-management/contracts/r-inv.openapi.yaml` và `ppswbs-spec/features/007-notification-management/contracts/notification-api.md` §2.6.
  - Quy tắc giao diện, state machine và điều kiện chấp thuận được quy định tại `ppswbs-spec/features/001-account-management/design/revision-2026-11-24.md` (amended 2026-11-25 for demo preview), `spec.md` (FR-048–052) và `design/ui-flow.md`.
- **Phạm vi triển khai Frontend:**
  1. T112: Cập nhật form tạo tài khoản nội bộ (bắt buộc email chuẩn hóa cho STAFF/MANAGER, bỏ password/opt-out notification, xử lý collision lookup); triển khai modal đối soát xung đột MEMBER collision review với nhãn DEMO/UNAVAILABLE rõ ràng, nút xuất JSON và từ chối retirement (409); cập nhật trang chi tiết tài khoản hiển thị đếm ngược thời hạn 48h từ server, trạng thái gửi thư trung thực, và modal sao chép nội dung email thủ công khi FAILED sau 2 lần thử SMTP; triển khai trang `/invitations/setup` hỗ trợ xác thực mã mời qua custom token và liên kết password hoặc Google redirect.
  2. T113: Gỡ bỏ trang danh mục vai trò (`/admin/roles`), menu, route và client endpoint; cô lập navigation của ADMIN chỉ còn Quản lý tài khoản và self-service; giữ hướng dẫn ngắn về vai trò trong form tạo/sửa.
  3. FE-007 T042: Loại trừ `INTERNAL_ACCOUNT_INVITATION` khỏi các thao tác gửi lại/hủy/gửi tiếp thông thường trong NotificationRequestDetailDrawer, và tích hợp API `getManualInvitation` cho ADMIN.
  4. T115: Bổ sung bộ test Vitest toàn diện cho các thành phần UI, luồng xử lý và guard mới.
