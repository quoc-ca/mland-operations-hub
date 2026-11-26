"""Build a composable Markdown document bundle into a DOCX file.

This module deliberately owns only local generation.  It never accesses Google
Workspace and it never reads or modifies generated DOCX files.
"""

from __future__ import annotations

import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

try:
    from .git_history import GitCommit, GitHistoryError, git_history_entries, git_history_entries_for_paths, markdown_change_history, markdown_template_change_history
except ImportError:  # Supports `python tools/generate_document.py`.
    from git_history import GitCommit, GitHistoryError, git_history_entries, git_history_entries_for_paths, markdown_change_history, markdown_template_change_history

IMAGE_PATTERN = re.compile(r"!\[[^\]]*\]\((?P<target>[^)\s]+)(?:\s+[^)]*)?\)")
LINK_PATTERN = re.compile(r"(?<!!)\[(?P<label>[^\]]+)\]\((?P<target>[^)\s]+)(?:\s+[^)]*)?\)")
RASTER_OR_VECTOR_EXTENSIONS = {".png", ".jpg", ".jpeg", ".gif", ".svg"}
DRIVE_IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg"}
DRIVE_ASSET_PATTERN = re.compile(
    r"(?m)^[ \t]*\{\{(?P<name>[A-Za-z0-9][A-Za-z0-9_-]*)(?P<attributes>(?:[ \t]+[^\s{}]+)*)[ \t]*\}\}[ \t]*$"
)
DRIVE_ASSET_BRACE_PATTERN = re.compile(r"\{\{.*?\}\}")
DRIVE_ASSET_PERCENT_WIDTH = re.compile(r"^(?:[1-9][0-9]?|100)%$")
DRIVE_ASSET_INCH_WIDTH = re.compile(r"^(?:0\.[1-9][0-9]*|[1-9][0-9]*(?:\.[0-9]+)?)in$")
GIT_HISTORY_MARKER = "<!-- AUTO-GENERATED: GIT-CHANGE-HISTORY -->"
USE_CASE_TABLE_REPORT_ID = "report-3-software-requirement-specification"
DOCX_TABLE_STYLE_MARKER = "MOH_Table_"

REPORT_FOOTER_LABELS = {
    "report-1-project-introduction": "Report 1 (Project Introduction)",
    "report-2-project-management-plan": "Report 2 (Project Management Plan)",
    "report-3-software-requirement-specification": "Report 3 (SRS Document)",
    "report-4-software-design-specification": "Report 4 (SDS Document)",
    "report-5.0-test-documentation": "Report 5 (Test Document)",
}

REPORT_COVER_SUBTITLES = {
    "report-1-project-introduction": "Report 1 \u2013 Project Introduction",
    "report-2-project-management-plan": "Report 2 \u2013 Project Management Plan",
    "report-3-software-requirement-specification": "Report 3 \u2013 Software Requirement Specification",
    "report-4-software-design-specification": "Report 4 \u2013 Software Design Specification",
    "report-5.0-test-documentation": "Report 5 \u2013 Software Test Documentation",
}
COVER_DATE_LINE = "\u2013 Hanoi, Aug 2026 \u2013"
COVER_AND_TOC_MARKER = "MOH_COVER_AND_TOC_PLACEHOLDER"
DOCX_DRIVE_IMAGE_TITLE_PREFIX = "MOH_DRIVE_IMAGE:"
WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
DRAWING_NS = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"


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
    change_log: dict[str, Any] | None


