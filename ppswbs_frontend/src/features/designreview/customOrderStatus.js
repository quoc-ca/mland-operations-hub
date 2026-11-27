/**
 * Unified Custom Order status classification & localization helpers for Feature-004 / Custom Manufacturing.
 * Used across Staff Operations, Manager Operations, and Member My Custom Orders views.
 */

export const ORDER_STAGES = [
  {
    key: 'QUOTE_PENDING',
    label: 'Chờ Quản lý duyệt',
    enLabel: 'Awaiting manager approval',
    desc: 'Đã gửi báo giá, đang chờ Quản lý phê duyệt',
    productionStatus: null,
    fulfilmentStatus: null,
    status: 'QUOTE_PENDING',
    color: '#ea580c',
    bg: '#ffedd5',
  },
  {
    key: 'FULL_PAYMENT_PENDING',
    label: 'Chờ khách thanh toán',
    enLabel: 'Awaiting customer payment',
    desc: 'Quản lý đã duyệt báo giá, chờ hội viên thanh toán 100%',
    productionStatus: null,
    fulfilmentStatus: null,
    status: 'FULL_PAYMENT_PENDING',
    color: '#d97706',
    bg: '#fef3c7',
  },
  {
    key: 'PAID',
    label: 'Chờ chế tác',
    enLabel: 'Awaiting production',
    desc: 'Đã thanh toán 100%, chờ đưa vào xưởng chế tác',
    productionStatus: 'PENDING',
    fulfilmentStatus: null,
    status: 'PAID',
    color: '#0284c7',
    bg: '#e0f2fe',
  },
  {
    key: 'IN_PRODUCTION',
    label: 'Đang gia công',
    enLabel: 'In production',
    desc: 'Đang chế tác thủ công tại xưởng kim hoàn',
    productionStatus: 'IN_PRODUCTION',
    fulfilmentStatus: null,
    status: 'IN_PRODUCTION',
    color: '#b45309',
    bg: '#fef3c7',
  },
  {
    key: 'READY_FOR_FULFILMENT',
    label: 'Sẵn sàng giao hàng',
    enLabel: 'Ready for fulfilment',
    desc: 'Đã chế tác xong, sẵn sàng đóng gói và bàn giao / giao hàng',
    productionStatus: 'COMPLETED',
    fulfilmentStatus: 'READY_FOR_FULFILMENT',
    status: 'READY_FOR_FULFILMENT',
    color: '#15803d',
    bg: '#dcfce7',
  },
  {
    key: 'DELIVERING',
    label: 'Đang vận chuyển',
    enLabel: 'Delivering',
    desc: 'Bưu tá GHTK đã lấy hàng và đang trên đường giao tới bạn',
    productionStatus: 'COMPLETED',
    fulfilmentStatus: 'HANDED_TO_CARRIER',
    status: 'DELIVERING',
    color: '#0891b2',
    bg: '#cffafe',
  },
  {
    key: 'CLOSED',
    label: 'Đã hoàn tất',
    enLabel: 'Completed',
    desc: 'Khách hàng đã nhận sản phẩm thành công / hoàn tất đơn hàng',
    productionStatus: 'COMPLETED',
    fulfilmentStatus: 'DELIVERED',
    status: 'CLOSED',
    color: '#334155',
    bg: '#e2e8f0',
  },
];

/**
 * Normalizes an order's state into one of the 4 operational stages
 * or its pre-payment status (FULL_PAYMENT_PENDING, QUOTE_PENDING, etc.).
 */
