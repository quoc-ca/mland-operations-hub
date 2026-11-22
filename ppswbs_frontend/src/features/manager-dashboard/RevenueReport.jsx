import React, { useState } from 'react';
import { useHttp } from '../../shared/api/useHttp';
import { useFormat } from '../../shared/i18n/I18nProvider';
import { Loading, useResource } from '../../shared/ui/components';
import { Change, TrendChart, UNIT_LABEL, rate, useL } from './TrendCharts';

export const PERIODS = [
  { id: 'DAY', label: 'Ngày', en: 'Day' },
  { id: 'WEEK', label: 'Tuần', en: 'Week' },
  { id: 'MONTH', label: 'Tháng', en: 'Month' },
  { id: 'YEAR', label: 'Năm', en: 'Year' },
];

// [Vietnamese, English]
const SOURCE_LABELS = {
  RETAIL: ['Bán lẻ', 'Retail'],
  WORKSHOP_REVENUE: ['Doanh thu workshop', 'Workshop revenue'],
  WORKSHOP_CAPACITY: ['Sức chứa buổi workshop', 'Workshop capacity'],
  ATTENDANCE: ['Check-in', 'Check-in'],
};

const REASON_LABELS = {
  SOURCE_UNAVAILABLE: ['tạm thời không đọc được', 'temporarily unavailable'],
  NON_VND_EXCLUDED: ['có giao dịch không phải VND, đã loại khỏi tổng', 'non-VND transactions were left out of the total'],
  WORKSHOP_LOCATION_UNRESOLVED: ['có hóa đơn workshop chưa xác định được chi nhánh, chưa được tính', 'some workshop bills have no known branch and are not counted'],
};
const pick = (pair, L) => (pair ? L(pair[0], pair[1]) : null);

const todayInVietnam = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date());

