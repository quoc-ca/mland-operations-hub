import React, { useState } from 'react';
import { useNavigate } from 'react-router';
import { ErrorNotice, Field, Loading, useCommand } from '../../../shared/ui/components';
import { useT } from '../../../shared/i18n/I18nProvider';
import { reviewManagementMessages } from '../messages';
import { useReviewManagementApi } from '../api/useReviewManagementApi';

export default function GuestLookupPage() {
  const t = useT(reviewManagementMessages); const api = useReviewManagementApi(); const navigate = useNavigate(); const command = useCommand();
  const [contactType, setContactType] = useState('EMAIL'); const [contactValue, setContactValue] = useState(''); const [sources, setSources] = useState(null);
  async function submit(event) { event.preventDefault(); if (!contactValue.trim()) return; const result = await command.run(key => api.lookupGuestEligibility(key, { contactType, contactValue: contactValue.trim() })); if (result.ok) setSources(result.result); }
  return <div className="review-container"><header className="page-header"><h1>{t('lookupTitle')}</h1><p className="muted">{t('lookupSubtitle')}</p></header>
    <form className="review-panel" onSubmit={submit}><ErrorNotice error={command.error} messages={reviewManagementMessages} onRetry={command.retry} /><Field label={t('contactType')}><select value={contactType} onChange={e => setContactType(e.target.value)}><option value="EMAIL">EMAIL</option><option value="PHONE">PHONE</option></select></Field><Field label={t('contactValue')}><input value={contactValue} onChange={e => setContactValue(e.target.value)} required /></Field><button className="button primary" disabled={command.pending}>{t('lookup')}</button></form>
    {command.pending && <Loading />}{sources && <div className="source-list">{sources.items?.length ? sources.items.map(source => <button className="source-card" key={`${source.sourceType}:${source.sourceReference}`} onClick={() => navigate('/reviews/new', { state: { source, handle: sources.eligibilityHandle } })}><strong>{source.label || (source.targetType === 'WORKSHOP_SESSION' ? t('sourceWorkshop') : t('sourceRetail'))}</strong><span>{t('verified')}</span><span>{t('review')}</span></button>) : <div className="empty-state">{t('noSources')}</div>}</div>}
  </div>;
}
