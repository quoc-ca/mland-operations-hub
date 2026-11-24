import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { useFormat, useT } from '../../../../shared/i18n/I18nProvider';
import { ErrorNotice, Loading, SuccessNotice, useCommand, useResource } from '../../../../shared/ui/components';
import { useRetailApi } from '../../application/useRetailApi';
import { RetailBenefitPanel } from './RetailBenefitPanel';
import { ProductThumb } from '../ProductThumb';
import { messages as retailMessages } from '../../messages';
import { sharedMessages } from '../../../../shared/ui/messages';
import { useDisplayTime } from '../orderCode';
import { bestVoucherCode, isKnownReason, NO_AUTO_DISCOUNT_REASON, reasonText, savedProductVouchers, usePublicProductVouchers, useVoucherOptions, voucherCheckBase, withPublicVouchers } from './voucherOptions';
import { AddressModal, addressApiClient, useProfile } from '../../../accountmanagement';

const initialSelection = { choice: 'AUTO', voucherCode: '', requestedPoints: '0' };
const AUTO_QUOTE_DELAY_MS = 300;
const TOAST_MS = 3000;
// A quote or preview this close to expiry is refreshed before the order is placed.
const EXPIRY_MARGIN_MS = 10_000;
const hasAmount = value => /^[0-9]+$/.test(String(value ?? '')) && BigInt(value) > 0n;

/**
 * Payment-detail lines in the Shopee order (Thinh, 2026-11-22): the automatic product discount ("Ưu đãi sản phẩm")
 * and the voucher ("Voucher giảm giá") are separate lines. FE-06 stacks them (Session 2026-11-22) and the quote
 * carries both parts (`autoDiscountAmount`, `voucherDiscountAmount`); a quote without them is labelled by whether a
 * voucher code was applied.
 */
