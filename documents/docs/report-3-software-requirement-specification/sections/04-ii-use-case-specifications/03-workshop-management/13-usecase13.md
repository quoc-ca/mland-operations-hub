### 3.1 Workshop Management

#### 3.1.13 Synchronize Workshop Schedule

<table>
<tr><td>Primary Actors</td><td>Manager</td><td>Secondary Actors</td><td>External Registration System</td></tr>
<tr><td>Description</td><td colspan="3">Exchanges enabled workshop availability updates with Klook while Mland remains the source of truth for capacity and holds.</td></tr>
<tr><td>Preconditions</td><td colspan="3">1. Klook credentials and endpoint configuration are approved and available.<br>2. Mland has a current schedule, capacity, and availability state.<br>3. The schedule or synchronization job is enabled.</td></tr>
<tr><td>Postconditions</td><td colspan="3">• An enabled availability update is sent to Klook and the synchronization result is recorded.<br>• Mland capacity and holds remain authoritative.<br>• A failed synchronization is available for retry and does not overwrite Mland availability.</td></tr>
<tr><td>Normal<br>Sequence/Flow</td><td colspan="3"><em>Synchronize Workshop Schedule</em><br>1. The Manager saves a schedule/capacity change or the synchronization job starts.<br>2. The system selects the affected branch, date, session, capacity, and availability state.<br>3. The system builds the approved Klook availability payload.<br>4. The system sends the update to Klook.<br>5. The system validates the response and records the synchronization status and correlation details.<br>6. Klook uses Mland availability/hold before confirming an external booking.</td></tr>
<tr><td>Alternative<br>Sequences/Flows</td><td colspan="3"><em>Step 2 — No enabled integration configuration</em><br>The system records that synchronization is skipped and keeps Mland operations available.<br><br><em>Step 4 — Klook rejects or cannot receive the update</em><br>The system records the failure and schedules a retry without changing Mland capacity.<br><br><em>Step 5 — Response is invalid or duplicated</em><br>The system does not apply an ambiguous response and records the issue for reconciliation.<br><br><em>Step 6 — Klook requests confirmation without an Mland hold</em><br>The system rejects the confirmation path because Mland must authorize availability first.</td></tr>
<tr><td>Business Rule</td><td colspan="3">GBR-11, BR-27-01, BR-27-02</td></tr>
</table>

<table><tr style="background-color:#f4cccc"><th>ID</th><th>Rule Definition</th></tr><tr><td>GBR-11</td><td>Klook obtains Mland availability/hold before it confirms a workshop booking; Mland remains the capacity source of truth.</td></tr><tr><td>BR-27-01</td><td>Only enabled and approved Klook integration configuration may send workshop availability updates.</td></tr><tr><td>BR-27-02</td><td>A synchronization failure must not alter Mland's schedule, capacity, or hold state.</td></tr></table>
