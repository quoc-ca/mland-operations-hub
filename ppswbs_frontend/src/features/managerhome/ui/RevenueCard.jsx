import React from 'react';
import { Link } from 'react-router';
import { useFormat, useT } from '../../../shared/i18n/I18nProvider';
import { managerHomeMessages } from './messages';

/** The dashboard's own monthly figure. A partial, empty or unavailable source is said so and never shown as a number. */
export default function RevenueCard({ revenue, onRetry }) {
  const t = useT(managerHomeMessages);
  const format = useFormat();
  const value = revenue.value;
  let body;
  if (revenue.state === 'loading') body = <p role="status">{t('loading')}</p>;
  else if (revenue.state === 'unavailable' || value?.status === 'UNAVAILABLE') {
    body = (
      <div role="alert">
        <p>{t('revenue.unavailable')}</p>
        <button type="button" className="button small outline" onClick={() => onRetry('revenue')}>{t('retry')}</button>
      </div>
    );
  } else if (value.status === 'PARTIAL') body = <p role="status">{t('revenue.partial')}</p>;
  else if (value.status === 'EMPTY' || value.totalRevenue == null) body = <p role="status">{t('revenue.empty')}</p>;
  else body = <p className="mh-count" aria-label={`${t('revenue.title')}: ${format.money(value.totalRevenue)}`}>{format.money(value.totalRevenue)}</p>;

  return (
    <section aria-labelledby="mh-revenue" className="mh-section">
      <h2 id="mh-revenue" className="mh-section-title">{t('revenue.title')}</h2>
      <div className="mh-panel">
        {body}
        {value?.isDemo && <p className="mh-note" role="status">{t('revenue.demo')}</p>}
        <Link to="/manager/dashboard" className="button small outline">{t('revenue.open')}</Link>
      </div>
    </section>
  );
}
