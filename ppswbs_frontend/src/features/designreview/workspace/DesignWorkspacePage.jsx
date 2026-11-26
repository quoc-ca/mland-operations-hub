import React, { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useRole } from '../../../shared/auth/useRole';
import { RequireSession } from '../../../shared/auth/RequireSession';
import { useT, useLanguage } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field, Loading, SuccessNotice, useCommand } from '../../../shared/ui/components';
import { useDesignReviewApi } from '../api/useDesignReviewApi';
import { useWorkshopRingDesignApi } from '../../ringdesignconfiguration/api/useWorkshopRingDesignApi';
import { derivePreviewModel } from '../../ringdesignconfiguration/domain/designPreview';
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

function formatDesignOptionLabel(d) {
  if (!d) return '';
  const snap = d.componentSnapshot || {};
  const preview = derivePreviewModel(snap);

  const mat = preview.material?.label || 'Bạc Ý 925';
  const shape = preview.shape?.label || 'Tròn cổ điển';
  const size = `Size ${preview.ringSize || '8'} (${preview.circumferenceMm || 48}mm)`;

  const totalGems = (snap.components || []).reduce((sum, c) => sum + (Number(c.quantity) || 1), 0);
  const gemsText = totalGems > 0 ? `${totalGems} đá quý` : 'Không đính đá';

  const hasTextEngraving = Boolean(snap.engravingText || (snap.engravings && snap.engravings.length > 0));
  const hasImgEngraving = Boolean(snap.customImages && snap.customImages.length > 0);
  let engravingText = 'Không khắc';
  if (hasTextEngraving && hasImgEngraving) {
    engravingText = 'Khắc chữ + ảnh';
  } else if (hasTextEngraving) {
    engravingText = 'Khắc chữ';
  } else if (hasImgEngraving) {
    engravingText = 'Khắc ảnh laser';
  }

  const shortId = d.designId?.length > 10 ? d.designId.slice(-6).toUpperCase() : d.designId;
  return `Mẫu #${shortId}: ${mat} · ${shape} · ${size} · ${gemsText} · ${engravingText} (v${d.designVersion || 1})`;
}

