### 3.1 Workshop Management

#### 3.1.2 View Workshop Package Details

<table>
<tr><td>Primary Actors</td><td>Guest, Member</td><td>Secondary Actors</td><td>None</td></tr>
<tr><td>Description</td><td colspan="3">As a Guest or Member, I want to view the complete details of a workshop package so that I can understand what is included, the required materials and tools, the duration, the price, and the applicable terms before deciding whether to book.</td></tr>
<tr><td>Preconditions</td><td colspan="3">1. The platform is available.<br>2. The actor has opened a published workshop package from the workshop package catalogue or has accessed its valid package link.<br>3. The requested package exists and is currently available for public viewing.</td></tr>
<tr><td>Postconditions</td><td colspan="3">• The actor views the latest available details of the selected workshop package, or an appropriate unavailable/error state.<br>• No booking, invoice, payment, seat hold, or package-price snapshot is created.<br>• The actor may return to the package list or continue to UC17 Create Workshop Booking.</td></tr>
<tr><td>Normal<br>Sequence/Flow</td><td colspan="3"><em>View Workshop Package Details</em><br>1. The actor selects a published package from the Browse Workshop Packages page.<br>2. The system retrieves the package details and associated published information.<br>3. The system displays the package name, overview, price, currency, duration, deposit information, included materials, available tools, supported workshop/design information, and applicable terms.<br>4. The actor reviews the package details.<br>5. The actor returns to the package list or selects the option to continue to workshop booking.<br>6. If the actor continues, the system passes the selected package to UC17 Create Workshop Booking.</td></tr>
<tr><td>Alternative<br>Sequences/Flows</td><td colspan="3"><em>Step 1 — Invalid or missing package identifier</em><br>The system displays a not-found message and provides a link to the Workshop Packages page.<br><br><em>Step 2 — Package is unpublished, inactive, or no longer available</em><br>The system does not display the package details and informs the actor that the package is unavailable. The actor may return to the package list.<br><br><em>Step 2 — Associated detail data is incomplete</em><br>The system displays the available package information, marks unavailable fields appropriately, and does not invent missing materials, tools, or terms.<br><br><em>Step 2 — Package retrieval fails</em><br>The system displays an error message and allows the actor to retry or return to the package list.</td></tr>
<tr><td>Business Rule</td><td colspan="3">BR-16-01, BR-16-02, BR-16-03</td></tr>
</table>

<table>
<tr style="background-color:#f4cccc"><th>ID</th><th>Rule Definition</th></tr>
<tr><td>BR-16-01</td><td>Only published and active workshop packages may be viewed through the public package-details page.</td></tr>
<tr><td>BR-16-02</td><td>Package details must reflect the currently published catalogue information; missing configuration must be shown as unavailable rather than inferred.</td></tr>
<tr><td>BR-16-03</td><td>Viewing package details is read-only and does not create a booking, invoice, payment, seat hold, or price snapshot.</td></tr>
</table>
