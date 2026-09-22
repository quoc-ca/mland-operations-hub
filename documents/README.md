# SEP490 documentation workflow

This repository manages documentation for **Personalized Product Sales and Workshop Booking System** (group **SEP490_G22**) through a Git-first workflow:

```text
Markdown fragments + Mermaid/PlantUML source
  -> temporary PNG assets
  -> Pandoc + shared reference.docx
  -> build/*.docx
```

GitHub Actions validates and generates document bundles, then synchronizes mapped Google Docs and Google Sheets. Git remains the source of truth; Google Workspace is the publishing and review surface.

Agents working in this folder must read [AGENT.md](AGENT.md) for editing rules and [CLAUDE.md](CLAUDE.md) for workspace context.

Each Report 1–5 bundle uses the SEP490 change-log table (`Date | A/M/D | In charge | Change Description`). It is generated from the configured, scoped Git history paths; the migration uses `M` by default rather than guessing an add/delete action from a commit message.

## Generate the active SEP490 reports

Run these commands from the repository root:

```powershell
$bundles = @(
  'report-1-project-introduction',
  'report-2-project-management-plan',
  'report-3-software-requirement-specification',
  'report-4-software-design-specification',
  'report-5.0-test-documentation'
)
$bundles | ForEach-Object { python tools/generate_document.py --bundle "docs/$_" --validate }
$bundles | ForEach-Object { python tools/generate_document.py --bundle "docs/$_" }
```

The generated artifacts are written to `build/*.docx` and ignored by Git. The generator never reads or edits output DOCX files after Pandoc produces them; open and assess each file manually.

## Bundle contract

Every `docs/report-*/document.yml` is JSON-compatible YAML so the local pipeline requires no Python packages. It declares:

- `id`: a stable report identifier;
- `output`: the DOCX filename;
- `fragments`: an explicit ordered list, starting with `front-matter.md`, followed by nested `sections/<H1-group>/<H2-section>.md` fragments;
- `renderers`: configurable public endpoints for Mermaid and PlantUML.
- `change_log`: the `template` format, historical source paths, and default A/M/D action.

Each content task must name the fragment(s) it is allowed to edit. Do not alter generated files or reorder fragments implicitly.

Use bundle-root-relative image references. PlantUML image references point to committed `.puml` source. Mermaid diagrams are committed as previewable Markdown files under `assets/diagrams/`, each containing exactly one non-empty `mermaid` fenced block; report fragments link to that Markdown file:

```markdown
[System context diagram](assets/diagrams/system-context.md)
![Order state](assets/diagrams/order-state.puml)
```

The generator renders Mermaid diagram links and PlantUML image references as temporary PNGs in DOCX output. Mermaid `.mmd` files are not supported. Active report raster images, including manually designed diagrams, use Drive placeholders rather than committed image files; Mermaid and PlantUML source remain committed when they are the diagram source of truth. External image URLs are rejected to keep builds reproducible.

To inject a Drive-hosted image only when publishing, place a standalone placeholder in a report fragment:

```markdown
{{asset-name}}
{{asset-name width=35%}}
{{asset-name width=2.4in}}
```

`asset-name` uses ASCII letters, digits, hyphens, and underscores only; it excludes the filename extension. `width` is optional: the default is `80%`; accepted explicit values are integer `1%`–`100%` or decimal `0.1in`–`10in`. No other attributes are supported. During the `develop` publishing workflow, the synchronizer matches the basename case-insensitively to exactly one PNG or JPEG in the Drive folder declared by the GitHub Secret `GDRIVE_ASSETS_FOLDER_ID`, then inserts it at the requested width. The `Assets/` folder is scanned directly, not recursively, and must be shared with the service-account email as a Reader. Missing, duplicate, non-downloadable, or unsupported assets fail only that report target with a diagnostic; they are not committed or logged. A Drive-only image change does not trigger GitHub Actions, so use a manual dispatch from `develop` to republish it.

Local DOCX builds do not contact Drive. They render `[Drive asset omitted: asset-name]` at a valid placeholder position; `--validate` checks placeholder and diagram syntax.

Mermaid source is sent to the configured `mermaid.ink` endpoint and PlantUML source to the configured PlantUML server. Do not use public rendering for sensitive diagrams. A network, response, source, or Pandoc failure exits non-zero and emits a diagnostic with report, fragment, asset, renderer, endpoint, status, and reason.

## Shared Word style

You own `templates/reference.docx`; automation reads it only. Configure it once with the shared style contract:

- Times New Roman Unicode, black, 13 pt body text;
- margins: top/bottom 2 cm, left 3 cm, right 2 cm;
- Heading 1 16 pt bold, Heading 2 14 pt bold, Heading 3 13 pt bold;
- compact tables, monospaced code, black underlined links; no header/footer.

Markdown controls semantic structure only: headings, lists, tables, links, code, and image references.

## Google Workspace synchronization

`manifest.yml` maps each document bundle and tracker folder directly to its target Google file. The workflow runs for a manual dispatch from `develop` and for relevant pushes to `develop`:

- manual dispatch builds and synchronizes all mapped Google Docs and Sheets only when dispatched from `develop`;
- a changed document bundle publishes only its mapped Google Doc;
- a changed tracker CSV publishes only its mapped Google Sheet;
- a changed manifest, reference DOCX, generator, synchronizer, or workflow republishes all mapped Google Docs (and `manifest.yml` also republishes all trackers).

Before the first run, enable the Drive and Sheets APIs, share every mapped Google Doc and Google Sheet with the service-account email as an Editor, and create the repository Actions secret `GDRIVE_CREDENTIALS` containing the complete service-account JSON. Do not commit that JSON.

The workflow processes every selected target independently: it builds each document bundle before replacing its Google Doc, and validates/reconciles each tracker spreadsheet from direct CSV filenames before writing its active Sheet values. Tabs without a CSV source are renamed with the `-out` archive suffix; archive tabs are preserved. Open the Google artifacts yourself to assess conversion and formatting.

Each mapped Google Doc and tracker spreadsheet is synchronized independently. A failed target does not prevent other selected targets from publishing, but the workflow ends failed if any target fails. The run log, GitHub Actions annotations, and Job Summary identify the target, phase, error, and suggested fix; successful remote writes are not rolled back.
