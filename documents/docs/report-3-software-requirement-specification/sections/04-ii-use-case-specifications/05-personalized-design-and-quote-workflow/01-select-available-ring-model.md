### 5.1 Select Available Ring Model

#### Primary Actors

- Guest
- Member

#### Secondary Actors

- None

#### Description

This use case allows a guest or member to browse approved ring models and select one as the starting point for a workshop design. The system displays the model information, supported workshop options, and current availability before the user continues to the configuration step.

#### Preconditions

1. The user can access the workshop browsing function.
2. At least one approved ring model is available in the catalogue.
3. The ring model catalogue is accessible to the system.

#### Normal Flow

**Select Available Ring Model**

1. The user opens the available ring model catalogue.
2. The system retrieves approved and active ring models.
3. The system displays the model name, reference image, description, base price, and availability status.
4. The user selects a ring model.
5. The system validates that the selected model is active and available for workshop configuration.
6. The system stores the selected model in the user's current workshop session.
7. The system directs the user to Configure Workshop Ring.

#### Alternative Flows

**Step 2 — No available model**

1. The system cannot find an active and approved ring model.
2. The system displays an availability message and does not create a workshop configuration.

**Step 5 — Model is unavailable**

1. The selected model has become inactive or unavailable.
2. The system rejects the selection and asks the user to choose another model.

**Step 2 — Catalogue retrieval failure**

1. The system cannot retrieve the ring model catalogue.
2. The system displays an error message and allows the user to retry.

#### Postconditions

- The selected ring model is stored in the current workshop session.
- No order, payment, or custom manufacturing request is created.
- The user can continue to Configure Workshop Ring.

#### Business Rules

- BR-32-01
- BR-32-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-32-01 | Only approved and active ring models may be selected for workshop configuration. |
| BR-32-02 | Selecting a ring model does not reserve stock, create an order, or authorize payment. |

