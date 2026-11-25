# Feature Specification: Staff Consultation Chat

**Date created**: 2026-11-18  
**Date updated**: 2026-11-25
**Author**: Ngô Tiến Đạt  
**Last updated by**: Ngô Tiến Đạt  
**Feature**: `013-staff-consultation-chat`  
**Status**: MEMBER-ONLY REBASELINE — 2026-11-25

## 1. Objective and boundary

Feature-013 provides a bilingual text conversation between an authenticated active
Member and Mland Staff or Manager for design consultation. It preserves customer context
and staff responses in one auditable conversation without making business decisions.

It does not approve/reject a design, publish a price, create a booking, mutate a
payment/order, replace Feature-004 AI Support, or replace Feature-004 human review.

## 2. Actors and permissions

| Actor | Permission |
|---|---|
| Member | Create a conversation from an owned valid context, send/read own messages, close/reopen own conversation. |
| Staff | Read/claim/release/send/close/reopen conversations within current active Workshop Location scope. |
| Manager | Global read/claim/release/assign/send/close/reopen authority; may route an unassigned context to a branch. |
| Admin | No business chat access. Technical/audit access only if a separate audit facade authorizes it. |
| Account facade | Supplies current role/status and Staff location scope; it does not own chat data. |
| Context facades | Validate ownership, version, status and safe preview for Feature-004/012 contexts. |

The backend is authoritative for role, status, ownership, branch scope and context
validity. Client-supplied role, owner, branch, display code or preview is ignored.

## 3. Context attachment

### 2026-11-22 saved-design entry point

The saved-design page may open a consultation directly with a
`WORKSHOP_RING_DESIGN` context. The backend validates the Member identity and
design ownership before creating the conversation. Only a
safe display projection is exposed to Staff/Manager; raw database IDs, private
customer data and full design payloads are not returned. A Member may also start
a contextless consultation. The conversation remains independent of booking,
payment and custom-order approval.

Allowed context types are:

- `WORKSHOP_RING_DESIGN` — Feature-012 configured design version.
- `INDEPENDENT_DESIGN_REQUEST` — Feature-004 request/version.

`GET /context-options` returns safe options owned by the current Member. Each option
contains `contextReference`, `displayCode`, `displayName`, `designVersion` and a safe
preview projection. The UI must display code, name, version and preview together.

On create, the backend revalidates the reference and version, then stores an immutable
context snapshot. A later design change cannot silently change an existing conversation.
Unknown, foreign, withdrawn, archived or stale contexts are rejected without revealing
another Member's data.

## 4. Message rules

- Message content is plain text only; HTML, script and markup are not interpreted.
- Normalize Unicode to NFC before validation and persistence.
- Empty/whitespace messages are invalid.
- Maximum length is 2,000 Unicode code points, not bytes.
- A Member may send 30 messages per rolling minute.
- A rejected rate-limited message is not persisted.
- A duplicate command with the same `Idempotency-Key` returns the original message result.
- A command key reused with a different payload returns `409 IDEMPOTENCY_KEY_REUSED`.
- Messages are immutable and cannot be edited or deleted in V1.

## 5. Conversation lifecycle

```text
OPEN → CLOSED
CLOSED → OPEN
```

Only an authorized participant may close/reopen. Sending while `CLOSED` is rejected.
Claim, release and assignment do not change message history. A conversation has at
most one current claimant; concurrent claims use optimistic version checking.

## 6. Branch and claim policy

- Staff inbox results are filtered by current active Workshop Location scope.
- Manager inbox is global.
- A context without a resolved branch is Manager-visible only.
- Manager may assign such a conversation to a valid active branch.
- Staff may claim only after assignment is within their scope.
- A client cannot widen scope through query parameters or guessed references.

## 7. Logging, privacy and retention

Transcript data is retained for three years under the approved business-history
policy. Audit events retain actor, action, outcome, target reference, timestamp,
correlation ID, context type, message length/hash and safe reason code. Raw message,
email, phone, Firebase token, authorization header and private preview URL are not
written to application logs or audit metadata.

Storage uses UTF-8/`utf8mb4`; API responses use UTF-8 JSON. Retention cleanup must
not remove audit evidence before its approved retention deadline or cascade-delete
conversation history accidentally.

## 8. Error contract

