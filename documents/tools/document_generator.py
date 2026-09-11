"""Build a composable Markdown document bundle into a DOCX file.

This module deliberately owns only local generation.  It never accesses Google
Workspace and it never reads or modifies generated DOCX files.
"""

from __future__ import annotations

import base64
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen

try:
    from .git_history import GitCommit, GitHistoryError, git_history_entries, markdown_change_history
except ImportError:  # Supports `python tools/generate_document.py`.
    from git_history import GitCommit, GitHistoryError, git_history_entries, markdown_change_history

IMAGE_PATTERN = re.compile(r"!\[[^\]]*\]\((?P<target>[^)\s]+)(?:\s+[^)]*)?\)")
DIAGRAM_EXTENSIONS = {".mmd": "mermaid", ".puml": "plantuml"}
RASTER_OR_VECTOR_EXTENSIONS = {".png", ".jpg", ".jpeg", ".gif", ".svg"}
DEFAULT_RENDERERS = {
    "mermaid": "https://mermaid.ink",
    "plantuml": "https://www.plantuml.com/plantuml",
}
GIT_HISTORY_MARKER = "<!-- AUTO-GENERATED: GIT-CHANGE-HISTORY -->"


@dataclass(frozen=True)
class Diagnostic:
    code: str
    reason: str
    report_id: str | None = None
    fragment: str | None = None
    diagram: str | None = None
    renderer: str | None = None
    endpoint: str | None = None
    status: str | None = None

    def format(self) -> str:
        fields = [("code", self.code), ("reason", self.reason)]
        for name in ("report_id", "fragment", "diagram", "renderer", "endpoint", "status"):
            value = getattr(self, name)
            if value is not None:
                fields.append((name, value))
        return "[ERROR] " + " ".join(f"{name}={value!r}" for name, value in fields)


class GenerationError(RuntimeError):
    def __init__(self, diagnostic: Diagnostic):
        self.diagnostic = diagnostic
        super().__init__(diagnostic.format())


@dataclass(frozen=True)
class Bundle:
    root: Path
    report_id: str
    output_name: str
    fragments: tuple[Path, ...]
    renderers: dict[str, str]


def _error(code: str, reason: str, **details: str | None) -> GenerationError:
    return GenerationError(Diagnostic(code=code, reason=reason, **details))


def _read_yaml(path: Path) -> dict[str, Any]:
    try:
        # JSON is a strict YAML 1.2 subset.  Keeping the manifest in this
        # subset avoids a runtime dependency for optional YAML parsing.
        data = json.loads(path.read_text(encoding="utf-8"))
    except OSError as exc:
        raise _error("manifest-unreadable", str(exc)) from exc
    except json.JSONDecodeError as exc:
        raise _error("manifest-invalid-yaml", f"document.yml must use JSON-compatible YAML: {exc}") from exc
    if not isinstance(data, dict):
        raise _error("manifest-invalid", "document.yml must contain a mapping.")
    return data


def _inside(base: Path, value: str, *, label: str, report_id: str | None = None) -> Path:
    candidate = Path(value)
    if candidate.is_absolute():
        raise _error("path-outside-bundle", f"{label} must be relative.", report_id=report_id)
    resolved = (base / candidate).resolve()
    try:
        resolved.relative_to(base.resolve())
    except ValueError as exc:
        raise _error("path-outside-bundle", f"{label} escapes the bundle: {value}", report_id=report_id) from exc
    return resolved


def load_bundle(bundle_root: Path) -> Bundle:
    root = bundle_root.resolve()
    manifest_path = root / "document.yml"
    if not manifest_path.is_file():
        raise _error("manifest-missing", f"Missing {manifest_path}")
    data = _read_yaml(manifest_path)
    report_id = data.get("id")
    output_name = data.get("output")
    fragments_value = data.get("fragments")
    if not isinstance(report_id, str) or not re.fullmatch(r"[a-z0-9][a-z0-9.-]*", report_id):
        raise _error("manifest-invalid", "id must use lowercase letters, digits, periods, and hyphens.")
    if not isinstance(output_name, str) or Path(output_name).name != output_name or not output_name.endswith(".docx"):
        raise _error("manifest-invalid", "output must be a DOCX filename without directories.", report_id=report_id)
    if not isinstance(fragments_value, list) or not fragments_value or not all(isinstance(item, str) for item in fragments_value):
        raise _error("manifest-invalid", "fragments must be a non-empty ordered list of paths.", report_id=report_id)
    if fragments_value[0] != "front-matter.md":
        raise _error("manifest-unordered", "front-matter.md must be the first fragment.", report_id=report_id)
    if any(not item.startswith("sections/") for item in fragments_value[1:]):
        raise _error("manifest-unordered", "all fragments after front-matter.md must live in sections/.", report_id=report_id)

    fragments: list[Path] = []
    seen: set[Path] = set()
    for item in fragments_value:
        if not item.endswith(".md"):
            raise _error("fragment-invalid", f"Fragment is not Markdown: {item}", report_id=report_id)
        fragment = _inside(root, item, label="fragment", report_id=report_id)
        if fragment in seen:
            raise _error("fragment-duplicate", f"Fragment is listed more than once: {item}", report_id=report_id)
        if not fragment.is_file():
            raise _error("fragment-missing", f"Fragment does not exist: {item}", report_id=report_id, fragment=item)
        seen.add(fragment)
        fragments.append(fragment)

    configured_renderers = data.get("renderers", {})
    if not isinstance(configured_renderers, dict) or not all(
        isinstance(name, str) and isinstance(url, str) for name, url in configured_renderers.items()
    ):
        raise _error("manifest-invalid", "renderers must map renderer names to URLs.", report_id=report_id)
    renderers = {**DEFAULT_RENDERERS, **configured_renderers}
    for name in DIAGRAM_EXTENSIONS.values():
        endpoint = renderers.get(name)
        if not isinstance(endpoint, str) or not endpoint.startswith("https://"):
            raise _error("renderer-invalid", f"{name} renderer must use an HTTPS URL.", report_id=report_id)
    return Bundle(root, report_id, output_name, tuple(fragments), renderers)


