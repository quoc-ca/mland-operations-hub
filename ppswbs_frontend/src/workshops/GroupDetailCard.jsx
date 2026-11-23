import React, { useEffect, useState } from 'react';
import {
  releaseGroupPendingHold,
  requestGroupConfirmationDelivery,
  WorkshopApiError,
} from '../api/workshops';
import { useLanguage, useT } from '../shared/i18n/I18nProvider';
import { messages } from './messages';
import { GroupHoldPanel } from './GroupHoldPanel';
import { GroupTicketList } from './GroupTicketList';
import {
  deferredNotice,
  formatSessionWindow,
  groupRemainingVnd,
  groupStatusLabel,
  isDeferredGroup,
  isFreeGroup,
  needsOnlinePayment,
  nonRefundableNotice,
  vnd,
} from './groupDisplay';

/**
 * One booking group with its ticket bookings (003.1 FR-042, FR-048, FR-049). The same card serves the Member list, the
 * Guest lookup, the group Magic Link and the screen right after confirmation, because all four read the same group
 * projection. The group holds the contact, the single payment and the voucher use; each ticket keeps its own status,
 * folio figures, recipient and attendance.
 */
function rebookHref(group) {
  const line = Array.isArray(group.lines) ? group.lines[0] : null;
  if (!line?.packageId) return '/workshops';
  const params = new URLSearchParams({ focus: 'schedule' });
  const count = Number(line.ticketCount ?? line.participantCount);
  if (count >= 1) params.set('participants', String(count));
  return `/workshops/${line.packageId}?${params.toString()}`;
}

