"""Generate a self-contained, local HTML catalogue of active diagram sources.

The output deliberately embeds diagram source in its script data.  It can be
opened using ``file://`` without reading files from the repository at runtime.
PlantUML and Mermaid previews are requested only when the browser renders the
gallery; Drive-hosted placeholders are recorded without downloading them.
"""

from __future__ import annotations

import argparse
import base64
import html
import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable

try:
    from .document_generator import DRIVE_ASSET_PATTERN, MERMAID_FENCE_PATTERN, Bundle, load_bundle
except ImportError:  # Supports `python documents/tools/generate_diagram_gallery.py`.
    from document_generator import DRIVE_ASSET_PATTERN, MERMAID_FENCE_PATTERN, Bundle, load_bundle


ACTIVE_BUNDLE_PATTERN = re.compile(
    r'^\s*source_bundle:\s*["\']?(?P<path>docs/[A-Za-z0-9._/-]+)["\']?\s*$',
    re.MULTILINE,
)
TITLE_PATTERN = re.compile(r"(?mi)^title\s+(?P<title>.+?)\s*$")
HEADING_PATTERN = re.compile(r"(?m)^#{1,6}\s+(?P<title>.+?)\s*$")
CATEGORY_ORDER = ("Context", "Use Cases", "Swimlanes / Workflows", "Activities / Details", "Mermaid", "Other", "Drive-hosted / Manual")


@dataclass(frozen=True)
class GalleryItem:
    title: str
    category: str
    renderer: str
    source: str
    endpoint: str | None
    report_id: str
    path: str
    status: str = "preview"


def active_bundles(documents_root: Path) -> list[Bundle]:
    """Load bundles selected by manifest.yml, preserving manifest order."""
    manifest = documents_root / "manifest.yml"
    paths = [match.group("path") for match in ACTIVE_BUNDLE_PATTERN.finditer(manifest.read_text(encoding="utf-8"))]
    if not paths:
        raise ValueError(f"No document source_bundle entries found in {manifest}")

    bundles: list[Bundle] = []
    for value in paths:
        root = (documents_root / value).resolve()
        try:
            root.relative_to(documents_root.resolve())
        except ValueError as exc:
            raise ValueError(f"Bundle escapes documents workspace: {value}") from exc
        bundles.append(load_bundle(root))
    return bundles


def _category(path: Path, renderer: str) -> str:
    parts = {part.casefold() for part in path.parts}
    if renderer == "mermaid":
        return "Mermaid"
    if "use-cases" in parts:
        return "Use Cases"
    if "workflows" in parts or "swimlanes" in parts:
        return "Swimlanes / Workflows"
    if "details" in parts or "activities" in parts:
        return "Activities / Details"
    if path.stem.casefold() == "context" or "context" in parts:
        return "Context"
    return "Other"


def _display_title(path: Path, source: str) -> str:
    match = TITLE_PATTERN.search(source)
    if match:
        return match.group("title").strip()
    label = re.sub(r"^\d+[-_.]", "", path.stem).replace("-", " ").replace("_", " ")
    return label.title()


def _nearest_heading(markdown: str, position: int) -> str:
    headings = [match.group("title").strip() for match in HEADING_PATTERN.finditer(markdown, 0, position)]
    return headings[-1] if headings else "Drive-hosted diagram"


def _source_items(bundle: Bundle) -> list[GalleryItem]:
    diagram_root = bundle.root / "assets" / "diagrams"
    if not diagram_root.is_dir():
        return []

    items: list[GalleryItem] = []
    for path in sorted(diagram_root.rglob("*")):
        if not path.is_file():
            continue
        suffix = path.suffix.casefold()
        source = path.read_text(encoding="utf-8")
        if suffix == ".puml":
            renderer = "plantuml"
        elif suffix == ".md":
            match = MERMAID_FENCE_PATTERN.fullmatch(source)
            if match is None or not match.group("source").strip():
                continue
            renderer = "mermaid"
            source = match.group("source").strip() + "\n"
        else:
            continue
        relative = path.relative_to(bundle.root).as_posix()
        items.append(
            GalleryItem(
                title=_display_title(path, source),
                category=_category(path.relative_to(diagram_root), renderer),
                renderer=renderer,
                source=source,
                endpoint=bundle.renderers[renderer],
                report_id=bundle.report_id,
                path=relative,
            )
        )
    return items


