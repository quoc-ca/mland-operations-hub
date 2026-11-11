import React from 'react';
import { useParams, useNavigate } from 'react-router';
import { useWorkshopDesignOptions } from '../application/useWorkshopDesignOptions';
import { messages } from '../messages';
import '../ring-design.css';

export default function RingModelSelectionPage() {
  const { packageReference } = useParams();
  const navigate = useNavigate();
  const { options, loading, error } = useWorkshopDesignOptions(packageReference);
  const lang = 'vi';
  const t = messages[lang];

  const models = options?.models || [];

  return (
    <div className="ring-design-container" data-testid="ring-model-selection-page">
      <div className="ring-design-header">
        <h1 className="ring-design-title">{t.modelChoiceTitle}</h1>
        <p className="ring-design-subtitle">{t.modelChoiceDesc}</p>
        <button
          type="button"
          className="ring-btn-secondary"
          onClick={() => navigate(`/workshops/${encodeURIComponent(packageReference)}/design`)}
        >
          ← {t.backToModels}
        </button>
      </div>

      {loading && (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#64748b' }}>
          Đang tải danh sách mẫu thiết kế...
        </div>
      )}

      {error && (
        <div className="ring-error-card" role="alert">
          {error.message || 'Không thể tải danh sách mẫu thiết kế.'}
        </div>
      )}

      {!loading && !error && models.length === 0 && (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#64748b' }}>
          Gói workshop này chưa có mẫu gợi ý sẵn. Bạn có thể bắt đầu với phôi trắng.
          <div style={{ marginTop: '1rem' }}>
            <button
              type="button"
              className="ring-btn-primary"
              style={{ width: 'auto' }}
              onClick={() => navigate(`/workshops/${encodeURIComponent(packageReference)}/design/configure?mode=BLANK`)}
            >
              {t.blankChoiceTitle}
            </button>
          </div>
        </div>
      )}

      <div className="ring-cards-grid" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))' }}>
        {models.map((model) => (
          <div
            key={model.modelReference}
            className="ring-section-card"
            style={{
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'space-between',
              cursor: 'pointer',
              border: '2px solid #e2e8f0',
            }}
            onClick={() => {
              navigate(
                `/workshops/${encodeURIComponent(packageReference)}/design/configure?mode=MODEL&modelRef=${encodeURIComponent(
                  model.modelReference
                )}`
              );
            }}
          >
            <div>
              {model.imageUrl && (
                <div
                  style={{
                    width: '100%',
                    height: '180px',
                    borderRadius: '8px',
                    overflow: 'hidden',
                    marginBottom: '1rem',
                    background: '#f1f5f9',
                  }}
                >
                  <img
                    src={model.imageUrl}
                    alt={model.name}
                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                  />
                </div>
              )}
              <h3 style={{ fontSize: '1.15rem', fontWeight: 600, color: '#0f172a', margin: '0 0 0.4rem' }}>
                {model.name}
              </h3>
              <p style={{ fontSize: '0.85rem', color: '#64748b', lineHeight: 1.4, margin: '0 0 0.5rem' }}>
                {model.description}
              </p>
              {model.difficultyScore !== undefined && (
                <div style={{ fontSize: '0.8rem', color: '#64748b', marginBottom: '0.5rem' }}>
                  Độ khó ước tính: {model.difficultyScore} / 10
                </div>
              )}
            </div>
            <button
              type="button"
              className="ring-btn-primary"
              onClick={(e) => {
                e.stopPropagation();
                navigate(
                  `/workshops/${encodeURIComponent(packageReference)}/design/configure?mode=MODEL&modelRef=${encodeURIComponent(
                    model.modelReference
                  )}`
                );
              }}
            >
              {t.selectModel}
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
