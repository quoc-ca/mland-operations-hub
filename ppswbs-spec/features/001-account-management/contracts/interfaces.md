# Account Management Internal Interfaces

**Date created**: 2026-10-26
**Date updated**: 2026-11-14
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
| `findTargetFacts` | Canonical opaque `AccountId` | `TargetFactsOutcome` | `FOUND` contains only Account ID, fixed role, lifecycle status and effective `activeWorkshopLocationIds`; `NOT_FOUND` means conclusive absence; `UNAVAILABLE` means Account or a required Workshop Location scope cannot be verified. It is for a target or a Staff location-scoped operation, never to establish the current Manager's global authority. |
| `findEligibleRoster` | Canonical `workshopLocationId` | `RosterOutcome` | `AVAILABLE` contains zero or more active `STAFF` entries, each limited to Account ID, current Account Profile full name and fixed role; `UNAVAILABLE` means effective Staff scope cannot be verified. Empty `AVAILABLE` is not an error. It does not select or authorize Managers. |

`activeWorkshopLocationIds` means active Account assignments intersected with
currently active authoritative Workshop Locations. It is resolved only for a
Staff location-scoped facade operation, not when building `CurrentAccountActor`
or authorizing an active Manager action.

`TargetFactsOutcome` and `RosterOutcome` are boundary types, not entities/DTOs.
They expose neither email, phone, provider identity, repository/entity types,
raw assignments, generic directory access nor a cross-feature permission
decision. Both methods are read-only and create no Account audit event.

A consumer rechecks `findTargetFacts` immediately before its dependent target or
Staff location-scoped mutation. It maps `NOT_FOUND` or facts failing its own
approved rule to generic `TARGET_NOT_ELIGIBLE`/`409`; it maps `UNAVAILABLE` to
`503`, preserves safe unsaved input, and starts or commits no mutation. An
active Manager requester is authorized from `CurrentAccountActor` without this
scope lookup. A consumer must not use a cache, fallback scope, background retry
or event-synchronized Account copy in V1.

## FE-008 shipping address facade

These methods provide shipping address lookups to approved order/fulfillment consumers (e.g. FE-008). They are module-boundary Java methods, returning immutable snapshots.

| Method | Inputs | Typed result | Contract |
| --- | --- | --- | --- |
| `findDefaultShippingAddress` | Canonical opaque `AccountId` | `Optional<MemberAddressSnapshot>` | Returns the current default shipping address snapshot for the Member, or empty if none exists. |
| `findShippingAddressById` | Canonical opaque `AccountId`, `Long addressId` | `Optional<MemberAddressSnapshot>` | Returns the verified address snapshot owned by the Member, or empty if not found or belongs to another account. |

`MemberAddressSnapshot` contains: `id`, `recipientName`, `phoneNumber`, `provinceId`, `provinceName`, `districtId`, `districtName`, `wardId`, `wardName`, `hamletId`, `hamletName`, `detailAddress`, `addressType`.
The consuming feature (FE-008) clones this snapshot into its order record at checkout; subsequent profile address edits or deletions in FE-001 do not alter historical orders.

## External boundaries

- Firebase adapter verifies bearer identity and revocation; creates/reconciles
  invited identities; generates fresh setup links; and invalidates sessions.
- Workshop-location facade validates active location IDs and provides permitted
  display summary. It owns location data.
- FE-07 accepts idempotent post-commit notification intents. It owns delivery,
  retries, and message outcome; Account owns business decision/audit.
