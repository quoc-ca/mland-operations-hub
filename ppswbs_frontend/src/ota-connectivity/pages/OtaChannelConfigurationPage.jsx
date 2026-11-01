import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useOtaConnectivityApi } from '../api/useOtaConnectivityApi';
import { Loading, ErrorNotice, SuccessNotice } from '../../shared/ui/components';
import { MappingModal } from '../components/MappingModal';
import { PriceTermsModal } from '../components/PriceTermsModal';
import { BookingEnableModal } from '../components/BookingEnableModal';
import '../ota-connectivity.css';

export default function OtaChannelConfigurationPage() {
  const { status: authStatus } = useAuth();
  const api = useOtaConnectivityApi();

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
      setSuccessMessage(`Mapping ${mappingModal.mapping.mappingId} updated successfully.`);
    } else {
      await api.createChannelMapping(mappingModal.channelId, data);
      setSuccessMessage('New channel mapping created successfully.');
    }
    await loadData();
  };

  const handleRecordPriceTerms = async (priceData) => {
    await api.recordPriceTerms(priceModal.mapping.mappingId, priceData);
    setSuccessMessage(`Price terms recorded successfully for mapping ${priceModal.mapping.mappingId}.`);
    await loadData();
  };

  const handleBookingEnable = async (enableData) => {
    await api.enableBooking(bookingEnableModal.mapping.mappingId, enableData);
    setSuccessMessage(`Bookings enabled for mapping ${bookingEnableModal.mapping.mappingId}.`);
    await loadData();
  };

  const handleSync = async (mapping, mode = 'PREPARATION') => {
    const reason = prompt(`Enter reason for ${mode} sync on mapping ${mapping.mappingId}:`, 'Routine inventory synchronization');
    if (!reason || !reason.trim()) return;

    try {
      setActionInProgress(true);
      setError(null);
      await api.syncChannelMapping(mapping.mappingId, { mode, reason: reason.trim() });
      setSuccessMessage(`Sync (${mode}) accepted for mapping ${mapping.mappingId}.`);
      await loadData();
    } catch (err) {
      setError(err);
    } finally {
      setActionInProgress(false);
    }
  };

  const handleSalesClose = async (mapping) => {
    const reason = prompt(`Enter reason for sales close on mapping ${mapping.mappingId}:`, 'Operations emergency sales close');
    if (!reason || !reason.trim()) return;

    try {
      setActionInProgress(true);
      setError(null);
      await api.closeChannelSales(mapping.mappingId, { reason: reason.trim() });
      setSuccessMessage(`Sales-close request accepted for mapping ${mapping.mappingId}.`);
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
    return <span className={`badge ${map[state] || 'badge-draft'}`}>{state}</span>;
  };

  const getCommercialBadge = (status) => {
    return (
      <span className={`badge ${status === 'APPROVED' ? 'badge-approved' : 'badge-pending'}`}>
        {status}
      </span>
    );
  };

  if (authStatus === 'loading') {
    return <Loading />;
  }

  if (authStatus !== 'signed-in') {
    return (
      <div className="ota-container">
        <div className="empty-state" role="status">
          <p>Authentication required to access OTA Channel Configuration.</p>
          <a className="button primary" href="/login">Sign In</a>
        </div>
      </div>
    );
  }

  return (
    <div className="ota-container">
      <div className="ota-header">
        <div>
          <h1>OTA Channel Configuration</h1>
          <p className="small muted">
            Manage channel mappings, commercial price terms, and synchronization controls.
          </p>
        </div>
        <div className="ota-header-actions">
          <button
            type="button"
            className="button primary"
            onClick={() => setMappingModal({ open: true, channelId: 'default-channel', mapping: null })}
            disabled={loading || actionInProgress}
          >
            + Create Mapping
          </button>
        </div>
      </div>

      <nav className="ota-nav-tabs" aria-label="OTA Navigation">
        <Link to="/ota-connectivity/channels" className="ota-nav-tab active">
          Channel Mappings
        </Link>
        <Link to="/ota-connectivity/reconciliation" className="ota-nav-tab">
          Reconciliation Queue
        </Link>
      </nav>

      {successMessage && <SuccessNotice>{successMessage}</SuccessNotice>}
      <ErrorNotice error={error} onRetry={loadData} onReload={loadData} />

      {loading ? (
        <Loading />
      ) : mappings.length === 0 ? (
        <div className="empty-state" role="status">
          <p>No OTA channel mappings configured.</p>
          <button
            type="button"
            className="button outline"
            onClick={() => setMappingModal({ open: true, channelId: 'default-channel', mapping: null })}
          >
            Create First Mapping
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
                  {m.bookingEnabled && <span className="badge badge-enabled">BOOKINGS OPEN</span>}
                  {m.salesControlStatus && m.salesControlStatus !== 'NONE' && (
                    <span className="badge badge-closed">CTRL: {m.salesControlStatus}</span>
                  )}
                </div>
                <div className="mapping-actions">
                  <button
                    type="button"
                    className="button small outline"
                    onClick={() => setMappingModal({ open: true, channelId: m.channelId || 'channel-default', mapping: m })}
                    disabled={actionInProgress}
                  >
                    Edit Mapping
                  </button>
                  <button
                    type="button"
                    className="button small outline"
                    onClick={() => setPriceModal({ open: true, mapping: m })}
                    disabled={actionInProgress}
                  >
                    Price Terms
                  </button>
                  <button
                    type="button"
                    className="button small outline"
                    onClick={() => handleSync(m, 'PREPARATION')}
                    disabled={actionInProgress}
                  >
                    Prep Sync
                  </button>
                  <button
                    type="button"
                    className="button small outline"
                    onClick={() => handleSync(m, 'FULL_RESYNC')}
                    disabled={actionInProgress}
                  >
                    Full Resync
                  </button>
                  <button
                    type="button"
                    className="button small danger"
                    onClick={() => handleSalesClose(m)}
                    disabled={actionInProgress || m.state === 'SALES_CLOSED'}
                  >
                    Sales Close
                  </button>
                  {/* Admin action: Booking Enablement */}
                  {m.state === 'SALES_CLOSED' && (
                    <button
                      type="button"
                      className="button small primary"
                      onClick={() => setBookingEnableModal({ open: true, mapping: m })}
                      disabled={actionInProgress}
                      title="Admin-only attestation to enable channel sales"
                    >
                      Admin Enable
                    </button>
                  )}
                </div>
              </div>

              <div className="mapping-grid">
                <div className="mapping-field">
                  <span className="mapping-label">Mapping ID / Version</span>
                  <span className="mapping-value mono">{m.mappingId} (v{m.version})</span>
                </div>
                <div className="mapping-field">
                  <span className="mapping-label">Provider Product Ref</span>
                  <span className="mapping-value mono">{m.providerProductReference || '—'}</span>
                </div>
                <div className="mapping-field">
                  <span className="mapping-label">Provider Option Ref</span>
                  <span className="mapping-value mono">{m.providerOptionReference || '—'}</span>
                </div>
                <div className="mapping-field">
                  <span className="mapping-label">Workshop Offer Ref</span>
                  <span className="mapping-value mono">{m.workshopOfferReference || '—'}</span>
                </div>
                <div className="mapping-field">
                  <span className="mapping-label">Session Ref</span>
                  <span className="mapping-value mono">{m.sessionReference || '—'}</span>
                </div>
                <div className="mapping-field">
                  <span className="mapping-label">Readiness Review</span>
                  <span className="mapping-value">{m.safeReadinessStatus || 'INCOMPLETE'}</span>
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
    </div>
  );
}
