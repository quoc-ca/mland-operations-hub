### 6.3 UC-65 Manage Promotions

#### UC Name

UC-65 Manage Promotions

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to create, edit, pause and resume automatic discounts and vouchers for products or workshop packages so that customers receive the intended offers.

#### Preconditions

- The Manager is signed in with an active account and the Manager role.

#### Normal Flow

***Create a Voucher***

1. The Manager opens Promotion programmes.
2. The system lists the programmes with name, code, kind, scope, discount, period and status.
3. The Manager selects New programme.
4. The system opens the form with a generated voucher code and a default 30-day period.
5. The Manager chooses Voucher and the product or workshop scope.
6. The Manager enters the name, period, discount, cap, minimum amount and point rule.
7. The Manager chooses the distribution, total uses and uses per Member.
8. The Manager selects the products, sizes and branches, or the workshop package, branch and session.
9. The system checks each field as its value changes.
10. The Manager selects Create programme.
11. The system validates the data, scope and code uniqueness.
12. The system saves the voucher and records the change in the audit log.
13. The system opens the programme details and confirms the creation (MSG38).

#### Alternative Flows

***Step 2.1 Manager Filters the List***

1. The Manager filters by scope or by status.
2. The system lists only the matching programmes.
3. The use case continues at step 3.

***Step 2.2 No Programme Matches***

1. The system informs the Manager that no programme matches the filters (MSG342).
2. The use case continues at step 3.

***Step 2.3 Programme List Cannot Be Loaded***

1. The system informs the Manager that the data could not be loaded (MSG05).
2. The use case ends.

***Step 5.1 Manager Creates an Automatic Discount***

1. The Manager chooses Automatic discount.
2. The system removes the code, distribution, use limits and point option.
3. The system states that an automatic discount always combines with points.
4. The Manager selects products or sizes, or one or more workshop packages only.
5. The use case continues at step 6.

***Step 6.1 Manager Chooses a Fixed Amount***

1. The Manager chooses a fixed discount amount.
2. The system hides the maximum discount field.
3. The use case continues at step 7.

***Step 7.1 Manager Chooses Must Be Saved***

1. The Manager chooses Must be saved.
2. The system sets uses per Member to one and locks that field.
3. The use case continues at step 8.

***Step 8.1 Selected Size Is Not Sold at a Chosen Branch***

1. The system warns that the size is not sold at any chosen branch.
2. The system still allows the save.
3. The use case continues at step 9.

***Step 9.1 Field Value Is Invalid***

1. The system shows the invalid field and the rule under that field (MSG08).
2. The Manager corrects the value.
3. The use case continues at step 9.

***Step 11.1 Scope Is Invalid***

1. The system rejects the save and shows the scope error under the picker (MSG40).
2. The use case continues at step 8.

***Step 11.2 Generated Code Already Exists***

1. The system draws a new code and sends the request again, up to three draws.
2. The use case continues at step 12.

***Step 11.3 Code Still Exists After Three Draws***

1. The system informs the Manager that the voucher code already exists (MSG39).
2. The use case ends without creating the programme.

***Step 11.4 Actor Lost the Manager Role or Session***

1. The system rejects the request (MSG01) or asks the Manager to sign in again (MSG02).
2. The system records the refused request in the audit log.
3. The use case ends without saving.

***Step 12.1 Result Is Not Confirmed***

1. The system informs the Manager that the result could not be confirmed (MSG04).
2. The system keeps the request so the Manager can send it again without a duplicate.
3. The use case continues at step 11.

***Step 13.1 Manager Views a Programme***

1. The Manager opens a programme from the list.
2. The system shows its conditions, targets, version and status.
3. The system shows used, held and saved uses, accepted orders and total discount given.
4. The system warns about targets that are hidden, stopped from sale, deleted, or inactive branches (MSG43).
5. The use case ends.

***Step 13.2 Manager Edits a Programme***

1. The Manager selects Edit on the programme details.
2. The system shows only the name, period, discount, minimum, limits and point option.
3. The Manager changes values and selects Save.
4. The system validates the changed values and saves a new revision for new transactions.
5. The system confirms the change (MSG42).
6. The use case ends.

***Step 13.3 Nothing Has Changed***

1. The system informs the Manager that nothing has changed yet (MSG344).
2. The use case continues at step 3 of Step 13.2.

***Step 13.4 New Limit Is Too Low***

1. The system rejects the limit below the used, held and saved uses (MSG41).
2. The use case continues at step 3 of Step 13.2.

***Step 13.5 Programme Was Changed by Someone Else***

1. The system rejects the change made against an older version (MSG03).
2. The Manager reloads the latest programme.
3. The use case continues at step 3 of Step 13.2.

***Step 13.6 Manager Pauses or Resumes a Programme***

1. The Manager selects Pause on an enabled programme, or Resume on a paused one.
2. The system asks the Manager to confirm and explains the effect (MSG343).
3. The Manager confirms.
4. The system changes the status and keeps the code, period, history and counts.
5. The system confirms the change (MSG42).
6. The use case ends.

***Step 13.7 Manager Cancels the Pause or Resume***

1. The Manager selects Cancel in the confirmation dialog.
2. The system closes the dialog without changing the programme.
3. The use case ends.

#### Postconditions

- A saved programme applies only to orders and bookings accepted after the save.
- Each voucher keeps a unique code that is never changed or reused.
- Each create, edit, pause and resume is recorded in the audit log.

#### Business Rule

BR-09, BR-15, BR-16, BR-17, BR-20, BR-22, BR-24, BR-25, BR-27, BR-32, BR-199, BR-200, BR-201, BR-202, BR-203, BR-204
