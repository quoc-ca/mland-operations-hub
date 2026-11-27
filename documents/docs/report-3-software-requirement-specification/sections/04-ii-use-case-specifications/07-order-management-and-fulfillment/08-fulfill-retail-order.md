### 7.5 Fulfill Retail Order

| Use Case ID | UC-59 | Use Case Name | Fulfill Retail Order |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff; Manager | Secondary Actors | Member |

#### Description

As branch Staff or a delegated Manager, I want to review a verified paid retail order and prepare it along the chosen pickup or GHTK chain so that the Member can collect or receive it.

#### Trigger

A Staff member or delegated Manager opens a paid retail order in the Đơn hàng cần chuẩn bị tab.

#### Preconditions

1. The platform is available and the retail order exists.
2. The actor is authenticated with verified identity, has an active account, and holds current authority.
3. The actor is assigned to the processing branch or holds applicable Manager action/branch delegation.
4. The order is verified paid, with the pickup or GHTK method and recipient data chosen at checkout (UC-54).

#### Postconditions

1. Successful preparation is recorded once with safe actor and time evidence; method and recipient data stay frozen.
2. The order follows only the chosen chain to completion without repricing or changing branch.
3. No new shipping fee, GHTK shipment API call, or new payment is created after checkout.
4. Rejected attempts preserve prior facts; no human paid override, new hold, or false transition occurs.

#### Normal Flow

1. Actor opens a paid retail order in the Đơn hàng cần chuẩn bị tab within the authorized branch.
2. System rechecks action and branch authority and retrieves payment, hold, and frozen purchase facts.
3. Actor reviews the Đang chuẩn bị hàng order, packs it, and selects Đã chuẩn bị xong.
4. System rechecks account, branch authority, verified paid state, method, data, and order state.
5. System marks the pickup order Chờ khách nhận and records attributable evidence (MSG83).
6. Member sees Sẵn sàng nhận hàng in Purchase Order History and collects the order at the counter (UC-60).
7. System records the received time while preserving frozen price and branch.
8. System moves the order to the Đã giao tab with paid and received times.

#### Alternative Flows

5a. Order uses GHTK delivery → System marks it Chờ ĐVVC lấy hàng (UC-61); the GHTK partner records Đã lấy hàng and Giao thành công, and System records the received time.

5b. Long wait before the actual GHTK handoff → System keeps only the required recipient name, phone, and address until handoff, starting the 30-day retirement clock then.

#### Exception Flows

2a. Wrong branch, revoked delegation, or unauthorized role → System denies access without mutation or private exposure; Admin, Guest, and Member cannot prepare orders.

2b. Processing branch became inactive → System preserves the branch and original deadlines without silent transfer, cancellation, or refund; currently authorized Staff or delegated Manager may complete paid orders.

4a. Pending, expired, or exception-only payment, or missing method data → System rejects preparation and preserves state; Staff cannot assert payment.

4b. Already preparing, later milestone, terminal state, or stale conflict → System acknowledges an identical accepted effect without change and rejects incompatible or stale updates.

5c. Staff tries to record a courier step → System rejects the attempt; only the GHTK partner account records courier pickup and delivery.

6a. Member asks to change the method after Đặt hàng → System keeps the existing choice; a paid order allows no method reset or branch transfer.

8a. Read or save failure → System reports a safe error and preserves accepted facts without a false transition.

#### Business Rules

BR-59-01, BR-59-02, BR-59-03, BR-59-04, BR-59-05, BR-59-06, BR-59-07, BR-59-08, BR-59-09, BR-59-10

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG83

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-59-01 | Only assigned-branch Staff or an explicitly action/branch-delegated Manager may prepare an order; role names or price approval alone grant no authority. |
| BR-59-02 | Preparation requires verified paid status and valid method and recipient data; human confirmation cannot assert payment or reprice the order. |
| BR-59-03 | Only the Member chooses method and recipient data at checkout; they are frozen after acceptance, and internal actors cannot change or impersonate that choice. |
| BR-59-04 | Support pickup or domestic GHTK; GHTK requires valid name, phone, and address with an optional note under the shared field limits, from a saved or new address. |
| BR-59-05 | Only the chosen method's chain is permitted: pickup Đang chuẩn bị hàng → Chờ khách nhận → Hoàn thành; GHTK Đang chuẩn bị hàng → Chờ ĐVVC lấy hàng → Đã bàn giao cho ĐVVC → Hoàn thành. Later progress never changes the branch, frozen totals, shipping fee, payment, or hold. |
| BR-59-06 | Retain necessary pending GHTK data and retire it 30 days after the original actual handoff. |
| BR-59-07 | GHTK is called only for the delivery-fee quote; no GHTK shipment API, provider tracking, delivery-failure/return, or cancellation/refund workflow is provided. |
| BR-59-08 | Identical recorded effects return a successful no-change; stale or conflicting requests are rejected for review without duplicate transitions. |
| BR-59-09 | Inactive branches may finish accepted paid orders under current branch-scoped authority; no silent transfer, cancellation, or refund is created. |
| BR-59-10 | Record each attempt and its actual outcome under the shared audit policy. |
