### 3.15 UC-46 Manage Workshop Locations

#### UC Name

UC-46 Manage Workshop Locations

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to create, rename, readdress, deactivate and reactivate workshop locations so that sessions are planned only at real, operating branches.

#### Preconditions

- The actor is signed in as an active Manager.

#### Normal Flow

***Create a Workshop Location***

1. The Manager opens the Locations tab of workshop operations administration.
2. The system shows every workshop location with its code, name, address and status.
3. The Manager enters the code, name and address of the new location.
4. The Manager selects Create location.
5. The system checks that the code and the name are filled in.
6. The system checks that no other location uses the same code.
7. The system saves the location and records the creation in the audit log.
8. The system confirms that the location has been created (MSG299).

#### Alternative Flows

***Step 3.1 Manager Edits an Existing Location***

1. The Manager selects Edit on a location.
2. The system shows the name, address and active status; the code cannot be changed.
3. The Manager changes the name, the address or the active status and selects Save location.
4. The system checks that the name is not empty and that at least one value has changed.
5. The system saves each changed value and records one audit entry per change.
6. The system confirms that the location has been updated (MSG302).
7. The use case ends after the update.

***Step 3.2 Location Is Still Used by an Unfinished Session***

1. The Manager clears Active for a location and saves it.
2. The system finds a session at the location that is neither finalized nor cancelled.
3. The system refuses the deactivation (MSG303).
4. The system keeps the location and all its values unchanged.
5. The use case ends without a change.

***Step 3.3 No Value Has Changed***

1. The system informs the Manager that a value must be changed before saving (MSG304).
2. The system saves nothing.
3. The use case continues at step 3.

***Step 5.1 Code or Name Is Missing***

1. The system shows that the location code and name are required (MSG300).
2. The system does not create the location.
3. The use case continues at step 3.

***Step 5.2 Actor Is Not an Active Manager***

1. The system refuses the action (MSG01).
2. The system changes no location.
3. The use case ends without a change.

***Step 6.1 Location Code Already Exists***

1. The system informs the Manager that the code is already used (MSG301).
2. The system does not create the location.
3. The use case continues at step 3.

#### Postconditions

- The location is stored with a unique code that never changes.
- A new location sells no workshop package until a Manager enables one there.
- An inactive location is not offered for new slots or sessions.
- Every applied change is kept in the audit log with its prior and new value.

#### Business Rule

BR-171, BR-172, BR-173, BR-174, BR-177
