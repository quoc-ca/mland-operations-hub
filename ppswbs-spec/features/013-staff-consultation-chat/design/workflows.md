# Feature-013 Workflows

**Date created**: 2026-11-18  
**Date updated**: 2026-11-18  
**Author**: Ngô Tiến Đạt  
**Last updated by**: Ngô Tiến Đạt

## W1 — Create conversation

1. Member requests context options.
2. Context adapter returns only owned valid projections.
3. Member selects one option; UI shows code/name/version/preview together.
4. Member submits context type/reference/version and optional initial text.
5. Backend authenticates Member, validates context ownership/version/status, normalizes text and validates length.
6. Service creates conversation, immutable context snapshot, optional first message, idempotency record and audit in one transaction.
7. Response returns safe conversation projection.

Failure: invalid/foreign/stale context returns a typed error without creating any row.
Duplicate command returns the original projection; changed payload returns conflict.

## W2 — Send message

1. Actor submits text with `Idempotency-Key`.
2. Backend checks identity, role/status, conversation visibility, `OPEN` state, length and rate limit.
3. NFC-normalized text is appended atomically with idempotency record and audit metadata.
4. Client keeps the message on success and polling observes the other participant's message.

Failure: no partial message/audit row; retry reuses the same key. A rejected rate-limit
attempt does not persist content.

## W3 — Inbox, claim and assignment

1. Staff/Manager requests scoped inbox.
2. Backend resolves current role and scope on every read.
3. Staff receives only assigned-location conversations; Manager receives global results.
4. Claim uses `If-Match-Version`; exactly one concurrent claimant succeeds.
5. Manager may assign an unscoped conversation to a valid active location.
6. Assignment and claim history are committed with audit metadata.

## W4 — Polling and read state

1. Visible inbox/detail page requests data every ten seconds.
2. Latest-request-wins protection prevents stale responses from overwriting newer state.
3. Opening a conversation posts read state with the latest visible message reference.
4. Polling failures preserve current messages and show retry status.

## W5 — Close/reopen

1. Authorized participant sends close/reopen with `If-Match-Version` and idempotency key.
2. Service locks the conversation, verifies current version and transition, updates state and audit atomically.
3. Closed conversation rejects new messages until authorized reopen.

## W6 — Logging and retention

Message content remains only in transcript storage. Application/audit logs receive
safe metadata and correlation IDs. A retention job may process records after three
years only under the approved policy and never cascades required audit evidence.

## W7 — Sidebar pagination

1. The client requests a page with optional status, opaque cursor and limit.
2. Backend applies actor scope, Member-hidden filtering where applicable and stable ordering.
3. The client renders the page and preserves the selected conversation.
4. Load more requests `nextCursor` and merges by conversation reference.
5. Polling refreshes the first page without duplicating loaded items.

## W8 — Contextless conversation

1. Member selects General consultation instead of a design context.
2. Client submits all context fields as null with an optional initial message.
3. Backend validates the all-null shape, creates a null context snapshot and keeps the conversation Manager-visible until assignment.

## W9 — Member soft hide

1. Member selects a CLOSED conversation and requests removal from their list.
2. Client asks for confirmation and sends DELETE with idempotency and optimistic-version headers.
3. Backend verifies ownership, CLOSED state and version, sets the Member-hidden marker, writes `MEMBER_HIDE` audit metadata and commits atomically.
4. Member sidebar removes the item; Staff and Manager operational reads retain it.

## W10 — Operational close

1. Staff or Manager opens an authorized OPEN conversation.
2. Staff passes active-location scope; Manager passes global role authorization.
3. Client confirms close and sends the current aggregate version.
4. Backend transitions to CLOSED, records audit evidence and rejects stale concurrent commands with `409 CHAT_STATE_CONFLICT`.

