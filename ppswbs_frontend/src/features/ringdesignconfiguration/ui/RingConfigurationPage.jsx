import React, { useEffect, useState } from 'react';
import { useParams, useSearchParams, useNavigate } from 'react-router';
import { useWorkshopDesignOptions } from '../application/useWorkshopDesignOptions';
import { useWorkshopDesignDraft } from '../application/useWorkshopDesignDraft';
import { useWorkshopDesignCommand } from '../application/useWorkshopDesignCommand';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { setStoredGuestSession } from '../api/workshopRingDesignApi';
import RingPreview from './RingPreview';
import DesignControlField from './DesignControlField';
import { messages } from '../messages';
import '../ring-design.css';

export default function RingConfigurationPage() {
  const { packageReference } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const isAuthenticated = Boolean(user);

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
    toggleComponent,
    updateComponentQuantity,
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
              helperText={t.ringSizeHelper}
              required
              errorText={clientValidation.errors.ringSize}
            >
              <div className="ring-pills-row" data-testid="ring-sizes-row">
                {(options?.sizes || ['6', '7', '8', '9', '10', '11', '12', '13', '14', '15']).map((size) => {
                  const isSelected = draft.ringSize === String(size);
                  return (
                    <button
                      type="button"
                      key={size}
                      className={`ring-size-pill ${isSelected ? 'selected' : ''}`}
                      onClick={() => updateRingSize(size)}
                      data-testid={`size-pill-${size}`}
                    >
                      Size {size}
                    </button>
                  );
                })}
              </div>
            </DesignControlField>
          </div>

          {/* Section 3: Gemstones */}
          <div className="ring-section-card">
            <h2 className="ring-section-title">{t.gemstoneSection}</h2>
            <p className="ring-section-desc">{t.gemstoneSectionDesc}</p>
            <div className="ring-cards-grid">
              {(options?.components || []).map((gem) => {
                const selectedItem = draft.components.find((c) => c.reference === gem.reference);
                const isSelected = Boolean(selectedItem);
                return (
                  <div
                    key={gem.reference}
                    className={`ring-option-card ${isSelected ? 'selected' : ''}`}
                    onClick={() => toggleComponent(gem.reference, gem.maxQuantity)}
                    role="button"
                    tabIndex={0}
                    aria-pressed={isSelected}
                    data-testid={`gemstone-card-${gem.reference}`}
                  >
                    <div>
                      <div className="ring-option-name">{gem.name}</div>
                      <div className="ring-option-desc">{gem.description}</div>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div className="ring-option-price">
                        {Number(gem.unitPrice).toLocaleString('vi-VN')} đ
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

          {/* Section 5: Engraving */}
          <div className="ring-section-card">
            <h2 className="ring-section-title">{t.engravingSection}</h2>
            <p className="ring-section-desc">{t.engravingSectionDesc}</p>

            <div style={{ marginBottom: '1rem' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer', fontWeight: 500 }}>
                <input
                  type="checkbox"
                  checked={Boolean(draft.engraving?.enabled)}
                  onChange={(e) => updateEngraving({ enabled: e.target.checked })}
                  data-testid="engraving-toggle"
                />
                <span>{t.engravingToggleLabel}</span>
              </label>
            </div>

            {draft.engraving?.enabled && (
              <div>
                <DesignControlField
                  label={t.engravingTextInputLabel}
                  errorText={clientValidation.errors.engravingText}
                >
                  <input
                    type="text"
                    maxLength={50}
                    placeholder={t.engravingPlaceholder}
                    value={draft.engraving.text || ''}
                    onChange={(e) => updateEngraving({ text: e.target.value })}
                    style={{
                      width: '100%',
                      padding: '0.65rem 0.85rem',
                      borderRadius: '8px',
                      border: '1px solid #cbd5e1',
                      fontSize: '0.95rem',
                    }}
                    data-testid="engraving-input"
                  />
                </DesignControlField>

                <DesignControlField label={t.engravingPlacementLabel}>
                  <div className="ring-pills-row">
                    <button
                      type="button"
                      className={`ring-size-pill ${draft.engraving.placement === 'INNER_BAND' ? 'selected' : ''}`}
                      onClick={() => updateEngraving({ placement: 'INNER_BAND' })}
                    >
                      {t.innerBand}
                    </button>
                    <button
                      type="button"
                      className={`ring-size-pill ${draft.engraving.placement === 'OUTER_BAND' ? 'selected' : ''}`}
                      onClick={() => updateEngraving({ placement: 'OUTER_BAND' })}
                    >
                      {t.outerBand}
                    </button>
                  </div>
                </DesignControlField>
              </div>
            )}
          </div>
        </div>

        {/* Right Column: Sticky Live Preview & Summary */}
        <div className="ring-preview-panel">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span className="ring-status-badge draft">{t.DRAFT}</span>
            <span className="ring-advisory-badge">{t.advisoryBadge}</span>
          </div>

          <RingPreview previewModel={previewModel} />

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
              <span className="ring-summary-value">Size {previewModel.ringSize}</span>
            </div>
            <div className="ring-summary-item">
              <span className="ring-summary-label">{t.gemstonesSummary}</span>
              <span className="ring-summary-value">
                {previewModel.gemstones.length > 0
                  ? previewModel.gemstones.map((g) => `${g.quantity}x ${g.label}`).join(', ')
                  : t.none}
              </span>
            </div>
            <div className="ring-summary-item">
              <span className="ring-summary-label">{t.engravingSummary}</span>
              <span className="ring-summary-value">
                {previewModel.engravingText ? `"${previewModel.engravingText}"` : t.none}
              </span>
            </div>
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

