import React, { useState } from 'react';
import { Link } from 'react-router';
import { useLanguage, useT } from '../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading } from '../../shared/ui/components';
import { StaffRetailOrdersPanel } from '../retail-order';
import { operatorHomeMessages } from './messages';
import { useStaffWork, workshopTasks } from './staffWork';
import './staff-home.css';

const SECOND_LINKS = [
  { to: '/settlement', key: 'link.settlement' },
  { to: '/ota-connectivity/add-ons', key: 'link.otaAddOns' },
];

/**
 * Staff home (Session 2026-11-22): two large cards, Workshop and Orders. The chosen side lists its work below; the
 * other card shows in red how much waits there. The Orders side is the branch retail order list itself.
 */
export default function StaffHome({ name }) {
  const t = useT(operatorHomeMessages);
  const { language } = useLanguage();
  const [side, setSide] = useState('WORKSHOP');
  const work = useStaffWork();
  const tasks = work.sessions.data ? workshopTasks(work.sessions.data, Date.now(), t, language) : [];
  const workshop = side === 'WORKSHOP';
  const count = value => (value == null ? '—' : value);

  return <div className="staff-home">
    <header className="ota-page-heading">
      <div>
        {/* The page title "Trang chủ" comes from the operations shell header; this only greets. */}
        <h2 className="operator-home-hello">{t('hello')}{name ? `, ${name}` : ''}</h2>
        <p>{new Date().toLocaleDateString(language === 'en' ? 'en-GB' : 'vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', weekday: 'long', day: '2-digit', month: '2-digit' })}</p>
      </div>
    </header>

    <div className="staff-home-cards" role="tablist" aria-label={t('yourWork')}>
      <button type="button" role="tab" aria-selected={workshop} className={`staff-home-card${workshop ? ' active' : ''}`} onClick={() => setSide('WORKSHOP')}>
        <span className="staff-home-card-head">
          <span className="staff-home-icon workshop" aria-hidden="true">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="4" width="18" height="17" rx="2" /><path d="M8 2v4M16 2v4M3 10h18" /></svg>
          </span>
          <span className="staff-home-card-name"><strong>{t('card.workshop')}</strong><small>{t('card.workshopHint')}</small></span>
          {!workshop && work.workshopCount > 0 && <span className="staff-home-alert">{t('card.workshopAlert', { count: work.workshopCount })}</span>}
        </span>
        <span className="staff-home-count"><strong>{count(work.workshopCount)}</strong>{t('card.tasksToday')}</span>
        {!workshop && tasks[0] && <span className="staff-home-next">{tasks[0].title} · {tasks[0].detail}</span>}
      </button>
      <button type="button" role="tab" aria-selected={!workshop} className={`staff-home-card${workshop ? '' : ' active'}`} onClick={() => setSide('ORDERS')}>
        <span className="staff-home-card-head">
          <span className="staff-home-icon orders" aria-hidden="true">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M3 7l9-4 9 4-9 4-9-4z" /><path d="M3 7v10l9 4 9-4V7" /><path d="M12 11v10" /></svg>
          </span>
          <span className="staff-home-card-name"><strong>{t('card.orders')}</strong><small>{t('card.ordersHint')}</small></span>
          {workshop && work.retailCount > 0 && <span className="staff-home-alert">{t('card.ordersAlert', { count: work.retailCount })}</span>}
        </span>
        <span className="staff-home-count"><strong>{count(work.retailCount)}</strong>{t('card.ordersWaiting')}</span>
      </button>
    </div>

    {workshop ? <section className="staff-home-list" aria-label={t('list.title')}>
      <div className="staff-home-list-head">
        <h2 className="operator-home-section">{t('list.title')}</h2>
        <Link to="/operations/sessions">{t('list.openSessions')}</Link>
      </div>
      {work.sessions.loading && !work.sessions.data && <Loading />}
      {work.sessions.error && <ErrorNotice error={work.sessions.error} onRetry={work.sessions.reload} />}
      {work.sessions.data && tasks.length === 0 && <p className="staff-home-empty">{t('list.empty')}</p>}
      {tasks.map(task => <Link key={task.id} className="staff-home-task" to={`/operations/sessions/${encodeURIComponent(task.id)}`}>
        <span className={`staff-home-dot${task.urgent ? ' urgent' : ''}`} aria-hidden="true" />
        <span className="staff-home-task-text"><strong>{task.title}</strong><small>{task.detail}</small></span>
        <span className="staff-home-task-action">{task.action}</span>
      </Link>)}
      <div className="staff-home-more">
        {SECOND_LINKS.map(link => <Link key={link.to} to={link.to}>{t(link.key)}</Link>)}
      </div>
    </section> : <section className="staff-home-orders" aria-label={t('orders.section')}>
      <StaffRetailOrdersPanel />
    </section>}
  </div>;
}
