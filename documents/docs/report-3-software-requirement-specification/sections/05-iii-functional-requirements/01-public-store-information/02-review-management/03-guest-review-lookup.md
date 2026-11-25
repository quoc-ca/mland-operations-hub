#### 1.2.3 Guest Review Lookup (Retired)

> **Retired (Feature-011 public-read / Member-write rebaseline, 2026-11-24):** the Guest lookup by email or phone is removed. Its route redirects to `/reviews` and the lookup API refuses every request. The text below is kept only as history and must not be implemented.

This screen allows Guests to:

- Find the workshop attendances and retail purchases they can review by entering the email or phone number used for the transaction.
- See only sources that match the contact: checked-in workshop attendances and order lines of orders that were picked up or delivered.
- Open Create Review for a source that has no review yet, or open the existing review of a source already reviewed.

A signed-in Member who opens this screen is sent to Eligible Review Sources instead. The lookup does not reveal whether other, unrelated transactions exist; no OTP is required in this version.

| Field Name | Description |
| --- | --- |
| Loại liên hệ | EMAIL (default) or PHONE. |
| Email hoặc số điện thoại | Required, at most 254 characters; an email must have a valid format ("Địa chỉ email không hợp lệ.") and a phone number must have 8–20 digits or phone symbols ("Số điện thoại không hợp lệ."). |
| Tìm nguồn | Runs the lookup; the form is disabled while it runs. |
| Source card | Source label (workshop or retail product), "Mã tham chiếu" and a badge: "Nguồn đã xác minh" for a reviewable source or "Đã viết đánh giá" for a reviewed one. |
| Đánh giá / Xem chi tiết | On a reviewable source, opens Create Review carrying a lookup proof valid for 30 minutes; on a reviewed source, opens its Review Details. |
| Empty message | "Không có nguồn đủ điều kiện." when no eligible source matches. |
| Error message | Server error shown with a retry action. |
