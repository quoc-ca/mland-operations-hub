import React, { useEffect, useRef, useState } from 'react';
import { newCommandId } from '../../shared/api/http';
import { useT } from '../../shared/i18n/I18nProvider';
import { useErrorText } from '../../shared/ui/components';
import { messages } from '../messages';

export const PACKAGE_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
export const PACKAGE_IMAGE_MAX_BYTES = 5_000_000;

/**
 * 003.2 FR-073: choose, preview and remove a package's cover image. A chosen file is uploaded at once; the form only
 * receives the media ID after the upload succeeded, so a failed upload keeps the previous image.
 */
export function PackageImageField({ api, savedPackage, value, onChange, onBusy, disabled }) {
  const t = useT(messages);
  const errorText = useErrorText(messages);
  const [upload, setUpload] = useState(null); // { file, key, pending, hint, error }
  const [local, setLocal] = useState(null); // { mediaId, url } of the file uploaded here
  const [saved, setSaved] = useState(null); // { mediaId, url } read from the server
  const alive = useRef(true);
  const urls = useRef([]);
  const apiRef = useRef(api);
  apiRef.current = api;

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
      urls.current.forEach(url => URL.revokeObjectURL(url));
      urls.current = [];
    };
  }, []);

  const keep = blob => {
    const url = URL.createObjectURL(blob);
    urls.current.push(url);
    return url;
  };

  // The stored image needs the Manager's bearer, so it is fetched and shown from a blob URL.
  const savedId = savedPackage?.imageMediaId || null;
  const needsSaved = Boolean(value) && value === savedId && local?.mediaId !== value;
  useEffect(() => {
    if (!needsSaved) return undefined;
    let active = true;
    apiRef.current.previewImage(savedPackage.packageId)
      .then(result => { if (active && alive.current && result?.blob) setSaved({ mediaId: savedId, url: keep(result.blob) }); })
      .catch(() => {});
    return () => { active = false; };
  }, [needsSaved, savedPackage?.packageId, savedId]);

  const send = async (file, key) => {
    setUpload({ file, key, pending: true });
    onBusy(true);
    try {
      const result = await apiRef.current.uploadImage(file, key);
      if (!alive.current) return;
      setLocal({ mediaId: result.mediaId, url: keep(file) });
      onChange(result.mediaId);
      setUpload(null);
    } catch (error) {
      if (alive.current) setUpload({ file, key, pending: false, error });
    } finally {
      if (alive.current) onBusy(false);
    }
  };

  const choose = event => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    const hint = !PACKAGE_IMAGE_TYPES.includes(file.type) ? 'pkg.image.type'
      : file.size > PACKAGE_IMAGE_MAX_BYTES ? 'pkg.image.size' : null;
    if (hint) { setUpload({ hint }); return; }
    send(file, newCommandId());
  };

  const preview = !value ? null : local?.mediaId === value ? local.url : saved?.mediaId === value ? saved.url : null;
  const busy = Boolean(upload?.pending);

  return (
    <div className="field package-image-field">
      <label htmlFor="package-image-input">{t('pkg.image.label')}</label>
      {preview && <img className="package-image-preview" src={preview} alt={t('pkg.image.previewAlt')} />}
      <input id="package-image-input" type="file" accept={PACKAGE_IMAGE_TYPES.join(',')} onChange={choose} disabled={disabled || busy} />
      <span className="muted small">{t('pkg.image.hint')}</span>
      {busy && <span className="muted small" role="status">{t('pkg.image.uploading')}</span>}
      {upload?.hint && <span className="field-error" role="alert">{t(upload.hint)}</span>}
      {upload?.error && <span className="field-error" role="alert">{t('pkg.image.failed')} {errorText(upload.error)}</span>}
      <div className="ops-actions">
        {upload?.error?.retryable && <button type="button" className="button outline small" onClick={() => send(upload.file, upload.key)} disabled={disabled}>{t('pkg.image.retry')}</button>}
        {value && <button type="button" className="button outline small" onClick={() => { setUpload(null); onChange(null); }} disabled={disabled || busy}>{t('pkg.image.remove')}</button>}
      </div>
    </div>
  );
}
