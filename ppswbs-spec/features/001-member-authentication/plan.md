# Implementation Plan: Member Authentication

**Branch**: 001-member-authentication | **Status**: Draft implementation plan
**Runtime**: Java 21, Spring Boot, Spring Security, Spring MVC, Spring Data JPA,
Bean Validation, Thymeleaf/htmx, Firebase Admin/Web SDK, Flyway
**Database baseline**: MySQL 9.7.2 for local, CI, staging, and production

## Architecture and boundaries

Members is a closed Spring Modulith module. Its web layer calls its application
layer; application owns transactions; domain owns state transitions and
invariants; infrastructure owns repositories and Firebase adapters. Other
modules use members.facade only. The workshopbooking module owns booking writes
and exposes its own facade for Member import and confirmation-related operations.

The request sequence is:

1. Browser obtains a Firebase ID token through Firebase Web SDK.
2. Spring verifies the token using Firebase Admin SDK.
3. Member application code resolves the MySQL account, active role/status, and
   policy acceptance; Firebase claims never supply application roles.
4. Controller returns DTOs or the common safe error envelope with correlation ID.

## Database and environment design

MySQL 9.7.2 is pinned for every deployed and integration-tested environment.
The database name is ppswbs. Root is only a container/bootstrap principal and
is never used by the service.

- ppswbs_migrator is supplied only to CI/deploy Flyway execution and has
  schema-migration privileges scoped to ppswbs.
- ppswbs_app is supplied only to runtime and has the minimum DML privileges
  required by deployed modules.
- Runtime configuration obtains JDBC_URL, JDBC_DRIVER, JDBC_USER, and
  JDBC_PASSWORD from environment/secret injection. Migration execution obtains
  FLYWAY_URL, FLYWAY_USER, and FLYWAY_PASSWORD similarly.
- No committed configuration, quickstart output, test fixture, log, or audit
  contains a value for a credential.

H2 remains available only as a fast test profile. A local-mysql profile connects
to MySQL 9.7.2 and is mandatory for manual local verification. CI integration
tests use Testcontainers pinned to mysql:9.7.2. Release evidence must include
MySQL migration and integration results; H2 results alone are insufficient.

## Forward migration and authorization design

Do not edit V1__init_member_auth.sql. Add forward migrations that:

1. create accounts and account_roles;
2. backfill one account for every existing Member and add nullable,
   uniqueness-validated members.account_id;
3. move entitlement/role lookups to accounts/account_roles while retaining
   external_user_id compatibility until a separately approved removal;
4. add a stored generated column that equals policy_type only for EFFECTIVE
   policy rows and a unique index on it, enforcing one effective document per
   type without restricting archived/draft versions.

Provisioning is UID-only and idempotent. Role/status lookup happens after token
verification. A Member entitlement requires active account state, MEMBER role,
and acceptance of both current effective policy records.

## Required implementation reconciliation

- Replace Firebase-auth UI placeholders with a Firebase Web SDK flow that sends
  a fresh bearer token to provisioning/entitlement APIs; retain safe noscript
  support guidance rather than an unsafe password form.
- Make API DTO validation match OpenAPI: the Guest import confirmation is
  required and true; confirmationToken is required/nonblank; all token failure
  variants map to the generic safe 400 response.
- Enable and test the booking expiry scheduler. Its transition must be
  idempotent, transactional, and preserve the payment/capacity gate.
- Protect Member-only routes through the reusable members authorization seam.
  Ready-ring and history modules consume the seam when they add their routes;
  this feature does not invent their APIs.
- Retain correlation IDs and typed exceptions, but ensure error/audit/log paths
  redact tokens, passwords, provider codes, and unnecessary PII.

## Release prerequisites

Approved policy content/version publication, Firebase provider configuration and
non-production Auth Emulator setup, a transactional email sender/captured-mail
adapter, and deployment secret provisioning are external prerequisites. They
remain explicitly unverified until evidenced; no placeholder is treated as
approval.
