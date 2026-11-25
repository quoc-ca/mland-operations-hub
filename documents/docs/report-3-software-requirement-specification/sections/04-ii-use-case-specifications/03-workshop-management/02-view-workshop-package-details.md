### 3.2 View Workshop Package Details

#### Primary Actors

Guest, Member

#### Secondary Actors

None

#### Description

As a Guest or Member, I want to view the complete details of a workshop package so that I can understand its description, price, participant limits, sessions, and available branches before deciding whether to book.

#### Preconditions

1. The platform is available.
2. The actor has opened a published workshop package from the workshop package catalogue or has accessed its valid package link.
3. The requested package exists and is currently available for public viewing.


#### Postconditions

- The actor views the latest available details of the selected workshop package, or an appropriate unavailable/error state.
- No booking, invoice, payment, seat hold, or package-price snapshot is created.
- The actor may return to the package list or continue to UC 21 Create Workshop Booking.

#### Normal Flow

**View Workshop Package Details**


1. The actor selects a published package from the Browse Workshop Packages page.
2. The system retrieves the package details and associated published information.
3. The system displays the package name, description, price, minimum and maximum participants per booking line, the number of sessions and their location and spacing rules, and the branches where the package is active.
4. The actor reviews the package details.
5. The actor returns to the package list, books now, or adds the package to the workshop cart.
6. If the actor continues, the system passes the selected package to UC 21 Create Workshop Booking.

#### Alternative Flows

**Step 1 — Invalid or missing package identifier**

The system displays a not-found message and provides a link to the Workshop Packages page.

**Step 2 — Package is unpublished, inactive, or no longer available**

The system does not display the package details and informs the actor that the package is unavailable. The actor may return to the package list.

**Step 2 — Associated detail data is incomplete**

The system displays the available package information, marks unavailable fields appropriately, and does not invent missing information.

**Step 2 — Package retrieval fails**

The system displays an error message and allows the actor to retry or return to the package list.

#### Business Rules

BR-39-01, BR-39-02, BR-39-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-39-01 | Only published and active workshop packages may be viewed through the public package-details page. |
| BR-39-02 | Package details must reflect the currently published catalogue information; missing configuration must be shown as unavailable rather than inferred. |
| BR-39-03 | Viewing package details is read-only and does not create a booking, invoice, payment, seat hold, or price snapshot. |
