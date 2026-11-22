import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { registerPrivateStateCleanup } from '../../shared/auth/privateStateCleanup';
import { newCommandId } from '../../shared/api/http';
import { useHttp } from '../../shared/api/useHttp';
import { ErrorNotice, Loading, useResource } from '../../shared/ui/components';
import OperationsAuthWorkspace from '../../shared/ui/OperationsAuthWorkspace';
import { createPackageAvailabilityApi } from '../api/packageAvailabilityApi';
import PackageAvailabilityMatrix, { cellKey } from '../components/PackageAvailabilityMatrix';
import '../../ota-connectivity/ota-connectivity.css';
import '../workshop-operations.css';

const SUCCESS_VISIBLE_MS = 4000;

const replaceCell = (cells, next) => {
  const key = cellKey(next.packageId, next.locationId);
  const kept = cells.filter(cell => cellKey(cell.packageId, cell.locationId) !== key);
  return [...kept, next];
};

function DeniedPanel() {
  return (
    <section className="ota-panel pam-denied">
      <p className="pam-denied-code">403</p>
      <h2>Bạn không có quyền xem trang này</h2>
      <p>Chỉ Manager đang hoạt động mới xem và chỉnh được ma trận gói × chi nhánh. Liên hệ Manager nếu bạn cần thay đổi.</p>
      <Link className="button primary" to="/">Về trang chính</Link>
    </section>
  );
}

function Shell({ children }) {
  return (
    <div className="ota-workspace">
      
      <main className="ota-main">
        <div className="ota-topline">MLAND / WORKSHOP OPERATIONS <span>● Backend-authorized</span></div>
        {children}
      </main>
    </div>
  );
}

