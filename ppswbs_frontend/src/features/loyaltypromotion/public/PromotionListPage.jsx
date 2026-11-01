import React from 'react';
import { Link, useSearchParams } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading } from '../../../shared/ui/components';
import { useLoyaltyApi } from '../api/useLoyaltyApi';
import { DiscountText, ScopeBadge, Terms } from '../display';
import { messages } from '../messages';
import { useCursorList } from '../useCursorList';
import { SaveVoucherButton } from './SaveVoucherButton';

/**
 * T070 public promotions: scope-labelled cards with their current terms. A card is not a quote: whether a voucher
 * applies is decided at checkout. Saving needs a signed-in Member.
 */
export default function PromotionListPage() {
  const t = useT(messages);
  const api = useLoyaltyApi();
  const [params, setParams] = useSearchParams();
  const scope = params.get('scope') || '';
  const list = useCursorList(cursor => api.listPublicPromotions({ scope: scope || undefined, cursor }), [scope]);

  return (
    <main className="container public-page">
      <p className="eyebrow">{t('public.eyebrow')}</p>
      <h1>{t('public.title')}</h1>
      <div className="filter-bar">
        <label>{t('field.scope')}
          <select value={scope} onChange={event => setParams(event.target.value ? { scope: event.target.value } : {})}>
            <option value="">{t('all')}</option>
            <option value="PRODUCT">{t('scope.PRODUCT')}</option>
            <option value="WORKSHOP">{t('scope.WORKSHOP')}</option>
          </select>
        </label>
        <Link className="text-link" to="/me/saved-vouchers">{t('nav.savedVouchers')}</Link>
      </div>
      {list.loading && list.items.length === 0 && <Loading />}
      {list.error && <ErrorNotice error={list.error} messages={messages} onRetry={list.retry} />}
      {!list.loading && !list.error && list.items.length === 0 && <div className="empty-state">{t('public.empty')}</div>}
      <div className="promo-grid">
        {list.items.map(promotion => (
          <article className="promo-card" key={promotion.id}>
            <div className="card-head"><ScopeBadge scope={promotion.scope} /><span className="muted small">{t(`kind.${promotion.kind}`)}</span></div>
            <h2><Link to={`/promotions/${encodeURIComponent(promotion.id)}`}>{promotion.name}</Link></h2>
            {promotion.code && <p className="mono code-chip">{promotion.code}</p>}
            <p><DiscountText discount={promotion.discount} /></p>
            <Terms item={promotion} />
            {promotion.kind === 'VOUCHER' && <SaveVoucherButton promotion={promotion} />}
          </article>
        ))}
      </div>
      {list.nextCursor && <div className="pager"><button type="button" className="button outline" disabled={list.loading} onClick={list.more}>{t('more')}</button></div>}
      <p className="muted small">{t('public.notQuote')}</p>
    </main>
  );
}
