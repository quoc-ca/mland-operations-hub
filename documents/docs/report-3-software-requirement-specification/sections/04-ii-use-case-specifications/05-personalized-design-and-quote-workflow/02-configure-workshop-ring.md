### 5.2 Configure Workshop Ring

| Use Case ID | UC-37 | Use Case Name | Configure Workshop Ring |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member | Secondary Actors | — |

#### Description

As a Member, I want to configure, save, and evaluate a workshop ring design so that I receive advisory price, difficulty, and workshop recommendations.

#### Trigger

A Member opens Configure Workshop Ring from a model, blank ring, or saved design.

#### Preconditions

1. The Member is signed in and the account is active.
2. The starting model or blank design has available server-supplied catalogue options.
3. When editing, the design belongs to the Member and is not archived.

#### Postconditions

1. A saved design version exists and belongs to the Member; earlier versions are retained.
2. No booking, ticket, payment, quotation, custom order, or custom design request is created automatically.

#### Normal Flow

1. Member opens Configure Workshop Ring.
2. System displays the server-supplied options for the starting point.
3. Member selects material, shape, ring size, dimensions, gemstones, components, attachments, and engraving.
4. System shows a local preview marked as a preview.
5. Member selects save and evaluate.
6. System validates ownership, version, options, and limits.
7. System stores a new immutable design version with advisory estimated price and difficulty.
8. System displays the saved version, summary, warnings, and workshop recommendation.

#### Alternative Flows

6a. Member selected a workshop package → System also validates package compatibility before saving.

8a. Member archives or restores the design → System hides or restores the design while retaining its version history.

#### Exception Flows

1a. Actor is a Guest → System shows public options only; protected actions prompt sign-in and create nothing.

4a. Member selects an invalid choice in preview → System flags invalid choices in the local preview.

6b. Option is invalid, inactive, unscored, incompatible, or exceeds limits → System rejects the save, identifies the problem, and keeps input.

6c. Design changed elsewhere → System returns a version conflict and asks the Member to reload.

7a. Rule service is unavailable → System saves no new version and asks the Member to retry.

#### Business Rules

BR-33-01, BR-33-02, BR-33-03, BR-33-04

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-33-01 | Only a signed-in Member may configure, save, evaluate, archive or restore a design, and only an own design. Options come from the server. |
| BR-33-02 | The system rejects invalid or incompatible options. A design has at most 10 gemstones, 2 text engravings, 2 symbol engravings and 2 laser images. |
| BR-33-03 | Estimated price, difficulty and the workshop recommendation are advisory; they change no package price, payment or quotation and hide no workshop. |
| BR-33-04 | A general-catalogue design has no package until the Member explicitly selects one; recommendations never select a package. |
