import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { ErrorNotice, Field, Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { createWorkshopPackageApi } from '../api/packageApi';
import { createOperationsAdminApi } from '../api/operationsAdminApi';
import { createWorkshopAddOnApi } from '../api/addOnApi';
import '../workshop-operations.css';
import '../../ota-connectivity/ota-connectivity.css';

const empty = { description: '', priceVnd: '' };
const unwrap = value => Array.isArray(value) ? value : (value?.items || value?.content || []);

export default function PackageAddOnCataloguePage() {
  const { status } = useAuth();
  const http = useHttp();
  const packageApi = createWorkshopPackageApi(http);
  const operationsApi = createOperationsAdminApi(http);
  const addOnApi = createWorkshopAddOnApi(http);
  const packagesResource = useResource(() => packageApi.listManaged(), []);
  const locationsResource = useResource(() => operationsApi.listLocations(), []);
  const [packageId, setPackageId] = useState('');
  const [locationId, setLocationId] = useState('');
  const [form, setForm] = useState(empty);
  const [editing, setEditing] = useState(null);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState(null);
  const command = useCommand();
  const packages = useMemo(() => unwrap(packagesResource.data), [packagesResource.data]);
  const locations = useMemo(() => unwrap(locationsResource.data), [locationsResource.data]);
  const addOnsResource = useResource(
    () => packageId && locationId ? addOnApi.listManaged(packageId, locationId) : Promise.resolve([]),
    [packageId, locationId],
  );
  const addOns = useMemo(() => unwrap(addOnsResource.data), [addOnsResource.data]);

  useEffect(() => {
    if (!packageId && packages[0]?.packageId != null) setPackageId(String(packages[0].packageId));
  }, [packages, packageId]);
  useEffect(() => {
    if (!locationId && locations[0]?.locationId != null) setLocationId(String(locations[0].locationId));
  }, [locations, locationId]);

  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') return <div className="ota-container"><div className="empty-state"><p>Authentication required to manage the package add-on catalogue.</p><Link className="button primary" to="/login">Sign In</Link></div></div>;

  const reset = () => { setEditing(null); setForm(empty); setError(null); };
  const save = async event => {
    event.preventDefault(); setError(null); setNotice('');
    if (!packageId || !locationId || !form.description.trim() || Number(form.priceVnd) <= 0) {
      setError({ message: 'Choose a package and location, then enter a description and positive VND price.' }); return;
    }
    const body = { description: form.description.trim(), priceVnd: Number(form.priceVnd) };
    const outcome = await command.run(key => editing
      ? addOnApi.update(packageId, locationId, editing.addOnReference, body, key)
      : addOnApi.create(packageId, locationId, body, key));
    if (outcome.ok) { setNotice(editing ? 'Add-on updated.' : 'Add-on draft created. Activate it when ready.'); reset(); addOnsResource.reload(); }
  };
  const transition = async (item, targetState) => {
    setError(null); setNotice('');
    const outcome = await command.run(key => addOnApi.transition(packageId, locationId, item.addOnReference, targetState, key));
    if (outcome.ok) { setNotice(`Add-on moved to ${targetState}.`); addOnsResource.reload(); }
  };
  const startEdit = item => { setEditing(item); setForm({ description: item.description || '', priceVnd: String(item.priceVnd ?? '') }); setNotice(''); };

  return <div className="ota-workspace">
    <aside className="ota-sidebar"><div className="ota-brand">MLAND <span>operations hub</span></div><nav><Link to="/operations/admin">Operations admin</Link><Link className="active" to="/operations/package-add-ons">Add-on catalogue</Link><Link to="/operations/packages">Packages</Link><Link to="/operations/sessions">Sessions</Link></nav></aside>
    <main className="ota-main"><header className="ota-topbar"><span>MANAGER / CATALOGUE</span><span>Updated just now</span></header>
      <div className="ota-page-head"><div><p className="eyebrow">MANAGER / PACKAGE ADD-ONS</p><h1>Package add-on catalogue</h1><p className="muted">Manage fixed workshop add-ons per package and location. This is separate from shop add-on billing.</p></div></div>
      <ErrorNotice error={packagesResource.error || locationsResource.error || addOnsResource.error || error || command.error} onRetry={() => { packagesResource.reload(); locationsResource.reload(); addOnsResource.reload(); }} />
      <SuccessNotice>{notice}</SuccessNotice>
      <section className="ota-panel"><div className="ota-evidence-grid"><Field label="Package"><select value={packageId} onChange={e => setPackageId(e.target.value)}><option value="">Select package</option>{packages.map(p => <option key={p.packageId} value={p.packageId}>{p.name || p.packageReference || p.packageId} · {p.state || ''}</option>)}</select></Field><Field label="Location"><select value={locationId} onChange={e => setLocationId(e.target.value)}><option value="">Select location</option>{locations.map(l => <option key={l.locationId || l.id} value={l.locationId || l.id}>{l.name || l.displayName || l.code}</option>)}</select></Field></div></section>
      <section className="ota-panel"><div className="ota-panel-head"><div><h2>{editing ? 'Edit add-on' : 'Create add-on draft'}</h2><p className="muted small">Prices are VND and become customer selectable only after activation.</p></div></div><form className="ota-evidence-grid" onSubmit={save}><Field label="Description"><input value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} maxLength={200} /></Field><Field label="Price (VND)"><input inputMode="numeric" value={form.priceVnd} onChange={e => setForm({ ...form, priceVnd: e.target.value })} /></Field><div className="actions form-actions"><button className="button primary" disabled={command.pending}>{editing ? 'Save changes' : 'Create draft'}</button>{editing && <button type="button" className="button outline" onClick={reset}>Cancel</button>}</div></form></section>
      <section className="ota-panel"><div className="ota-panel-head"><div><h2>Catalogue entries</h2><p className="muted small">{packageId && locationId ? `${addOns.length} entries for the selected scope.` : 'Select a package and location.'}</p></div></div>{addOnApi && addOnsResource.loading ? <Loading /> : addOns.length === 0 ? <div className="empty-state"><p>No add-ons in this scope.</p></div> : <div className="ops-table-wrap"><table className="ops-table"><thead><tr><th>Description</th><th>Price</th><th>State</th><th>Actions</th></tr></thead><tbody>{addOns.map(item => <tr key={item.addOnReference}><td>{item.description}<small>{item.addOnReference}</small></td><td>{Number(item.priceVnd || 0).toLocaleString('vi-VN')} {item.currencyCode || 'VND'}</td><td><span className="ops-status">{item.state}</span></td><td><div className="ops-actions"><button className="button outline" onClick={() => startEdit(item)} disabled={item.state === 'RETIRED'}>Edit</button>{item.state === 'DRAFT' && <button className="button primary" onClick={() => transition(item, 'ACTIVE')} disabled={command.pending}>Activate</button>}{item.state === 'ACTIVE' && <button className="button outline" onClick={() => transition(item, 'RETIRED')} disabled={command.pending}>Retire</button>}</div></td></tr>)}</tbody></table></div>}</section>
    </main>
  </div>;
}
