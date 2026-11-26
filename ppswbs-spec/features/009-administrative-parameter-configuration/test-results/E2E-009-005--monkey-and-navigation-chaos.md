---
format_version: 2
id: E2E-009-005
feature: 009-administrative-parameter-configuration
test_level: e2e
test_case_type: A
source_ref: spec.md#contract-recovery-and-exclusions
target_url: http://localhost:5173/admin/ring-difficulty
actor_role: Admin
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: admin-ring-difficulty-monkey-chaos
  account_ref: E2E_ADMIN_001
  isolation_key: E2E-009-005-local
cleanup:
  required: false
  method: docker-seed-reset-on-restart
---

# E2E-009-005: Monkey testing, rapid double-clicks, and browser Back/Forward/Refresh chaos on Admin screen

**Created**: 2026-11-26
**Updated**: 2026-11-26
**Owner**: QA Agent

## 1. Scenario Charter

- **User goal**: Stress-test the Admin Ring Difficulty screen against chaotic user behavior: malformed/extreme/XSS inputs, rapid double-clicking on Preview and Publish, and arbitrary browser Back/Forward/Refresh navigation mid-edit and post-publish.
- **Starting context**: Active Admin is authenticated and on `/admin/ring-difficulty`.
- **Scope**: Input fuzzing (`<script>`, `1e10`, extremely long strings, comma/dot decimals), double-click/spam-click idempotency and loading lock on Preview/Publish, and Back/Forward/F5 state consistency.
- **Out of scope**: Multi-tab `409` conflict (`E2E-009-004`) and role masking (`E2E-009-002`).

## 2. Preconditions and Test Data

- [ ] Required services in `health_urls` are ready.
- [ ] Active Admin account (`E2E_ADMIN_001`) is signed in.
- [ ] Starting session: authenticated as active Admin.

## 3. Exploratory Step Contract

### Step 1 — Monkey input fuzzing (XSS, scientific notation, extreme strings)

- **User charter**: Enter unexpected and extreme values into score and cutoff inputs to verify UI sanitization and validation resilience.
- **Explore & act**: Try typing or pasting `<script>alert(1)</script>`, `1e10`, `99999999999999999`, `1,25` (comma), and empty/whitespace into numeric policy fields.
- **Expected**:
  - No script executes (no XSS alert or broken HTML rendering).
  - Invalid/non-numeric or out-of-range inputs are either blocked by the input or flagged with clear validation errors adjacent to the field.
  - Confirm Publish remains locked/disabled and the page does not crash.
- **Decision rule**:
  - `CONTINUE`: UI remains stable, rejects/flags invalid inputs, and locks Publish.
  - `LOG_AND_CONTINUE`: Minor visual overflow on long error/input text without breaking functionality.
  - `STOP`: Page crashes (white screen), executes injected script, or allows publishing malformed values.

### Step 2 — Browser Back / Forward / Refresh while editing unsaved candidate

- **User charter**: Modify candidate fields without publishing, then navigate away, press browser Back/Forward, and press Refresh (F5).
- **Explore & act**: Change a candidate score field locally (do not publish), navigate to another page via header/nav or URL, press browser Back, then press browser Forward, then Back again, and finally reload the page.
- **Expected**:
  - Reloading or navigating Back/Forward reloads the current effective policy from the server and discards unsaved local edits (no phantom draft persistence).
  - No unhandled console errors, infinite redirect loops, or blank screens occur.
- **Decision rule**:
  - `CONTINUE`: Effective policy reloads cleanly from server and unsaved local edits are discarded.
  - `LOG_AND_CONTINUE`: Minor transient flicker during route transition.
  - `STOP`: App crashes, gets stuck in loading/redirect loop, or displays stale/corrupted values.

### Step 3 — Rapid double-click / spam-click on Preview and Publish

- **User charter**: Rapidly click Preview multiple times, then after a valid preview, rapidly click Confirm Publish multiple times.
- **Explore & act**: Enter a valid candidate change, rapidly double-click/spam the Preview button, wait for it to finish, then rapidly double-click/spam the Confirm Publish button.
- **Expected**:
  - Both Preview and Confirm Publish buttons immediately enter a loading/disabled state upon the first click to prevent duplicate submissions.
  - Publication completes cleanly, incrementing the effective version by **exactly 1** and creating **exactly 1** new history entry (no duplicate versions or `409` self-conflict).
- **Decision rule**:
  - `CONTINUE`: Buttons lock on first click and exactly one version is published.
  - `LOG_AND_CONTINUE`: Button does not visually show a spinner, though duplicate clicks still result in only one version via idempotency.
  - `STOP`: Multiple duplicate versions are created, self-conflict error breaks the flow, or UI hangs permanently in loading state.

### Step 4 — Back / Forward / Refresh immediately after successful Publish

- **User charter**: Immediately after publishing a new version, press browser Refresh and Back/Forward to verify state consistency.
- **Explore & act**: Refresh the page right after publication succeeds, then navigate Back and Forward.
- **Expected**:
  - After Refresh and Back/Forward, the newly published version remains the active effective version and appears at the top of publication history.
  - No duplicate publish command is re-triggered on refresh.
- **Decision rule**:
  - `CONTINUE`: Newly published version persists cleanly across Refresh and Back/Forward.
  - `LOG_AND_CONTINUE`: Minor scroll-position reset after refresh.
  - `STOP`: Refresh re-submits publication or reverts display to the old version.

## 4. Evidence and Findings

- Capture a screenshot for every `LOG_AND_CONTINUE` or `STOP` outcome.
- A `LOG_AND_CONTINUE` record contains: problem description, reproduction steps, screenshot path, and (when confirmed defect) its `BUG-LOG.md` reference.
- Capture console or network output only when it materially helps classify or reproduce the finding.

## 5. Execution History

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-26T19:20:48+07:00
- Executed by: QA Agent (browsermcp)
- Browser tool: browsermcp
- Final step: Step 4
- Actual result: XSS (<script>alert(1)</script>), 1e10, and 1,25 were flagged invalid with Publish locked and no script execution. Back/Forward/Reload discarded unsaved edits and reloaded effective policy. Rapid double-click on Preview and Publish created exactly 1 version (2026.11-v3.0), which persisted after reload.
- Findings: NONE
- Evidence screenshots:
  - ppswbs-spec/features/009-administrative-parameter-configuration/test-results/screenshots/E2E-009-005--step4-post-publish-reload.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
