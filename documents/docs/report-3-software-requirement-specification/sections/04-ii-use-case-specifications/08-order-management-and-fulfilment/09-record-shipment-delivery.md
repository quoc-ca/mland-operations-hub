### 8.9 UC-74 Record Shipment Delivery

#### UC Name

UC-74 Record Shipment Delivery

#### Primary Actors

Carrier

#### Secondary Actors

None

#### Description

As a Carrier, I want to record the courier pickup and delivery of GHTK shipments and view delivery statistics so that the Member and the branch see where each parcel is.

#### Preconditions

- The actor is signed in with an active Carrier account.
- At least one GHTK shipment of a paid retail order exists.

#### Normal Flow

***Record Courier Pickup of a Shipment***

1. The Carrier opens the GHTK shipments page.
2. The system checks that the actor is an active Carrier.
3. The system shows the shipments of every branch grouped by status tabs.
4. The Carrier opens the Đang lấy hàng tab.
5. The Carrier selects a shipment packed by the branch.
6. The Carrier selects Đã lấy hàng.
7. The system checks the shipment version and that the branch has packed the shipment.
8. The system moves the shipment to Đang giao hàng and records the step with its time.
9. The system moves the branch order to Đang giao hàng.
10. The system shows the shipment in the Đang giao hàng tab.

#### Alternative Flows

***Step 2.1 Account Is Not an Active Carrier***

1. The system informs the actor that the account is not an active GHTK partner account (MSG296).
2. The use case ends.

***Step 3.1 Carrier Searches Shipments***

1. The Carrier enters a tracking number, order code or recipient.
2. The system shows only the matching shipments.
3. The use case continues at step 4.

***Step 3.2 No Shipment to Show***

1. The system informs the Carrier that there is no shipment in the tab or none matches (MSG298).
2. The use case continues at step 4.

***Step 3.3 Shipments Cannot Be Loaded***

1. The system informs the Carrier that the data could not be loaded (MSG05).
2. The Carrier selects Thử lại.
3. The use case continues at step 3.

***Step 3.4 Carrier Views Delivery Statistics***

1. The Carrier opens the Thống kê page.
2. The Carrier selects a period type and a date.
3. The system shows shipments received, picked up and delivered, the average delivery time and the delivered shipping fees.
4. The system shows the trend and the figures by pickup branch.
5. The use case ends.

***Step 6.1 Carrier Records Delivery***

1. The Carrier opens the Đang giao hàng tab and selects a shipment.
2. The Carrier selects Giao thành công.
3. The system checks the shipment version and that the shipment is being delivered.
4. The system moves the shipment to Đã giao hàng and records the step with its time.
5. The system moves the branch order to Đã giao and records its completion time.
6. The system shows the shipment in the Đã giao hàng tab.
7. The use case ends.

***Step 7.1 Shipment Was Changed by Another User***

1. The system informs the Carrier that the shipment has changed (MSG03).
2. The system reloads the shipment list.
3. The use case continues at step 3.

***Step 7.2 Shipment Is Not at the Required Step***

1. The system informs the Carrier that the step cannot be recorded for this shipment (MSG297).
2. The system records nothing.
3. The use case continues at step 3.

#### Postconditions

- The shipment status, the time of each courier step and the branch order status are updated.
- The Member and Staff see the new status in their order lists.
- The customer pays no cash on delivery because the order was paid online.

#### Business Rule

BR-08, BR-37, BR-164, BR-168, BR-169, BR-170
