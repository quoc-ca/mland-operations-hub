#### 11.1.3 Eligible Review Sources

![](assets/screens/live/44-eligible-review-sources.png)

This screen allows Members to:

- See the "To review" tab with their own checked-in workshop attendances and picked-up or delivered retail order lines that have no review.
- See the "Reviewed" tab with their own reviews, open one, and edit it when the server allows.
- Start a review: a workshop ticket opens Create Review; a retail line opens **Rate products** for every not yet reviewed product of that order.

The screen is what a signed-in Member sees from the Reviews menu, and it lists only that Member's own sources. Each source can be reviewed only once. Other signed-in roles see the Public Review List instead.

**Field Description**

| Field Name | Description |
| --- | --- |
| Title | "Customer reviews". |
| Tabs | Options: To review, Reviewed, each with its count when it is not zero. Default: To review. |
| Filters | Target type on both tabs (default All Experiences); Sort by (default Newest) and Rating (1–5) (default All) only on Reviewed. Options as in the Public Review List. |
| Pending item | Product photo, name and size, or the workshop name; "Completed {date-time}"; and **Review**. Most recently completed first. |
| Reviewed item | Source information, a notice when the review is not public, the review card with **Open**, and **Edit review** when allowed. |
| Empty messages | "Nothing is waiting for your review.", "You have not written any reviews yet." or "No reviews match the filters." |
| Error message | Load failure shown with a reload action. |

**Rate products** shows one block per product of the order: photo, name and size, "Product quality" stars, review text with a counter, and photos. One **Post anonymously** choice covers the whole order. **Submit** sends one review per product and returns to "Reviewed" with "Thank you for your review!". A product whose review fails stays on screen with its error; **Back** returns to the previous screen.
