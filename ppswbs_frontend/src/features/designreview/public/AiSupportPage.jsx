import React, { useEffect, useState } from 'react';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field, Loading, useCommand } from '../../../shared/ui/components';
import { useDesignReviewApi } from '../api/useDesignReviewApi';
import { designReviewMessages } from '../messages';
import { LIMITS } from '../rules';

export default function AiSupportPage() {
  const t = useT(designReviewMessages);
  const { status } = useAuth();
  const api = useDesignReviewApi();
  const command = useCommand();

  const [inputMessage, setInputMessage] = useState('');
  const [messages, setMessages] = useState(() => {
    if (typeof localStorage !== 'undefined') {
      try {
        const saved = localStorage.getItem('mland_ai_chat_messages');
        return saved ? JSON.parse(saved) : [];
      } catch {
        return [];
      }
    }
    return [];
  });
  const [clientError, setClientError] = useState('');
  const [quota, setQuota] = useState(() => {
    if (typeof localStorage !== 'undefined') {
      try {
        const saved = localStorage.getItem('mland_ai_quota');
        return saved ? JSON.parse(saved) : null;
      } catch {
        return null;
      }
    }
    return null;
  });

  const isMember = status === 'signed-in';
  const defaultLimit = isMember ? 10 : 3;
  const currentLimit = quota ? quota.limit : defaultLimit;
  const currentRemaining = quota ? quota.remaining : defaultLimit;
  const isQuotaExceeded = (quota && quota.remaining <= 0) || command.error?.code === 'AI_DAILY_QUOTA_EXCEEDED';
  const charCount = [...inputMessage].length;

  useEffect(() => {
    if (typeof localStorage !== 'undefined') {
      try {
        localStorage.setItem('mland_ai_chat_messages', JSON.stringify(messages));
      } catch {}
    }
  }, [messages]);

  useEffect(() => {
    if (typeof localStorage !== 'undefined' && quota) {
      try {
        localStorage.setItem('mland_ai_quota', JSON.stringify(quota));
      } catch {}
    }
  }, [quota]);

  useEffect(() => {
    let active = true;
    if (api?.getAiQuota) {
      api.getAiQuota({ auth: isMember })
        .then(res => {
          if (active && res) {
            setQuota(res);
          }
        })
        .catch(() => {});
    }
    return () => {
      active = false;
    };
  }, [isMember]);

  const handleClearHistory = () => {
    setMessages([]);
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem('mland_ai_chat_messages');
    }
  };

  async function handleSend(e) {
    e.preventDefault();
    if (isQuotaExceeded) return;
    const trimmed = inputMessage.trim();
    if (!trimmed) {
      setClientError(t('validation.required'));
      return;
    }
    if ([...trimmed].length > LIMITS.promptMax) {
      setClientError(t('validation.promptTooLong'));
      return;
    }
    setClientError('');

    const userEntry = { sender: 'user', text: trimmed, timestamp: new Date().toISOString() };
    setMessages(prev => [...prev, userEntry]);

    try {
      await command.run(async () => {
        const result = await api.askAiSupport({ message: trimmed, auth: isMember });
        if (result?.quota) {
          setQuota(result.quota);
        }
        const assistantEntry = {
          sender: 'assistant',
          text: result.answer || result.message,
          scope: result.scope,
          correlationId: result.correlationId || result.providerCorrelationId,
          timestamp: new Date().toISOString(),
        };
        setMessages(prev => [...prev, assistantEntry]);
        setInputMessage('');
        return result;
      });
    } catch (err) {
      if (err?.quota) {
        setQuota(err.quota);
      }
    }
  }

  return (
    <div className="design-container">
      <header className="page-header">
        <h1>{t('aiChat.title')}</h1>
        <p className="muted">{t('aiChat.subtitle')}</p>
        <span className={`badge-tag ${isMember ? 'badge-success' : 'badge-status'}`}>
          {isMember ? t('aiChat.memberBadge') : t('aiChat.guestBadge')}
        </span>
        <span
          className="badge-tag"
          style={{ marginLeft: '0.5rem', background: '#f3f4f6', color: '#374151', border: '1px solid #d1d5db' }}
        >
          {t('aiChat.quotaNotice', { remaining: currentRemaining, limit: currentLimit })}
        </span>
        {messages.length > 0 && (
          <button
            type="button"
            onClick={handleClearHistory}
            className="button outline small"
            style={{ marginLeft: '0.75rem', fontSize: '0.75rem', padding: '0.2rem 0.5rem' }}
            title="Xóa lịch sử trò chuyện"
          >
            🗑️ Xóa lịch sử
          </button>
        )}
      </header>

      <div className="disclaimer-banner" role="note">
        {t('aiChat.disclaimer')}
      </div>

      <div className="chat-box" aria-live="polite">
        <div className="chat-messages">
          {messages.length === 0 ? (
            <p className="muted center" style={{ margin: 'auto' }}>
              {t('aiChat.empty')}
            </p>
          ) : (
            messages.map((msg, index) => (
              <div key={index} className={`chat-message ${msg.sender}`}>
                <span className="badge">
                  {msg.sender === 'user'
                    ? (isMember ? t('aiChat.memberBadge') : t('aiChat.guestBadge'))
                    : t('aiChat.title')}
                </span>
                <p style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{msg.text}</p>
              </div>
            ))
          )}
          {command.pending && <Loading />}
        </div>

        <form onSubmit={handleSend} className="chat-form">
          <ErrorNotice error={command.error} messages={designReviewMessages} onRetry={command.retry} />
          {clientError && <div className="notice error">{clientError}</div>}
          {isQuotaExceeded && (
            <div className="notice warning" style={{ marginBottom: '1rem' }} role="status">
              {t('aiChat.quotaExceeded')}
            </div>
          )}

          <div className="chat-input-row">
            <Field label={t('aiChat.title')} hint={t('aiChat.charCount', { count: charCount })}>
              <textarea
                value={inputMessage}
                onChange={e => {
                  setInputMessage(e.target.value);
                  if (clientError) setClientError('');
                }}
                placeholder={t('aiChat.inputPlaceholder')}
                disabled={command.pending || isQuotaExceeded}
                rows={2}
                aria-label={t('aiChat.inputPlaceholder')}
              />
            </Field>
            <button
              type="submit"
              className="button primary"
              disabled={command.pending || !inputMessage.trim() || isQuotaExceeded}
              style={{ marginTop: '1.5rem' }}
            >
              {t('aiChat.send')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
