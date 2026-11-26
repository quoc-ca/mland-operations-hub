---
format_version: 2
id: E2E-012-025
feature: 012-workshop-ring-design-configuration
test_level: e2e
test_case_type: A
source_ref: spec.md#41-admin-owned-difficulty-policy--proposed-2026-11-26
target_url: http://localhost:5173/
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: ring-design-policy-integration-immutability
  account_ref: E2E_MEMBER_001_AND_E2E_ADMIN_001
  isolation_key: E2E-012-025-local
cleanup:
  required: false
  method: docker-seed-reset-on-restart
---

# E2E-012-025: Feature 009 ↔ 012 integration — pending unscored options and saved version policy immutability

**Created**: 2026-11-26
**Updated**: 2026-11-26
**Owner**: QA Agent

## 1. Scenario Charter

- **User goal**: Verify cross-feature integration between Feature 009 and Feature 012: (1) previously saved design versions retain their original score, level, recommendation, and `rules_version` even after an Admin publishes a new difficulty policy, while new evaluations use the new policy; and (2) any newly active option without an Admin difficulty score is shown disabled (`DIFFICULTY_SCORE_PENDING`) and cannot be selected.
- **Starting context**: Local frontend and backend are running. Seeded Member and Admin accounts are available.
- **Scope**: Saved design immutability across policy publication, new evaluation picking up new policy version, and disabled state for pending unscored options (if present in seed/catalogue).
- **Out of scope**: Admin form validation (`E2E-009-003`) and monkey stress (`E2E-012-026`).

## 2. Preconditions and Test Data

- [ ] Required services in `health_urls` are ready.
- [ ] Seeded Member (`E2E_MEMBER_001`) and Admin (`E2E_ADMIN_001`) accounts are available.
- [ ] Starting session: authenticated as Member (`E2E_MEMBER_001`).

## 3. Exploratory Step Contract

### Step 1 — Member saves Design D1 under current policy version `vN` and checks option states

- **User charter**: Save a ring design as Member and record its initial evaluated difficulty score, level, recommendation, and `rules_version` (and check if any `DIFFICULTY_SCORE_PENDING` option is disabled).
- **Explore & act**: Sign in as `E2E_MEMBER_001`, open the ring configurator, inspect available options (noting any disabled option pending difficulty score), configure a valid design `D1`, and Save/Evaluate it.
- **Expected**:
  - Design `D1` saves successfully and displays its evaluated score (without `/10`), difficulty level, workshop recommendation, and `rules_version` (`vN`).
  - If any option lacks a published difficulty score, it is visible but disabled for selection with an explanation.
- **Decision rule**:
  - `CONTINUE`: Design `D1` is saved with observable score and `rules_version` `vN`.
  - `LOG_AND_CONTINUE`: `rules_version` label is shown inside a detail/history drawer rather than main summary.
  - `STOP`: Design fails to save or `rules_version` / score is missing.

### Step 2 — Admin publishes a new difficulty policy version `vN+1` in Feature 009

- **User charter**: Switch to Admin, significantly change the difficulty score of a material/parameter used in `D1`, preview, and publish policy `vN+1`.
- **Explore & act**: Sign out Member, sign in as `E2E_ADMIN_001`, open `/admin/ring-difficulty`, adjust a score used by `D1` (keeping all active models eligible), Preview, and Confirm Publish.
- **Expected**:
  - Policy `vN+1` publishes successfully and becomes the active effective policy.
- **Decision rule**:
  - `CONTINUE`: Policy `vN+1` is active.
  - `LOG_AND_CONTINUE`: Minor UI delay updating history.
  - `STOP`: Policy `vN+1` fails to publish.

### Step 3 — Member re-opens `D1` (immutable) and evaluates a new design/version under `vN+1`

- **User charter**: Sign back in as Member, view previously saved `D1` to verify it did NOT silently re-evaluate, then run a new Save/Evaluate to verify the new policy `vN+1` applies to new evaluations.
- **Explore & act**: Sign out Admin, sign in as `E2E_MEMBER_001`, open saved design `D1` from Member history (without re-evaluating), inspect its stored score, level, recommendation, and `rules_version`. Then create/evaluate a new version or new design `D2` with the same parameters as `D1`.
- **Expected**:
  - Viewing saved `D1` displays the **exact original** score, level, recommendation, and `rules_version` (`vN`) — it does NOT silently recalculate under `vN+1`.
  - Evaluating new design `D2` (or a new version) reflects the **updated score** from policy `vN+1` and references `rules_version` `vN+1`.
- **Decision rule**:
  - `CONTINUE`: Saved `D1` remains immutable on `vN` while new evaluation `D2` uses `vN+1`.
  - `LOG_AND_CONTINUE`: Minor UI refresh needed to see `D2` in history list.
  - `STOP`: Viewing `D1` silently changes its score/rules_version to `vN+1`, or new evaluation `D2` still uses `vN`.

## 4. Evidence and Findings

- Capture a screenshot for every `LOG_AND_CONTINUE` or `STOP` outcome.
- A `LOG_AND_CONTINUE` record contains: problem description, reproduction steps, screenshot path, and (when confirmed defect) its `BUG-LOG.md` reference.
- Capture console or network output only when it materially helps classify or reproduce the finding.

## 5. Execution History

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-26T19:34:23+07:00
- Executed by: QA Agent (browsermcp)
- Browser tool: browsermcp
- Final step: Step 3
- Actual result: Saved design DSG-b8a20bbb-c63f-4f52-88e2-f20881a493e1 retained Quy tắc: 2026.11-v4.0 after Admin published 2026.11-v5.0 (MAT-SILVER-925 = 2.5000). Newly evaluated design DSG-0efe1f29-015b-48ab-8324-1bf7da3ea65f picked up Quy tắc: 2026.11-v5.0 and updated difficulty level ("Trung bình"). No unscored DIFFICULTY_SCORE_PENDING option was present in current seed.
- Findings: NONE
- Evidence screenshots:
  - ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-025--step2-admin-v5.jpg
  - ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-025--step3-old-design-v4.jpg
  - ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-025--step3-new-design-v5.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
