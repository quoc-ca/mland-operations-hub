import React, { useState } from 'react';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice } from '../../../../shared/ui/components';
import { messages } from '../../messages';
import { checkImages, serverFieldErrors } from '../../rules';
import { fromProductImages, ImageEditor, imagesPayload, imagesPending } from '../ImageEditor';
import { useProductCommand } from '../ProductDetailPage';

/** T050 / FR images: replaces the whole ordered list (PUT products/{id}/images) after uploads are READY. */
export function ImagesSection({ product, api, reload, setNotice, deleted }) {
  const t = useT(messages);
  const [images, setImages] = useState(null);
  const [hints, setHints] = useState({});
  const command = useProductCommand({ reload, setNotice });
  const editing = images !== null;

  const save = async () => {
    const found = checkImages(images);
    if (imagesPending(images)) found.images = 'image.notReady';
    setHints(found);
    if (Object.keys(found).length) return;
    const outcome = await command.run(key => api.replaceImages(key, product.id, { expectedVersion: product.version, images: imagesPayload(images) }), 'images.saved');
    if (outcome.ok) setImages(null);
  };
  const cancel = () => { setImages(null); setHints({}); command.reset(); };

  const errors = {
    ...Object.fromEntries(Object.entries(hints).map(([key, value]) => [key, t(value)])),
    ...serverFieldErrors(command.error),
  };

  return (
    <section className="workspace-card" aria-labelledby="images-title">
      <div className="card-head">
        <h2 id="images-title">{t('section.images')}</h2>
        {!editing && !deleted && <button type="button" className="button outline" onClick={() => setImages(fromProductImages(product.images))}>{t('edit')}</button>}
      </div>
      {!editing ? (
        product.images?.length
          ? <ol className="image-names">{[...product.images].sort((a, b) => a.position - b.position).map(image => <li key={image.mediaId}>{image.altText}</li>)}</ol>
          : <p className="muted">{t('image.none')}</p>
      ) : (
        <>
          <ImageEditor images={images} onChange={setImages} errors={errors} disabled={command.pending} />
          <ErrorNotice error={command.error} messages={messages} onRetry={command.retry} onReload={reload} />
          <div className="actions form-actions">
            <button type="button" className="button primary" onClick={save} disabled={command.pending}>{command.pending ? t('saving') : t('save')}</button>
            <button type="button" className="button outline" onClick={cancel} disabled={command.pending}>{t('cancel')}</button>
          </div>
        </>
      )}
      <p className="muted small">{t('images.previewHint')}</p>
    </section>
  );
}
