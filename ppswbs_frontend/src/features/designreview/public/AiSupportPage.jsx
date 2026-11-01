import React, { useState } from 'react';
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
  const [messages, setMessages] = useState([]);
  const [clientError, setClientError] = useState('');

  const isMember = status === 'signed-in';
  const charCount = [...inputMessage].length;

  async function handleSend(e) {
    e.preventDefault();
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

    const res = await command.run(async () => {
      const result = await api.askAiSupport({ message: trimmed, auth: isMember });
      const assistantEntry = {
        sender: 'assistant',
        text: result.message,
        scope: result.scope,
        correlationId: result.providerCorrelationId,
        timestamp: new Date().toISOString(),
      };
      setMessages(prev => [...prev, assistantEntry]);
      setInputMessage('');
      return result;
    });
  }

  return (
    <div className="design-container">
      <header className="page-header">
        <h1>{t('aiChat.title')}</h1>
        <p className="muted">{t('aiChat.subtitle')}</p>
        <span className={`badge-tag ${isMember ? 'badge-success' : 'badge-status'}`}>
          {isMember ? t('aiChat.memberBadge') : t('aiChat.guestBadge')}
        </span>
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

          <div className="chat-input-row">
            <Field label={t('aiChat.title')} hint={t('aiChat.charCount', { count: charCount })}>
              <textarea
                value={inputMessage}
                onChange={e => {
                  setInputMessage(e.target.value);
                  if (clientError) setClientError('');
                }}
                placeholder={t('aiChat.inputPlaceholder')}
                disabled={command.pending}
                rows={2}
                aria-label={t('aiChat.inputPlaceholder')}
              />
            </Field>
            <button
              type="submit"
              className="button primary"
              disabled={command.pending || !inputMessage.trim()}
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
