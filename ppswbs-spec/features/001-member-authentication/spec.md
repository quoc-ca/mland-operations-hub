# Feature Specification: Member Registration and Sign-in

**Feature Branch**: `001-member-authentication`  
**Created**: 2026-09-29  
**Status**: Draft  
**Input**: User description: "Create detailed specifications for Member registration and sign-in before implementation."

## Clarifications

### Session 2026-09-29

- Q: Is a phone number mandatory when creating a Member? → A: Require it only where booking or checkout needs contact information.
- Q: Which past Guest bookings should a verified Member receive? → A: All statuses.
- Q: Should bookings for a changed Member email be linked automatically? → A: Link only after the new email is verified.
- Q: How should historical booking linking start? → A: Ask the Member to confirm before linking.
- Q: Must a customer agree to Terms and Privacy Policy when creating a Member? → A: Yes, through a required unchecked acceptance control.
- Q: What happens when Terms or Privacy Policy changes? → A: End active Member access; require sign-in and acceptance of the changed policy before Member rights resume.
- Q: What happens when a Member is suspended? → A: Deny all Member rights and provide a neutral support message.
- Q: How does a Google-only Member recover lost Google access? → A: Use Google's recovery process; do not transfer the Member identity manually in V1.
- Q: What proves a Guest booking belongs in Member history? → A: The booking email was confirmed when the booking was created.
- Q: What happens to a booking whose Guest email is unconfirmed? → A: Cancel it after 15 minutes.
- Q: Can the Guest resend the booking-email confirmation? → A: No; they must create a new booking after expiry.
- Q: Can a Guest pay a deposit before confirming booking email? → A: No.
- Q: Which accessibility commitment applies? → A: Best effort; no formal accessibility conformance claim.
- Q: Which browser/device baseline applies? → A: The latest two releases of Chrome, Edge, Firefox, and Safari on desktop, plus Chrome on Android and Safari on iPhone.
- Q: How should pre-account booking history be linked after verification? → A: Present the confirmed Member with an explicit import confirmation before linking all eligible bookings.

## User Scenarios & Testing *(mandatory)*

## Architecture Alignment

This feature is owned by the `members` module. Firebase identity verification, Member state, consent, policy acceptance, and Member-auth audit remain module-internal. Another business module may request only a public `members.facade` use case; it must not import Member controllers, HTTP DTOs, entities, repositories, or infrastructure adapters. The Guest-booking association is requested through `workshopbooking.facade`; direct booking persistence access is forbidden.

### User Story 1 - Create a Member identity (Priority: P1)

A Guest who needs a Member-only capability can create a Member identity using their Google account or an email address and password. A first successful identity creation gives the person one Member account and returns them to the protected action that prompted sign-in.

**Why this priority**: Member-only ready-ring retail and permitted history cannot provide value until a Guest can become a Member. Google reduces friction, while email/password keeps the service usable for people who do not want to use Google.

**Independent Test**: A new Guest completes either registration path and reaches the previously blocked Member-only action as a Member; repeated use of the same identity shows the same Member account.

**Acceptance Scenarios**:

1. **Given** a Guest has no Member account, **When** they successfully continue with Google for the first time, **Then** the system creates one Member account and returns them to the protected action or homepage.
2. **Given** a Guest has no Member account, **When** they submit a valid email address and password, **Then** the system creates one Member account, signs them in, and offers email verification.
3. **Given** a person already has a Member account, **When** they sign in again with the same linked identity, **Then** the system reuses the existing Member account and its permitted history.
4. **Given** registration cannot be completed, **When** the identity provider or input validation reports an error, **Then** the system explains the next safe action without exposing whether another person owns a specific account.

---

### User Story 2 - Sign in to a protected Member journey (Priority: P1)

A returning Member can sign in with a previously linked Google account or email/password account, retain the sign-in state in their browser, and sign out deliberately. A Guest continues to use public workshop booking and booking lookup without authentication.

**Why this priority**: The product must distinguish Member-only retail/history from public journeys without introducing registration friction into workshop booking.

**Independent Test**: Start from ready-ring checkout or Member history as a Guest, sign in successfully, and confirm that the original action becomes available. Sign out and confirm the same action is blocked again.

**Acceptance Scenarios**:

1. **Given** a Guest opens Member history or ready-ring checkout, **When** they are not signed in, **Then** the system directs them to sign in or register and preserves the intended return action.
2. **Given** a Member has signed in successfully, **When** they reopen the service in the same browser, **Then** the Member state remains available until they sign out or their identity session is no longer valid.
3. **Given** a Member signs out, **When** they try a Member-only action, **Then** the system treats them as a Guest and does not show protected information.
4. **Given** a Guest books a workshop or looks up a booking, **When** they do not sign in, **Then** those approved public journeys remain available.

