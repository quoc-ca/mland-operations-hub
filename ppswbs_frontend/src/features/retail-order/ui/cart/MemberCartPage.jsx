import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useFormat, useT } from '../../../../shared/i18n/I18nProvider';
import CartTabs from '../../../../shared/ui/CartTabs';
import { messages as retailMessages } from '../../messages';
import { ErrorNotice, Loading, useCommand, useResource } from '../../../../shared/ui/components';
import { useRetailApi } from '../../application/useRetailApi';
import { VoucherPicker } from '../checkout/VoucherPicker';
import { ProductThumb } from '../ProductThumb';
import { FlashToast } from '../FlashToast';
import { bestVoucherCode as bestOf, savedProductVouchers, usePublicProductVouchers, useVoucherOptions, voucherCheckBase, withPublicVouchers } from '../checkout/voucherOptions';

const MAX_QUANTITY = 99;
const initialSelection = { choice: 'AUTO', voucherCode: '', requestedPoints: '0' };
const validQuantity = value => Number.isInteger(value) && value >= 1 && value <= MAX_QUANTITY;
/** Why a typed quantity is refused, or '' when it can be saved: a whole number from 1 to 99 and within branch stock. */
export function quantityProblem(raw, availableQuantity, t) {
  const text = String(raw ?? '').trim();
  if (!text) return t('cart.enterAQuantityFrom1', { MAX_QUANTITY });
  if (!/^[0-9]+$/.test(text)) return t('cart.theQuantityMustBeA');
  const quantity = Number(text);
  if (!validQuantity(quantity)) return t('cart.theQuantityMustBeFrom', { MAX_QUANTITY });
  if (availableQuantity != null && BigInt(quantity) > BigInt(availableQuantity)) return t('cart.theBranchHasOnlyOf', { availableQuantity });
  return '';
}
const lineTotal = line => (line.indicativePrice == null ? null : (BigInt(line.indicativePrice) * BigInt(line.quantity || 0)).toString());
const isPositive = value => /^[1-9][0-9]*$/.test(String(value ?? ''));

/**
 * FE-08 Member cart in a Shopee-like layout: products grouped by branch with per-line and per-branch
 * ticks (only ticked lines are checked out), quantity steppers,
 * and one MLAND voucher + member-points bar at the bottom. Savings shown here are an estimate
 * from an FE-06 preview with in-store pickup; the checkout page recalculates with the real
 * fulfilment choice before any order is created.
 */
export default function MemberCartPage() {
  const t = useT(retailMessages);
  return (
    <>
    <CartTabs active="products" />
    <main className="container public-page retail-page retail-shopping retail-cart-page">
      <div className="retail-page-heading"><div><p className="retail-kicker">{t('cart.mlandShopping')}</p><h1>{t('cart.cart')}</h1></div><Link to="/products" className="retail-back-link">{t('common.continueShopping')}</Link></div>
      <RequireSession requiredText={t('cart.signInToSeeYour')}><CartContent /></RequireSession>
    </main>
    </>
  );
}

