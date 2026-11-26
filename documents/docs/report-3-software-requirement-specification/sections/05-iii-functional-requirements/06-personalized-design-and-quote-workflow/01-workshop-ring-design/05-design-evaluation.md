#### 6.1.5 Design Evaluation

This screen allows Members to:

- View the saved design with its code, version and status, a read-only preview and a summary of all selected options.
- Read the advisory estimated cost, estimated weight and crafting difficulty with its advice.
- See the server-owned advisory result in this order: above the configured too-risky threshold, show no workshop recommendation and explain the consultation/custom-design path; otherwise recommend Wax Casting above its configured difficulty threshold, Silver Clay above its configured gemstone-count threshold, or the applicable Signature Workshop. The Wax Casting reason states that it requires two sessions on different days; other workshop discovery actions remain visible.
- Explore the workshops, edit the design, open a design consultation or archive the design.

The screen requires sign-in, and only the owning Member can open a persisted design. Guest preview is local-only and cannot be evaluated server-side. The evaluation is advisory and never means Staff or Manager approval; the customer-facing lifecycle is Draft, Saved and Archived.

| Field Name | Description |
| --- | --- |
| Header | "Kết quả thẩm định thiết kế", "Mã thiết kế" and version, and a status badge such as "Đã lưu" or "Đã lưu trữ". |
| Advisory and surface warnings | Notice that price and difficulty are advisory; a warning about textured bands when the evaluation returns one. |
| "Chỉ số ước tính kỹ thuật" | Advisory estimated value, estimated weight with any excess-material charge, and server-returned difficulty, level, advice and policy version. Difficulty has no fixed maximum and must not be displayed as `{score} / 10`. These values do not change the workshop package price, custom quote, payment or booking state. |
| Configuration summary | Material, shape, size, thickness, width, weight, gemstones, attachments, engraving placements and laser images. |
| **Xem danh sách mẫu thiết kế đã lưu** | Opens My Ring Designs. |
| **Khám phá các workshop** | Opens the workshop list. |
| **Chỉnh sửa thiết kế** | Opens Ring Configuration in edit mode; hidden for archived designs. A saved version remains editable only through the owner's current-version rules. |
| **Tư vấn thiết kế** | Opens Consultation Chat with this design and version as context. |
| **Xóa khỏi danh sách lưu** | Opens a confirmation dialog ("Xác nhận lưu trữ") and archives the design (soft delete); hidden for already archived designs. Feature-012 does not attach designs to bookings. |
| "Recommended" | When the result is below the too-risky cutoff, a button opens the recommended package with its reason and, for Wax Casting, the two-session/two-day note. For `TOO_RISKY`, no workshop recommendation button appears; show the advisory explanation and keep Explore Workshops visible. |
| "⚠️ Cảnh báo độ khó chế tác" | Shows server-returned advisory warnings when present. A too-risky result suppresses the workshop recommendation but never hides general workshop discovery or automatically creates a custom request. |
