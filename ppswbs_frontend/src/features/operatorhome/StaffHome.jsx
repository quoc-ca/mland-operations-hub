import React, { useState } from 'react';
import { Link } from 'react-router';
import { ErrorNotice, Loading } from '../../shared/ui/components';
import { StaffRetailOrdersPanel } from '../retail-order';
import { useStaffWork, workshopTasks } from './staffWork';
import './staff-home.css';

const SECOND_LINKS = [
  { to: '/settlement', vi: 'Hóa đơn quyết toán', en: 'Final bills' },
  { to: '/ota-connectivity/add-ons', vi: 'Add-on tại xưởng (OTA)', en: 'Shop add-ons (OTA)' },
];

/**
 * Staff home (Session 2026-11-22): two large cards, Workshop and Orders. The chosen side lists its work below; the
 * other card shows in red how much waits there. The Orders side is the branch retail order list itself.
 */
export default function StaffHome({ name, vi }) {
  const [side, setSide] = useState('WORKSHOP');
  const work = useStaffWork();
  const tasks = work.sessions.data ? workshopTasks(work.sessions.data, Date.now(), vi) : [];
  const workshop = side === 'WORKSHOP';
  const count = value => (value == null ? '—' : value);

  return <div className="staff-home">
    <header className="ota-page-heading">
      <div>
        {/* The page title "Trang chủ" comes from the operations shell header; this only greets. */}
        <h2 className="operator-home-hello">{vi ? 'Xin chào' : 'Hello'}{name ? `, ${name}` : ''}</h2>
        <p>{new Date().toLocaleDateString(vi ? 'vi-VN' : 'en-GB', { timeZone: 'Asia/Ho_Chi_Minh', weekday: 'long', day: '2-digit', month: '2-digit' })}</p>
      </div>
    </header>

    <div className="staff-home-cards" role="tablist" aria-label={vi ? 'Việc của bạn' : 'Your work'}>
      <button type="button" role="tab" aria-selected={workshop} className={`staff-home-card${workshop ? ' active' : ''}`} onClick={() => setSide('WORKSHOP')}>
        <span className="staff-home-card-head">
          <span className="staff-home-icon workshop" aria-hidden="true">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="4" width="18" height="17" rx="2" /><path d="M8 2v4M16 2v4M3 10h18" /></svg>
          </span>
          <span className="staff-home-card-name"><strong>Workshop</strong><small>{vi ? 'Buổi học, check-in, quyết toán' : 'Sessions, check-in, final bills'}</small></span>
          {!workshop && work.workshopCount > 0 && <span className="staff-home-alert">{vi ? `${work.workshopCount} việc workshop cần làm` : `${work.workshopCount} workshop tasks`}</span>}
        </span>
        <span className="staff-home-count"><strong>{count(work.workshopCount)}</strong>{vi ? 'việc hôm nay' : 'tasks today'}</span>
        {!workshop && tasks[0] && <span className="staff-home-next">{tasks[0].title} · {tasks[0].detail}</span>}
      </button>
      <button type="button" role="tab" aria-selected={!workshop} className={`staff-home-card${workshop ? '' : ' active'}`} onClick={() => setSide('ORDERS')}>
        <span className="staff-home-card-head">
          <span className="staff-home-icon orders" aria-hidden="true">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M3 7l9-4 9 4-9 4-9-4z" /><path d="M3 7v10l9 4 9-4V7" /><path d="M12 11v10" /></svg>
          </span>
          <span className="staff-home-card-name"><strong>{vi ? 'Đơn hàng' : 'Orders'}</strong><small>{vi ? 'Bán lẻ tại chi nhánh, GHTK' : 'Branch retail, GHTK'}</small></span>
          {workshop && work.retailCount > 0 && <span className="staff-home-alert">{vi ? `${work.retailCount} đơn cần giải quyết` : `${work.retailCount} orders to handle`}</span>}
        </span>
        <span className="staff-home-count"><strong>{count(work.retailCount)}</strong>{vi ? 'đơn đang chờ' : 'orders waiting'}</span>
      </button>
    </div>

    {workshop ? <section className="staff-home-list" aria-label={vi ? 'Việc workshop hôm nay' : 'Workshop work today'}>
      <div className="staff-home-list-head">
        <h2 className="operator-home-section">{vi ? 'Việc workshop hôm nay' : 'Workshop work today'}</h2>
        <Link to="/operations/sessions">{vi ? 'Mở ca của tôi →' : 'Open my sessions →'}</Link>
      </div>
      {work.sessions.loading && !work.sessions.data && <Loading />}
      {work.sessions.error && <ErrorNotice error={work.sessions.error} onRetry={work.sessions.reload} />}
      {work.sessions.data && tasks.length === 0 && <p className="staff-home-empty">{vi ? 'Hôm nay bạn không còn buổi nào cần xử lý.' : 'No session needs you today.'}</p>}
      {tasks.map(task => <Link key={task.id} className="staff-home-task" to={`/operations/sessions/${encodeURIComponent(task.id)}`}>
        <span className={`staff-home-dot${task.urgent ? ' urgent' : ''}`} aria-hidden="true" />
        <span className="staff-home-task-text"><strong>{task.title}</strong><small>{task.detail}</small></span>
        <span className="staff-home-task-action">{task.action}</span>
      </Link>)}
      <div className="staff-home-more">
        {SECOND_LINKS.map(link => <Link key={link.to} to={link.to}>{vi ? link.vi : link.en}</Link>)}
      </div>
    </section> : <section className="staff-home-orders" aria-label={vi ? 'Đơn bán lẻ tại chi nhánh' : 'Branch retail orders'}>
      <StaffRetailOrdersPanel />
    </section>}
  </div>;
}
