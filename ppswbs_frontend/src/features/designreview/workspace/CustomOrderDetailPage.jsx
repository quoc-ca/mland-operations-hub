import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router';
import { useDesignReviewApi } from '../api/useDesignReviewApi';
import '../designreview.css';

export default function CustomOrderDetailPage() {
  const { customOrderId } = useParams();
  const api = useDesignReviewApi();

  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionLoading, setActionLoading] = useState(false);

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
      setError(err?.message || 'Không thể tải thông tin chi tiết đơn hàng.');
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
        throw new Error('Không nhận được liên kết thanh toán từ cổng VNPay.');
      }
    } catch (err) {
      console.error('Payment start failed:', err);
      setError(err?.message || 'Khởi tạo thanh toán toàn bộ thất bại.');
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
      setError(err?.message || 'Yêu cầu báo giá mới thất bại.');
    } finally {
      setActionLoading(false);
    }
  };

  const formatCurrency = (amount) => {
    if (!amount) return '0 đ';
    return Number(amount).toLocaleString('vi-VN') + ' đ';
  };

  const formatDate = (isoStr) => {
    if (!isoStr) return '—';
    try {
      const d = new Date(isoStr);
      return d.toLocaleString('vi-VN', {
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit'
      });
    } catch {
      return isoStr;
    }
  };

  const isQuoteExpired = (quote) => {
    if (!quote?.expiresAt) return false;
    return new Date() > new Date(quote.expiresAt) || quote.status === 'QUOTE_EXPIRED';
  };

  if (loading) {
    return (
      <div className="design-container" style={{ padding: '3rem', textAlign: 'center', color: '#64748b' }}>
        Đang tải thông tin đơn hàng gia công...
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="design-container" style={{ padding: '2rem 1rem' }}>
        <div className="alert-banner alert-banner-danger" style={{ marginBottom: '1.5rem' }}>
          <p>{error || 'Không tìm thấy đơn hàng tùy chỉnh.'}</p>
        </div>
        <Link to="/account/my-custom-orders" className="button button-outline">
          &larr; Quay lại danh sách đơn hàng
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
          &larr; Quay lại danh sách đơn hàng tùy chỉnh
        </Link>
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem', borderBottom: '1px solid #e2e8f0', paddingBottom: '1rem', marginBottom: '1.5rem' }}>
        <div>
          <span style={{ fontSize: '0.85rem', color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Chi tiết đơn hàng gia công</span>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 600, color: '#0f172a', margin: '0.2rem 0' }}>
            {order.customOrderReference}
          </h1>
          <span style={{ fontSize: '0.85rem', color: '#64748b' }}>
            Ngày tạo: {formatDate(order.createdAt)}
          </span>
        </div>
        <div>
          <span style={{
            display: 'inline-block',
            padding: '0.45rem 1rem',
            borderRadius: '9999px',
            fontSize: '0.9rem',
            fontWeight: 600,
            backgroundColor: isPaid ? '#dcfce7' : (expired ? '#fee2e2' : '#e0f2fe'),
            color: isPaid ? '#166534' : (expired ? '#991b1b' : '#0369a1')
          }}>
            {isPaid ? 'Đã thanh toán toàn bộ' : (expired ? 'Báo giá đã hết hạn' : (order.status === 'FULL_PAYMENT_PENDING' ? 'Chờ thanh toán 100%' : 'Chờ duyệt báo giá'))}
          </span>
        </div>
      </div>

      {/* Production & Fulfilment Status Stepper */}
      <div style={{ background: '#f8fafc', borderRadius: 8, padding: '1.25rem', border: '1px solid #e2e8f0', marginBottom: '1.5rem' }}>
        <h3 style={{ fontSize: '1rem', fontWeight: 600, color: '#1e293b', marginBottom: '1rem' }}>
          Tiến trình thực hiện đơn hàng
        </h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '1rem' }}>
          <div style={{ borderLeft: '3px solid #0284c7', paddingLeft: '0.75rem' }}>
            <span style={{ fontSize: '0.8rem', color: '#64748b', display: 'block' }}>1. Báo giá & Duyệt</span>
            <strong style={{ fontSize: '0.9rem', color: quote?.approvedByManager ? '#16a34a' : '#d97706' }}>
              {quote?.approvedByManager ? 'Đã duyệt báo giá' : 'Đang xử lý'}
            </strong>
          </div>
          <div style={{ borderLeft: `3px solid ${isPaid ? '#16a34a' : '#cbd5e1'}`, paddingLeft: '0.75rem' }}>
            <span style={{ fontSize: '0.8rem', color: '#64748b', display: 'block' }}>2. Thanh toán 100%</span>
            <strong style={{ fontSize: '0.9rem', color: isPaid ? '#16a34a' : '#64748b' }}>
              {isPaid ? 'Đã thanh toán' : (canPay ? 'Đang chờ thanh toán' : 'Chưa mở')}
            </strong>
          </div>
          <div style={{ borderLeft: `3px solid ${order.productionStatus ? '#0284c7' : '#cbd5e1'}`, paddingLeft: '0.75rem' }}>
            <span style={{ fontSize: '0.8rem', color: '#64748b', display: 'block' }}>3. Chế tác sản xuất</span>
            <strong style={{ fontSize: '0.9rem', color: order.productionStatus ? '#0f172a' : '#64748b' }}>
              {order.productionStatus || 'Chờ bắt đầu'}
            </strong>
          </div>
          <div style={{ borderLeft: `3px solid ${order.fulfilmentStatus ? '#0284c7' : '#cbd5e1'}`, paddingLeft: '0.75rem' }}>
            <span style={{ fontSize: '0.8rem', color: '#64748b', display: 'block' }}>4. Bàn giao sản phẩm</span>
            <strong style={{ fontSize: '0.9rem', color: order.fulfilmentStatus ? '#0f172a' : '#64748b' }}>
              {order.fulfilmentStatus || 'Chưa bàn giao'}
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
          Thông tin báo giá gia công
        </h3>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1.25rem', marginBottom: '1.5rem' }}>
          <div>
            <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>Tổng tiền thanh toán 1 lần (100%):</span>
            <strong style={{ fontSize: '1.5rem', color: '#0f172a' }}>
              {quote?.totalAmount ? formatCurrency(quote.totalAmount) : 'Đang tính toán'}
            </strong>
            <span style={{ fontSize: '0.8rem', color: '#94a3b8', display: 'block' }}>
              (Thanh toán một lần duy nhất)
            </span>
          </div>
          <div>
            <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>Mã tham chiếu báo giá:</span>
            <span style={{ fontWeight: 500, color: '#334155' }}>{quote?.quoteReference || '—'}</span>
            {quote?.quoteGeneration && (
              <span style={{ fontSize: '0.8rem', color: '#64748b', display: 'block' }}>
                Thế hệ báo giá: Lần #{quote.quoteGeneration}
              </span>
            )}
          </div>
          <div>
            <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>Thời hạn thanh toán:</span>
            <span style={{ fontWeight: 500, color: expired ? '#dc2626' : '#0f172a' }}>
              {quote?.expiresAt ? formatDate(quote.expiresAt) : 'Chưa thiết lập'}
            </span>
            {expired && (
              <span style={{ fontSize: '0.8rem', color: '#dc2626', display: 'block' }}>
                Hạn chót đã qua. Báo giá đã hết hiệu lực.
              </span>
            )}
          </div>
        </div>

        {quote?.managerReason && (
          <div style={{ background: '#f8fafc', padding: '0.75rem 1rem', borderRadius: 6, borderLeft: '4px solid #0284c7', marginBottom: '1.25rem' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 600, color: '#0369a1', display: 'block' }}>Ghi chú từ Quản lý (Manager):</span>
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
              {actionLoading ? 'Đang chuyển hướng...' : `Thanh toán toàn bộ ${formatCurrency(quote?.totalAmount)} qua VNPay`}
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
              {actionLoading ? 'Đang gửi...' : 'Yêu cầu báo giá mới'}
            </button>
          )}
        </div>
      </div>

      {/* Design Snapshot if available */}
      {snapshot && (
        <div style={{
          background: '#ffffff',
          border: '1px solid #e2e8f0',
          borderRadius: 8,
          padding: '1.5rem',
          boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
        }}>
          <h3 style={{ fontSize: '1.15rem', fontWeight: 600, color: '#0f172a', marginBottom: '1rem', borderBottom: '1px solid #f1f5f9', paddingBottom: '0.5rem' }}>
            Thông số kỹ thuật mẫu nhẫn chế tác (Feature-012 Snapshot)
          </h3>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem', fontSize: '0.9rem' }}>
            {snapshot.model && (
              <div>
                <span style={{ color: '#64748b', display: 'block' }}>Mẫu nhẫn:</span>
                <strong>{snapshot.model}</strong>
              </div>
            )}
            {snapshot.material && (
              <div>
                <span style={{ color: '#64748b', display: 'block' }}>Chất liệu:</span>
                <strong>{snapshot.material}</strong>
              </div>
            )}
            {snapshot.fingerSize && (
              <div>
                <span style={{ color: '#64748b', display: 'block' }}>Kích thước nhẫn:</span>
                <strong>{snapshot.fingerSize}</strong>
              </div>
            )}
            {snapshot.gemstones && Array.isArray(snapshot.gemstones) && (
              <div>
                <span style={{ color: '#64748b', display: 'block' }}>Số lượng đá đính:</span>
                <strong>{snapshot.gemstones.length} viên</strong>
              </div>
            )}
            {snapshot.engravingText && (
              <div>
                <span style={{ color: '#64748b', display: 'block' }}>Nội dung khắc laser:</span>
                <strong>{snapshot.engravingText}</strong>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
