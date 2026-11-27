### 11.4 UC-63 Reply to or Remove Review

#### UC Name

UC-63 Reply to or Remove Review

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to publish one official reply to a review, or remove a review with a reason, so that Mland answers customers and keeps unacceptable content out of public view.

#### Preconditions

- The actor is signed in as a Manager.
- The review exists.

#### Normal Flow

***Reply to Review***

1. The Manager requests to view the review moderation queue.
2. The system shows the reviews, newest first, with status, target and rating filters.
3. The Manager selects a review.
4. The system shows the review content, status, current reply and recent moderation events.
5. The Manager enters the reply text.
6. The Manager requests to save the reply.
7. The system checks the role and that the review has no reply yet.
8. The system saves the reply as the review's official Manager reply.
9. The system records a reply-creation moderation event.
10. The system confirms that the reply has been saved (MSG379).

#### Alternative Flows

***Step 4.1 Review Is Not Found***

1. The system informs the Manager that the review was not found (MSG369).
2. The use case ends.

***Step 5.1 Manager Edits the Existing Reply***

1. The Manager changes the text of the current reply and saves it.
2. The system checks that the reply was not changed meanwhile.
3. The system updates the reply and records a reply-edit moderation event.
4. The system confirms that the reply has been saved (MSG379).
5. The use case ends.

***Step 5.2 Manager Removes the Review***

1. The Manager enters a moderation reason.
2. The Manager requests to remove the review.
3. The system checks the reason and the review version.
4. The system changes the review status to removed and records a moderation event.
5. The system confirms that the review visibility has been updated (MSG377).
6. The use case ends.

***Step 5.3 Removal Reason Is Invalid***

1. During a removal, the system asks the Manager to enter a valid reason (MSG378).
2. The system does not change the review.
3. The use case continues at step 5.

***Step 7.1 Reply Text Is Invalid***

1. The system asks the Manager to enter a valid reply (MSG380).
2. The system keeps the entered text.
3. The use case continues at step 5.

***Step 7.2 Review Already Has a Reply***

1. The system informs the Manager that the review already has an official reply (MSG381).
2. The Manager reloads the review.
3. The use case continues at step 4.

***Step 7.3 Actor Is Not Allowed***

1. The system informs the actor that they do not have permission for this action (MSG01).
2. The system does not change the review or reply.
3. The use case ends.

***Step 7.4 Review or Reply Was Changed Meanwhile***

1. The system informs the Manager that the record was changed by someone else (MSG03).
2. The Manager reloads the latest review.
3. The use case continues at step 4.

#### Postconditions

- The review has at most one current Manager reply, shown under it in public reads.
- A removed review is excluded from public lists, details and aggregates, and is kept as history.
- A moderation event records each reply or removal with actor and time.

#### Business Rule

BR-227, BR-232, BR-233, BR-234
