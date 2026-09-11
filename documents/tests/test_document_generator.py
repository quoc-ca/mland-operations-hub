from __future__ import annotations

import tempfile
import unittest
import json
from pathlib import Path
from unittest.mock import MagicMock, patch
from urllib.error import URLError

from tools.document_generator import (
    GenerationError,
    _download_png,
    _pandoc_fragment,
    _require_pandoc,
    build_bundle,
    load_bundle,
    validate_bundle,
)


def manifest_text(*fragments: str) -> str:
    return json.dumps({"id": "report-1", "output": "report-1.docx", "fragments": list(fragments)})


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
            self.assertEqual(validate_bundle(bundle), [])

    def test_rejects_duplicate_fragment(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            bundle_root = write_bundle(Path(temp) / "report", manifest_text("front-matter.md", "sections/01-section.md", "sections/01-section.md"))
            with self.assertRaisesRegex(GenerationError, "fragment-duplicate"):
                load_bundle(bundle_root)

    def test_rejects_outside_fragment(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            bundle_root = write_bundle(Path(temp) / "report", manifest_text("front-matter.md", "sections/../../escape.md"))
            with self.assertRaisesRegex(GenerationError, "path-outside-bundle"):
                load_bundle(bundle_root)

    def test_rejects_missing_asset(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            bundle_root = write_bundle(
                Path(temp) / "report",
                manifest_text("front-matter.md", "sections/01-section.md"),
                "# Title\n![missing](assets/diagram.mmd)\n",
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

    def test_reports_unavailable_diagram_service_with_source_location(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "report"
            bundle_root = write_bundle(
                root,
                manifest_text("front-matter.md", "sections/01-section.md"),
                "# Title\n![flow](assets/diagrams/flow.mmd)\n",
            )
            diagram = bundle_root / "assets" / "diagrams" / "flow.mmd"
            diagram.parent.mkdir(parents=True)
            diagram.write_text("flowchart TD\n", encoding="utf-8")
            bundle = load_bundle(bundle_root)
            with patch("tools.document_generator.urlopen", side_effect=URLError("offline")):
                with self.assertRaisesRegex(GenerationError, "diagram-render-failed") as error:
                    _download_png(bundle, bundle.fragments[0], diagram, "mermaid", Path(temp) / "flow.png")
            self.assertIn("assets/diagrams/flow.mmd", str(error.exception))

    def test_reports_missing_pandoc(self) -> None:
        with patch("tools.document_generator.shutil.which", return_value=None):
            with self.assertRaisesRegex(GenerationError, "pandoc-missing"):
                _require_pandoc()

    def test_reports_invalid_diagram_response(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "report"
            bundle_root = write_bundle(
                root,
                manifest_text("front-matter.md", "sections/01-section.md"),
                "# Title\n![flow](assets/diagrams/flow.mmd)\n",
            )
            diagram = bundle_root / "assets" / "diagrams" / "flow.mmd"
            diagram.parent.mkdir(parents=True)
            diagram.write_text("flowchart TD\n", encoding="utf-8")
            bundle = load_bundle(bundle_root)
            response = MagicMock()
            response.headers.get_content_type.return_value = "text/html"
            response.read.return_value = b"service error"
            response.status = 200
            response.__enter__.return_value = response
            with patch("tools.document_generator.urlopen", return_value=response):
                with self.assertRaisesRegex(GenerationError, "diagram-render-invalid-response"):
                    _download_png(bundle, bundle.fragments[0], diagram, "mermaid", Path(temp) / "flow.png")

    def test_reports_missing_reference_document(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            bundle_root = write_bundle(root / "report", manifest_text("front-matter.md", "sections/01-section.md"))
            with self.assertRaisesRegex(GenerationError, "reference-doc-missing"):
                build_bundle(bundle_root, root, root / "build")

    def test_maps_pandoc_line_to_fragment(self) -> None:
        fragment = _pandoc_fragment("Error at line 12", [(1, 5, "front-matter.md"), (8, 20, "sections/01.md")])
        self.assertEqual(fragment, "sections/01.md")


if __name__ == "__main__":
    unittest.main()
