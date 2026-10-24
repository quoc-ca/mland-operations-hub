#### 12.1.3 Technical Order Support

![](assets/screens/78-technical-order-support.png)

This screen allows Administrators to:

- Look up internal order/payment references for a permitted technical-support purpose.
- View only necessary branch, timestamp, state and sanitized technical-code diagnostics.
- Read the technical result without customer identity, contact/address, cart or product-option data.
- Provide support without impersonation, business mutation, payment confirmation or revenue/price approval.

| Field Name | Description |
| --- | --- |
| Object / scope / update / state | Generic administrative table; displayed fields must be limited to the selected account, configuration or support purpose. |
| Find / Select Configuration | Selects an authorized administrative target; do not expose secrets or customer data through search. |
| Open Configuration | Opens only the permitted target details; technical support remains minimized and read-only. |
| Confirm | Applies only the supported administrative action, with explicit authority and required confirmation. |
| Safety notice | Keep secrets masked; Admin has no revenue access, business-price approval or manual payment override. |
