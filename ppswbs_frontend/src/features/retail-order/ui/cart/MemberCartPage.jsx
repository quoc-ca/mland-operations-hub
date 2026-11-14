import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useFormat } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, useCommand, useResource } from '../../../../shared/ui/components';
import { useRetailApi } from '../../application/useRetailApi';
import { VoucherPicker } from '../checkout/VoucherPicker';
import { bestVoucherCode as bestOf, useVoucherOptions, voucherCheckBase } from '../checkout/voucherOptions';

const MAX_QUANTITY = 99;
const initialSelection = { choice: 'AUTO', voucherCode: '', requestedPoints: '0' };
const validQuantity = value => Number.isInteger(value) && value >= 1 && value <= MAX_QUANTITY;
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
  return (
    <main className="container public-page retail-page retail-shopping retail-cart-page">
      <div className="retail-page-heading"><div><p className="retail-kicker">MLAND / MUA SẮM</p><h1>Giỏ hàng</h1></div><Link to="/products" className="retail-back-link">← Tiếp tục mua sắm</Link></div>
      <RequireSession requiredText="Đăng nhập để xem giỏ hàng của bạn."><CartContent /></RequireSession>
    </main>
  );
}

function CartContent() {
  const api = useRetailApi();
  const format = useFormat();
  const navigate = useNavigate();
  const cart = useResource(() => api.getCart(), []);
  const branches = useResource(() => api.listBranches(), []);
  const loyalty = useResource(() => api.getLoyaltyAccount(), []);
  const savedVoucherResource = useResource(() => api.listSavedVouchers(), []);
  const command = useCommand();
  const [notice, setNotice] = useState('');
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

  // Ticked lines: default to every sellable line once the cart loads; drop ids that left the cart.
  const selectable = line => line.saleEligible && line.indicativePrice != null;
  useEffect(() => {
    if (!cart.data) return;
    setSelectedIds(current => {
      if (current == null) return new Set(lines.filter(selectable).map(line => line.id));
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
  const branchName = locationId => branchNames.get(String(locationId)) || `Chi nhánh #${locationId}`;
  const itemCount = selectedLines.reduce((sum, line) => sum + line.quantity, 0);
  const subtotal = selectedLines.reduce((total, line) => total + BigInt(line.indicativePrice || '0') * BigInt(line.quantity || 0), 0n).toString();
  const availablePoints = loyalty.data ? String(loyalty.data.availablePoints ?? loyalty.data.balancePoints ?? '0') : null;
  const savedVouchers = (savedVoucherResource.data?.items || savedVoucherResource.data || [])
    .map(item => ({ code: item.promotion?.code || item.code, name: item.promotion?.name || item.name || item.promotion?.code || item.code, availability: item.availability,
      discount: item.promotion?.discount || null, minimumEligibleAmount: item.promotion?.minimumEligibleAmount || '0', endAt: item.promotion?.endAt || null }))
    .filter(item => item.code);
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
          setNotice(`Voucher ${code} chưa áp dụng được cho các sản phẩm đã chọn.`);
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
  const discountTag = quote?.appliedVoucherCode ? 'Đã áp dụng mã giảm giá' : 'Đang giảm giá';
  const pointsDiscount = quote?.eligible ? BigInt(quote.pointsDiscountAmount || '0') : 0n;
  const savings = (discount + pointsDiscount).toString();
  const total = quote?.payableAmount ?? subtotal;

  const changeQuantity = async (line, raw) => {
    const quantity = Number(raw);
    if (!validQuantity(quantity)) { setNotice(`Số lượng phải từ 1 đến ${MAX_QUANTITY}.`); return; }
    if (line.availableQuantity != null && BigInt(quantity) > BigInt(line.availableQuantity)) {
      setNotice(`Chi nhánh chỉ còn ${line.availableQuantity} sản phẩm này.`);
      return;
    }
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
    if (!selectedLines.length) { setNotice('Hãy chọn ít nhất một sản phẩm để mua.'); return; }
    navigate('/member/checkout', { state: { benefitSelection: selection, cartLineIds: selectedLines.map(line => line.id) } });
  };

  if (cart.loading && !cart.data) return <Loading />;
  if (cart.error) return <ErrorNotice error={cart.error} onRetry={cart.reload} />;
  if (lines.length === 0) return (
    <section className="workspace-card empty-state">
      <p>Giỏ hàng chưa có sản phẩm.</p>
      <Link className="button primary" to="/products">Xem sản phẩm</Link>
    </section>
  );

  return (
    <section className="cart-sp">
      <div className="cart-sp-header">
        <label className="cart-sp-check-label"><Checkbox checked={allSelected} indeterminate={!allSelected && selectedLines.length > 0} disabled={!selectableLines.length} onChange={toggleAll} label="Chọn tất cả sản phẩm" /><span>Sản phẩm</span></label>
        <span>Đơn giá</span><span>Số lượng</span><span>Số tiền</span><span>Thao tác</span>
      </div>

      {groups.map(([locationId, branchLines]) => {
        const branchSelectable = branchLines.filter(selectable);
        const branchSelected = branchSelectable.filter(line => selected.has(line.id)).length;
        return (
        <section className="cart-sp-branch" key={locationId}>
          <div className="cart-sp-branch-name">
            <Checkbox checked={branchSelectable.length > 0 && branchSelected === branchSelectable.length} indeterminate={branchSelected > 0 && branchSelected < branchSelectable.length}
              disabled={!branchSelectable.length} onChange={checked => toggleIds(branchSelectable.map(line => line.id), checked)} label={`Chọn tất cả sản phẩm của ${branchName(locationId)}`} />
            <span className="cart-sp-branch-icon" aria-hidden="true">⌂</span><strong>{branchName(locationId)}</strong>
          </div>
          {branchLines.map(line => (
            <article className={`cart-sp-line${selectable(line) ? '' : ' unavailable'}`} key={line.id}>
              <div className="cart-sp-product">
                <Checkbox checked={selected.has(line.id)} disabled={!selectable(line)} onChange={checked => toggleIds([line.id], checked)} label={`Chọn ${line.productName || 'sản phẩm'}`} />
                <div className="retail-product-placeholder" aria-hidden="true">◇</div>
                <div className="cart-sp-product-text">
                  <strong>{line.productName || `Sản phẩm #${line.variantId}`}</strong>
                  <span className="muted">Size {line.size ?? '—'} · {line.sku || 'SKU chưa có'}</span>
                  {line.availableQuantity != null && <span className="muted">Còn {line.availableQuantity} sản phẩm</span>}
                  {!line.saleEligible && <span className="status">Tạm thời không còn bán</span>}
                </div>
              </div>
              <div className="cart-sp-price"><span className="cart-sp-label">Đơn giá</span>{line.indicativePrice == null ? 'Chưa có giá' : lineDiscounts.get(line.id) > 0n ? <>
                <s className="cart-sp-was">{format.money(line.indicativePrice)}</s>
                <b className="cart-sp-now">{format.money(((BigInt(lineTotal(line)) - lineDiscounts.get(line.id)) / BigInt(line.quantity || 1)).toString())}</b>
                <small className="cart-sp-voucher-tag">{discountTag}</small>
              </> : format.money(line.indicativePrice)}</div>
              <div className="cart-sp-qty" role="group" aria-label={`Số lượng ${line.productName || ''}`}>
                <button type="button" aria-label="Giảm số lượng" disabled={command.pending || line.quantity <= 1} onClick={() => changeQuantity(line, line.quantity - 1)}>−</button>
                <input key={`${line.id}-${line.quantity}`} type="number" min="1" max={line.availableQuantity == null ? MAX_QUANTITY : Math.min(MAX_QUANTITY, Number(line.availableQuantity))}
                  defaultValue={line.quantity} disabled={command.pending} aria-label="Số lượng"
                  onBlur={event => event.target.value !== String(line.quantity) && changeQuantity(line, event.target.value)}
                  onKeyDown={event => event.key === 'Enter' && event.currentTarget.blur()} />
                <button type="button" aria-label="Tăng số lượng" disabled={command.pending || line.quantity >= MAX_QUANTITY || (line.availableQuantity != null && BigInt(line.quantity) >= BigInt(line.availableQuantity))} onClick={() => changeQuantity(line, line.quantity + 1)}>+</button>
              </div>
              <div className="cart-sp-amount"><span className="cart-sp-label">Số tiền</span>{lineTotal(line) == null ? '—' : format.money((BigInt(lineTotal(line)) - (lineDiscounts.get(line.id) || 0n)).toString())}</div>
              <div className="cart-sp-actions"><button type="button" className="text-link" disabled={command.pending} onClick={() => remove(line)}>Xóa</button></div>
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
          <strong>Voucher MLAND</strong>
          <span className={selection.choice === 'VOUCHER' ? 'cart-sp-applied' : 'cart-sp-link'}>
            {selection.choice === 'VOUCHER' ? (discount > 0n ? `${selection.voucherCode} · −${format.money(discount.toString())}` : (selectedVoucher?.name || selection.voucherCode)) : 'Chọn hoặc nhập mã'}
          </span>
        </button>

        <label className={`cart-sp-row cart-sp-points${canUsePoints || pointsOn ? '' : ' disabled'}`}>
          <input type="checkbox" checked={pointsOn} disabled={quoting || (!canUsePoints && !pointsOn)} onChange={event => togglePoints(event.target.checked)} />
          <span className="cart-sp-row-icon" aria-hidden="true">Ⓟ</span>
          <strong>Điểm thành viên</strong>
          <span className="muted">
            {availablePoints == null ? 'Đang tải điểm…'
              : availablePoints === '0' ? 'Bạn chưa có điểm'
                : pointsBelowMinimum ? `Cần tối thiểu ${format.number(quote.minRedeemPoints)} điểm để sử dụng`
                  : canUsePoints ? `Bạn có ${format.number(availablePoints)} điểm · dùng tối đa ${format.number(maxPoints)} điểm`
                    : `Bạn có ${format.number(availablePoints)} điểm`}
          </span>
          <span className={pointsOn ? 'cart-sp-applied' : 'muted'}>−{format.money(pointsDiscount.toString())}</span>
        </label>

        {quoteError && <p className="notice error cart-sp-quote-error">{quoteError.message || 'Không áp dụng được ưu đãi đã chọn.'}</p>}

        <div className="cart-sp-footer">
          <label className="cart-sp-check-label"><Checkbox checked={allSelected} indeterminate={!allSelected && selectedLines.length > 0} disabled={!selectableLines.length} onChange={toggleAll} label="Chọn tất cả sản phẩm" /><span>Chọn tất cả ({format.number(String(selectableLines.length))})</span></label>
          <button type="button" className="text-link cart-sp-delete-selected" disabled={command.pending || !selectedLines.length} onClick={removeSelected}>Xóa</button>
          <div className="cart-sp-total">
            <div>Tổng cộng ({format.number(String(itemCount))} sản phẩm): <strong>{format.money(total)}</strong></div>
            {savings !== '0' && <small>Tiết kiệm <b>{format.money(savings)}</b></small>}
            {quoting && <small className="muted">Đang tính ưu đãi…</small>}
          </div>
          <button type="button" className="button primary cart-sp-buy" disabled={command.pending || !selectedLines.length} onClick={goToCheckout}>Mua hàng</button>
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
