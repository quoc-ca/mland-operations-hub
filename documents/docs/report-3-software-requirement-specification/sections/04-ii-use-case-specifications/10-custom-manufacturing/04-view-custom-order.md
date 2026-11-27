### 10.4 View Custom Order

| Use Case ID | UC-70 | Use Case Name | View Custom Order |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member; Staff; Manager | Secondary Actors | None |

#### Description

As a Member, Staff, or Manager, I want to view custom orders within my authorized scope so that I can track quote, design, production, and delivery status with courier tracking.

#### Trigger

An authorized actor opens the custom order list or selects a custom order.

#### Preconditions

1. The actor is signed in as Member, Staff, or Manager.

#### Postconditions

1. The actor sees authorized custom order details, production timeline, and GHTK shipment information or an empty/error state.
2. No order, quote, or payment data is changed.

#### Normal Flow

1. Member opens the custom order list.
2. System retrieves the Member's own custom orders.
3. Member selects an order.
4. System displays status, quote, design details, production progress, and GHTK shipment card (including tracking number `S...`, carrier status, and delivery badge) once fulfillment begins.

#### Alternative Flows

1a. Staff or Manager opens the custom order list → System retrieves all custom orders for operations, including the `QUOTE_PENDING` stage; a Manager may approve a pending quote directly from that list.

2a. No order exists → System displays an empty state.

#### Exception Flows

4a. Member does not own the order → System denies access.

4b. Order does not exist → System reports that the order was not found.

#### Business Rules

BR-70-01, BR-70-02

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-70-01 | A Member sees only own orders. |
| BR-70-02 | Viewing is read-only; Admin and Guest have no business view of custom orders. |