def _image_targets(markdown: str) -> list[str]:
    return [match.group("target").strip("<>") for match in IMAGE_PATTERN.finditer(markdown)]


def _asset_path(bundle: Bundle, target: str, fragment: Path) -> Path:
    if "://" in target or target.startswith("data:"):
        raise _error(
            "asset-external", "Images must be committed bundle assets, not external URLs.",
            report_id=bundle.report_id, fragment=_relative(bundle, fragment), diagram=target,
        )
    return _inside(bundle.root, target, label="asset", report_id=bundle.report_id)


def _relative(bundle: Bundle, path: Path) -> str:
    return path.relative_to(bundle.root).as_posix()


def validate_bundle(bundle: Bundle) -> list[tuple[Path, Path, str]]:
    """Validate local inputs and return (fragment, diagram, renderer) tuples."""
    diagrams: list[tuple[Path, Path, str]] = []
    for fragment in bundle.fragments:
        relative_fragment = _relative(bundle, fragment)
        text = fragment.read_text(encoding="utf-8")
        for target in _image_targets(text):
            asset = _asset_path(bundle, target, fragment)
            suffix = asset.suffix.lower()
            if suffix not in set(DIAGRAM_EXTENSIONS) | RASTER_OR_VECTOR_EXTENSIONS:
                raise _error(
                    "asset-unsupported", f"Unsupported image extension: {target}",
                    report_id=bundle.report_id, fragment=relative_fragment, diagram=target,
                )
            if not asset.is_file():
                raise _error(
                    "asset-missing", f"Referenced asset does not exist: {target}",
                    report_id=bundle.report_id, fragment=relative_fragment, diagram=target,
                )
            if suffix in DIAGRAM_EXTENSIONS:
                diagrams.append((fragment, asset, DIAGRAM_EXTENSIONS[suffix]))
    return diagrams


def _diagram_url(renderer: str, endpoint: str, source: str) -> str:
    base = endpoint.rstrip("/")
    if renderer == "plantuml":
        # PlantUML's documented ~h format avoids a third-party encoder dependency.
        return f"{base}/png/~h{source.encode('utf-8').hex()}"
    if renderer == "mermaid":
        encoded = base64.urlsafe_b64encode(source.encode("utf-8")).decode("ascii").rstrip("=")
        return f"{base}/img/{quote(encoded)}?type=png"
    raise AssertionError(f"Unexpected renderer: {renderer}")


def _download_png(bundle: Bundle, fragment: Path, diagram: Path, renderer: str, destination: Path) -> None:
    endpoint = bundle.renderers[renderer]
    source = diagram.read_text(encoding="utf-8")
    url = _diagram_url(renderer, endpoint, source)
    try:
        request = Request(url, headers={"User-Agent": "local-document-generator/1.0"})
        with urlopen(request, timeout=30) as response:  # noqa: S310 - endpoints are manifest-controlled HTTPS URLs.
            content_type = response.headers.get_content_type()
            data = response.read()
            status = str(getattr(response, "status", 200))
    except HTTPError as exc:
        raise _error(
            "diagram-render-failed", str(exc), report_id=bundle.report_id,
            fragment=_relative(bundle, fragment), diagram=_relative(bundle, diagram),
            renderer=renderer, endpoint=endpoint, status=str(exc.code),
        ) from exc
    except (URLError, TimeoutError, OSError) as exc:
        raise _error(
            "diagram-render-failed", str(exc), report_id=bundle.report_id,
            fragment=_relative(bundle, fragment), diagram=_relative(bundle, diagram),
            renderer=renderer, endpoint=endpoint, status="unavailable",
        ) from exc
    if content_type not in {"image/png", "image/x-png"} or not data:
        raise _error(
            "diagram-render-invalid-response", f"Expected a non-empty PNG, received {content_type}.",
            report_id=bundle.report_id, fragment=_relative(bundle, fragment),
            diagram=_relative(bundle, diagram), renderer=renderer, endpoint=endpoint, status=status,
        )
    destination.write_bytes(data)


