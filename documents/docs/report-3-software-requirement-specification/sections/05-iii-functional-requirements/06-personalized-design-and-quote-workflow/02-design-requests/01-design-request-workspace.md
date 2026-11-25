#### 6.2.1 Design Request Workspace

This screen allows authenticated Members to:

- Describe the ring they want made, upload up to five reference images, and optionally attach a saved workshop ring design.
- See per-image quality feedback and replace images that are blurry or invalid before sending.
- Give consent to image processing whenever at least one image is attached.
- Submit the request for human review by Staff or a Manager.

A request needs a description or at least one image. A Member may attach one of their own non-archived Feature-012 designs; the server rechecks ownership and stores an immutable snapshot of the selected state. Guests see the entry point and sign-in prompt but cannot submit or receive private tracking data.

| Field Name | Description |
| --- | --- |
| Title and subtitle | "Biến ý tưởng của bạn thành hiện thực": the shop assesses the described ring and provides a suitable price. |
| **Xem yêu cầu của tôi** | Member only; opens the "Yêu cầu chế tác" tab of My Ring Designs. |
| Attached ring design | A list of the Member's own non-archived designs ("-- Không đính kèm mẫu thiết kế --" by default) with a preview card of the chosen design and **✕ Gỡ đính kèm**, or a link to design one. |
| "Mô tả ý tưởng thiết kế" | Optional text of at most 4,000 characters with a character counter. |
| Reference images | JPEG, PNG or WEBP, at most 10 MB and 4096 × 4096 px each, at most 5 images; each shows "Đang kiểm tra độ sắc nét của ảnh...", "Đạt chuẩn" or "Lỗi ảnh" and can be removed. |
| Image consent | Shown when images are attached; "Chấp thuận Xử lý Hình ảnh Tham khảo" text and a required checkbox. |
| **Gửi yêu cầu thiết kế** | Sends the request; disabled while sending, while images are being checked ("Đang kiểm tra ảnh…"), while a blurry image remains ("Vui lòng thay ảnh mờ") or without consent. |
| Validation and error messages | Empty request, description too long, too many images, blurry image, missing consent and image quality messages. |
