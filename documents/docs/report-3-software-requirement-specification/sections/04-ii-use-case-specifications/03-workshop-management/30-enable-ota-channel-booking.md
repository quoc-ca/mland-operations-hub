### 3.30 Enable OTA Channel Booking

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to record a readiness decision for a channel mapping so that partner booking is enabled only when every go-live check is verified. This function is gated: no OTA partner is live yet.

#### Preconditions

1. The actor is an active Manager.
2. The mapping is sales-closed.
3. A controlled readiness record exists for the provider, environment, and mapping.

#### Postconditions

- The readiness decision, checklist evidence references, reviewer, and mapping fingerprint are retained.
- Booking is enabled only for an approved decision; otherwise the mapping stays sales-closed.

#### Normal Flow

**Enable OTA Channel Booking**

1. The Manager opens a sales-closed mapping.
2. The Manager enters the readiness record reference, the outcome and evidence reference of each check, and a reason. Each check's generated evidence reference is stable and does not change when its outcome changes.
3. The system verifies the actor, the mapping state, and all twelve checks.
4. The system records an approved decision and enables booking for the mapping.

#### Alternative Flows

**Step 2 — Evidence reference is missing**

A check cannot be marked verified without an evidence reference.

**Step 3 — A check is failed or missing**

The system retains a rejected assessment and keeps the mapping sales-closed.

**Step 3 — Mapping is not sales-closed**

Booking cannot be enabled directly from a draft, preparation, suspended, or disabled mapping.

**Step 3 — Actor is not an active Manager**

The system refuses the request and audits the denied attempt.

**Step 4 — Mapping details change later**

A new readiness assessment is required.

**Step 4 — Synchronization fails or data no longer matches later**

Sales are suspended; after a confirmed resynchronization, a new assessment is needed to reopen.

#### Business Rules

BR-92-01, BR-92-02, BR-92-03, BR-92-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-92-01 | Only an active Manager records the decision; Admin has no authority, and no second approver is required. |
| BR-92-02 | The checks are contract onboarding, commercial terms, connection ownership, callback authentication, quota delivery, product-session mapping, data allowlist, preparation synchronization, ticket delivery and scan, cancellation and refund, late booking and capacity, and failure reconciliation. |
| BR-92-03 | Every check must be verified; the option must deliver one Mland-scannable ticket per participant. |
| BR-92-04 | Production partner callbacks are refused unless the provider is approved after its callback authentication is approved and implemented; provider go-live evidence is still outstanding. |
| BR-92-05 | Each readiness check has a stable generated evidence reference independent of the selected outcome; the reference is not manually editable. |
