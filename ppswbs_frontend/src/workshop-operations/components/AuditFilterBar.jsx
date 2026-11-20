import { useId, useState } from 'react';
import { OUTCOMES, activeFilterCount, hasReversedRange } from './auditReviewModel';

const FIELDS = {
  operations: [
    { name: 'action', label: 'Hành động', placeholder: 'VD: SESSION_CREATED' },
    { name: 'outcome', label: 'Kết quả', options: OUTCOMES },
    { name: 'actorId', label: 'Người thực hiện (ID)', placeholder: 'ID tài khoản', numeric: true },
    { name: 'sessionId', label: 'Ca (ID)', placeholder: 'ID ca', numeric: true },
    { name: 'from', label: 'Từ', datetime: true },
    { name: 'to', label: 'Đến', datetime: true },
  ],
  settlement: [
    { name: 'actionCode', label: 'Mã hành động', placeholder: 'VD: REFUND_APPROVED' },
    { name: 'actorId', label: 'Người thực hiện (ID)', numeric: true },
    { name: 'aggregateType', label: 'Loại đối tượng', placeholder: 'VD: SETTLEMENT_REFUND_REQUEST' },
    { name: 'aggregateReference', label: 'Mã đối tượng' },
    { name: 'from', label: 'Từ', datetime: true },
    { name: 'to', label: 'Đến', datetime: true },
  ],
};

/**
 * Filter form of one tab (FR-049). It edits a draft; "Lọc" applies it (the page writes it to the URL), so the
 * filters survive a reload and a return from sign-in. A reversed time range is refused here and the inputs stay
 * filled. While a page is loading the button is disabled but the filters stay as typed.
 */
export default function AuditFilterBar({ tab, applied, disabled, onApply }) {
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
    <form className="audit-filter-bar" onSubmit={submit} aria-label="Bộ lọc nhật ký audit">
      <button
        type="button"
        className="button outline audit-filter-toggle"
        aria-expanded={open}
        aria-controls={`${id}-fields`}
        onClick={() => setOpen(current => !current)}
      >
        Bộ lọc ({count})
      </button>
      <div id={`${id}-fields`} className={`audit-filters ${open ? 'is-open' : ''}`}>
        {fields.map(field => {
          const inputId = `${id}-${field.name}`;
          const invalid = rangeError && (field.name === 'from' || field.name === 'to');
          return (
            <div key={field.name} className="audit-filter-field">
              <label htmlFor={inputId}>{field.label}</label>
              {field.options ? (
                <select id={inputId} value={draft[field.name]} onChange={event => change(field.name, event.target.value)}>
                  <option value="">Tất cả</option>
                  {field.options.map(option => <option key={option} value={option}>{option}</option>)}
                </select>
              ) : (
                <input
                  id={inputId}
                  type={field.datetime ? 'datetime-local' : 'text'}
                  inputMode={field.numeric ? 'numeric' : undefined}
                  placeholder={field.placeholder}
                  value={draft[field.name]}
                  aria-invalid={invalid || undefined}
                  onChange={event => change(field.name, event.target.value)}
                />
              )}
            </div>
          );
        })}
        <button type="submit" className="button primary audit-filter-submit" disabled={disabled}>Lọc</button>
      </div>
      {rangeError && <p className="audit-range-error" role="alert">“Từ” phải trước hoặc bằng “Đến”.</p>}
    </form>
  );
}
