import React from 'react';
import { Link } from 'react-router';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { currentReview, reviewGate } from '../../domain/productRules';
import { messages } from '../messages';

/**
 * What a product can do next, as the label and kind of its one state button in the list. A submission waiting for a
 * decision or a rejected review is never published in one click (FR-010): the row links to the initial review instead.
 */
export function nextStateAction(product) {
  if (product.deletedAt) return null;
  if (product.published) return { key: 'hide', labelKey: 'list.hide', style: 'button outline small' };
  if (product.firstPublishedAt) return { key: 'publish', labelKey: 'list.publish', style: 'button primary small' };
  const gate = reviewGate(product);
  if (gate === 'PENDING') return { key: 'review', labelKey: 'list.reviewFirst', style: 'button primary small', link: true };
  if (gate === 'REJECTED') return { key: 'rejected', labelKey: 'list.seeReason', style: 'button outline small', link: true };
  // One label for every other never-published product; the review state is shown in the state column instead.
  return { key: 'approvePublish', labelKey: 'list.publish', style: 'button primary small' };
}

/** True while a Staff member's submission waits for a decision. */
export function awaitingReview(product) {
  return !product.published && !product.deletedAt && currentReview(product)?.state === 'PENDING';
}

/** State action of one row for a Manager: an explicit button, plus the details page one click away. */
export function RowActions({ product, busy, disabled, failed, onChange }) {
  const t = useT(messages);
  const action = nextStateAction(product);
  if (!action) return null;
  const details = `/catalogue-management/products/${encodeURIComponent(product.id)}`;
  if (action.link) {
    // The decision is made on the details page, where every size's price is shown and can be adjusted.
    return <Link className={action.style} to={`${details}#review-title`}>{t(action.labelKey)}</Link>;
  }
  return (
    <>
      <button type="button" className={action.style} disabled={disabled} onClick={() => onChange(product, action.key)}>
        {busy ? t('list.working') : t(action.labelKey)}
      </button>
      <Link className="button outline small" to={details}>{t('list.details')}</Link>
      {failed && <span className="muted small" role="alert">{t('list.actionFailed')}</span>}
    </>
  );
}
