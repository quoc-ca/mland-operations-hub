#!/usr/bin/env python3
"""Build document bundles and synchronize them, plus CSV trackers, to Google Workspace."""

from __future__ import annotations

import argparse
import csv
import os
import re
import sys
import tempfile
from pathlib import Path
from typing import Any

try:  # The workflow installs PyYAML; local selection tests do not require it.
    import yaml
except ImportError:  # pragma: no cover - exercised only without requirements installed.
    yaml = None

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
DOCUMENTS_ROOT = REPOSITORY_ROOT / "documents"
if str(DOCUMENTS_ROOT) not in sys.path:
    sys.path.insert(0, str(DOCUMENTS_ROOT))

from tools.document_generator import GenerationError, build_bundle
from tools.git_history import GitHistoryError, git_history_entries
from tools.github_issues import GitHubApi, WorkItemError, issue_snapshot_rows

try:  # Selection and validation tests do not need Google packages installed.
    from google.oauth2.service_account import Credentials
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaFileUpload
except ImportError:  # pragma: no cover - GitHub Actions installs requirements.txt.
    Credentials = None
    build = None
    MediaFileUpload = None


DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"
DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
GOOGLE_DOC_MIME = "application/vnd.google-apps.document"
GOOGLE_SHEET_MIME = "application/vnd.google-apps.spreadsheet"
GLOBAL_DOCUMENT_INPUTS = {"manifest.yml", "templates/reference.docx", "requirements.txt"}
GLOBAL_DOCUMENT_PREFIXES = ("tools/", ".github/scripts/", ".github/workflows/")
GLOBAL_TRACKER_INPUTS = {"manifest.yml"}
DRIVE_ID_PATTERN = re.compile(r"^[A-Za-z0-9_-]{10,}$")
ARCHIVE_SUFFIX = "-out"
INVALID_SHEET_TITLE = re.compile(r"[\\\\/:?*\[\]]")
MAX_SHEET_TITLE_LENGTH = 100


class SyncError(RuntimeError):
    """A configuration, build, or remote-state error that must stop the sync."""


def repo_path(root: Path, value: str) -> Path:
    path = (root / value).resolve()
    try:
        path.relative_to(root)
    except ValueError as exc:
        raise SyncError(f"Path outside repository is not allowed: {value}") from exc
    return path


def load_manifest(path: Path) -> dict[str, Any]:
    if yaml is None:
        raise SyncError("PyYAML is missing; install requirements.txt.")
    try:
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
    except OSError as exc:
        raise SyncError(f"Cannot read manifest: {path}") from exc
    except yaml.YAMLError as exc:
        raise SyncError(f"Invalid YAML in manifest: {exc}") from exc
    if not isinstance(data, dict):
        raise SyncError("Manifest root must be a mapping.")
    if not isinstance(data.get("reference_doc"), str):
        raise SyncError("Manifest must define a repository-level reference_doc.")
    if not isinstance(data.get("documents"), list) or not isinstance(data.get("trackers"), list):
        raise SyncError("Manifest must define documents and trackers lists.")
    return data


def validate_drive_id(label: str, drive_file_id: Any) -> str:
    if not isinstance(drive_file_id, str) or not DRIVE_ID_PATTERN.fullmatch(drive_file_id):
        raise SyncError(f"{label} has an invalid Drive file ID: {drive_file_id!r}")
    if drive_file_id.startswith("DRIVE_FILE_ID_"):
        raise SyncError(f"{label} still uses a placeholder Drive file ID.")
    return drive_file_id


def require_entry(entry: Any, expected_target: str) -> dict[str, Any]:
    if not isinstance(entry, dict):
        raise SyncError("Each manifest entry must be a mapping.")
    if not isinstance(entry.get("id"), str) or not entry["id"]:
        raise SyncError("Every manifest entry needs a non-empty id.")
    if entry.get("target") != expected_target:
        raise SyncError(f"{entry['id']} must declare target: {expected_target}.")
    if not isinstance(entry.get("drive_file_id"), str):
        raise SyncError(f"{entry['id']} must declare drive_file_id as a string.")
    return entry


def changed_paths(value: str) -> set[str]:
    return {line.strip().replace("\\", "/") for line in value.splitlines() if line.strip()}


