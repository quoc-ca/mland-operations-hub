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
    def test_governance_files_and_active_project_policy_are_present(self) -> None:
        agent_rules = (ROOT / "AGENT.md").read_text(encoding="utf-8")
        claude_context = (ROOT / "CLAUDE.md").read_text(encoding="utf-8")
        self.assertIn("Git and GitHub commit history", agent_rules)
        self.assertIn("[AGENT.md](AGENT.md)", claude_context)

        sources = [ROOT / "README.md"]
        sources.extend((ROOT / "docs").rglob("*.md"))
        sources.extend((ROOT / "trackers").rglob("*.csv"))
        content = "\n".join(path.read_text(encoding="utf-8-sig") for path in sources).casefold()
        self.assertNotIn("mland operations hub — draft", content)
        self.assertNotIn("mland draft", content)
        self.assertNotIn("v0.1-draft", content)

        for report_id in REPORT_IDS:
            with self.subTest(report_id=report_id):
                front_matter = (ROOT / "docs" / report_id / "front-matter.md").read_text(encoding="utf-8")
                self.assertIn("Active project, under validation", front_matter)

        history_fragments = (
            ROOT / "docs" / "report-1-vision-scope" / "sections" / "02-document-change-history" / "00-overview.md",
            ROOT / "docs" / "report-2.0-project-plan" / "sections" / "00-document-change-history" / "00-overview.md",
            ROOT / "docs" / "report-3.0-srs" / "sections" / "02-document-change-history" / "00-overview.md",
            ROOT / "docs" / "report-3.2-fds" / "sections" / "01-version-history" / "00-overview.md",
            ROOT / "docs" / "report-3.2-screen-design-spec" / "sections" / "02-document-change-history" / "00-overview.md",
            ROOT / "docs" / "report-4-tds" / "sections" / "02-document-change-history" / "00-overview.md",
            ROOT / "docs" / "report-5.0-test-plan" / "sections" / "00-document-change-history" / "00-overview.md",
        )
        for path in history_fragments:
            with self.subTest(path=path.relative_to(ROOT).as_posix()):
                self.assertIn(
                    "<!-- AUTO-GENERATED: GIT-CHANGE-HISTORY -->",
                    path.read_text(encoding="utf-8"),
                )

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
