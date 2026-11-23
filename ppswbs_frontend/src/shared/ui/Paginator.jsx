import React, { useEffect, useState } from 'react';
import { useT } from '../i18n/I18nProvider';
import { sharedMessages } from './messages';

/**
 * Previous / "page [n] of N" selector / next, for any list. The selector jumps to any page. `page` is zero-based.
 * Renders nothing when everything fits on one page.
 */
export function Paginator({ page, pageCount, onChange, label }) {
  const t = useT(sharedMessages);
  if (!pageCount || pageCount <= 1) return null;
  return (
    <nav className="pager" aria-label={label || t('pager.label')} style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '12px', marginTop: '20px', flexWrap: 'wrap' }}>
      <button type="button" className="button outline small" disabled={page <= 0} onClick={() => onChange(page - 1)}>{t('pager.prev')}</button>
      <span style={{ display: 'inline-flex', flexDirection: 'row', alignItems: 'center', gap: '8px', whiteSpace: 'nowrap', margin: 0 }}>
        {t('pager.page')}
        <select value={page} onChange={event => onChange(Number(event.target.value))} aria-label={t('pager.select')} style={{ width: 'auto', minWidth: '64px', margin: 0 }}>
          {Array.from({ length: pageCount }, (_, index) => <option key={index} value={index}>{index + 1}</option>)}
        </select>
        / {pageCount}
      </span>
      <button type="button" className="button outline small" disabled={page >= pageCount - 1} onClick={() => onChange(page + 1)}>{t('pager.next')}</button>
    </nav>
  );
}

/** Pages a list held in memory: returns the slice to show and the paginator state. Goes back to page 1 when the list changes. */
export function usePaged(items, pageSize, resetKey) {
  const [page, setPage] = useState(0);
  const list = Array.isArray(items) ? items : [];
  const pageCount = Math.max(1, Math.ceil(list.length / pageSize));
  const current = Math.min(page, pageCount - 1);
  useEffect(() => { setPage(0); }, [resetKey]);
  return { pageItems: list.slice(current * pageSize, (current + 1) * pageSize), page: current, pageCount, setPage };
}