function CartContent() {
  const api = useRetailApi();
  const format = useFormat();
  const t = useT(retailMessages);
  const navigate = useNavigate();
  const cart = useResource(() => api.getCart(), []);
  const branches = useResource(() => api.listBranches(), []);
  const loyalty = useResource(() => api.getLoyaltyAccount(), []);
  const savedVoucherResource = useResource(() => api.listSavedVouchers(), []);
  const command = useCommand();
  const [notice, setNotice] = useState('');
  const [skipToastOpen, setSkipToastOpen] = useState(true);
  // Per-line quantity problem shown under the quantity box; the reset counter puts the box back to the saved value.
  const [quantityErrors, setQuantityErrors] = useState({});
  const [quantityReset, setQuantityReset] = useState({});
  const [selection, setSelection] = useState(initialSelection);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [quote, setQuote] = useState(null);
  const [quoteError, setQuoteError] = useState(null);
  const [quoting, setQuoting] = useState(false);
  const [selectedIds, setSelectedIds] = useState(null);
  // 'auto': the best usable saved voucher is applied by itself (Shopee style); 'manual' after the Member picks or removes one.
  const [voucherMode, setVoucherMode] = useState('auto');
  const [rejectedCodes, setRejectedCodes] = useState(() => new Set());
  const [pointsWanted, setPointsWanted] = useState(false);
  const previewSeq = useRef(0);

  const lines = cart.data?.lines || [];
  const groups = useMemo(() => {
    const byLocation = new Map();
    lines.forEach(line => byLocation.set(line.locationId, [...(byLocation.get(line.locationId) || []), line]));
    return [...byLocation.entries()];
  }, [lines]);

  // Ticked lines: nothing is ticked when the cart opens, the Member ticks what to buy (Session 2026-11-16), except
  // the rings just put back by "Mua lại", which arrive ticked so the Member sees what they were (Session 2026-11-20).
  // Ids that left the cart or stopped being sellable are dropped.
  const location = useLocation();
  const preselect = useRef(Array.isArray(location.state?.selectLineIds) ? location.state.selectLineIds.map(String) : []);
  const skippedByBuyAgain = Array.isArray(location.state?.skipped) ? location.state.skipped : [];
  const selectable = line => line.saleEligible && line.indicativePrice != null;
  useEffect(() => {
    if (!cart.data) return;
    setSelectedIds(current => {
      if (current == null) {
        const wanted = new Set(preselect.current);
        return new Set(lines.filter(line => selectable(line) && wanted.has(String(line.id))).map(line => line.id));
      }
      const present = new Set(lines.filter(selectable).map(line => line.id));
      return new Set([...current].filter(id => present.has(id)));
    });
  }, [cart.data]); // eslint-disable-line react-hooks/exhaustive-deps
  const selected = selectedIds || new Set();
  const selectedLines = lines.filter(line => selected.has(line.id));
  const selectableLines = lines.filter(selectable);
  const allSelected = selectableLines.length > 0 && selectableLines.every(line => selected.has(line.id));
  const toggleIds = (ids, checked) => setSelectedIds(current => {
    const next = new Set(current || []);
    ids.forEach(id => (checked ? next.add(id) : next.delete(id)));
    return next;
  });
  const toggleAll = checked => toggleIds(selectableLines.map(line => line.id), checked);
  const selectedGroups = groups.map(([locationId, branchLines]) => [locationId, branchLines.filter(line => selected.has(line.id))])
    .filter(([, branchLines]) => branchLines.length > 0);
  const branchNames = useMemo(() => {
    const items = branches.data?.items || branches.data || [];
    return new Map(items.map(item => [String(item.id), item.name]));
  }, [branches.data]);
  const branchName = locationId => branchNames.get(String(locationId)) || t('common.branchNumber', { locationId });
  const itemCount = selectedLines.reduce((sum, line) => sum + line.quantity, 0);
  const subtotal = selectedLines.reduce((total, line) => total + BigInt(line.indicativePrice || '0') * BigInt(line.quantity || 0), 0n).toString();
  const availablePoints = loyalty.data ? String(loyalty.data.availablePoints ?? loyalty.data.balancePoints ?? '0') : null;
  const publicVouchers = usePublicProductVouchers(api);
  const savedVouchers = withPublicVouchers(savedProductVouchers(savedVoucherResource.data?.items || savedVoucherResource.data || []), publicVouchers);
  // Best saved voucher for the ticked lines by its real FE-06 saving (products, branches and minimum included).
  const voucherOptions = useVoucherOptions(api, savedVouchers, voucherCheckBase(selectedLines));
  const bestVoucherCode = bestOf(voucherOptions);
  useEffect(() => {
    if (voucherMode !== 'auto' || voucherOptions == null) return;
    const wanted = bestVoucherCode ? { choice: 'VOUCHER', voucherCode: bestVoucherCode } : { choice: 'AUTO', voucherCode: '' };
    if (wanted.choice !== selection.choice || wanted.voucherCode !== selection.voucherCode) {
      setSelection(current => ({ ...current, ...wanted, requestedPoints: '0' }));
    }
  }, [voucherMode, bestVoucherCode, voucherOptions]); // eslint-disable-line react-hooks/exhaustive-deps
  const selectedVoucher = savedVouchers.find(item => item.code === selection.voucherCode);

  // Estimate benefits for the ticked lines with in-store pickup; re-run whenever cart, ticks or benefits change.
  const previewKey = JSON.stringify({ selection, lines: selectedLines.map(line => [line.id, line.quantity, line.locationId]) });
  useEffect(() => {
    if (!selectedLines.length) { previewSeq.current++; setQuote(null); setQuoteError(null); setQuoting(false); return; }
    const seq = ++previewSeq.current;
    const body = {
      selection: { ...selection, voucherCode: selection.choice === 'VOUCHER' ? selection.voucherCode.trim() : null },
      branchOrders: selectedGroups.map(([locationId]) => ({ locationId: Number(locationId), method: 'PICKUP', shippingQuoteId: null, recipient: null })),
      cartLineIds: selectedLines.map(line => line.id),
      estimateOnly: true, // the cart only shows numbers; checkout makes the order-ready quote
    };
    setQuoting(true);
    api.previewBenefits(body)
      .then(result => { if (seq === previewSeq.current) { setQuote(result); setQuoteError(null); } })
      .catch(error => { if (seq === previewSeq.current) { setQuote(null); setQuoteError(error); } })
      .finally(() => { if (seq === previewSeq.current) setQuoting(false); });
  }, [previewKey]); // eslint-disable-line react-hooks/exhaustive-deps

  // A voucher FE-06 refuses is dropped quietly (and greyed in the picker); points follow the switch.
  useEffect(() => {
    if (!quote || quoting) return;
    if (!quote.eligible) {
      if (selection.requestedPoints !== '0') {
        if (selection.requestedPoints === String(quote.maxPoints)) setPointsWanted(false);
        setSelection(current => ({ ...current, requestedPoints: '0' }));
        return;
      }
      if (selection.choice === 'VOUCHER' && selection.voucherCode) {
        const code = selection.voucherCode;
        setRejectedCodes(current => new Set([...current, code]));
        if (voucherMode === 'manual') {
          setNotice(t('cart.voucherDoesNotApplyTo', { code }));
          setSelection(current => ({ ...current, choice: 'AUTO', voucherCode: '' }));
        }
      }
      return;
    }
    if (pointsWanted && selection.requestedPoints === '0' && isPositive(quote.maxPoints)
      && !(isPositive(quote.minRedeemPoints) && BigInt(quote.maxPoints) < BigInt(quote.minRedeemPoints))) {
      setSelection(current => ({ ...current, requestedPoints: String(quote.maxPoints) }));
    }
  }, [quote, quoting]); // eslint-disable-line react-hooks/exhaustive-deps

  const maxPoints = quote?.maxPoints;
  const pointsOn = selection.requestedPoints !== '0';
  const pointsBelowMinimum = isPositive(maxPoints) && isPositive(quote?.minRedeemPoints) && BigInt(maxPoints) < BigInt(quote.minRedeemPoints);
  const canUsePoints = isPositive(maxPoints) && !pointsBelowMinimum;
  const discount = quote?.eligible ? BigInt(quote.discountAmount || '0') : 0n;
  // The code's own saving: `discountAmount` also includes the automatic discount (BUG-INT-0002).
  const voucherSaving = quote?.eligible ? BigInt(quote.voucherDiscountAmount ?? quote.discountAmount ?? '0') : 0n;
  // Programme discount spread, in proportion to amount, over only the lines FE-06 says it covers, shown on each line
  // like Shopee. An automatic discount for some products must not lower the price of the other lines.
  const discountedIds = quote?.eligibleCartLineIds?.length ? new Set(quote.eligibleCartLineIds) : null;
  const lineDiscounts = useMemo(() => {
    const result = new Map();
    const covered = discountedIds ? selectedLines.filter(line => discountedIds.has(line.id)) : selectedLines;
    const base = covered.reduce((sum, line) => sum + BigInt(lineTotal(line) || '0'), 0n);
    if (discount <= 0n || base <= 0n) return result;
    let left = discount;
    covered.forEach((line, index) => {
      const amount = BigInt(lineTotal(line) || '0');
      const share = index === covered.length - 1 ? left : (discount * amount) / base;
      left -= share;
      result.set(line.id, share);
    });
    return result;
  }, [discount, quote, previewKey]); // eslint-disable-line react-hooks/exhaustive-deps
  // A voucher says so; an automatic programme is a plain sale price.
  const discountTag = quote?.appliedVoucherCode ? t('cart.voucherApplied') : t('cart.onSale');
  const pointsDiscount = quote?.eligible ? BigInt(quote.pointsDiscountAmount || '0') : 0n;
  const savings = (discount + pointsDiscount).toString();
  const total = quote?.payableAmount ?? subtotal;

  const changeQuantity = async (line, raw) => {
    const problem = quantityProblem(raw, line.availableQuantity, t);
    if (problem) {
      setQuantityErrors(current => ({ ...current, [line.id]: problem }));
      setQuantityReset(current => ({ ...current, [line.id]: (current[line.id] || 0) + 1 }));
      return;
    }
    const quantity = Number(String(raw).trim());
    setQuantityErrors(current => { const next = { ...current }; delete next[line.id]; return next; });
    setNotice('');
    const result = await command.run(key => api.updateLine(line.id, quantity, key));
    if (result.ok) cart.reload();
  };
  const remove = async line => {
    setNotice('');
    const result = await command.run(key => api.removeLine(line.id, key));
    if (result.ok) cart.reload();
  };
  const confirmVoucher = code => {
    setPickerOpen(false);
    setVoucherMode('manual');
    setNotice('');
    setSelection(current => ({ ...current, choice: code ? 'VOUCHER' : 'NONE', voucherCode: code || '', requestedPoints: '0' }));
  };
  const togglePoints = checked => {
    setPointsWanted(checked);
    setSelection(current => ({ ...current, requestedPoints: checked ? String(maxPoints) : '0' }));
  };
  const removeSelected = async () => {
    if (!selectedLines.length) return;
    setNotice('');
    for (const line of selectedLines) {
      const result = await command.run(key => api.removeLine(line.id, key));
      if (!result.ok) break;
    }
    cart.reload();
  };
  const goToCheckout = () => {
    if (!selectedLines.length) { setNotice(t('cart.selectAtLeastOneItem')); return; }
    navigate('/member/checkout', { state: { benefitSelection: selection, cartLineIds: selectedLines.map(line => line.id) } });
  };

  if (cart.loading && !cart.data) return <Loading />;
  if (cart.error) return <ErrorNotice error={cart.error} onRetry={cart.reload} />;
  if (lines.length === 0) return (
    <section className="workspace-card empty-state">
      <FlashToast message={skipToastOpen && skippedByBuyAgain.length ? { kind: 'warn', text: t('cart.couldNotAddAgainNot', { items: skippedByBuyAgain.join(', ') }) } : null} onDone={() => setSkipToastOpen(false)} />
      <p>{t('cart.yourCartIsEmpty')}</p>
      <Link className="button primary" to="/products">{t('common.browseProducts')}</Link>
    </section>
  );

  return (
    <section className="cart-sp">
      <FlashToast message={skipToastOpen && skippedByBuyAgain.length ? { kind: 'warn', text: t('cart.couldNotAddAgainNot2', { items: skippedByBuyAgain.join(', ') }) } : null} onDone={() => setSkipToastOpen(false)} />
      <div className="cart-sp-header">
        <label className="cart-sp-check-label"><Checkbox checked={allSelected} indeterminate={!allSelected && selectedLines.length > 0} disabled={!selectableLines.length} onChange={toggleAll} label={t('cart.selectAllItems')} /><span>{t('cart.product')}</span></label>
        <span>{t('cart.unitPrice')}</span><span>{t('common.quantity')}</span><span>{t('common.amount')}</span><span>{t('cart.actions')}</span>
      </div>

      {groups.map(([locationId, branchLines]) => {
        const branchSelectable = branchLines.filter(selectable);
        const branchSelected = branchSelectable.filter(line => selected.has(line.id)).length;
        return (
        <section className="cart-sp-branch" key={locationId}>
          <div className="cart-sp-branch-name">
            <Checkbox checked={branchSelectable.length > 0 && branchSelected === branchSelectable.length} indeterminate={branchSelected > 0 && branchSelected < branchSelectable.length}
              disabled={!branchSelectable.length} onChange={checked => toggleIds(branchSelectable.map(line => line.id), checked)} label={t('cart.selectAllItemsOf', { branch: branchName(locationId) })} />
            <span className="cart-sp-branch-icon" aria-hidden="true">⌂</span><strong>{branchName(locationId)}</strong>
          </div>
          {branchLines.map(line => (
            <article className={`cart-sp-line${selectable(line) ? '' : ' unavailable'}`} key={line.id}>
              <div className="cart-sp-product">
                <Checkbox checked={selected.has(line.id)} disabled={!selectable(line)} onChange={checked => toggleIds([line.id], checked)} label={t('cart.selectItem', { name: line.productName || t('cart.itemFallback') })} />
                <ProductThumb className="retail-product-placeholder" productId={line.productId} />
                <div className="cart-sp-product-text">
                  <strong>{line.productName || t('common.productNumber', { variantId: line.variantId })}</strong>
                  <span className="muted">{t('common.size', { size: line.size ?? '—' })} · {line.sku || t('cart.noSkuYet')}</span>
                  {line.availableQuantity != null && <span className="muted">{t('cart.left', { availableQuantity: line.availableQuantity })}</span>}
                  {!line.saleEligible && <span className="status">{t('cart.temporarilyNotForSale')}</span>}
                </div>
              </div>
              <div className="cart-sp-price"><span className="cart-sp-label">{t('cart.unitPrice')}</span>{line.indicativePrice == null ? t('common.noPriceYet') : lineDiscounts.get(line.id) > 0n ? <>
                <s className="cart-sp-was">{format.money(line.indicativePrice)}</s>
                <b className="cart-sp-now">{format.money(((BigInt(lineTotal(line)) - lineDiscounts.get(line.id)) / BigInt(line.quantity || 1)).toString())}</b>
                <small className="cart-sp-voucher-tag">{discountTag}</small>
              </> : format.money(line.indicativePrice)}</div>
              <div className="cart-sp-qty-cell"><div className="cart-sp-qty" role="group" aria-label={t('cart.quantity', { productName: line.productName || '' })}>
                <button type="button" aria-label={t('cart.decreaseQuantity')} disabled={command.pending || line.quantity <= 1} onClick={() => changeQuantity(line, line.quantity - 1)}>−</button>
                <input key={`${line.id}-${line.quantity}-${quantityReset[line.id] || 0}`} type="number" min="1" max={line.availableQuantity == null ? MAX_QUANTITY : Math.min(MAX_QUANTITY, Number(line.availableQuantity))}
                  defaultValue={line.quantity} disabled={command.pending} aria-label={t('common.quantity')} step="1" aria-invalid={Boolean(quantityErrors[line.id])}
                  onBlur={event => event.target.value !== String(line.quantity) && changeQuantity(line, event.target.value)}
                  onKeyDown={event => event.key === 'Enter' && event.currentTarget.blur()} />
                <button type="button" aria-label={t('cart.increaseQuantity')} disabled={command.pending || line.quantity >= MAX_QUANTITY || (line.availableQuantity != null && BigInt(line.quantity) >= BigInt(line.availableQuantity))} onClick={() => changeQuantity(line, line.quantity + 1)}>+</button>
              </div>{quantityErrors[line.id] && <small className="cart-sp-qty-error" role="alert">{quantityErrors[line.id]}</small>}</div>
              <div className="cart-sp-amount"><span className="cart-sp-label">{t('common.amount')}</span>{lineTotal(line) == null ? '—' : format.money((BigInt(lineTotal(line)) - (lineDiscounts.get(line.id) || 0n)).toString())}</div>
              <div className="cart-sp-actions"><button type="button" className="text-link" disabled={command.pending} onClick={() => remove(line)}>{t('cart.delete')}</button></div>
            </article>
          ))}
        </section>
        );
      })}

      {notice && <p className="notice error">{notice}</p>}
      {command.error && <ErrorNotice error={command.error} onRetry={command.retry} onReload={cart.reload} />}

      <section className="cart-sp-bottom">
        <button type="button" className="cart-sp-row cart-sp-voucher" onClick={() => setPickerOpen(true)}>
          <span className="cart-sp-row-icon" aria-hidden="true">▣</span>
          <strong>{t('common.mlandVoucher')}</strong>
          <span className={selection.choice === 'VOUCHER' ? 'cart-sp-applied' : 'cart-sp-link'}>
            {selection.choice === 'VOUCHER' ? (voucherSaving > 0n ? `${selection.voucherCode} · −${format.money(voucherSaving.toString())}` : (selectedVoucher?.name || selection.voucherCode)) : t('common.selectOrEnterACode')}
          </span>
        </button>

        <label className={`cart-sp-row cart-sp-points${canUsePoints || pointsOn ? '' : ' disabled'}`}>
          <input type="checkbox" checked={pointsOn} disabled={quoting || (!canUsePoints && !pointsOn)} onChange={event => togglePoints(event.target.checked)} />
          <span className="cart-sp-row-icon" aria-hidden="true">Ⓟ</span>
          <strong>{t('cart.memberPoints')}</strong>
          <span className="muted">
            {availablePoints == null ? t('cart.loadingPoints')
              : availablePoints === '0' ? t('cart.youHaveNoPointsYet')
                : pointsBelowMinimum ? t('common.atLeastPointsAreNeeded', { minRedeemPoints: format.number(quote.minRedeemPoints) })
                  : canUsePoints ? t('cart.youHavePointsUpTo', { availablePoints: format.number(availablePoints), maxPoints: format.number(maxPoints) })
                    : t('cart.youHavePoints', { availablePoints: format.number(availablePoints) })}
          </span>
          <span className={pointsOn ? 'cart-sp-applied' : 'muted'}>−{format.money(pointsDiscount.toString())}</span>
        </label>

        {quoteError && <p className="notice error cart-sp-quote-error">{quoteError.message || t('cart.theSelectedDiscountCannotBe')}</p>}

        <div className="cart-sp-footer">
          <label className="cart-sp-check-label"><Checkbox checked={allSelected} indeterminate={!allSelected && selectedLines.length > 0} disabled={!selectableLines.length} onChange={toggleAll} label={t('cart.selectAllItems')} /><span>{t('cart.selectAll')} ({format.number(String(selectableLines.length))})</span></label>
          <button type="button" className="text-link cart-sp-delete-selected" disabled={command.pending || !selectedLines.length} onClick={removeSelected}>{t('cart.delete')}</button>
          <div className="cart-sp-total">
            <div>{t('cart.totalItems', { itemCount: format.number(String(itemCount)) })} <strong>{format.money(total)}</strong></div>
            {savings !== '0' && <small>{t('common.youSave')} <b>{format.money(savings)}</b></small>}
            {quoting && <small className="muted">{t('cart.calculatingDiscounts')}</small>}
          </div>
          <button type="button" className="button primary cart-sp-buy" disabled={command.pending || !selectedLines.length} onClick={goToCheckout}>{t('cart.checkOut')}</button>
        </div>
      </section>

      {pickerOpen && <VoucherPicker vouchers={savedVouchers.filter(item => item.availability !== 'EXPIRED')} selectedCode={selection.choice === 'VOUCHER' ? selection.voucherCode : null}
        subtotal={subtotal} rejectedCodes={rejectedCodes} options={voucherOptions} onConfirm={confirmVoucher} onClose={() => setPickerOpen(false)} />}
    </section>
  );
}

/** Checkbox that also shows the "some selected" state used for branch and select-all ticks. */
function Checkbox({ checked, indeterminate = false, disabled = false, onChange, label }) {
  const ref = useRef(null);
  useEffect(() => { if (ref.current) ref.current.indeterminate = indeterminate; }, [indeterminate]);
  return <input ref={ref} type="checkbox" className="cart-sp-check" checked={checked} disabled={disabled} aria-label={label}
    onChange={event => onChange(event.target.checked)} />;
}
