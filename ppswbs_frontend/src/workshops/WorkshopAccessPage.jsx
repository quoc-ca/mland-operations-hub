import React, { useEffect, useRef, useState } from 'react';
import { exchangeGroupMagicLinkToken, WorkshopApiError } from '../api/workshops';
import { useT } from '../shared/i18n/I18nProvider';
import { messages } from './messages';
import { GroupDetailCard } from './GroupDetailCard';
import { parseMagicLinkToken } from './magicLinkToken';

/**
 * The group Magic Link (003.1 FR-042, FR-048). The token is read strictly from the URL fragment, exchanged once for a
 * read-only group projection and never persisted or logged; the address is scrubbed immediately. Expired, revoked and
 * unknown tokens answer the same 404. A recipient's QR email is not a Magic Link and gives no group view.
 */
export function WorkshopAccessPage() {
  const t = useT(messages);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [group, setGroup] = useState(null);
  // The token and its one exchange live for the page: the address is cleaned at once, so a second run of this effect
  // (React StrictMode in development) must not read the address again or exchange the token twice.
  const tokenRef = useRef(null);
  const exchangeRef = useRef(null);

  useEffect(() => {
    if (tokenRef.current === null) {
      tokenRef.current = parseMagicLinkToken(window.location.hash) || '';
    }
    const token = tokenRef.current;

    if (window.history && window.history.replaceState) {
      window.history.replaceState(null, '', '/workshop-booking/access');
    }

    if (!token) {
      setLoading(false);
      setError({ key: 'access.noToken' });
      return undefined;
    }

    if (!exchangeRef.current) exchangeRef.current = exchangeGroupMagicLinkToken(token);
    let active = true;
    exchangeRef.current
      .then(data => { if (active) setGroup(data); })
      .catch(err => {
        if (!active) return;
        if (err instanceof WorkshopApiError) {
          if (err.status === 404 || err.code === 'MAGIC_LINK_NOT_FOUND' || err.code === 'TOKEN_NOT_FOUND') {
            setError({ key: 'access.notFound' });
          } else if (err.status === 401 || err.status === 403) {
            setError({ key: 'access.revoked' });
          } else {
            setError(err.detail ? { text: err.detail } : { key: 'access.cannotVerify' });
          }
        } else {
          setError(err?.message ? { text: err.message } : { key: 'access.errorGeneric' });
        }
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  return (
    <main className="container public-page">
      <p className="eyebrow">{t('access.eyebrow')}</p>
      <h1>{t('access.title')}</h1>

      {loading && <div className="empty-state"><p>{t('access.verifying')}</p></div>}

      {error && !loading && (
        <div className="empty-state">
          <p className="status" style={{ color: '#b00', borderColor: '#b00', marginBottom: '16px' }}>{error.key ? t(error.key) : error.text}</p>
          <p className="muted" style={{ fontSize: '14px', lineHeight: '1.6' }}>
            {t('access.lookupHint')}
          </p>
          <div style={{ marginTop: '20px' }}>
            <a className="button primary" href="/workshop-booking/lookup">{t('access.toLookup')}</a>
          </div>
        </div>
      )}

      {!loading && !error && group && (
        <>
          <GroupDetailCard group={group} authorization={null} allowRelease={false} allowRecipientChange={false} />
          <div className="actions" style={{ marginTop: '24px' }}>
            <a className="button outline" href="/workshop-booking/lookup">{t('access.manageAtLookup')}</a>
            <a className="button primary" href="/">{t('access.home')}</a>
          </div>
        </>
      )}
    </main>
  );
}
