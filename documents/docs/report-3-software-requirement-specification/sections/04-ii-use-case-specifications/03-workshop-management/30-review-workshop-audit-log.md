### 3.30 UC-61 Review Workshop Audit Log

#### UC Name

UC-61 Review Workshop Audit Log

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to review the audit records of workshop operations and settlement and look up bookings across sessions so that I can investigate incidents and support customers.

#### Preconditions

- The actor is signed in as an active Manager.

#### Normal Flow

***Review Workshop Audit Log***

1. The Manager opens the workshop audit screen.
2. The system shows the operations tab with the records of the last 7 days.
3. The Manager selects the operations tab or the settlement tab.
4. The Manager enters filters such as action, outcome, actor, session, record and period.
5. The system checks the filters.
6. The system lists the matching records one page at a time, up to 100 per page.
7. The Manager selects to load more records.
8. The system shows the next page of records.
9. The system confirms that the list is complete for the applied filters.

#### Alternative Flows

***Step 1.1 Actor Is Not a Manager***

1. The system refuses access to the audit records (MSG01).
2. The system records the denied read in the audit log.
3. The use case ends without showing any record.

***Step 3.1 Manager Looks Up a Booking Across Sessions***

1. The Manager enters a booking code for customer support.
2. The system returns every ticket of the booking across sessions and locations.
3. The system shows no full contact email, phone number or payment data.
4. The system records the lookup in the audit log.
5. The use case ends after the lookup.

***Step 3.2 Booking Does Not Exist***

1. The system informs the Manager that no booking matches the code (MSG246).
2. The use case continues at step 3.

***Step 5.1 Filters Are Invalid***

1. The system informs the Manager that the filters are invalid (MSG259).
2. The system keeps the last valid list.
3. The use case continues at step 4.

***Step 6.1 No Record Matches the Filters***

1. The system informs the Manager that no record matches (MSG260).
2. The system offers to clear the filters.
3. The use case continues at step 4.

***Step 6.2 Records Cannot Be Loaded***

1. The system informs the Manager that the records could not be loaded (MSG05).
2. The use case ends without showing records.

#### Postconditions

- No audit record is created, changed or deleted by the review itself.
- Each cross-session booking lookup is recorded in the audit log.

#### Business Rule

BR-142
