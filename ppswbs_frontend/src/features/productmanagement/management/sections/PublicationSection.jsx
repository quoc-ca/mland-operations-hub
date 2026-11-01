import React from 'react';
import { useFormat, useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice } from '../../../../shared/ui/components';
import { messages } from '../../messages';
import { effectiveProposal } from '../../rules';
import { useProductCommand } from '../ProductDetailPage';

/**
 * T040 publication: shows per size the proposed, approved and applied amounts apart, then publishes or hides.
 * Missing prerequisites come back as PUBLICATION_PREREQUISITES_MISSING. Publishing does not open a checkout and
 * stock is not a condition. Hiding keeps every size and price.
 */
export function PublicationSection({ product, api, reload, setNotice, deleted }) {
  const t = useT(messages);
  const format = useFormat();
  const command = useProductCommand({ reload, setNotice });
  const target = !product.published;
  const toggle = () => command.run(key => api.setPublication(key, product.id, product.version, target), target ? 'publication.published' : 'publication.hidden');

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
      <p className="muted small">{t('publication.hint')}</p>
      {!deleted && (
        <button type="button" className={target ? 'button primary' : 'button outline'} disabled={command.pending} onClick={toggle}>
          {target ? t('publication.publish') : t('publication.hide')}
        </button>
      )}
      <ErrorNotice error={command.error} messages={messages} onRetry={command.retry} onReload={reload} />
    </section>
  );
}
