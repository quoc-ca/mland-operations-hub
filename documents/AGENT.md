# Agent Operating Rules for `documents/`

## Purpose

This folder is the Git-first source of truth for the Personalized Product Sales and Workshop Booking System reports. Markdown and CSV trackers are validated locally, built into DOCX, and synced by GitHub Actions to Google Docs and Sheets.

Act as a controlled documentation editor: make the smallest evidence-based change requested, and flag uncertainty instead of filling gaps.

## Editing boundaries

- Edit only the report fragment(s) or tracker record(s) named by the task.
- Do not change `document.yml`, `manifest.yml`, Drive file IDs, workflows, tracker columns, `build/`, or `templates/reference.docx` unless the task authorizes that exact change.
- Never edit generated DOCX files. Google Docs and Sheets are review surfaces, not editing surfaces.
- Keep fragment order and bundle structure. Run the generator and tests after a change when permitted.

## Evidence rules

- Keep these labels distinct: `Known Mland context`, `Observed`, `Stakeholder statement`, `Group decision`, `Assumption — validate with Mland`, `TBD — Mland decision required`.
- Do not invent facts, decisions, people, schedules, budgets, architecture, credentials, test results, or production data.
- Keep personal, customer, employee, and transaction data out of documents unless the task needs minimized, authorized data.
- The operational audit log is a product requirement. Never remove or weaken it.

## Writing rules (Reports 2, 3, 4)

**Style — each rule is checkable**
- Sentence ≤ 25 words. Paragraph ≤ 4 sentences.
- Define each term once, where it first appears. Reuse the same term afterwards.
- Prefer tables and numbered lists over prose. Keep the language of the existing report.
- State the point first, then the detail. Delete sentences that restate the heading.

**Self-contained content (no pointing to Specs)**
- A report must read correctly without opening `ppswbs-spec/`, `ppswbs-testing/`, or any `spec.md`, `plan.md`, `data-model.md`.
- Never write "see spec/plan/data-model" or paste a `ppswbs-spec` path. Select the relevant facts and rewrite them in the report.
- Internal trace IDs (`UC-xx`, `FR-xx`) are allowed only in a Traceability column or table.
- Copy only what the reader needs. Do not paste whole spec sections.

**Flow descriptions (use cases and detailed design)**
- Use cases describe actor-visible steps: numbered Main Flow, then Alternative and Exception Flows with the step they branch from.
- Detailed design (sequence diagrams) describes system interactions: UI → controller → service → data. Do not mix the two levels.
- One flow per scenario. Do not merge happy path, alternatives, and errors into one list.
- Before writing a flow, check the source in this order: running code (`ppswbs_backend`, `ppswbs_frontend`) > approved spec in `ppswbs-spec/` > existing report text.
- If the sources disagree, do not choose silently. Describe the code behaviour, mark the difference as `TBD — Mland decision required`, and tell the requester.
- Every step in a diagram must appear in the matching text, and the reverse.

## Change history

Git history is the authoritative record of document author, date, and change description. Change History tables and ChangeLog tabs are generated at build time. Do not add or edit manual versions, authors, dates, or ChangeLog records.

## Work tracking

GitHub Issues are the only source for assignable work. Declaration format (`MOH-WORK-ITEM`) and Issue rules are in [WORK-ITEMS.md](WORK-ITEMS.md). Read it before adding or touching any work item. `IssuesOnGithub` is generated and read-only.

## Escalation

Ask for explicit authorization before changing source boundaries, publishing configuration, mappings, templates, or external Google Workspace artifacts. Report missing evidence as a TBD or an assumption instead of guessing.
