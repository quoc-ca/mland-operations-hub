import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { registerPrivateStateCleanup } from '../../shared/auth/privateStateCleanup';
import { newCommandId } from '../../shared/api/http';
import { useHttp } from '../../shared/api/useHttp';
import { useT } from '../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../shared/ui/messages';
import { messages } from '../messages';
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
  const t = useT(messages);
  return (
    <section className="ota-panel pam-denied">
      <p className="pam-denied-code">403</p>
      <h2>{t('pam.deniedTitle')}</h2>
      <p>{t('pam.deniedBody')}</p>
      <Link className="button primary" to="/">{t('pam.home')}</Link>
    </section>
  );
}

function Shell({ children }) {
  const t = useT(messages);
  return (
    <div className="ota-workspace">
      
      <main className="ota-main">
        <div className="ota-topline">{t('topline.operations')} <span>● {t('authorized')}</span></div>
        {children}
      </main>
    </div>
  );
}

export default function PackageAvailabilityMatrixPage() {
  const t = useT(messages);
  const ts = useT(sharedMessages);
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
    return t('pam.cell', { package: pkg?.name ?? packageId, location: location?.name ?? locationId });
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
      setBanner({ kind: 'success', title: result.state === 'ACTIVE' ? t('pam.on') : t('pam.off'), label });
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
  }, [api, clearAll, updateCells, t]);

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
        topline={t('topline.operations')}
        message={t('pam.signIn')}
      />
    );
  }

  const heading = (
    <header className="ota-page-heading">
      <div>
        <p>{t('pam.intro')}</p>
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
        <span className="sr-only">{t('pam.loading')}</span>
        <span /><span /><span /><span />
      </div>
    );
  } else if (!matrix) {
    body = null;
  } else if (empty) {
    body = (
      <div className="pam-empty">
        <h3>{t('pam.emptyTitle')}</h3>
        <p>{t('pam.emptyBody')}</p>
        <div className="ops-actions">
          <Link className="button primary" to="/operations/packages">{t('pam.toPackages')}</Link>
          <button type="button" className="button outline" onClick={reloadAll}>{t('pam.reload')}</button>
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
        <p className="pam-note">{t('pam.note')}</p>
      </>
    );
  }

  return (
    <Shell>
      {heading}
      <section className="ota-panel">
        <div className="ota-panel-head">
          <div><h2>{t('pam.title')}</h2></div>
          <button type="button" className="button outline small" onClick={reloadAll}>{t('pam.reload')}</button>
        </div>

        {resource.error && !readDenied && <ErrorNotice error={resource.error} onRetry={resource.reload} />}

        {banner?.kind === 'success' && (
          <div className="pam-banner is-success" role="status">
            <div><strong>{banner.title}</strong>{t('pam.successTail', { label: banner.label })}</div>
          </div>
        )}
        {banner?.kind === 'conflict' && (
          <div className="pam-banner is-conflict" role="status">
            <div>
              <strong>{t('pam.conflictTitle')}</strong>
              {t('pam.conflictBody', { label: banner.label })}
            </div>
            <button type="button" className="button outline small" onClick={reloadAll}>{t('pam.reloadMatrix')}</button>
          </div>
        )}
        {banner?.kind === 'error' && (
          <div className="pam-banner is-error" role="alert">
            <div>
              <strong>{t('pam.errorTitle')}</strong>
              {t('pam.errorBody', { label: banner.label })}
              {banner.correlationId && <span className="pam-ref">{t('pam.errorRef', { id: banner.correlationId })}</span>}
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
                  {ts('retry')}
                </button>
              )}
              <button type="button" className="button outline small" onClick={reloadAll}>{t('pam.reloadMatrix')}</button>
            </div>
          </div>
        )}
        {!banner && savingKeys.length > 0 && (
          <div className="pam-banner is-saving" role="status">
            <div>
              <strong>{t('pam.savingTitle')}</strong>
              {t('pam.savingBody', { label: savingLabel })}
            </div>
          </div>
        )}

        {body}
      </section>
    </Shell>
  );
}
