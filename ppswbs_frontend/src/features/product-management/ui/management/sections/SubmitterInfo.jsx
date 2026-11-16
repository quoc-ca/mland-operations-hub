import React from 'react';
import { useFormat, useT } from '../../../../../shared/i18n/I18nProvider';
import { messages } from '../../messages';

/**
 * FR-060/FR-061 (Session 2026-11-16): who submitted a price proposal or a product review, the submitter's branches
 * at submit time, and who decided it. The backend sends ready labels (full name, "Tài khoản #id" or "Hệ thống");
 * nothing is shown for a revision that was never submitted. Shown as label / value lines so each fact reads on its
 * own row.
 */
export function branchesLabel(t, submitterBranches) {
  if (!submitterBranches) return null;
  switch (submitterBranches.kind) {
    case 'ALL': return t('people.branchesAll');
    case 'SYSTEM': return t('people.system');
    case 'NOT_RECORDED': return t('people.branchesNotRecorded');
    default: {
      const names = (submitterBranches.branches || [])
        .map(branch => branch.name || t('people.branchId', { id: branch.locationId }));
      return names.length ? names.join(', ') : t('people.branchesNone');
    }
  }
}

export function SubmitterInfo({ item }) {
  const t = useT(messages);
  const format = useFormat();
  if (!item?.submittedBy && !item?.decidedBy) return null;
  const branches = branchesLabel(t, item.submitterBranches);
  const person = (actor, at) => (
    <>
      <span className="who">{actor.displayName}</span>
      {at && <span className="when"> · {format.dateTime(at)}</span>}
    </>
  );
  return (
    <dl className="submitter-info">
      {item.submittedBy && (
        <div><dt>{t('people.submitter')}</dt><dd>{person(item.submittedBy, item.submittedAt)}</dd></div>
      )}
      {branches && <div><dt>{t('people.branchesLabel')}</dt><dd>{branches}</dd></div>}
      {item.decidedBy && (
        <div><dt>{t('people.decider')}</dt><dd>{person(item.decidedBy, item.decidedAt)}</dd></div>
      )}
    </dl>
  );
}
