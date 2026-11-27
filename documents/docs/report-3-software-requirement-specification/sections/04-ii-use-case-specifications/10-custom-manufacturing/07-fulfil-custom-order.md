### 10.7 Fulfil Custom Order

| Use Case ID | UC-73 | Use Case Name | Fulfil Custom Order |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff; Manager; GHTK Courier (Đối tác vận chuyển GHTK) | Secondary Actors | Delivery Service; Mail Gateway |

#### Description

As an authorized shop actor, I want to pack a finished custom order and mark it ready for the carrier, and as the GHTK courier partner, I want to record parcel pickup and successful delivery so that custom manufacturing delivery is attributable and automatically closes the order.

#### Trigger

Staff or a Manager opens a paid custom order in the ready-for-fulfilment stage (`READY_FOR_FULFILMENT`) to pack and mark it ready for courier pickup.

#### Preconditions

1. The actor is signed in as Staff, Manager, or an authenticated GHTK partner with an active `CARRIER` role.
2. The custom order has completed manufacturing and is in the ready-for-fulfilment stage (`READY_FOR_FULFILMENT`).
3. An active custom shipment record (`custom_order_shipments`) with a system-generated GHTK tracking number (`S...`, 10 digits) has been created.
4. Valid recipient delivery data (name, 10-digit mobile number, domestic delivery address) is attached.

#### Postconditions

1. The custom shipment moves through `READY_FOR_CARRIER` → `HANDED_TO_CARRIER` → `DELIVERED` with attributable tracking number, actor, and timestamp.
2. When the courier records `DELIVERED`, the system automatically executes terminal synchronization: `CustomOrder.status` becomes `CLOSED`, `fulfilmentStatus` becomes `DELIVERED`, and production progress is marked completed.
3. The custom order becomes eligible for product review under UC-75.
4. Staff direct closure without courier delivery is strictly blocked.

#### Normal Flow

1. Staff opens the custom order in `READY_FOR_FULFILMENT` on the custom order operations screen.
2. System displays order details, customer delivery address, and the generated GHTK tracking number (`S...`).
3. Staff packs the finished custom jewellery piece and clicks **Đóng gói xong & Bàn giao bưu tá GHTK**.
4. System records the shipment status as `READY_FOR_CARRIER` and displays "Chờ bưu tá GHTK lấy hàng" to Staff and "Chờ GHTK lấy hàng" to Member.
5. GHTK courier partner collects the parcel at the workshop branch, opens `/carrier/orders`, and clicks **Lấy hàng**.
6. System checks partner authority and version, records `HANDED_TO_CARRIER`, and updates the order display to "Đang vận chuyển" (`DELIVERING`).
7. GHTK courier partner delivers the parcel to the customer and clicks **Giao thành công**.
8. System records `DELIVERED` on the custom shipment, automatically sets `CustomOrder.status = CLOSED`, `fulfilmentStatus = DELIVERED`, and records actual delivery timestamp.
9. System displays "Đã giao hàng" and "Hoàn tất" to Member, Staff, and Manager.

#### Alternative Flows

2a. Customer chose in-store pickup at order creation → Staff verifies customer identity at the workshop counter, records in-person handover, and System records store pickup delivery and closes the order.

#### Exception Flows

3a. Missing recipient data or invalid mobile number → System displays validation error and prevents advancing to `READY_FOR_CARRIER` until corrected.

4a. Staff attempts to directly close or complete the order without courier delivery → System rejects the command with HTTP `409 CONFLICT` (`CUSTOM_ORDER_STATUS_CONFLICT`, current backend error code) and preserves the shipment/order state.

5a. Unauthorized non-carrier actor accesses carrier portal `/carrier/orders` → System masks with HTTP 404 / access denied.

6a. Stale shipment version or concurrent status change → System rejects conflicting update and instructs the actor to reload.

#### Business Rules

BR-73-01, BR-73-02, BR-73-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-73-01 | Only a paid, finished custom order can enter the fulfillment and delivery lifecycle. |
| BR-73-02 | Custom order GHTK delivery integrates through dedicated `custom_order_shipments` records with standard 10-digit tracking numbers (`S...`). Packing is recorded by Staff (`READY_FOR_CARRIER`), while courier pickup (`HANDED_TO_CARRIER`) and delivery (`DELIVERED`) are recorded exclusively by the authenticated carrier partner account. |
| BR-73-03 | Terminal delivery synchronization: a custom order is closed (`CLOSED`) upon verified courier delivery (`DELIVERED`); direct shop-side closure without delivery verification is rejected. |
