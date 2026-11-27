#### 11.1.4 Create Review

![](assets/screens/live/31-create-review.png)

This screen allows Members to:

- Write a review for one eligible workshop attendance chosen in Eligible Review Sources.
- Give a 1–5 star rating, optional text and up to 3 photos.
- Choose to post anonymously; the choice is fixed once the review is submitted.
- Publish the review, which becomes public at once and opens its Review Details.

Only the MEMBER role can open the screen; other signed-in roles are routed to the not-found page. The screen cannot be used without a selected source. Retail order lines are reviewed on **Rate products**, described in Eligible Review Sources. A source can have only one review.

**Field Description**

| Field Name | Description |
| --- | --- |
| No source selected | "Select an eligible source first." with **Back** and **To review**, both returning to Eligible Review Sources. |
| Title | "Share your experience", with the subtitle "Review your actual experience from a checked-in workshop or delivered order." |
| Source information | The chosen source's name with "Workshop session", or the product photo, name and size; it cannot be changed here. |
| Rating (1–5) | Required star rating, 5 by default. |
| Review text | Optional text, at most 2,000 characters, with a character counter. Text with SQL-like syntax (for example `--`, `;` or `/* */`) is refused: "Review content contains unsafe syntax or characters." |
| Attach Photos | Optional JPEG, PNG or WebP photos, at most 3 and 10MB in total ("Max 3 photos, total size up to 10MB"), with previews and a remove button per photo. |
| Post anonymously | When ticked, the reviewer's name is not shown publicly; otherwise the name is partly masked. The hint says the choice cannot change after submitting. |
| Publish review | Submits the review; on success shows "Review published publicly." and opens Review Details. |
| Error messages | For example "This source already has a review…" or "This source is not eligible for review…". |
