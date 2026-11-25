### 5.1 Select Available Ring Model

#### Primary Actors

- Guest
- Member

#### Secondary Actors

- None

#### Description

This use case allows a Guest or Member to browse the published ring models offered for a workshop package, and a Member to select one as the starting point of a ring design. A Member may also start from a blank ring. The design is not attached to a workshop booking or ticket.

#### Preconditions

1. The platform is available.
2. The catalogue options of the workshop package are published.
3. To select a model, the actor is a signed-in Member with an active account.

#### Normal Flow

**Select Available Ring Model**

1. The Guest or Member opens the ring model list of a workshop package.
2. The system retrieves the published models and options.
3. The system displays each model's image, material, name, description and advisory difficulty when available.
4. The Member selects a model or chooses a blank ring.
5. The system validates that the selected model is still published and compatible with the package.
6. The system opens Configure Workshop Ring with the selected starting point.

#### Alternative Flows

**Step 2 — No available model**

1. The system cannot find an active and approved ring model.
2. The system displays an availability message and does not create a workshop configuration.

**Step 4 — Actor is a Guest**

1. The system asks the Guest to sign in and sends no protected command.
2. No design is created.

**Step 5 — Model is unavailable**

1. The selected model has become inactive or unavailable.
2. The system rejects the selection and asks the user to choose another model.

**Step 2 — Catalogue retrieval failure**

1. The system cannot retrieve the ring model catalogue.
2. The system displays an error message and allows the user to retry.

#### Postconditions

- The Member continues to Configure Workshop Ring with the selected starting point; nothing is saved yet.
- No booking, ticket, order, payment or custom design request is created.

#### Business Rules

- BR-32-01
- BR-32-02
- BR-32-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-32-01 | Only published and active ring models may be selected. |
| BR-32-02 | Selecting a ring model does not reserve stock, create a booking or order, or authorize payment. |
| BR-32-03 | A Guest may browse public models and options; selecting and configuring require a signed-in Member. |
