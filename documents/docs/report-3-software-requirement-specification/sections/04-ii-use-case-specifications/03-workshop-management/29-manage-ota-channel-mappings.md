### 3.29 Manage OTA Channel Mappings

#### Primary Actors

Manager

#### Secondary Actors

OTA Partner

#### Description

As a Manager, I want to maintain OTA channel mappings, record confirmed price terms, resynchronize availability, and close channel sales so that a channel receives only approved Mland inventory and terms.

#### Preconditions

1. The actor is an active Manager.
2. The provider connection and credentials are set up as environment configuration.
3. No OTA partner is live yet; a mapping alone does not allow partner booking.

#### Postconditions

- The mapping, price terms, resynchronization request, or sales closure is recorded and audited.
- None of these actions enables booking or commits capacity.

#### Normal Flow

**Manage OTA Channel Mappings**

1. The Manager opens the channel mappings.
2. The Manager creates a draft mapping of a provider product and option to a Mland workshop offer and session.
3. The system validates and saves the mapping.
4. The Manager records the provider-confirmed retail and net prices, effective period, evidence reference, and reason.
5. The system stores them as an unchangeable price-terms record.
6. The Manager requests a preparation synchronization.
7. The system queues the delivery of schedule, availability, and sale state.

#### Alternative Flows

**Step 2 — Update an existing mapping**

Only a draft or sales-closed mapping can be updated; an outdated version, or an identity change while a delivery is pending, is refused.

**Step 3 — Mapping already exists**

An exact duplicate returns the existing draft; the same product and option with different workshop facts is refused.

**Step 4 — Commercial terms are not confirmed**

Nothing is stored and the mapping cannot be published.

**Step 6 — Force a full resynchronization**

The Manager gives a reason and the system queues the current schedule, availability, and sale state.

**Step 6 — Close channel sales**

The Manager gives a reason; Mland closes the mapping immediately and queues the closure to the partner.

**Step 7 — Delivery fails after all retries**

The system closes sales for that mapping only and opens a reconciliation item.

#### Business Rules

BR-91-01, BR-91-02, BR-91-03, BR-91-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-91-01 | Only an active Manager may act, across all locations; Admin has no authority. |
| BR-91-02 | Mland is the sole authority for availability and capacity; the first release publishes only per-person packages. |
| BR-91-03 | Price terms are provider-confirmed and entered in VND; later terms never rewrite an accepted booking. |
| BR-91-04 | By default, delivery uses one send and three retries, waiting 30 seconds, 2 minutes, and 10 minutes. |
