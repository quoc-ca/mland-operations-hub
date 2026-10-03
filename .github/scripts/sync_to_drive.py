#!/usr/bin/env python3
"""Build document bundles and synchronize them, plus CSV trackers, to Google Workspace."""

from __future__ import annotations

import argparse
import csv
import json
import os
import random
import re
import sys
import tempfile
import time
from pathlib import Path
from typing import Any, Callable

try:  # The workflow installs PyYAML; local selection tests do not require it.
    import yaml
except ImportError:  # pragma: no cover - exercised only without requirements installed.
    yaml = None

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
DOCUMENTS_ROOT = REPOSITORY_ROOT / "documents"
if str(DOCUMENTS_ROOT) not in sys.path:
    sys.path.insert(0, str(DOCUMENTS_ROOT))

from tools.document_generator import GenerationError, build_bundle, drive_image_placeholders, load_bundle
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
DRIVE_IMAGE_MIME_EXTENSIONS = {"image/png": ".png", "image/jpeg": ".jpg"}
GLOBAL_DOCUMENT_INPUTS = {"manifest.yml", "templates/reference.docx", "requirements.txt"}
GLOBAL_DOCUMENT_PREFIXES = ("tools/", ".github/scripts/", ".github/workflows/")
GLOBAL_TRACKER_INPUTS = {"manifest.yml"}
DRIVE_ID_PATTERN = re.compile(r"^[A-Za-z0-9_-]{10,}$")
ARCHIVE_SUFFIX = "-out"
INVALID_SHEET_TITLE = re.compile(r"[\\\\/:?*\[\]]")
MAX_SHEET_TITLE_LENGTH = 100
MAX_REMOTE_ATTEMPTS = 3


class SyncError(RuntimeError):
    """A configuration, build, or remote-state error that must stop the sync."""


class TargetError(SyncError):
    """A target-scoped error with the phase that failed."""

    def __init__(self, phase: str, error: Exception):
        super().__init__(str(error))
        self.phase = phase
        self.error = error


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
    identifier = entry.get("id")
    if not isinstance(identifier, str) or not identifier:
        raise SyncError("Every manifest entry needs a non-empty id.")
    if entry.get("target") != expected_target:
        raise SyncError(f"{identifier} must declare target: {expected_target}.")
    if not isinstance(entry.get("drive_file_id"), str):
        raise SyncError(f"{identifier} must declare drive_file_id as a string.")
    return entry


def changed_paths(value: str) -> set[str]:
    return {line.strip().replace("\\", "/") for line in value.splitlines() if line.strip()}


def _global_document_change(changed: set[str]) -> bool:
    return bool(changed & GLOBAL_DOCUMENT_INPUTS) or any(path.startswith(GLOBAL_DOCUMENT_PREFIXES) for path in changed)


def _bundle_changed(source_bundle: str, changed: set[str]) -> bool:
    bundle = source_bundle.rstrip("/")
    return any(path == bundle or path.startswith(bundle + "/") for path in changed)


