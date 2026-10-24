# Feature Specification: Member Profile
**Feature**: 001-account-management | **Date**: 2026-10-19
**Status**: Approved scope/design recorded from the user's explicit “PLEASE IMPLEMENT THIS PLAN” instruction in this conversation on 2026-10-19. Approval is limited to this feature, plan and tasks; it does not ratify any other feature.
**Revision**: The user approved the revision that sends authentication to the home page and does not block the profile by policy acceptance. This revision supersedes the earlier combined authentication/verification/policy journey; implementation remains pending.
## Scope and source alignment
Constitution 2.0.0 applies. Report 3 Manage Personal Profile and User Profile supply the business context. Firebase owns credentials and email verification; MySQL owns Member status, policy records and profile. Policy acceptance is not a prerequisite for 003. Booking, ring design, policy prompts in other features and implementation of the home page are outside scope.

## Authentication and navigation

This table is the authoritative journey reference for [plan](plan.md), [tasks](tasks.md) and [verification scenarios](quickstart.md). Checking a credential or its verified-email claim at the backend is automatic; it is not another user-facing email-verification step or MFA.

| Situation | Required result |
| --- | --- |
| New email/password registration | Create the Firebase identity, send the initial verification email and show the verification state of the Member Auth page. Do not open the profile yet. |
| Sign-in with an unverified email | Show the unfinished email-verification step. Do not resend automatically; resend only on the user's explicit request. |
| Sign-in with an already verified email | Complete backend credential/status checks and the session/bootstrap flow, then navigate to `/`. No verification screen or policy gate. |
| Email verification completed | Refresh/reload Firebase identity state and obtain a current ID token. Backend checks verified email; establish the session and navigate to `/` when eligible. A browser-supplied flag cannot grant access. |
| Credential no longer eligible for a new session | Ask the user to sign in again, then navigate to `/` on success. Do not make them verify an already verified email again. |
| Open the profile with a valid session | Check the credential and local Member eligibility, then show the profile; no sign-in, email-verification or policy step is repeated. A missing profile shows empty fields. |
| Missing/invalid/revoked credential, suspended account or credential-validation service failure | Keep the safe denial/recovery behavior in FR-013/016. Neither email verification nor lack of policy acceptance bypasses those checks. |

`/` is the fixed post-authentication destination, not an instruction to implement the home page. Authentication never automatically redirects to the profile. A protected web request without a usable session returns to the fixed sign-in page; successful authentication still ends at `/`. API failures remain typed JSON responses, not browser redirects.

