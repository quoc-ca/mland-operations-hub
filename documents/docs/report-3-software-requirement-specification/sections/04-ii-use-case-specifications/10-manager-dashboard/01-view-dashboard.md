### 10.1 UC-68 View Dashboard

#### UC Name

UC-68 View Dashboard

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to view verified revenue, workshop utilization, branch KPIs, today's operations and recent events so that I can evaluate business performance and daily operations.

#### Preconditions

- The actor is signed in as an active Manager.
- The Manager has at least one active branch assignment.

#### Normal Flow

***View Dashboard***

1. The Manager opens the dashboard.
2. The system shows today's sessions, cancelled sessions and check-in progress for all assigned branches.
3. The system shows the five KPI cards for the current month up to now, in Vietnam time.
4. The system shows the retail and workshop trends compared with the previous period of the same length.
5. The system shows the top 5 products and the top 5 workshop packages.
6. The system shows the KPI rows of each assigned branch.
7. The system shows up to five recent events from the last 24 hours.
8. The Manager selects a day, week, month or year and one branch or all branches.
9. The system checks the filter against the Manager's branch scope.
10. The system reloads the KPI cards, trends, best sellers and branch rows for that scope.
11. The system confirms the result with the active scope and the last-updated time.

#### Alternative Flows

***Step 1.1 Actor Is Not a Manager***

1. The system refuses access without showing any figure (MSG01).
2. The use case ends without showing the dashboard.

***Step 1.2 Session Has Expired***

1. The system informs the Manager that the session has expired (MSG02).
2. The use case ends without showing any figure.

***Step 2.1 Manager Chooses a Branch for Today's Operations***

1. The Manager selects one branch in the today's operations block.
2. The system shows that branch's sessions and check-in progress only.
3. The system keeps the choice for the current visit and leaves the revenue report unchanged.
4. The use case continues at step 3.

***Step 2.2 No Session Is Scheduled Today***

1. The system states that there is no session today (MSG365).
2. The use case continues at step 3.

***Step 2.3 An Operations Block Cannot Be Loaded***

1. The system shows that block as unavailable, never as zero (MSG05).
2. The system keeps the other blocks and the revenue report.
3. The Manager may retry that block only.
4. The use case continues at step 3.

***Step 3.1 Revenue Report Cannot Be Loaded***

1. The system informs the Manager that the dashboard could not be loaded (MSG82).
2. The system offers a retry.
3. The use case continues at step 3.

***Step 3.2 Some Figures Cannot Be Read***

1. The system marks the affected figures with "—" and names the unreadable sources (MSG84).
2. The use case continues at step 4.

***Step 3.3 Period Has No Verified Data***

1. The system shows an explicit empty state instead of zero revenue (MSG85).
2. The use case continues at step 4.

***Step 4.1 Trends or Best Sellers Cannot Be Loaded***

1. The system informs the Manager that this part could not be loaded (MSG82).
2. The system keeps the KPI cards and branch rows visible.
3. The use case continues at step 6.

***Step 7.1 No Recent Event***

1. The system states that nothing new happened in the last 24 hours (MSG366).
2. The use case continues at step 8.

***Step 7.2 One Audit Source Cannot Be Read***

1. The system shows the events of the readable source.
2. The system notes that part of the event data is missing (MSG367).
3. The use case continues at step 8.

***Step 7.3 Manager Opens the Audit Log***

1. The Manager selects the link to the audit log.
2. The system opens the workshop audit log.
3. The use case ends.

***Step 9.1 Filter Is Invalid***

1. The system rejects the filter, such as a period entirely in the future (MSG83).
2. The system keeps the last valid dashboard view.
3. The use case continues at step 8.

***Step 9.2 Branch Is Outside the Manager's Scope***

1. The system refuses the branch without showing its figures (MSG01).
2. The use case continues at step 8.

#### Postconditions

- No order, payment, workshop, product, promotion or master-data record is changed.
- The displayed figures match the verified records of the selected period and branch scope.

#### Business Rule

BR-39, BR-40, BR-224, BR-225, BR-226
