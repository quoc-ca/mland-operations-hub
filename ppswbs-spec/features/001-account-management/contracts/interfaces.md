# Account Management Internal Interfaces

> **R-INV interface extension:** [canonical boundary](../design/revision-2026-11-24.md). FE-001 exposes transient materialization for FE-007 worker attempts and authenticated ADMIN manual-content retrieval after two definite failures; both require a current, unexpired invitation version and never persist its URL in FE-007. Retail and workshop booking owners expose read-only, Account-ID-scoped inventory snapshots and definitive nonterminal-obligation outcomes for ADMIN review/JSON. Retail includes its server-stored cart; there is no server workshop-cart facade. FE-001 cannot traverse another module's repositories. Both obligation outcomes must be AVAILABLE and empty before MEMBER retirement; UNAVAILABLE blocks. Provider/DB/email partial outcomes use persisted reconciliation stage and correlation ID. No consumer may treat an email match as authority to attach a UID.

**Date created**: 2026-10-26
**Date updated**: 2026-11-24
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

## `accountmanagement::facade`

The general named facade is the default cross-feature Java boundary. R-INV adds
the narrowly scoped `accountmanagement :: invitationdelivery` named interface below.
The general facade publishes
`CurrentAccountActor` and verifier/resolver operations used by the shared
identity gate. It does not publish JPA entities, repositories, Firebase SDK
types, raw UID/token/email, or cross-feature capabilities. The R-INV delivery
event carries only its approved recipient email as the explicit exception.

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

## FE-003.1 internal alert recipients (FR-045, approved 2026-11-14)

This method is an approved PII exception to the general facade principle that
email is not exposed across feature boundaries. It exists solely for
003.1 FR-040a: when a late payment enters the Manager review queue, the
Workshop Booking feature must alert every active Manager by internal email.
The exception is narrow and bounded by the contract below.

| Method | Inputs | Typed result | Contract |
| --- | --- | --- | --- |
| `listActiveManagerEmails` | *(none)* | `List<String>` | Login emails of every Account with `role = MANAGER` and `status = ACTIVE`, de-duplicated and sorted. `ADMIN` is excluded. Empty list when none qualifies. Read-only; creates no Account audit event. |

**PII usage constraint:** the caller MUST use the returned list only inside its
own transaction to compose internal alert email recipients. The list MUST NOT
appear in domain events, audit records, application or provider logs, or any
consumer-owned persistence. The `LatePaymentReviewQueued` event published by
003.1 contains only the opaque booking/payment reference, never recipient
addresses.

**Eligibility note:** no condition beyond `status = ACTIVE` is required.
FR-013 guarantees that an `ACTIVE` internal Account has a verified email.
FR-019/FR-020 guarantee that a suspended Account has `status = SUSPENDED`
before restoration. `revocationPending` or unverified-email edge cases do not
apply to `ACTIVE` Managers under the current specification.

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
- For R-INV, Account persists and publishes the invitation outbox event; FE-007
  consumes it after commit and owns delivery, retries and message outcome.
  Account owns the identity decision, secret state and audit.

# R-INV FE-007 delivery boundary (2026-11-24)

FE-001 publishes `accountmanagement :: invitationdelivery` as its only invitation delivery named interface. `InternalAccountInvitationRequested` contains invitation ID/version, Account ID, approved recipient name/email, event reference and correlation reference; FE-001 persists an outbox row in the Account transaction and replays it after commit. The event contains no secret or URL. FE-007 consumes it idempotently, so FE-001 has no dependency on FE-007 Java types.

`InvitationDeliveryFacade.materialize(invitationId, version, requestId, attemptId, purpose)` returns typed `AVAILABLE(transientUrl, expiresAt)`, `STALE`, or `UNAVAILABLE`. Purpose is `SMTP_ATTEMPT` or `MANUAL_CONTENT`; the latter is valid only for an authenticated ADMIN after FE-007's two definite failures and terminal `FAILED`. It authorizes the current, unexpired, non-consumed version and exact request/attempt scope; an old version can never be delivered after rotation. `reportDeliveryState(invitationId, version, requestId, state, observedAt)` idempotently records safe `SENT`, `FAILED` or reconciled `UNKNOWN_OUTCOME` facts but never changes the creation-based `expiresAt`. FE-007 does not persist or log the transient URL; manual retrieval is audited without message content.

The existing generic Notification facade remains separate. Module direction for this invitation is `notificationmanagement -> accountmanagement :: invitationdelivery`, with no Account-to-Notification import. See FE-007 [notification facade](../../007-notification-management/contracts/notification-facade.md) §6 and [R-INV design](../design/revision-2026-11-24.md).
