# Feature Specification: Account Management

**Date created**: 2026-10-19
**Date updated**: 2026-10-26
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Feature Branch**: `son`

**Status**: Draft — business decisions clarified; planning, contract, and task artifacts must be regenerated before implementation.

**Input**: Replace the obsolete Member Profile scope with FE-001 Account Management, using `research.md` and the decisions recorded during the 2026-10-24–2026-10-25 clarification.

## Purpose and scope

FE-001 provides the account lifecycle for Mland: registration and sign-in, profile and credential self-service, and administration of internal Staff and Manager accounts. It provides a stable identity, role, status, location scope, and audit boundary for other business features.

The feature serves a small-to-medium business. It deliberately uses fixed roles and a small number of account states rather than configurable roles, permission rules, or complex account-linking flows.

### In scope

- Email/password registration and sign-in; Google and Facebook sign-in when those providers are configured.
- Sign-out, email verification, password reset, password change, and login-email change.
- Account bootstrap, own-profile management, and email synchronization after a verified email change.
- Admin list/detail, internal-account creation, invitation resend, internal profile edit, role/location assignment, account suspension/restoration, and fixed-role reference.
- Account lifecycle audit, safe retry/recovery, and notification requests to FE-07.

### Out of scope

- Dynamic RBAC: creating roles, changing permissions, or assigning multiple roles.
- A web UI/API to create, change, suspend, or restore an Admin account. Admin bootstrap is an operations process outside this feature.
- Automatic merging/linking of accounts that merely share an email address.
- Dashboard content, Home-page implementation, frontend application scaffolding, production deployment, and production identity-provider setup.
- Direct email delivery; FE-07 owns delivery after Account records a notification request.

## Clarifications

### Session 2026-10-26

- Q: Bạn chốt dùng một cổng bảo mật chung do FE-001 sở hữu cho toàn backend không? → A: Một cổng chung xác minh bearer/revocation, nạp actor hiện hành, mặc định từ chối route không khai báo; từng feature tự quyết quyền nghiệp vụ từ actor.
- Q: Bạn có xác nhận các giá trị database mặc định đã commit phải được coi là credential đã lộ và cần xoay vòng không? → A: Các giá trị đó là dữ liệu giả, chưa từng dùng hoặc reuse; phải bỏ khỏi cấu hình versioned nhưng không cần xoay vòng incident.
- Q: Bạn có chốt quy ước Flyway chung cho toàn repo là version số nguyên tăng dần không? → A: Dùng một dãy số nguyên tăng dần chung; owner kiểm tra/reserve số cao nhất trước khi tạo migration, và chỉ migration chưa merge/chưa áp dụng mới được đổi số.
- Q: Bạn có chốt FE-001 chỉ công khai một actor context tối thiểu cho feature khác không? → A: Công khai accountId opaque, role, status, emailVerified và activeLocationIds; UID/token ở lại identity gate, còn mỗi feature tự map context sang quyền nghiệp vụ của mình.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Register and use a personal account (Priority: P1)

A customer creates or signs in to a personal account through an allowed identity method. The system recognizes the person by their verified identity rather than by a client-supplied email or role. The customer reaches Home after sign-in and can complete the self-service actions for which they meet the requirements.

**Why this priority**: Account access is the entry point for every customer journey that needs a known person.

**Independent Test**: A new and a returning customer can register/sign in, reach Home, and repeatedly bootstrap the same account without receiving a duplicate account or another person's data.

**Acceptance Scenarios**:

1. **Given** a person completes an allowed registration or sign-in, **When** their identity is verified, **Then** the system creates or returns exactly one account for that identity and sends them to Home.
2. **Given** the same verified identity retries account bootstrap after a lost response, **When** the request is repeated, **Then** the system returns the existing account without changing its role, status, or business history.
3. **Given** a different identity has the same-looking email as an existing account, **When** it signs in, **Then** the system does not merge, link, or grant access to the existing account and gives safe recovery guidance.

---

### User Story 2 - Complete account verification and self-service (Priority: P1)

A signed-in person can see that their email still needs verification, verify it explicitly, manage their own profile, and use supported credential recovery/change flows without the business system ever receiving their password.

**Why this priority**: Customers need a safe path to recover access and maintain the personal information used by later features.

**Independent Test**: A customer with an unverified email can sign in and reach Home but is prevented from using verification-required profile functions until verification completes; a verified customer can update their profile and credential-related information safely.

**Acceptance Scenarios**:

