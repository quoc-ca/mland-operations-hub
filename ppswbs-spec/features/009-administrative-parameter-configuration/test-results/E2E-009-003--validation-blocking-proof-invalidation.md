---
format_version: 2
id: E2E-009-003
feature: 009-administrative-parameter-configuration
test_level: e2e
test_case_type: B
source_ref: spec.md#contract-recovery-and-exclusions
target_url: http://localhost:5173/admin/ring-difficulty
actor_role: Admin
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: admin-ring-difficulty-validation-blocking
  account_ref: E2E_ADMIN_001
  isolation_key: E2E-009-003-local
cleanup:
  required: false
  method: docker-seed-reset-on-restart
---

# E2E-009-003: Validation boundaries, blocking active model, and preview proof invalidation

**Created**: 2026-11-26
**Updated**: 2026-11-26
**Owner**: QA Agent

## 1. Scenario Charter

- **User goal**: Verify that invalid/boundary inputs are flagged inline without discarding unrelated edits, that a candidate making an active published model exceed the too-risky cutoff is blocked from publication during preview, and that editing the form after a valid preview invalidates the prior preview proof.
- **Starting context**: Active Admin is authenticated and on `/admin/ring-difficulty`.
- **Scope**: Field validation (negative scores, >4 decimals, non-increasing cutoffs, Wax cutoff >= too-risky cutoff), active model blocking in preview, and proof invalidation upon post-preview edit.
- **Out of scope**: Concurrent `409` conflict (`E2E-009-004`) and spam/back-forward monkey testing (`E2E-009-005`).

## 2. Preconditions and Test Data

- [ ] Required services in `health_urls` are ready.
- [ ] At least one active published ring model exists in seeded data.
- [ ] Starting session: authenticated as active Admin (`E2E_ADMIN_001`).

## 3. Exploratory Step Contract

### Step 1 — Enter invalid boundary values on candidate form

- **User charter**: Test client/server validation for negative scores, >4 decimal places, out-of-order difficulty cutoffs, and Wax Casting cutoff >= too-risky cutoff.
- **Explore & act**: Modify one unrelated valid field first (to verify preservation), then enter a negative score (e.g., `-0.5`), a 5-decimal value (`1.12345`), and an out-of-order cutoff (e.g., Wax Casting cutoff higher than or equal to the too-risky cutoff).
- **Expected**:
  - Clear validation errors appear adjacent to each invalid field.
  - The unrelated valid edit on the form is preserved (not reset).
  - Confirm Publish remains disabled/locked while validation errors exist.
- **Decision rule**:
  - `CONTINUE`: Inline validation errors appear per invalid field, unrelated edits stay intact, and Publish is locked.
  - `LOG_AND_CONTINUE`: Validation message only appears on blur/Preview rather than immediately on change, while still blocking Publish.
  - `STOP`: Invalid values are accepted for publication or wipe out other edited fields.

### Step 2 — Lower too-risky cutoff to trigger an active published model block

- **User charter**: Correct the syntax errors, then set valid ordered cutoffs where the too-risky cutoff is low enough that at least one active published model's default configuration exceeds it, and run Preview.
- **Explore & act**: Fix the invalid fields from Step 1, set the too-risky cutoff low (e.g., below a seeded published model's default score while keeping cutoffs strictly ordered), and click Preview.
- **Expected**:
  - Preview renders the impact table showing the affected active published model(s) marked as blocking/ineligible with a clear reason.
  - Confirm Publish remains disabled/unavailable because no publication proof is issued when any active model is blocked.
  - Prior effective policy version remains unchanged.
- **Decision rule**:
  - `CONTINUE`: Blocking model is surfaced in preview and Publish is prevented.
  - `LOG_AND_CONTINUE`: Blocking reason text is generic, though the specific model and block state are clear.
  - `STOP`: Preview issues a proof or allows publishing a policy that blocks an active published model.

### Step 3 — Run valid Preview, then edit a field to verify proof invalidation

- **User charter**: Restore valid cutoffs so all active models are eligible, run Preview to unlock Publish, then modify a field on the form and verify the old preview proof is invalidated.
- **Explore & act**: Adjust cutoffs/scores to valid values, click Preview to confirm eligibility and unlock Publish, then change any score field by a small valid amount (e.g. `+0.1`) without clicking Preview again.
- **Expected**:
  - After the valid Preview, Confirm Publish becomes enabled.
  - Immediately after editing a field post-preview, Confirm Publish becomes disabled/locked again and the UI indicates a fresh preview is required for the updated candidate.
- **Decision rule**:
  - `CONTINUE`: Post-preview edit reliably invalidates the previous proof and requires a fresh preview.
  - `LOG_AND_CONTINUE`: Stale preview table remains visible with a "stale/needs re-preview" badge while Publish is disabled.
  - `STOP`: Confirm Publish remains enabled after editing the form post-preview or publishes stale/unpreviewed values.

## 4. Evidence and Findings

- Capture a screenshot for every `LOG_AND_CONTINUE` or `STOP` outcome.
- A `LOG_AND_CONTINUE` record contains: problem description, reproduction steps, screenshot path, and (when confirmed defect) its `BUG-LOG.md` reference.
- Capture console or network output only when it materially helps classify or reproduce the finding.

## 5. Execution History

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-26T19:19:48+07:00
- Executed by: QA Agent (browsermcp)
- Browser tool: browsermcp
- Final step: Step 3
- Actual result: Negative (-0.5), 5-decimal (1.12345), and Wax >= TOO_RISKY (11.0000) triggered inline alerts while preserving valid edits and locking Preview/Publish. Lowering TOO_RISKY to 3.5000 flagged 4 blocking models in Preview and kept Publish disabled. Editing a field after a valid Preview immediately invalidated proof and disabled Publish.
- Findings: NONE
- Evidence screenshots:
  - ppswbs-spec/features/009-administrative-parameter-configuration/test-results/screenshots/E2E-009-003--step1-validation.jpg
  - ppswbs-spec/features/009-administrative-parameter-configuration/test-results/screenshots/E2E-009-003--step2-blocking-model.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
