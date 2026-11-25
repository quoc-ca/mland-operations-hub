### 10.7 Fulfil Custom Order

#### Primary Actors

- Staff
- Manager

#### Secondary Actors

- None

#### Description

This use case (use case 73) records the handover of a finished custom item, by Member pickup or by Staff's manual handoff to GHTK, and completes the order.

#### Preconditions

1. The actor is signed in as Staff or Manager.
2. The order is ready for handover (use case 72).

#### Normal Flow

**Fulfil Custom Order**

1. The actor selects the ready order.
2. The shop hands the item to the Member, or hands it to GHTK for delivery.
3. The actor confirms that the order is complete.
4. The system records the delivery and closes the order.

#### Alternative Flows

**Step 3 — Actor cancels the confirmation**

The order stays ready for handover.

**Step 4 — Save fails**

The system shows an error and keeps the previous status.

#### Postconditions

- The order is closed with a delivered status.

#### Business Rules

- BR-73-01
- BR-73-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-73-01 | Only a paid, finished order can be handed over and closed. |
| BR-73-02 | GHTK is a manual handoff; the platform does not track the custom delivery. |
