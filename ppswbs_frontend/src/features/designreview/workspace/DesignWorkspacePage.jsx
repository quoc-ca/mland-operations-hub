import React, { useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field, Loading, SuccessNotice, useCommand } from '../../../shared/ui/components';
import { useDesignReviewApi } from '../api/useDesignReviewApi';
import { designReviewMessages } from '../messages';
import {
  ALLOWED_IMAGE_TYPES,
  CONSENT_PURPOSE,
  CONSENT_VERSION,
  LIMITS,
  detectImageBlur,
  formatFileSize,
  validateImageDimensions,
  validateImageFile,
} from '../rules';

export default function DesignWorkspacePage() {
  const t = useT(designReviewMessages);
  const navigate = useNavigate();
  const api = useDesignReviewApi();
  const command = useCommand();
  const { status: authStatus, userId } = useAuth();
  const isAuthenticated = authStatus === 'signed-in' || Boolean(userId);

  const fileInputRef = useRef(null);

  const [searchParams] = useSearchParams();
  const ringDesignRef = searchParams.get('designReference') || searchParams.get('ringDesignReference') || '';

  const [description, setDescription] = useState('');
  const [selectedImages, setSelectedImages] = useState([]);
  const [consentAgreed, setConsentAgreed] = useState(false);
  const [errors, setErrors] = useState({});
  const [guestSubmission, setGuestSubmission] = useState(null);
  const [copiedLink, setCopiedLink] = useState(false);

  const charCount = [...description].length;

  async function handleFilesSelected(e) {
    const files = Array.from(e.target.files || []);
    if (!files.length) return;

    if (fileInputRef.current) fileInputRef.current.value = '';

    const availableSlots = LIMITS.maxImages - selectedImages.length;
    if (availableSlots <= 0) {
      setErrors(prev => ({ ...prev, images: t('validation.maxImagesExceeded') }));
      return;
    }

    const filesToProcess = files.slice(0, availableSlots);
    if (files.length > availableSlots) {
      setErrors(prev => ({ ...prev, images: t('validation.maxImagesExceeded') }));
    } else {
      setErrors(prev => {
        const copy = { ...prev };
        delete copy.images;
        return copy;
      });
    }

    const newEntries = filesToProcess.map(file => ({
      id: crypto.randomUUID(),
      file,
      name: file.name,
      size: file.size,
      previewUrl: URL.createObjectURL(file),
      quality: { checking: true, status: 'PENDING', score: null, reason: '' },
      error: null,
    }));

    setSelectedImages(prev => [...prev, ...newEntries]);

    // Process blur check and format validation for each new file
    for (const entry of newEntries) {
      const fileErr = validateImageFile(entry.file);
      if (fileErr) {
        updateImageQuality(entry.id, {
          checking: false,
          status: 'INVALID',
          reason: t(fileErr),
        });
        continue;
      }

      const dimErr = await validateImageDimensions(entry.file);
      if (dimErr) {
        updateImageQuality(entry.id, {
          checking: false,
          status: 'INVALID',
          reason: t(dimErr),
        });
        continue;
      }

      // Laplacian variance blur detection
      const blurResult = await detectImageBlur(entry.file, 80);
      updateImageQuality(entry.id, {
        checking: false,
        status: blurResult.status,
        score: blurResult.score,
        reason: blurResult.reason,
      });
    }
  }

  function updateImageQuality(id, qualityUpdate) {
    setSelectedImages(prev =>
      prev.map(item => (item.id === id ? { ...item, quality: { ...item.quality, ...qualityUpdate } } : item))
    );
  }

  function handleRemoveImage(id) {
    setSelectedImages(prev => {
      const found = prev.find(item => item.id === id);
      if (found?.previewUrl) {
        try {
          URL.revokeObjectURL(found.previewUrl);
        } catch {
          // ignore
        }
      }
      return prev.filter(item => item.id !== id);
    });
    setErrors(prev => {
      const copy = { ...prev };
      delete copy.images;
      return copy;
    });
  }

  const hasBlurryImage = selectedImages.some(item => item.quality.status === 'TOO_BLURRY');
  const hasInvalidImage = selectedImages.some(item => item.quality.status === 'INVALID');
  const isEvaluatingImages = selectedImages.some(item => item.quality.checking);

  async function handleSubmit(e) {
    e.preventDefault();
    const newErrors = {};

    if (!description.trim() && selectedImages.length === 0) {
      newErrors.form = t('validation.contentRequired');
    }
    if (description && [...description].length > LIMITS.descriptionMax) {
      newErrors.description = t('validation.descriptionTooLong');
    }

    if (selectedImages.length > LIMITS.maxImages) {
      newErrors.images = t('validation.maxImagesExceeded');
    }

    if (hasBlurryImage) {
      newErrors.images = t('validation.imageTooBlurry');
    }

    if (hasInvalidImage) {
      newErrors.images = 'Một số ảnh không hợp lệ. Vui lòng kiểm tra lại.';
    }

    if (selectedImages.length > 0 && !consentAgreed) {
      newErrors.consent = t('validation.consentRequired');
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }
    setErrors({});

    const res = await command.run(key =>
      api.submitDesignRequest(
        key,
        {
          description: description.trim(),
          designReference: ringDesignRef || null,
          consentVersion: selectedImages.length > 0 ? CONSENT_VERSION : null,
          consentPurpose: selectedImages.length > 0 ? CONSENT_PURPOSE : null,
          designVersion: 1,
          images: selectedImages.map(item => item.file),
        },
        isAuthenticated
      )
    );

    if (res.ok && res.result?.id) {
      if (isAuthenticated) {
        navigate(`/design-requests/${encodeURIComponent(res.result.id)}`, {
          state: { successMessage: 'Yêu cầu đã được gửi thành công, đang đợi phản hồi của nhân viên' },
        });
      } else {
        // Guest submission confirmation screen
        setGuestSubmission(res.result);
      }
    }
  }

  function handleCopyGuestLink() {
    if (!guestSubmission) return;
    const url = `${window.location.origin}/design-requests/${encodeURIComponent(guestSubmission.id)}?token=${encodeURIComponent(guestSubmission.guestTrackingToken || '')}`;
    navigator.clipboard.writeText(url).then(() => {
      setCopiedLink(true);
      setTimeout(() => setCopiedLink(false), 3000);
    });
  }

  if (guestSubmission) {
    const trackingUrl = `/design-requests/${encodeURIComponent(guestSubmission.id)}?token=${encodeURIComponent(guestSubmission.guestTrackingToken || '')}`;
    const fullTrackingUrl = `${window.location.origin}${trackingUrl}`;

    return (
      <div className="design-container">
        <header className="page-header">
          <h1>🎉 Gửi yêu cầu thiết kế thành công!</h1>
          <p className="muted">
            Mã yêu cầu: <strong>{guestSubmission.requestReference || guestSubmission.id}</strong>
          </p>
        </header>

        <div className="guest-tracking-card">
          <h3>📌 {t('guest.trackingLink')}</h3>
          <p>{t('guest.trackingLinkDesc')}</p>
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', margin: '1rem 0', flexWrap: 'wrap' }}>
            <input
              type="text"
              readOnly
              value={fullTrackingUrl}
              style={{ flex: 1, minWidth: '260px', padding: '0.6rem', background: '#ffffff', borderRadius: '4px', border: '1px solid #cbd5e1' }}
            />
            <button type="button" className="button outline" onClick={handleCopyGuestLink}>
              {copiedLink ? `✓ ${t('guest.copied')}` : `📋 ${t('guest.copyLink')}`}
            </button>
          </div>
          <p className="small muted">
            * Lưu ý: Vì bạn gửi với tư cách Khách, liên kết trên là cách duy nhất để xem cập nhật và phản hồi thẩm duyệt từ nhân viên cửa hàng.
          </p>
          <div style={{ marginTop: '1.5rem', display: 'flex', gap: '1rem' }}>
            <Link to={trackingUrl} className="button primary">
              Xem tiến độ yêu cầu ngay
            </Link>
            <button
              type="button"
              className="button outline"
              onClick={() => {
                setGuestSubmission(null);
                setDescription('');
                setSelectedImages([]);
                setConsentAgreed(false);
              }}
            >
              Gửi yêu cầu thiết kế khác
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="design-container">
      <header className="page-header">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <h1>{t('workspace.title')}</h1>
            <p className="muted">
              {t('workspace.subtitle')}
              {!isAuthenticated && ' (Bạn đang gửi yêu cầu với tư cách Khách — tối đa 5 yêu cầu/ngày)'}
            </p>
          </div>
          {isAuthenticated && (
            <Link to="/my/designs?tab=custom" className="button outline small" style={{ textDecoration: 'none' }}>
              <span>📋</span> Xem yêu cầu của tôi
            </Link>
          )}
        </div>
      </header>

      <div className="boundary-banner" role="status">
        <strong>{t('workspace.boundaryNotice')}</strong>
      </div>

      <ErrorNotice error={command.error} messages={designReviewMessages} onRetry={command.retry} />
      {errors.form && <div className="notice error">{errors.form}</div>}

      <form onSubmit={handleSubmit} className="stack">
        <Field
          label={t('workspace.descriptionLabel')}
          hint={t('workspace.charCount', { count: charCount })}
          error={errors.description || command.error?.fieldError?.('description')}
        >
          <textarea
            value={description}
            onChange={e => {
              setDescription(e.target.value);
              if (errors.description) setErrors(prev => ({ ...prev, description: null }));
              if (errors.form) setErrors(prev => ({ ...prev, form: null }));
            }}
            placeholder={t('workspace.descriptionPlaceholder')}
            rows={5}
            disabled={command.pending}
          />
        </Field>

        <div className="field">
          <label htmlFor="design-images-upload">
            Hình ảnh tham khảo mẫu thiết kế (Tối đa 5 ảnh, định dạng JPEG/PNG/WEBP)
          </label>
          <input
            id="design-images-upload"
            ref={fileInputRef}
            type="file"
            multiple
            accept={ALLOWED_IMAGE_TYPES.join(',')}
            onChange={handleFilesSelected}
            disabled={command.pending || selectedImages.length >= LIMITS.maxImages}
          />
          <span className="muted small">
            Đã chọn: {selectedImages.length}/{LIMITS.maxImages} ảnh. Hệ thống tự động kiểm tra độ sắc nét từng ảnh ngay khi chọn.
          </span>
          {errors.images && <span className="field-error" style={{ display: 'block', marginTop: '0.25rem' }}>{errors.images}</span>}
        </div>

        {hasBlurryImage && (
          <div className="blur-warning-banner" role="alert">
            <span>⚠️</span>
            <div>
              <strong>{t('validation.imageTooBlurry')}</strong>
              <div style={{ fontSize: '0.8rem', marginTop: '0.2rem' }}>
                Ảnh mờ sẽ khiến thợ kim hoàn không thể đánh giá chính xác độ khả thi. Vui lòng gỡ bỏ ảnh mờ và thay bằng ảnh chụp rõ nét hơn.
              </div>
            </div>
          </div>
        )}

        {selectedImages.length > 0 && (
          <div className="image-upload-grid">
            {selectedImages.map(img => {
              const isBlurry = img.quality.status === 'TOO_BLURRY';
              const isChecking = img.quality.checking;
              const isInvalid = img.quality.status === 'INVALID';

              return (
                <div key={img.id} className={`image-upload-card ${isBlurry ? 'blurry' : ''}`}>
                  <img src={img.previewUrl} alt={img.name} className="image-upload-thumb" />
                  <div className="image-upload-info">
                    <span style={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={img.name}>
                      {img.name}
                    </span>
                    <span className="muted">{formatFileSize(img.size)}</span>
                    <div>
                      {isChecking && <span className="badge-tag badge-warning">⏳ {t('validation.imageQualityChecking')}</span>}
                      {isBlurry && <span className="badge-tag badge-danger">❌ Quá mờ ({img.quality.score})</span>}
                      {isInvalid && (
                        <div>
                          <span className="badge-tag badge-danger">⚠️ Lỗi ảnh</span>
                          {img.quality.reason && (
                            <div className="field-error" style={{ fontSize: '0.75rem', marginTop: '0.25rem' }}>
                              {img.quality.reason}
                            </div>
                          )}
                        </div>
                      )}
                      {!isChecking && !isBlurry && !isInvalid && (
                        <span className="badge-tag badge-success">✓ Đạt chuẩn</span>
                      )}
                    </div>
                  </div>
                  <button
                    type="button"
                    className="button outline small"
                    style={{ marginTop: '0.5rem', width: '100%', padding: '0.25rem 0.5rem' }}
                    onClick={() => handleRemoveImage(img.id)}
                    disabled={command.pending}
                  >
                    Gỡ bỏ
                  </button>
                </div>
              );
            })}
          </div>
        )}

        {selectedImages.length > 0 && (
          <div className="consent-card" role="region" aria-label={t('consent.title')}>
            <h3>{t('consent.title')}</h3>
            <p className="small muted">
              Bằng việc gửi hình ảnh, bạn đồng ý cho phép chuyên viên cửa hàng và thợ kim hoàn lưu trữ và xem hình ảnh này để thẩm định tính khả thi của thiết kế thủ công.
            </p>
            <label className="consent-checkbox">
              <input
                type="checkbox"
                checked={consentAgreed}
                onChange={e => {
                  setConsentAgreed(e.target.checked);
                  if (errors.consent) setErrors(prev => ({ ...prev, consent: null }));
                }}
                disabled={command.pending}
              />
              <span>
                {t('consent.checkbox', {
                  purpose: 'HUMAN_DESIGN_REVIEW',
                  version: CONSENT_VERSION,
                })}
              </span>
            </label>
            {errors.consent && <div className="field-error">{errors.consent}</div>}
          </div>
        )}

        <div className="actions" style={{ marginTop: '1.5rem' }}>
          <button
            type="submit"
            className="button primary"
            disabled={
              command.pending ||
              isEvaluatingImages ||
              hasBlurryImage ||
              hasInvalidImage ||
              (selectedImages.length > 0 && !consentAgreed)
            }
          >
            {command.pending
              ? t('common.loading', undefined, 'Đang gửi…')
              : isEvaluatingImages
              ? 'Đang kiểm tra ảnh…'
              : hasBlurryImage
              ? 'Vui lòng thay ảnh mờ'
              : t('workspace.submit')}
          </button>
        </div>
      </form>
    </div>
  );
}
