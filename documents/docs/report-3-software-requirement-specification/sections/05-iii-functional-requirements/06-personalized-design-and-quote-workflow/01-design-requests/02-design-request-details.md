#### 6.1.2 Design Request Details

![](assets/screens/live/40-design-request-details.png)

This screen allows the owning Member to:

- Follow the review status of one design request.
- Read the reviewer's written explanation once the request is accepted or rejected.
- Review the submitted description, catalogue reference and reference images, and enlarge an image.
- Start a new design request or return to their request history.

The screen requires sign-in; a Guest sees a sign-in prompt. The server returns only the Member's own requests; another request shows an error. Requests not yet accepted or rejected show a waiting notice.

**Field Description**

| Field Name | Description |
| --- | --- |
| Success notice | Shown right after sending: the request was sent and awaits staff feedback (no English label in the app). |
| Title and reference | "Design Request Details" with "Reference: {ref}". |
| Review status badge | "Awaiting staff review", "Accepted" or "Rejected"; a request needing clarification still shows "Awaiting staff review". |
| Decision card | Shown after acceptance or rejection: "Your design has been accepted" or "Your design was rejected", with "Reviewer decision rationale & guidance". |
| Waiting notice | "Awaiting review by staff / manager" with a short explanation of the review queue. |
| Design idea description | The submitted idea and, when given, the "Product / Catalogue reference (optional)". |
| Image gallery | "Reference Design Images ({count}/5)" with each file name and a "Sharp" mark; selecting an image opens it enlarged with a close button. |
| **Create new design request** | Opens Design Request Workspace. |
| **My request history** | Opens the craft requests tab of My Ring Designs. |
| Load error | The server error message with a reload action. |
