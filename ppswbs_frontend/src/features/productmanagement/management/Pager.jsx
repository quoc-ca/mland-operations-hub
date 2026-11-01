import React from 'react';
import { useT } from '../../../shared/i18n/I18nProvider';
import { messages } from '../messages';

export function Pager({ page, pageSize, total, onChange }) {
  const t = useT(messages);
  const lastPage = Math.max(0, Math.ceil(Number(total || 0) / (pageSize || 20)) - 1);
  if (lastPage === 0) return null;
  return (
    <nav className="pager" aria-label={t('pager.label')}>
      <button type="button" className="button outline" disabled={page <= 0} onClick={() => onChange(page - 1)}>{t('pager.previous')}</button>
      <span>{t('pager.page', { page: page + 1, pages: lastPage + 1 })}</span>
      <button type="button" className="button outline" disabled={page >= lastPage} onClick={() => onChange(page + 1)}>{t('pager.next')}</button>
    </nav>
  );
}
