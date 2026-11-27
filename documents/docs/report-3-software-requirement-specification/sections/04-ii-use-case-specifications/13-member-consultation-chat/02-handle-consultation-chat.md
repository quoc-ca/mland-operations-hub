### 13.2 UC-43 Handle Consultation Chat

#### UC Name

UC-43 Handle Consultation Chat

#### Primary Actors

Staff, Manager

#### Secondary Actors

None

#### Description

As a Staff member or Manager, I want to claim and answer Member consultations so that each Member receives one accountable consultant reply.

#### Preconditions

- The actor is signed in as Staff or Manager.
- A Staff member has at least one active Workshop Location in scope.
- At least one consultation is visible to the actor.

#### Normal Flow

***Handle Consultation Chat***

1. The actor requests to open the consultation inbox.
2. The system shows the conversations visible to the actor, newest first, with unread counts.
3. The actor selects a conversation.
4. The system shows the design snapshot, branch, claim status and full message history.
5. The actor requests to claim the conversation.
6. The system records the actor as the current claimant.
7. The system confirms the claim (MSG403).
8. The actor enters a reply and sends it.
9. The system validates the reply and stores it in the conversation history.
10. The system confirms that the reply has been sent by showing it in the conversation.

#### Alternative Flows

***Step 1.1 Inbox Cannot Be Loaded***

1. The system informs the actor that the data could not be loaded (MSG05).
2. The use case ends.

***Step 2.1 Actor Filters by Status***

1. The actor selects Open or Closed in the status filter.
2. The system shows only the conversations with that status.
3. The use case continues at step 3.

***Step 2.2 No Conversation Is Visible***

1. The system informs the actor that no conversation is found (MSG408).
2. The use case ends.

***Step 3.1 Conversation Is Outside the Staff Scope***

1. The system hides any conversation not assigned to a branch in the Staff member's scope.
2. The system reports the conversation as not found.
3. The use case ends.

***Step 5.1 Conversation Is Already Claimed by the Actor***

1. The system shows the Release action instead of Claim.
2. The use case continues at step 8.

***Step 5.2 Manager Replies Without Claiming***

1. The Manager enters a reply without claiming the conversation.
2. The use case continues at step 9.

***Step 6.1 Conversation Is Claimed by Another Consultant***

1. The system rejects the claim.
2. The system informs the actor that another consultant has already claimed it (MSG404).
3. The use case continues at step 4.

***Step 6.2 Staff Claims an Unassigned or Out-of-Scope Conversation***

1. The system rejects the claim.
2. The system informs the Staff member that the action is not permitted (MSG01).
3. The use case ends.

***Step 6.3 Conversation Was Changed by Someone Else***

1. The system rejects the request because the conversation version is outdated.
2. The system informs the actor that the record has changed (MSG03).
3. The use case continues at step 4.

***Step 8.1 Conversation Has No Claimant***

1. The system hides the reply box from Staff and asks the Staff member to claim first (MSG405).
2. The use case continues at step 5.

***Step 9.1 Reply Is Empty***

1. The system informs the actor that the message cannot be empty (MSG392).
2. The use case continues at step 8.

***Step 9.2 Reply Is Too Long***

1. The system informs the actor that the message exceeds 2,000 characters (MSG393).
2. The use case continues at step 8.

***Step 9.3 Conversation Is Closed***

1. The system informs the actor that the conversation is closed (MSG395).
2. The system does not store the reply.
3. The use case continues at step 4.

***Step 10.1 Actor Releases the Claim***

1. The actor requests to release the conversation.
2. The system clears the claimant and records the release.
3. The system confirms the release (MSG406).
4. The use case ends.

***Step 10.2 Staff Releases a Claim Held by Someone Else***

1. The Staff member requests to release a conversation claimed by another consultant.
2. The system rejects the release and keeps the current claimant (MSG407).
3. The use case continues at step 4.

***Step 10.3 Actor Closes the Conversation***

1. The actor requests to close the conversation.
2. The system asks the actor to confirm closing (MSG398).
3. The actor confirms.
4. The system marks the conversation as closed and keeps its history.
5. The use case ends.

***Step 10.4 Actor Reopens a Closed Conversation***

1. The actor requests to reopen a closed conversation.
2. The system asks the actor to confirm reopening (MSG399).
3. The actor confirms.
4. The system marks the conversation as open.
5. The use case continues at step 8.

#### Postconditions

- The conversation has at most one current claimant.
- Each claim and release is recorded with actor, previous and new claimant, and time.
- Every accepted reply is stored once and cannot be edited or deleted.
- The audit trail records each action without the message text.
- No design, price, booking, payment or order is changed.

#### Business Rule

BR-241, BR-243, BR-245, BR-247, BR-248, BR-249, BR-251, BR-252, BR-253
