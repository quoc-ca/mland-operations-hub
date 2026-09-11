# Mland documentation workspace

`documents/` is the Git-first documentation workspace for the live Mland Operations Hub (MOH) project.

## Workflow and source of truth

Markdown report fragments and CSV trackers are the source of truth. The local generator validates report bundles, renders committed Mermaid or PlantUML sources to temporary assets, and creates ignored DOCX artifacts with Pandoc and `templates/reference.docx`. GitHub Actions repeats that process and publishes mapped DOCX and CSV content to Google Docs and Google Sheets.

Google Workspace is for publishing, review, comments, and suggestions. Do not use remote edits as a source to synchronize back into this folder. Never inspect or modify generated DOCX files unless a task explicitly requests manual review by a person.

## Workspace map

- `docs/report-*/` contains seven composable report bundles. `document.yml` defines the only valid composition order; `front-matter.md` and `sections/` hold authored content; committed diagram sources stay beneath the matching bundle.
- `trackers/` contains CSV-backed tracker groups mapped by `manifest.yml` to Google Sheets tabs.
- `tools/` validates and generates bundles. `build/` is generated and ignored. `templates/reference.docx` is a shared, read-only style input unless a task explicitly authorizes a style change.
- The parent repository's GitHub Actions workflow runs the synchronized publish path. Do not alter mappings, credentials, workflow behavior, or external artifacts without explicit authorization.

## Current project state

MOH is an active project. Individual requirements, scope decisions, designs, and targets may still be under validation; use the evidence labels already present in the reports and trackers instead of treating an assumption as a decision. Git and GitHub commit history provides automatically generated report and tracker change-history tables during build and synchronization.

Read [AGENT.md](AGENT.md) before changing this folder. It defines mandatory editing boundaries, evidence rules, change-history policy, and escalation conditions.
