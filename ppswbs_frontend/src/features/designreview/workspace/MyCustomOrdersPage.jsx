import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router';
import { useDesignReviewApi } from '../api/useDesignReviewApi';
import '../designreview.css';

export default function MyCustomOrdersPage() {
  const api = useDesignReviewApi();
  const navigate = useNavigate();

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
      setError(err?.message || 'Không thể tải danh sách dữ liệu. Vui lòng thử lại.');
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
        throw new Error('Không nhận được liên kết thanh toán từ cổng VNPay.');
      }
    } catch (err) {
      console.error('Payment start failed:', err);
      setError(err?.message || 'Khởi tạo thanh toán toàn bộ thất bại. Vui lòng thử lại.');
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
      setError(err?.message || 'Yêu cầu báo giá mới thất bại.');
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
      setClarifyError('Vui lòng nhập nội dung giải thích / làm rõ.');
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
      setClarifyError(err?.message || 'Gửi phản hồi làm rõ thất bại.');
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

  return (
    <div className="design-container custom-orders-page">
      <div className="page-header" style={{ marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.75rem', fontWeight: 600, color: '#0f172a', marginBottom: '0.5rem' }}>
          Đơn hàng cá nhân
        </h1>
        <p style={{ color: '#64748b', fontSize: '0.95rem' }}>
          Quản lý yêu cầu thiết kế nhẫn và đơn hàng cá nhân
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
          Đơn hàng cá nhân (Orders)
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
          Yêu cầu thiết kế (Requests)
        </button>
      </div>

      {error && (
        <div className="alert-banner alert-banner-danger" style={{ marginBottom: '1.5rem' }}>
          <p>{error}</p>
        </div>
      )}

      {loading ? (
        <div style={{ padding: '3rem', textAlign: 'center', color: '#64748b' }}>
          Đang tải dữ liệu...
        </div>
      ) : activeTab === 'orders' ? (
        // ORDERS TAB
        <div>
          {orders.length === 0 ? (
            <div style={{ padding: '3rem', textAlign: 'center', background: '#f8fafc', borderRadius: 8 }}>
              <p style={{ color: '#64748b', marginBottom: '1rem' }}>Bạn chưa có đơn hàng thiết kế cá nhân nào.</p>
              <Link to="/design-requests/new" className="button button-primary">
                Tạo yêu cầu thiết kế mới
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
                        <span style={{ fontSize: '0.85rem', color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Mã đơn gia công</span>
                        <h3 style={{ fontSize: '1.2rem', fontWeight: 600, color: '#0f172a', margin: '0.2rem 0' }}>
                          {order.customOrderReference}
                        </h3>
                        <span style={{ fontSize: '0.8rem', color: '#94a3b8' }}>
                          Ngày tạo: {formatDate(order.createdAt)}
                        </span>
                      </div>
                      <div style={{ textAlign: 'right' }}>
                        <span style={{
                          display: 'inline-block',
                          padding: '0.35rem 0.75rem',
                          borderRadius: '9999px',
                          fontSize: '0.85rem',
                          fontWeight: 600,
                          backgroundColor: isPaid ? '#dcfce7' : (expired ? '#fee2e2' : '#e0f2fe'),
                          color: isPaid ? '#166534' : (expired ? '#991b1b' : '#0369a1')
                        }}>
                          {isPaid ? 'Đã thanh toán toàn bộ' : (expired ? 'Báo giá đã hết hạn' : (order.status === 'FULL_PAYMENT_PENDING' ? 'Chờ thanh toán' : 'Chờ duyệt báo giá'))}
                        </span>
                        {quote?.quoteGeneration && (
                          <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '0.25rem' }}>
                            Phiên bản báo giá: Lần #{quote.quoteGeneration}
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Order Details & Quote */}
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1rem', marginBottom: '1.25rem' }}>
                      <div>
                        <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>Tổng tiền thanh toán 100%:</span>
                        <strong style={{ fontSize: '1.25rem', color: '#0f172a' }}>
                          {quote?.totalAmount ? formatCurrency(quote.totalAmount) : 'Đang tính toán'}
                        </strong>
                      </div>
                      <div>
                        <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>Hạn chót thanh toán:</span>
                        <span style={{ color: expired ? '#dc2626' : '#0f172a', fontWeight: 500 }}>
                          {quote?.expiresAt ? formatDate(quote.expiresAt) : 'Chưa thiết lập'}
                        </span>
                      </div>
                      <div>
                        <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>Trạng thái sản xuất:</span>
                        <span style={{ color: '#0f172a', fontWeight: 500 }}>
                          {order.productionStatus || (isPaid ? 'Đang chuẩn bị sản xuất' : 'Chờ thanh toán')}
                        </span>
                      </div>
                      <div>
                        <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>Trạng thái giao hàng:</span>
                        <span style={{ color: '#0f172a', fontWeight: 500 }}>
                          {order.fulfilmentStatus || 'Chưa bàn giao'}
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
                        Chi tiết đơn hàng
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
                          {actionLoading ? 'Đang xử lý...' : `Thanh toán toàn bộ (${formatCurrency(quote?.totalAmount)})`}
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
                          {actionLoading ? 'Đang gửi...' : 'Yêu cầu báo giá mới'}
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
              <p style={{ color: '#64748b', marginBottom: '1rem' }}>Bạn chưa gửi yêu cầu thiết kế tùy chỉnh nào.</p>
              <Link to="/design-requests/new" className="button button-primary">
                Tạo yêu cầu ngay
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
                        <span style={{ fontSize: '0.85rem', color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Mã yêu cầu</span>
                        <h3 style={{ fontSize: '1.15rem', fontWeight: 600, color: '#0f172a', margin: '0.2rem 0' }}>
                          {req.requestReference || req.id}
                        </h3>
                        <span style={{ fontSize: '0.8rem', color: '#94a3b8' }}>
                          Phiên bản: #{req.version || req.requestVersion || 1} &bull; Cập nhật: {formatDate(req.updatedAt || req.createdAt)}
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
                          {status === 'ACCEPTED' ? 'Đã chấp nhận' :
                           status === 'REJECTED' ? 'Đã từ chối' :
                           status === 'CLARIFICATION_REQUIRED' ? 'Cần làm rõ thông tin' : 'Đang chờ thẩm duyệt'}
                        </span>
                      </div>
                    </div>

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
                          {isClarification ? 'Yêu cầu làm rõ từ nhân viên thẩm duyệt:' : 'Phản hồi thẩm duyệt:'}
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
                          Phản hồi làm rõ
                        </button>
                      )}
                      <Link
                        to={`/design-requests/${req.id || req.requestReference}`}
                        className="button button-outline"
                        style={{ padding: '0.45rem 1rem', fontSize: '0.875rem' }}
                      >
                        Xem chi tiết
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
              Phản hồi làm rõ yêu cầu ({clarifyingRequest.requestReference})
            </h3>
            <p style={{ fontSize: '0.9rem', color: '#64748b', marginBottom: '1rem' }}>
              Vui lòng bổ sung hoặc làm rõ chi tiết thiết kế theo yêu cầu của đội ngũ nghệ nhân:
            </p>

            {clarifyError && (
              <div className="alert-banner alert-banner-danger" style={{ marginBottom: '1rem' }}>
                <p>{clarifyError}</p>
              </div>
            )}

            <form onSubmit={handleSubmitClarification}>
              <div style={{ marginBottom: '1rem' }}>
                <label style={{ display: 'block', fontWeight: 500, fontSize: '0.9rem', marginBottom: '0.35rem' }}>
                  Nội dung phản hồi / làm rõ:
                </label>
                <textarea
                  rows={4}
                  value={clarificationText}
                  onChange={(e) => setClarificationText(e.target.value)}
                  placeholder="Nhập chi tiết điều chỉnh kích thước, chất liệu, hoặc ghi chú thêm..."
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
                  Hủy
                </button>
                <button
                  type="submit"
                  className="button button-primary"
                  disabled={actionLoading}
                >
                  {actionLoading ? 'Đang gửi...' : 'Gửi phản hồi'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
