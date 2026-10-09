#### 2.3.4 Role / Permission Configuration

![Role / Permission Configuration mockup](assets/screens/75-role-permission-configuration.png)

This screen allows Administrators to:

- Inspect supported role/permission definitions and dependencies.
- Select permitted access-policy changes and confirm their save.
- Receive rejection of invalid dependencies or a change removing all authorized administrative access.
- Maintain the approved boundary without granting Admin revenue or business-approval rights.

| Field Name | Description |
| --- | --- |
| Object / scope / update / state | Generic administrative table; displayed fields must be limited to the selected account, configuration or support purpose. |
| Find / Select Configuration | Selects an authorized administrative target; do not expose secrets or customer data through search. |
| Open Configuration | Opens only the permitted target details; technical support remains minimized and read-only. |
| Confirm | Applies only the supported administrative action, with explicit authority and required confirmation. |
| Safety notice | Keep secrets masked; Admin has no revenue access, business-price approval or manual payment override. |
