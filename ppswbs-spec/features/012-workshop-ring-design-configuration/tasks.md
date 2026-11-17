# Feature-012 Tasks: Workshop Ring Design Configuration

Date created: 2026-11-08  
Date updated: 2026-11-14  
Author: Ngô Tiến Đạt  
Last updated by: Ngô Tiến Đạt  

## Phase 0 — Baseline and Decisions

- [x] Read and apply `ppswbs-spec/AGENT.md`, constitution and design kit.
- [x] Confirm Feature-012 boundary: Guest and Member workshop ring configuration; advisory price & difficulty scoring only; no direct booking/payment side effects.
- [x] Confirm separation of duties: Feature-012 owns `ring_designs` and `guest_design_sessions`; Feature-003 consumes validated design snapshots via `WorkshopRingDesignFacade`.
- [x] Record approval in `approval.md` (Tech Lead approval on 2026-11-09).

## Phase 1 — Specification and Data Model

- [x] Finalize `spec.md` with 5 core flows (Entry, Live 2D SVG preview, Save & Evaluate, Branch Compatibility Recovery, Session Expiry & Member History).
- [x] Define `data-model.md` with row-per-version `ring_designs` schema, `guest_design_sessions` schema, SHA-256 session digests, and JSON component snapshots.
- [x] Verify Flyway migration history: allocate and implement `V110__workshop_ring_design_configuration.sql` after `V109`.
- [x] Define 20-minute inactivity TTL for Guest sessions with `@Scheduled` purge job.
- [x] Define version locking via `If-Match-Version` and command idempotency via `Idempotency-Key`.

## Phase 2 — API Contract and Facade Architecture

- [x] Author OpenAPI contract `contracts/workshop-ring-design.openapi.yaml` (0.2.0-review).
- [x] Implement backend Spring Modulith module `features.ringdesignconfiguration`.
- [x] Implement DTO layer: `DesignOptionsResponseDto`, `CreateDesignRequest`, `SaveDesignVersionRequest`, `DesignResponseDto`, `AttachDesignRequestDto`, `BookingDesignSnapshotDto`.
- [x] Implement DAO layer: `RingDesignRepository`, `GuestDesignSessionRepository`, `RingCatalogueModelRepository`, `RingDesignIdempotencyRecordRepository`.
- [x] Implement service layer: `WorkshopRingDesignApplicationService`, `GuestDesignSessionService`, `RingDesignIdempotencyService`.
- [x] Implement rule engine & port: `DesignOptionRulesPort`, `ManagerDesignRuleAdapter`.
- [x] Implement controller layer: `WorkshopRingDesignController`, `WorkshopDesignOptionsController`, `WorkshopRingDesignExceptionHandler` with `@Order(HIGHEST_PRECEDENCE)`.
- [x] Implement inter-module integration facade: `WorkshopRingDesignFacade` and `WorkshopRingDesignFacadeImpl`.

## Phase 3 — Frontend Delivery

- [x] T001 Implement API adapter `workshopRingDesignApi.js` with headers `X-Guest-Design-Session`, `If-Match-Version`, and `Idempotency-Key`.
- [x] T002 Implement command hook `useWorkshopDesignCommand.js` with automatic session storage.
- [x] T003 Implement draft state hook `useWorkshopDesignDraft.js` with reactive 2D SVG model derivation.
- [x] T004 Implement bilingual messages dictionary `messages.js` (VI/EN).
- [x] T005 Implement `RingDesignEntryPage.jsx` (Model catalogue vs Blank canvas entry).
- [x] T006 Implement `RingModelSelectionPage.jsx` (Catalogue model selection).
- [x] T007 Implement `RingConfigurationPage.jsx` (5 parameter sections, live 2D SVG preview, reactive price summary, advisory disclaimer).
- [x] T008 Implement `RingPreview.jsx` (Dynamic SVG rendering for material textures, bands, gemstones, milgrain, engravings).
- [x] T009 Implement `DesignEvaluationPage.jsx` (Technical evaluation results, difficulty score, version history, archive action, booking transition).
- [x] T010 Implement `BranchCompatibilityPage.jsx` (Branch capability limitation recovery).
- [x] T011 Implement `MemberDesignHistoryPage.jsx` (Member design history and ownership list).
- [x] T012 Register routes in `routes.jsx` and mount in application router.
- [x] T013 Connect Feature-012 evaluation handoff to `WorkshopBookingPage.jsx` via `/workshops/:packageId/book?designId=...`.