def _drive_items(bundle: Bundle) -> list[GalleryItem]:
    """Discover semantic-section placeholders, never cover/logo placeholders."""
    items: list[GalleryItem] = []
    seen: set[str] = set()
    for fragment in bundle.fragments:
        if not fragment.relative_to(bundle.root).as_posix().startswith("sections/"):
            continue
        text = fragment.read_text(encoding="utf-8")
        for match in DRIVE_ASSET_PATTERN.finditer(text):
            name = match.group("name")
            key = name.casefold()
            if key in seen:
                continue
            seen.add(key)
            items.append(
                GalleryItem(
                    title=f"{_nearest_heading(text, match.start())} ({name})",
                    category="Drive-hosted / Manual",
                    renderer="drive",
                    source="",
                    endpoint=None,
                    report_id=bundle.report_id,
                    path=fragment.relative_to(bundle.root).as_posix(),
                    status="drive-only",
                )
            )
    return items


def discover_items(bundles: Iterable[Bundle]) -> list[GalleryItem]:
    items: list[GalleryItem] = []
    for bundle in bundles:
        items.extend(_source_items(bundle))
        items.extend(_drive_items(bundle))
    return sorted(items, key=lambda item: (CATEGORY_ORDER.index(item.category), item.title.casefold(), item.path))


