### 3.23 Manage Workshop Packages

| Use Case ID | UC-91 | Use Case Name | Manage Workshop Packages |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | None |

#### Description

As a Manager, I want to manage workshop packages, their add-ons, and selling branches so that customers book correct offers.

#### Trigger

A Manager opens workshop package, package add-on, or package availability management.

#### Preconditions

1. The Manager is authenticated and active.
2. An add-on or availability change requires an existing package and branch.

#### Postconditions

1. Packages, add-ons, and branch availability are saved with an audit entry.
2. Existing bookings stay unchanged.

#### Normal Flow

**Manage Packages**

1. Actor opens workshop package management.
2. System displays packages with state, price, ticket range, and required sessions.
3. Actor enters package details and saves a draft.
4. System validates and saves the package.
5. Actor publishes the draft.
6. System makes the package visible to customers.

**Manage Add-ons**

1. Actor selects a package and a branch.
2. System displays the add-ons of that pair.
3. Actor enters an add-on description and price.
4. System saves the add-on as a draft.
5. Actor activates the add-on.
6. System makes the add-on available for charging.

**Manage Branch Availability**

1. Actor opens the package availability matrix.
2. System displays branches against packages.
3. Actor turns selling on or off for one package at one branch.
4. System saves the change and confirms it.

#### Alternative Flows

P3a. Actor edits a package that is not retired → System applies the change to later bookings only.

P5a. Actor retires a published package → System stops new bookings and keeps existing bookings.

A3a. Actor edits an add-on that is not retired → System saves the new description or price.

A5a. Actor retires an active add-on → System stops offering it.

#### Exception Flows

P4a. Price, ticket range, session count, or another field is invalid → System rejects the package.

P5b. State change is not draft to published or published to retired → System refuses it.

A4a. Description is blank or price is not positive whole VND → System rejects the add-on.

V4a. The cell was changed elsewhere → System saves nothing and reloads the latest value.

#### Business Rules

BR-91-01, BR-91-02, BR-91-03, BR-91-04

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-91-01 | Only an active Manager creates or changes packages, add-ons, and branch availability. |
| BR-91-02 | A package moves from draft to published to retired; a retired package cannot be edited. |
| BR-91-03 | An add-on belongs to one package at one branch; it moves from draft to active to retired. |
| BR-91-04 | Turning availability off stops new bookings at that branch only; existing bookings stay unchanged. |
