### 3.11 Configure Supported Materials by Branch

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

Allows the Manager to configure which jewelry materials, ring options, and workshop packages are supported at each branch.

#### Preconditions

1. The Manager is authenticated and authorized.
2. The branch and material/ring catalogue entries exist.
3. The selected material is active and eligible for workshop use.

#### Normal Flow

**Configure Supported Materials by Branch**
1. The Manager selects a branch.
2. The system displays the currently supported materials, ring options, and packages.
3. The Manager adds, edits, or removes a supported option.
4. The system validates the catalogue entry, compatibility, active state, and branch scope.
5. The Manager confirms the configuration.
6. The system saves the branch configuration and makes it available to eligible booking/design flows.

#### Alternative Flows



**Step 3 — Catalogue entry is inactive or missing**
The system rejects the option and asks the Manager to select an active configured entry.



**Step 4 — Material/design combination is incompatible**
The system identifies the compatibility problem and does not save the combination.



**Step 3 — Removing an option used by existing bookings**
The system prevents removal from historical booking data and deactivates it only for new selections.



**Step 6 — Save fails**
The system displays an error and preserves the prior branch configuration.

#### Postconditions

• The branch-material and supported-option configuration is saved.• Booking and design selection flows use the branch-specific supported options.• Existing bookings retain their recorded selections.

#### Business Rules

BR-25-01, BR-25-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-25-01 | Only active and compatible materials/ring options configured for the selected branch may be offered for new workshop selections. |
| BR-25-02 | Changing branch support affects new selections and must not rewrite materials or options already recorded on existing bookings. |
