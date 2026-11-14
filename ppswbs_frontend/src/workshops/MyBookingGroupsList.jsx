import React from 'react';
import { Link } from 'react-router';
import { GROUP_STATUS_LABEL, formatSessionWindow, vnd } from './groupDisplay';

/** A session that already started can have reviews, so the package's reviews are offered from then on. */
function hasHeldSession(group) {
  const now = Date.now();
  return (group?.tickets || []).some(ticket => (ticket.sessions || []).some(session => {
    const start = new Date(session.scheduledStart).getTime();
    return !Number.isNaN(start) && start <= now;
  }));
}

function packageNames(group) {
  const names = new Set();
  (group?.lines || []).forEach(line => names.add(line.packageName || `Gói #${line.packageId}`));
  (group?.tickets || []).forEach(ticket => names.add(ticket.packageName || `Gói #${ticket.packageId}`));
  return [...names].filter(Boolean);
}

function sessionLabels(group) {
  const seen = new Map();
  (group?.tickets || []).forEach(ticket => (ticket.sessions || []).forEach(session => {
    if (!seen.has(session.sessionReference)) {
      seen.set(session.sessionReference, formatSessionWindow(session.scheduledStart, session.scheduledEnd));
    }
  }));
  return [...seen.values()];
}

function packageIdOf(group) {
  return group?.lines?.[0]?.packageId ?? group?.tickets?.[0]?.packageId ?? null;
}

/**
 * "Đặt chỗ của tôi" of a signed-in Member (003.1 FR-046, FR-048): the Member's own booking groups, newest first, each
 * with its status and its tickets, so the Member sees their tickets without typing a group code. Choosing one opens
 * the same group view a lookup shows.
 */
export function MyBookingGroupsList({ groups, loading, error, onRetry, onOpen, selectedCode }) {
  return (
    <section className="workspace-card my-bookings" aria-label="Đặt chỗ của tôi" style={{ marginBottom: '24px' }}>
      <div className="card-head" style={{ marginBottom: '12px' }}>
        <h2 style={{ margin: 0 }}>Đặt chỗ của tôi</h2>
        {Array.isArray(groups) && groups.length > 0 && <span className="muted">{groups.length} nhóm đặt chỗ</span>}
      </div>

      {loading && <p className="muted" role="status">Đang tải đặt chỗ của bạn…</p>}
      {error && (
        <p role="alert" style={{ color: '#b00' }}>
          {error} <button type="button" className="button outline small" onClick={onRetry}>Thử lại</button>
        </p>
      )}
      {!loading && !error && Array.isArray(groups) && groups.length === 0 && (
        <p className="muted">Bạn chưa có đặt chỗ nào. Sau khi đặt và thanh toán, vé của bạn sẽ hiện ở đây.</p>
      )}

      {Array.isArray(groups) && groups.length > 0 && (
        <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'grid', gap: '12px' }}>
          {groups.map(group => {
            const selected = selectedCode === group.groupCode;
            const sessions = sessionLabels(group);
            const packageId = packageIdOf(group);
            return (
              <li
                key={group.groupCode}
                style={{ border: selected ? '2px solid #002967' : '1px solid #e3e3e3', padding: '14px 16px', background: '#fff' }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: '12px', flexWrap: 'wrap' }}>
                  <div>
                    <strong style={{ color: '#002967', fontSize: '16px' }}>{group.groupCode}</strong>
                    <div>{packageNames(group).join(' · ')} · {(group.tickets || []).length} vé</div>
                  </div>
                  <span
                    className={`status ${group.groupStatus === 'CONFIRMED' ? 'ok' : ''}`}
                    style={{ fontWeight: 'bold', alignSelf: 'start' }}
                  >
                    {GROUP_STATUS_LABEL[group.groupStatus] || group.groupStatus}
                  </span>
                </div>

                {sessions.length > 0 && (
                  <p className="muted" style={{ margin: '8px 0 0', fontSize: '13px' }}>
                    {sessions.length > 1 ? 'Các buổi: ' : 'Buổi: '}{sessions.join(' · ')}
                  </p>
                )}
                <p className="muted" style={{ margin: '4px 0 0', fontSize: '13px' }}>
                  Thanh toán khi đặt: {vnd(group.groupCollectionVnd)}
                </p>

                <div style={{ marginTop: '10px', display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                  <button
                    type="button"
                    className="button outline small"
                    onClick={() => onOpen(group)}
                    aria-label={`Xem chi tiết ${group.groupCode}`}
                  >
                    {group.groupStatus === 'CONFIRMED' ? 'Xem vé và chi tiết' : 'Xem chi tiết'}
                  </button>
                  {hasHeldSession(group) && packageId != null && (
                    <Link className="button outline small" to={`/reviews?packageId=${encodeURIComponent(packageId)}`}>
                      Xem đánh giá của gói
                    </Link>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
