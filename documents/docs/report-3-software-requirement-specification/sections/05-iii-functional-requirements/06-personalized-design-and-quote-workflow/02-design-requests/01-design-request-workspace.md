#### 6.2.1 Design Request Workspace

{{r3-06-design-request-workspace-1 width=100% align=center}}

{{r3-06-design-request-workspace-2 width=100% align=center}}

**Description:** Displays the custom design request form and allows authenticated Members to describe their ring idea, attach up to five quality-checked reference images, optionally link a saved ring design, accept image consent, and submit for review.

::: {.docx-table width="100%" columns="50%,50%"}

| Field Name | Description |
| --- | --- |
| Title and subtitle | "Biến ý tưởng của bạn thành hiện thực": the shop assesses the described ring and provides a suitable price. |
| **Xem yêu cầu của tôi** | Member only; opens the **Yêu cầu chế tác** tab of My Ring Designs. |
| Attached ring design | A list of the Member's saved designs ("-- Không đính kèm mẫu thiết kế --" by default) with a preview card of the chosen design and **✕ Gỡ đính kèm**, or a link to design one. |
| Mô tả ý tưởng thiết kế | Optional idea description of at most 4,000 characters with a live character counter. |
| Reference images | JPEG, PNG or WEBP, at most 5 MB and 4096 × 4096 px each, up to 5 images; each shows sharpness check status and can be removed. |
| Image consent | Shown when images are attached; "Chấp thuận Điều khoản Sử dụng Hình ảnh & Bản quyền Chế tác" summary and a required checkbox. Ticking it, or **📜 Đọc & Chấp thuận Điều khoản**, opens the terms dialog, where **Chấp nhận** and **Từ chối** work only after scrolling to the end. |
| **Gửi yêu cầu thiết kế** | Submits the request once valid (requires at least one of description, reference images, or attached saved design); disabled while sending, while images are being checked, while a blurry image remains, or without required consent. Opens Design Request Details upon success. |
| Validation and error messages | Feedback for empty request, description exceeding limit, too many images, blurry images, missing consent, or rate limits. |

:::