export function discountLines(quote) {
  if (!quote) return { product: null, voucher: null, points: null, saved: '0' };
  const split = quote.autoDiscountAmount != null || quote.voucherDiscountAmount != null;
  const product = split ? quote.autoDiscountAmount : (quote.appliedVoucherCode ? null : quote.discountAmount);
  const voucher = split ? quote.voucherDiscountAmount : (quote.appliedVoucherCode ? quote.discountAmount : null);
  const points = quote.pointsDiscountAmount;
  const saved = [product, voucher, points].filter(hasAmount).reduce((sum, value) => sum + BigInt(value), 0n).toString();
  return { product: hasAmount(product) ? product : null, voucher: hasAmount(voucher) ? voucher : null,
    points: hasAmount(points) ? points : null, saved };
}
const isExpired = at => !at || new Date(at).getTime() - Date.now() < EXPIRY_MARGIN_MS;
const newIdempotencyKey = () => (globalThis.crypto?.randomUUID ? globalThis.crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`);

// FE-001 V150: two-level address after the 2025 merger (province -> ward/commune, no district). Older
// three-level fields are read only as a fallback.
const wardOf = address => address.communeName || address.wardName || '';
const fullAddress = address => [address.detailAddress, address.hamletName, wardOf(address), address.districtName, address.provinceName].filter(Boolean).join(', ');
const recipientOf = address => address ? {
  address: [address.detailAddress, address.hamletName].filter(Boolean).join(', '),
  province: address.provinceName,
  district: address.districtName || null,
  ward: wardOf(address),
  recipientName: address.recipientName,
  recipientPhone: address.phoneNumber,
} : null;

/**
 * Why a saved address cannot be used for GHTK, or '' when it can. Same rule as the server's checkout check
 * (RetailCheckoutQuoteValidator): recipient name 1-100 characters, phone 0 or +84 followed by 9 digits, province,
 * ward and a street address of at most 500 characters. Older saved addresses may not meet it.
 */
export function ghtkAddressProblem(address, t) {
  if (!address) return t('checkout.addAndChooseADelivery');
  const problems = [];
  const name = String(address.recipientName || '').trim();
  if (!name) problems.push(t('checkout.noRecipientName'));
  else if ([...name].length > 100) problems.push(t('checkout.theRecipientNameHasCharacters', { count: [...name].length }));
  if (!/^(?:0|\+84)\d{9}$/.test(String(address.phoneNumber || '').replace(/ /g, ''))) problems.push(t('checkout.invalidPhoneNumberA10'));
  if (!String(address.provinceName || '').trim() || !wardOf(address).trim()) problems.push(t('checkout.provinceOrWardMissing'));
  const street = [address.detailAddress, address.hamletName].filter(Boolean).join(', ').trim();
  if (!street) problems.push(t('checkout.streetAddressMissing'));
  else if ([...street].length > 500) problems.push(t('checkout.streetAddressLongerThan500'));
  return problems.length ? t('checkout.thisAddressCannotBeUsed', { problems: problems.join('; ') }) : '';
}

export default function MemberCheckoutPage() {
  const t = useT(retailMessages);
  return <main className="container public-page retail-page retail-shopping"><div className="retail-page-heading"><div><p className="retail-kicker">{t('common.memberKicker')}</p><h1>{t('checkout.checkout')}</h1><p>{t('checkout.checkTheItemsDeliveryAnd')}</p></div><Link to="/member/cart" className="retail-back-link">{t('checkout.backToCart')}</Link></div><RequireSession requiredText={t('checkout.signInToCheckOut')}><CheckoutContent /></RequireSession></main>;
}

function CheckoutContent() {
  const api = useRetailApi();
  const format = useFormat();
  const t = useT(retailMessages);
  const ts = useT(sharedMessages);
  const time = useDisplayTime();
  const cart = useResource(() => api.getCart(), []);
  const addresses = useResource(() => addressApiClient.getOwnAddresses(), []);
  const savedVoucherResource = useResource(() => api.listSavedVouchers(), []);
  const branches = useResource(() => api.listBranches(), []);
  const quoteCommand = useCommand();
  const shippingCommand = useCommand();
  const createCommand = useCommand();
  const paymentCommand = useCommand();
  const location = useLocation();
  const navigate = useNavigate();
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
  // BUG-E2E-0014: why the last "Đặt hàng" was refused (409). Unlike the toast it stays above the button until the
  // Member refreshes or places the order, so a refused click is never silent.
  const [conflictNotice, setConflictNotice] = useState(null);
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
  const addressProblem = selectedAddress ? ghtkAddressProblem(selectedAddress, t) : '';
  const branchNames = useMemo(() => new Map((branches.data?.items || branches.data || []).map(item => [String(item.id), item.name])), [branches.data]);
  const branchName = locationId => branchNames.get(String(locationId)) || t('common.branchNumber', { locationId });
  const methodFor = locationId => fulfilmentByLocation[String(locationId)] || 'PICKUP';
  const branchOrders = useMemo(() => groups.map(group => {
    const method = methodFor(group.locationId);
    return { locationId: Number(group.locationId), method, shippingQuoteId: method === 'GHTK' ? shippingQuotes[group.locationId]?.quoteId || null : null, recipient: method === 'GHTK' ? recipient : null };
  }), [groups, fulfilmentByLocation, shippingQuotes, recipient]); // eslint-disable-line react-hooks/exhaustive-deps
  const hasUnquotedDelivery = groups.some(group => methodFor(group.locationId) === 'GHTK' && !shippingQuotes[group.locationId]?.quoteId);
  const selectionBody = { ...selection, voucherCode: selection.choice === 'VOUCHER' ? selection.voucherCode.trim() : null };
  // Cash at the counter (FR-08-041): store pickup only, one branch per checkout in V1; no 5,000 VND minimum.
  // Cash at the counter (FR-08-041): every branch is store pickup; each branch order is paid at its own counter.
  const cashAllowed = groups.length > 0 && groups.every(group => methodFor(group.locationId) === 'PICKUP');
  useEffect(() => {
    if (!cashAllowed && paymentMethod === 'CASH_AT_COUNTER') setPaymentMethod('VNPAY');
  }, [cashAllowed, paymentMethod]);
  const isCashCheckout = checkout?.paymentMethod === 'CASH_AT_COUNTER';
  const publicVouchers = usePublicProductVouchers(api);
  const savedVouchers = withPublicVouchers(savedProductVouchers(savedVoucherResource.data?.items || savedVoucherResource.data || []), publicVouchers);
  // Merchandise value of the selected lines, used to grey out vouchers whose minimum is not reached.
  const voucherSubtotal = groups.reduce((sum, group) => group.lines.reduce((acc, line) => acc + (line.indicativePrice == null ? 0n : BigInt(line.indicativePrice) * BigInt(line.quantity)), sum), 0n).toString();

  // Real FE-06 saving of each saved voucher for these lines, the same check the cart uses.
  const voucherOptions = useVoucherOptions(api, savedVouchers, voucherCheckBase(groups.flatMap(group => group.lines)));
  // Opened by "Mua Ngay" (no choice from the cart): pick the best voucher once, as the cart does (Session 2026-11-16).
  // A choice brought from the cart, or one the Member makes here, is never overridden.
  const autoPickVoucher = useRef(!location.state?.benefitSelection);
  useEffect(() => {
    if (!autoPickVoucher.current || voucherOptions == null || checkout) return;
    autoPickVoucher.current = false;
    const best = bestVoucherCode(voucherOptions);
    if (best && selection.choice === 'AUTO') setSelection(current => ({ ...current, choice: 'VOUCHER', voucherCode: best, requestedPoints: '0' }));
  }, [voucherOptions]); // eslint-disable-line react-hooks/exhaustive-deps

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
    if (!to) { setShippingMessage(t('checkout.addAndChooseADelivery')); return; }
    const problem = ghtkAddressProblem(address, t);
    if (problem) {
      setShippingQuotes(current => { const next = { ...current }; delete next[locationId]; return next; });
      setFulfilmentByLocation(current => ({ ...current, [locationId]: 'PICKUP' }));
      setShippingMessage(problem);
      return null;
    }
    setShippingMessage('');
    setShippingQuotes(current => { const next = { ...current }; delete next[locationId]; return next; });
    const result = await shippingCommand.run(() => api.quoteShipping({ locationId: Number(locationId), recipient: to, ...(cartLineIds ? { cartLineIds } : {}) }));
    if (!result.ok) return null;
    setShippingQuotes(current => ({ ...current, [locationId]: result.result }));
    if (!result.result.deliveryAvailable || !result.result.quoteId) setShippingMessage(result.result.message || t('checkout.ghtkDoesNotDeliverTo'));
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
        // FE-06 says why when it knows (Session 2026-11-22): not saved yet, or nothing left after the automatic discount.
        const known = (quote.reasons || []).find(reason => isKnownReason(reason) && reason !== NO_AUTO_DISCOUNT_REASON);
        setToast(known ? t('checkout.voucherRefusedWhy', { code, reason: reasonText(known, t) }) : t('checkout.voucherDoesNotApplyTo', { code }));
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
    autoPickVoucher.current = false;
    const sameVoucher = next.choice === selection.choice && next.voucherCode === selection.voucherCode;
    if (sameVoucher && next.requestedPoints !== selection.requestedPoints) setPointsOn(next.requestedPoints !== '0');
    setSelection(next);
  };
  const placeOrder = async () => {
    setConflictNotice(null);
    const result = await createCommand.run(key => api.createCheckout({ branchOrders, benefitPreviewId: quote.previewId, benefitSelection: selectionBody, benefitTermsAccepted: true, paymentMethod, ...(cartLineIds ? { cartLineIds } : {}) }, key));
    if (result.ok) {
      setConflictNotice(null);
      setCheckout(result.result);
      // VNPay: go straight to the payment page after the order is placed, no second click (Session 2026-11-18).
      // If starting the payment fails, the page stays with the order and a "Thanh toán qua VNPay" retry.
      // Cash: straight to "Đơn mua", which opens "Chờ nhận tại cửa hàng" and says where and until when to pay
      // (Session 2026-11-20). VNPay: straight to the payment page.
      if (result.result?.paymentMethod === 'CASH_AT_COUNTER') navigate(`/member/orders?checkout=${encodeURIComponent(result.result.id)}`, { replace: true });
      else await startPayment(result.result?.id);
      return;
    }
    // The fee or preview expired on the server in the meantime: refresh quietly and let the Member confirm again.
    if (result.error?.status === 409) {
      createCommand.reset();
      // FE-06 refused the voucher at order time (used up, expired, other branch): drop it and fall back to the
      // automatic discount, so the next click does not hit the same refusal (Session 2026-11-17).
      if (result.error?.code === 'RETAIL_VOUCHER_REFUSED' && selection.choice === 'VOUCHER' && selection.voucherCode) {
        const refused = selection.voucherCode;
        setRejectedCodes(current => new Set([...current, refused]));
        autoPickVoucher.current = false;
        setSelection(current => ({ ...current, choice: 'AUTO', voucherCode: '', requestedPoints: '0' }));
        setPointsOn(false);
      }
      for (const group of groups) {
        if (methodFor(group.locationId) === 'GHTK') await quoteDelivery(group.locationId); // eslint-disable-line no-await-in-loop
      }
      setQuotedKey(null); setRefreshTick(tick => tick + 1);
      // Refusals are recognised by the backend code and shown from the dictionary, so the notice follows the language switch.
      // The backend sends the generic benefit-changed case under RETAIL_CONFLICT with only a Vietnamese sentence, so that one
      // is still matched by its text until the backend gives it a code (ERROR-LOG FE-DESIGN-0009).
      const code = result.error?.code;
      const notice = code === 'RETAIL_VOUCHER_REFUSED' || code === 'RETAIL_POINTS_CHANGED' ? t(`error.${code}`)
        : /^Ưu đãi của đơn vừa thay đổi/.test(result.error?.message || '') ? t('checkout.benefitChanged')
        : t('checkout.theOrderQuoteChangedOr');
      setToast(notice);
      setConflictNotice(notice);
    }
  };
  // "Cập nhật lại" on the refusal notice: fetch fresh GHTK fees and a fresh total, then let the Member confirm.
  const refreshAfterConflict = async () => {
    setConflictNotice(null);
    for (const group of groups) {
      if (methodFor(group.locationId) === 'GHTK') await quoteDelivery(group.locationId); // eslint-disable-line no-await-in-loop
    }
    setQuotedKey(null); setRefreshTick(tick => tick + 1);
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
      if (changes.length) { setToast(t('checkout.theShippingFeeChangedCheck', { changes: changes.join(', ') })); return; }
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
      setToast(t('checkout.theTotalChangedToPress', { payableAmount: format.money(quote.payableAmount) }));
      return;
    }
    placeOrder();
  }, [pendingPlace, quoteMatches, quoteCommand.pending, shippingCommand.pending]); // eslint-disable-line react-hooks/exhaustive-deps
  const startPayment = async checkoutId => {
    if (!checkoutId) return;
    const result = await paymentCommand.run(key => api.initiatePayment(checkoutId, key));
    if (!result.ok) return;
    if (result.result.paymentUrl) {
      // The browser Back button on VNPay then lands on "Đơn mua › Chờ thanh toán", not on an emptied checkout.
      window.history.replaceState(null, '', `/member/orders?checkout=${encodeURIComponent(checkoutId)}`);
      window.location.assign(result.result.paymentUrl);
    }
    // Nothing to pay at VNPay (a zero total is paid at once): show the order instead.
    else if (result.result.paymentState === 'PAID') window.location.assign(`/member/orders?checkout=${encodeURIComponent(checkoutId)}`);
  };
  const pay = () => startPayment(checkout?.id);

  if (cart.loading && !cart.data) return <Loading />;
  if (cart.error) return <ErrorNotice error={cart.error} onRetry={cart.reload} />;
  if (!groups.length) return <section className="workspace-card empty-state"><p>{cartLineIds ? t('checkout.theItemsYouChoseAre') : t('checkout.yourCartIsEmpty')}</p><Link className="button outline" to="/member/cart">{t('checkout.backToCart2')}</Link><Link className="button primary" to="/products">{t('common.browseProducts')}</Link></section>;
  const updating = !quoteMatches && !hasUnquotedDelivery;
  return <div className="retail-checkout-layout">
    {toast && <div className="checkout-toast" role="status" aria-live="polite">{toast}</div>}
    <section className="workspace-card retail-address-card">
      {addresses.loading && !addresses.data && <Loading />}
      {addresses.error && <ErrorNotice error={addresses.error} onRetry={addresses.reload} />}
      {selectedAddress ? <button type="button" className="retail-address-row" disabled={Boolean(checkout)} onClick={openAddressPicker} aria-label={t('checkout.changeDeliveryAddress')}>
        <span className="retail-address-pin" aria-hidden="true">📍</span>
        <span className="retail-address-lines">
          <span className="retail-address-who"><strong>{selectedAddress.recipientName}</strong><span>{selectedAddress.phoneNumber}</span>{selectedAddress.isDefault && <span className="retail-address-default">{t('checkout.default')}</span>}</span>
          <span>{[wardOf(selectedAddress), selectedAddress.districtName, selectedAddress.provinceName].filter(Boolean).join(', ')}</span>
          <span>{[selectedAddress.detailAddress, selectedAddress.hamletName].filter(Boolean).join(', ')}</span>
        </span>
        <span className="retail-address-chevron" aria-hidden="true">›</span>
      </button> : addresses.data && <button type="button" className="retail-address-row" onClick={() => openAddressForm(null)}>
        <span className="retail-address-pin" aria-hidden="true">📍</span>
        <span className="retail-address-lines"><strong>{t('checkout.addADeliveryAddress')}</strong></span>
        <span className="retail-address-chevron" aria-hidden="true">›</span>
      </button>}
      {addressProblem && !checkout && <p className="notice error retail-address-problem" role="alert">{addressProblem} <button type="button" className="text-link" onClick={() => openAddressForm(selectedAddress)}>{t('checkout.update')}</button></p>}
    </section>
    {addressPickerOpen && <div className="benefit-picker-backdrop" role="presentation"><section className="benefit-picker address-picker" role="dialog" aria-modal="true" aria-label={t('checkout.myAddresses')}>
      <div className="card-head"><h2>{t('checkout.myAddresses')}</h2></div>
      <div className="address-options" role="radiogroup" aria-label={t('checkout.chooseADeliveryAddress')}>{(addresses.data || []).map(address => <label key={address.id} className={`address-option${Number(draftAddressId) === address.id ? ' selected' : ''}`}>
        <input type="radio" name="checkout-address" checked={Number(draftAddressId) === address.id} onChange={() => setDraftAddressId(address.id)} />
        <span><strong>{address.recipientName}</strong> <span className="muted">| {address.phoneNumber}</span><small>{fullAddress(address)}</small>{address.isDefault && <span className="retail-address-default">{t('checkout.default')}</span>}</span>
        <button type="button" className="text-link address-edit" onClick={event => { event.preventDefault(); openAddressForm(address); }}>{t('checkout.update')}</button>
      </label>)}</div>
      <button type="button" className="button outline address-add" onClick={() => openAddressForm(null)}>{t('checkout.addANewAddress')}</button>
      <div className="address-picker-actions"><button type="button" className="button outline" onClick={() => setAddressPickerOpen(false)}>{ts('cancel')}</button><button type="button" className="button primary" disabled={draftAddressId == null} onClick={confirmAddress}>{ts('confirm')}</button></div>
    </section></div>}
    <AddressModal isOpen={Boolean(addressForm)} onClose={closeAddressForm} onSave={saveAddress} initialAddress={addressForm?.address || null}
      profileDefaults={{ fullName: profile?.fullName, phone: profile?.phone }} isFirstAddress={!addresses.data?.length} />
    <section className="workspace-card retail-checkout-products"><div className="retail-card-title"><div><span className="retail-kicker">{t('checkout.yourOrder')}</span><h2>{t('checkout.itemsByBranch')}</h2></div></div>{groups.map(group => { const method = methodFor(group.locationId); const shipping = shippingQuotes[group.locationId]; return <div className="checkout-branch" key={group.locationId}><strong>{branchName(group.locationId)}</strong>{group.lines.map(line => <div className="retail-checkout-item" key={line.id}><ProductThumb className="retail-product-placeholder" productId={line.productId} /><span><strong>{line.productName || t('common.productNumber', { variantId: line.variantId })}</strong><small>{t('common.size', { size: line.size ?? '—' })} · {t('common.quantity')} {line.quantity}</small></span><b>{line.indicativePrice == null ? t('common.noPriceYet') : format.money((BigInt(line.indicativePrice) * BigInt(line.quantity)).toString())}</b></div>)}<div className="retail-fulfilment-options"><label className={method === 'PICKUP' ? 'selected' : ''}><input type="radio" name={`fulfilment-${group.locationId}`} checked={method === 'PICKUP'} disabled={Boolean(checkout)} onChange={() => changeMethod(group.locationId, 'PICKUP')} /><span className="fulfil-name">{t('common.storePickup')}<small>{t('checkout.at', { branch: branchName(group.locationId) })}</small></span><b className="fulfil-fee">{t('checkout.free')}</b></label><label className={method === 'GHTK' ? 'selected' : ''}><input type="radio" name={`fulfilment-${group.locationId}`} checked={method === 'GHTK'} disabled={!selectedAddress || Boolean(addressProblem) || shippingCommand.pending || Boolean(checkout)} onChange={() => changeMethod(group.locationId, 'GHTK')} /><span className="fulfil-name">{t('common.homeDeliveryByGhtk')}<small>{t('checkout.fast')}</small></span><b className="fulfil-fee">{shipping?.quoteId ? format.money(shipping.deliveryFee) : method === 'GHTK' && shippingCommand.pending ? t('checkout.calculating') : ''}</b></label></div></div>; })}{(shippingMessage || shippingCommand.error) && <p className="notice error">{shippingMessage || shippingCommand.error?.message}</p>}</section>
    <section className="workspace-card retail-checkout-benefits"><div className="retail-card-title"><div><span className="retail-kicker">{t('checkout.justForYou')}</span><h2>{t('checkout.discountsPoints')}</h2></div></div><RetailBenefitPanel selection={selection} onChange={changeBenefits} savedVouchers={savedVouchers} voucherSubtotal={voucherSubtotal} rejectedCodes={rejectedCodes} voucherOptions={voucherOptions} maxPoints={quote?.maxPoints ?? null} minRedeemPoints={quote?.minRedeemPoints ?? null} quote={quote} quoteMatches={quoteMatches} autoQuote pending={createCommand.pending || Boolean(checkout)} hideActions />{savedVoucherResource.error && <ErrorNotice error={savedVoucherResource.error} onRetry={savedVoucherResource.reload} />}{quoteCommand.error && <ErrorNotice error={quoteCommand.error} onRetry={quoteCommand.retry} onReload={cart.reload} />}</section>
    <section className="retail-summary retail-payment-section"><div className="retail-payment-method"><h2>{t('checkout.paymentMethod')}</h2><div className="retail-payment-options" role="radiogroup" aria-label={t('checkout.paymentMethod')}><label className={paymentMethod === 'VNPAY' ? 'selected' : ''}><input type="radio" name="payment-method" checked={paymentMethod === 'VNPAY'} disabled={Boolean(checkout)} onChange={() => setPaymentMethod('VNPAY')} /> VNPay <span>{t('checkout.payOnlineNowAtLeast')}</span></label><label className={`${paymentMethod === 'CASH_AT_COUNTER' ? 'selected' : ''}${cashAllowed ? '' : ' disabled'}`}><input type="radio" name="payment-method" checked={paymentMethod === 'CASH_AT_COUNTER'} disabled={!cashAllowed || Boolean(checkout)} onChange={() => setPaymentMethod('CASH_AT_COUNTER')} /> {t('common.cashAtCounter')} <span>{cashAllowed ? (groups.length > 1 ? t('checkout.payAtEachBranchCounter') : t('checkout.payAtTheBranchOn')) : t('checkout.onlyWhenEveryBranchOrder')}</span></label></div></div><div className={`retail-payment-details${updating && quote ? ' is-updating' : ''}`}><h2>{t('checkout.paymentDetails')}</h2>{quote ? (lines => <><div><span>{t('common.merchandiseSubtotal')}</span><strong>{format.money(quote.merchandiseSubtotal)}</strong></div>{hasAmount(quote.shippingTotal) && <div><span>{t('common.shippingTotal')}</span><strong>{format.money(quote.shippingTotal)}</strong></div>}{lines.product && <div className="retail-minus"><span>{t('checkout.productDiscount')}</span><strong>−{format.money(lines.product)}</strong></div>}{lines.voucher && <div className="retail-minus"><span>{t('checkout.voucherDiscount')}{quote.appliedVoucherCode ? <small> ({quote.appliedVoucherCode})</small> : null}</span><strong>−{format.money(lines.voucher)}</strong></div>}{lines.points && <div className="retail-minus"><span>{t('checkout.pointsDiscount')}</span><strong>−{format.money(lines.points)}</strong></div>}<div className="retail-total"><span>{t('checkout.totalPayment')}</span><strong>{format.money(quote.payableAmount)}</strong></div>{hasAmount(lines.saved) && <div className="retail-saved"><span>{t('common.youSave')}</span><strong>{format.money(lines.saved)}</strong></div>}{updating && <p className="muted small">{t('checkout.updatingTheTotal')}</p>}</>)(discountLines(quote)) : <p className="muted">{hasUnquotedDelivery ? t('checkout.waitingForTheGhtkShipping') : t('checkout.calculatingTheTotal')}</p>}</div>{conflictNotice && !checkout && <div className="retail-conflict-notice" role="alert"><p>{conflictNotice}</p><button type="button" className="button small outline" onClick={refreshAfterConflict}>{t('checkout.update2')}</button></div>}<div className="retail-payment-actions"><button type="button" className="button primary" disabled={!quote?.eligible || !quoteMatches || createCommand.pending || Boolean(pendingPlace) || Boolean(checkout) || hasUnquotedDelivery} onClick={create}>{createCommand.pending ? t('checkout.placingOrder') : checkout && !isCashCheckout && paymentCommand.pending ? t('checkout.openingVnpay') : pendingPlace ? t('checkout.updating') : t('checkout.placeOrder')}</button></div>{createCommand.error && <ErrorNotice error={createCommand.error} onRetry={createCommand.retry} onReload={cart.reload} />}{checkout && isCashCheckout && <><SuccessNotice>{t('checkout.cashOrderPlaced', { where: checkout.branchOrders?.length > 1 ? t('checkout.eachBranch') : t('checkout.theBranch'), deadline: checkout.counterPaymentDeadline ? time(checkout.counterPaymentDeadline) : t('checkout.holdEnds') })}{checkout.branchOrders?.length > 1 ? <>: {checkout.branchOrders.map(order => `${branchName(order.locationId)} ${format.money(order.payableAmount)}`).join(' · ')}</> : <> {format.money(checkout.combinedPayableAmount)}</>}.</SuccessNotice><Link className="button primary" to={`/member/orders?checkout=${encodeURIComponent(checkout.id)}`}>{t('checkout.viewOrders')}</Link></>}{checkout && !isCashCheckout && !paymentCommand.error && <p className="muted small" role="status">{t('checkout.orderPlacedOpeningTheVnpay')}</p>}{checkout && !isCashCheckout && paymentCommand.error && <><SuccessNotice>{t('checkout.theOrderIsPlacedBut')}</SuccessNotice><button type="button" className="button primary" disabled={paymentCommand.pending} onClick={pay}>{paymentCommand.pending ? t('checkout.openingVnpay2') : t('checkout.payWithVnpay')}</button><Link className="text-link" to={`/member/orders?checkout=${encodeURIComponent(checkout.id)}`}>{t('checkout.viewOrders')}</Link></>}{paymentCommand.error && <ErrorNotice error={paymentCommand.error} onRetry={paymentCommand.retry} />}</section>
  </div>;
}
