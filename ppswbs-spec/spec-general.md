# General Spec
# Version: 0.1 | Status: Working Agreement | Updated: 2026-09-29

> This working agreement guides agent planning and implementation. It does not replace the approved documentation baseline in `../documents/docs/`, where architecture, deployment, and detailed security controls that remain `TBD` stay `TBD` until formally approved.

## 1. Context & Goal

**Business problem:** Mland needs one trustworthy bilingual path for Guests to discover and reserve ring workshops, and for Members to retain a consistent identity across booking, design consultation, retail, invoice and fulfilment journeys. The shop must not operate its own password store or rebuild social OAuth lifecycle for V1.

**Feature goal:** Firebase Authentication provides identity registration and sign-in through Google, Facebook and Firebase email/password. Spring Boot verifies Firebase ID tokens and MySQL retains Mland business identity, authorization and audit data. Guests remain able to use approved public flows until a feature explicitly requires Member identity.

**Success metric:** A valid Firebase identity provisions at most one Mland Member record, repeated sign-in preserves that Member’s business history, invalid tokens never reach protected business operations, and no Mland password or signed JWT is introduced.

**Tech context:** Java 21 + Spring Boot server-rendered web application with Thymeleaf and htmx progressive enhancement, REST API, and MySQL. Firebase Hosting is approved only as a preview capability; it does not choose a production backend origin, rewrite model, Cloud Run deployment or VPS deployment.

## 2. Actors & Roles

| Actor | Type | Responsibility and boundary |
| --- | --- | --- |
| Guest | External user | Uses public Mland journeys. A Guest becomes an authenticated Member only after Firebase token verification and successful Mland provisioning. |
| Member | Internal application role | Uses Member-only history and retail capabilities granted by Mland. |
| Staff / Owner / Admin Technical | Internal application roles | Receive authority only from Mland/MySQL. Staff, Owner and Admin Technical require MFA under the V1 authentication policy. |
| Firebase Authentication | External identity service | Manages credential lifecycle, Google/Facebook federation, Firebase email/password and Firebase ID tokens. It does not own Mland roles, booking, payment, invoice or audit decisions. |
| Google / Facebook | External identity providers | Authenticate a person for Firebase after their own provider configuration and approval. They do not grant Mland roles. |
| Firebase Hosting | External preview service | May host preview content when configured by an approved feature plan. It is not the production deployment decision in this agreement. |

## 3. Functional Requirements

### Identity and authentication

* THE web client SHALL use the Firebase web SDK for Google, Facebook and Firebase email/password sign-in when the applicable provider is enabled.
* AFTER Firebase completes sign-in, THE web client SHALL obtain a Firebase ID token and send it to protected Mland API requests through the `Authorization: Bearer` header. Tokens SHALL NOT be placed in URLs, application logs or committed files.
* THE Spring backend SHALL verify the Firebase ID token with Firebase Admin SDK before using its identity data. Decoding a token without verification is insufficient.
* AFTER a verified token is received, THE backend SHALL locate a Member by Firebase `uid` stored as `external_user_id`. If none exists, it SHALL create one Member transactionally with default role `MEMBER`, subject to a unique constraint on `external_user_id` and an audit event.
* THE backend SHALL use Mland/MySQL role and account-status data for authorization on every protected operation. A Firebase provider, email claim, display name, photo URL or custom claim SHALL NOT grant Staff, Owner or Admin Technical authority.
* WHEN a Firebase account links another provider and retains the same `uid`, THE existing Member record SHALL be reused. WHEN a different Firebase `uid` has the same email, THE backend SHALL NOT merge records automatically; account-linking resolution requires an explicit, authenticated policy.
* THE Firebase SDK SHALL manage token renewal. Mland SHALL NOT issue an additional application JWT or store a password hash for Firebase-managed accounts.

### Hosting preview

* Firebase Hosting MAY be used for temporary preview content after a feature plan specifies the preview source, access boundary and lifecycle.
* Firebase Hosting preview SHALL NOT be described as production deployment, nor imply that Spring Boot, MySQL, payment, booking capacity or dynamic private data are hosted or cached by Firebase Hosting.
* No Firebase Hosting configuration, project identifier, Hosting rewrite or production custom domain is created by this working agreement.

## 4. Non-functional Requirements

* **Security:** Use HTTPS; verify token signature, issuer, audience and expiration with Firebase Admin SDK; reject unverified, expired or wrong-project tokens. Do not log ID tokens, provider access tokens, secrets or unnecessary PII.
* **Authorization:** Authentication proves identity; Mland authorization decides business access. Protected booking, payment, invoice and administration operations remain server-side checks against Mland data.
* **Privacy and resilience:** `external_user_id` is the stable identity key. Email, display name and photo may be absent or change and must not be treated as authorization keys.
* **Hosting:** CDN/preview behavior applies only to deliberately public cacheable content. Never cache identity tokens, authenticated responses, booking capacity, payment status, invoice, Member history or administration data.
* **Testing:** Firebase Auth test environment is `TBD`. Before implementation, the feature plan must select Firebase Auth Emulator or a separate Firebase development/staging project; it must never exercise the production Firebase project. Firebase Test Lab is not the server-rendered web test strategy.

