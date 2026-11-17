import { useEffect, useState } from 'react';

const MOBILE_QUERY = '(max-width: 767.98px)';

export const cellKey = (packageId, locationId) => `${packageId}:${locationId}`;

function useIsMobile() {
  const read = () => (typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia(MOBILE_QUERY).matches
    : false);
  const [mobile, setMobile] = useState(read);
  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return undefined;
    const query = window.matchMedia(MOBILE_QUERY);
    const onChange = () => setMobile(query.matches);
    onChange();
    query.addEventListener?.('change', onChange);
    return () => query.removeEventListener?.('change', onChange);
  }, []);
  return mobile;
}

function LocationBadge({ active }) {
  return (
    <span className={`pam-badge ${active ? 'is-active' : 'is-inactive'}`}>
      {active ? 'Đang hoạt động' : 'Chưa hoạt động'}
    </span>
  );
}

function CellBox({ pkg, location, cell, saving, error, onToggle }) {
  const ticked = cell?.state === 'ACTIVE';
  const label = `${pkg.name} tại ${location.name}`;
  const toggle = () => {
    if (saving) return;
    onToggle(pkg.id, location.id, ticked ? 'INACTIVE' : 'ACTIVE');
  };
  return (
    <span
      className={`pam-cell${saving ? ' is-saving' : ''}${error ? ` has-${error}` : ''}`}
      data-error={error || undefined}
    >
      <input
        type="checkbox"
        checked={ticked}
        aria-label={label}
        aria-disabled={saving ? 'true' : undefined}
        aria-invalid={error ? 'true' : undefined}
        onClick={event => { if (saving) event.preventDefault(); }}
        onChange={toggle}
      />
      {saving && (
        <span className="pam-saving">
          <span className="pam-spinner" aria-hidden="true" />
          <span>Đang lưu</span>
        </span>
      )}
    </span>
  );
}

/**
 * Package x location matrix. A pair missing from `cells` is shown unticked with version 0.
 * Inactive locations stay visible and their cells stay tickable (approved decision 2026-11-17).
 */
export default function PackageAvailabilityMatrix({
  packages, locations, cells, onToggle, savingCells = {}, cellErrors = {}, layout,
}) {
  const detected = useIsMobile();
  const mobile = layout ? layout === 'cards' : detected;
  const byKey = new Map(cells.map(cell => [cellKey(cell.packageId, cell.locationId), cell]));
  const box = (pkg, location) => {
    const key = cellKey(pkg.id, location.id);
    return (
      <CellBox
        pkg={pkg}
        location={location}
        cell={byKey.get(key)}
        saving={Boolean(savingCells[key])}
        error={cellErrors[key]}
        onToggle={onToggle}
      />
    );
  };

  if (mobile) {
    return (
      <div className="pam-cards">
        {locations.map(location => (
          <section className="pam-card" key={location.id} aria-label={location.name}>
            <header>
              <h3>{location.name}</h3>
              <LocationBadge active={location.active} />
            </header>
            <ul>
              {packages.map(pkg => (
                <li key={pkg.id}>
                  <span>{pkg.name}</span>
                  {box(pkg, location)}
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>
    );
  }

  return (
    <div className="pam-table-wrap">
      <table className="pam-table">
        <thead>
          <tr>
            <th scope="col">Chi nhánh \ Gói</th>
            {packages.map(pkg => <th scope="col" key={pkg.id}>{pkg.name}</th>)}
          </tr>
        </thead>
        <tbody>
          {locations.map(location => (
            <tr key={location.id}>
              <th scope="row">
                <span className="pam-location-name">{location.name}</span>
                <LocationBadge active={location.active} />
              </th>
              {packages.map(pkg => <td key={pkg.id}>{box(pkg, location)}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
