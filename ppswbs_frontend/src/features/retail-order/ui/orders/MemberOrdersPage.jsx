import React, { useMemo } from 'react';
import { Link } from 'react-router';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useFormat } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useCommand, useResource } from '../../../../shared/ui/components';
import { useRetailApi } from '../../application/useRetailApi';

const paymentLabels = {
  ACCEPTED_UNPAID: 'Chờ thanh toán',
  PAYMENT_INITIATED: 'Đang thanh toán VNPay',
  PAID: 'Đã thanh toán',
  EXPIRED: 'Hết hạn giữ hàng',
  CANCELLED: 'Đã hủy',
};
const fulfilmentLabels = {
  READY_FOR_PICKUP: 'Sẵn sàng nhận tại cửa hàng',
  PICKED_UP: 'Đã nhận hàng',
  SHIPMENT_PENDING: 'Đang chuẩn bị giao',
  HANDED_TO_CARRIER: 'Đã giao cho GHTK',
  IN_TRANSIT: 'Đang vận chuyển',
  DELIVERED: 'Đã giao hàng',
};
const displayTime = value => value
  ? new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'short' })
  : '—';

export default function MemberOrdersPage() {
  return <main className="container public-page retail-page"><p className="eyebrow">FE-08 · MEMBER</p><h1>Đơn mua của tôi</h1><RequireSession requiredText="Đăng nhập để xem đơn mua."><OrdersContent /></RequireSession></main>;
}

function OrdersContent() {
  const api = useRetailApi();
  const format = useFormat();
  const orders = useResource(() => api.listOrders(), []);
  const branches = useResource(() => api.listBranches(), []);
  const cancel = useCommand();
  const branchNames = useMemo(() => new Map((branches.data?.items || branches.data || []).map(item => [String(item.id), item.name])), [branches.data]);
  const values = Array.isArray(orders.data) ? orders.data : orders.data?.items || [];
  const cancelCashOrder = async order => {
    const result = await cancel.run(key => api.cancelCheckout(order.checkoutId, key));
    if (result.ok) orders.reload();
  };
  if (orders.loading && !orders.data) return <Loading />;
  if (orders.error) return <ErrorNotice error={orders.error} onRetry={orders.reload} />;
  if (!values.length) return <section className="workspace-card empty-state"><p>Bạn chưa có đơn mua nào.</p><Link className="button primary" to="/products">Mua sắm ngay</Link></section>;
  return <section className="retail-orders">
    {cancel.error && <ErrorNotice error={cancel.error} onRetry={cancel.retry} onReload={orders.reload} />}
    {values.map(order => {
      const cash = order.paymentMethod === 'CASH_AT_COUNTER';
      const awaitingCash = cash && order.paymentState === 'ACCEPTED_UNPAID';
      return <article className="workspace-card retail-order" key={order.id}>
        <div><strong>Đơn #{String(order.id).slice(0, 8).toUpperCase()}</strong><span className="status">{awaitingCash ? 'Chờ thanh toán tại quầy' : paymentLabels[order.paymentState] || order.paymentState}</span></div>
        <p className="muted">{branchNames.get(String(order.locationId)) || `Chi nhánh #${order.locationId}`} · {order.fulfilmentMethod === 'GHTK' ? 'Giao qua GHTK' : 'Nhận tại cửa hàng'} · {cash ? 'Tiền mặt tại quầy' : 'VNPay'}{order.fulfilmentState ? ` · ${fulfilmentLabels[order.fulfilmentState] || order.fulfilmentState}` : ''}</p>
        <ul>{(order.items || []).map(item => <li key={`${order.id}-${item.variantId}`}>{item.productName} · Size {item.size ?? '—'} × {item.quantity}</li>)}</ul>
        {awaitingCash && <p className="notice">Mang mã đơn đến chi nhánh và thanh toán <strong>{format.money(order.payableAmount)}</strong> bằng tiền mặt trước <strong>{displayTime(order.counterPaymentDeadline)}</strong>. Quá hạn đơn sẽ tự hủy.</p>}
        <div><strong>{format.money(order.payableAmount)}</strong>{awaitingCash && order.checkoutId && <button type="button" className="button outline" disabled={cancel.pending} onClick={() => cancelCashOrder(order)}>Hủy đơn</button>}</div>
      </article>;
    })}
  </section>;
}
