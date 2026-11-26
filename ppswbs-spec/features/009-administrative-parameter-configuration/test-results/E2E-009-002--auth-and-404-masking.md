---
format_version: 2
id: E2E-009-002
feature: 009-administrative-parameter-configuration
test_level: e2e
test_case_type: A
source_ref: spec.md#actors-and-access
target_url: http://localhost:5173/admin/ring-difficulty
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: admin-ring-difficulty-auth-guard
  account_ref: E2E_GUEST_AND_NON_ADMIN
  isolation_key: E2E-009-002-local
cleanup:
  required: false
  method: docker-seed-reset-on-restart
---

# E2E-009-002: Non-Admin roles and Guests are blocked from Admin Ring Difficulty screen

**Created**: 2026-11-26
**Updated**: 2026-11-26
**Owner**: QA Agent

## 1. Scenario Charter

- **User goal**: Verify that unauthenticated Guests are redirected to sign-in recovery and authenticated non-Admin roles (Member, Manager, Staff) are masked to `/404` without leaking any protected policy values.
- **Starting context**: Local frontend and backend are running. Browser starts in an anonymous Guest session, then signs in as non-Admin roles.
- **Scope**: Route access control, `/404` masking for Member/Manager/Staff, and ensuring no policy numbers or history leak before or after role resolution.
- **Out of scope**: Valid Admin workflow (`E2E-009-001`) or backend-only direct curl fuzzing.

## 2. Preconditions and Test Data

- [ ] Required services in `health_urls` are ready.
- [ ] Seeded Member and Manager (or Staff) accounts are available without hardcoding secrets here.
- [ ] Starting session: anonymous Guest.

## 3. Exploratory Step Contract

### Step 1 — Guest accesses `/admin/ring-difficulty` directly

- **User charter**: Attempt to open the Admin ring difficulty URL while unauthenticated.
- **Explore & act**: Open `target_url` in an unauthenticated browser session and observe the resulting URL and rendered content.
- **Expected**:
  - Guest is redirected to the sign-in recovery flow (or shown an unauthorized/sign-in prompt).
  - No policy version, difficulty scores, cutoffs, or publication history are rendered at any point.
- **Decision rule**:
  - `CONTINUE`: Guest is blocked from viewing the admin screen and no protected policy data is visible.
  - `LOG_AND_CONTINUE`: Minor wording issue on the sign-in redirect banner.
  - `STOP`: Guest sees any policy values, scores, or history, or enters the admin screen without authentication.

### Step 2 — Authenticated Member accesses `/admin/ring-difficulty`

- **User charter**: Sign in as a regular Member and attempt to navigate to `/admin/ring-difficulty`.
- **Explore & act**: Sign in using the seeded Member account, then navigate to `target_url` and observe the rendered page and URL.
- **Expected**:
  - The browser masks the route to `/404` (or renders the 404 Not Found view) before loading policy data.
  - No policy values, scores, or admin navigation controls are exposed in the UI.
- **Decision rule**:
  - `CONTINUE`: Member is masked to `/404` with zero policy data exposed.
  - `LOG_AND_CONTINUE`: Page flashes a loading spinner briefly before `/404`, without rendering any protected values.
  - `STOP`: Member can view policy values, preview, or publish, or sees an unhandled crash instead of `/404`.

### Step 3 — Authenticated Manager accesses `/admin/ring-difficulty`

- **User charter**: Sign out Member, sign in as a Manager (who has catalogue permissions in other areas but must NOT access difficulty policy), and attempt to open `/admin/ring-difficulty`.
- **Explore & act**: Sign out, sign in with the seeded Manager account, and navigate to `target_url`.
- **Expected**:
  - Manager is masked to `/404` and denied access to ring difficulty configuration, preview, and publication.
  - No protected difficulty policy values or history are displayed.
- **Decision rule**:
  - `CONTINUE`: Manager is blocked via `/404` masking with no difficulty data exposed.
  - `LOG_AND_CONTINUE`: Minor UI navigation quirk on `/404` return link.
  - `STOP`: Manager can view, preview, or publish difficulty policy values.

## 4. Evidence and Findings

- Capture a screenshot for every `LOG_AND_CONTINUE` or `STOP` outcome.
- A `LOG_AND_CONTINUE` record contains: problem description, reproduction steps, screenshot path, and (when confirmed defect) its `BUG-LOG.md` reference.
- Capture console or network output only when it materially helps classify or reproduce the finding.

## 5. Execution History

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-26T19:31:25+07:00
- Executed by: QA Agent (browsermcp)
- Browser tool: browsermcp
- Final step: Step 3
- Actual result: Guest accessing /admin/ring-difficulty saw only sign-in recovery (/login?returnTo=...) with zero policy data. Authenticated Member (customer.test) and Manager (manager.test) were both masked to /404 ("Trang không tồn tại") with zero policy values exposed.
- Findings: NONE
- Evidence screenshots:
  - ppswbs-spec/features/009-administrative-parameter-configuration/test-results/screenshots/E2E-009-002--step1-guest-redirect.jpg
  - ppswbs-spec/features/009-administrative-parameter-configuration/test-results/screenshots/E2E-009-002--step2-member-404.jpg
  - ppswbs-spec/features/009-administrative-parameter-configuration/test-results/screenshots/E2E-009-002--step3-manager-404.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
