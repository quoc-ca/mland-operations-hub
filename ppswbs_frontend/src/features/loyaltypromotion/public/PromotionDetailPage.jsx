import React from 'react';
import { Link, useParams } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { useLoyaltyApi } from '../api/useLoyaltyApi';
import { DiscountText, ScopeBadge, Terms } from '../display';
import { messages } from '../messages';
import { SaveVoucherButton } from './SaveVoucherButton';

/** T070 public detail of one promotion (getPublicPromotion); not public any more reads as not found. */
export default function PromotionDetailPage() {
  const t = useT(messages);
  const api = useLoyaltyApi();
  const { promotionId } = useParams();
  const promotion = useResource(() => api.getPublicPromotion(promotionId), [promotionId]);

  return (
    <main className="container public-page">
      <p className="eyebrow"><Link to="/promotions">{t('public.eyebrow')}</Link></p>
      {promotion.loading && !promotion.data && <Loading />}
      {promotion.error && (promotion.error.status === 404
        ? <><h1>{t('public.notFound')}</h1><Link className="button outline" to="/promotions">{t('back')}</Link></>
        : <ErrorNotice error={promotion.error} messages={messages} onRetry={promotion.reload} />)}
      {promotion.data && !promotion.error && (
        <>
          <h1>{promotion.data.name}</h1>
          <section className="workspace-card">
            <div className="card-head"><ScopeBadge scope={promotion.data.scope} /><span className="muted">{t(`kind.${promotion.data.kind}`)}</span></div>
            {promotion.data.code && <p className="mono code-chip">{promotion.data.code}</p>}
            <p><strong><DiscountText discount={promotion.data.discount} /></strong></p>
            <Terms item={promotion.data} />
            {promotion.data.kind === 'VOUCHER' && <div className="form-actions"><SaveVoucherButton promotion={promotion.data} /></div>}
          </section>
          <p className="muted small">{t('public.notQuote')}</p>
        </>
      )}
    </main>
  );
}
