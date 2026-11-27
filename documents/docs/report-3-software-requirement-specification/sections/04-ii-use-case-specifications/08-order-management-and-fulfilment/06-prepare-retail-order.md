### 8.6 UC-40 Prepare Retail Order

#### UC Name

UC-40 Prepare Retail Order

#### Primary Actors

Staff, Manager

#### Secondary Actors

None

#### Description

As a Staff member or Manager, I want to pack a paid retail order of my branch and mark it ready so that the customer or the GHTK courier can collect it.

#### Preconditions

- The actor is signed in with an active Staff or Manager account assigned to the order's branch.
- The retail order is paid and is in Đang chuẩn bị hàng.

#### Normal Flow

***Prepare Pickup Order***

1. The actor opens the branch retail orders page.
2. The system shows the paid orders of the actor's branches under Đơn hàng cần chuẩn bị.
3. The actor opens a pickup order to see its items and payment.
4. The actor packs the items.
5. The actor selects Đã chuẩn bị xong.
6. The system checks the role, the branch, the payment and the pickup method of the order.
7. The system records the preparation with the actor and the time.
8. The system moves the order to Chờ khách nhận.
9. The system confirms that the order is packed and waits for the customer (MSG81).

#### Alternative Flows

***Step 3.1 Order Uses GHTK Delivery***

1. The actor opens the GHTK order to see its tracking number, recipient and items.
2. The actor packs the parcel.
3. The actor selects Đã chuẩn bị xong.
4. The system asks the actor to confirm the move to Chờ ĐVVC lấy hàng (MSG290).
5. The actor confirms.
6. The system checks the branch and the current shipment version.
7. The system moves the shipment to Chờ ĐVVC lấy hàng and records the step in its history.
8. The system confirms the new shipment status (MSG291).
9. The use case ends.

***Step 3.2 Actor Searches Orders***

1. The actor enters an order code, a tracking number or a recipient name.
2. The system shows only the matching orders.
3. The use case continues at step 3.

***Step 3.3 Orders Cannot Be Loaded***

1. The system informs the actor that the data could not be loaded (MSG05).
2. The use case ends.

***Step 6.1 Order Belongs to Another Branch***

1. The system refuses the action (MSG01).
2. The system records nothing.
3. The use case ends.

***Step 6.2 Order Was Already Prepared or Changed***

1. The system informs the actor that the order has changed (MSG03).
2. The system records nothing.
3. The use case continues at step 2.

***Step 7.1 Result Is Not Confirmed***

1. The system informs the actor that the result could not be confirmed (MSG04).
2. The actor selects the same action again.
3. The use case continues at step 6.

#### Postconditions

- A pickup order is in Chờ khách nhận and the Member sees it as ready for pickup.
- A GHTK order's shipment is in Chờ ĐVVC lấy hàng and appears to the Carrier.
- The preparation is recorded once with the actor and the time.

#### Business Rule

BR-08, BR-37, BR-161, BR-163, BR-164
