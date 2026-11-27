### 11.1 UC-04 Browse Public Reviews

#### UC Name

UC-04 Browse Public Reviews

#### Primary Actors

Guest, Member, Staff, Manager, Admin, Carrier

#### Secondary Actors

None

#### Description

As any visitor of the website, I want to read published workshop-session and retail-product reviews with their rating aggregates so that I can judge Mland's experiences and rings.

#### Preconditions

- The actor can open the public website.

#### Normal Flow

***Browse Public Reviews***

1. The actor requests to view customer reviews.
2. The system shows the public reviews, newest first, with the review count and average rating.
3. The actor selects a target type, a star rating or a sort order.
4. The system shows the matching public reviews and the aggregate of the selected target scope.
5. The actor requests more reviews.
6. The system appends the next page of public reviews.
7. The actor selects a review.
8. The system shows the review details with the reviewer's masked name and the Manager reply.

#### Alternative Flows

***Step 1.1 Actor Opens the Reviews of a Workshop Package***

1. The actor selects the reviews link on a workshop package page.
2. The system limits the list and the aggregate to the sessions of that package.
3. The use case continues at step 2.

***Step 2.1 Signed-in Member Opens the Reviews Page***

1. The system shows the Member's own pending and reviewed sources instead of the public list.
2. The use case ends; the Member may create or edit a review in UC-26.

***Step 2.2 No Public Review Exists***

1. The system shows that there is no public review yet (MSG368).
2. The use case ends.

***Step 2.3 Reviews Cannot Be Loaded***

1. The system informs the actor that the data could not be loaded (MSG05).
2. The actor requests to try again.
3. The use case continues at step 2.

***Step 2.4 Guest Selects Write a Review***

1. The system opens the sign-in page with a return link to the reviews page.
2. The system sends no request for private review data.
3. The use case ends.

***Step 4.1 No Review Matches the Filter***

1. The system shows that there is no public review yet (MSG368).
2. The use case continues at step 3.

***Step 8.1 Review Is Not Public***

1. The system informs the actor that the review was not found (MSG369).
2. The use case ends.

#### Postconditions

- No review data is changed.
- Only public reviews are shown and counted in the aggregate.

#### Business Rule

BR-227
