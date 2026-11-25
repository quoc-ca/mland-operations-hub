### 3.31 Resolve OTA Reconciliation Items

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to review and decide OTA reconciliation items so that unresolved delivery, event, and payout differences are handled with an audit record. Items from live partners depend on partner go-live.

#### Preconditions

1. The actor is an active Manager.
2. A reconciliation item exists.

#### Postconditions

- The decision and reason are appended to the audit history.
- The item and any affected mapping show the resulting state.

#### Normal Flow

**Resolve OTA Reconciliation Items**

1. The Manager opens the reconciliation items and filters them if needed.
2. The system lists synchronization, recovery, inbound-event, and payout dispute items.
3. The Manager reviews an item.
4. The Manager chooses a decision and enters a reason.
5. The system records the decision and updates the item.

#### Alternative Flows

**Step 4 — Request resynchronization**

For an open mapping item, the system queues the current full snapshot, sets the item in progress, and keeps the mapping suspended.

**Step 4 — Accept a full resynchronization**

The system resolves the item and returns the mapping to sales-closed only when all snapshot parts are delivered and the source version is still current.

**Step 4 — Reject or escalate**

A rejected item stays open; an escalated item is marked escalated. A suspended mapping stays suspended.

**Step 5 — Resynchronized data conflicts with recorded data**

The system does not overwrite the record, resolve the item, or release the mapping.

**Step 5 — Request key is reused with a different decision**

The system refuses the request as a conflict.

#### Business Rules

BR-93-01, BR-93-02, BR-93-03, BR-93-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-93-01 | Only an active Manager may view and decide items, across all locations; Admin and Staff are refused. |
| BR-93-02 | Resolving a mapping item never enables booking; a new readiness decision is required. |
| BR-93-03 | An inbound-event item is opened when automation cannot decide, for example an unknown booking, a refund without a cancellation, or a cancellation count above the revocable tickets. |
| BR-93-04 | The first release sends no email or other alert for new items; no shared-voucher fallback or shared-voucher incident item is created. |
