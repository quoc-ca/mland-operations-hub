import React, { useState } from 'react';
import { Link } from 'react-router';
import { useFormat, useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Field } from '../../../../shared/ui/components';
import { newCommandId } from '../../../../shared/api/http';
import { ACTIONS } from '../../application/useCatalogueCommand';
import { effectiveProposal, isAmount, productCode, serverFieldErrors } from '../../domain/productRules';
import { messages } from '../messages';
import { PriceDecision } from './sections/PriceSection';
import { useProductCommand } from './ProductDetailPage';
import { NoticeAt } from './notices';

/**
 * FR-065 (Session 2026-11-22): the "Prices to review" tab. One row per size whose current standalone proposal waits for
 * a decision (products already published once). A Manager gets the same approve/reject form as the details page,
 * "apply now" included, and a decision reloads the list and the tab counts. Staff see the waiting rows read-only
 * ("Chờ Manager duyệt"). With mode DRAFT (Staff sub-filter "Nháp chưa gửi") the rows are the unsent drafts, each with
 * a "Gửi duyệt" button that submits exactly that revision. With mode REJECTED (Staff sub-filter "Bị từ chối", 2026-11-23)
 * the rows are the rejected prices with the Manager who rejected, the reason and a link to propose again; a row leaves
 * the list when the size gets a new proposal.
 */
export function PendingPriceList({ products, api, reload, setNotice, canDecide = false, isManager = false, outOfScope = () => false, mode = 'PENDING' }) {
  const t = useT(messages);
  const format = useFormat();
  const rows = products.flatMap(product => (product.variants || [])
    .map(variant => ({ product, variant, proposal: effectiveProposal(variant) }))
    .filter(row => row.proposal?.state === mode));
  const label = mode === 'PENDING' ? 'list.tab.PRICE_PENDING' : `list.priceSub.PRICE_${mode}`;
  const priceHead = mode === 'DRAFT' ? 'price.draftPrice' : 'price.proposed';
  const personHead = mode === 'DRAFT' ? 'list.state' : mode === 'REJECTED' ? 'people.rejectedBy' : 'people.submittedBy';
  const lastHead = mode === 'PENDING' ? 'list.decision' : mode === 'REJECTED' ? 'price.reason' : 'list.actions';

  return (
    <div className="data-table" role="table" aria-label={t(label)}>
      <div className="data-row pending-prices head" role="row">
        <span role="columnheader">{t('field.name')}</span><span role="columnheader">{t('list.size')}</span>
        <span role="columnheader">{t('variant.appliedPrice')}</span><span role="columnheader">{t(priceHead)}</span>
        <span role="columnheader">{t(personHead)}</span><span role="columnheader">{t(lastHead)}</span>
      </div>
      {rows.map(({ product, variant, proposal }) => {
        const at = `pending-price-${variant.id}`;
        const note = (key, amount) => ({ key, params: { size: variant.size, amount: amount === undefined ? undefined : format.money(String(amount)) }, at });
        const detail = `/catalogue-management/products/${encodeURIComponent(product.id)}`;
        // T182 (FR-051): Staff act only on products sold at their branches; others are listed without the action.
        const readOnly = outOfScope(product);
        return (
          <div className="data-row pending-prices" role="row" key={proposal.id}>
            <span role="cell">
              <Link className="text-link" to={detail}>{product.name}</Link>
              <br /><span className="muted small mono">{productCode(product.id)}</span>
            </span>
            <span role="cell">{variant.size}</span>
            <span role="cell">{format.money(variant.appliedPrice)}</span>
            <span role="cell">{format.money(proposal.proposedPrice)}</span>
            <span role="cell">
              {mode === 'DRAFT' && <span className="muted">{t('list.notSent')}</span>}
              {mode === 'PENDING' && <Person name={proposal.submittedBy?.displayName} at={proposal.submittedAt} format={format} />}
              {mode === 'REJECTED' && <Person name={proposal.decidedBy?.displayName} at={proposal.decidedAt} format={format} />}
            </span>
            <span role="cell">
              {mode === 'DRAFT' && !readOnly && <SubmitDraft proposal={proposal} note={note} api={api} reload={reload} setNotice={setNotice} />}
              {mode === 'PENDING' && (canDecide
                ? <><PriceDecision proposal={proposal} note={note} api={api} reload={reload} setNotice={setNotice} /><NoticeAt at={at} /></>
                : <span className="status warn">{t('list.waitingForManager')}</span>)}
              {mode === 'REJECTED' && (
                <>
                  <span className="rejection-reason">{proposal.reason || '—'}</span>
                  {!readOnly && (isManager
                    ? <><br /><Link className="button outline small" to={`${detail}?priceSize=${encodeURIComponent(variant.id)}#price-title`}>{t('price.change')}</Link></>
                    : <ProposeAgain variant={variant} note={note} api={api} reload={reload} setNotice={setNotice} />)}
                  <NoticeAt at={at} />
                </>
              )}
            </span>
          </div>
        );
      })}
    </div>
  );
}

