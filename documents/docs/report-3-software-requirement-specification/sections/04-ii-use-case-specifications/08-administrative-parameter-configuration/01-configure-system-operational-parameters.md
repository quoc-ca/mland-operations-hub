### 8.1 Configure System Operational Parameters

#### Primary Actors

Admin

#### Secondary Actors

None

#### Description

Allows the Admin to configure operational values used by checkout workflows. The loyalty point policy (earning rate, point value, minimum points per use and point-discount cap) is not part of this use case; only the Manager manages it (use case 51). Custom-order queue configuration is retired from V1 (see section 10.2).

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
4. The system validates data types, ranges, dependencies, and effective-date rules.
5. The Admin reviews and confirms the changes.
6. The system saves the values, records an audit entry, and displays the updated configuration.

#### Alternative Flows

**Step 4 — Value is invalid or outside the permitted range**

The system displays field-level validation errors and does not save the invalid value.

**Step 4 — Values are inconsistent**

The system identifies the dependency and asks the Admin to correct it.

**Step 5 — Admin cancels**

No parameter is changed.

**Step 6 — Save fails**

The system displays an error and preserves the previous configuration.

#### Business Rules

BR-62-01, BR-62-02, BR-62-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-62-01 | Only an authorized Admin may change system-wide operational parameters. |
| BR-62-02 | Operational parameters must be validated as a consistent set before they become effective. |
| BR-62-03 | Every saved parameter change must retain the acting Admin, time, previous value, and new value for audit purposes. |
