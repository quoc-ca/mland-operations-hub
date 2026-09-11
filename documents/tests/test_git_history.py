from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.git_history import git_history_entries, markdown_change_history


class GitHistoryTests(unittest.TestCase):
    def test_reads_scoped_commit_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "docs" / "report-1"
            source.mkdir(parents=True)
            with patch(
                "tools.git_history.subprocess.run",
                side_effect=[
                    subprocess.CompletedProcess([], 0, f"{root}\n", ""),
                    subprocess.CompletedProcess([], 0, "abc1234\x1f2026-09-11\x1fMland Team\x1fRefine report\x1e", ""),
                ],
            ) as run:
                entries = git_history_entries(root, source)
        self.assertEqual([(entry.short_sha, entry.date, entry.author, entry.subject) for entry in entries], [("abc1234", "2026-09-11", "Mland Team", "Refine report")])
        self.assertEqual(run.call_args_list[1].args[0][-1], "docs/report-1")

    def test_formats_empty_history_as_generated_table(self) -> None:
        table = markdown_change_history(())
        self.assertIn("No committed changes found", table)
        self.assertIn("| Commit | Date | Changes | Author |", table)


if __name__ == "__main__":
    unittest.main()
