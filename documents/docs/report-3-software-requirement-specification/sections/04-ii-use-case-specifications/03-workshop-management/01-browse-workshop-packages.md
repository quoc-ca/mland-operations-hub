### 3.1 Browse Workshop Packages

| Use Case ID | UC-19 | Use Case Name | Browse Workshop Packages |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member | Secondary Actors | None |

#### Description

As a Guest or Member, I want to browse published workshop packages so that I can compare their overview and price before viewing details or starting a booking.

#### Trigger

An actor opens the Workshop Packages page.

#### Preconditions

1. The platform is available.
2. The actor can access the public workshop catalogue without authentication.
3. Workshop package data has been configured in the catalogue.

#### Postconditions

1. The actor views matching published workshop packages, or an empty or error state.
2. No booking, invoice, payment, seat hold, or package-price snapshot is created.
3. The actor may continue to UC-20 View Workshop Package Details or UC-21 Create Workshop Booking.

#### Normal Flow

1. Actor opens the Workshop Packages page.
2. System retrieves workshop packages with a published and active status.
3. System paginates the package list without keyword search or filters.
4. System displays each package name, overview, and discounted one-ticket price with the original price struck through.
5. System provides controls to view package details or continue to booking.
6. Actor selects a package or opens its details.

#### Alternative Flows

3a. Requested page does not exist → System displays an available page of the list.

#### Exception Flows

2a. Catalogue retrieval fails → System displays an error message and allows the actor to retry.

4a. No published package exists → System displays an empty-list message.

4b. Package becomes inactive or unpublished → System excludes it from refreshed results and informs the actor that the catalogue changed.

#### Business Rules

BR-38-01, BR-38-02, BR-38-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-38-01 | Only workshop packages with a published and active status are displayed to Guests and Members. |
| BR-38-02 | Browsing workshop packages is read-only; it does not create a booking, invoice, payment, seat hold, or price snapshot. |
| BR-38-03 | A workshop package must be selected before the actor proceeds to the workshop booking flow. |