export function GroupDetailCard({
  group,
  authorization,
  proof,
  onRefresh,
  onTicketUpdated,
  allowRelease = true,
  allowResend = true,
  allowManualRefresh = true,
  allowRecipientChange = true,
  renderTicketExtra,
  renderTicketAction,
}) {
  const t = useT(messages);
  const { language } = useLanguage();
  const [releaseOpen, setReleaseOpen] = useState(false);
  const [releaseReason, setReleaseReason] = useState('');
  const [releasing, setReleasing] = useState(false);
  const [releaseError, setReleaseError] = useState(null);
  const [resending, setResending] = useState(false);
  const [resendMessage, setResendMessage] = useState(null);
  const [resendError, setResendError] = useState(null);

  if (!group) return null;
  const pending = group.groupStatus === 'PENDING_PAYMENT';
  // Once the payment deadline has passed the server is only waiting for a late result: the hold panel reads the group by
  // itself then, and releasing or refreshing by hand is no longer offered (FR-032: the buffer is never shown).
  const [clock, setClock] = useState(() => Date.now());
  useEffect(() => {
    if (!pending || !group.paymentDeadlineAt) return undefined;
    const timer = setInterval(() => setClock(Date.now()), 250);
    return () => clearInterval(timer);
  }, [pending, group.paymentDeadlineAt]);
  const deadlinePassed = pending && Boolean(group.paymentDeadlineAt) && Date.parse(group.paymentDeadlineAt) <= clock;
  const confirmed = group.groupStatus === 'CONFIRMED';
  const remaining = groupRemainingVnd(group);

  const release = async () => {
    if (!releaseReason.trim()) {
      setReleaseError(t('group.release.reasonRequired'));
      return;
    }
    setReleasing(true);
    setReleaseError(null);
    try {
      await releaseGroupPendingHold(group.groupCode, {
        reason: releaseReason.trim(),
        contactEmail: !authorization ? proof?.contactEmail || undefined : undefined,
        contactPhone: !authorization ? proof?.contactPhone || undefined : undefined,
        authorization: authorization || undefined,
      });
      setReleaseOpen(false);
      setReleaseReason('');
      onRefresh?.();
    } catch (error) {
      if (error instanceof WorkshopApiError && error.code === 'PAYMENT_ATTEMPT_EXISTS') {
        setReleaseError(t('group.release.attemptExists'));
      } else {
        setReleaseError(error?.detail || error?.message || t('group.release.failed'));
      }
    } finally {
      setReleasing(false);
    }
  };

  const resend = async () => {
    setResending(true);
    setResendMessage(null);
    setResendError(null);
    try {
      await requestGroupConfirmationDelivery(group.groupCode, {
        contactEmail: !authorization ? proof?.contactEmail || undefined : undefined,
        contactPhone: !authorization ? proof?.contactPhone || undefined : undefined,
        authorization: authorization || undefined,
      });
      setResendMessage(t('group.resend.accepted'));
    } catch (error) {
      if (error instanceof WorkshopApiError && error.status === 429) {
        setResendError(t('group.resend.limit'));
      } else {
        setResendError(error?.detail || error?.message || t('group.resend.failed'));
      }
    } finally {
      setResending(false);
    }
  };

  return (
    <section className="workspace-card group-detail" style={{ marginTop: '24px', borderTop: '4px solid #002967' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #eee', paddingBottom: '16px', gap: '12px', flexWrap: 'wrap' }}>
        <div>
          <span className="muted" style={{ fontSize: '13px' }}>{t('group.code')}</span>
          <strong style={{ display: 'block', fontSize: '24px', color: '#002967', letterSpacing: '0.05em' }}>
            {group.groupCode}
          </strong>
        </div>
        <div style={{ textAlign: 'right' }}>
          <span className={`status ${confirmed ? 'ok' : ''}`} style={{ fontSize: '14px', fontWeight: 'bold' }}>
            {groupStatusLabel(group.groupStatus, language) || group.groupStatus}
          </span>
          <span className="muted" style={{ display: 'block', fontSize: '12px', marginTop: '4px' }}>
            {t('group.summary', { count: (group.tickets || []).length, amount: vnd(group.groupCollectionVnd, language) })}
          </span>
        </div>
      </div>

      {Array.isArray(group.lines) && group.lines.length > 0 && (
        <div style={{ marginTop: '16px' }}>
          {group.lines.map(line => (
            <p key={line.lineIndex} style={{ margin: '4px 0' }}>
              {line.packageName || t('group.package', { id: line.packageId })}: <strong>{t('group.ticketCount', { count: line.ticketCount ?? line.participantCount })}</strong>
              {' · '}{t('group.pricePerTicket', { amount: vnd(line.pricePerParticipantVnd, language) })}
            </p>
          ))}
        </div>
      )}

      {pending && needsOnlinePayment(group.paymentMode) && (
        <GroupHoldPanel group={group} authorization={authorization} proof={proof} onHoldEnded={onRefresh} />
      )}

      {isDeferredGroup(group.paymentMode) && (
        <p role="note" style={{ marginTop: '14px', padding: '12px 14px', background: '#f0f5ff', borderLeft: '4px solid #002967' }}>
          {deferredNotice(remaining, language)}
        </p>
      )}
      {isFreeGroup(group.paymentMode) && (
        <p role="note" style={{ marginTop: '14px', padding: '12px 14px', background: '#f0f9f4', borderLeft: '4px solid #2b8a3e' }}>
          {t('group.freeNotice')} {nonRefundableNotice(language)}
        </p>
      )}

      {group.groupStatus === 'EXPIRED' && (
        <div style={{ marginTop: '18px', padding: '16px', background: '#fff1f0', border: '1px solid #ffa39e' }}>
          <p style={{ margin: 0, color: '#cf1322' }}>
            {t('group.expired.main')}
          </p>
          <p style={{ margin: '8px 0 0', color: '#595959' }}>
            {t('group.expired.note')}
          </p>
          <div style={{ marginTop: '12px' }}>
            {/* Back to the same package at its session picker with the same ticket count, not the package list. */}
            <a className="button primary" href={rebookHref(group)}>{t('group.rebook')}</a>
          </div>
        </div>
      )}

      {group.groupStatus === 'CANCELLED' && (
        <p style={{ marginTop: '18px', padding: '16px', background: '#f5f5f5', border: '1px solid #d9d9d9', margin: '18px 0 0', color: '#595959' }}>
          {t('group.cancelled')}
        </p>
      )}

      <div style={{ marginTop: '20px' }}>
        <h3 style={{ margin: '0 0 10px', color: '#002967' }}>{t('group.tickets')}</h3>
        <GroupTicketList
          group={group}
          authorization={authorization}
          proof={proof}
          onTicketUpdated={onTicketUpdated}
          allowRecipientChange={allowRecipientChange && confirmed}
          allowCancel={allowRecipientChange && confirmed}
          allowChange={allowRecipientChange && confirmed}
          renderTicketAction={renderTicketAction}
        />
        {renderTicketExtra ? renderTicketExtra(group) : null}
      </div>

      <div className="actions" style={{ marginTop: '18px', display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
        {allowResend && confirmed && (
          <button type="button" className="button outline" onClick={resend} disabled={resending}>
            {resending ? t('group.resend.sending') : t('group.resend.button')}
          </button>
        )}
        {allowRelease && pending && !deadlinePassed && group.hasLivePaymentAttempt !== true && (
          <button type="button" className="button outline" style={{ color: '#b00', borderColor: '#b00' }} onClick={() => setReleaseOpen(true)}>
            {t('group.release.button')}
          </button>
        )}
        {allowRelease && pending && !deadlinePassed && group.hasLivePaymentAttempt === true && (
          <p role="note" className="muted" style={{ margin: 0 }}>
            {t('group.release.livePayment')}
          </p>
        )}
        {/* Expired and cancelled are final: refreshing can never change them. */}
        {allowManualRefresh && onRefresh && group.groupStatus !== 'EXPIRED' && group.groupStatus !== 'CANCELLED' && !deadlinePassed && (
          <button type="button" className="button outline" onClick={onRefresh}>{t('group.refresh')}</button>
        )}
      </div>

      {resendMessage && <p role="status" style={{ color: '#0b5c34', marginTop: '8px' }}>{resendMessage}</p>}
      {resendError && <p role="alert" style={{ color: '#b00', marginTop: '8px' }}>{resendError}</p>}

      {releaseOpen && (
        <div style={{ marginTop: '16px', padding: '16px', background: '#fff', border: '2px solid #b00' }}>
          <h4 style={{ margin: '0 0 8px', color: '#b00' }}>{t('group.release.title')}</h4>
          <p style={{ fontSize: '13px', margin: '0 0 10px' }}>
            {t('group.release.warning')}
          </p>
          <label style={{ display: 'block', marginBottom: '10px' }}>
            {t('group.release.reasonLabel')}
            <input
              type="text"
              value={releaseReason}
              onChange={event => setReleaseReason(event.target.value)}
              style={{ width: '100%', marginTop: '4px' }}
              disabled={releasing}
            />
          </label>
          <div style={{ display: 'flex', gap: '10px' }}>
            <button type="button" className="button primary" style={{ background: '#b00', borderColor: '#b00' }} onClick={release} disabled={releasing}>
              {releasing ? t('group.release.cancelling') : t('group.release.agree')}
            </button>
            <button type="button" className="button outline" onClick={() => setReleaseOpen(false)} disabled={releasing}>
              {t('group.close')}
            </button>
          </div>
          {releaseError && <p role="alert" style={{ color: '#b00', marginTop: '8px' }}>{releaseError}</p>}
        </div>
      )}
    </section>
  );
}

/** The session schedule of a group, read from its tickets (every ticket of a line shares the line's sessions). */
export function groupSessionLabels(group, language = 'vi') {
  const seen = new Map();
  (group?.tickets || []).forEach(ticket => {
    (ticket.sessions || []).forEach(session => {
      if (!seen.has(session.sessionReference)) {
        seen.set(session.sessionReference, formatSessionWindow(session.scheduledStart, session.scheduledEnd, language));
      }
    });
  });
  return [...seen.values()];
}
