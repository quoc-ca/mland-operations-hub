#### 6.1.5 Design Evaluation

This screen allows Members to:

- View the saved design with its code, version and status, a read-only preview and a summary of all selected options.
- Read the advisory estimated cost, estimated weight and crafting difficulty with its advice.
- See one advisory workshop recommendation: Silver Clay when the saved design has more than five gemstones, Wax Casting when difficulty is above 9, otherwise the applicable Signature Workshop. When both conditions apply, Wax Casting is the primary recommendation. The Wax Casting reason states that it requires two sessions on different days; other workshop discovery actions remain visible.
- Explore the workshops, edit the design, open a design consultation or archive the design.

The screen requires sign-in, and only the owning Member can open a persisted design. Guest preview is local-only and cannot be evaluated server-side. The evaluation is advisory and never means Staff or Manager approval; the customer-facing lifecycle is Draft, Saved and Archived.

| Field Name | Description |
| --- | --- |
| Header | "Kết quả thẩm định thiết kế", "Mã thiết kế" and version, and a status badge such as "Đã lưu" or "Đã lưu trữ". |
| Advisory and surface warnings | Notice that price and difficulty are advisory; a warning about textured bands when the evaluation returns one. |
| "Chỉ số ước tính kỹ thuật" | Advisory estimated value, estimated weight with any excess-material charge, and server-returned difficulty with advice. These values do not change the workshop package price, custom quote, payment or booking state. |
| Configuration summary | Material, shape, size, thickness, width, weight, gemstones, attachments, engraving placements and laser images. |
| **Xem danh sách mẫu thiết kế đã lưu** | Opens My Ring Designs. |
| **Khám phá các workshop** | Opens the workshop list. |
| **Chỉnh sửa thiết kế** | Opens Ring Configuration in edit mode; hidden for archived designs. A saved version remains editable only through the owner's current-version rules. |
| **Tư vấn thiết kế** | Opens Consultation Chat with this design and version as context. |
| **Xóa khỏi danh sách lưu** | Opens a confirmation dialog ("Xác nhận lưu trữ") and archives the design (soft delete); hidden for already archived designs. Feature-012 does not attach designs to bookings. |
| "Recommended" | Button with the recommended package name that opens that workshop, its reason, and for Wax Casting a note that it takes two sessions on two different days. |
| "⚠️ Cảnh báo độ khó chế tác" | Shows server-returned advisory warnings when present. It does not suppress the recommendation, hide other workshops or automatically create a custom request. |