@dataclass(frozen=True)
class DriveImageSettings:
    width: str
    align: str


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
    if not isinstance(report_id, str) or not re.fullmatch(r"[a-z0-9][a-z0-9.-]*", report_id):
        raise _error("manifest-invalid", "id must use lowercase letters, digits, periods, and hyphens.")
    if not isinstance(output_name, str) or Path(output_name).name != output_name or not output_name.endswith(".docx"):
        raise _error("manifest-invalid", "output must be a DOCX filename without directories.", report_id=report_id)
    # The folder tree is the document outline.  Teams frequently add, move, or
    # remove sections while drafting, so a hard-coded fragment list would make
    # an otherwise valid build fail until a manifest is edited by hand.
    front_matter = root / "front-matter.md"
    sections = root / "sections"
    if not front_matter.is_file():
        raise _error("fragment-missing", f"Fragment does not exist: {front_matter}", report_id=report_id, fragment="front-matter.md")
    if not sections.is_dir():
        raise _error("sections-missing", f"Missing sections directory: {sections}", report_id=report_id)
    section_fragments = sorted(path for path in sections.rglob("*.md") if path.is_file())
    if not section_fragments:
        raise _error("sections-empty", "sections/ must contain at least one Markdown fragment.", report_id=report_id)
    fragments = [front_matter, *section_fragments]

    change_log_value = data.get("change_log")
    change_log: dict[str, Any] | None = None
    if change_log_value is not None:
        if not isinstance(change_log_value, dict):
            raise _error("manifest-invalid", "change_log must be a mapping.", report_id=report_id)
        history_paths = change_log_value.get("history_paths")
        if change_log_value.get("format") != "template":
            raise _error("manifest-invalid", "change_log.format must be template.", report_id=report_id)
        if not isinstance(history_paths, list) or not history_paths or not all(isinstance(item, str) for item in history_paths):
            raise _error("manifest-invalid", "change_log.history_paths must be a non-empty list of relative paths.", report_id=report_id)
        for history_path in history_paths:
            candidate = Path(history_path)
            if candidate.is_absolute() or ".." in candidate.parts:
                raise _error("manifest-invalid", "change_log.history_paths must stay within the repository.", report_id=report_id)
        default_action = change_log_value.get("default_action", "M")
        if default_action not in {"A", "M", "D"}:
            raise _error("manifest-invalid", "change_log.default_action must be A, M, or D.", report_id=report_id)
        change_log = {"format": "template", "history_paths": tuple(history_paths), "default_action": default_action}
    return Bundle(root, report_id, output_name, tuple(fragments), change_log)


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


def drive_image_placeholders(bundle: Bundle) -> tuple[str, ...]:
    names: list[str] = []
    seen: set[str] = set()
    for fragment in bundle.fragments:
        text = fragment.read_text(encoding="utf-8")
        valid_ranges = [(match.start(), match.end()) for match in DRIVE_ASSET_PATTERN.finditer(text)]
        for match in DRIVE_ASSET_PATTERN.finditer(text):
            try:
                _drive_image_settings(match)
            except ValueError as exc:
                raise _error(
                    "drive-asset-placeholder-invalid",
                    str(exc),
                    report_id=bundle.report_id,
                    fragment=_relative(bundle, fragment),
                ) from exc
        remaining = DRIVE_ASSET_PATTERN.sub("", text)
        if "{{" in remaining or "}}" in remaining:
            raise _error(
                "drive-asset-placeholder-invalid",
                "Drive asset placeholders must stand alone and use {{asset-name}} with ASCII letters, digits, hyphens, or underscores.",
                report_id=bundle.report_id,
                fragment=_relative(bundle, fragment),
            )
        for candidate in DRIVE_ASSET_BRACE_PATTERN.finditer(text):
            if not any(start <= candidate.start() and candidate.end() <= end for start, end in valid_ranges):
                raise _error(
                    "drive-asset-placeholder-invalid",
                    "Drive asset placeholders must stand alone and use {{asset-name}} with ASCII letters, digits, hyphens, or underscores.",
                    report_id=bundle.report_id,
                    fragment=_relative(bundle, fragment),
                )
        for match in DRIVE_ASSET_PATTERN.finditer(text):
            name = match.group("name")
            key = name.casefold()
            if key not in seen:
                names.append(name)
                seen.add(key)
    return tuple(names)


