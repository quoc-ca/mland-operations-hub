### 8.7 UC-41 Fulfil Custom Order

#### UC Name

UC-41 Fulfil Custom Order

#### Primary Actors

Staff, Manager

#### Secondary Actors

None

#### Description

As a Staff member or Manager, I want to record the production and handover of a fully paid custom order so that the Member can follow it until completion.

#### Preconditions

- The actor is signed in with an active Staff or Manager account.
- The custom order has been paid in full.

#### Normal Flow

***Fulfil Custom Order***

1. The actor opens the custom orders page.
2. The system lists the custom orders in the tabs Chờ chế tác, Đang gia công, Sẵn sàng giao hàng and Đã hoàn tất.
3. The actor opens a paid order under Chờ chế tác.
4. The system shows the order reference, Member, design reference, quote and shipping address.
5. The actor selects Bắt đầu chế tác.
6. The system asks the actor to confirm the status change (MSG292).
7. The actor confirms.
8. The system checks the full payment and that the order is at the previous stage.
9. The system moves the order to Đang gia công.
10. The system confirms that the order status has been changed (MSG293).

#### Alternative Flows

***Step 3.1 Production Is Finished***

1. The actor opens an order under Đang gia công.
2. The actor selects Hoàn thành chế tác & Sẵn sàng giao.
3. The system moves the order to Sẵn sàng giao hàng and marks production as completed.
4. The use case continues at step 10.

***Step 3.2 Ring Is Handed Over***

1. The actor opens an order under Sẵn sàng giao hàng.
2. The actor selects Xác nhận đã bàn giao / Giao hàng.
3. The system moves the order to Đã hoàn tất and marks it as delivered.
4. The use case continues at step 10.

***Step 3.3 Actor Searches Orders***

1. The actor enters an order reference, a design request reference, a customer name or an email.
2. The system shows only the matching orders.
3. The use case continues at step 3.

***Step 3.4 Orders Cannot Be Loaded***

1. The system informs the actor that the data could not be loaded (MSG05).
2. The use case ends.

***Step 7.1 Actor Cancels the Confirmation***

1. The system closes the confirmation without changing the order.
2. The use case continues at step 3.

***Step 8.1 Order Is Not Fully Paid or Not at the Previous Stage***

1. The system refuses the change and states the reason (MSG294).
2. The system keeps the current order status.
3. The use case continues at step 2.

***Step 8.2 Order Is Already at the Requested Stage***

1. The system returns the order unchanged.
2. The use case continues at step 10.

***Step 8.3 Actor Is Not Staff or Manager***

1. The system refuses the change (MSG01).
2. The use case ends.

#### Postconditions

- The custom order is one stage further: Đang gia công, Sẵn sàng giao hàng or Đã hoàn tất.
- The production and fulfilment statuses follow the new stage.
- The Member sees the new stage of the order.

#### Business Rule

BR-07, BR-165
