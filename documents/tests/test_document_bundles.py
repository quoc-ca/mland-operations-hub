from __future__ import annotations

import unittest
from pathlib import Path
import re

from tools.document_generator import drive_image_placeholders, load_bundle, validate_bundle


ROOT = Path(__file__).resolve().parents[1]
REPORT_IDS = tuple(path.name for path in (ROOT / "docs").glob("report-*") if (path / "document.yml").is_file())


class MlandBundleTests(unittest.TestCase):
    def test_report_covers_use_the_shared_drive_fpt_placeholder(self) -> None:
        for report_id in REPORT_IDS:
            with self.subTest(report_id=report_id):
                bundle_root = ROOT / "docs" / report_id
                front_matter = (bundle_root / "front-matter.md").read_text(encoding="utf-8")
                self.assertIn("{{fpt-university width=35% align=center}}", front_matter)
                self.assertFalse((bundle_root / "assets" / "cover" / "fpt-university.png").exists())

    def test_governance_files_and_active_project_policy_are_present(self) -> None:
        agent_rules = (ROOT / "AGENT.md").read_text(encoding="utf-8")
        claude_context = (ROOT / "CLAUDE.md").read_text(encoding="utf-8")
        self.assertIn("Git and GitHub commit history", agent_rules)
        self.assertIn("[AGENT.md](AGENT.md)", claude_context)

        sources = [ROOT / "README.md"]
        sources.extend(path for report_id in REPORT_IDS for path in (ROOT / "docs" / report_id).rglob("*.md"))
        sources.extend(path for tracker in (
            "project-tracking", "report-5.1-unit-test", "report-5.2-integration-test", "report-5.3-system-test-frs", "report-5.4-system-test-nfrs", "report-5.5-acceptance-test-scripts",
        ) for path in (ROOT / "trackers" / tracker).rglob("*.csv"))
        content = "\n".join(path.read_text(encoding="utf-8-sig") for path in sources).casefold()
        self.assertNotIn("personalized product sales and workshop booking system — draft", content)
        self.assertNotIn("mland draft", content)
        self.assertNotIn("v0.1-draft", content)

        for report_id in REPORT_IDS:
            with self.subTest(report_id=report_id):
                front_matter = (ROOT / "docs" / report_id / "front-matter.md").read_text(encoding="utf-8")
                self.assertIn("Active project, under validation", front_matter)

    def test_all_report_bundles_are_valid(self) -> None:
        for report_id in REPORT_IDS:
            with self.subTest(report_id=report_id):
                root = ROOT / "docs" / report_id
                bundle = load_bundle(root)
                validate_bundle(bundle)
                if report_id == "report-3-software-requirement-specification":
                    # Report 3 diagrams are manually managed in Drive and injected
                    # only during the publishing workflow.
                    self.assertIn("r3-context-diagram", drive_image_placeholders(bundle))


if __name__ == "__main__":
    unittest.main()