def _global_document_change(changed: set[str]) -> bool:
    return bool(changed & GLOBAL_DOCUMENT_INPUTS) or any(path.startswith(GLOBAL_DOCUMENT_PREFIXES) for path in changed)


def _bundle_changed(source_bundle: str, changed: set[str]) -> bool:
    bundle = source_bundle.rstrip("/")
    return any(path == bundle or path.startswith(bundle + "/") for path in changed)


def select_entries(manifest: dict[str, Any], changed: set[str], sync_all: bool) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    documents = [require_entry(item, "google-doc") for item in manifest["documents"]]
    trackers = [require_entry(item, "google-sheet") for item in manifest["trackers"]]
    if sync_all:
        return documents, trackers
    if _global_document_change(changed):
        selected_documents = documents
    else:
        selected_documents = []
        for item in documents:
            source_bundle = item.get("source_bundle")
            if not isinstance(source_bundle, str) or not source_bundle:
                raise SyncError(f"{item['id']} must declare a source_bundle directory.")
            if _bundle_changed(source_bundle, changed):
                selected_documents.append(item)
    selected_trackers = trackers if changed & GLOBAL_TRACKER_INPUTS else [
        item for item in trackers
        if any(path.startswith(item["source_folder"].rstrip("/") + "/") for path in changed)
    ]
    return selected_documents, selected_trackers


def a1_sheet_name(sheet_name: str) -> str:
    return "'" + sheet_name.replace("'", "''") + "'"


def read_csv(path: Path) -> list[list[str]]:
    try:
        with path.open("r", encoding="utf-8-sig", newline="") as source:
            return list(csv.reader(source))
    except OSError as exc:
        raise SyncError(f"Cannot read CSV: {path}") from exc


def generated_git_history_values(root: Path, tracker: dict[str, Any], sheet: dict[str, Any]) -> list[list[str]]:
    csv_path = repo_path(root, f"{tracker['source_folder'].rstrip('/')}/{sheet['csv']}")
    template = read_csv(csv_path)
    if len(template) < 4 or not template[3]:
        raise SyncError(f"{tracker['id']} / {sheet['sheet_name']} must provide a four-row Git history template.")
    width = len(template[3])
    if any(len(row) != width for row in template[:4]):
        raise SyncError(f"{tracker['id']} / {sheet['sheet_name']} has an invalid Git history template shape.")
    try:
        entries = git_history_entries(root, repo_path(root, tracker["source_folder"]))
    except GitHistoryError as exc:
        raise SyncError(f"Cannot generate Git history for {tracker['id']}: {exc}") from exc

    values = template[:4]
    for index, entry in enumerate(entries, start=1):
        row = [f"GIT-{index:03d}", entry.short_sha, entry.date, entry.author, entry.subject]
        if len(row) != width:
            raise SyncError(f"{tracker['id']} / {sheet['sheet_name']} Git history header must have five columns.")
        values.append(row)
    return values


def generated_github_issue_values(
    root: Path, tracker: dict[str, Any], sheet: dict[str, Any], github_api: GitHubApi | None
) -> list[list[str]]:
    if github_api is None:
        raise SyncError("GitHub credentials are required to generate the IssuesOnGithub tracker snapshot.")
    csv_path = repo_path(root, f"{tracker['source_folder'].rstrip('/')}/{sheet['csv']}")
    template = read_csv(csv_path)
    if len(template) < 4 or not template[3]:
        raise SyncError(f"{tracker['id']} / {sheet['sheet_name']} must provide a four-row GitHub Issue template.")
    width = len(template[3])
    if width != 14 or any(len(row) != width for row in template[:4]):
        raise SyncError(f"{tracker['id']} / {sheet['sheet_name']} GitHub Issue template must have fourteen columns.")
    try:
        rows = issue_snapshot_rows(github_api.all_issues())
    except WorkItemError as exc:
        raise SyncError(f"Cannot generate GitHub Issue snapshot: {exc}") from exc
    if any(len(row) != width for row in rows):  # Defensive contract check for future schema changes.
        raise SyncError(f"{tracker['id']} / {sheet['sheet_name']} GitHub Issue snapshot has an invalid row shape.")
    return template[:4] + rows


