import React, { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { ConfirmDialog, ErrorNotice, Field, Loading, SuccessNotice, useCommand, useResource } from '../../../../shared/ui/components';
import { useCatalogueApi } from '../../api/useCatalogueApi';
import { messages } from '../../messages';
import { LIMITS, serverFieldErrors } from '../../rules';
import { PageTop } from '../ManagementLayout';
import { Pager } from '../Pager';

/**
 * T079 categories: unique names, edit, and activation only after the impact (affected products, version) has
 * been shown and confirmed. Cancel sends nothing; deactivating keeps product publication flags and history.
 */
export default function CategoriesPage() {
  const t = useT(messages);
  const api = useCatalogueApi();
  const [params, setParams] = useSearchParams();
  const page = Number(params.get('page') || 0);
  const categories = useResource(() => api.listCategories(page || undefined), [page]);
  const [notice, setNotice] = useState(null);
  const done = key => { setNotice(key); categories.reload(); };
  const data = categories.data;

  return (
    <>
      <PageTop eyebrow={t('nav.group')} title={t('category.title')} />
      <SuccessNotice>{notice && t(notice)}</SuccessNotice>
      <CategoryForm api={api} onDone={() => done('category.created')} />
      {categories.loading && !data && <Loading />}
      {categories.error && <ErrorNotice error={categories.error} messages={messages} onRetry={categories.reload} />}
      {data && (data.items.length === 0
        ? <div className="empty-state">{t('category.empty')}</div>
        : <section className="workspace-card">
          <h2>{t('category.list')}</h2>
          {data.items.map(category => <CategoryRow key={category.id} category={category} api={api} onDone={done} reload={categories.reload} />)}
          <Pager page={page} pageSize={data.pageSize} total={data.totalElements} onChange={next => setParams(next > 0 ? { page: String(next) } : {})} />
        </section>)}
    </>
  );
}

function CategoryForm({ api, category, onDone, onCancel, reload }) {
  const t = useT(messages);
  const [form, setForm] = useState({ name: category?.name || '', description: category?.description || '' });
  const [hint, setHint] = useState(null);
  const command = useCommand();
  const serverErrors = serverFieldErrors(command.error);
  const set = name => event => setForm(current => ({ ...current, [name]: event.target.value }));

  const submit = async event => {
    event.preventDefault();
    if (!form.name.trim()) { setHint('validation.required'); return; }
    setHint(null);
    const body = { name: form.name.trim(), description: form.description.trim() || null };
    const outcome = await command.run(key => (category
      ? api.updateCategory(key, category.id, { expectedVersion: category.version, ...body })
      : api.createCategory(key, body)));
    if (outcome.ok) { if (!category) setForm({ name: '', description: '' }); onDone(); }
  };

  return (
    <form className={category ? 'category-edit' : 'workspace-card'} onSubmit={submit} noValidate>
      {!category && <h2>{t('category.new')}</h2>}
      <div className="form-grid">
        <Field label={t('field.categoryName')} error={serverErrors.name || (hint && t(hint))}><input value={form.name} onChange={set('name')} maxLength={LIMITS.nameMax} /></Field>
        <Field label={t('field.categoryDescription')} error={serverErrors.description}><input value={form.description} onChange={set('description')} maxLength={LIMITS.descriptionMax} /></Field>
      </div>
      <ErrorNotice error={command.error} messages={messages} onRetry={async () => { const outcome = await command.retry(); if (outcome?.ok) onDone(); }} onReload={reload} />
      <div className="actions">
        <button type="submit" className="button primary" disabled={command.pending}>{category ? t('save') : t('category.create')}</button>
        {onCancel && <button type="button" className="button outline" onClick={onCancel}>{t('cancel')}</button>}
      </div>
    </form>
  );
}

function CategoryRow({ category, api, onDone, reload }) {
  const t = useT(messages);
  const [editing, setEditing] = useState(false);
  const [impactOpen, setImpactOpen] = useState(false);
  return (
    <div className="category-row">
      <div className="card-head">
        <div>
          <strong>{category.name}</strong> <span className={category.active ? 'status ok' : 'status'}>{category.active ? t('category.active') : t('category.inactive')}</span>
          {category.description && <p className="muted small">{category.description}</p>}
        </div>
        <div className="actions">
          <button type="button" className="button outline small" onClick={() => setEditing(value => !value)}>{t('edit')}</button>
          <button type="button" className="button outline small" onClick={() => setImpactOpen(true)}>{category.active ? t('category.deactivate') : t('category.activate')}</button>
        </div>
      </div>
      {editing && <CategoryForm api={api} category={category} reload={reload} onCancel={() => setEditing(false)} onDone={() => { setEditing(false); onDone('category.saved'); }} />}
      {impactOpen && <ImpactDialog category={category} api={api} onClose={() => setImpactOpen(false)} onDone={key => { setImpactOpen(false); onDone(key); }} />}
    </div>
  );
}

function ImpactDialog({ category, api, onClose, onDone }) {
  const t = useT(messages);
  const [page, setPage] = useState(0);
  const impact = useResource(() => api.categoryImpact(category.id, page || undefined), [category.id, page]);
  const command = useCommand();
  const target = !category.active;
  useEffect(() => { command.reset(); }, [impact.data?.version]); // eslint-disable-line react-hooks/exhaustive-deps

  const confirm = async () => {
    const outcome = await command.run(key => api.setCategoryActive(key, category.id, impact.data.version, target));
    if (outcome.ok) onDone(target ? 'category.activated' : 'category.deactivated');
  };
  const affected = impact.data?.affectedProducts;

  return (
    <ConfirmDialog open title={target ? t('category.activateTitle', { name: category.name }) : t('category.deactivateTitle', { name: category.name })}
      confirmLabel={target ? t('category.activate') : t('category.deactivate')} danger={!target}
      pending={command.pending || impact.loading || !impact.data} onConfirm={confirm} onCancel={onClose}>
      {impact.loading && !impact.data && <Loading />}
      {impact.error && <ErrorNotice error={impact.error} messages={messages} onRetry={impact.reload} />}
      {affected && (
        <>
          <p>{t('category.impactCount', { count: affected.totalElements })}</p>
          <p className="muted small">{target ? t('category.activateHint') : t('category.deactivateHint')}</p>
          {affected.items.length > 0 && (
            <ul className="impact-list">
              {affected.items.map(product => <li key={product.id}><Link to={`/catalogue-management/products/${encodeURIComponent(product.id)}`}>{product.name}</Link></li>)}
            </ul>
          )}
          <Pager page={page} pageSize={affected.pageSize} total={affected.totalElements} onChange={setPage} />
        </>
      )}
      <ErrorNotice error={command.error} messages={messages} onRetry={async () => { const outcome = await command.retry(); if (outcome?.ok) onDone(target ? 'category.activated' : 'category.deactivated'); }} onReload={impact.reload} />
    </ConfirmDialog>
  );
}
