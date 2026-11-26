import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { getOwnedBookingGroup, lookupGuestBookingGroup, WorkshopApiError } from '../api/workshops';
import { useLanguage, useT } from '../shared/i18n/I18nProvider';
import { messages } from './messages';
import { GroupDetailCard } from './GroupDetailCard';
import { awaitingResultMessage, countdownText, groupHoldPhase } from './groupDisplay';
import { useBookingIdentity } from './useBookingIdentity';
import { buildContactProof, DEFAULT_PHONE_COUNTRY } from './contactValidation';
import { PhoneInput } from './PhoneInput';
import './figmaPublicShell.css';

const GROUP_POLL_MS = 20000;

/**
 * What the browser return from the gateway means (003.1 FR-038): nothing. A group is confirmed only on verified
 * evidence the backend receives, so this page classifies the return for display and then reads the group itself.
 */
export function classifyGroupPaymentOutcome({ responseCode, groupStatus, error, noLiveAttempt } = {}) {
  if (groupStatus === 'EXPIRED' || error?.code === 'HOLD_EXPIRED' || error?.code === 'BOOKING_EXPIRED') {
    return {
      kind: 'EXPIRED',
      title: { key: 'return.expired.title' },
      message: { key: 'return.expired.message' },
    };
  }
  if (error && (error.code === 'NETWORK_ERROR' || error.code === 'NETWORK_TIMEOUT' || error.code === 'REQUEST_TIMEOUT'
    || Number(error.status || 0) === 0 || error.name === 'AbortError')) {
    return {
      kind: 'NETWORK_TIMEOUT',
      title: { key: 'return.network.title' },
      message: { key: 'return.network.message' },
    };
  }
  if (error) {
    return {
      kind: 'LOOKUP_ERROR',
      title: { key: 'return.lookupError.title' },
      message: error.detail || error.message ? { text: error.detail || error.message } : { key: 'return.lookupError.message' },
    };
  }
  // FR-032 (T172): the browser came back from the gateway without success and the server reports no live attempt, so the
  // attempt ended (cancelled or failed). Proposed copy; the server alone decided it (not the return code).
  if (responseCode && responseCode !== '00' && noLiveAttempt) {
    return {
      kind: 'PAYMENT_NOT_COMPLETED',
      title: { key: 'return.notCompleted.title' },
      message: { key: 'return.notCompleted.message' },
    };
  }
  if (responseCode === '00') {
    return {
      kind: 'PROVIDER_SUCCESS_PENDING_VERIFICATION',
      title: { key: 'return.pending.title' },
      message: { key: 'return.pending.message' },
    };
  }
  if (responseCode) {
    return {
      kind: 'PROVIDER_ERROR',
      title: { key: 'return.providerError.title', params: { code: responseCode } },
      message: { key: 'return.providerError.message' },
    };
  }
  return {
    kind: 'NO_PROVIDER_RESPONSE',
    title: { key: 'return.noResponse.title' },
    message: { key: 'return.noResponse.message' },
  };
}

/**
 * The payment result of a booking group (003.1 FR-032, FR-038). While the group is still held, the customer sees only
 * the 10-minute countdown; when it ends the page shows "Đang xác nhận kết quả thanh toán…" until the internal hold
 * ends and then the outcome the server reports. The 12-minute hold and the 2-minute buffer are never shown.
 */