def tracker_sheet_values(
    root: Path, tracker: dict[str, Any], sheet: dict[str, Any], github_api: GitHubApi | None = None
) -> list[list[str]]:
    generated = sheet.get("generated")
    if generated is None:
        return read_csv(repo_path(root, f"{tracker['source_folder'].rstrip('/')}/{sheet['csv']}"))
    if generated == "git-history":
        return generated_git_history_values(root, tracker, sheet)
    if generated == "github-issues":
        return generated_github_issue_values(root, tracker, sheet, github_api)
    raise SyncError(f"{tracker['id']} / {sheet['sheet_name']} has unsupported generated value: {generated!r}")


def validate_sheet_title(tracker_id: str, csv_name: str, sheet_name: str) -> None:
    if not sheet_name or len(sheet_name) > MAX_SHEET_TITLE_LENGTH or INVALID_SHEET_TITLE.search(sheet_name):
        raise SyncError(f"{tracker_id} / {csv_name} has an invalid Google Sheet title: {sheet_name!r}")
    if sheet_name.casefold().endswith(ARCHIVE_SUFFIX):
        raise SyncError(f"{tracker_id} / {csv_name} reserves the {ARCHIVE_SUFFIX!r} suffix for archived sheets.")


def generated_sheet_metadata(tracker: dict[str, Any]) -> dict[str, dict[str, str]]:
    configured = tracker.get("generated_sheets", [])
    if not isinstance(configured, list):
        raise SyncError(f"{tracker['id']} generated_sheets must be a list.")
    metadata: dict[str, dict[str, str]] = {}
    for item in configured:
        if not isinstance(item, dict) or not isinstance(item.get("csv"), str):
            raise SyncError(f"{tracker['id']} has invalid generated sheet metadata.")
        csv_name = item["csv"]
        if Path(csv_name).name != csv_name or Path(csv_name).suffix.lower() != ".csv":
            raise SyncError(f"{tracker['id']} has invalid generated CSV name: {csv_name!r}")
        generated = item.get("generated")
        if generated not in {"git-history", "github-issues"}:
            raise SyncError(f"{tracker['id']} / {csv_name} has unsupported generated value: {generated!r}")
        key = csv_name.casefold()
        if key in metadata:
            raise SyncError(f"{tracker['id']} configures generated CSV {csv_name!r} more than once.")
        metadata[key] = {"generated": generated}
    return metadata


def tracker_sheets(root: Path, tracker: dict[str, Any]) -> list[dict[str, str]]:
    source_folder = tracker.get("source_folder")
    if not isinstance(source_folder, str):
        raise SyncError(f"{tracker['id']} has a missing tracker source_folder.")
    source_path = repo_path(root, source_folder)
    if not source_path.is_dir():
        raise SyncError(f"{tracker['id']} has a missing tracker source_folder.")
    metadata = generated_sheet_metadata(tracker)
    sheets: list[dict[str, str]] = []
    seen_titles: set[str] = set()
    found_metadata: set[str] = set()
    for csv_path in sorted((path for path in source_path.glob("*.csv") if path.is_file()), key=lambda path: path.name.casefold()):
        csv_name = csv_path.name
        sheet_name = csv_path.stem
        validate_sheet_title(tracker["id"], csv_name, sheet_name)
        key = sheet_name.casefold()
        if key in seen_titles:
            raise SyncError(f"{tracker['id']} has CSV files with duplicate sheet titles: {sheet_name!r}")
        seen_titles.add(key)
        sheet: dict[str, str] = {"csv": csv_name, "sheet_name": sheet_name}
        generated = metadata.get(csv_name.casefold())
        if generated is not None:
            sheet.update(generated)
            found_metadata.add(csv_name.casefold())
        sheets.append(sheet)
    missing_generated = set(metadata) - found_metadata
    if missing_generated:
        names = ", ".join(sorted(missing_generated))
        raise SyncError(f"{tracker['id']} configures missing generated CSV files: {names}")
    return sheets