1. **Given** a signed-in person whose email is not verified, **When** they open Home, **Then** they see a non-blocking verification reminder and can explicitly request another verification email.
2. **Given** that person attempts a function that requires a verified email, **When** verification is still incomplete, **Then** the function is denied without losing the entered form data and the person is guided to verification.
3. **Given** a verified active account, **When** its owner saves a valid full name and optional phone number, **Then** the account shows the newly saved values and no role, status, email, or location changes.
4. **Given** an owner completes an identity-provider email change and returns with a current verified identity, **When** the system synchronizes the account, **Then** it records the current email for the same account and never accepts an email value supplied only by the browser.

---

### User Story 3 - Admin provision and maintain an internal account (Priority: P1)

An Admin creates a Staff or Manager account with a fixed role and valid location scope. The invited person completes their own setup before receiving internal access. The Admin can later maintain allowed information and role/location scope without accidentally changing unrelated security state.

**Why this priority**: Internal operations cannot begin until the business can safely provision and scope employees.

**Independent Test**: An Admin can create one pending Staff or Manager account, resend a setup invitation, and activate it only after its intended identity completes the required setup; duplicate create attempts do not create another account or invitation.

**Acceptance Scenarios**:

1. **Given** an Admin supplies a new Staff or Manager with valid location scope, **When** the creation is accepted, **Then** exactly one internal account is created in `PENDING_SETUP` and a post-commit invitation request is recorded.
2. **Given** an Admin retries the same interrupted create action, **When** the request is retried unchanged, **Then** the original result is returned; a reuse of the retry key with changed details is rejected.
3. **Given** a pending internal account signs in with its intended verified identity and has valid active location scope, **When** it completes account bootstrap, **Then** it becomes `ACTIVE` and is never replaced by a Member account.
4. **Given** an Admin edits internal details, **When** they change profile, role/location, or status, **Then** each action is separate, records an audit event, and cannot silently overwrite a newer Admin change.

---

### User Story 4 - Admin protect the business from an unsafe account (Priority: P1)

An Admin can find an allowed account, suspend it with a reason, and later restore it only after existing sessions have been invalidated. Suspension takes effect even when a downstream provider or email delivery is temporarily unavailable.

**Why this priority**: Immediate account containment is a core operational safety need.

**Independent Test**: An Admin suspends an account and its next protected action is denied immediately; restoration cannot make a stale session valid or bypass incomplete setup.

**Acceptance Scenarios**:

1. **Given** an allowed Member, Staff, or Manager account, **When** an Admin suspends it with a reason, **Then** protected access is denied immediately and the suspension is audited.
2. **Given** the identity-provider session invalidation is still pending, **When** an Admin tries to restore the account, **Then** restoration is unavailable until that invalidation completes.
3. **Given** an account is restored after session invalidation, **When** its owner returns, **Then** they must sign in again and the system restores the account's prior eligible state rather than granting new rights.

---

### User Story 5 - Understand fixed access without configuring permissions (Priority: P2)

An Admin can search and inspect accounts, see the fixed role catalogue, and make only the role/location assignments supported by the business. The Admin cannot create a new role, edit permissions, create another Admin, or use account administration to obtain unrelated business powers.

**Why this priority**: Clear administration avoids role mistakes while keeping V1 manageable.

**Independent Test**: An Admin can filter the account list and view the static role descriptions, while every attempt to configure dynamic permissions, assign an unsupported role, or manage an Admin account is rejected.

**Acceptance Scenarios**:

1. **Given** an Admin searches by email or full name and filters by role/status, **When** the list is displayed, **Then** results are paginated, ordered by recent change/creation, and do not expose credentials, provider identifiers, or unrelated business records.
2. **Given** a Staff assignment, **When** an Admin saves it, **Then** it has exactly one active location; a Manager assignment has one or more active locations.
3. **Given** an Admin opens the role catalogue, **When** it is displayed, **Then** it explains the fixed role scope without offering permission or role editing.

### Edge Cases

