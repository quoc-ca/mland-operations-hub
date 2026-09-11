from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

from tools.git_history import GitCommit


ROOT = Path(__file__).resolve().parents[1]
SCRIPT_PATH = ROOT.parent / ".github" / "scripts" / "sync_to_drive.py"
SPEC = importlib.util.spec_from_file_location("sync_to_drive", SCRIPT_PATH)
assert SPEC and SPEC.loader
sync = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(sync)


def document(identifier: str = "report-1") -> dict[str, str]:
    return {
        "id": identifier,
        "target": "google-doc",
        "source_bundle": f"docs/{identifier}",
        "drive_file_id": "1abcdefghijk",
    }


def tracker(identifier: str = "tracker-1") -> dict[str, object]:
    return {
        "id": identifier,
        "target": "google-sheet",
        "source_folder": f"trackers/{identifier}",
        "drive_file_id": "1abcdefghijk",
        "sheets": [{"csv": "items.csv", "sheet_name": "Items"}],
    }


class SyncSelectionTests(unittest.TestCase):
    def setUp(self) -> None:
        self.manifest = {"documents": [document("report-1"), document("report-2")], "trackers": [tracker()]}

    def test_selects_only_changed_bundle(self) -> None:
        documents, trackers = sync.select_entries(self.manifest, {"docs/report-1/sections/01.md"}, False)
        self.assertEqual([item["id"] for item in documents], ["report-1"])
        self.assertEqual(trackers, [])

    def test_global_document_input_selects_every_document(self) -> None:
        documents, trackers = sync.select_entries(self.manifest, {"templates/reference.docx"}, False)
        self.assertEqual([item["id"] for item in documents], ["report-1", "report-2"])
        self.assertEqual(trackers, [])

    def test_selects_only_changed_tracker(self) -> None:
        documents, trackers = sync.select_entries(self.manifest, {"trackers/tracker-1/items.csv"}, False)
        self.assertEqual(documents, [])
        self.assertEqual([item["id"] for item in trackers], ["tracker-1"])

    def test_sync_all_selects_everything(self) -> None:
        documents, trackers = sync.select_entries(self.manifest, set(), True)
        self.assertEqual(len(documents), 2)
        self.assertEqual(len(trackers), 1)


class SyncRemoteTests(unittest.TestCase):
    def test_generated_tracker_history_uses_git_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            tracker_root = root / "trackers" / "tracker-1"
            tracker_root.mkdir(parents=True)
            (tracker_root / "history.csv").write_text(
                "Mland Operations Hub,,,,\n"
                "Automatically generated from Git commit history; do not edit manually.,,,,\n"
                ",,,,\n"
                "#,Commit,Date,Author,Change Description\n",
                encoding="utf-8",
            )
            item = tracker()
            item["sheets"] = [{"csv": "history.csv", "sheet_name": "History", "generated": "git-history"}]
            with patch.object(
                sync,
                "git_history_entries",
                return_value=(GitCommit("abc1234", "2026-09-11", "Mland Team", "Refine tracker"),),
            ):
                values = sync.tracker_sheet_values(root, item, item["sheets"][0])
        self.assertEqual(values[3], ["#", "Commit", "Date", "Author", "Change Description"])
        self.assertEqual(values[4], ["GIT-001", "abc1234", "2026-09-11", "Mland Team", "Refine tracker"])

    def test_rejects_placeholder_and_invalid_drive_ids(self) -> None:
        with self.assertRaisesRegex(sync.SyncError, "placeholder"):
            sync.validate_drive_id("report", "DRIVE_FILE_ID_REPORT_1")
        with self.assertRaisesRegex(sync.SyncError, "invalid"):
            sync.validate_drive_id("report", "not an id")

    def test_uploads_prebuilt_docx_without_legacy_markdown_source(self) -> None:
        drive = MagicMock()
        drive.files().update().execute.return_value = {"id": "1abcdefghijk"}
        with tempfile.TemporaryDirectory() as temp:
            docx = Path(temp) / "report.docx"
            docx.write_bytes(b"docx")
            with patch.object(sync, "MediaFileUpload", return_value="media") as media_upload:
                drive.reset_mock()
                sync.sync_document(drive, document(), docx)
        media_upload.assert_called_once_with(str(docx), mimetype=sync.DOCX_MIME, resumable=False)
        drive.files().update.assert_called_once_with(
            fileId="1abcdefghijk",
            media_body="media",
            supportsAllDrives=True,
            fields="id,name,mimeType",
        )

    def test_remote_preflight_stops_before_writes_when_sheet_tab_is_missing(self) -> None:
        drive = MagicMock()
        drive.files().get().execute.return_value = {"mimeType": sync.GOOGLE_SHEET_MIME}
        sheets_api = MagicMock()
        sheets_api.spreadsheets().get().execute.return_value = {"sheets": [{"properties": {"title": "Other"}}]}
        with self.assertRaisesRegex(sync.SyncError, "missing mapped sheet tabs"):
            sync.validate_remote_targets(drive, sheets_api, [], [tracker()])
        sheets_api.spreadsheets().values().clear.assert_not_called()

    def test_remote_preflight_rejects_wrong_document_type(self) -> None:
        drive = MagicMock()
        drive.files().get().execute.return_value = {"mimeType": sync.GOOGLE_SHEET_MIME}
        with self.assertRaisesRegex(sync.SyncError, "must be"):
            sync.validate_remote_targets(drive, MagicMock(), [document()], [])


if __name__ == "__main__":
    unittest.main()
