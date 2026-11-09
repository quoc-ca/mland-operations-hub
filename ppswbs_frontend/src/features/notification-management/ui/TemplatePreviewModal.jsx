import React, { useEffect, useState } from 'react';

const DEFAULT_FIXTURES = {
  customerName: 'Nguyễn Văn A',
  bookingCode: 'BK-SAMPLE-2026',
  orderCode: 'ORD-SAMPLE-2026',
  depositAmount: '500,000 VND',
  orderTotal: '1,200,000 VND',
  workshopTitle: 'Làm nhẫn đôi cơ bản',
  startTime: '09:00 15/11/2026',
  locationAddress: 'Số 123 Đường ABC, Hà Nội',
  ticketUrl: 'https://mland.vn/tickets/preview-sample',
  orderDetailsUrl: 'https://mland.vn/orders/preview-sample',
  firebaseVerificationUrl: 'https://mland.vn/auth/verify?sample=true',
};

export function TemplatePreviewModal({
  isOpen,
  onClose,
  template,
  versionNumber,
  onPreview,
}) {
  const [sampleData, setSampleData] = useState({});
  const [previewResult, setPreviewResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [activeTab, setActiveTab] = useState('html');

  // Initialize fixture sample data
  useEffect(() => {
    if (template && isOpen) {
      const initial = {};
      const placeholders = template.allowedPlaceholders || [];
      placeholders.forEach((ph) => {
        initial[ph] = DEFAULT_FIXTURES[ph] || `[${ph}]`;
      });
      setSampleData(initial);
      setError(null);
      setPreviewResult(null);

      // Trigger initial render
      if (typeof onPreview === 'function') {
        setLoading(true);
        onPreview(versionNumber, initial)
          .then((res) => setPreviewResult(res))
          .catch((err) => setError(err?.message || 'Không thể render bản xem trước'))
          .finally(() => setLoading(false));
      }
    }
  }, [template, versionNumber, isOpen]);

  useEffect(() => {
    function handleKeyDown(e) {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const handleSampleChange = (key, value) => {
    setSampleData((prev) => ({ ...prev, [key]: value }));
  };

  const handleRefreshPreview = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await onPreview(versionNumber, sampleData);
      setPreviewResult(res);
    } catch (err) {
      setError(err?.message || 'Không thể render bản xem trước');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      className="nm-modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="preview-modal-title"
    >
      <div className="nm-modal nm-modal-lg">
        <div className="nm-modal-header">
          <div>
            <h3 id="preview-modal-title" style={{ margin: 0 }}>
              Xem trước mẫu thông báo email
            </h3>
            <div style={{ fontSize: '0.875rem', color: '#64748b', marginTop: '0.25rem' }}>
              {template?.name} (Phiên bản v{versionNumber})
            </div>
          </div>
          <button
            type="button"
            className="nm-modal-close"
            onClick={onClose}
            aria-label="Đóng"
          >
            &times;
          </button>
        </div>

        <div className="nm-modal-body">
          <div className="nm-alert nm-alert-info" role="status">
            <strong>Dữ liệu mẫu an toàn - Không sử dụng dữ liệu khách hàng thật:</strong> Bản xem trước sử dụng dữ liệu giả lập (synthetic fixtures). Không bao giờ hiển thị hoặc nạp thông tin thật của khách hàng, mật khẩu hay mã định danh bảo mật.
          </div>

          {error && <div className="nm-alert nm-alert-danger" role="alert">{error}</div>}

          {/* Sample Data Inputs Accordion */}
          <details style={{ marginBottom: '1rem', border: '1px solid #e2e8f0', borderRadius: '6px', padding: '0.75rem' }}>
            <summary style={{ cursor: 'pointer', fontWeight: 600, color: '#334155' }}>
              Tùy chỉnh giá trị mẫu ({Object.keys(sampleData).length} biến)
            </summary>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: '0.75rem', marginTop: '0.75rem' }}>
              {Object.keys(sampleData).map((key) => (
                <div key={key}>
                  <label
                    htmlFor={`sample-${key}`}
                    style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: '#64748b' }}
                  >
                    {key}
                  </label>
                  <input
                    id={`sample-${key}`}
                    type="text"
                    className="nm-input"
                    style={{ width: '100%', fontSize: '0.8125rem' }}
                    value={sampleData[key]}
                    onChange={(e) => handleSampleChange(key, e.target.value)}
                  />
                </div>
              ))}
            </div>
            <div style={{ marginTop: '0.75rem', textAlign: 'right' }}>
              <button
                type="button"
                className="nm-btn nm-btn-sm nm-btn-primary"
                onClick={handleRefreshPreview}
                disabled={loading}
              >
                {loading ? 'Đang cập nhật…' : 'Cập nhật bản xem trước'}
              </button>
            </div>
          </details>

          {/* Rendered Subject */}
          {previewResult && (
            <div style={{ marginBottom: '1rem', padding: '0.75rem', backgroundColor: '#f8fafc', borderRadius: '6px', border: '1px solid #e2e8f0' }}>
              <span style={{ fontSize: '0.75rem', fontWeight: 600, color: '#64748b' }}>Tiêu đề thư hiển thị:</span>
              <div style={{ fontSize: '0.9375rem', fontWeight: 600, color: '#0f172a', marginTop: '0.25rem' }}>
                {previewResult.renderedSubject}
              </div>
            </div>
          )}

          {/* Tabs */}
          <div className="nm-tabs">
            <button
              type="button"
              className={`nm-tab-btn ${activeTab === 'html' ? 'active' : ''}`}
              onClick={() => setActiveTab('html')}
            >
              Giao diện HTML
            </button>
            <button
              type="button"
              className={`nm-tab-btn ${activeTab === 'text' ? 'active' : ''}`}
              onClick={() => setActiveTab('text')}
            >
              Nội dung Plain Text
            </button>
          </div>

          {loading ? (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '240px' }} role="status">
              <span>Đang tải bản xem trước…</span>
            </div>
          ) : previewResult ? (
            <div>
              {activeTab === 'html' ? (
                <iframe
                  title="HTML Email Preview"
                  className="nm-preview-frame"
                  sandbox="allow-same-origin"
                  srcDoc={previewResult.renderedHtml || '<p style="color:#64748b;padding:1rem;">(Không có nội dung HTML)</p>'}
                />
              ) : (
                <pre className="nm-code-box">
                  {previewResult.renderedPlainText || '(Không có nội dung Plain Text)'}
                </pre>
              )}
            </div>
          ) : (
            <div style={{ textAlign: 'center', color: '#64748b', padding: '2rem 0' }}>
              Chưa có bản xem trước.
            </div>
          )}
        </div>

        <div className="nm-modal-footer">
          <button type="button" className="nm-btn" onClick={onClose}>
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}

export default TemplatePreviewModal;

