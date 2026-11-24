import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router';
import { useDesignReviewApi } from '../api/useDesignReviewApi';
import { useFormat, useT } from '../../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../../shared/ui/messages';
import { designReviewMessages } from '../messages';
import { wholeAmount } from '../format';
import { getOrderBadgeInfo, getProductionStatusLabel, getFulfilmentStatusLabel } from '../customOrderStatus';
import '../designreview.css';

export default function MyCustomOrdersPage() {
  const api = useDesignReviewApi();
  const navigate = useNavigate();
  const t = useT(designReviewMessages);
  const ts = useT(sharedMessages);
  const fmt = useFormat();

  const [activeTab, setActiveTab] = useState('orders'); // 'orders' | 'requests'
  const [orders, setOrders] = useState([]);
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Clarification Modal State
  const [clarifyingRequest, setClarifyingRequest] = useState(null);
  const [clarificationText, setClarificationText] = useState('');
  const [clarifyError, setClarifyError] = useState(null);

  useEffect(() => {
    loadData();
  }, [activeTab]);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      if (activeTab === 'orders') {
        const res = await api.listMyCustomOrders();
        setOrders(res?.items || []);
      } else {
        const res = await api.getMyDesignRequests();
        setRequests(res?.items || (Array.isArray(res) ? res : []));
      }
    } catch (err) {
      console.error('Failed to load custom data:', err);
      setError(err?.message || t('co.loadListFailed'));
    } finally {
      setLoading(false);
    }
  };

  const handlePayFullAmount = async (customOrderReference) => {
    setActionLoading(true);
    setError(null);
    try {
      const res = await api.startCustomOrderPayment(customOrderReference, {
        idempotencyKey: `pay_${customOrderReference}_${Date.now()}`
      });
      if (res?.paymentUrl) {
        window.location.href = res.paymentUrl;
      } else {
        throw new Error(t('co.paymentLinkMissing'));
      }
    } catch (err) {
      console.error('Payment start failed:', err);
      setError(err?.message || t('co.payFailedList'));
      setActionLoading(false);
    }
  };

  const handleRequestNewQuote = async (customOrderReference) => {
    setActionLoading(true);
    setError(null);
    try {
      await api.requestNewCustomOrderQuote(customOrderReference, {
        idempotencyKey: `reqquote_${customOrderReference}_${Date.now()}`
      });
      await loadData();
    } catch (err) {
      console.error('Request new quote failed:', err);
      setError(err?.message || t('co.requestQuoteFailed'));
    } finally {
      setActionLoading(false);
    }
  };

  const handleOpenClarification = (req) => {
    setClarifyingRequest(req);
    setClarificationText('');
    setClarifyError(null);
  };

  const handleSubmitClarification = async (e) => {
    e.preventDefault();
    if (!clarificationText.trim()) {
      setClarifyError(t('co.clarifyRequired'));
      return;
    }

    setActionLoading(true);
    setClarifyError(null);
    try {
      await api.reviseCustomDesignRequest(
        clarifyingRequest.requestReference || clarifyingRequest.id,
        {
          description: clarifyingRequest.description,
          clarificationResponse: clarificationText.trim(),
        },
        {
          idempotencyKey: `clarify_${clarifyingRequest.requestReference}_${Date.now()}`,
          ifMatchVersion: clarifyingRequest.version || clarifyingRequest.requestVersion
        }
      );
      setClarifyingRequest(null);
      setClarificationText('');
      await loadData();
    } catch (err) {
      console.error('Clarification submission failed:', err);
      setClarifyError(err?.message || t('co.clarifyFailed'));
    } finally {
      setActionLoading(false);
    }
  };

  const formatCurrency = (amount) => fmt.money(wholeAmount(amount || 0));

  const formatDate = (isoStr) => fmt.dateTime(isoStr);

  const isQuoteExpired = (quote) => {
    if (!quote?.expiresAt) return false;
    return new Date() > new Date(quote.expiresAt) || quote.status === 'QUOTE_EXPIRED';
  };

  return (
    <div className="design-container custom-orders-page">
      <div className="page-header" style={{ marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.75rem', fontWeight: 600, color: '#0f172a', marginBottom: '0.5rem' }}>
          {t('co.list.title')}
        </h1>
        <p style={{ color: '#64748b', fontSize: '0.95rem' }}>
          {t('co.list.subtitle')}
        </p>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: '1rem', borderBottom: '2px solid #e2e8f0', marginBottom: '1.5rem' }}>
        <button
          type="button"
          onClick={() => setActiveTab('orders')}
          style={{
            padding: '0.75rem 1.25rem',
            background: 'none',
            border: 'none',
            borderBottom: activeTab === 'orders' ? '3px solid #0284c7' : '3px solid transparent',
            color: activeTab === 'orders' ? '#0284c7' : '#64748b',
            fontWeight: activeTab === 'orders' ? 600 : 500,
            cursor: 'pointer',
            fontSize: '1rem',
            marginBottom: '-2px'
          }}
        >
          {t('co.list.tabOrders')}
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('requests')}
          style={{
            padding: '0.75rem 1.25rem',
            background: 'none',
            border: 'none',
            borderBottom: activeTab === 'requests' ? '3px solid #0284c7' : '3px solid transparent',
            color: activeTab === 'requests' ? '#0284c7' : '#64748b',
            fontWeight: activeTab === 'requests' ? 600 : 500,
            cursor: 'pointer',
            fontSize: '1rem',
            marginBottom: '-2px'
          }}
        >
          {t('co.list.tabRequests')}
        </button>
      </div>

      {error && (
        <div className="alert-banner alert-banner-danger" style={{ marginBottom: '1.5rem' }}>
          <p>{error}</p>
        </div>
      )}

      {loading ? (
        <div style={{ padding: '3rem', textAlign: 'center', color: '#64748b' }}>
          {t('co.list.loading')}
        </div>
      ) : activeTab === 'orders' ? (
        // ORDERS TAB
        <div>
          {orders.length === 0 ? (
            <div style={{ padding: '3rem', textAlign: 'center', background: '#f8fafc', borderRadius: 8 }}>
              <p style={{ color: '#64748b', marginBottom: '1rem' }}>{t('co.list.noOrders')}</p>
              <Link to="/design-requests/new" className="button button-primary">
                {t('co.list.createRequest')}
              </Link>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
              {orders.map((order) => {
                const quote = order.quote;
                const expired = isQuoteExpired(quote);
                const isPaid = order.status === 'PAID' || quote?.status === 'PAID';
                const canPay = (order.status === 'FULL_PAYMENT_PENDING' || quote?.status === 'QUOTE_READY') && !expired && !isPaid;
                const canRequestNewQuote = expired || order.status === 'QUOTE_EXPIRED';

                return (
                  <div
                    key={order.customOrderReference}
                    style={{
                      border: '1px solid #e2e8f0',
                      borderRadius: 8,
                      padding: '1.25rem',
                      background: '#ffffff',
                      boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem', borderBottom: '1px solid #f1f5f9', paddingBottom: '0.75rem', marginBottom: '1rem' }}>
                      <div>
                        <span style={{ fontSize: '0.85rem', color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{t('co.list.orderCode')}</span>
                        <h3 style={{ fontSize: '1.2rem', fontWeight: 600, color: '#0f172a', margin: '0.2rem 0' }}>
                          {order.customOrderReference}
                        </h3>
                        <span style={{ fontSize: '0.8rem', color: '#94a3b8' }}>
                          {t('co.createdAt', { date: formatDate(order.createdAt) })}
                        </span>
                      </div>
                      <div style={{ textAlign: 'right' }}>
                        {(() => {
                          const isClosed = order.status === 'CLOSED' || order.status === 'COMPLETED' || order.fulfilmentStatus === 'DELIVERED';
                          const isFulfilment = order.status === 'READY_FOR_FULFILMENT' || order.fulfilmentStatus === 'READY_FOR_FULFILMENT';
                          const isProduction = order.status === 'IN_PRODUCTION' || order.productionStatus === 'IN_PRODUCTION';

                          let badgeText = t('co.status.quotePending');
                          let badgeBg = '#f1f5f9';
                          let badgeColor = '#64748b';

                          if (isClosed) {
                            badgeText = t('co.status.closed');
                            badgeBg = '#e2e8f0';
                            badgeColor = '#334155';
                          } else if (isFulfilment) {
                            badgeText = t('co.status.readyForFulfilment');
                            badgeBg = '#dcfce7';
                            badgeColor = '#15803d';
                          } else if (isProduction) {
                            badgeText = t('co.status.inProduction');
                            badgeBg = '#fef3c7';
                            badgeColor = '#b45309';
                          } else if (isPaid) {
                            badgeText = t('co.status.paid');
                            badgeBg = '#dcfce7';
                            badgeColor = '#166534';
                          } else if (expired) {
                            badgeText = t('co.status.expired');
                            badgeBg = '#fee2e2';
                            badgeColor = '#991b1b';
                          } else if (order.status === 'FULL_PAYMENT_PENDING') {
                            badgeText = t('co.status.payPending');
                            badgeBg = '#e0f2fe';
                            badgeColor = '#0369a1';
                          }

                          return (
                            <span style={{
                              display: 'inline-block',
                              padding: '0.35rem 0.75rem',
                              borderRadius: '9999px',
                              fontSize: '0.85rem',
                              fontWeight: 600,
                              backgroundColor: badgeBg,
                              color: badgeColor
                            }}>
                              {badgeText}
                            </span>
                          );
                        })()}
                        {quote?.quoteGeneration && (
                          <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '0.25rem' }}>
                            {t('co.quote.generation', { n: quote.quoteGeneration })}
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Linked Design Reference and Preview */}
                    <div style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '1rem',
                      background: '#f8fafc',
                      borderRadius: 6,
                      padding: '0.75rem',
                      marginBottom: '1rem',
                      border: '1px solid #edf2f7'
                    }}>
                      {/* Thumbnail if available */}
                      {order.designImages && order.designImages.length > 0 ? (
                        <img
                          src={order.designImages[0].imageUrl || `/api/v1/design-requests/images/${encodeURIComponent(order.designImages[0].imageReference)}`}
                          alt="Mẫu thiết kế"
                          style={{
                            width: 64,
                            height: 64,
                            objectFit: 'cover',
                            borderRadius: 6,
                            border: '1px solid #cbd5e1',
                            flexShrink: 0
                          }}
                        />
                      ) : (
                        <div style={{
                          width: 64,
                          height: 64,
                          background: '#e2e8f0',
                          borderRadius: 6,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: '1.5rem',
                          flexShrink: 0
                        }}>
                          💍
                        </div>
                      )}

                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
                          <span style={{ fontSize: '0.85rem', color: '#64748b' }}>{t('co.list.linkedDesign')}</span>
                          {order.designRequestReference ? (
                            <button
                              type="button"
                              onClick={() => setActiveTab('requests')}
                              style={{
                                background: '#e0f2fe',
                                color: '#0369a1',
                                border: '1px solid #bae6fd',
                                borderRadius: 4,
                                padding: '2px 8px',
                                fontSize: '0.85rem',
                                fontWeight: 600,
                                cursor: 'pointer',
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: '4px'
                              }}
                              title={t('co.list.viewRequestInTab')}
                            >
                              🔗 {order.designRequestReference}
                            </button>
                          ) : (
                            <span style={{ fontSize: '0.85rem', color: '#94a3b8' }}>—</span>
                          )}
                          {order.designSnapshot?.material && (
                            <span style={{ fontSize: '0.8rem', background: '#fef3c7', color: '#92400e', padding: '2px 6px', borderRadius: 4 }}>
                              {order.designSnapshot.material}
                            </span>
                          )}
                        </div>
                        {order.designOriginalDescription && (
                          <p style={{
                            margin: '0.35rem 0 0',
                            fontSize: '0.85rem',
                            color: '#475569',
                            display: '-webkit-box',
                            WebkitLineClamp: 1,
                            WebkitBoxOrient: 'vertical',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis'
                          }} title={order.designOriginalDescription}>
                            &ldquo;{order.designOriginalDescription}&rdquo;
                          </p>
                        )}
                      </div>
                    </div>

                    {/* Order Details & Quote */}
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1rem', marginBottom: '1.25rem' }}>
                      <div>
                        <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>{t('co.list.total100')}</span>
                        <strong style={{ fontSize: '1.25rem', color: '#0f172a' }}>
                          {quote?.totalAmount ? formatCurrency(quote.totalAmount) : t('co.quote.calculating')}
                        </strong>
                      </div>
                      <div>
                        <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>{t('co.list.deadline')}</span>
                        <span style={{ color: expired ? '#dc2626' : '#0f172a', fontWeight: 500 }}>
                          {quote?.expiresAt ? formatDate(quote.expiresAt) : t('co.quote.notSet')}
                        </span>
                      </div>
                      <div>
                        <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>{t('co.list.productionStatus')}</span>
                        <span style={{ color: '#0f172a', fontWeight: 500 }}>
                          {getProductionStatusLabel(order.productionStatus, isPaid)}
                        </span>
                      </div>
                      <div>
                        <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>{t('co.list.deliveryStatus')}</span>
                        <span style={{ color: '#0f172a', fontWeight: 500 }}>
                          {getFulfilmentStatusLabel(order.fulfilmentStatus)}
                        </span>
                      </div>
                    </div>

                    {/* Action buttons */}
                    <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end', flexWrap: 'wrap', borderTop: '1px solid #f1f5f9', paddingTop: '1rem' }}>
                      <Link
                        to={`/account/my-custom-orders/${order.customOrderReference}`}
                        className="button button-outline"
                        style={{ padding: '0.5rem 1rem', fontSize: '0.9rem' }}
                      >
                        {t('co.list.orderDetail')}
                      </Link>

                      {canPay && (
                        <button
                          type="button"
                          className="button button-primary"
                          disabled={actionLoading}
                          onClick={() => handlePayFullAmount(order.customOrderReference)}
                          style={{
                            padding: '0.5rem 1.25rem',
                            fontSize: '0.9rem',
                            backgroundColor: '#16a34a',
                            color: '#ffffff',
                            fontWeight: 600
                          }}
                        >
                          {actionLoading ? t('co.list.processing') : t('co.list.payFull', { amount: formatCurrency(quote?.totalAmount) })}
                        </button>
                      )}

                      {canRequestNewQuote && (
                        <button
                          type="button"
                          className="button button-secondary"
                          disabled={actionLoading}
                          onClick={() => handleRequestNewQuote(order.customOrderReference)}
                          style={{ padding: '0.5rem 1rem', fontSize: '0.9rem' }}
                        >
                          {actionLoading ? t('co.action.sending') : t('co.action.requestNewQuote')}
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      ) : (
        // REQUESTS TAB
        <div>
          {requests.length === 0 ? (
            <div style={{ padding: '3rem', textAlign: 'center', background: '#f8fafc', borderRadius: 8 }}>
              <p style={{ color: '#64748b', marginBottom: '1rem' }}>{t('co.list.noRequests')}</p>
              <Link to="/design-requests/new" className="button button-primary">
                {t('co.list.createNow')}
              </Link>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
              {requests.map((req) => {
                const status = req.reviewStatus || req.status || 'NEED_REVIEW';
                const isClarification = status === 'CLARIFICATION_REQUIRED';

                return (
                  <div
                    key={req.requestReference || req.id}
                    style={{
                      border: '1px solid #e2e8f0',
                      borderRadius: 8,
                      padding: '1.25rem',
                      background: '#ffffff',
                      boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem', borderBottom: '1px solid #f1f5f9', paddingBottom: '0.75rem', marginBottom: '0.75rem' }}>
                      <div>
                        <span style={{ fontSize: '0.85rem', color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{t('co.list.requestCode')}</span>
                        <h3 style={{ fontSize: '1.15rem', fontWeight: 600, color: '#0f172a', margin: '0.2rem 0' }}>
                          {req.requestReference || req.id}
                        </h3>
                        <span style={{ fontSize: '0.8rem', color: '#94a3b8' }}>
                          {t('co.list.versionUpdated', { version: req.version || req.requestVersion || 1, date: formatDate(req.updatedAt || req.createdAt) })}
                        </span>
                      </div>
                      <div>
                        <span style={{
                          display: 'inline-block',
                          padding: '0.35rem 0.75rem',
                          borderRadius: '9999px',
                          fontSize: '0.85rem',
                          fontWeight: 600,
                          backgroundColor:
                            status === 'ACCEPTED' ? '#dcfce7' :
                            status === 'REJECTED' ? '#fee2e2' :
                            status === 'CLARIFICATION_REQUIRED' ? '#fef3c7' : '#f1f5f9',
                          color:
                            status === 'ACCEPTED' ? '#166534' :
                            status === 'REJECTED' ? '#991b1b' :
                            status === 'CLARIFICATION_REQUIRED' ? '#92400e' : '#334155'
                        }}>
                          {status === 'ACCEPTED' ? t('co.list.reqAccepted') :
                           status === 'REJECTED' ? t('co.list.reqRejected') :
                           status === 'CLARIFICATION_REQUIRED' ? t('co.list.reqClarify') : t('co.list.reqPending')}
                        </span>
                      </div>
                    </div>

                    {/* Linked Custom Order Badge if available */}
                    {req.customOrderReference && (
                      <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '0.5rem',
                        flexWrap: 'wrap',
                        background: '#f0fdf4',
                        border: '1px solid #bbf7d0',
                        borderRadius: 6,
                        padding: '0.5rem 0.75rem',
                        marginBottom: '0.75rem'
                      }}>
                        <span style={{ fontSize: '0.85rem', fontWeight: 600, color: '#166534' }}>
                          ✨ {t('co.list.createdOrder')} <strong>{req.customOrderReference}</strong>
                        </span>
                        <button
                          type="button"
                          onClick={() => setActiveTab('orders')}
                          style={{
                            background: '#ffffff',
                            color: '#166534',
                            border: '1px solid #86efac',
                            borderRadius: 4,
                            padding: '2px 8px',
                            fontSize: '0.8rem',
                            fontWeight: 500,
                            cursor: 'pointer'
                          }}
                        >
                          {t('co.list.viewOrderInTab')}
                        </button>
                        <Link
                          to={`/account/my-custom-orders/${req.customOrderReference}`}
                          style={{ fontSize: '0.8rem', color: '#0284c7', textDecoration: 'underline', marginLeft: 'auto' }}
                        >
                          {t('co.list.goToOrderDetail')}
                        </Link>
                      </div>
                    )}

                    {req.description && (
                      <p style={{ color: '#334155', fontSize: '0.95rem', marginBottom: '0.75rem', lineHeight: 1.5 }}>
                        {req.description}
                      </p>
                    )}

                    {/* Explanation if exists */}
                    {(req.decisionExplanation || req.explanation) && (
                      <div style={{
                        padding: '0.75rem 1rem',
                        background: isClarification ? '#fffbeb' : '#f8fafc',
                        borderLeft: `4px solid ${isClarification ? '#f59e0b' : '#94a3b8'}`,
                        borderRadius: 4,
                        marginBottom: '0.75rem'
                      }}>
                        <strong style={{ fontSize: '0.85rem', color: isClarification ? '#b45309' : '#475569', display: 'block', marginBottom: '0.25rem' }}>
                          {isClarification ? t('co.list.clarifyFromReviewer') : t('co.list.reviewFeedback')}
                        </strong>
                        <p style={{ margin: 0, fontSize: '0.9rem', color: '#1e293b' }}>
                          {req.decisionExplanation || req.explanation}
                        </p>
                      </div>
                    )}

                    {/* Action buttons */}
                    <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end', borderTop: '1px solid #f1f5f9', paddingTop: '0.75rem' }}>
                      {isClarification && (
                        <button
                          type="button"
                          className="button button-primary"
                          onClick={() => handleOpenClarification(req)}
                          style={{ padding: '0.45rem 1rem', fontSize: '0.875rem', backgroundColor: '#d97706', color: '#ffffff' }}
                        >
                          {t('co.list.respondClarify')}
                        </button>
                      )}
                      <Link
                        to={`/design-requests/${req.id || req.requestReference}`}
                        className="button button-outline"
                        style={{ padding: '0.45rem 1rem', fontSize: '0.875rem' }}
                      >
                        {t('co.list.viewDetail')}
                      </Link>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* Clarification Response Modal */}
      {clarifyingRequest && (
        <div style={{
          position: 'fixed',
          top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0,0,0,0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 9999,
          padding: '1rem'
        }}>
          <div style={{
            background: '#ffffff',
            borderRadius: 8,
            maxWidth: 550,
            width: '100%',
            padding: '1.5rem',
            boxShadow: '0 20px 25px -5px rgba(0,0,0,0.1)'
          }}>
            <h3 style={{ fontSize: '1.25rem', fontWeight: 600, marginBottom: '0.5rem' }}>
              {t('co.list.modalTitle', { ref: clarifyingRequest.requestReference })}
            </h3>
            <p style={{ fontSize: '0.9rem', color: '#64748b', marginBottom: '1rem' }}>
              {t('co.list.modalIntro')}
            </p>

            {clarifyError && (
              <div className="alert-banner alert-banner-danger" style={{ marginBottom: '1rem' }}>
                <p>{clarifyError}</p>
              </div>
            )}

            <form onSubmit={handleSubmitClarification}>
              <div style={{ marginBottom: '1rem' }}>
                <label style={{ display: 'block', fontWeight: 500, fontSize: '0.9rem', marginBottom: '0.35rem' }}>
                  {t('co.list.modalLabel')}
                </label>
                <textarea
                  rows={4}
                  value={clarificationText}
                  onChange={(e) => setClarificationText(e.target.value)}
                  placeholder={t('co.list.modalPlaceholder')}
                  style={{ width: '100%', padding: '0.5rem', borderRadius: 6, border: '1px solid #cbd5e1' }}
                  required
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
                <button
                  type="button"
                  className="button button-outline"
                  disabled={actionLoading}
                  onClick={() => setClarifyingRequest(null)}
                >
                  {ts('cancel')}
                </button>
                <button
                  type="submit"
                  className="button button-primary"
                  disabled={actionLoading}
                >
                  {actionLoading ? t('co.action.sending') : t('co.list.sendResponse')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
