from __future__ import annotations

import csv
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TRACKER_ROOT = ROOT / "trackers"
FORBIDDEN_SAMPLE_TEXT = (
    "shopeasy",
    "kiennt",
    "huynmg",
    "pass123",
    "test only",
    "illustrative demo",
)


class MlandTrackerLayoutTests(unittest.TestCase):
    def test_every_tracker_csv_is_a_clean_mland_tracker(self) -> None:
        files = sorted(TRACKER_ROOT.rglob("*.csv"))
        self.assertEqual(len(files), 37)
        for path in files:
            with self.subTest(path=path.relative_to(ROOT).as_posix()):
                with path.open(encoding="utf-8-sig", newline="") as source:
                    rows = list(csv.reader(source))
                self.assertGreaterEqual(len(rows), 3)
                self.assertEqual(rows[0][0], "Mland Operations Hub")
                width = len(rows[0])
                self.assertTrue(all(len(row) == width for row in rows))
                content = "\n".join(",".join(row) for row in rows).lower()
                self.assertFalse(any(value in content for value in FORBIDDEN_SAMPLE_TEXT))

    def test_change_log_templates_are_generated_from_git(self) -> None:
        policy = "Automatically generated from Git commit history; do not edit manually."
        paths = (
            TRACKER_ROOT / "report-2.1-project-tracking" / "ChangeLog.csv",
            TRACKER_ROOT / "report-3.1-rtw" / "9-ChangeLog.csv",
        )
        for path in paths:
            with self.subTest(path=path.relative_to(ROOT).as_posix()):
                with path.open(encoding="utf-8-sig", newline="") as source:
                    rows = list(csv.reader(source))
                self.assertEqual(rows[1][0], policy)
                self.assertEqual(rows[3][1:5], ["Commit", "Date", "Author", "Change Description"])
                self.assertTrue(all(not any(cell.strip() for cell in row) for row in rows[4:]))


if __name__ == "__main__":
    unittest.main()
