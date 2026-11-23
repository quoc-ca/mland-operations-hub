import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { useEnumLabel, useFormat, useT } from '../../shared/i18n/I18nProvider';
import { messages } from '../messages';
import { ErrorNotice, Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { createWorkshopPackageApi } from '../api/packageApi';
import { createOperationsAdminApi } from '../api/operationsAdminApi';
import { createWorkshopAddOnApi } from '../api/addOnApi';
import '../workshop-operations.css';
import '../../ota-connectivity/ota-connectivity.css';

const empty = { description: '', priceVnd: '' };
const unwrap = value => (Array.isArray(value) ? value : value?.items || value?.content || []);

export default function PackageAddOnCataloguePage() {
  const t = useT(messages);
  const fmt = useFormat();
  const packageStateLabel = useEnumLabel(messages, 'packageState');
  const addOnStateLabel = useEnumLabel(messages, 'addOnState');
  const { status } = useAuth();
  const http = useHttp();
  const packageApi = createWorkshopPackageApi(http);
  const operationsApi = createOperationsAdminApi(http);
  const addOnApi = createWorkshopAddOnApi(http);
  const packagesResource = useResource(() => packageApi.listManaged(), []);
  const locationsResource = useResource(() => operationsApi.listLocations(), []);
  const [packageId, setPackageId] = useState(() => {
    try { return sessionStorage.getItem('mland_addon_package_id') || ''; } catch { return ''; }
  });
  const [locationId, setLocationId] = useState(() => {
    try { return sessionStorage.getItem('mland_addon_location_id') || ''; } catch { return ''; }
  });
  const [form, setForm] = useState(empty);
  const [editing, setEditing] = useState(null);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState(null);
  const command = useCommand();
  const packages = useMemo(() => unwrap(packagesResource.data), [packagesResource.data]);
  const locations = useMemo(() => unwrap(locationsResource.data), [locationsResource.data]);

  const sortedPackages = useMemo(() => {
    return [...packages].sort((a, b) => {
      const order = { PUBLISHED: 0, DRAFT: 1, RETIRED: 2 };
      const stateA = order[a.state] ?? 99;
      const stateB = order[b.state] ?? 99;
      if (stateA !== stateB) return stateA - stateB;
      return (a.name || '').localeCompare(b.name || '');
    });
  }, [packages]);

  const handlePackageChange = id => {
    setPackageId(id);
    try { sessionStorage.setItem('mland_addon_package_id', id); } catch {}
  };

  const handleLocationChange = id => {
    setLocationId(id);
    try { sessionStorage.setItem('mland_addon_location_id', id); } catch {}
  };

  const addOnsResource = useResource(
    () => (packageId && locationId ? addOnApi.listManaged(packageId, locationId) : Promise.resolve([])),
    [packageId, locationId],
  );
  const addOns = useMemo(() => unwrap(addOnsResource.data), [addOnsResource.data]);

  useEffect(() => {
    if (!packageId && sortedPackages.length > 0) {
      const preferred = sortedPackages.find(p => p.state === 'PUBLISHED') || sortedPackages[0];
      const id = preferred?.packageId ?? preferred?.id;
      if (id != null) handlePackageChange(String(id));
    }
  }, [sortedPackages, packageId]);

  useEffect(() => {
    if (!locationId && locations.length > 0) {
      const firstLocId = locations[0]?.id ?? locations[0]?.locationId;
      if (firstLocId != null) handleLocationChange(String(firstLocId));
    }
  }, [locations, locationId]);

  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') {
    return (
      <OperationsAuthWorkspace
        topline={t('topline.operations')}
        message={t('addon.signIn')}
      />
    );
  }

  const reset = () => {
    setEditing(null);
    setForm(empty);
    setError(null);
  };

  const save = async event => {
    event.preventDefault();
    setError(null);
    setNotice('');
    if (!packageId || !locationId || !form.description.trim() || Number(form.priceVnd) <= 0) {
      setError({ message: t('addon.err.scope') });
      return;
    }
    const body = { description: form.description.trim(), priceVnd: Number(form.priceVnd) };
    const outcome = await command.run(key =>
      editing
        ? addOnApi.update(packageId, locationId, editing.addOnReference, body, key)
        : addOnApi.create(packageId, locationId, body, key),
    );
    if (outcome.ok) {
      setNotice(editing ? t('addon.ok.updated') : t('addon.ok.created'));
      reset();
      addOnsResource.reload();
    }
  };

  const transition = async (item, targetState) => {
    setError(null);
    setNotice('');
    const outcome = await command.run(key =>
      addOnApi.transition(packageId, locationId, item.addOnReference, targetState, key),
    );
    if (outcome.ok) {
      setNotice(t('addon.ok.transition', { state: addOnStateLabel(targetState) }));
      addOnsResource.reload();
    }
  };

  const startEdit = item => {
    setEditing(item);
    setForm({ description: item.description || '', priceVnd: String(item.priceVnd ?? '') });
    setNotice('');
  };

  return (
    <div className="ota-workspace">
      

      <main className="ota-main">
        <div className="ota-topline">
          {t('topline.operations')} <span>● {t('authorized')}</span>
        </div>
        <header className="ota-page-heading">
          <div>
            <p>{t('addon.intro')}</p>
          </div>
        </header>

        <ErrorNotice
          error={packagesResource.error || locationsResource.error || addOnsResource.error || error || command.error}
          onRetry={() => {
            packagesResource.reload();
            locationsResource.reload();
            addOnsResource.reload();
          }}
        />
        <SuccessNotice>{notice}</SuccessNotice>

        {/* Scope Selection Panel */}
        <section className="ota-panel">
          <div className="ota-panel-head">
            <div>
              <h2>{t('addon.scope.title')}</h2>
              <p className="muted small">{t('addon.scope.hint')}</p>
            </div>
          </div>
          <div className="ota-form-grid">
            <label>
              {t('addon.package')}
              <select value={packageId} onChange={e => handlePackageChange(e.target.value)}>
                <option value="">{t('addon.pickPackage')}</option>
                {sortedPackages.map(p => {
                  const id = p.packageId ?? p.id;
                  return (
                    <option key={id} value={id}>
                      {p.name || p.packageReference || id} {p.state ? `· ${packageStateLabel(p.state)}` : ''}
                    </option>
                  );
                })}
              </select>
            </label>
            <label>
              {t('addon.location')}
              <select value={locationId} onChange={e => handleLocationChange(e.target.value)}>
                <option value="">{t('addon.pickLocation')}</option>
                {locations.map(l => {
                  const id = l.id ?? l.locationId;
                  return (
                    <option key={id} value={id}>
                      {l.name || l.displayName || l.code}
                    </option>
                  );
                })}
              </select>
            </label>
          </div>
        </section>

        {/* Create / Edit Add-on Panel */}
        <section className="ota-panel">
          <div className="ota-panel-head">
            <div>
              <h2>{editing ? t('addon.editTitle') : t('addon.createTitle')}</h2>
              <p className="muted small">
                {t('addon.priceHint', { state: addOnStateLabel('ACTIVE') })}
              </p>
            </div>
          </div>
          <form onSubmit={save}>
            <div className="ota-form-grid">
              <label>
                {t('addon.description')}
                <input
                  value={form.description}
                  onChange={e => setForm({ ...form, description: e.target.value })}
                  maxLength={200}
                  placeholder={t('addon.descriptionPlaceholder')}
                  required
                />
              </label>
              <label>
                {t('addon.price')}
                <input
                  inputMode="numeric"
                  value={form.priceVnd}
                  onChange={e => setForm({ ...form, priceVnd: e.target.value })}
                  placeholder={t('addon.pricePlaceholder')}
                  required
                />
              </label>
            </div>
            <div className="ops-actions" style={{ marginTop: '16px' }}>
              <button className="button primary" type="submit" disabled={command.pending}>
                {editing ? t('addon.save') : t('addon.createDraft')}
              </button>
              {editing && (
                <button type="button" className="button outline" onClick={reset}>
                  {t('addon.cancelEdit')}
                </button>
              )}
            </div>
          </form>
        </section>

        {/* Add-on Catalogue Entries Table */}
        <section className="ota-panel">
          <div className="ota-panel-head">
            <div>
              <h2>{t('addon.list.title')}</h2>
              <p className="muted small">
                {packageId && locationId
                  ? t('addon.list.count', { count: addOns.length })
                  : t('addon.list.chooseScope')}
              </p>
            </div>
          </div>
          {addOnsResource.loading ? (
            <Loading />
          ) : addOns.length === 0 ? (
            <div className="empty-state">
              <p>{t('addon.list.empty')}</p>
            </div>
          ) : (
            <div className="ops-table-wrap">
              <table className="ops-table">
                <thead>
                  <tr>
                    <th>{t('addon.col.description')}</th>
                    <th>{t('addon.col.reference')}</th>
                    <th>{t('addon.col.price')}</th>
                    <th>{t('addon.col.status')}</th>
                    <th>{t('addon.col.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {addOns.map(item => (
                    <tr key={item.addOnReference}>
                      <td>
                        <strong>{item.description}</strong>
                      </td>
                      <td>
                        <code>{item.addOnReference}</code>
                      </td>
                      <td>
                        {fmt.money(String(item.priceVnd || 0))}
                      </td>
                      <td>
                        <span className="ops-status">{addOnStateLabel(item.state)}</span>
                      </td>
                      <td>
                        <div className="ops-actions" style={{ margin: 0 }}>
                          <button
                            className="button outline small"
                            onClick={() => startEdit(item)}
                            disabled={item.state === 'RETIRED'}
                          >
                            {t('addon.edit')}
                          </button>
                          {item.state === 'DRAFT' && (
                            <button
                              className="button primary small"
                              onClick={() => transition(item, 'ACTIVE')}
                              disabled={command.pending}
                            >
                              {t('addon.activate')}
                            </button>
                          )}
                          {item.state === 'ACTIVE' && (
                            <button
                              className="button outline small"
                              onClick={() => transition(item, 'RETIRED')}
                              disabled={command.pending}
                            >
                              {t('addon.retire')}
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}
