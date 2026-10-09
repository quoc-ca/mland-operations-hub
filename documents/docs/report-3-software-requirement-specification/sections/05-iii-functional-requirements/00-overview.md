# III. Functional Requirements

This section specifies the 78 screen-flow entries, grouped by feature and subfeature. Each screen includes its supplied layout, screen-specific actions and a component/field table, in that order. Shared screens are documented once; role-specific authorization still applies on every access and submission.

**Layout evidence.** Images are unchanged PNG exports from the stakeholder-supplied archive *mland-operation-hub (2).zip*, associated with the [Figma design](https://www.figma.com/design/m5Cyk5rit6lKaX3TPprSow/mland-operation-hub?node-id=0-1). Illustrative names, amounts, dates, counts, branches and statuses are not production data, default values or approved policies. The action lists describe required business behavior; the component tables describe the supplied layouts. Reused mockups may not yet show every required control.

**Shared field and interaction rules**

| Field Name | Description |
| --- | --- |
| Navigation and role badge | Header/sidebar labels are illustrative. Render only supported destinations within current authority; a badge or dashboard never grants a permission. |
| Record identity and scope | Load permitted current records. Members use their own data; Staff retail processing uses assigned branches; Manager maintenance requires applicable delegation. |
| Input initial data | New inputs start empty or with approved configured values; edit forms load permitted stored values. Mockup placeholder/sample text is not a persisted default. |
| Validation and limits | Validate type, requiredness, current options and business state before submission. Unspecified lengths, ranges, media constraints, date formats, paging and accessibility controls remain TBD; a mockup does not decide them. |
| Errors and concurrency | Show safe field/action feedback. Reject stale conflicts and retain prior accepted data on failure; pending, unavailable and empty states must not appear successful. |
| Payment and holds | Browser return never confirms payment. Apply only matching valid signed VNPay evidence or eligible QueryDR recovery. Preserve original deadlines and once-only effects; no manual paid override. |
| Attribution and audit | Retain attributable action/outcome evidence under the approved five-year audit policy, excluding secrets and unnecessary personal data. Payment, approval and actual handoff remain separate facts. |

**Confirmed workflow boundaries**

- Retail uses configured stable variants. Cart prices are indicative; accepted order terms are frozen. Whole-cart checkout creates no partial order. The original retail payment link lasts 10 minutes and the hold 15 minutes.
- Signature workshop supports freestyle or supported album/options/image customization. Silver Clay permits direct booking. Wax scheduling means attendance at the branch, followed by shop completion and shipping; session count and booking–manufacturing linkage remain TBD.
- Staff proposes the final custom amount. Manager approves the exact proposal version or returns it for revision before any balance-payment request. Product/package price review is a separate workflow, and retail price application/publication remain separate actions.
- Executive revenue is Manager-only. Admin provides supported technical/account configuration and minimized support, without revenue, customer impersonation, business-price approval or paid override.
- Pickup and actual manual GHTK handoff end the shop-side fulfilment flow. No GHTK API, fee quote, courier tracking, cancellation or refund workflow is added.
- Image consent precedes eligible storage/analysis. Apply purpose-specific media retention; Google Places content is read-only with attribution and is not persisted.

Background jobs and external interfaces are specified after the screen sections, using the existing inventories. Provider-specific contracts or operational values without approved evidence remain TBD.

**Screen coverage:** [Screen List](../03-i-overall-requirements/05-system-fuctionalities/01-screen-inventory/02-screen-list.md).
