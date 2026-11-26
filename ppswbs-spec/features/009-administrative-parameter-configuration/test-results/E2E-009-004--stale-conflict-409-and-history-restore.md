---
format_version: 2
id: E2E-009-004
feature: 009-administrative-parameter-configuration
test_level: e2e
test_case_type: A
source_ref: spec.md#user-journey-and-acceptance
target_url: http://localhost:5173/admin/ring-difficulty
actor_role: Admin
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: admin-ring-difficulty-concurrency-restore
  account_ref: E2E_ADMIN_001
  isolation_key: E2E-009-004-local
cleanup:
  required: false
  method: docker-seed-reset-on-restart
---

# E2E-009-004: Stale version conflict (409) recovery and history version restoration

**Created**: 2026-11-26
**Updated**: 2026-11-26
**Owner**: QA Agent

## 1. Scenario Charter

- **User goal**: Verify optimistic concurrency protection (`409`) when two Admin tabs edit/publish concurrently, ensuring the stale tab preserves its form edits and recovers by reloading and re-previewing; and verify inspecting and restoring a past policy version via a fresh preview and new publication.
- **Starting context**: Active Admin is authenticated. Two browser tabs/pages are available on `/admin/ring-difficulty`.
- **Scope**: Concurrent publish `409` handling, form preservation, reload-and-retry recovery, and history inspection/restore flow.
- **Out of scope**: Non-Admin access (`E2E-009-002`) and monkey stress (`E2E-009-005`).

## 2. Preconditions and Test Data

- [ ] Required services in `health_urls` are ready.
- [ ] Active Admin account (`E2E_ADMIN_001`) is signed in.
- [ ] Starting session: authenticated as active Admin across two browser tabs.

## 3. Exploratory Step Contract

### Step 1 — Open `/admin/ring-difficulty` in two tabs at the same effective version

- **User charter**: Load the same effective policy version in Tab A and Tab B, edit different valid values in each, and preview both.
- **Explore & act**: Open `target_url` in Tab A and Tab B; verify both show the same effective version; edit a valid score in Tab A and preview; edit a different valid score in Tab B and preview.
- **Expected**:
  - Both tabs display the same starting effective version and successfully generate a preview.
- **Decision rule**:
  - `CONTINUE`: Both tabs are ready to publish against the same base version.
  - `LOG_AND_CONTINUE`: Minor UI delay switching between tabs.
  - `STOP`: Cannot open or preview in both tabs.

### Step 2 — Publish in Tab A, then attempt stale Publish in Tab B

- **User charter**: Publish Tab A first, then attempt to publish Tab B's stale preview without reloading.
- **Explore & act**: In Tab A, confirm publication and wait for success. Switch to Tab B and click Confirm Publish.
- **Expected**:
  - Tab A publishes successfully and increments the effective version.
  - Tab B receives a stale version conflict (`409`) error message explaining the policy version has changed.
  - Tab B preserves the Admin's candidate form values (does not wipe edits) and prompts/allows reloading the latest effective version.
- **Decision rule**:
  - `CONTINUE`: Tab B rejects stale publish with a clear conflict message while preserving form edits.
  - `LOG_AND_CONTINUE`: Conflict message is slightly technical, though clear enough to guide reload.
  - `STOP`: Tab B silently overwrites Tab A's publication, wipes Tab B's form immediately without warning, or returns an unhandled 500 crash.

### Step 3 — Reload effective version in Tab B, re-preview, and publish

- **User charter**: Recover from the `409` conflict by reloading the latest effective version, re-applying/previewing the candidate, and publishing.
- **Explore & act**: Use the reload/refresh action in Tab B to fetch Tab A's newly published version, make a valid edit, run Preview, and Confirm Publish.
- **Expected**:
  - Tab B loads the latest effective version from Tab A.
  - After running a fresh Preview, Confirm Publish succeeds and creates the next version without overwriting Tab A's history entry.
- **Decision rule**:
  - `CONTINUE`: Conflict recovery succeeds cleanly and history records both publications sequentially.
  - `LOG_AND_CONTINUE`: Manual page reload is needed if no dedicated reload button exists, provided recovery works cleanly.
  - `STOP`: Tab B remains stuck in error state after reload or fails to publish a fresh preview.

### Step 4 — Inspect a past version in History and restore it via new publication

- **User charter**: Inspect an earlier policy version from the publication history and restore its values as a new version.
- **Explore & act**: Select or expand an earlier version in the publication history panel, inspect its stored values, use the restore/load-into-candidate action (or copy its values into the candidate form), run Preview, and Confirm Publish.
- **Expected**:
  - Past version values are viewable without altering the currently active version.
  - Restoring requires a fresh Preview and Confirm Publish, creating a brand-new version in history while leaving all past history rows immutable.
- **Decision rule**:
  - `CONTINUE`: Past version values are restored via a new previewed publication and history remains immutable.
  - `LOG_AND_CONTINUE`: Restore requires manually copying values if no one-click "Load into candidate" button is provided.
  - `STOP`: Restoring mutates/overwrites a historical record or publishes without a fresh preview.

## 4. Evidence and Findings

- Capture a screenshot for every `LOG_AND_CONTINUE` or `STOP` outcome.
- A `LOG_AND_CONTINUE` record contains: problem description, reproduction steps, screenshot path, and (when confirmed defect) its `BUG-LOG.md` reference.
- Capture console or network output only when it materially helps classify or reproduce the finding.

## 5. Execution History

<!-- EXECUTION:START -->
- Latest status: PASS_WITH_FINDINGS
- Executed at: 2026-11-26T19:21:44+07:00
- Executed by: QA Agent (browsermcp)
- Browser tool: browsermcp
- Final step: Step 4
- Actual result: Tab A published 2026.11-v4.0; Tab B's stale publish against v3.0 returned 409 Conflict and rendered "Tải phiên bản mới nhất" to reload v4.0. History rows v1.0..v4.0 remained immutable.
- Findings: Minor UX finding — on 409 Conflict in Tab B, the confirmation modal backdrop remained open covering "Tải phiên bản mới nhất", requiring the user to click "Hủy" first before reloading.
- Evidence screenshots:
  - ppswbs-spec/features/009-administrative-parameter-configuration/test-results/screenshots/E2E-009-004--step2-409-conflict.jpg
- Bug references: NONE (minor UX observation)
<!-- EXECUTION:END -->
