### 2.4 UC-33 Manage Product Categories

#### UC Name

UC-33 Manage Product Categories

#### Primary Actors

Staff, Manager

#### Secondary Actors

None

#### Description

As a Staff member or Manager, I want to create, rename, activate and deactivate product categories so that products are grouped clearly without losing history.

#### Preconditions

- The actor is signed in with an active Staff or Manager account.

#### Normal Flow

***Create Product Category***

1. The actor opens Product categories.
2. The system lists the categories by name, 20 per page, with their status.
3. The actor enters a category name and an optional description.
4. The actor selects Create category.
5. The system trims the values and validates their length.
6. The system checks that no category has the same name, ignoring case.
7. The system saves the category as active and records an audit entry.
8. The system confirms that the category has been created (MSG10).

#### Alternative Flows

***Step 2.1 Actor Filters the Category List***

1. The actor selects a status filter and enters part of a category name.
2. The system applies both filters before paging and returns to the first page.
3. The system shows that no category matches when the result is empty (MSG60).
4. The use case continues at step 3.

***Step 2.2 Search Text Is Too Long***

1. The system rejects a name search over 200 characters (MSG08).
2. The use case continues at step 2.1.1.

***Step 3.1 Actor Renames a Category***

1. The actor selects Edit on a category and changes its name or description.
2. The actor selects Save.
3. The use case continues at step 5.

***Step 3.2 Actor Deactivates a Category***

1. The actor selects Deactivate on an active category.
2. The system shows the number and list of affected products and asks for confirmation (MSG29).
3. The actor confirms the deactivation.
4. The system deactivates the category and records an audit entry.
5. The system removes its products from public reads and new purchases without changing their publication.
6. The system confirms the change (MSG10).
7. The use case ends.

***Step 3.3 Actor Activates a Category Again***

1. The actor selects Activate on an inactive category and confirms the dialog.
2. The system activates the category and records an audit entry.
3. The system shows again only its products that are still published, not deleted and eligible.
4. The system confirms the change (MSG10).
5. The use case ends.

***Step 3.4 Actor Cancels the Confirmation***

1. The system closes the dialog without changing the category.
2. The use case ends.

***Step 5.1 Name or Description Is Invalid***

1. The system marks an empty name, a name over 200 characters or a description over 5,000 characters (MSG08).
2. The use case continues at step 3.

***Step 6.1 Category Name Already Exists***

1. The system rejects the name, which matches an active or inactive category (MSG28).
2. The use case continues at step 3.

***Step 7.1 Category Changed Meanwhile***

1. The system rejects a change based on an older version and offers to reload (MSG03).
2. The use case continues at step 2.

***Step 7.2 Actor Lacks Permission***

1. The system rejects an actor who is not an active Staff member or Manager (MSG01).
2. The use case ends without a change.

***Step 7.3 Result Is Not Confirmed***

1. The system reports that the result is unknown (MSG04).
2. The actor retries the same request, which creates no duplicate category.
3. The use case continues at step 7.

#### Postconditions

- Category names stay unique across all categories, ignoring case.
- Categories are deactivated, never deleted, and every change is audited.
- Valid holds, accepted orders and product history are unchanged.

#### Business Rule

BR-13, BR-69, BR-75, BR-76, BR-77
