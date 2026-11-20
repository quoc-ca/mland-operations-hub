import { Fragment, useState } from 'react';
import { actorLabel, formatTime, shortRef } from './auditReviewModel';

const Ref = ({ value }) => <span className="mono" title={value || undefined}>{shortRef(value)}</span>;

function OutcomePill({ outcome }) {
  // Colour and text together, never colour alone.
  return <span className={`audit-pill ${outcome}`}>{outcome}</span>;
}

/** Operations tab (FR-051): time, actor, action, outcome, session, correlation id; whitelisted metadata on demand. */
export function OperationsRows({ rows }) {
  const [openIds, setOpenIds] = useState(() => new Set());
  const toggle = id => setOpenIds(current => {
    const next = new Set(current);
    if (next.has(id)) next.delete(id); else next.add(id);
    return next;
  });
  return (
    <table className="audit-table">
      <thead>
        <tr>
          <th scope="col">Thời gian</th><th scope="col">Người thực hiện</th><th scope="col">Hành động</th>
          <th scope="col">Kết quả</th><th scope="col">Ca</th><th scope="col">Mã tương quan</th><th scope="col"><span className="sr-only">Chi tiết</span></th>
        </tr>
      </thead>
      <tbody>
        {rows.map(row => {
          const open = openIds.has(row.id);
          const entries = Object.entries(row.metadata || {});
          return (
            <Fragment key={row.id}>
              <tr className={open ? 'is-open' : undefined}>
                <td data-label="Thời gian">{formatTime(row.occurredAt)}</td>
                <td data-label="Người thực hiện">{actorLabel(row.actor) || 'Hệ thống'}</td>
                <td data-label="Hành động" className="mono">{row.action}</td>
                <td data-label="Kết quả"><OutcomePill outcome={row.outcome} /></td>
                <td data-label="Ca" className="mono">{row.sessionId ? `#${row.sessionId}` : '—'}</td>
                <td data-label="Mã tương quan"><Ref value={row.correlationId} /></td>
                <td className="audit-row-action">
                  <button
                    type="button"
                    className="button outline small"
                    aria-expanded={open}
                    onClick={() => toggle(row.id)}
                  >
                    {open ? 'Thu gọn' : 'Chi tiết'}
                  </button>
                </td>
              </tr>
              {open && (
                <tr className="audit-detail">
                  <td colSpan={7}>
                    {entries.length > 0 ? (
                      <dl className="audit-meta">
                        {entries.map(([key, value]) => (
                          <Fragment key={key}><dt>{key}</dt><dd className="mono">{value}</dd></Fragment>
                        ))}
                      </dl>
                    ) : <p className="audit-note">—</p>}
                    <p className="audit-note">Chỉ hiện các khóa được phép. Không có email, số điện thoại hay họ tên khách.</p>
                  </td>
                </tr>
              )}
            </Fragment>
          );
        })}
      </tbody>
    </table>
  );
}

/** Settlement tab (FR-052): the full reason text is shown; it is Manager-only and never written anywhere. */
export function SettlementRows({ rows }) {
  return (
    <>
      <table className="audit-table">
        <thead>
          <tr>
            <th scope="col">Thời gian</th><th scope="col">Người thực hiện</th><th scope="col">Mã hành động</th>
            <th scope="col">Đối tượng</th><th scope="col">Tham chiếu chứng từ</th><th scope="col">Lý do</th><th scope="col">Mã tương quan</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(row => (
            <tr key={row.id}>
              <td data-label="Thời gian">{formatTime(row.occurredAt)}</td>
              <td data-label="Người thực hiện">{actorLabel(row.actor) || row.actorReference || '—'}</td>
              <td data-label="Mã hành động" className="mono">{row.actionCode}</td>
              <td data-label="Đối tượng" className="mono">{row.aggregateType}<br />{row.aggregateReference}</td>
              <td data-label="Tham chiếu chứng từ" className="mono">{row.evidenceReference || '—'}</td>
              <td data-label="Lý do" className="audit-reason">{row.reasonText || '—'}</td>
              <td data-label="Mã tương quan"><Ref value={row.correlationReference} /></td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="audit-note">Lý do hiện đầy đủ (tối đa 500 ký tự); có thể chứa thông tin cá nhân, chỉ Manager xem.</p>
    </>
  );
}
