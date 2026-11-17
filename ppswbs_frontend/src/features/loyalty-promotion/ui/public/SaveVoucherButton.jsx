import React, { useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../../../shared/auth/AuthProvider';
import { SignInLink } from '../../../../shared/auth/SignInLink';
import { useRole } from '../../../../shared/auth/useRole';
import { useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, useCommand } from '../../../../shared/ui/components';
import { useLoyaltyApi } from '../../application/useLoyaltyApi';
import { messages } from '../messages';

/**
 * Saves a voucher to the Member's Kho Voucher (PUT, idempotent). A Guest is asked to sign in first. A signed-in Staff,
 * Manager or Admin has no wallet (the PUT would be refused and send them home, T115): they get a short note instead,
 * and nothing is offered while the role is still unknown.
 * `saved` comes from the Member's wallet (useSavedVoucherIds) so a voucher saved earlier reads "Đã lưu" after a
 * reload; `onSaved` lets the page update that set.
 */
export function SaveVoucherButton({ promotion, saved: savedBefore = false, onSaved }) {
  const t = useT(messages);
  const { status } = useAuth();
  const role = useRole();
  const api = useLoyaltyApi();
  const command = useCommand();
  const [savedNow, setSavedNow] = useState(false);

  if (status !== 'signed-in') return <SignInLink className="button outline small">{t('public.signInToSave')}</SignInLink>;
  if (role === null) return null;
  if (role !== 'MEMBER') return <p className="muted small">{t('public.membersOnly')}</p>;
  if (savedBefore || savedNow) return <p className="status ok" role="status">{t('public.saved')} <Link className="text-link" to="/me/saved-vouchers">{t('nav.savedVouchers')}</Link></p>;
  const finish = outcome => { if (outcome?.ok) { setSavedNow(true); onSaved?.(promotion.id); } };
  return (
    <div>
      <button type="button" className="button primary small" disabled={command.pending}
        onClick={async () => finish(await command.run(key => api.saveVoucher(key, promotion.id)))}>{t('public.save')}</button>
      <ErrorNotice error={command.error} messages={messages} onRetry={async () => finish(await command.retry())} />
    </div>
  );
}
