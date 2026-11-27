#### 6.2.2 Design Review Details

![](assets/screens/live/56-design-review-details.png)

This screen allows Staff and Managers to:

- Inspect a design request: reference, version, request and review status, owner type, description, catalogue reference, reference images and the attached ring design.
- Record a decision of accept, reject or request clarification after a confirmation dialog.
- When accepting, enter the full custom quote in the same form: total amount, payment deadline in hours and an optional pricing basis.
- As a Manager, approve a quote prepared by Staff so that the Member can pay it in full from My Custom Orders.

Only the STAFF and MANAGER roles can open the screen. Reject and clarification need a written explanation of 1 to 2,000 characters; for accept it is optional and a default text is stored. A quote prepared by Staff waits for a Manager; a quote prepared by a Manager is ready to pay at once. An accepted or rejected request is final and the decision form is hidden.

On the screen, they can also:

- Open the attached design's drawing and technical specifications in a dialog.
- Enlarge a reference image.

**Field Description**

| Field Name | Description |
| --- | --- |
| Header | "Review Design Request #{id}", "Reference: {ref} \| Design version: #{n}", "Request status: {status}", "Review status: {status}" and the owner type, Member or Guest (owner type has no English label in the app). |
| Success and error notices | Result of the last decision, quote or approval, or the server error, such as a self-review refusal. |
| Final decision card | "Decision: Accept (ACCEPTED)" or "Decision: Reject (REJECTED)" with the reviewer's explanation. |
| Custom Crafting Quote | Shown for accepted requests. With a quote: quote code and version, "Approved by Manager" or "Awaiting Manager approval", total, payment deadline and pricing basis. Without a quote: the quote fields and **Send quote to Manager for approval** with its confirmation dialog. |
| **Approve quote (Manager Approve)** | Manager only, while the quote is not approved; approves it after the "Confirm quote approval" dialog. |
| Design drawing & specifications | Shown when a ring design or catalogue reference is attached; a button opens the drawing, material, main stone, size and engraving (no English label in the app). |
| Design idea description | The submitted idea and the catalogue reference. |
| Image gallery | "Reference Design Images ({count}/5)" with a quality mark; an image can be enlarged. |
| Decision | Options: Approve & send quote (ACCEPTED), Reject design (REJECTED), Clarification required (CLARIFICATION_REQUIRED). Default: ACCEPTED. The option texts have no English label in the app. |
| Quote fields | Shown for ACCEPTED: total (VND, digits only, 1 to 1,000,000,000, required), payment deadline (24 to 72 hours, default 72, required) and an optional pricing basis for the Manager. These labels have no English label in the app. |
| Explanation | "Reason / Guidance for member" for reject and clarification, required, at most 2,000 characters with a counter; an optional review note for accept. |
| Decision button | Approve & send quote, Reject design or Request clarification, by decision. Disabled while the explanation is empty for reject or clarification, or while the quote fields are missing or invalid for accept. Opens a confirmation dialog summarising the decision. These texts have no English label in the app. |
