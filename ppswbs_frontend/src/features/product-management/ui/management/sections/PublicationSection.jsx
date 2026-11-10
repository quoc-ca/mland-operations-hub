import React from 'react';
import { useFormat, useT } from '../../../../../shared/i18n/I18nProvider';
import { ErrorNotice } from '../../../../../shared/ui/components';
import { ACTIONS } from '../../../application/useCatalogueCommand';
import { messages } from '../../messages';
import { effectiveProposal, missingRequirements, reviewGate } from '../../../domain/productRules';
import { publishWithoutReview } from '../../../application/publishNow';
import { useRole } from '../../../../../shared/auth/useRole';
import { useProductCommand } from '../ProductDetailPage';
import { NoticeAt } from '../notices';

/**
 * T040 publication: shows per size the proposed, approved and applied amounts apart, then publishes or hides.
 * Missing prerequisites come back as PUBLICATION_PREREQUISITES_MISSING. Publishing does not open a checkout and
 * stock is not a condition. Hiding keeps every size and price.
 */
export function PublicationSection({ product, api, reload, setNotice, deleted, flagMissing = () => {} }) {
  const t = useT(messages);
  const format = useFormat();
  const command = useProductCommand({ reload, setNotice });
  const target = !product.published;
  const isManager = useRole() === 'MANAGER';
  // A Manager skips the initial review: submit, approve the proposed prices and apply them, then publish, each its own command.
  // Not for a submission waiting for a decision or a rejected review: those are decided in the initial review (FR-010).
  const gate = isManager ? reviewGate(product) : null;
  const canPublishNow = isManager && !product.published && !product.firstPublishedAt && !deleted && !gate;
  const publishNow = async () => {
    if (Object.keys(missingRequirements(product)).length) { flagMissing(); return; }
    const send = () => publishWithoutReview(api, product);
    const outcome = await command.run(send, { key: 'publication.published', at: 'publication' }, ACTIONS.publish);
    if (outcome.error?.code === 'PUBLICATION_PREREQUISITES_MISSING' || outcome.error?.fieldErrors?.length) flagMissing();
  };
  const toggle = async () => {
    const outcome = await command.run(key => api.setPublication(key, product.id, product.version, target), { key: target ? 'publication.published' : 'publication.hidden', at: 'publication' }, target ? ACTIONS.publish : ACTIONS.unpublish);
    if (outcome.error?.code === 'PUBLICATION_PREREQUISITES_MISSING') flagMissing();
  };

  return (
    <section className="workspace-card" aria-labelledby="publication-title">
      <div className="card-head">
        <h2 id="publication-title">{t('section.publication')}</h2>
        <span className={product.published ? 'status ok' : 'status'}>{product.published ? t('status.published') : t('status.unpublished')}</span>
      </div>
      <div className="data-table" role="table" aria-label={t('section.publication')}>
        <div className="data-row head decision" role="row">
          <span role="columnheader">{t('field.size')}</span><span role="columnheader">{t('price.proposed')}</span>
          <span role="columnheader">{t('price.approved')}</span><span role="columnheader">{t('variant.appliedPrice')}</span>
        </div>
        {(product.variants || []).map(variant => {
          const proposal = effectiveProposal(variant);
          return (
            <div className="data-row decision" role="row" key={variant.id}>
              <span role="cell">{variant.size}</span>
              <span role="cell">{format.money(proposal?.proposedPrice)}</span>
              <span role="cell">{format.money(proposal?.approvedPrice)}</span>
              <span role="cell">{variant.appliedPrice ? format.money(variant.appliedPrice) : <span className="muted">{t('publication.notApplied')}</span>}</span>
            </div>
          );
        })}
      </div>
      {gate ? (
        <p className="notice">
          {t(gate === 'PENDING' ? 'publication.managerReviewFirst' : 'publication.managerRejected')}{' '}
          <a href="#review-title">{t('publication.goToReview')}</a>
        </p>
      ) : <p className="muted small">{t(canPublishNow ? 'publication.managerHint' : 'publication.hint')}</p>}
      {canPublishNow && (
        <button type="button" className="button primary" disabled={command.pending} onClick={publishNow}>{t('publication.publishNow')}</button>
      )}
      {!deleted && !canPublishNow && !gate && (
        <button type="button" className={target ? 'button primary' : 'button outline'} disabled={command.pending} onClick={toggle}>
          {target ? t('publication.publish') : t('publication.hide')}
        </button>
      )}
      <ErrorNotice error={command.error} messages={messages} onRetry={command.retry} onReload={reload} />
      <NoticeAt at="publication" />
    </section>
  );
}
