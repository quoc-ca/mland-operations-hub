#### 8.2.2 Custom Order Operations

![Custom Order Operations mockup](assets/screens/59-custom-order-operations.png)

This screen allows authorized Staff to:

- View custom progress separately from verified deposit and final-payment facts.
- Update permitted shop-side manufacturing progress.
- Prepare and send a final-amount proposal to Manager.
- Record actual pickup or manual GHTK handoff only after eligible verified final payment.

| Field Name | Description |
| --- | --- |
| Order / type / branch / state | Read-only processing-context columns; assigned-branch and action restrictions apply. |
| Open Operations | Opens an authorized order; it does not grant cross-branch access. |
| Progress choices | Illustrative preparation, pickup/readiness and manual-carrier labels; valid transitions must be separate and state checked. |
| Carrier reference / note | Optional evidence input only when applicable; no tracking, fee quote or carrier API is introduced. |
| Save Progress | Records a permitted action after state/authority checks; there is no manual paid override. |
| Final-amount notice | Staff submits a proposal; Manager must approve the exact final amount before a payment request. |
