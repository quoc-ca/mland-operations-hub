### 8.8 UC-67 Reconcile Retail Payments

#### UC Name

UC-67 Reconcile Retail Payments

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to review the retail payments that need manual reconciliation and the daily confirmed cash of each branch so that unclear payments are checked against evidence.

#### Preconditions

- The actor is signed in as an active Manager.
- The system has recorded at least one retail payment for reconciliation or one counter cash collection.

#### Normal Flow

***Review Retail Payments to Reconcile***

1. The Manager opens the retail reconciliation page.
2. The system checks that the actor is an active Manager.
3. The system shows the transactions to check, newest first, with reason, amount and VNPay reference.
4. The Manager enters an order or transaction code to search.
5. The Manager selects a reason filter.
6. The system shows only the transactions on the page that match the code and the reason.

#### Alternative Flows

***Step 2.1 Actor Is Not an Active Manager***

1. The system refuses the action (MSG01).
2. The use case ends.

***Step 3.1 Manager Views Daily Retail Cash***

1. The Manager selects the Tiền mặt theo ngày tab.
2. The Manager selects a branch and a business date.
3. The system checks that the branch is one of the Manager's branches.
4. The system shows the number of collections, the total applied amount and each collection with time, order, amount and Staff.
5. The use case ends.

***Step 3.2 Manager Opens Another Page***

1. The Manager selects the next or previous page.
2. The use case continues at step 3.

***Step 3.3 Nothing to Show***

1. The system informs the Manager that there is no transaction or no cash to show (MSG295).
2. The use case ends.

***Step 3.4 Data Cannot Be Loaded***

1. The system informs the Manager that the data could not be loaded (MSG05).
2. The Manager selects Làm mới.
3. The use case continues at step 3.

***Step 3.5 Branch Is Outside the Manager's Branches***

1. The system refuses the request for that branch (MSG01).
2. The system shows no cash data.
3. The use case ends.

#### Postconditions

- The Manager has seen the payments that need a decision and the confirmed cash of the chosen branch and date.
- No order, payment or cash record is changed.

#### Business Rule

BR-34, BR-35, BR-159, BR-166, BR-167
