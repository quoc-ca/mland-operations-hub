### 5.1 Configure System Operational Parameters

#### Primary Actors

Admin

#### Secondary Actors

None

#### Description

Allows the Admin to configure operational values used by custom manufacturing, loyalty, and checkout workflows, including the minimum custom-order deadline, unavailable deadline dates, maximum queue size, loyalty rates, point value, and point-discount cap.

#### Preconditions

1. The Admin is authenticated and authorized to manage operational parameters.
2. The platform is available.
3. The parameter definitions and permitted value ranges are configured by the system.

#### Normal Flow

**Configure System Operational Parameters**

1. The Admin opens operational parameter configuration.
2. The system displays the current values and their descriptions.
3. The Admin edits one or more values, such as minimum deadline, unavailable dates, queue size, loyalty rate, point value, or discount cap.
4. The system validates data types, ranges, dependencies, and effective-date rules.
5. The Admin reviews and confirms the changes.
6. The system saves the values, records an audit entry, and displays the updated configuration.

#### Alternative Flows

**Step 4 — Value is invalid or outside the permitted range**

The system displays field-level validation errors and does not save the invalid value.

**Step 4 — Values are inconsistent**

The system identifies the dependency, such as a discount cap exceeding the permitted limit, and asks the Admin to correct it.

**Step 5 — Admin cancels**

No parameter is changed.

**Step 6 — Save fails**

The system displays an error and preserves the previous configuration.

#### Postconditions

• Valid parameter values are saved with the Admin actor and change time recorded.• Subsequent applicable workflows use the new values.• Invalid or incomplete values do not overwrite the previous configuration.

#### Business Rules

BR-54-01, BR-54-02, BR-54-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-54-01 | Only an authorized Admin may change system-wide operational parameters. |
| BR-54-02 | Operational parameters must be validated as a consistent set before they become effective. |
| BR-54-03 | Every saved parameter change must retain the acting Admin, time, previous value, and new value for audit purposes. |
