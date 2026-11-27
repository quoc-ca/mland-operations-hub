### 13.3 UC-64 Assign Consultation

#### UC Name

UC-64 Assign Consultation

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to assign an unassigned consultation to a Workshop Location so that the Staff of that location can answer it.

#### Preconditions

- The Manager is signed in.
- The Manager has opened a conversation in the consultation inbox.

#### Normal Flow

***Assign Consultation***

1. The Manager requests to assign the conversation to a branch.
2. The system shows the branches available for assignment.
3. The Manager selects one branch and confirms.
4. The system validates that the branch exists and is active.
5. The system records the branch on the conversation.
6. The system records the assignment with the previous and new branch.
7. The system makes the conversation visible to Staff of that branch.
8. The system confirms that the branch has been assigned (MSG409).

#### Alternative Flows

***Step 1.1 Conversation Already Has a Branch***

1. The Manager requests to assign the conversation to a different branch.
2. The use case continues at step 2.

***Step 3.1 Manager Cancels the Assignment***

1. The Manager cancels the branch selection.
2. The system keeps the current branch unchanged.
3. The use case ends.

***Step 4.1 Branch Is Missing or Inactive***

1. The system rejects the assignment.
2. The system informs the Manager that the branch does not exist or is not active (MSG410).
3. The use case continues at step 2.

***Step 5.1 Conversation Was Changed by Someone Else***

1. The system rejects the assignment because the conversation version is outdated.
2. The system informs the Manager that the record has changed (MSG03).
3. The use case ends without changing the branch.

***Step 5.2 Actor Is Not a Manager***

1. The system rejects the assignment.
2. The system informs the actor that the action is not permitted (MSG01).
3. The use case ends without changing the branch.

#### Postconditions

- The conversation is assigned to one active branch.
- Staff of that branch can see, claim and answer the conversation.
- The assignment is recorded with actor, previous branch, new branch and time.
- The claimant and message history are unchanged.

#### Business Rule

BR-247, BR-249, BR-250, BR-251
