import React, { useState } from 'react';
import { useNavigate } from 'react-router';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { ConfirmDialog, ErrorNotice, useCommand } from '../../../../shared/ui/components';
import { messages } from '../../messages';

/**
 * T064 delete: a separate confirmation; Cancel sends nothing. Deleting is a soft delete with history kept and is
 * different from hiding (publication) or stopping a size's sale. A 409 shows the latest product to confirm again.
 */
export function DeleteSection({ product, api, reload, deleted }) {
  const t = useT(messages);
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const command = useCommand();
  if (deleted) return null;

  const finish = outcome => {
    if (outcome?.ok) navigate('/catalogue-management/products', { state: { notice: 'delete.done' } });
    else if (outcome && !outcome.error.retryable) setOpen(false);
  };
  const confirm = async () => finish(await command.run(key => api.deleteProduct(key, product.id, product.version)));

  return (
    <section className="workspace-card danger-zone" aria-labelledby="delete-title">
      <h2 id="delete-title">{t('delete.title')}</h2>
      <p className="muted small">{t('delete.hint')}</p>
      <button type="button" className="button danger" onClick={() => { command.reset(); setOpen(true); }}>{t('delete.action')}</button>
      {!open && <ErrorNotice error={command.error} messages={messages} onReload={reload} />}
      <ConfirmDialog open={open} danger title={t('delete.confirmTitle', { name: product.name })} confirmLabel={t('delete.action')}
        pending={command.pending} onConfirm={confirm} onCancel={() => setOpen(false)}>
        <p>{t('delete.confirmText')}</p>
        <ErrorNotice error={command.error} messages={messages} onRetry={async () => finish(await command.retry())} />
      </ConfirmDialog>
    </section>
  );
}