def select_entries(manifest: dict[str, Any], changed: set[str], sync_all: bool) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    documents = manifest["documents"]
    trackers = manifest["trackers"]
    if sync_all:
        return documents, trackers
    if _global_document_change(changed):
        selected_documents = documents
    else:
        selected_documents = []
        for item in documents:
            source_bundle = item.get("source_bundle") if isinstance(item, dict) else None
            if isinstance(source_bundle, str) and source_bundle and _bundle_changed(source_bundle, changed):
                selected_documents.append(item)
    selected_trackers = trackers if changed & GLOBAL_TRACKER_INPUTS else [
        item for item in trackers
        if isinstance(item, dict)
        and isinstance(item.get("source_folder"), str)
        and any(path.startswith(item["source_folder"].rstrip("/") + "/") for path in changed)
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


def document_drive_image_placeholders(bundle_path: Path) -> tuple[str, ...]:
    return drive_image_placeholders(load_bundle(bundle_path))


def drive_assets_folder_id() -> str:
    folder_id = os.environ.get("GDRIVE_ASSETS_FOLDER_ID", "").strip()
    if not folder_id:
        raise SyncError("GDRIVE_ASSETS_FOLDER_ID is required when a document uses a Drive image placeholder.")
    if not DRIVE_ID_PATTERN.fullmatch(folder_id) or folder_id.startswith("DRIVE_FILE_ID_"):
        raise SyncError("GDRIVE_ASSETS_FOLDER_ID must contain a valid Drive folder ID.")
    return folder_id


def download_drive_assets(drive: Any, folder_id: str, names: tuple[str, ...], destination: Path) -> dict[str, Path]:
    requested = {name.casefold(): name for name in names}
    matches: dict[str, list[dict[str, Any]]] = {key: [] for key in requested}
    page_token: str | None = None
    while True:
        try:
            response = drive.files().list(
                q=f"'{folder_id}' in parents and trashed = false",
                spaces="drive",
                fields="nextPageToken,files(id,name,mimeType,capabilities(canDownload))",
                pageSize=1000,
                pageToken=page_token,
                supportsAllDrives=True,
                includeItemsFromAllDrives=True,
            ).execute()
        except Exception as exc:
            raise SyncError("Cannot list the configured Drive assets folder.") from exc
        for item in response.get("files", []):
            name = item.get("name")
            if not isinstance(name, str):
                continue
            key = Path(name).stem.casefold()
            if key in matches:
                matches[key].append(item)
        page_token = response.get("nextPageToken")
        if not page_token:
            break

    destination.mkdir(parents=True, exist_ok=True)
    downloaded: dict[str, Path] = {}
    for key, placeholder in requested.items():
        candidates = matches[key]
        if not candidates:
            raise SyncError(f"Drive asset {placeholder!r} was not found in the configured folder.")
        if len(candidates) != 1:
            raise SyncError(f"Drive asset {placeholder!r} has duplicate basenames in the configured folder.")
        asset = candidates[0]
        mime_type = asset.get("mimeType")
        extension = DRIVE_IMAGE_MIME_EXTENSIONS.get(mime_type)
        if extension is None:
            raise SyncError(f"Drive asset {placeholder!r} must be a PNG or JPEG file.")
        if asset.get("capabilities", {}).get("canDownload") is not True:
            raise SyncError(f"Drive asset {placeholder!r} is not downloadable by the service account.")
        file_id = asset.get("id")
        if not isinstance(file_id, str) or not file_id:
            raise SyncError(f"Drive asset {placeholder!r} returned no file identifier.")
        try:
            payload = drive.files().get_media(fileId=file_id, supportsAllDrives=True).execute()
        except Exception as exc:
            raise SyncError(f"Cannot download Drive asset {placeholder!r}.") from exc
        if not isinstance(payload, bytes) or not payload:
            raise SyncError(f"Drive asset {placeholder!r} returned empty image content.")
        path = destination / (str(len(downloaded) + 1) + extension)
        path.write_bytes(payload)
        downloaded[key] = path
    return downloaded


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


def target_context(target_type: str, entry: Any) -> tuple[str, str]:
    if not isinstance(entry, dict):
        return f"invalid-{target_type}", ""
    identifier = entry.get("id")
    source_key = "source_bundle" if target_type == "document" else "source_folder"
    return identifier if isinstance(identifier, str) and identifier else f"invalid-{target_type}", entry.get(source_key, "") if isinstance(entry.get(source_key), str) else ""


def underlying_error(error: Exception) -> Exception:
    current = error
    while isinstance(current, TargetError):
        current = current.error
    while current.__cause__ is not None:
        current = current.__cause__
    return current


def http_error_details(error: Exception) -> tuple[int | None, str | None, str | None]:
    remote = underlying_error(error)
    response = getattr(remote, "resp", None)
    status = getattr(response, "status", None)
    if not isinstance(status, int):
        return None, None, None
    reason: str | None = None
    message: str | None = None
    content = getattr(remote, "content", b"")
    try:
        payload = json.loads(content.decode("utf-8") if isinstance(content, bytes) else content)
        detail = payload.get("error", {})
        message = detail.get("message") if isinstance(detail.get("message"), str) else None
        errors = detail.get("errors", [])
        if isinstance(errors, list) and errors and isinstance(errors[0], dict):
            reason = errors[0].get("reason") if isinstance(errors[0].get("reason"), str) else None
    except (AttributeError, TypeError, UnicodeDecodeError, json.JSONDecodeError):
        pass
    return status, reason, message


def is_retryable(error: Exception) -> bool:
    status, reason, _ = http_error_details(error)
    return status in {429, 500, 502, 503, 504} or reason in {"rateLimitExceeded", "userRateLimitExceeded"}


def run_remote(phase: str, operation: Callable[[], Any]) -> Any:
    for attempt in range(1, MAX_REMOTE_ATTEMPTS + 1):
        try:
            return operation()
        except Exception as exc:
            if not is_retryable(exc) or attempt == MAX_REMOTE_ATTEMPTS:
                raise TargetError(phase, exc) from exc
            delay = min(2 ** (attempt - 1), 8) + random.random()
            print(json.dumps({"event": "sync_retry", "phase": phase, "attempt": attempt, "delay_seconds": round(delay, 2)}))
            time.sleep(delay)
    raise AssertionError("unreachable")


def run_local(phase: str, operation: Callable[[], Any]) -> Any:
    try:
        return operation()
    except TargetError:
        raise
    except Exception as exc:
        raise TargetError(phase, exc) from exc


def validate_document_local(root: Path, manifest: dict[str, Any], document: dict[str, Any]) -> None:
    reference_doc = repo_path(root, manifest["reference_doc"])
    if not reference_doc.is_file():
        raise SyncError(f"Shared reference DOCX is missing: {reference_doc}")
    source_bundle = document.get("source_bundle")
    if not isinstance(source_bundle, str) or not source_bundle:
        raise SyncError(f"{document['id']} must declare a source_bundle directory.")
    if not repo_path(root, source_bundle).is_dir():
        raise SyncError(f"Document bundle is missing: {source_bundle}")


def failure_details(error: TargetError) -> tuple[str, bool, str, str]:
    status, reason, remote_message = http_error_details(error)
    message = remote_message or str(error.error)
    message = " ".join(message.split())[:1000]
    if status in {401, 403}:
        return "ACCESS_DENIED", False, message, "Check the service account, Editor access, and enabled Google APIs."
    if status == 404:
        return "TARGET_NOT_FOUND", False, message, "Check the Drive file ID, file location, and sharing permission."
    if status == 400:
        return "REMOTE_REQUEST_INVALID", False, message, "Check the target configuration and the reported Google API request."
    if is_retryable(error):
        return "REMOTE_RETRY_EXHAUSTED", True, message, "Retry later; quota or Google service availability prevented completion."
    phase_codes = {
        "run": ("RUN_FAILED", "Check manifest syntax, credentials, and Google API client configuration."),
        "local": ("SOURCE_INVALID", "Fix the reported source path, CSV name, generated metadata, or tracker configuration."),
        "build": ("DOCUMENT_BUILD_FAILED", "Fix the reported document bundle, fragment, asset, or renderer issue."),
        "github": ("GITHUB_SOURCE_FAILED", "Check GitHub repository/token configuration and generated tracker inputs."),
        "assets": ("DRIVE_ASSET_FAILED", "Set GDRIVE_ASSETS_FOLDER_ID, share the image folder with the service account, and fix the named PNG/JPEG asset."),
        "reconcile": ("SHEET_RECONCILE_FAILED", "Resolve the tab/archive collision or spreadsheet structure error, then rerun."),
        "write": ("SHEET_WRITE_FAILED", "Check the spreadsheet access and rerun; source values remain in Git."),
        "upload": ("DOCUMENT_UPLOAD_FAILED", "Check the target Google Doc and service-account access, then rerun."),
    }
    code, hint = phase_codes.get(error.phase, ("SYNC_FAILED", "Review the target configuration and error message, then rerun."))
    return code, False, message, hint


def emit_result(result: dict[str, Any]) -> None:
    print("[SYNC_RESULT] " + json.dumps(result, ensure_ascii=False, sort_keys=True))
    if result["status"] != "failed":
        return
    title = f"Google Workspace sync failed: {result['target_type']} {result['target_id']} ({result['phase']})"
    detail = f"{result['message']} Hint: {result['hint']}"
    escaped_title = title.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    escaped_detail = detail.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print(f"::error title={escaped_title}::{escaped_detail}")


def success_result(target_type: str, target_id: str, source: str, phase: str, **extra: Any) -> dict[str, Any]:
    return {
        "target_type": target_type,
        "target_id": target_id,
        "source": source,
        "status": "success",
        "phase": phase,
        "error_code": None,
        "retryable": False,
        "message": "Synchronization completed.",
        "hint": "",
        **extra,
    }


def failed_result(target_type: str, target_id: str, source: str, error: TargetError) -> dict[str, Any]:
    error_code, retryable, message, hint = failure_details(error)
    return {
        "target_type": target_type,
        "target_id": target_id,
        "source": source,
        "status": "failed",
        "phase": error.phase,
        "error_code": error_code,
        "retryable": retryable,
        "message": message,
        "hint": hint,
    }


def process_document(root: Path, manifest: dict[str, Any], entry: Any, drive: Any, output_dir: Path, dry_run: bool) -> dict[str, Any]:
    target_id, source = target_context("document", entry)
    try:
        document = run_local("local", lambda: require_entry(entry, "google-doc"))
        target_id, source = target_context("document", document)
        run_local("local", lambda: validate_document_local(root, manifest, document))
        if dry_run:
            return success_result("document", target_id, source, "local-validation")
        bundle_path = repo_path(root, document["source_bundle"])
        placeholders = run_local("local", lambda: document_drive_image_placeholders(bundle_path))
        drive_assets: dict[str, Path] | None = None
        if placeholders:
            folder_id = run_local("assets", drive_assets_folder_id)
            asset_dir = output_dir / ("drive-assets-" + target_id)
            drive_assets = run_remote("assets", lambda: download_drive_assets(drive, folder_id, placeholders, asset_dir))
        docx = run_local("build", lambda: build_bundle(bundle_path, root, output_dir, drive_assets=drive_assets))
        run_remote("upload", lambda: drive_file(drive, target_id, document["drive_file_id"], GOOGLE_DOC_MIME))
        run_remote("upload", lambda: sync_document(drive, document, docx))
        return success_result("document", target_id, source, "upload")
    except TargetError as exc:
        return failed_result("document", target_id, source, exc)


def process_tracker(
    root: Path, entry: Any, drive: Any, sheets_api: Any, github_repository: str, github_token: str, dry_run: bool
) -> dict[str, Any]:
    target_id, source = target_context("tracker", entry)
    try:
        tracker = run_local("local", lambda: require_entry(entry, "google-sheet"))
        target_id, source = target_context("tracker", tracker)
        sheets = run_local("local", lambda: tracker_sheets(root, tracker))
        if dry_run:
            return success_result("tracker", target_id, source, "local-validation", sheet_count=len(sheets))
        needs_github = any(sheet.get("generated") == "github-issues" for sheet in sheets)
        github_api = run_local("github", lambda: GitHubApi(github_repository, github_token)) if needs_github else None
        values = {
            sheet["csv"]: run_local(
                "github" if sheet.get("generated") == "github-issues" else "local",
                lambda sheet=sheet: tracker_sheet_values(root, tracker, sheet, github_api),
            )
            for sheet in sheets
        }
        remote_sheets = run_remote("reconcile", lambda: (drive_file(drive, target_id, tracker["drive_file_id"], GOOGLE_SHEET_MIME), remote_tracker_sheets(sheets_api, tracker))[1])
        run_local("reconcile", lambda: reconciliation_requests(tracker, sheets, remote_sheets))
        run_remote("reconcile", lambda: reconcile_tracker(sheets_api, tracker, sheets, remote_sheets))
        updated_cells = run_remote(
            "write",
            lambda: sync_tracker(sheets_api, root, tracker, sheets, {(target_id, csv_name): rows for csv_name, rows in values.items()}),
        )
        return success_result("tracker", target_id, source, "write", sheet_count=len(sheets), updated_cells=updated_cells)
    except TargetError as exc:
        return failed_result("tracker", target_id, source, exc)


def write_result_report(path: str | None, results: list[dict[str, Any]]) -> None:
    if not path:
        return
    report_path = Path(path)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    summary = {"success": sum(item["status"] == "success" for item in results), "failed": sum(item["status"] == "failed" for item in results)}
    report_path.write_text(json.dumps({"summary": summary, "results": results}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def render_result_summary(report_path: Path, summary_path: Path) -> int:
    try:
        report = json.loads(report_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"SYNC ERROR: Cannot render result summary: {exc}", file=sys.stderr)
        return 1
    results = report.get("results", [])
    summary = report.get("summary", {})
    lines = ["## Google Workspace synchronization", "", f"- Success: {summary.get('success', 0)}", f"- Failed: {summary.get('failed', 0)}", "", "| Target | Status | Phase | Detail |", "| --- | --- | --- | --- |"]
    for item in results:
        target = f"{item.get('target_type', '')}: {item.get('target_id', '')}".replace("|", "\\|")
        detail = (item.get("message") or f"{item.get('updated_cells', '')} cells updated").replace("|", "\\|").replace("\n", " ")
        lines.append(f"| {target} | {item.get('status', '')} | {item.get('phase', '')} | {detail} |")
    try:
        with summary_path.open("a", encoding="utf-8") as destination:
            destination.write("\n".join(lines) + "\n")
    except OSError as exc:
        print(f"SYNC ERROR: Cannot write GitHub summary: {exc}", file=sys.stderr)
        return 1
    return 0


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
    parser.add_argument("--result-report", help="Write machine-readable per-target synchronization results to this JSON path.")
    parser.add_argument("--render-result-summary", help="Render an existing result-report JSON file into --github-summary.")
    parser.add_argument("--github-summary", help="GitHub Job Summary path used with --render-result-summary.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.render_result_summary:
        if not args.github_summary:
            raise SyncError("--github-summary is required with --render-result-summary.")
        return render_result_summary(Path(args.render_result_summary), Path(args.github_summary))

    results: list[dict[str, Any]] = []
    root = Path.cwd().resolve()
    try:
        manifest = load_manifest(repo_path(root, args.manifest))
        documents, trackers = select_entries(manifest, changed_paths(args.changed), args.all)
        if args.include_generated_trackers and not args.all:
            generated_trackers = [
                tracker for tracker in manifest["trackers"]
                if isinstance(tracker, dict)
                and any(sheet.get("generated") == "github-issues" for sheet in tracker.get("generated_sheets", []) if isinstance(sheet, dict))
            ]
            known_ids = {item.get("id") for item in trackers if isinstance(item, dict)}
            trackers.extend(tracker for tracker in generated_trackers if tracker.get("id") not in known_ids)
        if not documents and not trackers:
            print("No mapped document bundles or CSV sources changed; nothing to synchronize.")
            return 0
        if args.dry_run:
            for document in documents:
                result = process_document(root, manifest, document, None, root, True)
                results.append(result)
                emit_result(result)
            for tracker in trackers:
                result = process_tracker(root, tracker, None, None, args.github_repository, args.github_token, True)
                results.append(result)
                emit_result(result)
            return 1 if any(item["status"] == "failed" for item in results) else 0
        if not args.credentials:
            raise SyncError("--credentials is required.")
        credential_path = Path(args.credentials)
        if not credential_path.is_file():
            raise SyncError(f"Credentials file is missing: {credential_path}")
        try:
            drive, sheets_api = google_clients(credential_path)
        except Exception as exc:
            raise SyncError(f"Cannot create Google API clients: {exc}") from exc
        with tempfile.TemporaryDirectory(prefix="google-doc-build-", dir=root) as temp_dir:
            for document in documents:
                result = process_document(root, manifest, document, drive, Path(temp_dir), False)
                results.append(result)
                emit_result(result)
            for tracker in trackers:
                result = process_tracker(root, tracker, drive, sheets_api, args.github_repository, args.github_token, False)
                results.append(result)
                emit_result(result)
        succeeded = sum(item["status"] == "success" for item in results)
        failed = sum(item["status"] == "failed" for item in results)
        print(f"Sync complete: {succeeded} succeeded, {failed} failed.")
        return 1 if failed else 0
    except Exception as exc:
        error = TargetError("run", exc)
        result = failed_result("run", "workspace-sync", "", error)
        results.append(result)
        emit_result(result)
        return 1
    finally:
        write_result_report(args.result_report, results)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SyncError as exc:
        print(f"SYNC ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
