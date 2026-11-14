import React, { useEffect, useState } from 'react';
import { useParams, useSearchParams, useNavigate } from 'react-router';
import { useWorkshopDesignOptions } from '../application/useWorkshopDesignOptions';
import { useWorkshopDesignDraft } from '../application/useWorkshopDesignDraft';
import { useWorkshopDesignCommand } from '../application/useWorkshopDesignCommand';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { setStoredGuestSession } from '../api/workshopRingDesignApi';
import RingPreview from './RingPreview';
import DesignControlField from './DesignControlField';
import LaserEngravingImageCropper from './LaserEngravingImageCropper';
import { getRingCircumferenceMm, getRingDiameterMm } from '../domain/designPreview';
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

  const lang = 'vi';
  const t = messages[lang];

  const handleSaveAndEvaluate = async () => {
    if (!clientValidation.isValid) return;

    try {
      const payload = getPayloadForSave();
      // First creation of configured design draft
      const createRes = await executeCreate({
        packageReference,
        sourceModelReference: mode === 'MODEL' ? modelRef : null,
        startMode: mode === 'MODEL' ? 'PUBLISHED_MODEL' : 'BLANK',
      });

      const designId = createRes.designId;
      // If server returned guest session in response or header, save it
      if (createRes.guestSession) {
        setStoredGuestSession(createRes.guestSession);
      }

      // Then save the customized parameters as version 2
      const saveRes = await executeSave(designId, payload, createRes.designVersion || 1);

      // Navigate to summary and evaluation view
      navigate(`/workshops/${encodeURIComponent(packageReference)}/design/${encodeURIComponent(designId)}`);
    } catch {
      // Error handled by command hook
    }
  };

  if (optionsLoading) {
    return (
      <div className="ring-design-container" style={{ textAlign: 'center', padding: '4rem' }}>
        <p>Đang tải cấu hình tùy chọn gói workshop...</p>
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
          {optionsError.message || 'Không thể tải tùy chọn gói workshop.'}
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
                      <div className="ring-option-name">{mat.name}</div>
                      <div className="ring-option-desc">{mat.description}</div>
                    </div>
                    {mat.unitPrice && (
                      <div className="ring-option-price">
                        {Number(mat.unitPrice).toLocaleString('vi-VN')} {mat.currency || 'đ'}
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

            <DesignControlField label="Dáng đai nhẫn" required errorText={clientValidation.errors.shapeReference}>
              <div className="ring-cards-grid" style={{ marginBottom: '1.25rem' }}>
                {(options?.shapes || []).map((shp) => {
                  const isSelected = draft.shapeReference === shp.reference;
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
                        <div className="ring-option-name">{shp.name}</div>
                        <div className="ring-option-desc">{shp.description}</div>
                      </div>
                      {shp.priceAdjustment && Number(shp.priceAdjustment) > 0 && (
                        <div className="ring-option-price">
                          +{Number(shp.priceAdjustment).toLocaleString('vi-VN')} đ
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </DesignControlField>

            <DesignControlField
              label={t.ringSizeLabel}
              helperText={`${t.ringSizeHelper} Đã chọn: Size ${draft.ringSize} — Chu vi: ${getRingCircumferenceMm(draft.ringSize)} mm (Đường kính trong: ~${getRingDiameterMm(draft.ringSize)} mm)`}
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
                      title={`Size ${size} - Chu vi: ${circ} mm`}
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
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '0.25rem' }}>
              <h2 className="ring-section-title" style={{ margin: 0 }}>{t.gemstoneSection}</h2>
              {draft.components && draft.components.length > 0 && (
                <span className="ring-gemstone-total-badge" data-testid="gemstone-total-badge">
                  Đã chọn: {draft.components.reduce((sum, c) => sum + (Number(c.quantity) || 1), 0)} viên đá
                </span>
              )}
            </div>
            <p className="ring-section-desc">{t.gemstoneSectionDesc}</p>

            <div className="ring-gemstone-instruction" data-testid="gemstone-drag-instruction">
              ✨ <strong>Tùy chỉnh số lượng & vị trí:</strong> Chọn số lượng từng loại đá, sau đó <strong>nhấn giữ & kéo thả viên đá</strong> trực tiếp trên cửa sổ <em>Mặt chính diện (Top View)</em> để đặt vị trí bất kỳ quanh đường tròn nhẫn!
            </div>

            <div className="ring-cards-grid">
              {(options?.components || []).map((gem) => {
                const selectedItem = draft.components.find((c) => c.reference === gem.reference);
                const isSelected = Boolean(selectedItem);
                const currentQty = selectedItem ? Number(selectedItem.quantity) || 1 : 0;
                const maxQty = gem.maxQuantity || 10;

                return (
                  <div
                    key={gem.reference}
                    className={`ring-option-card ring-gemstone-card ${isSelected ? 'selected' : ''}`}
                    onClick={() => {
                      if (!isSelected) {
                        setComponentQuantity(gem.reference, 1, maxQty);
                      }
                    }}
                    role={isSelected ? 'group' : 'button'}
                    tabIndex={isSelected ? -1 : 0}
                    aria-label={gem.name}
                    aria-pressed={isSelected ? true : undefined}
                    data-testid={`gemstone-card-${gem.reference}`}
                  >
                    <div>
                      <div className="ring-option-name">{gem.name}</div>
                      <div className="ring-option-desc">{gem.description}</div>
                    </div>

                    <div className="ring-gemstone-footer">
                      <div className="ring-option-price">
                        {Number(gem.unitPrice).toLocaleString('vi-VN')} đ / viên
                        <span className="ring-gem-max-label"> (Tối đa {maxQty})</span>
                      </div>

                      {!isSelected ? (
                        <button
                          type="button"
                          className="ring-gem-btn-add"
                          onClick={(e) => {
                            e.stopPropagation();
                            setComponentQuantity(gem.reference, 1, maxQty);
                          }}
                          data-testid={`add-gemstone-${gem.reference}`}
                        >
                          + Thêm đá
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
                            aria-label={`Giảm số lượng ${gem.name}`}
                            data-testid={`decrease-gem-${gem.reference}`}
                          >
                            −
                          </button>
                          <span className="ring-stepper-value" data-testid={`qty-gem-${gem.reference}`}>
                            {currentQty} viên
                          </span>
                          <button
                            type="button"
                            className="ring-stepper-btn ring-stepper-plus"
                            onClick={() => setComponentQuantity(gem.reference, currentQty + 1, maxQty)}
                            disabled={currentQty >= maxQty}
                            aria-label={`Tăng số lượng ${gem.name}`}
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
          </div>

          {/* Section 4: Attachments */}
          <div className="ring-section-card">
            <h2 className="ring-section-title">{t.attachmentSection}</h2>
            <p className="ring-section-desc">{t.attachmentSectionDesc}</p>
            <div className="ring-cards-grid">
              {(options?.attachments || []).map((att) => {
                const isSelected = draft.attachments.some((a) => a.reference === att.reference);
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
                      <div className="ring-option-name">{att.name}</div>
                      <div className="ring-option-desc">{att.description}</div>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div className="ring-option-price">
                        +{Number(att.unitPrice).toLocaleString('vi-VN')} đ
                      </div>
                      {isSelected && (
                        <span style={{ fontSize: '0.75rem', fontWeight: 600, color: '#166534' }}>✓ Đã chọn</span>
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

            {draft.engraving?.enabled && (
              <div style={{ marginLeft: '1.5rem', marginBottom: '1.5rem' }}>
                {/* Standard Engraving Items */}
                {((draft.engraving?.items && draft.engraving.items.length > 0)
                  ? draft.engraving.items
                  : [
                      {
                        id: 'eng-1',
                        type: 'TEXT',
                        content: draft.engraving?.text || '',
                        placement: draft.engraving?.placement || 'INNER_BAND',
                      },
                    ]
                ).map((item, idx) => {
                  const updateItem = (patch) => {
                    const currentItems =
                      draft.engraving?.items && draft.engraving.items.length > 0
                        ? [...draft.engraving.items]
                        : [
                            {
                              id: 'eng-1',
                              type: 'TEXT',
                              content: draft.engraving?.text || '',
                              placement: draft.engraving?.placement || 'INNER_BAND',
                            },
                          ];
                    currentItems[idx] = { ...currentItems[idx], ...patch };
                    const firstText = currentItems[0]?.content || '';
                    const firstPlacement = currentItems[0]?.placement || 'INNER_BAND';
                    updateEngraving({
                      items: currentItems,
                      text: firstText,
                      placement: firstPlacement,
                    });
                  };

                  const removeItem = () => {
                    const currentItems = draft.engraving?.items?.filter((_, i) => i !== idx) || [];
                    const firstText = currentItems[0]?.content || '';
                    const firstPlacement = currentItems[0]?.placement || 'INNER_BAND';
                    updateEngraving({
                      items: currentItems,
                      text: firstText,
                      placement: firstPlacement,
                    });
                  };

                  return (
                    <div
                      key={item.id || idx}
                      style={{
                        padding: '0.75rem',
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
                          marginBottom: '0.35rem',
                        }}
                      >
                        <span style={{ fontSize: '0.85rem', fontWeight: 600, color: '#334155' }}>
                          Vị trí #{idx + 1}
                        </span>
                        {(draft.engraving?.items?.length || 0) > 1 && (
                          <button
                            type="button"
                            onClick={removeItem}
                            style={{
                              background: 'none',
                              border: 'none',
                              color: '#ef4444',
                              fontSize: '0.8rem',
                              cursor: 'pointer',
                            }}
                            data-testid={`remove-engraving-item-${idx}`}
                          >
                            {t.removeEngravingItem || 'Xóa'}
                          </button>
                        )}
                      </div>

                      <DesignControlField
                        label={t.engravingTextInputLabel}
                        errorText={idx === 0 ? clientValidation.errors.engravingText : undefined}
                      >
                        <input
                          type="text"
                          maxLength={50}
                          placeholder={t.engravingPlaceholder}
                          value={item.content || ''}
                          onChange={(e) => updateItem({ content: e.target.value })}
                          style={{
                            width: '100%',
                            padding: '0.6rem 0.8rem',
                            borderRadius: '6px',
                            border: '1px solid #cbd5e1',
                            fontSize: '0.9rem',
                          }}
                          data-testid={idx === 0 ? 'engraving-input' : `engraving-input-${idx}`}
                        />
                      </DesignControlField>

                      {/* Quick Symbol Buttons */}
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: '0.4rem',
                          marginTop: '0.35rem',
                          marginBottom: '0.65rem',
                        }}
                      >
                        <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                          {t.quickSymbols || 'Ký hiệu nhanh:'}
                        </span>
                        {['♥', '∞', '★', '✿', '💍'].map((sym) => (
                          <button
                            key={sym}
                            type="button"
                            onClick={() => updateItem({ content: `${item.content || ''}${sym}` })}
                            style={{
                              border: '1px solid #cbd5e1',
                              background: '#ffffff',
                              borderRadius: '4px',
                              padding: '0.15rem 0.4rem',
                              fontSize: '0.85rem',
                              cursor: 'pointer',
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
                            className={`ring-size-pill ${item.placement === 'INNER_BAND' ? 'selected' : ''}`}
                            onClick={() => updateItem({ placement: 'INNER_BAND' })}
                            data-testid={idx === 0 ? 'placement-inner' : `placement-inner-${idx}`}
                          >
                            {t.innerBand}
                          </button>
                          <button
                            type="button"
                            className={`ring-size-pill ${item.placement === 'OUTER_BAND' ? 'selected' : ''}`}
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

                <button
                  type="button"
                  onClick={() => {
                    const currentItems =
                      draft.engraving?.items && draft.engraving.items.length > 0
                        ? [...draft.engraving.items]
                        : [
                            {
                              id: 'eng-1',
                              type: 'TEXT',
                              content: draft.engraving?.text || '',
                              placement: draft.engraving?.placement || 'INNER_BAND',
                            },
                          ];
                    currentItems.push({
                      id: `eng-${Date.now().toString().slice(-4)}`,
                      type: 'TEXT',
                      content: '',
                      placement: 'OUTER_BAND',
                    });
                    updateEngraving({ items: currentItems });
                  }}
                  style={{
                    padding: '0.4rem 0.8rem',
                    background: '#f1f5f9',
                    border: '1px dashed #94a3b8',
                    borderRadius: '6px',
                    color: '#334155',
                    fontSize: '0.85rem',
                    cursor: 'pointer',
                    fontWeight: 500,
                  }}
                  data-testid="add-engraving-item-btn"
                >
                  {t.addEngravingItem || '+ Thêm vị trí/nội dung khắc'}
                </button>
              </div>
            )}

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
              <span className="ring-summary-value">{previewModel.material?.label}</span>
            </div>
            <div className="ring-summary-item">
              <span className="ring-summary-label">{t.shapeSummary}</span>
              <span className="ring-summary-value">{previewModel.shape?.label}</span>
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
                    (+{previewModel.excessWeightGrams.toFixed(2)}g: +{previewModel.excessMaterialCost?.toLocaleString('vi-VN')} đ)
                  </span>
                ) : (
                  <span style={{ fontSize: '0.8rem', color: '#166534', display: 'block', fontWeight: 500 }}>
                    (Định mức ≤ 3.0g)
                  </span>
                )}
              </span>
            </div>
            <div className="ring-summary-item">
              <span className="ring-summary-label">{t.gemstonesSummary}</span>
              <span className="ring-summary-value">
                {previewModel.gemstoneGroups?.length > 0
                  ? previewModel.gemstoneGroups.map((g) => `${g.quantity}x ${g.label}`).join(', ')
                  : previewModel.gemstones?.length > 0
                    ? previewModel.gemstones.map((g) => `${g.quantity || 1}x ${g.label}`).join(', ')
                    : t.none}
              </span>
            </div>
            <div className="ring-summary-item">
              <span className="ring-summary-label">{t.engravingSummary}</span>
              <span className="ring-summary-value">
                {previewModel.engravingText ? `"${previewModel.engravingText}"` : t.none}
                {previewModel.standardEngravingCost > 0 && (
                  <span style={{ fontSize: '0.8rem', color: '#0369a1', display: 'block', fontWeight: 500 }}>
                    (+80.000 đ)
                  </span>
                )}
              </span>
            </div>
            {previewModel.customImages?.length > 0 && (
              <div className="ring-summary-item" data-testid="summary-custom-images">
                <span className="ring-summary-label">Ảnh khắc laser</span>
                <span className="ring-summary-value">
                  {previewModel.customImages.length} ảnh
                  <span style={{ fontSize: '0.8rem', color: '#0369a1', display: 'block', fontWeight: 500 }}>
                    (+{(previewModel.customImages.length * 100000).toLocaleString('vi-VN')} đ)
                  </span>
                </span>
              </div>
            )}
          </div>

          <button
            type="button"
            className="ring-btn-primary"
            disabled={!clientValidation.isValid || saving}
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