---

### User Story 3 - Recover access and verify email (Priority: P2)

A Member who uses email/password can request a password reset and request a new email-verification message. A Member created with email/password may use new Member privileges immediately, but historical Guest bookings require verified matching email and the Member's explicit import confirmation.

**Why this priority**: A recoverable account reduces support burden, while verification protects past Guest data from being associated with an unproven email address.

**Independent Test**: Register by email/password, use the Member-only journey, request verification, complete it, confirm the history import, and verify that every eligible historical booking appears with its true status.

**Acceptance Scenarios**:

1. **Given** a Member cannot remember their password, **When** they request recovery with their email address, **Then** the system gives a non-enumerating confirmation and provides the supported recovery path.
2. **Given** a Member created by email/password has not verified their email, **When** they use a new Member-only retail or history action, **Then** the action remains available but no past Guest booking is automatically linked.
3. **Given** that Member subsequently verifies the same email address, **When** they confirm the offered history import, **Then** every eligible unlinked Guest booking with the verified matching email is linked once to that Member and retains its pending, confirmed, failed, or cancelled status.
4. **Given** a Member asks to resend verification, **When** the request is accepted, **Then** the system confirms the request without disclosing sensitive account state to other users.

---

### User Story 4 - Safely use more than one sign-in method (Priority: P2)

A Member can use another sign-in method only after proving control of their existing Member identity. The system must never automatically merge two different Member identities merely because the email text matches.

**Why this priority**: Automatic email-based merging can expose booking, order, and payment history to the wrong person.

**Independent Test**: Create a Member using one method, attempt the same email through a different unlinked method, authenticate the existing method, and confirm that linking requires that proof and retains one Member identity.

**Acceptance Scenarios**:

1. **Given** an email is already associated with a different Member identity, **When** a user attempts another sign-in method with that email, **Then** the system does not merge or expose history and directs them to authenticate the existing method.
2. **Given** a signed-in Member proves control of an additional sign-in method, **When** they link it successfully, **Then** subsequent sign-in using either method accesses the same Member account.
3. **Given** an account-linking attempt fails or is cancelled, **When** the failure is shown, **Then** the existing Member account and its business history remain unchanged.

---

### User Story 5 - Confirm Guest booking email before payment (Priority: P1)

A Guest who creates a workshop booking confirms the email address supplied for that booking before opening deposit payment. This proof later allows the booking to be safely offered to the verified Member history, without requiring the Guest to create an account at booking time.

**Why this priority**: A Member must not receive booking data merely because someone else typed their email address. The confirmation also prevents a deposit being collected for a temporary booking that will be cancelled.

**Independent Test**: Create a Guest booking, confirm the booking email within fifteen minutes, and verify that deposit payment becomes available. Repeat without confirmation and verify cancellation, no payment, and released booking capacity.

**Acceptance Scenarios**:

1. **Given** a Guest submits a valid booking request and contact email, **When** the booking is created, **Then** the system marks its email as awaiting confirmation and clearly states the fifteen-minute expiry.
2. **Given** a Guest has not confirmed the booking email, **When** they try to open deposit payment, **Then** the system refuses payment while preserving the pending confirmation state until expiry.
3. **Given** a Guest confirms the booking email within fifteen minutes, **When** they return to the booking, **Then** the booking becomes eligible for its normal payment flow and for future Member-history import.
4. **Given** a Guest has not confirmed the booking email after fifteen minutes, **When** the expiry is reached, **Then** the system cancels the temporary booking, releases its temporary capacity and invoice state, and requires a new booking request.

### Edge Cases

