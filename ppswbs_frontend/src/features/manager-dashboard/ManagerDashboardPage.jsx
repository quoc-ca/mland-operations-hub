import React, { useState } from 'react';
import { Link } from 'react-router';
import { RequireSession } from '../../shared/auth/RequireSession';
import { useHttp } from '../../shared/api/useHttp';
import { useFormat } from '../../shared/i18n/I18nProvider';
import { Loading, useResource } from '../../shared/ui/components';
import { OperationsSidebar } from '../../shared/ui/OperationsSidebar';
import '../../ota-connectivity/ota-connectivity.css';
import './managerDashboard.css';

/** FE-10 Manager dashboard: five read-only KPI cards, period and branch filters, branch rows and data quality. */
export default function ManagerDashboardPage() {
  return <div className="ota-workspace">
    <OperationsSidebar />
    <main className="ota-main manager-dashboard">
      <div className="ota-topline"><span><Link to="/home">← Trang chủ</Link> MLAND / DASHBOARD ĐIỀU HÀNH</span><span>● Backend-authorized</span></div>
      <RequireSession requiredText="Đăng nhập bằng tài khoản Manager để xem dashboard."><Dashboard /></RequireSession>
    </main>
  </div>;
}

export const PERIODS = [
  { id: 'DAY', label: 'Ngày' },
  { id: 'WEEK', label: 'Tuần' },
  { id: 'MONTH', label: 'Tháng' },
  { id: 'YEAR', label: 'Năm' },
];

const SOURCE_LABELS = {
  RETAIL: 'Bán lẻ',
  WORKSHOP_REVENUE: 'Doanh thu workshop',
  WORKSHOP_CAPACITY: 'Sức chứa buổi workshop',
  ATTENDANCE: 'Check-in',
};

const REASON_LABELS = {
  SOURCE_UNAVAILABLE: 'tạm thời không đọc được',
  NON_VND_EXCLUDED: 'có giao dịch không phải VND, đã loại khỏi tổng',
  WORKSHOP_LOCATION_UNRESOLVED: 'có hóa đơn workshop chưa xác định được chi nhánh, chưa được tính',
};

const todayInVietnam = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date());

