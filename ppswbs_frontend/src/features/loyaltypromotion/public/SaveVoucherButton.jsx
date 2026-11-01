import React, { useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useT } from '../../../shared/i18n/I18nProvider';
import { ErrorNotice, useCommand } from '../../../shared/ui/components';
import { useLoyaltyApi } from '../api/useLoyaltyApi';
import { messages } from '../messages';

/** Saves a voucher to the Member's Kho Voucher (PUT, idempotent). A Guest is asked to sign in first. */
export function SaveVoucherButton({ promotion }) {
  const t = useT(messages);
  const { status } = useAuth();
  const api = useLoyaltyApi();
  const command = useCommand();
  const [saved, setSaved] = useState(false);

  if (status !== 'signed-in') return <a className="button outline small" href="/#/login">{t('public.signInToSave')}</a>;
  if (saved) return <p className="status ok" role="status">{t('public.saved')} <Link className="text-link" to="/me/saved-vouchers">{t('nav.savedVouchers')}</Link></p>;
  const finish = outcome => { if (outcome?.ok) setSaved(true); };
  return (
    <div>
      <button type="button" className="button primary small" disabled={command.pending}
        onClick={async () => finish(await command.run(key => api.saveVoucher(key, promotion.id)))}>{t('public.save')}</button>
      <ErrorNotice error={command.error} messages={messages} onRetry={async () => finish(await command.retry())} />
    </div>
  );
}
