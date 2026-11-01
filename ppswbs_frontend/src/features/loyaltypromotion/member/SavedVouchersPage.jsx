import React from 'react';
import { Link } from 'react-router';
import { RequireSession } from '../../../shared/auth/RequireSession';
import { useFormat, useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading } from '../../../shared/ui/components';
import { useLoyaltyApi } from '../api/useLoyaltyApi';
import { DiscountText, ScopeBadge, Terms } from '../display';
import { messages } from '../messages';
import { useCursorList } from '../useCursorList';

/** T070 Kho Voucher: the Member's own saved vouchers with their current availability. Saving holds no quota. */
export default function SavedVouchersPage() {
  const t = useT(messages);
  return (
    <main className="container public-page">
      <p className="eyebrow">MEMBER</p>
      <h1>{t('saved.title')}</h1>
      <RequireSession requiredText={t('session.member')}><SavedList /></RequireSession>
    </main>
  );
}

function SavedList() {
  const t = useT(messages);
  const format = useFormat();
  const api = useLoyaltyApi();
  const list = useCursorList(cursor => api.listSavedVouchers({ cursor }), []);
  return (
    <>
      <p className="muted small">{t('saved.hint')}</p>
      {list.loading && list.items.length === 0 && <Loading />}
      {list.error && <ErrorNotice error={list.error} messages={messages} onRetry={list.retry} />}
      {!list.loading && !list.error && list.items.length === 0 && (
        <div className="empty-state"><p>{t('saved.empty')}</p><Link className="button primary" to="/promotions">{t('nav.promotions')}</Link></div>
      )}
      <div className="promo-grid">
        {list.items.map(({ promotion, savedAt, availability }) => (
          <article className="promo-card" key={promotion.id}>
            <div className="card-head">
              <ScopeBadge scope={promotion.scope} />
              <span className={availability === 'ACTIVE' ? 'status ok' : 'status'}>{t(`availability.${availability}`)}</span>
            </div>
            <h2>{promotion.name}</h2>
            <p className="mono code-chip">{promotion.code}</p>
            <p><DiscountText discount={promotion.discount} /></p>
            <Terms item={promotion} />
            <p className="muted small">{t('saved.savedAt', { at: format.dateTime(savedAt) })}</p>
          </article>
        ))}
      </div>
      {list.nextCursor && <div className="pager"><button type="button" className="button outline" disabled={list.loading} onClick={list.more}>{t('more')}</button></div>}
    </>
  );
}