def validate_local_inputs(
    root: Path, manifest: dict[str, Any], documents: list[dict[str, Any]], trackers: list[dict[str, Any]]
) -> dict[str, list[dict[str, str]]]:
    reference_doc = repo_path(root, manifest["reference_doc"])
    if documents and not reference_doc.is_file():
        raise SyncError(f"Shared reference DOCX is missing: {reference_doc}")
    for document in documents:
        source_bundle = document.get("source_bundle")
        if not isinstance(source_bundle, str) or not source_bundle:
            raise SyncError(f"{document['id']} must declare a source_bundle directory.")
        if not repo_path(root, source_bundle).is_dir():
            raise SyncError(f"Document bundle is missing: {source_bundle}")
    return {tracker["id"]: tracker_sheets(root, tracker) for tracker in trackers}


def build_documents(root: Path, documents: list[dict[str, Any]], output_dir: Path) -> dict[str, Path]:
    outputs: dict[str, Path] = {}
    for document in documents:
        try:
            outputs[document["id"]] = build_bundle(repo_path(root, document["source_bundle"]), root, output_dir)
        except GenerationError as exc:
            raise SyncError(exc.diagnostic.format()) from exc
    return outputs


def drive_file(drive: Any, label: str, drive_file_id: str, expected_mime: str) -> dict[str, str]:
    drive_file_id = validate_drive_id(label, drive_file_id)
    try:
        file_data = drive.files().get(fileId=drive_file_id, fields="id,name,mimeType", supportsAllDrives=True).execute()
    except Exception as exc:
        raise SyncError(f"Cannot access {label} ({drive_file_id}): {exc}") from exc
    if file_data.get("mimeType") != expected_mime:
        raise SyncError(f"{label} ({drive_file_id}) must be {expected_mime}, not {file_data.get('mimeType')!r}.")
    return file_data


def remote_tracker_sheets(sheets_api: Any, tracker: dict[str, Any]) -> list[dict[str, Any]]:
    try:
        spreadsheet = sheets_api.spreadsheets().get(
            spreadsheetId=tracker["drive_file_id"], fields="sheets(properties(sheetId,title,index))"
        ).execute()
    except Exception as exc:
        raise SyncError(f"Cannot inspect sheets in {tracker['id']} ({tracker['drive_file_id']}): {exc}") from exc
    sheets: list[dict[str, Any]] = []
    for item in spreadsheet.get("sheets", []):
        properties = item.get("properties", {})
        sheet_id = properties.get("sheetId")
        title = properties.get("title")
        if not isinstance(sheet_id, int) or not isinstance(title, str):
            raise SyncError(f"{tracker['id']} returned invalid sheet properties.")
        sheets.append({"sheetId": sheet_id, "title": title, "index": properties.get("index")})
    return sheets


def reconciliation_requests(
    tracker: dict[str, Any], desired_sheets: list[dict[str, str]], remote_sheets: list[dict[str, Any]]
) -> list[dict[str, Any]]:
    desired_by_key = {sheet["sheet_name"].casefold(): sheet["sheet_name"] for sheet in desired_sheets}
    active_by_key: dict[str, dict[str, Any]] = {}
    remote_titles = {sheet["title"].casefold() for sheet in remote_sheets}
    for sheet in remote_sheets:
        if sheet["title"].casefold().endswith(ARCHIVE_SUFFIX):
            continue
        key = sheet["title"].casefold()
        if key in active_by_key:
            raise SyncError(f"{tracker['id']} has duplicate active sheet titles differing only by case.")
        active_by_key[key] = sheet

    requests: list[dict[str, Any]] = []
    archive_titles: set[str] = set()
    for key, sheet in active_by_key.items():
        if key in desired_by_key:
            desired_title = desired_by_key[key]
            if sheet["title"] != desired_title:
                requests.append(
                    {
                        "updateSheetProperties": {
                            "properties": {"sheetId": sheet["sheetId"], "title": desired_title},
                            "fields": "title",
                        }
                    }
                )
            continue
        archive_title = f"{sheet['title']}{ARCHIVE_SUFFIX}"
        archive_key = archive_title.casefold()
        if len(archive_title) > MAX_SHEET_TITLE_LENGTH or INVALID_SHEET_TITLE.search(archive_title):
            raise SyncError(f"{tracker['id']} cannot archive sheet {sheet['title']!r} as {archive_title!r}.")
        if archive_key in remote_titles or archive_key in archive_titles:
            raise SyncError(f"{tracker['id']} cannot archive {sheet['title']!r}: {archive_title!r} already exists.")
        archive_titles.add(archive_key)
        requests.append(
            {
                "updateSheetProperties": {
                    "properties": {"sheetId": sheet["sheetId"], "title": archive_title},
                    "fields": "title",
                }
            }
        )
    for key, title in desired_by_key.items():
        if key not in active_by_key:
            requests.append({"addSheet": {"properties": {"title": title}}})
    return requests


