### 11.3 UC-42 Hide Review

#### UC Name

UC-42 Hide Review

#### Primary Actors

Staff, Manager

#### Secondary Actors

None

#### Description

As a Staff member or Manager, I want to hide a published review with a reason so that inappropriate content leaves public view while its history is kept.

#### Preconditions

- The actor is signed in as Staff or Manager.
- The review exists and is public.

#### Normal Flow

***Hide Review***

1. The actor requests to view the review moderation queue.
2. The system shows the reviews, newest first, with status, target and rating filters.
3. The actor selects a public review.
4. The system shows the review content, status, target, reply and recent moderation events.
5. The actor enters the moderation reason.
6. The actor requests to hide the review.
7. The system checks the actor's role, the review version and the reason.
8. The system changes the review status to hidden.
9. The system records a moderation event with the actor, role, old and new status and reason.
10. The system confirms that the review visibility has been updated (MSG377).

#### Alternative Flows

***Step 3.1 Manager Restores a Hidden Review***

1. The Manager selects a hidden review and enters a reason.
2. The Manager requests to restore the review.
3. The system changes the review status to public and records a moderation event.
4. The system confirms that the review visibility has been updated (MSG377).
5. The use case ends.

***Step 4.1 Staff Opens a Hidden Review***

1. The system shows that only a Manager can restore or remove a hidden review.
2. The system offers no moderation action to the Staff member.
3. The use case ends.

***Step 4.2 Review Is Not Found***

1. The system informs the actor that the review was not found (MSG369).
2. The use case ends.

***Step 7.1 Reason Is Invalid***

1. The system asks the actor to enter a valid reason (MSG378).
2. The system does not change the review.
3. The use case continues at step 5.

***Step 7.2 Action Is Not Allowed for the Actor***

1. The system informs the actor that they do not have permission for this action (MSG01).
2. The system does not change the review.
3. The use case ends.

***Step 7.3 Review Was Changed Meanwhile***

1. The system informs the actor that the review was changed by someone else (MSG03).
2. The actor reloads the latest review.
3. The use case continues at step 4.

#### Postconditions

- The review is hidden and no longer appears in public lists, details or aggregates.
- A moderation event records who hid the review, when and why.
- The review and its revisions are kept.

#### Business Rule

BR-227, BR-232, BR-233
