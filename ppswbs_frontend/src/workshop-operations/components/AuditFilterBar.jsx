import { useId, useState } from 'react';
import { useEnumLabel, useT } from '../../shared/i18n/I18nProvider';
import { messages } from '../messages';
import { OUTCOMES, activeFilterCount, hasReversedRange } from './auditReviewModel';

const FIELDS = {
  operations: [
    { name: 'action', placeholder: 'action' },
    { name: 'outcome', options: OUTCOMES },
    { name: 'actorId', placeholder: 'actorId', numeric: true },
    { name: 'sessionId', placeholder: 'sessionId', numeric: true },
    { name: 'from', datetime: true },
    { name: 'to', datetime: true },
  ],
  settlement: [
    { name: 'actionCode', placeholder: 'actionCode' },
    { name: 'actorId', numeric: true },
    { name: 'aggregateType', placeholder: 'aggregateType' },
    { name: 'aggregateReference' },
    { name: 'from', datetime: true },
    { name: 'to', datetime: true },
  ],
};

/**
 * Filter form of one tab (FR-049). It edits a draft; "Lọc" applies it (the page writes it to the URL), so the
 * filters survive a reload and a return from sign-in. A reversed time range is refused here and the inputs stay
 * filled. While a page is loading the button is disabled but the filters stay as typed.
 */
export default function AuditFilterBar({ tab, applied, disabled, onApply }) {
  const t = useT(messages);
  const outcomeLabel = useEnumLabel(messages, 'auditOutcome');
  const id = useId();
  const [draft, setDraft] = useState(applied);
  const [open, setOpen] = useState(false);
  const [rangeError, setRangeError] = useState(false);
  const fields = FIELDS[tab];
  const count = activeFilterCount(applied);

  const change = (name, value) => {
    setDraft(current => ({ ...current, [name]: value }));
    if (name === 'from' || name === 'to') setRangeError(false);
  };

  const submit = event => {
    event.preventDefault();
    if (hasReversedRange(draft)) {
      setRangeError(true);
      return;
    }
    setRangeError(false);
    onApply(draft);
  };

  return (
    <form className="audit-filter-bar" onSubmit={submit} aria-label={t('audit.filter.aria')}>
      <button
        type="button"
        className="button outline audit-filter-toggle"
        aria-expanded={open}
        aria-controls={`${id}-fields`}
        onClick={() => setOpen(current => !current)}
      >
        {t('audit.filter.toggle', { count })}
      </button>
      <div id={`${id}-fields`} className={`audit-filters ${open ? 'is-open' : ''}`}>
        {fields.map(field => {
          const inputId = `${id}-${field.name}`;
          const invalid = rangeError && (field.name === 'from' || field.name === 'to');
          return (
            <div key={field.name} className="audit-filter-field">
              <label htmlFor={inputId}>{t(`audit.field.${field.name}`)}</label>
              {field.options ? (
                <select id={inputId} value={draft[field.name]} onChange={event => change(field.name, event.target.value)}>
                  <option value="">{t('audit.all')}</option>
                  {field.options.map(option => <option key={option} value={option}>{outcomeLabel(option)}</option>)}
                </select>
              ) : (
                <input
                  id={inputId}
                  type={field.datetime ? 'datetime-local' : 'text'}
                  inputMode={field.numeric ? 'numeric' : undefined}
                  placeholder={field.placeholder ? t(`audit.ph.${field.placeholder}`) : undefined}
                  value={draft[field.name]}
                  aria-invalid={invalid || undefined}
                  onChange={event => change(field.name, event.target.value)}
                />
              )}
            </div>
          );
        })}
        <button type="submit" className="button primary audit-filter-submit" disabled={disabled}>{t('audit.apply')}</button>
      </div>
      {rangeError && <p className="audit-range-error" role="alert">{t('audit.rangeError')}</p>}
    </form>
  );
}