def _replace_diagrams(bundle: Bundle, fragment: Path, text: str, rendered: dict[Path, Path]) -> str:
    def replace(match: re.Match[str]) -> str:
        target = match.group("target").strip("<>")
        asset = _asset_path(bundle, target, fragment)
        if asset.suffix.lower() not in DIAGRAM_EXTENSIONS:
            return match.group(0)
        replacement = f"assets/{rendered[asset].name}"
        return match.group(0).replace(match.group("target"), replacement)

    return IMAGE_PATTERN.sub(replace, text)


def _replace_git_history(text: str, history: tuple[GitCommit, ...]) -> str:
    return text.replace(GIT_HISTORY_MARKER, markdown_change_history(history))


def _require_pandoc() -> str:
    pandoc = shutil.which("pandoc")
    if not pandoc:
        raise _error("pandoc-missing", "pandoc is not installed or is not on PATH.")
    return pandoc


def _pandoc_fragment(stderr: str, line_ranges: list[tuple[int, int, str]]) -> str:
    match = re.search(r"\bline\s+(\d+)\b", stderr, flags=re.IGNORECASE)
    if not match:
        return "unknown (Pandoc did not report a line)"
    line = int(match.group(1))
    for start, end, fragment in line_ranges:
        if start <= line <= end:
            return fragment
    return f"unknown (Pandoc reported composed line {line})"


def build_bundle(bundle_root: Path, repo_root: Path, output_dir: Path) -> Path:
    bundle = load_bundle(bundle_root)
    diagrams = validate_bundle(bundle)
    reference_doc = (repo_root / "templates" / "reference.docx").resolve()
    if not reference_doc.is_file():
        raise _error("reference-doc-missing", f"Missing shared style file: {reference_doc}", report_id=bundle.report_id)
    pandoc = _require_pandoc()
    destination_dir = output_dir.resolve()
    try:
        destination_dir.relative_to(repo_root.resolve())
    except ValueError as exc:
        raise _error("output-outside-repository", "output directory must stay inside the repository.", report_id=bundle.report_id) from exc
    destination_dir.mkdir(parents=True, exist_ok=True)
    destination = destination_dir / bundle.output_name

    with tempfile.TemporaryDirectory(prefix=f"{bundle.report_id}-") as temp_dir_name:
        temp_dir = Path(temp_dir_name)
        asset_dir = temp_dir / "assets"
        asset_dir.mkdir()
        rendered: dict[Path, Path] = {}
        for fragment, diagram, renderer in diagrams:
            if diagram in rendered:
                continue
            asset_name = hashlib.sha256(str(diagram.relative_to(bundle.root)).encode("utf-8")).hexdigest() + ".png"
            rendered_asset = asset_dir / asset_name
            _download_png(bundle, fragment, diagram, renderer, rendered_asset)
            rendered[diagram] = rendered_asset

        fragment_text = {fragment: fragment.read_text(encoding="utf-8") for fragment in bundle.fragments}
        if any(GIT_HISTORY_MARKER in text for text in fragment_text.values()):
            try:
                history = git_history_entries(repo_root, bundle.root)
            except GitHistoryError as exc:
                raise _error("git-history-unavailable", str(exc), report_id=bundle.report_id) from exc
            fragment_text = {fragment: _replace_git_history(text, history) for fragment, text in fragment_text.items()}

        composed = temp_dir / "composed.md"
        composed_parts: list[str] = []
        line_ranges: list[tuple[int, int, str]] = []
        current_line = 1
        for fragment in bundle.fragments:
            content = _replace_diagrams(bundle, fragment, fragment_text[fragment], rendered).rstrip()
            line_count = max(1, content.count("\n") + 1)
            line_ranges.append((current_line, current_line + line_count - 1, _relative(bundle, fragment)))
            composed_parts.append(content)
            current_line += line_count + 2
        composed_text = "\n\n".join(composed_parts) + "\n"
        composed.write_text(composed_text, encoding="utf-8")
        staged_output = temp_dir / bundle.output_name
        resource_path = os.pathsep.join((str(temp_dir), str(bundle.root)))
        command = [
            pandoc,
            "--from=markdown",
            "--to=docx",
            f"--reference-doc={reference_doc}",
            f"--resource-path={resource_path}",
            "--output", str(staged_output),
            str(composed),
        ]
        try:
            completed = subprocess.run(command, capture_output=True, text=True, check=False)
        except OSError as exc:
            raise _error("pandoc-failed", str(exc), report_id=bundle.report_id) from exc
        if completed.returncode:
            reason = completed.stderr.strip() or completed.stdout.strip() or f"Pandoc exited {completed.returncode}."
            raise _error(
                "pandoc-failed", reason, report_id=bundle.report_id,
                fragment=_pandoc_fragment(reason, line_ranges),
            )
        shutil.move(str(staged_output), destination)

    print(f"[SUCCESS] report_id={bundle.report_id!r} output={str(destination)!r} diagrams={len(rendered)}")
    return destination
