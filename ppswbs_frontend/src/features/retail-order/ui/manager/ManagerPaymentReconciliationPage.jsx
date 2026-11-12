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
    <RequireSession requiredText="Đăng nhập bằng tài khoản Manager để xem đối soát."><ManagerPaymentTabs /></RequireSession>
  </main>;
}

function ManagerPaymentTabs() {
  const [tab, setTab] = useState('VNPAY');
  return <>
    <div className="pickup-desk-tabs" role="tablist" aria-label="Loại đối soát">
      <button type="button" role="tab" aria-selected={tab === 'VNPAY'} className={tab === 'VNPAY' ? 'active' : ''} onClick={() => setTab('VNPAY')}>Đối soát VNPay</button>
      <button type="button" role="tab" aria-selected={tab === 'CASH'} className={tab === 'CASH' ? 'active' : ''} onClick={() => setTab('CASH')}>Tiền mặt theo ngày</button>
    </div>
    {tab === 'VNPAY' ? <ReconciliationList /> : <DailyCashPanel />}
  </>;
}

const todayInVietnam = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date());

/** FR-08-047: confirmed retail counter cash per permitted branch and Vietnam business date (read-only). */
function DailyCashPanel() {
  const api = useRetailApi();
  const format = useFormat();
  const branches = useResource(() => api.listBranches(), []);
  const branchList = branches.data?.items || branches.data || [];
  const [locationId, setLocationId] = useState('');
  const [businessDate, setBusinessDate] = useState(todayInVietnam());
  const selectedLocation = locationId || (branchList[0] ? String(branchList[0].id) : '');
  const result = useResource(() => (selectedLocation ? api.dailyCash(selectedLocation, businessDate) : Promise.resolve(null)), [selectedLocation, businessDate]);
  const data = result.data;
  return <section className="workspace-card reconciliation-panel" aria-label="Tiền mặt bán lẻ theo ngày">
    <div className="reconciliation-toolbar"><div><h2>Tiền mặt bán lẻ đã thu</h2><p className="muted">Tổng tiền mặt Staff đã xác nhận tại quầy theo chi nhánh và ngày (giờ Việt Nam). Chưa gồm tiền workshop và chưa có bước đếm két.</p></div><button type="button" className="button outline" onClick={result.reload} disabled={result.loading}>Làm mới</button></div>
    <div className="reconciliation-filters">
      <label>Chi nhánh<select value={selectedLocation} onChange={event => setLocationId(event.target.value)}>{branchList.map(item => <option key={item.id} value={String(item.id)}>{item.name}</option>)}</select></label>
      <label>Ngày<input type="date" value={businessDate} max={todayInVietnam()} onChange={event => setBusinessDate(event.target.value)} /></label>
    </div>
    {(result.loading || branches.loading) && <Loading />}
    {result.error && <ErrorNotice error={result.error} onRetry={result.reload} />}
    {data && !result.loading && <>
      <p className="cash-daily-total">{data.collectionCount} lần thu · Tổng <strong>{format.money(data.totalApplied)}</strong></p>
      {data.collections.length === 0 ? <p className="reconciliation-empty">Chưa có khoản tiền mặt nào trong ngày này.</p>
        : <div className="reconciliation-table-wrap"><table><thead><tr><th scope="col">Thời gian</th><th scope="col">Đơn</th><th scope="col">Số tiền</th><th scope="col">Khách đưa</th><th scope="col">Tiền thối</th><th scope="col">Staff</th></tr></thead><tbody>{data.collections.map(row => <tr key={row.collectionId}><td data-label="Thời gian">{displayTime(row.confirmedAt)}</td><td data-label="Đơn">#{String(row.orderId).slice(0, 8).toUpperCase()}</td><td data-label="Số tiền">{format.money(row.appliedAmount)}</td><td data-label="Khách đưa">{format.money(row.amountReceived)}</td><td data-label="Tiền thối">{format.money(row.changeReturned)}</td><td data-label="Staff">#{row.confirmedBy}</td></tr>)}</tbody></table></div>}
    </>}
  </section>;
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