def validate_remote_targets(
    drive: Any,
    sheets_api: Any,
    documents: list[dict[str, Any]],
    trackers: list[dict[str, Any]],
    tracker_sheets_by_id: dict[str, list[dict[str, str]]],
) -> dict[str, list[dict[str, Any]]]:
    """Validate every selected target and reconciliation plan before any remote write occurs."""
    for document in documents:
        drive_file(drive, document["id"], document["drive_file_id"], GOOGLE_DOC_MIME)
    remote_sheets_by_id: dict[str, list[dict[str, Any]]] = {}
    for tracker in trackers:
        drive_file(drive, tracker["id"], tracker["drive_file_id"], GOOGLE_SHEET_MIME)
        remote_sheets = remote_tracker_sheets(sheets_api, tracker)
        reconciliation_requests(tracker, tracker_sheets_by_id[tracker["id"]], remote_sheets)
        remote_sheets_by_id[tracker["id"]] = remote_sheets
    return remote_sheets_by_id


def reconcile_tracker(
    sheets_api: Any, tracker: dict[str, Any], desired_sheets: list[dict[str, str]], remote_sheets: list[dict[str, Any]]
) -> None:
    requests = reconciliation_requests(tracker, desired_sheets, remote_sheets)
    if not requests:
        return
    try:
        sheets_api.spreadsheets().batchUpdate(
            spreadsheetId=tracker["drive_file_id"], body={"requests": requests}
        ).execute()
    except Exception as exc:
        raise SyncError(f"Failed to reconcile sheets in {tracker['id']}: {exc}") from exc


def sync_document(drive: Any, document: dict[str, Any], docx: Path) -> None:
    if MediaFileUpload is None:
        raise SyncError("Google dependencies are missing; install requirements.txt.")
    media = MediaFileUpload(str(docx), mimetype=DOCX_MIME, resumable=False)
    try:
        drive.files().update(fileId=document["drive_file_id"], media_body=media, supportsAllDrives=True, fields="id,name,mimeType").execute()
    except Exception as exc:
        raise SyncError(f"Failed to replace Google Doc {document['id']} ({document['drive_file_id']}): {exc}") from exc
    print(f"[DOC] {docx.name} -> {document['drive_file_id']}")


def prepare_tracker_values(
    root: Path,
    trackers: list[dict[str, Any]],
    tracker_sheets_by_id: dict[str, list[dict[str, str]]],
    github_api: GitHubApi | None = None,
) -> dict[tuple[str, str], list[list[str]]]:
    return {
        (tracker["id"], sheet["csv"]): tracker_sheet_values(root, tracker, sheet, github_api)
        for tracker in trackers
        for sheet in tracker_sheets_by_id[tracker["id"]]
    }


def sync_tracker(
    sheets_api: Any,
    root: Path,
    tracker: dict[str, Any],
    desired_sheets: list[dict[str, str]],
    prepared_values: dict[tuple[str, str], list[list[str]]],
) -> int:
    spreadsheet_id = tracker["drive_file_id"]
    batch_data: list[dict[str, Any]] = []
    for sheet in desired_sheets:
        csv_path = repo_path(root, f"{tracker['source_folder'].rstrip('/')}/{sheet['csv']}")
        sheet_range = a1_sheet_name(sheet["sheet_name"])
        try:
            sheets_api.spreadsheets().values().clear(spreadsheetId=spreadsheet_id, range=sheet_range, body={}).execute()
        except Exception as exc:
            raise SyncError(f"Failed to clear {tracker['id']} / {sheet['sheet_name']}: {exc}") from exc
        values = prepared_values[(tracker["id"], sheet["csv"])]
        if values:
            batch_data.append({"range": f"{sheet_range}!A1", "majorDimension": "ROWS", "values": values})
        print(f"[SHEET] {csv_path} -> {spreadsheet_id} / {sheet['sheet_name']} ({len(values)} rows)")
    if not batch_data:
        return 0
    try:
        result = sheets_api.spreadsheets().values().batchUpdate(spreadsheetId=spreadsheet_id, body={"valueInputOption": "RAW", "data": batch_data}).execute()
    except Exception as exc:
        raise SyncError(f"Failed to write CSV values to {tracker['id']}: {exc}") from exc
    return int(result.get("totalUpdatedCells", 0))


