import { Fragment, useState } from 'react';
import { useEnumLabel, useT } from '../../shared/i18n/I18nProvider';
import { messages } from '../messages';
import { actorLabel, formatTime, shortRef } from './auditReviewModel';

const Ref = ({ value }) => <span className="mono" title={value || undefined}>{shortRef(value)}</span>;

function OutcomePill({ outcome }) {
  const outcomeLabel = useEnumLabel(messages, 'auditOutcome');
  // Colour and text together, never colour alone.
  return <span className={`audit-pill ${outcome}`}>{outcomeLabel(outcome)}</span>;
}

/** Operations tab (FR-051): time, actor, action, outcome, session, correlation id; whitelisted metadata on demand. */
export function OperationsRows({ rows }) {
  const t = useT(messages);
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
          <th scope="col">{t('audit.col.time')}</th><th scope="col">{t('audit.col.actor')}</th><th scope="col">{t('audit.col.action')}</th>
          <th scope="col">{t('audit.col.outcome')}</th><th scope="col">{t('audit.col.session')}</th><th scope="col">{t('audit.col.correlation')}</th><th scope="col"><span className="sr-only">{t('audit.col.details')}</span></th>
        </tr>
      </thead>
      <tbody>
        {rows.map(row => {
          const open = openIds.has(row.id);
          const entries = Object.entries(row.metadata || {});
          return (
            <Fragment key={row.id}>
              <tr className={open ? 'is-open' : undefined}>
                <td data-label={t('audit.col.time')}>{formatTime(row.occurredAt)}</td>
                <td data-label={t('audit.col.actor')}>{actorLabel(row.actor) || t('audit.system')}</td>
                <td data-label={t('audit.col.action')} className="mono">{row.action}</td>
                <td data-label={t('audit.col.outcome')}><OutcomePill outcome={row.outcome} /></td>
                <td data-label={t('audit.col.session')} className="mono">{row.sessionId ? `#${row.sessionId}` : '—'}</td>
                <td data-label={t('audit.col.correlation')}><Ref value={row.correlationId} /></td>
                <td className="audit-row-action">
                  <button
                    type="button"
                    className="button outline small"
                    aria-expanded={open}
                    onClick={() => toggle(row.id)}
                  >
                    {open ? t('audit.collapse') : t('audit.col.details')}
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
                    <p className="audit-note">{t('audit.metaNote')}</p>
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
  const t = useT(messages);
  return (
    <>
      <table className="audit-table">
        <thead>
          <tr>
            <th scope="col">{t('audit.col.time')}</th><th scope="col">{t('audit.col.actor')}</th><th scope="col">{t('audit.col.actionCode')}</th>
            <th scope="col">{t('audit.col.aggregate')}</th><th scope="col">{t('audit.col.evidence')}</th><th scope="col">{t('audit.col.reason')}</th><th scope="col">{t('audit.col.correlation')}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(row => (
            <tr key={row.id}>
              <td data-label={t('audit.col.time')}>{formatTime(row.occurredAt)}</td>
              <td data-label={t('audit.col.actor')}>{actorLabel(row.actor) || row.actorReference || '—'}</td>
              <td data-label={t('audit.col.actionCode')} className="mono">{row.actionCode}</td>
              <td data-label={t('audit.col.aggregate')} className="mono">{row.aggregateType}<br />{row.aggregateReference}</td>
              <td data-label={t('audit.col.evidence')} className="mono">{row.evidenceReference || '—'}</td>
              <td data-label={t('audit.col.reason')} className="audit-reason">{row.reasonText || '—'}</td>
              <td data-label={t('audit.col.correlation')}><Ref value={row.correlationReference} /></td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="audit-note">{t('audit.reasonNote')}</p>
    </>
  );
}