- A configured external sign-in provider is unavailable, a popup is blocked, or the provider reports an account-method conflict: no local account is created until a valid identity is established, and the person receives retry/alternative-method guidance.
- A password-reset or verification request must not reveal whether an email account exists.
- A person can sign in with an unverified email but cannot bypass an operation's verified-email requirement, even by refreshing the UI or presenting stale identity information.
- An email change that succeeds at the identity provider but initially fails to synchronize can be retried from the current identity without reverting to an older email.
- A location becomes inactive after a Staff/Manager account is active: the account's role/status is not changed automatically, but actions in that location are denied.
- An invitation expires, is not delivered, or an internal creation partly fails: the original account remains pending; an authorized resend/recovery never creates a second identity or account.
- A notification-delivery failure does not undo a committed account, role, status, or audit change; it remains recoverable through FE-07.
- A concurrent Admin edit is rejected rather than overwriting the latest profile, role/location, or status state.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST create or return one Account for each verified identity and MUST use that identity, not a client-supplied email, account ID, role, or provider profile, to determine account ownership.
- **FR-002**: The system MUST support email/password registration and sign-in and MAY show Google/Facebook sign-in only when the corresponding provider is configured for the environment.
- **FR-003**: The system MUST send every successful sign-in to Home and MUST NOT make a role dashboard or policy acceptance a sign-in prerequisite.
- **FR-004**: The system MUST permit a person with an unverified email to sign in and see a clear verification reminder; each protected function MUST independently enforce its verified-email requirement.
- **FR-005**: The system MUST let an account owner explicitly sign out, request email verification, reset an eligible password, change an eligible password after required reauthentication, and change login email only through a verified identity-provider flow.
- **FR-006**: The system MUST synchronize a changed login email only from the current verified identity for the same Account and MUST reject stale or browser-supplied email data as an authority source.
- **FR-007**: The system MUST allow an active, verified account owner to view and replace their own full name and optional phone number. A full name is required after outer-whitespace trimming, contains 1–100 Unicode characters, and contains no ISO control character. A phone is optional, trimmed, 7–30 characters when present, and is neither normalized, verified by OTP, nor required to be unique.
- **FR-008**: The system MUST keep profile, role/location, status, email, credential, and identity changes as distinct actions. Profile editing MUST NOT change role, status, login email, identity, or location scope.
- **FR-009**: The system MUST support exactly the fixed roles `MEMBER`, `STAFF`, `MANAGER`, and `ADMIN`. Each Account has exactly one role; provider claims and frontend visibility MUST NOT grant a business role.
- **FR-010**: The system MUST expose a read-only fixed-role catalogue to Admins. It MUST NOT provide user-configurable permissions, custom roles, multiple role assignment, or a dynamic RBAC administration interface.
- **FR-011**: The system MUST allow an Admin to create only Staff or Manager internal accounts. An internal account starts as `PENDING_SETUP`; an Admin account is created and managed only by the separate operations bootstrap process.
- **FR-012**: The system MUST require exactly one active location for Staff and one or more active locations for Manager. It MUST validate assignments against the authoritative workshop-location source and MUST NOT own a duplicate location record.
- **FR-013**: The system MUST activate a pending Staff/Manager account only when its intended identity signs in, its email is verified, and its assigned location scope remains valid. This activation MUST NOT create or overwrite a Member account.
- **FR-014**: The system MUST make internal-account creation safely retryable and MUST preserve one result for a repeated unchanged creation request. A changed request using the same retry key MUST be rejected.
- **FR-015**: The system MUST let an Admin resend a setup invitation only for a pending internal account, subject to a five-minute resend cooldown. A resend uses a fresh identity-provider setup link and MUST NOT retain the raw link in audit or log records.
- **FR-016**: The system MUST let an Admin search and filter allowed account records by full name/email, role, and status; paginate the results; order them with recently changed/created records first; and exclude credentials, provider identifiers, and unrelated business data from the list/detail responses.
- **FR-017**: The system MUST allow an Admin to edit only full name and phone for Staff/Manager accounts. Member profile values are self-managed; login-email change remains an owner flow.
- **FR-018**: The system MUST make Admin changes to internal profile, role/location, and status conflict-safe. A change based on an older displayed version MUST be rejected and require reload rather than overwrite current data.
- **FR-019**: The system MUST allow an Admin to suspend or restore only Member, Staff, or Manager accounts. Suspension requires a reason, takes effect immediately for protected access, and does not depend on notification delivery or identity-provider invalidation completing first.
- **FR-020**: The system MUST invalidate prior identity sessions after suspension. Restoration MUST wait until the invalidation is complete, restore the eligible pre-suspension state (`ACTIVE` or `PENDING_SETUP`), and require the person to sign in again.
- **FR-021**: The system MUST use the states `ACTIVE`, `PENDING_SETUP`, and `SUSPENDED`; email verification is a separate condition. Deactivating a location MUST NOT itself change account role/status, but location-scoped access MUST be denied.
- **FR-022**: The system MUST never automatically merge, link, upgrade, or reuse accounts merely because emails match while identities differ. It MUST provide safe guidance to use the original sign-in method or contact support.
- **FR-023**: Every account-security or administrative mutation MUST create a safe audit record with actor, target, action, outcome, time, and correlation information. Required role/status/location before/after data and suspension reason may be included; raw passwords, tokens, action links, full names, and phone values MUST NOT be included. Audit/security evidence is retained for five years.
- **FR-024**: The system MUST record account invitation, lock, unlock, and role-change notification requests together with their successful account mutation. FE-07 delivery occurs after that mutation; a delivery failure MUST NOT roll back the accepted account decision.
- **FR-025**: The system MUST provide safe, actionable recovery for invalid identity, unverified email, suspended account, forbidden role/location, invalid input, stale Admin change, duplicate creation retry, missing record, and temporarily unavailable dependency. Error responses MUST not expose protected data, credentials, tokens, stack traces, or another account's existence beyond what the caller is authorized to know.
- **FR-026**: The system MUST ensure that every protected action uses the current Account role, status, ownership, and location scope on the server. Hiding a frontend control or retaining an old token/UI state MUST NOT preserve a revoked capability.
- **FR-027**: The system MUST provide one shared server-side identity gate for the backend. It MUST admit only explicitly declared public routes; for all other routes it verifies the bearer identity and revocation state, resolves a safe current Account actor, and denies access when this cannot be done. The actor contract exposes only opaque account ID, role, status, email-verification state, and active location IDs; raw identity-provider UID/token remain inside the gate. The gate MUST NOT own or dynamically configure another feature's business capabilities; each feature derives its action authority from the current actor under its own approved rules.

