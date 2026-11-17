import React from 'react';
import { Link, useSearchParams } from 'react-router';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading } from '../../../../shared/ui/components';
import { useLoyaltyApi } from '../../application/useLoyaltyApi';
import { usePublicTargetLabels } from '../../application/useTargetLabels';
import { messages } from '../messages';
import { useCursorList } from '../../application/useCursorList';
import { useSavedVoucherIds } from '../../application/useSavedVoucherIds';
import { SaveVoucherButton } from './SaveVoucherButton';
import { VoucherTicket } from './VoucherTicket';
import './voucher-wallet.css';

// Kho Voucher tabs: every public voucher, or only those for products or for workshops (the list's scope query).
const SCOPES = ['', 'PRODUCT', 'WORKSHOP'];

/**
 * T070 Kho Voucher: the public vouchers that are running now (the server lists only enabled programmes inside their
 * window), as tickets with "Lưu" and "Điều kiện". Automatic discounts need no saving and are shown on product and
 * package pages, so they are left out here. A ticket is not a quote: whether a voucher applies is decided at checkout.
 */
export default function PromotionListPage() {
  const t = useT(messages);
  const api = useLoyaltyApi();
  const [params, setParams] = useSearchParams();
  const scope = params.get('scope') || '';
  const list = useCursorList(cursor => api.listPublicPromotions({ scope: scope || undefined, cursor }), [scope]);
  const vouchers = list.items.filter(item => item.kind === 'VOUCHER');
  const labels = usePublicTargetLabels(api, vouchers);
  const wallet = useSavedVoucherIds(api);

  return (
    <main className="container public-page lp-theme lp-public">
      <p className="eyebrow">{t('public.eyebrow')}</p>
      <h1>{t('public.title')}</h1>
      <div className="voucher-tabs" role="tablist" aria-label={t('public.tabs')}>
        {SCOPES.map(value => (
          <button key={value || 'ALL'} type="button" role="tab" aria-selected={scope === value} className={scope === value ? 'active' : ''}
            onClick={() => setParams(value ? { scope: value } : {})}>
            {value ? t(`scope.${value}`) : t('all')}
          </button>
        ))}
        <Link className="text-link voucher-tabs-more" to="/me/saved-vouchers">{t('nav.savedVouchers')}</Link>
      </div>
      {list.loading && list.items.length === 0 && <Loading />}
      {list.error && <ErrorNotice error={list.error} messages={messages} onRetry={list.retry} />}
      {!list.loading && !list.error && vouchers.length === 0 && !list.nextCursor && <div className="empty-state">{t('public.emptyVouchers')}</div>}
      <div className="voucher-ticket-grid">
        {vouchers.map(promotion => (
          <VoucherTicket key={promotion.id} promotion={promotion} labels={labels} availability={wallet.availabilityOf(promotion.id)}
            action={<SaveVoucherButton promotion={promotion} saved={wallet.isSaved(promotion.id)} onSaved={wallet.markSaved} />} />
        ))}
      </div>
      {list.nextCursor && <div className="pager"><button type="button" className="button outline" disabled={list.loading} onClick={list.more}>{t('more')}</button></div>}
      <p className="muted small">{t('public.notQuote')}</p>
    </main>
  );
}
