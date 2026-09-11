"""Read scoped Git commit history for generated document change logs."""

from __future__ import annotations

import subprocess
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class GitCommit:
    short_sha: str
    date: str
    author: str
    subject: str


class GitHistoryError(RuntimeError):
    """Git history cannot be read for a generated artifact."""


def _run_git(arguments: list[str]) -> str:
    try:
        completed = subprocess.run(arguments, capture_output=True, text=True, check=False)
    except OSError as exc:
        raise GitHistoryError(f"Cannot run Git: {exc}") from exc
    if completed.returncode:
        reason = completed.stderr.strip() or completed.stdout.strip() or f"Git exited {completed.returncode}."
        raise GitHistoryError(reason)
    return completed.stdout


def git_history_entries(start: Path, source: Path) -> tuple[GitCommit, ...]:
    """Return commits touching ``source``, newest first, from its containing Git repository."""
    git_root = Path(_run_git(["git", "-C", str(start), "rev-parse", "--show-toplevel"]).strip()).resolve()
    try:
        relative_source = source.resolve().relative_to(git_root).as_posix()
    except ValueError as exc:
        raise GitHistoryError(f"Source path is outside its Git repository: {source}") from exc

    output = _run_git(
        [
            "git",
            "-C",
            str(git_root),
            "log",
            "--date=short",
            "--format=%h%x1f%ad%x1f%an%x1f%s%x1e",
            "--",
            relative_source,
        ]
    )
    entries: list[GitCommit] = []
    for record in output.split("\x1e"):
        if not record.strip():
            continue
        fields = record.strip("\r\n").split("\x1f")
        if len(fields) != 4:
            raise GitHistoryError("Git returned an invalid change-history record.")
        entries.append(GitCommit(short_sha=fields[0], date=fields[1], author=fields[2], subject=fields[3]))
    return tuple(entries)


def markdown_change_history(entries: tuple[GitCommit, ...]) -> str:
    """Format Git history as the Markdown table embedded in generated reports."""
    rows = [
        "| Commit | Date | Changes | Author |",
        "| --- | --- | --- | --- |",
    ]
    if not entries:
        rows.append("| — | — | No committed changes found for this report path. | — |")
        return "\n".join(rows)
    for entry in entries:
        subject = entry.subject.replace("|", "\\|").replace("\n", " ")
        author = entry.author.replace("|", "\\|").replace("\n", " ")
        rows.append(f"| `{entry.short_sha}` | {entry.date} | {subject} | {author} |")
    return "\n".join(rows)
