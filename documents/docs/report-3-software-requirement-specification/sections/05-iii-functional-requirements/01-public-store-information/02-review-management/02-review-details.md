#### 1.2.2 Review Details

This screen allows Guests, Members, Staff and Managers to:

- Read one review with its rating, text, photos, target and creation time.
- Read the current Manager reply of the review, if any.
- As the signed-in author of the review, open Edit Own Review.
- Go back to the review list.

The page is public. The edit action is shown only when the viewer is signed in and the server confirms that the viewer owns the review.

| Field Name | Description |
| --- | --- |
| Title | "Chi tiết đánh giá". |
| Reviewed source | Above the review: for a product, its photo, name and size (the size only for the owner), with **Xem sản phẩm** opening the product; for a workshop, its name, shown only to the owner. Hidden when the source cannot be found. |
| Rating and status | Star rating (1–5) and the review status badge. |
| Reviewer | Masked display name or the anonymous label; never email, phone or account number. |
| Review text and photos | Optional text up to 2,000 characters and up to 3 photo thumbnails; a photo opens full size on click. |
| Phản hồi từ Ban Quản lý Mland | The one current Manager reply with its time, shown only when a reply exists. |
| Target and time | "Buổi trải nghiệm Workshop" or "Sản phẩm bán lẻ", the workshop package name when present, and the creation time. |
| Chỉnh sửa đánh giá của bạn | Opens Edit Own Review; shown only to the signed-in owner. |
| Quay lại | Returns the owner to the "Đã đánh giá" tab (`/reviews?tab=reviewed`); other viewers go to `/reviews`. |
| Error message | "Không tìm thấy đánh giá được yêu cầu." when the review does not exist or cannot be read, with a reload action. |
