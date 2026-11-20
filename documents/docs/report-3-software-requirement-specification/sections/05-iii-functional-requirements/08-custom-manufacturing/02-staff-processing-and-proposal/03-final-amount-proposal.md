#### 8.2.3 Custom Quote Preparation

![](assets/screens/60-final-amount-proposal.png)

This screen allows authorized Staff to:

- Prepare or review the complete custom-order quote before payment. No remaining
  balance or post-production surcharge payment is created in this flow.
- Prepare the complete custom-order quote and submit it to Manager.
- View pending or returned quote status, revise and resubmit when needed.
- View the Manager's decision; the one-time full-payment request must use the approved quote version.

| Field Name | Description |
| --- | --- |
| Order / type / branch / state | Read-only processing-context columns; assigned-branch and action restrictions apply. |
| Open Operations | Opens an authorized order; it does not grant cross-branch access. |
| Progress choices | Illustrative preparation, pickup/readiness and manual-carrier labels; valid transitions must be separate and state checked. |
| Carrier reference / note | Optional evidence input only when applicable; no tracking, fee quote or carrier API is introduced. |
| Save Progress | Records a permitted action after state/authority checks; there is no manual paid override. |
| Quote notice | Staff submits a complete quote; Manager must approve the exact quote before a full-payment request. |
