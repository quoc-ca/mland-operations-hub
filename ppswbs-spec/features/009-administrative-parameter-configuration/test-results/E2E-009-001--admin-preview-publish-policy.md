---
format_version: 2
id: E2E-009-001
feature: 009-administrative-parameter-configuration
test_level: e2e
test_case_type: N
source_ref: spec.md#user-journey-and-acceptance
target_url: http://localhost:5173/admin/ring-difficulty
actor_role: Admin
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: admin-ring-difficulty-happy-path
  account_ref: E2E_ADMIN_001
  isolation_key: E2E-009-001-local
cleanup:
  required: false
  method: docker-seed-reset-on-restart
---

# E2E-009-001: Active Admin previews and publishes a valid ring difficulty policy

**Created**: 2026-11-26
**Updated**: 2026-11-26
**Owner**: QA Agent

## 1. Scenario Charter

- **User goal**: As an active Admin, review the current effective ring difficulty policy, adjust valid candidate values, preview before/after impacts on standard and active published models, and publish a new immutable policy version.
- **Starting context**: Local frontend and backend are running with seeded data. An active Admin account is available for sign-in.
- **Scope**: Happy path from loading `/admin/ring-difficulty`, editing candidate fields, running preview (including optional non-personal sample if available), confirming atomic publication, and verifying the updated effective version and publication history.
- **Out of scope**: Non-Admin access blocking (`E2E-009-002`), invalid inputs/blocking models (`E2E-009-003`), `409` concurrency/history restore (`E2E-009-004`), and monkey/navigation stress (`E2E-009-005`).

## 2. Preconditions and Test Data

- [ ] Required services in `health_urls` are ready.
- [ ] Seeded active Admin account (`E2E_ADMIN_001`) is available without hardcoding secrets here.
- [ ] Starting session: authenticated as active Admin (or sign in as Admin when prompted).

## 3. Exploratory Step Contract

### Step 1 — Open Admin Ring Difficulty screen and inspect effective policy

- **User charter**: Open the ring difficulty administration screen as an active Admin and review the currently effective policy version and history.
- **Explore & act**: Navigate to `target_url` (signing in as `E2E_ADMIN_001` first if redirected to sign-in), then inspect the rendered screen structure, current version, editable parameter sections, and publication history.
- **Expected**:
  - The screen loads without authorization error or `/404` masking.
  - Current effective policy version number, editable difficulty parameters (material, shape, gemstone/component, attachment scores, engraving/image coefficients, four level cutoffs, Wax Casting cutoff, Silver Clay gemstone cutoff), and publication history are visible.
  - No Member designs, individual orders, payment details, or executive revenue data are shown.
- **Decision rule**:
  - `CONTINUE`: Effective policy version, parameter form, and history section are visible.
  - `LOG_AND_CONTINUE`: Minor layout or label clarity issue that does not block reading or editing parameters.
  - `STOP`: Screen fails to load, redirects Admin to `/404` or unauthorized, or exposes Member/payment data.

### Step 2 — Edit candidate values with valid inputs

- **User charter**: Adjust a few candidate parameter values (for example, slightly increasing a material or standard engraving score while keeping all cutoffs strictly ordered and all active models eligible).
- **Explore & act**: Locate an editable score or coefficient field in the form, enter a valid non-negative value with up to 4 decimal places, and observe form feedback.
- **Expected**:
  - The edited field updates cleanly without discarding unrelated fields on the form.
  - No validation error is shown for valid inputs.
  - Publication confirmation remains disabled or requires a fresh preview before publishing.
- **Decision rule**:
  - `CONTINUE`: Candidate value is accepted locally and unrelated fields remain intact.
  - `LOG_AND_CONTINUE`: Minor UI quirk while editing that still keeps the candidate valid.
  - `STOP`: Form blocks valid input, resets unrelated fields, or allows direct publication without previewing the edited candidate.

### Step 3 — Preview candidate impact on standard samples and active published models

- **User charter**: Run a preview of the edited candidate policy to inspect before/after scores, difficulty levels, and workshop recommendations, plus eligibility of all active published models.
- **Explore & act**: Trigger the Preview action (optionally selecting a non-personal sample if offered by the UI) and wait for the preview result to render.
- **Expected**:
  - The preview displays before/after score, difficulty level, and workshop recommendation for required standard non-personal examples.
  - The preview lists before/after scores and eligibility status for every active published model using its default configuration, with zero blocking models.
  - Publication confirmation becomes enabled after the eligible preview succeeds.
- **Decision rule**:
  - `CONTINUE`: Preview renders complete before/after comparisons and enables publication confirmation.
  - `LOG_AND_CONTINUE`: Optional non-personal sample selector is missing/limited while required standard examples and active models render properly.
  - `STOP`: Preview fails with server error, omits active published models, or fails to enable publication after a valid eligible preview.

### Step 4 — Confirm atomic publication and verify new effective version & audit history

- **User charter**: Confirm publication of the previewed candidate and verify the new policy version becomes effective and appears in history.
- **Explore & act**: Click the Publish/Confirm action, confirm any confirmation dialog if shown, and inspect the updated effective policy header and publication history list.
- **Expected**:
  - Publication succeeds and displays confirmation feedback with the new incremented policy version number.
  - The newly published values appear as the active effective policy.
  - The publication history includes a new immutable entry showing the Admin actor, timestamp, and version change without overwriting prior history rows.
- **Decision rule**:
  - `CONTINUE`: New version is effective and recorded in audit history.
  - `LOG_AND_CONTINUE`: Confirmation toast/banner disappears too quickly, though version and history updated correctly.
  - `STOP`: Publication fails, version does not increment, prior history is overwritten, or old values remain active.

## 4. Evidence and Findings

- Capture a screenshot for every `LOG_AND_CONTINUE` or `STOP` outcome.
- A `LOG_AND_CONTINUE` record contains: problem description, reproduction steps, screenshot path, and (when confirmed defect) its `BUG-LOG.md` reference.
- Capture console or network output only when it materially helps classify or reproduce the finding.

## 5. Execution History

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-26T19:18:57+07:00
- Executed by: QA Agent (browsermcp)
- Browser tool: browsermcp
- Final step: Step 4
- Actual result: Active Admin opened /admin/ring-difficulty (v1.0), edited MAT-COPPER to 1.1000, ran Preview (4 standard samples + 12 active models, 0 blocked), confirmed modal, and published 2026.11-v2.0 (POST 201).
- Findings: NONE
- Evidence screenshots:
  - ppswbs-spec/features/009-administrative-parameter-configuration/test-results/screenshots/E2E-009-001--step1-initial.jpg
  - ppswbs-spec/features/009-administrative-parameter-configuration/test-results/screenshots/E2E-009-001--step4-after-click-publish.jpg
  - ppswbs-spec/features/009-administrative-parameter-configuration/test-results/screenshots/E2E-009-001--step4-published.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
