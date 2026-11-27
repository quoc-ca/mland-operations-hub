#### 10.3.3 Promotion Details

![](assets/screens/live/69-promotion-details.png)

This screen allows Managers to:

- View a programme's kind, voucher code, scope, status, discount and conditions.
- Edit the programme's name, validity window, discount, minimum value, usage limits and points setting.
- Pause or resume the programme after confirmation.
- Follow the programme's usage and the total amount discounted.

**Field Description**

| Field Name | Description |
| --- | --- |
| Title / Back | Programme name; **Back** returns to Promotion List. |
| Header | Kind (Voucher or Automatic discount), "Voucher code: {code}", scope badge and status badge (Enabled or Paused). |
| Discount | "{value} off", or "{value} off (up to {max})" for a percentage. |
| Conditions | Valid period, Minimum amount, Combine with points, branches or other targets, Total uses and Uses per Member. A warning lists targeted products that can no longer be bought or targeted branches that are no longer active. |
| Version line | "Version {version} · revision {revision} · created {createdAt}". |
| Edit | Opens the **Edit programme** form below with **Save** and **Cancel**. Kind, scope, voucher code and targets cannot change; Uses per Member cannot change for a voucher that must be saved. A limit cannot be lower than the uses consumed, held or saved. |
| Pause / Resume | Opens a confirmation. Pausing says customers cannot use it on new orders while history and usage are kept; resuming makes it usable again within its period. |
| Usage | Voucher: Used / total, Held, Uses per Member, Distribution, "Saved, not used yet" (voucher that must be saved) and Total discount given. Automatic discount: Accepted orders and Total discount given. |
