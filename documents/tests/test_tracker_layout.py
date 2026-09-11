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
    def test_every_tracker_csv_is_a_clean_mland_draft(self) -> None:
        files = sorted(TRACKER_ROOT.rglob("*.csv"))
        self.assertEqual(len(files), 37)
        for path in files:
            with self.subTest(path=path.relative_to(ROOT).as_posix()):
                with path.open(encoding="utf-8-sig", newline="") as source:
                    rows = list(csv.reader(source))
                self.assertGreaterEqual(len(rows), 3)
                self.assertEqual(rows[0][0], "Mland Operations Hub — Draft")
                width = len(rows[0])
                self.assertTrue(all(len(row) == width for row in rows))
                content = "\n".join(",".join(row) for row in rows).lower()
                self.assertFalse(any(value in content for value in FORBIDDEN_SAMPLE_TEXT))


if __name__ == "__main__":
    unittest.main()
