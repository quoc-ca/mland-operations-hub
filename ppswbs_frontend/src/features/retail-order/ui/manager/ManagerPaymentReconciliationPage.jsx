import React, { useMemo, useState } from 'react';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useFormat } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useResource } from '../../../../shared/ui/components';
import { useRetailApi } from '../../application/useRetailApi';
import { Link } from 'react-router';
import { useText } from '../useText';

const reasons = {
  QUERY_UNRESOLVED: ['Chưa xác định được kết quả thanh toán', 'Payment result not determined'],
  LATE_OR_INVALID_STATE: ['Thanh toán về muộn hoặc đơn không còn hợp lệ', 'Late payment or the order is no longer valid'],
};

function displayTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh',
  }).format(date);
}

export default function ManagerPaymentReconciliationPage() {
  const t = useText();
  return <div className="ota-workspace">
    
    <main className="ota-main payment-reconciliation">
      <div className="ota-topline"><span className="retail-staff-crumb"><Link to="/home">{t('← Trang chủ', '← Home')}</Link> {t('MLAND / ĐỐI SOÁT BÁN LẺ', 'MLAND / RETAIL RECONCILIATION')}</span><span>● Backend-authorized</span></div>
      <div className="ota-page-heading"><div><p>{t('Kiểm tra các giao dịch mua hàng cần xử lý thủ công.', 'Check the purchase transactions that need manual handling.')}</p></div></div>
      <RequireSession requiredText={t('Đăng nhập bằng tài khoản Manager để xem đối soát.', 'Sign in with a Manager account to see reconciliation.')}><ManagerPaymentTabs /></RequireSession>
    </main>
  </div>;
}

function ManagerPaymentTabs() {
  const t = useText();
  const [tab, setTab] = useState('VNPAY');
  return <>
    <div className="pickup-desk-tabs" role="tablist" aria-label={t('Loại đối soát', 'Reconciliation type')}>
      <button type="button" role="tab" aria-selected={tab === 'VNPAY'} className={tab === 'VNPAY' ? 'active' : ''} onClick={() => setTab('VNPAY')}>{t('Đối soát VNPay', 'VNPay reconciliation')}</button>
      <button type="button" role="tab" aria-selected={tab === 'CASH'} className={tab === 'CASH' ? 'active' : ''} onClick={() => setTab('CASH')}>{t('Tiền mặt theo ngày', 'Daily cash')}</button>
    </div>
    {tab === 'VNPAY' ? <ReconciliationList /> : <DailyCashPanel />}
  </>;
}

const todayInVietnam = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date());

