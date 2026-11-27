### 4.3 UC-39 Review Custom Design Request

#### UC Name

UC-39 Review Custom Design Request

#### Primary Actors

Staff, Manager

#### Secondary Actors

None

#### Description

As a Staff member or Manager, I want to review a Member's custom design request by hand and record my decision with an explanation so that the Member knows the next step.

#### Preconditions

- The actor is signed in as Staff or Manager.
- The design request is in the Need review or Clarification required status.

#### Normal Flow

***Review Custom Design Request***

1. The actor opens the design review queue.
2. The system lists the design requests, newest first, with a status filter.
3. The actor selects a design request.
4. The system shows the description, reference images, design snapshot, version and status.
5. The actor chooses the Accept decision.
6. The actor enters the explanation for the Member.
7. The actor selects Record decision.
8. The system asks the actor to confirm the decision and explanation.
9. The actor confirms the decision.
10. The system checks the role, the explanation, the request status and the request version.
11. The system records the decision with the reviewer, role, version and time.
12. The system sets the request to Accepted and raises its version.
13. The system confirms that the decision has been recorded (MSG276).

#### Alternative Flows

***Step 5.1 Actor Rejects the Request***

1. The actor chooses the Reject decision.
2. The use case continues at step 6.
3. At step 12, the system sets the request to Rejected.

***Step 5.2 Actor Asks for Clarification***

1. The actor chooses the Clarification required decision.
2. The use case continues at step 6.
3. At step 12, the system sets the request to Clarification required and waits for the Member's revision.

***Step 7.1 Explanation Is Missing or Too Long***

1. The system informs the actor that the explanation is invalid (MSG274).
2. The system does not record the decision.
3. The use case continues at step 6.

***Step 9.1 Actor Cancels the Confirmation***

1. The actor cancels the confirmation dialog.
2. The use case continues at step 5.

***Step 10.1 Request Already Has a Final Decision***

1. The system informs the actor that the request has already been decided (MSG275).
2. The system keeps the earlier decision.
3. The use case ends without a new decision.

***Step 10.2 Request Version Has Changed***

1. The system informs the actor that the request has changed (MSG03).
2. The system does not record the decision.
3. The use case continues at step 3.

***Step 10.3 Actor Is Not Allowed to Review***

1. The system refuses the decision (MSG01).
2. The use case ends without a decision.

#### Postconditions

- The decision is stored with reviewer, role, version, explanation and time.
- The request status and next action follow the decision.
- An accepted request is ready for quote preparation in UC-31 Submit Custom Order.

#### Business Rule

BR-05, BR-149
