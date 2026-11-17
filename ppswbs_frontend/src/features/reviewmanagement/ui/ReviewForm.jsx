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
  const [images, setImages] = useState(
    existing?.imageUrls ? existing.imageUrls.map(url => ({ dataUrl: url, size: 0, name: 'image' })) : []
  );
  // Anonymity is chosen when the review is created and cannot change afterwards, so the choice is offered only then.
  const [anonymous, setAnonymous] = useState(false);
  const [clientError, setClientError] = useState('');
  const [success, setSuccess] = useState('');

  const MAX_IMAGES = 3;
  const MAX_TOTAL_SIZE = 10 * 1024 * 1024; // 10MB

  function handleFilesSelected(event) {
    const files = Array.from(event.target.files || []);
    if (!files.length) return;

    if (images.length + files.length > MAX_IMAGES) {
      setClientError(t('errMaxPhotos'));
      event.target.value = '';
      return;
    }

    const invalidType = files.some(file => !file.type.startsWith('image/'));
    if (invalidType) {
      setClientError(t('errPhotoType'));
      event.target.value = '';
      return;
    }

    const currentTotalSize = images.reduce((acc, img) => acc + (img.size || 0), 0);
    const newFilesTotalSize = files.reduce((acc, f) => acc + f.size, 0);
    if (currentTotalSize + newFilesTotalSize > MAX_TOTAL_SIZE) {
      setClientError(t('errMaxPhotoSize'));
      event.target.value = '';
      return;
    }

    setClientError('');

    Promise.all(
      files.map(
        file =>
          new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve({ dataUrl: reader.result, size: file.size, name: file.name });
            reader.onerror = reject;
            reader.readAsDataURL(file);
          })
      )
    )
      .then(newImages => {
        setImages(prev => [...prev, ...newImages]);
      })
      .catch(() => {
        setClientError('Không thể đọc tệp hình ảnh.');
      })
      .finally(() => {
        event.target.value = '';
      });
  }

  function removeImage(indexToRemove) {
    setImages(prev => prev.filter((_, idx) => idx !== indexToRemove));
  }

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

      <div className="field review-photo-upload-section">
        <label className="field-label" style={{ fontWeight: 500, fontSize: '0.9rem', color: '#374151' }}>
          {t('attachPhotos')}
        </label>
        <span className="muted small" style={{ fontSize: '0.825rem', color: '#6b7280' }}>
          {t('attachPhotosHint')}
        </span>

        {images.length < MAX_IMAGES && (
          <label className="review-file-upload-label" style={{ marginTop: '4px' }}>
            <span>📷 {t('attachPhotos')} ({images.length}/{MAX_IMAGES})</span>
            <input
              type="file"
              accept="image/png,image/jpeg,image/webp"
              multiple
              style={{ display: 'none' }}
              onChange={handleFilesSelected}
              disabled={command.pending}
              data-testid="review-image-input"
            />
          </label>
        )}

        {images.length > 0 && (
          <div className="review-image-previews" data-testid="review-image-previews">
            {images.map((img, idx) => (
              <div key={idx} className="review-image-preview-item">
                <img src={img.dataUrl} alt={`Attached preview ${idx + 1}`} />
                <button
                  type="button"
                  className="review-image-remove-btn"
                  onClick={() => removeImage(idx)}
                  title={t('removePhoto')}
                  disabled={command.pending}
                  aria-label={t('removePhoto')}
                >
                  ×
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

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
