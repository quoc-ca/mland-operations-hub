import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useSearchParams } from 'react-router';
import { useConsultationChatApi } from '../api/useConsultationChatApi';
import { consultationMessages } from '../messages';
import { useT, useLanguage } from '../../../shared/i18n/I18nProvider';
import { validateMessageText, countCodePoints, MAX_MESSAGE_LENGTH, POLLING_INTERVAL_MS } from '../domain/chatRules';
import { ContextSelectorModal } from './ContextSelectorModal';
import { newCommandId } from '../../../shared/api/http';
import '../consultation-chat.css';

export function MemberConsultationPage() {
  const t = useT(consultationMessages);
  const { language } = useLanguage();
  const api = useConsultationChatApi();

  const [searchParams] = useSearchParams();
  const designRefParam = searchParams.get('designReference') || searchParams.get('designId');
  const designVersionParam = searchParams.get('designVersion');
  const autoAttachedRef = useRef(false);

  const [conversations, setConversations] = useState([]);
  const [nextCursor, setNextCursor] = useState(null);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [selectedRef, setSelectedRef] = useState(null);
  const [activeConversation, setActiveConversation] = useState(null);
  const [loadingList, setLoadingList] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [messageInput, setMessageInput] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState(null);
  const [rateLimitSeconds, setRateLimitSeconds] = useState(null);

  const messagesEndRef = useRef(null);
  const pollTimerRef = useRef(null);
  const apiRef = useRef(api);
  apiRef.current = api;
  const selectedRefRef = useRef(selectedRef);
  selectedRefRef.current = selectedRef;
  const nextCursorRef = useRef(nextCursor);
  nextCursorRef.current = nextCursor;

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'auto' });
  };

  // Fetch conversations list
  const loadConversations = useCallback(async (silent = false, append = false) => {
    if (!silent) setLoadingList(true);
    if (append) setLoadingMore(true);
    try {
      const res = await apiRef.current.listConversations({ cursor: append ? nextCursorRef.current : undefined, limit: 20 });
      const items = res?.items || [];
      setConversations((prev) => append
        ? Array.from(new Map([...prev, ...items].map((item) => [item.conversationReference, item])).values())
        : items);
      setNextCursor(res?.nextCursor || null);
      setHasMore(Boolean(res?.hasMore));
      setSelectedRef((prev) => prev || (items.length > 0 ? items[0].conversationReference : null));
    } catch (err) {
      if (!silent) setError(err.message || t('errorGeneral'));
    } finally {
      if (!silent) setLoadingList(false);
      if (append) setLoadingMore(false);
    }
  }, [t]);

  // Fetch conversation detail
  const loadDetail = useCallback(async (convRef, silent = false) => {
    if (!convRef) return;
    if (!silent) setLoadingDetail(true);
    try {
      const detail = await apiRef.current.getConversation(convRef);
      setActiveConversation(detail);

      // Record read state if there are messages
      const msgs = detail?.messages || [];
      if (msgs.length > 0 && detail.unreadCount > 0) {
        const lastMsg = msgs[msgs.length - 1];
        apiRef.current.recordRead(convRef, { messageReference: lastMsg.messageReference }).catch(() => {});
      }
    } catch (err) {
      if (!silent) setError(err.message || t('errorGeneral'));
    } finally {
      if (!silent) setLoadingDetail(false);
    }
  }, [t]);

  // Initial load
  useEffect(() => {
    loadConversations();
  }, [loadConversations]);

  // Handle auto-attaching design reference from URL if provided
  useEffect(() => {
    if (!designRefParam || autoAttachedRef.current || loadingList) return;
    autoAttachedRef.current = true;

    const existing = conversations.find(
      (c) => c.contextReference === designRefParam || c.contextSnapshot?.displayCode === designRefParam
    );

    if (existing) {
      setSelectedRef(existing.conversationReference);
    } else {
      (async () => {
        try {
          const key = newCommandId();
          const newConv = await api.createConversation(key, {
            contextType: 'WORKSHOP_RING_DESIGN',
            contextReference: designRefParam,
            contextVersion: designVersionParam ? Number(designVersionParam) : 1,
            initialMessage: undefined,
          });
          setConversations((prev) => [newConv, ...prev]);
          setSelectedRef(newConv.conversationReference);
        } catch (err) {
          setError(err.message || t('errorGeneral'));
        }
      })();
    }
  }, [designRefParam, designVersionParam, conversations, loadingList, api, t]);

  // Load detail when selectedRef changes
  useEffect(() => {
    if (selectedRef) {
      loadDetail(selectedRef);
      setMessageInput('');
      setError(null);
    }
  }, [selectedRef, loadDetail]);

  // Scroll to bottom when messages update
  useEffect(() => {
    scrollToBottom();
  }, [activeConversation?.messages]);

  // Visible-tab polling every 10s
  useEffect(() => {
    const poll = () => {
      if (document.visibilityState === 'visible') {
        loadConversations(true);
        if (selectedRefRef.current) {
          loadDetail(selectedRefRef.current, true);
        }
      }
    };

    pollTimerRef.current = setInterval(poll, POLLING_INTERVAL_MS);
    return () => {
      if (pollTimerRef.current) clearInterval(pollTimerRef.current);
    };
  }, [loadConversations, loadDetail]);

  // Rate limit countdown timer
  useEffect(() => {
    if (rateLimitSeconds === null || rateLimitSeconds <= 0) return;
    const timer = setInterval(() => {
      setRateLimitSeconds((prev) => (prev > 1 ? prev - 1 : null));
    }, 1000);
    return () => clearInterval(timer);
  }, [rateLimitSeconds]);

  // Send message
  const handleSendMessage = async (e) => {
    e.preventDefault();
    if (!activeConversation || !messageInput.trim()) return;

    if (activeConversation.status === 'CLOSED') {
      setError(t('chatClosedWarning'));
      return;
    }

    const valErr = validateMessageText(messageInput, t);
    if (valErr) {
      setError(valErr);
      return;
    }

    setSending(true);
    setError(null);

    try {
      const key = newCommandId();
      await api.sendMessage(key, activeConversation.conversationReference, { message: messageInput.trim() });
      setMessageInput('');
      loadDetail(activeConversation.conversationReference, true);
      loadConversations(true);
    } catch (err) {
      if (err.status === 429 || err.code === 'CHAT_RATE_LIMITED') {
        const retryAfter = err.retryAfterSeconds || 60;
        setRateLimitSeconds(retryAfter);
        setError(t('rateLimitWarning', { seconds: retryAfter }));
      } else {
        setError(err.message || t('errorGeneral'));
      }
    } finally {
      setSending(false);
    }
  };

  // Close conversation
  const handleClose = async () => {
    if (!activeConversation || !window.confirm(t('closeConfirm'))) return;
    try {
      const key = newCommandId();
      const updated = await api.close(key, activeConversation.aggregateVersion, activeConversation.conversationReference);
      setActiveConversation((prev) => ({ ...prev, ...updated }));
      loadConversations(true);
    } catch (err) {
      setError(err.message || t('errorGeneral'));
    }
  };

  // Reopen conversation
  const handleReopen = async () => {
    if (!activeConversation || !window.confirm(t('reopenConfirm'))) return;
    try {
      const key = newCommandId();
      const updated = await api.reopen(key, activeConversation.aggregateVersion, activeConversation.conversationReference);
      setActiveConversation((prev) => ({ ...prev, ...updated }));
      loadConversations(true);
    } catch (err) {
      setError(err.message || t('errorGeneral'));
    }
  };

  const handleCreated = (newConv) => {
    setConversations((prev) => [newConv, ...prev]);
    setSelectedRef(newConv.conversationReference);
  };

  const handleHide = async () => {
    if (!activeConversation) return;
    if (activeConversation.status !== 'CLOSED') {
      setError(t('deleteRequiresClosed'));
      return;
    }
    if (!window.confirm(t('deleteChatConfirm'))) return;
    try {
      await api.hideForMember(newCommandId(), activeConversation.aggregateVersion, activeConversation.conversationReference);
      setConversations((prev) => prev.filter((item) => item.conversationReference !== activeConversation.conversationReference));
      setSelectedRef(null);
      setActiveConversation(null);
      setError(null);
    } catch (err) {
      setError(err.code === 'CHAT_DELETE_REQUIRES_CLOSED' ? t('deleteRequiresClosed') : (err.message || t('errorGeneral')));
    }
  };

  const currentCount = countCodePoints(messageInput);
  const isClosed = activeConversation?.status === 'CLOSED';

  return (
    <div className="consultation-layout">
      {/* Sidebar */}
      <div className="consultation-sidebar">
        <div className="consultation-sidebar-header">
          <h2 className="consultation-sidebar-title">{t('title')}</h2>
          <button
            type="button"
            className="btn-primary-consultation"
            onClick={() => setIsModalOpen(true)}
            style={{ fontSize: '0.85rem', padding: '0.4rem 0.8rem' }}
          >
            + {t('newConsultation')}
          </button>
        </div>

        <ul className="consultation-items-list">
          {loadingList && conversations.length === 0 ? (
            <li style={{ padding: '2rem', textAlign: 'center', color: '#888' }}>
              {t('loadingList')}
            </li>
          ) : conversations.length === 0 ? (
            <li style={{ padding: '2rem', textAlign: 'center', color: '#888' }}>
              {t('noConversations')}
            </li>
          ) : (
            conversations.map((conv) => {
              const isActive = selectedRef === conv.conversationReference;
              const snapshot = conv.contextSnapshot;
              const code = snapshot?.displayCode || conv.conversationReference;
              const name = snapshot?.displayName || t('defaultDesignName');

              return (
                <li
                  key={conv.conversationReference}
                  className={`consultation-item ${isActive ? 'active' : ''}`}
                  onClick={() => setSelectedRef(conv.conversationReference)}
                >
                  <div className="consultation-item-header">
                    <span className="consultation-item-code">{code}</span>
                    <span className={`consultation-status-badge ${conv.status?.toLowerCase()}`}>
                      {conv.status === 'OPEN' ? t('statusOpen') : t('statusClosed')}
                    </span>
                  </div>
                  <div className="consultation-item-name">{name}</div>
                  <div className="consultation-item-meta">
                    <span>
                      {conv.assignedLocationReference
                        ? `${t('assignedBranch')}: ${conv.assignedLocationReference}`
                        : t('unassignedBranch')}
                    </span>
                    {conv.unreadCount > 0 && (
                      <span className="consultation-unread-badge">
                        {t('unreadBadge', { count: conv.unreadCount })}
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
            {loadingMore ? '...' : t('loadMore')}
          </button>
        )}
      </div>

      {/* Main View */}
      <div className="consultation-chat-main">
        {!activeConversation ? (
          <div className="consultation-empty-state">
            <div style={{ fontSize: '3rem' }}>💬</div>
            <p>{t('noConversations')}</p>
          </div>
        ) : (
          <>
            {/* Context Header */}
            <div className="consultation-context-header">
              <div className="consultation-context-info">
                <div className="consultation-context-thumbnail">
                  {activeConversation.contextSnapshot?.preview?.imageUrl ? (
                    <img
                      src={activeConversation.contextSnapshot.preview.imageUrl}
                      alt={t('previewAlt')}
                      style={{ width: '100%', height: '100%', borderRadius: '6px', objectFit: 'cover' }}
                    />
                  ) : (
                    activeConversation.contextType === 'WORKSHOP_RING_DESIGN'
                      ? '💍'
                      : (activeConversation.contextType === 'INDEPENDENT_DESIGN_REQUEST' ? '📝' : '💬')
                  )}
                </div>
                <div className="consultation-context-details">
                  <h3>{activeConversation.contextSnapshot?.displayName || t('generalConsultation')}</h3>
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
                      {activeConversation.assignedLocationReference || t('unassignedBranch')}
                    </span>
                  </div>
                </div>
              </div>

              <div className="consultation-actions">
                <span className={`consultation-status-badge ${activeConversation.status?.toLowerCase()}`}>
                  {activeConversation.status === 'OPEN' ? t('statusOpen') : t('statusClosed')}
                </span>
                {activeConversation.status === 'OPEN' ? (
                  <button
                    type="button"
                    className="btn-secondary-consultation"
                    onClick={handleClose}
                  >
                    {t('close')}
                  </button>
                ) : (
                  <button
                    type="button"
                    className="btn-secondary-consultation"
                    onClick={handleReopen}
                  >
                    {t('reopen')}
                  </button>
                )}
                {isClosed && (
                  <button
                    type="button"
                    className="btn-secondary-consultation btn-danger-consultation"
                    onClick={handleHide}
                  >
                    {t('deleteChat')}
                  </button>
                )}
              </div>
            </div>

            {error && <div className="consultation-alert error">{error}</div>}

            {/* Messages Thread */}
            <div className="consultation-messages-thread">
              {loadingDetail && !activeConversation.messages ? (
                <div style={{ textAlign: 'center', color: '#888', padding: '2rem' }}>
                  {t('loadingDetail')}
                </div>
              ) : activeConversation.messages?.length === 0 ? (
                <div style={{ textAlign: 'center', color: '#888', padding: '3rem' }}>
                  {t('noMessagesYet')}
                </div>
              ) : (
                activeConversation.messages?.map((msg, idx) => {
                  const roleClass = msg.senderRole?.toLowerCase() || 'member';
                  const roleName = msg.senderRole === 'MEMBER'
                    ? t('senderMember')
                    : (msg.senderRole === 'MANAGER' ? t('senderManager') : t('senderStaff'));
                  const timeStr = msg.createdAt
                    ? new Date(msg.createdAt).toLocaleTimeString(language === 'en' ? 'en-US' : 'vi-VN', { hour: '2-digit', minute: '2-digit' })
                    : '';

                  return (
                    <div key={msg.messageReference || idx} className={`consultation-bubble-group ${roleClass}`}>
                      <div className="consultation-bubble-meta">
                        <span className={`consultation-role-pill ${roleClass}`}>{roleName}</span>
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
                  {t('chatClosedWarning')}
                </div>
              ) : (
                <>
                  <textarea
                    className="consultation-textarea"
                    placeholder={t('typeMessagePlaceholder')}
                    value={messageInput}
                    onChange={(e) => setMessageInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' && !e.shiftKey) {
                        e.preventDefault();
                        handleSendMessage(e);
                      }
                    }}
                    disabled={sending || rateLimitSeconds !== null}
                    maxLength={MAX_MESSAGE_LENGTH}
                  />
                  <div className="consultation-composer-footer">
                    <span
                      className={`consultation-char-counter ${
                        currentCount > 1900 ? (currentCount > 2000 ? 'limit-exceeded' : 'limit-near') : ''
                      }`}
                    >
                      {t('charCount', { count: currentCount })}
                    </span>
                    <button
                      type="submit"
                      className="btn-primary-consultation"
                      disabled={sending || !messageInput.trim() || currentCount > 2000 || rateLimitSeconds !== null}
                    >
                      {sending ? t('sending') : t('send')}
                    </button>
                  </div>
                </>
              )}
            </form>
          </>
        )}
      </div>

      {/* Context Selector Modal */}
      <ContextSelectorModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onCreated={handleCreated}
      />
    </div>
  );
}
