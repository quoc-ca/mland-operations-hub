"""GitHub Issue declarations, API access, and reporting rows for this project."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen


WORK_ITEM_PATTERN = re.compile(r"<!--\s*MOH-WORK-ITEM\s*(?P<payload>\{.*?\})\s*-->", re.DOTALL)
WORK_ITEM_ID_PATTERN = re.compile(r"<!--\s*MOH-WORK-ITEM-ID:\s*(?P<id>[A-Z][A-Z0-9-]{2,63})\s*-->")
WORK_ITEM_SOURCE_PATTERN = re.compile(r"<!--\s*MOH-WORK-ITEM-SOURCE:\s*(?P<source>[^\r\n]+?)\s*-->")
WORK_ITEM_ID = re.compile(r"^[A-Z][A-Z0-9-]{2,63}$")
GITHUB_USERNAME = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$")
REPOSITORY = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
FENCED_CODE_BLOCK = re.compile(r"^\s*(```|~~~).*?^\s*\1\s*$", re.MULTILINE | re.DOTALL)
WORK_TYPES = {"task", "bug", "research", "requirement-gap", "decision", "test", "documentation", "risk-action"}
PRIORITIES = {"critical", "high", "medium", "low"}
DOMAINS = {"booking", "staff-shifts", "inventory", "finance", "reporting", "forecasting", "platform", "documentation"}
SEVERITIES = {"blocker", "major", "minor"}


class WorkItemError(RuntimeError):
    """A declaration or GitHub API condition that must stop synchronization."""


@dataclass(frozen=True)
class WorkItem:
    identifier: str
    title: str
    assignee: str
    labels: tuple[str, ...]
    source: str


def discover_work_items(repository_root: Path, documents_root: Path | None = None) -> tuple[WorkItem, ...]:
    root = repository_root.resolve()
    scan_root = (documents_root or root / "documents" / "docs").resolve()
    try:
        scan_root.relative_to(root)
    except ValueError as exc:
        raise WorkItemError(f"Documents root is outside repository: {scan_root}") from exc
    if not scan_root.is_dir():
        raise WorkItemError(f"Documents root is missing: {scan_root}")

    items: list[WorkItem] = []
    identifiers: dict[str, str] = {}
    for path in sorted(scan_root.rglob("*.md")):
        relative = path.relative_to(root).as_posix()
        # Examples in Markdown code fences document the declaration grammar;
        # they must never create work in GitHub.
        text = FENCED_CODE_BLOCK.sub("", path.read_text(encoding="utf-8"))
        for match in WORK_ITEM_PATTERN.finditer(text):
            item = _parse_work_item(match.group("payload"), relative)
            previous = identifiers.get(item.identifier)
            if previous is not None:
                raise WorkItemError(f"Duplicate work-item ID {item.identifier!r} in {previous} and {relative}.")
            identifiers[item.identifier] = relative
            items.append(item)
    return tuple(items)


def _parse_work_item(payload: str, relative_source: str) -> WorkItem:
    try:
        data = json.loads(payload)
    except json.JSONDecodeError as exc:
        raise WorkItemError(f"Invalid MOH-WORK-ITEM JSON in {relative_source}: {exc.msg}") from exc
    if not isinstance(data, dict):
        raise WorkItemError(f"MOH-WORK-ITEM in {relative_source} must be a JSON object.")
    required = {"id", "title", "assignee", "labels", "source"}
    unknown = set(data) - required
    missing = required - set(data)
    if missing or unknown:
        raise WorkItemError(
            f"MOH-WORK-ITEM in {relative_source} must contain exactly {sorted(required)}; "
            f"missing={sorted(missing)}, unknown={sorted(unknown)}."
        )
    identifier = data["id"]
    title = data["title"]
    assignee = data["assignee"]
    labels = data["labels"]
    source = data["source"]
    if not isinstance(identifier, str) or not WORK_ITEM_ID.fullmatch(identifier):
        raise WorkItemError(f"Invalid work-item ID in {relative_source}: {identifier!r}")
    if not isinstance(title, str) or not title.strip() or len(title) > 256:
        raise WorkItemError(f"Work-item {identifier} needs a non-empty title of at most 256 characters.")
    if not isinstance(assignee, str) or not GITHUB_USERNAME.fullmatch(assignee):
        raise WorkItemError(f"Work-item {identifier} has an invalid GitHub assignee: {assignee!r}")
    if not isinstance(labels, list) or not labels or not all(isinstance(label, str) and label.strip() for label in labels):
        raise WorkItemError(f"Work-item {identifier} needs a non-empty labels list.")
    if len(set(labels)) != len(labels):
        raise WorkItemError(f"Work-item {identifier} repeats a label.")
    _validate_work_item_taxonomy(identifier, labels)
    if source != relative_source:
        raise WorkItemError(f"Work-item {identifier} source must equal {relative_source!r}, not {source!r}.")
    return WorkItem(identifier, title.strip(), assignee, tuple(labels), source)


def _validate_work_item_taxonomy(identifier: str, labels: list[str]) -> None:
    def values(prefix: str) -> list[str]:
        return [label.removeprefix(prefix) for label in labels if label.startswith(prefix)]

    types = values("type:")
    priorities = values("priority:")
    domains = values("domain:")
    severities = values("severity:")
    sources = values("source:")
    if len(types) != 1 or types[0] not in WORK_TYPES:
        raise WorkItemError(f"Work-item {identifier} needs exactly one supported type:* label.")
    if len(priorities) != 1 or priorities[0] not in PRIORITIES:
        raise WorkItemError(f"Work-item {identifier} needs exactly one supported priority:* label.")
    if not domains or any(domain not in DOMAINS for domain in domains):
        raise WorkItemError(f"Work-item {identifier} needs at least one supported domain:* label.")
    if sources != ["report"]:
        raise WorkItemError(f"Work-item {identifier} needs exactly the source:report label.")
    if severities and (types[0] != "bug" or len(severities) != 1 or severities[0] not in SEVERITIES):
        raise WorkItemError(f"Work-item {identifier} may use one supported severity:* label only when type:bug.")


def managed_issue_body(item: WorkItem) -> str:
    return "\n".join(
        (
            f"<!-- MOH-WORK-ITEM-ID: {item.identifier} -->",
            f"<!-- MOH-WORK-ITEM-SOURCE: {item.source} -->",
            "This GitHub Issue is managed from a versioned report declaration.",
            "",
            f"Source fragment: `{item.source}`",
        )
    )


def managed_issue_id(issue: dict[str, Any]) -> str | None:
    body = issue.get("body")
    if not isinstance(body, str):
        return None
    match = WORK_ITEM_ID_PATTERN.search(body)
    return match.group("id") if match else None


def managed_issue_source(issue: dict[str, Any]) -> str:
    body = issue.get("body")
    if not isinstance(body, str):
        return ""
    match = WORK_ITEM_SOURCE_PATTERN.search(body)
    return match.group("source") if match else ""


def label_names(issue: dict[str, Any]) -> tuple[str, ...]:
    labels = issue.get("labels", [])
    result: list[str] = []
    if isinstance(labels, list):
        for label in labels:
            if isinstance(label, str):
                result.append(label)
            elif isinstance(label, dict) and isinstance(label.get("name"), str):
                result.append(label["name"])
    return tuple(result)


def issue_snapshot_rows(issues: list[dict[str, Any]]) -> list[list[str]]:
    rows: list[list[str]] = []
    for issue in sorted((item for item in issues if "pull_request" not in item), key=lambda item: int(item.get("number", 0))):
        labels = label_names(issue)
        assignees = issue.get("assignees", [])
        usernames = sorted(
            assignee["login"] for assignee in assignees
            if isinstance(assignee, dict) and isinstance(assignee.get("login"), str)
        ) if isinstance(assignees, list) else []
        rows.append(
            [
                str(issue.get("number", "")),
                managed_issue_id(issue) or "",
                str(issue.get("title", "")),
                _prefixed_value(labels, "type:"),
                "; ".join(_prefixed_values(labels, "domain:")),
                _prefixed_value(labels, "priority:"),
                "; ".join(usernames),
                str(issue.get("state", "")),
                "; ".join(labels),
                str(issue.get("created_at", "")),
                str(issue.get("updated_at", "")),
                str(issue.get("closed_at") or ""),
                managed_issue_source(issue),
                str(issue.get("html_url", "")),
            ]
        )
    return rows


def synchronize_work_items(api: Any, items: tuple[WorkItem, ...], dry_run: bool = False) -> list[tuple[str, str]]:
    """Preflight every declared item, then create or update open managed issues.

    The client is intentionally duck-typed so this policy can be unit-tested
    without network access. Preflight completes before the first write.
    """
    available_labels = api.labels()
    planned: list[tuple[WorkItem, list[dict[str, Any]]]] = []
    failures: list[str] = []
    for item in items:
        missing_labels = sorted(set(item.labels) - available_labels)
        if missing_labels:
            failures.append(f"{item.identifier}: unknown GitHub labels: {', '.join(missing_labels)}")
        if not api.assignee_is_valid(item.assignee):
            failures.append(f"{item.identifier}: GitHub assignee is not assignable: {item.assignee}")
        matches = api.managed_issues(item.identifier)
        if len(matches) > 1:
            failures.append(f"{item.identifier}: duplicate managed GitHub Issues: " + ", ".join(str(issue.get("number", "?")) for issue in matches))
        planned.append((item, matches))
    if failures:
        raise WorkItemError("Managed work-item preflight failed:\n- " + "\n- ".join(failures))

    actions: list[tuple[str, str]] = []
    for item, matches in planned:
        if not matches:
            action = "CREATE"
            if not dry_run:
                api.post(f"/repos/{api.repository}/issues", _issue_payload(item))
        else:
            issue = matches[0]
            if issue.get("state") == "closed":
                actions.append(("SKIP-CLOSED", item.identifier))
                continue
            action = "UPDATE" if _managed_issue_differs(issue, item) else "UNCHANGED"
            if action == "UPDATE" and not dry_run:
                api.patch(f"/repos/{api.repository}/issues/{issue['number']}", _issue_payload(item))
        actions.append((action, item.identifier))
    return actions


def _issue_payload(item: WorkItem) -> dict[str, Any]:
    return {"title": item.title, "body": managed_issue_body(item), "assignees": [item.assignee], "labels": list(item.labels)}


def _managed_issue_differs(issue: dict[str, Any], item: WorkItem) -> bool:
    assignees = issue.get("assignees", [])
    existing_assignees = sorted(
        assignee["login"] for assignee in assignees
        if isinstance(assignee, dict) and isinstance(assignee.get("login"), str)
    ) if isinstance(assignees, list) else []
    return (
        issue.get("title") != item.title
        or issue.get("body") != managed_issue_body(item)
        or existing_assignees != [item.assignee]
        or sorted(label_names(issue)) != sorted(item.labels)
    )


def _prefixed_values(labels: tuple[str, ...], prefix: str) -> tuple[str, ...]:
    return tuple(label.removeprefix(prefix) for label in labels if label.startswith(prefix))


def _prefixed_value(labels: tuple[str, ...], prefix: str) -> str:
    values = _prefixed_values(labels, prefix)
    return values[0] if values else ""


class GitHubApi:
    """Minimal REST client using a workflow's scoped GitHub token."""

    def __init__(self, repository: str, token: str):
        if not REPOSITORY.fullmatch(repository):
            raise WorkItemError(f"Invalid GitHub repository: {repository!r}")
        if not token:
            raise WorkItemError("GitHub token is required.")
        self.repository = repository
        self.token = token

    def get(self, path: str) -> Any:
        return self._request("GET", path)

    def post(self, path: str, payload: dict[str, Any]) -> Any:
        return self._request("POST", path, payload)

    def patch(self, path: str, payload: dict[str, Any]) -> Any:
        return self._request("PATCH", path, payload)

    def _request(self, method: str, path: str, payload: dict[str, Any] | None = None) -> Any:
        url = f"https://api.github.com{path}"
        data = json.dumps(payload).encode("utf-8") if payload is not None else None
        request = Request(
            url,
            data=data,
            method=method,
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {self.token}",
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "mland-operations-hub-work-item-sync",
            },
        )
        try:
            with urlopen(request, timeout=30) as response:  # noqa: S310 - fixed GitHub API origin.
                text = response.read().decode("utf-8")
        except HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise WorkItemError(f"GitHub API {method} {path} failed with {exc.code}: {detail}") from exc
        except (URLError, TimeoutError, OSError) as exc:
            raise WorkItemError(f"GitHub API {method} {path} failed: {exc}") from exc
        return json.loads(text) if text else None

    def all_issues(self) -> list[dict[str, Any]]:
        issues: list[dict[str, Any]] = []
        page = 1
        while True:
            result = self.get(f"/repos/{self.repository}/issues?state=all&per_page=100&page={page}")
            if not isinstance(result, list):
                raise WorkItemError("GitHub issue listing returned an invalid response.")
            issues.extend(item for item in result if isinstance(item, dict))
            if len(result) < 100:
                return issues
            page += 1

    def labels(self) -> set[str]:
        result = self.get(f"/repos/{self.repository}/labels?per_page=100")
        if not isinstance(result, list):
            raise WorkItemError("GitHub label listing returned an invalid response.")
        return {item["name"] for item in result if isinstance(item, dict) and isinstance(item.get("name"), str)}

    def assignee_is_valid(self, username: str) -> bool:
        try:
            self.get(f"/repos/{self.repository}/assignees/{quote(username, safe='')}")
        except WorkItemError as exc:
            if " failed with 404:" in str(exc):
                return False
            raise
        return True

    def managed_issues(self, identifier: str) -> list[dict[str, Any]]:
        query = quote(f'repo:{self.repository} is:issue in:body "MOH-WORK-ITEM-ID: {identifier}"')
        result = self.get(f"/search/issues?q={query}&per_page=100")
        if not isinstance(result, dict) or not isinstance(result.get("items"), list):
            raise WorkItemError(f"GitHub search returned an invalid response for {identifier}.")
        return [
            issue for issue in result["items"]
            if isinstance(issue, dict) and "pull_request" not in issue and managed_issue_id(issue) == identifier
        ]
