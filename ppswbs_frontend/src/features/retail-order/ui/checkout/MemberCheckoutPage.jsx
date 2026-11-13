import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useFormat } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, SuccessNotice, useCommand, useResource } from '../../../../shared/ui/components';
import { useRetailApi } from '../../application/useRetailApi';
import { RetailBenefitPanel } from './RetailBenefitPanel';
import { AddressModal, addressApiClient, useProfile } from '../../../accountmanagement';

const initialSelection = { choice: 'AUTO', voucherCode: '', requestedPoints: '0' };
const AUTO_QUOTE_DELAY_MS = 300;
const TOAST_MS = 3000;
// A quote or preview this close to expiry is refreshed before the order is placed.
const EXPIRY_MARGIN_MS = 10_000;
const hasAmount = value => /^[0-9]+$/.test(String(value ?? '')) && BigInt(value) > 0n;
const isExpired = at => !at || new Date(at).getTime() - Date.now() < EXPIRY_MARGIN_MS;
const newIdempotencyKey = () => (globalThis.crypto?.randomUUID ? globalThis.crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`);

const fullAddress = address => [address.detailAddress, address.hamletName, address.wardName, address.districtName, address.provinceName].filter(Boolean).join(', ');
const recipientOf = address => address ? {
  address: [address.detailAddress, address.hamletName].filter(Boolean).join(', '),
  province: address.provinceName,
  district: address.districtName,
  ward: address.wardName,
  recipientName: address.recipientName,
  recipientPhone: address.phoneNumber,
} : null;

export default function MemberCheckoutPage() {
  return <main className="container public-page retail-page retail-shopping"><div className="retail-page-heading"><div><p className="retail-kicker">FE-08 · MEMBER</p><h1>Thanh toán</h1><p>Kiểm tra sản phẩm, nhận hàng và ưu đãi trước khi đặt hàng.</p></div><Link to="/member/cart" className="retail-back-link">← Quay lại giỏ hàng</Link></div><RequireSession requiredText="Đăng nhập để thanh toán."><CheckoutContent /></RequireSession></main>;
}

function CheckoutContent() {
  const api = useRetailApi();
  const format = useFormat();
  const cart = useResource(() => api.getCart(), []);
  const addresses = useResource(() => addressApiClient.getOwnAddresses(), []);
  const savedVoucherResource = useResource(() => api.listSavedVouchers(), []);
  const branches = useResource(() => api.listBranches(), []);
  const quoteCommand = useCommand();
  const shippingCommand = useCommand();
  const createCommand = useCommand();
  const paymentCommand = useCommand();
  const location = useLocation();
  // Lines ticked on the cart page; null (direct visit) keeps the whole-cart checkout.
  const cartLineIds = Array.isArray(location.state?.cartLineIds) && location.state.cartLineIds.length ? location.state.cartLineIds.map(String) : null;
  const lineFilter = cartLineIds ? new Set(cartLineIds) : null;
  // Voucher/points chosen on the cart page arrive via router state; the checkout still recalculates them.
  const [selection, setSelection] = useState(() => {
    const fromCart = location.state?.benefitSelection;
    return fromCart && typeof fromCart.choice === 'string' ? { ...initialSelection, ...fromCart } : initialSelection;
  });
  const [selectedAddressId, setSelectedAddressId] = useState(null);
  const [addressPickerOpen, setAddressPickerOpen] = useState(false);
  const [draftAddressId, setDraftAddressId] = useState(null);
  // Add/edit an address without leaving checkout (Shopee-style); null = closed, { address: null } = new address.
  const [addressForm, setAddressForm] = useState(null);
  const addressSaved = useRef(false);
  const { profile } = useProfile();
  const [fulfilmentByLocation, setFulfilmentByLocation] = useState({});
  const [shippingQuotes, setShippingQuotes] = useState({});
  const [shippingMessage, setShippingMessage] = useState('');
  const [quote, setQuote] = useState(null);
  const [quotedKey, setQuotedKey] = useState(null);
  const [checkout, setCheckout] = useState(null);
  const [paymentMethod, setPaymentMethod] = useState('VNPAY');
  const [toast, setToast] = useState('');
  // Vouchers FE-06 rejected for this order (e.g. other products or branches), shown greyed in the picker.
  const [rejectedCodes, setRejectedCodes] = useState(() => new Set());
  // The Member's points switch; the requested amount follows the latest maximum after a voucher change.
  const [pointsOn, setPointsOn] = useState(() => (location.state?.benefitSelection?.requestedPoints || '0') !== '0');
  // Set while a stale fee or preview is refreshed; the order is placed once the new total equals the one shown.
  const [pendingPlace, setPendingPlace] = useState(null);
  const [refreshTick, setRefreshTick] = useState(0);
  useEffect(() => {
    if (!toast) return undefined;
    const timer = setTimeout(() => setToast(''), TOAST_MS);
    return () => clearTimeout(timer);
  }, [toast]);

  const groups = useMemo(() => {
    const byLocation = new Map();
    (cart.data?.lines || []).filter(line => !lineFilter || lineFilter.has(String(line.id))).forEach(line => byLocation.set(line.locationId, [...(byLocation.get(line.locationId) || []), line]));
    return [...byLocation.entries()].map(([locationId, lines]) => ({ locationId, lines }));
  }, [cart.data, location.state]); // eslint-disable-line react-hooks/exhaustive-deps
  // Opening the page shows the default address (Shopee-style); "Thay đổi" picks another saved address.
  useEffect(() => {
    if (selectedAddressId != null || !addresses.data?.length) return;
    setSelectedAddressId((addresses.data.find(item => item.isDefault) || addresses.data[0]).id);
  }, [addresses.data, selectedAddressId]);

  const selectedAddress = useMemo(() => {
    if (!addresses.data?.length) return null;
    return addresses.data.find(item => item.id === Number(selectedAddressId)) || addresses.data.find(item => item.isDefault) || addresses.data[0];
  }, [addresses.data, selectedAddressId]);
  const recipient = recipientOf(selectedAddress);
  const branchNames = useMemo(() => new Map((branches.data?.items || branches.data || []).map(item => [String(item.id), item.name])), [branches.data]);
  const branchName = locationId => branchNames.get(String(locationId)) || `Chi nhánh #${locationId}`;
  const methodFor = locationId => fulfilmentByLocation[String(locationId)] || 'PICKUP';
  const branchOrders = useMemo(() => groups.map(group => {
    const method = methodFor(group.locationId);
    return { locationId: Number(group.locationId), method, shippingQuoteId: method === 'GHTK' ? shippingQuotes[group.locationId]?.quoteId || null : null, recipient: method === 'GHTK' ? recipient : null };
  }), [groups, fulfilmentByLocation, shippingQuotes, recipient]); // eslint-disable-line react-hooks/exhaustive-deps
  const hasUnquotedDelivery = groups.some(group => methodFor(group.locationId) === 'GHTK' && !shippingQuotes[group.locationId]?.quoteId);
  const selectionBody = { ...selection, voucherCode: selection.choice === 'VOUCHER' ? selection.voucherCode.trim() : null };
  // Cash at the counter (FR-08-041): store pickup only, one branch per checkout in V1; no 5,000 VND minimum.
  const cashAllowed = groups.length === 1 && groups.every(group => methodFor(group.locationId) === 'PICKUP');
  useEffect(() => {
    if (!cashAllowed && paymentMethod === 'CASH_AT_COUNTER') setPaymentMethod('VNPAY');
  }, [cashAllowed, paymentMethod]);
  const isCashCheckout = checkout?.paymentMethod === 'CASH_AT_COUNTER';
  const savedVouchers = (savedVoucherResource.data?.items || savedVoucherResource.data || []).map(item => ({ code: item.promotion?.code || item.code, name: item.promotion?.name || item.name || item.promotion?.code || item.code, availability: item.availability, discount: item.promotion?.discount || null, minimumEligibleAmount: item.promotion?.minimumEligibleAmount || '0', endAt: item.promotion?.endAt || null })).filter(item => item.code);
  // Merchandise value of the selected lines, used to grey out vouchers whose minimum is not reached.
  const voucherSubtotal = groups.reduce((sum, group) => group.lines.reduce((acc, line) => acc + (line.indicativePrice == null ? 0n : BigInt(line.indicativePrice) * BigInt(line.quantity)), sum), 0n).toString();

  // Totals refresh by themselves whenever address, delivery, voucher, points or payment method change.
  const previewBody = { selection: selectionBody, branchOrders, paymentMethod, ...(cartLineIds ? { cartLineIds } : {}) };
  const quoteKey = JSON.stringify(previewBody);
  const latestKey = useRef(quoteKey);
  latestKey.current = quoteKey;
  const quoteMatches = quote != null && quotedKey === quoteKey;
  useEffect(() => {
    if (checkout || !groups.length || hasUnquotedDelivery || shippingCommand.pending) return undefined;
    const key = quoteKey;
    const body = JSON.parse(key);
    const timer = setTimeout(async () => {
      const result = await quoteCommand.run(() => api.previewBenefits(body));
      // Only the newest request may update the totals; a slower older answer is dropped.
      if (result.ok && latestKey.current === key) { setQuote(result.result); setQuotedKey(key); }
    }, AUTO_QUOTE_DELAY_MS);
    return () => clearTimeout(timer);
  }, [quoteKey, refreshTick, checkout, groups.length, hasUnquotedDelivery, shippingCommand.pending]); // eslint-disable-line react-hooks/exhaustive-deps

  const quoteDelivery = async (locationId, address = selectedAddress) => {
    const to = recipientOf(address);
    if (!to) { setShippingMessage('Hãy thêm và chọn một địa chỉ nhận hàng trước khi dùng GHTK.'); return; }
    setShippingMessage('');
    setShippingQuotes(current => { const next = { ...current }; delete next[locationId]; return next; });
    const result = await shippingCommand.run(() => api.quoteShipping({ locationId: Number(locationId), recipient: to, ...(cartLineIds ? { cartLineIds } : {}) }));
    if (!result.ok) return null;
    setShippingQuotes(current => ({ ...current, [locationId]: result.result }));
    if (!result.result.deliveryAvailable || !result.result.quoteId) setShippingMessage(result.result.message || 'GHTK chưa hỗ trợ giao đến địa chỉ này. Hãy chọn nhận tại cửa hàng hoặc một địa chỉ khác.');
    return result.result;
  };
  const changeMethod = async (locationId, method) => {
    setFulfilmentByLocation(current => ({ ...current, [locationId]: method }));
    if (method !== 'GHTK') { setShippingQuotes(current => { const next = { ...current }; delete next[locationId]; return next; }); setShippingMessage(''); return; }
    await quoteDelivery(locationId);
  };
  const openAddressPicker = () => { setDraftAddressId(selectedAddress?.id ?? null); setAddressPickerOpen(true); };
  const confirmAddress = async () => {
    setAddressPickerOpen(false);
    const next = addresses.data?.find(item => item.id === Number(draftAddressId));
    if (!next || next.id === selectedAddress?.id) return;
    setSelectedAddressId(next.id);
    // Branches delivered by GHTK get a fresh fee for the new address straight away.
    for (const group of groups) {
      if (methodFor(group.locationId) === 'GHTK') await quoteDelivery(group.locationId, next); // eslint-disable-line no-await-in-loop
    }
  };
  const openAddressForm = address => { addressSaved.current = false; setAddressPickerOpen(false); setAddressForm({ address }); };
  const closeAddressForm = () => {
    setAddressForm(null);
    // "Trở lại" returns to the address list; after a successful save the list is not reopened.
    if (!addressSaved.current) setAddressPickerOpen(true);
  };
  const saveAddress = async data => {
    const editing = addressForm?.address;
    const saved = editing
      ? await addressApiClient.updateOwnAddress(editing.id, data)
      : await addressApiClient.createOwnAddress(data, newIdempotencyKey());
    addressSaved.current = true;
    const list = await addresses.reload();
    const next = (list || []).find(item => item.id === saved?.id) || saved;
    if (!next) return;
    // A new address becomes the checkout address at once; editing the selected one refreshes its GHTK fees.
    if (!editing || editing.id === selectedAddress?.id) {
      setSelectedAddressId(next.id);
      for (const group of groups) {
        if (methodFor(group.locationId) === 'GHTK') await quoteDelivery(group.locationId, next); // eslint-disable-line no-await-in-loop
      }
    }
  };
  // A voucher FE-06 refuses is removed quietly and greyed in the picker; points follow the switch.
  useEffect(() => {
    if (!quoteMatches || !quote || checkout) return;
    if (!quote.eligible) {
      if (selection.requestedPoints !== '0') {
        // Points the server refuses even at its own maximum: switch them off instead of retrying.
        if (selection.requestedPoints === String(quote.maxPoints)) setPointsOn(false);
        setSelection(current => ({ ...current, requestedPoints: '0' }));
        return;
      }
      if (selection.choice === 'VOUCHER' && selection.voucherCode) {
        const code = selection.voucherCode;
        setRejectedCodes(current => new Set([...current, code]));
        setSelection(current => ({ ...current, choice: 'NONE', voucherCode: '' }));
        setToast(`Voucher ${code} chưa áp dụng được cho đơn này.`);
      }
      return;
    }
    const max = /^[0-9]+$/.test(String(quote.maxPoints ?? '')) ? BigInt(quote.maxPoints) : 0n;
    const min = /^[0-9]+$/.test(String(quote.minRedeemPoints ?? '')) ? BigInt(quote.minRedeemPoints) : 0n;
    if (pointsOn && selection.requestedPoints === '0' && max > 0n && max >= min) {
      setSelection(current => ({ ...current, requestedPoints: max.toString() }));
    }
  }, [quoteMatches, quote, checkout]); // eslint-disable-line react-hooks/exhaustive-deps
  const changeBenefits = next => {
    const sameVoucher = next.choice === selection.choice && next.voucherCode === selection.voucherCode;
    if (sameVoucher && next.requestedPoints !== selection.requestedPoints) setPointsOn(next.requestedPoints !== '0');
    setSelection(next);
  };
  const placeOrder = async () => {
    const result = await createCommand.run(key => api.createCheckout({ branchOrders, benefitPreviewId: quote.previewId, benefitSelection: selectionBody, benefitTermsAccepted: true, paymentMethod, ...(cartLineIds ? { cartLineIds } : {}) }, key));
    if (result.ok) { setCheckout(result.result); return; }
    // The fee or preview expired on the server in the meantime: refresh quietly and let the Member confirm again.
    if (result.error?.status === 409) {
      createCommand.reset();
      for (const group of groups) {
        if (methodFor(group.locationId) === 'GHTK') await quoteDelivery(group.locationId); // eslint-disable-line no-await-in-loop
      }
      setQuotedKey(null); setRefreshTick(tick => tick + 1);
      setToast('Thông tin đơn hàng vừa được cập nhật. Kiểm tra lại tổng tiền rồi bấm Đặt hàng.');
    }
  };
  const create = async () => {
    if (!quoteMatches || !quote?.previewId || hasUnquotedDelivery) return;
    const shownTotal = quote.payableAmount;
    // GHTK fees and benefit previews are valid for a limited time; refresh a stale one before placing the order.
    const staleBranches = groups.filter(group => methodFor(group.locationId) === 'GHTK' && isExpired(shippingQuotes[group.locationId]?.expiresAt));
    if (staleBranches.length) {
      const changes = [];
      for (const group of staleBranches) {
        const before = shippingQuotes[group.locationId]?.deliveryFee;
        const fresh = await quoteDelivery(group.locationId); // eslint-disable-line no-await-in-loop
        if (!fresh?.quoteId) return;
        if (String(fresh.deliveryFee) !== String(before)) changes.push(`${format.money(before)} → ${format.money(fresh.deliveryFee)}`);
      }
      if (changes.length) { setToast(`Phí vận chuyển đã thay đổi (${changes.join(', ')}). Kiểm tra lại tổng tiền rồi bấm Đặt hàng.`); return; }
      setPendingPlace({ total: shownTotal });
      return;
    }
    if (isExpired(quote.expiresAt)) {
      setPendingPlace({ total: shownTotal }); setQuotedKey(null); setRefreshTick(tick => tick + 1);
      return;
    }
    await placeOrder();
  };
  useEffect(() => {
    if (!pendingPlace || !quoteMatches || quoteCommand.pending || shippingCommand.pending) return;
    const expected = pendingPlace.total;
    setPendingPlace(null);
    if (String(quote.payableAmount) !== String(expected)) {
      setToast(`Tổng thanh toán đã thay đổi thành ${format.money(quote.payableAmount)}. Bấm Đặt hàng để xác nhận.`);
      return;
    }
    placeOrder();
  }, [pendingPlace, quoteMatches, quoteCommand.pending, shippingCommand.pending]); // eslint-disable-line react-hooks/exhaustive-deps
  const pay = async () => {
    if (!checkout?.id) return;
    const result = await paymentCommand.run(key => api.initiatePayment(checkout.id, key));
    if (result.ok && result.result.paymentUrl) window.location.assign(result.result.paymentUrl);
  };

  if (cart.loading && !cart.data) return <Loading />;
  if (cart.error) return <ErrorNotice error={cart.error} onRetry={cart.reload} />;
  if (!groups.length) return <section className="workspace-card empty-state"><p>{cartLineIds ? 'Sản phẩm bạn chọn không còn trong giỏ hàng.' : 'Giỏ hàng đã trống.'}</p><Link className="button outline" to="/member/cart">Về giỏ hàng</Link><Link className="button primary" to="/products">Xem sản phẩm</Link></section>;
  const updating = !quoteMatches && !hasUnquotedDelivery;
  return <div className="retail-checkout-layout">
    {toast && <div className="checkout-toast" role="status" aria-live="polite">{toast}</div>}
    <section className="workspace-card retail-address-card">
      {addresses.loading && !addresses.data && <Loading />}
      {addresses.error && <ErrorNotice error={addresses.error} onRetry={addresses.reload} />}
      {selectedAddress ? <button type="button" className="retail-address-row" disabled={Boolean(checkout)} onClick={openAddressPicker} aria-label="Đổi địa chỉ nhận hàng">
        <span className="retail-address-pin" aria-hidden="true">📍</span>
        <span className="retail-address-lines">
          <span className="retail-address-who"><strong>{selectedAddress.recipientName}</strong><span>{selectedAddress.phoneNumber}</span>{selectedAddress.isDefault && <span className="retail-address-default">Mặc định</span>}</span>
          <span>{[selectedAddress.wardName, selectedAddress.districtName, selectedAddress.provinceName].filter(Boolean).join(', ')}</span>
          <span>{[selectedAddress.detailAddress, selectedAddress.hamletName].filter(Boolean).join(', ')}</span>
        </span>
        <span className="retail-address-chevron" aria-hidden="true">›</span>
      </button> : addresses.data && <button type="button" className="retail-address-row" onClick={() => openAddressForm(null)}>
        <span className="retail-address-pin" aria-hidden="true">📍</span>
        <span className="retail-address-lines"><strong>Thêm địa chỉ nhận hàng</strong></span>
        <span className="retail-address-chevron" aria-hidden="true">›</span>
      </button>}
    </section>
    {addressPickerOpen && <div className="benefit-picker-backdrop" role="presentation"><section className="benefit-picker address-picker" role="dialog" aria-modal="true" aria-label="Địa chỉ của tôi">
      <div className="card-head"><h2>Địa chỉ của tôi</h2></div>
      <div className="address-options" role="radiogroup" aria-label="Chọn địa chỉ nhận hàng">{(addresses.data || []).map(address => <label key={address.id} className={`address-option${Number(draftAddressId) === address.id ? ' selected' : ''}`}>
        <input type="radio" name="checkout-address" checked={Number(draftAddressId) === address.id} onChange={() => setDraftAddressId(address.id)} />
        <span><strong>{address.recipientName}</strong> <span className="muted">| {address.phoneNumber}</span><small>{fullAddress(address)}</small>{address.isDefault && <span className="retail-address-default">Mặc định</span>}</span>
        <button type="button" className="text-link address-edit" onClick={event => { event.preventDefault(); openAddressForm(address); }}>Cập nhật</button>
      </label>)}</div>
      <button type="button" className="button outline address-add" onClick={() => openAddressForm(null)}>+ Thêm địa chỉ mới</button>
      <div className="address-picker-actions"><button type="button" className="button outline" onClick={() => setAddressPickerOpen(false)}>Hủy</button><button type="button" className="button primary" disabled={draftAddressId == null} onClick={confirmAddress}>Xác nhận</button></div>
    </section></div>}
    <AddressModal isOpen={Boolean(addressForm)} onClose={closeAddressForm} onSave={saveAddress} initialAddress={addressForm?.address || null}
      profileDefaults={{ fullName: profile?.fullName, phone: profile?.phone }} isFirstAddress={!addresses.data?.length} />
    <section className="workspace-card retail-checkout-products"><div className="retail-card-title"><div><span className="retail-kicker">ĐƠN HÀNG CỦA BẠN</span><h2>Sản phẩm theo chi nhánh</h2></div></div>{groups.map(group => { const method = methodFor(group.locationId); const shipping = shippingQuotes[group.locationId]; return <div className="checkout-branch" key={group.locationId}><strong>{branchName(group.locationId)}</strong>{group.lines.map(line => <div className="retail-checkout-item" key={line.id}><span className="retail-product-placeholder" aria-hidden="true">◇</span><span><strong>{line.productName || `Sản phẩm #${line.variantId}`}</strong><small>Size {line.size ?? '—'} · Số lượng {line.quantity}</small></span><b>{line.indicativePrice == null ? 'Chưa có giá' : format.money((BigInt(line.indicativePrice) * BigInt(line.quantity)).toString())}</b></div>)}<div className="retail-fulfilment-options"><label className={method === 'PICKUP' ? 'selected' : ''}><input type="radio" name={`fulfilment-${group.locationId}`} checked={method === 'PICKUP'} disabled={Boolean(checkout)} onChange={() => changeMethod(group.locationId, 'PICKUP')} /><span className="fulfil-name">Nhận tại cửa hàng<small>Tại {branchName(group.locationId)}</small></span><b className="fulfil-fee">Miễn phí</b></label><label className={method === 'GHTK' ? 'selected' : ''}><input type="radio" name={`fulfilment-${group.locationId}`} checked={method === 'GHTK'} disabled={!selectedAddress || shippingCommand.pending || Boolean(checkout)} onChange={() => changeMethod(group.locationId, 'GHTK')} /><span className="fulfil-name">Giao tận nơi qua GHTK<small>Nhanh</small></span><b className="fulfil-fee">{shipping?.quoteId ? format.money(shipping.deliveryFee) : method === 'GHTK' && shippingCommand.pending ? 'Đang tính…' : ''}</b></label></div></div>; })}{(shippingMessage || shippingCommand.error) && <p className="notice error">{shippingMessage || shippingCommand.error?.message}</p>}</section>
    <section className="workspace-card retail-checkout-benefits"><div className="retail-card-title"><div><span className="retail-kicker">DÀNH RIÊNG CHO BẠN</span><h2>Ưu đãi & điểm</h2></div></div><RetailBenefitPanel selection={selection} onChange={changeBenefits} savedVouchers={savedVouchers} voucherSubtotal={voucherSubtotal} rejectedCodes={rejectedCodes} maxPoints={quote?.maxPoints ?? null} minRedeemPoints={quote?.minRedeemPoints ?? null} quote={quote} quoteMatches={quoteMatches} autoQuote pending={createCommand.pending || Boolean(checkout)} hideActions />{savedVoucherResource.error && <ErrorNotice error={savedVoucherResource.error} onRetry={savedVoucherResource.reload} />}{quoteCommand.error && <ErrorNotice error={quoteCommand.error} onRetry={quoteCommand.retry} onReload={cart.reload} />}</section>
    <section className="retail-summary retail-payment-section"><div className="retail-payment-method"><h2>Phương thức thanh toán</h2><div className="retail-payment-options" role="radiogroup" aria-label="Phương thức thanh toán"><label className={paymentMethod === 'VNPAY' ? 'selected' : ''}><input type="radio" name="payment-method" checked={paymentMethod === 'VNPAY'} disabled={Boolean(checkout)} onChange={() => setPaymentMethod('VNPAY')} /> VNPay <span>Thanh toán online ngay, tối thiểu 5.000đ</span></label><label className={`${paymentMethod === 'CASH_AT_COUNTER' ? 'selected' : ''}${cashAllowed ? '' : ' disabled'}`}><input type="radio" name="payment-method" checked={paymentMethod === 'CASH_AT_COUNTER'} disabled={!cashAllowed || Boolean(checkout)} onChange={() => setPaymentMethod('CASH_AT_COUNTER')} /> Tiền mặt tại quầy <span>{cashAllowed ? 'Trả khi nhận hàng tại chi nhánh, giữ hàng tối đa 3 ngày' : 'Chỉ áp dụng khi nhận tại cửa hàng ở một chi nhánh'}</span></label></div></div><div className={`retail-payment-details${updating && quote ? ' is-updating' : ''}`}><h2>Chi tiết thanh toán</h2>{quote ? <><div><span>Tiền hàng</span><strong>{format.money(quote.merchandiseSubtotal)}</strong></div>{hasAmount(quote.discountAmount) && <div><span>Voucher giảm giá</span><strong>−{format.money(quote.discountAmount)}</strong></div>}{hasAmount(quote.pointsDiscountAmount) && <div><span>Giảm bằng điểm</span><strong>−{format.money(quote.pointsDiscountAmount)}</strong></div>}{hasAmount(quote.shippingTotal) && <div><span>Phí vận chuyển</span><strong>{format.money(quote.shippingTotal)}</strong></div>}<div className="retail-total"><span>Tổng thanh toán</span><strong>{format.money(quote.payableAmount)}</strong></div>{updating && <p className="muted small">Đang cập nhật tổng tiền…</p>}</> : <p className="muted">{hasUnquotedDelivery ? 'Đang chờ phí giao hàng GHTK…' : 'Đang tính tổng tiền…'}</p>}</div><div className="retail-payment-actions"><button type="button" className="button primary" disabled={!quote?.eligible || !quoteMatches || createCommand.pending || Boolean(pendingPlace) || Boolean(checkout) || hasUnquotedDelivery} onClick={create}>{createCommand.pending ? 'Đang đặt hàng…' : pendingPlace ? 'Đang cập nhật…' : 'Đặt hàng'}</button></div>{createCommand.error && <ErrorNotice error={createCommand.error} onRetry={createCommand.retry} onReload={cart.reload} />}{checkout && isCashCheckout && <><SuccessNotice>Đã đặt hàng. Vui lòng đến chi nhánh trước {checkout.counterPaymentDeadline ? new Date(checkout.counterPaymentDeadline).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }) : 'hạn giữ hàng'} và thanh toán {format.money(checkout.combinedPayableAmount)} bằng tiền mặt khi nhận hàng.</SuccessNotice><Link className="button primary" to="/member/orders">Xem đơn mua</Link></>}{checkout && !isCashCheckout && <><SuccessNotice>Đã tạo checkout #{checkout.id}.</SuccessNotice><button type="button" className="button primary" disabled={paymentCommand.pending} onClick={pay}>{paymentCommand.pending ? 'Đang chuyển VNPay…' : 'Thanh toán qua VNPay'}</button><Link className="text-link" to="/member/orders">Xem đơn mua</Link></>}{paymentCommand.error && <ErrorNotice error={paymentCommand.error} onRetry={paymentCommand.retry} />}</section>
  </div>;
}
