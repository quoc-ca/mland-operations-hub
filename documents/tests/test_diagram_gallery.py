from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.generate_diagram_gallery import active_bundles, discover_items, generate


def write_gallery_fixture(root: Path) -> Path:
    documents = root / "documents"
    documents.mkdir()
    (documents / "manifest.yml").write_text(
        "reference_doc: templates/reference.docx\n\ndocuments:\n  - id: report-gallery\n    source_bundle: docs/report-gallery\n",
        encoding="utf-8",
    )
    bundle = documents / "docs" / "report-gallery"
    (bundle / "sections").mkdir(parents=True)
    (bundle / "assets" / "diagrams" / "use-cases").mkdir(parents=True)
    (bundle / "assets" / "diagrams" / "workflows").mkdir(parents=True)
    (bundle / "assets" / "diagrams" / "details").mkdir(parents=True)
    (bundle / "document.yml").write_text(
        json.dumps({"id": "report-gallery", "output": "report-gallery.docx", "fragments": ["front-matter.md", "sections/01-overview.md"]}),
        encoding="utf-8",
    )
    (bundle / "front-matter.md").write_text("{{fpt-university width=35%}}\n", encoding="utf-8")
    (bundle / "sections" / "01-overview.md").write_text(
        "## Context diagram\n\n{{r3-context-diagram width=80%}}\n",
        encoding="utf-8",
    )
    diagrams = bundle / "assets" / "diagrams"
    (diagrams / "context.puml").write_text("@startuml\ntitle Context\n@enduml\n", encoding="utf-8")
    (diagrams / "use-cases" / "booking.puml").write_text("@startuml\ntitle Booking use cases\n@enduml\n", encoding="utf-8")
    (diagrams / "workflows" / "booking.puml").write_text("@startuml\ntitle Booking swimlane\n@enduml\n", encoding="utf-8")
    (diagrams / "details" / "booking.puml").write_text("@startuml\ntitle Booking activity\n@enduml\n", encoding="utf-8")
    (diagrams / "other.puml").write_text("@startuml\ntitle Other diagram\n@enduml\n", encoding="utf-8")
    (diagrams / "mermaid.md").write_text("```mermaid\nflowchart TD\n  A --> B\n```\n", encoding="utf-8")
    (diagrams / "not-a-diagram.md").write_text("# Not Mermaid\n", encoding="utf-8")
    return documents


class DiagramGalleryTests(unittest.TestCase):
    def test_discovers_source_categories_and_section_drive_placeholders(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            documents = write_gallery_fixture(Path(temp))
            items = discover_items(active_bundles(documents))

        self.assertEqual(sum(item.status == "preview" for item in items), 6)
        self.assertEqual({item.category for item in items if item.status == "preview"}, {
            "Context", "Use Cases", "Swimlanes / Workflows", "Activities / Details", "Mermaid", "Other",
        })
        drive = [item for item in items if item.status == "drive-only"]
        self.assertEqual(len(drive), 1)
        self.assertEqual(drive[0].title, "Context diagram (r3-context-diagram)")
        self.assertNotIn("fpt-university", [item.title for item in items])

    def test_generates_a_self_contained_gallery_without_source_fetches(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            write_gallery_fixture(root)
            output = root / "local-notes" / "diagram-gallery.html"
            generate(root, output)
            page = output.read_text(encoding="utf-8")

        self.assertIn('id="gallery-data"', page)
        self.assertIn("r3-context-diagram", page)
        self.assertIn("/png/~h", page)
        self.assertIn("/img/", page)
        self.assertNotIn("fetch(", page)
        self.assertIn("Có trên Drive", page)


if __name__ == "__main__":
    unittest.main()
