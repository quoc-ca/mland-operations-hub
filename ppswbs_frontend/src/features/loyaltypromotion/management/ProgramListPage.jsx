import React from 'react';
import { Link, useLocation, useSearchParams } from 'react-router';
import { useFormat, useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, SuccessNotice } from '../../../shared/ui/components';
import { useLoyaltyApi } from '../api/useLoyaltyApi';
import { DiscountText, ScopeBadge } from '../display';
import { messages } from '../messages';
import { PageTop } from './PromotionManagementLayout';
import { useCursorList } from '../useCursorList';

/** T030: programme list with scope/status filters (in the URL) and cursor paging. */
export default function ProgramListPage() {
  const t = useT(messages);
  const format = useFormat();
  const api = useLoyaltyApi();
  const location = useLocation();
  const [params, setParams] = useSearchParams();
  const scope = params.get('scope') || '';
  const status = params.get('status') || '';
  const list = useCursorList(cursor => api.listPrograms({ scope: scope || undefined, status: status || undefined, cursor }), [scope, status]);
  const notice = location.state?.notice || null;

  const setFilter = name => event => {
    const next = new URLSearchParams(params);
    if (event.target.value) next.set(name, event.target.value); else next.delete(name);
    setParams(next);
  };

  return (
    <>
      <PageTop title={t('programs.title')}>
        <Link className="button primary" to="/promotion-management/programs/new">{t('nav.newProgram')}</Link>
      </PageTop>
      <SuccessNotice>{notice && t(notice)}</SuccessNotice>
      <div className="filter-bar">
        <label>{t('field.scope')}
          <select value={scope} onChange={setFilter('scope')}>
            <option value="">{t('all')}</option>
            <option value="PRODUCT">{t('scope.PRODUCT')}</option>
            <option value="WORKSHOP">{t('scope.WORKSHOP')}</option>
          </select>
        </label>
        <label>{t('field.status')}
          <select value={status} onChange={setFilter('status')}>
            <option value="">{t('all')}</option>
            <option value="ENABLED">{t('status.ENABLED')}</option>
            <option value="PAUSED">{t('status.PAUSED')}</option>
          </select>
        </label>
      </div>
      {list.loading && list.items.length === 0 && <Loading />}
      {list.error && <ErrorNotice error={list.error} messages={messages} onRetry={list.retry} />}
      {!list.loading && !list.error && list.items.length === 0 && <div className="empty-state">{t('programs.empty')}</div>}
      {list.items.length > 0 && (
        <section className="workspace-card">
          <div className="data-table" role="table" aria-label={t('programs.title')}>
            <div className="data-row head programs" role="row">
              <span role="columnheader">{t('field.name')}</span><span role="columnheader">{t('field.kind')}</span>
              <span role="columnheader">{t('field.discount')}</span><span role="columnheader">{t('field.window')}</span>
              <span role="columnheader">{t('field.status')}</span>
            </div>
            {list.items.map(program => (
              <div className="data-row programs" role="row" key={program.id}>
                <span role="cell"><Link className="text-link" to={`/promotion-management/programs/${encodeURIComponent(program.id)}`}>{program.name}</Link>{program.code && <span className="mono"> · {program.code}</span>}</span>
                <span role="cell">{t(`kind.${program.kind}`)} <ScopeBadge scope={program.scope} /></span>
                <span role="cell"><DiscountText discount={program.discount} /></span>
                <span role="cell">{format.dateTime(program.startAt)} – {format.dateTime(program.endAt)}</span>
                <span role="cell"><span className={program.status === 'ENABLED' ? 'status ok' : 'status'}>{t(`status.${program.status}`)}</span></span>
              </div>
            ))}
          </div>
          {list.nextCursor && <div className="pager"><button type="button" className="button outline" disabled={list.loading} onClick={list.more}>{t('more')}</button></div>}
        </section>
      )}
    </>
  );
}
