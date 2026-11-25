import React from 'react';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { managerHomeMessages } from './messages';

/** Sessions today and cancelled ones, with a branch picker. Operations only: no revenue and no utilization (FR-007). */
export default function TodayStrip({ strip, locations, branch, onBranchChange, onRetry }) {
  const t = useT(managerHomeMessages);
  const options = locations.state === 'ready' ? locations.value : [];
  return (
    <section aria-labelledby="mh-today" className="mh-section">
      <h2 id="mh-today" className="mh-section-title">{t('today.title')}</h2>
      <div className="mh-panel">
        <label className="mh-branch">
          <span>{t('today.branch')}</span>
          <select value={branch} onChange={event => onBranchChange(event.target.value)}>
            <option value="all">{t('today.allBranches')}</option>
            {options.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>
        </label>
        {locations.state === 'unavailable' && <p className="mh-note" role="status">{t('today.branchUnavailable')}</p>}
        {strip.state === 'loading' && <p role="status">{t('loading')}</p>}
        {strip.state === 'unavailable' && (
          <div role="alert">
            <p>{t('unavailable')}</p>
            <button type="button" className="button small outline" onClick={() => onRetry('strip')}>{t('retry')}</button>
          </div>
        )}
        {strip.state === 'ready' && (strip.value.sessions === 0
          ? <p role="status">{t('today.none')}</p>
          : (
            <p className="mh-strip">
              <span>{t('today.sessions')}: </span><strong className="mh-count">{strip.value.sessions}</strong>
              {strip.value.cancelledSessions > 0 && <span> ({t('today.cancelled', { count: strip.value.cancelledSessions })})</span>}
              {strip.value.expected != null && <span> · {t('today.checkIn')}: <strong>{strip.value.checkedIn}/{strip.value.expected}</strong></span>}
            </p>
          ))}
      </div>
    </section>
  );
}
