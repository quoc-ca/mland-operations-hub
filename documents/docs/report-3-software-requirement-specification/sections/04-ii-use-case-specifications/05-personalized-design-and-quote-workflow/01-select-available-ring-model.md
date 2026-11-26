### 5.1 Select Available Ring Model

| Use Case ID | UC-36 | Use Case Name | Select Available Ring Model |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member | Secondary Actors | — |

#### Description

As a Guest or Member, I want to browse published workshop ring models so that a Member can select one as a design starting point.

#### Trigger

A Guest or Member opens the general ring catalogue or a workshop package's ring model list.

#### Preconditions

1. The platform is available.
2. Published ring models and their catalogue options are available.
3. Selecting a model requires a signed-in Member with an active account.

#### Postconditions

1. Member continues to Configure Workshop Ring with the selected starting point; nothing is saved yet.
2. No booking, ticket, order, payment, or custom design request is created.

#### Normal Flow

1. Guest or Member opens the general ring catalogue.
2. System retrieves the published models and options.
3. System displays each model's image, material, name, description, and advisory difficulty.
4. Member selects a published ring model.
5. System validates that the selected model and its complete defaults remain published, scored, and eligible.
6. System opens Configure Workshop Ring with the selected starting point.

#### Alternative Flows

1a. Actor opens a workshop package's model list → System shows only models compatible with that package.

4a. Member chooses a blank ring → System skips model validation and opens Configure Workshop Ring with a blank starting point.

#### Exception Flows

2a. No active and approved ring model exists → System displays an availability message and creates no workshop configuration.

2b. Ring model catalogue retrieval fails → System displays an error message and allows retrying.

4a. Actor is a Guest → System prompts the Guest to sign in and sends no protected command.

5a. Selected model became inactive or unavailable → System rejects the selection and asks the Member to choose another model.

#### Business Rules

BR-32-01, BR-32-02, BR-32-03, BR-32-04

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-32-01 | Only published and active ring models may be selected. |
| BR-32-02 | Selecting a ring model does not reserve stock, create a booking or order, or authorize payment. |
| BR-32-03 | A Guest may browse public models and options; selecting and configuring require a signed-in Member. |
| BR-32-04 | General discovery includes every active published model; a workshop package page filters models by compatibility. |
