import React from 'react';
import { Link } from 'react-router';
import { useT } from '../shared/i18n/I18nProvider';
import { sharedMessages } from '../shared/ui/messages';

/**
 * NotFoundPage (R-404 / FR-046 / T074).
 * Generic 404 presentation with ONLY one Home link.
 * Must NOT disclose attempted URL, role, account status, scope or authorization explanation.
 * Must NOT provide Back action or automatic redirect.
 */
export default function NotFoundPage() {
  const t = useT(sharedMessages);
  return (
    <main className="public-page not-found-page">
      <div className="container empty-state" style={{ textAlign: 'center', padding: '4rem 1rem' }}>
        <p className="eyebrow" style={{ color: '#6b717f', fontSize: '0.875rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
          404 Not Found
        </p>
        <h1 style={{ fontSize: '2rem', fontWeight: 600, color: '#111827', margin: '0.75rem 0' }}>
          {t('notFound.title')}
        </h1>
        <p style={{ color: '#4b5563', maxWidth: '480px', margin: '0 auto 1.5rem', lineHeight: '1.5' }}>
          {t('notFound.body')}
        </p>
        <div>
          <Link to="/" className="button primary" style={{ display: 'inline-block' }}>
            {t('notFound.home')}
          </Link>
        </div>
      </div>
    </main>
  );
}
