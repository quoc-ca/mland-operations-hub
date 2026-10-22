# Current status: specification only

Implementation, migration, UI and tests were removed at the user's request on 2026-10-19. The evidence below is historical evidence from the removed implementation; it does not verify the current tree or establish readiness for release. All implementation and runtime verification must be performed again if implementation is requested.

## Current revision

The user subsequently approved the distinct authentication journeys, fixed post-authentication destination `/` and removal of the policy gate for 003. Both ACTIVE and PENDING_POLICY_ACCEPTANCE permit the session/profile when other checks pass; SUSPENDED does not. Refer to the [current spec navigation table](spec.md#authentication-and-navigation) and [AC-01–AC-07](quickstart.md#journey-acceptance-scenarios).

The original revision task changed documentation only. A subsequent user request synchronized web/Figma with the revised journey; [prototype verification](../../../prototypes/003-member-profile/VERIFICATION.md) records those UX checks separately. Prototype checks are not production authentication/profile/database evidence. Production task completion markers remain unchanged.

Documentation verification for this revision: the SpecKit prerequisite command `./.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireSpec -RequireTasks -IncludeTasks`, run from the SpecKit root, resolved feature 003 successfully; no extension hooks were registered. Read-only cross-artifact analysis covered 18 FRs, four SCs and eight tasks, with 100% requirement coverage, no unmapped tasks, no constitution conflicts and no critical/high findings. The seven AC scenarios were walked through against the navigation/access rules; runtime execution remains pending. Static checks passed for all 26 local Markdown links/anchors, OpenAPI YAML, unchanged API structure after removing description fields, unchanged FR-004–FR-010, identical task markers and identical historical content below the separator. `git diff --check` passed. No backend or prototype test result is claimed.

## Historical evidence — removed implementation

Everything below the separator is preserved from the previous implementation, including its old policy-gate behavior, commands, task references and completion statements. Those statements are historical, not current status. Some referenced scripts/tests were removed, and historical references to T009 or runnable commands in quickstart do not describe the current task list/quickstart.

---

# Verification Evidence
Feature: 003-member-profile. Results are recorded from fresh execution, not inferred from existing checkboxes.
## Design gate
User approval: explicit implementation request in this conversation, 2026-10-19.
Operational design kit read and checked. No reviewer-owned checklists created or changed.
**Final acceptance status: CHƯA HOÀN TẤT — MySQL verification pending.** Application implementation is present; the database gate is not waived.

Read-only SpecKit prerequisite command passed with FEATURE_DIR=features/003-member-profile. No extensions.yml hooks exist. Cross-artifact analysis: 18 functional requirements + 4 success criteria, 8 tasks, 100% requirement coverage, 0 critical/high findings, 0 unmapped tasks, 0 constitution conflicts. Requirement coverage: FR-001–003 T002/T004/T005; FR-004–009 T002/T003/T005; FR-010–013 T002/T004/T006; FR-014–016 T002/T004/T005/T006; FR-017 T003/T005; FR-018 T002/T004/T005/T006; SC-001 T001/T007/T008, SC-002–003 T007, SC-004 T006/T008. No reviewer checklist markers were changed.

## Fresh execution — 2026-10-19

| Check | Command / scope | Observed result |
| --- | --- | --- |
| Clean build and full current-source suite | From backend: `mvn.cmd clean test -q -Ddebug=false -Dlogging.level.root=WARN -Dlogging.level.org.springframework=WARN`, with `PROFILE_FIREBASE_EMULATOR=true`, `FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099` | Exit 0; **48 tests discovered, 43 passed, 0 failures, 0 errors, 5 MySQL tests skipped**. Fourteen current test classes. Includes existing Member/auth regression and Spring Modulith verification. |
| Profile API/web | `MemberProfileIT` | 16 passed: ownership, missing profile without writes, Unicode, exact/duplicate JSON fields, phone/clear, last save, access gates, CSRF, policy change/acceptance, cookie and DEBUG log redaction. |
| Persistence, concurrency and rollback | `MemberProfilePersistenceIT` | 4 passed on H2: simultaneous first saves, last save, atomic audit rollback, bootstrap idempotence/no email merge/suspension, concurrent UID initialization. H2 is supplemental. |
| SDK boundary | `ProfileIdentityGatewayTest` | 2 passed: both credential verification methods request revocation checks; safe invalid/unavailable provider outcomes. |
| Real provider simulator | `FirebaseProfileEmulatorIT` | 1 passed against running Auth Emulator: real email/password registration, verification action, new ID token, Firebase cookie with exp–iat exactly 28,800 seconds, cookie and ID-token revocation. Emulator does not prove production cryptographic/project integration. |
| Real HTTP, no-JS forms | From root: `python -X utf8 ppswbs-spec/features/003-member-profile/emulator/profile-http-smoke.py` | Passed: Emulator identity → actual app cookie → SSR policy form → SSR profile form → API read → logout. No JS/browser is used by the form runner. Secure cookies are explicitly forwarded only to the fixed loopback HTTP app. |
| JS/static contract | `node --check .../member-profile-auth.js`; Python YAML/Markdown/template checks | Passed: OpenAPI YAML, strict body contract, 18 FRs, local links, UTF-8 markup, no script on profile/policy pages. |
| Scope preservation | `git diff --check`; diff restricted to V1–V3 migrations, workshopbooking and designreview production directories | Passed; those existing migrations and business code unchanged. No production Firebase configuration or deployment. |

The first non-clean run found obsolete compiled tests in target. Its earlier count is intentionally superseded by the clean-run results above. The clean plugin initially lacked a cached dependency; the approved network-enabled rerun succeeded.

## Fixes verified during development

- Request URI handling works with both real servlet requests and MockMvc.
- The new name storage reserves 200 UTF-16 units for portability while enforcing the approved 100 Unicode code points; no change to the business limit.
- Profile and session raw bodies bypass Spring converter DEBUG string logging; identity/input/response log representations are redacted. A DEBUG capture test confirms no profile values appear.
- Bootstrap UID collisions/gap-lock conflicts retry in a fresh transaction, bounded to three attempts, without reactivating suspended Members.
- Firebase Admin reads the emulator **environment variable**, not the Java system property. Test/manual commands set the environment explicitly; existing global Firebase configuration was not rewritten.

## Review and remaining gates

Evidence-code-review and security review were limited to the changed feature, approved requirements and local test environment. Checked UID ownership, token/cookie boundaries, CSRF, typed failures, strict input, no secret/profile-value logging, minimal atomic audit, module boundaries, migration preservation and scope. No remaining confirmed critical/high implementation finding; **SC-002 remains unverified on MySQL**.

Verification exception: MySQL 8.4 was not available. The request to create the dedicated local test container was rejected; the tool supplied no detailed reason. No container was created and no alternate database/reset was used to bypass that decision.

Manual verification: inspected the new schema/locking code and provided guarded MySQL migration/upgrade and persistence/concurrency/rollback tests. These checks are prepared but all five are skipped without `PROFILE_MYSQL_URL`; H2 and source review do not replace the MySQL gate. Run the two commands in [quickstart](quickstart.md) on the disposable database before closing T007/T009.

Verification exception: browser UI inspection was unavailable: the browser tool reported no IAB and an empty browser inventory.

Manual verification: actual rendered HTML/forms were exercised via the local HTTP runner, and MockMvc verified escaping, CSRF and no-JS submission. Responsive CSS/labels were statically reviewed. No screenshot, cross-browser, visual accessibility conformance or production TLS/SameSite claim is made.

## Convergence

FR-001–FR-018 implementation is present. SC-003 and SC-004 have the evidence above; SC-002 and the full verification portion of SC-001 require the MySQL gate. Remaining work is external verification, not permission to redesign booking/ring logic or to deploy. See [blocker report](blocker-report.md) and [tasks](tasks.md).