def _drive_image_settings(match: re.Match[str]) -> DriveImageSettings:
    """Parse the optional width and alignment on a Drive image placeholder."""
    attributes: dict[str, str] = {}
    for token in match.group("attributes").split():
        if "=" not in token:
            raise ValueError(f"Invalid Drive image setting {token!r}; expected name=value.")
        key, value = token.split("=", 1)
        if key not in {"width", "align"}:
            raise ValueError(f"Unsupported Drive image setting {key!r}; supported settings are width and align.")
        if key in attributes:
            raise ValueError(f"Drive image setting {key!r} may only be specified once.")
        if not value:
            raise ValueError(f"Drive image setting {key!r} must have a value.")
        attributes[key] = value

    width = attributes.get("width")
    if width is None:
        width = "80%"
    if DRIVE_ASSET_PERCENT_WIDTH.fullmatch(width):
        pass
    elif DRIVE_ASSET_INCH_WIDTH.fullmatch(width):
        inches = float(width.removesuffix("in"))
        if not 0.1 <= inches <= 10:
            raise ValueError("Drive asset width must be an integer from 1% to 100% or a value from 0.1in to 10in.")
    else:
        raise ValueError("Drive asset width must be an integer from 1% to 100% or a value from 0.1in to 10in.")

    align = attributes.get("align", "left")
    if align not in {"left", "center", "right"}:
        raise ValueError("Drive image align must be left, center, or right.")
    return DriveImageSettings(width=width, align=align)


def validate_bundle(bundle: Bundle) -> None:
    """Validate local bundle inputs without rendering or downloading diagrams."""
    for fragment in bundle.fragments:
        relative_fragment = _relative(bundle, fragment)
        text = fragment.read_text(encoding="utf-8")
        drive_image_placeholders(Bundle(bundle.root, bundle.report_id, bundle.output_name, (fragment,), bundle.change_log))
        for target in _image_targets(text):
            asset = _asset_path(bundle, target, fragment)
            suffix = asset.suffix.lower()
            if suffix not in RASTER_OR_VECTOR_EXTENSIONS:
                raise _error(
                    "asset-unsupported", f"Unsupported image extension: {target}",
                    report_id=bundle.report_id, fragment=relative_fragment, diagram=target,
                )
            if not asset.is_file():
                raise _error(
                    "asset-missing", f"Referenced asset does not exist: {target}",
                    report_id=bundle.report_id, fragment=relative_fragment, diagram=target,
                )
        for match in LINK_PATTERN.finditer(text):
            target = match.group("target").strip("<>")
            if target.startswith("assets/diagrams/"):
                raise _error(
                    "diagram-source-unsupported", "Diagram source files are not rendered into documents; use a standalone Drive asset placeholder instead.",
                    report_id=bundle.report_id, fragment=relative_fragment, diagram=target,
                )


def _replace_drive_image_placeholders(text: str, drive_assets: Mapping[str, Path] | None, rendered: Mapping[str, Path]) -> str:
    def replace(match: re.Match[str]) -> str:
        name = match.group("name")
        settings = _drive_image_settings(match)
        if drive_assets is None:
            return f"[Drive asset omitted: {name}]"
        asset = rendered.get(name.casefold())
        if asset is None:
            raise _error("drive-asset-missing", f"No downloaded Drive asset is available for placeholder {name!r}.")
        title = f"{DOCX_DRIVE_IMAGE_TITLE_PREFIX}{settings.align}"
        return f'![{name}](assets/{asset.name} "{title}"){{ width={settings.width} }}'

    return DRIVE_ASSET_PATTERN.sub(replace, text)


def _replace_git_history(text: str, history: tuple[GitCommit, ...], change_log: Mapping[str, Any] | None = None) -> str:
    if change_log and change_log["format"] == "template":
        rendered = markdown_template_change_history(history, default_action=change_log["default_action"])
    else:
        rendered = markdown_change_history(history)
    wrapped = f"::: {{.generated-history}}\n\n{rendered.rstrip()}\n\n:::\n"
    return text.replace(GIT_HISTORY_MARKER, wrapped)


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


