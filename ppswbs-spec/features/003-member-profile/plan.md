# Implementation Plan: Member Profile
**Date**: 2026-10-19 | **Spec**: [spec.md](spec.md)
**Approval**: User explicitly requested implementation of the preceding design; this document operationalizes it without expanding business scope.
## Technical Context
Java 21, Spring Boot 3.4.1, Thymeleaf, Spring Security, JPA, Flyway, Firebase Admin SDK 9.11.0 and MySQL 8.4. Existing JUnit/Spring Boot Test/MockMvc; H2 is supplemental only. Firebase Auth Emulator and disposable MySQL are selected for integration verification. No new production framework.
## Constitution Check
Closed members module owns persistence, use cases, DTOs, controllers and profile identity adapter. Separate profile SecurityFilterChains in configuration consume a members facade exposing only authentication boundary primitives. No entities/controllers/infrastructure exported. No raw SQL in application code. Firebase owns tokens/cookies/passwords. CSRF and server forms preserve no-JS core profile/policy. No architecture exceptions added.
## Design
- Dedicated profile API and web security chains ordered before existing fallback; do not alter security behavior of preexisting routes. Prevent profile auth filters from auto-registering as global servlet filters.
- Firebase identity adapter verifies ID tokens/cookies with checkRevoked=true and issues eight-hour cookies; safe identity record contains UID, email, verified flag and auth_time only. No client roles.
- Web public auth/session bootstrap routes use CSRF; protected profile/policy/logout use cookie identity. Cookie name __Host-mland_profile. Invalid/expired cookies redirect to fixed login page, not user-controlled URLs.
- member_profiles uses member_id as PK/FK, nullable stored full_name/phone, created_at/updated_at. GET on absent row is read-only. PUT and policy/provision state changes lock local Member via JPA, use controlled DTOs and minimal audit.
- Session bootstrap provisions Member atomically under UID uniqueness; duplicate UID race retries outside the failed transaction, never by email. ACTIVE and PENDING_POLICY_ACCEPTANCE are the only allowable bootstrap states; suspension denies.
- Profile service requires ACTIVE plus both effective policy types and exact accepted versions. Policy page permits PENDING_POLICY_ACCEPTANCE or ACTIVE, never SUSPENDED; acceptance rechecks/locks effective versions and transitions only PENDING_POLICY_ACCEPTANCE to ACTIVE.
- API request body has strict exact two-key/type validation, with Bean Validation/custom Unicode name constraint. Web binder has explicit allowed fields and rejects suppressed/duplicate parameters. Reuse the same normalization/service rules.
- API response: fullName/phone/email/emailVerified/updatedAt. Missing local Member returns 409 with code MEMBER_INITIALIZATION_REQUIRED; missing profile is 200 with null profile fields. Shared errors follow existing ErrorResponse schema.
- GET/POST /member/profile; GET/POST /member/profile/policies; POST /member/profile/logout; GET /member-auth/profile-signin; POST /member-auth/profile-session. No endpoint accepts arbitrary returnTo.
- Policy acceptance and session auth mutations have CSRF protection; isolated HTML errors preserve form data without logging it. JSON unknown body is rejected locally, not by changing global Jackson settings.
## Data Model and Contract
See [data-model.md](data-model.md) and [OpenAPI](contracts/member-profile.openapi.yaml).
## Verification and delivery
Implement tasks in order, tests first where practical. Run focused profile/security tests then existing suite/Modulith. Run disposable MySQL empty+upgrade, concurrency and rollback checks; Auth Emulator provider contract smoke; render/browser no-JS form checks. Record exact commands/results in [verification.md](verification.md); report unavailable checks honestly. Do not claim production deployment or real-project integration.
