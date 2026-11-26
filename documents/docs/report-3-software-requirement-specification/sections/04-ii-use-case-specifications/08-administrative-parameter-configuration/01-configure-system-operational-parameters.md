### 8.1 Configure System Operational Parameters

#### Primary Actors

Admin

#### Secondary Actors

None

#### Description

Allows the Admin to configure approved operational values used by checkout workflows and the workshop-ring difficulty policy. The current FE-09 delivery covers only the ring-difficulty screen; checkout operational parameters remain a separate, unspecified delivery scope. For ring difficulty, the Admin alone changes and publishes per-option scores, engraving coefficients and classification/recommendation thresholds; Feature-012 owns the policy and evaluation. The Manager may maintain separately authorized non-difficulty ring-catalogue fields but cannot change or approve difficulty values. The loyalty point policy (earning rate, point value, minimum points per use and point-discount cap) is not part of this use case; only the Manager manages it (use case 51). Custom-order queue configuration is retired from V1 (see section 10.2).

#### Preconditions

1. The Admin is authenticated and authorized to manage operational parameters.
2. The platform is available.
3. The parameter definitions and permitted value ranges are configured by the system.


#### Postconditions

- Valid parameter values are saved with the Admin actor and change time recorded.
- Subsequent applicable workflows use the new values.
- Invalid or incomplete values do not overwrite the previous configuration.

#### Normal Flow

**Configure System Operational Parameters**

1. The Admin opens operational parameter configuration.
2. The system displays the current values and their descriptions.
3. The Admin edits one or more values.
4. The system validates data types, ranges and dependencies. For ring difficulty it previews the old and proposed outcomes on required standard non-personal examples and any additional non-personal examples chosen by Admin, without exposing Member records.
5. The Admin reviews and confirms the changes.
6. For ring difficulty, the API verifies a fresh preview of the exact proposed values by the same Admin, then atomically publishes a new immutable policy version, records an audit entry, and displays the effective configuration. Other operational parameters follow their separately specified delivery contract.

#### Alternative Flows

**Step 4 — Value is invalid or outside the permitted range**

The system displays field-level validation errors and does not save the invalid value.

**Step 4 — Values are inconsistent**

The system identifies the dependency and asks the Admin to correct it.

**Step 5 — Admin cancels**

No parameter is changed.

**Step 6 — Save fails**

The system displays an error and preserves the previous configuration.

**Step 6 — Ring-difficulty preview proof is missing, expired or mismatched**

The API rejects publication, keeps the prior policy effective and asks the Admin
to preview the current candidate again. A retry of a previously completed
identical publication returns its recorded result.

#### Business Rules

BR-62-01, BR-62-02, BR-62-03, BR-62-04, BR-62-05, BR-62-06

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-62-01 | Only an authorized Admin may change system-wide operational parameters. |
| BR-62-02 | Operational parameters must be validated as a consistent set before they become effective. |
| BR-62-03 | Every saved parameter change must retain the acting Admin, time, previous value, and new value for audit purposes. |
| BR-62-04 | Only an authorized Admin may publish a workshop-ring difficulty policy. Manager catalogue commands must not alter any difficulty score, coefficient or threshold. |
| BR-62-05 | A ring-difficulty publication is an atomic, versioned change after a before/after preview. Existing saved design versions retain their original evaluation; subsequent evaluations use the newly effective policy. |
| BR-62-06 | The ring-difficulty publication API rejects a missing, expired or mismatched preview proof; a completed identical retry replays its original result. |
