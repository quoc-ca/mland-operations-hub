import React, { useState } from 'react';

/**
 * Session 2026-11-22: a section of the details page that folds like the accordions of seller centres. The whole heading
 * row is the toggle: title on the left, a short summary and a chevron on the right. The heading keeps only the title as
 * its accessible name (the summary is repeated in the content), so "Đề xuất giá" is still found as that heading.
 * `forceOpen` keeps it open while something in it must be seen (a missing requirement).
 */
export function useCollapsible(defaultOpen = true) {
  const [open, setOpen] = useState(defaultOpen);
  return [open, () => setOpen(value => !value)];
}

export function CollapsibleHead({ id, title, summary, open, onToggle, level = 2 }) {
  const Heading = `h${level}`;
  return (
    <Heading id={id} className="collapsible-heading">
      {/* T232: the body exists only while open, so aria-controls points at it only then. Folded, the summary (waiting
          count, price status) is the only place that information is shown, so it describes the toggle without becoming
          part of the heading's name; open, it is repeated in the content and hidden from assistive technology. */}
      <button type="button" className={`collapsible-toggle${open ? ' open' : ''}`} aria-expanded={open}
        aria-label={typeof title === 'string' ? title : undefined}
        aria-controls={open ? `${id}-body` : undefined} aria-describedby={!open && summary ? `${id}-summary` : undefined} onClick={onToggle}>
        <span className="collapsible-title">{title}</span>
        {summary && <span className="collapsible-summary" id={`${id}-summary`} aria-hidden="true">{summary}</span>}
        <svg className="collapsible-chevron" aria-hidden="true" viewBox="0 0 20 20" width="16" height="16"><path d="M5.5 7.5 10 12l4.5-4.5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>
      </button>
    </Heading>
  );
}
