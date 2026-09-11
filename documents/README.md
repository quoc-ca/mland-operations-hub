# Mland documentation workflow

This repository manages Mland documentation through a Git-first workflow:

```text
Markdown fragments + Mermaid/PlantUML source
  -> temporary PNG assets
  -> Pandoc + shared reference.docx
  -> build/*.docx
```

GitHub Actions validates and generates document bundles, then synchronizes mapped Google Docs and Google Sheets. Git remains the source of truth; Google Workspace is the publishing and review surface.

Agents working in this folder must read [AGENT.md](AGENT.md) for editing rules and [CLAUDE.md](CLAUDE.md) for workspace context.

## Generate the Mland reports

Run these commands from the repository root:

```powershell
$bundles = @(
  'report-1-vision-scope',
  'report-2.0-project-plan',
  'report-3.0-srs',
  'report-3.2-fds',
  'report-3.2-screen-design-spec',
  'report-4-tds',
  'report-5.0-test-plan'
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

Each content task must name the fragment(s) it is allowed to edit. Do not alter generated files or reorder fragments implicitly.

Use bundle-root-relative image references. A Mermaid or PlantUML image reference points to its committed source:

```markdown
![System context](assets/diagrams/system-context.mmd)
![Order state](assets/diagrams/order-state.puml)
```

The generator replaces those references with temporary PNGs. Regular PNG/JPG/GIF/SVG image assets must also be committed under the bundle. External image URLs are rejected to keep builds reproducible.

Mermaid source is sent to the configured `mermaid.ink` endpoint and PlantUML source to the configured PlantUML server. Do not use public rendering for sensitive diagrams. A network, response, source, or Pandoc failure exits non-zero and emits a diagnostic with report, fragment, asset, renderer, endpoint, status, and reason.

## Shared Word style

You own `templates/reference.docx`; automation reads it only. Configure it once with the shared style contract:

- Times New Roman Unicode, black, 13 pt body text;
- margins: top/bottom 2 cm, left 3 cm, right 2 cm;
- Heading 1 16 pt bold, Heading 2 14 pt bold, Heading 3 13 pt bold;
- compact tables, monospaced code, black underlined links; no header/footer.

Markdown controls semantic structure only: headings, lists, tables, links, code, and image references.

## Google Workspace synchronization

`manifest.yml` maps each document bundle and tracker folder directly to its target Google file. The workflow runs for a manual dispatch and for relevant pushes to `main`:

- manual dispatch builds and synchronizes all mapped Google Docs and Sheets;
- a changed document bundle publishes only its mapped Google Doc;
- a changed tracker CSV publishes only its mapped Google Sheet;
- a changed manifest, reference DOCX, generator, synchronizer, or workflow republishes all mapped Google Docs (and `manifest.yml` also republishes all trackers).

Before the first run, enable the Drive and Sheets APIs, share every mapped Google Doc and Google Sheet with the service-account email as an Editor, and create the repository Actions secret `GDRIVE_CREDENTIALS` containing the complete service-account JSON. Do not commit that JSON.

The workflow builds every selected document bundle before it contacts Google. It then validates all selected Google file types and mapped Sheet tab names before the first remote write. A DOCX upload replaces the mapped Google Doc's full content; tracker sync clears and rewrites mapped Sheet values. Open the Google artifacts yourself to assess conversion and formatting.
