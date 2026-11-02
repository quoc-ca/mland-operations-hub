import React, { useRef, useState } from 'react';
import { Link } from 'react-router';
import { useFormat, useT } from '../../../../shared/i18n/I18nProvider';
import { ConfirmDialog, ErrorNotice, useCommand } from '../../../../shared/ui/components';
import { useCatalogueApi } from '../../api/useCatalogueApi';
import { messages } from '../../messages';
import { LIMITS } from '../../rules';
import { PageTop } from '../ManagementLayout';

function save({ blob, fileName }, fallback) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName || fallback;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/**
 * T101 Excel import: template → file (client checks are hints only) → validation result (row/column errors with
 * report, or a preview) → separate confirmation → created products with SKUs. Each step has its own key. When a
 * confirmation gets no answer, the batch is read again before sending the same key. Imported products have no
 * images yet and need them before the review.
 */
export default function ImportPage() {
  const t = useT(messages);
  const format = useFormat();
  const api = useCatalogueApi();
  const inputRef = useRef(null);
  const [file, setFile] = useState(null);
  const [hint, setHint] = useState(null);
  const [batch, setBatch] = useState(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const template = useCommand();
  const report = useCommand();
  const validation = useCommand();
  const confirmation = useCommand();
  const refresh = useCommand();

  const choose = event => {
    const chosen = event.target.files[0] || null;
    event.target.value = '';
    setBatch(null);
    validation.reset();
    confirmation.reset();
    setFile(chosen);
    if (!chosen) { setHint(null); return; }
    setHint(!chosen.name.toLowerCase().endsWith('.xlsx') ? 'import.typeHint' : chosen.size > LIMITS.importBytes ? 'import.sizeHint' : null);
  };

  const validate = async () => {
    const outcome = await validation.run(key => api.validateImport(key, file));
    if (outcome.ok) setBatch(outcome.result);
  };
  const retryValidate = async () => {
    const outcome = await validation.retry();
    if (outcome?.ok) setBatch(outcome.result);
  };
  const confirm = async () => {
    const outcome = await confirmation.run(key => api.confirmImport(key, batch.batchId, batch.version));
    afterConfirm(outcome);
  };
  const afterConfirm = outcome => {
    if (!outcome) return;
    if (outcome.ok) { setBatch(outcome.result); setConfirmOpen(false); }
    else if (!outcome.error.retryable) setConfirmOpen(false);
  };
  // After a lost answer: read the batch first; only if it is still VALIDATED resend the same key.
  const checkThenRetry = async () => {
    const latest = await refresh.run(() => api.getImport(batch.batchId));
    if (!latest.ok) return;
    setBatch(latest.result);
    if (latest.result.state === 'IMPORTED') { confirmation.reset(); setConfirmOpen(false); return; }
    if (latest.result.state === 'VALIDATED') afterConfirm(await confirmation.retry());
    else { confirmation.reset(); setConfirmOpen(false); }
  };
  const restart = () => { setBatch(null); setFile(null); setHint(null); validation.reset(); confirmation.reset(); };

  const staleImport = ['IMPORT_BATCH_EXPIRED', 'IMPORT_PREVIEW_STALE', 'IMPORT_HAS_ERRORS'].includes(confirmation.error?.code);

  return (
    <>
      <PageTop eyebrow={t('nav.group')} title={t('import.title')} />
      <section className="workspace-card">
        <h2>{t('import.step1')}</h2>
        <p className="muted small">{t('import.templateHint')}</p>
        <button type="button" className="button outline" disabled={template.pending}
          onClick={async () => { const outcome = await template.run(() => api.downloadImportTemplate()); if (outcome.ok) save(outcome.result, 'mau-import-san-pham.xlsx'); }}>
          {t('import.downloadTemplate')}
        </button>
        <ErrorNotice error={template.error} messages={messages} onRetry={async () => { const outcome = await template.retry(); if (outcome?.ok) save(outcome.result, 'mau-import-san-pham.xlsx'); }} />
      </section>

      <section className="workspace-card">
        <h2>{t('import.step2')}</h2>
        <input ref={inputRef} type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" hidden onChange={choose} />
        <div className="actions">
          <button type="button" className="button outline" onClick={() => inputRef.current?.click()} disabled={validation.pending}>{t('import.chooseFile')}</button>
          {file && <span>{file.name}</span>}
        </div>
        {hint && <p className="field-error">{t(hint)}</p>}
        {file && !batch && (
          <button type="button" className="button primary" onClick={validate} disabled={validation.pending}>{validation.pending ? t('import.validating') : t('import.validate')}</button>
        )}
        <ErrorNotice error={validation.error} messages={messages} onRetry={retryValidate} />
      </section>

      {batch && (
        <section className="workspace-card" aria-live="polite">
          <div className="card-head">
            <h2>{t('import.step3')}</h2>
            <span className={batch.state === 'REJECTED' ? 'status' : 'status ok'}>{t(`import.state.${batch.state}`)}</span>
          </div>
          <p className="muted small">{t('import.summary', { file: batch.fileName, rows: batch.rowCount, products: batch.productCount, variants: batch.variantCount })}</p>

          {batch.state === 'REJECTED' && (
            <>
              <p>{t('import.errorCount', { count: batch.errorCount })}</p>
              {batch.errors.length > 0 ? (
                <div className="data-table" role="table" aria-label={t('import.errors')}>
                  <div className="data-row head import-errors" role="row">
                    <span role="columnheader">{t('import.row')}</span><span role="columnheader">{t('import.column')}</span>
                    <span role="columnheader">{t('import.value')}</span><span role="columnheader">{t('import.problem')}</span>
                  </div>
                  {batch.errors.map((error, index) => (
                    <div className="data-row import-errors" role="row" key={index}>
                      <span role="cell">{error.row}</span><span role="cell">{error.column}</span>
                      <span role="cell" className="mono">{error.value ?? '—'}</span>
                      <span role="cell">{t(`importCode.${error.code}`) !== `importCode.${error.code}` ? t(`importCode.${error.code}`) : error.message}</span>
                    </div>
                  ))}
                </div>
              ) : <p className="muted">{t('import.errorsExpired')}</p>}
              <div className="actions form-actions">
                <button type="button" className="button outline" disabled={report.pending}
                  onClick={async () => { const outcome = await report.run(() => api.downloadImportErrors(batch.batchId)); if (outcome.ok) save(outcome.result, 'bao-cao-loi-import.xlsx'); }}>
                  {t('import.downloadReport')}
                </button>
                <button type="button" className="button primary" onClick={restart}>{t('import.again')}</button>
              </div>
              <ErrorNotice error={report.error} messages={messages} onRetry={async () => { const outcome = await report.retry(); if (outcome?.ok) save(outcome.result, 'bao-cao-loi-import.xlsx'); }} />
            </>
          )}

          {batch.state === 'VALIDATED' && (
            <>
              <p>{t('import.expiresAt', { at: format.dateTime(batch.expiresAt) })}</p>
              <ul className="preview-list">
                {(batch.preview || []).map(item => (
                  <li key={item.groupKey}><strong>{item.name}</strong> <span className="muted">({item.groupKey})</span> — {t('import.previewSizes', { sizes: item.sizes.join(', ') })}</li>
                ))}
              </ul>
              {!staleImport && <button type="button" className="button primary" onClick={() => { confirmation.reset(); setConfirmOpen(true); }}>{t('import.confirm')}</button>}
            </>
          )}

          {batch.state === 'IMPORTED' && (
            <>
              <p className="notice success">{t('import.done', { count: batch.created?.length || 0 })}</p>
              <ul className="preview-list">
                {(batch.created || []).map(item => (
                  <li key={item.productId}>
                    <Link className="text-link" to={`/catalogue-management/products/${encodeURIComponent(item.productId)}`}>{t('import.createdProduct', { id: item.productId, group: item.groupKey })}</Link>
                    <span className="mono small"> {item.skus.join(', ')}</span>
                  </li>
                ))}
              </ul>
              <p className="notice">{t('import.addImages')}</p>
              <button type="button" className="button outline" onClick={restart}>{t('import.another')}</button>
            </>
          )}

          {!confirmOpen && <ErrorNotice error={confirmation.error} messages={messages} />}
          {!confirmOpen && staleImport && <button type="button" className="button primary" onClick={restart}>{t('import.again')}</button>}
        </section>
      )}

      <ConfirmDialog open={confirmOpen} title={t('import.confirmTitle')} confirmLabel={t('import.confirm')}
        pending={confirmation.pending || refresh.pending} onConfirm={confirm} onCancel={() => setConfirmOpen(false)}>
        <p>{t('import.confirmText', { products: batch?.productCount, variants: batch?.variantCount })}</p>
        <ErrorNotice error={confirmation.error} messages={messages} onRetry={checkThenRetry} />
        <ErrorNotice error={refresh.error} messages={messages} onRetry={checkThenRetry} />
      </ConfirmDialog>
    </>
  );
}
