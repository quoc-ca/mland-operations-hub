import React from 'react';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { messages } from '../messages';

/** Server-side paging: previous / "page [n] of N" selector (jump to any page) / next. `page` is zero-based. */
export function Pager({ page, pageSize, total, onChange }) {
  const t = useT(messages);
  const lastPage = Math.max(0, Math.ceil(Number(total || 0) / (pageSize || 20)) - 1);
  if (lastPage === 0) return null;
  return (
    <nav className="pager" aria-label={t('pager.label')}>
      <button type="button" className="button outline" disabled={page <= 0} onClick={() => onChange(page - 1)}>{t('pager.previous')}</button>
      <span style={{ display: 'inline-flex', flexDirection: 'row', alignItems: 'center', gap: '8px', whiteSpace: 'nowrap', margin: 0 }}>
        {t('pager.pageWord')}
        <select value={page} onChange={event => onChange(Number(event.target.value))} aria-label={t('pager.choose')} style={{ width: 'auto', minWidth: '64px', margin: 0 }}>
          {Array.from({ length: lastPage + 1 }, (_, index) => <option key={index} value={index}>{index + 1}</option>)}
        </select>
        / {lastPage + 1}
      </span>
      <button type="button" className="button outline" disabled={page >= lastPage} onClick={() => onChange(page + 1)}>{t('pager.next')}</button>
    </nav>
  );
}