def google_clients(credential_path: Path) -> tuple[Any, Any]:
    if Credentials is None or build is None:
        raise SyncError("Google dependencies are missing; install requirements.txt.")
    credentials = Credentials.from_service_account_file(str(credential_path), scopes=[DRIVE_SCOPE, SHEETS_SCOPE])
    return build("drive", "v3", credentials=credentials, cache_discovery=False), build("sheets", "v4", credentials=credentials, cache_discovery=False)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", default="manifest.yml")
    parser.add_argument("--credentials")
    parser.add_argument("--changed", default="", help="Newline-separated changed repository paths")
    parser.add_argument("--all", action="store_true", help="Synchronize every manifest entry")
    parser.add_argument("--include-generated-trackers", action="store_true", help="Also export generated tracker views.")
    parser.add_argument("--github-repository", default=os.environ.get("GITHUB_REPOSITORY", ""))
    parser.add_argument("--github-token", default=os.environ.get("GITHUB_TOKEN", ""))
    parser.add_argument("--dry-run", action="store_true", help="Validate local selection without building or contacting Google")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = Path.cwd().resolve()
    manifest = load_manifest(repo_path(root, args.manifest))
    documents, trackers = select_entries(manifest, changed_paths(args.changed), args.all)
    if args.include_generated_trackers and not args.all:
        generated_trackers = [
            tracker for tracker in manifest["trackers"]
            if any(sheet.get("generated") == "github-issues" for sheet in tracker.get("generated_sheets", []))
        ]
        known_ids = {tracker["id"] for tracker in trackers}
        trackers.extend(require_entry(tracker, "google-sheet") for tracker in generated_trackers if tracker["id"] not in known_ids)
    if not documents and not trackers:
        print("No mapped document bundles or CSV sources changed; nothing to synchronize.")
        return 0
    tracker_sheets_by_id = validate_local_inputs(root, manifest, documents, trackers)
    if args.dry_run:
        print(f"Dry run: {len(documents)} Google Docs and {len(trackers)} Google Sheets selected.")
        return 0
    if not args.credentials:
        raise SyncError("--credentials is required.")
    credential_path = Path(args.credentials)
    if not credential_path.is_file():
        raise SyncError(f"Credentials file is missing: {credential_path}")
    with tempfile.TemporaryDirectory(prefix="google-doc-build-", dir=root) as temp_dir:
        outputs = build_documents(root, documents, Path(temp_dir))
        needs_github = any(
            sheet.get("generated") == "github-issues"
            for sheets in tracker_sheets_by_id.values()
            for sheet in sheets
        )
        try:
            github_api = GitHubApi(args.github_repository, args.github_token) if needs_github else None
        except WorkItemError as exc:
            raise SyncError(f"Cannot configure GitHub Issue snapshot: {exc}") from exc
        prepared_tracker_values = prepare_tracker_values(root, trackers, tracker_sheets_by_id, github_api)
        drive, sheets_api = google_clients(credential_path)
        remote_sheets_by_id = validate_remote_targets(drive, sheets_api, documents, trackers, tracker_sheets_by_id)
        for document in documents:
            sync_document(drive, document, outputs[document["id"]])
        for tracker in trackers:
            reconcile_tracker(
                sheets_api, tracker, tracker_sheets_by_id[tracker["id"]], remote_sheets_by_id[tracker["id"]]
            )
        updated_cells = sum(
            sync_tracker(sheets_api, root, tracker, tracker_sheets_by_id[tracker["id"]], prepared_tracker_values)
            for tracker in trackers
        )
    print(f"Sync complete: {len(documents)} Google Docs, {len(trackers)} Google Sheets, {updated_cells} sheet cells updated.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SyncError as exc:
        print(f"SYNC ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
