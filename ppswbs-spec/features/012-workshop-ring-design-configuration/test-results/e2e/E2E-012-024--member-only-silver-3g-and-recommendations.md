---
format_version: 2
id: E2E-012-024
feature: 012-workshop-ring-design-configuration
test_level: e2e
test_case_type: N
source_ref: spec.md#workshop-recommendation
target_url: http://localhost:5173/
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: ring-design-member-only-recommendations
  account_ref: E2E_MEMBER_001
  isolation_key: E2E-012-024-local
cleanup:
  required: false
  method: docker-seed-reset-on-restart
---

# E2E-012-024: Member-only rebaseline, S925 3g allowance, Silver Clay S999 disclosure, and TOO_RISKY recommendation

**Created**: 2026-11-26
**Updated**: 2026-11-26
**Owner**: QA Agent

## 1. Scenario Charter

- **User goal**: Verify the 2026-11-26 Member-only rebaseline (Guest local preview prompts sign-in on Save without creating a Guest draft), Signature Silver 925 3g allowance advisory surcharge, Silver Clay recommendation (>5 gemstones) with S999 disclosure without mutating saved S925, and `TOO_RISKY` recommendation behavior.
- **Starting context**: Local frontend and backend are running with seeded data. Browser starts as an anonymous Guest.
- **Scope**: Guest sign-in gate on protected actions, Member save/evaluate with S925 > 3g, Silver Clay S999 disclosure, `TOO_RISKY` consultation path, and verifying no `/10` suffix on difficulty scores.
- **Out of scope**: Admin policy editing (`E2E-009-*`), policy version immutability (`E2E-012-025`), and monkey/navigation chaos (`E2E-012-026`).

## 2. Preconditions and Test Data

- [ ] Required services in `health_urls` are ready.
- [ ] Seeded Member account (`E2E_MEMBER_001`) is available without hardcoding secrets here.
- [ ] Starting session: anonymous Guest.

## 3. Exploratory Step Contract

### Step 1 — Guest explores local preview and triggers sign-in on protected Save/Evaluate

- **User charter**: Open the workshop ring configurator as a Guest, adjust parameters locally, and attempt to save/evaluate.
- **Explore & act**: Navigate to `target_url`, find the ring configurator entry from the UI, change a material/shape/parameter in local preview, and click Save or Evaluate.
- **Expected**:
  - Guest can view catalogue options and see local preview clearly marked as preview.
  - Clicking Save/Evaluate opens the sign-in flow and does not create a Guest draft or expose private design/session data.
- **Decision rule**:
  - `CONTINUE`: Local preview works and protected action prompts sign-in without creating a Guest draft.
  - `LOG_AND_CONTINUE`: Minor wording issue on the sign-in prompt.
  - `STOP`: Guest can save/evaluate without signing in, or app crashes.

### Step 2 — Member configures S925 > 3g and > 5 gemstones, then saves/evaluates

- **User charter**: Sign in as Member, configure a Signature Silver 925 design exceeding 3g estimated mass and containing more than 5 gemstones (up to 10), then save/evaluate.
- **Explore & act**: Sign in as `E2E_MEMBER_001`, open the configurator, select Silver 925 (`S925`), set dimensions/size so estimated mass exceeds 3g, add 6+ gemstones (within max 10), and click Save/Evaluate.
- **Expected**:
  - The design is NOT blocked by the 3g allowance; UI displays an advisory excess-material surcharge/notice for mass above 3g.
  - Workshop recommendation suggests **Silver Clay** (`Recommended` badge + link to workshop detail + reason text) and explicitly discloses that the Silver Clay workshop uses **S999**, while the saved design's material remains **S925**.
  - General workshop discovery actions remain visible (not hidden by the recommendation).
  - Difficulty score is displayed without appending `/10`.
- **Decision rule**:
  - `CONTINUE`: S925 > 3g saves cleanly, Silver Clay + S999 disclosure renders accurately, and saved material stays S925.
  - `LOG_AND_CONTINUE`: Minor formatting quirk in the advisory surcharge text.
  - `STOP`: Design is blocked at 3g, S925 is silently mutated to S999, `/10` is appended to score, or general workshop discovery is hidden.

### Step 3 — Member evaluates a high-complexity design exceeding too-risky cutoff

- **User charter**: Configure a design whose difficulty score exceeds the too-risky cutoff (e.g., max gemstones, attachments, text/symbol/image engravings) and save/evaluate.
- **Explore & act**: Create or edit a design with high-difficulty options and engravings/images so the evaluated score exceeds the active too-risky cutoff, then Save/Evaluate.
- **Expected**:
  - Evaluation returns `TOO_RISKY` status with no workshop package recommendation.
  - UI explains the consultation/custom-design path without automatically creating a consultation/booking request and keeps general workshop discovery visible.
- **Decision rule**:
  - `CONTINUE`: `TOO_RISKY` renders with consultation explanation and no auto-created booking/request.
  - `LOG_AND_CONTINUE`: Minor copy issue on consultation guidance.
  - `STOP`: A workshop package is still recommended above too-risky cutoff, or an unintended booking/request record is created.

## 4. Evidence and Findings

- Capture a screenshot for every `LOG_AND_CONTINUE` or `STOP` outcome.
- A `LOG_AND_CONTINUE` record contains: problem description, reproduction steps, screenshot path, and (when confirmed defect) its `BUG-LOG.md` reference.
- Capture console or network output only when it materially helps classify or reproduce the finding.

## 5. Execution History

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-26T19:27:57+07:00
- Executed by: QA Agent (browsermcp)
- Browser tool: browsermcp
- Final step: Step 3
- Actual result: Guest Save redirected to /login?returnTo=... without creating a draft. Member saved S925 + 6 gemstones (DSG-b8a20bbb-c63f-4f52-88e2-f20881a493e1), receiving Silver Clay Workshop - 5g recommendation with S999 disclosure while keeping S925, no /10 suffix, and general workshop discovery visible. High-difficulty design (DSG-071cacc6-e0ae-4fdc-87e9-ce91d550b7ac) returned TOO_RISKY with "Tư vấn thiết kế" and "Khám phá các workshop" visible.
- Findings: NONE
- Evidence screenshots:
  - ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-024--step1-guest-configurator.jpg
  - ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-024--step2-silver-clay-s999.jpg
  - ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-024--step3-too-risky.jpg
- Bug references: NONE
<!-- EXECUTION:END -->
