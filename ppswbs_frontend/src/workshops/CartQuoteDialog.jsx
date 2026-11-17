import React, { useEffect, useRef } from 'react';

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * A modal window over the cart page for the quote and its confirmation. Opening moves focus into it, Tab stays inside,
 * Escape or a click on the dim background closes it (not while the booking is being confirmed), the page behind does
 * not scroll, and focus returns to the control that opened it.
 */
export function CartQuoteDialog({ open, title, busy = false, onClose, children }) {
  const dialogRef = useRef(null);
  const returnFocusTo = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    returnFocusTo.current = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    dialogRef.current?.focus();
    return () => {
      document.body.style.overflow = previousOverflow;
      const target = returnFocusTo.current;
      if (target && typeof target.focus === 'function' && document.contains(target)) target.focus();
    };
  }, [open]);

  if (!open) return null;

  const onKeyDown = event => {
    if (event.key === 'Escape' && !busy) {
      event.stopPropagation();
      onClose?.();
      return;
    }
    if (event.key !== 'Tab') return;
    const items = Array.from(dialogRef.current?.querySelectorAll(FOCUSABLE) || []).filter(node => !node.closest('[hidden]'));
    if (items.length === 0) { event.preventDefault(); dialogRef.current?.focus(); return; }
    const first = items[0];
    const last = items[items.length - 1];
    if (event.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.current)) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  };

  return (
    <div className="quote-dialog-backdrop" onMouseDown={event => { if (event.target === event.currentTarget && !busy) onClose?.(); }}>
      <div ref={dialogRef} className="quote-dialog" role="dialog" aria-modal="true" aria-label={title} tabIndex={-1} onKeyDown={onKeyDown}>
        <button type="button" className="quote-dialog-close" aria-label="Đóng báo giá" disabled={busy} onClick={() => onClose?.()}>×</button>
        {children}
      </div>
    </div>
  );
}
