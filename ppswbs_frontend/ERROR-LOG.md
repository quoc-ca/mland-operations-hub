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