def _prepare_front_matter(content: str, report_id: str) -> str:
    """Keep only the logo from front-matter.md and insert the cover/ToC marker for reports."""
    if report_id not in REPORT_COVER_SUBTITLES:
        return content
    logo_lines = [line for line in content.splitlines() if line.strip().startswith("{{") and line.strip().endswith("}}")]
    parts = [*logo_lines, "", COVER_AND_TOC_MARKER] if logo_lines else [COVER_AND_TOC_MARKER]
    return "\n".join(parts)


def _build_cover_and_toc_blocks(
    document: ET.Element, qname: callable, report_id: str
) -> list[ET.Element]:
    """Build cover page and auto-generated Table of Contents OOXML blocks."""
    xml_space = "{http://www.w3.org/XML/1998/namespace}space"

    def make_para(
        text: str = "",
        *,
        align: str | None = None,
        before: str | None = None,
        after: str | None = None,
        bold: bool = False,
        color: str | None = None,
        size_half_pt: str | None = None,
        left_indent: str | None = None,
    ) -> ET.Element:
        p = ET.Element(qname("p"))
        pPr = ET.SubElement(p, qname("pPr"))
        if before is not None or after is not None:
            sp = ET.SubElement(pPr, qname("spacing"))
            if before is not None:
                sp.set(qname("before"), before)
            if after is not None:
                sp.set(qname("after"), after)
        if left_indent is not None:
            ind = ET.SubElement(pPr, qname("ind"))
            ind.set(qname("left"), left_indent)
        if align is not None:
            jc = ET.SubElement(pPr, qname("jc"))
            jc.set(qname("val"), align)
        if text:
            r = ET.SubElement(p, qname("r"))
            rPr = ET.SubElement(r, qname("rPr"))
            if bold:
                ET.SubElement(rPr, qname("b"))
            if color is not None:
                c = ET.SubElement(rPr, qname("color"))
                c.set(qname("val"), color)
            if size_half_pt is not None:
                sz = ET.SubElement(rPr, qname("sz"))
                sz.set(qname("val"), size_half_pt)
                szCs = ET.SubElement(rPr, qname("szCs"))
                szCs.set(qname("val"), size_half_pt)
            t = ET.SubElement(r, qname("t"))
            t.set(xml_space, "preserve")
            t.text = text
        return p

    def make_page_break() -> ET.Element:
        p = ET.Element(qname("p"))
        r = ET.SubElement(p, qname("r"))
        br = ET.SubElement(r, qname("br"))
        br.set(qname("type"), "page")
        return p

    subtitle = REPORT_COVER_SUBTITLES[report_id]
    blocks: list[ET.Element] = [
        make_para("CAPSTONE PROJECT REPORT", align="center", before="2800", after="160", bold=True, color="C00000", size_half_pt="44"),
        make_para(subtitle, align="center", before="0", after="3800", bold=True, color="C00000", size_half_pt="28"),
        make_para(COVER_DATE_LINE, align="center", before="0", after="0", size_half_pt="22"),
        make_page_break(),
        make_para("Table of Contents", before="0", after="200", bold=True, color="C00000", size_half_pt="32"),
    ]

    # Collect Heading1..Heading3 and ensure each has a bookmark for ToC links.
    toc_entries: list[tuple[int, str, str]] = []
    existing_ids = [
        int(el.get(qname("id"), "0"))
        for el in document.findall(".//" + qname("bookmarkStart"))
        if el.get(qname("id"), "").isdigit()
    ]
    bm_id = max(existing_ids, default=0)
    for p in document.findall(".//" + qname("p")):
        st = p.find("./" + qname("pPr") + "/" + qname("pStyle"))
        if st is None:
            continue
        val = st.get(qname("val"), "")
        if val not in {"Heading1", "Heading2", "Heading3"}:
            continue
        heading_text = "".join(t.text or "" for t in p.findall(".//" + qname("t"))).strip()
        if not heading_text:
            continue
        level = int(val[-1])
        bm = p.find("./" + qname("bookmarkStart"))
        if bm is not None and bm.get(qname("name")):
            anchor = bm.get(qname("name"))
        else:
            bm_id += 1
            anchor = f"_Toc_{bm_id}"
            bm_start = ET.Element(qname("bookmarkStart"))
            bm_start.set(qname("id"), str(bm_id))
            bm_start.set(qname("name"), anchor)
            bm_end = ET.Element(qname("bookmarkEnd"))
            bm_end.set(qname("id"), str(bm_id))
            insert_pos = 1 if len(p) > 0 and p[0].tag == qname("pPr") else 0
            p.insert(insert_pos, bm_start)
            p.append(bm_end)
        toc_entries.append((level, heading_text, anchor))

    # Wrap ToC in a native Word SDT + TOC field so Word updates page numbers automatically.
    sdt = ET.Element(qname("sdt"))
    sdtPr = ET.SubElement(sdt, qname("sdtPr"))
    docPartObj = ET.SubElement(sdtPr, qname("docPartObj"))
    docPartGallery = ET.SubElement(docPartObj, qname("docPartGallery"))
    docPartGallery.set(qname("val"), "Table of Contents")
    ET.SubElement(docPartObj, qname("docPartUnique"))
    sdtContent = ET.SubElement(sdt, qname("sdtContent"))

    field_start = ET.SubElement(sdtContent, qname("p"))
    r_begin = ET.SubElement(field_start, qname("r"))
    fc_begin = ET.SubElement(r_begin, qname("fldChar"))
    fc_begin.set(qname("fldCharType"), "begin")
    r_instr = ET.SubElement(field_start, qname("r"))
    instr = ET.SubElement(r_instr, qname("instrText"))
    instr.set(xml_space, "preserve")
    instr.text = ' TOC \\o "1-3" \\h \\z \\u '
    r_sep = ET.SubElement(field_start, qname("r"))
    fc_sep = ET.SubElement(r_sep, qname("fldChar"))
    fc_sep.set(qname("fldCharType"), "separate")

    indents = {1: "0", 2: "360", 3: "720"}
    for level, heading_text, anchor in toc_entries:
        p = ET.SubElement(sdtContent, qname("p"))
        pPr = ET.SubElement(p, qname("pPr"))
        sp = ET.SubElement(pPr, qname("spacing"))
        sp.set(qname("before"), "40" if level == 1 else "20")
        sp.set(qname("after"), "40" if level == 1 else "20")
        ind = ET.SubElement(pPr, qname("ind"))
        ind.set(qname("left"), indents[level])
        hl = ET.SubElement(p, qname("hyperlink"))
        hl.set(qname("anchor"), anchor)
        hl.set(qname("history"), "1")
        r = ET.SubElement(hl, qname("r"))
        rPr = ET.SubElement(r, qname("rPr"))
        if level == 1:
            ET.SubElement(rPr, qname("b"))
        c = ET.SubElement(rPr, qname("color"))
        c.set(qname("val"), "000000")
        sz = ET.SubElement(rPr, qname("sz"))
        sz.set(qname("val"), "20")
        szCs = ET.SubElement(rPr, qname("szCs"))
        szCs.set(qname("val"), "20")
        t = ET.SubElement(r, qname("t"))
        t.set(xml_space, "preserve")
        t.text = heading_text

    field_end = ET.SubElement(sdtContent, qname("p"))
    r_end = ET.SubElement(field_end, qname("r"))
    fc_end = ET.SubElement(r_end, qname("fldChar"))
    fc_end.set(qname("fldCharType"), "end")

    blocks.append(sdt)
    blocks.append(make_page_break())
    return blocks


