import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router';
import { useAdminAccounts } from '../hooks/useAdminAccounts';
import { useWorkshopLocations } from '../hooks/useWorkshopLocations';
import { ApiError } from '../../../shared/api/http';
import { useT } from '../../../shared/i18n/I18nProvider';
import { accountMessages } from '../messages';
import '../accountmanagement.css';

export const AdminAccountDetailPage: React.FC = () => {
  const t = useT(accountMessages);
  const { accountId } = useParams<{ accountId: string }>();
  const { locations, error: locationsError } = useWorkshopLocations();
  const {
    selectedAccount,
    loading,
    error,
    loadDetail,
    updateProfile,
    assignRoleLocation,
    suspendAccount,
    restoreAccount,
    resendInvitation,
  } = useAdminAccounts();

  // Profile edit form
  const [editName, setEditName] = useState('');
  const [editPhone, setEditPhone] = useState('');
  const [profileActionError, setProfileActionError] = useState<string | null>(null);
  const [profileActionSuccess, setProfileActionSuccess] = useState<string | null>(null);
  const [savingProfile, setSavingProfile] = useState(false);

  // Role & location form
  const [editRole, setEditRole] = useState<'STAFF' | 'MANAGER'>('STAFF');
  const [editLocations, setEditLocations] = useState<number[]>([]);
  const [roleActionError, setRoleActionError] = useState<string | null>(null);
  const [roleActionSuccess, setRoleActionSuccess] = useState<string | null>(null);
  const [savingRole, setSavingRole] = useState(false);

  // Suspension modal / form
  const [showSuspendModal, setShowSuspendModal] = useState(false);
  const [suspendReason, setSuspendReason] = useState('');
  const [suspendError, setSuspendError] = useState<string | null>(null);
  const [statusActionSuccess, setStatusActionSuccess] = useState<string | null>(null);
  const [actionPending, setActionPending] = useState(false);

  // Invitation resend state
  const [resendNotice, setResendNotice] = useState<string | null>(null);

  useEffect(() => {
    if (accountId) {
      loadDetail(accountId);
    }
  }, [accountId, loadDetail]);

  useEffect(() => {
    if (selectedAccount) {
      setEditName(selectedAccount.profile?.fullName || '');
      setEditPhone(selectedAccount.profile?.phone || '');
      if (selectedAccount.role === 'STAFF' || selectedAccount.role === 'MANAGER') {
        setEditRole(selectedAccount.role);
      }
      setEditLocations(
        selectedAccount.locationAssignments?.filter(l => l.active).map(l => l.locationId) || []
      );
    }
  }, [selectedAccount]);

  if (loading && !selectedAccount) {
    return (
      <div className="account-page-container">
        <div className="account-card">
          <p style={{ color: '#6b717f' }}>{t('detail.loading', { id: accountId ?? '' })}</p>
        </div>
      </div>
    );
  }

  if (!selectedAccount) {
    return (
      <div className="account-page-container">
        <div className="account-alert account-alert-error" role="alert">
          <span>{error?.message || t('detail.notFound', { id: accountId ?? '' })}</span>
        </div>
        <Link to="/admin/accounts" className="account-btn account-btn-secondary">
          {t('detail.backToList')}
        </Link>
      </div>
    );
  }

  const isAdminAccount = selectedAccount.role === 'ADMIN';
  const isCarrier = selectedAccount.role === 'CARRIER';
  const isSuspended = selectedAccount.status === 'SUSPENDED';
  const isPendingSetup = selectedAccount.status === 'PENDING_SETUP';
  const isRevocationPending = selectedAccount.revocationStatus === 'PENDING';

  const handleProfileSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setProfileActionError(null);
    setProfileActionSuccess(null);

    if (!editName.trim()) {
      setProfileActionError(t('detail.nameRequired'));
      return;
    }

    setSavingProfile(true);
    try {
      const result = await updateProfile(
        selectedAccount.accountId,
        editName.trim(),
        editPhone.trim() || undefined,
        selectedAccount.version
      );
      if (result) {
        setProfileActionSuccess(t('detail.profileSaved'));
      }
    } catch (err: any) {
      if (err instanceof ApiError && err.status === 409) {
        setProfileActionError(t('detail.profileConflict'));
      } else {
        setProfileActionError(err?.message || t('detail.profileError'));
      }
    } finally {
      setSavingProfile(false);
    }
  };

  const handleRoleLocationSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setRoleActionError(null);
    setRoleActionSuccess(null);

    if (editLocations.length === 0) {
      setRoleActionError(t('detail.locationRequired'));
      return;
    }
    if (editRole === 'STAFF' && editLocations.length !== 1) {
      setRoleActionError(t('detail.staffOneLocation'));
      return;
    }

    setSavingRole(true);
    try {
      const result = await assignRoleLocation(
        selectedAccount.accountId,
        editRole,
        editLocations,
        selectedAccount.version
      );
      if (result) {
        setRoleActionSuccess(t('detail.roleSaved'));
      }
    } catch (err: any) {
      if (err instanceof ApiError && err.status === 409) {
        setRoleActionError(t('detail.roleConflict'));
      } else {
        setRoleActionError(err?.message || t('detail.roleError'));
      }
    } finally {
      setSavingRole(false);
    }
  };

  const handleSuspendConfirm = async () => {
    if (!suspendReason.trim()) {
      setSuspendError(t('detail.reasonRequired'));
      return;
    }

    setSuspendError(null);
    setActionPending(true);
    try {
      const result = await suspendAccount(
        selectedAccount.accountId,
        suspendReason.trim(),
        selectedAccount.version
      );
      if (result) {
        setShowSuspendModal(false);
        setSuspendReason('');
        setStatusActionSuccess(t('detail.suspended'));
      }
    } catch (err: any) {
      if (err instanceof ApiError && err.status === 409) {
        setSuspendError(t('detail.suspendConflict'));
      } else {
        setSuspendError(err?.message || t('detail.suspendError'));
      }
    } finally {
      setActionPending(false);
    }
  };

  const handleRestore = async () => {
    setStatusActionSuccess(null);
    setActionPending(true);
    try {
      const result = await restoreAccount(selectedAccount.accountId, selectedAccount.version);
      if (result) {
        setStatusActionSuccess(t('detail.restored'));
      }
    } catch (err: any) {
      if (err instanceof ApiError && err.status === 409) {
        setStatusActionSuccess(null);
        alert(t('detail.restoreBlocked'));
      } else {
        alert(err?.message || t('detail.restoreError'));
      }
    } finally {
      setActionPending(false);
    }
  };

  const handleResendInvitation = async () => {
    setResendNotice(null);
    setActionPending(true);
    try {
      const result = await resendInvitation(selectedAccount.accountId);
      if (result) {
        setResendNotice(t('detail.invitationSent'));
      }
    } catch (err: any) {
      setResendNotice(err?.message || t('detail.invitationError'));
    } finally {
      setActionPending(false);
    }
  };

  return (
    <div className="account-page-container">
      {/* Back button matching Figma */}
      <div>
        <Link to="/admin/accounts" className="account-back-btn">
          {t('detail.back')}
        </Link>
      </div>

      {/* Admin Navigation Tabs (Figma F08-F11) */}
      <nav className="admin-nav-tabs" aria-label={t('detail.navAria')}>
        <Link to="/admin/accounts" className="admin-nav-tab active">
          {t('detail.navAccounts')}
        </Link>
        <Link to="/admin/roles" className="admin-nav-tab">
          {t('detail.navRoles')}
        </Link>
      </nav>

      <header className="account-page-header">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <h1 className="account-page-title">
              {selectedAccount.profile?.fullName ? `${selectedAccount.profile.fullName} · ACC-${selectedAccount.accountId}` : t('detail.accountFallback', { id: selectedAccount.accountId })}
            </h1>
            <p className="account-page-description">
              {t('detail.loginIdentity')} <strong>{selectedAccount.loginEmail}</strong>
            </p>
          </div>
          <div>
            <span
              className={`account-badge ${
                selectedAccount.status === 'ACTIVE'
                  ? 'account-badge-active'
                  : selectedAccount.status === 'PENDING_SETUP'
                  ? 'account-badge-pending'
                  : 'account-badge-suspended'
              }`}
            >
              {selectedAccount.status === 'ACTIVE' ? t('detail.status.ACTIVE') : selectedAccount.status === 'PENDING_SETUP' ? t('detail.status.PENDING_SETUP') : t('detail.status.SUSPENDED')}
            </span>
          </div>
        </div>
      </header>

      {statusActionSuccess && (
        <div className="account-alert account-alert-success" role="status">
          <span>{statusActionSuccess}</span>
        </div>
      )}

      {/* Account Overview Table */}
      <section className="account-card" aria-labelledby="overview-heading">
        <h2 id="overview-heading" className="account-card-title">{t('detail.overview')}</h2>
        <table className="account-table">
          <tbody>
            <tr>
              <th style={{ width: '220px' }}>{t('detail.systemRole')}</th>
              <td><strong>{selectedAccount.role}</strong></td>
            </tr>
            <tr>
              <th>{t('detail.activeStatus')}</th>
              <td>{selectedAccount.status}</td>
            </tr>
            <tr>
              <th>{isCarrier ? t('detail.branchAssigned') : selectedAccount.role === 'MANAGER' ? t('detail.branchAssignedOrg') : t('detail.branchAssigned')}</th>
              <td>
                {isCarrier ? (
                  <span style={{ color: '#6b717f' }}>{t('detail.notApplicableCarrier')}</span>
                ) : selectedAccount.locationAssignments && selectedAccount.locationAssignments.filter(l => l.active).length > 0 ? (
                  <div>
                    <span>
                      {selectedAccount.locationAssignments
                        .filter(l => l.active)
                        .map(l => {
                          const locObj = locations.find(loc => loc.id === l.locationId);
                          return locObj ? locObj.name : t('detail.branchFallback', { id: l.locationId });
                        })
                        .join(', ')}
                    </span>
                    <div style={{ fontSize: '0.8rem', color: '#6b717f', marginTop: '0.25rem' }}>
                      {selectedAccount.role === 'MANAGER'
                        ? t('detail.managerScope')
                        : t('detail.staffScope')}
                    </div>
                  </div>
                ) : (
                  <span style={{ color: '#6b717f' }}>{t('detail.unassigned')}</span>
                )}
              </td>
            </tr>
            <tr>
              <th>{t('detail.revocation')}</th>
              <td>
                {selectedAccount.revocationStatus === 'PENDING' ? (
                  <span style={{ color: '#b45309', fontWeight: 600 }}>{t('detail.revocationPending')}</span>
                ) : selectedAccount.revocationStatus === 'CONFIRMED' ? (
                  <span style={{ color: '#15803d', fontWeight: 600 }}>{t('detail.revocationConfirmed')}</span>
                ) : (
                  t('detail.notApplicable')
                )}
              </td>
            </tr>
            <tr>
              <th>{t('detail.version')}</th>
              <td>{selectedAccount.version}</td>
            </tr>
            <tr>
              <th>{t('detail.createdAt')}</th>
              <td>{selectedAccount.createdAt}</td>
            </tr>
            <tr>
              <th>{t('detail.updatedAt')}</th>
              <td>{selectedAccount.updatedAt}</td>
            </tr>
          </tbody>
        </table>

        {/* Action Controls for Status / Resend */}
        <div style={{ marginTop: '1.25rem', display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          {isPendingSetup && !isCarrier && (
            <button
              type="button"
              className="account-btn account-btn-secondary account-btn-sm"
              onClick={handleResendInvitation}
              disabled={actionPending}
            >
              {t('detail.resend')}
            </button>
          )}

          {!isAdminAccount && !isSuspended && (
            <button
              type="button"
              className="account-btn account-btn-danger account-btn-sm"
              onClick={() => setShowSuspendModal(true)}
              disabled={actionPending}
            >
              {t('detail.suspend')}
            </button>
          )}

          {!isAdminAccount && isSuspended && (
            <button
              type="button"
              className="account-btn account-btn-primary account-btn-sm"
              onClick={handleRestore}
              disabled={actionPending || isRevocationPending}
              title={isRevocationPending ? t('detail.restoreTitle') : undefined}
            >
              {t('detail.restore')}
            </button>
          )}

          {isAdminAccount && (
            <span style={{ fontSize: '0.85rem', color: '#6b717f', alignSelf: 'center' }}>
              {t('detail.adminProtected')}
            </span>
          )}
        </div>

        {resendNotice && (
          <div className="account-alert account-alert-info" style={{ marginTop: '1rem' }}>
            <span>{resendNotice}</span>
          </div>
        )}
      </section>

      {/* Edit Internal Profile */}
      {!isAdminAccount && (
        <section className="account-card" aria-labelledby="edit-profile-heading">
          <h2 id="edit-profile-heading" className="account-card-title">{t('detail.editProfile')}</h2>

          {profileActionSuccess && (
            <div className="account-alert account-alert-success" role="status">
              <span>{profileActionSuccess}</span>
            </div>
          )}

          {profileActionError && (
            <div className="account-alert account-alert-error" role="alert">
              <span>{profileActionError}</span>
            </div>
          )}

          <form className="account-form" onSubmit={handleProfileSave}>
            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="edit-name">
                {t('detail.fullName')}
              </label>
              <input
                id="edit-name"
                type="text"
                className="account-input"
                value={editName}
                onChange={e => setEditName(e.target.value)}
                maxLength={100}
                disabled={savingProfile}
              />
            </div>

            <div className="account-field">
              <label className="account-label" htmlFor="edit-phone">
                {t('detail.phone')}
              </label>
              <input
                id="edit-phone"
                type="tel"
                className="account-input"
                value={editPhone}
                onChange={e => setEditPhone(e.target.value)}
                maxLength={30}
                disabled={savingProfile}
              />
            </div>

            <div>
              <button
                type="submit"
                className="account-btn account-btn-primary account-btn-sm"
                disabled={savingProfile}
              >
                {savingProfile ? t('detail.saving') : t('detail.saveProfile')}
              </button>
            </div>
          </form>
        </section>
      )}

      {/* Assign Role and Location (STAFF / MANAGER only) */}
      {(selectedAccount.role === 'STAFF' || selectedAccount.role === 'MANAGER') && (
        <section className="account-card" aria-labelledby="assign-heading">
          <h2 id="assign-heading" className="account-card-title">{t('detail.assignHeading')}</h2>

          {roleActionSuccess && (
            <div className="account-alert account-alert-success" role="status">
              <span>{roleActionSuccess}</span>
            </div>
          )}

          {roleActionError && (
            <div className="account-alert account-alert-error" role="alert">
              <span>{roleActionError}</span>
            </div>
          )}

          <form className="account-form" onSubmit={handleRoleLocationSave}>
            <div className="account-field">
              <label className="account-label account-label-required" htmlFor="edit-role-select">
                {t('detail.internalRole')}
              </label>
              <select
                id="edit-role-select"
                className="account-select"
                value={editRole}
                onChange={e => {
                  const r = e.target.value as 'STAFF' | 'MANAGER';
                  setEditRole(r);
                  if (r === 'STAFF' && editLocations.length > 1) {
                    setEditLocations([editLocations[0]]);
                  }
                }}
                disabled={savingRole}
              >
                <option value="STAFF">{t('detail.optStaff')}</option>
                <option value="MANAGER">{t('detail.optManager')}</option>
              </select>
              <span className="account-field-hint" data-testid="edit-role-scope-hint">
                {editRole === 'MANAGER'
                  ? t('detail.hintManager')
                  : t('detail.hintStaff')}
              </span>
            </div>

            {editRole !== selectedAccount.role && (
              <div className="account-alert account-alert-info" role="note" data-testid="role-transition-notice" style={{ marginTop: '0.5rem', marginBottom: '0.75rem' }}>
                <span>
                  {t('detail.transition', { from: selectedAccount.role, to: editRole })}
                </span>
              </div>
            )}

            <div className="account-field">
              <span className="account-label account-label-required">
                {editRole === 'MANAGER' ? t('detail.locationsManager') : t('detail.locationsStaff')}
              </span>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', marginTop: '0.25rem' }}>
                {locationsError && (
                  <span className="account-field-error" role="alert">{locationsError}</span>
                )}
                {locations
                  .filter(loc => loc.active || editLocations.includes(loc.id))
                  .map(loc => (
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
                      type={editRole === 'STAFF' ? 'radio' : 'checkbox'}
                      name="edit-locs"
                      checked={editLocations.includes(loc.id)}
                      onChange={() => {
                        if (editRole === 'STAFF') {
                          setEditLocations([loc.id]);
                        } else {
                          if (editLocations.includes(loc.id)) {
                            if (editLocations.length > 1) {
                              setEditLocations(editLocations.filter(id => id !== loc.id));
                            }
                          } else {
                            setEditLocations([...editLocations, loc.id]);
                          }
                        }
                      }}
                      disabled={savingRole}
                    />
                    <span>{loc.active ? loc.name : t('detail.inactiveLocation', { name: loc.name })}</span>
                  </label>
                ))}
              </div>
            </div>

            <div>
              <button
                type="submit"
                className="account-btn account-btn-primary account-btn-sm"
                disabled={savingRole}
              >
                {savingRole ? t('detail.updating') : t('detail.updateRole')}
              </button>
            </div>
          </form>
        </section>
      )}

      {/* Carrier Role Governance Notice (FR-011, FR-012, FR-017) */}
      {isCarrier && (
        <section className="account-card" aria-labelledby="carrier-governance-heading">
          <h2 id="carrier-governance-heading" className="account-card-title">{t('detail.carrierGovernance')}</h2>
          <div className="account-alert account-alert-info">
            <span>
              {t('detail.carrierNotice')}
            </span>
          </div>
        </section>
      )}

      {/* Suspend Confirmation Modal */}
      {showSuspendModal && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="suspend-modal-title"
          className="account-modal-overlay"
        >
          <div className="account-modal-container account-modal-sm">
            <div className="account-modal-header">
              <h3 id="suspend-modal-title" className="account-modal-title" style={{ color: '#dc2626' }}>
                {t('detail.suspendTitle')}
              </h3>
              <button
                type="button"
                className="account-modal-close-btn"
                onClick={() => {
                  setShowSuspendModal(false);
                  setSuspendError(null);
                }}
              >
                ✕
              </button>
            </div>

            <div className="account-modal-body">
              <p style={{ fontSize: '0.9rem', color: '#6b717f', marginTop: 0 }}>
                {t('detail.suspendWarning')}
              </p>

              {suspendError && (
                <div className="account-alert account-alert-error" role="alert">
                  <span>{suspendError}</span>
                </div>
              )}

              <div className="account-field" style={{ marginBottom: '1rem' }}>
                <label className="account-label account-label-required" htmlFor="suspend-reason">
                  {t('detail.reason')}
                </label>
                <textarea
                  id="suspend-reason"
                  className="account-textarea"
                  rows={3}
                  value={suspendReason}
                  onChange={e => setSuspendReason(e.target.value)}
                  placeholder={t('detail.reasonPlaceholder')}
                />
              </div>
            </div>

            <div className="account-modal-footer">
              <button
                type="button"
                className="account-btn account-btn-secondary"
                onClick={() => {
                  setShowSuspendModal(false);
                  setSuspendError(null);
                }}
                disabled={actionPending}
              >
                {t('detail.cancel')}
              </button>
              <button
                type="button"
                className="account-btn account-btn-danger"
                onClick={handleSuspendConfirm}
                disabled={actionPending}
              >
                {actionPending ? t('detail.processing') : t('detail.confirmSuspend')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminAccountDetailPage;
