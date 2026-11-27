### 3.16 UC-47 Manage Workshop Packages

#### UC Name

UC-47 Manage Workshop Packages

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to create, publish, edit and retire workshop packages, their add-ons and their availability per location so that customers book only offers each branch really sells.

#### Preconditions

- The actor is signed in as an active Manager.

#### Normal Flow

***Create and Publish a Workshop Package***

1. The Manager opens the workshop package management page.
2. The system shows every package with its price, ticket bounds, required sessions and state.
3. The Manager selects Create package.
4. The Manager enters the name, description, public price, ticket bounds, required sessions and session rules.
5. The Manager selects Create draft.
6. The system validates the entered values.
7. The system saves the package as Draft and records the creation in the audit log.
8. The system informs the Manager that the draft has been created (MSG306).
9. The Manager selects Publish draft.
10. The system moves the package to Published and records the transition in the audit log.
11. The system confirms that the package has been published (MSG306).

#### Alternative Flows

***Step 3.1 Manager Edits a Package***

1. The Manager selects Edit on a package that is not retired.
2. The Manager changes the values and selects Save changes.
3. The system validates the values and saves them for bookings made afterwards only.
4. The system keeps the package and price frozen in every existing booking.
5. The system confirms that the package has been updated (MSG306).
6. The use case ends after the update.

***Step 3.2 Manager Retires a Published Package***

1. The Manager selects Retire on a published package.
2. The system asks the Manager to confirm the retirement (MSG307).
3. The Manager confirms the retirement.
4. The system moves the package to Retired and stops new bookings of it.
5. The system keeps existing bookings and the package record unchanged.
6. The system confirms that the package has been retired (MSG306).
7. The use case ends after the retirement.

***Step 3.3 Manager Sets Package Availability per Location***

1. The Manager opens the package by location matrix.
2. The system shows locations as rows and packages as columns, with a ticked cell for each active pair.
3. The Manager ticks or unticks one cell.
4. The system saves the new availability of that pair and records it in the audit log.
5. The system confirms the change of the pair (MSG309).
6. The use case ends after the change.

***Step 3.4 Availability Cell Was Changed by Another Manager***

1. The system informs the Manager that the cell was changed elsewhere (MSG03).
2. The system reloads the latest value of the cell and saves nothing.
3. The use case ends without a change.

***Step 3.5 Manager Manages Add-ons of a Package at a Location***

1. The Manager opens the add-on catalogue and selects a package and a location.
2. The system shows every add-on of that pair with its description, price and state.
3. The Manager enters a description and a price and selects Create draft.
4. The system validates the description and the price.
5. The system saves the add-on as Draft and records it in the audit log (MSG311).
6. The Manager selects Activate on the draft add-on.
7. The system makes the add-on Active and selectable by customers (MSG311).
8. The use case ends after the activation.

***Step 3.6 Manager Stops Selling or Edits an Add-on***

1. The Manager selects Stop selling, or edits the description or price of an add-on.
2. The system retires the add-on, or saves the new values for later selections only (MSG311).
3. The use case ends after the change.

***Step 3.7 Add-on Values Are Invalid***

1. The system shows that a package, a location, a description and a valid price are required (MSG310).
2. The system saves no add-on.
3. The use case ends without a change.

***Step 6.1 Package Values Are Invalid***

1. The system shows the error under each invalid field (MSG305).
2. The system saves nothing.
3. The use case continues at step 4.

***Step 6.2 Actor Is Not an Active Manager***

1. The system refuses the action (MSG01).
2. The system changes no package, availability or add-on.
3. The use case ends without a change.

***Step 10.1 Lifecycle Change Is Not Allowed***

1. The system informs the Manager that the package or add-on cannot take that state or be edited (MSG308).
2. The system keeps the current state and values.
3. The use case ends without a change.

#### Postconditions

- The package moves only from Draft to Published to Retired and is never deleted.
- Package changes apply to bookings made afterwards; existing bookings keep their frozen terms.
- A package is offered at a location only when it is Published and active there.
- Only Active add-ons of the selected package and location are offered to customers.
- Every applied change is kept in the audit log.

#### Business Rule

BR-171, BR-174, BR-175, BR-176, BR-177, BR-178
