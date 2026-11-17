import React, { useState } from 'react';
import { Navigate, useNavigate } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useRole } from '../../../shared/auth/useRole';
import { ErrorNotice, Field, Loading, useCommand } from '../../../shared/ui/components';
import { useReviewManagementApi } from '../api/useReviewManagementApi';
import { reviewManagementMessages } from '../messages';
import { validateContact } from '../rules';

export default function GuestLookupPage() {
  const t = useT(reviewManagementMessages);
  const api = useReviewManagementApi();
  const navigate = useNavigate();
  const command = useCommand();

  const [contactType, setContactType] = useState('EMAIL');
  const [contactValue, setContactValue] = useState('');
  const [clientError, setClientError] = useState('');
  const [sources, setSources] = useState(null);

  // The email/phone lookup is for Guests. A signed-in Member already has their own list of reviewable sources.
  const { status: authStatus } = useAuth();
  const role = useRole();
  if (authStatus === 'signed-in' && role === 'MEMBER') return <Navigate to="/reviews/eligible-sources" replace />;

  async function submit(event) {
    event.preventDefault();
    const contactErr = validateContact(contactType, contactValue);
    if (contactErr) {
      setClientError(t(contactErr));
      return;
    }
    setClientError('');

    const result = await command.run(key =>
      api.lookupGuestEligibility(key, {
        contactType,
        contactValue: contactValue.trim(),
      })
    );

    if (result.ok) {
      setSources(result.result);
    }
  }

  return (
    <div className="review-container">
      <header className="page-header">
        <h1>{t('lookupTitle')}</h1>
        <p className="muted">{t('lookupSubtitle')}</p>
      </header>

      <form className="review-panel" onSubmit={submit}>
        <ErrorNotice
          error={command.error}
          messages={reviewManagementMessages}
          onRetry={command.retry}
        />
        {clientError && (
          <div className="notice error" role="alert">
            {clientError}
          </div>
        )}

        <Field label={t('contactType')}>
          <select
            value={contactType}
            onChange={e => setContactType(e.target.value)}
            disabled={command.pending}
          >
            <option value="EMAIL">EMAIL</option>
            <option value="PHONE">PHONE</option>
          </select>
        </Field>

        <Field label={t('contactValue')}>
          <input
            type={contactType === 'EMAIL' ? 'email' : 'tel'}
            value={contactValue}
            onChange={e => setContactValue(e.target.value)}
            required
            disabled={command.pending}
          />
        </Field>

        <button
          type="submit"
          className="button primary"
          disabled={command.pending}
        >
          {t('lookup')}
        </button>
      </form>

      {command.pending && <Loading />}

      {sources && (
        <div className="source-list">
          {sources.items?.length ? (
            sources.items.map(source => (
              <button
                type="button"
                className="source-card"
                key={`${source.sourceType}:${source.sourceReference}`}
                onClick={() =>
                  navigate('/reviews/new', {
                    state: {
                      source,
                      handle: sources.eligibilityHandle,
                    },
                  })
                }
              >
                <strong>
                  {source.label ||
                    (source.targetType === 'WORKSHOP_SESSION'
                      ? t('sourceWorkshop')
                      : t('sourceRetail'))}
                </strong>
                <span>{t('sourceReference')}: {source.sourceReference}</span>
                <span className="badge-tag badge-verified">{t('verified')}</span>
                <span className="action-label">{t('review')} &rarr;</span>
              </button>
            ))
          ) : (
            <div className="empty-state">{t('noSources')}</div>
          )}
        </div>
      )}
    </div>
  );
}