## Phase 4 — Verification Evidence

- [x] Backend integration test suite: 25/25 tests passed (100%) (`GuestDesignSessionServiceTest`, `ManagerDesignRuleAdapterTest`, `WorkshopRingDesignApplicationServiceTest`, `WorkshopRingDesignControllerMockMvcTest`).
- [x] Frontend Vitest suite: 32/32 tests passed (100%) (`workshopRingDesignApi.test.js`, `designDomain.test.js`, `screens.test.jsx`).
- [x] Spring Modulith Architecture verification: 5/5 tests passed (100%).
- [x] E2E-012-001 (Model or Blank Entry): PASS with JPEG screenshot evidence.
- [x] E2E-012-002 (Live Ring Configuration): PASS with JPEG screenshot evidence.
- [x] E2E-012-003 (Save, Evaluate & Versioning): PASS with JPEG screenshot evidence (Re-tested and verified).
- [x] E2E-012-004 (Branch Compatibility Recovery): PASS with JPEG screenshot evidence.
- [x] E2E-012-005 (Guest Expiry 20-min branch): PASS with JPEG screenshot evidence (HTTP 401 `DESIGN_SESSION_EXPIRED`).
- [x] BUG-E2E-0004 resolved and closed in `ppswbs-testing/BUG-LOG.md`.

## Phase 6 — Materials, Sizes 6-29, Catalogue Models & Dual-View Sliders

- [x] T014 Implement migration `V120__ring_design_copper_steel_and_models.sql` (Deactivate Gold, add Copper & Stainless Steel 316L, expand models to 12).
- [x] T015 Expand backend size range to sizes 6-29 in `ManagerDesignRuleAdapter.java`.
- [x] T016 Add perimeter calculation in mm ($P = \text{size} + 40\text{mm}$) and update UI size selector pills.
- [x] T017 Implement Dual View SVG (Top View and Side View) in `RingPreview.jsx`.
- [x] T018 Add thickness slider (1.0 - 3.0mm) and band width slider (1.5 - 6.0mm) with synchronized preview update.
- [x] T019 Update Navbar with direct link for workshop ring design discoverability.

## Phase 7 — Multi-Gemstone Stepper & Constrained Circular Drag-and-Drop

- [x] T020 Implement stepper controls `[-] {count} [+]` on gemstone cards with unit price & subtotal.
- [x] T021 Support multiple gemstone instances per type up to `maxQuantity`.
- [x] T022 Implement drag-and-drop on Top View SVG preview strictly constrained to ring circle band ($R = 85\text{px}$) via polar projection $\theta = \text{atan2}(y - 135, x - 150)$.
- [x] T023 Synchronize prong settings and gemstone silhouettes on Side View SVG.
- [x] T024 Update design evaluation summary to format `${quantity}x ${label}`.
- [x] T025 Verify full Vitest suite (88/88 tests passed) and browser E2E test verification.

## Phase 8 — Release Gates

- [ ] Giai đoạn 1: Quản trị danh mục động cho Manager (FE-09) — Xây dựng màn hình quản lý danh mục nhẫn cho Manager để cập nhật động các mẫu đá quý, chất liệu và phụ kiện thay vì sử dụng seed ban đầu.
- [ ] Giai đoạn 2: Phê duyệt chính thức Revision Figma — Cập nhật mã revision Figma chính thức khi Tech Lead và UI Designer hoàn thiện rà soát giao diện visual.

