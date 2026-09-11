# Agent Operating Rules for `documents/`

## Purpose

This folder is the Git-first source of truth for Mland Operations Hub (MOH) documentation. Markdown bundles and CSV trackers are validated locally, generated into DOCX files, and synchronized by GitHub Actions to mapped Google Docs and Google Sheets.

Act as a controlled documentation editor: make the smallest evidence-based change requested, preserve the source contracts, and surface uncertainty instead of filling gaps with plausible content.

## Editing boundaries

- Edit only the report fragment(s) or tracker record(s) explicitly named by the task.
- Do not change `document.yml`, `manifest.yml`, Drive file IDs, GitHub Actions workflow/configuration, tracker columns, generated `build/` files, or `templates/reference.docx` unless the task explicitly authorizes that exact change.
- Never edit generated DOCX files. Google Docs and Google Sheets are publishing, review, comment, and suggestion surfaces; they are not source-of-truth editing surfaces.
- Preserve fragment order and bundle structure. Use the generator and tests after a change when the task permits execution.

## Evidence and content rules

- Preserve the distinction between `Known Mland context`, `Observed`, `Stakeholder statement`, `Group decision`, `Assumption — validate with Mland`, and `TBD — Mland decision required`.
- Do not invent facts, stakeholder decisions, people, schedules, budgets, architecture, credentials, test results, or production data.
- Keep personal, customer, employee, and transaction data out of documentation unless the task explicitly needs minimized and authorized data.
- MOH's operational audit log is a product requirement. Do not remove or weaken it when applying the document-history policy below.

## Change history

Git and GitHub commit history are the authoritative record of document author, date, and change description. Do not add or update manual document versions, authors, dates, or ChangeLog records. Retained ChangeLog sections and tracker tabs are policy notices only and must not be populated with operational rows.

## Escalation

Ask for explicit authorization before changing source boundaries, publishing configuration, mappings, templates, or external Google Workspace artifacts. Report missing evidence as a TBD or an assumption instead of guessing.
