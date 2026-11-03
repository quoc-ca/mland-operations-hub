# Verification Report: Account Management (FE-001)

**Date created**: 2026-11-03
**Date updated**: 2026-11-03
**Author**: Phan Sơn
**Last updated by**: Phan Sơn (Tech Lead approved baseline)

**Report status**: DESIGN VERIFIED — IMPLEMENTATION EVIDENCE PENDING

---

## 1. Scope & Verification Approach

This verification report defines the mandatory acceptance criteria, verification methods, and evidence retention requirements for Feature 001 Account Management.
Acceptance is split into two distinct tiers:
1. **Design-Level Verification** (performed & verified against artifacts before implementation handoff)
2. **Implementation-Level Verification** (must be executed locally by the implementation persona to produce execution logs and test screenshots before sign-off)

> [!WARNING]
> **Phase 1 Prerequisite Warning (T003 & T005 Status)**:
> In `tasks.md`, foundational tasks **T003** (Flyway fresh & upgrade schema validation on MySQL) and **T005** (Frontend dev server verification) are currently marked `[ ]` (NOT DONE). Although Phase 2 tasks are marked `[x]`, the implementation persona **MUST NOT** consider Phase 2 fully validated until T003 and T005 execution evidence is retained in Section 3 of this document.

---

## 2. Quickstart Scenarios Verification Matrix (V-01 → V-09)

| Scenario ID | Name & Target | Requirements Covered | Verification Type | Current Status | Acceptance Criteria Summary | Task Refs |
|---|---|---|---|---|---|---|
| **V-01** | Email/Password Registration & Bootstrap | FR-001, FR-002, FR-003, FR-007, FR-034, SC-001 | Implementation (UI + API) | **NOT EXECUTED** | 1 Account + 1 Profile created; role=MEMBER, status=ACTIVE; full_name from email local part; repeated bootstrap is idempotent. | T013, T014, T015, T016, T017, T018 |
| **V-02** | Unverified Email Sign-in & Profile Gate | FR-004, FR-005, FR-006, SC-001 | Implementation (UI + API) | **NOT EXECUTED** | Non-blocking reminder on Home; Profile save disabled until reload + fresh bearer + `PUT /accounts/me/email` succeeds; 90s cooldown in `localStorage` by Account ID; cross-device sync works without webhook/poll. | T021, T022, T024, T051, T052 |
| **V-03** | Profile Save & Verified Email Sync | FR-006, FR-007, FR-008, SC-002 | Implementation (UI + API) | **NOT EXECUTED** | Valid name (1-100 chars, no control chars) & phone (7-30 chars) saved; no role/status change; `verifyBeforeUpdateEmail` retains old email until verified. | T020, T021, T022, T023, T024 |
| **V-04** | Admin Internal Account Provisioning & Activation | FR-011, FR-012, FR-013, FR-014, FR-015, SC-003 | Implementation (UI + API) | **NOT EXECUTED** | Creates PENDING_SETUP Account; STAFF has 1 location, MANAGER has 1+; Idempotency-Key returns preserved result; changed body gives 409; intended identity activation requires verified email & active scope; 5-min invitation cooldown. | T025, T026, T027, T028, T029, T031, T032 |
| **V-05** | Account Suspension & Restoration Guard | FR-019, FR-020, FR-021, SC-004 | Implementation (API + UI) | **NOT EXECUTED** | Mandatory reason; protected access denied immediately (403); restore blocked (409) until Firebase revocation confirmed; restored account requires fresh sign-in. | T033, T034, T035, T036, T037 |
| **V-06** | Admin Account List, Filter & Role Catalogue | FR-009, FR-010, FR-016, SC-006 | Implementation (UI + API) | **NOT EXECUTED** | Paginated accounts search/filter; recent updates first; no credentials/UIDs exposed; static role catalogue (MEMBER, STAFF, MANAGER, ADMIN) without edit controls. | T038, T039, T040, T041, T042, T043 |
| **V-07** | Workshop Consumer Facade (Facts & Roster) | FR-028, FR-029, FR-030, FR-031, FR-032 | Integration Test (Java Module Boundary) | **NOT EXECUTED** | `findEligibleRoster` returns only ACTIVE candidates with effective `activeWorkshopLocationIds`; `findTargetFacts` returns FOUND, NOT_FOUND, or UNAVAILABLE; UNAVAILABLE fails closed (503) without mutation. | T010, T030 |
| **V-08** | Shared Identity Gate & Security Boundaries | FR-026, FR-027, WF-01, WF-06 | Integration Test / HTTP | **NOT EXECUTED** | Missing/invalid/revoked bearer → 401; suspended account → 403; non-localhost origin (`http://localhost:3000`) → CORS blocked; gate resolves minimal actor without Workshop scope. | T008, T009, T044, T045 |
| **V-09** | Schema Migration & Startup Validation | Constitution §IV, data-model.md | DB / Backend Startup | **NOT EXECUTED** | `spring.jpa.hibernate.ddl-auto=validate` passes on fresh DB (V1→latest) and upgrade DB (V5 baseline→latest); `AuditOutcome` mapped as `VARCHAR(16)`. | T001, T002, T003 |

