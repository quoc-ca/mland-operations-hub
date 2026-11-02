import React, { useEffect, useRef } from 'react';
import { newCommandId } from '../../../shared/api/http';
import { useT } from '../../../shared/i18n/I18nProvider';
import { useErrorText } from '../../../shared/ui/components';
import { useCatalogueApi } from '../api/useCatalogueApi';
import { messages } from '../messages';
import { LIMITS } from '../rules';

const TYPES = ['image/jpeg', 'image/png', 'image/webp'];

/**
 * Ordered image list for create and replace. Each new file is uploaded on its own (POST media-uploads, one file,
 * its own key) and attached by mediaId only; a failed upload is retried with the same key. Draft images are not
 * served by the backend, so existing ones are listed by alt text; a new file shows a local preview.
 * Item: { localId, mediaId, altText, state: 'ready'|'uploading'|'failed', error, file, previewUrl, commandId }.
 */
export function ImageEditor({ images, onChange, errors = {}, disabled }) {
  const t = useT(messages);
  const errorText = useErrorText(messages);
  const api = useCatalogueApi();
  const latest = useRef(images);
  latest.current = images;
  const inputRef = useRef(null);

  // Local previews are released when the editor closes.
  const previews = useRef(new Set());
  useEffect(() => () => previews.current.forEach(url => URL.revokeObjectURL(url)), []);

  // Every change goes through commit so a later change in the same tick (an upload result right after adding the file)
  // builds on it instead of on the list of the last render.
  const commit = next => { latest.current = next; onChange(next); };
  const update = (localId, patch) => commit(latest.current.map(item => (item.localId === localId ? { ...item, ...patch } : item)));

  const upload = async item => {
    update(item.localId, { state: 'uploading', error: null });
    try {
      const result = await api.uploadMedia(item.commandId, item.file);
      update(item.localId, { state: 'ready', mediaId: result.mediaId });
    } catch (error) {
      update(item.localId, { state: 'failed', error });
    }
  };

  const addFiles = event => {
    const files = [...event.target.files];
    event.target.value = '';
    const room = LIMITS.maxImages - latest.current.length;
    const added = files.slice(0, Math.max(0, room)).map(file => {
      const hint = !TYPES.includes(file.type) ? 'image.typeHint' : file.size > LIMITS.mediaBytes ? 'image.sizeHint' : null;
      const previewUrl = URL.createObjectURL(file);
      previews.current.add(previewUrl);
      return { localId: newCommandId(), mediaId: null, altText: '', state: hint ? 'rejected' : 'uploading', hint, file, previewUrl, commandId: newCommandId() };
    });
    commit([...latest.current, ...added]);
    added.filter(item => !item.hint).forEach(upload);
  };

  const move = (index, delta) => {
    const next = [...latest.current];
    const [item] = next.splice(index, 1);
    next.splice(index + delta, 0, item);
    commit(next);
  };
  const remove = localId => commit(latest.current.filter(item => item.localId !== localId));

  return (
    <div className="image-editor">
      {errors.images && <p className="field-error">{errors.images}</p>}
      <ol className="image-list">
        {images.map((item, index) => (
          <li key={item.localId} className="image-item">
            {item.previewUrl ? <img src={item.previewUrl} alt="" /> : <div className="image-placeholder" aria-hidden="true">#{index + 1}</div>}
            <div className="image-fields">
              <label>
                {t('image.altText', { position: index + 1 })}
                <input value={item.altText} maxLength={LIMITS.altTextMax} disabled={disabled}
                  aria-invalid={errors[`images[${index}].altText`] ? true : undefined}
                  onChange={event => update(item.localId, { altText: event.target.value })} />
              </label>
              {errors[`images[${index}].altText`] && <span className="field-error">{errors[`images[${index}].altText`]}</span>}
              {errors[`images[${index}].mediaId`] && <span className="field-error">{errors[`images[${index}].mediaId`]}</span>}
              <span className="muted small" role="status">
                {item.state === 'uploading' && t('image.uploading')}
                {item.state === 'ready' && item.file && t('image.uploaded')}
                {item.state === 'rejected' && t(item.hint)}
              </span>
              {item.state === 'failed' && (
                <span className="field-error">{errorText(item.error)}{' '}
                  <button type="button" className="button outline small" onClick={() => upload(item)}>{t('retry')}</button>
                </span>
              )}
            </div>
            <div className="image-actions">
              <button type="button" className="button outline small" disabled={disabled || index === 0} onClick={() => move(index, -1)} aria-label={t('image.moveUp', { position: index + 1 })}>↑</button>
              <button type="button" className="button outline small" disabled={disabled || index === images.length - 1} onClick={() => move(index, 1)} aria-label={t('image.moveDown', { position: index + 1 })}>↓</button>
              <button type="button" className="button outline small" disabled={disabled} onClick={() => remove(item.localId)}>{t('image.remove')}</button>
            </div>
          </li>
        ))}
      </ol>
      {images.length === 0 && <p className="muted">{t('image.none')}</p>}
      <input ref={inputRef} type="file" accept={TYPES.join(',')} multiple hidden onChange={addFiles} />
      <button type="button" className="button outline" disabled={disabled || images.length >= LIMITS.maxImages} onClick={() => inputRef.current?.click()}>
        {t('image.add', { count: images.length, max: LIMITS.maxImages })}
      </button>
    </div>
  );
}

/** Images ready to send: every item uploaded, positions follow the list order. */
export function imagesPayload(images) {
  return images.map((item, index) => ({ mediaId: item.mediaId, altText: item.altText.trim(), position: index }));
}

export function imagesPending(images) {
  return images.some(item => item.state !== 'ready');
}

export function fromProductImages(images) {
  return [...(images || [])].sort((a, b) => a.position - b.position)
    .map(image => ({ localId: image.mediaId, mediaId: image.mediaId, altText: image.altText, state: 'ready' }));
}
