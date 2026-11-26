---
format_version: 2
id: E2E-012-026
feature: 012-workshop-ring-design-configuration
test_level: e2e
test_case_type: A
source_ref: spec.md#9-errors-and-concurrency
target_url: http://localhost:5173/
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: ring-design-monkey-navigation-chaos
  account_ref: E2E_MEMBER_001
  isolation_key: E2E-012-026-local
cleanup:
  required: false
  method: docker-seed-reset-on-restart
---

# E2E-012-026: Monkey testing, rapid clicks, Back/Forward/Refresh chaos, and 409 version conflict on Ring Configurator

**Created**: 2026-11-26
**Updated**: 2026-11-26
**Owner**: QA Agent

## 1. Scenario Charter

- **User goal**: Stress-test the Workshop Ring Configurator against chaotic user behavior: rapid parameter switching, Back/Forward/Refresh while in unsaved local preview vs after saving, spam-clicking Save/Evaluate, and two-tab stale `409 DESIGN_VERSION_CONFLICT`.
- **Starting context**: Local frontend and backend are running. Member account is available.
- **Scope**: Unsaved local preview reset on Refresh/Back-Forward, saved design persistence across Refresh/Back-Forward, spam-click idempotency/loading lock, and two-tab `409` conflict handling.
- **Out of scope**: Guest sign-in flow (`E2E-012-024`) and Admin policy changes (`E2E-012-025`).

## 2. Preconditions and Test Data

- [ ] Required services in `health_urls` are ready.
- [ ] Seeded Member account (`E2E_MEMBER_001`) is available without hardcoding secrets here.
- [ ] Starting session: authenticated as Member (`E2E_MEMBER_001`).

## 3. Exploratory Step Contract

### Step 1 — Rapid parameter switching + Back / Forward / Refresh on unsaved local preview

- **User charter**: Rapidly toggle materials, shapes, gemstones, and engravings in local preview without saving, then press browser Back, Forward, and Refresh (F5).
- **Explore & act**: Open the configurator as `E2E_MEMBER_001`, rapidly switch options back and forth, navigate to a workshop page or home, press browser Back, then Forward, then Back again, and finally press Refresh.
- **Expected**:
  - Unsaved local preview changes are discarded/reset to the default configuration on Refresh or Back/Forward navigation (no phantom state).
  - No UI crash, frozen controls, or unhandled console errors occur during rapid switching and navigation.
- **Decision rule**:
  - `CONTINUE`: Local preview resets cleanly and UI remains responsive.
  - `LOG_AND_CONTINUE`: Minor visual flicker during rapid option switching.
  - `STOP`: App crashes, freezes, or leaks stale/corrupted preview values across navigation.

### Step 2 — Spam-click Save / Evaluate, then Back / Forward / Refresh after Save

- **User charter**: Configure a valid design, rapidly double-click/spam Save/Evaluate, then immediately press Refresh and Back/Forward.
- **Explore & act**: Select valid options, rapidly click Save/Evaluate multiple times, wait for completion, then press Refresh and browser Back/Forward.
- **Expected**:
  - Save/Evaluate button locks/disables on first click and creates **exactly 1** saved design/version (no duplicate designs via idempotency).
  - After Refresh and Back/Forward, the saved design remains accessible in the Member's saved designs list/detail without re-submitting a duplicate save.
- **Decision rule**:
  - `CONTINUE`: Exactly one saved design/version is created and persists across Refresh/Back/Forward.
  - `LOG_AND_CONTINUE`: Button lacks a visible spinner though idempotency prevents duplicate saves.
  - `STOP`: Duplicate designs/versions are created or Refresh re-triggers save.

### Step 3 — Two-tab concurrent edit triggering `409 DESIGN_VERSION_CONFLICT`

- **User charter**: Open the same saved design in two browser tabs, save a new version in Tab A, then attempt to save an edit from Tab B's stale version.
- **Explore & act**: Open the saved design from Step 2 in Tab A and Tab B. In Tab A, modify a parameter and Save. Switch to Tab B (without refreshing), modify a parameter, and click Save.
- **Expected**:
  - Tab A saves successfully and increments the design version.
  - Tab B rejects the stale save with a clear version conflict message (`409 DESIGN_VERSION_CONFLICT`) rather than silently overwriting Tab A's changes.
- **Decision rule**:
  - `CONTINUE`: Stale save is rejected with clear conflict feedback and Tab A's version is preserved.
  - `LOG_AND_CONTINUE`: Conflict message requires manual page reload in Tab B to fetch latest version.
  - `STOP`: Tab B silently overwrites Tab A's saved version or crashes with an unhandled error.

## 4. Evidence and Findings

- Capture a screenshot for every `LOG_AND_CONTINUE` or `STOP` outcome.
- A `LOG_AND_CONTINUE` record contains: problem description, reproduction steps, screenshot path, and (when confirmed defect) its `BUG-LOG.md` reference.
- Capture console or network output only when it materially helps classify or reproduce the finding.

## 5. Execution History

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-26T19:29:20+07:00
- Executed by: QA Agent (browsermcp)
- Browser tool: browsermcp
- Final step: Step 3
- Actual result: Rapid option switching + Back/Forward/Reload reset unsaved local preview cleanly to defaults without crash. Spam-clicking Save created exactly 1 design (DSG-3c027ebf-9fdb-4564-b605-49c023137368) which persisted across reload. Two-tab concurrent edit returned 409 Conflict on Tab B's stale version without overwriting Tab A.
- Findings: NONE
- Evidence screenshots:
  - ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-026--step2-saved-after-reload.jpg
  - ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-026--step3-409-conflict.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
