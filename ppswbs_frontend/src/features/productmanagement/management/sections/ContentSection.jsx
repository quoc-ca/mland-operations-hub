import React, { useEffect, useState } from 'react';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field, useResource } from '../../../../shared/ui/components';
import { messages } from '../../messages';
import { checkProductContent, LIMITS, serverFieldErrors } from '../../rules';
import { useProductCommand } from '../ProductDetailPage';

/** T050: name, description and category only (PUT products/{id}); sizes and SKUs stay read-only. */
export function ContentSection({ product, api, reload, setNotice, deleted }) {
  const t = useT(messages);
  const categories = useResource(() => api.listCategories(), []);
  const initial = () => ({ name: product.name, description: product.description || '', categoryId: product.category?.id || '' });
  const [form, setForm] = useState(initial);
  const [editing, setEditing] = useState(false);
  const [hints, setHints] = useState({});
  const command = useProductCommand({ reload, setNotice });

  // Outside editing the form follows the latest product; while editing it keeps what was typed.
  useEffect(() => { if (!editing) setForm(initial()); }, [product.version, editing]); // eslint-disable-line react-hooks/exhaustive-deps

  const serverErrors = serverFieldErrors(command.error);
  const errorOf = field => serverErrors[field] || (hints[field] && t(hints[field]));
  const set = name => event => setForm(current => ({ ...current, [name]: event.target.value }));

  const save = async event => {
    event.preventDefault();
    const found = checkProductContent(form);
    setHints(found);
    if (Object.keys(found).length) return;
    const body = { expectedVersion: product.version, name: form.name.trim(), description: form.description.trim(), categoryId: form.categoryId };
    const outcome = await command.run(key => api.updateProduct(key, product.id, body), 'content.saved');
    if (outcome.ok) setEditing(false);
  };
  const cancel = () => { setEditing(false); setHints({}); command.reset(); };

  const options = (categories.data?.items || []).filter(category => category.active || category.id === product.category?.id);

  return (
    <section className="workspace-card" aria-labelledby="content-title">
      <div className="card-head">
        <h2 id="content-title">{t('section.content')}</h2>
        {!editing && !deleted && <button type="button" className="button outline" onClick={() => setEditing(true)}>{t('edit')}</button>}
      </div>
      {!editing ? (
        <dl className="facts">
          <div><dt>{t('field.name')}</dt><dd>{product.name}</dd></div>
          <div><dt>{t('field.category')}</dt><dd>{product.category?.name || '—'}</dd></div>
          <div className="wide"><dt>{t('field.description')}</dt><dd className="pre-line">{product.description || '—'}</dd></div>
        </dl>
      ) : (
        <form onSubmit={save} noValidate>
          <div className="form-grid">
            <Field label={t('field.name')} error={errorOf('name')}><input value={form.name} onChange={set('name')} maxLength={LIMITS.nameMax} /></Field>
            <Field label={t('field.category')} error={errorOf('categoryId')}>
              <select value={form.categoryId} onChange={set('categoryId')}>
                <option value="">{t('field.chooseCategory')}</option>
                {options.map(category => <option key={category.id} value={category.id}>{category.name}</option>)}
              </select>
            </Field>
          </div>
          <Field label={t('field.description')} error={errorOf('description')}><textarea value={form.description} onChange={set('description')} maxLength={LIMITS.descriptionMax} /></Field>
          <p className="muted small">{t('content.reviewHint')}</p>
          <ErrorNotice error={command.error} messages={messages} onRetry={command.retry} onReload={reload} />
          <div className="actions form-actions">
            <button type="submit" className="button primary" disabled={command.pending}>{command.pending ? t('saving') : t('save')}</button>
            <button type="button" className="button outline" onClick={cancel} disabled={command.pending}>{t('cancel')}</button>
          </div>
        </form>
      )}
    </section>
  );
}