function Person({ name, at, format }) {
  return <>{name || '—'}{at && <><br /><span className="muted small">{format.dateTime(at)}</span></>}</>;
}

/**
 * Staff on a rejected price (Session 2026-11-23): "Đề xuất giá mới" opens a price field on the row; sending creates the
 * new proposal for the size and submits it to a Manager (two commands, FR-013). The old price stays applied meanwhile.
 * When the proposal is saved but the submission fails, the draft is kept and waits under "Nháp chưa gửi".
 */
function ProposeAgain({ variant, note, api, reload, setNotice }) {
  const t = useT(messages);
  const [amount, setAmount] = useState(null);
  const [localError, setLocalError] = useState(null);
  const command = useProductCommand({ reload, setNotice });
  if (amount === null) {
    return <><br /><button type="button" className="button outline small" onClick={() => setAmount('')}>{t('price.propose')}</button></>;
  }
  const submit = async event => {
    event.preventDefault();
    setNotice(null);
    if (!isAmount(amount)) { setLocalError(t('validation.amount')); return; }
    setLocalError(null);
    const value = String(amount).trim();
    let saved = false;
    const send = async key => {
      const created = await api.proposePrice(key, variant.id, variant.version, value);
      saved = true;
      return api.submitPrice(newCommandId(), created.resourceId, created.version);
    };
    const outcome = await command.run(send, note('price.submitted'), ACTIONS.proposePrice);
    if (outcome.ok) { setAmount(null); return; }
    if (saved) {
      await reload();
      command.reset();
      setNotice({ ...note('price.proposedDraft', value), tone: 'error' });
    }
  };
  const fieldError = serverFieldErrors(command.error).proposedPrice || localError;
  return (
    <form className="amount-form" onSubmit={submit} noValidate aria-label={t('price.propose')}>
      <Field label={t('price.newPrice')} error={fieldError}><input inputMode="numeric" value={amount} onChange={event => { setAmount(event.target.value); setLocalError(isAmount(event.target.value) ? null : t('validation.amount')); }} /></Field>
      <ErrorNotice error={command.error} messages={messages} onReload={reload} />
      <div className="actions">
        <button type="submit" className="button primary small" disabled={command.pending}>{t('price.submit')}</button>
        <button type="button" className="button outline small" onClick={() => { setAmount(null); setLocalError(null); command.reset(); }}>{t('cancel')}</button>
      </div>
    </form>
  );
}

/** Sends one unsent draft to the Manager (FR-013), then the list and counts reload. */
function SubmitDraft({ proposal, note, api, reload, setNotice }) {
  const t = useT(messages);
  const command = useProductCommand({ reload, setNotice });
  return (
    <div>
      <button type="button" className="button primary small" disabled={command.pending}
        onClick={() => command.run(key => api.submitPrice(key, proposal.id, proposal.version), note('price.submitted'), ACTIONS.submitPrice)}>
        {t('price.submit')}
      </button>
      <ErrorNotice error={command.error} messages={messages} onRetry={command.retry} onReload={reload} />
    </div>
  );
}
