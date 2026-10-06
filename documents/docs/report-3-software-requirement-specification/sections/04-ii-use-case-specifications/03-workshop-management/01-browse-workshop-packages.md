### 3.1 Browse Workshop Packages

#### Primary Actors

Guest, Member

#### Secondary Actors

None

#### Description

As a Guest or Member, I want to browse published workshop packages so that I can compare their overview, price, duration, and availability before viewing details or starting a booking.

#### Preconditions

1. The platform is available.
2. The actor can access the public workshop catalogue without authentication.
3. Workshop package data has been configured in the catalogue.

#### Normal Flow

**Browse Workshop Packages**

1. The actor opens the Workshop Packages page.
2. The system retrieves workshop packages with a published and active status.
3. The actor optionally enters a keyword or applies available filters, such as branch or package status.
4. The system validates the search and filter criteria.
5. The system returns matching packages in the configured catalogue display order.
6. The system displays each package's name, overview, price, duration, and controls to view details or continue to booking.
7. The actor selects a package or opens its details.

#### Alternative Flows

**Step 5 — matching package exists**

The system displays an empty-result message and allows the actor to change or clear the search/filter criteria.

**Step 5 — package is inactive, unpublished, or no longer available**

The system excludes the package from refreshed results and informs the actor that the catalogue has changed.

**Step 4 — Invalid or unsupported search/filter criteria**

The system rejects the criteria, displays a validation message, and keeps the actor on the Browse Workshop Packages page.

**Step 2 — Catalogue retrieval fails**

The system displays an error message and allows the actor to retry.

#### Postconditions

- The actor views matching published workshop packages, or an appropriate empty/error state.
- No booking, invoice, payment, seat hold, or package-price snapshot is created.
- The actor may continue to UC16 View Workshop Package Details or UC17 Create Workshop Booking.

#### Business Rules

BR-15-01, BR-15-02, BR-15-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-15-01 | Only workshop packages with a published and active status are displayed to Guests and Members. |
| BR-15-02 | Browsing workshop packages is read-only; it does not create a booking, invoice, payment, seat hold, or price snapshot. |
| BR-15-03 | A workshop package must be selected before the actor proceeds to the workshop booking flow. |
