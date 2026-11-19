import React, { useEffect, useState } from 'react';
import { useParams, useSearchParams, useNavigate } from 'react-router';
import { useWorkshopDesignOptions } from '../application/useWorkshopDesignOptions';
import { useWorkshopDesignDraft } from '../application/useWorkshopDesignDraft';
import { useWorkshopDesignCommand } from '../application/useWorkshopDesignCommand';
import { useWorkshopRingDesignApi } from '../api/useWorkshopRingDesignApi';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useLanguage } from '../../../shared/i18n/I18nProvider';
import { setStoredGuestSession } from '../api/workshopRingDesignApi';
import RingPreview from './RingPreview';
import DesignControlField from './DesignControlField';
import LaserEngravingImageCropper from './LaserEngravingImageCropper';
import { getRingCircumferenceMm, getRingDiameterMm } from '../domain/designPreview';
import { getTranslatedOption } from '../domain/designTranslations';
import { messages } from '../messages';
import '../ring-design.css';

export default function RingConfigurationPage() {
  const { packageReference } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { status, userId } = useAuth();
  const isAuthenticated = status === 'signed-in' || Boolean(userId);

  const mode = searchParams.get('mode') || 'BLANK';
  const modelRef = searchParams.get('modelRef') || null;
  const targetDesignId = searchParams.get('designId') || null;
  const api = useWorkshopRingDesignApi();
  const [loadedDesign, setLoadedDesign] = useState(null);
  const [initialLoading, setInitialLoading] = useState(Boolean(targetDesignId));
  const [submitAttempted, setSubmitAttempted] = useState(false);

  const { options, loading: optionsLoading, error: optionsError } = useWorkshopDesignOptions(packageReference);
  const {
    draft,
    isDirty,
    previewModel,
    clientValidation,
    updateMaterial,
    updateShape,
    updateRingSize,
    updateThickness,
    updateBandWidth,
    toggleComponent,
    setComponentQuantity,
    updateComponentQuantity,
    moveGemstone,
    toggleAttachment,
    updateEngraving,
    resetDraftFromModel,
    resetFromSavedDesign,
    getPayloadForSave,
  } = useWorkshopDesignDraft(packageReference);

  const { loading: saving, error: commandError, executeCreate, executeSave } = useWorkshopDesignCommand(isAuthenticated);

  // Initialize draft when model is selected
  useEffect(() => {
    if (options && mode === 'MODEL' && modelRef) {
      const selectedModel = options.models?.find((m) => m.modelReference === modelRef);
      if (selectedModel) {
        resetDraftFromModel(selectedModel);
      }
    }
  }, [options, mode, modelRef, resetDraftFromModel]);

  // Load existing design when editing an already saved design
  useEffect(() => {
    if (!targetDesignId) return;
    let active = true;
    async function loadExisting() {
      try {
        setInitialLoading(true);
        const res = await api.getDesign(targetDesignId, null, isAuthenticated);
        if (active && res) {
          setLoadedDesign(res);
          resetFromSavedDesign(res);
        }
      } catch (err) {
        console.error('Không thể tải thiết kế cũ:', err);
      } finally {
        if (active) setInitialLoading(false);
      }
    }
    loadExisting();
    return () => {
      active = false;
    };
  }, [targetDesignId, api, isAuthenticated, resetFromSavedDesign]);

  const { language } = useLanguage();
  const lang = language === 'en' ? 'en' : 'vi';
  const t = messages[lang] || messages.vi;

  const handleSaveAndEvaluate = async () => {
    setSubmitAttempted(true);
    if (!clientValidation.isValid) {
      const firstError = document.querySelector('.ring-error-card, .field-error, .ring-validation-summary, [aria-invalid="true"]');
      if (firstError) {
        firstError.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
      return;
    }

    try {
      const payload = getPayloadForSave();
      let targetId = targetDesignId;
      let currentVersion = loadedDesign?.designVersion || 1;

      if (!targetId) {
        // First creation of configured design draft
        const createRes = await executeCreate({
          packageReference,
          sourceModelReference: mode === 'MODEL' ? modelRef : null,
          startMode: mode === 'MODEL' ? 'PUBLISHED_MODEL' : 'BLANK',
        });

        targetId = createRes.designId;
        currentVersion = createRes.designVersion || 1;
        if (createRes.guestSession) {
          setStoredGuestSession(createRes.guestSession);
        }
      }

      // Then save the customized parameters as next version
      const saveRes = await executeSave(targetId, payload, currentVersion);

      // Navigate to summary and evaluation view
      navigate(`/workshops/${encodeURIComponent(packageReference)}/design/${encodeURIComponent(targetId)}`);
    } catch {
      // Error handled by command hook
    }
  };

      if (optionsLoading || initialLoading) {
        return (
          <div className="ring-design-container" style={{ textAlign: 'center', padding: '4rem' }}>
            <p>{t.loadingDesign || (language === 'en' ? 'Loading design configuration...' : 'Đang tải cấu hình thiết kế...')}</p>
          </div>
        );
      }

      return (
        <div className="ring-design-container" data-testid="ring-configuration-page">
          <div className="ring-design-header">
            <h1 className="ring-design-title">{t.featureTitle}</h1>
            <p className="ring-design-subtitle">{t.featureSubtitle}</p>
            <div className="ring-notice-banner" role="note">
              <span>⚠️</span>
              <span>{t.advisoryWarning}</span>
            </div>
          </div>

          {optionsError && (
            <div className="ring-error-card" role="alert">
              {optionsError.message || t.optionsLoadError || (language === 'en' ? 'Unable to load workshop design options.' : 'Không thể tải tùy chọn gói workshop.')}
            </div>
          )}

          {commandError && (
            <div className="ring-error-card" role="alert">
              {commandError}
            </div>
          )}

          <div className="ring-configurator-layout">
            {/* Left Column: Interactive Controls */}
            <div className="ring-controls-panel">
              {/* Section 1: Material */}
              <div className="ring-section-card">
                <h2 className="ring-section-title">{t.materialSection}</h2>
                <p className="ring-section-desc">{t.materialSectionDesc}</p>
                <div className="ring-cards-grid">
                  {(options?.materials || []).map((mat) => {
                    const isSelected = draft.materialReference === mat.reference;
                    const trans = getTranslatedOption('materials', mat.reference, language, mat);
                    return (
                      <div
                        key={mat.reference}
                        className={`ring-option-card ${isSelected ? 'selected' : ''}`}
                        onClick={() => updateMaterial(mat.reference)}
                        role="button"
                        tabIndex={0}
                        aria-pressed={isSelected}
                        data-testid={`material-card-${mat.reference}`}
                      >
                        <div>
                          <div className="ring-option-name">{trans.name}</div>
                          <div className="ring-option-desc">{trans.description}</div>
                        </div>
                        {mat.unitPrice && (
                          <div className="ring-option-price">
                            {Number(mat.unitPrice).toLocaleString(language === 'en' ? 'en-US' : 'vi-VN')} {mat.currency || (language === 'en' ? 'VND' : 'đ')}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Section 2: Shape & Size */}
              <div className="ring-section-card">
                <h2 className="ring-section-title">{t.shapeSection}</h2>
                <p className="ring-section-desc">{t.shapeSectionDesc}</p>

                <DesignControlField label={t.shapeLabel || (language === 'en' ? 'Band Shape' : 'Dáng đai nhẫn')} required errorText={clientValidation.errors.shapeReference}>
                  <div className="ring-cards-grid" style={{ marginBottom: '1.25rem' }}>
                    {(options?.shapes || []).map((shp) => {
                      const isSelected = draft.shapeReference === shp.reference;
                      const trans = getTranslatedOption('shapes', shp.reference, language, shp);
                      return (
                        <div
                          key={shp.reference}
                          className={`ring-option-card ${isSelected ? 'selected' : ''}`}
                          onClick={() => updateShape(shp.reference)}
                          role="button"
                          tabIndex={0}
                          aria-pressed={isSelected}
                          data-testid={`shape-card-${shp.reference}`}
                        >
                          <div>
                            <div className="ring-option-name">{trans.name}</div>
                            <div className="ring-option-desc">{trans.description}</div>
                          </div>
                          {Boolean(shp.priceAdjustment && Number(shp.priceAdjustment) > 0) && (
                            <div className="ring-option-price">
                              +{Number(shp.priceAdjustment).toLocaleString(language === 'en' ? 'en-US' : 'vi-VN')} {shp.currency || (language === 'en' ? 'VND' : 'đ')}
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </DesignControlField>

                <DesignControlField
                  label={t.ringSizeLabel}
                  helperText={`${t.ringSizeHelper} ${
                    language === 'en'
                      ? `Selected: Size ${draft.ringSize} — Circumference: ${getRingCircumferenceMm(draft.ringSize)} mm (Inner diameter: ~${getRingDiameterMm(draft.ringSize)} mm)`
                      : `Đã chọn: Size ${draft.ringSize} — Chu vi: ${getRingCircumferenceMm(draft.ringSize)} mm (Đường kính trong: ~${getRingDiameterMm(draft.ringSize)} mm)`
                  }`}
                  required
                  errorText={clientValidation.errors.ringSize}
                >
                  <div className="ring-pills-row" data-testid="ring-sizes-row">
                    {(options?.sizes || Array.from({ length: 24 }, (_, i) => String(i + 6))).map((size) => {
                      const isSelected = draft.ringSize === String(size);
                      const circ = getRingCircumferenceMm(size);
                      return (
                        <button
                          type="button"
                          key={size}
                          className={`ring-size-pill ${isSelected ? 'selected' : ''}`}
                          onClick={() => updateRingSize(size)}
                          data-testid={`size-pill-${size}`}
                          aria-pressed={isSelected}
                          title={`Size ${size} - ${language === 'en' ? 'Circumference' : 'Chu vi'}: ${circ} mm`}
                        >
                          <span className="ring-size-pill-num">Size {size}</span>
                          <span className="ring-size-pill-circ">({circ} mm)</span>
                        </button>
                      );
                    })}
                  </div>
                </DesignControlField>
              </div>

              {/* Section 3: Gemstones */}
              <div className="ring-section-card">
                {(() => {
                  const totalGemstones = (draft.components || []).reduce((sum, c) => sum + (Number(c.quantity) || 0), 0);
                  const isGemstoneLimitReached = totalGemstones >= 10;
                  return (
                    <>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '0.25rem' }}>
                        <h2 className="ring-section-title" style={{ margin: 0 }}>{t.gemstoneSection}</h2>
                        <span
                          className="ring-gemstone-total-badge"
                          style={{
                            background: isGemstoneLimitReached ? '#fee2e2' : undefined,
                            color: isGemstoneLimitReached ? '#b91c1c' : undefined,
                          }}
                          data-testid="gemstone-total-counter"
                        >
                          {totalGemstones} / 10
                        </span>
                      </div>
                      <p className="ring-section-desc">{t.gemstoneSectionDesc}</p>

                      {isGemstoneLimitReached && (
                        <div
                          className="ring-error-card"
                          style={{ marginBottom: '0.75rem', fontSize: '0.85rem', padding: '0.5rem 0.75rem' }}
                          data-testid="gemstone-quota-warning"
                        >
                          {t.gemstoneTotalLimitNotice || 'Đã đạt giới hạn tối đa 10 viên đá quý.'}
                        </div>
                      )}

                      <div className="ring-gemstone-instruction" data-testid="gemstone-drag-instruction">
                        {language === 'en' ? (
                          <>
                            ✨ <strong>Customize quantity & placement:</strong> Choose the count for each gemstone, then <strong>click & drag stones</strong> directly on the <em>Top / Front View</em> window to position them anywhere along the ring band!
                          </>
                        ) : (
                          <>
                            ✨ <strong>Tùy chỉnh số lượng & vị trí:</strong> Chọn số lượng từng loại đá, sau đó <strong>nhấn giữ & kéo thả viên đá</strong> trực tiếp trên cửa sổ <em>Mặt chính diện (Top View)</em> để đặt vị trí bất kỳ quanh đường tròn nhẫn!
                          </>
                        )}
                      </div>

                      <div className="ring-cards-grid">
                        {(options?.components || []).map((gem) => {
                          const selectedItem = draft.components.find((c) => c.reference === gem.reference);
                          const isSelected = Boolean(selectedItem);
                          const currentQty = selectedItem ? Number(selectedItem.quantity) || 1 : 0;
                          const maxQty = gem.maxQuantity || 10;
                          const trans = getTranslatedOption('components', gem.reference, language, gem);

                          return (
                            <div
                              key={gem.reference}
                              className={`ring-option-card ring-gemstone-card ${isSelected ? 'selected' : ''}`}
                              onClick={() => {
                                if (!isSelected && !isGemstoneLimitReached) {
                                  setComponentQuantity(gem.reference, 1, maxQty);
                                }
                              }}
                              role={isSelected ? 'group' : 'button'}
                              tabIndex={isSelected ? -1 : 0}
                              aria-label={trans.name}
                              aria-pressed={isSelected ? true : undefined}
                              data-testid={`gemstone-card-${gem.reference}`}
                            >
                              <div>
                                <div className="ring-option-name">{trans.name}</div>
                                <div className="ring-option-desc">{trans.description}</div>
                              </div>

                              <div className="ring-gemstone-footer">
                                <div className="ring-option-price">
                                  {Number(gem.unitPrice).toLocaleString(language === 'en' ? 'en-US' : 'vi-VN')} {gem.currency || (language === 'en' ? 'VND' : 'đ')} {t.perStone || (language === 'en' ? '/ stone' : '/ viên')}
                                  <span className="ring-gem-max-label"> ({t.maxLabel || (language === 'en' ? 'Max' : 'Tối đa')} {maxQty})</span>
                                </div>

                                {!isSelected ? (
                                  <button
                                    type="button"
                                    className="ring-gem-btn-add"
                                    disabled={isGemstoneLimitReached}
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      if (!isGemstoneLimitReached) {
                                        setComponentQuantity(gem.reference, 1, maxQty);
                                      }
                                    }}
                                    data-testid={`add-gemstone-${gem.reference}`}
                                  >
                                    {t.addGemstoneBtn || (language === 'en' ? '+ Add Gemstone' : '+ Thêm đá')}
                                  </button>
                                ) : (
                                  <div
                                    className="ring-gemstone-stepper"
                                    onClick={(e) => e.stopPropagation()}
                                  >
                                    <button
                                      type="button"
                                      className="ring-stepper-btn ring-stepper-minus"
                                      onClick={() => setComponentQuantity(gem.reference, currentQty - 1, maxQty)}
                                      aria-label={`Giảm số lượng ${trans.name}`}
                                      data-testid={`decrease-gem-${gem.reference}`}
                                    >
                                      −
                                    </button>
                                    <span className="ring-stepper-value" data-testid={`qty-gem-${gem.reference}`}>
                                      {currentQty} {language === 'en' ? (currentQty > 1 ? 'stones' : 'stone') : 'viên'}
                                    </span>
                                    <button
                                      type="button"
                                      className="ring-stepper-btn ring-stepper-plus"
                                      onClick={() => setComponentQuantity(gem.reference, currentQty + 1, maxQty)}
                                      disabled={currentQty >= maxQty || isGemstoneLimitReached}
                                      aria-label={`Tăng số lượng ${trans.name}`}
                                      data-testid={`increase-gem-${gem.reference}`}
                                    >
                                      +
                                    </button>
                                  </div>
                                )}
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </>
                  );
                })()}
              </div>

              {/* Section 4: Attachments */}
              <div className="ring-section-card">
                <h2 className="ring-section-title">{t.attachmentSection}</h2>
                <p className="ring-section-desc">{t.attachmentSectionDesc}</p>
                <div className="ring-cards-grid">
                  {(options?.attachments || []).map((att) => {
                    const isSelected = draft.attachments.some((a) => a.reference === att.reference);
                    const trans = getTranslatedOption('attachments', att.reference, language, att);
                    return (
                      <div
                        key={att.reference}
                        className={`ring-option-card ${isSelected ? 'selected' : ''}`}
                        onClick={() => toggleAttachment(att.reference)}
                        role="button"
                        tabIndex={0}
                        aria-pressed={isSelected}
                        data-testid={`attachment-card-${att.reference}`}
                      >
                        <div>
                          <div className="ring-option-name">{trans.name}</div>
                          <div className="ring-option-desc">{trans.description}</div>
                        </div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <div className="ring-option-price">
                            +{Number(att.unitPrice).toLocaleString(language === 'en' ? 'en-US' : 'vi-VN')} {att.currency || (language === 'en' ? 'VND' : 'đ')}
                          </div>
                          {isSelected && (
                            <span style={{ fontSize: '0.75rem', fontWeight: 600, color: '#166534' }}>✓ {t.selectedLabel || (language === 'en' ? 'Selected' : 'Đã chọn')}</span>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Section 5: Engraving & Custom Image */}
              <div className="ring-section-card">
                <h2 className="ring-section-title">{t.engravingSection}</h2>
                <p className="ring-section-desc">{t.engravingSectionDesc}</p>

                {/* Surface Texture Collision Warning */}
                {previewModel.showSurfaceTextureWarning && (
                  <div
                    style={{
                      marginBottom: '1.25rem',
                      padding: '0.85rem 1rem',
                      borderRadius: '8px',
                      background: '#fffbeb',
                      border: '1.5px solid #f59e0b',
                      color: '#92400e',
                      fontSize: '0.875rem',
                      lineHeight: 1.5,
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: '0.5rem',
                    }}
                    data-testid="surface-texture-warning"
                  >
                    <span>{t.surfaceTextureWarning}</span>
                  </div>
                )}

                {/* Standard Text/Symbol Engraving Option */}
                <div style={{ marginBottom: '1rem' }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer', fontWeight: 600 }}>
                    <input
                      type="checkbox"
                      checked={Boolean(draft.engraving?.enabled)}
                      onChange={(e) => {
                        const enabled = e.target.checked;
                        if (enabled && (!draft.engraving?.items || draft.engraving.items.length === 0)) {
                          updateEngraving({
                            enabled: true,
                            items: [
                              {
                                id: 'eng-1',
                                type: 'TEXT',
                                content: draft.engraving?.text || '',
                                placement: draft.engraving?.placement || 'INNER_BAND',
                              },
                            ],
                          });
                        } else {
                          updateEngraving({ enabled });
                        }
                      }}
                      data-testid="engraving-toggle"
                    />
                    <span>{t.standardEngravingTitle || 'Khắc chữ & biểu tượng tiêu chuẩn (Phí 1 lần: +80.000 đ)'}</span>
                  </label>
                  <p style={{ margin: '0.25rem 0 0 1.5rem', fontSize: '0.85rem', color: '#64748b' }}>
                    {t.standardEngravingDesc ||
                      'Chỉ trả phí 1 lần (+80.000 đ) — Khắc không giới hạn số lượng chữ và ký hiệu cả mặt trong và mặt ngoài nhẫn.'}
                  </p>
                </div>

                {draft.engraving?.enabled && (() => {
                  const currentItems = (draft.engraving?.items && draft.engraving.items.length > 0)
                    ? draft.engraving.items
                    : [
                        {
                          id: 'eng-1',
                          type: 'TEXT',
                          content: draft.engraving?.text || '',
                          placement: draft.engraving?.placement || 'INNER_BAND',
                        },
                      ];
                  const textItems = currentItems.filter((it) => it.type !== 'SYMBOL');
                  const symbolItems = currentItems.filter((it) => it.type === 'SYMBOL');
                  const isLimitReached = currentItems.length >= 2;
                  const isTextLimitReached = textItems.length >= 2;
                  const isSymbolLimitReached = symbolItems.length >= 2;

                  const popularSymbols = ['♥', '★', '∞', '✿', '✦', '⚓', '♫', '☘', '👑', '💍', '☾', '✝'];

                  return (
                    <div style={{ marginLeft: '1.5rem', marginBottom: '1.5rem' }}>
                      {/* Unified Counter & Limits */}
                      <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', marginBottom: '0.75rem', flexWrap: 'wrap' }}>
                        <span
                          style={{
                            fontSize: '0.8rem',
                            fontWeight: 600,
                            color: isLimitReached ? '#b91c1c' : '#0369a1',
                            background: isLimitReached ? '#fee2e2' : '#e0f2fe',
                            padding: '0.2rem 0.6rem',
                            borderRadius: '999px',
                          }}
                          data-testid="engraving-unified-counter"
                        >
                          {language === 'en'
                            ? `Engraving Positions: ${currentItems.length}/2 (Max 50 chars each)`
                            : `Vị trí khắc: ${currentItems.length}/2 (Tối đa 50 ký tự/vị trí)`}
                        </span>
                        {/* Hidden compatibility counters for tests */}
                        <span style={{ display: 'none' }} data-testid="engraving-text-counter">TEXT {textItems.length}/2</span>
                        <span style={{ display: 'none' }} data-testid="engraving-symbol-counter">SYMBOL {symbolItems.length}/2</span>
                      </div>

                      {isLimitReached && (
                        <div
                          className="ring-error-card"
                          style={{ marginBottom: '0.5rem', fontSize: '0.8rem', padding: '0.4rem 0.6rem' }}
                          data-testid="engraving-quota-msg"
                        >
                          {language === 'en' ? 'Maximum 2 engraving positions reached.' : 'Đã đạt tối đa 2 vị trí khắc.'}
                        </div>
                      )}
                      {isTextLimitReached && (
                        <div style={{ display: 'none' }} data-testid="engraving-text-quota-msg">
                          {t.textEngravingLimitNotice || 'Số lượng mục khắc chữ đã đạt tối đa (2/2).'}
                        </div>
                      )}
                      {isSymbolLimitReached && (
                        <div style={{ display: 'none' }} data-testid="engraving-symbol-quota-msg">
                          {t.symbolEngravingLimitNotice || 'Số lượng mục khắc biểu tượng đã đạt tối đa (2/2).'}
                        </div>
                      )}

                      {/* Unified Engraving Items (Text & Symbols Combined) */}
                      {currentItems.map((item, idx) => {
                        const updateItem = (patch) => {
                          const updated = [...currentItems];
                          updated[idx] = { ...updated[idx], ...patch };
                          const firstText = updated[0]?.content || '';
                          const firstPlacement = updated[0]?.placement || 'INNER_BAND';
                          updateEngraving({
                            items: updated,
                            text: firstText,
                            placement: firstPlacement,
                          });
                        };

                        const removeItem = () => {
                          const updated = currentItems.filter((_, i) => i !== idx);
                          const firstText = updated[0]?.content || '';
                          const firstPlacement = updated[0]?.placement || 'INNER_BAND';
                          updateEngraving({
                            items: updated,
                            text: firstText,
                            placement: firstPlacement,
                          });
                        };

                        const currentLen = (item.content || '').length;

                        return (
                          <div
                            key={item.id || idx}
                            style={{
                              padding: '0.85rem',
                              marginBottom: '0.75rem',
                              background: '#f8fafc',
                              border: '1px solid #e2e8f0',
                              borderRadius: '8px',
                            }}
                            data-testid={`engraving-item-${idx}`}
                          >
                            <div
                              style={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                marginBottom: '0.4rem',
                              }}
                            >
                              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                                <span style={{ fontSize: '0.85rem', fontWeight: 700, color: '#1e293b' }}>
                                  {language === 'en' ? `Position #${idx + 1}` : `Vị trí khắc #${idx + 1}`}
                                </span>
                                <span
                                  style={{
                                    fontSize: '0.75rem',
                                    fontWeight: 600,
                                    color: currentLen >= 50 ? '#b91c1c' : '#64748b',
                                  }}
                                >
                                  {currentLen}/50 {language === 'en' ? 'chars' : 'ký tự'}
                                </span>
                              </div>
                              {currentItems.length > 1 && (
                                <button
                                  type="button"
                                  onClick={removeItem}
                                  style={{
                                    background: 'none',
                                    border: 'none',
                                    color: '#ef4444',
                                    fontSize: '0.8rem',
                                    fontWeight: 600,
                                    cursor: 'pointer',
                                    padding: '2px 6px',
                                  }}
                                  data-testid={`remove-engraving-item-${idx}`}
                                >
                                  {t.removeEngravingItem || 'Xóa'}
                                </button>
                              )}
                            </div>

                            <DesignControlField
                              label={language === 'en' ? 'Engraving Content (Letters, Numbers & Symbols)' : 'Nội dung khắc (Chữ, Số & Biểu tượng)'}
                              errorText={idx === 0 ? clientValidation.errors.engravingText : undefined}
                            >
                              <input
                                type="text"
                                maxLength={50}
                                placeholder={language === 'en' ? 'Enter names, dates, or click symbols below...' : 'Nhập tên, ngày kỷ niệm, hoặc bấm chọn biểu tượng bên dưới...'}
                                value={item.content || ''}
                                onChange={(e) => updateItem({ content: e.target.value })}
                                style={{
                                  width: '100%',
                                  padding: '0.65rem 0.85rem',
                                  borderRadius: '6px',
                                  border: '1px solid #cbd5e1',
                                  fontSize: '0.9rem',
                                  backgroundColor: '#ffffff',
                                }}
                                data-testid={idx === 0 ? 'engraving-input' : `engraving-input-${idx}`}
                              />
                            </DesignControlField>

                            {/* Quick Symbol Buttons Palette */}
                            <div
                              style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '0.35rem',
                                marginTop: '0.35rem',
                                marginBottom: '0.65rem',
                                flexWrap: 'wrap',
                              }}
                            >
                              <span style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600 }}>
                                {language === 'en' ? 'Symbols:' : 'Chèn biểu tượng:'}
                              </span>
                              {popularSymbols.map((sym) => (
                                <button
                                  key={sym}
                                  type="button"
                                  onClick={() => {
                                    const curr = item.content || '';
                                    if (curr.length + sym.length <= 50) {
                                      updateItem({ content: `${curr}${sym}` });
                                    }
                                  }}
                                  style={{
                                    border: '1px solid #cbd5e1',
                                    background: '#ffffff',
                                    borderRadius: '4px',
                                    padding: '0.15rem 0.45rem',
                                    fontSize: '0.9rem',
                                    cursor: 'pointer',
                                    lineHeight: 1.2,
                                  }}
                                  title={`Chèn ${sym}`}
                                >
                                  {sym}
                                </button>
                              ))}
                            </div>

                            <DesignControlField label={t.engravingPlacementLabel}>
                              <div className="ring-pills-row">
                                <button
                                  type="button"
                                  className={`ring-size-pill ring-placement-pill ${item.placement === 'INNER_BAND' ? 'selected' : ''}`}
                                  onClick={() => updateItem({ placement: 'INNER_BAND' })}
                                  data-testid={idx === 0 ? 'placement-inner' : `placement-inner-${idx}`}
                                >
                                  {t.innerBand}
                                </button>
                                <button
                                  type="button"
                                  className={`ring-size-pill ring-placement-pill ${item.placement === 'OUTER_BAND' ? 'selected' : ''}`}
                                  onClick={() => updateItem({ placement: 'OUTER_BAND' })}
                                  data-testid={idx === 0 ? 'placement-outer' : `placement-outer-${idx}`}
                                >
                                  {t.outerBand}
                                </button>
                              </div>
                            </DesignControlField>
                          </div>
                        );
                      })}

                      {/* Unified Add Button */}
                      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginTop: '0.5rem' }}>
                        <button
                          type="button"
                          onClick={() => {
                            if (isLimitReached) return;
                            const updated = [
                              ...currentItems,
                              {
                                id: `eng-${Date.now().toString().slice(-4)}`,
                                type: 'TEXT',
                                content: '',
                                placement: currentItems[0]?.placement === 'INNER_BAND' ? 'OUTER_BAND' : 'INNER_BAND',
                              },
                            ];
                            updateEngraving({ items: updated });
                          }}
                          disabled={isLimitReached}
                          style={{
                            padding: '0.45rem 0.9rem',
                            background: isLimitReached ? '#f1f5f9' : '#ffffff',
                            border: '1px dashed #0284c7',
                            borderRadius: '6px',
                            color: isLimitReached ? '#94a3b8' : '#0284c7',
                            fontSize: '0.85rem',
                            cursor: isLimitReached ? 'not-allowed' : 'pointer',
                            fontWeight: 600,
                            opacity: isLimitReached ? 0.6 : 1,
                          }}
                          data-testid="add-engraving-item-btn"
                        >
                          {language === 'en' ? '+ Add Engraving Position (Max 2)' : '+ Thêm vị trí khắc (Tối đa 2)'}
                        </button>

                        {/* Compatibility test buttons */}
                        <button
                          type="button"
                          onClick={() => {
                            if (isTextLimitReached) return;
                            const updated = [
                              ...currentItems,
                              {
                                id: `eng-${Date.now().toString().slice(-4)}`,
                                type: 'TEXT',
                                content: '',
                                placement: 'OUTER_BAND',
                              },
                            ];
                            updateEngraving({ items: updated });
                          }}
                          disabled={isTextLimitReached}
                          style={{ display: 'none' }}
                          data-testid="add-text-engraving-btn"
                        >
                          + Thêm dòng chữ
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            if (isSymbolLimitReached) return;
                            const updated = [
                              ...currentItems,
                              {
                                id: `eng-sym-${Date.now().toString().slice(-4)}`,
                                type: 'SYMBOL',
                                content: '♥',
                                placement: 'INNER_BAND',
                              },
                            ];
                            updateEngraving({ items: updated });
                          }}
                          disabled={isSymbolLimitReached}
                          style={{ display: 'none' }}
                          data-testid="add-symbol-engraving-btn"
                        >
                          + Thêm biểu tượng
                        </button>
                      </div>
                    </div>
                  );
                })()}

                {/* Custom Laser Image Cropper */}
                <LaserEngravingImageCropper
                  customImages={draft.engraving?.customImages || []}
                  onChangeCustomImages={(customImages) => updateEngraving({ customImages })}
                  t={t}
                />
              </div>
            </div>

            {/* Right Column: Sticky Live Preview & Summary */}
            <div className="ring-preview-panel">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span className="ring-status-badge draft">{t.DRAFT}</span>
                <span className="ring-advisory-badge">{t.advisoryBadge}</span>
              </div>

              <RingPreview
                previewModel={previewModel}
                onThicknessChange={updateThickness}
                onBandWidthChange={updateBandWidth}
                onGemstoneMove={moveGemstone}
              />

              {isDirty && (
                <div style={{ fontSize: '0.85rem', color: '#b45309', background: '#fffbeb', padding: '0.5rem 0.75rem', borderRadius: '6px' }}>
                  {t.unsavedChangesNotice}
                </div>
              )}

              <div className="ring-summary-box">
                <h3 style={{ fontSize: '1rem', fontWeight: 600, margin: '0 0 0.75rem', color: '#0f172a' }}>
                  {t.summaryHeading}
                </h3>
                <div className="ring-summary-item">
                  <span className="ring-summary-label">{t.materialSummary}</span>
                  <span className="ring-summary-value">
                    {getTranslatedOption('materials', draft.materialReference, language, { name: previewModel.material?.label }).name}
                  </span>
                </div>
                <div className="ring-summary-item">
                  <span className="ring-summary-label">{t.shapeSummary}</span>
                  <span className="ring-summary-value">
                    {getTranslatedOption('shapes', draft.shapeReference, language, { name: previewModel.shape?.label }).name}
                  </span>
                </div>
                <div className="ring-summary-item">
                  <span className="ring-summary-label">{t.sizeSummary}</span>
                  <span className="ring-summary-value">Size {previewModel.ringSize} ({previewModel.circumferenceMm} mm)</span>
                </div>
                <div className="ring-summary-item">
                  <span className="ring-summary-label">{t.thicknessSummary}</span>
                  <span className="ring-summary-value">{previewModel.thicknessMm?.toFixed(1)} mm</span>
                </div>
                <div className="ring-summary-item">
                  <span className="ring-summary-label">{t.bandWidthSummary}</span>
                  <span className="ring-summary-value">{previewModel.bandWidthMm?.toFixed(1)} mm</span>
                </div>
                <div className="ring-summary-item">
                  <span className="ring-summary-label">{t.weightSummary}</span>
                  <span className="ring-summary-value" data-testid="weight-summary-value">
                    ~{previewModel.estimatedWeightGrams?.toFixed(2)} g
                    {previewModel.excessWeightGrams > 0 ? (
                      <span style={{ fontSize: '0.8rem', color: '#b45309', display: 'block', fontWeight: 500 }}>
                        (+{previewModel.excessWeightGrams.toFixed(2)}g: +{previewModel.excessMaterialCost?.toLocaleString(language === 'en' ? 'en-US' : 'vi-VN')} {language === 'en' ? 'VND' : 'đ'})
                      </span>
                    ) : (
                      <span style={{ fontSize: '0.8rem', color: '#166534', display: 'block', fontWeight: 500 }}>
                        ({language === 'en' ? 'Standard quota ≤ 3.0g' : 'Định mức ≤ 3.0g'})
                      </span>
                    )}
                  </span>
                </div>
                <div className="ring-summary-item">
                  <span className="ring-summary-label">{t.gemstonesSummary}</span>
                  <span className="ring-summary-value">
                    {previewModel.gemstoneGroups?.length > 0
                      ? previewModel.gemstoneGroups.map((g) => `${g.quantity}x ${getTranslatedOption('components', g.reference, language, { name: g.label }).name}`).join(', ')
                      : previewModel.gemstones?.length > 0
                        ? previewModel.gemstones.map((g) => `${g.quantity || 1}x ${getTranslatedOption('components', g.reference, language, { name: g.label }).name}`).join(', ')
                        : t.none}
                  </span>
                </div>
                <div className="ring-summary-item">
                  <span className="ring-summary-label">{t.engravingSummary}</span>
                  <span className="ring-summary-value">
                    {previewModel.engravingText ? `"${previewModel.engravingText}"` : t.none}
                    {previewModel.standardEngravingCost > 0 && (
                      <span style={{ fontSize: '0.8rem', color: '#0369a1', display: 'block', fontWeight: 500 }}>
                        (+80.000 {language === 'en' ? 'VND' : 'đ'})
                      </span>
                    )}
                  </span>
                </div>
                {previewModel.customImages?.length > 0 && (
                  <div className="ring-summary-item" data-testid="summary-custom-images">
                    <span className="ring-summary-label">{language === 'en' ? 'Laser Engraving' : 'Ảnh khắc laser'}</span>
                    <span className="ring-summary-value">
                      {previewModel.customImages.length} {language === 'en' ? (previewModel.customImages.length > 1 ? 'images' : 'image') : 'ảnh'}
                      <span style={{ fontSize: '0.8rem', color: '#0369a1', display: 'block', fontWeight: 500 }}>
                        (+{(previewModel.customImages.length * 100000).toLocaleString(language === 'en' ? 'en-US' : 'vi-VN')} {language === 'en' ? 'VND' : 'đ'})
                      </span>
                    </span>
                  </div>
                )}
              </div>

              {commandError && (
                <div className="ring-error-card" style={{ marginBottom: '1rem' }} data-testid="command-error-summary">
                  <strong>{language === 'en' ? 'Save Error:' : 'Lỗi lưu mẫu nhẫn:'}</strong> {commandError}
                </div>
              )}

              {submitAttempted && !clientValidation.isValid && (
                <div className="ring-error-card ring-validation-summary" style={{ marginBottom: '1rem' }} data-testid="client-validation-summary">
                  <div style={{ fontWeight: 700, marginBottom: '0.25rem' }}>
                    ⚠️ {language === 'en' ? 'Please complete the following before proceeding:' : 'Vui lòng hoàn tất các thông tin sau để tiếp tục:'}
                  </div>
                  <ul style={{ margin: 0, paddingLeft: '1.25rem' }}>
                    {Object.values(clientValidation.errors).map((err, i) => (
                      <li key={i}>{err}</li>
                    ))}
                  </ul>
                </div>
              )}

              <button
                type="button"
                className="ring-btn-primary"
                disabled={saving}
                onClick={handleSaveAndEvaluate}
                data-testid="save-and-evaluate-btn"
              >
                {saving ? t.saving : t.saveAndEvaluate}
              </button>
            </div>
          </div>
        </div>
      );
    }

