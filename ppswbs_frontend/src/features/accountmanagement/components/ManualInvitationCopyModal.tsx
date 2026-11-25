import React, { useEffect, useState } from 'react';
import { accountManagementApiClient, ManualInvitationResponse } from '../api/AccountManagementApiClient';
import { useFormat } from '../../../shared/i18n/I18nProvider';

interface ManualInvitationCopyModalProps {
  isOpen: boolean;
  onClose: () => void;
  requestId: number | string;
}

export const ManualInvitationCopyModal: React.FC<ManualInvitationCopyModalProps> = ({
  isOpen,
  onClose,
  requestId,
}) => {
  const fmt = useFormat();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<ManualInvitationResponse | null>(null);
  const [copied, setCopied] = useState(false);
  const [copiedLink, setCopiedLink] = useState(false);

  useEffect(() => {
    if (isOpen && requestId) {
      setLoading(true);
      setError(null);
      setData(null);
      setCopied(false);
      setCopiedLink(false);

      accountManagementApiClient
        .getManualInvitation(requestId)
        .then(res => setData(res))
        .catch(err => setError(err?.message || 'Không thể tải nội dung thư mời thủ công.'))
        .finally(() => setLoading(false));
    }
  }, [isOpen, requestId]);

  if (!isOpen) return null;

  // Extract setup link from plainText or fallback
  const linkMatch = data?.plainText?.match(/https?:\/\/[^\s]+/);
  const setupUrl = linkMatch ? linkMatch[0] : '';

  const handleCopyAll = async () => {
    if (!data) return;
    const textToCopy = `Tiêu đề: ${data.subject}\n\n${data.plainText}`;
    try {
      await navigator.clipboard.writeText(textToCopy);
      setCopied(true);
      setTimeout(() => setCopied(false), 3000);
    } catch {
      // Fallback
    }
  };

  const handleCopyLinkOnly = async () => {
    if (!setupUrl) return;
    try {
      await navigator.clipboard.writeText(setupUrl);
      setCopiedLink(true);
      setTimeout(() => setCopiedLink(false), 3000);
    } catch {
      // Fallback
    }
  };

  return (
    <div className="account-modal-overlay" role="dialog" aria-modal="true" aria-labelledby="manual-copy-title">
      <div className="account-modal-container" style={{ maxWidth: '650px', maxHeight: '90vh', overflowY: 'auto' }}>
        <div className="account-modal-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 id="manual-copy-title" style={{ margin: 0, fontSize: '1.15rem', color: '#0f172a' }}>
            Sao chép nội dung thư mời thủ công (ADMIN)
          </h2>
          <button
            type="button"
            className="account-modal-close"
            onClick={onClose}
            aria-label="Đóng"
            style={{ background: 'none', border: 'none', fontSize: '1.5rem', cursor: 'pointer' }}
          >
            ×
          </button>
        </div>

        <div className="account-modal-body" style={{ padding: '1rem 0' }}>
          {loading && (
            <div style={{ textAlign: 'center', padding: '2rem 0', color: '#64748b' }}>
              Đang tải nội dung thư mời…
            </div>
          )}

          {error && (
            <div
              className="account-alert account-alert-error"
              role="alert"
              style={{
                backgroundColor: '#fef2f2',
                border: '1px solid #fecaca',
                color: '#b91c1c',
                padding: '0.75rem 1rem',
                borderRadius: '6px',
                marginBottom: '1rem',
              }}
            >
              {error}
            </div>
          )}

          {data && (
            <>
              <div
                className="account-alert account-alert-warning"
                style={{
                  backgroundColor: '#fffbeb',
                  border: '1px solid #fde68a',
                  color: '#92400e',
                  padding: '0.75rem 1rem',
                  borderRadius: '6px',
                  marginBottom: '1rem',
                  fontSize: '0.85rem',
                }}
              >
                <strong>Lưu ý bảo mật & Kiểm toán:</strong>
                <p style={{ margin: '0.25rem 0 0 0' }}>
                  Hành động trích xuất nội dung thư mời được hệ thống kiểm toán (audit). Việc sao chép thủ công không làm thay đổi trạng thái gửi thư (vẫn giữ FAILED) và không chứng minh thư đã được gửi đến người nhận ngoài đời thực.
                </p>
              </div>

              <div className="account-card" style={{ marginBottom: '1rem', padding: '0.75rem 1rem' }}>
                <div style={{ marginBottom: '0.75rem' }}>
                  <span style={{ fontSize: '0.8rem', color: '#64748b', fontWeight: 600 }}>Thời hạn hiệu lực liên kết:</span>
                  <div style={{ fontSize: '0.9rem', color: '#b91c1c', fontWeight: 600 }}>
                    Hết hạn lúc {fmt.dateTime(data.expiresAt)}
                  </div>
                </div>

                <div style={{ marginBottom: '0.75rem' }}>
                  <label style={{ display: 'block', fontSize: '0.8rem', color: '#64748b', fontWeight: 600, marginBottom: '0.25rem' }}>
                    Tiêu đề thư (Subject):
                  </label>
                  <input
                    type="text"
                    readOnly
                    value={data.subject}
                    className="account-input"
                    style={{ background: '#f8fafc', fontWeight: 500 }}
                  />
                </div>

                <div style={{ marginBottom: '0.75rem' }}>
                  <label style={{ display: 'block', fontSize: '0.8rem', color: '#64748b', fontWeight: 600, marginBottom: '0.25rem' }}>
                    Nội dung thư dạng văn bản (Plain Text Body):
                  </label>
                  <textarea
                    readOnly
                    rows={6}
                    value={data.plainText}
                    className="account-input"
                    style={{ background: '#f8fafc', fontFamily: 'monospace', fontSize: '0.85rem', resize: 'vertical' }}
                  />
                </div>

                {setupUrl && (
                  <div>
                    <label style={{ display: 'block', fontSize: '0.8rem', color: '#64748b', fontWeight: 600, marginBottom: '0.25rem' }}>
                      Liên kết kích hoạt (Setup URL):
                    </label>
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <input
                        type="text"
                        readOnly
                        value={setupUrl}
                        className="account-input"
                        style={{ background: '#f8fafc', fontSize: '0.85rem' }}
                      />
                      <button
                        type="button"
                        className="account-btn account-btn-secondary"
                        onClick={handleCopyLinkOnly}
                        style={{ whiteSpace: 'nowrap' }}
                      >
                        {copiedLink ? '✓ Đã chép link' : 'Chép link'}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </>
          )}
        </div>

        <div className="account-modal-footer" style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end', borderTop: '1px solid #e2e8f0', paddingTop: '1rem' }}>
          {data && (
            <button
              type="button"
              className="account-btn account-btn-primary"
              onClick={handleCopyAll}
              data-testid="copy-all-email-btn"
            >
              {copied ? '✓ Đã sao chép toàn bộ thư' : 'Sao chép toàn bộ nội dung thư'}
            </button>
          )}

          <button
            type="button"
            className="account-btn account-btn-secondary"
            onClick={onClose}
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
};
