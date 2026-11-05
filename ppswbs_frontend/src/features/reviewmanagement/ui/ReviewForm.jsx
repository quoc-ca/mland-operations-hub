import React, { useState } from 'react';
import { ErrorNotice, Field, SuccessNotice, useCommand } from '../../../shared/ui/components';
import { useT } from '../../../shared/i18n/I18nProvider';
import { reviewManagementMessages } from '../messages';

export default function ReviewForm({ source, handle, existing, onSaved }) {
  const t = useT(reviewManagementMessages);
  const command = useCommand();
  const [rating, setRating] = useState(existing?.rating || 5);
  const [bodyText, setBodyText] = useState(existing?.bodyText || '');
  const [clientError, setClientError] = useState('');
  const [success, setSuccess] = useState('');

  async function submit(event) {
    event.preventDefault();
    const body = bodyText.trim();
    if (!rating || rating < 1 || rating > 5) return setClientError(t('validationRating'));
    if (body.length > 2000) return setClientError(t('validationLength'));
    setClientError(''); setSuccess('');
    const result = existing
      ? await command.run(key => onSaved({ key, rating: Number(rating), bodyText: body, version: existing.version }))
      : await command.run(key => onSaved({ key, rating: Number(rating), bodyText: body, source, handle }));
    if (result.ok) setSuccess(t(existing ? 'successEdited' : 'successCreated'));
  }

  return <form className="review-form" onSubmit={submit}>
    <ErrorNotice error={command.error} messages={reviewManagementMessages} onRetry={command.retry} />
    {clientError && <div className="notice error" role="alert">{clientError}</div>}
    <SuccessNotice>{success}</SuccessNotice>
    <Field label={t('rating')}>
      <select value={rating} onChange={e => setRating(e.target.value)} disabled={command.pending}>
        {[5, 4, 3, 2, 1].map(value => <option key={value} value={value}>{value} / 5</option>)}
      </select>
    </Field>
    <Field label={t('bodyText')} hint={`${bodyText.length}/2,000`}>
      <textarea value={bodyText} onChange={e => setBodyText(e.target.value)} placeholder={t('bodyPlaceholder')} rows={6} disabled={command.pending} />
    </Field>
    <button type="submit" className="button primary" disabled={command.pending}>{t(existing ? 'save' : 'submit')}</button>
  </form>;
}
