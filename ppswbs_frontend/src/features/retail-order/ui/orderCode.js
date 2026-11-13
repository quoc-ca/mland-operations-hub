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
