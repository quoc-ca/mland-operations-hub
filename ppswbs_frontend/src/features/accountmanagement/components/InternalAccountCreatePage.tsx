import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useAdminAccounts } from '../hooks/useAdminAccounts';
import { useWorkshopLocations } from '../hooks/useWorkshopLocations';
import { newCommandId } from '../../../shared/api/http';
import { useT } from '../../../shared/i18n/I18nProvider';
import { accountMessages } from '../messages';
import '../accountmanagement.css';

export const InternalAccountCreatePage: React.FC = () => {
  const t = useT(accountMessages);
  const navigate = useNavigate();
  const { createInternalAccount, loading, error } = useAdminAccounts();
  const { locations, error: locationsError } = useWorkshopLocations();
  // Only active workshop locations can receive a new assignment
  const availableLocations = locations.filter(loc => loc.active);

  const [role, setRole] = useState<'STAFF' | 'MANAGER' | 'CARRIER'>('STAFF');
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [selectedLocations, setSelectedLocations] = useState<number[]>([]);
  const [sendNotification, setSendNotification] = useState<boolean>(true);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [createdSummary, setCreatedSummary] = useState<any | null>(null);

  // Preserve idempotency key across retries for the same submit action
  const [idempotencyKey, setIdempotencyKey] = useState<string>(() => newCommandId());

  // Preselect the first active location once the real list has loaded
  useEffect(() => {
    if (selectedLocations.length === 0 && availableLocations.length > 0) {
      setSelectedLocations([availableLocations[0].id]);
    }
  }, [availableLocations, selectedLocations.length]);

  const handleLocationToggle = (locationId: number) => {
    if (role === 'STAFF') {
      // Staff must have exactly 1 location
      setSelectedLocations([locationId]);
    } else {
      // Manager can have 1 or more
      if (selectedLocations.includes(locationId)) {
        if (selectedLocations.length > 1) {
          setSelectedLocations(selectedLocations.filter(id => id !== locationId));
        }
      } else {
        setSelectedLocations([...selectedLocations, locationId]);
      }
    }
  };

  const validate = () => {
    const errors: Record<string, string> = {};
    const trimmedName = fullName.trim();

    if (!trimmedName) {
      errors.fullName = t('create.val.nameRequired');
    } else if (trimmedName.length > 100) {
      errors.fullName = t('create.val.nameLength');
    }

    if (role === 'CARRIER') {
      const trimmedEmail = email.trim();
      if (!trimmedEmail) {
        errors.email = t('create.val.carrierEmailRequired');
      } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
        errors.email = t('create.val.emailFormat');
      }

      if (!password) {
        errors.password = t('create.val.carrierPasswordRequired');
      } else if (password.length < 6) {
        errors.password = t('create.val.passwordLength');
      }

      const trimmedPhone = phone.trim();
      if (!trimmedPhone) {
        errors.phone = t('create.val.carrierPhoneRequired');
      } else if (trimmedPhone.length < 7 || trimmedPhone.length > 30) {
        errors.phone = t('create.val.phoneLength');
      }
    } else {
      if (phone && phone.trim()) {
        const trimmedPhone = phone.trim();
        if (trimmedPhone.length < 7 || trimmedPhone.length > 30) {
          errors.phone = t('create.val.phoneLength');
        }
      }

      if (selectedLocations.length === 0) {
        errors.locations = t('create.val.locationRequired');
      } else if (role === 'STAFF' && selectedLocations.length !== 1) {
        errors.locations = t('create.val.staffOneLocation');
      }
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;

    const payload: any = {
      role,
      fullName: fullName.trim(),
      phone: phone.trim() || undefined,
    };

    if (role === 'CARRIER') {
      payload.email = email.trim();
      payload.password = password;
      // locationIds must be omitted for CARRIER (FR-011, FR-012)
    } else {
      payload.locationIds = selectedLocations;
    }

    // Ephemeral write-only password handling: clear input from memory
    setPassword('');

    const result = await createInternalAccount(payload, idempotencyKey);

    if (result) {
      setCreatedSummary(result);
    }
  };

  const handleResetForNew = () => {
    setFullName('');
    setPhone('');
    setEmail('');
    setPassword('');
    setSelectedLocations(availableLocations.length > 0 ? [availableLocations[0].id] : []);
    setRole('STAFF');
    setFieldErrors({});
    setCreatedSummary(null);
    setIdempotencyKey(newCommandId());
  };

  return (
    <div className="account-page-container">
      {/* Back button matching Figma */}
      <div>
        <Link to="/admin/accounts" className="account-back-btn">
          {t('create.back')}
        </Link>
      </div>

      {/* Admin Navigation Tabs (Figma F08-F11) */}
      <nav className="admin-nav-tabs" aria-label={t('create.navAria')}>
        <Link to="/admin/accounts" className="admin-nav-tab active">
          {t('create.navAccounts')}
        </Link>
        <Link to="/admin/roles" className="admin-nav-tab">
          {t('create.navRoles')}
        </Link>
      </nav>

      <header className="account-page-header">
        <h1 className="account-page-title">{t('create.title')}</h1>
        <p className="account-page-description">
          {t('create.description')}
        </p>
      </header>

      {createdSummary ? (
        <section className="account-card" aria-labelledby="created-title">
          <h2 id="created-title" className="account-card-title" style={{ color: '#15803d' }}>
            {t('create.successTitle')}
          </h2>

          <div className="account-alert account-alert-info">
            <div>
              <strong>
                {t('create.statusLabel')}{' '}
                {createdSummary.status === 'ACTIVE'
                  ? t('create.statusActive')
                  : t('create.statusPending')}
              </strong>
              <p style={{ margin: '0.25rem 0' }}>
                {createdSummary.role === 'CARRIER'
                  ? t('create.carrierDone')
                  : t('create.internalDone')}
              </p>
              <p style={{ margin: '0.25rem 0', fontSize: '0.85rem', color: '#4b5563' }}>
                {createdSummary.role === 'CARRIER'
                  ? t('create.carrierNote')
                  : t('create.internalNote')}
              </p>
            </div>
          </div>

          <table className="account-table" style={{ marginBottom: '1.5rem' }}>
            <tbody>
              <tr>
                <th style={{ width: '200px' }}>{t('create.accountId')}</th>
                <td>{createdSummary.accountId}</td>
              </tr>
              <tr>
                <th>{t('create.role')}</th>
                <td>
                  <span className="account-badge account-badge-readonly">{createdSummary.role}</span>
                </td>
              </tr>
              <tr>
                <th>{t('create.identityEmail')}</th>
                <td>{createdSummary.loginEmail}</td>
              </tr>
              <tr>
                <th>{t('create.accountStatus')}</th>
                <td>
                  <span
                    className={
                      createdSummary.status === 'ACTIVE'
                        ? 'account-badge account-badge-active'
                        : 'account-badge account-badge-pending'
                    }
                  >
                    {createdSummary.status}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>

          <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
            <Link
              to={`/admin/accounts/${createdSummary.accountId}`}
              className="account-btn account-btn-primary"
            >
              {t('create.viewDetail')}
            </Link>
            <button
              type="button"
              className="account-btn account-btn-secondary"
              onClick={handleResetForNew}
            >
              {t('create.another')}
            </button>
            <Link to="/admin/accounts" className="account-btn account-btn-secondary">
              {t('create.list')}
            </Link>
          </div>
        </section>
      ) : (
        <div className="account-card">
          {error && (
            <div className="account-alert account-alert-error" role="alert">
              <span>{error.message || t('create.error')}</span>
            </div>
          )}

          <form className="account-form" onSubmit={handleSubmit} noValidate>
            <h3 style={{ fontSize: '1.05rem', fontWeight: 600, margin: '0 0 0.5rem 0', color: '#1a1a1a', borderBottom: '1px solid #eceef1', paddingBottom: '0.5rem' }}>
              {t('create.sectionPersonal')}
            </h3>

            {role === 'CARRIER' && (
              <>
                <div className="account-field">
                  <label className="account-label account-label-required" htmlFor="carrier-email">
                    {t('create.carrierEmail')}
                  </label>
                  <input
                    id="carrier-email"
                    type="email"
                    className="account-input"
                    placeholder={t('create.carrierEmailPlaceholder')}
                    value={email}
                    onChange={e => setEmail(e.target.value)}
                    aria-invalid={Boolean(fieldErrors.email)}
                    disabled={loading}
                  />
                  {fieldErrors.email && (
                    <span className="account-field-error" role="alert">{fieldErrors.email}</span>
                  )}
                </div>

                <div className="account-field">
                  <label className="account-label account-label-required" htmlFor="carrier-password">
                    {t('create.initialPassword')}
                  </label>
                  <input
                    id="carrier-password"
                    type="password"
                    className="account-input"
                    placeholder={t('create.passwordPlaceholder')}
                    value={password}
                    onChange={e => setPassword(e.target.value)}
                    aria-invalid={Boolean(fieldErrors.password)}
                    disabled={loading}
                  />
                  <span className="account-field-hint">
                    {t('create.passwordHint')}
                  </span>
                  {fieldErrors.password && (
                    <span className="account-field-error" role="alert">{fieldErrors.password}</span>
                  )}
                </div>
              </>
            )}

            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="internal-name">
                {role === 'CARRIER' ? t('create.nameCarrier') : t('create.nameStaff')}
              </label>
              <input
                id="internal-name"
                type="text"
                className="account-input"
                placeholder={role === 'CARRIER' ? t('create.namePlaceholderCarrier') : t('create.namePlaceholderStaff')}
                value={fullName}
                onChange={e => setFullName(e.target.value)}
                maxLength={100}
                aria-invalid={Boolean(fieldErrors.fullName)}
                disabled={loading}
              />
              {fieldErrors.fullName && (
                <span className="account-field-error" role="alert">{fieldErrors.fullName}</span>
              )}
            </div>

            <div className="account-field">
              <label
                className={`account-label ${role === 'CARRIER' ? 'account-label-required' : ''}`}
                htmlFor="internal-phone"
              >
                {role === 'CARRIER' ? t('create.phoneCarrier') : t('create.phoneStaff')}
              </label>
              <input
                id="internal-phone"
                type="tel"
                className="account-input"
                placeholder={t('create.phonePlaceholder')}
                value={phone}
                onChange={e => setPhone(e.target.value)}
                maxLength={30}
                aria-invalid={Boolean(fieldErrors.phone)}
                disabled={loading}
              />
              {fieldErrors.phone && (
                <span className="account-field-error" role="alert">{fieldErrors.phone}</span>
              )}
            </div>

            <h3 style={{ fontSize: '1.05rem', fontWeight: 600, margin: '1rem 0 0.5rem 0', color: '#1a1a1a', borderBottom: '1px solid #eceef1', paddingBottom: '0.5rem' }}>
              {t('create.sectionRole')}
            </h3>

            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="internal-role">
                {t('create.internalRole')}
              </label>
              <select
                id="internal-role"
                className="account-select"
                value={role}
                onChange={e => {
                  const newRole = e.target.value as 'STAFF' | 'MANAGER' | 'CARRIER';
                  setRole(newRole);
                  if (newRole === 'STAFF' && selectedLocations.length > 1) {
                    setSelectedLocations([selectedLocations[0]]);
                  }
                }}
                disabled={loading}
              >
                <option value="STAFF">{t('create.optStaff')}</option>
                <option value="MANAGER">{t('create.optManager')}</option>
                <option value="CARRIER">{t('create.optCarrier')}</option>
              </select>
              <span className="account-field-hint" data-testid="role-scope-hint">
                {role === 'CARRIER'
                  ? t('create.hintCarrier')
                  : role === 'MANAGER'
                  ? t('create.hintManager')
                  : t('create.hintStaff')}
              </span>
            </div>

            {role !== 'CARRIER' && (
              <>
                <div className="account-field">
                  <span className="account-label account-label-required">
                    {role === 'MANAGER' ? t('create.locationsManager') : t('create.locationsStaff')}
                  </span>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', marginTop: '0.25rem' }}>
                    {locationsError && (
                      <span className="account-field-error" role="alert">{locationsError}</span>
                    )}
                    {availableLocations.map(loc => {
                      const isChecked = selectedLocations.includes(loc.id);
                      return (
                        <label
                          key={loc.id}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '0.5rem',
                            fontSize: '0.9rem',
                            cursor: 'pointer',
                          }}
                        >
                          <input
                            type={role === 'STAFF' ? 'radio' : 'checkbox'}
                            name="locations"
                            checked={isChecked}
                            onChange={() => handleLocationToggle(loc.id)}
                            disabled={loading}
                          />
                          <span>{loc.name}</span>
                        </label>
                      );
                    })}
                  </div>
                  {fieldErrors.locations && (
                    <span className="account-field-error" role="alert">{fieldErrors.locations}</span>
                  )}
                </div>

                <div className="account-field" style={{ marginTop: '0.5rem' }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.875rem', cursor: 'pointer', color: '#1a1a1a' }}>
                    <input
                      type="checkbox"
                      checked={sendNotification}
                      onChange={e => setSendNotification(e.target.checked)}
                    />
                    <span>{t('create.sendNotification')}</span>
                  </label>
                </div>
              </>
            )}

            <div style={{ display: 'flex', gap: '1rem', marginTop: '1rem' }}>
              <button
                type="submit"
                className="account-btn account-btn-primary"
                disabled={loading}
              >
                {loading ? t('create.submitting') : t('create.submit')}
              </button>
              <Link to="/admin/accounts" className="account-btn account-btn-secondary">
                {t('create.cancel')}
              </Link>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};

export default InternalAccountCreatePage;
