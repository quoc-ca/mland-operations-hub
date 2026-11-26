### 8.1 Configure System Operational Parameters

| Use Case ID | UC-62 | Use Case Name | Configure System Operational Parameters |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Admin | Secondary Actors | None |

#### Description

As an Admin, I want to publish a validated workshop-ring difficulty policy so that later design evaluations use consistent settings.

#### Trigger

An Admin opens operational parameter configuration.

#### Preconditions

1. The Admin is authenticated and authorized to manage operational parameters.
2. The platform is available.
3. Parameter definitions and permitted value ranges are configured by the system.

#### Postconditions

1. A complete policy version is published with the acting Admin and publication time recorded.
2. Later ring-design evaluations use the new version.
3. Rejected candidates leave the previous version effective.

#### Normal Flow

1. Admin opens the ring-difficulty policy screen.
2. System displays the effective version and publication history.
3. Admin edits a complete local candidate.
4. Admin requests a preview.
5. System displays before-and-after examples and every active published model's eligibility.
6. Admin confirms publication.
7. System displays the new effective version and publication record.

#### Alternative Flows

3a. Admin loads historical values → System places them in a new candidate requiring preview.

#### Exception Flows

3b. Admin cancels or resets edits → System restores the effective values without publishing.

4a. Value is invalid or outside the permitted range → System displays field-level validation errors and preserves the effective policy.

4b. Values are inconsistent or an option lacks a score → System identifies the issue and preserves the effective policy.

5a. An active published model becomes invalid, unscored, or too risky → System identifies the model and issues no publication proof.

6a. Preview proof is missing, expired, or mismatched → System rejects publication and requests a fresh preview.

6b. Policy or catalogue version changed → System preserves edits and requires reload and a fresh preview.

6c. Publication fails → System reports failure and preserves the previously effective policy.

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
| BR-62-01 | Only an authorized active Admin may publish the ring-difficulty policy. |
| BR-62-02 | The complete policy must pass validation before it becomes effective. |
| BR-62-03 | Every publication must retain the acting Admin, time, previous values and new values for audit purposes. |
| BR-62-04 | Only an authorized Admin may publish a workshop-ring difficulty policy. Manager catalogue commands must not alter any difficulty score, coefficient or threshold. |
| BR-62-05 | A ring-difficulty publication is an atomic, versioned change after a before/after preview. Existing saved design versions retain their original evaluation; subsequent evaluations use the newly effective policy. |
| BR-62-06 | The ring-difficulty publication API rejects a missing, expired or mismatched preview proof; a completed identical retry replays its original result. |
| BR-62-07 | Each active published model's complete default configuration remains valid, fully scored, and below or at the too-risky cutoff after policy publication. |
| BR-62-08 | The proposed ring-difficulty screen excludes checkout parameters, Manager-owned loyalty policy, and retired V1 custom-order queue configuration. |
