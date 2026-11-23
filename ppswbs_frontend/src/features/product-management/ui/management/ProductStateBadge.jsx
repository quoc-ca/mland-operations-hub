import React from 'react';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { listStateOf } from '../../domain/productRules';
import { messages } from '../messages';

// FR-064: the state column names the product's tab (Session 2026-11-22), so a hidden product never looks like one ready to publish.
// T209: shared by the list, the details page and its publication section, so none of them imports another page for it.
const STATE_TONE = { PUBLISHED: ' ok', APPROVED: ' warn', PENDING: ' warn', REJECTED: ' danger' };

export function ProductStateBadge({ product }) {
  const t = useT(messages);
  const state = listStateOf(product);
  if (state === 'DELETED') return <span className="status">{t('status.deleted')}</span>;
  return <span className={`status${STATE_TONE[state] || ''}`}>{t(`list.tab.${state}`)}</span>;
}
