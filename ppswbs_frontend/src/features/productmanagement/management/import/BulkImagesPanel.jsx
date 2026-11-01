import React, { useEffect, useRef, useState } from 'react';
import { newCommandId } from '../../../../shared/api/http';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { useErrorText } from '../../../../shared/ui/components';
import { messages } from '../../messages';
import { LIMITS, matchProductForFile } from '../../rules';

const TYPES = ['image/jpeg', 'image/png', 'image/webp'];

/**
 * Images for the products an import has just created (the Excel file itself carries no images, A-003). Three ways to
 * assign them, freely mixed: pick files on a product's row; pick many files at once and choose the product under each
 * one; or let a file name that contains the group key or the product name be assigned on its own. Nothing is sent
 * until "Save all images": then, product by product, each file is uploaded (its own key, reused on a retry), the
 * product is read again for its current version and images, and its image list is replaced (PUT products/{id}/images)
 * — the same calls as the image section of the product page. A product that fails keeps its images for a retry; the
 * others are saved. Alt text defaults to the product name and stays editable.
 *
 * @param products [{ id, name, groupKey }] created by the import
 */
export function BulkImagesPanel({ api, products }) {
  const t = useT(messages);
  const errorText = useErrorText(messages);
  const [items, setItems] = useState([]);
  const [results, setResults] = useState({});
  const [saving, setSaving] = useState(false);
  const latest = useRef(items);
  latest.current = items;
  const previews = useRef(new Set());
  useEffect(() => () => previews.current.forEach(url => URL.revokeObjectURL(url)), []);

  const nameOf = id => products.find(product => product.id === id)?.name || '';
  const commit = next => { latest.current = next; setItems(next); };
  const update = (localId, patch) => commit(latest.current.map(item => (item.localId === localId ? { ...item, ...patch } : item)));

  const addFiles = (fileList, productId) => {
    const added = [...fileList].map(file => {
      const previewUrl = URL.createObjectURL(file);
      previews.current.add(previewUrl);
      const target = productId ?? matchProductForFile(file.name, products) ?? '';
      const hint = !TYPES.includes(file.type) ? 'image.typeHint' : file.size > LIMITS.mediaBytes ? 'image.sizeHint' : null;
      return { localId: newCommandId(), file, previewUrl, hint, productId: target, altText: target ? nameOf(target) : '', uploadKey: newCommandId(), mediaId: null };
    });
    commit([...latest.current, ...added]);
  };
  const pick = productId => event => { addFiles(event.target.files, productId); event.target.value = ''; };
  const assign = (item, productId) => update(item.localId, { productId, altText: item.altText && item.altText !== nameOf(item.productId) ? item.altText : nameOf(productId) });

  const imagesOf = id => items.filter(item => item.productId === id);
  const unassigned = items.filter(item => !item.productId);
  const problemOf = id => {
    const list = imagesOf(id);
    if (list.length > LIMITS.maxImages) return t('bulk.tooMany', { max: LIMITS.maxImages });
    if (list.some(item => item.hint)) return t('bulk.fixRefused');
    if (list.some(item => !item.altText.trim() || [...item.altText.trim()].length > LIMITS.altTextMax)) return t('bulk.altRequired', { max: LIMITS.altTextMax });
    return null;
  };
  const toSave = products.filter(product => imagesOf(product.id).length > 0 && results[product.id]?.state !== 'saved');
  const blocked = toSave.some(product => problemOf(product.id));

  const saveProduct = async product => {
    const list = latest.current.filter(item => item.productId === product.id);
    for (const item of list) {
      if (!item.mediaId) {
        const uploaded = await api.uploadMedia(item.uploadKey, item.file);
        update(item.localId, { mediaId: uploaded.mediaId });
        item.mediaId = uploaded.mediaId;
      }
    }
    const current = await api.getProduct(product.id);
    const existing = [...(current.images || [])].sort((a, b) => a.position - b.position);
    if (existing.length + list.length > LIMITS.maxImages) throw new Error(t('bulk.tooMany', { max: LIMITS.maxImages }));
    const images = [
      ...existing.map(image => ({ mediaId: image.mediaId, altText: image.altText })),
      ...list.map(item => ({ mediaId: item.mediaId, altText: item.altText.trim() })),
    ].map((image, position) => ({ ...image, position }));
    await api.replaceImages(newCommandId(), product.id, { expectedVersion: current.version, images });
  };

  const saveAll = async () => {
    setSaving(true);
    for (const product of toSave) {
      setResults(current => ({ ...current, [product.id]: { state: 'saving' } }));
      try {
        await saveProduct(product);
        setResults(current => ({ ...current, [product.id]: { state: 'saved' } }));
      } catch (error) {
        setResults(current => ({ ...current, [product.id]: { state: 'failed', error } }));
      }
    }
    setSaving(false);
  };

  const card = item => {
    const locked = results[item.productId]?.state === 'saved' || results[item.productId]?.state === 'saving';
    return (
      <li key={item.localId} className="bulk-image">
        <img src={item.previewUrl} alt="" />
        <span className="mono small">{item.file.name}</span>
        {item.hint && <span className="field-error">{t(item.hint)}</span>}
        <label>{t('bulk.assignTo')}
          <select value={item.productId} disabled={locked} onChange={event => assign(item, event.target.value)}>
            <option value="">{t('bulk.unassigned')}</option>
            {products.map(product => <option key={product.id} value={product.id}>{product.name} ({product.groupKey})</option>)}
          </select>
        </label>
        {item.productId && (
          <label>{t('bulk.altText')}
            <input value={item.altText} maxLength={LIMITS.altTextMax} disabled={locked} onChange={event => update(item.localId, { altText: event.target.value })} />
          </label>
        )}
        {!locked && <button type="button" className="text-button" onClick={() => commit(latest.current.filter(other => other.localId !== item.localId))}>{t('image.remove')}</button>}
      </li>
    );
  };

  return (
    <section className="bulk-images" aria-labelledby="bulk-images-title">
      <h3 id="bulk-images-title">{t('bulk.title')}</h3>
      <p className="muted small">{t('bulk.hint')}</p>
      <label className="button outline file-button">
        {t('bulk.pickMany')}
        <input type="file" accept={TYPES.join(',')} multiple onChange={pick(undefined)} />
      </label>

      <ul className="bulk-products">
        {products.map(product => {
          const result = results[product.id];
          const list = imagesOf(product.id);
          return (
            <li key={product.id}>
              <div className="bulk-product-head">
                <strong>{product.name}</strong> <span className="muted small">({product.groupKey}) · {t('bulk.count', { count: list.length, max: LIMITS.maxImages })}</span>
                {result?.state === 'saved' && <span className="status ok">{t('bulk.saved')}</span>}
                {result?.state === 'saving' && <span className="status">{t('bulk.saving')}</span>}
                {result?.state !== 'saved' && (
                  <label className="text-button file-button">
                    {t('bulk.pickFor')}
                    <input type="file" accept={TYPES.join(',')} multiple aria-label={t('bulk.pickForProduct', { name: product.name })} onChange={pick(product.id)} />
                  </label>
                )}
              </div>
              {list.length > 0 && <ul className="bulk-grid">{list.map(card)}</ul>}
              {list.length > 0 && result?.state !== 'saved' && problemOf(product.id) && <p className="field-error">{problemOf(product.id)}</p>}
              {result?.state === 'failed' && <p className="field-error" role="alert">{result.error?.code ? errorText(result.error) : result.error?.message}</p>}
            </li>
          );
        })}
      </ul>

      {unassigned.length > 0 && (
        <>
          <h4>{t('bulk.unassignedTitle', { count: unassigned.length })}</h4>
          <ul className="bulk-grid">{unassigned.map(card)}</ul>
        </>
      )}

      <button type="button" className="button primary" disabled={saving || toSave.length === 0 || blocked} onClick={saveAll}>
        {saving ? t('bulk.savingAll') : t('bulk.saveAll', { count: toSave.length })}
      </button>
    </section>
  );
}
