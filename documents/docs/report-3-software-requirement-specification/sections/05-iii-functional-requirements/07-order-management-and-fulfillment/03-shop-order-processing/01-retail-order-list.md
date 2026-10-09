#### 7.3.1 Retail Order List

![Retail Order List mockup](assets/screens/56-retail-order-list.png)

This screen allows authorized Staff to:

- Browse retail orders within Staff's assigned branches.
- View current order references and processing states.
- Open authorized Retail Order Operations.

| Field Name | Description |
| --- | --- |
| Order / type / branch / state | Read-only processing-context columns; assigned-branch and action restrictions apply. |
| Open Operations | Opens an authorized order; it does not grant cross-branch access. |
| Progress choices | Illustrative preparation, pickup/readiness and manual-carrier labels; valid transitions must be separate and state checked. |
| Carrier reference / note | Optional evidence input only when applicable; no tracking, fee quote or carrier API is introduced. |
| Save Progress | Records a permitted action after state/authority checks; there is no manual paid override. |
| Final-amount notice | Staff submits a proposal; Manager must approve the exact final amount before a payment request. |