## 5. Data Model

The physical schema remains a feature-plan decision. The following conceptual ownership is mandatory:

| Data | Owner | Constraint |
| --- | --- | --- |
| Firebase `uid` / `external_user_id` | Mland MySQL referencing Firebase identity | Mandatory and unique per Member; used for lookup and provisioning. |
| Email, display name, photo URL | Mland profile snapshot | Optional contact/profile data; mutable and not a role or merge key. |
| Member, Staff, Owner and Admin Technical roles | Mland MySQL | Granted, changed and audited only by Mland authorization workflows. |
| Booking, payment, invoice, order, consent and audit data | Mland MySQL | Never moved to Firebase Authentication. |
| Password credential, social-provider credential and Firebase refresh lifecycle | Firebase Authentication | Never copied into Mland MySQL. |

## 6. Error Handling & Security

* WHERE the bearer token is missing, malformed, expired, revoked, wrong-project or fails verification, THE backend SHALL return `401 Unauthorized` without exposing token details.
* WHERE the verified Firebase identity maps to a locked or unauthorized Mland account, THE backend SHALL return `403 Forbidden` and preserve the audit/security context without exposing protected data.
* WHERE two concurrent verified requests provision the same previously unseen Firebase `uid`, THE unique identity constraint and transaction handling SHALL leave exactly one Member record.
* WHERE a social provider does not return an email, THE system SHALL retain Firebase UID identity and request only the additional profile/contact information defined by the applicable feature; it SHALL NOT reject identity solely for missing email unless that feature explicitly requires it.
* WHERE a same-email, different-UID collision occurs, THE system SHALL not merge bookings, orders or roles automatically. It SHALL surface a safe support/account-linking path defined by a later feature specification.
* Client sign-out SHALL use Firebase SDK sign-out. This agreement does not introduce a separate Mland server session or browser-stored application token.

## 7. Acceptance Criteria

* [ ] A Guest can complete Google, Facebook or enabled Firebase email/password sign-in and the client receives a Firebase ID token through the Firebase SDK.
* [ ] A verified first sign-in creates exactly one Member with a unique `external_user_id`, default `MEMBER` role and appropriate audit record; a repeated sign-in retains the same Mland history.
* [ ] A modified, expired, wrong-project or otherwise unverifiable token receives `401`; a valid identity with insufficient Mland authority receives `403`.
* [ ] A Google/Facebook identity cannot gain Staff, Owner or Admin Technical permissions through provider claims, email, display name or client-supplied role data.
* [ ] A same-email, different-Firebase-UID situation does not merge Member records or business history automatically.
* [ ] Preview documentation does not claim Firebase Hosting is production deployment or that it accelerates/caches private or dynamic business data.
* [ ] No password hash, Mland-signed JWT, Firebase secret, OAuth secret or Firebase project identifier is committed by the authentication implementation.

## 8. Use Case Relationships

```text
Guest public journey ──optional sign-in──► Firebase Authentication
Firebase Authentication ──verified ID token──► Mland Member provisioning
Mland Member provisioning ──uses──► Member-only booking history and ready-ring retail
Staff / Owner / Admin Technical access ──requires──► Firebase identity + Mland role + MFA policy
Firebase Hosting preview ──supports──► approved preview workflow only
```

## 9. Out of Scope

* Mland-created passwords, password hashes, password reset email flows, Mland-signed JWTs and a separate application refresh-token system for Firebase-managed identities.
* Automatic account merge based on email, provider display name or photo URL.
* Granting Mland roles through social provider data or Firebase custom claims.
* Firebase Hosting production deployment, Hosting rewrite rules, Cloud Run/VPS selection, database deployment, custom production domain or production Firebase project creation.
* Firebase Test Lab as a testing mechanism for the server-rendered web application.
* A final Firebase Auth test environment, test automation or CI configuration before the relevant feature plan is approved.

## 10. Notes / Open Questions

* Which Firebase project hierarchy, billing owner, access roles and secret-management process will be used for development, staging and production?
* Will Firebase Auth Emulator or a separate Firebase development/staging project be used for integration tests?
* Which Firebase email/password, Google and Facebook settings require provider approval, and what is the final account-linking and recovery support process?
* How will Mland enforce, recover and audit MFA for Staff, Owner and Admin Technical under Firebase/Identity Platform capabilities?
* What preview content can Firebase Hosting publish for a server-rendered application, who can access it, and when does it expire?
* Which production origin and deployment model will be selected after the deployment feature plan and cost/SLA decisions are approved?

## References

* [Firebase Authentication](https://firebase.google.com/docs/auth/)
* [Firebase Admin token verification](https://firebase.google.com/docs/auth/admin/verify-id-tokens)
* [Firebase Hosting use cases](https://firebase.google.com/docs/hosting/use-cases)
* [Firebase Hosting preview channels](https://firebase.google.com/docs/hosting/test-preview-deploy)
