# Feature 003 UI acceptance mapping: Figma ↔ React

**Ngày xác minh:** 2026-11-04  
**Figma file:** [mland-operation-hub](https://www.figma.com/design/m5Cyk5rit6lKaX3TPprSow/mland-operation-hub)  
**Figma file key:** `m5Cyk5rit6lKaX3TPprSow`

Tài liệu này là inventory chuẩn cho UI Feature 003. `node-id` là ID đã đọc trực tiếp từ Figma metadata; route/component là source of truth trong `ppswbs_frontend/src/**/routes.jsx`. `screenRegistry.js` chỉ là inventory legacy và không thay thế mapping này.

## Public, Guest và Member

| Figma frame | Route | React component | Actor/state | Acceptance |
|---|---|---|---|---|
| `199:58` PUBLIC · Workshop detail | `/workshops/:packageId` | `WorkshopDetailPage` | Public | Xem chi tiết package và chuyển sang booking |
| `199:2` GUEST · Workshop booking | `/workshops/:packageId/book` | `WorkshopBookingPage` | Guest | Nhập contact, chọn session, preview/accept quote và giữ chỗ |
| `200:2` MEMBER · Workshop booking | `/workshops/:packageId/book` | `WorkshopBookingPage` | Member-authenticated variant | Dùng identity Member và BenefitSelector workshop; không chọn actor thủ công |
| `199:22` GUEST · Booking lookup | `/workshop-booking/lookup` | `WorkshopLookupPage` | Guest | Tra booking bằng booking code + contact proof |
| `200:40` MEMBER · My bookings | `/workshop-booking/access` | `WorkshopAccessPage` | Member | Danh sách/chi tiết booking của Member sau authentication |
| `199:40` GUEST · Payment result | `/workshop-booking/:bookingCode/payment-return` | `WorkshopPaymentReturnPage` | Guest/Member | Hiển thị kết quả redirect; trạng thái cuối chỉ lấy từ backend/IPN |
| `200:21` MEMBER · Booking confirmation | `/workshop-booking/:bookingCode/payment-return` | `WorkshopPaymentReturnPage` | Member | Xác nhận booking sau payment evidence đã verified |
| `203:2` PUBLIC · Booking access | `/workshop-booking/access` | `WorkshopAccessPage` | Public access handoff | Trang chuyển tiếp sang lookup hoặc authenticated access |
| `297:4518` GUEST · Payment · Expired / timeout | `/workshop-booking/:bookingCode/payment-return` | `WorkshopPaymentReturnPage` | Expired/timeout state | Link/hold hết hạn, cho phép recovery theo contract |
| `297:4556` GUEST · Payment · Provider error | `/workshop-booking/:bookingCode/payment-return` | `WorkshopPaymentReturnPage` | Provider error | Không tự xác nhận booking; hiển thị retry/support state |
| `297:601` MEMBER · Booking · Expired hold | `/workshops/:packageId/book` | `WorkshopBookingPage` | Member expired hold | Không giữ chỗ quá hạn, yêu cầu tạo lại quote/hold |
| `297:639` MEMBER · Booking · Payment recovery | `/workshop-booking/:bookingCode/payment-return` | `WorkshopPaymentReturnPage` | Member recovery | Tạo payment attempt mới theo idempotency contract |

## Staff / Workshop Operations

| Figma frame | Route | React component | Actor/state | Acceptance |
|---|---|---|---|---|
| `188:2` STAFF · Session operations · Queue | `/operations/sessions` | `OperationsDashboardPage` | Staff/Manager | Queue phiên, filter và work items |
| `203:22` STAFF · Session detail | `/operations/sessions/:sessionId` | `OperationsSessionPage` | Staff/Manager | Chi tiết phiên và thao tác vận hành |
| `188:52` STAFF · Check-in | `/operations/sessions/:sessionId` | `OperationsSessionPage` | Staff scoped | Check-in, attendance và trạng thái phiên |
| `188:102` STAFF · Workshop add-on | `/operations/sessions/:sessionId` | `OperationsSessionPage` | Staff scoped | Ghi nhận add-on tại workshop và customer consent |
| `297:1811` STAFF · Check-in · Error / offline | `/operations/sessions/:sessionId` | `OperationsSessionPage` | Offline/error | Fail-closed, không ghi partial attendance |
| `297:1856` STAFF · Workshop add-on · Consent pending | `/operations/sessions/:sessionId` | `OperationsSessionPage` | Consent pending | Chưa tạo bill nếu consent chưa hoàn tất |
| `72:776` STAFF · Dashboard | `/operations/sessions` | `OperationsDashboardPage` | Staff | Dashboard tổng quan (frame legacy-compatible) |

## Manager / Workshop Operations và Settlement

| Figma frame | Route | React component | Actor/state | Acceptance |
|---|---|---|---|---|
| `186:2` MANAGER · Location management | `/operations/locations` | `OperationsAdministrationPage` | Manager global | CRUD/location activation theo contract |
| `186:52` MANAGER · Slot management | `/operations/slots` | `OperationsAdministrationPage` | Manager global | Cấu hình slot/capacity và idempotent write |
| `186:102` MANAGER · Session administration | `/operations/admin` | `OperationsAdministrationPage` | Manager global | Tạo/sửa/đóng phiên, không sửa attendance đã finalized |
| `186:152` MANAGER · Package management | `/operations/packages` hoặc `/workshop-packages` | `WorkshopPackageManagementPage` | Manager global | DRAFT/PUBLISHED/RETIRED và policy package |
| `186:202` MANAGER · Add-on catalogue | `/operations/package-add-ons` | `PackageAddOnCataloguePage` | Manager global | Package add-on catalogue; tách khỏi shop add-on tại quầy |
| `186:252` MANAGER · Settlement cases | `/settlement/cases` | `SettlementCasesPage` | Manager global | Case list/detail và trạng thái xử lý |
| `203:112` MANAGER · Settlement case detail | `/settlement/cases` | `SettlementCasesPage` | Manager global | Case detail, evidence và quyết định |
| `186:302` MANAGER · Refund policy | `/settlement` | `SettlementWorkspacePage` | Manager global | Policy/eligibility; Mland booking cancellation không tự refund |
| `297:4203` MANAGER · Settlement · Case list | `/settlement/cases` | `SettlementCasesPage` | Case list | Danh sách case và filter |
| `297:4248` MANAGER · Settlement · Pending provider | `/settlement/cases` | `SettlementCasesPage` | `PENDING_PROVIDER` | Chờ provider evidence/polling, không tự đánh dấu hoàn tất |
| `297:4293` MANAGER · Settlement · Required session change | `/settlement` | `SettlementWorkspacePage` | Session change | Validate cặp session nguyên tử, không partial mutation |
| `297:4338` MANAGER · Settlement · Final payment recovery | `/settlement` | `SettlementWorkspacePage` | Recovery | Theo dõi final payment/recovery state |
| `186:352` MANAGER · OTA finance | `/ota-connectivity/finance` | `OtaFinanceReconciliationPage` | Manager global | Financial evidence, payout reconciliation, dispute |
| `186:402` MANAGER · OTA reconciliation | `/ota-connectivity/reconciliation` | `OtaReconciliationPage` | Manager global | Work item reconciliation và resolution |
| `203:157` MANAGER · OTA channels | `/ota-connectivity/channels` | `OtaChannelConfigurationPage` | Manager/Admin | Mapping, readiness, sales-close/booking-enable |
| `297:4383` MANAGER · OTA finance · Evidence / dispute | `/ota-connectivity/finance` | `OtaFinanceReconciliationPage` | Evidence/dispute | Redact PII/credentials và mở dispute khi lệch |
| `297:4428` MANAGER · OTA add-on · Quote / acceptance | `/ota-connectivity/add-ons` | `OtaAddonPage` | Staff scoped | Quote và customer consent tại quầy |
| `297:4473` MANAGER · OTA add-on · Direct bill | `/ota-connectivity/add-ons` | `OtaAddonPage` | Staff scoped | Direct bill Mland sau consent; không qua checkout OTA |

## Admin

| Figma frame | Route | React component | Actor/state | Acceptance |
|---|---|---|---|---|
| `192:2` ADMIN · OTA provider configuration | `/ota-connectivity/channels` | `OtaChannelConfigurationPage` | Admin technical | Credential/provider configuration; không expose secret |

## Quy tắc đồng bộ acceptance

1. Một frame acceptance phải có đúng một route/component mapping ở đây; responsive mobile là variant của cùng route, không tạo route giả.
2. Khi đổi Figma, cập nhật đồng thời node ID, route, component và trạng thái acceptance trong tài liệu này trước khi sửa code.
3. Khi thêm route React Feature 003, phải thêm frame Figma tương ứng hoặc ghi rõ blocker; không dùng fixture/frontend giả để che thiếu contract backend.
4. Các frame `297:*` là state variants của flow chính, không phải module độc lập.
5. Kiểm tra route thực tế bằng các file `ppswbs_frontend/src/*/routes.jsx`; kiểm tra API/role theo backend và contract tương ứng trước khi ghi “accepted”.

## UI acceptance sync audit (2026-11-04)

This section records the implementation check against the Figma state frames. A frame is not complete merely because a route exists; the component must render the state and invoke the contractually correct action.

| Flow | Actual implementation | Result | Gap / evidence |
|---|---|---|---|
| Guest payment result | `WorkshopPaymentReturnPage` reads `vnp_ResponseCode`, treats browser return as display-only, and requires backend lookup/IPN verification before showing `CONFIRMED`, `PENDING_PAYMENT`, `EXPIRED`, or `CANCELLED`. `classifyGuestPaymentOutcome` now separates `EXPIRED`, `NETWORK_TIMEOUT`, provider `PROVIDER_ERROR` with the response code, provider success pending verification, lookup failure, and no provider response. | PASS for behavior | Focused evidence: `src/workshops/paymentReturnOutcome.test.js`; browser return never confirms a booking without verified backend/IPN evidence. Figma `297:4518` and `297:4556` are covered by the distinct outcome copy; visual capture remains a QA follow-up. |
| Member expired hold / payment recovery | `WorkshopLookupPage` derives `MEMBER` from authentication, uses bearer ownership, enforces the ten-minute hold, and classifies `HOLD_EXPIRED`/`BOOKING_EXPIRED` as expired without automatically creating a second transaction. `paymentRecoveryState.js` covers pending, unknown, expired, verified-paid and verified-unpaid states. | PASS for behavior; PARTIAL for visual separation | Covered by `workshops-payment-and-recovery.test.js`; Figma `297:601` and `297:639` remain state variants of the lookup/payment-return components rather than separate routes. |
| Staff check-in | `OperationsSessionPage` provides ticket-code and QR-token check-in, booking lookup, session transitions, attendance finalization, and typed error rendering. | PASS for core behavior | Figma `188:52` is covered. `297:1811` uses the shared `ErrorNotice` fail-closed path, but there is no dedicated offline queue/retry UI. |
| Staff workshop add-on | `OperationsSessionPage` now embeds `StaffWorkshopAddonPanel`, which records a catalogue add-on against a booking only after counter consent. The browser sends only `catalogueAddOnReference`; backend derives amount/currency and enforces Staff/Manager scope. | PASS for core behavior; evidence pending | Covers Figma `188:102` / `297:1856` at `/operations/sessions/:sessionId`. Uses `POST /api/v1/workshop-bookings/{bookingCode}/final-bill/charges` with a generated `Idempotency-Key`. Since 2026-11-05 a `FINALIZED` folio rejects the add-on (no separate direct shop bill); post-finalize corrections follow the Manager-only T040 flow. |
| Manager settlement | `SettlementCasesPage` supplies case list/detail, status filters, pending-provider and required-session-change views. `SettlementWorkspacePage` supplies final-payment recovery and policy/eligibility actions. | PASS for implemented contract paths | Need state-level UI evidence for `PENDING_PROVIDER`, required session change and final-payment recovery; backend/API tests alone do not prove visual acceptance. |
| Manager OTA finance | `OtaFinanceReconciliationPage` reads redacted financial evidence and records payout/reconciliation evidence with typed errors and dispute responses. | PASS for core behavior; evidence pending | Covers Figma `186:352` / `297:4383`; capture matched, mismatch/dispute and forbidden-state UI evidence. |
| Manager OTA add-on | `OtaAddonPage` implements quote, consent and direct-bill for OTA channel bookings; free laser engraving creates no bill and paid options do. | PASS for core behavior; evidence pending | `otaFinanceAddonPages.test.jsx` covers paid, free and validation paths. The direct-booking Staff add-on UI is the OPEN-folio `/final-bill/charges` flow above; it is separate from this OTA bill. |

### Verified test evidence

On 2026-11-04 the frontend targeted suite completed successfully:

`npm test -- --run src/workshops/workshops-payment-and-recovery.test.js src/ota-connectivity/test/otaFinanceAddonPages.test.jsx src/ota-connectivity/test/otaConnectivityPages.test.jsx`

Result: **3 test files passed, 33 tests passed, 0 failures**. These tests validate payment/recovery and Manager OTA finance/add-on behavior.

Staff add-on evidence:

`npm test -- --run src/workshop-operations/test/staffAddonPages.test.jsx src/workshop-operations/test/packageApi.test.js`

Result: **2 test files passed, 11 tests passed, 0 failures**. This covers consent gating, idempotent write header/body, successful recording, and fail-closed `409 FINAL_BILL_NOT_OPEN` handling for the Staff panel.

Guest payment outcome evidence:

`npm test -- --run src/workshops/paymentReturnOutcome.test.js src/workshops/workshops-payment-and-recovery.test.js src/shared/payment/paymentRecoveryState.test.js`

Result: **3 test files passed, 29 tests passed, 0 failures**. This covers provider response-code errors, network/request timeout, expired holds, and display-only provider success pending verified IPN.

Settlement rendered evidence:

`npm test -- --run src/workshop-settlement/test/settlementUiEvidence.test.jsx`

Result: **1 test file passed, 4 tests passed, 0 failures**. This covers empty case list,
`PENDING_PROVIDER`, empty refund-policy history, and backend error without a fabricated list.

OTA finance/add-on rendered evidence:

`npm test -- --run src/ota-connectivity/test/otaFinanceAddonPages.test.jsx`

Result: **1 test file passed, 6 tests passed, 0 failures**. This covers empty/error finance
states plus paid, free and invalid add-on states.

Screenshot verification exception: the configured browser access host was unavailable during
this run (`browsers: []` from the CUA state), so these entries record rendered test evidence
and do not claim screenshots.

## T040 Staff direct Mland add-on screen (2026-11-04)

> **Superseded — 2026-11-05.** Under the
> [open-folio policy](direct-booking-open-folio-policy.md) a `FINALIZED` direct
> folio accepts no add-on, and `SHOP_ADDON` is used only for an OTA booking's
> shop add-on bill (D7–D10, D12). This frame and `StaffWorkshopAddonPanel`'s
> post-finalize bill/payment flow must be realigned under 003.3 T040 (Manager-only
> overcharge correction) and T087; the row below is kept as a historical record.

| Figma frame | Route | React component | Contract |
|---|---|---|---|
| `327:1083` STAFF · Workshop add-on · Direct Mland bill | `/operations/sessions/:sessionId` | `StaffWorkshopAddonPanel` | Create a separate bill after the final bill is finalized, then start a separate SHOP_ADDON payment attempt; original final bill remains immutable. |

The screen submits only booking code, catalogue add-on reference, and customer consent reference. Amount/currency are resolved by the backend. A browser return is informational; the UI waits for signed IPN/QueryDR evidence before treating the add-on as paid.
