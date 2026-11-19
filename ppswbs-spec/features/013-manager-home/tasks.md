# Tasks: Manager Home

**Date created**: 2026-11-18
**Date updated**: 2026-11-18
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Input**: Design documents from `ppswbs-spec/features/013-manager-home/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/reads.md](contracts/reads.md), [quickstart.md](quickstart.md)

**Tests**: Included. The plan's verification plan requires component tests for every section and an integration test for every added read, and the frontend persona requires `npm test` and `npm run build` to pass before hand-over.

**Organization**: Grouped by user story. **Status: DRAFT; no design approval is recorded in an `approval.md`, so no code task below may start until T001 has produced that approval.**

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1 to US5, as in the spec
- FE = `ppswbs_frontend/src/features/managerhome` (new slice). BE = `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features`.

## Phase 1: Setup

- [x] T001 Design gate (no code before it): run `$feature-design` for 013 to add `design/architecture.md` (status `proposed`: the new slice `ppswbs_frontend/src/features/managerhome`, its infrastructure port, and the owner reads R-1 to R-6 with their modules) and `design/workflows.md` (open the page, refresh, a card failing, a session lost, a non-Manager opening the address); get the owners of 003.2, 003.3, 003.4, 011 and 010 to agree to the reads and definitions that concern them ([contracts/reads.md](contracts/reads.md)); then ask the Tech Lead for approval and record revision, scope, date and name in `approval.md`. Traces: persona gate F01, D18. **Done 2026-11-18 for wave 1 only**: drafts in `design/`; Tech Lead approval (Hồ Hoàng Quốc, 2026-11-18, wave 1) recorded in `approval.md`; owner agreement to R-1 to R-6 not obtained, so T014–T020, T023, T024, T030, T031 stay blocked.
- [x] T002 Create the slice folders `ui/`, `application/`, `infrastructure/`, `tests/` and the empty route file `routes.jsx` under FE, following plan "Source code". No other folder is created.

---

## Phase 2: Foundational (blocks every story)

- [x] T003 [P] Add `application/vietnamTime.js` in FE: functions that return today's date as `yyyy-MM-dd` and the instant 24 hours ago, both computed in Vietnam time (UTC+7) regardless of the browser's time zone (spec FR-007, FR-010; research D7).
- [x] T004 Add `infrastructure/homeReads.js` in FE with one function per **existing** read in [contracts/reads.md](contracts/reads.md) section A (late payments, change requests, design review, sessions of a day with optional `locationId`, executive report with `periodType=MONTH` and no branch filter, Operations audit events with `outcome=DENIED`, Operations audit events with `action=WORKSHOP_PACKAGE_AVAILABILITY_CHANGED`, workshop locations for the branch picker). Each function takes the shared `http` client, never calls `fetch` directly, and maps the response to the view model in [data-model.md](data-model.md) so no raw response shape reaches the UI (constitution, frontend boundary). Audit rows keep only time, actor, action; the free-text reason and metadata are dropped here.
- [x] T005 Add `application/useHomeCards.js` in FE: a hook that runs one independent load per card and returns, for each, `state` (`loading`, `ready`, `unavailable`), the value and its own `reload`. A failed card never changes another card; a card whose read is not registered is not returned at all (spec FR-006, Clarification 1). It records `lastUpdatedAt` in Vietnam time, reloads only on open and on an explicit refresh (never on a timer; FR-013), and drops all values on sign-out or session loss through the shared private-state cleanup (FR-018).
- [x] T006 Add `ui/messages.js` in FE with every label of the page in Vietnamese and English, following the existing language choice (spec FR-016). Wording comes from the spec; no new copy is invented beyond the section titles and the states in the spec.
- [x] T007 Add `ui/ManagerHomePage.jsx` and `routes.jsx` in FE: the page shell with the heading, a refresh button, the last-updated time in Vietnam time, and the section order of spec FR-002 (waiting work, today, revenue, recent events, shortcuts). Access: only an active Manager sees any figure; Staff and other roles are sent to their own home (reuse `roleHomePath`); no session shows the existing sign-in prompt that returns to `/manager/home` (FR-001, FR-018). Sections render their own components as the stories land.
- [x] T008 Register the new route at the composition root: in `ppswbs_frontend/src/app/router.jsx` import the slice's `managerHomeRoutes` next to `operatorHomeRoutes` and spread it into the route list, and in the same change remove the `/manager/home` entry from `ppswbs_frontend/src/features/operatorhome/routes.jsx`, so exactly one route serves `/manager/home` and no feature imports another feature (constitution, frontend boundary). `/staff/home` and `/home` are unchanged. The old Manager code inside `OperatorHomePage` is no longer routed and is deleted in T034.
- [x] T009 [P] Tests in `FE/tests/`: `vietnamTime.test.js` (day boundary at 00:00 UTC+7, 24-hour window), `homeReads.test.js` (each mapper; audit rows never carry a reason or metadata), `useHomeCards.test.jsx` (cards fail independently, an unregistered card is absent, no timer refresh, the last-updated time shown in Vietnam time and renewed only on open or explicit refresh, values dropped on session loss), and `readOnly.test.jsx` (while the page opens, refreshes and every card is used, no request other than GET is sent; spec FR-012).

**Checkpoint**: the page opens for a Manager, shows empty sections, and refuses other roles.

---

## Phase 3: User Story 1 - See what is waiting for me (Priority: P1) — MVP

**Goal**: A card per queue with the number waiting, opening the owning list.

**Independent Test**: Manager with items in two queues and none in a third; counts match the lists; one failing source affects only its card.

- [x] T010 [US1] Add `application/queues.js` in FE: the registry of the six queues (late payments, refund queue, change requests, cash-close differences, OTA reconciliation, design review), each with its owning list `target` and its count read, which is `null` until the owner's read exists. Wave 1 registers only late payments (`/operations/late-payments`, `PENDING_MANAGER`), change requests (`/settlement/change-requests`, `PENDING`) and design review (`/design-review-requests`, `NEED_REVIEW`); their count is the number of items the owner's default response returns (research D1).
- [x] T011 [US1] Add `ui/WaitingWorkSection.jsx` in FE: one card per registered queue with a text label, the count, and a link to the owning list; a card with count 0 is dimmed and grouped under "no outstanding work"; when every shown card is 0 the section shows the single state worded "No outstanding work in the queues shown here" (Vietnamese: "Không có việc tồn đọng trong các hàng đợi đang hiển thị") that claims only the shown queues (spec FR-003 to FR-006, edge cases); a failed card shows its own unavailable state with its own retry and never 0; state is never conveyed by colour alone and cards are keyboard-operable (FR-017).
- [x] T012 [US1] Tests `FE/tests/waitingWork.test.jsx`: counts of the three wave-1 queues; opening a card goes to its list; a zero card is dimmed and grouped; all-zero shows the single state limited to shown queues; one failing read leaves the others and shows retry, not 0; a queue with no registered read renders no card; a count refused with 403 shows no number (FR-014); one failing read never blanks the page (SC-003).
- [ ] T013 [US1] Cross-check (manual, recorded in the task note): for each wave-1 card, open the card's list and confirm it holds exactly the count shown (spec FR-004, SC-002), against the development database.

### Owner reads for US1 (cross-feature; each needs its owner's approval before work starts)

- [x] T014 [US1] **Owner 003.3**: add the refund-queue waiting count, `{ "waiting": <integer ≥ 0> }`, Manager only, read-only, writing no audit row, in `BE/workshopsettlement/` with a JUnit test and a MySQL integration test proving the count equals the size of the list the card link opens. The owner decides which `PENDING_*` statuses mean "waiting" (proposal in [contracts/reads.md](contracts/reads.md) R-2). Blocks T017.
- [x] T015 [US1] **Owner 003.3**: add the count of cash closes in state `PENDING_MANAGER_CONFIRMATION`, same response shape and rules as T014 (R-3), with the same tests. Blocks T018.
- [x] T016 [US1] **Owner 003.4**: add the OTA reconciliation items-needing-attention count, same response shape and rules (R-4); the owner decides among `OPEN`, `IN_PROGRESS`, `ESCALATED` (proposal: `OPEN` and `ESCALATED`), with the same tests, in `BE/otaconnectivity/`. Blocks T019.
- [x] T017 [US1] Register the refund-queue card in `FE/application/queues.js`, link filtered to the same set as T014 (needs T014).
- [x] T018 [US1] Register the cash-close card, link to the cash-close screen's confirmation view (needs T015).
- [x] T019 [US1] Register the OTA reconciliation card, link filtered to the same set as T016 (needs T016).
- [ ] T020 [US1] DROPPED by owner decision (Hồ Hoàng Quốc, 2026-11-18): feature 011 has no "awaiting moderation" concept, so no review-moderation card and no R-6 read are built; the home does not wait for the 011 owner.

**Checkpoint**: wave 1 shows three cards; each owner read adds one card without changing the others.

---

## Phase 4: User Story 2 - Check today's operations (Priority: P2)

**Goal**: Sessions today and check-in progress, for all branches or one.

**Independent Test**: Sessions today at two branches, one cancelled; the numbers match the session board.

- [x] T021 [US2] Add `ui/TodayStrip.jsx` in FE: sessions scheduled today (Vietnam day), cancelled sessions included and "of which N cancelled" shown separately, from the existing session list (spec FR-007); a branch picker defaulting to all branches and offering only branches the Manager may see, kept for the visit only and affecting only this strip (FR-008); an empty state "no sessions today"; revenue and utilization are NOT shown here.
- [x] T022 [US2] Tests `FE/tests/todayStrip.test.jsx`: six sessions with one cancelled show 6 and "of which 1 cancelled"; picking a branch narrows the strip and leaves the queue cards unchanged; the chosen branch is kept after a refresh within the visit; no sessions shows the empty state; the strip shows no revenue or utilization.
- [x] T023 [US2] **Owner 003.2**: add `GET /api/v1/workshop-day-summaries?date=<yyyy-MM-dd>[&locationId=]` returning `{ "date", "sessions", "cancelledSessions", "checkedIn", "expected" }` where `sessions` includes cancelled sessions and `checkedIn` and `expected` cover non-cancelled sessions only, where "expected" is the participants holding a valid ticket for those sessions, not the capacity (spec FR-007, R-1), Manager only, in `BE/workshopoperations/`, with a JUnit test and a MySQL integration test comparing the numbers with the session list and the rosters. Blocks T024.
- [x] T024 [US2] Show check-in progress ("checked in of expected", over non-cancelled sessions) in `FE/ui/TodayStrip.jsx` from the T023 read; until T023 is merged the strip shows only the session and cancelled counts (needs T023); extend `todayStrip.test.jsx` with a cancelled session excluded from the progress.

**Checkpoint**: the strip is correct on its own; check-in progress appears with T023.

---

## Phase 5: User Story 3 - See this month's revenue (Priority: P2)

**Goal**: One figure equal to the dashboard's, opening the dashboard.

**Independent Test**: The home figure equals the dashboard's for the current month and all branches.

- [x] T025 [US3] Add `ui/RevenueCard.jsx` in FE: the current month's `totals.totalRevenue` in VND from the same executive-report read the dashboard uses (research D5); `PARTIAL`, `UNAVAILABLE`, an empty total and HTTP 503 are shown as the card's own notice and never as a guessed value; when `developmentDemo` is true the card carries the same demo marker the dashboard shows; activating the card opens `/manager/dashboard` (spec FR-009).
- [x] T026 [US3] Tests `FE/tests/revenueCard.test.jsx`: a complete figure equals the dashboard's value from the same mocked response; partial and unavailable and 503 show a notice and no number; the demo marker appears when set; activating opens the dashboard; a failure does not affect other sections.
- [ ] T027 [US3] Cross-check (manual, recorded in the task note): compare the home figure with `/manager/dashboard` for the current month in the development environment (spec SC-004).

---

## Phase 6: User Story 4 - Notice recent events (Priority: P3)

**Goal**: A few recent refused attempts and package availability changes, with a link to the audit log.

**Independent Test**: Produce a refused attempt and a package availability change; both appear newest first.

- [x] T028 [US4] Add `ui/RecentEvents.jsx` in FE: up to five events from the last 24 hours, newest first, each with time, who and what (no personal data), merging Operations refused attempts and package availability changes, and a link to `/operations/audit` (spec FR-010). The link is rendered only when the Operations audit read succeeded, so it never points to a screen that is not deployed in the environment (spec edge cases). The Settlement half is read only when T030 exists; until then the section says the Settlement source is not yet included (a source that has not been delivered, not a failed read) and still shows the Operations events. Once T030 exists, a Settlement read that fails shows the "part of the data is missing" note (spec FR-010). When no source can be read the section is unavailable; with nothing recent it says so.
- [x] T029 [US4] Tests `FE/tests/recentEvents.test.jsx`: two sources merged newest first and capped at five; only events within 24 hours; a missing Settlement source shows the note and still shows Operations events; nothing recent shows the empty message; neither source readable shows unavailable without affecting other sections; no reason text or metadata is rendered.
- [x] T030 [US4] **Owner 003.3**: add an optional boolean filter for denied rows (action code starting with `DENIED_`) to `GET /api/v1/workshop-audit/settlement-records` in `BE/workshopsettlement/` (R-5); the existing response and rules are unchanged; extend `SettlementAuditReviewServiceTest` and `SettlementAuditReviewIT`. Blocks T031.
- [x] T031 [US4] Add the denied-only Settlement read to `FE/infrastructure/homeReads.js` and merge its rows in `RecentEvents.jsx`, removing the missing-data note when it is readable (needs T030); extend `recentEvents.test.jsx` with a Settlement refusal and an Operations change appearing in one list.

---

## Phase 7: User Story 5 - Reach frequent tasks (Priority: P3)

- [x] T032 [US5] Add `ui/Shortcuts.jsx` in FE: a fixed row of four shortcuts (spec FR-011) (Workshop packages `/operations/packages`, Package availability `/operations/package-availability`, Locations & slots `/operations/admin`, Session board `/operations/sessions`); the set is a proposal the owner confirms (spec Assumptions). Add `FE/tests/shortcuts.test.jsx`: each shortcut opens its screen.

---

## Phase 8: Polish and cross-cutting

- [ ] T033 Layout and accessibility in `FE/ui/`: usable at 375 px with cards stacked and no horizontal scrolling; keyboard operation and text labels throughout (spec FR-016, FR-017, SC-005). Add the styles the slice needs without changing shared styles.
- [x] T034 Retire the Manager variant of the old home in `ppswbs_frontend/src/features/operatorhome/`: remove the Manager branch from `operatorSections.js` and its tests in `operatorHome.test.jsx`, keeping the Staff home and `/home` redirect unchanged; the Manager no longer sees the fixed menu cards (needs T007 to T032 for the cards that ship).
- [x] T035 Role and access tests in `FE/tests/access.test.jsx`: Staff, Admin and a signed-out visitor opening `/manager/home` see no Manager figure (Staff go to their own home); an expired session returns to sign-in and then to `/manager/home` (spec FR-001, FR-018, SC-006).
- [x] T036 Gate before hand-over: run `npm test` and `npm run build` in `ppswbs_frontend/` and record the actual results; for each owner read merged, run that owner's tests and `ModulithArchitectureTests`. Record failures instead of hiding them.
- [ ] T037 Run the scenarios of [quickstart.md](quickstart.md) against the development environment and record what was and was not executed, including the timed check of SC-001; hand the acceptance criteria (the UI-visible parts of SC-001 to SC-007) to `ppswbs-testing/` for the browser scenario under its own persona (`$e2e-test-specify`), which is **not** a task of this feature.

---

## Dependencies and order

- T001 (design gate and approval) blocks every code task, T002 onward.
- Phase 1 → Phase 2 → stories. Phase 2 blocks everything.
- US1 (P1) is the MVP and needs only Phase 2. US2 to US5 each need only Phase 2 and are independent of each other.
- Owner reads: T014 → T017, T015 → T018, T016 → T019, T023 → T024, T030 → T031. T020 is dropped (no review-moderation card).
- US4 also needs the Audit review of feature 003.2 (003.2 T170 and 003.2 T171, its Phase 19 tasks) present in the environment, or the section has no source to read.
- T034 comes after the cards that ship; T035 to T037 come last.

## Parallel opportunities

- After T007: T010–T012 (US1), T021–T022 (US2), T025–T026 (US3), T028–T029 (US4) and T032 (US5) touch different files and can run together.
- Owner reads T014, T015, T016, T023, T030 are in different modules and can be built in parallel by their owners.

## Implementation strategy

1. **MVP:** Phases 1–2 and US1 wave 1 (three cards). It already answers "what is waiting".
2. Add US3 (revenue), US2 (session counts), US4 (Operations events) and US5 in any order; each is independent.
3. As each owner delivers its read, switch its card on (T017–T019, T024, T031). The page never waits for them.
4. Finish with Polish (T033–T037).

## Notes

- A queue whose read does not exist is hidden, never shown as unavailable or zero (spec Clarification 1).
- Counts are never shown without a matching list (spec FR-004).
- No task writes data, adds a table, or changes the Manager Dashboard (spec FR-012, FR-015).
- Evidence 2026-11-18 (wave 1, frontend): code in `ppswbs_frontend/src/features/managerhome/`; `npm test` 119 files / 1039 tests passed and `npm run build` passed (before the review-moderation cleanup); `npx vitest run src/features/managerhome` 42 passed after it. Ticked: T002–T012, T021, T022, T025, T026, T028, T029, T032, T034, T035, T036 (T036 covers the frontend gate only; no owner read was merged). T010 queue links: late payments and design review lists already show only the waiting set; change requests opens with `?status=PENDING`.
- Evidence 2026-11-18 (owner reads, owner agreement Hồ Hoàng Quốc as recorded in approval.md): T030 SettlementAuditReviewServiceTest 12/12, SettlementAuditReviewIT 6/6. T014+T015 ManagerHomeWaitingCountsIT 3/3 (count equals ?waiting=true list; cash-close count equals ?awaitingConfirmation=true list; Staff 403). T016 OtaReconciliationApiTest 16/16 and OtaChannelControllerIT 19/19 on MySQL (count equals list; Admin and Staff refused the count, owner decision: Manager only). T023 WorkshopDaySummaryIT 3/3. OpenAPI: OtaConnectivityOpenApiIT 9/9. Definitions used: refund waiting = PENDING_ACCOUNT/APPROVAL/TRANSFER/RECONCILIATION; OTA = OPEN and ESCALATED; cash close = PENDING_MANAGER_CONFIRMATION; expected = ACTIVE-ticket holders on not-cancelled sessions; denied = action code DENIED_*. T018: a confirmation view was added to the cash-close screen (cashCloseConfirmationView.test.jsx 3/3) so the card opens a list equal to its count. T017/T019/T024/T031 frontend: 47 managerhome tests plus workshop-settlement tests, npm test 119 files all passed, npm run build passed.
- Backend run 2026-11-18 (003 packages + ModulithArchitectureTests): the first run (1893 tests) had 14 failures and 5 errors in 8 classes. Fixed in tests and contracts only (no product code): WorkshopPackageFacadeIT and WorkshopOtaSnapshotIT activate the Package at the session location (Phase 18 availability); WorkshopSettlementMigrationIT inserts currency_code; WorkshopSettlementContractIT lists the existing change-request-status route (now also in the OpenAPI); WorkshopBookingContractIT reads the contract with CRLF normalised. Re-run after the fixes (1894 tests): 1 failure and 5 errors in 2 classes. BookingIdempotencyIT (H2 table missing, "database is empty") ran early in the run, before the new Manager Home ITs, and RefundReceiptImageMysqlIT uses its own data; both pass when run alone (5/5 and 3/3), so they are order-dependent in the full run. The cause is not proven and neither was fixed.
- Not ticked: T013, T027, T037 (manual checks against the dev environment not run); T033 (375 px layout CSS written, not checked in a browser); T020 dropped.
