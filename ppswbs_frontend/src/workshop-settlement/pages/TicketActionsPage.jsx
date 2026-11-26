import React, { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useHttp } from '../../shared/api/useHttp';
import { ConfirmDialog, Field, SuccessNotice, useCommand } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { OpsPage } from '../OpsPage';
import { useRole } from '../../shared/auth/useRole';
import { useFormat, useLanguage, useT } from '../../shared/i18n/I18nProvider';
import { formatSessionWindow } from '../../workshops/groupDisplay';
import { messages } from '../messages';
import { BuyerProof, ProblemNotice, ReasonDialog, VoucherNotRestoredNotice, splitList } from '../ui';
import { TicketSessionPicker } from '../../shared/ui/TicketSessionPicker';

/**
 * Staff or Manager cancels or changes tickets for the buyer (FR-041–FR-045, FR-055): one ticket on the ticket route, several
 * tickets of one group all or none on the group route. The buyer proof comes from the code sent to the booking's contact.
 * Prices, outcomes and queue status are the server's answer; a price increase is shown first and repeated with confirmation.
 */
export default function TicketActionsPage() {
  const t = useT(messages);
  const fmt = useFormat();
  const { language } = useLanguage();
  const pickerLabels = {
    loading: t('picker.loading'),
    loadFailed: t('picker.loadFailed'),
    retry: t('picker.retry'),
    sessionToChange: t('picker.sessionToChange'),
    bothSessions: t('picker.bothSessions'),
    sessionLabel: ordinal => t('picker.sessionLabel', { ordinal }),
    notChangeable: t('picker.notChangeable'),
    noOptions: t('picker.noOptions'),
    noPairs: t('picker.noPairs'),
    seatsLeft: count => t(count === 1 ? 'picker.seatsLeftOne' : 'picker.seatsLeft', { count }),
    quotaLeft: count => t(count === 1 ? 'picker.quotaLeftOne' : 'picker.quotaLeft', { count }),
    full: t('picker.full'),
    needsManager: t('picker.needsManager'),
    priceIncrease: amount => t('picker.priceIncrease', { amount }),
    allowanceUsed: t('picker.allowanceUsed'),
    windowClosed: t('picker.windowClosed'),
    rule: {
      CHANGE_ALLOWANCE_EXHAUSTED: t('picker.rule.CHANGE_ALLOWANCE_EXHAUSTED'),
      ADVANCE_CHANGE_WINDOW_EXPIRED: t('picker.rule.ADVANCE_CHANGE_WINDOW_EXPIRED'),
      RESCHEDULE_QUOTA_EXHAUSTED: t('picker.rule.RESCHEDULE_QUOTA_EXHAUSTED'),
    },
    choose: t('picker.choose'),
    pickPlaceholder: t('picker.pickPlaceholder'),
    locationFallback: reference => t('picker.locationFallback', { reference }),
  };
  const api = createSettlementApi(useHttp());
  const [params] = useSearchParams();
  const tab = params.get('tab') === 'change' ? 'change' : 'cancel';
  const [groupCode, setGroupCode] = useState('');
  const [ticketText, setTicketText] = useState('');
  const [changes, setChanges] = useState([]);
  const [builderCode, setBuilderCode] = useState('');
  const [loadedCode, setLoadedCode] = useState('');
  const [selection, setSelection] = useState(null);
  const [changeType, setChangeType] = useState('ADVANCE');
  const [reason, setReason] = useState('');
  const [proof, setProof] = useState(null);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const role = useRole();
  const isManager = role === 'MANAGER';
  const [allowanceTicket, setAllowanceTicket] = useState('');
  const [granting, setGranting] = useState(false);
  const [result, setResult] = useState(null);
  const [message, setMessage] = useState('');
  const command = useCommand();

  const tickets = splitList(ticketText);
  const proofBody = proof ? { buyerVerificationReference: proof } : {};
  const needsGroup = tab === 'cancel' ? tickets.length > 1 : changes.length > 1;
  const groupReady = Boolean(groupCode.trim());

  const cancel = async () => {
    setMessage(''); setResult(null);
    const body = { reason: reason.trim(), ...proofBody };
    const out = await command.run(key => (tickets.length === 1
      ? api.cancelTicket(tickets[0], body, key)
      : api.cancelGroupTickets(groupCode.trim(), { ...body, ticketCodes: tickets }, key)));
    if (out.ok) {
      setConfirmCancel(false); setProof(null);
      setResult({ kind: 'cancel', tickets: out.result.tickets || [out.result] });
      setMessage(t('ta.msg.cancelled'));
    }
  };

  const change = async confirmIncrease => {
    setMessage(''); setResult(null);
    const base = { type: changeType, reason: reason.trim(), ...proofBody, ...(confirmIncrease ? { confirmIncrease: true } : {}) };
    const items = changes.map(({ ticketCode, fromSessionReferences, targetSessionReferences }) => ({ ticketCode, fromSessionReferences, targetSessionReferences }));
    const out = await command.run(key => (changes.length === 1
      ? api.changeTicket(items[0].ticketCode, { ...base, fromSessionReferences: items[0].fromSessionReferences, targetSessionReferences: items[0].targetSessionReferences }, key)
      : api.changeGroupTickets(groupCode.trim(), { ...base, changes: items }, key)));
    if (!out.ok) return;
    const data = out.result;
    setResult({ kind: 'change', data, grouped: changes.length > 1 });
    if (data.applied) { setProof(null); setMessage(t('ta.msg.applied')); }
    else if (data.changeRequestReference) { setProof(null); setMessage(t('ta.msg.queued')); }
  };

  const grant = async grantReason => {
    setMessage(''); setResult(null);
    const out = await command.run(key => api.grantChangeAllowance(allowanceTicket.trim(), { reason: grantReason }, key));
    if (out.ok) {
      setGranting(false); setAllowanceTicket('');
      setMessage(t('ta.msg.granted', { code: out.result.ticketCode, ref: out.result.allowanceReference }));
    }
  };

  const awaiting =result?.kind === 'change' && !result.data.applied && result.data.confirmIncreaseRequired;
  const increase = result?.grouped ? result.data.totalPriceIncrease : result?.data?.priceIncreaseAmount;
  const cancelReady = tickets.length > 0 && reason.trim() && (tickets.length === 1 || groupReady);
  const changeReady = changes.length > 0 && reason.trim() && (changes.length === 1 || groupReady);

  const addToChange = () => {
    if (!selection || !loadedCode) return;
    setChanges(list => [...list.filter(item => item.ticketCode !== loadedCode), {
      ticketCode: loadedCode,
      fromSessionReferences: selection.fromSessionReferences,
      targetSessionReferences: selection.targetSessionReferences,
      requiresManager: selection.requiresManager,
      targetLocationNames: selection.targetLocationNames || [],
    }]);
    setLoadedCode(''); setBuilderCode(''); setSelection(null);
  };
  const changeTypeTo = value => { setChangeType(value); setChanges([]); setLoadedCode(''); setSelection(null); };

  return (
    <OpsPage title={t(tab === 'change' ? 'ta.titleChange' : 'ta.titleCancel')} intro={t('ta.intro')}>
      <nav className="wsp-nav-tabs" aria-label={t('ta.tabsAria')}>
        <Link className={`wsp-nav-tab ${tab === 'cancel' ? 'active' : ''}`} to="/settlement/ticket-actions?tab=cancel">{t('ta.tabCancel')}</Link>
        <Link className={`wsp-nav-tab ${tab === 'change' ? 'active' : ''}`} to="/settlement/ticket-actions?tab=change">{t('ta.tabChange')}</Link>
      </nav>
      <SuccessNotice>{message}</SuccessNotice>
      <ProblemNotice error={command.error} onRetry={command.retry} />
      <section className="wsp-panel">
        <div className="wsp-form">
          <Field label={t('ta.groupCode')} hint={t(needsGroup ? 'ta.groupHintMulti' : 'ta.groupHintOne')}>
            <input value={groupCode} onChange={event => setGroupCode(event.target.value)} />
          </Field>
          {tab === 'cancel' ? (
            <Field label={t('ta.ticketCodes')} hint={t('ta.ticketCodesHint')}>
              <textarea value={ticketText} onChange={event => setTicketText(event.target.value)} placeholder="GRP-…-L0-T1, GRP-…-L0-T2" />
            </Field>
          ) : (<>
            <Field label={t('ta.changeType')}>
              <select value={changeType} onChange={event => changeTypeTo(event.target.value)}>
                <option value="ADVANCE">{t('ta.typeAdvance')}</option>
                <option value="LATE_ARRIVAL">{t('ta.typeLate')}</option>
              </select>
            </Field>
            <Field label={t('ta.ticketToChange')} hint={t('ta.ticketToChangeHint')}>
              <input value={builderCode} onChange={event => setBuilderCode(event.target.value)} placeholder="GRP-…-L0-T1" />
            </Field>
            <button className="button outline" type="button" disabled={!builderCode.trim()} onClick={() => { setSelection(null); setLoadedCode(builderCode.trim()); }}>{t('ta.showSessions')}</button>
            {loadedCode && (
              <section aria-label={t('ta.sessionOptions')}>
                <h3>{loadedCode}</h3>
                <TicketSessionPicker
                  ticketCode={loadedCode}
                  type={changeType}
                  loadOptions={from => api.getChangeOptions(loadedCode, { type: changeType, from })}
                  onSelect={setSelection}
                  labels={pickerLabels}
                  formatWindow={(start, end) => formatSessionWindow(start, end, language)}
                  formatMoney={value => fmt.money(value)}
                />
                {selection?.requiresManager && <p className="muted small" role="note">{t('ta.needsManager')}</p>}
                <button className="button primary" type="button" disabled={!selection} onClick={addToChange}>{t('ta.addToChange')}</button>
              </section>
            )}
            {changes.length > 0 && (
              <ul aria-label={t('ta.ticketsToChange')}>
                {changes.map(item => (
                  <li key={item.ticketCode}>
                    {t('ta.moved', { code: item.ticketCode, sessions: t(item.fromSessionReferences.length === 2 ? 'ta.bothSessions' : 'ta.oneSession'), to: item.targetLocationNames?.length ? t('ta.movedTo', { places: [...new Set(item.targetLocationNames)].join(', ') }) : '', manager: item.requiresManager ? t('ta.needsManagerShort') : '' })}
                    {' '}<button className="button outline small" type="button" onClick={() => setChanges(list => list.filter(entry => entry.ticketCode !== item.ticketCode))}>{t('ta.remove')}</button>
                  </li>
                ))}
              </ul>
            )}
          </>)}
          <Field label={t('ta.reason')}><textarea value={reason} maxLength={1000} onChange={event => setReason(event.target.value)} /></Field>
          <BuyerProof groupCode={groupCode.trim()} onReference={setProof} staffRequest={code => api.requestBuyerVerification(code)} />
          {tab === 'cancel'
            ? <button className="button danger" type="button" disabled={command.pending || !cancelReady || !proof} onClick={() => setConfirmCancel(true)}>{t('ta.cancelSelected')}</button>
            : <button className="button primary" type="button" disabled={command.pending || !changeReady || !proof} onClick={() => change(false)}>{t('ta.changeTickets')}</button>}
        </div>
      </section>

      {tab === 'change' && isManager && (
        <section className="wsp-panel" aria-label={t('ta.allowanceAria')}>
          <h2>{t('ta.allowanceTitle')}</h2>
          <p className="muted small">{t('ta.allowanceHint')}</p>
          <div className="wsp-inline-form">
            <label>{t('ta.allowanceTicket')}<input value={allowanceTicket} onChange={event => setAllowanceTicket(event.target.value)} placeholder="GRP-…-L0-T1" /></label>
            <button className="button outline" type="button" disabled={command.pending || !allowanceTicket.trim()} onClick={() => setGranting(true)}>{t('ta.allowanceGrant')}</button>
          </div>
        </section>
      )}
      {awaiting && (
        <section className="wsp-panel" role="status" aria-label={t('ta.increaseAria')}>
          <h2>{t('ta.increaseTitle')}</h2>
          <p>{t('ta.increasePre')}<strong>{fmt.money(increase)}</strong>{t('ta.increasePost')}</p>
          <button className="button primary" type="button" disabled={command.pending} onClick={() => change(true)}>{t('ta.increaseConfirm')}</button>
        </section>
      )}
      {result?.kind === 'change' && result.data.changeRequestReference && (
        <section className="wsp-panel" role="status"><p>{t('ta.queuedPre')}<strong>{result.data.changeRequestReference}</strong>{t('ta.queuedMid')}<Link to="/settlement/change-requests">{t('ta.queuedLink')}</Link>.</p></section>
      )}
      {result?.kind === 'change' && result.data.applied && (
        <section className="wsp-panel" aria-label={t('ta.changeResultAria')}>
          <h2>{t('ta.changeApplied')}</h2>
          <ul>
            {(result.grouped ? result.data.tickets : [result.data]).map(item => (
              <li key={item.ticketCode}>{t('ta.changeLine', { code: item.ticketCode, balance: fmt.money(item.balanceDue), increase: item.priceIncreaseAmount && item.priceIncreaseAmount !== '0' ? t('ta.changeIncrease', { amount: fmt.money(item.priceIncreaseAmount) }) : '' })}</li>
            ))}
          </ul>
        </section>
      )}
      {result?.kind === 'cancel' && (
        <section className="wsp-panel" aria-label={t('ta.cancelResultAria')}>
          <h2>{t('ta.cancelled')}</h2>
          <ul>
            {result.tickets.map(item => (
              <li key={item.ticketCode}>
                {t('ta.cancelLine', { code: item.ticketCode, outcome: t(item.benefitOutcome === 'VALID_CANCELLATION' ? 'ta.cancelValid' : 'ta.cancelLate'), points: item.pointsReturned, voucher: (item.voucherRestoration?.restored ?? item.voucherRestored) ? t('ta.voucherRestored') : '' })}
                <VoucherNotRestoredNotice voucherRestoration={item.voucherRestoration} />
              </li>
            ))}
          </ul>
        </section>
      )}

      <ReasonDialog open={granting} title={t('ta.grantTitle')} confirmLabel={t('ta.grantConfirm')} pending={command.pending} onCancel={() => setGranting(false)} onConfirm={grant}>
        <p>{t('ta.grantBody', { code: allowanceTicket.trim() })}</p>
      </ReasonDialog>
      <ConfirmDialog open={confirmCancel} title={t('ta.cancelDialogTitle')} danger confirmLabel={t('ta.cancelDialogConfirm')} pending={command.pending} onCancel={() => setConfirmCancel(false)} onConfirm={cancel}>
        <p>{t('ta.cancelDialogBody', { count: tickets.length, allOrNone: tickets.length > 1 ? t('ta.allOrNone') : '' })}</p>
      </ConfirmDialog>
    </OpsPage>
  );
}
