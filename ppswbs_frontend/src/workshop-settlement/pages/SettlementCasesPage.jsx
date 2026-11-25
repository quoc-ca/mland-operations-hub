import React, { useMemo, useState } from 'react';
import { useUrlFilters } from '../../shared/ui/useUrlFilters';
import { Link } from 'react-router';
import { useHttp } from '../../shared/api/useHttp';
import { ErrorNotice, Loading, useResource } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { OpsPage } from '../OpsPage';
import { useEnumLabel, useT } from '../../shared/i18n/I18nProvider';
import { messages } from '../messages';
import { asList } from '../ui';

/**
 * Manager case list. The Mland refund policy was removed (FR-043: Mland always refunds 100%), as were case detail by
 * booking and the whole-booking cancellation, remedy and required-session-change routes (FR-047, FR-048): per-ticket
 * work lives on the ticket folio, refund queue, ticket actions and change-request screens, so this page only lists
 * cases the server still serves.
 */
export default function SettlementCasesPage() {
  const t = useT(messages);
  const typeLabel = useEnumLabel(messages, 'caseType');
  const statusLabel = useEnumLabel(messages, 'caseStatus');
  const api = createSettlementApi(useHttp());
  const [filters, setFilters] = useUrlFilters({ caseType: '', status: '' });
  const caseList = useResource(() => api.listCases({ ...(filters.caseType ? { caseType: filters.caseType } : {}), ...(filters.status ? { status: filters.status } : {}) }), [filters.caseType, filters.status]);
  const cases = useMemo(() => asList(caseList.data), [caseList.data]);

  return (
    <OpsPage title={t('cases.title')} intro={t('cases.intro')}>
      <ErrorNotice error={caseList.error} onRetry={() => caseList.reload()} />
      <section className="wsp-panel">
        <div className="wsp-panel-head"><h2>{t('cases.all')}</h2><button className="button secondary" type="button" onClick={() => caseList.reload()}>{t('cases.refresh')}</button></div>
        <div className="wsp-inline-form">
          <label>{t('cases.type')}
            <select value={filters.caseType} onChange={event => setFilters(v => ({ ...v, caseType: event.target.value }))}>
              <option value="">{t('cases.filterAll')}</option>
              <option value="CUSTOMER_CANCELLATION">{typeLabel('CUSTOMER_CANCELLATION')}</option>
              <option value="REQUIRED_SESSION_CHANGE">{typeLabel('REQUIRED_SESSION_CHANGE')}</option>
              <option value="MLAND_CANCELLATION_REMEDY">{typeLabel('MLAND_CANCELLATION_REMEDY')}</option>
            </select>
          </label>
          <label>{t('cases.status')}
            <select value={filters.status} onChange={event => setFilters(v => ({ ...v, status: event.target.value }))}>
              <option value="">{t('cases.filterAll')}</option>
              {['OPEN', 'PENDING_PROVIDER', 'RESOLVED', 'REJECTED', 'FAILED'].map(value => <option key={value} value={value}>{statusLabel(value)}</option>)}
            </select>
          </label>
        </div>
        {caseList.loading ? <Loading /> : caseList.error ? null : cases.length === 0 ? <div className="empty-state"><p>{t('cases.empty')}</p></div> : (
          <table className="ops-table">
            <thead><tr><th>{t('cases.col.case')}</th><th>{t('cases.col.type')}</th><th>{t('cases.col.status')}</th><th>{t('cases.col.booking')}</th></tr></thead>
            <tbody>{cases.map(item => <tr key={item.caseReference}><td>{item.caseReference}</td><td>{typeLabel(item.caseType)}</td><td><span className="ops-status">{statusLabel(item.status)}</span></td><td>{item.bookingCode}</td></tr>)}</tbody>
          </table>
        )}
        <p className="muted small">{t('cases.refundsIn')} <Link to="/settlement/refunds">{t('cases.refundQueue')}</Link>{t('cases.changesIn')} <Link to="/settlement/change-requests">{t('cases.changeQueue')}</Link>.</p>
      </section>
    </OpsPage>
  );
}
