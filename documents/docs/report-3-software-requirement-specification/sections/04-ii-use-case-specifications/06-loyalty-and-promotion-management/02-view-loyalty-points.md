### 6.2 UC-25 View Loyalty Points

#### UC Name

UC-25 View Loyalty Points

#### Primary Actors

Member

#### Secondary Actors

None

#### Description

As a Member, I want to see my point balance, held and available points, point value and point history so that I know what I can redeem.

#### Preconditions

- The Member is signed in with an active account.
- A current loyalty point policy exists.

#### Normal Flow

***View Loyalty Points***

1. The Member opens My points.
2. The system loads the Member's own loyalty account and the current point policy.
3. The system shows the available, balance and held points.
4. The system shows the earning rate, point value, minimum points per use and point-discount cap.
5. The system lists the Member's point history, newest first.
6. The system shows each entry's time, type, source order or workshop, and balance and held changes.
7. The Member selects Load more.
8. The system adds the next page of history entries.
9. The system confirms the balance and history shown are the current recorded values.

#### Alternative Flows

***Step 1.1 Actor Is Not Signed In***

1. The system asks the actor to sign in with a Member account.
2. The actor signs in as a Member.
3. The use case continues at step 2.

***Step 1.2 Signed-in Actor Is Not a Member***

1. The system shows the not-found page without loading any loyalty data.
2. The use case ends.

***Step 2.1 Session Has Expired***

1. The system informs the Member that the session has expired (MSG02).
2. The use case ends without showing loyalty data.

***Step 2.2 Loyalty Account Cannot Be Loaded***

1. The system informs the Member that the data could not be loaded (MSG05).
2. The system shows no balance instead of a zero balance.
3. The system offers Retry.
4. The use case continues at step 2.

***Step 5.1 Member Has No Point Entry***

1. The system informs the Member that there are no point transactions yet (MSG341).
2. The use case ends.

***Step 5.2 Point History Cannot Be Loaded***

1. The system informs the Member that the data could not be loaded (MSG05).
2. The system offers Retry.
3. The use case continues at step 5.

***Step 7.1 No More History Entries***

1. The system offers no Load more.
2. The use case continues at step 9.

#### Postconditions

- The Member sees only their own points and point history.
- No point transaction is created or changed.

#### Business Rule

BR-18, BR-19, BR-197, BR-198
