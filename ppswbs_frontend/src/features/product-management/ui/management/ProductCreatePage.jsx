import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { newCommandId } from '../../../../shared/api/http';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field, useResource } from '../../../../shared/ui/components';
import { useCatalogueApi } from '../../application/useCatalogueApi';
import { ACTIONS, useCatalogueCommand } from '../../application/useCatalogueCommand';
import { messages } from '../messages';
import { checkImages, checkProductContent, checkVariants, LIMITS, serverFieldErrors } from '../../domain/productRules';
import { ImageEditor, imagesPayload, imagesPending } from './ImageEditor';
import { PageTop } from './ManagementLayout';
import { SizePicker } from './SizePicker';

/**
 * T028 create: sizes are chosen only here (the size list is immutable afterwards), as chips 5–29 with one price and
 * quantity applicable to all of them. The proposed price is not a selling price; it goes to the review. Errors keep everything typed; success opens the new product.
 * FR-028: the key of a create whose answer is unknown is kept in sessionStorage until the outcome is known, so after a
 * reload the page asks for its receipt and links the product that was created instead of letting it be created twice.
 */
const PENDING = 'pm.pendingCreate';
const pending = {
  read: () => { try { return JSON.parse(sessionStorage.getItem(PENDING) || 'null'); } catch { return null; } },
  keep: key => { try { sessionStorage.setItem(PENDING, JSON.stringify({ key })); } catch { /* no storage: retry still works */ } },
  forget: () => { try { sessionStorage.removeItem(PENDING); } catch { /* nothing kept */ } },
};

export default function ProductCreatePage() {
  const t = useT(messages);
  const api = useCatalogueApi();
  const navigate = useNavigate();
  const categories = useResource(() => api.listCategories(), []);
  const [form, setForm] = useState({ name: '', description: '', categoryId: '' });
  const [variants, setVariants] = useState([]);
  const [images, setImages] = useState([]);
  const [hints, setHints] = useState({});
  const command = useCatalogueCommand();
  const [recovery, setRecovery] = useState(null);

  const checkPending = async () => {
    const kept = pending.read();
    if (!kept?.key) return;
    try {
      const receipt = await api.getCommandReceipt(kept.key, ACTIONS.createProduct);
      pending.forget();
      setRecovery({ productId: receipt.result.resourceId });
    } catch (error) {
      if (error?.code === 'COMMAND_RESULT_NOT_FOUND') { pending.forget(); setRecovery(null); } else setRecovery({ error });
    }
  };
  useEffect(() => { checkPending(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Known outcome (saved, or refused for good) ends the pending create; a lost answer or 503 keeps it for a retry.
  const finish = outcome => {
    if (!outcome) return;
    if (outcome.ok) {
      pending.forget();
      navigate(`/catalogue-management/products/${encodeURIComponent(outcome.result.resourceId)}`, { state: { notice: { key: 'create.success', params: { name: form.name.trim() } } } });
    } else if (!outcome.error.retryable) pending.forget();
  };

  const serverErrors = serverFieldErrors(command.error);
  const errorOf = field => serverErrors[field] || (hints[field] && t(hints[field]));
  const set = name => event => setForm(current => ({ ...current, [name]: event.target.value }));

  const submit = async event => {
    event.preventDefault();
    const found = { ...checkProductContent(form), ...checkVariants(variants), ...checkImages(images) };
    if (imagesPending(images)) found.images = 'image.notReady';
    setHints(found);
    if (Object.keys(found).length) return;
    const body = {
      name: form.name.trim(),
      description: form.description.trim(),
      categoryId: form.categoryId,
      variants: variants.map(variant => ({ size: Number(variant.size), proposedPrice: variant.proposedPrice, quantity: variant.quantity })),
      images: imagesPayload(images),
    };
    const key = newCommandId();
    pending.keep(key);
    finish(await command.run(sent => api.createProduct(sent, body), ACTIONS.createProduct, key));
  };

  const activeCategories = (categories.data?.items || []).filter(category => category.active);

  return (
    <>
      <PageTop eyebrow={t('nav.group')} title={t('create.title')} />
      {recovery?.productId && (
        <p className="notice success" role="status">{t('create.recovered')}{' '}
          <Link className="text-link" to={`/catalogue-management/products/${encodeURIComponent(recovery.productId)}`}>{t('create.openRecovered')}</Link>
        </p>
      )}
      {recovery?.error && <ErrorNotice error={recovery.error} messages={messages} onRetry={checkPending} />}
      <form className="workspace-card" onSubmit={submit} noValidate>
        <h2>{t('section.content')}</h2>
        <div className="form-grid">
          <Field label={t('field.name')} error={errorOf('name')}><input value={form.name} onChange={set('name')} maxLength={LIMITS.nameMax} /></Field>
          <Field label={t('field.category')} error={errorOf('categoryId')}>
            <select value={form.categoryId} onChange={set('categoryId')}>
              <option value="">{t('field.chooseCategory')}</option>
              {activeCategories.map(category => <option key={category.id} value={category.id}>{category.name}</option>)}
            </select>
          </Field>
        </div>
        {categories.error && <ErrorNotice error={categories.error} messages={messages} onRetry={categories.reload} />}
        <Field label={t('field.description')} error={errorOf('description')}><textarea value={form.description} onChange={set('description')} maxLength={LIMITS.descriptionMax} /></Field>

        <h2 className="section-title">{t('create.sizes')}</h2>
        <p className="muted small">{t('create.sizesHint')}</p>
        {errorOf('variants') && <p className="field-error">{errorOf('variants')}</p>}
        <SizePicker variants={variants} onChange={update => { setVariants(update); setHints(current => ({ ...current, variants: undefined })); }} errorOf={errorOf} disabled={command.pending} />

        <h2 className="section-title">{t('section.images')}</h2>
        <ImageEditor images={images} onChange={setImages} errors={{ ...Object.fromEntries(Object.keys(hints).filter(key => key.startsWith('images')).map(key => [key, t(hints[key])])), ...Object.fromEntries(Object.entries(serverErrors).filter(([key]) => key.startsWith('images'))) }} disabled={command.pending} />

        <ErrorNotice error={command.error} messages={messages} onRetry={async () => finish(await command.retry())} />
        <div className="actions form-actions">
          <button type="submit" className="button primary" disabled={command.pending}>{command.pending ? t('saving') : t('create.submit')}</button>
          <button type="button" className="button outline" onClick={() => navigate('/catalogue-management/products')}>{t('cancel')}</button>
        </div>
      </form>
    </>
  );
}
