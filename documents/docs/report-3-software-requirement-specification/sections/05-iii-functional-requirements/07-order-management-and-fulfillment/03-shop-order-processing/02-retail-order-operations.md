#### 7.3.2 Retail Order Operations

![Retail Order Operations mockup](assets/screens/57-retail-order-operations.png)

This screen allows authorized Staff to:

- Review verified payment and the owner's selected fulfilment data before starting preparation.
- Record preparing, then the readiness state appropriate to Pickup or GHTK; preparation freezes Member fulfilment edits.
- Record actual owner pickup after confirming the signed-in owning Member and a ready order.
- Record actual manual carrier handoff only after carrier preparation, keeping those events separate.
- Use valid forward transitions without a manual paid override, courier tracking or automatic cancellation.

| Field Name | Description |
| --- | --- |
| Order / type / branch / state | Read-only processing-context columns; assigned-branch and action restrictions apply. |
| Open Operations | Opens an authorized order; it does not grant cross-branch access. |
| Progress choices | Illustrative preparation, pickup/readiness and manual-carrier labels; valid transitions must be separate and state checked. |
| Carrier reference / note | Optional evidence input only when applicable; no tracking, fee quote or carrier API is introduced. |
| Save Progress | Records a permitted action after state/authority checks; there is no manual paid override. |
| Final-amount notice | Staff submits a proposal; Manager must approve the exact final amount before a payment request. |
