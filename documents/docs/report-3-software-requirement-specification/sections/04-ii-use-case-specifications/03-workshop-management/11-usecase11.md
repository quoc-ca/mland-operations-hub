### 3.1 Workshop Management

#### 3.1.11 Configure Supported Materials by Branch

<table>
<tr><td>Primary Actors</td><td>Manager</td><td>Secondary Actors</td><td>None</td></tr>
<tr><td>Description</td><td colspan="3">Allows the Manager to configure which jewelry materials, ring options, and workshop packages are supported at each branch.</td></tr>
<tr><td>Preconditions</td><td colspan="3">1. The Manager is authenticated and authorized.<br>2. The branch and material/ring catalogue entries exist.<br>3. The selected material is active and eligible for workshop use.</td></tr>
<tr><td>Postconditions</td><td colspan="3">• The branch-material and supported-option configuration is saved.<br>• Booking and design selection flows use the branch-specific supported options.<br>• Existing bookings retain their recorded selections.</td></tr>
<tr><td>Normal<br>Sequence/Flow</td><td colspan="3"><em>Configure Supported Materials by Branch</em><br>1. The Manager selects a branch.<br>2. The system displays the currently supported materials, ring options, and packages.<br>3. The Manager adds, edits, or removes a supported option.<br>4. The system validates the catalogue entry, compatibility, active state, and branch scope.<br>5. The Manager confirms the configuration.<br>6. The system saves the branch configuration and makes it available to eligible booking/design flows.</td></tr>
<tr><td>Alternative<br>Sequences/Flows</td><td colspan="3"><em>Step 3 — Catalogue entry is inactive or missing</em><br>The system rejects the option and asks the Manager to select an active configured entry.<br><br><em>Step 4 — Material/design combination is incompatible</em><br>The system identifies the compatibility problem and does not save the combination.<br><br><em>Step 3 — Removing an option used by existing bookings</em><br>The system prevents removal from historical booking data and deactivates it only for new selections.<br><br><em>Step 6 — Save fails</em><br>The system displays an error and preserves the prior branch configuration.</td></tr>
<tr><td>Business Rule</td><td colspan="3">BR-25-01, BR-25-02</td></tr>
</table>

<table><tr style="background-color:#f4cccc"><th>ID</th><th>Rule Definition</th></tr><tr><td>BR-25-01</td><td>Only active and compatible materials/ring options configured for the selected branch may be offered for new workshop selections.</td></tr><tr><td>BR-25-02</td><td>Changing branch support affects new selections and must not rewrite materials or options already recorded on existing bookings.</td></tr></table>