export default function PackageAvailabilityMatrixPage() {
  const { status } = useAuth();
  const http = useHttp();
  const api = useMemo(() => createPackageAvailabilityApi(http), [http]);
  const signedIn = status === 'signed-in';
  const resource = useResource(() => (signedIn ? api.readMatrix() : Promise.resolve(null)), [signedIn]);
  const [denied, setDenied] = useState(false);
  const [saving, setSaving] = useState({});
  const [cellErrors, setCellErrors] = useState({});
  const [banner, setBanner] = useState(null);
  const savingRef = useRef({});
  const dataRef = useRef(null);
  dataRef.current = resource.data;
  const setData = resource.setData;

  const clearAll = useCallback(() => {
    dataRef.current = null;
    setData(null);
    savingRef.current = {};
    setSaving({});
    setCellErrors({});
    setBanner(null);
  }, [setData]);

  // Session loss / sign-out: drop the matrix from memory (shared cleanup registry).
  useEffect(() => registerPrivateStateCleanup(clearAll), [clearAll]);
  useEffect(() => { if (!signedIn) clearAll(); }, [signedIn, clearAll]);

  // A 403 on read: drop whatever matrix was held.
  const readDenied = resource.error?.status === 403;
  useEffect(() => { if (readDenied) clearAll(); }, [readDenied, clearAll]);

  useEffect(() => {
    if (banner?.kind !== 'success') return undefined;
    const timer = setTimeout(() => setBanner(current => (current === banner ? null : current)), SUCCESS_VISIBLE_MS);
    return () => clearTimeout(timer);
  }, [banner]);

  const updateCells = useCallback(change => {
    const current = dataRef.current;
    if (!current) return;
    const next = { ...current, cells: change(current.cells || []) };
    dataRef.current = next;
    setData(next);
  }, [setData]);

  const matrix = resource.data;
  const names = (packageId, locationId) => {
    const pkg = matrix?.packages?.find(p => p.id === packageId);
    const location = matrix?.locations?.find(l => l.id === locationId);
    return `${pkg?.name ?? packageId} tại ${location?.name ?? locationId}`;
  };

  const send = useCallback(async (packageId, locationId, state, version, idempotencyKey, label) => {
    const key = cellKey(packageId, locationId);
    savingRef.current = { ...savingRef.current, [key]: true };
    setSaving(savingRef.current);
    setCellErrors(({ [key]: _gone, ...rest }) => rest);
    setBanner(null);
    try {
      const result = await api.setAvailability(packageId, locationId, state, version, idempotencyKey);
      updateCells(cells => replaceCell(cells, {
        packageId, locationId, state: result.state, version: result.version,
      }));
      setBanner({ kind: 'success', title: result.state === 'ACTIVE' ? 'Đã bật bán' : 'Đã tắt bán', label });
    } catch (error) {
      if (error?.status === 403) {
        clearAll();
        setDenied(true);
        return;
      }
      if (error?.code === 'OPTIMISTIC_LOCK_CONFLICT') {
        setCellErrors(current => ({ ...current, [key]: 'conflict' }));
        try {
          const fresh = await api.readMatrix();
          const cell = fresh?.cells?.find(c => cellKey(c.packageId, c.locationId) === key)
            || { packageId, locationId, state: 'INACTIVE', version: 0 };
          updateCells(cells => replaceCell(cells, cell));
        } catch {
          // The cell keeps its previous value; "Tải lại ma trận" reads again.
        }
        setBanner({ kind: 'conflict', label });
      } else {
        setCellErrors(current => ({ ...current, [key]: 'error' }));
        setBanner({
          kind: 'error',
          label,
          correlationId: error?.correlationId || null,
          retry: error?.retryable ? { packageId, locationId, state, version, idempotencyKey, label } : null,
        });
      }
    } finally {
      const { [key]: _done, ...rest } = savingRef.current;
      savingRef.current = rest;
      setSaving(rest);
    }
  }, [api, clearAll, updateCells]);

  const onToggle = (packageId, locationId, nextState) => {
    const key = cellKey(packageId, locationId);
    if (savingRef.current[key]) return;
    const cell = dataRef.current?.cells?.find(c => cellKey(c.packageId, c.locationId) === key);
    send(packageId, locationId, nextState, cell?.version ?? 0, newCommandId(), names(packageId, locationId));
  };

  const reloadAll = () => {
    setCellErrors({});
    setBanner(null);
    resource.reload();
  };

  if (status === 'loading') return <Loading />;
  if (!signedIn) {
    return (
      <OperationsAuthWorkspace
        topline="MLAND / WORKSHOP OPERATIONS"
        message="Yêu cầu đăng nhập để quản lý ma trận gói × chi nhánh."
      />
    );
  }

  const heading = (
    <header className="ota-page-heading">
      <div>
        <p>Chọn gói workshop được bán ở từng chi nhánh. Tắt một ô chỉ ngừng nhận đặt mới, không hủy đơn đã đặt.</p>
      </div>
    </header>
  );

  if (denied || readDenied) {
    return <Shell>{heading}<DeniedPanel /></Shell>;
  }

  const packages = matrix?.packages || [];
  const locations = matrix?.locations || [];
  const empty = matrix && (packages.length === 0 || locations.length === 0);
  const savingKeys = Object.keys(saving);
  const savingLabel = savingKeys.length
    ? names(...savingKeys[0].split(':').map(Number))
    : '';

  let body;
  if (resource.loading && !matrix) {
    body = (
      <div className="pam-skeleton" aria-busy="true">
        <span className="sr-only">Đang tải ma trận…</span>
        <span /><span /><span /><span />
      </div>
    );
  } else if (!matrix) {
    body = null;
  } else if (empty) {
    body = (
      <div className="pam-empty">
        <h3>Chưa có gói hoặc chi nhánh để thiết lập</h3>
        <p>Hãy tạo gói workshop và chi nhánh trước, sau đó quay lại trang này để chọn gói bán ở từng chi nhánh.</p>
        <div className="ops-actions">
          <Link className="button primary" to="/operations/packages">Đến Gói workshop</Link>
          <button type="button" className="button outline" onClick={reloadAll}>Tải lại</button>
        </div>
      </div>
    );
  } else {
    body = (
      <>
        <PackageAvailabilityMatrix
          packages={packages}
          locations={locations}
          cells={matrix.cells || []}
          onToggle={onToggle}
          savingCells={saving}
          cellErrors={cellErrors}
        />
        <p className="pam-note">Ô được tick = gói đang bán (ACTIVE) tại chi nhánh đó. Tắt ô chỉ ngừng nhận đặt mới; đơn đã đặt vẫn giữ nguyên.</p>
      </>
    );
  }

  return (
    <Shell>
      {heading}
      <section className="ota-panel">
        <div className="ota-panel-head">
          <div><h2>Gói đang bán theo chi nhánh</h2></div>
          <button type="button" className="button outline small" onClick={reloadAll}>Tải lại</button>
        </div>

        {resource.error && !readDenied && <ErrorNotice error={resource.error} onRetry={resource.reload} />}

        {banner?.kind === 'success' && (
          <div className="pam-banner is-success" role="status">
            <div><strong>{banner.title}</strong>{banner.label}. Thông báo tự ẩn sau vài giây.</div>
          </div>
        )}
        {banner?.kind === 'conflict' && (
          <div className="pam-banner is-conflict" role="status">
            <div>
              <strong>Ô vừa được thay đổi ở nơi khác</strong>
              Đã tải lại giá trị mới nhất của {banner.label}. Hãy kiểm tra rồi thao tác lại nếu cần.
            </div>
            <button type="button" className="button outline small" onClick={reloadAll}>Tải lại ma trận</button>
          </div>
        )}
        {banner?.kind === 'error' && (
          <div className="pam-banner is-error" role="alert">
            <div>
              <strong>Không lưu được thay đổi</strong>
              {banner.label} giữ nguyên giá trị cũ. Vui lòng thử lại.
              {banner.correlationId && <span className="pam-ref"> Mã tham chiếu: {banner.correlationId}</span>}
            </div>
            <div className="ops-actions">
              {banner.retry && (
                <button
                  type="button"
                  className="button outline small"
                  onClick={() => send(
                    banner.retry.packageId, banner.retry.locationId, banner.retry.state,
                    banner.retry.version, banner.retry.idempotencyKey, banner.retry.label,
                  )}
                >
                  Thử lại
                </button>
              )}
              <button type="button" className="button outline small" onClick={reloadAll}>Tải lại ma trận</button>
            </div>
          </div>
        )}
        {!banner && savingKeys.length > 0 && (
          <div className="pam-banner is-saving" role="status">
            <div>
              <strong>Đang lưu…</strong>
              {savingLabel}. Ô đang lưu bị vô hiệu, các ô khác vẫn dùng được.
            </div>
          </div>
        )}

        {body}
      </section>
    </Shell>
  );
}
