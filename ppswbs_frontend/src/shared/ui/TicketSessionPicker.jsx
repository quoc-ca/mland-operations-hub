import React, { useEffect, useRef, useState } from 'react';

const BOTH = 'BOTH';

const EN = {
  loading: 'Loading the sessions this ticket can move to…',
  loadFailed: 'The sessions could not be loaded.',
  retry: 'Try again',
  sessionToChange: 'Session to change',
  bothSessions: 'Both sessions together',
  sessionLabel: ordinal => `Session ${ordinal}`,
  notChangeable: 'cannot be changed',
  noOptions: 'No other session can replace this one right now.',
  noPairs: 'No pair of sessions fits the package rules right now.',
  seatsLeft: count => `${count} seat${count === 1 ? '' : 's'} left`,
  quotaLeft: count => `${count} change${count === 1 ? '' : 's'} left`,
  full: 'Full',
  needsManager: 'Needs a Manager to approve',
  priceIncrease: amount => `Price increase ${amount}`,
  allowanceUsed: 'The one change of this ticket is already used.',
  windowClosed: 'The change window has closed for this session.',
  rule: {
    CHANGE_ALLOWANCE_EXHAUSTED: 'the ticket’s change is used',
    ADVANCE_CHANGE_WINDOW_EXPIRED: 'less than 24 hours before the session',
    RESCHEDULE_QUOTA_EXHAUSTED: 'the session’s reschedule quota is full',
  },
  choose: 'Choose the new session',
  pickPlaceholder: 'Select a session…',
  locationFallback: reference => `Location ${reference}`,
};

const defaultWindow = (start, end) => {
  const from = new Date(start);
  const to = new Date(end);
  if (Number.isNaN(from.getTime())) return '';
  const day = from.toLocaleDateString();
  const clock = value => value.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  return Number.isNaN(to.getTime()) ? `${day} ${clock(from)}` : `${day} ${clock(from)}–${clock(to)}`;
};

// FR-058/FR-063: every row names its location; a null name falls back to the reference, never to an empty label.
const locationText = (item, text) => item.locationName || (item.locationReference != null ? text.locationFallback(item.locationReference) : '');

const defaultMoney = value => `${Number(value || 0).toLocaleString()} VND`;

/**
 * Lists the sessions a ticket may move to (003.3 FR-044a) and lets the user pick one replacement per changed session, or
 * a pair when both sessions move. The list is advisory: the server re-checks everything when the change is confirmed.
 *
 * `loadOptions(fromSessionReferences)` returns the ChangeOptions of the contract (an empty array asks for every session
 * of the ticket, one by one; two references also ask for the valid pairs). `onSelect` receives
 * `{ fromSessionReferences, targetSessionReferences, requiresManager, managerRules, priceIncreaseAmountVnd }`, or null
 * when the choice is cleared.
 */
