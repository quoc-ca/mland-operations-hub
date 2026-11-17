import React from 'react';
import { useParams, useNavigate } from 'react-router';
import { messages } from '../messages';
import '../ring-design.css';

export default function RingDesignEntryPage() {
  const { packageReference } = useParams();
  const navigate = useNavigate();
  const lang = 'vi';
  const t = messages[lang];

  return (
    <div className="ring-design-container" data-testid="ring-design-entry-page">
      <div className="ring-design-header">
        <h1 className="ring-design-title">{t.featureTitle}</h1>
        <p className="ring-design-subtitle">{t.featureSubtitle}</p>
        <div className="ring-notice-banner" role="note">
          <span>ℹ️</span>
          <span>{t.workshopOnlyNotice}</span>
        </div>
      </div>

      <div style={{ maxWidth: '860px', margin: '2rem auto 0' }}>
        <h2 style={{ fontSize: '1.4rem', fontWeight: 600, marginBottom: '1.5rem', textAlign: 'center' }}>
          {t.chooseStartingPoint}
        </h2>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
          {/* Card 1: Choose from models */}
          <div
            className="ring-section-card"
            style={{
              cursor: 'pointer',
              border: '2px solid #e2e8f0',
              transition: 'all 0.2s ease',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'space-between',
            }}
            onClick={() => navigate(`/workshops/${encodeURIComponent(packageReference)}/design/models`)}
          >
            <div>
              <div style={{ fontSize: '2.5rem', marginBottom: '1rem' }}>💍</div>
              <h3 style={{ fontSize: '1.2rem', fontWeight: 600, color: '#0f172a', margin: '0 0 0.5rem' }}>
                {t.modelChoiceTitle}
              </h3>
              <p style={{ fontSize: '0.9rem', color: '#64748b', lineHeight: 1.5, margin: 0 }}>
                {t.modelChoiceDesc}
              </p>
            </div>
            <button
              type="button"
              className="ring-btn-primary"
              style={{ marginTop: '1.5rem' }}
              onClick={(e) => {
                e.stopPropagation();
                navigate(`/workshops/${encodeURIComponent(packageReference)}/design/models`);
              }}
            >
              {t.selectModel}
            </button>
          </div>

          {/* Card 2: Blank design */}
          <div
            className="ring-section-card"
            style={{
              cursor: 'pointer',
              border: '2px solid #e2e8f0',
              transition: 'all 0.2s ease',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'space-between',
            }}
            onClick={() => navigate(`/workshops/${encodeURIComponent(packageReference)}/design/configure?mode=BLANK`)}
          >
            <div>
              <div style={{ fontSize: '2.5rem', marginBottom: '1rem' }}>✨</div>
              <h3 style={{ fontSize: '1.2rem', fontWeight: 600, color: '#0f172a', margin: '0 0 0.5rem' }}>
                {t.blankChoiceTitle}
              </h3>
              <p style={{ fontSize: '0.9rem', color: '#64748b', lineHeight: 1.5, margin: 0 }}>
                {t.blankChoiceDesc}
              </p>
            </div>
            <button
              type="button"
              className="ring-btn-secondary"
              style={{ marginTop: '1.5rem', width: '100%' }}
              onClick={(e) => {
                e.stopPropagation();
                navigate(`/workshops/${encodeURIComponent(packageReference)}/design/configure?mode=BLANK`);
              }}
            >
              {t.startConfiguring}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

