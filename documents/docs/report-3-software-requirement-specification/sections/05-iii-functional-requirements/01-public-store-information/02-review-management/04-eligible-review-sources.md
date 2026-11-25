#### 1.2.4 Eligible Review Sources

This screen allows Members to:

- See the "Chưa đánh giá" tab with their own checked-in workshop attendances and picked-up or delivered retail order lines that have no review.
- See the "Đã đánh giá" tab with their own reviews, open one, and edit it when the server allows.
- Start a review: a workshop ticket opens Create Review; a retail line opens one screen that reviews every not yet reviewed product of that order.

The screen is the signed-in Member's view of `/reviews` and lists only that Member's own sources. Each source can be reviewed only once.

| Field Name | Description |
| --- | --- |
| Title | "Đánh giá từ khách hàng". |
| Tabs | "Chưa đánh giá" and "Đã đánh giá", each with its count. |
| Filters | Review target on both tabs; sort order and star rating on "Đã đánh giá". |
| Pending item | Source information, completion time and the review button. |
| Reviewed item | Source information, the review card, a notice when the review is not public, and the edit button when allowed. |
| Empty messages | Shown when a tab has nothing to list or no review matches the filters. |
| Error message | Load failure shown with a reload action. |
