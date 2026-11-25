#### 1.2.8 Review Moderation Details

This screen allows Staff and Managers to:

- Read one review with its rating, text, status, target and creation time.
- As Staff, hide a review that is not hidden; Staff cannot restore, remove or reply.
- As a Manager, hide a review, restore a hidden review to public, or remove a review.
- As a Manager, write the one current reply of the review or edit it.
- Give a reason with each visibility change; the change is recorded with the actor, role, reason, time and previous and new status.

Removal keeps the review and its history but takes it out of public view for good; a removed review cannot be restored. Only Staff and Managers can open this screen.

| Field Name | Description |
| --- | --- |
| Danh sách kiểm duyệt | Returns to the Review Moderation Queue. |
| Review summary | Masked name and status, star rating, status badge, review text, target type and reference, and creation time. |
| Lý do xử lý | Reason for the visibility change, at most 500 characters, with a counter. The server requires a reason. |
| Ẩn đánh giá | Sets the review to HIDDEN; shown when the review is not hidden. A Staff member viewing a hidden review sees that only a Manager can restore or remove it. |
| Khôi phục công khai | Manager only; sets a hidden review back to PUBLIC. |
| Gỡ đánh giá | Manager only; sets the review to REMOVED; not shown for a removed review. |
| Phản hồi của Manager | Shows the current reply with its version and time, if any. |
| Phản hồi / Lưu phản hồi | Manager only; required reply text up to 2,000 characters that creates the reply or replaces the current one. Staff see that only a Manager can send an official reply. |
| Messages | "Đã cập nhật trạng thái đánh giá." or "Đã lưu phản hồi." on success; a conflict or not-allowed message when the review changed or the action is not permitted. |
