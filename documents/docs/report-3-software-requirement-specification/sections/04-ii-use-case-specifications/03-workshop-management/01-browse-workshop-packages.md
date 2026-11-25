### 3.1 Browse Workshop Packages

#### Primary Actors

Guest, Member

#### Secondary Actors

None

#### Description

As a Guest or Member, I want to browse published workshop packages so that I can compare their overview and price before viewing details or starting a booking.

#### Preconditions

1. The platform is available.
2. The actor can access the public workshop catalogue without authentication.
3. Workshop package data has been configured in the catalogue.


#### Postconditions

- The actor views matching published workshop packages, or an appropriate empty/error state.
- No booking, invoice, payment, seat hold, or package-price snapshot is created.
- The actor may continue to UC 20 View Workshop Package Details or UC 21 Create Workshop Booking.

#### Normal Flow

**Browse Workshop Packages**

1. The actor opens the Workshop Packages page.
2. The system retrieves workshop packages with a published and active status.
3. The system prepares the list; no keyword search or filter is offered.
4. The system paginates the list.
5. The system returns the published packages.
6. The system displays each package's name, overview, and the price of one ticket after the best automatic discount (the original price struck through; vouchers and points are not subtracted), with controls to view details or continue to booking.
7. The actor selects a package or opens its details.

#### Alternative Flows

**Step 2 — Catalogue retrieval fails**

The system displays an error message and allows the actor to retry.

**Step 4 — Requested page does not exist**

The system shows an available page of the list.

**Step 5 — No published package exists**

The system displays an empty-list message.

**Step 5 — package is inactive, unpublished, or no longer available**

The system excludes the package from refreshed results and informs the actor that the catalogue has changed.

#### Business Rules

BR-38-01, BR-38-02, BR-38-03

#### Business Rule Definitions

| ID       | Rule Definition                                                                                                        |
| -------- | ---------------------------------------------------------------------------------------------------------------------- |
| BR-38-01 | Only workshop packages with a published and active status are displayed to Guests and Members.                         |
| BR-38-02 | Browsing workshop packages is read-only; it does not create a booking, invoice, payment, seat hold, or price snapshot. |
| BR-38-03 | A workshop package must be selected before the actor proceeds to the workshop booking flow.                            |
