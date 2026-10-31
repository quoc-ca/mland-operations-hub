### 5.2 Configure Workshop Ring

#### Primary Actors

- Guest
- Member

#### Secondary Actors

- None

#### Description

This use case allows the user to configure the selected ring model by choosing supported workshop options such as band material, gemstone, size, and engraving. The system validates the configuration against the approved product rules and keeps the configuration for the workshop booking flow.

#### Preconditions

1. The user has selected an active ring model.
2. The selected model has available configuration options.
3. The user is in an active workshop session.

#### Normal Flow

**Configure Workshop Ring**

1. The system displays the selected ring model and its supported configuration options.
2. The user selects the required options, such as material, gemstone, ring size, or engraving.
3. The system validates each selected option against the model's compatibility rules.
4. The system calculates and displays the current estimated workshop price.
5. The user confirms the configuration.
6. The system stores the valid configuration in the current workshop session.
7. The system allows the user to continue to workshop registration or request staff consultation.

#### Alternative Flows

**Step 3 — Incompatible option combination**

1. The selected options are not compatible with the ring model.
2. The system identifies the invalid combination and asks the user to select compatible options.

**Step 2 — Required option is missing**

1. The user attempts to confirm without selecting a required option.
2. The system highlights the missing option and prevents confirmation.

**Step 4 — Option becomes unavailable**

1. A selected option becomes unavailable during configuration.
2. The system refreshes the available options and asks the user to choose a replacement.

#### Postconditions

- A valid ring configuration is stored in the current workshop session.
- The system records the current estimated price for display and review.
- No final quotation, order, payment, or custom manufacturing request is created.

#### Business Rules

- BR-33-01
- BR-33-02
- BR-33-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-33-01 | Configuration options must be supported by the selected approved ring model. |
| BR-33-02 | The system must reject incompatible combinations and missing required options. |
| BR-33-03 | The displayed price is an estimate until the applicable staff or manager review is completed. |

