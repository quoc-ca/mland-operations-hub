### 10.4 View Custom Order

#### Primary Actors

- Member
- Staff
- Manager

#### Secondary Actors

- None

#### Description

This use case (use case 70) lets a Member view their own custom orders, and lets Staff and Managers view all custom orders for operations.

#### Preconditions

1. The actor is signed in with one of the listed roles.

#### Normal Flow

**View Custom Order**

1. The actor opens the custom order list.
2. The system returns the own orders to a Member, or all orders to Staff and Managers.
3. The actor selects an order.
4. The system shows the status, quote, design information and production and handover progress.

#### Alternative Flows

**Step 2 — No order**

The system shows an empty state.

**Step 4 — Member does not own the order**

The system denies access.

**Step 4 — Order does not exist**

The system reports that it was not found.

#### Postconditions

- No order, quote or payment data is changed.

#### Business Rules

- BR-70-01
- BR-70-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-70-01 | A Member sees only own orders. |
| BR-70-02 | Viewing is read-only; Admin and Guest have no business view of custom orders. |
