import React, { useMemo, useState } from 'react';
import { useFormat, useT } from '../../../shared/i18n/I18nProvider';
import { useRingDifficultyPolicy } from '../application/useRingDifficultyPolicy';
import { OPTION_KINDS } from '../domain/policyValidation';
import { adminRingDifficultyMessages } from '../messages';
import '../admin-ring-difficulty.css';

function buildOptionNameLookup(catalogueOptions) {
  const map = new Map();
  if (!catalogueOptions) return map;
  for (const m of catalogueOptions.materials || []) map.set(`MATERIAL:${m.reference}`, m);
  for (const s of catalogueOptions.shapes || []) map.set(`SHAPE:${s.reference}`, s);
  for (const g of catalogueOptions.components || []) map.set(`GEMSTONE:${g.reference}`, g);
  for (const a of catalogueOptions.attachments || []) map.set(`ATTACHMENT:${a.reference}`, a);
  return map;
}

export default function RingDifficultyPolicyPage() {
  const t = useT(adminRingDifficultyMessages);
  const fmt = useFormat();

  const {
    activePolicy,
    versions,
    catalogueOptions,
    candidate,
    samples,
    previewResult,
    proofValid,
    changedFieldCount,
    localValidation,
    loading,
    loadError,
    previewing,
    publishing,
    actionError,
    publishSuccess,
    reload,
    updateOptionScore,
    updateCoefficient,
    updateThreshold,
    addSample,
    removeSample,
    resetCandidate,
    restoreVersion,
    runPreview,
    runPublish,
  } = useRingDifficultyPolicy();

  const [groupQueries, setGroupQueries] = useState({});
  const [modelFilter, setModelFilter] = useState('ALL');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [sampleForm, setSampleForm] = useState({
    sampleName: '',
    materialReference: 'MAT-SILVER-925',
    shapeReference: 'SHP-CLASSIC',
    engravingEnabled: false,
    customImageCount: 0,
  });

  const optionLookup = useMemo(() => buildOptionNameLookup(catalogueOptions), [catalogueOptions]);

  const activeScoreMap = useMemo(() => {
    const map = new Map();
    for (const o of activePolicy?.optionScores || []) {
      map.set(`${o.kind}:${o.reference}`, o.score);
    }
    return map;
  }, [activePolicy]);

  // Merge active policy optionScores with any newly active unscored options from catalogueOptions
  const mergedOptionsByKind = useMemo(() => {
    const groups = { MATERIAL: [], SHAPE: [], GEMSTONE: [], ATTACHMENT: [] };
    const seen = new Set();

    for (const item of candidate?.optionScores || []) {
      if (!groups[item.kind]) continue;
      seen.add(`${item.kind}:${item.reference}`);
      const meta = optionLookup.get(`${item.kind}:${item.reference}`);
      const isPending = meta?.unavailableReason === 'DIFFICULTY_SCORE_PENDING' || !String(item.score ?? '').trim();
      groups[item.kind].push({
        kind: item.kind,
        reference: item.reference,
        name: meta?.name || item.reference,
        currentScore: activeScoreMap.get(`${item.kind}:${item.reference}`),
        candidateScore: item.score,
        isPending,
      });
    }

    for (const [key, meta] of optionLookup.entries()) {
      if (seen.has(key)) continue;
      const [kind, reference] = key.split(':');
      if (!groups[kind]) continue;
      groups[kind].push({
        kind,
        reference,
        name: meta.name || reference,
        currentScore: undefined,
        candidateScore: '',
        isPending: true,
      });
    }

    for (const kind of OPTION_KINDS) {
      groups[kind].sort((a, b) => {
        if (a.isPending !== b.isPending) return a.isPending ? -1 : 1;
        return a.reference.localeCompare(b.reference);
      });
    }

    return groups;
  }, [activeScoreMap, candidate?.optionScores, optionLookup]);

  const modelImpacts = previewResult?.modelImpacts || [];
  const affectedModels = useMemo(
    () =>
      modelImpacts.filter(
        (m) =>
          !m.eligible ||
          m.before?.difficultyScore !== m.after?.difficultyScore ||
          m.before?.difficultyLevel !== m.after?.difficultyLevel ||
          m.before?.recommendationCode !== m.after?.recommendationCode
      ),
    [modelImpacts]
  );
  const blockedModels = useMemo(() => modelImpacts.filter((m) => !m.eligible), [modelImpacts]);

  const filteredModels = useMemo(() => {
    if (modelFilter === 'BLOCKED') return blockedModels;
    if (modelFilter === 'AFFECTED') return affectedModels;
    return modelImpacts;
  }, [affectedModels, blockedModels, modelFilter, modelImpacts]);

  if (loading) {
    return (
      <div className="ard-page" data-testid="ring-difficulty-loading">
        <p role="status" aria-busy="true">{t('loading')}</p>
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="ard-page" data-testid="ring-difficulty-load-error">
        <div className="ard-alert ard-alert-error" role="alert">
          <span>{loadError.message || t('loadError')}</span>
          <button type="button" className="ard-btn ard-btn-secondary" onClick={() => reload()}>
            {t('retry')}
          </button>
        </div>
      </div>
    );
  }

  if (!activePolicy || !candidate) {
    return (
      <div className="ard-page" data-testid="ring-difficulty-empty-error">
        <div className="ard-alert ard-alert-error" role="alert">
          <span>{t('emptyPolicyError')}</span>
          <button type="button" className="ard-btn ard-btn-secondary" onClick={() => reload()}>
            {t('retry')}
          </button>
        </div>
      </div>
    );
  }

  const handlePreviewClick = async () => {
    await runPreview();
  };

  const handleConfirmPublish = async () => {
    const res = await runPublish();
    if (res) {
      setConfirmOpen(false);
    }
  };

  const handleAddSample = (e) => {
    e.preventDefault();
    if (!sampleForm.sampleName.trim()) return;
    addSample({
      sampleName: sampleForm.sampleName.trim(),
      materialReference: sampleForm.materialReference,
      shapeReference: sampleForm.shapeReference,
      engravingEnabled: Boolean(sampleForm.engravingEnabled),
      customImageCount: Number(sampleForm.customImageCount || 0),
    });
    setSampleForm((prev) => ({ ...prev, sampleName: '' }));
  };

  const formatOutcome = (outcome) => {
    if (!outcome) return '—';
    const lvl = t(`level.${outcome.difficultyLevel}`) || outcome.difficultyLevel;
    const rec = t(`rec.${outcome.recommendationCode}`) || outcome.recommendationCode;
    return `${outcome.difficultyScore} · ${lvl} · ${rec}`;
  };

  const standardComparisons = (previewResult?.comparisons || []).filter((c) => c.source === 'STANDARD');
  const adminComparisons = (previewResult?.comparisons || []).filter((c) => c.source === 'ADMIN');

  return (
    <div className="ard-page" data-testid="ring-difficulty-page">
      <header className="ard-header">
        <h1 className="ard-title">{t('pageTitle')}</h1>
        <p className="ard-subtitle">{t('pageSubtitle')}</p>
      </header>

      {/* Sticky summary bar */}
      <section className="ard-sticky-summary" aria-label={t('summary.effectiveVersion')} data-testid="policy-sticky-summary">
        <div className="ard-summary-meta">
          <span>
            <strong>{t('summary.effectiveVersion')}:</strong>{' '}
            <span data-testid="effective-rules-version">{activePolicy.rulesVersion}</span>
          </span>
          <span>
            <strong>{t('summary.publishedAt')}:</strong> {fmt.dateTime(activePolicy.publishedAt)}
          </span>
          <span>
            <strong>{t('summary.publishedBy')}:</strong> {activePolicy.publishedBy}
          </span>
          {changedFieldCount > 0 ? (
            <span className="ard-badge ard-badge-warn" data-testid="unsaved-badge">
              {t('summary.unsaved')} ({changedFieldCount})
            </span>
          ) : (
            <span className="ard-badge ard-badge-neutral">{t('summary.clean')}</span>
          )}
          {proofValid ? (
            <span className="ard-badge ard-badge-ok" data-testid="proof-valid-badge">
              {t('summary.proofValid', { time: fmt.dateTime(previewResult.previewExpiresAt) })}
            </span>
          ) : previewResult && !previewResult.publicationEligible ? (
            <span className="ard-badge ard-badge-danger" data-testid="proof-blocked-badge">
              {t('summary.proofBlocked')}
            </span>
          ) : (
            <span className="ard-badge ard-badge-info" data-testid="proof-missing-badge">
              {t('summary.proofMissing')}
            </span>
          )}
        </div>

        <div className="ard-actions-bar">
          <button
            type="button"
            className="ard-btn ard-btn-secondary"
            onClick={resetCandidate}
            disabled={changedFieldCount === 0 || previewing || publishing}
            data-testid="reset-candidate-btn"
          >
            {t('actions.reset')}
          </button>
          <button
            type="button"
            className="ard-btn ard-btn-primary"
            onClick={handlePreviewClick}
            disabled={!localValidation.valid || previewing || publishing}
            data-testid="preview-policy-btn"
          >
            {previewing ? t('actions.previewing') : t('actions.preview')}
          </button>
          <button
            type="button"
            className="ard-btn ard-btn-publish"
            onClick={() => setConfirmOpen(true)}
            disabled={!proofValid || !localValidation.valid || previewing || publishing}
            data-testid="open-publish-confirm-btn"
          >
            {t('actions.publish')}
          </button>
        </div>
      </section>

      {publishSuccess && (
        <div className="ard-alert ard-alert-success" role="status" data-testid="publish-success-banner">
          <span>{t('status.publishSuccess', { version: publishSuccess.rulesVersion })}</span>
        </div>
      )}

      {actionError && (
        <div className="ard-alert ard-alert-error" role="alert" data-testid="policy-action-error">
          <div>
            <strong>{actionError.code}:</strong>{' '}
            {actionError.status === 409
              ? actionError.code === 'PREVIEW_EXPIRED' || actionError.code === 'PREVIEW_MISMATCH'
                ? t('error.proofInvalid')
                : t('error.conflict')
              : actionError.retryable
                ? t('error.retryable')
                : actionError.message}
          </div>
          {actionError.status === 409 && (
            <button
              type="button"
              className="ard-btn ard-btn-secondary"
              onClick={() => reload({ preserveCandidate: true })}
              data-testid="reload-latest-btn"
            >
              {t('reloadLatest')}
            </button>
          )}
        </div>
      )}

      {/* Option Score Groups */}
      {OPTION_KINDS.map((kind) => {
        const query = (groupQueries[kind] || '').trim().toLowerCase();
        const rows = mergedOptionsByKind[kind].filter(
          (row) =>
            !query ||
            row.reference.toLowerCase().includes(query) ||
            row.name.toLowerCase().includes(query)
        );
        return (
          <section className="ard-card" key={kind} data-testid={`group-${kind}`}>
            <div className="ard-card-header">
              <h2 className="ard-card-title">{t(`groups.${kind}`)}</h2>
              <input
                type="search"
                className="ard-search-input"
                placeholder={t('table.searchPlaceholder')}
                aria-label={`${t(`groups.${kind}`)} ${t('table.searchPlaceholder')}`}
                value={groupQueries[kind] || ''}
                onChange={(e) => setGroupQueries((prev) => ({ ...prev, [kind]: e.target.value }))}
              />
            </div>

            <div className="ard-table-wrap">
              <table className="ard-table">
                <thead>
                  <tr>
                    <th>{t('table.reference')}</th>
                    <th>{t('table.name')}</th>
                    <th>{t('table.currentScore')}</th>
                    <th>{t('table.candidateScore')}</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.length === 0 ? (
                    <tr>
                      <td colSpan={4}>{t('table.emptyGroup')}</td>
                    </tr>
                  ) : (
                    rows.map((row) => {
                      const errKey = localValidation.fieldErrors[`option.${row.kind}:${row.reference}`];
                      const errId = `err-${row.kind}-${row.reference}`;
                      return (
                        <tr
                          key={`${row.kind}:${row.reference}`}
                          className={row.isPending ? 'ard-row-pending' : ''}
                          data-testid={`option-row-${row.kind}-${row.reference}`}
                        >
                          <td data-label={t('table.reference')}>
                            <code>{row.reference}</code>
                            {row.isPending && (
                              <div>
                                <span className="ard-badge ard-badge-warn">{t('table.pendingBadge')}</span>
                              </div>
                            )}
                          </td>
                          <td data-label={t('table.name')}>{row.name}</td>
                          <td data-label={t('table.currentScore')}>
                            {row.currentScore !== undefined ? row.currentScore : '—'}
                          </td>
                          <td data-label={t('table.candidateScore')}>
                            <input
                              type="text"
                              inputMode="decimal"
                              className="ard-input"
                              aria-label={`${row.reference} ${t('table.candidateScore')}`}
                              aria-invalid={Boolean(errKey)}
                              aria-describedby={errKey ? errId : undefined}
                              value={row.candidateScore}
                              onChange={(e) => updateOptionScore(row.kind, row.reference, e.target.value)}
                              data-testid={`score-input-${row.kind}-${row.reference}`}
                            />
                            {errKey && (
                              <div id={errId} className="ard-field-error" role="alert">
                                {t(errKey)}
                              </div>
                            )}
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </section>
        );
      })}

      {/* Coefficients */}
      <section className="ard-card" data-testid="group-coefficients">
        <div className="ard-card-header">
          <h2 className="ard-card-title">{t('groups.coefficients')}</h2>
        </div>
        <div className="ard-grid-2">
          {[
            { key: 'engravingBonus', label: t('field.engravingBonus') },
            { key: 'customImageBonusPerImage', label: t('field.customImageBonusPerImage') },
          ].map(({ key, label }) => {
            const errKey = localValidation.fieldErrors[key];
            const errId = `err-${key}`;
            return (
              <div className="ard-field" key={key}>
                <label htmlFor={`input-${key}`}>{label}</label>
                <input
                  id={`input-${key}`}
                  type="text"
                  inputMode="decimal"
                  className="ard-input ard-input-wide"
                  aria-invalid={Boolean(errKey)}
                  aria-describedby={errKey ? errId : undefined}
                  value={candidate[key]}
                  onChange={(e) => updateCoefficient(key, e.target.value)}
                  data-testid={`coeff-input-${key}`}
                />
                {errKey && (
                  <div id={errId} className="ard-field-error" role="alert">
                    {t(errKey)}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </section>

      {/* Thresholds */}
      <section className="ard-card" data-testid="group-thresholds">
        <div className="ard-card-header">
          <h2 className="ard-card-title">{t('groups.thresholds')}</h2>
        </div>
        <div className="ard-grid-2">
          {[
            { key: 'easyMax', label: t('field.easyMax'), mode: 'decimal' },
            { key: 'mediumMax', label: t('field.mediumMax'), mode: 'decimal' },
            { key: 'hardMax', label: t('field.hardMax'), mode: 'decimal' },
            { key: 'veryHardMax', label: t('field.veryHardMax'), mode: 'decimal' },
            { key: 'waxAbove', label: t('field.waxAbove'), mode: 'decimal' },
            { key: 'silverClayGemstonesAbove', label: t('field.silverClayGemstonesAbove'), mode: 'numeric' },
          ].map(({ key, label, mode }) => {
            const errKey = localValidation.fieldErrors[`thresholds.${key}`];
            const errId = `err-th-${key}`;
            return (
              <div className="ard-field" key={key}>
                <label htmlFor={`input-th-${key}`}>{label}</label>
                <input
                  id={`input-th-${key}`}
                  type="text"
                  inputMode={mode}
                  className="ard-input ard-input-wide"
                  aria-invalid={Boolean(errKey)}
                  aria-describedby={errKey ? errId : undefined}
                  value={candidate.thresholds[key]}
                  onChange={(e) => updateThreshold(key, e.target.value)}
                  data-testid={`threshold-input-${key}`}
                />
                {errKey && (
                  <div id={errId} className="ard-field-error" role="alert">
                    {t(errKey)}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </section>

      {/* Optional Non-Personal Admin Samples */}
      <section className="ard-card" data-testid="optional-samples-section">
        <div className="ard-card-header">
          <h2 className="ard-card-title">{t('samples.title')}</h2>
        </div>
        <form onSubmit={handleAddSample} className="ard-grid-2" style={{ marginBottom: '1rem' }}>
          <div className="ard-field">
            <label htmlFor="sample-name">{t('samples.name')}</label>
            <input
              id="sample-name"
              type="text"
              className="ard-input ard-input-wide"
              value={sampleForm.sampleName}
              onChange={(e) => setSampleForm((p) => ({ ...p, sampleName: e.target.value }))}
              data-testid="sample-name-input"
            />
          </div>
          <div className="ard-field">
            <label htmlFor="sample-material">{t('samples.material')}</label>
            <input
              id="sample-material"
              type="text"
              className="ard-input ard-input-wide"
              value={sampleForm.materialReference}
              onChange={(e) => setSampleForm((p) => ({ ...p, materialReference: e.target.value }))}
            />
          </div>
          <div className="ard-field">
            <label htmlFor="sample-shape">{t('samples.shape')}</label>
            <input
              id="sample-shape"
              type="text"
              className="ard-input ard-input-wide"
              value={sampleForm.shapeReference}
              onChange={(e) => setSampleForm((p) => ({ ...p, shapeReference: e.target.value }))}
            />
          </div>
          <div className="ard-field">
            <label htmlFor="sample-images">{t('samples.images')}</label>
            <input
              id="sample-images"
              type="number"
              min={0}
              max={2}
              className="ard-input ard-input-wide"
              value={sampleForm.customImageCount}
              onChange={(e) => setSampleForm((p) => ({ ...p, customImageCount: Number(e.target.value) }))}
            />
          </div>
          <div className="ard-actions-bar">
            <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.85rem' }}>
              <input
                type="checkbox"
                checked={sampleForm.engravingEnabled}
                onChange={(e) => setSampleForm((p) => ({ ...p, engravingEnabled: e.target.checked }))}
              />
              {t('samples.engraving')}
            </label>
            <button type="submit" className="ard-btn ard-btn-secondary" disabled={samples.length >= 20} data-testid="add-sample-btn">
              {t('samples.add')}
            </button>
          </div>
        </form>
        {samples.length === 0 ? (
          <p style={{ color: '#64748b', fontSize: '0.85rem', margin: 0 }}>{t('samples.empty')}</p>
        ) : (
          <ul style={{ margin: 0, paddingLeft: '1.2rem' }}>
            {samples.map((s, idx) => (
              <li key={`${s.sampleName}-${idx}`} style={{ marginBottom: '0.35rem', fontSize: '0.85rem' }}>
                <strong>{s.sampleName}</strong> ({s.materialReference} / {s.shapeReference}){' '}
                <button type="button" className="ard-btn ard-btn-secondary" onClick={() => removeSample(idx)}>
                  {t('samples.remove')}
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* Preview Results */}
      {previewResult && (
        <section className="ard-card" data-testid="preview-results-section">
          <div className="ard-card-header">
            <h2 className="ard-card-title">{t('preview.title')}</h2>
            <span className="ard-badge ard-badge-neutral">
              {t('preview.catalogueRevision')}: {previewResult.catalogueRevision}
            </span>
          </div>

          {standardComparisons.length > 0 && (
            <div style={{ marginBottom: '1.25rem' }}>
              <h3 style={{ fontSize: '0.95rem', marginBottom: '0.5rem' }}>{t('preview.standardTitle')}</h3>
              <div className="ard-table-wrap">
                <table className="ard-table" data-testid="standard-comparisons-table">
                  <thead>
                    <tr>
                      <th>{t('samples.name')}</th>
                      <th>{t('preview.colBefore')}</th>
                      <th>{t('preview.colAfter')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {standardComparisons.map((c, i) => (
                      <tr key={`std-${i}`}>
                        <td data-label={t('samples.name')}>{c.before?.sampleName || c.after?.sampleName}</td>
                        <td data-label={t('preview.colBefore')}>{formatOutcome(c.before)}</td>
                        <td data-label={t('preview.colAfter')}>{formatOutcome(c.after)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {adminComparisons.length > 0 && (
            <div style={{ marginBottom: '1.25rem' }}>
              <h3 style={{ fontSize: '0.95rem', marginBottom: '0.5rem' }}>{t('preview.adminTitle')}</h3>
              <div className="ard-table-wrap">
                <table className="ard-table" data-testid="admin-comparisons-table">
                  <thead>
                    <tr>
                      <th>{t('samples.name')}</th>
                      <th>{t('preview.colBefore')}</th>
                      <th>{t('preview.colAfter')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {adminComparisons.map((c, i) => (
                      <tr key={`adm-${i}`}>
                        <td data-label={t('samples.name')}>{c.before?.sampleName || c.after?.sampleName}</td>
                        <td data-label={t('preview.colBefore')}>{formatOutcome(c.before)}</td>
                        <td data-label={t('preview.colAfter')}>{formatOutcome(c.after)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {(previewResult.unavailableStandardCases || []).length > 0 && (
            <div style={{ marginBottom: '1.25rem' }}>
              <h3 style={{ fontSize: '0.95rem', marginBottom: '0.5rem' }}>{t('preview.unavailableTitle')}</h3>
              <ul>
                {previewResult.unavailableStandardCases.map((u, idx) => (
                  <li key={idx}><strong>{u.sampleName}:</strong> {u.reason}</li>
                ))}
              </ul>
            </div>
          )}

          <div className="ard-card-header" style={{ marginTop: '1rem' }}>
            <div>
              <h3 style={{ fontSize: '0.95rem', margin: '0 0 0.25rem' }}>{t('preview.modelsTitle')}</h3>
              <div style={{ fontSize: '0.82rem', color: '#475569', display: 'flex', gap: '1rem' }}>
                <span data-testid="reviewed-models-count">{t('preview.reviewedCount', { total: modelImpacts.length })}</span>
                <span data-testid="blocked-models-count">{t('preview.blockedCount', { blocked: previewResult.blockingModelCount })}</span>
              </div>
            </div>
            <div className="ard-filter-pills">
              <button
                type="button"
                className={`ard-filter-pill ${modelFilter === 'ALL' ? 'active' : ''}`}
                onClick={() => setModelFilter('ALL')}
              >
                {t('preview.filterAll', { count: modelImpacts.length })}
              </button>
              <button
                type="button"
                className={`ard-filter-pill ${modelFilter === 'AFFECTED' ? 'active' : ''}`}
                onClick={() => setModelFilter('AFFECTED')}
              >
                {t('preview.filterAffected', { count: affectedModels.length })}
              </button>
              <button
                type="button"
                className={`ard-filter-pill ${modelFilter === 'BLOCKED' ? 'active' : ''}`}
                onClick={() => setModelFilter('BLOCKED')}
              >
                {t('preview.filterBlocked', { count: blockedModels.length })}
              </button>
            </div>
          </div>

          <div className="ard-table-wrap">
            <table className="ard-table" data-testid="model-impacts-table">
              <thead>
                <tr>
                  <th>{t('preview.colModel')}</th>
                  <th>{t('preview.colMaterial')}</th>
                  <th>{t('preview.colBefore')}</th>
                  <th>{t('preview.colAfter')}</th>
                  <th>{t('preview.colStatus')}</th>
                </tr>
              </thead>
              <tbody>
                {filteredModels.length === 0 ? (
                  <tr>
                    <td colSpan={5}>{t('preview.noModels')}</td>
                  </tr>
                ) : (
                  filteredModels.map((m) => (
                    <tr
                      key={m.modelReference}
                      className={!m.eligible ? 'ard-row-blocked' : ''}
                      data-testid={`model-impact-${m.modelReference}`}
                    >
                      <td data-label={t('preview.colModel')}>
                        <code>{m.modelReference}</code>
                        <div>{m.name}</div>
                      </td>
                      <td data-label={t('preview.colMaterial')}>{m.materialReference}</td>
                      <td data-label={t('preview.colBefore')}>{formatOutcome(m.before)}</td>
                      <td data-label={t('preview.colAfter')}>{formatOutcome(m.after)}</td>
                      <td data-label={t('preview.colStatus')}>
                        {m.eligible ? (
                          <span className="ard-badge ard-badge-ok">{t('preview.eligible')}</span>
                        ) : (
                          <div>
                            <span className="ard-badge ard-badge-danger">{t('preview.blocked')}</span>
                            <ul style={{ margin: '0.35rem 0 0', paddingLeft: '1rem', fontSize: '0.78rem', color: '#991b1b' }}>
                              {(m.blockingReasons || []).map((reason) => (
                                <li key={reason}>{t(`block.${reason}`) || reason}</li>
                              ))}
                            </ul>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {/* Immutable Publication History */}
      <section className="ard-card" data-testid="policy-history-section">
        <div className="ard-card-header">
          <h2 className="ard-card-title">{t('history.title')}</h2>
        </div>
        {versions.length === 0 ? (
          <p style={{ color: '#64748b', fontSize: '0.85rem', margin: 0 }}>{t('history.empty')}</p>
        ) : (
          <div className="ard-table-wrap">
            <table className="ard-table" data-testid="policy-history-table">
              <thead>
                <tr>
                  <th>{t('summary.effectiveVersion')}</th>
                  <th>{t('summary.publishedAt')}</th>
                  <th>{t('summary.publishedBy')}</th>
                  <th>{t('preview.colStatus')}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {versions.map((v) => (
                  <tr key={v.rulesVersion} data-testid={`history-row-${v.rulesVersion}`}>
                    <td data-label={t('summary.effectiveVersion')}><code>{v.rulesVersion}</code></td>
                    <td data-label={t('summary.publishedAt')}>{fmt.dateTime(v.publishedAt)}</td>
                    <td data-label={t('summary.publishedBy')}>{v.publishedBy}</td>
                    <td data-label={t('preview.colStatus')}>
                      <span className={`ard-badge ${v.status === 'EFFECTIVE' ? 'ard-badge-ok' : 'ard-badge-neutral'}`}>
                        {v.status === 'EFFECTIVE' ? t('history.effective') : t('history.historical')}
                      </span>
                    </td>
                    <td>
                      <button
                        type="button"
                        className="ard-btn ard-btn-secondary"
                        onClick={() => restoreVersion(v)}
                        data-testid={`restore-version-${v.rulesVersion}`}
                      >
                        {t('history.restore')}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Confirmation Dialog */}
      {confirmOpen && (
        <div className="ard-modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="ard-confirm-title" data-testid="publish-confirm-dialog">
          <div className="ard-modal">
            <h2 id="ard-confirm-title" style={{ marginTop: 0, fontSize: '1.15rem' }}>{t('confirm.title')}</h2>
            <p style={{ fontSize: '0.9rem', marginBottom: '0.5rem' }}>
              <strong>{t('confirm.activeVersion')}:</strong> {activePolicy.rulesVersion}
            </p>
            <p style={{ fontSize: '0.9rem', marginBottom: '0.75rem' }}>
              <strong>{t('confirm.changedCount')}:</strong> {changedFieldCount}
            </p>
            <p style={{ fontSize: '0.85rem', color: '#475569', marginBottom: '1.25rem' }}>{t('confirm.note')}</p>
            <div className="ard-actions-bar" style={{ justifyContent: 'flex-end' }}>
              <button
                type="button"
                className="ard-btn ard-btn-secondary"
                onClick={() => setConfirmOpen(false)}
                disabled={publishing}
                data-testid="cancel-publish-btn"
              >
                {t('confirm.cancel')}
              </button>
              <button
                type="button"
                className="ard-btn ard-btn-publish"
                onClick={handleConfirmPublish}
                disabled={publishing || !proofValid}
                data-testid="confirm-publish-btn"
              >
                {publishing ? t('actions.publishing') : t('confirm.submit')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
