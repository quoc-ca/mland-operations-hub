from __future__ import annotations

import unittest
from pathlib import Path
import re

from tools.document_generator import drive_image_placeholders, load_bundle, validate_bundle


ROOT = Path(__file__).resolve().parents[1]
REPORT_IDS = (
    "report-1-project-introduction",
    "report-2-project-management-plan",
    "report-3-software-requirement-specification",
    "report-4-software-design-specification",
    "report-5.0-test-documentation",
)


class MlandBundleTests(unittest.TestCase):
    def test_manifest_maps_only_the_new_template_targets(self) -> None:
        manifest = (ROOT / "manifest.yml").read_text(encoding="utf-8")
        document_block, tracker_block = manifest.split("trackers:", maxsplit=1)
        self.assertEqual(re.findall(r"^  - id: ([^\n]+)$", document_block, flags=re.MULTILINE), list(REPORT_IDS))
        self.assertEqual(
            re.findall(r"^  - id: ([^\n]+)$", tracker_block, flags=re.MULTILINE),
            [
                "project-tracking",
                "report-5.1-unit-test",
                "report-5.2-integration-test",
                "report-5.3-system-test-frs",
                "report-5.4-system-test-nfrs",
                "report-5.5-acceptance-test-scripts",
            ],
        )
        self.assertTrue(all((ROOT / path).is_dir() for path in re.findall(r"^    source_bundle: ([^\n]+)$", document_block, flags=re.MULTILINE)))
        self.assertTrue(all((ROOT / path.rstrip("/")).is_dir() for path in re.findall(r"^    source_folder: ([^\n]+)$", tracker_block, flags=re.MULTILINE)))

    def test_report_covers_use_the_shared_drive_fpt_placeholder(self) -> None:
        for report_id in REPORT_IDS:
            with self.subTest(report_id=report_id):
                bundle_root = ROOT / "docs" / report_id
                front_matter = (bundle_root / "front-matter.md").read_text(encoding="utf-8")
                self.assertIn("{{fpt-university width=35%}}", front_matter)
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

        for report_id in REPORT_IDS:
            path = ROOT / "docs" / report_id / "sections" / "01-change-log" / "00-overview.md"
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
                if report_id == "report-3-software-requirement-specification":
                    # Report 3 deliberately uses a hybrid diagram strategy:
                    # code-managed diagrams are rendered from committed PUML,
                    # while manually designed diagrams are resolved from Drive.
                    # Only the former appear in validate_bundle's render list.
                    self.assertGreaterEqual(len(diagrams), 14)
                    self.assertTrue(all(item[2] == "plantuml" for item in diagrams))
                    self.assertIn("r3-context-diagram", drive_image_placeholders(bundle))
                else:
                    self.assertEqual(diagrams, [])


if __name__ == "__main__":
    unittest.main()
