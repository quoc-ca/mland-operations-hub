### 3.8 Configure Supported Materials by Branch

> **Retired (confirmed 2026-11-25):** use case 29 is not part of the platform; no specification or implementation exists for it. The text below is kept only as history and must not be implemented.

| Use Case ID | UC-29 | Use Case Name | Configure Supported Materials by Branch |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | None |

#### Description

As a Manager, I want to configure which jewelry materials, ring options, and workshop packages are supported at each branch so that only valid branch options are offered.

#### Trigger

A Manager selects a branch in the supported-materials configuration screen.

#### Preconditions

1. The Manager is authenticated and authorized.
2. The branch and material or ring catalogue entries exist.
3. The selected material is active and eligible for workshop use.

#### Postconditions

1. The branch-material and supported-option configuration is saved.
2. New booking and design flows use the branch-specific supported options.
3. Existing bookings retain their recorded selections.

#### Normal Flow

1. Actor selects a branch.
2. System displays the currently supported materials, ring options, and packages.
3. Actor adds, edits, or removes a supported option.
4. System validates the catalogue entry, compatibility, active state, and branch scope.
5. Actor confirms the configuration.
6. System saves the branch configuration for eligible booking and design flows.

#### Alternative Flows

3a. Removing an option used by existing bookings → System preserves historical booking data and deactivates it only for new selections.

#### Exception Flows

3a. Catalogue entry is inactive or missing → System rejects the option and asks the Manager to select an active entry.

4a. Material or design combination is incompatible → System identifies the compatibility problem and does not save it.

6a. Save fails → System displays an error and preserves the prior branch configuration.

#### Business Rules

BR-25-01, BR-25-02

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-25-01 | Only active and compatible materials/ring options configured for the selected branch may be offered for new workshop selections. |
| BR-25-02 | Changing branch support affects new selections and must not rewrite materials or options already recorded on existing bookings. |
