import React, { useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { ErrorNotice, Field, Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { OperationsSidebar } from '../../shared/ui/OperationsSidebar';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { createOperationsAdminApi } from '../api/operationsAdminApi';
import { useRole } from '../../shared/auth/useRole';
import { BulkSessionForm, SessionEditForm } from '../components/SessionAdminForms';
import { LocationEditForm } from '../components/LocationEditForm';
import { SlotEditForm } from '../components/SlotEditForm';
import { sessionTimeRange } from './OperationsDashboardPage';
import '../workshop-operations.css';
import '../../ota-connectivity/ota-connectivity.css';

const unwrap = data => Array.isArray(data) ? data : data?.items || data?.content || [];
const emptyLocation = { code: '', name: '', address: '', active: true };
const emptySlot = { label: '', startsAtLocal: '09:00', endsAtLocal: '12:00', active: true };
const emptySessionFilters = { from: '', to: '', state: '', locationId: '' };
const SESSION_FILTER_STATES = ['DRAFT', 'PUBLISHED', 'SALES_PAUSED', 'IN_PROGRESS', 'ATTENDANCE_FINALIZED', 'CANCELLATION_REQUESTED', 'CANCELLED'];
const emptySession = { locationId: '', slotId: '', workshopDate: '', capacity: '10', checkInOpensAt: '' };

function ListState({ resource, items, empty, children }) {
  if (resource.loading) return <Loading />;
  if (resource.error) return <ErrorNotice error={resource.error} onRetry={resource.reload} onReload={resource.reload} />;
  if (items === undefined && Array.isArray(unwrap(resource.data)) && unwrap(resource.data).length === 0) return <div className="empty-state"><p>{empty}</p></div>;
  if (items && items.length === 0) return <div className="empty-state"><p>{empty}</p></div>;
  return children || <div className="empty-state"><p>{empty}</p></div>;
}

export default function OperationsAdministrationPage() {
  const { status } = useAuth();
  const [params, setParams] = useSearchParams();
  const pathname = useLocation().pathname;
  const tab = params.get('tab') || (pathname.endsWith('/slots') ? 'slots' : pathname.endsWith('/sessions') ? 'sessions' : 'locations');
  const api = createOperationsAdminApi(useHttp());
  const locations = useResource(() => api.listLocations(), []);
  const locationItems = useMemo(() => unwrap(locations.data), [locations.data]);
  // Slots and sessions are planned only at active locations; the location list itself shows inactive ones too.
  const selectableLocations = useMemo(() => locationItems.filter(item => item.active !== false), [locationItems]);
  const [selectedLocation, setSelectedLocation] = useState('');
  const locationId = selectedLocation || selectableLocations[0]?.id || selectableLocations[0]?.locationId || '';
  const slots = useResource(() => locationId ? api.listSlots(locationId) : Promise.resolve([]), [locationId]);
  const [sessionFilters, setSessionFilters] = useState(emptySessionFilters);
  // The list endpoint filters server-side by from/to (workshop date), state and locationId; empty values are omitted.
  const sessions = useResource(() => api.listSessions(Object.fromEntries(Object.entries(sessionFilters).filter(([, value]) => value))), [sessionFilters.from, sessionFilters.to, sessionFilters.state, sessionFilters.locationId]);
  const command = useCommand();
  const [locationForm, setLocationForm] = useState(emptyLocation);
  const [slotForm, setSlotForm] = useState(emptySlot);
  const [sessionForm, setSessionForm] = useState(emptySession);
  const [notice, setNotice] = useState('');
  const [formError, setFormError] = useState('');
  const [sessionTargetState, setSessionTargetState] = useState('');
  // Bulk generation and session edits are Manager commands; the server still decides (the role is UX only).
  const role = useRole();
  const isManager = role === 'MANAGER';
  const [editingSessionId, setEditingSessionId] = useState(null);
  const [editingLocationId, setEditingLocationId] = useState(null);
  const [editingSlotId, setEditingSlotId] = useState(null);
  useEffect(() => {
    if (sessionForm.locationId && String(sessionForm.locationId) !== String(selectedLocation)) {
      setSelectedLocation(sessionForm.locationId);
    }
  }, [sessionForm.locationId, selectedLocation]);

  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') {
    return (
      <OperationsAuthWorkspace
        topline="MLAND / WORKSHOP OPERATIONS"
        message="Authentication required to manage workshop operations."
      />
    );
  }

  const switchTab = next => { setParams({ tab: next }); setNotice(''); setFormError(''); };
  const run = async (action, success) => { setFormError(''); const outcome = await command.run(action); if (outcome.ok) { setNotice(success); locations.reload(); slots.reload(); sessions.reload(); } };
  const submitLocation = e => { e.preventDefault(); if (!locationForm.code.trim() || !locationForm.name.trim()) return setFormError('Location code and name are required.'); run(key => api.createLocation({ ...locationForm, code: locationForm.code.trim(), name: locationForm.name.trim() }, key), 'Location created.'); };
  const submitSlot = e => { e.preventDefault(); if (!locationId) return setFormError('Choose a location first.'); if (!slotForm.label.trim()) return setFormError('Slot label is required.'); run(key => api.createSlot(locationId, slotForm, key), 'Slot created.'); };
  const submitSession = e => { e.preventDefault(); const capacity = Number(sessionForm.capacity); if (!sessionForm.locationId || !sessionForm.slotId || !sessionForm.workshopDate || !Number.isInteger(capacity) || capacity < 1) return setFormError('Location, slot, date and positive capacity are required.'); run(key => api.createSession({ ...sessionForm, locationId: Number(sessionForm.locationId), capacity, checkInOpensAt: sessionForm.checkInOpensAt || null }, key), 'Session created in DRAFT.'); };
  const changeSessionLocation = value => { setSelectedLocation(value); setSessionForm({ ...sessionForm, locationId: value, slotId: '' }); };

  const renderLocations = () => <><section className="ota-panel"><div className="ota-panel-head"><div><h2>Workshop locations</h2><p className="muted small">Manage active branches used by slots and sessions.</p></div></div>{isManager && <form className="ota-evidence-grid" onSubmit={submitLocation}><Field label="Code"><input value={locationForm.code} onChange={e => setLocationForm({ ...locationForm, code: e.target.value })} /></Field><Field label="Name"><input value={locationForm.name} onChange={e => setLocationForm({ ...locationForm, name: e.target.value })} /></Field><Field label="Address"><input value={locationForm.address} onChange={e => setLocationForm({ ...locationForm, address: e.target.value })} /></Field><label className="package-checkbox"><input type="checkbox" checked={locationForm.active} onChange={e => setLocationForm({ ...locationForm, active: e.target.checked })} /> Active</label><div className="actions"><button className="button primary" disabled={command.pending}>Create location</button></div></form>}</section><ListState resource={locations} empty="No workshop locations found."><section className="ota-panel"><div className="ota-panel-head"><h2>Location queue</h2><span>{locationItems.length} records</span></div><div className="ops-table-wrap"><table className="ops-table"><thead><tr><th>Code</th><th>Name</th><th>Address</th><th>Status</th>{isManager && <th>Edit</th>}</tr></thead><tbody>{locationItems.map(item => <React.Fragment key={item.id || item.locationId}><tr><td><strong>{item.code}</strong></td><td>{item.name}</td><td>{item.address || '—'}</td><td><span className="ops-status">{item.active === false ? 'INACTIVE' : 'ACTIVE'}</span></td>{isManager && <td><button type="button" className="button outline small" aria-expanded={editingLocationId === item.id} onClick={() => setEditingLocationId(editingLocationId === item.id ? null : item.id)}>Edit</button></td>}</tr>{isManager && editingLocationId === item.id && <tr><td colSpan={5}><LocationEditForm key={`${item.id}-${item.name}-${item.address}-${item.active}`} api={api} location={item} onSaved={() => { setEditingLocationId(null); setNotice('Location updated.'); locations.reload(); }} onReload={locations.reload} onClose={() => setEditingLocationId(null)} /></td></tr>}</React.Fragment>)}</tbody></table></div></section></ListState></>;
  const renderSlots = () => <><section className="ota-panel"><div className="ota-panel-head"><div><h2>Slot administration</h2><p className="muted small">Reusable non-overlapping time slots per location.</p></div><select value={locationId} onChange={e => setSelectedLocation(e.target.value)} aria-label="Location"><option value="">Select location</option>{selectableLocations.map(item => <option key={item.id || item.locationId} value={item.id || item.locationId}>{item.name || item.code}</option>)}</select></div>{isManager && <form className="ota-evidence-grid" onSubmit={submitSlot}><Field label="Label"><input value={slotForm.label} onChange={e => setSlotForm({ ...slotForm, label: e.target.value })} placeholder="Morning" /></Field><Field label="Starts at"><input type="time" value={slotForm.startsAtLocal} onChange={e => setSlotForm({ ...slotForm, startsAtLocal: e.target.value })} /></Field><Field label="Ends at"><input type="time" value={slotForm.endsAtLocal} onChange={e => setSlotForm({ ...slotForm, endsAtLocal: e.target.value })} /></Field><label className="package-checkbox"><input type="checkbox" checked={slotForm.active} onChange={e => setSlotForm({ ...slotForm, active: e.target.checked })} /> Active</label><div className="actions"><button className="button primary" disabled={command.pending || !locationId}>Create slot</button></div></form>}</section><ListState resource={slots} empty={locationId ? 'No slots found for this location.' : 'Select a location to view slots.'}><section className="ota-panel"><div className="ota-panel-head"><h2>Reusable slots</h2><span>{unwrap(slots.data).length} records</span></div><div className="ops-table-wrap"><table className="ops-table"><thead><tr><th>Label</th><th>Time</th><th>Status</th>{isManager && <th>Action</th>}</tr></thead><tbody>{unwrap(slots.data).map(item => <React.Fragment key={item.id}><tr><td><strong>{item.label}</strong></td><td>{item.startsAtLocal} – {item.endsAtLocal}</td><td><span className="ops-status">{item.active === false ? 'INACTIVE' : 'ACTIVE'}</span></td>{isManager && <td><div className="actions"><button type="button" className="button outline small" aria-expanded={editingSlotId === item.id} onClick={() => setEditingSlotId(editingSlotId === item.id ? null : item.id)}>Edit</button><button className="button outline small" disabled={command.pending} onClick={() => run(key => api.updateSlot(item.id, { label: item.label, startsAtLocal: item.startsAtLocal, endsAtLocal: item.endsAtLocal, active: item.active === false }, key), item.active === false ? 'Slot activated.' : 'Slot retired.')}>{item.active === false ? 'Activate' : 'Deactivate'}</button></div></td>}</tr>{isManager && editingSlotId === item.id && <tr><td colSpan={4}><SlotEditForm key={`${item.id}-${item.label}-${item.startsAtLocal}-${item.endsAtLocal}`} api={api} slot={item} onSaved={() => { setEditingSlotId(null); setNotice('Slot updated.'); slots.reload(); }} onReload={slots.reload} onClose={() => setEditingSlotId(null)} /></td></tr>}</React.Fragment>)}</tbody></table></div></section></ListState></>;
  const sessionItems = unwrap(sessions.data);
  const renderSessions = () => <><section className="ota-panel"><div className="ota-panel-head"><div><h2>Session administration</h2><p className="muted small">Review, publish and close workshop sessions.</p></div></div>{isManager && <form className="ota-evidence-grid" onSubmit={submitSession}><Field label="Location"><select value={sessionForm.locationId} onChange={e => setSessionForm({ ...sessionForm, locationId: e.target.value, slotId: '' })}><option value="">Select location</option>{selectableLocations.map(item => <option key={item.id || item.locationId} value={item.id || item.locationId}>{item.name || item.code}</option>)}</select></Field><Field label="Slot"><select value={sessionForm.slotId} onChange={e => setSessionForm({ ...sessionForm, slotId: e.target.value })}><option value="">Select slot</option>{unwrap(sessionForm.locationId && String(sessionForm.locationId) === String(locationId) ? slots.data : []).map(item => <option key={item.id} value={item.id}>{item.label} ({item.startsAtLocal}–{item.endsAtLocal})</option>)}</select></Field><Field label="Workshop date"><input type="date" value={sessionForm.workshopDate} onChange={e => setSessionForm({ ...sessionForm, workshopDate: e.target.value })} /></Field><Field label="Capacity"><input type="number" min="1" value={sessionForm.capacity} onChange={e => setSessionForm({ ...sessionForm, capacity: e.target.value })} /></Field><div className="actions"><button className="button primary" disabled={command.pending}>New session</button></div></form>}</section>{isManager && <BulkSessionForm api={api} locations={selectableLocations} onDone={sessions.reload} />}<section className="ota-panel"><form className="ota-inline-form ops-filter-form" aria-label="Filter sessions" onSubmit={e => { e.preventDefault(); sessions.reload(); }}><label>From<input type="date" value={sessionFilters.from} onChange={e => setSessionFilters({ ...sessionFilters, from: e.target.value })} /></label><label>To<input type="date" value={sessionFilters.to} onChange={e => setSessionFilters({ ...sessionFilters, to: e.target.value })} /></label><label>State<select value={sessionFilters.state} onChange={e => setSessionFilters({ ...sessionFilters, state: e.target.value })}><option value="">All states</option>{SESSION_FILTER_STATES.map(v => <option key={v}>{v}</option>)}</select></label><label>Location<select value={sessionFilters.locationId} onChange={e => setSessionFilters({ ...sessionFilters, locationId: e.target.value })}><option value="">All locations</option>{locationItems.map(item => <option key={item.id || item.locationId} value={item.id || item.locationId}>{item.name || item.code}</option>)}</select></label><button className="button outline" type="button" onClick={() => setSessionFilters(emptySessionFilters)}>Xóa lọc</button><button className="button primary" type="submit">Refresh board</button></form></section><ListState resource={sessions} empty="No sessions match the current scope."><section className="ota-panel"><div className="ota-panel-head"><h2>Session queue</h2><span>{sessionItems.length} records</span></div><div className="ops-table-wrap"><table className="ops-table"><thead><tr><th>Date</th><th>Time</th><th>Session</th><th>Location</th><th>Capacity</th><th>Status</th><th>Transition</th>{isManager && <th>Edit</th>}</tr></thead><tbody>{sessionItems.map(item => <React.Fragment key={item.id}><tr><td>{item.workshopDate || '—'}</td><td><strong>{sessionTimeRange(item) || '—'}</strong></td><td><strong>{item.id}</strong></td><td>{item.locationName || locationItems.find(loc => String(loc.id ?? loc.locationId) === String(item.locationId))?.name || '—'}</td><td>{item.capacity ?? '—'}</td><td><span className="ops-status">{item.state || 'DRAFT'}</span></td><td><div className="actions"><select value={sessionTargetState} onChange={e => setSessionTargetState(e.target.value)} aria-label={`Target state for ${item.id}`}><option value="">Choose state</option><option value="PUBLISHED">PUBLISHED</option><option value="SALES_PAUSED">SALES_PAUSED</option><option value="CANCELLED">CANCELLED</option></select><button className="button outline small" disabled={command.pending || !sessionTargetState || sessionTargetState === item.state} onClick={() => run(key => api.transitionSession(item.id, sessionTargetState, key), 'Session state updated.')}>Apply</button></div></td>{isManager && <td><button type="button" className="button outline small" aria-expanded={editingSessionId === item.id} onClick={() => setEditingSessionId(editingSessionId === item.id ? null : item.id)}>Edit</button></td>}</tr>{isManager && editingSessionId === item.id && <tr><td colSpan={8}><SessionEditForm key={`${item.id}-${item.capacity}-${item.checkInOpensAt}`} api={api} session={item} onSaved={() => { setEditingSessionId(null); setNotice('Session updated.'); sessions.reload(); }} onReload={sessions.reload} onClose={() => setEditingSessionId(null)} /></td></tr>}</React.Fragment>)}</tbody></table></div></section></ListState></>;

  return <div className="ota-workspace"><OperationsSidebar /><main className="ota-main"><div className="ota-topline">MLAND / OPERATIONS <span>● Backend-authorized</span></div><header className="ota-page-heading"><div><p className="ota-eyebrow">MANAGER / WORKSHOPS</p><h1>{tab === 'locations' ? 'Location management' : tab === 'slots' ? 'Slot management' : 'Session administration'}</h1><p>Configure workshop capacity foundations and record operational decisions with evidence.</p></div></header><nav className="ota-nav-tabs" aria-label="Operations administration"><button className={`ota-nav-tab ${tab === 'locations' ? 'active' : ''}`} onClick={() => switchTab('locations')}>Locations</button><button className={`ota-nav-tab ${tab === 'slots' ? 'active' : ''}`} onClick={() => switchTab('slots')}>Slots</button><button className={`ota-nav-tab ${tab === 'sessions' ? 'active' : ''}`} onClick={() => switchTab('sessions')}>Sessions</button></nav><SuccessNotice>{notice}</SuccessNotice><ErrorNotice error={formError ? new Error(formError) : null} /><ErrorNotice error={command.error} onRetry={command.retry} />{tab === 'locations' ? renderLocations() : tab === 'slots' ? renderSlots() : renderSessions()}</main></div>;
}
