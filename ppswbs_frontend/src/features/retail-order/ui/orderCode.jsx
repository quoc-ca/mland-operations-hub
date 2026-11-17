/** Display code of a branch order (FR-08-050/051): server orderCode, else ML + yyMMdd + first 8 id chars. */
export function orderCode(order, orderedAt) {
  if (order?.orderCode) return order.orderCode;
  const id = String(order?.id || order?.branchOrderId || '').replace(/-/g, '').toUpperCase().slice(0, 8);
  const at = orderedAt || order?.orderedAt;
  const day = at ? new Date(at).toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).slice(2).replace(/-/g, '') : '000000';
  return `ML${day}${id}`;
}

export const displayTime = value => value
  ? new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'short' })
  : '—';

/** Lets a whole card toggle its details by click or Enter/Space, while its buttons keep their own action. */
export function cardToggleProps(open, onToggle) {
  return {
    role: 'button',
    tabIndex: 0,
    'aria-expanded': open,
    onClick: event => { if (!event.target.closest('button, a, input, label')) onToggle(); },
    onKeyDown: event => { if ((event.key === 'Enter' || event.key === ' ') && event.target === event.currentTarget) { event.preventDefault(); onToggle(); } },
  };
}

const positive = value => { try { return BigInt(value || '0') > 0n; } catch { return false; } };

/**
 * Money facts of one branch order for the detail panel: its voucher-and-points share (server value, else
 * subtotal + shipping - payable), whether shipping applies, and how many branch orders were placed together.
 */
export function orderMoney(order, together) {
  let discount = order?.discountAmount;
  if (discount == null) {
    try {
      const value = BigInt(order?.subtotal || '0') + BigInt(order?.shippingCharge || '0') - BigInt(order?.payableAmount || '0');
      discount = value > 0n ? value.toString() : '0';
    } catch { discount = '0'; }
  }
  return {
    discount,
    hasDiscount: positive(discount),
    hasShipping: order?.fulfilmentMethod === 'GHTK' || positive(order?.shippingCharge),
    together: Math.max(order?.checkoutOrderCount || 0, together || 0, 1),
  };
}

/** Detail rows shared by Member and Staff: subtotal, shipping only for delivery, discount only when applied. */
export function OrderMoneyRows({ order, format, together }) {
  const money = orderMoney(order, together);
  return <>
    <div><dt>Tiền hàng</dt><dd>{format.money(order.subtotal)}</dd></div>
    {money.hasShipping && <div><dt>Phí vận chuyển</dt><dd>{format.money(order.shippingCharge || '0')}</dd></div>}
    {money.hasDiscount && <div><dt>Giảm giá (voucher, điểm)</dt><dd>−{format.money(money.discount)}</dd></div>}
    {money.hasDiscount && money.together > 1 && <div className="wide"><dt>Ghi chú</dt><dd>Đơn tách từ lần đặt gồm {money.together} chi nhánh; giảm giá chia theo tỷ lệ giá trị từng đơn.</dd></div>}
  </>;
}
