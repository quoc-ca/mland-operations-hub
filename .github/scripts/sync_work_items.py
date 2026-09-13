#!/usr/bin/env python3
"""Synchronize report-declared MOH work items to GitHub Issues.

Only the develop workflow may run this script with a write-capable token.
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
DOCUMENTS_ROOT = REPOSITORY_ROOT / "documents"
if str(DOCUMENTS_ROOT) not in sys.path:
    sys.path.insert(0, str(DOCUMENTS_ROOT))

from tools.github_issues import GitHubApi, WorkItemError, discover_work_items, synchronize_work_items


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY", ""))
    parser.add_argument("--token", default=os.environ.get("GITHUB_TOKEN", ""))
    parser.add_argument("--documents-root", default="documents/docs")
    parser.add_argument("--dry-run", action="store_true", help="Validate and print planned changes without writing GitHub Issues.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    items = discover_work_items(REPOSITORY_ROOT, (REPOSITORY_ROOT / args.documents_root).resolve())
    api = GitHubApi(args.repository, args.token)
    for action, identifier in synchronize_work_items(api, items, dry_run=args.dry_run):
        print(f"[{action}] work_item_id={identifier!r}")
    print(f"Managed work-item synchronization complete: {len(items)} declaration(s).")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except WorkItemError as exc:
        print(f"WORK ITEM SYNC ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
