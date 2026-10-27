# Account Management Validation Guide

**Date created**: 2026-10-26
**Date updated**: 2026-10-26
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

Run only against disposable MySQL and non-production Firebase/Auth Emulator
configuration. Never use production identities, credentials, or databases.

## Prerequisites

- Required runtime configuration is supplied through environment/secret channel;
  no database credential default is accepted.
- Migration version is reserved and database is empty or a supported V5/V6
  upgrade fixture.
- Auth Emulator or approved non-production Firebase project is available.
- FE-07 and workshop-location test adapters expose deterministic outcomes.

## Scenarios

1. Email/password, Google, and Facebook (when configured) bootstrap the same UID
   repeatedly without duplicate Account; same email/different UID is denied and
   not merged.
2. Unverified Member reaches `/` but profile is denied; after verification and
   token refresh, profile save succeeds. Password/email changes never send a
   password to Mland and email sync reads only current identity.
3. Missing, invalid, expired, revoked, and unavailable identity outcomes return
   safe `401`/`503`; a non-public undeclared route is denied by the common gate.
4. Admin creates Staff and Manager with valid scope, retries a lost response with
   the same idempotency key, and observes one pending Account/invitation only.
5. Pending identity activates only after verified email and valid location;
   invalid/inactive scope denies internal action. Staff one-location and Manager
   multi-location rules are covered.
6. Admin stale profile/role/status command returns `409`; own profile commits
   last successful value.
7. Suspend immediately denies next protected request even when revoke/FE-07
   fails. Restore remains unavailable until revocation completes and requires
   fresh sign-in.
8. Verify safe audit fields, no raw secret/PII, post-commit notification failure
   isolation, facade-only Modulith dependency, MySQL empty/upgrade migration,
   OpenAPI/authorization/error coverage, and React loading/error/retry states.

## Evidence

Record command, revision, Java/MySQL/Firebase environment, scenario result, and
known limitation in the feature validation report. A mock-only or H2-only check
does not prove Firebase/MySQL integration behavior.
