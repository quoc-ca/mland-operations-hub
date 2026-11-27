### 2.2 Performance

::: {.docx-table width="100%" columns="24%,40%,36%" repeat-header="false"}

| Requirement | Target | Condition |
| --- | --- | --- |
| Workshop schedule response | 95% of responses within 2 seconds, with no errors | Up to 100 users active simultaneously |
| Page load time | Any page loads within 3 seconds | Normal network connection |
| Form submission response | Ring design save and evaluation acknowledged within 3 seconds | Up to 100 users active simultaneously |
| Image quality check | Quality result for up to 5 images within 3 seconds | One design request |
| Maximum simultaneous users | System remains fully functional for up to 100 users | Single server deployment with one MySQL database |
| Workflow step wait | No user-visible step waits longer than 30 seconds | Any cross-feature workflow |
| External service timeout | VNPay 10 seconds, GHTK 5 seconds, Gemini 15 seconds; a timeout never counts as success | Each external call |
| Workshop booking hold | Seats, voucher and points are held 12 minutes and released within 5 minutes after expiry | From booking confirmation |
| Retail order hold | Items, voucher and points are held 10 minutes for VNPay and 72 hours for counter cash | From checkout |
| VNPay payment link | Expires 10 minutes after creation | Every VNPay payment |

:::