export default function DesignWorkspacePage() {
  const t = useT(designReviewMessages);
  const { language } = useLanguage();
  const lang = language === 'en' ? 'en' : 'vi';
  const navigate = useNavigate();
  const api = useDesignReviewApi();
  const workshopRingApi = useWorkshopRingDesignApi();
  const command = useCommand();
  const { status: authStatus, userId } = useAuth();
  const isAuthenticated = authStatus === 'signed-in' || Boolean(userId);
  const role = useRole();
  const isMember = isAuthenticated && role === 'MEMBER';

  const fileInputRef = useRef(null);
  const termsScrollRef = useRef(null);

  const [searchParams] = useSearchParams();
  const ringDesignRef = searchParams.get('designReference') || searchParams.get('ringDesignReference') || '';

  const [description, setDescription] = useState('');
  const [selectedImages, setSelectedImages] = useState([]);
  const [consentAgreed, setConsentAgreed] = useState(false);
  const [showTermsModal, setShowTermsModal] = useState(false);
  const [hasScrolledToBottom, setHasScrolledToBottom] = useState(false);
  const [selectedPreviewImage, setSelectedPreviewImage] = useState(null);
  const [errors, setErrors] = useState({});
  const [guestSubmission, setGuestSubmission] = useState(null);
  const [copiedLink, setCopiedLink] = useState(false);
  const [availableDesigns, setAvailableDesigns] = useState([]);
  const [selectedDesignRef, setSelectedDesignRef] = useState(ringDesignRef);
  const [loadingDesigns, setLoadingDesigns] = useState(false);
  const selectedDesignObj = availableDesigns.find((d) => d.designId === selectedDesignRef);

  useEffect(() => {
    if (!showTermsModal) return;
    const el = termsScrollRef.current;
    if (!el) return;

    const checkScroll = () => {
      if (el.clientHeight > 0 && el.scrollHeight <= el.clientHeight) {
        setHasScrolledToBottom(true);
        return;
      }
      if (el.scrollHeight > 0 && el.scrollTop + el.clientHeight >= el.scrollHeight - 20) {
        setHasScrolledToBottom(true);
      }
    };

    checkScroll();
    el.addEventListener('scroll', checkScroll, { passive: true });
    return () => el.removeEventListener('scroll', checkScroll);
  }, [showTermsModal]);

  const handleTermsScroll = (e) => {
    const { scrollTop, scrollHeight, clientHeight } = e.currentTarget;
    if (scrollHeight > 0 && scrollTop + clientHeight >= scrollHeight - 20) {
      setHasScrolledToBottom(true);
    }
  };

  const handleOpenTermsModal = () => {
    setHasScrolledToBottom(consentAgreed);
    setShowTermsModal(true);
  };

  const handleAcceptTerms = () => {
    setConsentAgreed(true);
    setShowTermsModal(false);
    setErrors(prev => {
      const copy = { ...prev };
      delete copy.consent;
      return copy;
    });
  };

  const handleDeclineTerms = () => {
    setConsentAgreed(false);
    setShowTermsModal(false);
    navigate(-1);
  };

  useEffect(() => {
    if (ringDesignRef) {
      setSelectedDesignRef(ringDesignRef);
    }
  }, [ringDesignRef]);

  useEffect(() => {
    if (!isMember) return;
    setLoadingDesigns(true);
    workshopRingApi
      .listMyDesigns(null, true, true)
      .then((designs) => {
        setAvailableDesigns(Array.isArray(designs) ? designs : []);
      })
      .catch(() => { })
      .finally(() => setLoadingDesigns(false));
  }, [isMember, workshopRingApi]);

  const charCount = [...description].length;

  async function handleFilesSelected(e) {
    const files = Array.from(e.target.files || []);
    if (!files.length) return;

    if (fileInputRef.current) fileInputRef.current.value = '';

    const oversizedFiles = files.filter(f => f.size > LIMITS.maxImageBytes);
    if (oversizedFiles.length > 0) {
      const names = oversizedFiles.map(f => `"${f.name}" (${(f.size / (1024 * 1024)).toFixed(1)} MB)`).join(', ');
      setErrors(prev => ({
        ...prev,
        images: `Dung lượng ảnh ${names} vượt quá giới hạn 5 MB. Hệ thống không cho phép tải lên ảnh vượt quá 5 MB, vui lòng chọn ảnh có kích thước nhỏ hơn.`,
      }));
    }

    const validSizeFiles = files.filter(f => f.size <= LIMITS.maxImageBytes);
    if (!validSizeFiles.length) {
      return;
    }

    const availableSlots = LIMITS.maxImages - selectedImages.length;
    if (availableSlots <= 0) {
      setErrors(prev => ({ ...prev, images: t('validation.maxImagesExceeded') }));
      return;
    }

    const filesToProcess = validSizeFiles.slice(0, availableSlots);
    if (validSizeFiles.length > availableSlots) {
      setErrors(prev => ({ ...prev, images: t('validation.maxImagesExceeded') }));
    } else if (oversizedFiles.length === 0) {
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

    const hasOversizedImage = selectedImages.some(item => item.file && item.file.size > LIMITS.maxImageBytes);
    if (hasOversizedImage) {
      newErrors.images = t('validation.imageTooLarge');
    }

    if (hasBlurryImage) {
      newErrors.images = t('validation.imageTooBlurry');
    }

    if (hasInvalidImage) {
      newErrors.images = 'Một số ảnh không hợp lệ. Vui lòng kiểm tra lại.';
    }

    if (selectedImages.length > 0 && !consentAgreed) {
      newErrors.consent = t('validation.consentRequired');
      setHasScrolledToBottom(false);
      setShowTermsModal(true);
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
      navigate(`/design-requests/${encodeURIComponent(res.result.id)}`, {
        state: { successMessage: 'Yêu cầu đã được gửi thành công, đang đợi phản hồi của nhân viên' },
      });
    }
  }

  if (!isAuthenticated) {
    return (
      <div className="design-container">
        <RequireSession requiredText="Vui lòng đăng nhập để tạo yêu cầu thiết kế tùy chỉnh" />
      </div>
    );
  }

  return (
    <div className="design-container">
        <header className="design-workspace-header">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem' }}>
            <div>
              <div className="design-workspace-eyebrow">
                <span>✨</span> Chế tác thủ công riêng biệt · Personal Custom Request
              </div>
              <h1 className="design-workspace-title">{t('workspace.title')}</h1>
              <p className="design-workspace-subtitle">{t('workspace.subtitle')}</p>
            </div>
            {isAuthenticated && (
                <Link to="/my/designs?tab=custom" className="button outline" style={{ textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: '0.5rem', fontWeight: 600, padding: '0.6rem 1.25rem', borderRadius: '8px', border: '1.5px solid #cbd5e1' }}>
                  <span>📋</span> Xem yêu cầu của tôi
                </Link>
          )}
              </div>
      </header>

        <ErrorNotice error={command.error} messages={designReviewMessages} onRetry={command.retry} />
        {errors.form && <div className="notice error">{errors.form}</div>}

        <form onSubmit={handleSubmit} className="stack">
          {/* Feature-012 Ring Design Attachment */}
            <div className="design-workspace-card" data-testid="ring-design-attachment-field">
              <h2 className="design-workspace-card-title">
                <span>💍</span> Mẫu thiết kế nhẫn Feature-012 đính kèm (Tùy chọn)
              </h2>
              <p className="design-workspace-card-desc">
                Bạn có thể liên kết một mẫu thiết kế nhẫn đã lưu hoặc hoàn thành trước đó để nghệ nhân kim hoàn tham chiếu kích thước, chất liệu và cấu trúc.
              </p>
              {isAuthenticated ? (
                <div>
                  {availableDesigns.length > 0 ? (
                <div>
                  <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
                    <select
                      id="ring-design-select"
                      value={selectedDesignRef}
                      onChange={(e) => setSelectedDesignRef(e.target.value)}
                      style={{ flex: 1, minWidth: '260px', padding: '0.75rem 1rem', borderRadius: '8px', border: '1.5px solid #cbd5e1', fontSize: '0.925rem', backgroundColor: '#ffffff' }}
                      data-testid="ring-design-select"
                    >
                      <option value="">-- Không đính kèm mẫu thiết kế --</option>
                      {availableDesigns.map((d) => (
                        <option key={d.designId} value={d.designId}>
                          {formatDesignOptionLabel(d)}
                        </option>
                      ))}
                    </select>
                    {selectedDesignRef && (
                      <button
                        type="button"
                        className="button outline small"
                        onClick={() => setSelectedDesignRef('')}
                        data-testid="clear-design-btn"
                        style={{ padding: '0.7rem 1rem', borderRadius: '8px', fontWeight: 600 }}
                      >
                        ✕ Gỡ đính kèm
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
                          marginTop: '1rem',
                          padding: '1.25rem',
                          background: 'linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%)',
                          border: '1px solid #cbd5e1',
                          borderRadius: '10px',
                          fontSize: '0.9rem',
                        }}
                        data-testid="selected-design-preview-card"
                      >
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem', flexWrap: 'wrap', gap: '0.5rem' }}>
                          <span style={{ fontWeight: 700, color: '#0f172a', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                            <span>💎</span> Chi tiết mẫu đã chọn: <code style={{ color: '#0284c7', background: '#e0f2fe', padding: '0.2rem 0.5rem', borderRadius: '4px', fontWeight: 600 }}>{selectedDesignObj.designId}</code>
                          </span>
                          <span style={{ fontSize: '0.75rem', padding: '0.25rem 0.6rem', background: '#dbeafe', color: '#1d4ed8', borderRadius: '6px', fontWeight: 700 }}>
                            Phiên bản {selectedDesignObj.designVersion} · {selectedDesignObj.status || 'SAVED'}
                          </span>
                        </div>
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '0.75rem', color: '#334155', lineHeight: 1.5 }}>
                          <div><strong>Chất liệu:</strong> {preview.material?.label || 'Bạc Ý 925'}</div>
                          <div><strong>Dáng đai:</strong> {preview.shape?.label || 'Tròn cổ điển'}</div>
                          <div><strong>Kích cỡ:</strong> Size {preview.ringSize || '8'} (Chu vi ~{preview.circumferenceMm} mm)</div>
                          <div><strong>Kích thước đai:</strong> Dày {preview.thicknessMm} mm · Rộng {preview.bandWidthMm} mm</div>
                          <div><strong>Đá quý:</strong> {totalGems > 0 ? `${totalGems} viên` : 'Không có'}</div>
                          <div><strong>Khắc cá nhân:</strong> {snap.engravingText ? `Chữ: "${snap.engravingText}"` : (snap.customImages?.length > 0 ? `${snap.customImages.length} ảnh laser` : 'Không có')}</div>
                          {cost != null && (
                            <div><strong>Giá thẩm định ước tính:</strong> {Number(cost).toLocaleString('vi-VN')} đ</div>
                          )}
                          {diffScore != null && (
                            <div><strong>Độ khó tham khảo:</strong> {diffScore}/10</div>
                          )}
                        </div>
                      </div>
                    );
                  })()}
                </div>
              ) : (
                <p className="muted small" style={{ margin: 0 }}>
                  Bạn chưa có mẫu thiết kế nào được lưu.{' '}
                  <Link to="/ring-design" target="_blank" style={{ color: '#b45309', fontWeight: 600 }}>Thiết kế mẫu nhẫn ngay ↗</Link>
                </p>
              )}
            </div>
          ) : (
            <div>
              <input
                id="ring-design-select"
                type="text"
                placeholder="Nhập mã thiết kế (VD: DES-176378...)"
                value={selectedDesignRef}
                onChange={(e) => setSelectedDesignRef(e.target.value)}
                style={{ width: '100%', padding: '0.75rem 1rem', borderRadius: '8px', border: '1.5px solid #cbd5e1' }}
                data-testid="guest-ring-design-input"
              />
              <span className="muted small" style={{ display: 'block', marginTop: '0.4rem' }}>
                Nếu bạn đã lưu một mẫu thiết kế nhẫn ở bước workshop, nhập mã thiết kế tại đây để nhân viên tham khảo.
              </span>
            </div>
          )}
        </div>

        {/* Request Description Card */}
        <div className="design-workspace-card">
          <h2 className="design-workspace-card-title">
            <span>📝</span> Mô tả ý tưởng & Yêu cầu chế tác
          </h2>
          <p className="design-workspace-card-desc">
            Hãy mô tả chi tiết mong muốn của bạn về chiếc nhẫn (chất liệu, họa tiết, kích thước đặc biệt, ngân sách hoặc thời gian dự kiến).
          </p>
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
              style={{
                width: '100%',
                padding: '0.85rem 1rem',
                borderRadius: '8px',
                border: '1.5px solid #cbd5e1',
                fontSize: '0.95rem',
                lineHeight: 1.6,
                boxSizing: 'border-box',
              }}
            />
          </Field>
        </div>

        {/* Reference Image Upload Card */}
        <div className="design-workspace-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '0.35rem' }}>
            <h2 className="design-workspace-card-title" style={{ margin: 0 }}>
              <span>🖼️</span> Hình ảnh tham khảo mẫu thiết kế
            </h2>
            <span style={{ fontSize: '0.85rem', fontWeight: 600, padding: '0.2rem 0.6rem', background: '#f1f5f9', borderRadius: '12px', color: '#475569' }}>
              {selectedImages.length}/{LIMITS.maxImages} ảnh
            </span>
          </div>
          <p className="design-workspace-card-desc">
            Tải lên bản vẽ, ảnh phác thảo hoặc hình chụp chiếc nhẫn bạn muốn chế tác. Tối đa 5 ảnh (JPEG, PNG, WEBP, tối đa 5 MB/ảnh).
          </p>

          <div className="field">
            <label htmlFor="design-images-upload" style={{ fontWeight: 600, fontSize: '0.95rem', color: '#1e293b', marginBottom: '0.5rem', display: 'block' }}>
              Hình ảnh tham khảo mẫu thiết kế (Tối đa 5 ảnh, định dạng JPEG/PNG/WEBP)
            </label>
            <label
              htmlFor="design-images-upload"
              className="image-dropzone"
              style={{ opacity: selectedImages.length >= LIMITS.maxImages || command.pending ? 0.6 : 1, display: 'flex' }}
            >
              <div className="image-dropzone-icon">📷</div>
              <div className="image-dropzone-text">
                {selectedImages.length >= LIMITS.maxImages ? 'Đã đạt giới hạn tối đa 5 ảnh' : 'Nhấn để chọn ảnh từ máy tính hoặc kéo thả vào đây'}
              </div>
              <div className="image-dropzone-subtext">
                Định dạng hỗ trợ: JPEG, PNG, WEBP · Dung lượng tối đa: 5 MB/ảnh · Tự động kiểm tra độ sắc nét
              </div>
              <input
                id="design-images-upload"
                ref={fileInputRef}
                type="file"
                multiple
                accept={ALLOWED_IMAGE_TYPES.join(',')}
                onChange={handleFilesSelected}
                disabled={command.pending || selectedImages.length >= LIMITS.maxImages}
                style={{ display: 'none' }}
              />
            </label>
            <span className="muted small" style={{ display: 'block', marginTop: '0.35rem' }}>
              Đã chọn: {selectedImages.length}/{LIMITS.maxImages} ảnh. Dung lượng tối đa 5 MB/ảnh. Hệ thống tự động kiểm tra độ sắc nét từng ảnh ngay khi chọn.
            </span>
            {errors.images && <span className="field-error" style={{ display: 'block', marginTop: '0.5rem' }}>{errors.images}</span>}
          </div>

          {hasBlurryImage && (
            <div className="blur-warning-banner" role="alert" style={{ marginTop: '1rem' }}>
              <span style={{ fontSize: '1.25rem' }}>⚠️</span>
              <div>
                <strong>{t('validation.imageTooBlurry')}</strong>
                <div style={{ fontSize: '0.85rem', marginTop: '0.2rem' }}>
                  Ảnh mờ sẽ khiến thợ kim hoàn không thể đánh giá chính xác độ khả thi. Vui lòng gỡ bỏ ảnh mờ và thay bằng ảnh chụp rõ nét hơn.
                </div>
              </div>
            </div>
          )}

          {selectedImages.length > 0 && (
            <div className="image-upload-grid" style={{ marginTop: '1.25rem' }}>
              {selectedImages.map(img => {
                const isBlurry = img.quality.status === 'TOO_BLURRY';
                const isChecking = img.quality.checking;
                const isInvalid = img.quality.status === 'INVALID';

                return (
                  <div key={img.id} className={`image-upload-card ${isBlurry ? 'blurry' : ''}`}>
                    <img
                      src={img.previewUrl}
                      alt={img.name}
                      className="image-upload-thumb"
                      style={{ cursor: 'pointer' }}
                      onClick={() => setSelectedPreviewImage(img)}
                      title="Nhấn để xem ảnh phóng to"
                    />
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
                    <div style={{ display: 'flex', gap: '0.35rem', marginTop: '0.5rem', width: '100%' }}>
                      <button
                        type="button"
                        className="button outline small"
                        style={{ flex: 1, padding: '0.35rem 0.5rem', fontSize: '0.8rem', borderRadius: '6px' }}
                        onClick={() => setSelectedPreviewImage(img)}
                      >
                        🔍 Xem
                      </button>
                      <button
                        type="button"
                        className="button outline small"
                        style={{ flex: 1, padding: '0.35rem 0.5rem', fontSize: '0.8rem', color: '#dc2626', borderColor: '#fca5a5', borderRadius: '6px' }}
                        onClick={() => handleRemoveImage(img.id)}
                        disabled={command.pending}
                      >
                        Gỡ bỏ
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {selectedImages.length > 0 && (
          <div className="consent-card" role="region" aria-label={t('consent.title')}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
              <h3 style={{ margin: 0 }}>{t('consent.title')}</h3>
              {consentAgreed && (
                <span className="badge-tag badge-success">{t('consent.acceptedBadge')}</span>
              )}
            </div>
            <p className="small muted" style={{ margin: '0.5rem 0' }}>
              {t('consent.cardSummary')}
            </p>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap', marginTop: '0.75rem' }}>
              <button
                type="button"
                className={`button small ${consentAgreed ? 'outline' : 'primary'}`}
                onClick={handleOpenTermsModal}
                disabled={command.pending}
              >
                {consentAgreed ? t('consent.reviewModal') : t('consent.openModal')}
              </button>
              <label className="consent-checkbox" style={{ margin: 0 }}>
                <input
                  type="checkbox"
                  checked={consentAgreed}
                  onChange={() => {
                    if (!consentAgreed) {
                      setHasScrolledToBottom(false);
                      setShowTermsModal(true);
                    } else {
                      setConsentAgreed(false);
                    }
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
            </div>
            {errors.consent && <div className="field-error" style={{ marginTop: '0.5rem' }}>{errors.consent}</div>}
          </div>
        )}

        <div className="actions" style={{ marginTop: '2rem', display: 'flex', justifyContent: 'flex-end' }}>
          <button
            type="submit"
            className="button primary btn-luxury-submit"
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

      {showTermsModal && (
        <div className="dialog-backdrop" role="presentation" style={{ zIndex: 1000 }}>
          <div
            className="dialog terms-modal-container"
            role="dialog"
            aria-modal="true"
            aria-labelledby="terms-modal-title"
          >
            <h2 id="terms-modal-title" style={{ marginTop: 0, marginBottom: '0.5rem', fontSize: '1.25rem' }}>
              {t('consent.modalTitle')}
            </h2>
            <p className="small muted" style={{ marginTop: 0, marginBottom: '1rem' }}>
              {t('consent.modalSubtitle')}
            </p>

            <div
              data-testid="terms-scroll-box"
              ref={termsScrollRef}
              onScroll={handleTermsScroll}
              tabIndex={0}
              className="terms-scroll-box"
            >
              {lang === 'en' ? (
                <>
                  <h4>1. Purpose of Using Crafting Images</h4>
                  <p>
                    Mland Workshop will use reference images, sketches, and materials provided by you exclusively for technical feasibility analysis, 3D modeling, and custom handmade ring crafting according to your individual order. By submitting images with this request, you consent to our store, technicians, and jewelers storing and using the images throughout the design, crafting, and warranty processes.
                  </p>
                  <h4>2. Disclaimer of Copyright & Intellectual Property Liability</h4>
                  <p>
                    Mland Workshop operates as an artisan manufacturing service based on custom customer requests. <strong>The workshop assumes NO liability whatsoever for any copyright infringement, trademark disputes, or intellectual property violations</strong> arising from or related to images, logos, or designs uploaded by you. You represent that you possess lawful rights to use the submitted images and bear sole legal responsibility toward third parties and governing authorities.
                  </p>
                  <h4>3. Customer Agreement Upon Submission</h4>
                  <p>
                    By attaching and submitting images with this custom crafting request, you explicitly and unconditionally agree that the workshop has full rights to utilize these images for the purpose of creating this design.
                  </p>
                  <h4>4. Confidentiality & Internal Storage</h4>
                  <p>
                    All image assets and request details will remain confidential and circulated internally solely for fulfilling your crafting order in accordance with Mland privacy standards, and will not be mass commercialized without prior written agreement.
                  </p>
                  <h4>5. Execution & Navigation Terms</h4>
                  <p>
                    You must scroll to the bottom to read these complete terms. If you choose <strong>"Accept"</strong>, the system will record your agreement and allow you to proceed with submitting the request. If you choose <strong>"Decline"</strong>, you cannot proceed and will be redirected to the previous screen.
                  </p>
                  <div style={{ marginTop: '1.25rem', padding: '0.625rem', background: '#e2e8f0', borderRadius: '4px', textAlign: 'center', fontWeight: 'bold', fontSize: '0.8rem', color: '#475569' }}>
                    --- END OF TERMS & CONDITIONS ---
                  </div>
                </>
              ) : (
                <>
                  <h4>1. Mục đích sử dụng hình ảnh chế tác</h4>
                  <p>
                    Workshop Mland sẽ sử dụng hình ảnh, bản phác thảo và tài liệu tham khảo do Quý khách cung cấp độc quyền phục vụ việc nghiên cứu tính khả thi kỹ thuật, lập bản vẽ mô phỏng 3D và trực tiếp gia công chế tác sản phẩm nhẫn thủ công theo đơn đặt hàng cá nhân của Quý khách. Khi gửi hình ảnh này kèm theo yêu cầu, Quý khách chấp thuận việc cửa hàng, chuyên viên kỹ thuật và nghệ nhân kim hoàn lưu trữ và sử dụng hình ảnh cho toàn bộ quy trình thiết kế, chế tác và bảo hành sản phẩm.
                  </p>
                  <h4>2. Miễn trừ trách nhiệm về bản quyền và sở hữu trí tuệ</h4>
                  <p>
                    Workshop Mland hoạt động với tư cách là cơ sở gia công chế tác kim hoàn thủ công theo yêu cầu cá nhân hóa của khách hàng. <strong>Workshop hoàn toàn không chịu trách nhiệm</strong> về bất kỳ khiếu nại, tranh chấp, khiếu kiện bản quyền, nhãn hiệu thương mại hoặc hành vi xâm phạm quyền sở hữu trí tuệ nào phát sinh từ hình ảnh, logo, hoa văn hoặc mẫu thiết kế do Quý khách tải lên. Quý khách cam kết có toàn quyền hợp pháp sử dụng các hình ảnh đã gửi và tự chịu toàn bộ trách nhiệm trước pháp luật cũng như các bên thứ ba liên quan.
                  </p>
                  <h4>3. Chấp thuận của khách hàng khi gửi yêu cầu</h4>
                  <p>
                    Bằng việc gửi hình ảnh kèm theo yêu cầu chế tác riêng này, Quý khách chính thức xác nhận và đồng ý vô điều kiện rằng workshop có toàn quyền sử dụng hình ảnh cho mục đích chế tác mẫu thiết kế này.
                  </p>
                  <h4>4. Bảo mật thông tin nội bộ</h4>
                  <p>
                    Toàn bộ tư liệu hình ảnh của Quý khách chỉ được lưu hành nội bộ phục vụ việc sản xuất đơn hàng của Quý khách tại xưởng, tuân thủ chính sách bảo mật của Mland và không sử dụng cho mục đích kinh doanh đại trà khi chưa có sự đồng ý của Quý khách.
                  </p>
                  <h4>5. Điều khoản thực hiện</h4>
                  <p>
                    Quý khách cần cuộn xuống dưới cùng để đọc hết toàn bộ nội dung văn bản này. Nếu Quý khách chọn <strong>"Chấp nhận"</strong>, hệ thống sẽ ghi nhận và cho phép Quý khách tiếp tục gửi yêu cầu chế tác. Nếu Quý khách chọn <strong>"Từ chối"</strong>, hệ thống sẽ không cho phép tiếp tục và tự động đưa Quý khách trở về màn hình trước đó.
                  </p>
                  <div style={{ marginTop: '1.25rem', padding: '0.625rem', background: '#e2e8f0', borderRadius: '4px', textAlign: 'center', fontWeight: 'bold', fontSize: '0.8rem', color: '#475569' }}>
                    --- HẾT NỘI DUNG ĐIỀU KHOẢN ---
                  </div>
                </>
              )}
            </div>

            {!hasScrolledToBottom ? (
              <div data-testid="scroll-warning" className="terms-status-notice warning">
                <span>⚠️</span>
                <span>{t('consent.modalScrollWarning')}</span>
              </div>
            ) : (
              <div data-testid="scroll-success" className="terms-status-notice success">
                <span>✓</span>
                <span>{t('consent.modalScrollSuccess')}</span>
              </div>
            )}

            <div className="dialog-actions" style={{ marginTop: '1.25rem', display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
              <button
                type="button"
                className="button outline danger"
                onClick={handleDeclineTerms}
                disabled={!hasScrolledToBottom}
              >
                {t('consent.decline')}
              </button>
              <button
                type="button"
                className="button primary"
                onClick={handleAcceptTerms}
                disabled={!hasScrolledToBottom}
              >
                {t('consent.accept')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Full-size image modal */}
      {selectedPreviewImage && (
        <div
          role="dialog"
          aria-modal="true"
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            width: '100vw',
            height: '100vh',
            background: 'rgba(0, 0, 0, 0.75)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999,
            padding: '1rem',
          }}
          onClick={() => setSelectedPreviewImage(null)}
        >
          <div
            style={{
              background: '#ffffff',
              padding: '1rem',
              borderRadius: '8px',
              maxWidth: '90vw',
              maxHeight: '90vh',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
            }}
            onClick={e => e.stopPropagation()}
          >
            <img
              src={selectedPreviewImage.previewUrl}
              alt={selectedPreviewImage.name}
              style={{ maxWidth: '100%', maxHeight: '75vh', objectFit: 'contain' }}
            />
            <div style={{ marginTop: '0.75rem', display: 'flex', justifyContent: 'space-between', width: '100%', alignItems: 'center' }}>
              <span className="small muted">{selectedPreviewImage.name}</span>
              <button type="button" className="button outline small" onClick={() => setSelectedPreviewImage(null)}>
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
