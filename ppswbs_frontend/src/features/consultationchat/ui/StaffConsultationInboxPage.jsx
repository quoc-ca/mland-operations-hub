import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useSearchParams } from 'react-router';
import { useConsultationChatApi } from '../api/useConsultationChatApi';
import { consultationMessages } from '../messages';
import { useT, useLanguage } from '../../../shared/i18n/I18nProvider';
import { validateMessageText, countCodePoints, MAX_MESSAGE_LENGTH, POLLING_INTERVAL_MS } from '../domain/chatRules';
import { newCommandId } from '../../../shared/api/http';
import { useRole } from '../../../shared/auth/useRole';
import AttachedRingHoverRecap from './AttachedRingHoverRecap';
import '../consultation-chat.css';

export function StaffConsultationInboxPage({ currentUser = {} }) {
  const { language } = useLanguage();
  const lang = language === 'en' ? 'en' : 'vi';
  const tFn = useT(consultationMessages);
  const t = Object.assign((k, p) => tFn(k, p), consultationMessages[lang] || consultationMessages.vi);
  const api = useConsultationChatApi();
  const userRole = useRole();
  const isManager = (currentUser?.role || userRole) === 'MANAGER';

  const [conversations, setConversations] = useState([]);
  const [nextCursor, setNextCursor] = useState(null);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [selectedRef, setSelectedRef] = useState(null);
  const [activeConversation, setActiveConversation] = useState(null);
  // The filter lives in the URL, so the rail badge can open the inbox on exactly the set it counts.
  const [searchParams, setSearchParams] = useSearchParams();
  const statusParam = searchParams.get('status');
  let statusFilter = ['OPEN', 'CLOSED'].includes(statusParam) ? statusParam : '';
  if (searchParams.get('unclaimed') === 'true') statusFilter = 'UNCLAIMED';
  const setStatusFilter = (value) => {
    let next = value ? { status: value } : {};
    if (value === 'UNCLAIMED') next = { status: 'OPEN', unclaimed: 'true' };
    setSearchParams(next, { replace: true });
  };
  const [loadingList, setLoadingList] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [messageInput, setMessageInput] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState(null);
  const [actionSuccess, setActionSuccess] = useState(null);

  // Assign modal state
  const [isAssignModalOpen, setIsAssignModalOpen] = useState(false);
  const [selectedBranch, setSelectedBranch] = useState('HN-HOANKIEM');
  const [assigning, setAssigning] = useState(false);
  const [incomingToast, setIncomingToast] = useState(null);

  const messagesEndRef = useRef(null);
  const messagesContainerRef = useRef(null);
  const userJustSentRef = useRef(false);
  const pollTimerRef = useRef(null);
  const lastMsgRefMap = useRef({});
  const apiRef = useRef(api);
  apiRef.current = api;
  const selectedRefRef = useRef(selectedRef);
  selectedRefRef.current = selectedRef;
  const statusFilterRef = useRef(statusFilter);
  statusFilterRef.current = statusFilter;
  const nextCursorRef = useRef(nextCursor);
  nextCursorRef.current = nextCursor;

  // Synthesized notification chime via Web Audio API
  const playNotificationChime = useCallback(() => {
    try {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) return;
      const ctx = new AudioCtx();
      const now = ctx.currentTime;

      const osc1 = ctx.createOscillator();
      const gain1 = ctx.createGain();
      osc1.type = 'sine';
      osc1.frequency.setValueAtTime(784, now);
      gain1.gain.setValueAtTime(0.12, now);
      gain1.gain.exponentialRampToValueAtTime(0.001, now + 0.15);
      osc1.connect(gain1);
      gain1.connect(ctx.destination);
      osc1.start(now);
      osc1.stop(now + 0.15);

      const osc2 = ctx.createOscillator();
      const gain2 = ctx.createGain();
      osc2.type = 'sine';
      osc2.frequency.setValueAtTime(1046, now + 0.1);
      gain2.gain.setValueAtTime(0.15, now + 0.1);
      gain2.gain.exponentialRampToValueAtTime(0.001, now + 0.35);
      osc2.connect(gain2);
      gain2.connect(ctx.destination);
      osc2.start(now + 0.1);
      osc2.stop(now + 0.35);
    } catch {
      // Audio context might be restricted before user interaction
    }
  }, []);

  // Dismiss incoming toast after 6 seconds
  useEffect(() => {
    if (!incomingToast) return;
    const tId = setTimeout(() => setIncomingToast(null), 6000);
    return () => clearTimeout(tId);
  }, [incomingToast]);

  const scrollToBottom = () => {
    if (messagesContainerRef.current) {
      messagesContainerRef.current.scrollTop = messagesContainerRef.current.scrollHeight;
    }
  };

  const loadConversations = useCallback(async (silent = false, append = false) => {
    if (!silent) setLoadingList(true);
    if (append) setLoadingMore(true);
    try {
      const res = await apiRef.current.listConversations({
        ...(statusFilterRef.current === 'UNCLAIMED'
          ? { status: 'OPEN', unclaimed: true }
          : { status: statusFilterRef.current || undefined }),
        cursor: append ? nextCursorRef.current : undefined,
        limit: 20,
      });
      const items = res?.items || [];
      setConversations((prev) => append
        ? Array.from(new Map([...prev, ...items].map((item) => [item.conversationReference, item])).values())
        : items);
      setNextCursor(res?.nextCursor || null);
      setHasMore(Boolean(res?.hasMore));
      setSelectedRef((prev) => prev || (items.length > 0 ? items[0].conversationReference : null));
    } catch (err) {
      if (!silent) setError(err.message || t.errorGeneral);
    } finally {
      if (!silent) setLoadingList(false);
      if (append) setLoadingMore(false);
    }
  }, [t.errorGeneral]);

  const loadDetail = useCallback(async (convRef, silent = false) => {
    if (!convRef) return;
    if (!silent) setLoadingDetail(true);
    try {
      const detail = await apiRef.current.getConversation(convRef);
      setActiveConversation(detail);

      const msgs = detail?.messages || [];
      if (msgs.length > 0) {
        const lastMsg = msgs[msgs.length - 1];
        const prevLastId = lastMsgRefMap.current[convRef];
        if (prevLastId && prevLastId !== lastMsg.messageReference && lastMsg.senderType === 'MEMBER') {
          playNotificationChime();
          setIncomingToast({
            sender: detail.memberName || 'Khách hàng',
            text: lastMsg.messageText,
            ref: convRef,
          });
        }
        lastMsgRefMap.current[convRef] = lastMsg.messageReference;

        if (detail.unreadCount > 0) {
          apiRef.current.recordRead(convRef, { messageReference: lastMsg.messageReference }).catch(() => { });
        }
      }
    } catch (err) {
      if (!silent) setError(err.message || t.errorGeneral);
    } finally {
      if (!silent) setLoadingDetail(false);
    }
  }, [t.errorGeneral, playNotificationChime]);

  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
    loadConversations();
  }, [loadConversations, statusFilter]);

  useEffect(() => {
    if (selectedRef) {
      loadDetail(selectedRef);
      setMessageInput('');
      setError(null);
      setActionSuccess(null);
    }
  }, [selectedRef, loadDetail]);

  useEffect(() => {
    if (userJustSentRef.current) {
      scrollToBottom();
      userJustSentRef.current = false;
    }
  }, [activeConversation?.messages]);

  // Continuous background & foreground polling: every 3s when visible, every 6s when hidden
  useEffect(() => {
    let timerId = null;

    const runPoll = () => {
      loadConversations(true);
      if (selectedRefRef.current) {
        loadDetail(selectedRefRef.current, true);
      }
      const interval = document.visibilityState === 'visible' ? POLLING_INTERVAL_MS : 6000;
      timerId = setTimeout(runPoll, interval);
    };

    timerId = setTimeout(runPoll, POLLING_INTERVAL_MS);

    const handleFocusOrVisible = () => {
      if (document.visibilityState === 'visible') {
        if (timerId) clearTimeout(timerId);
        runPoll();
      }
    };

    window.addEventListener('focus', handleFocusOrVisible);
    document.addEventListener('visibilitychange', handleFocusOrVisible);

    return () => {
      if (timerId) clearTimeout(timerId);
      window.removeEventListener('focus', handleFocusOrVisible);
      document.removeEventListener('visibilitychange', handleFocusOrVisible);
    };
  }, [loadConversations, loadDetail]);

  // Send message
  const handleSendMessage = async (e) => {
    e.preventDefault();
    if (!activeConversation || !messageInput.trim()) return;

    if (activeConversation.status === 'CLOSED') {
      setError(t.chatClosedWarning);
      return;
    }

    const valErr = validateMessageText(messageInput);
    if (valErr) {
      setError(valErr);
      return;
    }

    setSending(true);
    setError(null);
    setActionSuccess(null);

    try {
      const key = newCommandId();
      await api.sendMessage(key, activeConversation.conversationReference, { message: messageInput.trim() });
      setMessageInput('');
      userJustSentRef.current = true;
      loadDetail(activeConversation.conversationReference, true);
      loadConversations(true);
    } catch (err) {
      setError(err.message || t.errorGeneral);
    } finally {
      setSending(false);
    }
  };

  // Claim
  const handleClaim = async () => {
    if (!activeConversation) return;
    setError(null);
    try {
      const key = newCommandId();
      const updated = await api.claim(key, activeConversation.aggregateVersion, activeConversation.conversationReference);
      setActiveConversation((prev) => ({ ...prev, ...updated }));
      setActionSuccess(t.claimSuccess);
      loadConversations(true);
    } catch (err) {
      setError(err.message || t.errorGeneral);
    }
  };

  // Release
  const handleRelease = async () => {
    if (!activeConversation) return;
    setError(null);
    try {
      const key = newCommandId();
      const updated = await api.release(key, activeConversation.aggregateVersion, activeConversation.conversationReference);
      setActiveConversation((prev) => ({ ...prev, ...updated }));
      setActionSuccess(t.releaseSuccess);
      loadConversations(true);
    } catch (err) {
      setError(err.message || t.errorGeneral);
    }
  };

  // Assign branch
  const handleAssignSubmit = async (e) => {
    e.preventDefault();
    if (!activeConversation || !selectedBranch) return;
    setAssigning(true);
    setError(null);
    try {
      const key = newCommandId();
      const updated = await api.assign(
        key,
        activeConversation.aggregateVersion,
        activeConversation.conversationReference,
        { locationReference: selectedBranch }
      );
      setActiveConversation((prev) => ({ ...prev, ...updated }));
      setIsAssignModalOpen(false);
      setActionSuccess(t.assignSuccess);
      loadConversations(true);
    } catch (err) {
      setError(err.message || t.errorGeneral);
    } finally {
      setAssigning(false);
    }
  };

  // Close conversation
  const handleClose = async () => {
    if (!activeConversation || !window.confirm(t.closeConfirm)) return;
    try {
      const key = newCommandId();
      const updated = await api.close(key, activeConversation.aggregateVersion, activeConversation.conversationReference);
      setActiveConversation((prev) => ({ ...prev, ...updated }));
      loadConversations(true);
    } catch (err) {
      setError(err.message || t.errorGeneral);
    }
  };

  // Reopen conversation
  const handleReopen = async () => {
    if (!activeConversation || !window.confirm(t.reopenConfirm)) return;
    try {
      const key = newCommandId();
      const updated = await api.reopen(key, activeConversation.aggregateVersion, activeConversation.conversationReference);
      setActiveConversation((prev) => ({ ...prev, ...updated }));
      loadConversations(true);
    } catch (err) {
      setError(err.message || t.errorGeneral);
    }
  };

  const currentCount = countCodePoints(messageInput);
  const isClosed = activeConversation?.status === 'CLOSED';
  const isClaimedByMe = activeConversation?.claimantRole !== null; // claimant is set

  return (
    <div className="wsp-workspace">
      {/* Real-time screen notification toast for incoming customer messages */}
      {incomingToast && (
        <div
          data-testid="chat-screen-notification"
          style={{
            position: 'fixed',
            top: '1.25rem',
            right: '1.25rem',
            zIndex: 99999,
            backgroundColor: '#0284c7',
            color: '#ffffff',
            padding: '0.85rem 1.25rem',
            borderRadius: 8,
            boxShadow: '0 10px 25px -5px rgba(0,0,0,0.3)',
            display: 'flex',
            alignItems: 'center',
            gap: '0.75rem',
            maxWidth: 380,
            cursor: 'pointer',
            border: '2px solid #38bdf8',
          }}
          onClick={() => {
            setSelectedRef(incomingToast.ref);
            setIncomingToast(null);
          }}
        >
          <span style={{ fontSize: '1.5rem' }}>🔔</span>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontWeight: 700, fontSize: '0.9rem' }}>
              Tin nhắn mới từ {incomingToast.sender}:
            </div>
            <div style={{ fontSize: '0.85rem', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', opacity: 0.95 }}>
              "{incomingToast.text}"
            </div>
          </div>
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              setIncomingToast(null);
            }}
            style={{ background: 'none', border: 'none', color: '#ffffff', cursor: 'pointer', fontSize: '1rem', fontWeight: 'bold' }}
          >
            ✕
          </button>
        </div>
      )}

      <main className="wsp-main">
        <div className="consultation-layout">
          {/* Sidebar */}
          <div className="consultation-sidebar">
            <div className="consultation-sidebar-header">
              <h2 className="consultation-sidebar-title">{t.inboxTitle}</h2>
            </div>

            <div className="consultation-filter-bar">
              <select
                className="consultation-filter-select"
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
              >
                <option value="">{t.allStatuses}</option>
                <option value="OPEN">{t.filterOpen}</option>
                <option value="UNCLAIMED">{t.filterUnclaimed}</option>
                <option value="CLOSED">{t.filterClosed}</option>
              </select>
            </div>

            <ul className="consultation-items-list">
              {loadingList && conversations.length === 0 ? (
                <li style={{ padding: '2rem', textAlign: 'center', color: '#888' }}>
                  Đang tải danh sách...
                </li>
              ) : conversations.length === 0 ? (
                <li style={{ padding: '2rem', textAlign: 'center', color: '#888' }}>
                  {t.noConversations}
                </li>
              ) : (
                conversations.map((conv) => {
                  const isActive = selectedRef === conv.conversationReference;
                  const snapshot = conv.contextSnapshot;
                  const code = snapshot?.displayCode || conv.conversationReference;
                  const name = snapshot?.displayName || 'Thiết kế';

                  return (
                    <li
                      key={conv.conversationReference}
                      className={`consultation-item ${isActive ? 'active' : ''}`}
                      onClick={() => setSelectedRef(conv.conversationReference)}
                    >
                      <div className="consultation-item-header">
                        <span className="consultation-item-code">{code}</span>
                        <span className={`consultation-status-badge ${conv.status?.toLowerCase()}`}>
                          {conv.status === 'OPEN' ? t.statusOpen : t.statusClosed}
                        </span>
                      </div>
                      <div className="consultation-item-name">{name}</div>
                      <div className="consultation-item-meta">
                        <span>
                          {conv.assignedLocationReference || t.unassignedBranch}
                        </span>
                        {conv.unreadCount > 0 && (
                          <span className="consultation-unread-badge">
                            {t.unreadBadge.replace('{count}', String(conv.unreadCount))}
                          </span>
                        )}
                      </div>
                    </li>
                  );
                })
              )}
            </ul>
            {hasMore && (
              <button type="button" className="btn-secondary-consultation" onClick={() => loadConversations(false, true)} disabled={loadingMore}>
                {loadingMore ? '...' : t.loadMore}
              </button>
            )}
          </div>

          {/* Main View */}
          <div className="consultation-chat-main">
            {!activeConversation ? (
              <div className="consultation-empty-state">
                <div style={{ fontSize: '3rem' }}>💬</div>
                <p>{t.noConversations}</p>
              </div>
            ) : (
              <>
                {/* Header */}
                <div className="consultation-context-header">
                  <div className="consultation-context-info">
                    <AttachedRingHoverRecap conversation={activeConversation}>
                      <div className="consultation-context-thumbnail">
                        {activeConversation.contextSnapshot?.preview?.imageUrl ? (
                          <img
                            src={activeConversation.contextSnapshot.preview.imageUrl}
                            alt="Preview"
                            style={{ width: '100%', height: '100%', borderRadius: '6px', objectFit: 'cover' }}
                          />
                        ) : (
                          activeConversation.contextType === 'WORKSHOP_RING_DESIGN'
                            ? '💍'
                            : (activeConversation.contextType === 'INDEPENDENT_DESIGN_REQUEST' ? '📝' : '💬')
                        )}
                      </div>
                    </AttachedRingHoverRecap>
                    <div className="consultation-context-details">
                      <h3>{activeConversation.contextSnapshot?.displayName || t.generalConsultation || 'Tư vấn chung'}</h3>
                      <div className="consultation-context-tags">
                        {activeConversation.contextSnapshot?.displayCode && (
                          <span className="consultation-context-tag">
                            {activeConversation.contextSnapshot.displayCode}
                          </span>
                        )}
                        {activeConversation.contextSnapshot?.designVersion && (
                          <span className="consultation-context-tag">
                            v{activeConversation.contextSnapshot.designVersion}
                          </span>
                        )}
                        <span className="consultation-context-tag">
                          {activeConversation.assignedLocationReference || t.unassignedBranch}
                        </span>
                        <span className="consultation-context-tag">
                          {activeConversation.claimantRole ? t.claimant : t.unclaimed}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Action Buttons */}
                  <div className="consultation-actions">
                    <span className={`consultation-status-badge ${activeConversation.status?.toLowerCase()}`}>
                      {activeConversation.status === 'OPEN' ? t.statusOpen : t.statusClosed}
                    </span>

                    {isManager && (
                      <button
                        type="button"
                        className="btn-secondary-consultation"
                        onClick={() => setIsAssignModalOpen(true)}
                      >
                        📍 {t.assign}
                      </button>
                    )}

                    {!activeConversation.claimantRole ? (
                      <button
                        type="button"
                        className="btn-primary-consultation"
                        onClick={handleClaim}
                      >
                        {t.claim}
                      </button>
                    ) : (
                      <button
                        type="button"
                        className="btn-secondary-consultation"
                        onClick={handleRelease}
                      >
                        {t.release}
                      </button>
                    )}

                    {activeConversation.status === 'OPEN' ? (
                      <button
                        type="button"
                        className="btn-secondary-consultation"
                        onClick={handleClose}
                      >
                        {t.close}
                      </button>
                    ) : (
                      <button
                        type="button"
                        className="btn-secondary-consultation"
                        onClick={handleReopen}
                      >
                        {t.reopen}
                      </button>
                    )}
                  </div>
                </div>

                {error && <div className="consultation-alert error">{error}</div>}
                {actionSuccess && <div className="consultation-alert warning">{actionSuccess}</div>}

                {/* Messages Thread */}
                  <div className="consultation-messages-thread" ref={messagesContainerRef}>
                    {loadingDetail && !activeConversation.messages ? (
                      <div style={{ textAlign: 'center', color: '#888', padding: '2rem' }}>
                        Đang tải nội dung...
                      </div>
                    ) : activeConversation.messages?.length === 0 ? (
                      <div style={{ textAlign: 'center', color: '#888', padding: '3rem' }}>
                        {t.noMessagesYet}
                      </div>
                    ) : (
                      activeConversation.messages?.map((msg, idx) => {
                        const isStaffMsg = msg.senderRole === 'STAFF' || msg.senderRole === 'MANAGER';
                        const roleClass = isStaffMsg ? 'member' : 'staff'; // Staff view: staff messages align right
                        const roleName = msg.senderRole === 'MEMBER'
                          ? t.senderMember
                          : (msg.senderRole === 'MANAGER' ? t.senderManager : t.senderStaff);
                        const timeStr = msg.createdAt
                          ? new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                          : '';

                        return (
                          <div key={msg.messageReference || idx} className={`consultation-bubble-group ${roleClass}`}>
                            <div className="consultation-bubble-meta">
                              <span className={`consultation-role-pill ${msg.senderRole?.toLowerCase()}`}>
                                {roleName}
                              </span>
                              <span>{timeStr}</span>
                            </div>
                            <div className={`consultation-bubble ${roleClass}`}>
                              {msg.text}
                            </div>
                          </div>
                        );
                      })
                    )}
                    <div ref={messagesEndRef} />
                  </div>

                  {/* Composer */}
                  <form className="consultation-composer" onSubmit={handleSendMessage}>
                    {isClosed ? (
                      <div className="consultation-alert warning" style={{ margin: 0 }}>
                        {t.chatClosedWarning}
                      </div>
                    ) : !isClaimedByMe && !isManager ? (
                      <div className="consultation-alert warning" style={{ margin: 0, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span>{t.mustClaimWarning}</span>
                        <button type="button" className="btn-primary-consultation" onClick={handleClaim} style={{ fontSize: '0.8rem', padding: '0.3rem 0.7rem' }}>
                          {t.claim}
                        </button>
                      </div>
                    ) : (
                      <>
                        <textarea
                          className="consultation-textarea"
                          placeholder={t.typeMessagePlaceholder}
                          value={messageInput}
                          onChange={(e) => setMessageInput(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter' && !e.shiftKey) {
                              e.preventDefault();
                              handleSendMessage(e);
                            }
                          }}
                          disabled={sending}
                          maxLength={MAX_MESSAGE_LENGTH}
                        />
                        <div className="consultation-composer-footer">
                          <span
                            className={`consultation-char-counter ${currentCount > 1900 ? (currentCount > 2000 ? 'limit-exceeded' : 'limit-near') : ''
                              }`}
                          >
                            {t.charCount.replace('{count}', String(currentCount))}
                          </span>
                          <button
                            type="submit"
                            className="btn-primary-consultation"
                            disabled={sending || !messageInput.trim() || currentCount > 2000}
                          >
                            {sending ? 'Đang gửi...' : t.send}
                          </button>
                        </div>
                      </>
                    )}
                  </form>
                </>
            )}
              </div>

            {/* Assign Branch Modal (Manager only) */}
            {isAssignModalOpen && (
              <div className="consultation-modal-overlay" onClick={() => setIsAssignModalOpen(false)}>
                <div className="consultation-modal" style={{ maxWidth: '450px' }} onClick={(e) => e.stopPropagation()}>
                  <div className="consultation-modal-header">
                    <h3 className="consultation-modal-title">{t.assign}</h3>
                    <button type="button" className="btn-secondary-consultation" onClick={() => setIsAssignModalOpen(false)}>
                      ✕
                    </button>
                  </div>
                  <form onSubmit={handleAssignSubmit}>
                    <div className="consultation-modal-body">
                      <p style={{ margin: 0, color: '#555' }}>{t.selectBranchToAssign}</p>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                        <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer' }}>
                          <input
                            type="radio"
                            name="branch"
                            value="HN-HOANKIEM"
                            checked={selectedBranch === 'HN-HOANKIEM'}
                            onChange={(e) => setSelectedBranch(e.target.value)}
                          />
                          <span>{t.branchHN_HOANKIEM} (HN-HOANKIEM)</span>
                        </label>
                        <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer' }}>
                          <input
                            type="radio"
                            name="branch"
                            value="HN-TAYHO"
                            checked={selectedBranch === 'HN-TAYHO'}
                            onChange={(e) => setSelectedBranch(e.target.value)}
                          />
                          <span>{t.branchHN_TAYHO} (HN-TAYHO)</span>
                        </label>
                      </div>
                    </div>
                    <div className="consultation-modal-footer">
                      <button type="button" className="btn-secondary-consultation" onClick={() => setIsAssignModalOpen(false)}>
                        {t.cancel}
                      </button>
                      <button type="submit" className="btn-primary-consultation" disabled={assigning}>
                        {assigning ? 'Đang xử lý...' : t.confirm}
                      </button>
                    </div>
                  </form>
                </div>
              </div>
            )}
          </div>
      </main>
    </div>
  );
}
