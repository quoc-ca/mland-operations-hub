import React, { useMemo, useState } from 'react';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useFormat } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../../shared/ui/components';
import { useRetailApi } from '../../application/useRetailApi';

const reasons = {
  QUERY_UNRESOLVED: 'Chưa xác định được kết quả thanh toán',
  LATE_OR_INVALID_STATE: 'Thanh toán về muộn hoặc đơn không còn hợp lệ',
};

function displayTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh',
  }).format(date);
}

export default function ManagerPaymentReconciliationPage() {
  return <main className="container public-page retail-page payment-reconciliation">
    <header className="payment-reconciliation-heading"><div><p className="retail-kicker">FE-08 · MANAGER</p><h1>Đối soát thanh toán</h1><p>Kiểm tra các giao dịch mua hàng cần xử lý thủ công.</p></div></header>
    <RequireSession requiredText="Đăng nhập bằng tài khoản Manager để xem đối soát."><ReconciliationList /></RequireSession>
  </main>;
}

function ReconciliationList() {
  const api = useRetailApi();
  const format = useFormat();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [reason, setReason] = useState('ALL');
  const result = useResource(() => api.listPaymentReconciliations(page), [page]);
  const rows = Array.isArray(result.data) ? result.data : [];
  const visible = useMemo(() => {
    const term = search.trim().toLocaleLowerCase('vi');
    return rows.filter(row => (reason === 'ALL' || row.reason === reason)
      && (!term || [row.checkoutId, row.merchantReference, row.providerTransaction].some(value => String(value || '').toLocaleLowerCase('vi').includes(term))));
  }, [rows, search, reason]);
  const formatMoney = amount => amount == null ? '—' : format.money(String(amount));
  return <section className="workspace-card reconciliation-panel" aria-label="Danh sách giao dịch cần đối soát">
    <div className="reconciliation-toolbar"><div><h2>Giao dịch cần kiểm tra</h2><p className="muted">Danh sách do hệ thống ghi nhận. Bộ lọc áp dụng cho trang đang xem.</p></div><button type="button" className="button outline" onClick={result.reload} disabled={result.loading}>Làm mới</button></div>
    <div className="reconciliation-filters"><label>Tìm mã đơn hoặc giao dịch<input value={search} onChange={event => setSearch(event.target.value)} placeholder="Nhập mã cần tìm" /></label><label>Lý do<select value={reason} onChange={event => setReason(event.target.value)}><option value="ALL">Tất cả lý do</option><option value="QUERY_UNRESOLVED">Chưa rõ kết quả thanh toán</option><option value="LATE_OR_INVALID_STATE">Thanh toán muộn hoặc đơn không hợp lệ</option></select></label></div>
    {result.loading && <Loading />}
    {result.error && <><ErrorNotice error={result.error} /><button type="button" className="button outline" onClick={result.reload}>Thử lại</button></>}
    {!result.loading && !result.error && rows.length === 0 && <p className="reconciliation-empty">Chưa có giao dịch cần đối soát trên trang này.</p>}
    {!result.loading && !result.error && rows.length > 0 && visible.length === 0 && <p className="reconciliation-empty">Không có giao dịch phù hợp với bộ lọc trên trang này.</p>}
    {!result.loading && !result.error && visible.length > 0 && <div className="reconciliation-table-wrap"><table><thead><tr><th scope="col">Checkout</th><th scope="col">Mã thanh toán</th><th scope="col">Mã VNPay</th><th scope="col">Số tiền</th><th scope="col">Lý do</th><th scope="col">Ghi nhận lúc</th></tr></thead><tbody>{visible.map(row => <tr key={row.merchantReference}><td data-label="Checkout">{row.checkoutId}</td><td data-label="Mã thanh toán">{row.merchantReference}</td><td data-label="Mã VNPay">{row.providerTransaction || 'Chưa có'}</td><td data-label="Số tiền">{formatMoney(row.amountVnd)}</td><td data-label="Lý do"><span className="reconciliation-reason">{reasons[row.reason] || 'Cần kiểm tra'}</span></td><td data-label="Ghi nhận lúc">{displayTime(row.createdAt)}</td></tr>)}</tbody></table></div>}
    {!result.error && <div className="reconciliation-pagination"><button type="button" className="button outline" onClick={() => setPage(current => current - 1)} disabled={page === 0 || result.loading}>Trang trước</button><span>Trang {page + 1}</span><button type="button" className="button outline" onClick={() => setPage(current => current + 1)} disabled={result.loading || rows.length < 50}>Trang tiếp</button></div>}
  </section>;
}
