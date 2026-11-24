#### 1.2.6 Edit Own Review

This screen allows Members to:

- Change the star rating, text and photos of a review they wrote.
- Keep the review public after the edit; each edit records a revision and an audit entry.
- Return to the review's details after saving.

The screen requires sign-in and opens only for the owner of the review; any other user sees a forbidden message instead of the form. The source, target and the anonymous choice cannot be changed. The spec also lets a Guest edit their own review, but this screen requires sign-in; how a Guest edits is TBD.

| Field Name | Description |
| --- | --- |
| Title | "Chỉnh sửa đánh giá". |
| Edit notice | States that the review stays PUBLIC after editing and that the audit history is kept. |
| Số sao (1–5) | Star rating, filled with the current value. |
| Nội dung đánh giá | Optional text, at most 2,000 characters, filled with the current text, with a character counter. |
| Gắn ảnh trải nghiệm | Current photos with remove buttons; new JPEG, PNG or WebP photos can be added up to 3 photos and 10MB in total. |
| Lưu thay đổi | Saves the edit; on success shows "Đánh giá đã được cập nhật." and opens Review Details. |
| Forbidden message | "Bạn không có quyền chỉnh sửa đánh giá này..." with **Quay lại** to the Public Review List and **Xem chi tiết** to the review. |
| Conflict message | Shown when the review changed since it was loaded; the user must reload the page before continuing. |
