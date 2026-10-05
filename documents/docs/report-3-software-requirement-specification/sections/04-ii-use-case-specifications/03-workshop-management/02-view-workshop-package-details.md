### 3.2 View Workshop Package Details

| Attribute | Value |
|---|---|
| Primary Actors | Guest, Member |
| Secondary Actors | None |
| Description | As a Guest or Member, I want to view the complete details of a workshop package so that I can understand what is included, the required materials and tools, the duration, the price, and the applicable terms before deciding whether to book. |
| Preconditions | 1. The platform is available.<br>2. The actor has opened a published workshop package from the workshop package catalogue or has accessed its valid package link.<br>3. The requested package exists and is currently available for public viewing. |
| Postconditions | • The actor views the latest available details of the selected workshop package, or an appropriate unavailable/error state.• No booking, invoice, payment, seat hold, or package-price snapshot is created.• The actor may return to the package list or continue to UC17 Create Workshop Booking. |
| Normal Sequence/Flow | View Workshop Package Details<br>1. The actor selects a published package from the Browse Workshop Packages page.<br>2. The system retrieves the package details and associated published information.<br>3. The system displays the package name, overview, price, currency, duration, deposit information, included materials, available tools, supported workshop/design information, and applicable terms.<br>4. The actor reviews the package details.<br>5. The actor returns to the package list or selects the option to continue to workshop booking.<br>6. If the actor continues, the system passes the selected package to UC17 Create Workshop Booking. |
| Alternative Sequences/Flows | <br><br>Step 1 — Invalid or missing package identifierThe system displays a not-found message and provides a link to the Workshop Packages page.Step 2 — Package is unpublished, inactive, or no longer availableThe system does not display the package details and informs the actor that the package is unavailable. The actor may return to the package list.Step 2 — Associated detail data is incompleteThe system displays the available package information, marks unavailable fields appropriately, and does not invent missing materials, tools, or terms.Step 2 — Package retrieval failsThe system displays an error message and allows the actor to retry or return to the package list. |
| Business Rule | BR-16-01, BR-16-02, BR-16-03 |

| ID | Rule Definition |
|---|---|
| BR-16-01 | Only published and active workshop packages may be viewed through the public package-details page. |
| BR-16-02 | Package details must reflect the currently published catalogue information; missing configuration must be shown as unavailable rather than inferred. |
| BR-16-03 | Viewing package details is read-only and does not create a booking, invoice, payment, seat hold, or price snapshot. |
