### 13.1 UC-20 Chat with Staff Consultant

#### UC Name

UC-20 Chat with Staff Consultant

#### Primary Actors

Member

#### Secondary Actors

None

#### Description

As a Member, I want to exchange plain-text messages with Staff or a Manager, optionally about a saved design, so that I receive design consultation.

#### Preconditions

- The Member is signed in with an active account.
- The Member is on the consultation page or on a saved design page.

#### Normal Flow

***Chat with Staff Consultant***

1. The Member requests to open the consultation page.
2. The system shows the Member's open and closed conversations, newest first.
3. The Member requests a new consultation.
4. The system shows the Member's owned designs and design requests with code, name, version and preview.
5. The Member selects one design to discuss.
6. The Member enters an optional first message and confirms.
7. The system validates the selected design and freezes a snapshot of it.
8. The system creates an open conversation and stores the first message.
9. The Member enters a message and sends it.
10. The system validates the message and stores it in the conversation history.
11. The system refreshes the conversation periodically and shows the consultant replies.
12. The system confirms that the message has been sent by showing it in the conversation.

#### Alternative Flows

***Step 1.1 Member Opens Consultation from a Saved Design***

1. The Member requests consultation from a saved workshop ring design.
2. The system opens the existing conversation for that design, or creates one with that design attached.
3. The use case continues at step 9.

***Step 2.1 Member Continues an Existing Conversation***

1. The Member selects an existing conversation in the list.
2. The system shows the design snapshot and the full message history.
3. The use case continues at step 9.

***Step 2.2 Conversation List Cannot Be Loaded***

1. The system informs the Member that the data could not be loaded (MSG05).
2. The use case ends.

***Step 4.1 Member Has No Saved Design***

1. The system informs the Member that no design or design request is available (MSG397).
2. The Member chooses a general consultation without a design.
3. The Member enters an optional first message and confirms.
4. The use case continues at step 8.

***Step 5.1 Member Chooses a General Consultation***

1. The Member chooses a general consultation without a design.
2. The Member enters an optional first message and confirms.
3. The use case continues at step 8.

***Step 6.1 No Design or General Consultation Is Selected***

1. The system asks the Member to select a design context (MSG402).
2. The use case continues at step 5.

***Step 7.1 Selected Design Is Not Valid***

1. The system rejects a design that is unknown, not owned, archived, withdrawn or no longer current.
2. The system informs the Member that the design context is not valid (MSG396).
3. The system does not create the conversation.
4. The use case continues at step 4.

***Step 10.1 Message Is Empty***

1. The system informs the Member that the message cannot be empty (MSG392).
2. The use case continues at step 9.

***Step 10.2 Message Is Too Long***

1. The system informs the Member that the message exceeds 2,000 characters (MSG393).
2. The use case continues at step 9.

***Step 10.3 Member Sends Messages Too Quickly***

1. The system rejects the message without storing it.
2. The system informs the Member how many seconds to wait (MSG394).
3. The system disables sending until the wait ends.
4. The use case continues at step 9.

***Step 10.4 Conversation Is Closed***

1. The system informs the Member that the conversation is closed (MSG395).
2. The system does not store the message.
3. The use case ends.

***Step 10.5 Same Message Is Sent Again After a Lost Response***

1. The system recognises the repeated send command.
2. The system returns the original message without storing a second copy.
3. The use case continues at step 11.

***Step 11.1 Member Closes the Conversation***

1. The Member requests to close the conversation.
2. The system asks the Member to confirm closing (MSG398).
3. The Member confirms.
4. The system marks the conversation as closed and keeps its history.
5. The use case ends.

***Step 11.2 Member Reopens a Closed Conversation***

1. The Member requests to reopen a closed conversation.
2. The system asks the Member to confirm reopening (MSG399).
3. The Member confirms.
4. The system marks the conversation as open.
5. The use case continues at step 9.

***Step 11.3 Member Removes a Closed Conversation from the List***

1. The Member requests to remove a closed conversation from the list.
2. The system asks the Member to confirm hiding (MSG400).
3. The Member confirms.
4. The system hides the conversation from the Member's list only.
5. The use case ends.

***Step 11.4 Member Removes an Open Conversation***

1. The system informs the Member that the conversation must be closed first (MSG401).
2. The system keeps the conversation in the list.
3. The use case continues at step 9.

***Step 11.5 Conversation Was Changed by Someone Else***

1. The system rejects the close, reopen or hide request because the conversation version is outdated.
2. The system informs the Member that the record has changed (MSG03).
3. The use case continues at step 2.

#### Postconditions

- The conversation is stored with its Member owner and an immutable design snapshot or no design.
- Every accepted message is stored once and cannot be edited or deleted.
- The audit trail records the action without the message text.
- No design, price, booking, payment or order is changed.

#### Business Rule

BR-241, BR-242, BR-243, BR-244, BR-245, BR-246, BR-249, BR-251, BR-252, BR-253
