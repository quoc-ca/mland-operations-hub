import React, { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useCurrentAccount } from '../hooks/useCurrentAccount';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useHttp } from '../../../shared/api/useHttp';
import { useT } from '../../../shared/i18n/I18nProvider';
import { accountMessages } from '../messages';
import '../accountmanagement.css';

type Shipment = { status?: string; createdAt?: string };
type TodayCounts = { today: number; waitingPickup: number; delivering: number };

const VN_OFFSET_MS = 7 * 3_600_000;
const vnDay = (value: string | number) => new Date(new Date(value).getTime() + VN_OFFSET_MS).toISOString().slice(0, 10);

/** Today's GHTK work for the carrier home page (FE-08, Session 2026-11-23): new shipments today (Vietnam time),
 * shipments waiting to be picked up and shipments on the way. */
export function carrierTodayCounts(items: Shipment[], now = Date.now()): TodayCounts {
  const today = vnDay(now);
  return {
    today: items.filter(item => item.createdAt && vnDay(item.createdAt) === today).length,
    waitingPickup: items.filter(item => item.status === 'READY_FOR_CARRIER').length,
    delivering: items.filter(item => item.status === 'HANDED_TO_CARRIER' || item.status === 'IN_TRANSIT').length,
  };
}

function useCarrierToday(enabled: boolean): TodayCounts | null {
  const request = useHttp() as unknown as (path: string, options?: { query?: Record<string, unknown> }) => Promise<{ items?: Shipment[]; totalPages?: number }>;
  const [counts, setCounts] = useState<TodayCounts | null>(null);
  useEffect(() => {
    if (!enabled) return undefined;
    let live = true;
    (async () => {
      const items: Shipment[] = [];
      for (let page = 0; page < 10; page += 1) {
        const result = await request('/api/v1/carrier/shipments', { query: { page, size: 200 } }); // eslint-disable-line no-await-in-loop
        items.push(...(result?.items || []));
        if (!result || page + 1 >= (result.totalPages || 0)) break;
      }
      if (live) setCounts(carrierTodayCounts(items));
    })().catch(() => { /* no notice when the shipments cannot be read */ });
    return () => { live = false; };
  }, [enabled, request]);
  return counts;
}

export const CarrierPortalPage: React.FC = () => {
  const { account, status: accountStatus, error } = useCurrentAccount();
  const { signOut } = useAuth();
  const t = useT(accountMessages);
  const today = useCarrierToday(account?.role === 'CARRIER' && account?.status !== 'SUSPENDED');

  if (accountStatus === 'loading') {
    return (
      <div className="account-page-container">
        <div className="account-card">
          <p style={{ color: '#6b717f' }}>{t('carrier.checking')}</p>
        </div>
      </div>
    );
  }

  // Access defense (FR-003, FR-026, FR-027): Ensure role is CARRIER (or ADMIN managing carrier)
  if (account && account.role !== 'CARRIER' && account.role !== 'ADMIN') {
    return (
      <div className="account-page-container">
        <div className="account-alert account-alert-error" role="alert">
          <span>{t('carrier.noAccess')}</span>
        </div>
        <div className="account-card" style={{ marginTop: '1rem', textAlign: 'center', padding: '2rem' }}>
          <Link to="/" className="account-btn account-btn-primary">
            {t('carrier.backHome')}
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="account-page-container">
      <header className="account-page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          <h1 className="account-page-title" style={{ margin: 0 }}>
            {t('carrier.title')}
          </h1>
          <span className="account-badge account-badge-active">
            CARRIER · {account?.status || 'ACTIVE'}
          </span>
        </div>
        <p className="account-page-description" style={{ marginTop: '0.5rem' }}>
          {t('carrier.description')}
        </p>
      </header>

      {error && (
        <div className="account-alert account-alert-error" role="alert">
          <span>{error.message || t('carrier.loadError')}</span>
        </div>
      )}

      {account?.status === 'SUSPENDED' && (
        <div className="account-alert account-alert-error" role="alert">
          <strong>{t('carrier.suspended')}</strong> {t('carrier.contactAdmin')}
        </div>
      )}

      <div className="account-card" style={{ marginBottom: '1.5rem' }}>
        <h2 className="account-card-title">{t('carrier.identity')}</h2>
        <table className="account-table">
          <tbody>
            <tr>
              <th style={{ width: '220px' }}>{t('carrier.partnerName')}</th>
              <td><strong>{account?.email?.split('@')[0] || t('carrier.partnerDefault')}</strong></td>
            </tr>
            <tr>
              <th>{t('carrier.signInEmail')}</th>
              <td>{account?.email || 'ghtk.test@mland.vn'}</td>
            </tr>
            <tr>
              <th>{t('carrier.systemRole')}</th>
              <td>
                <span className="account-badge account-badge-readonly">CARRIER</span>
              </td>
            </tr>
            <tr>
              <th>{t('carrier.branchScope')}</th>
              <td>{t('carrier.nationwide')}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1.5rem' }}>
        <div className="account-card" style={{ display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1.1rem', color: '#1a1a1a' }}>
              {t('carrier.shipmentsTitle')}
            </h3>
            <p style={{ color: '#4b5563', fontSize: '0.9rem', lineHeight: 1.5, margin: 0 }}>
              {t('carrier.shipmentsBody')}
            </p>
            {today && (
              <div className="carrier-today" role="status">
                <strong>{t('carrier.todayNew', { count: today.today })}</strong>
                <span>{t('carrier.todayDetail', { waiting: today.waitingPickup, delivering: today.delivering })}</span>
              </div>
            )}
          </div>
          <div style={{ marginTop: '1.25rem' }}>
            <Link
              to="/carrier/orders"
              className="account-btn account-btn-primary"
              style={{ display: 'inline-block' }}
            >
              {t('carrier.viewShipments')}
            </Link>
          </div>
        </div>

        <div className="account-card" style={{ display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1.1rem', color: '#1a1a1a' }}>
              {t('carrier.securityTitle')}
            </h3>
            <p style={{ color: '#4b5563', fontSize: '0.9rem', lineHeight: 1.5, margin: 0 }}>
              {t('carrier.securityBody')}
            </p>
          </div>
          <div style={{ marginTop: '1.25rem' }}>
            <Link
              to="/security/password"
              className="account-btn account-btn-secondary"
              style={{ display: 'inline-block' }}
            >
              {t('carrier.changePassword')}
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CarrierPortalPage;
