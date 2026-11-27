### 6.1 Document Management

All reports are written as Markdown fragments in the Git repository, which is the single source of truth. A Python and Pandoc tool builds each report into Word format using the shared FPT template. On every push to `develop`, GitHub Actions rebuilds the reports and publishes them to Google Docs and Google Sheets for review.

Comments from the supervisor or teammates on Google Docs are applied back to the Markdown source, never edited directly in the published copy. The change log of each report is generated from Git commit history, so every change has an author, a date, and a description. Feature specifications are kept in the same repository and approved by the feature owner before implementation.
