# SpecKit Document Metadata Policy

This policy applies to Markdown artifacts created or updated by this project's
SpecKit workflows. It does not change document-history rules in `documents/`,
skill-package YAML authorship, or executable/schema contracts such as OpenAPI,
JSON, and SQL. AGENT instructions and operational `ERROR-LOG.md` reports are not
SpecKit-generated design artifacts.

## Required block

Place this block directly below the main H1 title, after any YAML frontmatter.
Do not leave these placeholders in a newly generated document:

```markdown
**Date created**: [YYYY-MM-DD]
**Date updated**: [YYYY-MM-DD]
**Author**: [RESPONSIBLE_MEMBER]
**Last updated by**: [RESPONSIBLE_MEMBER]
```

Dates use `YYYY-MM-DD` in `Asia/Bangkok`. Author fields identify the responsible
team member supplied for the document, not the AI agent performing the edit.
They do not establish reviewer approval, decision authority, or ratification.

## Before a workflow can write

- Read this policy before setup, hooks, template copying, or other steps that
  may create or update a covered artifact. Read-only discovery may proceed.
- Establish the responsible member for the creation/update from explicit user
  input or a retrievable assignment clearly covering this operation. Reuse a
  supplied identity within its stated scope; do not ask again unnecessarily.
- If the member is unknown, ask before writing the affected artifact. Do not
  infer identity from the OS account, Git configuration, prior authorship,
  model name, or a person merely mentioned in a document.
- Do not substitute `Codex`, invent a human name, or publish `TBD` attribution
  to avoid the question. Existing `Author` alone does not identify this update's
  responsible member.
- This gate does not block the immediate operational error report required by
  a code-folder AGENT. That report uses the actual reporter and its own format.

## Creation, updates, and no-op

| Operation | Required behavior |
| --- | --- |
| Create a new artifact | Both dates are the local creation date; both author fields are the supplied responsible member. |
| Update existing content | Preserve `Date created` and `Author`; set `Date updated` to the local update date and `Last updated by` to the member responsible for this update. |
| Valid task/checklist state change | Treat as an update, without changing who is allowed to modify those markers. |
| Read/review with no content change | Leave the file byte-for-byte unchanged, including metadata. |
| Regenerate an existing artifact | Compare proposed content before writing; preserve creation metadata and apply update/no-op rules instead of resetting authorship. |

Determine whether there is a substantive content/marker change before refreshing
metadata. Do not create a write solely to renew dates or attribution. Multiple
edits on the same local date may retain the same date value.

Replace generic `Created` or creation `Date` labels with the canonical fields
when creating a new document. Keep distinct governance fields such as
`Ratified` and `Last Amended`; they are not creation/update metadata aliases.

## Legacy artifacts

Do not bulk-migrate existing documents as a side effect of a workflow. When an
assigned edit touches a legacy artifact, preserve known provenance and obtain
the responsible update member before writing. Do not invent a missing creation
date or original author, or use today's date as historical creation evidence.
Ask for the missing provenance or an explicit decision about that legacy
artifact before adding a complete metadata block; do not silently backfill it.
An authorized content edit may retain its legacy header without migrating it,
but must report the metadata gap rather than claim full compliance. Narrow
marker/append-only workflows may update existing update fields only; missing
creation metadata requires a separately authorized provenance correction.

## Workflow-specific constraints

- Apply the block to `spec.md`, `plan.md`, `tasks.md`, Markdown checklists,
  `research.md`, `data-model.md`, `quickstart.md`, Markdown contracts, and the
  constitution when those artifacts are created by their owning workflow.
- `specify` must use it in the built-in `checklists/requirements.md` output;
  `checklist` must include it in fallback output when the template is unavailable.
- `clarify` may update checklist update metadata only when a marker actually
  changes; item text, creation provenance, notes, order, and unrelated formatting
  remain protected. Unchanged revalidation is a no-op.
- `checklist` may append items and update update metadata, never mark generated
  items approved or rewrite earlier checklist content.
- `converge` may append tasks and update update metadata only when tasks are
  appended; no findings means no write. Prior tasks, IDs, order, and markers
  remain unchanged.
- `implement` may update task completion markers and task update metadata only
  when completion is evidenced. Reviewer-owned checklists remain read-only.
- `analyze` remains read-only. This policy does not grant any workflow broader
  write scope, approval authority, or permission to run a hook it lacks.

## Verification

Before reporting a write complete, check field names and placement, real ISO
dates and supplied identities, preserved creation provenance, justified update
fields, unchanged marker ownership, and no duplicate generic date labels.
When reporting a no-op, verify that the artifact was not rewritten. Keep source
Markdown metadata out of machine-readable API contracts and schema payloads.
