import React, { useState, useEffect } from 'react';
import { useConsultationChatApi } from '../api/useConsultationChatApi';
import { consultationMessages } from '../messages';
import { validateMessageText, countCodePoints, MAX_MESSAGE_LENGTH } from '../domain/chatRules';
import { newCommandId } from '../../../shared/api/http';

export function ContextSelectorModal({ isOpen, onClose, onCreated, lang = 'vi' }) {
  const t = consultationMessages[lang] || consultationMessages.vi;
  const api = useConsultationChatApi();

  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [options, setOptions] = useState([]);
  const [selectedContext, setSelectedContext] = useState(null);
  const [useGeneralConsultation, setUseGeneralConsultation] = useState(false);
  const [initialMessage, setInitialMessage] = useState('');
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!isOpen) {
      setSelectedContext(null);
      setUseGeneralConsultation(false);
      setInitialMessage('');
      setError(null);
      return;
    }

    setLoading(true);
    setError(null);
    api.getContextOptions()
      .then((res) => {
        const items = res?.items || [];
        setOptions(items);
        if (items.length > 0) {
          setSelectedContext(items[0]);
        }
      })
      .catch((err) => {
        setError(err.message || t.errorGeneral);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [isOpen]);

  if (!isOpen) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!selectedContext && !useGeneralConsultation) {
      setError(t.selectContext);
      return;
    }

    if (initialMessage.trim()) {
      const msgErr = validateMessageText(initialMessage);
      if (msgErr) {
        setError(msgErr);
        return;
      }
    }

    setSubmitting(true);
    setError(null);

    try {
      const key = newCommandId();
      const body = {
        contextType: useGeneralConsultation ? null : selectedContext.contextType,
        contextReference: useGeneralConsultation ? null : selectedContext.contextReference,
        contextVersion: useGeneralConsultation ? null : selectedContext.designVersion,
        initialMessage: initialMessage.trim() || undefined,
      };

      const created = await api.createConversation(key, body);
      if (onCreated) {
        onCreated(created);
      }
      onClose();
    } catch (err) {
      setError(err.message || t.errorGeneral);
    } finally {
      setSubmitting(false);
    }
  };

  const currentCount = countCodePoints(initialMessage);

  return (
    <div className="consultation-modal-overlay" onClick={onClose}>
      <div className="consultation-modal" onClick={(e) => e.stopPropagation()}>
        <div className="consultation-modal-header">
          <h3 className="consultation-modal-title">{t.selectContext}</h3>
          <button type="button" className="btn-secondary-consultation" onClick={onClose}>
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="consultation-modal-body">
            {error && <div className="consultation-alert error">{error}</div>}

            <p style={{ margin: 0, color: '#555' }}>{t.selectContextPrompt}</p>

            <button
              type="button"
              className={`consultation-context-card ${useGeneralConsultation ? 'selected' : ''}`}
              onClick={() => { setUseGeneralConsultation(true); setSelectedContext(null); }}
            >
              {t.generalConsultation}
            </button>

            {loading ? (
              <div style={{ textAlign: 'center', padding: '2rem', color: '#888' }}>
                Đang tải danh sách thiết kế...
              </div>
            ) : options.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '2rem', color: '#888' }}>
                {t.noContextAvailable}
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', maxHeight: '250px', overflowY: 'auto' }}>
                {options.map((opt) => {
                  const isSelected = selectedContext?.contextReference === opt.contextReference &&
                                     selectedContext?.designVersion === opt.designVersion;
                  return (
                    <div
                      key={`${opt.contextType}-${opt.contextReference}-v${opt.designVersion}`}
                      className={`consultation-context-card ${isSelected ? 'selected' : ''}`}
                      onClick={() => { setUseGeneralConsultation(false); setSelectedContext(opt); }}
                    >
                      <div className="consultation-context-thumbnail">
                        {opt.preview?.imageUrl ? (
                          <img
                            src={opt.preview.imageUrl}
                            alt={opt.displayName}
                            style={{ width: '100%', height: '100%', borderRadius: '6px', objectFit: 'cover' }}
                          />
                        ) : (
                          opt.contextType === 'WORKSHOP_RING_DESIGN' ? '💍' : '📝'
                        )}
                      </div>
                      <div style={{ flex: 1 }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <span style={{ fontWeight: 600, fontSize: '0.95rem', color: '#1a1a1a' }}>
                            {opt.displayCode}
                          </span>
                          <span className="consultation-context-tag">
                            {opt.contextType === 'WORKSHOP_RING_DESIGN' ? t.contextTypeWorkshop : t.contextTypeIndependent} (v{opt.designVersion})
                          </span>
                        </div>
                        <div style={{ fontSize: '0.875rem', color: '#444', marginTop: '0.2rem' }}>
                          {opt.displayName}
                        </div>
                        {opt.preview?.summary && (
                          <div style={{ fontSize: '0.8rem', color: '#777', marginTop: '0.2rem' }}>
                            {opt.preview.summary}
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            <div style={{ marginTop: '0.5rem' }}>
              <label style={{ display: 'block', fontWeight: 600, fontSize: '0.9rem', marginBottom: '0.35rem' }}>
                Tin nhắn ban đầu (tùy chọn):
              </label>
              <textarea
                className="consultation-textarea"
                rows={3}
                placeholder={t.typeMessagePlaceholder}
                value={initialMessage}
                onChange={(e) => setInitialMessage(e.target.value)}
                maxLength={MAX_MESSAGE_LENGTH}
              />
              <div style={{ textAlign: 'right', fontSize: '0.75rem', color: '#888', marginTop: '0.25rem' }}>
                {t.charCount.replace('{count}', String(currentCount))}
              </div>
            </div>
          </div>

          <div className="consultation-modal-footer">
            <button
              type="button"
              className="btn-secondary-consultation"
              onClick={onClose}
              disabled={submitting}
            >
              {t.cancel}
            </button>
            <button
              type="submit"
              className="btn-primary-consultation"
              disabled={submitting || loading || (!useGeneralConsultation && options.length === 0)}
            >
              {submitting ? 'Đang tạo...' : t.confirm}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