## User scenarios
US1: A Member with verified email and an allowed local status reads their own profile, regardless of policy acceptance; missing profile returns null fields without writes.
US2: The Member saves a Unicode full name and optional international contact phone. The last successfully committed valid save replaces both fields. Failed validation/save leaves accepted data intact.
US3: A new registrant completes the registration and verification journey in the navigation table, reaches the home page and can subsequently open/save the server-rendered profile without JavaScript.
US4: Missing/invalid/revoked authentication, unverified email, suspension or a missing local Member cannot grant profile access. Safe UI recovery and typed API errors are supplied; policy state is not a denial reason.
US5: A returning user with verified email signs in and reaches `/` without repeating email verification or accepting policy.
US6: A user who left registration unfinished signs in, resumes verification without an automatic resend, and reaches `/` after successful verification/session establishment.
## Functional Requirements
- FR-001: GET/PUT /api/v1/members/me/profile require backend-validated Bearer Firebase ID tokens, including revocation checks. Cookie alone never authenticates the API.
- FR-002: Resolve ownership only from verified UID and local Member; never client account IDs/email, provider role claims or automatic email merging.
- FR-003: Before reading/writing profile, require nonempty backend-checked verified email and a local Member in ACTIVE or PENDING_POLICY_ACCEPTANCE. Deny SUSPENDED and any status outside this allowlist. Missing, outdated or unaccepted policy is not a profile access condition; this allowance grants no rights in other features.
- FR-004: GET returns fullName, phone, email, emailVerified, updatedAt; absent profile returns null profile fields and performs no persistence writes.
- FR-005: PUT requires exactly fullName and phone keys, replacing both; reject additional/missing/wrong-type fields with 400. No role, status, password, UID, email or verification updates.
- FR-006: Strip outer Unicode whitespace from fullName, preserve accents/case/internal spacing; require 1–100 Unicode code points and no ISO control characters.
- FR-007: Blank phone becomes null; nonblank phone is not reformatted and matches ^\\+[1-9]\\d{1,14}$. No OTP, identity proof or uniqueness requirement.
- FR-008: Serialize valid profile writes on the owning Member row; last committed save wins. No version/ETag conflict gate. Concurrent first saves create only one profile.
- FR-009: Persist profile and minimal successful-update audit atomically. Audit includes Member ID, changed-field names, action, time, outcome, correlation; no full names, phones, passwords or tokens.
- FR-010: Web GET/POST /member/profile use Firebase session cookies, CSRF-protected writes, server-side validation and post/redirect/get on success. Validation errors retain escaped submitted data.
- FR-011: The Member Auth entry supports distinct Firebase email/password registration, sign-in and unfinished verification states according to the navigation table. After the initial registration email, resend is user-initiated only. Already verified users skip the verification state; all successful session flows end at `/`, not the profile or policy. No password reaches Mland. JS is limited to this authentication page under the ratified Member Auth exception.
- FR-012: POST /member-auth/profile-session verifies ID token, verified email and auth_time no older than 5 minutes, checks Member eligibility via FR-014, then creates a fixed eight-hour Firebase cookie with HttpOnly, Secure, SameSite=Lax and Path=/; no sliding expiry. Reject future auth_time beyond clock tolerance (30 seconds). Recent authentication is a new-session condition, not a request to reverify email or renew a valid session on each profile visit.
- FR-013: Verify cookie revocation and local status on protected web requests. Logout clears this profile session cookie via CSRF-protected POST; it does not change unrelated auth flows.
- FR-014: After credential/email verification, session bootstrap idempotently creates local Member by UID only, retaining existing account/policy state. ACTIVE and PENDING_POLICY_ACCEPTANCE are eligible for the 003 session/profile; never reactivate suspended Member or merge by email. No policy acceptance or account-status transition is needed for this allowance.
- FR-015: Policy acceptance must not gate sign-in, the post-authentication home redirect or profile reads/writes. Do not require a policy lookup, prompt or acceptance mutation for these operations, even when policy versions change or no current policy is available. Dedicated profile policy pages/routes are outside 003. Other features own any necessary policy prompts/acceptance rules; their behavior and existing policy history are unchanged.
- FR-016: API returns 400 validation, 401 absent/invalid/expired/revoked credential, 403 unverified/missing email or disallowed Member status, 409 MEMBER_INITIALIZATION_REQUIRED for missing local Member. Credential-validation service outage returns safe 503 without granting access. Web recovery uses fixed local authentication states/routes; success navigates only to `/`. No arbitrary returnTo or policy redirect is accepted.
- FR-017: Add a versioned member_profiles migration with Member PK/FK and timestamps; never edit existing migrations or reset existing data. Scope HTTP security to the new profile/API/auth routes.
- FR-018: Keep public profile responses/private pages non-cacheable; no cross-domain facade/entity leakage, credentials in logs or unsupported fields silently accepted.
## Success Criteria
SC-001: Every FR has tasks and fresh verification evidence; tests cover successful and denied paths.
SC-002: Empty/upgrade MySQL migration tests preserve preexisting members/policies and create profile PK/FK correctly.
SC-003: A real Firebase Auth Emulator smoke exercises the distinct registration, verified sign-in and unfinished-verification journeys, ID tokens and session cookies/revocation. Verify the fixed home redirect, user-initiated resend and credential-too-old recovery; controlled verifier tests cover denied paths.
SC-004: A rendered profile form can submit without JS after Firebase authentication, with CSRF, validation feedback and safe PRG. Profile access/save works for both allowed statuses without policy checks or prompts.
## Boundaries and assumptions
No changes to booking/design, production Firebase setup, roles/RBAC or unrelated authentication/entitlement flows. Existing Firebase support is assessed for compatibility, not assumed correct. API first entry for a missing Member is the session/bootstrap flow. Uninitialized/new profiles may show empty names; the name becomes mandatory on save. Cookie Secure is not disabled for normal runtime; local manual checks use HTTPS or browser localhost secure-cookie support. Initial languages are Vietnamese/English using the existing message mechanism.

**Prototype alignment**: The [web prototype and Figma handoff](../../../prototypes/003-member-profile/FIGMA-HANDOFF.md) were subsequently synchronized at the user's request: distinct authentication journeys, a minimal home destination and no profile policy gate. They are UX review artifacts, not production authentication/database evidence; this spec remains the implementation source.