const vnTime = (value, withTime = true) => {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('vi-VN', withTime
    ? { dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' }
    : { dateStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' }).format(date);
};

/** The inclusive last day of a half-open period, for display only. */
const lastDay = (scope) => {
  if (!scope?.endAt) return '—';
  const end = new Date(scope.endAt);
  const now = Date.now();
  // A finished period ends at 00:00 of the next day; show the day before. A current period ends now.
  return end.getTime() < now - 1000 ? vnTime(new Date(end.getTime() - 1), false) : vnTime(end, true);
};

export function percent(value) {
  if (value === null || value === undefined) return '—';
  return `${Number(value).toLocaleString('vi-VN', { maximumFractionDigits: 1 })}%`;
}

function errorText(error) {
  if (!error) return '';
  if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (error.status === 403) return 'Chỉ Manager được phân công chi nhánh mới xem được dashboard này, và chỉ trong các chi nhánh của mình.';
  if (error.status === 400) return 'Bộ lọc không hợp lệ (ví dụ kỳ nằm hoàn toàn trong tương lai). Đang giữ kết quả hợp lệ gần nhất.';
  if (error.status === 503) return 'Dữ liệu dashboard tạm thời không đọc được. Bạn có thể thử lại.';
  return 'Không tải được dashboard. Bạn có thể thử lại.';
}

function Dashboard() {
  const http = useHttp();
  const format = useFormat();
  // Every filter applies as soon as it is chosen; reloading the page refreshes (FR-023). No timer.
  const [filter, setFilter] = useState({ period: 'MONTH', anchor: null, locations: [] }); // [] = every assigned branch
  const [branchOptions, setBranchOptions] = useState([]);

  const report = useResource(async () => {
    const data = await http('/api/v1/executive-reports', {
      query: {
        periodType: filter.period,
        anchorDate: filter.anchor || undefined,
        locationId: filter.locations.length ? filter.locations.join(',') : undefined,
      },
    });
    // The default view lists every branch the Manager may filter (Session 2026-11-14).
    if (!filter.locations.length && data?.branches) {
      setBranchOptions(data.branches.map(branch => ({ id: branch.locationId, name: branch.branchName })));
    }
    return data;
  }, [filter]);

  const data = report.data;
  const anchor = filter.anchor || todayInVietnam();
  const choosePeriod = period => setFilter(current => ({ ...current, period }));
  // Picking a date shows exactly that day.
  const chooseDate = value => { if (value) setFilter(current => ({ ...current, period: 'DAY', anchor: value })); };
  const toggleBranch = id => setFilter(current => {
    const next = current.locations.includes(id) ? current.locations.filter(x => x !== id) : [...current.locations, id];
    // Unselecting the last branch, or selecting every branch, is the same as "all".
    const all = branchOptions.length > 0 && next.length === branchOptions.length;
    return { ...current, locations: all ? [] : next.sort((a, b) => a - b) };
  });

  return <>
    <header className="ota-page-heading">
      <div>
        <p className="ota-eyebrow">FE-10 · Manager</p>
        <h1>Dashboard điều hành</h1>
        <p>Doanh thu đã xác minh và hiệu suất workshop của các chi nhánh bạn phụ trách.</p>
      </div>
      <div className="md-refresh">
        <span>Cập nhật lúc <strong>{vnTime(data?.lastUpdatedAt)}</strong></span>
      </div>
    </header>

    <section className="md-filters" aria-label="Bộ lọc dashboard" aria-busy={report.loading}>
      <div className="md-filter-group">
        <span className="md-filter-label">Kỳ báo cáo</span>
        <div className="md-segment" role="radiogroup" aria-label="Loại kỳ">
          {PERIODS.map(item => <button key={item.id} type="button" role="radio" aria-checked={filter.period === item.id}
            className={filter.period === item.id ? 'active' : ''} onClick={() => choosePeriod(item.id)}>{item.label}</button>)}
        </div>
      </div>
      <label className="md-filter-group">
        <span className="md-filter-label">Ngày</span>
        <input type="date" aria-label="Chọn ngày" value={anchor} max={todayInVietnam()} onChange={event => chooseDate(event.target.value)} />
      </label>
      <div className="md-filter-group md-branches">
        <span className="md-filter-label">Chi nhánh</span>
        <div className="md-chips">
          <button type="button" aria-pressed={filter.locations.length === 0} className={filter.locations.length === 0 ? 'md-chip active' : 'md-chip'}
            onClick={() => setFilter(current => ({ ...current, locations: [] }))}>Tất cả</button>
          {branchOptions.map(branch => <button key={branch.id} type="button" aria-pressed={filter.locations.includes(branch.id)}
            className={filter.locations.includes(branch.id) ? 'md-chip active' : 'md-chip'} onClick={() => toggleBranch(branch.id)}>{branch.name}</button>)}
        </div>
      </div>
    </section>

    {report.error && <div className="notice error md-notice" role="alert">
      <p>{errorText(report.error)}</p>
      {report.error.status !== 401 && report.error.status !== 403 && <button type="button" className="button small outline" onClick={report.reload}>Thử lại</button>}
    </div>}
    {report.loading && !data && <Loading />}
    {data && <div className={report.loading ? 'md-reloading' : ''}><Report data={data} format={format} /></div>}
  </>;
}

function Report({ data, format }) {
  const totals = data.totals || {};
  const scope = data.scope || {};
  const branchCount = (scope.locationIds || []).length;
  const problems = (data.quality || []).filter(item => item.status === 'UNAVAILABLE' || item.safeReasonCode);
  return <>
    <p className="md-scope">
      <span>{PERIODS.find(item => item.id === scope.periodType)?.label || scope.periodType}</span>
      {vnTime(scope.startAt, false)} – {lastDay(scope)} · {branchCount} chi nhánh · giờ Việt Nam
    </p>

    {data.status === 'PARTIAL' && <div className="md-quality warn" role="status">
      <strong>Số liệu chưa đầy đủ.</strong> Các mục đánh dấu “—” không có dữ liệu, không phải bằng 0.
      <ul>{problems.map(item => <li key={item.source}>{SOURCE_LABELS[item.source] || item.source}: {REASON_LABELS[item.safeReasonCode] || 'tạm thời không đọc được'}</li>)}</ul>
    </div>}
    {data.status === 'EMPTY' && <div className="md-quality" role="status">
      <strong>Chưa có giao dịch đã xác minh trong kỳ này.</strong> Đơn chờ thanh toán, đã hủy hoặc buổi chưa chốt điểm danh không được tính.
    </div>}

    <section className="md-kpis" aria-label="Chỉ số chính">
      <Kpi label="Doanh thu bán lẻ" value={format.money(totals.retailRevenue)} hint="Sau giảm giá, không gồm phí ship" missing={totals.retailRevenue == null} />
      <Kpi label="Doanh thu workshop" value={format.money(totals.workshopRevenue)} hint="Hóa đơn đã thanh toán đủ" missing={totals.workshopRevenue == null} />
      <Kpi label="Tổng doanh thu" value={format.money(totals.totalRevenue)} hint="Bán lẻ + workshop" missing={totals.totalRevenue == null} strong />
      <Kpi label="Đơn bán lẻ đã thanh toán" value={totals.paidRetailOrderCount == null ? '—' : format.number(totals.paidRetailOrderCount)} hint="VNPay và tiền mặt đã xác nhận" missing={totals.paidRetailOrderCount == null} />
      <Kpi label="Tỷ lệ lấp đầy workshop" value={percent(totals.workshopUtilizationPercent)}
        hint={totals.completedSessionCapacity ? `${format.number(totals.checkedInParticipants)} / ${format.number(totals.completedSessionCapacity)} chỗ đã check-in` : 'Chưa có buổi đã chốt điểm danh'}
        missing={totals.completedSessionCapacity == null} meter={totals.workshopUtilizationPercent} />
    </section>

    <BranchTable branches={data.branches || []} total={totals.totalRevenue} format={format} />
  </>;
}

function Kpi({ label, value, hint, missing, strong, meter }) {
  return <article className={`md-kpi${strong ? ' md-kpi-strong' : ''}`}>
    <span className="md-kpi-label">{label}</span>
    <strong className="md-kpi-value">{missing ? '—' : value}</strong>
    {meter != null && <span className="md-meter" aria-hidden="true"><span style={{ width: `${Math.min(100, meter)}%` }} /></span>}
    <small>{missing ? 'Chưa có dữ liệu' : hint}</small>
  </article>;
}

function BranchTable({ branches, total, format }) {
  const totalNumber = Number(total || 0);
  return <section className="md-panel" aria-label="Chỉ số theo chi nhánh">
    <div className="md-panel-head"><h2>Theo chi nhánh</h2><span className="muted">{branches.length} chi nhánh</span></div>
    {branches.length === 0 ? <p className="md-empty">Không có chi nhánh trong phạm vi.</p> :
      <div className="md-table-wrap"><table className="md-table">
        <thead><tr>
          <th scope="col">Chi nhánh</th><th scope="col">Bán lẻ</th><th scope="col">Workshop</th>
          <th scope="col">Tổng doanh thu</th><th scope="col">Đơn đã TT</th><th scope="col">Lấp đầy</th>
        </tr></thead>
        <tbody>{branches.map(branch => {
          const v = branch.values || {};
          const share = totalNumber > 0 && v.totalRevenue != null ? Number(v.totalRevenue) * 100 / totalNumber : null;
          return <tr key={branch.locationId}>
            <th scope="row" data-label="Chi nhánh">{branch.branchName}</th>
            <td data-label="Bán lẻ">{format.money(v.retailRevenue)}</td>
            <td data-label="Workshop">{format.money(v.workshopRevenue)}</td>
            <td data-label="Tổng doanh thu"><strong>{format.money(v.totalRevenue)}</strong>
              {share != null && <span className="md-share" title={`${percent(share)} tổng doanh thu`}><span style={{ width: `${share}%` }} /></span>}</td>
            <td data-label="Đơn đã TT">{v.paidRetailOrderCount == null ? '—' : format.number(v.paidRetailOrderCount)}</td>
            <td data-label="Lấp đầy">{v.workshopUtilizationPercent == null ? <span className="muted">{v.completedSessionCapacity === 0 ? 'Chưa có buổi' : '—'}</span>
              : <span className="md-util"><span className="md-meter small"><span style={{ width: `${Math.min(100, v.workshopUtilizationPercent)}%` }} /></span>{percent(v.workshopUtilizationPercent)}</span>}</td>
          </tr>;
        })}</tbody>
      </table></div>}
  </section>;
}
