### 8.1 Configure System Operational Parameters

| Use Case ID | UC-62 | Use Case Name | Configure System Operational Parameters |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Admin | Secondary Actors | None |

#### Description

As an Admin, I want to configure operational values and workshop-ring difficulty policy so that applicable workflows use validated settings.

#### Trigger

An Admin opens operational parameter configuration.

#### Preconditions

1. The Admin is authenticated and authorized to manage operational parameters.
2. The platform is available.
3. Parameter definitions and permitted value ranges are configured by the system.

#### Postconditions

1. Valid parameter values are saved with the acting Admin and change time recorded.
2. Subsequent applicable workflows use the new values.
3. Invalid or incomplete values do not overwrite the previous configuration.

#### Normal Flow

1. Admin opens operational parameter configuration.
2. System displays current values and descriptions.
3. Admin edits the workshop-ring difficulty policy.
4. System validates the complete candidate policy.
5. System previews before-and-after outcomes on non-personal examples and every active published model.
6. Admin reviews the preview and confirms publication.
7. System publishes an immutable policy version and displays the effective values.

#### Alternative Flows

3a. Admin edits a checkout operational value → System follows the separately specified checkout parameter workflow.

6a. Admin cancels → System discards unsaved edits and changes no parameter.

#### Exception Flows

4a. Value is invalid or outside the permitted range → System displays field-level validation errors and preserves the effective policy.

4b. Values are inconsistent or an option lacks a score → System identifies the issue and preserves the effective policy.

5a. An active published model becomes invalid, unscored, or too risky → System identifies the model and issues no publication proof.

7a. Preview proof is missing, expired, or mismatched → System rejects publication and requests a fresh preview.

7b. Publication fails → System reports failure and preserves the previously effective policy.

#### Business Rules

BR-62-01, BR-62-02, BR-62-03, BR-62-04, BR-62-05, BR-62-06, BR-62-07, BR-62-08

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-62-01 | Only an authorized Admin may change system-wide operational parameters. |
| BR-62-02 | Operational parameters must be validated as a consistent set before they become effective. |
| BR-62-03 | Every saved parameter change must retain the acting Admin, time, previous value, and new value for audit purposes. |
| BR-62-04 | Only an authorized Admin may publish a workshop-ring difficulty policy. Manager catalogue commands must not alter any difficulty score, coefficient or threshold. |
| BR-62-05 | A ring-difficulty publication is an atomic, versioned change after a before/after preview. Existing saved design versions retain their original evaluation; subsequent evaluations use the newly effective policy. |
| BR-62-06 | The ring-difficulty publication API rejects a missing, expired or mismatched preview proof; a completed identical retry replays its original result. |
| BR-62-07 | Each active published model's complete default configuration remains valid, fully scored, and below or at the too-risky cutoff after policy publication. |
| BR-62-08 | The proposed ring-difficulty screen excludes checkout parameters, Manager-owned loyalty policy, and retired V1 custom-order queue configuration. |
