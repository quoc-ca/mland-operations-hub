import React, { useState, useEffect } from 'react';
import { Link } from 'react-router';
import {
  accountManagementApiClient,
  RoleCatalogueEntry,
} from '../api/AccountManagementApiClient';
import { useT } from '../../../shared/i18n/I18nProvider';
import { accountMessages } from '../messages';
import '../accountmanagement.css';

export const RoleCataloguePage: React.FC = () => {
  const t = useT(accountMessages);
  const [roles, setRoles] = useState<RoleCatalogueEntry[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    const fetchRoles = async () => {
      setLoading(true);
      setError(null);
      try {
        const resp = await accountManagementApiClient.listRoles();
        if (active) {
          setRoles(resp.roles);
        }
      } catch (err: any) {
        if (active) {
          setError(err?.message || t('roles.loadError'));
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    fetchRoles();
    return () => {
      active = false;
    };
  }, [t]);

  return (
    <div className="account-page-container">
      {/* Back button matching Figma */}
      <div>
        <Link to="/admin/accounts" className="account-back-btn">
          {t('roles.back')}
        </Link>
      </div>

      {/* Admin Navigation Tabs (Figma F08-F11) */}
      <nav className="admin-nav-tabs" aria-label={t('roles.navAria')}>
        <Link to="/admin/accounts" className="admin-nav-tab">
          {t('roles.navAccounts')}
        </Link>
        <Link to="/admin/roles" className="admin-nav-tab active">
          {t('roles.navRoles')}
        </Link>
      </nav>

      <header className="account-page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          <h1 className="account-page-title" style={{ margin: 0 }}>{t('roles.title')}</h1>
          <span className="account-badge account-badge-readonly">
            {t('roles.readonlyBadge')}
          </span>
        </div>
        <p className="account-page-description" style={{ marginTop: '0.5rem' }}>
          {t('roles.description')}
        </p>
      </header>

      {error && (
        <div className="account-alert account-alert-error" role="alert">
          <span>{error}</span>
        </div>
      )}

      {loading ? (
        <div className="account-card">
          <p style={{ color: '#6b717f' }}>{t('roles.loading')}</p>
        </div>
      ) : (
        <div className="account-card">
          <table className="account-table">
            <thead>
              <tr>
                <th style={{ width: '180px' }}>{t('roles.colRole')}</th>
                <th>{t('roles.colDescription')}</th>
              </tr>
            </thead>
            <tbody>
              {roles.map(r => {
                const roleCode = r.code || r.role;
                const roleDesc = r.displayName || r.description;
                return (
                  <tr key={roleCode}>
                    <td>
                      <span className="account-badge account-badge-readonly" style={{ fontSize: '0.85rem' }}>
                        {roleCode}
                      </span>
                    </td>
                    <td style={{ lineHeight: 1.6 }}>{roleDesc}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>

          <div style={{ marginTop: '1.5rem', padding: '1rem', backgroundColor: '#f4f5f7', borderRadius: '4px', border: '1px solid #eceef1' }}>
            <p style={{ margin: '0 0 0.5rem 0', fontSize: '0.9rem', fontWeight: 600, color: '#1a1a1a' }}>
              {t('roles.rulesTitle')}
            </p>
            <ul style={{ margin: 0, paddingLeft: '1.25rem', fontSize: '0.85rem', color: '#6b717f', lineHeight: 1.6 }}>
              <li>{t('roles.rule1')}</li>
              <li>{t('roles.rule2')}</li>
              <li>{t('roles.rule3')}</li>
              <li>{t('roles.rule4')}</li>
              <li>{t('roles.rule5')}</li>
              <li>{t('roles.rule6')}</li>
            </ul>
            <p style={{ margin: '0.75rem 0 0 0', fontSize: '0.85rem', color: '#1a1a1a', fontWeight: 500 }}>
              {t('roles.tip')}
            </p>
          </div>
        </div>
      )}
    </div>
  );
};

export default RoleCataloguePage;
