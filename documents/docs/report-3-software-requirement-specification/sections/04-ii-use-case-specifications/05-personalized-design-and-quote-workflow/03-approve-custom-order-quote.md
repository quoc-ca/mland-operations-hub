### 5.3 UC-62 Approve Custom Order Quote

#### UC Name

UC-62 Approve Custom Order Quote

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to approve the quote prepared by Staff for a custom order so that the Member can pay the full amount.

#### Preconditions

- The Manager is signed in.
- The custom order has a latest quote waiting for Manager approval.
- The custom order is not yet paid.

#### Normal Flow

***Approve Custom Order Quote***

1. The Manager opens the accepted design request of the custom order.
2. The system shows the quote reference, version, amount, deadline, note and pending status.
3. The Manager selects Approve quote.
4. The system asks the Manager to confirm the approval.
5. The Manager confirms the approval.
6. The system checks the Manager role, the order lock and that the quote is the latest pending one.
7. The system records the Manager's approval on the quote.
8. The system sets the quote ready and the custom order waiting for full payment.
9. The system confirms that the quote has been approved (MSG280).

#### Alternative Flows

***Step 3.1 Manager Does Not Approve the Quote***

1. The Manager leaves the page without approving.
2. The system keeps the quote waiting for approval.
3. The use case ends without approval.

***Step 5.1 Manager Cancels the Confirmation***

1. The Manager cancels the confirmation dialog.
2. The use case continues at step 2.

***Step 6.1 Actor Is Not a Manager***

1. The system refuses the approval (MSG01).
2. The use case ends without approval.

***Step 6.2 Quote Is No Longer the Latest Pending Quote***

1. The system informs the Manager that the quote can no longer be approved (MSG279).
2. The system keeps the quote unchanged.
3. The use case ends without approval.

***Step 6.3 Custom Order Is Already Paid***

1. The system informs the Manager that the order is locked (MSG279).
2. The use case ends without approval.

#### Postconditions

- The quote records the approving Manager.
- The custom order waits for the Member's full payment before the quote deadline.

#### Business Rule

BR-06, BR-151
