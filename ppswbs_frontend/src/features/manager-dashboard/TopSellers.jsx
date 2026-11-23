import React from 'react';
import { Loading } from '../../shared/ui/components';
import { useL } from './TrendCharts';

/**
 * FE-10 best sellers (Thinh, 2026-11-23): the five products with the most units sold (paid retail orders) and the five
 * workshop packages with the most confirmed tickets, for the dashboard filters. Retail stays amber and workshop blue,
 * like the rest of the page. Each row names the item and writes its count, so colour is never the only cue; hovering or
 * focusing a row shows its revenue and its share of the five.
 */
export default function TopSellers({ resource, format }) {
  const L = useL();
  const title = L('Bán chạy nhất', 'Best sellers');
  if (resource.error) return <section className="md-panel md-top" aria-label={title}>
    <div className="md-panel-head"><h2>{title}</h2></div>
    <div className="notice error md-notice" role="alert"><p>{L('Không tải được danh sách bán chạy.', 'The best sellers could not be loaded.')}</p>
      <button type="button" className="button small outline" onClick={resource.reload}>{L('Thử lại', 'Try again')}</button></div>
  </section>;
  if (!resource.data) return <section className="md-panel md-top" aria-label={title} aria-busy="true">
    <div className="md-panel-head"><h2>{title}</h2></div><Loading />
  </section>;

  const data = resource.data;
  return <section className={`md-top${resource.loading ? ' md-reloading' : ''}`} aria-label={title}>
    <div className="md-chart-grid">
      <Ranking tone="amber" title={L('Top 5 sản phẩm bán chạy', 'Top 5 products')}
        note={L('Theo số sản phẩm trong đơn đã thanh toán', 'By units in paid orders')}
        items={data.products} unit={L('chiếc', 'sold')} format={format}
        revenueLabel={L('Tiền hàng (trước ưu đãi)', 'Merchandise (before discounts)')}
        empty={L('Chưa có sản phẩm nào được bán trong kỳ này.', 'No product was sold in this period.')} />
      <Ranking tone="blue" title={L('Top 5 gói workshop đặt nhiều nhất', 'Top 5 workshop packages')}
        note={L('Theo số vé đã xác nhận', 'By confirmed tickets')}
        items={data.packages} unit={L('vé', 'tickets')} format={format}
        revenueLabel={L('Theo giá gói (trước ưu đãi)', 'At package price (before promotions)')}
        empty={L('Chưa có vé workshop nào được xác nhận trong kỳ này.', 'No workshop ticket was confirmed in this period.')} />
    </div>
  </section>;
}

function Ranking({ tone, title, note, items, unit, format, revenueLabel, empty }) {
  const L = useL();
  const list = items || [];
  const max = Math.max(0, ...list.map(item => Number(item.quantity)));
  const sum = list.reduce((total, item) => total + Number(item.quantity), 0);
  return <figure className={`md-panel md-panel-tone tone-${tone} md-rank`} aria-label={title}>
    <figcaption className="md-panel-head">
      <h2>{title}</h2><span className="muted">{note}</span>
    </figcaption>
    {items == null ? <p className="md-empty">{L('Tạm thời không đọc được dữ liệu này.', 'This data is temporarily unavailable.')}</p>
      : list.length === 0 ? <p className="md-empty">{empty}</p>
      : <ol className="md-rank-list">
        {list.map((item, index) => {
          const width = max > 0 ? Math.max(4, (Number(item.quantity) / max) * 100) : 0;
          const share = sum > 0 ? Math.round((Number(item.quantity) * 100) / sum) : 0;
          return <li key={item.id ?? index} className="md-rank-row" tabIndex={0}
            aria-label={`${index + 1}. ${item.name}: ${format.number(item.quantity)} ${unit}, ${revenueLabel} ${format.money(item.revenue)}`}>
            <span className={`md-rank-badge${index === 0 ? ' first' : ''}`} aria-hidden="true">{index + 1}</span>
            <span className="md-rank-main">
              <span className="md-rank-name" title={item.name}>{item.name}</span>
              <span className="md-rank-track" aria-hidden="true"><span style={{ width: `${width}%` }} /></span>
            </span>
            <span className="md-rank-value"><strong>{format.number(item.quantity)}</strong> {unit}</span>
            <span className="md-tooltip md-rank-tip" aria-hidden="true">
              <strong>{item.name}</strong>
              <span>{L('Số lượng', 'Quantity')}: <b>{format.number(item.quantity)} {unit}</b></span>
              <span>{revenueLabel}: <b>{format.money(item.revenue)}</b></span>
              <span>{L('Tỷ trọng trong top 5', 'Share of the top 5')}: <b>{share}%</b></span>
            </span>
          </li>;
        })}
      </ol>}
  </figure>;
}
