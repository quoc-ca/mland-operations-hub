### 4.3 Approve Custom Design Pricing

#### Primary Actors

Manager

#### Secondary Actors

AI API

#### Description

As a Manager, I want to use an AI-generated price suggestion as supporting information so that I can decide the published price of a new workshop package or retail product.

#### Preconditions

1. The platform is available.
2. The Manager is authenticated and authorized to decide published catalogue prices.
3. The workshop package or retail product contains the configured materials, components, cost inputs, and applicable pricing rules.
4. The approved AI API is configured and enabled.

#### Normal Flow

**Approve Custom Design Pricing**

1. The Manager opens the pricing approval function for a new workshop package or retail product.
2. The system displays the configured materials, components, cost inputs, and applicable pricing rules.
3. The Manager confirms that the inputs are complete and requests an AI price suggestion.
4. The system validates the inputs and sends the permitted pricing context to the AI API.
5. The AI API returns a suggested price and supporting explanation.
6. The system displays the suggestion as advisory information and identifies that the AI did not publish the price.
7. The Manager reviews the inputs and suggestion, then accepts, adjusts, or rejects the proposed price.
8. The system validates the Manager's final price against the configured pricing rules.
9. The Manager confirms the final decision.
10. The system saves the approved price, records the Manager and decision context, and publishes the price when the catalogue workflow permits publication.

#### Alternative Flows

**Step 2 — Required pricing input is missing or inconsistent**

The system identifies the missing or inconsistent data and asks the Manager to complete the configuration before requesting a suggestion.

**Step 4 — AI API is unavailable or times out**

The system displays an error and allows the Manager to retry or decide the price using the configured business inputs without treating the AI suggestion as required.

**Step 5 — AI suggestion is missing, invalid, or outside the configured range**

The system marks the suggestion as unusable and asks the Manager to review the inputs or enter a price manually.

**Step 7 — Manager rejects the suggestion**

The system does not publish the AI suggestion and allows the Manager to enter another price or cancel the pricing operation.

**Step 8 — Final price violates a configured pricing rule**

The system displays the validation error and does not save or publish the invalid price.

**Step 9 — Manager cancels**

The system discards the unsaved pricing decision and leaves the current catalogue price unchanged.

**Step 10 — Price save or publication fails**

The system rolls back the incomplete pricing operation, displays an error, and preserves the previous catalogue state.

#### Postconditions

- A valid final price is saved only after Manager confirmation.
- The AI suggestion is retained as supporting decision context and is not treated as the final price.
- The published price belongs to the workshop package or retail product catalogue workflow.
- This use case does not price, accept, reject, or review a custom-manufacturing order.
- The Manager, final price, source inputs, and decision time are retained as audit evidence.

#### Business Rules

GBR-06, BR-31-01, BR-31-02, BR-31-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-31-01 | AI price suggestions are advisory and must not publish or change a catalogue price without Manager confirmation. |
| BR-31-02 | Only configured workshop-package or retail-product inputs may be sent for this pricing suggestion. |
| BR-31-03 | AI does not price, accept, reject, or review a custom-manufacturing order. |