export function TicketSessionPicker({ ticketCode, type, loadOptions, onSelect, labels, formatWindow = defaultWindow, formatMoney = defaultMoney }) {
  const text = { ...EN, ...(labels || {}), rule: { ...EN.rule, ...(labels?.rule || {}) } };
  const [state, setState] = useState({ status: 'loading', data: null, error: null });
  const [scope, setScope] = useState(null);
  const [pairData, setPairData] = useState(null);
  const [picked, setPicked] = useState(null);
  const latest = useRef(0);
  const loader = useRef(loadOptions);
  loader.current = loadOptions;
  const notify = useRef(onSelect);
  notify.current = onSelect;

  const load = () => {
    const ticket = ++latest.current;
    setState({ status: 'loading', data: null, error: null });
    setScope(null);
    setPairData(null);
    setPicked(null);
    notify.current?.(null);
    Promise.resolve(loader.current([]))
      .then(data => { if (ticket === latest.current) setState({ status: 'ready', data, error: null }); })
      .catch(error => { if (ticket === latest.current) setState({ status: 'error', data: null, error }); });
  };

  // Reload whenever the ticket or the change type changes; a stale answer never replaces a newer one.
  useEffect(() => { load(); return () => { latest.current += 1; }; }, [ticketCode, type]); // eslint-disable-line react-hooks/exhaustive-deps

  const choose = (selection, option) => {
    setPicked(selection);
    notify.current?.(option);
  };

  const pickScope = async next => {
    setScope(next);
    setPicked(null);
    notify.current?.(null);
    if (next === BOTH) {
      const froms = state.data.perFromSession.map(item => item.fromSessionReference);
      setPairData(null);
      const ticket = ++latest.current;
      try {
        const data = await loader.current(froms);
        if (ticket === latest.current) setPairData(data);
      } catch (error) {
        if (ticket === latest.current) setState({ status: 'error', data: null, error });
      }
    }
  };

  if (state.status === 'loading') return <p className="muted" role="status">{text.loading}</p>;
  if (state.status === 'error') {
    return (
      <div role="alert" style={{ color: '#b00', fontSize: '13px' }}>
        <p style={{ margin: '0 0 6px' }}>{state.error?.detail || state.error?.message || text.loadFailed}</p>
        <button type="button" className="button outline small" onClick={load}>{text.retry}</button>
      </div>
    );
  }

  const data = state.data;
  const sessions = data.perFromSession || [];
  const both = sessions.length === 2 && sessions.every(item => item.sourceChangeable);
  const managerNote = rules => (rules || []).map(rule => text.rule[rule] || rule).join('; ');

  // <option> holds text only, so the label is built as a string here.
  const optionText = option => [
    [locationText(option, text), formatWindow(option.start, option.end)].filter(Boolean).join(' – '),
    option.selectable === false && option.remainingCapacity <= 0 ? text.full : text.seatsLeft(option.remainingCapacity),
    Number.isFinite(option.remainingRescheduleQuota) ? text.quotaLeft(option.remainingRescheduleQuota) : null,
    option.requiresManager ? `${text.needsManager}${option.managerRules?.length ? ` (${managerNote(option.managerRules)})` : ''}` : null,
    Number(option.priceIncreaseAmountVnd) > 0 ? text.priceIncrease(formatMoney(option.priceIncreaseAmountVnd)) : null,
  ].filter(Boolean).join(' · ');

  const selected = scope && scope !== BOTH ? sessions.find(item => item.fromSessionReference === scope) : null;

  return (
    <div className="ticket-session-picker">
      {data.allowance?.exhausted && <p role="note" className="muted" style={{ fontSize: '13px' }}>{text.allowanceUsed}</p>}
      {data.windowOpen === false && type === 'ADVANCE' && <p role="note" className="muted" style={{ fontSize: '13px' }}>{text.windowClosed}</p>}
      <fieldset style={{ border: 'none', padding: 0, margin: '8px 0' }}>
        <legend style={{ fontSize: '13px', fontWeight: 600 }}>{text.sessionToChange}</legend>
        {sessions.map((item, index) => (
          <label key={item.fromSessionReference} style={{ display: 'block', fontSize: '13px', margin: '4px 0' }}>
            <input
              type="radio"
              name={`from-${ticketCode}`}
              checked={scope === item.fromSessionReference}
              disabled={!item.sourceChangeable}
              onChange={() => pickScope(item.fromSessionReference)}
            />
            {' '}{text.sessionLabel(index + 1)}: {formatWindow(item.start, item.end)}
            {!item.sourceChangeable && <span className="muted"> ({text.notChangeable})</span>}
          </label>
        ))}
        {both && (
          <label style={{ display: 'block', fontSize: '13px', margin: '4px 0' }}>
            <input type="radio" name={`from-${ticketCode}`} checked={scope === BOTH} onChange={() => pickScope(BOTH)} />
            {' '}{text.bothSessions}
          </label>
        )}
      </fieldset>

      {selected && (
        <fieldset style={{ border: 'none', padding: 0, margin: '8px 0' }}>
          <legend style={{ fontSize: '13px', fontWeight: 600 }}>{text.choose}</legend>
          {selected.options.length === 0 && <p className="muted" style={{ fontSize: '13px' }}>{text.noOptions}</p>}
          {selected.options.length > 0 && (
            <select
              aria-label={text.choose}
              name={`to-${ticketCode}`}
              value={picked || ''}
              style={{ width: '100%', fontSize: '13px' }}
              onChange={event => {
                const option = selected.options.find(item => item.sessionReference === event.target.value);
                if (!option) return;
                choose(option.sessionReference, {
                  fromSessionReferences: [selected.fromSessionReference],
                  targetSessionReferences: [option.sessionReference],
                  targetLocationNames: [locationText(option, text)],
                  requiresManager: option.requiresManager,
                  managerRules: option.managerRules || [],
                  priceIncreaseAmountVnd: option.priceIncreaseAmountVnd,
                });
              }}
            >
              <option value="" disabled>{text.pickPlaceholder}</option>
              {selected.options.map(option => (
                <option key={option.sessionReference} value={option.sessionReference} disabled={!option.selectable}>
                  {optionText(option)}
                </option>
              ))}
            </select>
          )}
        </fieldset>
      )}

      {scope === BOTH && (
        <fieldset style={{ border: 'none', padding: 0, margin: '8px 0' }}>
          <legend style={{ fontSize: '13px', fontWeight: 600 }}>{text.choose}</legend>
          {!pairData && <p className="muted" role="status" style={{ fontSize: '13px' }}>{text.loading}</p>}
          {pairData && (pairData.pairs || []).length === 0 && <p className="muted" style={{ fontSize: '13px' }}>{text.noPairs}</p>}
          {pairData && (pairData.pairs || []).length > 0 && (
            <select
              aria-label={text.choose}
              name={`pair-${ticketCode}`}
              value={picked || ''}
              style={{ width: '100%', fontSize: '13px' }}
              onChange={event => {
                const pair = pairData.pairs.find(item => item.sessionReferences.join('>') === event.target.value);
                if (!pair) return;
                choose(event.target.value, {
                  fromSessionReferences: sessions.map(item => item.fromSessionReference),
                  targetSessionReferences: pair.sessionReferences,
                  targetLocationNames: (pair.sessions || []).map(item => locationText(item, text)),
                  requiresManager: pair.requiresManager,
                  managerRules: pair.managerRules || [],
                  priceIncreaseAmountVnd: pair.priceIncreaseAmountVnd,
                });
              }}
            >
              <option value="" disabled>{text.pickPlaceholder}</option>
              {pairData.pairs.map(pair => {
                const [firstWindow, secondWindow] = (pair.sessions || []).map(item => `${[locationText(item, text), formatWindow(item.start, item.end)].filter(Boolean).join(' – ')} (${[text.seatsLeft(item.remainingCapacity), Number.isFinite(item.remainingRescheduleQuota) ? text.quotaLeft(item.remainingRescheduleQuota) : null].filter(Boolean).join(', ')})`);
                const notes = [
                  !pair.selectable ? text.full : null,
                  pair.requiresManager ? text.needsManager : null,
                  Number(pair.priceIncreaseAmountVnd) > 0 ? text.priceIncrease(formatMoney(pair.priceIncreaseAmountVnd)) : null,
                ].filter(Boolean);
                return (
                  <option key={pair.sessionReferences.join('>')} value={pair.sessionReferences.join('>')} disabled={!pair.selectable}>
                    {firstWindow} → {secondWindow}{notes.length ? ` · ${notes.join(' · ')}` : ''}
                  </option>
                );
              })}
            </select>
          )}
        </fieldset>
      )}
    </div>
  );
}
