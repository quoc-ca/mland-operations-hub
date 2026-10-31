# Account Management Internal Interfaces

**Date created**: 2026-10-26
**Date updated**: 2026-10-26
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

## `accountmanagement::facade`

The named facade is the only cross-feature Java boundary. It publishes
`CurrentAccountActor` and verifier/resolver operations used by the shared
identity gate. It does not publish JPA entities, repositories, Firebase SDK
types, raw UID/token/email, or cross-feature capabilities.

`CurrentAccountActor` contains opaque `accountId`, `role`, `status`,
`emailVerified`, and `activeLocationIds`. A missing/invalid/revoked bearer,
unavailable verifier, missing Account, or ineligible state is represented by
the shared typed identity outcome, never by a null actor.

Consuming features receive only this actor and enforce their own action/resource
authority. They must not infer authority from provider claims or import Account
internals. FE-002 derives catalogue permissions from role/status/location under
its own approved contract.

## External boundaries

- Firebase adapter verifies bearer identity and revocation; creates/reconciles
  invited identities; generates fresh setup links; and invalidates sessions.
- Workshop-location facade validates active location IDs and provides permitted
  display summary. It owns location data.
- FE-07 accepts idempotent post-commit notification intents. It owns delivery,
  retries, and message outcome; Account owns business decision/audit.
