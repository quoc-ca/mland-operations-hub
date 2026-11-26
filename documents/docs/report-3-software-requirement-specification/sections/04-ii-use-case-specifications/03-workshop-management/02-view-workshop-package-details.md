### 3.2 View Workshop Package Details

| Use Case ID | UC-20 | Use Case Name | View Workshop Package Details |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member | Secondary Actors | None |

#### Description

As a Guest or Member, I want to view the complete details of a workshop package so that I can understand its price, participant limits, sessions, and branches before booking.

#### Trigger

An actor opens a published workshop package from the catalogue or a valid package link.

#### Preconditions

1. The platform is available.
2. The actor has opened a published workshop package from the catalogue or via a valid package link.
3. The requested package exists and is currently available for public viewing.

#### Postconditions

1. The actor views the latest available details of the selected package, or an unavailable or error state.
2. No booking, invoice, payment, seat hold, or package-price snapshot is created.
3. The actor may return to the package list or continue to UC-21 Create Workshop Booking.

#### Normal Flow

1. Actor selects a published package from the Workshop Packages page.
2. System retrieves the package details and associated published information.
3. System displays the description, price, participant limits, session count, location and spacing rules, and active branches.
4. Actor reviews the package details.
5. Actor selects to book now or add the package to the workshop cart.
6. System passes the selected package to UC-21 Create Workshop Booking.

#### Alternative Flows

2a. Associated detail data is incomplete → System displays available details and marks missing fields as unavailable without inventing data.

5a. Actor chooses to return to the package list → System navigates back to UC-19 Browse Workshop Packages.

#### Exception Flows

1a. Package identifier is invalid or missing → System displays a not-found message and links to the Workshop Packages page.

2a. Package is unpublished, inactive, or no longer available → System hides details, informs the actor, and allows returning to the list.

2b. Package retrieval fails → System displays an error message and allows the actor to retry or return.

#### Business Rules

BR-39-01, BR-39-02, BR-39-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-39-01 | Only published and active workshop packages may be viewed through the public package-details page. |
| BR-39-02 | Package details must reflect the currently published catalogue information; missing configuration must be shown as unavailable rather than inferred. |
| BR-39-03 | Viewing package details is read-only and does not create a booking, invoice, payment, seat hold, or price snapshot. |
