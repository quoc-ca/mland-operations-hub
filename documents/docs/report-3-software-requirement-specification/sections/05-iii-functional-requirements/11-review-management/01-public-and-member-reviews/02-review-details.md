#### 11.1.2 Review Details

![](assets/screens/live/29-review-details.png)

This screen allows Guests, Members, Staff and Managers to:

- Read one review with its rating, text, photos, target and creation time.
- Read the current Manager reply of the review, if any.
- As the signed-in author of the review, open Edit Own Review.
- Go back to the review list.

The page is public. The edit action is shown only when the viewer is signed in and the server confirms that the viewer owns the review.

**Field Description**

| Field Name | Description |
| --- | --- |
| Title | "Review detail". |
| Reviewed source | Above the review: for a product, its photo, name and size (the size is known only to the owner), with **View product** opening the product; for a workshop, its name, shown only to the owner. Hidden when the source cannot be found. |
| Hidden notice | Shown to the owner when the review is not public: "This review is currently hidden from the public page." |
| Rating and status | Star rating (1–5) and the status badge (PUBLIC, HIDDEN or REMOVED). |
| Reviewer | Masked display name or "Mland Customer"; never email, phone or account number. |
| Review text and photos | Optional text up to 2,000 characters and up to 3 photo thumbnails; a photo opens full size on click. |
| Official Response from Mland Management | The one current Manager reply with its time, shown only when a reply exists. |
| Target and time | "Workshop session" or "Retail product", "Package: {name}" when present, and the creation time. |
| Edit your review | Opens Edit Own Review; shown only to the signed-in owner. |
| Back | Returns the owner to the "Reviewed" tab of Eligible Review Sources; other viewers go to the Public Review List. |
| Error message | "The requested review was not found." when the review does not exist or cannot be read, with a reload action. |
