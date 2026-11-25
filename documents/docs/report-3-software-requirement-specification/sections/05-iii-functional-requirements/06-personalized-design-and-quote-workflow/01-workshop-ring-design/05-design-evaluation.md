#### 6.1.5 Design Evaluation

This screen allows Members to:

- View the saved design with its code, version and status, a read-only preview and a summary of all selected options.
- Read the advisory estimated cost, estimated weight and crafting difficulty with its advice.
- See one recommended workshop package: Wax Casting when difficulty is above 9, otherwise Silver Clay when there are more than 5 stones, otherwise Signature Workshop. Above 11 no package is recommended; a risk warning offers a custom crafting request instead.
- Explore the workshops, edit the design, open a design consultation or archive the design.

The screen requires sign-in, and only the owning Member can open a persisted design. Guest preview is local-only and cannot be evaluated server-side. The evaluation is advisory and never means Staff or Manager approval; the customer-facing lifecycle is Draft, Saved and Archived.

| Field Name | Description |
| --- | --- |
| Header | "Kết quả thẩm định thiết kế", "Mã thiết kế" and version, and a status badge such as "Đã lưu" or "Đã lưu trữ". |
| Advisory and surface warnings | Notice that price and difficulty are advisory; a warning about textured bands when the evaluation returns one. |
| "Chỉ số ước tính kỹ thuật" | Estimated cost ("Theo gói" when there is none) marked as not changing the deposit, estimated weight with any excess-material charge, and difficulty "{score} / 10" with advice. |
| Configuration summary | Material, shape, size, thickness, width, weight, gemstones, attachments, engraving placements and laser images. |
| **Xem danh sách mẫu thiết kế đã lưu** | Opens My Ring Designs. |
| **Khám phá các workshop** | Opens the workshop list. |
| **Chỉnh sửa thiết kế** | Opens Ring Configuration in edit mode; hidden for archived or locked designs. |
| **Tư vấn thiết kế** | Opens Consultation Chat with this design and version as context. |
| **Xóa khỏi danh sách lưu** | Opens a confirmation dialog ("Xác nhận lưu trữ") and archives the design (soft delete); hidden for archived, locked or booking-attached designs. |
| "Recommended" | Button with the recommended package name that opens that workshop, its reason, and for Wax Casting a note that it takes two sessions on two different days. |
| "⚠️ Cảnh báo độ khó chế tác" | Replaces the recommendation when difficulty is above 11: "Mẫu nhẫn quá rủi ro để người dùng tự tay chế tạo" and **Gửi yêu cầu chế tác Custom cho mẫu này →**, which opens Design Request Workspace with the design attached. |
