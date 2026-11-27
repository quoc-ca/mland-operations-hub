### 11.2 UC-26 Create or Edit Own Review

#### UC Name

UC-26 Create or Edit Own Review

#### Primary Actors

Member

#### Secondary Actors

None

#### Description

As a Member, I want to review a workshop I attended or a ring I received, and correct my review later, so that other customers can learn from my experience.

#### Preconditions

- The Member is signed in with an active account.
- The Member has a checked-in workshop ticket or a picked-up or delivered retail order line.

#### Normal Flow

***Create Own Review***

1. The Member requests to view their reviews.
2. The system shows the Member's own sources that are waiting for a review.
3. The Member selects a source to review.
4. The system shows the review form for that workshop session or product.
5. The Member enters a star rating, optional text and up to three photos.
6. The Member chooses whether to appear anonymously.
7. The Member submits the review.
8. The system checks again that the source is eligible and not yet reviewed.
9. The system saves the review as public with a masked or anonymous reviewer name.
10. The system records the first revision of the review.
11. The system confirms that the review has been published (MSG370).

#### Alternative Flows

***Step 1.1 Member Edits an Existing Review***

1. The Member opens one of their reviewed sources and requests to edit the review.
2. The system shows the review form with the current rating, text and photos.
3. The Member changes the rating, text or photos and saves.
4. The system checks that the Member owns the review and that it was not changed meanwhile.
5. The system updates the review without changing its visibility status.
6. The system records a new revision of the review.
7. The system confirms that the review has been updated (MSG371).
8. The use case ends.

***Step 1.2 Guest Selects Write a Review***

1. The system opens the sign-in page with a return link to the reviews page.
2. The system sends no request for private review data.
3. The use case ends.

***Step 1.3 Edited Review Is Not Owned or Was Removed***

1. During an edit, the system informs the Member that they do not have permission to edit the review (MSG01).
2. The system does not change the review.
3. The use case ends.

***Step 1.4 Edited Review Was Changed Meanwhile***

1. During an edit, the system informs the Member that the review was changed by someone else (MSG03).
2. The Member reloads the latest review.
3. The use case continues at step 1.

***Step 2.1 Nothing Is Waiting for a Review***

1. The system shows that nothing is waiting for the Member's review (MSG375).
2. The use case ends.

***Step 2.2 Sources Cannot Be Loaded***

1. The system informs the Member that the data could not be loaded (MSG05).
2. The use case ends.

***Step 3.1 Member Reviews Several Lines of One Retail Order***

1. The system shows one rating and text block for each unreviewed line of the order.
2. The Member rates the lines and submits them together.
3. The system creates one review for each line, following steps 8 to 10.
4. The system shows the Member's reviewed sources with a thank-you notice.
5. The use case ends.

***Step 3.2 Some Order-Line Reviews Are Not Sent***

1. The system keeps the reviews already created.
2. The system informs the Member that some reviews could not be sent (MSG376).
3. The system keeps the unsent lines on the form for another submission.
4. The use case continues at step 3.

***Step 7.1 Review Input Is Invalid***

1. The system informs the Member which input is invalid (MSG374).
2. The system keeps the entered data.
3. The use case continues at step 5.

***Step 8.1 Source Is Not Eligible***

1. The system informs the Member that the source is not eligible for a review (MSG372).
2. The system creates no review.
3. The use case ends.

***Step 8.2 Source Has Already Been Reviewed***

1. The system informs the Member that this source already has a review (MSG373).
2. The system creates no second review.
3. The use case ends.

#### Postconditions

- The review is public and linked to exactly one eligible source.
- An edited review keeps its visibility status and has a new revision.
- The public aggregate of the target includes the new or edited rating.

#### Business Rule

BR-38, BR-228, BR-229, BR-230, BR-231, BR-232
