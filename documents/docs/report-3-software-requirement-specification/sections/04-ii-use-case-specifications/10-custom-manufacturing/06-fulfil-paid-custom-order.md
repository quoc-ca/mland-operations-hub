### 10.6 Fulfil Paid Custom Order

#### Primary Actors

- Staff
- Manager

#### Secondary Actors

- None

#### Description

This use case (use case 72) lets Staff or a Manager move a fully paid custom order through production until it is ready for handover.

#### Preconditions

1. The actor is signed in as Staff or Manager.
2. The order is paid in full.

#### Normal Flow

**Fulfil Paid Custom Order**

1. The actor opens the custom order operations list and selects a paid order.
2. The actor confirms the start of production.
3. The system records the order as in production.
4. When the item is finished, the actor confirms it is ready for handover.
5. The system records production as completed and the order as ready for handover.

#### Alternative Flows

**Step 2 or 4 — Actor cancels the confirmation**

The status does not change.

**Step 3 or 5 — Save fails**

The system shows an error and keeps the previous status.

**Any step — Actor is not Staff or Manager**

The system denies the change.

#### Postconditions

- The order is in production or ready for handover.

#### Business Rules

- BR-72-01
- BR-72-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-72-01 | Production starts only after the complete quoted amount is verified. |
| BR-72-02 | The stages move forward: waiting for production, in production, ready for handover. |
