import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useOtaConnectivityApi } from '../api/useOtaConnectivityApi';
import { Loading, ErrorNotice, SuccessNotice } from '../../shared/ui/components';
import { OperationsAuthWorkspace } from '../../shared/ui/OperationsAuthWorkspace';
import { useT, useEnumLabel } from '../../shared/i18n/I18nProvider';
import { otaMessages } from '../messages';
import { MappingModal } from '../components/MappingModal';
import { PriceTermsModal } from '../components/PriceTermsModal';
import { BookingEnableModal } from '../components/BookingEnableModal';
import '../ota-connectivity.css';

export default function OtaChannelConfigurationPage() {
  const { status: authStatus } = useAuth();
  const api = useOtaConnectivityApi();
  const t = useT(otaMessages);
  const stateLabel = useEnumLabel(otaMessages, 'state');
  const commercialLabel = useEnumLabel(otaMessages, 'commercial');
  const readinessLabel = useEnumLabel(otaMessages, 'readiness');
  const salesControlLabel = useEnumLabel(otaMessages, 'salesControl');

  const [mappings, setMappings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  // Modals state
  const [mappingModal, setMappingModal] = useState({ open: false, channelId: 'default-channel', mapping: null });
  const [priceModal, setPriceModal] = useState({ open: false, mapping: null });
  const [bookingEnableModal, setBookingEnableModal] = useState({ open: false, mapping: null });
  const [actionInProgress, setActionInProgress] = useState(false);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const items = await api.listOtaChannels();
      setMappings(items || []);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [api]);

  useEffect(() => {
    if (authStatus === 'signed-in') {
      loadData();
    }
  }, [authStatus, loadData]);

  const handleCreateOrUpdateMapping = async (data) => {
    if (mappingModal.mapping) {
      await api.updateChannelMapping(mappingModal.mapping.mappingId, data);
      setSuccessMessage(t('channels.updated', { id: mappingModal.mapping.mappingId }));
    } else {
      await api.createChannelMapping(mappingModal.channelId, data);
      setSuccessMessage(t('channels.created'));
    }
    await loadData();
  };

  const handleRecordPriceTerms = async (priceData) => {
    await api.recordPriceTerms(priceModal.mapping.mappingId, priceData);
    setSuccessMessage(t('channels.priceRecorded', { id: priceModal.mapping.mappingId }));
    await loadData();
  };

  const handleBookingEnable = async (enableData) => {
    await api.enableBooking(bookingEnableModal.mapping.mappingId, enableData);
    setSuccessMessage(t('channels.bookingEnabled', { id: bookingEnableModal.mapping.mappingId }));
    await loadData();
  };

  const handleSync = async (mapping, mode = 'PREPARATION') => {
    const reason = prompt(t('channels.syncPrompt', { mode, id: mapping.mappingId }), 'Routine inventory synchronization');
    if (!reason || !reason.trim()) return;

    try {
      setActionInProgress(true);
      setError(null);
      await api.syncChannelMapping(mapping.mappingId, { mode, reason: reason.trim() });
      setSuccessMessage(t('channels.syncAccepted', { mode, id: mapping.mappingId }));
      await loadData();
    } catch (err) {
      setError(err);
    } finally {
      setActionInProgress(false);
    }
  };

  const handleSalesClose = async (mapping) => {
    const reason = prompt(t('channels.closePrompt', { id: mapping.mappingId }), 'Operations emergency sales close');
    if (!reason || !reason.trim()) return;

    try {
      setActionInProgress(true);
      setError(null);
      await api.closeChannelSales(mapping.mappingId, { reason: reason.trim() });
      setSuccessMessage(t('channels.closeAccepted', { id: mapping.mappingId }));
      await loadData();
    } catch (err) {
      setError(err);
    } finally {
      setActionInProgress(false);
    }
  };

  const getStatusBadge = (state) => {
    const map = {
      DRAFT: 'badge-draft',
      PREPARATION_SYNC: 'badge-prep',
      SALES_CLOSED: 'badge-closed',
      BOOKING_ENABLED: 'badge-enabled',
      SYNC_SUSPENDED: 'badge-suspended',
      DISABLED: 'badge-disabled',
    };
    return <span className={`badge ${map[state] || 'badge-draft'}`}>{stateLabel(state)}</span>;
  };

  const getCommercialBadge = (status) => {
    return (
      <span className={`badge ${status === 'APPROVED' ? 'badge-approved' : 'badge-pending'}`}>
        {commercialLabel(status)}
      </span>
    );
  };

  if (authStatus === 'loading') {
    return <Loading />;
  }

  if (authStatus !== 'signed-in') {
    return (
      <OperationsAuthWorkspace
        topline="MLAND / OTA CONNECTIVITY"
        message={t('auth.channels')}
      />
    );
  }

  return (
    <div className="ota-workspace">
      
      <main className="ota-main">
        <div className="ota-topline">MLAND / OTA CONNECTIVITY <span>● {t('channels.sub')}</span></div>
        <div className="ota-header" style={{ marginTop: '24px' }}>
        <div>
          <p className="small muted">
            {t('channels.intro')}
          </p>
        </div>
        <div className="ota-header-actions">
          <button
            type="button"
            className="button primary"
            onClick={() => setMappingModal({ open: true, channelId: 'default-channel', mapping: null })}
            disabled={loading || actionInProgress}
          >
            {t('channels.create')}
          </button>
        </div>
      </div>


      {successMessage && <SuccessNotice>{successMessage}</SuccessNotice>}
      <ErrorNotice error={error} onRetry={loadData} onReload={loadData} />

      {loading ? (
        <Loading />
      ) : mappings.length === 0 ? (
        <div className="empty-state" role="status">
          <p>{t('channels.empty')}</p>
          <button
            type="button"
            className="button outline"
            onClick={() => setMappingModal({ open: true, channelId: 'default-channel', mapping: null })}
          >
            {t('channels.createFirst')}
          </button>
        </div>
      ) : (
        <div className="mappings-list">
          {mappings.map((m) => (
            <div key={m.mappingId} className="mapping-card">
              <div className="mapping-card-header">
                <div className="mapping-title">
                  <span>{m.provider}</span>
                  {getStatusBadge(m.state)}
                  {getCommercialBadge(m.commercialStatus)}
                  {m.bookingEnabled && <span className="badge badge-enabled">{t('channels.bookingsOpen')}</span>}
                  {m.salesControlStatus && m.salesControlStatus !== 'NONE' && (
                    <span className="badge badge-closed">{t('channels.control', { status: salesControlLabel(m.salesControlStatus) })}</span>
                  )}
                </div>
                <div className="mapping-actions">
                  <button
                    type="button"
                    className="button small outline"
                    onClick={() => setMappingModal({ open: true, channelId: m.channelId || 'channel-default', mapping: m })}
                    disabled={actionInProgress}
                  >
                    {t('channels.edit')}
                  </button>
                  <button
                    type="button"
                    className="button small outline"
                    onClick={() => setPriceModal({ open: true, mapping: m })}
                    disabled={actionInProgress}
                  >
                    {t('channels.priceTerms')}
                  </button>
                  <button
                    type="button"
                    className="button small outline"
                    onClick={() => handleSync(m, 'PREPARATION')}
                    disabled={actionInProgress}
                  >
                    {t('channels.prepSync')}
                  </button>
                  <button
                    type="button"
                    className="button small outline"
                    onClick={() => handleSync(m, 'FULL_RESYNC')}
                    disabled={actionInProgress}
                  >
                    {t('channels.fullResync')}
                  </button>
                  <button
                    type="button"
                    className="button small danger"
                    onClick={() => handleSalesClose(m)}
                    disabled={actionInProgress || m.state === 'SALES_CLOSED'}
                  >
                    {t('channels.salesClose')}
                  </button>
                  {/* Admin action: Booking Enablement */}
                  {m.state === 'SALES_CLOSED' && (
                    <button
                      type="button"
                      className="button small primary"
                      onClick={() => setBookingEnableModal({ open: true, mapping: m })}
                      disabled={actionInProgress}
                      title={t('channels.adminEnableTitle')}
                    >
                      {t('channels.adminEnable')}
                    </button>
                  )}
                </div>
              </div>

              <div className="mapping-grid">
                <div className="mapping-field">
                  <span className="mapping-label">{t('channels.field.mapping')}</span>
                  <span className="mapping-value mono">{m.mappingId} (v{m.version})</span>
                </div>
                <div className="mapping-field">
                  <span className="mapping-label">{t('channels.field.productRef')}</span>
                  <span className="mapping-value mono">{m.providerProductReference || '—'}</span>
                </div>
                <div className="mapping-field">
                  <span className="mapping-label">{t('channels.field.optionRef')}</span>
                  <span className="mapping-value mono">{m.providerOptionReference || '—'}</span>
                </div>
                <div className="mapping-field">
                  <span className="mapping-label">{t('channels.field.offerRef')}</span>
                  <span className="mapping-value mono">{m.workshopOfferReference || '—'}</span>
                </div>
                <div className="mapping-field">
                  <span className="mapping-label">{t('channels.field.sessionRef')}</span>
                  <span className="mapping-value mono">{m.sessionReference || '—'}</span>
                </div>
                <div className="mapping-field">
                  <span className="mapping-label">{t('channels.field.readiness')}</span>
                  <span className="mapping-value">{readinessLabel(m.safeReadinessStatus || 'INCOMPLETE')}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Modals */}
      {mappingModal.open && (
        <MappingModal
          open={mappingModal.open}
          channelId={mappingModal.channelId}
          mapping={mappingModal.mapping}
          onClose={() => setMappingModal({ open: false, channelId: 'default-channel', mapping: null })}
          onSave={handleCreateOrUpdateMapping}
        />
      )}

      {priceModal.open && (
        <PriceTermsModal
          open={priceModal.open}
          mapping={priceModal.mapping}
          onClose={() => setPriceModal({ open: false, mapping: null })}
          onSave={handleRecordPriceTerms}
        />
      )}

      {bookingEnableModal.open && (
        <BookingEnableModal
          open={bookingEnableModal.open}
          mapping={bookingEnableModal.mapping}
          onClose={() => setBookingEnableModal({ open: false, mapping: null })}
          onSave={handleBookingEnable}
        />
      )}
      </main>
    </div>
  );
}
