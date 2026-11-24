import React from 'react';
import { Link } from 'react-router';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../../../shared/ui/messages';
import { currentReview, reviewGate } from '../../domain/productRules';
import { messages } from '../messages';

/**
 * What a product can do next, as the label and kind of its one state button in the list. A submission waiting for a
 * decision or a rejected review is never published in one click (FR-010): the row links to the initial review instead.
 * Session 2026-11-23: publishing, hiding and showing again act on every branch at once, so they are a Manager's actions
 * only (FR-017); a Staff member gets *Xem lý do* for a rejected review and stops selling at their own branch instead
 * (see {@link branchSaleAction}).
 */
export function nextStateAction(product, isManager = true) {
  if (product.deletedAt) return null;
  if (!isManager) {
    return !product.published && !product.firstPublishedAt && reviewGate(product) === 'REJECTED'
      ? { key: 'rejected', labelKey: 'list.seeReason', style: 'button outline small', link: true } : null;
  }
  if (product.published) return { key: 'hide', labelKey: 'list.hide', style: 'button outline small' };
  // FR-064 (Session 2026-11-22): a hidden product is shown again, worded apart from a first publication.
  if (product.firstPublishedAt) return { key: 'publish', labelKey: 'list.showAgain', style: 'button primary small' };
  const gate = reviewGate(product);
  if (gate === 'PENDING') return { key: 'review', labelKey: 'list.reviewFirst', style: 'button primary small', link: true };
  if (gate === 'REJECTED') return { key: 'rejected', labelKey: 'list.seeReason', style: 'button outline small', link: true };
  // One label for every other never-published product; the review state is shown in the state column instead.
  return { key: 'approvePublish', labelKey: 'list.publish', style: 'button primary small' };
}

/**
 * Session 2026-11-23 (FR-049, FR-051): the Staff row action. Only while the list is filtered on one of the Staff member's
 * branches and the product is sold there (an active branch): stop or resume selling at that branch, the other branches are
 * untouched. Null otherwise (another branch, every branch, a Manager, a deleted product).
 */
export function branchSaleAction(product, { isManager, locationId, myBranches }) {
  if (isManager || !locationId || product.deletedAt || !Array.isArray(myBranches)) return null;
  if (!myBranches.some(branch => String(branch.id) === String(locationId))) return null;
  const branch = (product.branches || []).find(item => String(item.locationId) === String(locationId));
  if (!branch || !branch.active) return null;
  return branch.saleEnabled
    ? { key: 'branchStop', labelKey: 'list.branchStop', style: 'button outline small', locationId: branch.locationId, saleEnabled: true }
    : { key: 'branchResume', labelKey: 'list.branchResume', style: 'button primary small', locationId: branch.locationId, saleEnabled: false };
}

/** True while a Staff member's submission waits for a decision. */
export function awaitingReview(product) {
  return !product.published && !product.deletedAt && currentReview(product)?.state === 'PENDING';
}

/**
 * State action of one row: an explicit button, plus the details page one click away. A Manager gets the publication
 * actions; a Staff member gets the branch sale action passed in `branchAction` (or *Xem lý do*). A product outside a Staff
 * member's branches (T182, `outOfScope`) only links to its details.
 */
export function RowActions({ product, busy, disabled, failed, onChange, isManager = true, outOfScope = false, branchAction = null }) {
  const t = useT(messages);
  const details = `/catalogue-management/products/${encodeURIComponent(product.id)}`;
  const action = outOfScope ? null : (nextStateAction(product, isManager) || branchAction);
  if (!action) return product.deletedAt ? null : <Link className="button outline small" to={details}>{t('list.details')}</Link>;
  if (action.link) {
    // The decision is made on the details page, where every size's price is shown and can be adjusted.
    return <Link className={action.style} to={`${details}#review-title`}>{t(action.labelKey)}</Link>;
  }
  return (
    <>
      <button type="button" className={action.style} disabled={disabled} onClick={() => onChange(product, action.key, action)}>
        {busy ? t('list.working') : t(action.labelKey)}
      </button>
      <Link className="button outline small" to={details}>{t('list.details')}</Link>
      {failed && <RowFailure failed={failed} />}
    </>
  );
}

/**
 * T220: why a row action failed. A known error code (403, branch closed, ...) is named; a 409 also says the row was read
 * again. Any other failure keeps the hint to open the details, where every missing condition is shown.
 */
function RowFailure({ failed }) {
  const t = useT(messages);
  const tShared = useT(sharedMessages);
  const error = failed.error;
  const key = error?.code ? `error.${error.code}` : null;
  const known = key && (t(key) !== key || tShared(key) !== key);
  const text = known ? (t(key) !== key ? t(key) : tShared(key))
    : error?.status === 403 ? tShared('error.forbidden')
      : error?.status === 409 ? tShared('error.stale') : t('list.actionFailed');
  return (
    <span className="muted small" role="alert">
      {text}{failed.reloaded ? ` ${t('list.reloadedLatest')}` : ''}
    </span>
  );
}
