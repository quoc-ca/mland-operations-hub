### 5.1 Administrative Parameter Configuration

#### 5.1.1 Configure System Operational Parameters

<table>
<tr><td>Primary Actors</td><td>Admin</td><td>Secondary Actors</td><td>None</td></tr>
<tr><td>Description</td><td colspan="3">Allows the Admin to configure operational values used by custom manufacturing, loyalty, and checkout workflows, including the minimum custom-order deadline, unavailable deadline dates, maximum queue size, loyalty rates, point value, and point-discount cap.</td></tr>
<tr><td>Preconditions</td><td colspan="3">1. The Admin is authenticated and authorized to manage operational parameters.<br>2. The platform is available.<br>3. The parameter definitions and permitted value ranges are configured by the system.</td></tr>
<tr><td>Postconditions</td><td colspan="3">• Valid parameter values are saved with the Admin actor and change time recorded.<br>• Subsequent applicable workflows use the new values.<br>• Invalid or incomplete values do not overwrite the previous configuration.</td></tr>
<tr><td>Normal<br>Sequence/Flow</td><td colspan="3"><em>Configure System Operational Parameters</em><br>1. The Admin opens operational parameter configuration.<br>2. The system displays the current values and their descriptions.<br>3. The Admin edits one or more values, such as minimum deadline, unavailable dates, queue size, loyalty rate, point value, or discount cap.<br>4. The system validates data types, ranges, dependencies, and effective-date rules.<br>5. The Admin reviews and confirms the changes.<br>6. The system saves the values, records an audit entry, and displays the updated configuration.</td></tr>
<tr><td>Alternative<br>Sequences/Flows</td><td colspan="3"><em>Step 4 — Value is invalid or outside the permitted range</em><br>The system displays field-level validation errors and does not save the invalid value.<br><br><em>Step 4 — Values are inconsistent</em><br>The system identifies the dependency, such as a discount cap exceeding the permitted limit, and asks the Admin to correct it.<br><br><em>Step 5 — Admin cancels</em><br>No parameter is changed.<br><br><em>Step 6 — Save fails</em><br>The system displays an error and preserves the previous configuration.</td></tr>
<tr><td>Business Rule</td><td colspan="3">BR-54-01, BR-54-02, BR-54-03</td></tr>
</table>

<table><tr style="background-color:#f4cccc"><th>ID</th><th>Rule Definition</th></tr><tr><td>BR-54-01</td><td>Only an authorized Admin may change system-wide operational parameters.</td></tr><tr><td>BR-54-02</td><td>Operational parameters must be validated as a consistent set before they become effective.</td></tr><tr><td>BR-54-03</td><td>Every saved parameter change must retain the acting Admin, time, previous value, and new value for audit purposes.</td></tr></table>
