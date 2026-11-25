#### 6.2.1 Design Request Workspace

This screen allows authenticated Members to:

- Describe the ring they want made, upload up to five reference images, and optionally attach a saved workshop ring design.
- See per-image quality feedback and replace images that are blurry or invalid before sending.
- Give consent to image processing whenever at least one image is attached.
- Submit the request for human review by Staff or a Manager.

The screen requires sign-in; a Guest sees the entry point and only a sign-in prompt, and cannot submit or receive private tracking data. The screen requires a description or at least one image; an attached ring design alone is not enough on this screen, although the server would accept it. The Member may choose one of their own saved Feature-012 designs and is taken to Design Request Details after sending. The server rechecks ownership of the attached design and stores an immutable snapshot of its selected state.

| Field Name | Description |
| --- | --- |
| Title and subtitle | "Biến ý tưởng của bạn thành hiện thực": the shop assesses the described ring and provides a suitable price. |
| **Xem yêu cầu của tôi** | Member only; opens the "Yêu cầu chế tác" tab of My Ring Designs. |
| Attached ring design | A list of the Member's saved designs ("-- Không đính kèm mẫu thiết kế --" by default) with a preview card of the chosen design and **✕ Gỡ đính kèm**, or a link to design one. |
| "Mô tả ý tưởng thiết kế" | Optional text of at most 4,000 characters with a character counter. |
| Reference images | JPEG, PNG or WEBP, at most 5 MB and 4096 × 4096 px each, at most 5 images; each shows "Đang kiểm tra độ sắc nét của ảnh...", "Đạt chuẩn" or "Lỗi ảnh" and can be removed. |
| Image consent | Shown when images are attached; "Chấp thuận Điều khoản Sử dụng Hình ảnh & Bản quyền Chế tác" summary and a required checkbox. Ticking it, or **📜 Đọc & Chấp thuận Điều khoản**, opens the terms dialog, where **Chấp nhận** and **Từ chối** work only after scrolling to the end; **Từ chối** leaves the page for the previous one. |
| **Gửi yêu cầu thiết kế** | Sends the request; disabled while sending, while images are being checked ("Đang kiểm tra ảnh…"), while a blurry image remains ("Vui lòng thay ảnh mờ") or without consent. |
| Validation and error messages | Empty request, description too long, too many images, blurry image, missing consent, image quality and image rate limit messages. |
