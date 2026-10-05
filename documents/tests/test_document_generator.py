from __future__ import annotations

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

    def test_drive_placeholder_is_standalone_and_uses_local_marker_without_assets(self) -> None:
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
            self.assertEqual(injected, "![ring-hero](assets/ring-hero.png){ width=80% }\n")
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
            "![ring-hero](assets/ring-hero.png){ width=35% }\n![ring-hero](assets/ring-hero.png){ width=2.4in }\n",
        )

    def test_rejects_invalid_drive_placeholder_width_or_attributes(self) -> None:
        invalid = ("0%", "101%", "0.05in", "10.1in", "35px", "35% height=2in")
        for value in invalid:
            with self.subTest(value=value), tempfile.TemporaryDirectory() as temp:
                bundle_root = write_bundle(
                    Path(temp) / "report",
                    manifest_text("front-matter.md", "sections/01-section.md"),
                    f"# Title\n\n{{{{ring-hero width={value}}}}}\n",
                )
                with self.assertRaisesRegex(GenerationError, "drive-asset-placeholder-invalid"):
                    validate_bundle(load_bundle(bundle_root))

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

    def test_use_case_filter_is_enabled_only_for_report_3(self) -> None:
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
                ) as run:
                    build_bundle(bundle_root, root, root / "build")
                    return run.call_args.args[0]

        report_3_command = capture_build_command(report_3_id)
        other_report_command = capture_build_command("report-1")
        filter_path = Path(__file__).resolve().parents[1] / "tools" / "usecase_headings_to_table.lua"
        self.assertIn(f"--lua-filter={filter_path}", report_3_command)
        self.assertFalse(any(argument.startswith("--lua-filter=") for argument in other_report_command))

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
            source_path.write_text(source, encoding="utf-8")
            result = subprocess.run(
                [
                    shutil.which("pandoc") or "pandoc",
                    "--from=markdown",
                    "--to=docx",
                    f"--lua-filter={filter_path}",
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
        rows = tables[0].findall("w:tr", namespace)
        self.assertEqual(len(rows), 7)
        self.assertEqual(len(rows[0].findall("w:tc", namespace)), 4)
        self.assertTrue(all(len(row.findall("w:tc", namespace)) == 2 for row in rows[1:]))
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
        self.assertIn("| `abc1234` | 2026-09-11 | Refine SRS \\| tracker | Mland Team |", result)


if __name__ == "__main__":
    unittest.main()
