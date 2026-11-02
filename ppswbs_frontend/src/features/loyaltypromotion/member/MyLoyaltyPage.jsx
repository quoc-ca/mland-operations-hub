import React from 'react';
import { Link } from 'react-router';
import { RequireSession } from '../../../shared/auth/RequireSession';
import { useFormat, useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../shared/ui/components';
import { useLoyaltyApi } from '../api/useLoyaltyApi';
import { messages } from '../messages';
import { useCursorList } from '../useCursorList';

/**
 * T040 My Loyalty: the Member's own balance, held and available points, the conversion rules and the history.
 * A failed read never keeps showing old numbers as confirmed; an empty history differs from a load error.
 * There is no purge or expiry screen (points do not expire, history is kept).
 */
export default function MyLoyaltyPage() {
  const t = useT(messages);
  return (
    <main className="container public-page">
      <p className="eyebrow">MEMBER</p>
      <h1>{t('loyalty.title')}</h1>
      <RequireSession requiredText={t('session.member')}><LoyaltyContent /></RequireSession>
    </main>
  );
}

function LoyaltyContent() {
  const t = useT(messages);
  const format = useFormat();
  const api = useLoyaltyApi();
  const account = useResource(() => api.getOwnAccount(), []);
  const history = useCursorList(cursor => api.listOwnTransactions({ cursor }), []);
  const data = account.error ? null : account.data;
  const policy = data?.policy;

  return (
    <>
      {account.loading && !data && <Loading />}
      {account.error && <ErrorNotice error={account.error} messages={messages} onRetry={account.reload} />}
      {data && (
        <section className="workspace-card">
          <div className="points-grid">
            <div><span>{t('loyalty.available')}</span><strong>{format.number(data.availablePoints)}</strong></div>
            <div><span>{t('loyalty.balance')}</span><strong>{format.number(data.balancePoints)}</strong></div>
            <div><span>{t('loyalty.held')}</span><strong>{format.number(data.heldPoints)}</strong></div>
          </div>
          <p className="muted small">{t('loyalty.heldHint')}</p>
          {policy && (
            <ul className="rules">
              <li>{t('policy.earnText', { amount: format.money(policy.earnUnitAmount) })}</li>
              <li>{t('policy.redeemText', { amount: format.money(policy.redeemPointValue) })}</li>
              <li>{t('loyalty.minRedeem', { points: format.number(policy.minRedeemPoints) })}</li>
              <li>{t('loyalty.maxRedeem', { percent: policy.maxRedeemPercent })}</li>
            </ul>
          )}
          <p className="actions"><Link className="text-link" to="/me/saved-vouchers">{t('nav.savedVouchers')}</Link></p>
        </section>
      )}

      <section className="workspace-card">
        <h2>{t('loyalty.history')}</h2>
        {history.loading && history.items.length === 0 && <Loading />}
        {history.error && <ErrorNotice error={history.error} messages={messages} onRetry={history.retry} />}
        {!history.loading && !history.error && history.items.length === 0 && <p className="muted">{t('loyalty.noHistory')}</p>}
        {history.items.length > 0 && (
          <div className="data-table" role="table" aria-label={t('loyalty.history')}>
            <div className="data-row head ledger" role="row">
              <span role="columnheader">{t('loyalty.when')}</span><span role="columnheader">{t('loyalty.kind')}</span>
              <span role="columnheader">{t('loyalty.source')}</span><span role="columnheader">{t('loyalty.balanceChange')}</span>
              <span role="columnheader">{t('loyalty.heldChange')}</span>
            </div>
            {history.items.map(entry => (
              <div className="data-row ledger" role="row" key={entry.id}>
                <span role="cell">{format.dateTime(entry.occurredAt)}</span>
                <span role="cell">{t(`ledger.${entry.kind}`)}</span>
                <span role="cell">{t(`source.${entry.sourceKind}`)} #{entry.sourceRef}</span>
                <span role="cell">{signed(format, entry.balanceChange)}</span>
                <span role="cell">{signed(format, entry.heldChange)}</span>
              </div>
            ))}
          </div>
        )}
        {history.nextCursor && <div className="pager"><button type="button" className="button outline" disabled={history.loading} onClick={history.more}>{t('more')}</button></div>}
      </section>
    </>
  );
}

function signed(format, value) {
  if (value === null || value === undefined || value === '0') return '0';
  const text = String(value);
  return text.startsWith('-') ? format.number(text) : `+${format.number(text)}`;
}