export function getOrderStage(order) {
  if (!order) return 'UNKNOWN';

  const status = (order.status || '').toUpperCase();
  const prodStatus = (order.productionStatus || '').toUpperCase();
  const fulfStatus = (order.fulfilmentStatus || '').toUpperCase();
  const quoteStatus = (order.quote?.status || '').toUpperCase();

  // Closed / Delivered / Completed
  if (status === 'CLOSED' || status === 'COMPLETED' || fulfStatus === 'DELIVERED') {
    return 'CLOSED';
  }

  // Delivering / In Transit / Handed to carrier
  if (status === 'DELIVERING' || fulfStatus === 'HANDED_TO_CARRIER' || fulfStatus === 'IN_TRANSIT') {
    return 'DELIVERING';
  }

  // Ready for delivery / pickup
  if (status === 'READY_FOR_FULFILMENT' || fulfStatus === 'READY_FOR_FULFILMENT' || fulfStatus === 'READY_FOR_CARRIER') {
    return 'READY_FOR_FULFILMENT';
  }

  // In production crafting
  if (status === 'IN_PRODUCTION' || prodStatus === 'IN_PRODUCTION') {
    return 'IN_PRODUCTION';
  }

  // Awaiting production / Paid
  if (status === 'PAID' || quoteStatus === 'PAID' || prodStatus === 'PENDING') {
    return 'PAID';
  }

  // Awaiting customer full payment
  if (status === 'FULL_PAYMENT_PENDING' || quoteStatus === 'QUOTE_READY') {
    return 'FULL_PAYMENT_PENDING';
  }

  // Quote waiting for manager approval
  if (status === 'QUOTE_PENDING' || quoteStatus === 'QUOTE_PENDING') {
    return 'QUOTE_PENDING';
  }

  return status || 'QUOTE_PENDING';
}

/**
 * Returns stage configuration object by key.
 */
export function getStageConfig(stageKey) {
  return ORDER_STAGES.find((s) => s.key === stageKey) || {
    key: stageKey,
    label: stageKey,
    enLabel: stageKey,
    desc: '',
    color: '#64748b',
    bg: '#f1f5f9',
  };
}

/**
 * Friendly label for production status.
 */
export function getProductionStatusLabel(prodStatus, isPaid = true) {
  if (!prodStatus) return isPaid ? 'Chờ chế tác' : 'Chưa bắt đầu';
  const s = String(prodStatus).toUpperCase();
  if (s === 'PENDING') return 'Chờ chế tác';
  if (s === 'IN_PRODUCTION') return 'Đang gia công tại xưởng';
  if (s === 'COMPLETED') return 'Đã hoàn thành chế tác';
  return prodStatus;
}

/**
 * Friendly label for fulfilment status.
 */
export function getFulfilmentStatusLabel(fulfStatus) {
  if (!fulfStatus) return 'Chưa bàn giao';
  const s = String(fulfStatus).toUpperCase();
  if (s === 'READY_FOR_FULFILMENT') return 'Sẵn sàng giao hàng';
  if (s === 'READY_FOR_CARRIER') return 'Chờ bưu tá lấy';
  if (s === 'HANDED_TO_CARRIER' || s === 'IN_TRANSIT') return 'Bưu tá đang giao';
  if (s === 'DELIVERED') return 'Đã giao hàng thành công';
  return fulfStatus;
}

/**
 * Resolves badge display text and colors for both internal staff and members.
 */
export function getOrderBadgeInfo(order) {
  if (!order) return { text: 'Không rõ', color: '#64748b', bg: '#f1f5f9' };

  const stage = getOrderStage(order);
  const quote = order.quote;
  const expired = quote?.expiresAt ? new Date() > new Date(quote.expiresAt) || quote.status === 'QUOTE_EXPIRED' : false;

  switch (stage) {
    case 'PAID':
      return { text: 'Đã thanh toán (Chờ chế tác)', color: '#0284c7', bg: '#e0f2fe' };
    case 'IN_PRODUCTION':
      return { text: 'Đang gia công', color: '#b45309', bg: '#fef3c7' };
    case 'READY_FOR_FULFILMENT':
      return { text: 'Sẵn sàng giao hàng', color: '#15803d', bg: '#dcfce7' };
    case 'DELIVERING':
      return { text: 'Đang vận chuyển (GHTK)', color: '#0891b2', bg: '#cffafe' };
    case 'CLOSED':
      return { text: 'Đã hoàn tất', color: '#334155', bg: '#e2e8f0' };
    case 'FULL_PAYMENT_PENDING':
      return expired
        ? { text: 'Báo giá hết hạn', color: '#dc2626', bg: '#fee2e2' }
        : { text: 'Chờ thanh toán (100%)', color: '#d97706', bg: '#fef3c7' };
    case 'QUOTE_PENDING':
      return { text: 'Chờ Quản lý duyệt báo giá', color: '#ea580c', bg: '#ffedd5' };
    case 'QUOTE_EXPIRED':
      return { text: 'Báo giá hết hạn', color: '#dc2626', bg: '#fee2e2' };
    default:
      return { text: stage, color: '#64748b', bg: '#f1f5f9' };
  }
}
