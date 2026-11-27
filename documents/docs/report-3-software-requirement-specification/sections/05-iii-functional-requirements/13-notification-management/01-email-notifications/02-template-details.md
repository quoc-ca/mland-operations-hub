#### 13.1.2 Template Details

![](assets/screens/live/94-template-details.png)

This screen allows Admins and Managers to:

- See a template's name, code, owning module, business description and allowed placeholders.
- Follow the template's version history with each version's subject, author and activation time.
- Create a new draft version and edit an existing draft.
- Preview any version with editable sample values.
- Activate a draft so that it replaces the current active version.

Only draft versions can be edited or activated; active and archived versions are read-only. A draft may use only the template's allowed placeholder variables, each written inside double curly braces. Activation applies to emails created after that moment; emails already rendered keep their earlier content.

**Field Description**

| Field Name | Description |
| --- | --- |
| ← Back to the template list | Returns to Notification Templates. |
| Header | Template name and "Template code: {code} • Module: {module}", with **+ Create new draft** and **Refresh**. |
| Business description & allowed variables | Business description ("No description." when empty) and "Allowed variables (Allowed Placeholders):" with the list, or "No variables". |
| Content version history (Version History) | Columns Version, Status ("In use", "Draft" or "Archived"), Email subject (Subject), Created by / Created on ("System" when no author) and Activated at. Empty: "This template has no content versions yet." |
| Preview | Opens the preview dialog for that version. It shows a safe-sample-data notice, "Customize sample values ({count} variables)" with **Update preview**, the rendered subject, and tabs "HTML view" and "Plain Text content". |
| Edit | Draft only. Opens the editor "Edit draft v{version} ({name})" with the draft's content. |
| Draft editor | "Email subject (Subject) *" (required), "HTML content (HTML Template)" and "Plain Text content (Fallback)"; at least one body is required. Variables outside the allowed list show a warning and block saving. Buttons **Save draft** or **Update draft**. |
| Activate | Draft only. Opens "Confirm activating version v{version}", which names the version being replaced and warns that new emails use the new version at once; **Cancel** or **Confirm activation**. |
| Messages | For example "New draft v{version} has been created.", "Draft v{version} has been updated." or "Version v{version} was activated successfully."; a failed save keeps the typed content. |
