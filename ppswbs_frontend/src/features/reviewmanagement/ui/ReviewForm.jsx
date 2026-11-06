import React, { useState } from 'react';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field, SuccessNotice, useCommand } from '../../../shared/ui/components';
import { reviewManagementMessages } from '../messages';
import { LIMITS, validateBodyText, validateRating } from '../rules';

export default function ReviewForm({ source, handle, existing, onSaved }) {
  const t = useT(reviewManagementMessages);
  const command = useCommand();

  const [rating, setRating] = useState(existing?.rating || 5);
  const [bodyText, setBodyText] = useState(existing?.bodyText || '');
  // Anonymity is chosen when the review is created and cannot change afterwards, so the choice is offered only then.
  const [anonymous, setAnonymous] = useState(false);
  const [clientError, setClientError] = useState('');
  const [success, setSuccess] = useState('');

  async function submit(event) {
    event.preventDefault();
    const ratingErr = validateRating(rating);
    if (ratingErr) {
      setClientError(t(ratingErr));
      return;
    }

    const body = bodyText.trim();
    const bodyErr = validateBodyText(body);
    if (bodyErr) {
      setClientError(t(bodyErr));
      return;
    }

    setClientError('');
    setSuccess('');

    const result = existing
      ? await command.run(key =>
          onSaved({
            key,
            rating: Number(rating),
            bodyText: body,
            version: existing.version,
          })
        )
      : await command.run(key =>
          onSaved({
            key,
            rating: Number(rating),
            bodyText: body,
            source,
            handle,
            ...(anonymous ? { anonymous: true } : {}),
          })
        );

    if (result.ok) {
      setSuccess(t(existing ? 'successEdited' : 'successCreated'));
    }
  }

  return (
    <form className="review-form" onSubmit={submit}>
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
      <SuccessNotice>{success}</SuccessNotice>

      <Field label={t('rating')}>
        <select
          value={rating}
          onChange={e => setRating(Number(e.target.value))}
          disabled={command.pending}
        >
          {[5, 4, 3, 2, 1].map(value => (
            <option key={value} value={value}>
              {'★'.repeat(value)} ({value} / 5)
            </option>
          ))}
        </select>
      </Field>

      <Field
        label={t('bodyText')}
        hint={`${bodyText.length}/${LIMITS.bodyTextMax.toLocaleString()}`}
      >
        <textarea
          value={bodyText}
          onChange={e => setBodyText(e.target.value)}
          placeholder={t('bodyPlaceholder')}
          rows={6}
          disabled={command.pending}
        />
      </Field>

      {!existing && (
        <div className="field">
          <label className="check-row">
            <input
              type="checkbox"
              checked={anonymous}
              onChange={e => setAnonymous(e.target.checked)}
              disabled={command.pending}
            />{' '}
            {t('anonymousLabel')}
          </label>
          <span className="muted small">{t('anonymousHint')}</span>
        </div>
      )}

      <button
        type="submit"
        className="button primary"
        disabled={command.pending}
      >
        {t(existing ? 'save' : 'submit')}
      </button>
    </form>
  );
}
