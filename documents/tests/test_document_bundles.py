from __future__ import annotations

import unittest
from pathlib import Path

from tools.document_generator import load_bundle, validate_bundle


ROOT = Path(__file__).resolve().parents[1]
REPORT_IDS = (
    "report-1-vision-scope",
    "report-2.0-project-plan",
    "report-3.0-srs",
    "report-3.2-fds",
    "report-3.2-screen-design-spec",
    "report-4-tds",
    "report-5.0-test-plan",
)


class MlandBundleTests(unittest.TestCase):
    def test_all_mland_report_bundles_are_nested_and_valid(self) -> None:
        for report_id in REPORT_IDS:
            with self.subTest(report_id=report_id):
                root = ROOT / "docs" / report_id
                bundle = load_bundle(root)
                relative_fragments = [fragment.relative_to(root).as_posix() for fragment in bundle.fragments]
                self.assertEqual(relative_fragments[0], "front-matter.md")
                self.assertTrue(all(path.startswith("sections/") for path in relative_fragments[1:]))
                self.assertTrue(any(path.count("/") >= 2 for path in relative_fragments[1:]))
                diagrams = validate_bundle(bundle)
                if report_id == "report-1-vision-scope":
                    self.assertEqual(len(diagrams), 1)
                    self.assertEqual(diagrams[0][2], "mermaid")
                else:
                    self.assertEqual(diagrams, [])


if __name__ == "__main__":
    unittest.main()
