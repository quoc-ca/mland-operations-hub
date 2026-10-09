#### 8.2.3 Final Amount Proposal

![Final Amount Proposal mockup](assets/screens/60-final-amount-proposal.png)

This screen allows authorized Staff to:

- Review the remaining 50% of the wax-package price and enter actual surcharges.
- Prepare the resulting final-balance proposal and submit it to Manager.
- View pending or returned proposal status, revise and resubmit when needed.
- View the Manager's decision; the balance-payment request must use the approved proposal version.

| Field Name | Description |
| --- | --- |
| Order / type / branch / state | Read-only processing-context columns; assigned-branch and action restrictions apply. |
| Open Operations | Opens an authorized order; it does not grant cross-branch access. |
| Progress choices | Illustrative preparation, pickup/readiness and manual-carrier labels; valid transitions must be separate and state checked. |
| Carrier reference / note | Optional evidence input only when applicable; no tracking, fee quote or carrier API is introduced. |
| Save Progress | Records a permitted action after state/authority checks; there is no manual paid override. |
| Final-amount notice | Staff submits a proposal; Manager must approve the exact final amount before a payment request. |
