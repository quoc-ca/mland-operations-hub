from __future__ import annotations

import importlib.util
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

from tools.git_history import GitCommit


ROOT = Path(__file__).resolve().parents[1]
SCRIPT_PATH = ROOT.parent / ".github" / "scripts" / "sync_to_drive.py"
WORKFLOW_PATH = ROOT.parent / ".github" / "workflows" / "sync-to-drive.yml"
SPEC = importlib.util.spec_from_file_location("sync_to_drive", SCRIPT_PATH)
assert SPEC and SPEC.loader
sync = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(sync)


def document(identifier: str = "report-1") -> dict[str, str]:
    return {"id": identifier, "target": "google-doc", "source_bundle": f"docs/{identifier}", "drive_file_id": "1abcdefghijk"}


def tracker(identifier: str = "tracker-1") -> dict[str, object]:
    return {"id": identifier, "target": "google-sheet", "source_folder": f"trackers/{identifier}", "drive_file_id": "1abcdefghijk"}


def remote_sheet(sheet_id: int, title: str, index: int = 0) -> dict[str, object]:
    return {"sheetId": sheet_id, "title": title, "index": index}


class FakeHttpError(Exception):
    def __init__(self, status: int, reason: str, message: str = "Google API error") -> None:
        super().__init__(message)
        self.resp = type("Response", (), {"status": status})()
        self.content = json.dumps({"error": {"message": message, "errors": [{"reason": reason}]}}).encode("utf-8")


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


