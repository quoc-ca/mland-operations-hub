import React from 'react';
import { Link } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { managerHomeMessages } from './messages';

/** One card per registered queue. A zero card is dimmed and grouped; a failed card shows its own retry and never 0. */
export default function WaitingWorkSection({ queues, section, onRetry }) {
  const t = useT(managerHomeMessages);
  const cards = queues.map(queue => ({ queue, data: section(`queue:${queue.id}`) }));
  const isZero = card => card.data.state === 'ready' && card.data.value === 0;
  const active = cards.filter(card => !isZero(card));
  const quiet = cards.filter(isZero);
  const everyShownIsZero = cards.length > 0 && quiet.length === cards.length;

  const renderCard = ({ queue, data }) => {
    const label = t(`queue.${queue.id}`);
    if (data.state === 'loading') {
      return <li key={queue.id} className="mh-card" aria-busy="true"><strong>{label}</strong><span role="status">{t('loading')}</span></li>;
    }
    if (data.state === 'unavailable') {
      return (
        <li key={queue.id} className="mh-card mh-card-unavailable">
          <strong>{label}</strong>
          <span role="alert">{t('unavailable')}</span>
          <button type="button" className="button small outline" onClick={() => onRetry(`queue:${queue.id}`)}>{t('retry')}</button>
        </li>
      );
    }
    const zero = data.value === 0;
    return (
      <li key={queue.id} className={`mh-card${zero ? ' mh-card-dim' : ''}`}>
        <Link to={queue.target} aria-label={`${label}: ${t('waiting.items', { count: data.value })}. ${t('waiting.open')}`}>
          <strong>{label}</strong>
          <span className="mh-count">{data.value}</span>
          <span className="mh-hint">{t('waiting.items', { count: data.value })} · {t('waiting.open')}</span>
        </Link>
      </li>
    );
  };

  return (
    <section aria-labelledby="mh-waiting" className="mh-section">
      <h2 id="mh-waiting" className="mh-section-title">{t('waiting.title')}</h2>
      {everyShownIsZero && <p className="mh-empty" role="status">{t('waiting.noWork')}</p>}
      {active.length > 0 && <ul className="mh-grid">{active.map(renderCard)}</ul>}
      {quiet.length > 0 && !everyShownIsZero && (
        <>
          <h3 className="mh-subtitle">{t('waiting.noWorkGroup')}</h3>
          <ul className="mh-grid">{quiet.map(renderCard)}</ul>
        </>
      )}
    </section>
  );
}
