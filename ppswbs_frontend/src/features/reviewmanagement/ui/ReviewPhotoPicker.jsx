import React from 'react';
import { useT } from '../../../shared/i18n/I18nProvider';
import { reviewManagementMessages } from '../messages';
import { compressReviewImage } from './compressReviewImage';

export const MAX_IMAGES = 3;
export const MAX_TOTAL_SIZE = 10 * 1024 * 1024; // 10MB

/** Up to 3 photos (JPEG/PNG/WebP, 10MB together), read as data URLs; `onError` gets the message of a rejected pick. */
export default function ReviewPhotoPicker({ images, onChange, onError, disabled, testId = 'review-image-input' }) {
  const t = useT(reviewManagementMessages);

  function handleFilesSelected(event) {
    const files = Array.from(event.target.files || []);
    if (!files.length) return;

    if (images.length + files.length > MAX_IMAGES) {
      onError(t('errMaxPhotos'));
      event.target.value = '';
      return;
    }
    if (files.some(file => !file.type.startsWith('image/'))) {
      onError(t('errPhotoType'));
      event.target.value = '';
      return;
    }
    const currentTotalSize = images.reduce((acc, img) => acc + (img.size || 0), 0);
    const newFilesTotalSize = files.reduce((acc, f) => acc + f.size, 0);
    if (currentTotalSize + newFilesTotalSize > MAX_TOTAL_SIZE) {
      onError(t('errMaxPhotoSize'));
      event.target.value = '';
      return;
    }
    onError('');

    Promise.all(files.map(compressReviewImage))
      .then(newImages => onChange(prev => [...prev, ...newImages]))
      .catch(() => onError('Không thể đọc tệp hình ảnh.'))
      .finally(() => {
        event.target.value = '';
      });
  }

  return (
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
            disabled={disabled}
            data-testid={testId}
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
                onClick={() => onChange(prev => prev.filter((_, i) => i !== idx))}
                title={t('removePhoto')}
                disabled={disabled}
                aria-label={t('removePhoto')}
              >
                ×
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