def _safe_json(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")


def render_gallery(items: Iterable[GalleryItem]) -> str:
    data = [asdict(item) for item in items]
    category_links = "".join(f'<a href="#category-{index}">{html.escape(category)}</a>' for index, category in enumerate(CATEGORY_ORDER, start=1))
    return f"""<!doctype html>
<html lang="vi">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <meta name="description" content="Danh mục sơ đồ nội bộ của SEP490_G22." />
  <title>Diagram Gallery · SEP490_G22</title>
  <style>
    :root {{ color-scheme: light; --ink:#1c2530; --muted:#617080; --paper:#f7f6f2; --panel:#fff; --line:#d9ddd9; --gold:#9b7d43; --focus:#0f6291; --shadow:0 14px 38px #17253618; }}
    * {{ box-sizing:border-box; }} body {{ margin:0; font:16px/1.5 system-ui,-apple-system,"Segoe UI",sans-serif; color:var(--ink); background:var(--paper); }}
    header {{ padding:2.5rem max(1.25rem,calc((100% - 1100px)/2)); background:linear-gradient(135deg,#18232d,#2c3944); color:#fff; }}
    h1 {{ margin:0; font-size:clamp(1.8rem,4vw,3rem); }} header p {{ max-width:760px; color:#e3e8e6; }}
    nav {{ display:flex; flex-wrap:wrap; gap:.55rem; margin-top:1rem; }} nav a {{ color:#fff; border:1px solid #ffffff66; padding:.35rem .65rem; border-radius:999px; text-decoration:none; font-size:.88rem; }} nav a:focus-visible,button:focus-visible,a:focus-visible {{ outline:3px solid var(--focus); outline-offset:3px; }}
    main {{ max-width:1100px; margin:auto; padding:1.5rem 1.25rem 4rem; }} section {{ scroll-margin-top:1rem; margin-top:2.5rem; }} h2 {{ border-bottom:2px solid var(--gold); padding-bottom:.4rem; }} .grid {{ display:grid; grid-template-columns:repeat(auto-fill,minmax(250px,1fr)); gap:1rem; }}
    .card {{ min-height:250px; display:flex; flex-direction:column; text-align:left; border:1px solid var(--line); background:var(--panel); border-radius:12px; overflow:hidden; box-shadow:0 2px 8px #1725360b; }} button.card {{ cursor:pointer; padding:0; color:inherit; font:inherit; }}
    .thumb {{ height:170px; padding:.65rem; display:grid; place-items:center; background:#eef0ed; border-bottom:1px solid var(--line); }} .thumb img {{ max-width:100%; max-height:100%; object-fit:contain; }}
    .card-body {{ padding:.85rem 1rem 1rem; }} .card h3 {{ margin:0; font-size:1rem; }} .meta {{ color:var(--muted); font-size:.82rem; margin:.25rem 0 0; word-break:break-word; }} .status {{ margin-top:.6rem; color:#80591c; font-size:.86rem; }}
    .drive {{ background:#f5f0e5; justify-content:center; text-align:center; color:#6b542a; }} .empty {{ color:var(--muted); border:1px dashed var(--line); padding:1rem; border-radius:10px; }}
    dialog {{ width:min(96vw,1200px); height:min(94vh,900px); padding:0; border:0; border-radius:14px; box-shadow:var(--shadow); background:var(--panel); }} dialog::backdrop {{ background:#0c1519aa; }} .viewer-head {{ display:flex; flex-wrap:wrap; align-items:center; gap:.5rem; padding:.65rem .9rem; border-bottom:1px solid var(--line); }} .viewer-head h2 {{ flex:1 1 360px; margin:0; border:0; padding:0; font-size:1.05rem; }} button.control {{ border:1px solid var(--line); background:#fff; padding:.35rem .65rem; border-radius:7px; cursor:pointer; }} .stage {{ height:calc(100% - 56px); overflow:hidden; background:#eef0ed; touch-action:none; display:grid; place-items:center; }} .stage img {{ max-width:92%; max-height:92%; transform-origin:center; user-select:none; cursor:grab; }} .stage img.dragging {{ cursor:grabbing; }} .error {{ padding:1rem; color:#9a2626; }}
    @media (max-width:600px) {{ header {{ padding:1.75rem 1.1rem; }} main {{ padding-inline:1rem; }} .grid {{ grid-template-columns:1fr; }} dialog {{ width:100vw; height:100vh; border-radius:0; }} }}
  </style>
</head>
<body>
  <header>
    <h1>Diagram Gallery</h1>
    <p>Công cụ tham khảo nội bộ cho SEP490_G22. Mở trực tiếp từ máy; PUML/Mermaid chỉ được gửi tới renderer công khai khi browser hiển thị ảnh. Ảnh sơ đồ trên Drive được ghi nhận nhưng không tải hoặc preview.</p>
    <nav aria-label="Nhóm sơ đồ">{category_links}</nav>
  </header>
  <main id="gallery"><noscript><p class="empty">Gallery cần JavaScript để render sơ đồ.</p></noscript></main>
  <dialog id="viewer" aria-labelledby="viewer-title"><div class="viewer-head"><h2 id="viewer-title"></h2><button class="control" type="button" data-zoom="out">−</button><button class="control" type="button" data-zoom="reset">100%</button><button class="control" type="button" data-zoom="in">+</button><button class="control" type="button" id="close-viewer">Đóng</button></div><div class="stage" id="stage"><img id="viewer-image" alt="" hidden /><p id="viewer-error" class="error" hidden></p></div></dialog>
  <script id="gallery-data" type="application/json">{_safe_json(data)}</script>
  <script>
    (() => {{
      const items = JSON.parse(document.getElementById('gallery-data').textContent);
      const order = {_safe_json(CATEGORY_ORDER)};
      const gallery = document.getElementById('gallery');
      const viewer = document.getElementById('viewer'); const viewerImage = document.getElementById('viewer-image'); const viewerError = document.getElementById('viewer-error'); const viewerTitle = document.getElementById('viewer-title');
      const state = {{ scale:1, x:0, y:0, dragging:false, startX:0, startY:0 }};
      const hex = source => Array.from(new TextEncoder().encode(source), b => b.toString(16).padStart(2,'0')).join('');
      const base64Url = source => btoa(String.fromCharCode(...new TextEncoder().encode(source))).replace(/[+]/g,'-').replace(/[/]/g,'_').replace(/=+$/,'');
      const imageUrl = item => item.renderer === 'plantuml' ? `${{item.endpoint.replace(/\\/$/,'')}}/png/~h${{hex(item.source)}}` : `${{item.endpoint.replace(/\\/$/,'')}}/img/${{encodeURIComponent(base64Url(item.source))}}?type=png`;
      const escapeHtml = value => String(value).replace(/[&<>"']/g, character => ({{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}}[character]));
      const apply = () => viewerImage.style.transform = `translate(${{state.x}}px, ${{state.y}}px) scale(${{state.scale}})`;
      const reset = () => {{ state.scale=1; state.x=0; state.y=0; apply(); }};
      const zoom = amount => {{ state.scale=Math.min(5,Math.max(.3,state.scale+amount)); apply(); }};
      const error = (item, target) => {{ target.replaceChildren(Object.assign(document.createElement('p'),{{className:'status',textContent:`Không render được ${{item.renderer.toUpperCase()}}. Kiểm tra Internet hoặc endpoint renderer.`}})); }};
      const open = item => {{ reset(); viewerTitle.textContent=item.title; viewerError.hidden=true; viewerImage.hidden=false; viewerImage.alt=item.title; viewerImage.onload=()=>{{ viewerImage.hidden=false; }}; viewerImage.onerror=()=>{{ viewerImage.hidden=true; viewerError.textContent=`Không render được ${{item.renderer.toUpperCase()}}: ${{item.path}}`; viewerError.hidden=false; }}; viewerImage.src=imageUrl(item); viewer.showModal(); }};
      for (const [index, category] of order.entries()) {{
        const group=items.filter(item=>item.category===category); const section=document.createElement('section'); section.id=`category-${{index+1}}`; section.innerHTML=`<h2>${{category}}</h2>`; const grid=document.createElement('div'); grid.className='grid';
        if (!group.length) {{ grid.innerHTML='<p class="empty">Chưa có source diagram hợp lệ trong nhóm này.</p>'; }}
        for (const item of group) {{
          const body=`<div class="card-body"><h3>${{escapeHtml(item.title)}}</h3><p class="meta">${{item.renderer === 'drive' ? 'Drive placeholder' : item.renderer.toUpperCase()}} · ${{escapeHtml(item.report_id)}}</p><p class="meta">${{escapeHtml(item.path)}}</p></div>`;
          if (item.status === 'drive-only') {{ const card=document.createElement('article'); card.className='card'; card.innerHTML=`<div class="thumb drive">Có trên Drive<br><small>Không preview trong gallery</small></div>${{body}}`; grid.append(card); continue; }}
          const card=document.createElement('button'); card.type='button'; card.className='card'; card.setAttribute('aria-label',`Mở ${{item.title}}`); const thumb=document.createElement('div'); thumb.className='thumb'; const img=document.createElement('img'); img.alt=`Preview: ${{item.title}}`; img.loading='lazy'; img.src=imageUrl(item); img.onerror=()=>error(item,thumb); thumb.append(img); card.append(thumb); card.insertAdjacentHTML('beforeend',body); card.addEventListener('click',()=>open(item)); grid.append(card);
        }}
        section.append(grid); gallery.append(section);
      }}
      document.getElementById('close-viewer').addEventListener('click',()=>viewer.close());
      document.querySelectorAll('[data-zoom]').forEach(button=>button.addEventListener('click',()=>{{ const value=button.dataset.zoom; value==='reset'?reset():zoom(value==='in'?.25:-.25); }}));
      document.getElementById('stage').addEventListener('wheel',event=>{{ event.preventDefault(); zoom(event.deltaY<0?.15:-.15); }},{{passive:false}});
      viewerImage.addEventListener('pointerdown',event=>{{ state.dragging=true; state.startX=event.clientX-state.x; state.startY=event.clientY-state.y; viewerImage.classList.add('dragging'); viewerImage.setPointerCapture(event.pointerId); }});
      viewerImage.addEventListener('pointermove',event=>{{ if(!state.dragging)return; state.x=event.clientX-state.startX; state.y=event.clientY-state.startY; apply(); }});
      const stop=()=>{{ state.dragging=false; viewerImage.classList.remove('dragging'); }}; viewerImage.addEventListener('pointerup',stop); viewerImage.addEventListener('pointercancel',stop);
      document.addEventListener('keydown',event=>{{ if(!viewer.open)return; if(event.key==='+')zoom(.25); if(event.key==='-')zoom(-.25); if(event.key==='0')reset(); }});
    }})();
  </script>
</body>
</html>
"""


def generate(repo_root: Path, output: Path) -> list[GalleryItem]:
    items = discover_items(active_bundles(repo_root / "documents"))
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(render_gallery(items), encoding="utf-8")
    return items


def main() -> None:
    repo_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description="Generate the local static diagram gallery.")
    parser.add_argument("--output", type=Path, default=repo_root / "local-notes" / "diagram-gallery.html")
    args = parser.parse_args()
    output = args.output.resolve()
    items = generate(repo_root, output)
    print(f"[SUCCESS] output={str(output)!r} diagrams={sum(item.status == 'preview' for item in items)} drive_only={sum(item.status == 'drive-only' for item in items)}")


if __name__ == "__main__":
    main()
