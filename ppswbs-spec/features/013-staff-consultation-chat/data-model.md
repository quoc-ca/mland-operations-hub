# Feature-013 Data Model

**Date created**: 2026-11-18  
**Date updated**: 2026-11-18  
**Author**: Ngô Tiến Đạt  
**Last updated by**: Ngô Tiến Đạt

## Ownership

Feature-013 owns conversation, message, claim, read-state and chat-audit records.
It uses Account, Feature-004 and Feature-012 ports; it never reads their repositories.

## Logical tables

### `consultation_conversations`

| Field | Rule |
|---|---|
| `conversation_id` | Internal primary key; never exposed directly. |
| `conversation_reference` | Required opaque unique public reference. |
| `member_user_id` | Required owner FK to the Member account. |
| `context_type` | Nullable; `WORKSHOP_RING_DESIGN` or `INDEPENDENT_DESIGN_REQUEST` when a design is attached. |
| `context_reference` | Nullable opaque safe reference returned by the owning facade. |
| `context_version` | Nullable immutable version selected by Member. |
| `context_snapshot` | Nullable server-built safe JSON snapshot; never client-built. |
| `assigned_location_id` | Nullable until Manager routes an unscoped context. |
| `claimed_by_user_id` | Nullable current claimant; history is separate. |
| `status` | `OPEN` or `CLOSED`. |
| `aggregate_version` | Optimistic concurrency version. |
| `created_at`, `updated_at`, `closed_at` | Required lifecycle timestamps as applicable. |
| `member_hidden_at`, `member_hidden_by_user_id` | Member-side soft-hide marker; never deletes operational history. |

### `consultation_messages`

Stores immutable normalized UTF-8 text, sender account, conversation FK, created time,
correlation ID and command reference. The message body is not copied to audit or logs.

Unique constraint:

```text
(conversation_id, sender_user_id, client_message_reference)
```

### `consultation_read_states`

Stores the latest read message/version for each authorized actor and conversation.
It is a projection, not a replacement for message history.

### `consultation_claim_events`

Append-only claim, release and assign events with actor, previous/current claimant,
previous/current location, reason, outcome, timestamp and correlation ID.

### `consultation_audit_events`

Append-only security/business evidence. Store action, outcome, actor, target,
correlation ID, context type, safe reason code and message length/hash where relevant.
Never store raw message, token, email, phone or private preview URL.

## Invariants

- One conversation has exactly one Member owner and either one immutable context snapshot or an explicitly null context.
- Context target and version cannot be changed after creation.
- A conversation cannot reference an inaccessible or invalid context.
- Staff reads/writes require current active location scope; Manager reads/writes are global.
- No Admin business mutation is allowed.
- Message text is NFC-normalized UTF-8 and max 2,000 Unicode code points.
- Message rows are append-only and are not hard-deleted during retention.
- One current claimant is protected by optimistic locking.
- Audit rows use `ON DELETE RESTRICT` semantics and are not cascade-deleted.
- Retention cleanup never deletes a live conversation while message/audit history remains required.
- Context fields are either all null or all valid; no partial context is persisted.
- Member queries exclude `member_hidden_at IS NOT NULL`; Staff and Manager queries do not.
- Soft hide is allowed only for `CLOSED` conversations owned by the Member.

## Migration requirements

Current repository migration maximum is `V183`; verify `flyway_schema_history` before
selecting a new version, expected to be `V184` or higher. Do not modify applied SQL,
use `baseline`, `repair`, `outOfOrder` or ignore patterns to hide a conflict. Verify
fresh install, upgrade, rerun and `flyway validate()` after approval.

