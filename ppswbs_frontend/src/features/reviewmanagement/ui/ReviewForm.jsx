import React, { useState } from 'react';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field, SuccessNotice, useCommand } from '../../../shared/ui/components';
import { reviewManagementMessages } from '../messages';
import { LIMITS, validateBodyText, validateRating } from '../rules';
import ReviewPhotoPicker, { MAX_IMAGES, MAX_TOTAL_SIZE } from './ReviewPhotoPicker';
import StarPicker from './StarPicker';

export default function ReviewForm({ source, handle, existing, onSaved }) {
  const t = useT(reviewManagementMessages);
  const command = useCommand();

  const [rating, setRating] = useState(existing?.rating || 5);
  const [bodyText, setBodyText] = useState(existing?.bodyText || '');
  const [images, setImages] = useState(
    existing?.imageUrls ? existing.imageUrls.map(url => ({ dataUrl: url, size: 0, name: 'image' })) : []
  );
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

    if (images.length > MAX_IMAGES) {
      setClientError(t('errMaxPhotos'));
      return;
    }

    const totalSize = images.reduce((acc, img) => acc + (img.size || 0), 0);
    if (totalSize > MAX_TOTAL_SIZE) {
      setClientError(t('errMaxPhotoSize'));
      return;
    }

    setClientError('');
    setSuccess('');

    const imageUrls = images.map(img => img.dataUrl);

    const result = existing
      ? await command.run(key =>
          onSaved({
            key,
            rating: Number(rating),
            bodyText: body,
            imageUrls,
            version: existing.version,
          })
        )
      : await command.run(key =>
          onSaved({
            key,
            rating: Number(rating),
            bodyText: body,
            imageUrls,
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

      <div className="field">
        <span className="field-label">{t('rating')}</span>
        <StarPicker value={rating} onChange={setRating} disabled={command.pending} />
      </div>

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

      <ReviewPhotoPicker images={images} onChange={setImages} onError={setClientError} disabled={command.pending} />

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
