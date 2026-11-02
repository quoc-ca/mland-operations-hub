import React, { useState } from 'react';
import { useNavigate } from 'react-router';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field, useCommand, useResource } from '../../../shared/ui/components';
import { useCatalogueApi } from '../api/useCatalogueApi';
import { messages } from '../messages';
import { checkImages, checkProductContent, checkVariants, LIMITS, serverFieldErrors } from '../rules';
import { ImageEditor, imagesPayload, imagesPending } from './ImageEditor';
import { PageTop } from './ManagementLayout';

const emptyVariant = () => ({ size: '', proposedPrice: '', quantity: '0' });

/**
 * T028 create: sizes are chosen only here (the size list is immutable afterwards). The proposed price is not a
 * selling price; it goes to the review. Errors keep everything typed; success opens the new product.
 */
export default function ProductCreatePage() {
  const t = useT(messages);
  const api = useCatalogueApi();
  const navigate = useNavigate();
  const categories = useResource(() => api.listCategories(), []);
  const [form, setForm] = useState({ name: '', description: '', categoryId: '' });
  const [variants, setVariants] = useState([emptyVariant()]);
  const [images, setImages] = useState([]);
  const [hints, setHints] = useState({});
  const command = useCommand();

  const serverErrors = serverFieldErrors(command.error);
  const errorOf = field => serverErrors[field] || (hints[field] && t(hints[field]));
  const set = name => event => setForm(current => ({ ...current, [name]: event.target.value }));
  const setVariant = (index, name) => event => setVariants(current => current.map((variant, i) => (i === index ? { ...variant, [name]: event.target.value } : variant)));

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
    const outcome = await command.run(key => api.createProduct(key, body));
    if (outcome.ok) navigate(`/catalogue-management/products/${encodeURIComponent(outcome.result.resourceId)}`, { state: { notice: 'create.success' } });
  };

  const activeCategories = (categories.data?.items || []).filter(category => category.active);

  return (
    <>
      <PageTop eyebrow={t('nav.group')} title={t('create.title')} />
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
        <div className="variant-rows">
          {variants.map((variant, index) => (
            <div className="variant-row" key={index}>
              <Field label={t('field.size')} error={errorOf(`variants[${index}].size`)}><input inputMode="numeric" value={variant.size} onChange={setVariant(index, 'size')} /></Field>
              <Field label={t('field.proposedPrice')} error={errorOf(`variants[${index}].proposedPrice`)}><input inputMode="numeric" value={variant.proposedPrice} onChange={setVariant(index, 'proposedPrice')} /></Field>
              <Field label={t('field.quantity')} error={errorOf(`variants[${index}].quantity`)}><input inputMode="numeric" value={variant.quantity} onChange={setVariant(index, 'quantity')} /></Field>
              <button type="button" className="button outline small" disabled={variants.length === 1}
                onClick={() => setVariants(current => current.filter((_, i) => i !== index))}>{t('create.removeSize')}</button>
            </div>
          ))}
        </div>
        <button type="button" className="button outline" disabled={variants.length >= LIMITS.maxSizes} onClick={() => setVariants(current => [...current, emptyVariant()])}>{t('create.addSize')}</button>

        <h2 className="section-title">{t('section.images')}</h2>
        <ImageEditor images={images} onChange={setImages} errors={{ ...Object.fromEntries(Object.keys(hints).filter(key => key.startsWith('images')).map(key => [key, t(hints[key])])), ...Object.fromEntries(Object.entries(serverErrors).filter(([key]) => key.startsWith('images'))) }} disabled={command.pending} />

        <ErrorNotice error={command.error} messages={messages} onRetry={async () => {
          const outcome = await command.retry();
          if (outcome?.ok) navigate(`/catalogue-management/products/${encodeURIComponent(outcome.result.resourceId)}`, { state: { notice: 'create.success' } });
        }} />
        <div className="actions form-actions">
          <button type="submit" className="button primary" disabled={command.pending}>{command.pending ? t('saving') : t('create.submit')}</button>
          <button type="button" className="button outline" onClick={() => navigate('/catalogue-management/products')}>{t('cancel')}</button>
        </div>
      </form>
    </>
  );
}