- A browser blocks, cancels, or loses connectivity during a federated sign-in; the user remains on a recoverable sign-in view and can choose the supported fallback.
- A sign-in session expires, is revoked, or belongs to a different service environment; protected actions are refused, protected content is not displayed, and the user is asked to sign in again.
- Two first-use requests for the same verified identity arrive at the same time; exactly one Member account results.
- An identity has no usable email address; it can remain an authenticated identity, but it cannot auto-link Guest bookings and the service asks for only the contact data needed by the next journey.
- A Member repeats the historical-Guest-booking linking action; already linked bookings do not duplicate or move to another Member.
- A Member is suspended or lacks the Member role; valid identity alone does not grant access to Member-only actions.
- A Member changes to a new email address; no booking history for that email is offered until the new email is verified and the Member explicitly confirms import.
- A Guest does not confirm booking email within fifteen minutes; the temporary booking is cancelled, payment remains unavailable, and confirmation cannot be resent for that booking.
- A new Terms or Privacy Policy version is published while a Member is active; the next Member request ends Member access and requires new acceptance before protected actions resume.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST offer Member sign-in with Google and email/password; Google MUST be presented as the primary sign-in choice and email/password as an alternative.
- **FR-002**: The system MUST create at most one Member account for a first successful identity and MUST reuse that Member account for repeated sign-in by the same linked identity.
- **FR-003**: The system MUST treat identity authentication and Mland authorization as separate decisions; only a Mland Member account in an allowed status may use Member-only functions.
- **FR-004**: The system MUST allow a Guest to begin registration or sign-in from any Member-only action and MUST return the successfully authenticated Member to that initiating action when it remains available.
- **FR-005**: The system MUST require an email address and password for the email/password registration path. It MUST NOT require a phone number merely to create a Member; journeys that require contact details may request them later.
- **FR-006**: The system MUST allow a newly created email/password Member to use Member-only rights immediately after successful registration.
- **FR-007**: The system MUST provide password-recovery initiation and email-verification resend for the email/password path. Recovery responses MUST NOT disclose whether the submitted email has an account.
- **FR-008**: The system MUST persist sign-in state in the Member's browser until deliberate sign-out, invalidation, or expiration and MUST remove access to Member-only actions immediately after sign-out.
- **FR-009**: The system MUST keep workshop booking, consented design submission, and booking lookup available to Guests unless their own feature explicitly requires a Member identity. Deposit payment for a Guest booking MUST require confirmed booking email.
- **FR-010**: The system MUST deny Member-only ready-ring checkout and Member history to a Guest or to a Member whose account status does not authorize the action, including direct access attempts that bypass the user interface.
- **FR-011**: The system MUST use a stable external identity identifier as the Member lookup key. Email, provider display name, photo, and client-supplied role information MUST NOT grant authorization or determine an automatic account merge.
- **FR-012**: The system MUST not automatically merge two different external identity identifiers, even where the supplied email text matches. It MUST require successful authentication of the existing Member identity before an additional sign-in method can be linked.
- **FR-013**: The system MUST show an understandable, non-sensitive account-linking recovery path when a sign-in method conflicts with an existing Member identity.
- **FR-014**: The system MUST record a technical audit event for Member provisioning, successful Member sign-in, sign-out, email-verification state change observed by Mland, password-recovery request accepted by the identity service, account-link success or failure, policy acceptance, booking-email confirmation, booking-email expiry, and Guest-history linking attempt.
- **FR-015**: The system MUST offer a historical Guest-booking import only when both the Member email and the booking email are verified as the same address. The Member MUST explicitly confirm the import before any eligible booking is linked.
- **FR-016**: A historical Guest booking is eligible for import only if its email was confirmed when that booking was created. All of its business statuses may appear in Member history, and the link MUST be idempotent and preserve existing business data.
- **FR-017**: The system MUST not offer historical Guest-booking import for an unverified Member email, an unverified booking email, or an email changed after Member creation until that new email has been verified.
- **FR-018**: The system MUST protect Member-only information from identities that are missing, malformed, expired, revoked, untrusted for this service, suspended, or unauthorized. The response MUST not reveal token, account, booking, order, or payment details. A suspended Member MUST lose Member access and receive only a neutral support message.
- **FR-019**: The system MUST send a confirmation request to the Guest booking contact email and require completion within fifteen minutes of temporary booking creation. Until confirmation, the system MUST not create or open deposit payment for that booking.
- **FR-020**: The system MUST cancel an unconfirmed temporary booking at the fifteen-minute expiry, release its temporary capacity and invoice state, and require a new booking request. The system MUST NOT offer confirmation-email resend for that expired or pending booking.
- **FR-021**: The system MUST require explicit, unchecked acceptance of the current Terms of Use and Privacy Policy at Member creation and retain the accepted policy versions with the acceptance event.
- **FR-022**: When a new Terms of Use or Privacy Policy version takes effect, the system MUST end active Member access and require sign-in plus acceptance of the current versions before Member-only rights resume. Guest public journeys MUST remain available.
- **FR-023**: The system MUST direct a Member who loses access to a Google-only identity to the provider's recovery process. V1 MUST NOT manually transfer Member identity or history to a different identity.
- **FR-024**: The system MUST support only the `MEMBER` account role in this feature. Staff, Owner, and Admin Technical identity lifecycle, MFA, and authorization are explicitly excluded.
- **FR-025**: The system MUST provide sign-in, registration, recovery, verification, policy-acceptance, and error feedback in both Vietnamese and English.
- **FR-026**: The system MUST provide labelled core authentication controls and understandable feedback; keyboard and assistive-technology support are best-effort and this feature makes no formal accessibility-conformance claim.
- **FR-027**: The system MUST support Member registration and sign-in in the latest two releases of Chrome, Edge, Firefox, and Safari on desktop, plus Chrome on Android and Safari on iPhone.

