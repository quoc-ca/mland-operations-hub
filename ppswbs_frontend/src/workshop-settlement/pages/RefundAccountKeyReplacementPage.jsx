import React, { useState } from 'react';
import { useHttp } from '../../shared/api/useHttp';
import { useRole } from '../../shared/auth/useRole';
import { SuccessNotice, useCommand } from '../../shared/ui/components';
import { createSettlementApi } from '../api/settlementApi';
import { OpsPage } from '../OpsPage';
import { useT } from '../../shared/i18n/I18nProvider';
import { messages } from '../messages';
import { ProblemNotice, ReasonDialog } from '../ui';

/**
 * Manager operation (003.3 FR-040; moved from Admin to Manager on 2026-11-23): after the encryption key is replaced and the service restarted,
 * returns every refund request that holds an encrypted account number to PENDING_ACCOUNT and sends a new link. The
 * Manager approves again afterwards. The backend still decides the permission (403 for anyone else).
 */
export default function RefundAccountKeyReplacementPage() {
  const t = useT(messages);
  const api = createSettlementApi(useHttp());
  const role = useRole();
  const command = useCommand();
  const [asking, setAsking] = useState(false);
  const [result, setResult] = useState(null);
  const [message, setMessage] = useState('');

  const finish = out => {
    if (!out?.ok) return;
    setAsking(false);
    setResult(out.result);
    setMessage(t('keyRepl.done'));
  };
  const confirm = async reason => finish(await command.run(key => api.replaceRefundAccountKey({ reason }, key)));
  const retry = async () => finish(await command.retry());

  if (role !== 'MANAGER') {
    return (
      <OpsPage title={t('keyRepl.title')} intro={t('keyRepl.intro')}>
        <div className="notice error" role="alert"><p>{t('keyRepl.managerOnly')}</p></div>
      </OpsPage>
    );
  }

  return (
    <OpsPage title={t('keyRepl.title')} intro={t('keyRepl.intro')}>
      <SuccessNotice>{message}</SuccessNotice>
      <ProblemNotice error={command.error} onRetry={retry} onReload={() => setAsking(false)} />
      <section className="ota-panel">
        <div className="notice" role="note"><p>{t('keyRepl.warning')}</p></div>
        <button type="button" className="button primary" disabled={command.pending} onClick={() => { setMessage(''); setAsking(true); }}>
          {t('keyRepl.run')}
        </button>
      </section>
      {result && (
        <section className="ota-panel" aria-label={t('keyRepl.resultTitle')}>
          <div className="ota-panel-head"><h2>{t('keyRepl.resultTitle')}</h2></div>
          <dl>
            <dt>{t('keyRepl.reset')}</dt><dd>{result.requestsReset}</dd>
            <dt>{t('keyRepl.sent')}</dt><dd>{result.linksSent}</dd>
            <dt>{t('keyRepl.failed')}</dt><dd>{result.failed}</dd>
          </dl>
          {result.failed > 0 && <p className="muted">{t('keyRepl.failedHint')}</p>}
        </section>
      )}
      <ReasonDialog open={asking} title={t('keyRepl.confirmTitle')} confirmLabel={t('keyRepl.run')} pending={command.pending} onCancel={() => setAsking(false)} onConfirm={confirm}>
        <p>{t('keyRepl.confirmBody')}</p>
      </ReasonDialog>
    </OpsPage>
  );
}
