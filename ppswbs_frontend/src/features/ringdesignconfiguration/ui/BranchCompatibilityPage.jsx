import React from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { messages } from '../messages';
import '../ring-design.css';

export default function BranchCompatibilityPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const packageReference = searchParams.get('packageReference') || '';
  const designId = searchParams.get('designId') || '';
  const reason = searchParams.get('reason') || 'Chi nhánh này chưa hỗ trợ một số vật liệu kim hoàn đặc thù của mẫu nhẫn.';

  const lang = 'vi';
  const t = messages[lang];

  return (
    <div className="ring-design-container" data-testid="branch-compatibility-page">
      <div className="ring-recovery-card" role="region" aria-label="Branch compatibility warning">
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: '1rem' }}>
          <div style={{ fontSize: '2rem' }}>⚠️</div>
          <div>
            <h2 style={{ fontSize: '1.25rem', fontWeight: 600, margin: '0 0 0.5rem', color: '#92400e' }}>
              {t.branchRecoveryTitle}
            </h2>
            <p style={{ fontSize: '0.95rem', margin: '0 0 1rem', lineHeight: 1.5 }}>
              {t.branchRecoveryDesc}
            </p>
            <div style={{ background: '#fef3c7', padding: '0.75rem 1rem', borderRadius: '6px', marginBottom: '1.5rem', fontWeight: 500 }}>
              Lý do từ hệ thống: <em>{reason}</em>
            </div>
            <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
              <button
                type="button"
                className="ring-btn-primary"
                style={{ width: 'auto' }}
                onClick={() =>
                  navigate(
                    `/workshops/${encodeURIComponent(packageReference)}/design/configure?mode=EDIT&designId=${encodeURIComponent(
                      designId
                    )}`
                  )
                }
              >
                🛠️ {t.branchRecoveryAction}
              </button>
              <button
                type="button"
                className="ring-btn-secondary"
                onClick={() =>
                  navigate(`/workshops/booking?packageReference=${encodeURIComponent(packageReference)}&designId=${encodeURIComponent(designId)}`)
                }
              >
                📍 {t.changeBranchAction}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

