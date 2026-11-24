import React, { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useFormat, useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field, Loading, SuccessNotice, useCommand } from '../../../shared/ui/components';
import { useDesignReviewApi } from '../api/useDesignReviewApi';
import { useWorkshopRingDesignApi } from '../../ringdesignconfiguration/api/useWorkshopRingDesignApi';
import { derivePreviewModel } from '../../ringdesignconfiguration/domain/designPreview';
import { designReviewMessages } from '../messages';
import { wholeAmount } from '../format';
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

function formatDesignOptionLabel(d, t) {
  if (!d) return '';
  const snap = d.componentSnapshot || {};
  const preview = derivePreviewModel(snap);

  const mat = preview.material?.label || t('ws.defaultMaterial');
  const shape = preview.shape?.label || t('ws.defaultShape');
  const size = t('ws.sizeShort', { size: preview.ringSize || '8', mm: preview.circumferenceMm || 48 });

  const totalGems = (snap.components || []).reduce((sum, c) => sum + (Number(c.quantity) || 1), 0);
  const gemsText = totalGems > 0 ? t('ws.gemsText', { count: totalGems }) : t('ws.noGems');

  const hasTextEngraving = Boolean(snap.engravingText || (snap.engravings && snap.engravings.length > 0));
  const hasImgEngraving = Boolean(snap.customImages && snap.customImages.length > 0);
  let engravingText = t('ws.engNone');
  if (hasTextEngraving && hasImgEngraving) {
    engravingText = t('ws.engBoth');
  } else if (hasTextEngraving) {
    engravingText = t('ws.engText');
  } else if (hasImgEngraving) {
    engravingText = t('ws.engImage');
  }

  const shortId = d.designId?.length > 10 ? d.designId.slice(-6).toUpperCase() : d.designId;
  return t('ws.optionLabel', { id: shortId, material: mat, shape, size, gems: gemsText, engraving: engravingText, version: d.designVersion || 1 });
}

