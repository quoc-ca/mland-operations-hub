import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { Link } from 'react-router';
import { useHttp } from '../../../shared/api/useHttp';
import { designReviewApi } from '../api/designReviewApi';
import {
  ORDER_STAGES,
  getOrderStage,
  getStageConfig,
  getOrderBadgeInfo,
  getProductionStatusLabel,
  getFulfilmentStatusLabel,
} from '../customOrderStatus';
import DesignImage from '../components/DesignImage';
import '../designreview.css';

const formatVND = (val) => {
  if (!val && val !== 0) return '---';
  try {
    return new Intl.NumberFormat('vi-VN').format(Number(val)) + ' ₫';
  } catch {
    return `${val} ₫`;
  }
};

const TABS = [
  { id: 'PAID', vi: 'Chờ chế tác', en: 'Awaiting production' },
  { id: 'IN_PRODUCTION', vi: 'Đang gia công', en: 'In production' },
  { id: 'READY_FOR_FULFILMENT', vi: 'Sẵn sàng giao hàng', en: 'Ready for fulfilment' },
  { id: 'CLOSED', vi: 'Đã hoàn tất', en: 'Completed' },
  { id: 'ALL', vi: 'Tất cả đơn', en: 'All orders' },
];

export default function StaffCustomOrdersPage() {
  const http = useHttp();
  const api = useMemo(() => designReviewApi(http), [http]);

  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activeTab, setActiveTab] = useState('PAID');
  const [search, setSearch] = useState('');
  const [actionBusyId, setActionBusyId] = useState(null);
  const [notification, setNotification] = useState(null);
  const [activeLightboxImg, setActiveLightboxImg] = useState(null);
  const [expandedOrders, setExpandedOrders] = useState({});

  // Confirmation modal state for radio button stage transition
  const [pendingTransition, setPendingTransition] = useState(null);

  const loadOrders = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.listOperationalCustomOrders({ status: '' });
      setOrders(Array.isArray(data) ? data : (data?.items || []));
    } catch (err) {
      setError(err.message || 'Không thể tải danh sách đơn gia công.');
    } finally {
      setLoading(false);
    }
  }, [api]);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  // Accurate stage counts based on normalized order stage
  const counts = useMemo(() => {
    const map = { PAID: 0, IN_PRODUCTION: 0, READY_FOR_FULFILMENT: 0, CLOSED: 0, ALL: orders.length };
    orders.forEach((o) => {
      const stage = getOrderStage(o);
      if (map[stage] !== undefined) {
        map[stage] = (map[stage] || 0) + 1;
      }
    });
    return map;
  }, [orders]);

  // Filter orders according to active stage tab and search query
  const filteredOrders = useMemo(() => {
    const term = search.trim().toLowerCase();
    return orders.filter((o) => {
      const stage = getOrderStage(o);
      // Filter by stage tab
      if (activeTab !== 'ALL' && stage !== activeTab) {
        return false;
      }
      // Search filter
      if (!term) return true;
      const ref = (o.customOrderReference || '').toLowerCase();
      const drRef = (o.designRequestReference || '').toLowerCase();
      const customer = (o.customerName || '').toLowerCase();
      const email = (o.customerEmail || '').toLowerCase();
      const desc = (o.designOriginalDescription || '').toLowerCase();
      return (
        ref.includes(term) ||
        drRef.includes(term) ||
        customer.includes(term) ||
        email.includes(term) ||
        desc.includes(term)
      );
    });
  }, [orders, activeTab, search]);

  const toggleExpand = (id) => {
    setExpandedOrders((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  // Perform status update
  const executeStatusUpdate = async (orderRef, targetStage) => {
    setActionBusyId(orderRef);
    setNotification(null);
    try {
      await api.updateCustomOrderStatus(orderRef, {
        productionStatus: targetStage.productionStatus,
        fulfilmentStatus: targetStage.fulfilmentStatus,
        status: targetStage.status,
      });
      setNotification({
        type: 'success',
        message: `Đơn hàng ${orderRef} đã chuyển sang trạng thái "${targetStage.label}" thành công.`,
      });
      await loadOrders();
    } catch (err) {
      setNotification({
        type: 'error',
        message: err.message || `Cập nhật trạng thái cho ${orderRef} thất bại.`,
      });
    } finally {
      setActionBusyId(null);
      setPendingTransition(null);
    }
  };

  // Triggered when user confirms in the popup modal
  const handleConfirmTransition = async () => {
    if (!pendingTransition) return;
    const { order, targetStage } = pendingTransition;
    await executeStatusUpdate(order.customOrderReference, targetStage);
  };

  return (
    <div className="wsp-workspace" style={{ padding: '1.5rem 2rem', maxWidth: 1200, margin: '0 auto' }}>
      {/* Top Breadcrumb & Heading */}
      <div className="wsp-topline" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
        <span style={{ fontSize: '0.85rem', color: '#64748b' }}>
          <Link to="/home" style={{ color: '#0284c7', textDecoration: 'none' }}>Trang chủ</Link>
          {' '}/ Quản lý đơn gia công tùy chỉnh (Custom Orders)
        </span>
        <span style={{ fontSize: '0.85rem', color: '#16a34a', fontWeight: 500 }}>● Đã xác thực nội bộ</span>
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem', marginBottom: '1.5rem' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 700, color: '#0f172a', margin: '0 0 0.25rem 0' }}>
            Điều phối & Chế tác Đơn hàng Custom
          </h1>
          <p style={{ margin: 0, color: '#475569', fontSize: '0.95rem' }}>
            Theo dõi tiến độ, chuẩn bị chế tác tại xưởng và hoàn tất giao hàng cho các đơn custom đã thanh toán.
          </p>
        </div>
        <button
          type="button"
          onClick={loadOrders}
          disabled={loading}
          style={{
            padding: '0.5rem 1.25rem',
            borderRadius: 6,
            border: '1px solid #cbd5e1',
            background: '#ffffff',
            color: '#1e293b',
            fontWeight: 600,
            cursor: loading ? 'not-allowed' : 'pointer',
            boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
          }}
        >
          {loading ? 'Đang tải...' : '🔄 Làm mới'}
        </button>
      </div>

      {/* Notifications */}
      {notification && (
        <div
          style={{
            padding: '0.75rem 1rem',
            borderRadius: 8,
            marginBottom: '1.25rem',
            backgroundColor: notification.type === 'success' ? '#dcfce7' : '#fee2e2',
            color: notification.type === 'success' ? '#166534' : '#991b1b',
            border: `1px solid ${notification.type === 'success' ? '#bbf7d0' : '#fecaca'}`,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <span>{notification.message}</span>
          <button
            type="button"
            onClick={() => setNotification(null)}
            style={{ background: 'transparent', border: 'none', cursor: 'pointer', fontWeight: 'bold' }}
          >
            ✕
          </button>
        </div>
      )}

      {error && (
        <div style={{ padding: '1rem', background: '#fee2e2', color: '#991b1b', borderRadius: 8, marginBottom: '1.25rem' }}>
          {error}
        </div>
      )}

      {/* Summary KPI Cards */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
          gap: '1rem',
          marginBottom: '1.5rem',
        }}
      >
        <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 8, padding: '1rem' }}>
          <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>Chờ chuẩn bị chế tác</span>
          <strong style={{ fontSize: '1.5rem', color: '#0284c7' }}>{counts.PAID || 0}</strong>
        </div>
        <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 8, padding: '1rem' }}>
          <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>Đang gia công tại xưởng</span>
          <strong style={{ fontSize: '1.5rem', color: '#b45309' }}>{counts.IN_PRODUCTION || 0}</strong>
        </div>
        <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 8, padding: '1rem' }}>
          <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>Sẵn sàng giao hàng</span>
          <strong style={{ fontSize: '1.5rem', color: '#15803d' }}>{counts.READY_FOR_FULFILMENT || 0}</strong>
        </div>
        <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 8, padding: '1rem' }}>
          <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>Đã hoàn tất</span>
          <strong style={{ fontSize: '1.5rem', color: '#334155' }}>{counts.CLOSED || 0}</strong>
        </div>
      </div>

      {/* Search Toolbar */}
      <div style={{ display: 'flex', gap: '1rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Tìm theo mã đơn CO-, mã yêu cầu DR-, tên hoặc email khách..."
          style={{
            flex: 1,
            minWidth: 260,
            padding: '0.65rem 1rem',
            border: '1px solid #cbd5e1',
            borderRadius: 6,
            fontSize: '0.95rem',
          }}
        />
      </div>

      {/* Stage Tabs */}
      <div
        role="tablist"
        style={{
          display: 'flex',
          gap: '0.5rem',
          borderBottom: '2px solid #e2e8f0',
          marginBottom: '1.5rem',
          overflowX: 'auto',
        }}
      >
        {TABS.map((tab) => {
          const isActive = activeTab === tab.id;
          const count = counts[tab.id] || 0;
          return (
            <button
              key={tab.id}
              type="button"
              role="tab"
              aria-selected={isActive}
              onClick={() => setActiveTab(tab.id)}
              style={{
                background: 'none',
                border: 'none',
                padding: '0.75rem 1rem',
                fontSize: '0.95rem',
                fontWeight: isActive ? 700 : 500,
                color: isActive ? '#0f172a' : '#64748b',
                borderBottom: isActive ? '3px solid #0284c7' : '3px solid transparent',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem',
                whiteSpace: 'nowrap',
                marginBottom: -2,
              }}
            >
              <span>{tab.vi}</span>
              {tab.id !== 'ALL' && (
                <span
                  style={{
                    backgroundColor: isActive ? '#0284c7' : '#e2e8f0',
                    color: isActive ? '#ffffff' : '#475569',
                    fontSize: '0.75rem',
                    padding: '0.1rem 0.5rem',
                    borderRadius: 9999,
                    fontWeight: 600,
                  }}
                >
                  {count}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* Orders List */}
      {filteredOrders.length === 0 ? (
        <div
          style={{
            textAlign: 'center',
            padding: '3rem 1rem',
            background: '#f8fafc',
            borderRadius: 8,
            border: '1px dashed #cbd5e1',
            color: '#64748b',
          }}
        >
          <div style={{ fontSize: '2.5rem', marginBottom: '0.5rem' }}>📦</div>
          <p style={{ margin: 0, fontWeight: 500 }}>Không tìm thấy đơn gia công nào trong danh mục này.</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          {filteredOrders.map((order) => {
            const isBusy = actionBusyId === order.customOrderReference;
            const quote = order.quote;
            const snapshot = order.designSnapshot;
            const isExpanded = !!expandedOrders[order.customOrderReference];
            const amountVnd = quote?.totalAmount ? formatVND(Number(quote.totalAmount)) : '---';

            // Normalized stage and badge
            const currentStageKey = getOrderStage(order);
            const currentStageConfig = getStageConfig(currentStageKey);
            const badgeInfo = getOrderBadgeInfo(order);

            return (
              <div
                key={order.customOrderReference}
                style={{
                  border: '1px solid #e2e8f0',
                  borderRadius: 10,
                  background: '#ffffff',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
                  overflow: 'hidden',
                }}
              >
                {/* Order Card Header */}
                <div
                  style={{
                    padding: '1rem 1.25rem',
                    background: '#f8fafc',
                    borderBottom: '1px solid #e2e8f0',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    flexWrap: 'wrap',
                    gap: '0.75rem',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
                    <strong style={{ fontSize: '1.15rem', color: '#0f172a' }}>
                      {order.customOrderReference}
                    </strong>
                    <span
                      style={{
                        display: 'inline-block',
                        padding: '0.3rem 0.75rem',
                        borderRadius: 9999,
                        fontSize: '0.8rem',
                        fontWeight: 600,
                        color: badgeInfo.color,
                        backgroundColor: badgeInfo.bg,
                      }}
                    >
                      {badgeInfo.text}
                    </span>

                    {/* DR Linking Badge */}
                    {order.designRequestId && (
                      <Link
                        to={`/design-review-requests/${encodeURIComponent(order.designRequestId)}`}
                        style={{
                          fontSize: '0.8rem',
                          color: '#0284c7',
                          background: '#f0f9ff',
                          border: '1px solid #bae6fd',
                          padding: '0.2rem 0.6rem',
                          borderRadius: 4,
                          textDecoration: 'none',
                          fontWeight: 600,
                        }}
                        title="Xem chi tiết hồ sơ thẩm duyệt thiết kế gốc"
                      >
                        🔗 {order.designRequestReference || order.designRequestId}
                      </Link>
                    )}
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                    <span style={{ fontSize: '0.85rem', color: '#64748b' }}>
                      Ngày tạo: {order.createdAt ? new Date(order.createdAt).toLocaleDateString('vi-VN') : '---'}
                    </span>
                    <strong style={{ fontSize: '1.1rem', color: '#0f172a' }}>
                      {amountVnd}
                    </strong>
                  </div>
                </div>

                {/* Order Card Body */}
                <div style={{ padding: '1.25rem' }}>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1.25rem' }}>
                    {/* Column 1: Customer & Delivery Info */}
                    <div>
                      <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '0.9rem', color: '#475569', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                        Thông tin khách hàng & Bàn giao
                      </h4>
                      <p style={{ margin: '0 0 0.35rem 0', fontSize: '0.95rem', color: '#1e293b' }}>
                        <strong>Khách hàng:</strong> {order.customerName || (order.memberAccountId ? `Thành viên #${order.memberAccountId}` : 'Khách vãng lai')}
                      </p>
                      {order.customerEmail && (
                        <p style={{ margin: '0 0 0.35rem 0', fontSize: '0.9rem', color: '#475569' }}>
                          <strong>Email:</strong> {order.customerEmail}
                        </p>
                      )}
                      {order.shippingAddress && (
                        <p style={{ margin: '0 0 0.35rem 0', fontSize: '0.9rem', color: '#475569' }}>
                          <strong>Địa chỉ nhận:</strong> {order.shippingAddress}
                        </p>
                      )}
                      {order.designOriginalDescription && (
                        <div style={{ marginTop: '0.5rem', background: '#f8fafc', padding: '0.65rem', borderRadius: 6, border: '1px solid #f1f5f9' }}>
                          <span style={{ fontSize: '0.8rem', color: '#64748b', display: 'block', fontWeight: 600 }}>Mô tả yêu cầu gốc của khách:</span>
                          <span style={{ fontSize: '0.85rem', color: '#334155' }}>{order.designOriginalDescription}</span>
                        </div>
                      )}
                    </div>

                    {/* Column 2: Design Specifications & Preview */}
                    <div>
                      <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '0.9rem', color: '#475569', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                        Chi tiết mẫu thiết kế
                      </h4>
                      {snapshot ? (
                        <div style={{ fontSize: '0.9rem', color: '#334155', display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                          {snapshot.metalType && <div><strong>Chất liệu:</strong> {snapshot.metalType}</div>}
                          {snapshot.gemstoneType && <div><strong>Đá chủ:</strong> {snapshot.gemstoneType}</div>}
                          {snapshot.ringSize && <div><strong>Size nhẫn:</strong> {snapshot.ringSize}</div>}
                          {snapshot.engravingText && <div><strong>Khắc chữ:</strong> "{snapshot.engravingText}"</div>}
                        </div>
                      ) : (
                        <p style={{ fontSize: '0.9rem', color: '#64748b', fontStyle: 'italic' }}>
                          Thiết kế mẫu nhẫn chế tác thủ công theo yêu cầu thẩm duyệt.
                        </p>
                      )}

                      {/* Customer uploaded design reference images */}
                      {order.designImages && order.designImages.length > 0 && (
                        <div style={{ marginTop: '0.75rem' }}>
                          <span style={{ fontSize: '0.8rem', color: '#64748b', display: 'block', marginBottom: '0.35rem' }}>
                            Ảnh mẫu tham khảo ({order.designImages.length} ảnh):
                          </span>
                          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                            {order.designImages.map((img, idx) => {
                              return (
                                <DesignImage
                                  key={img.imageReference || idx}
                                  image={img}
                                  alt={img.originalFilename || `Ảnh tham khảo ${idx + 1}`}
                                  onClick={() => setActiveLightboxImg(img)}
                                  style={{
                                    width: 52,
                                    height: 52,
                                    objectFit: 'cover',
                                    borderRadius: 4,
                                    border: '1px solid #cbd5e1',
                                    cursor: 'pointer',
                                    transition: 'transform 0.15s ease',
                                  }}
                                  title="Bấm để phóng to xem chi tiết"
                                />
                              );
                            })}
                          </div>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Interactive Radio Button Stage Switcher */}
                  <div
                    className="order-stage-switcher"
                    style={{
                      marginTop: '1.25rem',
                      padding: '1rem',
                      background: '#f8fafc',
                      borderRadius: 8,
                      border: '1px solid #e2e8f0',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem', flexWrap: 'wrap', gap: '0.5rem' }}>
                      <span style={{ fontSize: '0.85rem', fontWeight: 600, color: '#475569', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                        Chuyển chặng chế tác & Bàn giao (Chọn radio để đổi trạng thái):
                      </span>
                      <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                        Hiện tại: <strong style={{ color: currentStageConfig.color }}>{currentStageConfig.label}</strong>
                      </span>
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.75rem' }}>
                      {ORDER_STAGES.map((stage) => {
                        const isCurrent = currentStageKey === stage.key;
                        // The server only accepts the next stage of a paid order.
                        const isNext = ORDER_STAGES[ORDER_STAGES.findIndex((s) => s.key === currentStageKey) + 1]?.key === stage.key
                          && ORDER_STAGES.some((s) => s.key === currentStageKey);
                        return (
                          <label
                            key={stage.key}
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: '0.6rem',
                              padding: '0.65rem 0.85rem',
                              borderRadius: 6,
                              border: isCurrent ? `2px solid ${stage.color}` : '1px solid #cbd5e1',
                              background: isCurrent ? '#ffffff' : '#f8fafc',
                              cursor: isNext ? 'pointer' : 'default',
                              boxShadow: isCurrent ? '0 1px 3px rgba(0,0,0,0.08)' : 'none',
                              transition: 'all 0.15s ease',
                            }}
                            title={isCurrent ? 'Trạng thái hiện tại của đơn hàng' : isNext ? `Bấm để chuyển đơn sang ${stage.label}` : undefined}
                          >
                            <input
                              type="radio"
                              name={`stage-${order.customOrderReference}`}
                              value={stage.key}
                              checked={isCurrent}
                              disabled={isBusy || (!isCurrent && !isNext)}
                              onChange={() => {
                                if (!isCurrent) {
                                  setPendingTransition({
                                    order,
                                    targetStage: stage,
                                    currentStageConfig,
                                  });
                                }
                              }}
                              style={{ width: 17, height: 17, cursor: isNext ? 'pointer' : 'default', accentColor: stage.color }}
                            />
                            <div>
                              <div style={{ fontSize: '0.88rem', fontWeight: isCurrent ? 700 : 500, color: isCurrent ? stage.color : '#334155' }}>
                                {stage.label}
                              </div>
                              {isCurrent && (
                                <span style={{ fontSize: '0.72rem', color: '#16a34a', fontWeight: 600 }}>● Đang ở bước này</span>
                              )}
                            </div>
                          </label>
                        );
                      })}
                    </div>

                    {/* Operational Quick Action Buttons (One-click advance) */}
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '1rem', paddingTop: '0.75rem', borderTop: '1px solid #e2e8f0', flexWrap: 'wrap', gap: '0.5rem' }}>
                      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                        {currentStageKey === 'PAID' && (
                          <button
                            type="button"
                            disabled={isBusy}
                            onClick={() =>
                              setPendingTransition({
                                order,
                                targetStage: getStageConfig('IN_PRODUCTION'),
                                currentStageConfig,
                              })
                            }
                            style={{
                              padding: '0.5rem 1rem',
                              backgroundColor: '#0284c7',
                              color: '#ffffff',
                              border: 'none',
                              borderRadius: 6,
                              fontWeight: 600,
                              fontSize: '0.85rem',
                              cursor: isBusy ? 'not-allowed' : 'pointer',
                            }}
                          >
                            🔨 Bắt đầu chế tác
                          </button>
                        )}

                        {currentStageKey === 'IN_PRODUCTION' && (
                          <button
                            type="button"
                            disabled={isBusy}
                            onClick={() =>
                              setPendingTransition({
                                order,
                                targetStage: getStageConfig('READY_FOR_FULFILMENT'),
                                currentStageConfig,
                              })
                            }
                            style={{
                              padding: '0.5rem 1rem',
                              backgroundColor: '#16a34a',
                              color: '#ffffff',
                              border: 'none',
                              borderRadius: 6,
                              fontWeight: 600,
                              fontSize: '0.85rem',
                              cursor: isBusy ? 'not-allowed' : 'pointer',
                            }}
                          >
                            📦 Hoàn thành chế tác & Sẵn sàng giao
                          </button>
                        )}

                        {currentStageKey === 'READY_FOR_FULFILMENT' && (
                          <button
                            type="button"
                            disabled={isBusy}
                            onClick={() =>
                              setPendingTransition({
                                order,
                                targetStage: getStageConfig('CLOSED'),
                                currentStageConfig,
                              })
                            }
                            style={{
                              padding: '0.5rem 1rem',
                              backgroundColor: '#334155',
                              color: '#ffffff',
                              border: 'none',
                              borderRadius: 6,
                              fontWeight: 600,
                              fontSize: '0.85rem',
                              cursor: isBusy ? 'not-allowed' : 'pointer',
                            }}
                          >
                            ✅ Xác nhận đã bàn giao / Giao hàng
                          </button>
                        )}
                      </div>

                      <button
                        type="button"
                        onClick={() => toggleExpand(order.customOrderReference)}
                        style={{
                          padding: '0.5rem 0.75rem',
                          backgroundColor: '#ffffff',
                          color: '#475569',
                          border: '1px solid #cbd5e1',
                          borderRadius: 6,
                          fontWeight: 500,
                          fontSize: '0.85rem',
                          cursor: 'pointer',
                        }}
                      >
                        {isExpanded ? 'Ẩn chi tiết ▲' : 'Xem thêm ▼'}
                      </button>
                    </div>
                  </div>

                  {/* Expanded Details Drawer */}
                  {isExpanded && (
                    <div
                      style={{
                        marginTop: '1rem',
                        padding: '1rem',
                        background: '#f1f5f9',
                        borderRadius: 6,
                        border: '1px solid #e2e8f0',
                        fontSize: '0.85rem',
                        color: '#334155',
                      }}
                    >
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '0.75rem' }}>
                        <div><strong>Mã báo giá:</strong> {quote?.quoteReference || 'Chưa có'}</div>
                        <div><strong>Hạn báo giá:</strong> {quote?.expiresAt ? new Date(quote.expiresAt).toLocaleString('vi-VN') : 'Không có'}</div>
                        <div><strong>Trạng thái sản xuất:</strong> {getProductionStatusLabel(order.productionStatus)}</div>
                        <div><strong>Trạng thái giao nhận:</strong> {getFulfilmentStatusLabel(order.fulfilmentStatus)}</div>
                        {order.staffExplanation && (
                          <div style={{ gridColumn: '1 / -1' }}>
                            <strong>Ghi chú chuyên viên:</strong> {order.staffExplanation}
                          </div>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Confirmation Modal Popup for Status Transition */}
      {pendingTransition && (
        <div
          role="dialog"
          aria-modal="true"
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.65)',
            zIndex: 9999,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '1.5rem',
          }}
        >
          <div
            style={{
              backgroundColor: '#ffffff',
              borderRadius: 12,
              maxWidth: 520,
              width: '100%',
              padding: '1.75rem',
              boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
              border: '1px solid #e2e8f0',
            }}
          >
            <h3 style={{ margin: '0 0 1rem 0', fontSize: '1.25rem', fontWeight: 700, color: '#0f172a', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span>🔔</span> Xác nhận thay đổi trạng thái đơn hàng
            </h3>
            <p style={{ margin: '0 0 1rem 0', fontSize: '0.95rem', color: '#334155', lineHeight: 1.5 }}>
              Bạn có chắc chắn muốn chuyển trạng thái đơn hàng <strong>{pendingTransition.order.customOrderReference}</strong>?
            </p>

            <div
              style={{
                background: '#f8fafc',
                padding: '1rem',
                borderRadius: 8,
                border: '1px solid #e2e8f0',
                marginBottom: '1rem',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <div>
                <span style={{ fontSize: '0.75rem', color: '#64748b', display: 'block', textTransform: 'uppercase' }}>Trạng thái hiện tại:</span>
                <strong style={{ color: pendingTransition.currentStageConfig.color, fontSize: '0.95rem' }}>
                  {pendingTransition.currentStageConfig.label}
                </strong>
              </div>
              <span style={{ fontSize: '1.5rem', color: '#94a3b8' }}>➔</span>
              <div>
                <span style={{ fontSize: '0.75rem', color: '#64748b', display: 'block', textTransform: 'uppercase' }}>Trạng thái mới:</span>
                <strong style={{ color: pendingTransition.targetStage.color, fontSize: '0.95rem' }}>
                  {pendingTransition.targetStage.label}
                </strong>
              </div>
            </div>

            <p style={{ margin: '0 0 1.5rem 0', fontSize: '0.85rem', color: '#64748b', lineHeight: 1.4 }}>
              <strong>Mô tả:</strong> {pendingTransition.targetStage.desc}.
              <br />
              Sau khi xác nhận, đơn sẽ tự động chuyển sang tab <strong>"{pendingTransition.targetStage.label}"</strong> và cập nhật đồng bộ cho cả Quản lý và Khách hàng.
            </p>

            <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end' }}>
              <button
                type="button"
                onClick={() => setPendingTransition(null)}
                disabled={actionBusyId}
                style={{
                  padding: '0.6rem 1.25rem',
                  background: '#f1f5f9',
                  border: '1px solid #cbd5e1',
                  borderRadius: 6,
                  fontWeight: 600,
                  cursor: 'pointer',
                  color: '#475569',
                }}
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                onClick={handleConfirmTransition}
                disabled={actionBusyId}
                style={{
                  padding: '0.6rem 1.5rem',
                  background: pendingTransition.targetStage.color,
                  border: 'none',
                  borderRadius: 6,
                  fontWeight: 600,
                  cursor: actionBusyId ? 'not-allowed' : 'pointer',
                  color: '#ffffff',
                  boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
                }}
              >
                {actionBusyId ? 'Đang cập nhật...' : 'Xác nhận chuyển'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Image Lightbox Modal */}
      {activeLightboxImg && (
        <div
          onClick={() => setActiveLightboxImg(null)}
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.75)',
            zIndex: 9999,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '2rem',
          }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              position: 'relative',
              maxWidth: '90vw',
              maxHeight: '90vh',
              background: '#ffffff',
              borderRadius: 8,
              overflow: 'hidden',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.5)',
            }}
          >
            <button
              type="button"
              onClick={() => setActiveLightboxImg(null)}
              style={{
                position: 'absolute',
                top: '0.5rem',
                right: '0.5rem',
                backgroundColor: 'rgba(0,0,0,0.6)',
                color: '#fff',
                border: 'none',
                borderRadius: '50%',
                width: 32,
                height: 32,
                cursor: 'pointer',
                fontWeight: 'bold',
                fontSize: '1rem',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              ✕
            </button>
            <DesignImage
              image={activeLightboxImg}
              alt="Ảnh phóng to"
              style={{ maxWidth: '85vw', maxHeight: '80vh', objectFit: 'contain', display: 'block' }}
            />
          </div>
        </div>
      )}
    </div>
  );
}
