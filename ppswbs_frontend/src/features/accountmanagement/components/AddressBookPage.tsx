import React, { useState } from 'react';
import { Link } from 'react-router';
import { useMemberAddresses } from '../hooks/useMemberAddresses';
import { useProfile } from '../hooks/useProfile';
import { AddressResponse, AddressCreateRequest, AddressUpdateRequest } from '../api/AddressApiClient';
import AddressModal from './AddressModal';
import { useT, useEnumLabel } from '../../../shared/i18n/I18nProvider';
import { accountMessages } from '../messages';
import '../accountmanagement.css';

export const AddressBookPage: React.FC = () => {
  const t = useT(accountMessages);
  const typeLabel = useEnumLabel(accountMessages, 'addr.type');
  const {
    addresses,
    isQuotaReached,
    loading,
    saving,
    deletingId,
    settingDefaultId,
    error,
    successNotice,
    loadAddresses,
    createAddress,
    updateAddress,
    deleteAddress,
    setDefaultAddress,
    setError,
    setSuccessNotice,
  } = useMemberAddresses();

  const { profile } = useProfile();

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingAddress, setEditingAddress] = useState<AddressResponse | null>(null);
  const [deleteConfirmTarget, setDeleteConfirmTarget] = useState<AddressResponse | null>(null);

  const handleOpenAdd = () => {
    if (isQuotaReached) return;
    setEditingAddress(null);
    setIsModalOpen(true);
  };

  const handleOpenEdit = (addr: AddressResponse) => {
    setEditingAddress(addr);
    setIsModalOpen(true);
  };

  const handleSave = async (data: AddressCreateRequest | AddressUpdateRequest) => {
    if (editingAddress) {
      await updateAddress(editingAddress.id, data as AddressUpdateRequest);
    } else {
      await createAddress(data as AddressCreateRequest);
    }
  };

  const handleDeleteRequest = (addr: AddressResponse) => {
    if (addr.isDefault && addresses.length > 1) {
      setError(t('addr.deleteDefaultError'));
      return;
    }
    setDeleteConfirmTarget(addr);
  };

  const handleConfirmDelete = async () => {
    if (!deleteConfirmTarget) return;
    try {
      await deleteAddress(deleteConfirmTarget.id);
      setDeleteConfirmTarget(null);
    } catch {
      // Error handled in hook
    }
  };

  return (
    <div className="account-page-container">
      {/* Breadcrumb / Back Link */}
      <div style={{ marginBottom: '1rem' }}>
        <Link to="/profile" className="account-back-link">
          {t('addr.back')}
        </Link>
      </div>

      {/* Header */}
      <header className="account-page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 className="account-page-title">{t('addr.title')}</h1>
          <p className="account-page-description">
            {t('addr.description')}
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <span
            className="account-quota-badge"
            aria-label={t('addr.savedAria', { count: addresses.length })}
          >
            {t('addr.saved')} <strong>{addresses.length}/10</strong> {t('addr.addressesUnit')}
          </span>

          <button
            type="button"
            className="account-btn account-btn-primary"
            onClick={handleOpenAdd}
            disabled={isQuotaReached || loading || saving}
            title={isQuotaReached ? t('addr.quotaTitle') : undefined}
          >
            {t('addr.add')}
          </button>
        </div>
      </header>

      {/* Alerts */}
      {successNotice && (
        <div className="account-alert account-alert-success" role="status">
          <span>{successNotice}</span>
          <button
            type="button"
            className="account-alert-dismiss"
            onClick={() => setSuccessNotice(null)}
            aria-label={t('addr.closeNotice')}
          >
            ✕
          </button>
        </div>
      )}

      {error && (
        <div className="account-alert account-alert-error" role="alert">
          <div>
            <strong>{t('addr.noticePrefix')}</strong>
            <span>{error}</span>
          </div>
          <button
            type="button"
            className="account-alert-dismiss"
            onClick={() => setError(null)}
            aria-label={t('addr.closeError')}
          >
            ✕
          </button>
        </div>
      )}

      {/* Quota reached notice */}
      {isQuotaReached && (
        <div className="account-alert account-alert-warning" role="region" aria-label={t('addr.quotaRegion')}>
          <span>
            {t('addr.quotaReached')}
          </span>
        </div>
      )}

      {/* Loading state */}
      {loading && (
        <div className="account-card" style={{ textAlign: 'center', padding: '3rem 1rem' }}>
          <p style={{ color: '#4b5563' }}>{t('addr.loading')}</p>
        </div>
      )}

      {/* Empty State */}
      {!loading && addresses.length === 0 && (
        <div className="account-card" style={{ textAlign: 'center', padding: '3.5rem 1rem' }}>
          <div style={{ fontSize: '3rem', marginBottom: '1rem', color: '#9ca3af' }}>📍</div>
          <h2 style={{ fontSize: '1.25rem', marginBottom: '0.5rem', color: '#111827' }}>
            {t('addr.emptyTitle')}
          </h2>
          <p style={{ color: '#6b7280', marginBottom: '1.5rem', maxWidth: '450px', margin: '0 auto 1.5rem auto' }}>
            {t('addr.emptyBody')}
          </p>
          <button
            type="button"
            className="account-btn account-btn-primary"
            onClick={handleOpenAdd}
          >
            {t('addr.addFirst')}
          </button>
        </div>
      )}

      {/* Address List */}
      {!loading && addresses.length > 0 && (
        <div className="account-address-grid">
          {addresses.map(addr => {
            const isDefaultAndMultiple = addr.isDefault && addresses.length > 1;

            return (
              <div
                key={addr.id}
                className={`account-card account-address-card ${addr.isDefault ? 'account-address-card-default' : ''}`}
              >
                <div className="account-address-card-header">
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
                    <span className="account-address-name">{addr.recipientName}</span>
                    <span className="account-address-phone">{addr.phoneNumber}</span>
                  </div>

                  <div style={{ display: 'flex', gap: '0.375rem', alignItems: 'center' }}>
                    {addr.isDefault && (
                      <span className="account-badge account-badge-success" aria-label={t('addr.defaultAria')}>
                        {t('addr.default')}
                      </span>
                    )}
                    <span
                      className="account-badge account-badge-secondary"
                      aria-label={t('addr.typeAria', { type: typeLabel(addr.addressType === 'HOME' ? 'HOME' : 'OFFICE') })}
                    >
                      {typeLabel(addr.addressType === 'HOME' ? 'HOME' : 'OFFICE')}
                    </span>
                  </div>
                </div>

                <div className="account-address-card-body">
                  <p className="account-address-detail">{addr.detailAddress}</p>
                  <p className="account-address-division">
                    {[
                      addr.hamletName,
                      addr.communeName || addr.wardName,
                      addr.districtName,
                      addr.provinceName,
                    ]
                      .filter(Boolean)
                      .join(', ')}
                  </p>
                </div>

                <div className="account-address-card-footer">
                  <div style={{ display: 'flex', gap: '0.5rem' }}>
                    <button
                      type="button"
                      className="account-btn account-btn-secondary account-btn-sm"
                      onClick={() => handleOpenEdit(addr)}
                      disabled={saving || deletingId === addr.id}
                    >
                      {t('addr.edit')}
                    </button>

                    <button
                      type="button"
                      className="account-btn account-btn-danger account-btn-sm"
                      onClick={() => handleDeleteRequest(addr)}
                      disabled={saving || deletingId === addr.id || isDefaultAndMultiple}
                      title={
                        isDefaultAndMultiple
                          ? t('addr.deleteDefaultBlocked')
                          : t('addr.deleteTitle')
                      }
                    >
                      {deletingId === addr.id ? t('addr.deleting') : t('addr.delete')}
                    </button>
                  </div>

                  {!addr.isDefault && (
                    <button
                      type="button"
                      className="account-btn account-btn-secondary account-btn-sm"
                      onClick={() => setDefaultAddress(addr.id)}
                      disabled={settingDefaultId === addr.id || saving}
                    >
                      {settingDefaultId === addr.id ? t('addr.settingDefault') : t('addr.setDefault')}
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Modal Add / Edit */}
      <AddressModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSave={handleSave}
        initialAddress={editingAddress}
        profileDefaults={{
          fullName: profile?.fullName,
          phone: profile?.phone,
        }}
        isFirstAddress={addresses.length === 0}
      />

      {/* Delete Confirmation Dialog */}
      {deleteConfirmTarget && (
        <div className="account-modal-overlay" role="presentation">
          <div
            className="account-modal-container account-modal-sm"
            role="dialog"
            aria-modal="true"
            aria-labelledby="delete-confirm-title"
          >
            <div className="account-modal-header">
              <h2 id="delete-confirm-title" className="account-modal-title">
                {t('addr.confirmDeleteTitle')}
              </h2>
              <button
                type="button"
                className="account-modal-close-btn"
                onClick={() => setDeleteConfirmTarget(null)}
                aria-label={t('addr.close')}
              >
                ✕
              </button>
            </div>

            <div className="account-modal-body">
              <p style={{ margin: 0 }}>
                {t('addr.deleteQuestion')}{' '}
                <strong>{deleteConfirmTarget.recipientName}</strong> (
                {deleteConfirmTarget.detailAddress}, {deleteConfirmTarget.provinceName}) {t('addr.deleteQuestionEnd')}
              </p>
              <p style={{ fontSize: '0.85rem', color: '#6b7280', marginTop: '0.5rem' }}>
                {t('addr.deleteWarning')}
              </p>
            </div>

            <div className="account-modal-footer">
              <button
                type="button"
                className="account-btn account-btn-secondary"
                onClick={() => setDeleteConfirmTarget(null)}
              >
                {t('addr.cancel')}
              </button>
              <button
                type="button"
                className="account-btn account-btn-danger"
                onClick={handleConfirmDelete}
                disabled={deletingId === deleteConfirmTarget.id}
              >
                {deletingId === deleteConfirmTarget.id ? t('addr.deleting') : t('addr.confirmDelete')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AddressBookPage;

