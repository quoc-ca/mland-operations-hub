from __future__ import annotations

import base64
import tempfile
import unittest
import json
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path
from unittest.mock import patch

from tools.document_generator import (
    GIT_HISTORY_MARKER,
    GenerationError,
    _pandoc_fragment,
    _replace_drive_image_placeholders,
    _replace_git_history,
    _apply_docx_layout,
    _require_pandoc,
    build_bundle,
    drive_image_placeholders,
    load_bundle,
    validate_bundle,
)
from tools.git_history import GitCommit


def manifest_text(*fragments: str, report_id: str = "report-1") -> str:
    return json.dumps({"id": report_id, "output": f"{report_id}.docx", "fragments": list(fragments)})


def write_bundle(root: Path, manifest: str, front_matter: str = "# Title\n") -> Path:
    root.mkdir(parents=True)
    (root / "document.yml").write_text(manifest, encoding="utf-8")
    (root / "front-matter.md").write_text(front_matter, encoding="utf-8")
    (root / "sections").mkdir()
    (root / "sections" / "01-section.md").write_text("## Section\n", encoding="utf-8")
    return root


class BundleValidationTests(unittest.TestCase):
    def test_accepts_explicitly_ordered_bundle(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            bundle_root = write_bundle(Path(temp) / "report", manifest_text("front-matter.md", "sections/01-section.md"))
            bundle = load_bundle(bundle_root)
            self.assertEqual(bundle.report_id, "report-1")
            self.assertIsNone(validate_bundle(bundle))

    def test_discovers_fragments_from_sections_tree(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            bundle_root = write_bundle(Path(temp) / "report", manifest_text("front-matter.md", "sections/moved-away.md"))
            nested = bundle_root / "sections" / "02-topic" / "01-detail.md"
            nested.parent.mkdir()
            nested.write_text("### Detail\n", encoding="utf-8")
            bundle = load_bundle(bundle_root)
            self.assertEqual(
                [fragment.relative_to(bundle_root).as_posix() for fragment in bundle.fragments],
                ["front-matter.md", "sections/01-section.md", "sections/02-topic/01-detail.md"],
            )

    def test_ignores_legacy_fragment_entries(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            bundle_root = write_bundle(Path(temp) / "report", manifest_text("front-matter.md", "sections/../../escape.md"))
            self.assertIsNone(validate_bundle(load_bundle(bundle_root)))

    def test_rejects_missing_asset(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            bundle_root = write_bundle(
                Path(temp) / "report",
                manifest_text("front-matter.md", "sections/01-section.md"),
                "# Title\n![missing](assets/diagram.png)\n",
            )
            bundle = load_bundle(bundle_root)
            with self.assertRaisesRegex(GenerationError, "asset-missing"):
                validate_bundle(bundle)

    def test_rejects_external_asset(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            bundle_root = write_bundle(
                Path(temp) / "report",
                manifest_text("front-matter.md", "sections/01-section.md"),
                "# Title\n![external](https://example.com/image.png)\n",
            )
            bundle = load_bundle(bundle_root)
            with self.assertRaisesRegex(GenerationError, "asset-external"):
                validate_bundle(bundle)

    def test_reports_missing_pandoc(self) -> None:
        with patch("tools.document_generator.shutil.which", return_value=None):
            with self.assertRaisesRegex(GenerationError, "pandoc-missing"):
                _require_pandoc()

    def test_rejects_code_managed_diagram_source(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            bundle_root = write_bundle(
                Path(temp) / "report",
                manifest_text("front-matter.md", "sections/01-section.md"),
                "# Title\n[flow](assets/diagrams/flow.md)\n",
            )
            diagram = bundle_root / "assets" / "diagrams" / "flow.md"
            diagram.parent.mkdir(parents=True)
            diagram.write_text("flowchart TD\n", encoding="utf-8")
            with self.assertRaisesRegex(GenerationError, "diagram-source-unsupported"):
                validate_bundle(load_bundle(bundle_root))

    def test_drive_placeholder_defaults_and_local_omission_marker(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            bundle_root = write_bundle(
                Path(temp) / "report",
                manifest_text("front-matter.md", "sections/01-section.md"),
                "# Title\n\n{{ring-hero}}\n",
            )
            bundle = load_bundle(bundle_root)
            self.assertEqual(drive_image_placeholders(bundle), ("ring-hero",))
            rendered = Path(temp) / "ring-hero.png"
            rendered.write_bytes(b"png")
            injected = _replace_drive_image_placeholders("{{ring-hero}}\n", {"ring-hero": rendered}, {"ring-hero": rendered})
            self.assertEqual(
                injected,
                '![ring-hero](assets/ring-hero.png "MOH_DRIVE_IMAGE:left"){ width=80% }\n',
            )
        self.assertEqual(
            _replace_drive_image_placeholders("{{ring-hero}}\n", None, {}),
            "[Drive asset omitted: ring-hero]\n",
        )

    def test_drive_placeholder_accepts_percent_and_inch_widths(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            bundle_root = write_bundle(
                Path(temp) / "report",
                manifest_text("front-matter.md", "sections/01-section.md"),
                "# Title\n\n{{ring-hero width=35%}}\n\n{{ring-hero width=2.4in}}\n",
            )
            bundle = load_bundle(bundle_root)
            self.assertEqual(drive_image_placeholders(bundle), ("ring-hero",))
            rendered = Path(temp) / "ring-hero.png"
            rendered.write_bytes(b"png")
            injected = _replace_drive_image_placeholders(
                "{{ring-hero width=35%}}\n{{ring-hero width=2.4in}}\n",
                {"ring-hero": rendered},
                {"ring-hero": rendered},
            )
        self.assertEqual(
            injected,
            '![ring-hero](assets/ring-hero.png "MOH_DRIVE_IMAGE:left"){ width=35% }\n'
            '![ring-hero](assets/ring-hero.png "MOH_DRIVE_IMAGE:left"){ width=2.4in }\n',
        )

    def test_drive_placeholder_width_and_alignment_are_independent_and_order_agnostic(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            rendered = Path(temp) / "ring-hero.png"
            rendered.write_bytes(b"png")
            injected = _replace_drive_image_placeholders(
                "{{ring-hero align=right}}\n"
                "{{ring-hero width=35% align=center}}\n"
                "{{ring-hero align=left width=2.4in}}\n",
                {"ring-hero": rendered},
                {"ring-hero": rendered},
            )
        self.assertEqual(
            injected,
            '![ring-hero](assets/ring-hero.png "MOH_DRIVE_IMAGE:right"){ width=80% }\n'
            '![ring-hero](assets/ring-hero.png "MOH_DRIVE_IMAGE:center"){ width=35% }\n'
            '![ring-hero](assets/ring-hero.png "MOH_DRIVE_IMAGE:left"){ width=2.4in }\n',
        )

    @unittest.skipUnless(shutil.which("pandoc"), "Pandoc is not installed")
    def test_injected_drive_images_have_no_caption_keep_alt_and_apply_alignment(self) -> None:
        image_filter = Path(__file__).resolve().parents[1] / "tools" / "drive_image_layout.lua"
        reference_doc = Path(__file__).resolve().parents[1] / "templates" / "reference.docx"
        source = """![asset-left](assets/test.png "MOH_DRIVE_IMAGE:left"){ width=80% }

![asset-center](assets/test.png "MOH_DRIVE_IMAGE:center"){ width=35% }

![asset-right](assets/test.png "MOH_DRIVE_IMAGE:right"){ width=2.4in }

![asset-default](assets/test.png "MOH_DRIVE_IMAGE:left"){ width=80% }

![Manual figure caption](assets/test.png){ width=80% }
"""
        png = base64.b64decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/S0cAAAAASUVORK5CYII="
        )
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            assets = root / "assets"
            assets.mkdir()
            (assets / "test.png").write_bytes(png)
            source_path = root / "images.md"
            output_path = root / "images.docx"
            source_path.write_text(source, encoding="utf-8")
            result = subprocess.run(
                [
                    shutil.which("pandoc") or "pandoc",
                    "--from=markdown+link_attributes",
                    "--to=docx",
                    f"--reference-doc={reference_doc}",
                    f"--lua-filter={image_filter}",
                    "--output",
                    str(output_path),
                    str(source_path),
                ],
                cwd=root,
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(_apply_docx_layout(output_path), (0, 4))
            with zipfile.ZipFile(output_path) as docx:
                document = ET.fromstring(docx.read("word/document.xml"))

        word_ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        drawing_ns = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
        namespace = {"w": word_ns, "wp": drawing_ns}
        image_paragraphs = [
            paragraph for paragraph in document.findall(".//w:p", namespace)
            if paragraph.find(".//wp:docPr", namespace) is not None
        ]
        self.assertEqual(len(image_paragraphs), 5)
        self.assertEqual(
            [
                paragraph.find("./w:pPr/w:jc", namespace).get(f"{{{word_ns}}}val")
                for paragraph in image_paragraphs[:4]
            ],
            ["left", "center", "right", "left"],
        )
        pictures = [paragraph.find(".//wp:docPr", namespace) for paragraph in image_paragraphs]
        self.assertEqual(
            [picture.get("descr") for picture in pictures[:4]],
            ["asset-left", "asset-center", "asset-right", "asset-default"],
        )
        self.assertTrue(all(picture.get("title") == "" for picture in pictures[:4]))
        visible_text = "".join(node.text or "" for node in document.findall(".//w:t", namespace))
        for asset_name in ("asset-left", "asset-center", "asset-right", "asset-default"):
            self.assertNotIn(asset_name, visible_text)
        self.assertIn("Manual figure caption", visible_text)

    def test_rejects_invalid_drive_placeholder_width_or_attributes(self) -> None:
        invalid = (
            ("width=0%", "Drive asset width must"),
            ("width=101%", "Drive asset width must"),
            ("width=0.05in", "Drive asset width must"),
            ("width=10.1in", "Drive asset width must"),
            ("width=35px", "Drive asset width must"),
            ("align=justify", "align must be left, center, or right"),
            ("height=2in", "Unsupported Drive image setting"),
            ("width=35% width=40%", "may only be specified once"),
            ("align=left align=right", "may only be specified once"),
        )
        for settings, expected in invalid:
            with self.subTest(settings=settings), tempfile.TemporaryDirectory() as temp:
                bundle_root = write_bundle(
                    Path(temp) / "report",
                    manifest_text("front-matter.md", "sections/01-section.md"),
                    f"# Title\n\n{{{{ring-hero {settings}}}}}\n",
                )
                with self.assertRaises(GenerationError) as raised:
                    validate_bundle(load_bundle(bundle_root))
                self.assertEqual(raised.exception.diagnostic.code, "drive-asset-placeholder-invalid")
                self.assertEqual(raised.exception.diagnostic.fragment, "front-matter.md")
                self.assertIn(expected, raised.exception.diagnostic.reason)

    def test_rejects_non_standalone_drive_placeholder(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            bundle_root = write_bundle(
                Path(temp) / "report",
                manifest_text("front-matter.md", "sections/01-section.md"),
                "# Title\nImage: {{ring-hero}}\n",
            )
            with self.assertRaisesRegex(GenerationError, "drive-asset-placeholder-invalid"):
                validate_bundle(load_bundle(bundle_root))

    def test_reports_missing_reference_document(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            bundle_root = write_bundle(root / "report", manifest_text("front-matter.md", "sections/01-section.md"))
            with self.assertRaisesRegex(GenerationError, "reference-doc-missing"):
                build_bundle(bundle_root, root, root / "build")

    def test_shared_table_filter_is_enabled_for_all_reports_after_use_case_filter(self) -> None:
        report_3_id = "report-3-software-requirement-specification"

        def capture_build_command(report_id: str) -> list[str]:
            with tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                reference_dir = root / "templates"
                reference_dir.mkdir()
                (reference_dir / "reference.docx").write_bytes(b"reference")
                bundle_root = write_bundle(
                    root / "report",
                    manifest_text("front-matter.md", "sections/01-section.md", report_id=report_id),
                )

                def fake_pandoc(command: list[str], **_: object) -> subprocess.CompletedProcess[str]:
                    output_index = command.index("--output") + 1
                    Path(command[output_index]).write_bytes(b"docx")
                    return subprocess.CompletedProcess(command, 0, "", "")

                with patch("tools.document_generator._require_pandoc", return_value="pandoc"), patch(
                    "tools.document_generator.subprocess.run", side_effect=fake_pandoc
                ) as run, patch("tools.document_generator._apply_docx_layout", return_value=(0, 0)):
                    build_bundle(bundle_root, root, root / "build")
                    return run.call_args.args[0]

        report_3_command = capture_build_command(report_3_id)
        other_report_command = capture_build_command("report-1")
        filter_path = Path(__file__).resolve().parents[1] / "tools" / "usecase_headings_to_table.lua"
        layout_filter_path = Path(__file__).resolve().parents[1] / "tools" / "markdown_table_layout.lua"
        drive_image_filter_path = Path(__file__).resolve().parents[1] / "tools" / "drive_image_layout.lua"
        self.assertIn(f"--lua-filter={filter_path}", report_3_command)
        self.assertIn(f"--lua-filter={layout_filter_path}", report_3_command)
        self.assertIn(f"--lua-filter={layout_filter_path}", other_report_command)
        self.assertIn(f"--lua-filter={drive_image_filter_path}", report_3_command)
        self.assertIn(f"--lua-filter={drive_image_filter_path}", other_report_command)
        self.assertLess(report_3_command.index(f"--lua-filter={filter_path}"), report_3_command.index(f"--lua-filter={layout_filter_path}"))
        self.assertLess(report_3_command.index(f"--lua-filter={layout_filter_path}"), report_3_command.index(f"--lua-filter={drive_image_filter_path}"))
        self.assertIn("--from=markdown+fenced_divs+raw_html", report_3_command)

    @unittest.skipUnless(shutil.which("pandoc"), "Pandoc is not installed")
    def test_use_case_filter_generates_native_table_and_keeps_unknown_heading_outside(self) -> None:
        filter_path = Path(__file__).resolve().parents[1] / "tools" / "usecase_headings_to_table.lua"
        source = """# UC-Test

## Primary Actors
Member

## Secondary Actors
None

## Description
Long enough description for the table cell.

## Preconditions
The system is available.

## Normal Sequence/Flow
1. The member starts the flow.
2. The system completes it.

## Alternative Sequences/Flows
No alternatives apply.

## Postconditions
The result is visible.

## Business Rule
BR-1 applies.

## Additional Notes
This heading remains outside the generated table.
"""
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source_path = root / "uc.md"
            output_path = root / "uc.docx"
            legacy_api_filter = root / "legacy-pandoc-api.lua"
            source_path.write_text(source, encoding="utf-8")
            legacy_api_filter.write_text("pandoc.Caption = nil\n", encoding="utf-8")
            layout_filter = Path(__file__).resolve().parents[1] / "tools" / "markdown_table_layout.lua"
            drive_image_filter = Path(__file__).resolve().parents[1] / "tools" / "drive_image_layout.lua"
            result = subprocess.run(
                [
                    shutil.which("pandoc") or "pandoc",
                    "--from=markdown",
                    "--to=docx",
                    f"--lua-filter={legacy_api_filter}",
                    f"--lua-filter={filter_path}",
                    f"--lua-filter={layout_filter}",
                    f"--lua-filter={drive_image_filter}",
                    "--output",
                    str(output_path),
                    str(source_path),
                ],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(result.returncode, 0, result.stderr)

            with zipfile.ZipFile(output_path) as docx:
                document = ET.fromstring(docx.read("word/document.xml"))

        namespace = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
        tables = document.findall(".//w:tbl", namespace)
        self.assertEqual(len(tables), 1)
        table_style = tables[0].find("./w:tblPr/w:tblStyle", namespace)
        self.assertIsNotNone(table_style)
        self.assertEqual(table_style.attrib.get(f"{{{namespace['w']}}}val"), "Metadata Table")
        rows = tables[0].findall("w:tr", namespace)
        self.assertEqual(len(rows), 7)
        self.assertEqual(len(rows[0].findall("w:tc", namespace)), 4)
        self.assertTrue(all(len(row.findall("w:tc", namespace)) == 2 for row in rows[1:]))
        first_row_labels = [
            "".join(node.text or "" for node in rows[0].findall(f"w:tc[{index}]//w:t", namespace))
            for index in (1, 3)
        ]
        remaining_row_labels = [
            "".join(node.text or "" for node in row.findall("w:tc[1]//w:t", namespace))
            for row in rows[1:]
        ]
        self.assertEqual(
            first_row_labels + remaining_row_labels,
            [
                "Primary Actors",
                "Secondary Actors",
                "Description",
                "Preconditions",
                "Postconditions",
                "Normal Flows",
                "Alternative Flows",
                "Business Rules",
            ],
        )
        span = rows[1].find(".//w:gridSpan", namespace)
        self.assertIsNotNone(span)
        self.assertEqual(span.attrib.get(f"{{{namespace['w']}}}val"), "3")
        body = document.find("w:body", namespace)
        self.assertIsNotNone(body)
        body_children = list(body) if body is not None else []
        table_index = body_children.index(tables[0])
        notes_after_table = [
            index for index, element in enumerate(body_children)
            if index > table_index
            and "Additional Notes" in "".join(node.text or "" for node in element.findall(".//w:t", namespace))
        ]
        self.assertTrue(notes_after_table, "Unknown same-level heading should remain after the table")

    @unittest.skipUnless(shutil.which("pandoc"), "Pandoc is not installed")
    def test_markdown_table_layout_sets_full_width_grid_and_header_policy(self) -> None:
        layout_filter = Path(__file__).resolve().parents[1] / "tools" / "markdown_table_layout.lua"
        reference_doc = Path(__file__).resolve().parents[1] / "templates" / "reference.docx"
        source = """<!-- MOH-SOURCE: report-2/sections/table.md -->

| No. | Description | Note |
|---|---|---|
| A-01 | A longer description for width calculation. | A short note. |

::: {.docx-table width="90%" columns="15%,50%,35%" repeat-header="true"}

| No. | Description | Note |
|---|---|---|
| A-02 | Another longer description for width calculation. | A longer note. |

:::

::: {.generated-history}

| Commit | Date | Changes |
|---|---|---|
| abc123 | 2026-10-09 | Generated history row |

:::
"""
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source_path = root / "tables.md"
            output_path = root / "tables.docx"
            source_path.write_text(source, encoding="utf-8")
            result = subprocess.run(
                [
                    shutil.which("pandoc") or "pandoc",
                    "--from=markdown+fenced_divs+raw_html",
                    "--to=docx",
                    f"--reference-doc={reference_doc}",
                    f"--lua-filter={layout_filter}",
                    "--output",
                    str(output_path),
                    str(source_path),
                ],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(_apply_docx_layout(output_path), (2, 0))
            with zipfile.ZipFile(output_path) as docx:
                document = ET.fromstring(docx.read("word/document.xml"))
                styles = ET.fromstring(docx.read("word/styles.xml"))

        namespace = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
        tables = document.findall(".//w:tbl", namespace)
        self.assertEqual(len(tables), 3)
        widths = [table.find("./w:tblPr/w:tblW", namespace) for table in tables]
        self.assertEqual(widths[0].attrib.get(f"{{{namespace['w']}}}w"), "5000")
        self.assertEqual(widths[0].attrib.get(f"{{{namespace['w']}}}type"), "pct")
        self.assertEqual(widths[1].attrib.get(f"{{{namespace['w']}}}w"), "4500")
        self.assertIsNone(tables[0].find("./w:tr/w:trPr/w:tblHeader", namespace))
        self.assertIsNotNone(tables[1].find("./w:tr/w:trPr/w:tblHeader", namespace))
        self.assertIsNotNone(tables[2].find("./w:tr/w:trPr/w:tblHeader", namespace))
        self.assertNotIn("MOH-SOURCE", "".join(node.text or "" for node in document.findall(".//w:t", namespace)))
        for table in tables[:2]:
            style = table.find("./w:tblPr/w:tblStyle", namespace)
            self.assertIsNotNone(style)
            self.assertEqual(style.attrib.get(f"{{{namespace['w']}}}val"), "TableGrid")
        history_style = tables[2].find("./w:tblPr/w:tblStyle", namespace)
        self.assertIsNotNone(history_style)
        self.assertNotEqual(history_style.attrib.get(f"{{{namespace['w']}}}val"), "TableGrid")
        grid_style = next(
            style for style in styles.findall(".//w:style", namespace)
            if style.get(f"{{{namespace['w']}}}styleId") == "TableGrid"
        )
        border_edges = grid_style.findall("./w:tblPr/w:tblBorders/*", namespace)
        self.assertEqual(
            {edge.tag.rsplit("}", 1)[-1] for edge in border_edges},
            {"top", "left", "bottom", "right", "insideH", "insideV"},
        )
        configured_columns = [int(item.attrib[f"{{{namespace['w']}}}w"]) for item in tables[1].findall("./w:tblGrid/w:gridCol", namespace)]
        self.assertEqual(len(configured_columns), 3)
        section = document.find(".//w:sectPr", namespace)
        page_size = section.find("./w:pgSz", namespace)
        page_margins = section.find("./w:pgMar", namespace)
        text_width = int(page_size.attrib[f"{{{namespace['w']}}}w"]) - sum(
            int(page_margins.get(f"{{{namespace['w']}}}{side}", "0"))
            for side in ("left", "right", "gutter")
        )
        self.assertEqual(sum(configured_columns), round(text_width * 0.9))
        self.assertAlmostEqual(configured_columns[0] / sum(configured_columns), 0.15, delta=0.01)
        self.assertAlmostEqual(configured_columns[1] / sum(configured_columns), 0.50, delta=0.01)
        self.assertAlmostEqual(configured_columns[2] / sum(configured_columns), 0.35, delta=0.01)
        first_row_cells = tables[1].findall("./w:tr[1]/w:tc", namespace)
        self.assertEqual(
            [int(cell.find("./w:tcPr/w:tcW", namespace).get(f"{{{namespace['w']}}}w")) for cell in first_row_cells],
            configured_columns,
        )

    @unittest.skipUnless(shutil.which("pandoc"), "Pandoc is not installed")
    def test_markdown_table_layout_rejects_invalid_directives_with_fragment_context(self) -> None:
        layout_filter = Path(__file__).resolve().parents[1] / "tools" / "markdown_table_layout.lua"
        invalid_settings = (
            ('width="101%"', "between 1% and 100%"),
            ('columns="15%,50%,30%"', "add up to 100%"),
            ('columns="15%,50%,35%,1%"', "has 4 widths but the table has 3 columns"),
            ('repeat-header="sometimes"', "repeat-header must be 'true' or 'false'"),
        )
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for index, (setting, expected) in enumerate(invalid_settings):
                with self.subTest(setting=setting):
                    source_path = root / f"invalid-{index}.md"
                    source_path.write_text(
                        f'''<!-- MOH-SOURCE: report-2/sections/invalid-{index}.md -->

::: {{.docx-table {setting}}}

| No. | Description | Note |
|---|---|---|
| A-01 | Example | Example |

:::
''',
                        encoding="utf-8",
                    )
                    result = subprocess.run(
                        [
                            shutil.which("pandoc") or "pandoc",
                            "--from=markdown+fenced_divs+raw_html",
                            "--to=docx",
                            f"--lua-filter={layout_filter}",
                            "--output",
                            str(root / f"invalid-{index}.docx"),
                            str(source_path),
                        ],
                        capture_output=True,
                        text=True,
                        check=False,
                    )
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn(expected, result.stderr)
                    self.assertIn(f"report-2/sections/invalid-{index}.md", result.stderr)

    @unittest.skipUnless(shutil.which("pandoc"), "Pandoc is not installed")
    def test_markdown_table_layout_accepts_fractional_column_percentages(self) -> None:
        layout_filter = Path(__file__).resolve().parents[1] / "tools" / "markdown_table_layout.lua"
        reference_doc = Path(__file__).resolve().parents[1] / "templates" / "reference.docx"
        source = """<!-- MOH-SOURCE: report-2/sections/fractional.md -->

::: {.docx-table columns="0.5%,49.5%,50%"}

| No. | Description | Note |
|---|---|---|
| A-01 | Example | Example |

:::
"""
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source_path = root / "fractional.md"
            output_path = root / "fractional.docx"
            source_path.write_text(source, encoding="utf-8")
            result = subprocess.run(
                [
                    shutil.which("pandoc") or "pandoc",
                    "--from=markdown+fenced_divs+raw_html",
                    "--to=docx",
                    f"--reference-doc={reference_doc}",
                    f"--lua-filter={layout_filter}",
                    "--output",
                    str(output_path),
                    str(source_path),
                ],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(_apply_docx_layout(output_path), (1, 0))
            with zipfile.ZipFile(output_path) as docx:
                document = ET.fromstring(docx.read("word/document.xml"))
        namespace = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
        columns = [
            int(item.attrib[f"{{{namespace['w']}}}w"])
            for item in document.findall(".//w:tbl/w:tblGrid/w:gridCol", namespace)
        ]
        self.assertEqual(len(columns), 3)
        self.assertAlmostEqual(columns[0] / sum(columns), 0.005, delta=0.001)
        self.assertAlmostEqual(columns[1] / sum(columns), 0.495, delta=0.001)
        self.assertAlmostEqual(columns[2] / sum(columns), 0.50, delta=0.001)

    @unittest.skipUnless(shutil.which("pandoc"), "Pandoc is not installed")
    def test_use_case_filter_rejects_missing_and_duplicate_fields(self) -> None:
        filter_path = Path(__file__).resolve().parents[1] / "tools" / "usecase_headings_to_table.lua"
        complete = """# UC-Test

## Primary Actors
Member

## Secondary Actors
None

## Description
Description text.

## Preconditions
Precondition text.

## Normal Flow
Normal text.

## Alternative Flows
Alternative text.

## Postconditions
Postcondition text.

## Business Rules
Rule text.
"""
        invalid_inputs = (
            (
                complete.replace("## Business Rules\nRule text.\n", ""),
                "Missing required use-case field heading: Business Rules",
            ),
            (
                complete.replace(
                    "## Preconditions\n",
                    "## Description\nDuplicate description.\n## Preconditions\n",
                ),
                "Duplicate use-case field heading: Description",
            ),
        )
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for index, (source, expected_error) in enumerate(invalid_inputs):
                with self.subTest(expected_error=expected_error):
                    source_path = root / f"invalid-{index}.md"
                    source_path.write_text(source, encoding="utf-8")
                    result = subprocess.run(
                        [
                            shutil.which("pandoc") or "pandoc",
                            "--from=markdown",
                            "--to=json",
                            f"--lua-filter={filter_path}",
                            str(source_path),
                        ],
                        capture_output=True,
                        text=True,
                        check=False,
                    )
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn(expected_error, result.stderr)

    def test_maps_pandoc_line_to_fragment(self) -> None:
        fragment = _pandoc_fragment("Error at line 12", [(1, 5, "front-matter.md"), (8, 20, "sections/01.md")])
        self.assertEqual(fragment, "sections/01.md")

    def test_replaces_change_history_marker_with_git_table(self) -> None:
        text = f"# Document Change History\n\n{GIT_HISTORY_MARKER}\n"
        result = _replace_git_history(
            text,
            (GitCommit(short_sha="abc1234", date="2026-09-11", author="Mland Team", subject="Refine SRS | tracker"),),
        )
        self.assertNotIn(GIT_HISTORY_MARKER, result)
        self.assertIn("::: {.generated-history}", result)
        self.assertIn("| `abc1234` | 2026-09-11 | Refine SRS \\| tracker | Mland Team |", result)


if __name__ == "__main__":
    unittest.main()
