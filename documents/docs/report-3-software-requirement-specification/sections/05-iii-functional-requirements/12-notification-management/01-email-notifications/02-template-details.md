#### 12.1.2 Template Details

This screen allows Admins and Managers to:

- See a template's name, code, owning module, business description and allowed placeholders.
- Follow the template's version history with each version's subject, author and activation time.
- Create a new draft version and edit an existing draft.
- Preview any version with editable sample values.
- Activate a draft so that it replaces the current active version.

Only draft versions can be edited or activated; active and archived versions are read-only. A draft may use only the template's allowed placeholder variables (each written inside double curly braces). Activation applies to emails created after that moment; emails already rendered keep their earlier content.

| Field Name | Description |
| --- | --- |
| ← Quay lại danh sách mẫu thư | Returns to Notification Templates. |
| Header | Template name, "Mã mẫu" and "Phân hệ", with **+ Tạo bản nháp mới** and **Làm mới**. |
| Mô tả nghiệp vụ & Biến cho phép | Business description ("Không có mô tả." when empty) and the list of allowed placeholders. |
| Version history | Columns Phiên bản, Trạng thái ("Đang áp dụng", "Bản nháp" or "Lưu trữ"), Tiêu đề email (Subject), Tạo bởi / Ngày tạo and Kích hoạt lúc. |
| Xem trước | Opens the preview dialog for that version with sample values for each variable that can be changed before rendering. |
| Chỉnh sửa | Draft only. Opens the editor with the draft's content. |
| Draft editor | "Tiêu đề email (Subject)" (required), "Nội dung HTML" and "Nội dung Plain Text (Fallback)"; at least one body is required. Unknown placeholders show a warning and block saving. Buttons **Lưu bản nháp** or **Cập nhật bản nháp**. |
| Kích hoạt | Draft only. Opens a confirmation that names the version being replaced and warns that new emails use the new version at once. |
| Messages | A success or error message after each action; a failed save keeps the typed content. |
