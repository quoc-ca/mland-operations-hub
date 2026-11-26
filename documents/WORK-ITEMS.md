# Operational Work Tracking Rules

Moved from `AGENT.md`. Content is unchanged.

GitHub Issues are the sole operational source of truth for assignable work: implementation, bugs, research, requirement gaps, decisions, tests, documentation, and risk mitigation. CSV trackers remain evidence and generated reporting; never use them as a parallel task queue.

- Only add a managed work-item declaration when the task explicitly authorizes the named report fragment, title, GitHub username assignee, and labels. Its visible Markdown action item must explain the work; the declaration is its hidden machine-readable counterpart.
- A declaration is scanned only from `documents/docs/` and must use exactly this JSON structure. `source` must equal the fragment's repository-relative path; labels must already exist in GitHub.

```html
<!-- MOH-WORK-ITEM {"id":"MOH-001","title":"Concise outcome","assignee":"github-username","labels":["type:task","priority:high","domain:platform","source:report"],"source":"documents/docs/report-x/sections/01-example.md"} -->
```

- Never add a declaration with an invented assignee, label, deadline, fact, or decision. Do not create, close, reopen, or manually alter a managed Issue outside the `develop` synchronization workflow.
- Manually discovered Issues are allowed through the repository Issue form. They remain unmanaged and are reviewed by the team before assignment.
- `IssuesOnGithub` is a generated, read-only snapshot published from `develop`; do not edit its CSV rows or Google Sheet tab. GitHub's own history remains the authoritative Issue lifecycle record.
