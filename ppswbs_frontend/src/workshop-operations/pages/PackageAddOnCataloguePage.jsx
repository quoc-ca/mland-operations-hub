import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { ErrorNotice, Loading, SuccessNotice, useCommand, useResource } from '../../shared/ui/components';
import { OperationsSidebar } from '../../shared/ui/OperationsSidebar';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { createWorkshopPackageApi } from '../api/packageApi';
import { createOperationsAdminApi } from '../api/operationsAdminApi';
import { createWorkshopAddOnApi } from '../api/addOnApi';
import '../workshop-operations.css';
import '../../ota-connectivity/ota-connectivity.css';

const empty = { description: '', priceVnd: '' };
const unwrap = value => (Array.isArray(value) ? value : value?.items || value?.content || []);

export default function PackageAddOnCataloguePage() {
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
        topline="MLAND / WORKSHOP OPERATIONS"
        message="Yêu cầu đăng nhập để quản lý danh mục package add-ons."
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
      setError({ message: 'Vui lòng chọn gói workshop, chi nhánh, nhập mô tả và đơn giá VND hợp lệ.' });
      return;
    }
    const body = { description: form.description.trim(), priceVnd: Number(form.priceVnd) };
    const outcome = await command.run(key =>
      editing
        ? addOnApi.update(packageId, locationId, editing.addOnReference, body, key)
        : addOnApi.create(packageId, locationId, body, key),
    );
    if (outcome.ok) {
      setNotice(editing ? 'Đã cập nhật add-on thành công.' : 'Đã tạo bản nháp add-on. Hãy kích hoạt khi sẵn sàng.');
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
      setNotice(`Add-on đã chuyển sang trạng thái ${targetState}.`);
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
      <OperationsSidebar />

      <main className="ota-main">
        <div className="ota-topline">
          MLAND / WORKSHOP OPERATIONS <span>● Backend-authorized</span>
        </div>
        <header className="ota-page-heading">
          <div>
            <p className="ota-eyebrow">CATALOGUE</p>
            <h1>Package add-on catalogue</h1>
            <p>Quản lý các tùy chọn Add-on gắn kèm theo từng gói Workshop và Chi nhánh vận hành.</p>
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
              <h2>Phạm vi áp dụng (Scope selection)</h2>
              <p className="muted small">Chọn gói workshop và chi nhánh để lọc hoặc tạo mới add-on tương ứng.</p>
            </div>
          </div>
          <div className="ota-form-grid">
            <label>
              Gói Workshop (Package) *
              <select value={packageId} onChange={e => handlePackageChange(e.target.value)}>
                <option value="">-- Chọn gói Workshop --</option>
                {sortedPackages.map(p => {
                  const id = p.packageId ?? p.id;
                  return (
                    <option key={id} value={id}>
                      {p.name || p.packageReference || id} {p.state ? `· ${p.state}` : ''}
                    </option>
                  );
                })}
              </select>
            </label>
            <label>
              Chi nhánh (Location) *
              <select value={locationId} onChange={e => handleLocationChange(e.target.value)}>
                <option value="">-- Chọn chi nhánh --</option>
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
              <h2>{editing ? 'Chỉnh sửa add-on' : 'Tạo add-on mới (Draft)'}</h2>
              <p className="muted small">
                Đơn giá tính theo VND. Khách chỉ có thể chọn add-on khi ở trạng thái <code>ACTIVE</code>.
              </p>
            </div>
          </div>
          <form onSubmit={save}>
            <div className="ota-form-grid">
              <label>
                Mô tả add-on (Description) *
                <input
                  value={form.description}
                  onChange={e => setForm({ ...form, description: e.target.value })}
                  maxLength={200}
                  placeholder="Ví dụ: Đính đá phong thủy cao cấp, Khắc tên laser..."
                  required
                />
              </label>
              <label>
                Đơn giá (VND) *
                <input
                  inputMode="numeric"
                  value={form.priceVnd}
                  onChange={e => setForm({ ...form, priceVnd: e.target.value })}
                  placeholder="Ví dụ: 150000"
                  required
                />
              </label>
            </div>
            <div className="ops-actions" style={{ marginTop: '16px' }}>
              <button className="button primary" type="submit" disabled={command.pending}>
                {editing ? 'Lưu thay đổi' : 'Tạo bản nháp (Draft)'}
              </button>
              {editing && (
                <button type="button" className="button outline" onClick={reset}>
                  Hủy chỉnh sửa
                </button>
              )}
            </div>
          </form>
        </section>

        {/* Add-on Catalogue Entries Table */}
        <section className="ota-panel">
          <div className="ota-panel-head">
            <div>
              <h2>Danh sách Add-on đã cấu hình</h2>
              <p className="muted small">
                {packageId && locationId
                  ? `Đang có ${addOns.length} mục cho gói và chi nhánh đã chọn.`
                  : 'Vui lòng chọn Gói Workshop và Chi nhánh ở trên.'}
              </p>
            </div>
          </div>
          {addOnsResource.loading ? (
            <Loading />
          ) : addOns.length === 0 ? (
            <div className="empty-state">
              <p>Chưa có add-on nào trong phạm vi này.</p>
            </div>
          ) : (
            <div className="ops-table-wrap">
              <table className="ops-table">
                <thead>
                  <tr>
                    <th>Mô tả Add-on</th>
                    <th>Mã tham chiếu</th>
                    <th>Đơn giá</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
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
                        {Number(item.priceVnd || 0).toLocaleString('vi-VN')} {item.currencyCode || 'VND'}
                      </td>
                      <td>
                        <span className="ops-status">{item.state}</span>
                      </td>
                      <td>
                        <div className="ops-actions" style={{ margin: 0 }}>
                          <button
                            className="button outline small"
                            onClick={() => startEdit(item)}
                            disabled={item.state === 'RETIRED'}
                          >
                            Chỉnh sửa
                          </button>
                          {item.state === 'DRAFT' && (
                            <button
                              className="button primary small"
                              onClick={() => transition(item, 'ACTIVE')}
                              disabled={command.pending}
                            >
                              Kích hoạt
                            </button>
                          )}
                          {item.state === 'ACTIVE' && (
                            <button
                              className="button outline small"
                              onClick={() => transition(item, 'RETIRED')}
                              disabled={command.pending}
                            >
                              Ngừng bán
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