export function WorkshopPaymentReturnPage({ groupCode }) {
  const t = useT(messages);
  const { language } = useLanguage();
  // An outcome/error text is { key, params } (translated here, so it follows the language) or { text } (server text).
  const say = value => (value.key ? t(value.key, value.params) : value.text);
  const { authStatus, mode: lookupMode, profileLoading, operatorRole, memberAuthorization } = useBookingIdentity();
  const params = new URLSearchParams(window.location.search);
  const vnpResponseCode = params.get('vnp_ResponseCode');
  // "Tiếp tục thanh toán" from the header notice (FR-058): no gateway answer exists yet, so no outcome is shown.
  const resuming = params.get('resume') === '1' && !vnpResponseCode;

  const [contactProof, setContactProof] = useState('');
  const [phoneCountry, setPhoneCountry] = useState(DEFAULT_PHONE_COUNTRY);
  const [proofType, setProofType] = useState('phone');
  const [isVerifying, setIsVerifying] = useState(false);
  const [group, setGroup] = useState(null);
  const [verifyError, setVerifyError] = useState(null);
  const [phase, setPhase] = useState(() => groupHoldPhase(null));
  const [authorization, setAuthorization] = useState(null);

  useEffect(() => {
    if (!group || group.groupStatus !== 'PENDING_PAYMENT') { setPhase(groupHoldPhase(null)); return undefined; }
    const tick = () => setPhase(groupHoldPhase(group));
    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, [group?.groupStatus, group?.paymentDeadlineAt, group?.holdExpiresAt]);

  const readGroup = async event => {
    if (event) event.preventDefault();
    if (!groupCode) return;
    if (lookupMode === null) {
      setVerifyError({ title: { key: 'return.verify.noAccount.title' }, message: { key: 'return.verify.noAccount.message' } });
      return;
    }
    if (lookupMode === 'GUEST' && !contactProof.trim()) {
      setVerifyError({ title: { key: 'return.verify.needProof.title' }, message: { key: 'return.verify.needProof.message' } });
      return;
    }
    if (lookupMode === 'GUEST' && !guestProof.proof) {
      setVerifyError({ title: { key: 'return.verify.badProof.title' }, message: { text: guestProof.error } });
      return;
    }
    setIsVerifying(true);
    setVerifyError(null);
    try {
      let result;
      if (lookupMode === 'MEMBER') {
        const bearer = await memberAuthorization();
        if (!bearer) throw new Error('MEMBER_SESSION_EXPIRED');
        setAuthorization(bearer);
        result = await getOwnedBookingGroup(groupCode, { authorization: bearer });
      } else {
        result = await lookupGuestBookingGroup({ groupCode, ...guestProof.proof });
      }
      setGroup(result);
    } catch (err) {
      setVerifyError(classifyGroupPaymentOutcome({ error: err instanceof WorkshopApiError ? err : err }));
    } finally {
      setIsVerifying(false);
    }
  };

  // A signed-in Member needs no proof: show the group they just paid for without a click.
  useEffect(() => {
    if (lookupMode === 'MEMBER' && groupCode && !group && !isVerifying && !verifyError) readGroup();
  }, [lookupMode, groupCode]);

  // The gateway said yes but the verified evidence (IPN) can take a few seconds: re-read quietly a few times.
  const [rechecks, setRechecks] = useState(0);
  useEffect(() => {
    if (!group || group.groupStatus !== 'PENDING_PAYMENT' || !vnpResponseCode || rechecks >= 15
      || (vnpResponseCode !== '00' && group.hasLivePaymentAttempt !== true)) return undefined;
    const timer = setTimeout(() => { setRechecks(count => count + 1); readGroup(); }, 4000);
    return () => clearTimeout(timer);
  }, [group, rechecks, vnpResponseCode]);

  // Staff/Manager decisions (e.g. a schedule-change request) happen elsewhere, so the open group is read again every
  // GROUP_POLL_MS while the tab is visible, and right away when the tab becomes visible again.
  const readGroupRef = useRef(readGroup);
  readGroupRef.current = readGroup;
  const polling = useRef(false);
  const hasGroup = Boolean(group);
  useEffect(() => {
    if (!hasGroup) return undefined;
    const poll = async () => {
      if (document.visibilityState === 'hidden' || polling.current) return;
      polling.current = true;
      try { await readGroupRef.current(); } finally { polling.current = false; }
    };
    const timer = setInterval(poll, GROUP_POLL_MS);
    document.addEventListener('visibilitychange', poll);
    return () => { clearInterval(timer); document.removeEventListener('visibilitychange', poll); };
  }, [hasGroup]);

  // The one proof the Guest typed, in the normalized form every call sends: { proof, error }.
  const guestProof = buildContactProof(proofType === 'email'
    ? { email: contactProof }
    : { phone: contactProof, phoneCountry }, language);
  const confirmed = group?.groupStatus === 'CONFIRMED';
  const returnOutcome = confirmed
    ? {
      kind: 'CONFIRMED',
      title: { key: 'return.confirmed.title' },
      message: { key: 'return.confirmed.message' },
    }
    : classifyGroupPaymentOutcome({
      responseCode: vnpResponseCode,
      groupStatus: group?.groupStatus,
      noLiveAttempt: group?.groupStatus === 'PENDING_PAYMENT' && group?.hasLivePaymentAttempt === false,
    });

  return (
    <main className="public-page figma-public-page">
      <p className="eyebrow">{t('return.eyebrow')}</p>
      <h1>{resuming ? t('return.title.resume') : t('return.title.result')}</h1>

      {!resuming && <div className="workspace-card" style={{ padding: '24px', borderLeft: '4px solid #002967', marginTop: '20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
          {!confirmed && (
            <span className={vnpResponseCode === '00' ? 'status ok' : 'status'}>
              {t('return.responseCode')} {vnpResponseCode || 'N/A'}
            </span>
          )}
          <strong>{say(returnOutcome.title)}</strong>
        </div>
        <p className="prototype-note" style={{ margin: '14px 0 0', lineHeight: '1.6' }}>
          <strong>{say(returnOutcome.message)}</strong>
          {!confirmed && returnOutcome.kind !== 'PAYMENT_NOT_COMPLETED' && (
            <> {t('return.signedNote')}</>
          )}
        </p>
      </div>}

      <section className="workspace-card" style={{ marginTop: '24px' }}>
        <h2>{group ? (resuming ? t('return.section.pending') : t('return.section.tickets')) : t('return.section.check')}</h2>
        <p className="muted" style={{ margin: '0 0 16px' }}>
          {t('return.groupCode')} <strong>{groupCode || t('return.unknown')}</strong>
        </p>

        {!group && operatorRole ? (
          <div role="status" className="operator-payment-note" style={{ background: '#f8fafc', border: '1px solid #dcdfe6', padding: '16px 18px', lineHeight: '1.6' }}>
            <strong>{t('return.operator.title', { role: operatorRole })}</strong>
            <p style={{ margin: '8px 0 0' }}>
              {t('return.operator.note')}
            </p>
            <p style={{ margin: '8px 0 12px' }}>
              {t('return.operator.step1')} <strong>{t('return.operator.invoice')}</strong>{t('return.operator.step2')} <strong>{t('return.operator.loadFolio')}</strong>{t('return.operator.step3')}
            </p>
            {operatorRole !== 'ADMIN' && <Link className="button primary" to="/settlement">{t('return.operator.back')}</Link>}
          </div>
        ) : !group ? (
          <form className="payment-form" onSubmit={readGroup}>
            <p hidden={lookupMode !== 'GUEST'} style={{ fontSize: '14px', lineHeight: '1.6', marginBottom: '14px' }}>
              {t('return.guestPrompt')}
            </p>
            <p className="muted" hidden={lookupMode !== 'MEMBER'}>
              {t('return.memberNote')}
            </p>

            <div hidden={lookupMode !== 'GUEST'} style={{ display: 'flex', gap: '12px', marginBottom: '12px' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '14px' }}>
                <input type="radio" name="proofType" checked={proofType === 'phone'} onChange={() => setProofType('phone')} />
                {t('return.phone')}
              </label>
              <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '14px' }}>
                <input type="radio" name="proofType" checked={proofType === 'email'} onChange={() => setProofType('email')} />
                Email
              </label>
            </div>

            <div hidden={lookupMode !== 'GUEST'} style={{ display: 'flex', gap: '10px' }}>
              {proofType === 'email' ? (
                <input
                  type="email"
                  placeholder="email@example.com"
                  value={contactProof}
                  onChange={event => setContactProof(event.target.value)}
                  style={{ flex: 1, padding: '10px 14px', border: '1px solid #ccc' }}
                  disabled={isVerifying}
                  required={lookupMode === 'GUEST'}
                />
              ) : (
                <div style={{ flex: 1 }}>
                  <PhoneInput
                    country={phoneCountry}
                    value={contactProof}
                    onCountryChange={setPhoneCountry}
                    onValueChange={setContactProof}
                    placeholder="0901234567"
                    disabled={isVerifying}
                    required={lookupMode === 'GUEST'}
                  />
                </div>
              )}
              <button type="submit" className="button primary" disabled={isVerifying}>
                {isVerifying ? t('return.checking') : t('return.viewStatus')}
              </button>
            </div>

            {lookupMode === 'MEMBER' && (
              <button type="submit" className="button primary" disabled={isVerifying}>
                {isVerifying ? t('return.checking') : t('return.viewStatus')}
              </button>
            )}
            {profileLoading && <p className="muted">{t('return.profileLoading')}</p>}
            {lookupMode === null && !profileLoading && authStatus === 'signed-in' && (
              <p className="muted">{t('return.relogin')}</p>
            )}
            {verifyError && (
              <div role="alert" style={{ color: '#b00', fontSize: '13px', marginTop: '8px' }}>
                <strong>{say(verifyError.title)}</strong>
                <p style={{ margin: '4px 0 0' }}>{say(verifyError.message)}</p>
              </div>
            )}
          </form>
        ) : (
          <>
            {group.groupStatus === 'PENDING_PAYMENT' && phase.phase === 'PAYABLE' && (
              <p role="timer" style={{ margin: '0 0 12px' }}>
                {t('return.deadlineLeft')} <strong>{countdownText(phase.secondsLeft, language)}</strong>
              </p>
            )}
            {group.groupStatus === 'PENDING_PAYMENT' && phase.phase === 'AWAITING_RESULT' && (
              <p role="status" style={{ margin: '0 0 12px' }}>{awaitingResultMessage(language)}</p>
            )}

            <GroupDetailCard
              group={group}
              authorization={lookupMode === 'MEMBER' ? authorization : null}
              proof={lookupMode === 'GUEST' ? (guestProof.proof || undefined) : undefined}
              onRefresh={readGroup}
              allowManualRefresh={false}
              onTicketUpdated={readGroup}
            />
          </>
        )}
      </section>
    </main>
  );
}
