import React, { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useFormat } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useCommand, useResource } from '../../../../shared/ui/components';
import { useRetailApi } from '../../application/useRetailApi';

const validQuantity = value => Number.isInteger(value) && value >= 1 && value <= 99;

export default function MemberCartPage() {
  return (
    <main className="container public-page retail-page retail-shopping">
      <div className="retail-page-heading"><div><p className="retail-kicker">MLAND / MUA SẮM</p><h1>Giỏ hàng</h1><p>Kiểm tra sản phẩm trước khi thanh toán.</p></div><Link to="/products" className="retail-back-link">← Tiếp tục mua sắm</Link></div>
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
  const groups = useMemo(() => {
    const byLocation = new Map();
    lines.forEach(line => byLocation.set(line.locationId, [...(byLocation.get(line.locationId) || []), line]));
    return [...byLocation.entries()];
  }, [lines]);
  const subtotal = useMemo(() => lines.reduce((total, line) => total + BigInt(line.indicativePrice || '0') * BigInt(line.quantity || 0), 0n).toString(), [lines]);

  const changeQuantity = async (line, raw) => {
    const quantity = Number(raw);
    if (!validQuantity(quantity)) { setNotice('Số lượng phải từ 1 đến 99.'); return; }
    if (line.availableQuantity != null && BigInt(quantity) > BigInt(line.availableQuantity)) {
      setNotice('Số lượng vượt quá hàng hiện có.');
      return;
    }
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
      <div className="retail-cart-groups">
        {groups.map(([locationId, branchLines]) => <section className="retail-branch-card" key={locationId}>
          <div className="retail-branch-heading"><span className="retail-branch-icon" aria-hidden="true">⌂</span><div><strong>Chi nhánh #{locationId}</strong><small>{branchLines.length} sản phẩm</small></div></div>
        {branchLines.map(line => (
          <article className="retail-line" key={line.id}>
            <div className="retail-product-placeholder" aria-hidden="true">◇</div>
            <div className="retail-line-main">
              <strong>{line.productName || `Sản phẩm #${line.variantId}`}</strong>
              <span className="muted">Size {line.size ?? '—'} · {line.sku || 'SKU chưa có'}</span>
              {line.availableQuantity != null && <span className="muted">Còn {line.availableQuantity} sản phẩm</span>}
              {!line.saleEligible && <span className="status">Tạm thời không còn bán</span>}
            </div>
            <div className="retail-line-price">{line.indicativePrice == null ? 'Chưa có giá' : format.money(line.indicativePrice)}</div>
            <label className="retail-quantity">Số lượng
              <input type="number" min="1" max={line.availableQuantity == null ? 99 : Math.min(99, Number(line.availableQuantity))} defaultValue={line.quantity} disabled={command.pending}
                onBlur={event => event.target.value !== String(line.quantity) && changeQuantity(line, event.target.value)} />
            </label>
            <button type="button" className="text-link retail-delete" disabled={command.pending} onClick={() => remove(line)}>Xóa</button>
          </article>
        ))}</section>)}
        <p className="retail-help">Giá và số lượng sẽ được kiểm tra lại ở bước thanh toán.</p>
      </div>
      <aside className="retail-summary">
        <h2>Tóm tắt giỏ hàng</h2>
        <div><span>Sản phẩm</span><strong>{lines.reduce((sum, line) => sum + line.quantity, 0)}</strong></div>
        <div className="retail-summary-total"><span>Tạm tính</span><strong>{format.money(subtotal)}</strong></div>
        <p className="muted small">Ưu đãi và phương thức nhận hàng sẽ được xem ở bước tiếp theo.</p>
        <button type="button" className="button primary" disabled={command.pending} onClick={() => navigate('/member/checkout')}>Mua hàng <span aria-hidden="true">→</span></button>
        <Link className="retail-summary-link" to="/member/orders">Xem đơn mua của tôi</Link>
      </aside>
      {notice && <p className="notice error">{notice}</p>}
      {command.error && <ErrorNotice error={command.error} onRetry={command.retry} onReload={cart.reload} />}
    </section>
  );
}
