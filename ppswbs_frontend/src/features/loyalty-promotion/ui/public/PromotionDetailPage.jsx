import React from 'react';
import { Link, useParams } from 'react-router';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../../shared/ui/components';
import { useLoyaltyApi } from '../../application/useLoyaltyApi';
import { usePublicTargetLabels } from '../../application/useTargetLabels';
import { useSavedVoucherIds } from '../../application/useSavedVoucherIds';
import { DiscountText, Terms } from '../display';
import { messages } from '../messages';
import { SaveVoucherButton } from './SaveVoucherButton';
import { VoucherTicket } from './VoucherTicket';
import './voucher-wallet.css';

/**
 * T070 "Điều kiện" of one promotion (getPublicPromotion): the ticket, then every condition with the products,
 * packages and branches it applies to (FR-011). Not public any more reads as not found.
 */
export default function PromotionDetailPage() {
  const t = useT(messages);
  const api = useLoyaltyApi();
  const { promotionId } = useParams();
  const promotion = useResource(() => api.getPublicPromotion(promotionId), [promotionId]);
  const labels = usePublicTargetLabels(api, promotion.data ? [promotion.data] : []);
  const wallet = useSavedVoucherIds(api);
  const item = promotion.data;

  return (
    <main className="container public-page lp-theme lp-public">
      <p className="eyebrow"><Link to="/promotions">{t('public.title')}</Link></p>
      {promotion.loading && !item && <Loading />}
      {promotion.error && (promotion.error.status === 404
        ? <><h1>{t('public.notFound')}</h1><Link className="button outline" to="/promotions">{t('back')}</Link></>
        : <ErrorNotice error={promotion.error} messages={messages} onRetry={promotion.reload} />)}
      {item && !promotion.error && (
        <div className="voucher-detail">
          <h1>{item.kind === 'VOUCHER' ? t('detail.title') : item.name}</h1>
          {item.kind === 'VOUCHER' && (
            <VoucherTicket promotion={item} labels={labels} availability={wallet.availabilityOf(item.id)} showConditions={false}
              action={<SaveVoucherButton promotion={item} saved={wallet.isSaved(item.id)} onSaved={wallet.markSaved} />} />
          )}
          <section className="voucher-detail-section">
            <h2>{t('detail.offer')}</h2>
            <p>{item.name} — <DiscountText discount={item.discount} /></p>
          </section>
          <section className="voucher-detail-section">
            <h2>{t('detail.targets')}</h2>
            <Terms item={item} labels={labels} />
          </section>
          <p className="muted small">{t('public.notQuote')}</p>
        </div>
      )}
    </main>
  );
}
