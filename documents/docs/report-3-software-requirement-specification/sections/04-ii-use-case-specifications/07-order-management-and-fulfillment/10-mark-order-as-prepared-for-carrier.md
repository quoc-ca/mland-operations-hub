### 7.7 Mark Order as Prepared for Carrier

| Use Case ID | UC-61 | Use Case Name | Mark Order as Prepared for Carrier |
| --- | --- | --- | --- |
| Created By | ThinhBT | Created Date | 20/11/2026 |
| Primary Actors | Staff; Manager; GHTK partner (Đối tác vận chuyển) | Secondary Actors | Delivery Service; Mail Gateway |

#### Description

As an authorized shop actor, I want to mark a packed paid GHTK order ready for the courier, and as the GHTK partner I want to record the courier pickup and the delivery, so that each step of the parcel is attributable across both retail and custom manufacturing orders.

#### Trigger

A shop actor opens a paid GHTK order in Đơn hàng cần chuẩn bị to pack and mark it ready for the courier.

#### Preconditions

1. The platform is available and the retail or custom order exists.
2. The actor is authenticated with verified identity, has an active account, and holds current authority.
3. Shop actors have assigned-branch or Manager delegation; the GHTK partner uses an active CARRIER account.
4. The order is verified paid and GHTK-selected with necessary recipient name, phone, and address.

#### Postconditions

1. Shipment moves READY_FOR_CARRIER → HANDED_TO_CARRIER → DELIVERED once each with tracking number, actor, and time.
2. Original actual handoff starts the 30-day recipient-data retirement clock; retries do not reset it.
3. No GHTK shipment API, provider tracking, new payment, repeat deduction, or return is created.

#### Normal Flow

1. Staff opens the paid GHTK order in Đơn hàng cần chuẩn bị within authorized branch scope.
2. System checks account/branch authority, verified paid state, fixed GHTK method, and preparation status.
3. Staff packs the order and presses Đã chuẩn bị xong.
4. System records READY_FOR_CARRIER and displays Chờ ĐVVC lấy hàng to Staff and Chờ GHTK lấy hàng to Member.
5. GHTK partner collects the parcel at the branch and presses Đã lấy hàng on the partner portal `/carrier/orders`.
6. System rechecks partner account, state, and version, then records HANDED_TO_CARRIER once.
7. GHTK partner delivers the parcel and presses Giao thành công.
8. System rechecks state and version, records DELIVERED once, and sets received time and 30-day data retirement.
9. System displays Hoàn thành to Staff and Đã giao to Member.

#### Alternative Flows

8a. Recipient entry is old or 30-day retirement becomes due → System keeps required recipient data while handoff is pending, then deletes/obscures recipient data and copies 30 days after original actual handoff while keeping minimized custody/payment evidence.

#### Exception Flows

2a. Unauthorized, wrong branch, or revoked delegation → System rejects without mutation or exposure; GHTK partner sees only courier steps and never sees prices, vouchers, points, or Member account data.

2b. Unpaid, unprepared, pickup order, or incompatible terminal state → System rejects without skipping readiness or moving backwards.

2c. Processing branch became inactive → System preserves the branch and deadlines without silent transfer or refund; currently authorized Staff/delegated Manager may complete paid orders.

3a. Missing recipient data or invalid domestic Vietnam fields → System rejects with correction guidance (name 1–100 chars, address 10–500 chars, optional note ≤ 500 chars, 10-digit 0/+84 mobile); method/recipient edits remain frozen after preparing.

5a. Packing recorded but no actual GHTK handoff → System retains READY_FOR_CARRIER until Đã lấy hàng is recorded by the GHTK partner; Staff cannot record Đã lấy hàng or Giao thành công.

6a. Already-recorded handoff, duplicate, or stale shipment version → System returns no-change for an identical accepted effect and rejects stale/conflicting updates, asking the actor to reload.

8b. Read/save failure or uncertain recording outcome → System reports a safe error and preserves accepted custody/payment facts without fabricating completion.

#### Business Rules

BR-61-01, BR-61-02, BR-61-03, BR-61-04, BR-61-05, BR-61-06, BR-61-07, BR-61-08

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-61-01 | Only assigned-branch Staff or explicitly action/branch-delegated Manager packs and marks ready; only the GHTK partner account records the courier pickup and the delivery. |
| BR-61-02 | Đã chuẩn bị xong records READY_FOR_CARRIER; Đã lấy hàng records HANDED_TO_CARRIER afterward; Giao thành công records DELIVERED. Packing alone never proves carrier custody. |
| BR-61-03 | Courier pickup requires verified paid GHTK readiness, valid recipient data, the current shipment version, actor and actual pickup time; the platform tracking number identifies the parcel. The carrier portal `/carrier/orders` manages parcels originating from retail orders and custom manufacturing orders. |
| BR-61-04 | Delivered is terminal for shop fulfilment; no backward/cross-terminal override, GHTK shipment API, provider tracking, delivery-failure/return or cancellation/refund workflow is added. |
| BR-61-05 | Keep necessary recipient data while handoff is pending; retire private values/copies 30 days after original actual handoff, without resetting on retries. |
| BR-61-06 | Keep minimized custody/payment/audit evidence under five-year retention without preserving retired recipient/contact/address values. |
| BR-61-07 | Identical recorded handoff returns successful no-change; reject conflicts without another handoff, settlement or retirement-clock reset. |
| BR-61-08 | Record each attempt and its actual outcome under the shared audit policy. |
