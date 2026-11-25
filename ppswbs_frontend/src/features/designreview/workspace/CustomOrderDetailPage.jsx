import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router';
import { useDesignReviewApi } from '../api/useDesignReviewApi';
import { useFormat, useT } from '../../../shared/i18n/I18nProvider';
import { sharedMessages } from '../../../shared/ui/messages';
import DesignImage from '../components/DesignImage';
import { designReviewMessages } from '../messages';
import { wholeAmount } from '../format';
import { getOrderBadgeInfo, getProductionStatusLabel, getFulfilmentStatusLabel } from '../customOrderStatus';
import '../designreview.css';

export default function CustomOrderDetailPage() {
  const { customOrderId } = useParams();
  const api = useDesignReviewApi();
  const t = useT(designReviewMessages);
  const fmt = useFormat();

  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [selectedImage, setSelectedImage] = useState(null);

  useEffect(() => {
    loadOrder();
  }, [customOrderId]);

  const loadOrder = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.getMyCustomOrder(customOrderId);
      setOrder(res);
    } catch (err) {
      console.error('Failed to load custom order detail:', err);
      setError(err?.message || t('co.loadDetailFailed'));
    } finally {
      setLoading(false);
    }
  };

  const handlePayFullAmount = async () => {
    setActionLoading(true);
    setError(null);
    try {
      const res = await api.startCustomOrderPayment(order.customOrderReference, {
        idempotencyKey: `pay_${order.customOrderReference}_${Date.now()}`
      });
      if (res?.paymentUrl) {
        window.location.href = res.paymentUrl;
      } else {
        throw new Error(t('co.paymentLinkMissing'));
      }
    } catch (err) {
      console.error('Payment start failed:', err);
      setError(err?.message || t('co.payFailedDetail'));
      setActionLoading(false);
    }
  };

  const handleRequestNewQuote = async () => {
    setActionLoading(true);
    setError(null);
    try {
      await api.requestNewCustomOrderQuote(order.customOrderReference, {
        idempotencyKey: `reqquote_${order.customOrderReference}_${Date.now()}`
      });
      await loadOrder();
    } catch (err) {
      console.error('Request new quote failed:', err);
      setError(err?.message || t('co.requestQuoteFailed'));
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

  if (loading) {
    return (
      <div className="design-container" style={{ padding: '3rem', textAlign: 'center', color: '#64748b' }}>
        {t('co.loadingOrder')}
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="design-container" style={{ padding: '2rem 1rem' }}>
        <div className="alert-banner alert-banner-danger" style={{ marginBottom: '1.5rem' }}>
          <p>{error || t('co.orderNotFound')}</p>
        </div>
        <Link to="/account/my-custom-orders" className="button button-outline">
          &larr; {t('co.backToOrders')}
        </Link>
      </div>
    );
  }

  const quote = order.quote;
  const expired = isQuoteExpired(quote);
  const isPaid = order.status === 'PAID' || quote?.status === 'PAID';
  const canPay = (order.status === 'FULL_PAYMENT_PENDING' || quote?.status === 'QUOTE_READY') && !expired && !isPaid;
  const canRequestNewQuote = expired || order.status === 'QUOTE_EXPIRED';
  const snapshot = order.designSnapshot;

  return (
    <div className="design-container custom-order-detail-page">
      <div style={{ marginBottom: '1.25rem' }}>
        <Link to="/account/my-custom-orders" style={{ color: '#0284c7', textDecoration: 'none', fontSize: '0.9rem', fontWeight: 500 }}>
          &larr; {t('co.backToCustomOrders')}
        </Link>
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem', borderBottom: '1px solid #e2e8f0', paddingBottom: '1rem', marginBottom: '1.5rem' }}>
        <div>
          <span style={{ fontSize: '0.85rem', color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{t('co.detailTitle')}</span>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 600, color: '#0f172a', margin: '0.2rem 0' }}>
            {order.customOrderReference}
          </h1>
          <span style={{ fontSize: '0.85rem', color: '#64748b' }}>
            {t('co.createdAt', { date: formatDate(order.createdAt) })}
          </span>
        </div>
        <div>
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
              badgeText = t('co.status.payPending100');
              badgeBg = '#e0f2fe';
              badgeColor = '#0369a1';
            }

            return (
              <span style={{
                display: 'inline-block',
                padding: '0.45rem 1rem',
                borderRadius: '9999px',
                fontSize: '0.9rem',
                fontWeight: 600,
                backgroundColor: badgeBg,
                color: badgeColor
              }}>
                {badgeText}
              </span>
            );
          })()}
        </div>
      </div>

      {/* Production & Fulfilment Status Stepper */}
      <div style={{ background: '#f8fafc', borderRadius: 8, padding: '1.25rem', border: '1px solid #e2e8f0', marginBottom: '1.5rem' }}>
        <h3 style={{ fontSize: '1rem', fontWeight: 600, color: '#1e293b', marginBottom: '1rem' }}>
          {t('co.progress.title')}
        </h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '1rem' }}>
          <div style={{ borderLeft: '3px solid #0284c7', paddingLeft: '0.75rem' }}>
            <span style={{ fontSize: '0.8rem', color: '#64748b', display: 'block' }}>{t('co.progress.step1')}</span>
            <strong style={{ fontSize: '0.9rem', color: quote?.approvedByManager ? '#16a34a' : '#d97706' }}>
              {quote?.approvedByManager ? t('co.progress.quoteApproved') : t('co.progress.processing')}
            </strong>
          </div>
          <div style={{ borderLeft: `3px solid ${isPaid ? '#16a34a' : '#cbd5e1'}`, paddingLeft: '0.75rem' }}>
            <span style={{ fontSize: '0.8rem', color: '#64748b', display: 'block' }}>{t('co.progress.step2')}</span>
            <strong style={{ fontSize: '0.9rem', color: isPaid ? '#16a34a' : '#64748b' }}>
              {isPaid ? t('co.progress.paid') : (canPay ? t('co.progress.awaitingPayment') : t('co.progress.notOpen'))}
            </strong>
          </div>
          <div style={{ borderLeft: `3px solid ${order.productionStatus ? '#0284c7' : '#cbd5e1'}`, paddingLeft: '0.75rem' }}>
            <span style={{ fontSize: '0.8rem', color: '#64748b', display: 'block' }}>{t('co.progress.step3')}</span>
            <strong style={{ fontSize: '0.9rem', color: order.productionStatus ? '#0f172a' : '#64748b' }}>
              {getProductionStatusLabel(order.productionStatus, isPaid)}
            </strong>
          </div>
          <div style={{ borderLeft: `3px solid ${order.fulfilmentStatus ? '#0284c7' : '#cbd5e1'}`, paddingLeft: '0.75rem' }}>
            <span style={{ fontSize: '0.8rem', color: '#64748b', display: 'block' }}>{t('co.progress.step4')}</span>
            <strong style={{ fontSize: '0.9rem', color: order.fulfilmentStatus ? '#0f172a' : '#64748b' }}>
              {getFulfilmentStatusLabel(order.fulfilmentStatus)}
            </strong>
          </div>
        </div>
      </div>

      {/* Quote Summary Box */}
      <div style={{
        background: '#ffffff',
        border: '1px solid #e2e8f0',
        borderRadius: 8,
        padding: '1.5rem',
        marginBottom: '1.5rem',
        boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
      }}>
        <h3 style={{ fontSize: '1.15rem', fontWeight: 600, color: '#0f172a', marginBottom: '1rem', borderBottom: '1px solid #f1f5f9', paddingBottom: '0.5rem' }}>
          {t('co.quote.title')}
        </h3>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1.25rem', marginBottom: '1.5rem' }}>
          <div>
            <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>{t('co.quote.total1x')}</span>
            <strong style={{ fontSize: '1.5rem', color: '#0f172a' }}>
              {quote?.totalAmount ? formatCurrency(quote.totalAmount) : t('co.quote.calculating')}
            </strong>
            <span style={{ fontSize: '0.8rem', color: '#94a3b8', display: 'block' }}>
              {t('co.quote.onePayment')}
            </span>
          </div>
          <div>
            <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>{t('co.quote.reference')}</span>
            <span style={{ fontWeight: 500, color: '#334155' }}>{quote?.quoteReference || '—'}</span>
            {quote?.quoteGeneration && (
              <span style={{ fontSize: '0.8rem', color: '#64748b', display: 'block' }}>
                {t('co.quote.generation', { n: quote.quoteGeneration })}
              </span>
            )}
          </div>
          <div>
            <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>{t('co.quote.deadline')}</span>
            <span style={{ fontWeight: 500, color: expired ? '#dc2626' : '#0f172a' }}>
              {quote?.expiresAt ? formatDate(quote.expiresAt) : t('co.quote.notSet')}
            </span>
            {expired && (
              <span style={{ fontSize: '0.8rem', color: '#dc2626', display: 'block' }}>
                {t('co.quote.deadlinePassed')}
              </span>
            )}
          </div>
        </div>

        {quote?.managerReason && (
          <div style={{ background: '#f8fafc', padding: '0.75rem 1rem', borderRadius: 6, borderLeft: '4px solid #0284c7', marginBottom: '1.25rem' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 600, color: '#0369a1', display: 'block' }}>{t('co.quote.managerNote')}</span>
            <p style={{ margin: '0.25rem 0 0', fontSize: '0.9rem', color: '#334155' }}>{quote.managerReason}</p>
          </div>
        )}

        {/* Action CTAs */}
        <div style={{ display: 'flex', gap: '1rem', justifyContent: 'flex-end', borderTop: '1px solid #f1f5f9', paddingTop: '1rem' }}>
          {canPay && (
            <button
              type="button"
              className="button button-primary"
              disabled={actionLoading}
              onClick={handlePayFullAmount}
              style={{
                backgroundColor: '#16a34a',
                color: '#ffffff',
                padding: '0.75rem 1.75rem',
                fontSize: '1rem',
                fontWeight: 600
              }}
            >
              {actionLoading ? t('co.action.redirecting') : t('co.action.payFull', { amount: formatCurrency(quote?.totalAmount) })}
            </button>
          )}

          {canRequestNewQuote && (
            <button
              type="button"
              className="button button-secondary"
              disabled={actionLoading}
              onClick={handleRequestNewQuote}
              style={{ padding: '0.75rem 1.5rem', fontSize: '0.95rem' }}
            >
              {actionLoading ? t('co.action.sending') : t('co.action.requestNewQuote')}
            </button>
          )}
        </div>
      </div>

      {/* Design Evidence & Linked Request */}
      <div style={{
        background: '#ffffff',
        border: '1px solid #e2e8f0',
        borderRadius: 8,
        padding: '1.5rem',
        boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
        marginBottom: '1.5rem'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.75rem', marginBottom: '1.25rem', borderBottom: '1px solid #f1f5f9', paddingBottom: '0.75rem' }}>
          <h3 style={{ fontSize: '1.15rem', fontWeight: 600, color: '#0f172a', margin: 0 }}>
            {t('co.evidence.title')}
          </h3>
          {order.designRequestReference && (
            <Link
              to={`/design-requests/${order.designRequestId || order.designRequestReference}`}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.4rem',
                fontSize: '0.85rem',
                fontWeight: 600,
                color: '#0284c7',
                background: '#f0f9ff',
                padding: '4px 10px',
                borderRadius: 6,
                border: '1px solid #bae6fd',
                textDecoration: 'none'
              }}
            >
              🔗 {order.designRequestReference} &bull; {t('co.evidence.viewOriginalRequest')}
            </Link>
          )}
        </div>

        {/* Customer description */}
        {order.designOriginalDescription && (
          <div style={{ marginBottom: '1.25rem', background: '#f8fafc', padding: '0.75rem 1rem', borderRadius: 6, borderLeft: '4px solid #3b82f6' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 600, color: '#1d4ed8', display: 'block', marginBottom: '0.25rem' }}>
              {t('co.evidence.customerDescription')}
            </span>
            <p style={{ margin: 0, fontSize: '0.9rem', color: '#334155', lineHeight: 1.5 }}>
              {order.designOriginalDescription}
            </p>
          </div>
        )}

        {/* Staff explanation */}
        {order.staffExplanation && (
          <div style={{ marginBottom: '1.25rem', background: '#f0fdf4', padding: '0.75rem 1rem', borderRadius: 6, borderLeft: '4px solid #16a34a' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 600, color: '#166534', display: 'block', marginBottom: '0.25rem' }}>
              {t('co.evidence.staffExplanation')}
            </span>
            <p style={{ margin: 0, fontSize: '0.9rem', color: '#14532d', lineHeight: 1.5 }}>
              {order.staffExplanation}
            </p>
          </div>
        )}

        {/* Uploaded design photos gallery */}
        {order.designImages && order.designImages.length > 0 && (
          <div style={{ marginBottom: '1.25rem' }}>
            <span style={{ fontSize: '0.85rem', fontWeight: 600, color: '#475569', display: 'block', marginBottom: '0.5rem' }}>
              📷 {t('co.evidence.uploadedImages')}
            </span>
            <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
              {order.designImages.map((img, idx) => {
                return (
                  <div
                    key={img.imageReference || idx}
                    onClick={() => setSelectedImage(img)}
                    style={{
                      cursor: 'pointer',
                      border: '1px solid #cbd5e1',
                      borderRadius: 6,
                      overflow: 'hidden',
                      width: 90,
                      height: 90,
                      boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
                      transition: 'transform 0.15s ease',
                      position: 'relative'
                    }}
                    title={t('co.evidence.clickToEnlarge')}
                  >
                    <DesignImage
                      image={img}
                      alt={img.originalFilename || `Ảnh mẫu ${idx + 1}`}
                      style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    />
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Technical specs snapshot */}
        {snapshot && (
          <div style={{ borderTop: (order.designImages?.length || order.designOriginalDescription) ? '1px solid #f1f5f9' : 'none', paddingTop: '1rem', marginTop: '1rem' }}>
            <span style={{ fontSize: '0.85rem', fontWeight: 600, color: '#475569', display: 'block', marginBottom: '0.75rem' }}>
              📐 {t('co.snapshot.title')}
            </span>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem', fontSize: '0.9rem' }}>
              {snapshot.model && (
                <div>
                  <span style={{ color: '#64748b', display: 'block' }}>{t('co.snapshot.model')}</span>
                  <strong>{snapshot.model}</strong>
                </div>
              )}
              {snapshot.material && (
                <div>
                  <span style={{ color: '#64748b', display: 'block' }}>{t('co.snapshot.material')}</span>
                  <strong>{snapshot.material}</strong>
                </div>
              )}
              {snapshot.fingerSize && (
                <div>
                  <span style={{ color: '#64748b', display: 'block' }}>{t('co.snapshot.fingerSize')}</span>
                  <strong>{snapshot.fingerSize}</strong>
                </div>
              )}
              {snapshot.gemstones && Array.isArray(snapshot.gemstones) && (
                <div>
                  <span style={{ color: '#64748b', display: 'block' }}>{t('co.snapshot.gemCount')}</span>
                  <strong>{t('co.snapshot.gemUnit', { count: snapshot.gemstones.length })}</strong>
                </div>
              )}
              {snapshot.engravingText && (
                <div>
                  <span style={{ color: '#64748b', display: 'block' }}>{t('co.snapshot.engraving')}</span>
                  <strong>{snapshot.engravingText}</strong>
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Lightbox Modal */}
      {selectedImage && (
        <div
          onClick={() => setSelectedImage(null)}
          style={{
            position: 'fixed',
            top: 0, left: 0, right: 0, bottom: 0,
            background: 'rgba(0,0,0,0.75)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 99999,
            padding: '2rem'
          }}
        >
          <div style={{ position: 'relative', maxWidth: '90vw', maxHeight: '90vh' }}>
            <DesignImage
              image={selectedImage}
              alt="Phóng to ảnh mẫu"
              style={{ maxWidth: '100%', maxHeight: '85vh', objectFit: 'contain', borderRadius: 8, boxShadow: '0 25px 50px -12px rgba(0,0,0,0.25)' }}
            />
            <button
              type="button"
              onClick={() => setSelectedImage(null)}
              style={{
                position: 'absolute',
                top: -15, right: -15,
                background: '#ffffff',
                border: 'none',
                borderRadius: '50%',
                width: 36,
                height: 36,
                fontSize: '1.25rem',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: '0 2px 5px rgba(0,0,0,0.2)'
              }}
            >
              ✕
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