def _apply_docx_layout(docx_path: Path, report_id: str | None = None) -> tuple[int, int]:
    """Apply table widths, footer label, and injected Drive image alignment in the DOCX."""
    qname = lambda name: f"{{{WORD_NS}}}{name}"
    with zipfile.ZipFile(docx_path, "r") as source:
        entries = [(info, source.read(info.filename)) for info in source.infolist()]

    document_entry = next((index for index, (info, _) in enumerate(entries) if info.filename == "word/document.xml"), None)
    if document_entry is None:
        raise ValueError("DOCX is missing word/document.xml.")

    document_xml = entries[document_entry][1]
    namespace_pairs = ET.iterparse(io.BytesIO(document_xml), events=("start-ns",))
    for _, (prefix, uri) in namespace_pairs:
        ET.register_namespace(prefix, uri)
    document = ET.fromstring(document_xml)
    changed_images = 0
    for paragraph in document.findall(".//" + qname("p")):
        for picture in paragraph.findall(".//" + f"{{{DRAWING_NS}}}docPr"):
            marker = picture.get("title", "")
            if not marker.startswith(DOCX_DRIVE_IMAGE_TITLE_PREFIX):
                continue
            align = marker.removeprefix(DOCX_DRIVE_IMAGE_TITLE_PREFIX)
            if align not in {"left", "center", "right"}:
                raise ValueError(f"Invalid internal Drive image alignment marker: {marker!r}.")
            paragraph_properties = paragraph.find("./" + qname("pPr"))
            if paragraph_properties is None:
                paragraph_properties = ET.Element(qname("pPr"))
                paragraph.insert(0, paragraph_properties)
            justification = paragraph_properties.find("./" + qname("jc"))
            if justification is None:
                justification = ET.Element(qname("jc"))
                paragraph_properties.append(justification)
            justification.set(qname("val"), align)
            picture.set("title", "")
            changed_images += 1

    section = document.find(".//" + qname("sectPr"))
    page_size = section.find("./" + qname("pgSz")) if section is not None else None
    page_margins = section.find("./" + qname("pgMar")) if section is not None else None
    if page_size is None or page_margins is None:
        raise ValueError("DOCX section is missing page size or page margins needed for layout.")
    page_width = int(page_size.get(qname("w"), "0"))
    margins = sum(int(page_margins.get(qname(side), "0")) for side in ("left", "right", "gutter"))
    text_width = page_width - margins
    if text_width <= 0:
        raise ValueError("DOCX page margins leave no printable text width for tables.")

    changed_tables = 0
    for table in document.findall(".//" + qname("tbl")):
        properties = table.find("./" + qname("tblPr"))
        if properties is None:
            continue
        style = properties.find("./" + qname("tblStyle"))
        if style is None:
            continue
        style_id = style.get(qname("val"), "")
        is_metadata_table = style_id == "MetadataTable"
        if is_metadata_table:
            width_hundredths = 10000
        elif style_id.startswith(DOCX_TABLE_STYLE_MARKER):
            marker_width = style_id.removeprefix(DOCX_TABLE_STYLE_MARKER)
            if not marker_width.isdigit():
                raise ValueError(f"Invalid internal table-width marker: {style_id!r}.")
            width_hundredths = int(marker_width)
            if not 100 <= width_hundredths <= 10000:
                raise ValueError(f"Table-width marker is outside 1%–100%: {style_id!r}.")
        else:
            continue
        table_width = properties.find("./" + qname("tblW"))
        if table_width is None:
            table_width = ET.Element(qname("tblW"))
            properties.insert(list(properties).index(style) + 1, table_width)
        table_width.set(qname("type"), "pct")
        # OOXML stores table percentages in fiftieths of one percent: 5000 = 100%.
        table_width.set(qname("w"), str(round(width_hundredths / 2)))
        grid = table.find("./" + qname("tblGrid"))
        grid_columns = grid.findall("./" + qname("gridCol")) if grid is not None else []
        if not grid_columns:
            raise ValueError(f"Table {style_id!r} has no grid columns to size.")
        original_widths = [int(column.get(qname("w"), "0")) for column in grid_columns]
        original_total = sum(original_widths)
        if original_total <= 0:
            raise ValueError(f"Table {style_id!r} has no measurable column widths.")
        target_total = round(text_width * width_hundredths / 10000)
        new_widths = [max(1, round(value * target_total / original_total)) for value in original_widths]
        new_widths[-1] += target_total - sum(new_widths)
        if new_widths[-1] <= 0:
            raise ValueError(f"Table {style_id!r} has more columns than available width units.")
        for column, column_width in zip(grid_columns, new_widths):
            column.set(qname("w"), str(column_width))

        for row in table.findall("./" + qname("tr")):
            column_index = 0
            for cell in row.findall("./" + qname("tc")):
                cell_properties = cell.find("./" + qname("tcPr"))
                span = 1
                if cell_properties is not None:
                    span_element = cell_properties.find("./" + qname("gridSpan"))
                    if span_element is not None:
                        span = int(span_element.get(qname("val"), "1"))
                cell_width = sum(new_widths[column_index:column_index + span])
                column_index += span
                if cell_properties is None:
                    cell_properties = ET.Element(qname("tcPr"))
                    cell.insert(0, cell_properties)
                cell_width_element = cell_properties.find("./" + qname("tcW"))
                if cell_width_element is None:
                    cell_width_element = ET.Element(qname("tcW"))
                    cell_properties.insert(0, cell_width_element)
                cell_width_element.set(qname("type"), "dxa")
                cell_width_element.set(qname("w"), str(cell_width))
        cell_margins = properties.find("./" + qname("tblCellMar"))
        if cell_margins is None:
            cell_margins = ET.Element(qname("tblCellMar"))
            insert_idx = len(list(properties))
            for idx, child in enumerate(list(properties)):
                if child.tag in {qname("tblLook"), qname("tblCaption"), qname("tblDescription")}:
                    insert_idx = idx
                    break
            properties.insert(insert_idx, cell_margins)
        for side in ("top", "left", "bottom", "right"):
            side_el = cell_margins.find("./" + qname(side))
            if side_el is None:
                side_el = ET.Element(qname(side))
                cell_margins.append(side_el)
            side_el.set(qname("w"), "75")
            side_el.set(qname("type"), "dxa")
        if not is_metadata_table:
            style.set(qname("val"), "TableGrid")
        changed_tables += 1

    changed_footers = 0
    footer_label = REPORT_FOOTER_LABELS.get(report_id or "")
    if footer_label:
        from xml.sax.saxutils import escape as xml_escape

        escaped_label = xml_escape(footer_label)
        for index, (info, payload) in enumerate(entries):
            if not (info.filename.startswith("word/footer") and info.filename.endswith(".xml")):
                continue
            footer_text = payload.decode("utf-8")
            updated_text, count = re.subn(
                r"(<w:t\b[^>]*>)\s*Report\s*(</w:t>)",
                lambda m: f"{m.group(1)}{escaped_label}{m.group(2)}",
                footer_text,
            )
            if count > 0:
                entries[index] = (info, updated_text.encode("utf-8"))
                changed_footers += 1

    changed_cover = 0
    if report_id in REPORT_COVER_SUBTITLES:
        body = document.find("./" + qname("body"))
        if body is not None:
            for idx, child in enumerate(list(body)):
                if child.tag == qname("p") and "".join(t.text or "" for t in child.findall(".//" + qname("t"))).strip() == COVER_AND_TOC_MARKER:
                    cover_blocks = _build_cover_and_toc_blocks(document, qname, report_id)
                    body.remove(child)
                    for offset, blk in enumerate(cover_blocks):
                        body.insert(idx + offset, blk)
                    changed_cover = 1
                    break

    if not changed_tables and not changed_images and not changed_footers and not changed_cover:
        return 0, 0

    entries[document_entry] = (
        entries[document_entry][0],
        ET.tostring(document, encoding="utf-8", xml_declaration=True),
    )
    staged_path = docx_path.with_name(docx_path.name + ".layout-tmp")
    try:
        with zipfile.ZipFile(staged_path, "w") as target:
            for info, payload in entries:
                target.writestr(info, payload)
        shutil.move(str(staged_path), docx_path)
    finally:
        if staged_path.exists():
            staged_path.unlink()
    return changed_tables, changed_images


