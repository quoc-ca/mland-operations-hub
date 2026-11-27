#### 11.1.5 Edit Own Review

![](assets/screens/live/45-edit-own-review.png)

This screen allows Members to:

- Change the star rating, text and photos of a review they wrote.
- Keep the review public after the edit; each edit records a revision and an audit entry.
- Return to the review's details after saving.

Only the MEMBER role can open the screen; other signed-in roles are routed to the not-found page. A Member who does not own the review sees a forbidden message instead of the form. The source, target and the anonymous choice cannot be changed.

**Field Description**

| Field Name | Description |
| --- | --- |
| Title | "Edit review", with the subtitle "Update your rating and feedback for this verified experience." |
| Edit notice | "Note: PUBLIC status is preserved after editing and an audit revision is recorded." |
| Reviewed source | The product photo, name and size, or the workshop name, when it can be found. |
| Rating (1–5) | Star rating, filled with the current value. |
| Review text | Optional text, at most 2,000 characters, filled with the current text, with a character counter. |
| Attach Photos | Current photos with remove buttons; new JPEG, PNG or WebP photos can be added up to 3 photos and 10MB in total. |
| Save changes | Saves the edit; on success shows "Review updated." and opens Review Details. |
| Back | At the bottom of the form; returns to the previous screen, or to Review Details when the form was opened directly. |
| Forbidden message | "You are not allowed to edit this review. Only the owner can edit their own review." with **Back** to Eligible Review Sources and **Open** to the review. |
| Conflict message | "The data changed. Load the latest version before continuing." when the review changed since it was loaded. |