### Key Entities *(include if feature involves data)*

- **External Identity**: A verified identity supplied by an approved identity service, identified by a stable external identifier and optionally accompanied by email and display profile data. It proves who is signing in but does not confer a Mland role.
- **Member Account**: The Mland business account for a customer. It has one stable external identity identifier, an account status, the `MEMBER` role, and permitted access to Member journeys.
- **Linked Sign-in Method**: An additional approved way for the same person to authenticate to one External Identity after the person proves control of the existing identity.
- **Guest Booking Link**: The auditable association between one verified Member and an eligible Guest booking created before that Member account existed. A booking can have at most one such Member association.
- **Booking Email Confirmation**: Proof that the Guest controlled the contact email supplied at temporary booking creation. It expires after fifteen minutes if not completed and is required before deposit payment or Member-history import.
- **Policy Acceptance**: The Member's auditable agreement to a specific Terms of Use and Privacy Policy version. A later version makes prior Member access invalid until new acceptance is recorded.
- **Authentication Audit Event**: A time-stamped technical record of an account lifecycle or authorization-relevant authentication event. It excludes credentials, tokens, passwords, and unnecessary personal data.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In moderated acceptance testing, at least 95% of new users who complete an approved sign-in method reach their intended Member-only action within two minutes without staff help.
- **SC-002**: In acceptance testing, 100% of attempts to access Member-only history or ready-ring checkout without a valid authorized Member identity are denied before protected information is shown.
- **SC-003**: In concurrency and repeat-sign-in testing, 100% of first-use identity cases result in exactly one Member account and retain that same account on subsequent sign-in.
- **SC-004**: In verification-state testing, 100% of historical Guest bookings remain unlinked until both booking and Member email are verified and the Member confirms import; every eligible booking is then linked exactly once with its true status.
- **SC-005**: In account-collision testing, 100% of different-identity/same-email cases preserve separate accounts and business history until the person authenticates the existing identity and completes an explicit linking flow.
- **SC-006**: In release QA, 100% of core authentication journeys pass on the latest two releases of Chrome, Edge, Firefox, and Safari on desktop, plus Chrome on Android and Safari on iPhone, with Vietnamese and English confirmation or error feedback.
- **SC-007**: In booking-email expiry testing, 100% of unconfirmed temporary bookings are cancelled at fifteen minutes, cannot open deposit payment, and release temporary capacity and invoice state.
- **SC-008**: In policy-version testing, 100% of active Member sessions lose Member-only access after a new mandatory policy version takes effect and regain it only after sign-in and explicit acceptance.

## Assumptions

- The approved identity service manages credentials, password reset, sign-in provider interaction, and identity-session renewal; Mland never stores a password or independently issued authentication token.
- Google and email/password are the only Member sign-in methods in this feature. Facebook is permitted by the general working agreement only when a separately approved feature extension enables it.
- Google-provided email is treated as verified only when the approved identity service reports it verified.
- A newly created email/password Member may use newly created Member data before verification, but verified email plus the Member's confirmation is required to import historical Guest bookings.
- A change to the identity-service email is not a profile-editing feature in V1. It may make historical booking import available only after that changed email is verified and the Member confirms import.
- The policy text, policy-version ownership, data-retention period, and exact privacy/legal approval process remain `TBD` dependencies, but approved current versions must exist before Member creation is released.
- Accessibility is best-effort; the feature does not claim WCAG conformance.
- Phone number, profile editing, account deletion, loyalty, device/session management, Staff/Owner/Admin Technical access, and production deployment are out of scope.

## Dependencies

- UC-02 in the approved SRS supplies the business outcome: create a Member account, create an authenticated session, record a technical audit event, and resume the intended action.
- Member-only ready-ring checkout and permitted Member history are supplied by their owning features; this feature supplies only Member identity and authorization preconditions.
- Guest-booking linkage depends on the booking feature owning confirmed contact-email status, a fifteen-minute temporary-booking expiry, capacity/invoice cleanup, and an auditable Member association.
- Privacy notice and terms content, including current versions, must be approved before the corresponding personal-data collection screens are released.