---

## 3. Design-Level Evidence (Verified Pre-Implementation)

The following items have been statically validated across design artifacts (`spec.md`, `plan.md`, `data-model.md`, `contracts/`, `design/`):

| Area | Verified Fact | Status | Source / Evidence |
|---|---|---|---|
| **Contract Alignment** | Full OpenAPI 3.1 YAML regenerated covering all approved routes, DTOs, parameters, and error responses. | **PASS** | `contracts/account-management.openapi.yaml` |
| **Module Boundaries** | `CurrentAccountActor` minimal context (accountId, role, status, emailVerified) isolates admission from Workshop scope. | **PASS** | `contracts/interfaces.md`, `CurrentAccountActor.java` |
| **Consumer Facade Contract** | Named sealed types `TargetFactsOutcome` (FOUND/NOT_FOUND/UNAVAILABLE) and `RosterOutcome` (AVAILABLE/UNAVAILABLE) formally defined. | **PASS** | `contracts/interfaces.md`, `ppswbs_backend/.../facade/` |
| **Data Invariants** | Unverified email bootstrap creates Account+Profile; missing email bootstrap creates neither; initial name = email local part; unique `firebase_uid`. | **PASS** | `data-model.md`, `research.md` (D-02, D-03, D-04) |
| **Audit Outcome Baseline** | Option B selected: `AuditOutcome` Java enum (`SUCCESS`, `FAILURE`) with `VARCHAR(16)` column mapping via append-only Flyway migration. | **PASS** | `data-model.md` §Audit outcome storage contract, `AuditOutcome.java` |
| **Error Contract** | Shared error envelope `{ code, message, correlationId, timestamp, fieldErrors? }` implemented in `AccountExceptionHandler.java`. | **PASS** | `AccountExceptionHandler.java`, `ErrorResponse.java` |
| **CORS & Transport** | Local CORS exact origin `http://localhost:5173`; `Access-Control-Allow-Credentials: false`; bearer-only transport. | **PASS** | `SecurityConfiguration.java`, plan.md |
| **WF-08 Email Gate Design** | Firebase-default verification handler; no custom Mland callback/webhook/job; 90-second Account-ID-keyed `localStorage` cooldown; `verifyBeforeUpdateEmail`. | **PASS** | `design/workflows.md` WF-08, `spec.md` FR-005/006 |

---

## 4. Implementation Evidence Log (Placeholders for Persona Triển khai)

*To be completed by the implementation persona during and after execution:*

### 4.1 Foundational Evidence (Phase 1 & 2)
- [ ] **T003 Fresh Database Startup**:
  - Command: `mvn test -Dtest=AccountManagementStartupFreshIT` (or manual startup log)
  - Result: *[Paste log timestamp & outcome]*
- [ ] **T003 Upgrade Path Startup (V5 → Latest)**:
  - Command: `mvn test -Dtest=AccountManagementStartupUpgradeIT`
  - Result: *[Paste verification query showing VARCHAR(16) column]*
- [ ] **T005 Frontend Dev Server**:
  - Command: `npm run dev` in `ppswbs_frontend/`
  - Result: *[Confirm server starts at http://localhost:5173]*
- [ ] **T011 Spring Modulith Module Detection**:
  - Command: `mvn test -Dtest=ApplicationModulesTest`
  - Result: *[Verify accountmanagement module verified without circular dependencies]*

### 4.2 User Story Evidence (Phase 3–7)
- [ ] **V-01 (Bootstrap)**: Execution log / screenshot of `/accounts/me` response
- [ ] **V-02 (Verification Gate & WF-08)**:
  - Same-browser verification and reload icon test output
  - Cross-device verification test output
  - 90-second cooldown verification in `localStorage`
- [ ] **V-03 (Profile & Email Update)**:
  - Profile update validation error check
  - `verifyBeforeUpdateEmail` flow trace
- [ ] **V-04 (Admin Provisioning & Idempotency)**:
  - Idempotency-Key retry log (same key/body -> 200 preserved; different body -> 409)
  - Invitation resend cooldown log
- [ ] **V-05 (Suspension & Invalidation)**:
  - Immediate 403 on next request log
  - Restoration blocked prior to revocation confirmation log
- [ ] **V-06 (Admin Search & Role Catalogue)**:
  - Query filtering response sample
  - Role catalogue read-only payload

### 4.3 Polish & Accessibility (Phase 8)
- [ ] **T047 Accessibility Scan (WCAG 2.2 AA)**:
  - Tool used: Axe-core / Lighthouse
  - Browsers: Chrome (latest), Edge (latest), Firefox (latest)
  - Result: *[0 critical violations]*

---

## 5. Sign-Off Conditions

Do **NOT** sign off FE-001 or hand off to production until:
1. All items in Section 4.1 (Foundational Evidence) are marked passed with attached terminal outputs.
2. Quickstart scenarios V-01 through V-09 have recorded positive execution outcomes.
3. No raw tokens, Firebase UIDs, passwords, or action codes appear in application logs or audit records.
4. `approval.md` is confirmed by Tech Lead Phan Sơn.