/** FR-08-047: confirmed retail counter cash per permitted branch and Vietnam business date (read-only). */
function DailyCashPanel() {
  const api = useRetailApi();
  const format = useFormat();
  const t = useText();
  const branches = useResource(() => api.listBranches(), []);
  const branchList = branches.data?.items || branches.data || [];
  const [locationId, setLocationId] = useState('');
  const [businessDate, setBusinessDate] = useState(todayInVietnam());
  const selectedLocation = locationId || (branchList[0] ? String(branchList[0].id) : '');
  const result = useResource(() => (selectedLocation ? api.dailyCash(selectedLocation, businessDate) : Promise.resolve(null)), [selectedLocation, businessDate]);
  const data = result.data;
  return <section className="workspace-card reconciliation-panel" aria-label={t('Tiền mặt bán lẻ theo ngày', 'Daily retail cash')}>
    <div className="reconciliation-toolbar"><div><h2>{t('Tiền mặt bán lẻ đã thu', 'Retail cash collected')}</h2><p className="muted">{t('Tổng tiền mặt Staff đã xác nhận tại quầy theo chi nhánh và ngày (giờ Việt Nam). Chưa gồm tiền workshop và chưa có bước đếm két.', 'Cash confirmed by Staff at the counter, per branch and day (Vietnam time). Workshop cash and the till count are not included.')}</p></div><button type="button" className="button outline" onClick={result.reload} disabled={result.loading}>{t('Làm mới', 'Refresh')}</button></div>
    <div className="reconciliation-filters">
      <label>{t('Chi nhánh', 'Branch')}<select value={selectedLocation} onChange={event => setLocationId(event.target.value)}>{branchList.map(item => <option key={item.id} value={String(item.id)}>{item.name}</option>)}</select></label>
      <label>{t('Ngày', 'Date')}<input type="date" value={businessDate} max={todayInVietnam()} onChange={event => setBusinessDate(event.target.value)} /></label>
    </div>
    {(result.loading || branches.loading) && <Loading />}
    {result.error && <ErrorNotice error={result.error} onRetry={result.reload} />}
    {data && !result.loading && <>
      <p className="cash-daily-total">{t(`${data.collectionCount} lần thu · Tổng`, `${data.collectionCount} collections · Total`)} <strong>{format.money(data.totalApplied)}</strong></p>
      {data.collections.length === 0 ? <p className="reconciliation-empty">{t('Chưa có khoản tiền mặt nào trong ngày này.', 'No cash collected on this day.')}</p>
        : <div className="reconciliation-table-wrap"><table><thead><tr><th scope="col">{t('Thời gian', 'Time')}</th><th scope="col">{t('Đơn', 'Order')}</th><th scope="col">{t('Số tiền', 'Amount')}</th><th scope="col">{t('Khách đưa', 'Received')}</th><th scope="col">{t('Tiền thối', 'Change')}</th><th scope="col">{t('Staff', 'Staff')}</th></tr></thead><tbody>{data.collections.map(row => <tr key={row.collectionId}><td data-label={t('Thời gian', 'Time')}>{displayTime(row.confirmedAt)}</td><td data-label={t('Đơn', 'Order')}>#{String(row.orderId).slice(0, 8).toUpperCase()}</td><td data-label={t('Số tiền', 'Amount')}>{format.money(row.appliedAmount)}</td><td data-label={t('Khách đưa', 'Received')}>{format.money(row.amountReceived)}</td><td data-label={t('Tiền thối', 'Change')}>{format.money(row.changeReturned)}</td><td data-label={t('Staff', 'Staff')}>#{row.confirmedBy}</td></tr>)}</tbody></table></div>}
    </>}
  </section>;
}

function ReconciliationList() {
  const api = useRetailApi();
  const format = useFormat();
  const t = useText();
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
  return <section className="workspace-card reconciliation-panel" aria-label={t('Danh sách giao dịch cần đối soát', 'Transactions to reconcile')}>
    <div className="reconciliation-toolbar"><div><h2>{t('Giao dịch cần kiểm tra', 'Transactions to check')}</h2><p className="muted">{t('Danh sách do hệ thống ghi nhận. Bộ lọc áp dụng cho trang đang xem.', 'Recorded by the system. Filters apply to the page shown.')}</p></div><button type="button" className="button outline" onClick={result.reload} disabled={result.loading}>{t('Làm mới', 'Refresh')}</button></div>
    <div className="reconciliation-filters"><label>{t('Tìm mã đơn hoặc giao dịch', 'Search order or transaction code')}<input value={search} onChange={event => setSearch(event.target.value)} placeholder={t('Nhập mã cần tìm', 'Enter a code')} /></label><label>{t('Lý do', 'Reason')}<select value={reason} onChange={event => setReason(event.target.value)}><option value="ALL">{t('Tất cả lý do', 'All reasons')}</option><option value="QUERY_UNRESOLVED">{t('Chưa rõ kết quả thanh toán', 'Payment result unknown')}</option><option value="LATE_OR_INVALID_STATE">{t('Thanh toán muộn hoặc đơn không hợp lệ', 'Late payment or invalid order')}</option></select></label></div>
    {result.loading && <Loading />}
    {result.error && <><ErrorNotice error={result.error} /><button type="button" className="button outline" onClick={result.reload}>{t('Thử lại', 'Retry')}</button></>}
    {!result.loading && !result.error && rows.length === 0 && <p className="reconciliation-empty">{t('Chưa có giao dịch cần đối soát trên trang này.', 'No transaction to reconcile on this page.')}</p>}
    {!result.loading && !result.error && rows.length > 0 && visible.length === 0 && <p className="reconciliation-empty">{t('Không có giao dịch phù hợp với bộ lọc trên trang này.', 'No transaction matches the filters on this page.')}</p>}
    {!result.loading && !result.error && visible.length > 0 && <div className="reconciliation-table-wrap"><table><thead><tr><th scope="col">Checkout</th><th scope="col">{t('Mã thanh toán', 'Payment reference')}</th><th scope="col">{t('Mã VNPay', 'VNPay transaction')}</th><th scope="col">{t('Số tiền', 'Amount')}</th><th scope="col">{t('Lý do', 'Reason')}</th><th scope="col">{t('Ghi nhận lúc', 'Recorded at')}</th></tr></thead><tbody>{visible.map(row => <tr key={row.merchantReference}><td data-label="Checkout">{row.checkoutId}</td><td data-label={t('Mã thanh toán', 'Payment reference')}>{row.merchantReference}</td><td data-label={t('Mã VNPay', 'VNPay transaction')}>{row.providerTransaction || t('Chưa có', 'None')}</td><td data-label={t('Số tiền', 'Amount')}>{formatMoney(row.amountVnd)}</td><td data-label={t('Lý do', 'Reason')}><span className="reconciliation-reason">{reasons[row.reason] ? t(reasons[row.reason]) : t('Cần kiểm tra', 'To check')}</span></td><td data-label={t('Ghi nhận lúc', 'Recorded at')}>{displayTime(row.createdAt)}</td></tr>)}</tbody></table></div>}
    {!result.error && <div className="reconciliation-pagination"><button type="button" className="button outline" onClick={() => setPage(current => current - 1)} disabled={page === 0 || result.loading}>{t('Trang trước', 'Previous page')}</button><span>{t('Trang', 'Page')} {page + 1}</span><button type="button" className="button outline" onClick={() => setPage(current => current + 1)} disabled={result.loading || rows.length < 50}>{t('Trang tiếp', 'Next page')}</button></div>}
  </section>;
}
