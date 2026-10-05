### 3.1 Workshop Management

#### 3.1.1 Browse Workshop Packages

<table>
<tr><td>Primary Actors</td><td>Guest, Member</td><td>Secondary Actors</td><td>None</td></tr>
<tr><td>Description</td><td colspan="3">As a Guest or Member, I want to browse published workshop packages so that I can compare their overview, price, duration, and availability before viewing details or starting a booking.</td></tr>
<tr><td>Preconditions</td><td colspan="3">1. The platform is available.<br>2. The actor can access the public workshop catalogue without authentication.<br>3. Workshop package data has been configured in the catalogue.</td></tr>
<tr><td>Postconditions</td><td colspan="3">• The actor views matching published workshop packages, or an appropriate empty/error state.<br>• No booking, invoice, payment, seat hold, or package-price snapshot is created.<br>• The actor may continue to UC16 View Workshop Package Details or UC17 Create Workshop Booking.</td></tr>
<tr><td>Normal<br>Sequence/Flow</td><td colspan="3"><em>Browse Workshop Packages</em><br>1. The actor opens the Workshop Packages page.<br>2. The system retrieves workshop packages with a published and active status.<br>3. The actor optionally enters a keyword or applies available filters, such as branch or package status.<br>4. The system validates the search and filter criteria.<br>5. The system returns matching packages in the configured catalogue display order.<br>6. The system displays each package's name, overview, price, duration, and controls to view details or continue to booking.<br>7. The actor selects a package or opens its details.</td></tr>
<tr><td>Alternative<br>Sequences/Flows</td><td colspan="3"><em>Step 5 — No matching package exists</em><br>The system displays an empty-result message and allows the actor to change or clear the search/filter criteria.<br><br><em>Step 5 — A package is inactive, unpublished, or no longer available</em><br>The system excludes the package from refreshed results and informs the actor that the catalogue has changed.<br><br><em>Step 4 — Invalid or unsupported search/filter criteria</em><br>The system rejects the criteria, displays a validation message, and keeps the actor on the Browse Workshop Packages page.<br><br><em>Step 2 — Catalogue retrieval fails</em><br>The system displays an error message and allows the actor to retry.</td></tr>
<tr><td>Business Rule</td><td colspan="3">BR-15-01, BR-15-02, BR-15-03</td></tr>
</table>

<table>
<tr style="background-color:#f4cccc"><th>ID</th><th>Rule Definition</th></tr>
<tr><td>BR-15-01</td><td>Only workshop packages with a published and active status are displayed to Guests and Members.</td></tr>
<tr><td>BR-15-02</td><td>Browsing workshop packages is read-only; it does not create a booking, invoice, payment, seat hold, or price snapshot.</td></tr>
<tr><td>BR-15-03</td><td>A workshop package must be selected before the actor proceeds to the workshop booking flow.</td></tr>
</table>