| Code | HTTP | Meaning |
|---|---:|---|
| `CHAT_CONTEXT_INVALID` | 400 | Context type/reference/version is malformed or unavailable. |
| `CHAT_MESSAGE_INVALID` | 400 | Empty or invalid text. |
| `CHAT_MESSAGE_TOO_LONG` | 400 | More than 2,000 Unicode code points. |
| `CHAT_AUTHENTICATION_REQUIRED` | 401 | Firebase identity is absent/invalid. |
| `CHAT_ACCESS_DENIED` | 403 | Ownership, Staff scope, status or role denied. |
| `CHAT_NOT_FOUND` | 404 | Resource is not visible to the caller. |
| `CHAT_CONTEXT_STALE` | 409 | Context version is no longer current. |
| `CHAT_STATE_CONFLICT` | 409 | Stale conversation version or concurrent claim/action. |
| `CHAT_IDEMPOTENCY_CONFLICT` | 409 | Same command key was reused with a different payload. |
| `CHAT_CONVERSATION_CLOSED` | 409 | Message command targets a closed conversation. |
| `CHAT_RATE_LIMITED` | 429 | Member exceeded 30 messages in one rolling minute. |
| `CHAT_UNAVAILABLE` | 503 | Temporary database/context-facade failure. |

## 9. Acceptance criteria

1. Member can select an owned context and sees matching code/name/version/preview.
2. A foreign, nonexistent or stale context cannot create a conversation.
3. Staff sees only assigned conversations in the current branch.
4. Manager can see and claim global conversations and assign unscoped context to a branch.
5. Member, Staff and Manager can exchange Vietnamese text without encoding corruption.
6. Duplicate send retry creates one message only.
7. Rate limit returns `429` and does not persist the rejected message.
8. Closed conversations reject new messages and preserve history.
9. Audit proves send/claim/assign/close/deny actions without raw message text.
10. Feature-013 never mutates booking, payment, design approval, catalogue price or order state.

## 10. Explicit non-goals

- Guest chat.
- AI participation or AI-generated staff replies.
- Attachments, reference-image uploads, files, links or rich text.
- Email notifications, notification center, push notifications, WebSocket/SSE.
- Threaded messages, reactions, read receipts visible to customers beyond unread state.
- Booking/order/payment support actions or final quote approval.

## 11. Sidebar, contextless conversations and Member-side hiding

The sidebar lists only `OPEN` and `CLOSED` conversations. Member-hidden
conversations are excluded from Member list/detail responses but remain visible
to Staff and Manager. Listing uses opaque cursor pagination with default limit
20 and maximum limit 50; polling must merge by conversation reference without
duplicating items or losing the selected conversation.

Member may create a conversation without a design. In that case
`contextType`, `contextReference`, `contextVersion`, and `contextSnapshot` are
all null. Partial context is invalid. An unassigned contextless conversation is
Manager-visible until assigned to a valid Workshop Location.

Member may soft-hide only an owned `CLOSED` conversation. The command requires
confirmation in the UI, `Idempotency-Key`, and `If-Match-Version`. An `OPEN`
conversation returns `CHAT_DELETE_REQUIRES_CLOSED`. Soft hiding never deletes
messages, claims, read states, or audit history and has no Member restore action
in V1.

Staff may close conversations within active Workshop Location scope. Manager
may close any authorized conversation. Both actions remain protected by
optimistic locking and audit evidence.

# Route masking synchronization (2026-11-20)

`/operations/consultations` has browser allowlist `STAFF` and `MANAGER`; direct navigation by `MEMBER`, `ADMIN`, or `CARRIER` is replaced to `/404` before inbox data is requested. The `STAFF` location-scope and `MANAGER` global authority checks remain server-side; this route rule does not authorize access to an individual consultation.

## 12. Frontend consistency and stable-scroll contract — 2026-11-24

The general consultation and Feature-004 design-context consultation use the
same chat surface geometry. Contextual headers may show a safe design
projection, but they must not create a narrower or wider chatbox. The shared
contract includes the same desktop maximum width, mobile behavior, header
height, message padding, composer spacing and sidebar width reservation.

Loading a conversation, switching conversation, polling, refocusing the tab,
changing visibility, or rendering an empty/loading/error state must not move
the page or message thread to the bottom. A successful explicit send may bring
the sent message into view. When polling receives a new inbound message while
the reader is not at the bottom, the client preserves the current position and
presents a visible new-message affordance. Staff and Manager screens follow the
same rule.

The implementation must preserve UTF-8 Vietnamese text, shared i18n behavior,
and equal sidebar width for loading, empty, error and loaded states.

