import React, { useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field, Loading, useCommand } from '../../../shared/ui/components';
import { useDesignReviewApi } from '../api/useDesignReviewApi';
import { designReviewMessages } from '../messages';
import {
  ALLOWED_IMAGE_TYPES,
  CONSENT_PURPOSE,
  CONSENT_VERSION,
  LIMITS,
  validateImageFile,
} from '../rules';

export default function DesignWorkspacePage() {
  const t = useT(designReviewMessages);
  const navigate = useNavigate();
  const api = useDesignReviewApi();
  const command = useCommand();
  const fileInputRef = useRef(null);

  const [description, setDescription] = useState('');
  const [catalogueReference, setCatalogueReference] = useState('');
  const [imageFile, setImageFile] = useState(null);
  const [consentAgreed, setConsentAgreed] = useState(false);
  const [errors, setErrors] = useState({});

  const charCount = [...description].length;

  function handleFileChange(e) {
    const file = e.target.files?.[0];
    if (!file) return;

    const fileError = validateImageFile(file);
    if (fileError) {
      setErrors(prev => ({ ...prev, image: t(fileError) }));
      setImageFile(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }

    setErrors(prev => {
      const copy = { ...prev };
      delete copy.image;
      delete copy.consent;
      return copy;
    });
    setImageFile(file);
    setConsentAgreed(false);
  }

  function handleRemoveFile() {
    setImageFile(null);
    setConsentAgreed(false);
    if (fileInputRef.current) fileInputRef.current.value = '';
    setErrors(prev => {
      const copy = { ...prev };
      delete copy.image;
      delete copy.consent;
      return copy;
    });
  }

  async function handleSubmit(e) {
    e.preventDefault();
    const newErrors = {};

    if (!description.trim() && !imageFile) {
      newErrors.form = t('validation.contentRequired');
    }
    if (description && [...description].length > LIMITS.descriptionMax) {
      newErrors.description = t('validation.descriptionTooLong');
    }
    if (imageFile && !consentAgreed) {
      newErrors.consent = t('validation.consentRequired');
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }
    setErrors({});

    const res = await command.run(key =>
      api.submitDesignRequest(key, {
        description: description.trim(),
        catalogueReference: catalogueReference.trim(),
        consentVersion: imageFile ? CONSENT_VERSION : null,
        consentPurpose: imageFile ? CONSENT_PURPOSE : null,
        designVersion: 1,
        image: imageFile,
      })
    );

    if (res.ok && res.result?.id) {
      navigate(`/design-requests/${encodeURIComponent(res.result.id)}`);
    }
  }

  return (
    <div className="design-container">
      <header className="page-header">
        <h1>{t('workspace.title')}</h1>
        <p className="muted">{t('workspace.subtitle')}</p>
      </header>

      <div className="boundary-banner" role="status">
        <strong>{t('workspace.boundaryNotice')}</strong>
      </div>

      <ErrorNotice
        error={command.error}
        messages={designReviewMessages}
        onRetry={command.retry}
      />
      {errors.form && <div className="notice error">{errors.form}</div>}

      <form onSubmit={handleSubmit} className="stack">
        <Field
          label={t('workspace.descriptionLabel')}
          hint={t('workspace.charCount', { count: charCount })}
          error={errors.description || command.error?.fieldError?.('description')}
        >
          <textarea
            value={description}
            onChange={e => {
              setDescription(e.target.value);
              if (errors.description) setErrors(prev => ({ ...prev, description: null }));
            }}
            placeholder={t('workspace.descriptionPlaceholder')}
            rows={5}
            disabled={command.pending}
          />
        </Field>

        <Field
          label={t('workspace.catalogueRefLabel')}
          error={errors.catalogueReference || command.error?.fieldError?.('catalogueReference')}
        >
          <input
            type="text"
            value={catalogueReference}
            onChange={e => setCatalogueReference(e.target.value)}
            placeholder={t('workspace.catalogueRefPlaceholder')}
            disabled={command.pending}
          />
        </Field>

        <div className="field">
          <label htmlFor="design-image-upload">{t('workspace.imageLabel')}</label>
          <input
            id="design-image-upload"
            ref={fileInputRef}
            type="file"
            accept={ALLOWED_IMAGE_TYPES.join(',')}
            onChange={handleFileChange}
            disabled={command.pending}
          />
          <span className="muted small">{t('workspace.imageHint')}</span>
          {errors.image && <span className="field-error">{errors.image}</span>}
        </div>

        {imageFile && (
          <div className="image-preview-box">
            <span>
              {t('workspace.fileSelected', {
                name: imageFile.name,
                size: `${(imageFile.size / 1024 / 1024).toFixed(2)} MB`,
              })}
            </span>
            <button
              type="button"
              className="button outline small"
              onClick={handleRemoveFile}
              disabled={command.pending}
            >
              {t('workspace.removeImage')}
            </button>
          </div>
        )}

        {imageFile && (
          <div className="consent-card" role="region" aria-label={t('consent.title')}>
            <h3>{t('consent.title')}</h3>
            <p className="small muted">{t('consent.body')}</p>
            <label className="consent-checkbox">
              <input
                type="checkbox"
                checked={consentAgreed}
                onChange={e => {
                  setConsentAgreed(e.target.checked);
                  if (errors.consent) setErrors(prev => ({ ...prev, consent: null }));
                }}
                disabled={command.pending}
              />
              <span>
                {t('consent.checkbox', {
                  purpose: CONSENT_PURPOSE,
                  version: CONSENT_VERSION,
                })}
              </span>
            </label>
            {errors.consent && <div className="field-error">{errors.consent}</div>}
          </div>
        )}

        <div className="actions" style={{ marginTop: '1.5rem' }}>
          <button
            type="submit"
            className="button primary"
            disabled={command.pending || (imageFile && !consentAgreed)}
          >
            {command.pending ? t('common.loading', undefined, 'Đang gửi…') : t('workspace.submit')}
          </button>
        </div>
      </form>
    </div>
  );
}

