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

## Use case template (Report 3, section 4.II)

Write every use case in this exact order. Keep each item short; a reader should grasp it in one pass.

1. Heading `### n.m Name`, then a 3-row header table: `Use Case ID | Use Case Name`, `Created By | Created Date`, `Primary Actors | Secondary Actors`. The ID is `UC-nn` from the use case list.
2. `Description`: one sentence, "As a … I want … so that …".
3. `Trigger`: one sentence naming the event that starts the use case.
4. `Preconditions` and `Postconditions`: numbered, one line each, at most 5 items.
5. `Normal Flow`: numbered happy path only. One action per step, one actor per step (`Actor` or `System`), at most 20 words. No conditions, no errors, no "if/when/or".
6. `Alternative Flows`: valid branches where the use case still succeeds. Format `<step><letter>. <condition> → <system response>`, for example `3a.`. Write `None.` if empty.
7. `Exception Flows`: branches that fail or stop. Same format as item 6, with the message code where one exists, for example `(MSG04)`. Write `None.` if empty.
8. `Business Rules`: IDs only. `Non-Functional Requirements`, `Priority`: copy from the source, else `—`. `Messages`: list the codes used, else `—`.
9. `Business Rule Definitions`: one-line table, kept until the appendix holds these definitions.

Rules for flows:
- Each case appears once. Do not repeat a Normal Flow step or an Exception case under Alternative Flows.
- Reference message codes (`MSGnn` from Appendix V.2) instead of retyping message text. Never invent a code.
- A long rule, list, or policy goes to Business Rules or the appendix, not into a flow line.
- Keep the actor in the header consistent with who performs step 1.

## Screen specification template (Report 3, section 5.III)

Keep screen specifications as concise UI references. Do not duplicate Section 4.II use-case flows or backend internals (idempotency, SMTP retries, audit logs).

1. Heading `#### n.m.k Screen Name`.
2. Image placeholder(s): `{{r3-<nn>-<screen-name> width=100% align=center}}` (where `<nn>` is the section number, e.g., `02`). For tall screens, split into sequential screenshots (`{{r3-<nn>-<screen-name>-1 ...}}`, `{{r3-<nn>-<screen-name>-2 ...}}`).
3. Brief description (1–2 sentences): start with `**Description:**` and state what the screen **displays** and **who can do what** on it.
4. Field table wrapped in `::: {.docx-table width="100%" columns="50%,50%"}` with two columns: `Field Name` and `Description`. List only visible UI fields, key input rules, and navigation actions concisely.

## Change history

Git and GitHub commit history are the authoritative record of document author, date, and change description. Change History tables and ChangeLog tabs are generated at build time. Do not add or edit manual document versions or ChangeLog records. Exception: the `Created By` and `Created Date` cells of a use case header may be filled by hand. Leave blank when unknown; never guess.

## Work tracking

GitHub Issues are the only source for assignable work. Declaration format (`MOH-WORK-ITEM`) and Issue rules are in [WORK-ITEMS.md](WORK-ITEMS.md). Read it before adding or touching any work item. `IssuesOnGithub` is generated and read-only.

## Escalation

Ask for explicit authorization before changing source boundaries, publishing configuration, mappings, templates, or external Google Workspace artifacts. Report missing evidence as a TBD or an assumption instead of guessing.
