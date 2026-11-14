import React, { useState } from 'react';
import { Link } from 'react-router';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading } from '../../../../shared/ui/components';
import { useLoyaltyApi } from '../../application/useLoyaltyApi';
import { usePublicTargetLabels } from '../../application/useTargetLabels';
import { DiscountText, ScopeBadge, Terms, useDateTime } from '../display';
import { messages } from '../messages';
import { useCursorList } from '../../application/useCursorList';
import '../public/voucher-wallet.css';

/** T070 Kho Voucher: the Member's own saved vouchers with their current availability. Saving holds no quota. */
export default function SavedVouchersPage() {
  const t = useT(messages);
  return (
    <main className="container public-page lp-theme lp-public">
      <p className="eyebrow">MEMBER</p>
      <h1>{t('saved.title')}</h1>
      <RequireSession requiredText={t('session.member')}><SavedList /></RequireSession>
    </main>
  );
}

// Still valid: usable now, or will be once it starts. Everything else (expired, paused, no uses left...) is not.
const VALID = new Set(['ACTIVE', 'SCHEDULED']);
const TABS = ['usable', 'unusable'];
const tabOf = availability => (VALID.has(availability) ? 'usable' : 'unusable');
// "Dùng ngay" opens where the voucher can be used; the code is checked at checkout (FR-012, FR-013).
const useNowPath = scope => (scope === 'WORKSHOP' ? '/workshops' : '/products');

function SavedList() {
  const t = useT(messages);
  const dateTime = useDateTime();
  const api = useLoyaltyApi();
  const list = useCursorList(cursor => api.listSavedVouchers({ cursor }), []);
  const labels = usePublicTargetLabels(api, list.items.map(item => item.promotion));
  const [chosenTab, setChosenTab] = useState(null);
  const counts = { usable: 0, unusable: 0 };
  list.items.forEach(item => { counts[tabOf(item.availability)] += 1; });
  // Open on the valid vouchers unless there are none yet; the Member's own choice wins after that.
  const tab = chosenTab || (counts.usable === 0 && counts.unusable > 0 ? 'unusable' : 'usable');
  const shown = list.items.filter(item => tabOf(item.availability) === tab);
  return (
    <>
      <p className="muted small">{t('saved.hint')}</p>
      {list.items.length > 0 && (
        <div className="voucher-tabs" role="tablist" aria-label={t('saved.tabs')}>
          {TABS.map(key => (
            <button key={key} type="button" role="tab" aria-selected={tab === key} className={tab === key ? 'active' : ''} onClick={() => setChosenTab(key)}>
              {t(`saved.tab.${key}`)} <span className="voucher-tab-count">{counts[key]}</span>
            </button>
          ))}
          <Link className="text-link voucher-tabs-more" to="/promotions">{t('saved.findMore')}</Link>
        </div>
      )}
      {list.loading && list.items.length === 0 && <Loading />}
      {list.error && <ErrorNotice error={list.error} messages={messages} onRetry={list.retry} />}
      {!list.loading && !list.error && list.items.length === 0 && (
        <div className="empty-state"><p>{t('saved.empty')}</p><Link className="button primary" to="/promotions">{t('nav.promotions')}</Link></div>
      )}
      {list.items.length > 0 && shown.length === 0 && <div className="empty-state">{t(`saved.tabEmpty.${tab}`)}</div>}
      <div className="promo-grid">
        {shown.map(({ promotion, savedAt, availability }) => (
          <article className="promo-card" key={promotion.id}>
            <div className="card-head">
              <ScopeBadge scope={promotion.scope} />
              <span className={availability === 'ACTIVE' ? 'status ok' : 'status'}>{t(`availability.${availability}`)}</span>
            </div>
            <h2>{promotion.name}</h2>
            <p className="mono code-chip">{promotion.code}</p>
            <p><DiscountText discount={promotion.discount} /></p>
            <Terms item={promotion} labels={labels} />
            <p className="muted small">{t('saved.savedAt', { at: dateTime(savedAt) })}</p>
            {availability === 'ACTIVE' && <Link className="button primary small voucher-use-now" to={useNowPath(promotion.scope)}>{t('saved.useNow')}</Link>}
          </article>
        ))}
      </div>
      {list.nextCursor && <div className="pager"><button type="button" className="button outline" disabled={list.loading} onClick={list.more}>{t('more')}</button></div>}
    </>
  );
}