const vnTime = (value, withTime = true, en = false) => {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat(en ? 'en-GB' : 'vi-VN', withTime
    ? { dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' }
    : { dateStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' }).format(date);
};

/** The inclusive last day of a half-open period, for display only. */
const lastDay = (scope, en = false) => {
  if (!scope?.endAt) return '—';
  const end = new Date(scope.endAt);
  const now = Date.now();
  // A finished period ends at 00:00 of the next day; show the day before. A current period ends now.
  return end.getTime() < now - 1000 ? vnTime(new Date(end.getTime() - 1), false, en) : vnTime(end, true, en);
};

export function percent(value, en = false) {
  if (value === null || value === undefined) return '—';
  return `${Number(value).toLocaleString(en ? 'en-US' : 'vi-VN', { maximumFractionDigits: 1 })}%`;
}

function errorText(error, L) {
  if (!error) return '';
  if (error.status === 401) return L('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.', 'Your session has expired. Please sign in again.');
  if (error.status === 403) return L('Chỉ Manager được phân công chi nhánh mới xem được dashboard này, và chỉ trong các chi nhánh của mình.', 'Only a Manager assigned to a branch can see this dashboard, and only for their own branches.');
  if (error.status === 400) return L('Bộ lọc không hợp lệ (ví dụ kỳ nằm hoàn toàn trong tương lai). Đang giữ kết quả hợp lệ gần nhất.', 'Invalid filter (for example a period entirely in the future). Keeping the last valid result.');
  if (error.status === 503) return L('Dữ liệu dashboard tạm thời không đọc được. Bạn có thể thử lại.', 'Dashboard data is temporarily unavailable. You can try again.');
  return L('Không tải được dashboard. Bạn có thể thử lại.', 'The dashboard could not be loaded. You can try again.');
}

/** FE-10 revenue block: five read-only KPI cards, period and branch filters, trends, branch rows and data quality. Loads and fails on its own. */
export default function RevenueReport() {
  const http = useHttp();
  const format = useFormat();
  const L = useL();
  const en = L(false, true);
  // Every filter applies as soon as it is chosen; reloading the page refreshes (FR-023). No timer.
  const [filter, setFilter] = useState({ period: 'MONTH', anchor: null, locations: [] }); // [] = every assigned branch
  const [branchOptions, setBranchOptions] = useState([]);

  const query = {
    periodType: filter.period,
    anchorDate: filter.anchor || undefined,
    locationId: filter.locations.length ? filter.locations.join(',') : undefined,
  };
  const report = useResource(async () => {
    const data = await http('/api/v1/executive-reports', { query });
    // The default view lists every branch the Manager may filter (Session 2026-11-14).
    if (!filter.locations.length && data?.branches) {
      setBranchOptions(data.branches.map(branch => ({ id: branch.locationId, name: branch.branchName })));
    }
    return data;
  }, [filter]);
  // Trends read the same filters on their own, so a slow trend never holds back the KPI cards.
  const trend = useResource(() => http('/api/v1/executive-reports/trends', { query }), [filter]);

  const data = report.data;
  const anchor = filter.anchor || todayInVietnam();
  const choosePeriod = period => setFilter(current => ({ ...current, period }));
  // Picking a date shows exactly that day.
  const chooseDate = value => { if (value) setFilter(current => ({ ...current, period: 'DAY', anchor: value })); };
  // One click shows exactly that branch (Session 2026-11-16); clicking the shown branch again goes back to "all".
  const toggleBranch = id => setFilter(current => ({
    ...current, locations: current.locations.length === 1 && current.locations[0] === id ? [] : [id],
  }));

  return <>
    <p className="md-updated">{L('Cập nhật lúc', 'Updated at')} <strong>{vnTime(data?.lastUpdatedAt, true, en)}</strong></p>

    <section className="md-filters" aria-label={L('Bộ lọc dashboard', 'Dashboard filters')} aria-busy={report.loading}>
      <div className="md-filter-group">
        <span className="md-filter-label">{L('Kỳ báo cáo', 'Period')}</span>
        <div className="md-segment" role="radiogroup" aria-label={L('Loại kỳ', 'Period type')}>
          {PERIODS.map(item => <button key={item.id} type="button" role="radio" aria-checked={filter.period === item.id}
            className={filter.period === item.id ? 'active' : ''} onClick={() => choosePeriod(item.id)}>{L(item.label, item.en)}</button>)}
        </div>
      </div>
      <label className="md-filter-group">
        <span className="md-filter-label">{L('Ngày', 'Date')}</span>
        <input type="date" aria-label={L('Chọn ngày', 'Pick a date')} value={anchor} max={todayInVietnam()} onChange={event => chooseDate(event.target.value)} />
      </label>
      <div className="md-filter-group md-branches">
        <span className="md-filter-label">{L('Chi nhánh', 'Branch')}</span>
        <div className="md-chips">
          <button type="button" aria-pressed={filter.locations.length === 0} className={filter.locations.length === 0 ? 'md-chip active' : 'md-chip'}
            onClick={() => setFilter(current => ({ ...current, locations: [] }))}>{L('Tất cả', 'All')}</button>
          {branchOptions.map(branch => <button key={branch.id} type="button" aria-pressed={filter.locations.includes(branch.id)}
            className={filter.locations.includes(branch.id) ? 'md-chip active' : 'md-chip'} onClick={() => toggleBranch(branch.id)}>{branch.name}</button>)}
        </div>
      </div>
    </section>

    {report.error && <div className="notice error md-notice" role="alert">
      <p>{errorText(report.error, L)}</p>
      {report.error.status !== 401 && report.error.status !== 403 && <button type="button" className="button small outline" onClick={report.reload}>{L('Thử lại', 'Try again')}</button>}
    </div>}
    {report.loading && !data && <Loading />}
    {data && <div className={report.loading ? 'md-reloading' : ''}><Report data={data} format={format} trend={trend} /></div>}
  </>;
}

function Report({ data, format, trend }) {
  const L = useL();
  const en = L(false, true);
  const totals = data.totals || {};
  const scope = data.scope || {};
  const branchCount = (scope.locationIds || []).length;
  const problems = (data.quality || []).filter(item => item.status === 'UNAVAILABLE' || item.safeReasonCode);
  return <>
    <p className="md-scope">
      <span>{(period => (period ? L(period.label, period.en) : scope.periodType))(PERIODS.find(item => item.id === scope.periodType))}</span>
      {vnTime(scope.startAt, false, en)} – {lastDay(scope, en)} · {L(`${branchCount} chi nhánh · giờ Việt Nam`, `${branchCount} ${branchCount === 1 ? 'branch' : 'branches'} · Vietnam time`)}
    </p>

    {data.status === 'PARTIAL' && <div className="md-quality warn" role="status">
      <strong>{L('Số liệu chưa đầy đủ.', 'Figures are incomplete.')}</strong> {L('Các mục đánh dấu “—” không có dữ liệu, không phải bằng 0.', 'Items marked “—” have no data; they are not zero.')}
      <ul>{problems.map(item => <li key={item.source}>{pick(SOURCE_LABELS[item.source], L) || item.source}: {pick(REASON_LABELS[item.safeReasonCode], L) || L('tạm thời không đọc được', 'temporarily unavailable')}</li>)}</ul>
    </div>}
    {data.status === 'EMPTY' && <div className="md-quality" role="status">
      <strong>{L('Chưa có giao dịch đã xác minh trong kỳ này.', 'No verified transactions in this period.')}</strong> {L('Đơn chờ thanh toán, đã hủy hoặc buổi chưa chốt điểm danh không được tính.', 'Unpaid or cancelled orders and sessions without finalized attendance are not counted.')}
    </div>}

    <section className="md-kpis" aria-label={L('Chỉ số chính', 'Key figures')}>
      <Kpi tone="amber" label={L('Doanh thu bán lẻ', 'Retail revenue')} value={format.money(totals.retailRevenue)} hint={L('Sau giảm giá, không gồm phí ship', 'After discounts, without shipping')} missing={totals.retailRevenue == null} />
      <Kpi tone="blue" label={L('Doanh thu workshop', 'Workshop revenue')} value={format.money(totals.workshopRevenue)} hint={L('Hóa đơn đã thanh toán đủ', 'Fully paid bills')} missing={totals.workshopRevenue == null} />
      <Kpi label={L('Tổng doanh thu', 'Total revenue')} value={format.money(totals.totalRevenue)} hint={L('Bán lẻ + workshop', 'Retail + workshop')} missing={totals.totalRevenue == null} strong />
      <Kpi tone="green" label={L('Đơn bán lẻ đã thanh toán', 'Paid retail orders')} value={totals.paidRetailOrderCount == null ? '—' : format.number(totals.paidRetailOrderCount)} hint={L('VNPay và tiền mặt đã xác nhận', 'Confirmed VNPay and cash')} missing={totals.paidRetailOrderCount == null} />
      <Kpi tone="violet" label={L('Tỷ lệ lấp đầy workshop', 'Workshop utilization')} value={percent(totals.workshopUtilizationPercent, en)}
        hint={totals.completedSessionCapacity ? L(`${format.number(totals.checkedInParticipants)} / ${format.number(totals.completedSessionCapacity)} chỗ đã check-in`, `${format.number(totals.checkedInParticipants)} / ${format.number(totals.completedSessionCapacity)} places checked in`) : L('Chưa có buổi đã chốt điểm danh', 'No session with finalized attendance yet')}
        missing={totals.completedSessionCapacity == null} meter={totals.workshopUtilizationPercent} />
    </section>

    <Trends trend={trend} format={format} />

    <BranchTable branches={data.branches || []} total={totals.totalRevenue} format={format} />
  </>;
}

const TREND_SOURCES = {
  RETAIL_ORDERS: ['Đơn bán lẻ', 'Retail orders'], RETAIL_REVENUE: ['Doanh thu bán lẻ', 'Retail revenue'], WORKSHOP_BOOKINGS: ['Vé workshop', 'Workshop tickets'],
  ATTENDANCE: ['Check-in workshop', 'Workshop check-in'], WORKSHOP_REVENUE: ['Doanh thu workshop', 'Workshop revenue'],
};

const C = { dark: '#111827', green: '#16a34a', red: '#dc2626', amber: '#f59e0b', gray: '#9ca3af', blue: '#2563eb' };

/** FE-10 trends (Session 2026-11-16): retail and workshop, by hour, day or month, against the previous period. */
function Trends({ trend, format }) {
  const L = useL();
  const en = L(false, true);
  const t = trend.data;
  if (trend.error) return <section className="md-panel md-trends" aria-label={L('Xu hướng', 'Trends')}>
    <div className="md-panel-head"><h2>{L('Xu hướng', 'Trends')}</h2></div>
    <div className="notice error md-notice" role="alert"><p>{L('Không tải được biểu đồ xu hướng.', 'The trend charts could not be loaded.')}</p>
      <button type="button" className="button small outline" onClick={trend.reload}>{L('Thử lại', 'Try again')}</button></div>
  </section>;
  if (!t) return <section className="md-panel md-trends" aria-label={L('Xu hướng', 'Trends')} aria-busy="true">
    <div className="md-panel-head"><h2>{L('Xu hướng', 'Trends')}</h2></div><Loading />
  </section>;

  const unit = pick(UNIT_LABEL[t.granularity], L) || L('ngày', 'day');
  const points = t.points || [];
  const col = key => points.map(p => p.values?.[key] ?? null);
  const cur = t.current || {};
  const prev = t.previous || {};
  const money = value => format.money(value);
  const count = value => format.number(value);
  const pct = value => percent(value, en);
  const retailDropped = v => v.retailCancelled == null ? null : Number(v.retailCancelled) + Number(v.retailExpired);
  const workshopDropped = v => v.workshopCancelled == null ? null : Number(v.workshopCancelled) + Number(v.workshopAbandoned);
  const attended = v => v.placesCheckedIn == null ? null : Number(v.placesCheckedIn) + Number(v.placesNoShow);

  const per = L(`Theo ${unit} · so với kỳ trước cùng độ dài`, `By ${unit} · vs the previous period of the same length`);
  return <section className={`md-trends${trend.loading ? ' md-reloading' : ''}`} aria-label={L('Xu hướng', 'Trends')}>
    {(t.unavailable || []).length > 0 && <div className="md-quality warn" role="status">
      <strong>{L('Một số biểu đồ chưa đủ dữ liệu:', 'Some charts are missing data:')}</strong> {t.unavailable.map(s => pick(TREND_SOURCES[s], L) || s).join(', ')} {L('tạm thời không đọc được.', 'temporarily unavailable.')}
    </div>}

    <div className="md-panel md-panel-tone tone-amber">
      <div className="md-panel-head"><h2>{L('Xu hướng bán lẻ', 'Retail trends')}</h2><span className="muted">{per}</span></div>
      <div className="md-trend-stats">
        <Stat label={L('Đơn đặt', 'Orders placed')} value={cur.retailPlaced} show={count}><Change current={cur.retailPlaced} previous={prev.retailPlaced} /></Stat>
        <Stat label={L('Đơn đã thanh toán', 'Orders paid')} value={cur.retailPaid} show={count}><Change current={cur.retailPaid} previous={prev.retailPaid} /></Stat>
        <Stat label={L('Đơn hủy / hết hạn', 'Cancelled / expired')} value={retailDropped(cur)} show={count}
          note={L(`Tỷ lệ ${pct(rate(retailDropped(cur), cur.retailPlaced))}`, `Rate ${pct(rate(retailDropped(cur), cur.retailPlaced))}`)}><Change current={retailDropped(cur)} previous={retailDropped(prev)} inverse /></Stat>
        <Stat label={L('Doanh thu bán lẻ', 'Retail revenue')} value={cur.retailRevenue} show={money}><Change current={cur.retailRevenue} previous={prev.retailRevenue} /></Stat>
      </div>
      <div className="md-chart-grid">
        <TrendChart title={L(`Doanh thu bán lẻ theo ${unit}`, `Retail revenue by ${unit}`)} kind="bar" points={points} format={money}
          series={[{ key: 'r', label: L('Doanh thu', 'Revenue'), color: C.amber, values: col('retailRevenue') }]} />
        <TrendChart title={L(`Đơn đặt và đơn bị hủy theo ${unit}`, `Orders placed and cancelled by ${unit}`)} points={points} format={count}
          series={[
            { key: 'placed', label: L('Đặt', 'Placed'), color: C.dark, values: col('retailPlaced') },
            { key: 'paid', label: L('Đã thanh toán', 'Paid'), color: C.green, values: col('retailPaid') },
            { key: 'cancelled', label: L('Hủy', 'Cancelled'), color: C.red, values: col('retailCancelled') },
            { key: 'expired', label: L('Hết hạn TT', 'Payment expired'), color: C.amber, values: col('retailExpired') },
          ]} />
      </div>
    </div>

    <div className="md-panel md-panel-tone tone-blue">
      <div className="md-panel-head"><h2>{L('Xu hướng workshop', 'Workshop trends')}</h2><span className="muted">{per}</span></div>
      <div className="md-trend-stats">
        <Stat label={L('Vé đặt', 'Tickets booked')} value={cur.workshopBooked} show={count}
          note={L(`Hủy / bỏ dở ${count(workshopDropped(cur) ?? 0)}`, `Cancelled / abandoned ${count(workshopDropped(cur) ?? 0)}`)}><Change current={cur.workshopBooked} previous={prev.workshopBooked} /></Stat>
        <Stat label={L('Lượt check-in', 'Check-ins')} value={cur.placesCheckedIn} show={count}
          note={L(`Trên ${count(cur.placesRegistered ?? 0)} lượt đăng ký`, `Out of ${count(cur.placesRegistered ?? 0)} registered`)}><Change current={cur.placesCheckedIn} previous={prev.placesCheckedIn} /></Stat>
        <Stat label={L('Tỷ lệ vắng mặt', 'No-show rate')} value={rate(cur.placesNoShow, attended(cur))} show={pct}
          note={L(`${count(cur.placesNoShow ?? 0)} lượt không đến`, `${count(cur.placesNoShow ?? 0)} no-shows`)}><Change current={rate(cur.placesNoShow, attended(cur))} previous={rate(prev.placesNoShow, attended(prev))} inverse /></Stat>
        <Stat label={L('Doanh thu workshop', 'Workshop revenue')} value={cur.workshopRevenue} show={money}><Change current={cur.workshopRevenue} previous={prev.workshopRevenue} /></Stat>
      </div>
      <div className="md-chart-grid">
        <TrendChart title={L(`Doanh thu workshop theo ${unit}`, `Workshop revenue by ${unit}`)} kind="bar" points={points} format={money}
          empty={L('Chưa có hóa đơn workshop nào được thanh toán đủ trong kỳ này.', 'No workshop bill was fully paid in this period.')}
          series={[{ key: 'w', label: L('Doanh thu', 'Revenue'), color: C.dark, values: col('workshopRevenue') }]} />
        <TrendChart title={L(`Người tham gia theo ${unit} diễn ra buổi học`, `Participants by ${unit} of the session`)} points={points} format={count}
          series={[
            { key: 'reg', label: L('Đăng ký', 'Registered'), color: C.blue, values: col('placesRegistered') },
            { key: 'in', label: 'Check-in', color: C.green, values: col('placesCheckedIn') },
            { key: 'no', label: L('Không đến', 'No-show'), color: C.amber, values: col('placesNoShow') },
            { key: 'cx', label: L('Hủy vé', 'Ticket cancelled'), color: C.red, values: col('placesCancelled') },
          ]} />
        <TrendChart title={L(`Vé đặt và vé bị hủy theo ${unit} đặt chỗ`, `Tickets booked and cancelled by ${unit} of booking`)} points={points} format={count}
          series={[
            { key: 'booked', label: L('Đặt', 'Booked'), color: C.dark, values: col('workshopBooked') },
            { key: 'ok', label: L('Đã thanh toán', 'Paid'), color: C.green, values: col('workshopConfirmed') },
            { key: 'cx', label: L('Hủy', 'Cancelled'), color: C.red, values: col('workshopCancelled') },
            { key: 'ab', label: L('Bỏ dở', 'Abandoned'), color: C.amber, values: col('workshopAbandoned') },
          ]} />
      </div>
      <p className="md-footnote">{L('Một lượt = một vé ở một buổi (gói 2 buổi tính 2 lượt). “Không đến” chỉ có sau khi buổi đã chốt điểm danh. Doanh thu workshop tính khi hóa đơn được thanh toán đủ; tiền cọc của khách không đến chưa được tính.',
        'One place = one ticket at one session (a 2-session package counts 2). “No-show” appears only once attendance is finalized. Workshop revenue counts when the bill is fully paid; deposits of no-shows are not counted yet.')}</p>
    </div>
  </section>;
}

function Stat({ label, value, show, note, children }) {
  return <article className="md-stat">
    <span className="md-kpi-label">{label}</span>
    <strong>{value == null ? '—' : show(value)}</strong>
    {children}
    {note && value != null && <small className="md-stat-note">{note}</small>}
  </article>;
}

function Kpi({ label, value, hint, missing, strong, meter, tone }) {
  const L = useL();
  return <article className={`md-kpi${strong ? ' md-kpi-strong' : ''}${tone ? ` tone-${tone}` : ''}`}>
    <span className="md-kpi-label">{label}</span>
    <strong className="md-kpi-value">{missing ? '—' : value}</strong>
    {meter != null && <span className="md-meter" aria-hidden="true"><span style={{ width: `${Math.min(100, meter)}%` }} /></span>}
    <small>{missing ? L('Chưa có dữ liệu', 'No data yet') : hint}</small>
  </article>;
}

function BranchTable({ branches, total, format }) {
  const L = useL();
  const en = L(false, true);
  const totalNumber = Number(total || 0);
  return <section className="md-panel md-panel-tone tone-dark" aria-label={L('Chỉ số theo chi nhánh', 'Figures by branch')}>
    <div className="md-panel-head"><h2>{L('Theo chi nhánh', 'By branch')}</h2><span className="muted">{L(`${branches.length} chi nhánh`, `${branches.length} ${branches.length === 1 ? 'branch' : 'branches'}`)}</span></div>
    {branches.length === 0 ? <p className="md-empty">{L('Không có chi nhánh trong phạm vi.', 'No branch in scope.')}</p> :
      <div className="md-table-wrap"><table className="md-table">
        <thead><tr>
          <th scope="col">{L('Chi nhánh', 'Branch')}</th><th scope="col">{L('Bán lẻ', 'Retail')}</th><th scope="col">Workshop</th>
          <th scope="col">{L('Tổng doanh thu', 'Total revenue')}</th><th scope="col">{L('Đơn đã TT', 'Paid orders')}</th><th scope="col">{L('Lấp đầy', 'Utilization')}</th>
        </tr></thead>
        <tbody>{branches.map(branch => {
          const v = branch.values || {};
          const share = totalNumber > 0 && v.totalRevenue != null ? Number(v.totalRevenue) * 100 / totalNumber : null;
          return <tr key={branch.locationId}>
            <th scope="row" data-label={L('Chi nhánh', 'Branch')}>{branch.branchName}</th>
            <td data-label={L('Bán lẻ', 'Retail')}>{format.money(v.retailRevenue)}</td>
            <td data-label="Workshop">{format.money(v.workshopRevenue)}</td>
            <td data-label={L('Tổng doanh thu', 'Total revenue')}><strong>{format.money(v.totalRevenue)}</strong>
              {share != null && <span className="md-share" title={L(`${percent(share)} tổng doanh thu`, `${percent(share, true)} of total revenue`)}><span style={{ width: `${share}%` }} /></span>}</td>
            <td data-label={L('Đơn đã TT', 'Paid orders')}>{v.paidRetailOrderCount == null ? '—' : format.number(v.paidRetailOrderCount)}</td>
            <td data-label={L('Lấp đầy', 'Utilization')}>{v.workshopUtilizationPercent == null ? <span className="muted">{v.completedSessionCapacity === 0 ? L('Chưa có buổi đã chốt điểm danh', 'No session with finalized attendance yet') : '—'}</span>
              : <span className="md-util"><span className="md-meter small"><span style={{ width: `${Math.min(100, v.workshopUtilizationPercent)}%` }} /></span>{percent(v.workshopUtilizationPercent, en)}</span>}</td>
          </tr>;
        })}</tbody>
      </table></div>}
  </section>;
}