export default function DesignWorkspacePage() {
  const t = useT(designReviewMessages);
  const fmt = useFormat();
  const navigate = useNavigate();
  const api = useDesignReviewApi();
  const workshopRingApi = useWorkshopRingDesignApi();
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
  const [availableDesigns, setAvailableDesigns] = useState([]);
  const [selectedDesignRef, setSelectedDesignRef] = useState(ringDesignRef);
  const [loadingDesigns, setLoadingDesigns] = useState(false);
  const selectedDesignObj = availableDesigns.find((d) => d.designId === selectedDesignRef);

  useEffect(() => {
    if (ringDesignRef) {
      setSelectedDesignRef(ringDesignRef);
    }
  }, [ringDesignRef]);

  useEffect(() => {
    if (!isAuthenticated) return;
    setLoadingDesigns(true);
    workshopRingApi
      .listMyDesigns(null, true, true)
      .then((designs) => {
        setAvailableDesigns(Array.isArray(designs) ? designs : []);
      })
      .catch(() => {})
      .finally(() => setLoadingDesigns(false));
  }, [isAuthenticated, workshopRingApi]);

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
          reasonKey: fileErr,
        });
        continue;
      }

      const dimErr = await validateImageDimensions(entry.file);
      if (dimErr) {
        updateImageQuality(entry.id, {
          checking: false,
          status: 'INVALID',
          reasonKey: dimErr,
        });
        continue;
      }

      // Laplacian variance blur detection
      const blurResult = await detectImageBlur(entry.file, 80, t);
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
      newErrors.images = t('ws.imagesInvalid');
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
          designReference: selectedDesignRef || ringDesignRef || null,
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
          state: { successMessage: t('workspace.success') },
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
          <h1>🎉 {t('ws.successTitle')}</h1>
          <p className="muted">
            {t('ws.requestCode')}: <strong>{guestSubmission.requestReference || guestSubmission.id}</strong>
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
            {t('ws.guestNotice')}
          </p>
          <div style={{ marginTop: '1.5rem', display: 'flex', gap: '1rem' }}>
            <Link to={trackingUrl} className="button primary">
              {t('ws.viewProgress')}
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
              {t('ws.submitAnother')}
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
              {!isAuthenticated && ` ${t('ws.guestNote')}`}
            </p>
          </div>
          {isAuthenticated && (
            <Link to="/my/designs?tab=custom" className="button outline small" style={{ textDecoration: 'none' }}>
              <span>📋</span> {t('ws.myRequests')}
            </Link>
          )}
        </div>
      </header>

      <ErrorNotice error={command.error} messages={designReviewMessages} onRetry={command.retry} />
      {errors.form && <div className="notice error">{errors.form}</div>}

      <form onSubmit={handleSubmit} className="stack">
        {/* Feature-012 Ring Design Attachment */}
        <div className="field" data-testid="ring-design-attachment-field">
          <label htmlFor="ring-design-select">
            {t('ws.attachLabel')}
          </label>
          {isAuthenticated ? (
            <div>
              {availableDesigns.length > 0 ? (
                <div>
                  <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
                    <select
                      id="ring-design-select"
                      value={selectedDesignRef}
                      onChange={(e) => setSelectedDesignRef(e.target.value)}
                      style={{ flex: 1, minWidth: '260px', padding: '0.6rem 0.75rem', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '0.875rem' }}
                      data-testid="ring-design-select"
                    >
                      <option value="">{t('ws.noAttach')}</option>
                      {availableDesigns.map((d) => (
                        <option key={d.designId} value={d.designId}>
                          {formatDesignOptionLabel(d, t)}
                        </option>
                      ))}
                    </select>
                    {selectedDesignRef && (
                      <button
                        type="button"
                        className="button outline small"
                        onClick={() => setSelectedDesignRef('')}
                        data-testid="clear-design-btn"
                      >
                        ✕ {t('ws.detach')}
                      </button>
                    )}
                  </div>

                  {selectedDesignObj && (() => {
                    const snap = selectedDesignObj.componentSnapshot || {};
                    const preview = derivePreviewModel(snap);
                    const totalGems = (snap.components || []).reduce((sum, c) => sum + (Number(c.quantity) || 1), 0);
                    const cost = selectedDesignObj.evaluation?.estimatedCost;
                    const diffScore = selectedDesignObj.evaluation?.difficultyScore;
                    return (
                      <div
                        style={{
                          marginTop: '0.75rem',
                          padding: '0.875rem 1rem',
                          background: '#f8fafc',
                          border: '1px solid #cbd5e1',
                          borderRadius: '8px',
                          fontSize: '0.875rem',
                        }}
                        data-testid="selected-design-preview-card"
                      >
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.625rem', flexWrap: 'wrap', gap: '0.5rem' }}>
                          <span style={{ fontWeight: 600, color: '#0f172a' }}>
                            💎 {t('ws.selectedDetail')}: <code style={{ color: '#0284c7', background: '#f1f5f9', padding: '0.15rem 0.4rem', borderRadius: '4px' }}>{selectedDesignObj.designId}</code>
                          </span>
                          <span style={{ fontSize: '0.75rem', padding: '0.2rem 0.5rem', background: '#e0f2fe', color: '#0369a1', borderRadius: '4px', fontWeight: 600 }}>
                            {t('ws.designVersion', { version: selectedDesignObj.designVersion })} · {selectedDesignObj.status || 'SAVED'}
                          </span>
                        </div>
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.5rem', color: '#334155', lineHeight: 1.5 }}>
                          <div><strong>{t('ws.material')}:</strong> {preview.material?.label || t('ws.defaultMaterial')}</div>
                          <div><strong>{t('ws.shape')}:</strong> {preview.shape?.label || t('ws.defaultShape')}</div>
                          <div><strong>{t('ws.size')}:</strong> {t('ws.sizeValue', { size: preview.ringSize || '8', circumference: preview.circumferenceMm })}</div>
                          <div><strong>{t('ws.bandSize')}:</strong> {t('ws.bandValue', { thickness: preview.thicknessMm, width: preview.bandWidthMm })}</div>
                          <div><strong>{t('ws.gems')}:</strong> {totalGems > 0 ? t('ws.gemsCount', { count: totalGems }) : t('ws.none')}</div>
                          <div><strong>{t('ws.engraving')}:</strong> {snap.engravingText ? t('ws.engravingText', { text: snap.engravingText }) : (snap.customImages?.length > 0 ? t('ws.engravingImages', { count: snap.customImages.length }) : t('ws.none'))}</div>
                          {cost != null && (
                            <div><strong>{t('ws.estimatedCost')}:</strong> {fmt.money(wholeAmount(cost))}</div>
                          )}
                          {diffScore != null && (
                            <div><strong>{t('ws.difficulty')}:</strong> {diffScore}/10</div>
                          )}
                        </div>
                      </div>
                    );
                  })()}
                </div>
              ) : (
                <p className="muted small" style={{ margin: 0 }}>
                  {t('ws.noDesigns')}{' '}
                  <Link to="/ring-design" target="_blank">{t('ws.designNow')}</Link>
                </p>
              )}
            </div>
          ) : (
            <div>
              <input
                id="ring-design-select"
                type="text"
                placeholder={t('ws.codePlaceholder')}
                value={selectedDesignRef}
                onChange={(e) => setSelectedDesignRef(e.target.value)}
                style={{ width: '100%', padding: '0.5rem', borderRadius: '4px', border: '1px solid #cbd5e1' }}
                data-testid="guest-ring-design-input"
              />
              <span className="muted small">
                {t('ws.codeHint')}
              </span>
            </div>
          )}
        </div>

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
            {t('ws.imagesLabel')}
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
            {t('ws.imagesSelected', { count: selectedImages.length, max: LIMITS.maxImages })}
          </span>
          {errors.images && <span className="field-error" style={{ display: 'block', marginTop: '0.25rem' }}>{errors.images}</span>}
        </div>

        {hasBlurryImage && (
          <div className="blur-warning-banner" role="alert">
            <span>⚠️</span>
            <div>
              <strong>{t('validation.imageTooBlurry')}</strong>
              <div style={{ fontSize: '0.8rem', marginTop: '0.2rem' }}>
                {t('ws.blurDetail')}
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
                      {isBlurry && <span className="badge-tag badge-danger">❌ {t('ws.blurryBadge', { score: img.quality.score })}</span>}
                      {isInvalid && (
                        <div>
                          <span className="badge-tag badge-danger">⚠️ {t('ws.invalidBadge')}</span>
                          {img.quality.reasonKey && (
                            <div className="field-error" style={{ fontSize: '0.75rem', marginTop: '0.25rem' }}>
                              {t(img.quality.reasonKey)}
                            </div>
                          )}
                        </div>
                      )}
                      {!isChecking && !isBlurry && !isInvalid && (
                        <span className="badge-tag badge-success">✓ {t('ws.okBadge')}</span>
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
                    {t('ws.remove')}
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
              {t('ws.consentBody')}
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
              ? t('ws.sending')
              : isEvaluatingImages
              ? t('ws.checkingImages')
              : hasBlurryImage
              ? t('ws.replaceBlurry')
              : t('workspace.submit')}
          </button>
        </div>
      </form>
    </div>
  );
}
