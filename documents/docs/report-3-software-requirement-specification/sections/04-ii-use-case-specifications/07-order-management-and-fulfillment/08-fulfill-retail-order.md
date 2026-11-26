### 7.8 Fulfill Retail Order

| Use Case ID | UC-59 | Use Case Name | Fulfill Retail Order |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member | Secondary Actors | None |

#### Description

As a Member, I want the pickup or GHTK choice and recipient data I gave at checkout recorded on each paid order and its progress shown so that I know when to collect it or expect delivery.

#### Trigger

A Member completes checkout (UC54) and verified payment (UC55) for a retail order.

#### Preconditions

1. The platform is available and the retail order exists.
2. The Member is authenticated with verified identity, has an active account, and owns the verified paid order.
3. Method and recipient data were chosen at checkout (UC54) and cannot change after acceptance.
4. The processing branch was fixed at checkout and cannot be replaced here.

#### Postconditions

1. The paid order carries pickup or GHTK with required data; a GHTK order carries one shipment with a platform tracking number.
2. Authorized preparation continues along the chosen chain without repricing or changing branch.
3. No new shipping fee, GHTK shipment API call, or new payment is created after checkout.

#### Normal Flow

1. Member selects store pickup with branch choice at checkout (UC54).
2. System records the method on the paid branch order.
3. System displays the fixed branch, chosen method, and current step in Purchase Order History.
4. Staff prepares the order (UC58) and marks it Chờ khách nhận.
5. Member sees Sẵn sàng nhận hàng and collects the order at the counter (UC60).
6. System records the received time while preserving frozen price and branch.
7. System moves the order to the Đã giao tab with paid and received times.

#### Alternative Flows

1a. Member selects GHTK delivery with a saved or new domestic Vietnam address at checkout → System records one prepaid shipment with a platform tracking number after verified payment, Staff marks it Chờ ĐVVC lấy hàng (UC61), GHTK partner records Đã lấy hàng (Đang giao hàng) and Giao thành công (Đã giao), and System records the received time.

6a. Long wait before actual GHTK handoff → System keeps only required recipient name, phone, and address until original actual handoff, starting the 30-day retirement clock at handoff.

#### Exception Flows

1b. Invalid method or incomplete GHTK data → System identifies invalid fields at checkout (name 1–100 chars, address 10–500 chars, optional note ≤ 500 chars, 10-digit 0/+84 mobile) for correction before Đặt hàng.

2a. Wrong owner, invalid account, or unpaid/expired order → System rejects without exposing another order, accepting data, or changing payment facts.

2b. Member wants a different method after Đặt hàng → System rejects changes and retains the existing choice; unpaid orders may be cancelled and reordered, while paid orders allow no reset or branch transfer.

3a. Processing branch became inactive → System preserves the recorded branch, history, and deadlines without silent relocation, allowing currently authorized Staff/delegated Manager to complete paid orders.

4a. Staff tries to record a courier step → System rejects the attempt; only the GHTK partner account records courier pickup and delivery.

6b. Save failure → System reports a safe error and preserves previously accepted data and payment facts.

#### Business Rules

BR-59-01, BR-59-02, BR-59-03, BR-59-04, BR-59-05, BR-59-06, BR-59-07

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-59-01 | Only the active owning Member chooses method and recipient data at checkout; they cannot change after acceptance, and internal actors cannot impersonate the Member. |
| BR-59-02 | Support pickup or domestic GHTK; GHTK requires valid name, phone and address with optional note under the shared field limits, from a saved address or a new one. |
| BR-59-03 | The method and GHTK fee are frozen at checkout; later progress never changes the branch, frozen prices/totals, shipping fee, payment or hold. |
| BR-59-04 | Only the chosen method's chain is permitted: pickup Đang chuẩn bị hàng → Chờ khách nhận → Hoàn thành; GHTK Đang chuẩn bị hàng → Chờ ĐVVC lấy hàng → Đã bàn giao cho ĐVVC → Hoàn thành. |
| BR-59-05 | Retain necessary pending GHTK data and retire it 30 days after original actual handoff. |
| BR-59-06 | GHTK is called only for the delivery-fee quote; no GHTK shipment API, provider tracking, delivery-failure/return or cancellation/refund workflow is provided. |
| BR-59-07 | Record each attempt and its actual outcome under the shared audit policy. |