class TrackerDiscoveryTests(unittest.TestCase):
    def test_discovers_direct_csvs_and_generated_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            tracker_root = root / "trackers" / "tracker-1"
            tracker_root.mkdir(parents=True)
            (tracker_root / "Risks.csv").write_text("Risk\n", encoding="utf-8")
            (tracker_root / "History.csv").write_text("History\n", encoding="utf-8")
            (tracker_root / "nested").mkdir()
            (tracker_root / "nested" / "Ignored.csv").write_text("Ignored\n", encoding="utf-8")
            item = tracker()
            item["generated_sheets"] = [{"csv": "History.csv", "generated": "git-history"}]
            sheets = sync.tracker_sheets(root, item)
        self.assertEqual(sheets, [{"csv": "History.csv", "sheet_name": "History", "generated": "git-history"}, {"csv": "Risks.csv", "sheet_name": "Risks"}])

    def test_accepts_an_empty_csv_as_a_tab_source(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            tracker_root = root / "trackers" / "tracker-1"
            tracker_root.mkdir(parents=True)
            (tracker_root / "test.csv").touch()
            item = tracker()
            sheets = sync.tracker_sheets(root, item)
            values = sync.tracker_sheet_values(root, item, sheets[0])
            requests = sync.reconciliation_requests(item, sheets, [])
        self.assertEqual(sheets, [{"csv": "test.csv", "sheet_name": "test"}])
        self.assertEqual(values, [])
        self.assertEqual(requests, [{"addSheet": {"properties": {"title": "test"}}}])

    def test_rejects_reserved_archive_suffix_and_invalid_titles(self) -> None:
        with self.assertRaisesRegex(sync.SyncError, "reserves"):
            sync.validate_sheet_title("tracker-1", "Old-out.csv", "Old-out")
        with self.assertRaisesRegex(sync.SyncError, "invalid Google Sheet title"):
            sync.validate_sheet_title("tracker-1", "bad[title].csv", "bad[title]")

    def test_rejects_generated_metadata_for_missing_csv(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "trackers" / "tracker-1").mkdir(parents=True)
            item = tracker()
            item["generated_sheets"] = [{"csv": "History.csv", "generated": "git-history"}]
            with self.assertRaisesRegex(sync.SyncError, "missing generated CSV"):
                sync.tracker_sheets(root, item)


class ReconciliationTests(unittest.TestCase):
    def test_creates_missing_and_archives_extra_even_when_counts_match(self) -> None:
        desired = [{"csv": "Risks.csv", "sheet_name": "Risks"}, {"csv": "Defects.csv", "sheet_name": "Defects"}]
        requests = sync.reconciliation_requests(tracker(), desired, [remote_sheet(1, "Risks"), remote_sheet(2, "Notes")])
        self.assertEqual(requests, [{"updateSheetProperties": {"properties": {"sheetId": 2, "title": "Notes-out"}, "fields": "title"}}, {"addSheet": {"properties": {"title": "Defects"}}}])

    def test_leaves_archived_sheets_unchanged(self) -> None:
        requests = sync.reconciliation_requests(tracker(), [{"csv": "Risks.csv", "sheet_name": "Risks"}], [remote_sheet(1, "Notes-out")])
        self.assertEqual(requests, [{"addSheet": {"properties": {"title": "Risks"}}}])

    def test_stops_on_archive_name_collision(self) -> None:
        with self.assertRaisesRegex(sync.SyncError, "already exists"):
            sync.reconciliation_requests(tracker(), [], [remote_sheet(1, "Notes"), remote_sheet(2, "Notes-out")])

    def test_normalizes_existing_title_case_without_archiving(self) -> None:
        requests = sync.reconciliation_requests(tracker(), [{"csv": "Risks.csv", "sheet_name": "Risks"}], [remote_sheet(1, "RISKS")])
        self.assertEqual(requests[0]["updateSheetProperties"]["properties"]["title"], "Risks")

    def test_reconcile_sends_a_single_structural_batch(self) -> None:
        sheets_api = MagicMock()
        sheets_resource = sheets_api.spreadsheets.return_value
        sheets_resource.batchUpdate.return_value.execute.return_value = {}
        sync.reconcile_tracker(sheets_api, tracker(), [{"csv": "Risks.csv", "sheet_name": "Risks"}], [remote_sheet(1, "Notes")])
        sheets_resource.batchUpdate.assert_called_once_with(
            spreadsheetId="1abcdefghijk",
            body={"requests": [{"updateSheetProperties": {"properties": {"sheetId": 1, "title": "Notes-out"}, "fields": "title"}}, {"addSheet": {"properties": {"title": "Risks"}}}]},
        )


class SyncRemoteTests(unittest.TestCase):
    def test_generated_tracker_history_uses_git_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            tracker_root = root / "trackers" / "tracker-1"
            tracker_root.mkdir(parents=True)
            (tracker_root / "history.csv").write_text("Mland Operations Hub,,,,\nAutomatically generated from Git commit history; do not edit manually.,,,,\n,,,,\n#,Commit,Date,Author,Change Description\n", encoding="utf-8")
            sheet = {"csv": "history.csv", "sheet_name": "history", "generated": "git-history"}
            with patch.object(sync, "git_history_entries", return_value=(GitCommit("abc1234", "2026-09-11", "Mland Team", "Refine tracker"),)):
                values = sync.tracker_sheet_values(root, tracker(), sheet)
        self.assertEqual(values[4], ["GIT-001", "abc1234", "2026-09-11", "Mland Team", "Refine tracker"])

    def test_generated_github_issue_snapshot_uses_generated_csv(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            tracker_root = root / "trackers" / "tracker-1"
            tracker_root.mkdir(parents=True)
            (tracker_root / "issues.csv").write_text("Mland Operations Hub,,,,,,,,,,,,,\nAutomatically generated from GitHub Issues on develop document pushes; do not edit manually.,,,,,,,,,,,,,\n,,,,,,,,,,,,,\nGitHub #,Work Item ID,Title,Type,Domain,Priority,Assignee(s),State,Labels,Created At,Updated At,Closed At,Source Fragment,URL\n", encoding="utf-8")
            sheet = {"csv": "issues.csv", "sheet_name": "issues", "generated": "github-issues"}
            github = MagicMock()
            github.all_issues.return_value = [{"number": 7, "title": "Task", "state": "open", "labels": [], "assignees": [], "html_url": "https://example/7"}]
            values = sync.tracker_sheet_values(root, tracker(), sheet, github)
        self.assertEqual(values[4][0:3], ["7", "", "Task"])

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


class DriveAssetTests(unittest.TestCase):
    def drive_with_assets(self, files: list[dict[str, object]], payload: bytes = b"image") -> MagicMock:
        drive = MagicMock()
        drive.files().list().execute.return_value = {"files": files}
        drive.files().get_media().execute.return_value = payload
        return drive

    def test_downloads_unique_png_by_casefolded_basename(self) -> None:
        drive = self.drive_with_assets([
            {"id": "asset-id", "name": "Ring-Hero.PNG", "mimeType": "image/png", "capabilities": {"canDownload": True}},
        ], b"png")
        drive.reset_mock()
        with tempfile.TemporaryDirectory() as temp:
            assets = sync.download_drive_assets(drive, "1assetsfolder", ("ring-hero",), Path(temp))
            payload = assets["ring-hero"].read_bytes()
        self.assertEqual(payload, b"png")
        drive.files.return_value.get_media.assert_called_once_with(fileId="asset-id", supportsAllDrives=True)

    def test_rejects_missing_duplicate_and_unsupported_assets(self) -> None:
        cases = (
            ([], "not found"),
            ([
                {"id": "a", "name": "ring-hero.png", "mimeType": "image/png", "capabilities": {"canDownload": True}},
                {"id": "b", "name": "ring-hero.jpg", "mimeType": "image/jpeg", "capabilities": {"canDownload": True}},
            ], "duplicate"),
            ([{"id": "a", "name": "ring-hero.svg", "mimeType": "image/svg+xml", "capabilities": {"canDownload": True}}], "PNG or JPEG"),
        )
        for files, message in cases:
            with self.subTest(message=message), tempfile.TemporaryDirectory() as temp:
                with self.assertRaisesRegex(sync.SyncError, message):
                    sync.download_drive_assets(self.drive_with_assets(files), "1assetsfolder", ("ring-hero",), Path(temp))

    def test_missing_assets_secret_fails_only_document_target(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "templates").mkdir()
            (root / "templates" / "reference.docx").write_bytes(b"reference")
            (root / "docs" / "report-1").mkdir(parents=True)
            manifest = {"reference_doc": "templates/reference.docx"}
            with patch.object(sync, "document_drive_image_placeholders", return_value=("ring-hero",)), patch.dict(os.environ, {}, clear=True):
                result = sync.process_document(root, manifest, document(), MagicMock(), root / "build", False)
        self.assertEqual(result["status"], "failed")
        self.assertEqual(result["phase"], "assets")
        self.assertEqual(result["error_code"], "DRIVE_ASSET_FAILED")

    def test_remote_preflight_plans_missing_tab_creation_without_writes(self) -> None:
        drive = MagicMock()
        drive.files().get().execute.return_value = {"mimeType": sync.GOOGLE_SHEET_MIME}
        sheets_api = MagicMock()
        sheets_api.spreadsheets().get().execute.return_value = {"sheets": [{"properties": {"sheetId": 1, "title": "Other", "index": 0}}]}
        result = sync.validate_remote_targets(drive, sheets_api, [], [tracker()], {"tracker-1": [{"csv": "Items.csv", "sheet_name": "Items"}]})
        self.assertEqual(result["tracker-1"], [remote_sheet(1, "Other")])
        sheets_api.spreadsheets().batchUpdate.assert_not_called()
        sheets_api.spreadsheets().values().clear.assert_not_called()

    def test_remote_preflight_rejects_wrong_document_type(self) -> None:
        drive = MagicMock()
        drive.files().get().execute.return_value = {"mimeType": sync.GOOGLE_SHEET_MIME}
        with self.assertRaisesRegex(sync.SyncError, "must be"):
            sync.validate_remote_targets(drive, MagicMock(), [document()], [], {})


class BestEffortSyncTests(unittest.TestCase):
    def test_source_failure_does_not_call_remote_apis_for_that_tracker(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "trackers" / "broken"
            source.mkdir(parents=True)
            (source / "Old-out.csv").touch()
            item = tracker("broken")
            drive = MagicMock()
            sheets_api = MagicMock()
            result = sync.process_tracker(root, item, drive, sheets_api, "", "", False)
        self.assertEqual(result["status"], "failed")
        self.assertEqual(result["phase"], "local")
        self.assertEqual(result["error_code"], "SOURCE_INVALID")
        drive.files().get.assert_not_called()
        sheets_api.spreadsheets().get.assert_not_called()

    def test_valid_tracker_can_succeed_after_another_tracker_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            broken_root = root / "trackers" / "broken"
            good_root = root / "trackers" / "good"
            broken_root.mkdir(parents=True)
            good_root.mkdir(parents=True)
            (broken_root / "Old-out.csv").touch()
            (good_root / "Items.csv").write_text("value\n", encoding="utf-8")
            drive = MagicMock()
            drive.files().get().execute.return_value = {"mimeType": sync.GOOGLE_SHEET_MIME}
            sheets_api = MagicMock()
            sheets_api.spreadsheets().get().execute.return_value = {"sheets": []}
            sheets_api.spreadsheets().batchUpdate().execute.return_value = {}
            sheets_api.spreadsheets().values().clear().execute.return_value = {}
            sheets_api.spreadsheets().values().batchUpdate().execute.return_value = {"totalUpdatedCells": 1}
            failed = sync.process_tracker(root, tracker("broken"), drive, sheets_api, "", "", False)
            succeeded = sync.process_tracker(root, tracker("good"), drive, sheets_api, "", "", False)
        self.assertEqual(failed["status"], "failed")
        self.assertEqual(succeeded["status"], "success")
        self.assertEqual(succeeded["updated_cells"], 1)

    def test_retries_transient_remote_error_three_times(self) -> None:
        attempts = 0

        def fail() -> None:
            nonlocal attempts
            attempts += 1
            raise FakeHttpError(429, "rateLimitExceeded")

        with patch.object(sync.time, "sleep") as sleep:
            with self.assertRaises(sync.TargetError) as raised:
                sync.run_remote("write", fail)
        self.assertEqual(attempts, 3)
        self.assertEqual(raised.exception.phase, "write")
        self.assertEqual(sleep.call_count, 2)

    def test_does_not_retry_non_transient_remote_error(self) -> None:
        attempts = 0

        def fail() -> None:
            nonlocal attempts
            attempts += 1
            raise FakeHttpError(403, "forbidden")

        with patch.object(sync.time, "sleep") as sleep:
            with self.assertRaises(sync.TargetError):
                sync.run_remote("upload", fail)
        self.assertEqual(attempts, 1)
        sleep.assert_not_called()

    def test_writes_machine_report_and_markdown_summary(self) -> None:
        results = [
            sync.success_result("tracker", "good", "trackers/good", "write", updated_cells=3),
            sync.failed_result("tracker", "broken", "trackers/broken", sync.TargetError("local", sync.SyncError("invalid title"))),
        ]
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            report = root / "results.json"
            summary = root / "summary.md"
            sync.write_result_report(str(report), results)
            code = sync.render_result_summary(report, summary)
            report_data = json.loads(report.read_text(encoding="utf-8"))
            summary_text = summary.read_text(encoding="utf-8")
        self.assertEqual(code, 0)
        self.assertEqual(report_data["summary"], {"success": 1, "failed": 1})
        self.assertEqual(
            set(report_data["results"][0]),
            {"target_type", "target_id", "source", "status", "phase", "error_code", "retryable", "message", "hint", "updated_cells"},
        )
        self.assertIn("tracker: broken", summary_text)
        self.assertIn("invalid title", summary_text)


class WorkflowContractTests(unittest.TestCase):
    def test_workspace_publishing_is_restricted_to_develop(self) -> None:
        workflow = WORKFLOW_PATH.read_text(encoding="utf-8")
        self.assertIn("branches: [develop]", workflow)
        self.assertNotIn("branches: [main]", workflow)
        self.assertIn('"$GITHUB_REF" != "refs/heads/develop"', workflow)

    def test_workflow_writes_report_and_always_renders_summary(self) -> None:
        workflow = WORKFLOW_PATH.read_text(encoding="utf-8")
        self.assertIn("--result-report sync-results.json", workflow)
        self.assertIn("Publish synchronization summary", workflow)
        self.assertIn("if: always()", workflow)

    def test_workflow_passes_drive_assets_folder_secret(self) -> None:
        workflow = WORKFLOW_PATH.read_text(encoding="utf-8")
        self.assertIn("GDRIVE_ASSETS_FOLDER_ID: ${{ secrets.GDRIVE_ASSETS_FOLDER_ID }}", workflow)


if __name__ == "__main__":
    unittest.main()
