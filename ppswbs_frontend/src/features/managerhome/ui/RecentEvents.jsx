import React from 'react';
import { Link } from 'react-router';
import { useEnumLabel, useLanguage, useT } from '../../../shared/i18n/I18nProvider';
import { formatVietnamTime } from '../application/vietnamTime';
import { managerHomeMessages } from './messages';

/** Up to five recent refused attempts and package availability changes. Shows time, who and what; never a reason text. */
export default function RecentEvents({ events, onRetry }) {
  const t = useT(managerHomeMessages);
  const kindLabel = useEnumLabel(managerHomeMessages, 'events.kind');
  const { language } = useLanguage();
  const value = events.value;
  return (
    <section aria-labelledby="mh-events" className="mh-section">
      <h2 id="mh-events" className="mh-section-title">{t('events.title')}</h2>
      <div className="mh-panel">
        {events.state === 'loading' && <p role="status">{t('loading')}</p>}
        {events.state === 'unavailable' && (
          <div role="alert">
            <p>{t('unavailable')}</p>
            <button type="button" className="button small outline" onClick={() => onRetry('events')}>{t('retry')}</button>
          </div>
        )}
        {events.state === 'ready' && (
          <>
            {value.items.length === 0
              ? <p role="status">{t('events.none')}</p>
              : (
                <ul className="mh-events">
                  {value.items.map((item, index) => (
                    <li key={item.id ?? `${item.kind}-${item.time}-${index}`}>
                      <time dateTime={item.time || undefined}>{formatVietnamTime(item.time, language)}</time>
                      {' · '}<span>{item.actor || t('events.system')}</span>
                      {' · '}<span>{kindLabel(item.kind)}</span> <code>{item.action}</code>
                    </li>
                  ))}
                </ul>
              )}
            {value.partial && <p className="mh-note" role="status">{t('events.partial')}</p>}
            {!value.settlementIncluded && <p className="mh-note" role="status">{t('events.settlementMissing')}</p>}
            <Link to="/operations/audit" className="button small outline">{t('events.open')}</Link>
          </>
        )}
      </div>
    </section>
  );
}
