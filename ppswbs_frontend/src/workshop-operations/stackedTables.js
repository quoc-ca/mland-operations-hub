// Mobile card layout for `.ops-table`: CSS shows each row as a card and reads each cell's
// caption from `data-label`. This copies the header text into those attributes so the page
// markup stays unchanged. Presentation only; it never changes cell content.
export function labelTableCells(root = document) {
  root.querySelectorAll('table.ops-table').forEach((table) => {
    const heads = Array.from(table.querySelectorAll('thead th')).map((th) => th.textContent.trim());
    if (!heads.length) return;
    table.querySelectorAll('tbody tr').forEach((row) => {
      Array.from(row.children).forEach((cell, index) => {
        if (cell.tagName !== 'TD' || cell.hasAttribute('colspan')) return;
        const label = heads[index] || '';
        if (cell.getAttribute('data-label') !== label) cell.setAttribute('data-label', label);
      });
    });
  });
}

let installed = false;

export function installStackedTableLabels() {
  if (installed || typeof document === 'undefined' || typeof MutationObserver === 'undefined') return;
  installed = true;
  let queued = false;
  const run = () => { queued = false; labelTableCells(); };
  new MutationObserver(() => {
    if (!queued) { queued = true; requestAnimationFrame(run); }
  }).observe(document.body, { childList: true, subtree: true });
  labelTableCells();
}
