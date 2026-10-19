# Feature Specification: Member Profile
**Feature**: 003-member-profile | **Date**: 2026-10-19
**Status**: Approved scope/design recorded from the user's explicit “PLEASE IMPLEMENT THIS PLAN” instruction in this conversation on 2026-10-19. Approval is limited to this feature, plan and tasks; it does not ratify any other feature.
## Scope and source alignment
User-approved plan is the detailed source. Constitution 2.0.0 applies. Report 3 Manage Personal Profile and User Profile supply the business context. Firebase owns credentials and email verification; MySQL owns Member status, policy acceptance and profile. Booking and ring-design logic is excluded and cannot be used as a requirements source.
## User scenarios
US1: A verified active Member accepting current policies reads their own profile; missing profile returns null fields without writes.
US2: The Member saves a Unicode full name and optional international contact phone. The last successfully committed valid save replaces both fields. Failed validation/save leaves accepted data intact.
US3: A browser signs in/registers using Firebase, verifies email, exchanges a recent ID token for an eight-hour Firebase cookie, accepts current policies, then views/saves the server-rendered profile without JavaScript.
US4: Missing/invalid/revoked authentication, unverified email, suspension, missing local Member or outdated policies cannot grant profile access. Safe UI recovery and typed API errors are supplied.
## Functional Requirements
- FR-001: GET/PUT /api/v1/members/me/profile require verified Bearer Firebase ID tokens, including revocation checks. Cookie alone never authenticates the API.
- FR-002: Resolve ownership only from verified UID and local Member; never client account IDs/email, provider role claims or automatic email merging.
- FR-003: Require nonempty verified email, ACTIVE local Member and acceptance of the currently effective Terms/Privacy before reading/writing profile.
- FR-004: GET returns fullName, phone, email, emailVerified, updatedAt; absent profile returns null profile fields and performs no persistence writes.
- FR-005: PUT requires exactly fullName and phone keys, replacing both; reject additional/missing/wrong-type fields with 400. No role, status, password, UID, email or verification updates.
- FR-006: Strip outer Unicode whitespace from fullName, preserve accents/case/internal spacing; require 1–100 Unicode code points and no ISO control characters.
- FR-007: Blank phone becomes null; nonblank phone is not reformatted and matches ^\\+[1-9]\\d{1,14}$. No OTP, identity proof or uniqueness requirement.
- FR-008: Serialize valid profile writes on the owning Member row; last committed save wins. No version/ETag conflict gate. Concurrent first saves create only one profile.
- FR-009: Persist profile and minimal successful-update audit atomically. Audit includes Member ID, changed-field names, action, time, outcome, correlation; no full names, phones, passwords or tokens.
- FR-010: Web GET/POST /member/profile use Firebase session cookies, CSRF-protected writes, server-side validation and post/redirect/get on success. Validation errors retain escaped submitted data.
- FR-011: A profile-specific Member Auth entry page supports Firebase email/password registration, login, verification resend/recheck; no password reaches Mland. JS is limited to this authentication page under the ratified Member Auth exception.
- FR-012: POST /member-auth/profile-session verifies ID token, email and auth_time no older than 5 minutes, then creates a fixed eight-hour Firebase cookie with HttpOnly, Secure, SameSite=Lax and Path=/; no sliding expiry. Reject future auth_time beyond clock tolerance (30 seconds).
- FR-013: Verify cookie revocation and local status on protected web requests. Logout clears this profile session cookie via CSRF-protected POST; it does not change unrelated auth flows.
- FR-014: Profile session bootstrap idempotently creates local Member by UID only, retaining existing account/policy state; never reactivate suspended Member or merge by email.
- FR-015: Before profile, dedicated server-rendered policy GET/POST accepts the exact current effective Terms and Privacy versions; repeated acceptance is idempotent; changed versions require redisplay/reconfirmation. No effective required policy is a closed failure, not implicit consent.
- FR-016: API returns 400 validation, 401 absent/invalid/expired/revoked credential, 403 email/state/policy gates, 409 MEMBER_INITIALIZATION_REQUIRED for missing local Member. Provider-verification outage returns safe 503 without granting access. Web redirects only to fixed local authentication/policy routes.
- FR-017: Add a versioned member_profiles migration with Member PK/FK and timestamps; never edit existing migrations or reset existing data. Scope HTTP security to the new profile/API/auth routes.
- FR-018: Keep public profile responses/private pages non-cacheable; no cross-domain facade/entity leakage, credentials in logs or unsupported fields silently accepted.
## Success Criteria
SC-001: Every FR has tasks and fresh verification evidence; tests cover successful and denied paths.
SC-002: Empty/upgrade MySQL migration tests preserve preexisting members/policies and create profile PK/FK correctly.
SC-003: A real Firebase Auth Emulator smoke exercises registration, verification, ID token and session cookies/revocation; controlled verifier tests cover adversarial gate paths.
SC-004: A rendered profile/policy form can submit without JS after Firebase authentication, with CSRF, validation feedback and safe PRG.
## Boundaries and assumptions
No changes to booking/design, production Firebase setup, roles/RBAC or global authentication contracts. Existing Firebase support is assessed for compatibility, not assumed correct. API first entry for a missing Member is the session/bootstrap flow. Uninitialized/new profiles may show empty names; the name becomes mandatory on save. Login to the dedicated page may require fresh Firebase authentication. Cookie Secure is not disabled for normal runtime; local manual checks use HTTPS or browser localhost secure-cookie support. Initial languages are Vietnamese/English using the existing message mechanism.