def build_bundle(
    bundle_root: Path, repo_root: Path, output_dir: Path, *, drive_assets: Mapping[str, Path] | None = None
) -> Path:
    bundle = load_bundle(bundle_root)
    validate_bundle(bundle)
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
        rendered_drive_assets: dict[str, Path] = {}
        if drive_assets is not None:
            for name in drive_image_placeholders(bundle):
                asset = drive_assets.get(name.casefold())
                if asset is None or not asset.is_file() or asset.suffix.lower() not in DRIVE_IMAGE_EXTENSIONS:
                    raise _error("drive-asset-missing", f"No valid downloaded Drive asset is available for placeholder {name!r}.", report_id=bundle.report_id)
                target = asset_dir / ("drive-" + hashlib.sha256(name.casefold().encode("utf-8")).hexdigest() + asset.suffix.lower())
                shutil.copyfile(asset, target)
                rendered_drive_assets[name.casefold()] = target

        fragment_text = {fragment: fragment.read_text(encoding="utf-8") for fragment in bundle.fragments}
        if any(GIT_HISTORY_MARKER in text for text in fragment_text.values()):
            try:
                if bundle.change_log:
                    sources = tuple(repo_root / path for path in bundle.change_log["history_paths"])
                    history = git_history_entries_for_paths(repo_root, sources)
                else:
                    history = git_history_entries(repo_root, bundle.root)
            except GitHistoryError as exc:
                raise _error("git-history-unavailable", str(exc), report_id=bundle.report_id) from exc
            fragment_text = {fragment: _replace_git_history(text, history, bundle.change_log) for fragment, text in fragment_text.items()}

        composed = temp_dir / "composed.md"
        composed_parts: list[str] = []
        line_ranges: list[tuple[int, int, str]] = []
        current_line = 1
        for fragment in bundle.fragments:
            raw_content = fragment_text[fragment]
            if fragment.name == "front-matter.md":
                raw_content = _prepare_front_matter(raw_content, bundle.report_id)
            content = _replace_drive_image_placeholders(raw_content, drive_assets, rendered_drive_assets).rstrip()
            source_marker = f"<!-- MOH-SOURCE: {_relative(bundle, fragment)} -->\n\n"
            content = source_marker + content
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
            "--from=markdown+fenced_divs+raw_html",
            "--to=docx",
            f"--reference-doc={reference_doc}",
            f"--resource-path={resource_path}",
        ]
        if bundle.report_id == USE_CASE_TABLE_REPORT_ID:
            use_case_filter = Path(__file__).resolve().with_name("usecase_headings_to_table.lua")
            if not use_case_filter.is_file():
                raise _error(
                    "usecase-filter-missing",
                    f"Missing use-case table filter: {use_case_filter}",
                    report_id=bundle.report_id,
                )
            command.append(f"--lua-filter={use_case_filter}")
        table_layout_filter = Path(__file__).resolve().with_name("markdown_table_layout.lua")
        if not table_layout_filter.is_file():
            raise _error(
                "table-layout-filter-missing",
                f"Missing Markdown table layout filter: {table_layout_filter}",
                report_id=bundle.report_id,
            )
        command.append(f"--lua-filter={table_layout_filter}")
        drive_image_filter = Path(__file__).resolve().with_name("drive_image_layout.lua")
        if not drive_image_filter.is_file():
            raise _error(
                "drive-image-filter-missing",
                f"Missing Drive image layout filter: {drive_image_filter}",
                report_id=bundle.report_id,
            )
        command.append(f"--lua-filter={drive_image_filter}")
        command.extend(("--output", str(staged_output), str(composed)))
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
        try:
            _apply_docx_layout(staged_output, bundle.report_id)
        except (OSError, ValueError, zipfile.BadZipFile, ET.ParseError) as exc:
            raise _error(
                "docx-layout-failed", str(exc), report_id=bundle.report_id,
            ) from exc
        shutil.move(str(staged_output), destination)

    print(f"[SUCCESS] report_id={bundle.report_id!r} output={str(destination)!r}")
    return destination
