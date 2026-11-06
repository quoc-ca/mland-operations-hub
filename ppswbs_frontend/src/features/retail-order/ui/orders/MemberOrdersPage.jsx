import React from 'react';
import { Link } from 'react-router';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useFormat } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../../shared/ui/components';
import { useRetailApi } from '../../application/useRetailApi';

export default function MemberOrdersPage() {
  return <main className="container public-page retail-page"><p className="eyebrow">FE-08 · MEMBER</p><h1>Đơn mua của tôi</h1><RequireSession requiredText="Đăng nhập để xem đơn mua."><OrdersContent /></RequireSession></main>;
}

function OrdersContent() {
  const api = useRetailApi();
  const format = useFormat();
  const orders = useResource(() => api.listOrders(), []);
  const values = Array.isArray(orders.data) ? orders.data : orders.data?.items || [];
  if (orders.loading && !orders.data) return <Loading />;
  if (orders.error) return <ErrorNotice error={orders.error} onRetry={orders.reload} />;
  if (!values.length) return <section className="workspace-card empty-state"><p>Bạn chưa có đơn mua nào.</p><Link className="button primary" to="/products">Mua sắm ngay</Link></section>;
  return <section className="retail-orders">{values.map(order => <article className="workspace-card retail-order" key={order.id}>
    <div><strong>Đơn #{order.id}</strong><span className="status">{order.fulfilmentState}</span></div>
    <p className="muted">Chi nhánh #{order.locationId} · Thanh toán: {order.paymentState}</p>
    <ul>{(order.items || []).map(item => <li key={`${order.id}-${item.variantId}`}>{item.productName} · Size {item.size ?? '—'} × {item.quantity}</li>)}</ul>
    <strong>{format.money(order.payableAmount)}</strong>
  </article>)}</section>;
}
