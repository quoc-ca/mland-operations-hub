import React, { useCallback, useEffect, useState } from 'react';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { messages } from '../messages';

/**
 * Branches of a product voucher (Session 2026-11-09, T086): "All branches" (default, nothing sent) or some of the
 * branches from branch management (`/api/v1/workshop-locations`, owner 003.2) — the same list 002's product create
 * form offers, so only the configured, visible active branches appear (IDs are the FE-08 cart-line `locationId`).
 * An automatic discount always applies everywhere, so it only shows a note.
 */
export function ProductBranchPicker({ api, voucher, mode, value, onChange, error }) {
  const t = useT(messages);
  const [branches, setBranches] = useState(null);
  const [failed, setFailed] = useState(false);

  const load = useCallback(async () => {
    setFailed(false);
    try {
      const list = await api.listWorkshopBranches();
      setBranches((Array.isArray(list) ? list : []).filter(branch => branch.active !== false));
    } catch {
      setFailed(true);
    }
  }, [api]);
  useEffect(() => { if (voucher && mode === 'SOME' && !branches) load(); }, [voucher, mode, branches, load]);

  if (!voucher) return <p className="muted small">{t('branch.autoHint')}</p>;

  const chosen = new Set((value || []).map(String));
  const toggle = id => {
    const next = new Set(chosen);
    if (next.has(id)) next.delete(id); else next.add(id);
    onChange(mode, [...next]);
  };

  return (
    <fieldset className="branch-picker">
      <legend className="sr-only">{t('branch.title')}</legend>
      <p className="muted small">{t('branch.hint')}</p>
      <label className="check-row">
        <input type="radio" name="branchMode" checked={mode !== 'SOME'} onChange={() => onChange('ALL', [])} /> {t('branch.all')}
      </label>
      <label className="check-row">
        <input type="radio" name="branchMode" checked={mode === 'SOME'} onChange={() => onChange('SOME', [...chosen])} /> {t('branch.some')}
      </label>
      {mode === 'SOME' && (
        <div className="target-sizes" aria-label={t('branch.title')}>
          {failed && <p className="muted small" role="status">{t('branch.failed')} <button type="button" className="text-button" onClick={load}>{t('branch.retry')}</button></p>}
          {!failed && !branches && <p className="muted small" role="status">{t('branch.loading')}</p>}
          {branches && branches.length === 0 && <p className="muted small">{t('branch.none')}</p>}
          {branches && branches.map(branch => (
            <label key={branch.id} className="check-row">
              <input type="checkbox" checked={chosen.has(String(branch.id))} onChange={() => toggle(String(branch.id))} />
              {branch.name}{branch.address ? <span className="muted small"> · {branch.address}</span> : null}
            </label>
          ))}
        </div>
      )}
      {error && <p className="field-error">{error}</p>}
    </fieldset>
  );
}
