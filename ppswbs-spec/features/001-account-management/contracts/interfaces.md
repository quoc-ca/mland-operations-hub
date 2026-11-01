# Account Management Internal Interfaces

**Date created**: 2026-10-26
**Date updated**: 2026-11-01
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

## `accountmanagement::facade`

The named facade is the only cross-feature Java boundary. It publishes
`CurrentAccountActor` and verifier/resolver operations used by the shared
identity gate. It does not publish JPA entities, repositories, Firebase SDK
types, raw UID/token/email, or cross-feature capabilities.

`CurrentAccountActor` contains opaque `accountId`, `role`, `status`, and
`emailVerified`. It does not contain Workshop Location scope. A
missing/invalid/revoked bearer, unavailable verifier, missing Account, or
ineligible state is represented by the shared typed identity outcome, never by
a null actor.

Consuming features receive only this actor and enforce their own action/resource
authority. They must not infer authority from provider claims or import Account
internals. FE-002 derives catalogue permissions from role/status/location under
its own approved contract.

## FE-003 target facts and roster facade

These are the only additional FE-001 methods available to an approved Workshop
consumer. They are Java module-boundary methods, never REST endpoints. The
consumer must authorize its own actor and resource before invoking either method.

| Method | Inputs | Typed result | Contract |
| --- | --- | --- | --- |
| `findTargetFacts` | Canonical opaque `AccountId` | `TargetFactsOutcome` | `FOUND` contains only Account ID, fixed role, lifecycle status and effective `activeWorkshopLocationIds`; `NOT_FOUND` means conclusive absence; `UNAVAILABLE` means Account or required Workshop Location scope cannot be verified. |
| `findEligibleRoster` | Canonical `workshopLocationId`; required role limited to `STAFF` or `MANAGER` | `RosterOutcome` | `AVAILABLE` contains zero or more entries, each limited to Account ID, current Account Profile full name and fixed role; `UNAVAILABLE` means effective scope cannot be verified. Empty `AVAILABLE` is not an error. |

`activeWorkshopLocationIds` means active Account assignments intersected with
currently active authoritative Workshop Locations. It is resolved only for the
location-scoped facade operation, not when building `CurrentAccountActor`.

`TargetFactsOutcome` and `RosterOutcome` are boundary types, not entities/DTOs.
They expose neither email, phone, provider identity, repository/entity types,
raw assignments, generic directory access nor a cross-feature permission
decision. Both methods are read-only and create no Account audit event.

A consumer rechecks `findTargetFacts` immediately before its dependent sensitive
mutation. It maps `NOT_FOUND` or facts failing its own approved rule to generic
`TARGET_NOT_ELIGIBLE`/`409`; it maps `UNAVAILABLE` to `503`, preserves safe
unsaved input, and starts or commits no mutation. It must not use a cache,
fallback scope, background retry or event-synchronized Account copy in V1.

## External boundaries

- Firebase adapter verifies bearer identity and revocation; creates/reconciles
  invited identities; generates fresh setup links; and invalidates sessions.
- Workshop-location facade validates active location IDs and provides permitted
  display summary. It owns location data.
- FE-07 accepts idempotent post-commit notification intents. It owns delivery,
  retries, and message outcome; Account owns business decision/audit.
