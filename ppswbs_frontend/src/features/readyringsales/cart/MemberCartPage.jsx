import React, { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { RequireSession } from '../../../shared/auth/RequireSession';
import { useFormat } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useCommand, useResource } from '../../../shared/ui/components';
import { useRetailApi } from '../api/useRetailApi';

const validQuantity = value => Number.isInteger(value) && value >= 1 && value <= 99;

export default function MemberCartPage() {
  return (
    <main className="container public-page retail-page">
      <p className="eyebrow">FE-08 · MEMBER</p>
      <h1>Giỏ hàng</h1>
      <RequireSession requiredText="Đăng nhập để xem giỏ hàng của bạn."><CartContent /></RequireSession>
    </main>
  );
}

function CartContent() {
  const api = useRetailApi();
  const format = useFormat();
  const navigate = useNavigate();
  const cart = useResource(() => api.getCart(), []);
  const command = useCommand();
  const [notice, setNotice] = useState('');
  const lines = cart.data?.lines || [];
  const subtotal = useMemo(() => lines.reduce((total, line) => total + BigInt(line.indicativePrice || '0') * BigInt(line.quantity || 0), 0n).toString(), [lines]);

  const changeQuantity = async (line, raw) => {
    const quantity = Number(raw);
    if (!validQuantity(quantity)) { setNotice('Số lượng phải từ 1 đến 99.'); return; }
    setNotice('');
    const result = await command.run(key => api.updateLine(line.id, quantity, key));
    if (result.ok) cart.reload();
  };
  const remove = async line => {
    setNotice('');
    const result = await command.run(key => api.removeLine(line.id, key));
    if (result.ok) cart.reload();
  };

  if (cart.loading && !cart.data) return <Loading />;
  if (cart.error) return <ErrorNotice error={cart.error} onRetry={cart.reload} />;
  if (lines.length === 0) return (
    <section className="workspace-card empty-state">
      <p>Giỏ hàng chưa có sản phẩm.</p>
      <Link className="button primary" to="/products">Xem sản phẩm</Link>
    </section>
  );

  return (
    <section className="retail-cart-layout">
      <div className="retail-lines">
        <p className="muted small">Giá và số lượng được kiểm tra lại khi tạo checkout.</p>
        {lines.map(line => (
          <article className="retail-line" key={line.id}>
            <div className="retail-line-main">
              <strong>{line.productName || `Sản phẩm #${line.variantId}`}</strong>
              <span className="muted">{line.sku || 'SKU chưa có'} · Size {line.size ?? '—'} · Chi nhánh #{line.locationId}</span>
              {!line.saleEligible && <span className="status">Tạm thời không còn bán</span>}
            </div>
            <div className="retail-line-price">{format.money(line.indicativePrice)}</div>
            <label className="retail-quantity">Số lượng
              <input type="number" min="1" max="99" defaultValue={line.quantity} disabled={command.pending}
                onBlur={event => event.target.value !== String(line.quantity) && changeQuantity(line, event.target.value)} />
            </label>
            <button type="button" className="text-link retail-delete" disabled={command.pending} onClick={() => remove(line)}>Xóa</button>
          </article>
        ))}
      </div>
      <aside className="retail-summary">
        <h2>Tóm tắt</h2>
        <div><span>Tạm tính</span><strong>{format.money(subtotal)}</strong></div>
        <p className="muted small">Phí giao hàng và ưu đãi sẽ được báo giá ở bước checkout.</p>
        <button type="button" className="button primary" onClick={() => navigate('/member/checkout')}>Tiến hành checkout</button>
        <Link className="text-link" to="/member/orders">Đơn mua của tôi</Link>
      </aside>
      {notice && <p className="notice error">{notice}</p>}
      {command.error && <ErrorNotice error={command.error} onRetry={command.retry} onReload={cart.reload} />}
    </section>
  );
}
