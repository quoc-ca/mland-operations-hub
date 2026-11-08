import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { newCommandId } from '../../../../shared/api/http';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field, useResource } from '../../../../shared/ui/components';
import { useCatalogueApi } from '../../application/useCatalogueApi';
import { ACTIONS, useCatalogueCommand } from '../../application/useCatalogueCommand';
import { applyWithoutReview } from '../../application/publishNow';
import { useRole } from '../../../../shared/auth/useRole';
import { messages } from '../messages';
import { branchesPayload, checkBranchQuantities, checkImages, checkProductContent, checkVariants, LIMITS, serverFieldErrors } from '../../domain/productRules';
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
  // US10: branches this account may sell at (Manager: every active branch; Staff: assigned ones — the API filters).
  const branchList = useResource(() => api.listBranches(), []);
  const availableBranches = (Array.isArray(branchList.data) ? branchList.data : branchList.data?.items || [])
    .filter(branch => branch.active !== false);
  const [branchIds, setBranchIds] = useState([]);
  const onlyBranch = availableBranches.length === 1 ? String(availableBranches[0].id) : null;
  useEffect(() => { if (onlyBranch) setBranchIds(current => (current.length ? current : [onlyBranch])); }, [onlyBranch]);
  const chosenBranches = availableBranches.filter(branch => branchIds.includes(String(branch.id)));
  const toggleBranch = id => setBranchIds(current => (current.includes(id) ? current.filter(value => value !== id) : [...current, id]));
  const [form, setForm] = useState({ name: '', description: '', categoryId: '' });
  const [variants, setVariants] = useState([]);
  const [images, setImages] = useState([]);
  const [hints, setHints] = useState({});
  const command = useCatalogueCommand();
  const [recovery, setRecovery] = useState(null);
  // A Manager needs no review round: the product is applied as soon as it is created, and published if chosen.
  const isManager = useRole() === 'MANAGER';
  const [publishNow, setPublishNow] = useState(false);
  const [applying, setApplying] = useState(false);
  const busy = command.pending || applying;

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
  const finish = async outcome => {
    if (!outcome) return;
    if (outcome.ok) {
      pending.forget();
      const id = outcome.result.resourceId;
      const params = { name: form.name.trim() };
      let notice = { key: 'create.success', params };
      if (isManager) {
        // Each step is its own command; if one fails the product is already saved and the detail page says what is left.
        setApplying(true);
        try {
          const applied = await applyWithoutReview(api, await api.getProduct(id));
          if (publishNow) await api.setPublication(newCommandId(), applied.id, applied.version, true);
          notice = { key: publishNow ? 'create.managerPublished' : 'create.managerSaved', params };
        } catch {
          notice = { key: 'create.managerFollowUpFailed', params, tone: 'error' };
        } finally {
          setApplying(false);
        }
      }
      navigate(`/catalogue-management/products/${encodeURIComponent(id)}`, { state: { notice } });
    } else if (!outcome.error.retryable) pending.forget();
  };

  const serverErrors = serverFieldErrors(command.error);
  const errorOf = field => serverErrors[field] || (hints[field] && t(hints[field]));
  const set = name => event => setForm(current => ({ ...current, [name]: event.target.value }));

  const submit = async event => {
    event.preventDefault();
    const orderedBranchIds = chosenBranches.map(branch => String(branch.id));
    const found = { ...checkProductContent(form), ...checkVariants(variants), ...checkImages(images), ...checkBranchQuantities(variants, orderedBranchIds) };
    if (imagesPending(images)) found.images = 'image.notReady';
    // Applying right away needs what a review would need: at least one image.
    else if (isManager && !images.length) found.images = 'require.images';
    setHints(found);
    if (Object.keys(found).length) return;
    const body = {
      name: form.name.trim(),
      description: form.description.trim(),
      categoryId: form.categoryId,
      variants: variants.map(variant => ({ size: Number(variant.size), proposedPrice: variant.proposedPrice, quantity: variant.quantity })),
      images: imagesPayload(images),
      branches: branchesPayload(variants, orderedBranchIds),
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

        <h2 className="section-title">{t('branch.title')}</h2>
        <p className="muted small">{t('branch.createHint')}</p>
        {branchList.loading && <p className="muted small">{t('branch.loading')}</p>}
        {branchList.error && <ErrorNotice error={branchList.error} messages={messages} onRetry={branchList.reload} />}
        {!branchList.loading && !branchList.error && availableBranches.length === 0 && <p className="notice error">{t('branch.noneAssigned')}</p>}
        {availableBranches.length > 0 && (
          <div className="branch-choice" role="group" aria-label={t('branch.title')}>
            {availableBranches.map(branch => (
              <label key={branch.id} className={branchIds.includes(String(branch.id)) ? 'branch-option on' : 'branch-option'}>
                <input type="checkbox" checked={branchIds.includes(String(branch.id))} disabled={busy}
                  onChange={() => { toggleBranch(String(branch.id)); setHints(current => ({ ...current, branches: undefined })); }} />
                <span><strong>{branch.name}</strong>{branch.address && <span className="muted small"> · {branch.address}</span>}</span>
              </label>
            ))}
          </div>
        )}
        {errorOf('branches') && <p className="field-error">{errorOf('branches')}</p>}

        <h2 className="section-title">{t('create.sizes')}</h2>
        <p className="muted small">{t(isManager ? 'create.sizesHintManager' : 'create.sizesHint')}</p>
        {errorOf('variants') && <p className="field-error">{errorOf('variants')}</p>}
        <SizePicker variants={variants} onChange={update => { setVariants(update); setHints(current => ({ ...current, variants: undefined })); }} errorOf={errorOf} disabled={busy}
          priceLabel={isManager ? t('field.sellingPrice') : undefined} branches={chosenBranches} />

        <h2 className="section-title">{t('section.images')}</h2>
        <ImageEditor images={images} onChange={setImages} errors={{ ...Object.fromEntries(Object.keys(hints).filter(key => key.startsWith('images')).map(key => [key, t(hints[key])])), ...Object.fromEntries(Object.entries(serverErrors).filter(([key]) => key.startsWith('images'))) }} disabled={busy} />

        {isManager && (
          <fieldset className="publish-choice">
            <legend>{t('create.afterCreate')}</legend>
            <label><input type="radio" name="publishNow" checked={!publishNow} disabled={busy} onChange={() => setPublishNow(false)} /> {t('create.keepHidden')}</label>
            <label><input type="radio" name="publishNow" checked={publishNow} disabled={busy} onChange={() => setPublishNow(true)} /> {t('create.publishNow')}</label>
            <p className="muted small">{t('create.managerHint')}</p>
          </fieldset>
        )}

        <ErrorNotice error={command.error} messages={messages} onRetry={async () => finish(await command.retry())} />
        <div className="actions form-actions">
          <button type="submit" className="button primary" disabled={busy}>
            {applying ? t('create.applying') : command.pending ? t('saving') : t(isManager && publishNow ? 'create.submitPublish' : 'create.submit')}
          </button>
          <button type="button" className="button outline" onClick={() => navigate('/catalogue-management/products')}>{t('cancel')}</button>
        </div>
      </form>
    </>
  );
}
