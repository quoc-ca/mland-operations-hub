from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools.github_issues import WorkItem, WorkItemError, discover_work_items, issue_snapshot_rows, synchronize_work_items


class FakeGitHubApi:
    repository = "mland/example"

    def __init__(self, existing: list[dict] | None = None, labels: set[str] | None = None, valid_assignees: set[str] | None = None) -> None:
        self.existing = existing or []
        self.available_labels = labels or {"type:task", "priority:high", "domain:platform", "source:report"}
        self.valid_assignees = valid_assignees or {"quoc-ca"}
        self.writes: list[tuple[str, str, dict]] = []

    def labels(self) -> set[str]:
        return self.available_labels

    def assignee_is_valid(self, username: str) -> bool:
        return username in self.valid_assignees

    def managed_issues(self, identifier: str) -> list[dict]:
        return [issue for issue in self.existing if f"MOH-WORK-ITEM-ID: {identifier}" in issue.get("body", "")]

    def post(self, path: str, payload: dict) -> None:
        self.writes.append(("POST", path, payload))

    def patch(self, path: str, payload: dict) -> None:
        self.writes.append(("PATCH", path, payload))


def item() -> WorkItem:
    return WorkItem(
        "MOH-001", "Confirm platform scope", "quoc-ca",
        ("type:task", "priority:high", "domain:platform", "source:report"),
        "documents/docs/report/sections/01.md",
    )


class WorkItemDeclarationTests(unittest.TestCase):
    def test_discovers_declaration_and_ignores_code_example(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "documents" / "docs" / "report" / "sections"
            source.mkdir(parents=True)
            path = source / "01.md"
            relative = "documents/docs/report/sections/01.md"
            path.write_text(
                "```html\n<!-- MOH-WORK-ITEM {\"id\":\"MOH-EXAMPLE\"} -->\n```\n"
                f"<!-- MOH-WORK-ITEM {{\"id\":\"MOH-001\",\"title\":\"Confirm scope\",\"assignee\":\"quoc-ca\",\"labels\":[\"type:task\",\"priority:high\",\"domain:platform\",\"source:report\"],\"source\":\"{relative}\"}} -->\n",
                encoding="utf-8",
            )
            items = discover_work_items(root)
        self.assertEqual([work.identifier for work in items], ["MOH-001"])

    def test_rejects_duplicate_declaration_before_sync(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "documents" / "docs"
            source.mkdir(parents=True)
            for name in ("a.md", "b.md"):
                relative = f"documents/docs/{name}"
                (source / name).write_text(
                    f"<!-- MOH-WORK-ITEM {{\"id\":\"MOH-001\",\"title\":\"Task\",\"assignee\":\"quoc-ca\",\"labels\":[\"type:task\",\"priority:high\",\"domain:platform\",\"source:report\"],\"source\":\"{relative}\"}} -->",
                    encoding="utf-8",
                )
            with self.assertRaisesRegex(WorkItemError, "Duplicate"):
                discover_work_items(root)

    def test_preflight_then_create_update_and_closed_skip(self) -> None:
        work = item()
        api = FakeGitHubApi()
        self.assertEqual(synchronize_work_items(api, (work,)), [("CREATE", "MOH-001")])
        self.assertEqual(api.writes[0][0], "POST")

        unchanged = {"number": 3, "state": "open", "title": work.title, "body": api.writes[0][2]["body"], "assignees": [{"login": "quoc-ca"}], "labels": [{"name": name} for name in work.labels]}
        api = FakeGitHubApi([unchanged])
        self.assertEqual(synchronize_work_items(api, (work,)), [("UNCHANGED", "MOH-001")])
        self.assertEqual(api.writes, [])

        changed = dict(unchanged, title="Old title")
        api = FakeGitHubApi([changed])
        self.assertEqual(synchronize_work_items(api, (work,)), [("UPDATE", "MOH-001")])
        self.assertEqual(api.writes[0][0], "PATCH")

        closed = dict(unchanged, state="closed")
        api = FakeGitHubApi([closed])
        self.assertEqual(synchronize_work_items(api, (work,)), [("SKIP-CLOSED", "MOH-001")])
        self.assertEqual(api.writes, [])

    def test_preflight_fails_without_any_write(self) -> None:
        api = FakeGitHubApi(labels={"type:task"}, valid_assignees=set())
        with self.assertRaisesRegex(WorkItemError, "unknown GitHub labels"):
            synchronize_work_items(api, (item(),))
        self.assertEqual(api.writes, [])


class GitHubIssueSnapshotTests(unittest.TestCase):
    def test_snapshot_filters_pull_requests_and_maps_managed_and_manual_issues(self) -> None:
        rows = issue_snapshot_rows([
            {"number": 2, "title": "Manual issue", "state": "open", "labels": [{"name": "type:bug"}, {"name": "domain:booking"}], "assignees": [], "created_at": "c", "updated_at": "u", "closed_at": None, "html_url": "https://example/2"},
            {"number": 1, "title": "Managed", "state": "closed", "labels": [{"name": "type:task"}, {"name": "priority:high"}, {"name": "domain:platform"}], "assignees": [{"login": "quoc-ca"}], "body": "<!-- MOH-WORK-ITEM-ID: MOH-001 -->\n<!-- MOH-WORK-ITEM-SOURCE: documents/docs/x.md -->", "created_at": "c", "updated_at": "u", "closed_at": "z", "html_url": "https://example/1"},
            {"number": 3, "pull_request": {}, "title": "PR"},
        ])
        self.assertEqual([row[0] for row in rows], ["1", "2"])
        self.assertEqual(rows[0][1:7], ["MOH-001", "Managed", "task", "platform", "high", "quoc-ca"])
        self.assertEqual(rows[1][1], "")


if __name__ == "__main__":
    unittest.main()