### Key Entities *(include if feature involves data)*

- **Account**: The business account for one verified identity; holds current login email, fixed role, lifecycle state, location-scope relationship, concurrency version, and security-recovery state.
- **Account Profile**: The owner-editable full name and optional phone attached to one Account.
- **Account Location Assignment**: The relationship between a Staff/Manager Account and authoritative workshop location(s), constrained by the Account role.
- **Internal Account Provisioning**: The retryable record of one Admin request to create and invite a Staff/Manager account, including its setup progress.
- **Account Audit Event**: Redacted evidence of an account/security action and its outcome, retained for support and accountability.
- **Notification Request**: The post-commit request consumed by FE-07 to deliver an invitation or account-change message.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Each supported registration/sign-in method can create or return exactly one account for the same identity across repeated bootstrap attempts, with no account created for a failed identity flow.
- **SC-002**: A verified active account owner can complete profile save, password-recovery initiation, and verified email synchronization without an operator handling the credential or manually editing the account.
- **SC-003**: Every tested Staff/Manager creation retry results in at most one internal account and one intended identity; no pending internal account can access internal work before activation.
- **SC-004**: Every tested suspension denies the account's next protected action immediately; no restoration succeeds while prior-session invalidation remains incomplete, and a restored user must sign in again.
- **SC-005**: Every tested Admin mutation records a redacted audit event and remains effective when the corresponding notification delivery is delayed or fails.
- **SC-006**: Every documented unauthorized, stale, invalid, or provider-unavailable scenario returns a safe recovery outcome without revealing credentials, provider identifiers, or another account's protected information.

## Assumptions and dependencies

- A separate frontend foundation supplies routing, identity-client initialization, current-authentication state, and authenticated request transport. FE-001 does not create that foundation or Home/dashboard content.
- The configured identity provider remains responsible for passwords, verification messages, password reset, external sign-in, and proof of identity. V1 does not require MFA, an additional identity tier, a forced eight-hour re-login, or a separate application password/session store.
- The shared identity gate is the sole backend bearer-verification path. It has an explicit public-route allowlist and a deny-by-default fallback; CORS and CSRF behavior are designed per declared frontend origin and bearer transport rather than disabled globally.
- Database connection configuration is supplied through the approved environment/secret channel with no committed default credential. The previously observed defaults were confirmed as synthetic and not reused, so no credential-rotation incident is required.
- Schema migrations use one repository-wide, append-only sequence of increasing integer versions. Existing applied/merged versions retain their number; an owner checks and reserves the next number before creating a new migration.
- Google/Facebook choices are visible only when their provider configuration is available; their absence does not block email/password sign-in.
- The workshop-location owner supplies authoritative active-location validation and display data through its permitted boundary.
- FE-07 accepts and delivers post-commit notification requests. It is responsible for mail delivery status/retry, while Account remains authoritative for the account decision and audit record.
- An operations procedure creates the initial Admin identity and bootstraps its Mland Account outside the web UI. That procedure is a dependency, not a self-service feature requirement.
- Existing `plan.md`, `tasks.md`, `data-model.md`, and `contracts/member-profile.openapi.yaml` describe the superseded Member Profile design. They must be regenerated from this specification before implementation and must not be treated as current instructions.

## Supersession note

This specification replaces the legacy FE-001 Member Profile/SSR/cookie-session scope at the same feature path. It is a business specification, not evidence that a backend, frontend, migration, identity-provider configuration, or operational bootstrap has been implemented or approved for release.
