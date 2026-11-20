### 7.8 Fulfill Retail Order

#### Primary Actors

Member.

#### Secondary Actors

None.

#### Description

As a Member, I want the pickup or GHTK choice and recipient data I gave at checkout recorded on each paid order and its progress shown so that I know when to collect it or expect delivery.

#### Preconditions

1. The platform is available and the retail order exists.
2. The actor is authenticated with verified identity, has an active account, and holds current authority for the requested action.
3. The Member owns the verified paid order. Method/data were chosen at checkout (UC45) and cannot change after acceptance.
4. The processing branch was selected and fixed at checkout; this use case cannot replace it.

#### Postconditions

- The paid order carries pickup or GHTK with necessary data without changing frozen purchase/payment/branch facts; a GHTK order carries one shipment with a platform tracking number.
- Authorized preparation continues along the chosen chain; this use case does not itself assert actual completion.
- No new shipping fee, GHTK shipment API call or new payment is created after checkout.

#### Normal Flow

**Fulfill Retail Order**

1. At checkout the owning Member chose pickup or GHTK for each branch and, for GHTK, a saved or new recipient address (UC45).
2. After verified payment the system records the choice on each paid branch order; for GHTK it records one prepaid shipment with a platform-generated tracking number.
3. The system shows the fixed processing branch, the chosen method and the current step in Purchase Order History.
4. Authorized Staff prepares the order (UC49) and marks it ready: Chờ khách nhận for pickup (UC50), Chờ ĐVVC lấy hàng for GHTK (UC53).
5. Pickup: the Member sees Sẵn sàng nhận hàng and collects the order at the counter (UC52).
6. GHTK: the Member sees Chờ GHTK lấy hàng, then Đang giao hàng once the courier collects the parcel, then Đã giao once the GHTK partner records the delivery (UC53).
7. The system records the received time at the counter handover or the GHTK delivery, without repricing or changing branch.
8. The order moves to the Đã giao tab with its paid and received times.

#### Alternative Flows

**Steps 2 and 6 — Wrong owner, invalid account or unpaid/expired order**

Reject without exposing another order, accepting data or changing payment facts. Staff/Manager/Admin cannot make the Member choice through customer impersonation.

**Step 1 — Invalid method or incomplete GHTK data**

Identify missing/invalid fields at checkout and permit correction before Đặt hàng. GHTK delivery is domestic Vietnam: recipient name 1–100 Unicode characters, address 10–500 Unicode characters, optional note at most 500 Unicode characters, with leading/trailing whitespace trimmed. Contact mobile number is 10 digits starting with 0 or its equivalent +84 form.

**Steps 2 to 6 — Member wants a different method after Đặt hàng**

Reject method/recipient changes and retain the existing choice/data. While the order is unpaid the Member may cancel it and order again; after payment there is no reset, backward transition, branch transfer or special override.

**Steps 3 and 7 — Processing branch became inactive**

Preserve the recorded branch/history and do not silently relocate. An inactive branch cannot receive new checkout, but accepted orders retain their branch and original deadlines. Currently authorized assigned Staff/delegated Manager may complete paid orders; physical handover problems require operational follow-up without automatic transfer, cancellation or refund.

**Step 7 — Save failure**

Report a safe error and preserve previously accepted data/payment facts; do not invent another order/payment.

**Steps 7 and 8 — Long wait before actual GHTK handoff**

Keep only recipient name/phone/address necessary to fulfil the pending handoff. The 30-day retirement clock begins at the original actual handoff, not at entry/editing or preparation.

**Step 6 — Staff tries to record a courier step**

Reject it. Only the GHTK partner account records the courier pickup and the delivery; Staff packs the order and marks it ready. Pickup collects no extra address or separate contact; branch and frozen payment terms remain unchanged.

#### Business Rules

BR-51-01, BR-51-02, BR-51-03, BR-51-04, BR-51-05, BR-51-06, BR-51-07

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-51-01 | Only the active owning Member chooses method and recipient data at checkout; they cannot change after acceptance, and internal actors cannot impersonate the Member. |
| BR-51-02 | Support pickup or domestic GHTK; GHTK requires valid name, phone and address with optional note under the shared field limits, from a saved address or a new one. |
| BR-51-03 | The method and GHTK fee are frozen at checkout; later progress never changes the branch, frozen prices/totals, shipping fee, payment or hold. |
| BR-51-04 | Only the chosen method's chain is permitted: pickup Đang chuẩn bị hàng → Chờ khách nhận → Hoàn thành; GHTK Đang chuẩn bị hàng → Chờ ĐVVC lấy hàng → Đã bàn giao cho ĐVVC → Hoàn thành. |
| BR-51-05 | Retain necessary pending GHTK data and retire it 30 days after original actual handoff. |
| BR-51-06 | GHTK is called only for the delivery-fee quote; no GHTK shipment API, provider tracking, delivery-failure/return or cancellation/refund workflow is provided. |
| BR-51-07 | Record each attempt and its actual outcome under the shared audit policy. |
