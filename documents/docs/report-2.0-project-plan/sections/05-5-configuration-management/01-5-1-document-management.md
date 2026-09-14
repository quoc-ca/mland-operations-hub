## **5.1 Document and Work Management**

Mland documentation is managed Git-first: report fragments and tracker CSV sources are versioned in the repository, generated into DOCX/Google Workspace publishing artifacts, and traceable through commit history.

GitHub Issues are the sole operational work system for implementation, domain follow-up, research, bugs, tests, decisions, documentation, and risk mitigation. Each managed report work item has a visible action statement plus a hidden declaration in its named source fragment. The `develop` workflow validates labels and assignees before it creates or updates the corresponding GitHub Issue. Closed Issues and removed declarations are never closed, deleted, or reopened automatically.

**Known Mland context:** GitHub assignment notifications are the team notification mechanism. The project tracker remains evidence and reporting rather than a second task queue.

**Generated reporting:** The Project Tracking workbook's `IssuesOnGithub` tab is a read-only snapshot of non-pull-request GitHub Issues. It is refreshed by qualifying documentation pushes to `develop`; it is not a live two-way integration.

**TBD — Mland decision required:** Agree the members allowed to push or merge work-item declarations to `develop`, prepare the required GitHub labels and Iteration 01–15 milestones, and confirm each member's GitHub notification settings.

> **TBD — Mland decision required:** Complete this section with approved Mland requirements, design decisions, or evidence.
