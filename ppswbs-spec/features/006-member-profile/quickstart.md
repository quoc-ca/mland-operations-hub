# Member Profile — planned verification

Status: specification only. The implementation and executable verification tooling were removed at the user's request on 2026-10-19. The routes below describe the intended contract, not available functionality.

Use the [spec navigation table](spec.md#authentication-and-navigation) as the source of truth. The web/Figma prototype was subsequently synchronized for UX review. Its simulated behavior and earlier backend runtime results are not production acceptance evidence for this revision.

## Journey acceptance scenarios

The table below is a documentation walkthrough now and a required runtime test inventory for later implementation. T002 defines the checks; T004–T006 implement the behavior; T007–T008 execute/review evidence. All scenarios require safe credential/status checks; no policy requirement is inferred.

| ID | Input / action | Expected result | Requirements |
| --- | --- | --- | --- |
| AC-01 | Register a new Firebase email/password identity; then complete email verification. | Registration sends the initial email and shows verification. Backend-checked verified email plus an eligible recent credential permits session/bootstrap and navigation to `/`; no policy or automatic profile redirect. | FR-011–FR-015, SC-003 |
| AC-02 | Sign in with already verified email, for both ACTIVE and PENDING_POLICY_ACCEPTANCE. | Complete the session/bootstrap checks and go straight to `/`. No verification screen, email send or policy prompt. An already valid session also opens the profile without repeating sign-in. | FR-003, FR-011–FR-015, SC-003 |
| AC-03 | Sign in with unverified email after leaving registration unfinished; request recheck/resend. | Show verification, not home/profile. Page entry/sign-in/recheck does not resend. Only an explicit resend action sends another email. After verification, follow the same successful session/home path as AC-01. | FR-003, FR-011–FR-014, SC-003 |
| AC-04 | Sign in, open and save own profile with missing acceptance, outdated acceptance, changed policy versions or no current policy available. Repeat for both allowed statuses. | No policy lookup, prompt or acceptance mutation is required; sign-in still ends at `/`, and web GET/POST plus API GET/PUT profile work when otherwise valid. Missing policy is never a profile 403. No status conversion to ACTIVE or consent-history mutation occurs. | FR-003–FR-005, FR-010, FR-014–FR-016, SC-004 |
| AC-05 | An eligible, verified Member has no profile; open the page or API, then save valid fields. | GET is read-only with empty/null profile fields. First valid save creates one profile, not a new registration or Member. Distinguish this from a missing Member: API 409, then the UID-based session/bootstrap flow. | FR-002, FR-004, FR-008, FR-014, FR-016 |
| AC-06 | Missing/invalid/expired/revoked credential, SUSPENDED or other disallowed status, unverified email, or credential-validation outage. | No profile access. API uses 401 for credential failure, 403 for email/status, 409 for missing Member and 503 for credential-validation outage; web uses fixed recovery/authentication destinations. Policy absence cannot bypass these checks. | FR-001–FR-003, FR-012–FR-014, FR-016 |
| AC-07 | Verification completes but auth_time is older than five minutes, or credential no longer validates; retry with a fresh sign-in. Also check excessive future auth_time. | Do not grant a new session from the ineligible credential. Recover through sign-in and then `/`, without asking to verify an already verified email again. A still-valid existing session is not forced through fresh sign-in on every visit. Reject future auth_time beyond the specified 30-second tolerance. | FR-011–FR-013, FR-016, SC-003 |

## Remaining implementation checks

After implementation, exercise strict profile validation, ownership, CSRF, no-JS profile forms/PRG, failed-save draft preservation, concurrent first saves/last-commit wins, atomic audit, MySQL fresh/upgrade migrations and Spring Modulith verification as specified in [plan](plan.md) and [tasks](tasks.md). Web uses the eight-hour Firebase session cookie; API GET/PUT uses Bearer Firebase ID tokens.

Use Firebase Auth Emulator and an authorized disposable MySQL database, not production data/configuration. Prepare concrete commands and test tooling only when implementation is separately requested. [Historical evidence](verification.md#historical-evidence--removed-implementation) does not validate the current repository or revised journey.
