import React, { useCallback, useEffect, useState } from 'react';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { Field } from '../../../../shared/ui/components';
import { messages } from '../messages';

const today = () => new Date().toISOString().slice(0, 10);
const asList = value => (Array.isArray(value) ? value : []);

/** Session option text: date, slot and branch name, so a Manager never has to remember a UUID. */
export const sessionLabel = (session, branchNames) =>
  [session.workshopDate, session.slotId, branchNames[String(session.locationId)]].filter(Boolean).join(' · ') || String(session.id);

/**
 * Workshop-scope target picker of the create form: package, branch and session are chosen from 003's lists, each with
 * "All" (no restriction) or exactly one item. The form keeps the chosen id as text ('' = all), so the request body is
 * unchanged: an unrestricted dimension is simply left out. Sessions shown are upcoming ones; a session can be narrowed
 * to the chosen branch. The workshop date is not asked here: the booking period is the programme's validity window.
 * With `packagesOnly` (an automatic discount) only packages are offered, as checkboxes, one or more.
 */
export function WorkshopTargetPicker({ api, form, setField, packagesOnly = false, errors = {} }) {
  const t = useT(messages);
  const [lists, setLists] = useState(null);
  const [failed, setFailed] = useState(false);

  const load = useCallback(async () => {
    setFailed(false);
    try {
      const [packages, branches, sessions] = await Promise.all([
        api.listWorkshopPackages(), api.listWorkshopBranches(), api.listWorkshopSessions({ from: today() }),
      ]);
      setLists({ packages: asList(packages), branches: asList(branches), sessions: asList(sessions) });
    } catch {
      setFailed(true);
    }
  }, [api]);
  useEffect(() => { load(); }, [load]);

  if (failed) {
    return <p className="muted small" role="status">{t('workshopPicker.failed')} <button type="button" className="text-button" onClick={load}>{t('workshopPicker.retry')}</button></p>;
  }
  if (!lists) return <p className="muted small" role="status">{t('workshopPicker.loading')}</p>;

  const branchNames = Object.fromEntries(lists.branches.map(branch => [String(branch.id), branch.name]));
  const sessions = lists.sessions.filter(session => !form.branchIds || String(session.locationId) === form.branchIds);
  const change = name => event => setField(name, event.target.value);
  // Changing the branch clears a session that no longer belongs to it.
  const changeBranch = event => {
    const branchId = event.target.value;
    setField('branchIds', branchId);
    const chosen = lists.sessions.find(session => String(session.id) === form.sessionIds);
    if (chosen && branchId && String(chosen.locationId) !== branchId) setField('sessionIds', '');
  };

  if (packagesOnly) {
    // Session 2026-11-14 (FR-044): a workshop automatic discount covers one or more packages, never a branch,
    // date or session, because its price is shown before the customer picks them.
    const chosen = new Set(String(form.packageIds || '').split(',').filter(Boolean));
    const toggle = packageId => event => {
      const next = new Set(chosen);
      if (event.target.checked) next.add(packageId); else next.delete(packageId);
      setField('packageIds', lists.packages.map(item => String(item.packageId)).filter(item => next.has(item)).join(','));
    };
    return (
      <fieldset className="package-checks">
        <legend>{t('target.packageIds')}</legend>
        {lists.packages.map(item => (
          <label key={item.packageId} className="check-row">
            <input type="checkbox" checked={chosen.has(String(item.packageId))} onChange={toggle(String(item.packageId))} /> {item.name}
          </label>
        ))}
        {errors.packageIds && <p className="field-error">{errors.packageIds}</p>}
      </fieldset>
    );
  }

  return (
    <div className="form-grid">
      <Field label={t('target.packageIds')} error={errors.packageIds}>
        <select value={form.packageIds} onChange={change('packageIds')}>
          <option value="">{t('workshopPicker.allPackages')}</option>
          {lists.packages.map(item => <option key={item.packageId} value={String(item.packageId)}>{item.name}</option>)}
        </select>
      </Field>
      <Field label={t('target.branchIds')} error={errors.branchIds}>
        <select value={form.branchIds} onChange={changeBranch}>
          <option value="">{t('workshopPicker.allBranches')}</option>
          {lists.branches.map(item => <option key={item.id} value={String(item.id)}>{item.name}</option>)}
        </select>
      </Field>
      <Field label={t('target.sessionIds')} error={errors.sessionIds}>
        <select value={form.sessionIds} onChange={change('sessionIds')}>
          <option value="">{t('workshopPicker.allSessions')}</option>
          {sessions.map(item => <option key={item.id} value={String(item.id)}>{sessionLabel(item, branchNames)}</option>)}
        </select>
      </Field>
    </div>
  );
}
